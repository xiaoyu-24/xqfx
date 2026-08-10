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

        Map<String, Long> urgencyCounts = new LinkedHashMap<>();
        for (RequirementUrgency urgency : RequirementUrgency.values()) {
            urgencyCounts.put(urgency.name(), repository.countByDeletedFalseAndSaveTypeAndUrgency(RequirementSaveType.SUBMITTED, urgency));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("draftCount", draftCount);
        result.put("statusCounts", statusCounts);
        result.put("urgencyCounts", urgencyCounts);
        return result;
    }

    /**
     * 全平台需求分布，用于需求概览页。草稿只计入总数、系统和紧急程度，
     * 状态与完成率仅基于已正式提交的需求，避免草稿干扰流程统计。
     */
    @GetMapping("/overview")
    @Transactional(readOnly = true)
    OverviewResponse overview() {
        var terminalStatuses = Set.of(RequirementStatus.COMPLETED, RequirementStatus.REJECTED, RequirementStatus.CLOSED);
        var total = repository.countByDeletedFalse();
        var submitted = repository.countByDeletedFalseAndSaveType(RequirementSaveType.SUBMITTED);
        var drafts = repository.countByDeletedFalseAndSaveType(RequirementSaveType.DRAFT);
        var pendingEvaluation = repository.countByDeletedFalseAndSaveTypeAndStatus(
                RequirementSaveType.SUBMITTED, RequirementStatus.PENDING_EVALUATION);
        var unfinished = repository.countByDeletedFalseAndSaveTypeAndStatusNotIn(
                RequirementSaveType.SUBMITTED, terminalStatuses);
        var completed = repository.countByDeletedFalseAndSaveTypeAndStatus(
                RequirementSaveType.SUBMITTED, RequirementStatus.COMPLETED);
        var highUrgencyPending = repository.countByDeletedFalseAndSaveTypeAndUrgencyAndStatusNotIn(
                RequirementSaveType.SUBMITTED, RequirementUrgency.HIGH, terminalStatuses);

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (RequirementStatus status : RequirementStatus.values()) {
            statusCounts.put(status.name(), repository.countByDeletedFalseAndSaveTypeAndStatus(RequirementSaveType.SUBMITTED, status));
        }

        Map<String, Long> urgencyCounts = new LinkedHashMap<>();
        for (RequirementUrgency urgency : RequirementUrgency.values()) {
            urgencyCounts.put(urgency.name(), repository.countByDeletedFalseAndUrgency(urgency));
        }

        var systemCounts = repository.countRequirementsBySystem().stream()
                .map(item -> new SystemCount(item.getSystemId(), item.getSystemName(), item.getRequirementCount()))
                .toList();
        var completionRate = submitted == 0 ? 0 : (int) Math.round((double) completed * 100 / submitted);

        return new OverviewResponse(
                total,
                drafts,
                unfinished,
                pendingEvaluation,
                Math.max(unfinished - pendingEvaluation, 0),
                completed,
                completionRate,
                highUrgencyPending,
                systemCounts,
                statusCounts,
                urgencyCounts);
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
                                RequirementStatus status, RequirementUrgency urgency,
                                java.time.LocalDateTime updatedAt) {
        static WorkbenchRequirement from(RequirementEntity requirement) {
            return new WorkbenchRequirement(
                    requirement.id(),
                    requirement.title(),
                    requirement.requesterName(),
                    requirement.system().name(),
                    requirement.status(),
                    requirement.urgency(),
                    requirement.updatedAt());
        }
    }

    record OverviewResponse(long total, long draftCount, long unfinishedCount, long pendingEvaluationCount,
                            long inProgressCount, long completedCount, int completionRate,
                            long highUrgencyPendingCount, List<SystemCount> systemCounts,
                            Map<String, Long> statusCounts, Map<String, Long> urgencyCounts) {
    }

    record SystemCount(Long systemId, String systemName, Long count) {
    }
}
