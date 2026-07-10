package com.xqfx.requirements.system;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
class SystemVersionService {

    private final SystemRepository systemRepository;
    private final SystemVersionRepository versionRepository;

    SystemVersionService(SystemRepository systemRepository, SystemVersionRepository versionRepository) {
        this.systemRepository = systemRepository;
        this.versionRepository = versionRepository;
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
}
