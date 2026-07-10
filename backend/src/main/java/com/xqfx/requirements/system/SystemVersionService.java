package com.xqfx.requirements.system;

import com.xqfx.requirements.requirement.RequirementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
class SystemVersionService {

    private final SystemRepository systemRepository;
    private final SystemVersionRepository versionRepository;
    private final RequirementRepository requirements;

    SystemVersionService(SystemRepository systemRepository, SystemVersionRepository versionRepository, RequirementRepository requirements) {
        this.systemRepository = systemRepository;
        this.versionRepository = versionRepository;
        this.requirements = requirements;
    }

    @Transactional
    SystemVersionResponse create(Long systemId, String name) {
        var system = findActiveSystem(systemId);
        if (!system.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "系统已停用，不能新增版本");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("版本名称不能为空");
        }
        if (versionRepository.existsBySystemIdAndActiveNameKey(systemId, SystemVersionEntity.normalizedName(name))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "版本名称已存在");
        }
        return toResponse(versionRepository.save(new SystemVersionEntity(system, name)));
    }

    @Transactional(readOnly = true)
    java.util.List<SystemVersionResponse> list(Long systemId) {
        findActiveSystem(systemId);
        return versionRepository.findBySystemIdAndDeletedFalseOrderByNameAsc(systemId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    SystemVersionResponse update(Long id, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("版本名称不能为空");
        }
        var version = findActiveVersion(id);
        if (versionRepository.existsBySystemIdAndActiveNameKeyAndIdNot(version.system().id(), SystemVersionEntity.normalizedName(name), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "版本名称已存在");
        }
        version.updateName(name);
        return toResponse(version);
    }

    @Transactional
    SystemVersionResponse updateStatus(Long id, SystemVersionStatus status) {
        var version = findActiveVersion(id);
        version.updateStatus(status);
        return toResponse(version);
    }

    @Transactional
    void delete(Long id) {
        var version = findActiveVersion(id);
        if (requirements.countByTargetVersionIdAndDeletedFalse(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "版本存在关联需求，无法删除");
        }
        version.delete();
    }

    private SystemVersionResponse toResponse(SystemVersionEntity version) {
        return SystemVersionResponse.from(version, requirements.countByTargetVersionIdAndDeletedFalse(version.id()));
    }

    private SystemEntity findActiveSystem(Long id) {
        return systemRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "系统不存在"));
    }

    private SystemVersionEntity findActiveVersion(Long id) {
        return versionRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "版本不存在"));
    }
}
