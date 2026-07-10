package com.xqfx.requirements.system;

import com.xqfx.requirements.requirement.RequirementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
class SystemService {

    private final SystemRepository repository;
    private final RequirementRepository requirements;

    SystemService(SystemRepository repository, RequirementRepository requirements) {
        this.repository = repository;
        this.requirements = requirements;
    }

    @Transactional
    SystemResponse create(String name, String ownerName, List<String> collaborators) {
        var profile = SystemProfile.create(name, ownerName, collaborators);
        if (repository.existsByActiveNameKey(SystemEntity.normalizedName(profile.name()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "系统名称已存在");
        }
        var saved = repository.save(new SystemEntity(profile));
        return SystemResponse.from(saved);
    }

    @Transactional(readOnly = true)
    List<SystemResponse> list() {
        return repository.findAllByDeletedFalse().stream()
                .map(SystemResponse::from)
                .toList();
    }

    @Transactional
    SystemResponse update(Long id, String name, String ownerName, List<String> collaborators) {
        var system = findActive(id);
        var profile = SystemProfile.create(name, ownerName, collaborators);
        if (repository.existsByActiveNameKeyAndIdNot(SystemEntity.normalizedName(profile.name()), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "系统名称已存在");
        }
        system.update(profile);
        return SystemResponse.from(system);
    }

    @Transactional
    SystemResponse updateStatus(Long id, SystemStatus status) {
        var system = findActive(id);
        system.updateStatus(status);
        return SystemResponse.from(system);
    }

    @Transactional
    void delete(Long id) {
        var system = findActive(id);
        if (requirements.countBySystemIdAndDeletedFalse(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "系统存在关联需求，无法删除");
        }
        system.delete();
    }

    private SystemEntity findActive(Long id) {
        return repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "系统不存在"));
    }
}
