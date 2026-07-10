package com.xqfx.requirements.system;

import java.util.List;

record SystemResponse(Long id, String name, String ownerName, List<String> collaborators) {

    static SystemResponse from(SystemEntity system) {
        return new SystemResponse(system.id(), system.name(), system.ownerName(), system.collaborators());
    }
}
