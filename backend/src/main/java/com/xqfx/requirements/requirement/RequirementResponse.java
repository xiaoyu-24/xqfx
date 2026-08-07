package com.xqfx.requirements.requirement;

import java.time.LocalDate;
import java.time.LocalDateTime;

record RequirementResponse(
        Long id,
        String requesterName,
        Long departmentId,
        String department,
        String title,
        Long typeId,
        String type,
        String content,
        Long systemId,
        RequirementSaveType saveType,
        RequirementStatus status,
        Long targetVersionId,
        String targetVersionName,
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime submittedAt,
        LocalDateTime statusUpdatedAt,
        LocalDateTime completedAt,
        String handledBy,
        String completionDescription,
        long recordVersion) {

    static RequirementResponse from(RequirementEntity requirement) {
        var department = requirement.department();
        var type = requirement.type();
        return new RequirementResponse(
                requirement.id(),
                requirement.requesterName(),
                department == null ? null : department.id(),
                department == null ? null : department.name(),
                requirement.title(),
                type == null ? null : type.id(),
                type == null ? null : type.name(),
                requirement.content(),
                requirement.system() == null ? null : requirement.system().id(),
                requirement.saveType(),
                requirement.status(),
                requirement.targetVersion() == null ? null : requirement.targetVersion().id(),
                requirement.targetVersion() == null ? null : requirement.targetVersion().name(),
                requirement.periodStartDate(),
                requirement.periodEndDate(),
                requirement.createdAt(),
                requirement.updatedAt(),
                requirement.submittedAt(),
                requirement.statusUpdatedAt(),
                requirement.completedAt(),
                requirement.handledBy(),
                requirement.completionDescription(),
                requirement.recordVersion());
    }
}
