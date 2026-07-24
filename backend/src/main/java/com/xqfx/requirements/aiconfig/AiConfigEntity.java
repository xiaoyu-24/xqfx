package com.xqfx.requirements.aiconfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "ai_config")
class AiConfigEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(length = 500)
    private String serviceUrl;

    @Column(length = 200)
    private String modelName;

    @Column(length = 1000)
    private String apiKeyEncrypted;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected AiConfigEntity() {
    }

    Long id() { return id; }
    boolean enabled() { return enabled; }
    String serviceUrl() { return serviceUrl; }
    String modelName() { return modelName; }
    String apiKeyEncrypted() { return apiKeyEncrypted; }

    void update(boolean enabled, String serviceUrl, String modelName) {
        this.enabled = enabled;
        this.serviceUrl = serviceUrl;
        this.modelName = modelName;
    }

    void updateApiKeyEncrypted(String encrypted) {
        this.apiKeyEncrypted = encrypted;
    }

    @PrePersist
    void setInitialTimestamps() {
        var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        this.updatedAt = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    }
}
