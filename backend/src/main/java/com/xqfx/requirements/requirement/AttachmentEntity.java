package com.xqfx.requirements.requirement;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "attachments")
class AttachmentEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) private RequirementEntity requirement;
    @Column(nullable = false) private String originalName;
    @Column(nullable = false) private String storedName;
    @Column(nullable = false) private String relativePath;
    @Column(nullable = false) private String contentType;
    @Column(nullable = false) private long sizeBytes;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private boolean deleted = false;
    private LocalDateTime deletedAt;
    protected AttachmentEntity() { }
    AttachmentEntity(RequirementEntity requirement, String originalName, String storedName, String relativePath, String contentType, long sizeBytes) { this.requirement=requirement;this.originalName=originalName;this.storedName=storedName;this.relativePath=relativePath;this.contentType=contentType;this.sizeBytes=sizeBytes;this.createdAt=LocalDateTime.now(); }
    Long id(){return id;} String originalName(){return originalName;} String storedName(){return storedName;} String contentType(){return contentType;} long sizeBytes(){return sizeBytes;}
    void delete(){deleted=true;deletedAt=LocalDateTime.now();}
}
