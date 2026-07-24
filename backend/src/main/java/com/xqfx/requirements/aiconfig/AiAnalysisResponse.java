package com.xqfx.requirements.aiconfig;

import java.time.LocalDate;

record AiAnalysisResponse(
        String requesterName,
        String department,
        String title,
        String type,
        String content,
        Long systemId,
        Long targetVersionId,
        LocalDate periodStartDate,
        LocalDate periodEndDate
) {
}
