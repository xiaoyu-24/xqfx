package com.xqfx.requirements.user;

public record UserResponse(
        Long id,
        String username,
        String displayName,
        String department,
        boolean admin,
        boolean disabled,
        boolean mustChangePassword) {

    static UserResponse from(UserEntity user) {
        return new UserResponse(
                user.id(),
                user.username(),
                user.displayName(),
                user.department(),
                user.isAdmin(),
                user.isDisabled(),
                user.mustChangePassword());
    }
}
