package com.xqfx.requirements.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "users")
public class UserEntity {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long recordVersion;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String displayName;

    @Column(length = 50)
    private String department;

    @Column(nullable = false)
    private boolean adminRole = false;

    @Column(nullable = false)
    private boolean disabled = false;

    @Column(nullable = false)
    private boolean mustChangePassword = true;

    @Column(nullable = false)
    private int failedLoginAttempts = 0;

    private LocalDateTime lockedUntil;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected UserEntity() {
    }

    UserEntity(String username, String passwordHash, String displayName, String department, boolean adminRole) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.department = department;
        this.adminRole = adminRole;
    }

    public Long id() {
        return id;
    }

    public String username() {
        return username;
    }

    String passwordHash() {
        return passwordHash;
    }

    public String displayName() {
        return displayName;
    }

    public String department() {
        return department;
    }

    public boolean isAdmin() {
        return adminRole;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    void updateProfile(String displayName, String department, boolean adminRole) {
        this.displayName = displayName;
        this.department = department;
        this.adminRole = adminRole;
    }

    void updateDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    /** 管理员重置密码：下次登录必须改密。 */
    void resetPassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.mustChangePassword = true;
        clearLoginFailures();
    }

    /** 用户自行修改密码。 */
    void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.mustChangePassword = false;
        clearLoginFailures();
    }

    boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now(ZONE));
    }

    LocalDateTime lockedUntil() {
        return lockedUntil;
    }

    void recordLoginFailure() {
        this.failedLoginAttempts += 1;
        if (this.failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            this.lockedUntil = LocalDateTime.now(ZONE).plusMinutes(LOCK_MINUTES);
            this.failedLoginAttempts = 0;
        }
    }

    void clearLoginFailures() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    @PrePersist
    void setInitialTimestamps() {
        var now = LocalDateTime.now(ZONE);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        this.updatedAt = LocalDateTime.now(ZONE);
    }
}
