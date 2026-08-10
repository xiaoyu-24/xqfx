package com.xqfx.requirements.requirement;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;

import java.util.List;

final class RequirementSpecifications {
    private RequirementSpecifications() { }

    static Specification<RequirementEntity> filtered(Long systemId, boolean unassignedSystem, Long targetVersionId,
                                                     Long departmentId, String requesterName, Long typeId,
                                                     RequirementStatus status, RequirementSaveType saveType,
                                                     String keyword,
                                                     boolean unfinishedOnly, String sortBy, String sortDirection,
                                                     Long requesterUserId) {
        Specification<RequirementEntity> specification = (root, query, criteriaBuilder) -> criteriaBuilder.isFalse(root.get("deleted"));
        if (systemId != null) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("system").get("id"), systemId));
        if (unassignedSystem) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.isNull(root.get("system")));
        if (targetVersionId != null) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("targetVersion").get("id"), targetVersionId));
        if (departmentId != null) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("department").get("id"), departmentId));
        if (hasText(requesterName)) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("requesterName"), requesterName.trim()));
        if (typeId != null) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("type").get("id"), typeId));
        if (status != null) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), status));
        if (saveType != null) specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("saveType"), saveType));
        if (unfinishedOnly) {
            specification = specification.and((root, query, criteriaBuilder) -> root.get("status").in(unfinishedStatuses()));
        }
        if (requesterUserId != null) {
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("requesterUser").get("id"), requesterUserId));
        }
        if (hasText(keyword)) {
            var pattern = "%" + keyword.trim().toLowerCase() + "%";
            specification = specification.and((root, query, criteriaBuilder) -> {
                var progressQuery = query.subquery(Long.class);
                Root<RequirementProgressEntity> progress = progressQuery.from(RequirementProgressEntity.class);
                progressQuery.select(criteriaBuilder.literal(1L)).where(criteriaBuilder.and(
                        criteriaBuilder.equal(progress.get("requirement").get("id"), root.get("id")),
                        criteriaBuilder.like(criteriaBuilder.lower(progress.get("content")), pattern)
                ));
                return criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("content")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("requesterName")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("handledBy")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("completionDescription")), pattern),
                        criteriaBuilder.exists(progressQuery)
                );
            });
        }
        return specification.and(orderBy(sortBy, sortDirection));
    }

    private static Specification<RequirementEntity> orderBy(String sortBy, String sortDirection) {
        return (root, query, criteriaBuilder) -> {
            if (!Long.class.equals(query.getResultType())) {
                if ("urgency".equals(sortBy)) {
                    Expression<Integer> priority = criteriaBuilder.<Integer>selectCase()
                            .when(criteriaBuilder.equal(root.get("urgency"), RequirementUrgency.HIGH), 1)
                            .when(criteriaBuilder.equal(root.get("urgency"), RequirementUrgency.MEDIUM), 2)
                            .otherwise(3);
                    var order = "desc".equalsIgnoreCase(sortDirection)
                            ? criteriaBuilder.desc(priority)
                            : criteriaBuilder.asc(priority);
                    query.orderBy(order, criteriaBuilder.desc(root.get("createdAt")));
                } else {
                    query.orderBy(criteriaBuilder.desc(root.get("createdAt")));
                }
            }
            return criteriaBuilder.conjunction();
        };
    }

    private static List<RequirementStatus> unfinishedStatuses() {
        return List.of(
                RequirementStatus.PENDING_EVALUATION,
                RequirementStatus.CONFIRMED,
                RequirementStatus.IN_DEVELOPMENT,
                RequirementStatus.PAUSED);
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
}
