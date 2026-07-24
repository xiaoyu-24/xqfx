package com.xqfx.requirements.aiconfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xqfx.requirements.system.SystemEntity;
import com.xqfx.requirements.system.SystemRepository;
import com.xqfx.requirements.system.SystemVersionEntity;
import com.xqfx.requirements.system.SystemVersionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
class AiAnalysisService {

    private static final Set<String> APPROVED_DEPARTMENTS = Set.of(
            "IT部", "FAE部", "总经办", "供应链部", "财务部",
            "产品部", "市场运营部", "业务部", "品质部", "人力资源部"
    );
    private static final Set<String> VALID_TYPES = Set.of("BUG", "REQUIREMENT");

    private final AiConfigService configService;
    private final SystemRepository systemRepository;
    private final SystemVersionRepository versionRepository;
    private final ObjectMapper objectMapper;
    private final int requestTimeoutSeconds;

    AiAnalysisService(AiConfigService configService,
                      SystemRepository systemRepository,
                      SystemVersionRepository versionRepository,
                      ObjectMapper objectMapper,
                      @Value("${app.ai.request-timeout-seconds:30}") int requestTimeoutSeconds) {
        this.configService = configService;
        this.systemRepository = systemRepository;
        this.versionRepository = versionRepository;
        this.objectMapper = objectMapper;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    AiAnalysisResponse analyze(String text) {
        var config = configService.getActiveEntity();
        if (config == null || !config.enabled()) {
            throw new AiAnalysisException("AI 功能未启用，请先在 AI 配置页开启并激活一个配置");
        }
        if (config.serviceUrl() == null || config.serviceUrl().isBlank()) {
            throw new AiAnalysisException("AI 服务地址未配置");
        }
        if (config.modelName() == null || config.modelName().isBlank()) {
            throw new AiAnalysisException("AI 模型名未配置");
        }

        String apiKey = configService.getDecryptedApiKey(config);
        String prompt = buildPrompt(text);

        try {
            String responseContent = callModel(config.serviceUrl(), config.modelName(), apiKey, prompt);
            return parseAndValidate(responseContent);
        } catch (AiAnalysisException e) {
            throw e;
        } catch (Exception e) {
            throw new AiAnalysisException("AI 分析失败：" + e.getMessage());
        }
    }

    private String buildPrompt(String userText) {
        var systems = systemRepository.findAllByDeletedFalse().stream()
                .filter(SystemEntity::isActive)
                .collect(Collectors.toList());

        var systemDict = new StringBuilder();
        for (var system : systems) {
            systemDict.append("- 系统ID=").append(system.id()).append("，名称=\"").append(system.name()).append("\"");
            var versions = versionRepository.findBySystemIdAndDeletedFalseOrderByNameAsc(system.id()).stream()
                    .filter(SystemVersionEntity::isActive)
                    .toList();
            if (!versions.isEmpty()) {
                systemDict.append("，版本：");
                systemDict.append(versions.stream()
                        .map(v -> "ID=" + v.id() + "/\"" + v.name() + "\"")
                        .collect(Collectors.joining("、")));
            }
            systemDict.append("\n");
        }

        return """
                你是一个需求分析助手。请从用户提供的文本中提取需求信息，严格按以下 JSON 格式返回（不要输出其他内容）：
                {"requesterName":"...","department":"...","title":"...","type":"...","content":"...","system":"...","version":"...","periodStartDate":"...","periodEndDate":"..."}

                规则：
                1. requesterName 为提出需求的人名（2-4个汉字），无法确定则填 null。
                2. department 必须是以下之一：%s。用户可能写“IT”“it部”“信息技术部”等变体，请匹配最接近的部门名称。无法确定则填 null。
                3. type 只能是 BUG 或 REQUIREMENT。无法确定则填 null。
                4. title 为简短概括，content 为完整需求描述。无法提取则填 null。
                5. system 填写最匹配的系统名称。用户文本中可能使用简称、别名或大小写不同的名称，请尽量匹配下方系统列表中最接近的系统。无法唯一确定则填 null。
                6. version 填写最匹配的版本名称（必须从对应系统的版本列表中匹配），无法唯一确定则填 null。
                7. periodStartDate 和 periodEndDate 格式为 yyyy-MM-dd，无法确定则填 null。

                可选部门：%s

                系统与版本字典：
                %s
                用户文本：
                %s
                """.formatted(
                String.join("、", APPROVED_DEPARTMENTS),
                String.join("、", APPROVED_DEPARTMENTS),
                systemDict.toString(),
                userText
        );
    }

    private String callModel(String serviceUrl, String modelName, String apiKey, String prompt) throws Exception {
        var requestBody = objectMapper.writeValueAsString(java.util.Map.of(
                "model", modelName,
                "messages", List.of(java.util.Map.of("role", "user", "content", prompt)),
                "temperature", 0.1
        ));

        var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(serviceUrl.replaceAll("/+$", "") + "/chat/completions"))
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));

        if (apiKey != null && !apiKey.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(requestTimeoutSeconds))
                .build();

        var response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AiAnalysisException("AI 服务返回错误，状态码：" + response.statusCode());
        }

        var responseJson = objectMapper.readTree(response.body());
        JsonNode choices = responseJson.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new AiAnalysisException("AI 服务未返回有效结果");
        }
        return choices.get(0).get("message").get("content").asText();
    }

    private AiAnalysisResponse parseAndValidate(String content) {
        try {
            // 提取 JSON 部分（模型可能包含 markdown 代码块）
            String json = content.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("\\n?```$", "").strip();
            }

            var node = objectMapper.readTree(json);

            String requesterName = getNullableString(node, "requesterName");
            String department = getValidDepartment(node);
            String title = getNullableString(node, "title");
            String type = getValidType(node);
            String contentText = getNullableString(node, "content");
            Long systemId = resolveSystemId(node);
            Long targetVersionId = resolveVersionId(node, systemId);
            LocalDate periodStart = getValidDate(node, "periodStartDate");
            LocalDate periodEnd = getValidDate(node, "periodEndDate");

            return new AiAnalysisResponse(requesterName, department, title, type, contentText, systemId, targetVersionId, periodStart, periodEnd);
        } catch (AiAnalysisException e) {
            throw e;
        } catch (Exception e) {
            throw new AiAnalysisException("AI 返回结果解析失败，请重试");
        }
    }

    private String getValidDepartment(JsonNode node) {
        var value = getNullableString(node, "department");
        if (value == null) return null;
        // 精确匹配
        if (APPROVED_DEPARTMENTS.contains(value)) return value;
        // 模糊匹配：去掉"部"字后比较，或包含匹配
        var normalizedInput = value.replace("部", "").trim().toLowerCase(Locale.ROOT);
        var matches = APPROVED_DEPARTMENTS.stream()
                .filter(d -> {
                    var normalizedDept = d.replace("部", "").trim().toLowerCase(Locale.ROOT);
                    return normalizedDept.equals(normalizedInput)
                            || normalizedDept.contains(normalizedInput)
                            || normalizedInput.contains(normalizedDept);
                })
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private String getValidType(JsonNode node) {
        var value = getNullableString(node, "type");
        if (value != null && VALID_TYPES.contains(value.toUpperCase(Locale.ROOT))) {
            return value.toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private Long resolveSystemId(JsonNode node) {
        var systemName = getNullableString(node, "system");
        if (systemName == null) return null;

        var normalized = systemName.trim().toLowerCase(Locale.ROOT);
        var activeSystems = systemRepository.findAllByDeletedFalse().stream()
                .filter(SystemEntity::isActive)
                .toList();

        // 精确匹配
        var exactMatches = activeSystems.stream()
                .filter(s -> SystemEntity.normalizedName(s.name()).equals(normalized))
                .toList();
        if (exactMatches.size() == 1) return exactMatches.get(0).id();

        // 模糊匹配：包含关系
        var fuzzyMatches = activeSystems.stream()
                .filter(s -> {
                    var sysName = SystemEntity.normalizedName(s.name());
                    return sysName.contains(normalized) || normalized.contains(sysName);
                })
                .toList();
        return fuzzyMatches.size() == 1 ? fuzzyMatches.get(0).id() : null;
    }

    private Long resolveVersionId(JsonNode node, Long systemId) {
        if (systemId == null) return null;
        var versionName = getNullableString(node, "version");
        if (versionName == null) return null;

        var normalized = versionName.trim().toLowerCase(Locale.ROOT);
        var activeVersions = versionRepository.findBySystemIdAndDeletedFalseOrderByNameAsc(systemId).stream()
                .filter(SystemVersionEntity::isActive)
                .toList();

        // 精确匹配
        var exactMatches = activeVersions.stream()
                .filter(v -> SystemVersionEntity.normalizedName(v.name()).equals(normalized))
                .toList();
        if (exactMatches.size() == 1) return exactMatches.get(0).id();

        // 模糊匹配：包含关系
        var fuzzyMatches = activeVersions.stream()
                .filter(v -> {
                    var vName = SystemVersionEntity.normalizedName(v.name());
                    return vName.contains(normalized) || normalized.contains(vName);
                })
                .toList();
        return fuzzyMatches.size() == 1 ? fuzzyMatches.get(0).id() : null;
    }

    private LocalDate getValidDate(JsonNode node, String field) {
        var value = getNullableString(node, field);
        if (value == null) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String getNullableString(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || value.isNull()) return null;
        var text = value.asText().strip();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
    }

    static class AiAnalysisException extends RuntimeException {
        AiAnalysisException(String message) {
            super(message);
        }
    }
}
