package com.xqfx.requirements.requirement;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
class DashboardController {

    private final RequirementRepository repository;

    DashboardController(RequirementRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/summary")
    Map<String, Object> summary() {
        long total = repository.countByDeletedFalseAndSaveType(RequirementSaveType.SUBMITTED);
        long draftCount = repository.countByDeletedFalseAndSaveType(RequirementSaveType.DRAFT);

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (RequirementStatus status : RequirementStatus.values()) {
            statusCounts.put(status.name(), repository.countByDeletedFalseAndSaveTypeAndStatus(RequirementSaveType.SUBMITTED, status));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("draftCount", draftCount);
        result.put("statusCounts", statusCounts);
        return result;
    }
}
