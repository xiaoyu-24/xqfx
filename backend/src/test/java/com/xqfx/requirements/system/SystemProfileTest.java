package com.xqfx.requirements.system;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SystemProfileTest {

    @Test
    void rejectsBlankOwnerWhenCreatingSystem() {
        assertThrows(IllegalArgumentException.class, () -> {
            Class<?> profileType = Class.forName("com.xqfx.requirements.system.SystemProfile");
            var create = profileType.getMethod("create", String.class, String.class, List.class);

            try {
                create.invoke(null, "客户管理系统", " ", List.of());
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        });
    }

    @Test
    void rejectsDuplicateCollaboratorsWhenCreatingSystem() {
        assertThrows(IllegalArgumentException.class, () ->
                SystemProfile.create("客户管理系统", "李明", List.of("王芳", " 王芳 "))
        );
    }
}
