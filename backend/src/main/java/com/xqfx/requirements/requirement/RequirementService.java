package com.xqfx.requirements.requirement;

import com.xqfx.requirements.dictionary.DictionaryCategory;
import com.xqfx.requirements.dictionary.DictionaryItemEntity;
import com.xqfx.requirements.dictionary.DictionaryService;
import com.xqfx.requirements.system.SystemEntity;
import com.xqfx.requirements.system.SystemRepository;
import com.xqfx.requirements.system.SystemService;
import com.xqfx.requirements.system.SystemVersionEntity;
import com.xqfx.requirements.system.SystemVersionRepository;
import com.xqfx.requirements.user.UserEntity;
import com.xqfx.requirements.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
class RequirementService {

    private final RequirementRepository requirements;
    private final SystemRepository systems;
    private final SystemService systemService;
    private final SystemVersionRepository versions;
    private final DictionaryService dictionaries;
    private final RequirementProgressRepository progresses;
    private final UserRepository users;
    private final RequirementNotificationService notifications;

    RequirementService(RequirementRepository requirements, SystemRepository systems, SystemService systemService,
                       SystemVersionRepository versions, DictionaryService dictionaries,
                       RequirementProgressRepository progresses, UserRepository users,
                       RequirementNotificationService notifications) {
        this.requirements = requirements;
        this.systems = systems;
        this.systemService = systemService;
        this.versions = versions;
        this.dictionaries = dictionaries;
        this.progresses = progresses;
        this.users = users;
        this.notifications = notifications;
    }

    @Transactional
    RequirementResponse create(UserEntity requester, String requesterName, Long departmentId, String title, Long typeId, String content,
                               Long systemId, Long targetVersionId, LocalDate start, LocalDate end,
                               String newSystemName, Long newSystemOwnerUserId,
                               List<Long> newSystemCollaboratorUserIds) {
        var department = requester.department() == null
                ? dictionaries.requireActive(departmentId, DictionaryCategory.DEPARTMENT, "部门")
                : requester.department();
        var type = dictionaries.requireActive(typeId, DictionaryCategory.REQUIREMENT_TYPE, "需求类型");
        if (systemId != null && newSystemName != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能同时选择已有系统和新系统");
        }

        var system = newSystemName == null
                ? (systemId == null ? null : findSystem(systemId))
                : createNewSystem(newSystemName, newSystemOwnerUserId, newSystemCollaboratorUserIds);
        if (system != null && !system.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "系统已停用，不能新建需求");
        }

        var version = targetVersionId == null ? null : findVersion(targetVersionId);
        if (version != null && !version.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "版本已停用，不能作为目标版本");
        }
        assertVersionBelongsToSystem(version, system);

        var saved = requirements.save(new RequirementEntity(requester, normalizeRequesterName(requesterName, requester), department, title, type, content,
                system, version, RequirementPeriod.of(start, end)));
        notifications.onRequirementSubmitted(saved, requester);
        return RequirementResponse.from(saved);
    }

    @Transactional
    RequirementResponse createDraft(UserEntity requester, String requesterName, Long departmentId, String title, Long typeId,
                                    String content, Long systemId, Long targetVersionId,
                                    LocalDate start, LocalDate end) {
        var department = requester.department() == null
                ? resolveOptionalActive(departmentId, DictionaryCategory.DEPARTMENT, "部门")
                : requester.department();
        var type = resolveOptionalActive(typeId, DictionaryCategory.REQUIREMENT_TYPE, "需求类型");
        var system = systemId == null ? null : findSystem(systemId);
        var version = targetVersionId == null ? null : findVersion(targetVersionId);
        assertVersionBelongsToSystem(version, system);
        return RequirementResponse.from(requirements.save(RequirementEntity.draft(requester, normalizeRequesterName(requesterName, requester), department, title,
                type, content, system, version, RequirementPeriod.of(start, end))));
    }

    @Transactional(readOnly = true)
    List<RequirementResponse> list(Long typeId, Long systemId, RequirementSaveType saveType) {
        var items = saveType != null
                ? requirements.findBySaveTypeAndDeletedFalse(saveType)
                : (systemId != null
                        ? requirements.findBySystemIdAndDeletedFalse(systemId)
                        : (typeId != null
                                ? requirements.findByType_IdAndDeletedFalse(typeId)
                                : requirements.findAllByDeletedFalse()));
        return items.stream().map(RequirementResponse::from).toList();
    }

    @Transactional(readOnly = true)
    RequirementPageResponse page(int page, int size, Long systemId, boolean unassignedSystem,
                                 Long targetVersionId, Long departmentId, String requesterName, Long typeId,
                                 RequirementStatus status, RequirementSaveType saveType, String keyword,
                                 LocalDate submittedFrom, LocalDate submittedTo,
                                 LocalDate periodOverlapStart, LocalDate periodOverlapEnd) {
        validatePageFilter(page, size, systemId, unassignedSystem, submittedFrom, submittedTo,
                periodOverlapStart, periodOverlapEnd);
        return RequirementPageResponse.from(requirements.findAll(
                RequirementSpecifications.filtered(systemId, unassignedSystem, targetVersionId, departmentId,
                        requesterName, typeId, status, saveType, keyword, submittedFrom, submittedTo,
                        periodOverlapStart, periodOverlapEnd),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @Transactional(readOnly = true)
    RequirementPageResponse managementPage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("分页参数无效");
        }
        return RequirementPageResponse.from(requirements.findAll(RequirementSpecifications.management(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"))));
    }

    @Transactional(readOnly = true)
    RequirementResponse get(Long id) {
        return RequirementResponse.from(findActive(id));
    }

    @Transactional(readOnly = true)
    List<RequirementProgressResponse> progresses(Long id) {
        findActive(id);
        return progresses.findByRequirement_IdOrderByCreatedAtAscIdAsc(id).stream()
                .map(RequirementProgressResponse::from)
                .toList();
    }

    @Transactional
    RequirementProgressResponse addProgress(Long id, UserEntity author, String content, RequirementStatus status, Long recordVersion) {
        var requirement = findActive(id);
        var statusChanged = status != null && status != requirement.status();
        if (statusChanged) {
            assertRecordVersion(requirement.recordVersion(), recordVersion, "需求已被其他人修改，请刷新后重试");
            requirement.updateStatusFromProgress(status);
            requirements.flush();
        }
        var progress = progresses.save(new RequirementProgressEntity(
                requirement, author.id(), author.displayName(), content.trim(), status));
        if (statusChanged) {
            notifications.onStatusChanged(requirement, progress, author);
        }
        return RequirementProgressResponse.from(progress);
    }

    @Transactional
    RequirementResponse updateDraft(Long id, String requesterName, Long departmentId, String title, Long typeId,
                                    String content, Long systemId, Long targetVersionId,
                                    LocalDate start, LocalDate end, Long recordVersion) {
        var requirement = findActive(id);
        assertRecordVersion(requirement.recordVersion(), recordVersion, "需求已被其他人修改，请刷新后重试");
        if (!requirement.isDraft()) {
            throw new IllegalArgumentException("只有草稿可以暂存更新");
        }
        var department = resolveForUpdate(departmentId, requirement.department(),
                DictionaryCategory.DEPARTMENT, "部门");
        var type = resolveForUpdate(typeId, requirement.type(),
                DictionaryCategory.REQUIREMENT_TYPE, "需求类型");
        var system = systemId == null ? null : findSystem(systemId);
        var version = targetVersionId == null ? null : findVersion(targetVersionId);
        assertVersionBelongsToSystem(version, system);
        requirement.updateDraft(requesterName, department, title, type, content, system, version,
                RequirementPeriod.of(start, end));
        requirements.flush();
        return RequirementResponse.from(requirement);
    }

    @Transactional
    RequirementResponse update(Long id, UserEntity actor, String requesterName, Long departmentId, String title, Long typeId,
                               String content, Long systemId, Long targetVersionId,
                               LocalDate start, LocalDate end, Long recordVersion) {
        var requirement = findActive(id);
        var submittingDraft = requirement.isDraft();
        assertRecordVersion(requirement.recordVersion(), recordVersion, "需求已被其他人修改，请刷新后重试");
        var department = resolveForUpdate(departmentId, requirement.department(),
                DictionaryCategory.DEPARTMENT, "部门");
        var type = resolveForUpdate(typeId, requirement.type(),
                DictionaryCategory.REQUIREMENT_TYPE, "需求类型");
        var system = systemId == null ? null : findSystem(systemId);
        var version = targetVersionId == null ? null : findVersion(targetVersionId);
        if (system != null && !system.isActive()
                && (requirement.system() == null || !system.id().equals(requirement.system().id()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "系统已停用，不能主动更换");
        }
        if (version != null && !version.isActive()
                && (requirement.targetVersion() == null || !version.id().equals(requirement.targetVersion().id()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "版本已停用，不能主动更换");
        }
        assertVersionBelongsToSystem(version, system);
        requirement.update(requesterName, department, title, type, content, system, version,
                RequirementPeriod.of(start, end));
        if (submittingDraft) {
            requirement.linkRequesterUserIfMissing(actor);
        }
        requirements.flush();
        if (submittingDraft) {
            notifications.onRequirementSubmitted(requirement, actor);
        }
        return RequirementResponse.from(requirement);
    }

    @Transactional
    RequirementResponse assign(Long id, Long assigneeUserId, Long recordVersion, UserEntity actor) {
        var requirement = findActive(id);
        assertRecordVersion(requirement.recordVersion(), recordVersion, "需求已被其他人修改，请刷新后重试");
        var assignee = assigneeUserId == null ? null : findEnabledUser(assigneeUserId);
        var currentAssignee = requirement.assignee();
        if (Objects.equals(currentAssignee == null ? null : currentAssignee.id(), assignee == null ? null : assignee.id())) {
            return RequirementResponse.from(requirement);
        }
        requirement.assign(assignee);
        requirements.flush();
        if (assignee != null) {
            notifications.onAssigneeChanged(requirement, actor);
        }
        return RequirementResponse.from(requirement);
    }

    @Transactional
    void delete(Long id) {
        findActive(id).delete();
    }

    private DictionaryItemEntity resolveOptionalActive(Long id, DictionaryCategory category, String label) {
        return id == null ? null : dictionaries.requireActive(id, category, label);
    }

    /**
     * A disabled item may remain on an old record. It cannot be selected for a different value.
     */
    private DictionaryItemEntity resolveForUpdate(Long requestedId, DictionaryItemEntity current,
                                                  DictionaryCategory category, String label) {
        if (requestedId == null) {
            return null;
        }
        if (current != null && current.id().equals(requestedId)) {
            return current;
        }
        return dictionaries.requireActive(requestedId, category, label);
    }

    private RequirementEntity findActive(Long id) {
        return requirements.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "需求不存在"));
    }

    private SystemEntity findSystem(Long id) {
        return systems.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "系统不存在"));
    }

    private SystemVersionEntity findVersion(Long id) {
        return versions.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "版本不存在"));
    }

    private UserEntity findEnabledUser(Long id) {
        var user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (user.isDisabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户已停用，不能指派");
        }
        return user;
    }

    private SystemEntity createNewSystem(String name, Long ownerUserId, List<Long> collaboratorUserIds) {
        return systemService.createBoundEntity(name, ownerUserId, collaboratorUserIds);
    }

    private static String normalizeRequesterName(String requesterName, UserEntity requester) {
        return requesterName == null || requesterName.isBlank() ? requester.displayName() : requesterName.trim();
    }

    private static void assertVersionBelongsToSystem(SystemVersionEntity version, SystemEntity system) {
        if (version != null && (system == null || !version.system().id().equals(system.id()))) {
            throw new IllegalArgumentException("目标版本不属于所属系统");
        }
    }

    private static void validatePageFilter(int page, int size, Long systemId, boolean unassignedSystem,
                                           LocalDate submittedFrom, LocalDate submittedTo,
                                           LocalDate periodOverlapStart, LocalDate periodOverlapEnd) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("分页参数无效");
        }
        if (systemId != null && unassignedSystem) {
            throw new IllegalArgumentException("不能同时筛选具体系统和暂无系统");
        }
        if (submittedFrom != null && submittedTo != null && submittedTo.isBefore(submittedFrom)) {
            throw new IllegalArgumentException("填写结束日期不能早于开始日期");
        }
        if ((periodOverlapStart == null) != (periodOverlapEnd == null)) {
            throw new IllegalArgumentException("周期筛选开始和结束日期必须同时填写");
        }
        if (periodOverlapStart != null && periodOverlapEnd.isBefore(periodOverlapStart)) {
            throw new IllegalArgumentException("周期筛选结束日期不能早于开始日期");
        }
    }

    private static void assertRecordVersion(long currentVersion, Long requestVersion, String message) {
        if (requestVersion == null || requestVersion != currentVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }
}
