package com.xqfx.requirements.aiconfig;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
class AiConfigService {

    private static final long CONFIG_ID = 1L;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final AiConfigRepository repository;
    private final byte[] encryptionKey;
    private final int requestTimeoutSeconds;

    AiConfigService(AiConfigRepository repository,
                    @Value("${app.ai.encryption-key:}") String encryptionKeyBase64,
                    @Value("${app.ai.request-timeout-seconds:30}") int requestTimeoutSeconds) {
        this.repository = repository;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.encryptionKey = encryptionKeyBase64.isBlank() ? null : Base64.getDecoder().decode(encryptionKeyBase64);
    }

    @Transactional(readOnly = true)
    AiConfigResponse getConfig() {
        var config = repository.findById(CONFIG_ID).orElse(null);
        if (config == null) {
            return new AiConfigResponse(false, null, null, null);
        }
        String mask = null;
        if (config.apiKeyEncrypted() != null && encryptionKey != null) {
            var decrypted = decrypt(config.apiKeyEncrypted());
            mask = maskApiKey(decrypted);
        }
        return new AiConfigResponse(config.enabled(), config.serviceUrl(), config.modelName(), mask);
    }

    @Transactional
    AiConfigResponse saveConfig(AiConfigSaveRequest request) {
        var config = repository.findById(CONFIG_ID).orElseThrow(() -> new IllegalStateException("AI 配置记录不存在"));
        config.update(request.enabled(), blankToNull(request.serviceUrl()), blankToNull(request.modelName()));
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            if (encryptionKey == null) {
                throw new IllegalStateException("服务端未配置 AI_ENCRYPTION_KEY，无法保存 API Key");
            }
            config.updateApiKeyEncrypted(encrypt(request.apiKey()));
        }
        repository.save(config);
        String mask = null;
        if (config.apiKeyEncrypted() != null && encryptionKey != null) {
            mask = maskApiKey(decrypt(config.apiKeyEncrypted()));
        }
        return new AiConfigResponse(config.enabled(), config.serviceUrl(), config.modelName(), mask);
    }

    boolean testConnection() {
        var config = repository.findById(CONFIG_ID).orElseThrow(() -> new IllegalStateException("AI 配置记录不存在"));
        if (config.serviceUrl() == null || config.serviceUrl().isBlank()) {
            throw new IllegalArgumentException("请先配置服务地址");
        }
        try {
            var client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .build();
            var requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(config.serviceUrl().replaceAll("/+$", "") + "/models"))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .GET();
            if (config.apiKeyEncrypted() != null && encryptionKey != null) {
                requestBuilder.header("Authorization", "Bearer " + decrypt(config.apiKeyEncrypted()));
            }
            var response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            throw new AiConnectionException("连接测试失败：" + e.getMessage());
        }
    }

    String getDecryptedApiKey() {
        var config = repository.findById(CONFIG_ID).orElse(null);
        if (config == null || config.apiKeyEncrypted() == null || encryptionKey == null) {
            return null;
        }
        return decrypt(config.apiKeyEncrypted());
    }

    AiConfigEntity getEntity() {
        return repository.findById(CONFIG_ID).orElse(null);
    }

    private String encrypt(String plainText) {
        try {
            var iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            var cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            var combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("加密 API Key 失败", e);
        }
    }

    private String decrypt(String encryptedBase64) {
        try {
            var combined = Base64.getDecoder().decode(encryptedBase64);
            var iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            var cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("解密 API Key 失败", e);
        }
    }

    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static class AiConnectionException extends RuntimeException {
        AiConnectionException(String message) {
            super(message);
        }
    }
}
