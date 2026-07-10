package com.xqfx.requirements.system;

import java.util.List;
import java.util.Set;

public record SystemProfile(String name, String ownerName, List<String> collaborators) {

    public static SystemProfile create(String name, String ownerName, List<String> collaborators) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("系统名称不能为空");
        }
        if (ownerName == null || ownerName.isBlank()) {
            throw new IllegalArgumentException("系统负责人不能为空");
        }

        var normalizedCollaborators = collaborators.stream()
                .map(String::trim)
                .toList();
        if (Set.copyOf(normalizedCollaborators).size() != normalizedCollaborators.size()) {
            throw new IllegalArgumentException("系统协助人不能重复");
        }

        return new SystemProfile(name.trim(), ownerName.trim(), normalizedCollaborators);
    }
}
