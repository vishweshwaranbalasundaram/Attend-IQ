package com.attendiq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AttendanceServiceValidationTest {
    @Test
    void rejectsInvalidReadIdsBeforeOpeningDatabaseConnection() {
        AttendanceService service = new AttendanceService(new AttendanceRepository());

        assertEquals("Session ID must be positive.", assertThrows(IllegalArgumentException.class,
                () -> service.getAttendance(0, 1)).getMessage());
        assertEquals("Student ID must be positive.", assertThrows(IllegalArgumentException.class,
                () -> service.getAttendance(1, 0)).getMessage());
    }
}