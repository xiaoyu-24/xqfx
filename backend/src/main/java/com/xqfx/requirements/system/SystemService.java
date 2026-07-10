package com.xqfx.requirements.system;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
class SystemService {

    private final SystemRepository repository;

    SystemService(SystemRepository repository) {
        this.repository = repository;
    }

    @Transactional
    SystemResponse create(String name, String ownerName, List<String> collaborators) {
        var profile = SystemProfile.create(name, ownerName, collaborators);
        var saved = repository.save(new SystemEntity(profile));
        return SystemResponse.from(saved);
    }

    @Transactional(readOnly = true)
    List<SystemResponse> list() {
        return repository.findAll().stream()
                .map(SystemResponse::from)
                .toList();
    }
}
