package com.attendiq;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AttendanceEligibilityTest {
    @Test
    void calculatesClassesNeededAndSafeBunksAtTheThreshold() {
        assertEquals(2, AttendanceCalculator.classesNeeded(7, 10, 75));
        assertEquals(2, AttendanceCalculator.safeBunksRemaining(9, 10, 75));
        assertEquals(0, AttendanceCalculator.safeBunksRemaining(8, 10, 75));
        assertEquals(0, AttendanceCalculator.safeBunksRemaining(0, 0, 75));
    }

    @Test
    void validatesAttendanceInputsForSafeBunkCalculation() {
        assertThrows(IllegalArgumentException.class, () -> AttendanceCalculator.safeBunksRemaining(3, 2, 75));
        assertThrows(IllegalArgumentException.class, () -> AttendanceCalculator.safeBunksRemaining(1, 2, 0));
    }
}
