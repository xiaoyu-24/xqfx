package com.xqfx.requirements.requirement;

import com.xqfx.requirements.user.CurrentUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @GetMapping("/workbench")
    @Transactional(readOnly = true)
    WorkbenchResponse workbench() {
        var userId = CurrentUser.require().id();
        var terminalStatuses = Set.of(RequirementStatus.COMPLETED, RequirementStatus.REJECTED, RequirementStatus.CLOSED);
        var owned = repository.findWorkbenchOwned(userId, RequirementSaveType.SUBMITTED, terminalStatuses);
        var ownedIds = owned.stream().map(RequirementEntity::id).collect(java.util.stream.Collectors.toSet());
        var assisting = repository.findWorkbenchAssisting(userId, RequirementSaveType.SUBMITTED, terminalStatuses).stream()
                .filter(requirement -> !ownedIds.contains(requirement.id()))
                .toList();
        return new WorkbenchResponse(
                owned.stream().map(WorkbenchRequirement::from).toList(),
                assisting.stream().map(WorkbenchRequirement::from).toList());
    }

    record WorkbenchResponse(List<WorkbenchRequirement> owned, List<WorkbenchRequirement> assisting) {
    }

    record WorkbenchRequirement(Long id, String title, String requesterName, String systemName,
                                RequirementStatus status, java.time.LocalDateTime updatedAt) {
        static WorkbenchRequirement from(RequirementEntity requirement) {
            return new WorkbenchRequirement(
                    requirement.id(),
                    requirement.title(),
                    requirement.requesterName(),
                    requirement.system().name(),
                    requirement.status(),
                    requirement.updatedAt());
        }
    }
}
