package com.xqfx.requirements.system;

record SystemVersionResponse(Long id, Long systemId, String name, SystemVersionStatus status) {

    static SystemVersionResponse from(SystemVersionEntity version) {
        return new SystemVersionResponse(version.id(), version.system().id(), version.name(), version.status());
    }
}
