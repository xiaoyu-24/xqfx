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
        var system = systemRepository.findById(systemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "系统不存在"));
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("版本名称不能为空");
        }
        return SystemVersionResponse.from(versionRepository.save(new SystemVersionEntity(system, name)));
    }

    @Transactional(readOnly = true)
    java.util.List<SystemVersionResponse> list(Long systemId) {
        return versionRepository.findBySystemIdAndDeletedFalseOrderByNameAsc(systemId).stream()
                .map(SystemVersionResponse::from)
                .toList();
    }

    @Transactional
    SystemVersionResponse update(Long id, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("版本名称不能为空");
        }
        var version = versionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "版本不存在"));
        version.updateName(name);
        return SystemVersionResponse.from(version);
    }

    @Transactional
    SystemVersionResponse updateStatus(Long id, SystemVersionStatus status) {
        var version = versionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "版本不存在"));
        version.updateStatus(status);
        return SystemVersionResponse.from(version);
    }

    @Transactional
    void delete(Long id) {
        var version = versionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "版本不存在"));
        if (requirements.countByTargetVersionIdAndDeletedFalse(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "版本存在关联需求，无法删除");
        }
        version.delete();
    }
}
