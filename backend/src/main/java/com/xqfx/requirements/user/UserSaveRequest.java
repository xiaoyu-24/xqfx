package com.xqfx.requirements.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserSaveRequest(
        @NotBlank(message = "请输入账号")
        @Size(max = 50, message = "账号最长 50 位")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "账号只能包含字母、数字、点、下划线和短横线")
        String username,

        @NotBlank(message = "请输入姓名")
        @Size(max = 50, message = "姓名最长 50 位")
        String displayName,

        @Size(max = 50, message = "部门最长 50 位")
        String department,

        boolean admin) {
}
