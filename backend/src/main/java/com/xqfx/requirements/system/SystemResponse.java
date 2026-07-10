package com.xqfx.requirements.system;

import java.util.List;

record SystemResponse(
        Long id,
        String name,
        String ownerName,
        SystemStatus status,
        List<String> collaborators,
        long versionCount,
        long requirementCount
) {

    static SystemResponse from(SystemEntity system, long versionCount, long requirementCount) {
        return new SystemResponse(
                system.id(),
                system.name(),
                system.ownerName(),
                system.status(),
                system.collaborators(),
                versionCount,
                requirementCount
        );
    }
}
