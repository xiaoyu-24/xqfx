package com.xqfx.requirements.system;

record SystemVersionResponse(Long id, Long systemId, String name, SystemVersionStatus status, long requirementCount) {

    static SystemVersionResponse from(SystemVersionEntity version, long requirementCount) {
        return new SystemVersionResponse(version.id(), version.system().id(), version.name(), version.status(), requirementCount);
    }
}
