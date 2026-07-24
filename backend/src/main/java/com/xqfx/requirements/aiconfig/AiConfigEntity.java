package com.xqfx.requirements.aiconfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(nullable = false)
    private boolean isActive = false;

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

    AiConfigEntity(String name) {
        this.name = name;
    }

    Long id() { return id; }
    String name() { return name; }
    boolean enabled() { return enabled; }
    boolean isActive() { return isActive; }
    String serviceUrl() { return serviceUrl; }
    String modelName() { return modelName; }
    String apiKeyEncrypted() { return apiKeyEncrypted; }

    void update(String name, boolean enabled, String serviceUrl, String modelName) {
        this.name = name;
        this.enabled = enabled;
        this.serviceUrl = serviceUrl;
        this.modelName = modelName;
    }

    void updateApiKeyEncrypted(String encrypted) {
        this.apiKeyEncrypted = encrypted;
    }

    void activate() { this.isActive = true; }
    void deactivate() { this.isActive = false; }

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
