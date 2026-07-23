package com.xqfx.requirements.requirement;

import java.util.Set;

final class DepartmentPolicy {
    private static final Set<String> APPROVED = Set.of(
            "IT部", "FAE部", "总经办", "供应链部", "财务部",
            "产品部", "市场运营部", "业务部", "品质部", "人力资源部"
    );
    private DepartmentPolicy() {
    }

    static void validate(String department) {
        if (department == null || department.isBlank()) {
            return;
        }
        if (!APPROVED.contains(department)) {
            throw new IllegalArgumentException("部门必须从指定部门列表中选择");
        }
    }
}
