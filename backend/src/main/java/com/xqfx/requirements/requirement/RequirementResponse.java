package com.xqfx.requirements.requirement;
import java.time.LocalDate;
record RequirementResponse(Long id,RequirementType type,RequirementStatus status,Long targetVersionId,LocalDate periodStartDate,LocalDate periodEndDate){static RequirementResponse from(RequirementEntity r){return new RequirementResponse(r.id(),r.type(),r.status(),r.targetVersion()==null?null:r.targetVersion().id(),r.periodStartDate(),r.periodEndDate());}}
