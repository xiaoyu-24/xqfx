package com.xqfx.requirements.system;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_versions")
public class SystemVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "system_id", nullable = false)
    private SystemEntity system;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String activeNameKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SystemVersionStatus status = SystemVersionStatus.ACTIVE;

    @Column(nullable = false)
    private boolean deleted = false;

    private LocalDateTime deletedAt;

    protected SystemVersionEntity() {
    }

    public SystemVersionEntity(SystemEntity system, String name) {
        this.system = system;
        this.name = name.trim();
        this.activeNameKey = normalizedName(name);
    }

    public Long id() { return id; }
    public SystemEntity system() { return system; }
    public String name() { return name; }
    SystemVersionStatus status() { return status; }
    public boolean isActive() { return status == SystemVersionStatus.ACTIVE; }

    void updateName(String name) { this.name = name.trim(); this.activeNameKey = normalizedName(name); }
    void updateStatus(SystemVersionStatus status) { this.status = status; }
    void delete() { this.deleted = true; this.deletedAt = LocalDateTime.now(); this.activeNameKey = null; }

    public static String normalizedName(String name) { return name.trim().toLowerCase(java.util.Locale.ROOT); }
}
