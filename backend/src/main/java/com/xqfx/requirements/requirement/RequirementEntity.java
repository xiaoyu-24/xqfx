package com.xqfx.requirements.requirement;

import com.xqfx.requirements.system.SystemEntity;
import com.xqfx.requirements.system.SystemVersionEntity;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "requirements")
class RequirementEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String requesterName;
    @Column(nullable = false) private String department;
    @Column(nullable = false) private String title;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RequirementType type;
    @Column(nullable = false, length = 10000) private String content;
    @ManyToOne private SystemEntity system;
    @ManyToOne private SystemVersionEntity targetVersion;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RequirementStatus status = RequirementStatus.PENDING_EVALUATION;
    protected RequirementEntity() { }
    RequirementEntity(String requesterName, String department, String title, RequirementType type, String content, SystemEntity system, SystemVersionEntity targetVersion, RequirementPeriod period) {
        this.requesterName=requesterName; this.department=department; this.title=title; this.type=type; this.content=content; this.system=system; this.targetVersion=targetVersion; this.periodStartDate=period.startDate(); this.periodEndDate=period.endDate();
    }
    Long id(){return id;} RequirementType type(){return type;} RequirementStatus status(){return status;} SystemVersionEntity targetVersion(){return targetVersion;} LocalDate periodStartDate(){return periodStartDate;} LocalDate periodEndDate(){return periodEndDate;}
}
