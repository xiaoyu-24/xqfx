package com.xqfx.requirements.requirement;

import com.xqfx.requirements.system.SystemEntity;
import com.xqfx.requirements.system.SystemVersionEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "requirements")
class RequirementEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String requesterName;
    private String department;
    private String title;
    @Enumerated(EnumType.STRING) private RequirementType type;
    @Column(length = 10000) private String content;
    @ManyToOne private SystemEntity system;
    @ManyToOne private SystemVersionEntity targetVersion;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
    @Enumerated(EnumType.STRING) private RequirementStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RequirementSaveType saveType;
    @Column(nullable = false) private boolean deleted = false;
    private LocalDateTime deletedAt;
    protected RequirementEntity() { }
    RequirementEntity(String requesterName, String department, String title, RequirementType type, String content, SystemEntity system, SystemVersionEntity targetVersion, RequirementPeriod period) {
        this.requesterName=requesterName; this.department=department; this.title=title; this.type=type; this.content=content; this.system=system; this.targetVersion=targetVersion; this.periodStartDate=period.startDate(); this.periodEndDate=period.endDate(); this.saveType=RequirementSaveType.SUBMITTED; this.status=RequirementStatus.PENDING_EVALUATION;
    }
    static RequirementEntity draft(String requesterName,String department,String title,RequirementType type,String content,SystemEntity system,SystemVersionEntity targetVersion,RequirementPeriod period){var draft=new RequirementEntity();draft.requesterName=requesterName;draft.department=department;draft.title=title;draft.type=type;draft.content=content;draft.system=system;draft.targetVersion=targetVersion;draft.periodStartDate=period.startDate();draft.periodEndDate=period.endDate();draft.saveType=RequirementSaveType.DRAFT;return draft;}
    Long id(){return id;} String requesterName(){return requesterName;} String department(){return department;} String title(){return title;} String content(){return content;} SystemEntity system(){return system;} RequirementType type(){return type;} RequirementStatus status(){return status;} RequirementSaveType saveType(){return saveType;} SystemVersionEntity targetVersion(){return targetVersion;} LocalDate periodStartDate(){return periodStartDate;} LocalDate periodEndDate(){return periodEndDate;}
    void updateStatus(RequirementStatus status){this.status=status;}
    void update(String requesterName,String department,String title,RequirementType type,String content,SystemEntity system,SystemVersionEntity targetVersion,RequirementPeriod period,RequirementStatus status){this.requesterName=requesterName;this.department=department;this.title=title;this.type=type;this.content=content;this.system=system;this.targetVersion=targetVersion;this.periodStartDate=period.startDate();this.periodEndDate=period.endDate();if(isDraft()){this.saveType=RequirementSaveType.SUBMITTED;this.status=status==null?RequirementStatus.PENDING_EVALUATION:status;}else if(status!=null){this.status=status;}}
    boolean isDraft(){return saveType==RequirementSaveType.DRAFT;}
    void updateDraft(String requesterName,String department,String title,RequirementType type,String content,SystemEntity system,SystemVersionEntity targetVersion,RequirementPeriod period){this.requesterName=requesterName;this.department=department;this.title=title;this.type=type;this.content=content;this.system=system;this.targetVersion=targetVersion;this.periodStartDate=period.startDate();this.periodEndDate=period.endDate();}
    void delete(){this.deleted=true;this.deletedAt=LocalDateTime.now();}
}
