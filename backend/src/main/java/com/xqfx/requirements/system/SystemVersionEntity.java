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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SystemVersionStatus status = SystemVersionStatus.ACTIVE;

    protected SystemVersionEntity() {
    }

    public SystemVersionEntity(SystemEntity system, String name) {
        this.system = system;
        this.name = name.trim();
    }

    public Long id() { return id; }
    public SystemEntity system() { return system; }
    String name() { return name; }
    SystemVersionStatus status() { return status; }
}
