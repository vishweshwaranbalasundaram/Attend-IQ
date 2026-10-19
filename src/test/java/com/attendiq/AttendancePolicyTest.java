package com.attendiq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AttendancePolicyTest {
    @Test
    void defaultsToSeventyFiveAndAcceptsConfiguredThreshold() {
        assertEquals(75, AttendancePolicy.parseTarget(null));
        assertEquals(90, AttendancePolicy.parseTarget(" 90 "));
    }

    @Test
    void rejectsInvalidThresholdConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> AttendancePolicy.parseTarget("abc"));
        assertThrows(IllegalArgumentException.class, () -> AttendancePolicy.parseTarget("0"));
        assertThrows(IllegalArgumentException.class, () -> AttendancePolicy.parseTarget("101"));
    }
}