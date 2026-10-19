package com.attendiq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class AttendanceCalculatorTest {
    @Test
    void calculatesPercentageAndHandlesNoSessions() {
        assertEquals(75.0, AttendanceCalculator.percentage(3, 4));
        assertEquals(0.0, AttendanceCalculator.percentage(0, 0));
    }

    @Test
    void calculatesClassesNeededToReachTarget() {
        assertEquals(6, AttendanceCalculator.classesNeeded(6, 10, 75));
        assertEquals(0, AttendanceCalculator.classesNeeded(8, 10, 75));
    }

    @Test
    void reportsImpossiblePerfectTargetWhenAttendanceIsNotPerfect() {
        assertEquals(-1, AttendanceCalculator.classesNeeded(9, 10, 100));
        assertEquals(0, AttendanceCalculator.classesNeeded(0, 0, 100));
    }

    @Test
    void rejectsInvalidAttendanceCounts() {
        assertEquals("Attendance counts must satisfy 0 <= attended <= total.",
            assertThrows(IllegalArgumentException.class,
                () -> AttendanceCalculator.percentage(4, 3)).getMessage());
    }
}