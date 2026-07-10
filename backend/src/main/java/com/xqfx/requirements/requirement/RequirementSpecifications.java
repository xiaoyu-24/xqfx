package com.xqfx.requirements.requirement;

import org.springframework.data.jpa.domain.Specification;
import java.time.LocalDate;

final class RequirementSpecifications {
    private RequirementSpecifications() { }

    static Specification<RequirementEntity> filtered(Long systemId, boolean unassignedSystem, Long targetVersionId, String department, String requesterName, RequirementType type, RequirementStatus status, RequirementSaveType saveType, String keyword, LocalDate submittedFrom, LocalDate submittedTo, LocalDate periodOverlapStart, LocalDate periodOverlapEnd) {
        Specification<RequirementEntity> specification = (root, query, criteriaBuilder) -> criteriaBuilder.isFalse(root.get("deleted"));
        if (systemId != null) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("system").get("id"), systemId));
        if (unassignedSystem) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.isNull(root.get("system")));
        if (targetVersionId != null) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("targetVersion").get("id"), targetVersionId));
        if (hasText(department)) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("department"), department.trim()));
        if (hasText(requesterName)) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("requesterName"), requesterName.trim()));
        if (type != null) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("type"), type));
        if (status != null) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), status));
        if (saveType != null) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("saveType"), saveType));
        if (hasText(keyword)) {
            var pattern = "%" + keyword.trim().toLowerCase() + "%";
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("content")), pattern)
            ));
        }
        if (submittedFrom != null) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("submittedAt"), submittedFrom.atStartOfDay()));
        if (submittedTo != null) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.lessThan(root.get("submittedAt"), submittedTo.plusDays(1).atStartOfDay()));
        if (periodOverlapStart != null && periodOverlapEnd != null) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.and(criteriaBuilder.lessThanOrEqualTo(root.get("periodStartDate"), periodOverlapEnd), criteriaBuilder.greaterThanOrEqualTo(root.get("periodEndDate"), periodOverlapStart)));
        return specification;
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
}
