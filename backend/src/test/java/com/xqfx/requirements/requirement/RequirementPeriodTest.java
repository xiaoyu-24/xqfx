package com.xqfx.requirements.requirement;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;

class RequirementPeriodTest {

    @Test
    void rejectsEndDateBeforeStartDate() {
        assertThrows(IllegalArgumentException.class, () -> {
            Class<?> periodType = Class.forName("com.xqfx.requirements.requirement.RequirementPeriod");
            var create = periodType.getMethod("of", LocalDate.class, LocalDate.class);

            try {
                create.invoke(null, LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 10));
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        });
    }
}
