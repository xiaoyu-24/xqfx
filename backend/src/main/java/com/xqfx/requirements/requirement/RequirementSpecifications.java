package com.xqfx.requirements.requirement;

import org.springframework.data.jpa.domain.Specification;
import java.time.LocalDate;

final class RequirementSpecifications {
    private RequirementSpecifications() { }

    static Specification<RequirementEntity> filtered(Long systemId, boolean unassignedSystem, Long targetVersionId,
                                                     Long departmentId, String requesterName, Long typeId,
                                                     RequirementStatus status, RequirementSaveType saveType,
                                                     String keyword, LocalDate submittedFrom, LocalDate submittedTo,
                                                     LocalDate periodOverlapStart, LocalDate periodOverlapEnd) {
        Specification<RequirementEntity> specification = (root, query, criteriaBuilder) -> criteriaBuilder.isFalse(root.get("deleted"));
        if (systemId != null) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("system").get("id"), systemId));
        if (unassignedSystem) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.isNull(root.get("system")));
        if (targetVersionId != null) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("targetVersion").get("id"), targetVersionId));
        if (departmentId != null) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("department").get("id"), departmentId));
        if (hasText(requesterName)) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("requesterName"), requesterName.trim()));
        if (typeId != null) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("type").get("id"), typeId));
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

    static Specification<RequirementEntity> management() {
        return (root, query, criteriaBuilder) -> {
            var active = criteriaBuilder.isFalse(root.get("deleted"));
            var terminal = root.get("status").in(RequirementStatus.COMPLETED, RequirementStatus.CLOSED, RequirementStatus.REJECTED);
            return criteriaBuilder.and(active, criteriaBuilder.or(criteriaBuilder.isNull(root.get("status")), criteriaBuilder.not(terminal)));
        };
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
}
