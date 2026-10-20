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

    @Test
    void rejectsInvalidMarkRequestsBeforeOpeningDatabaseConnection() {
        AttendanceService service = new AttendanceService(new AttendanceRepository());

        assertEquals("Session ID and student ID must be positive.", assertThrows(
                IllegalArgumentException.class,
                () -> service.markAttendance(0, 1, AttendanceStatus.PRESENT)).getMessage());
        assertEquals("Attendance status is required.", assertThrows(IllegalArgumentException.class,
                () -> service.markAttendance(1, 1, null)).getMessage());
    }

    @Test
    void rejectsInvalidListAndDateFilterRequestsBeforeOpeningDatabaseConnection() {
        AttendanceService service = new AttendanceService(new AttendanceRepository());

        assertEquals("Student ID must be positive.", assertThrows(IllegalArgumentException.class,
                () -> service.listByStudent(0)).getMessage());
        assertEquals("Session ID must be positive.", assertThrows(IllegalArgumentException.class,
                () -> service.listBySession(0)).getMessage());
        assertEquals("Subject ID must be positive.", assertThrows(IllegalArgumentException.class,
                () -> service.listBySubjectAndDates(0, null, null)).getMessage());
        assertEquals("Enter a valid date range with the end on or after the start.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.listBySubjectAndDates(1, null, null)).getMessage());
    }

    @Test
    void rejectsInvalidCorrectionAndDeletionRequestsBeforeOpeningDatabaseConnection() {
        AttendanceService service = new AttendanceService(new AttendanceRepository());

        assertEquals("Session, student, and faculty IDs must be positive.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.updateAttendanceStatus(0, 1, AttendanceStatus.PRESENT,
                                1, "reason")).getMessage());
        assertEquals("Attendance status is required.", assertThrows(IllegalArgumentException.class,
                () -> service.updateAttendanceStatus(1, 1, null, 1, "reason")).getMessage());
        assertEquals("A correction/deletion reason is required.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.updateAttendanceStatus(1, 1, AttendanceStatus.PRESENT,
                                1, " ")).getMessage());
        assertEquals("Reason must be at most 1000 characters.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.deleteAttendance(1, 1, 1, "x".repeat(1001))).getMessage());
        assertEquals("Session ID must be positive.", assertThrows(IllegalArgumentException.class,
                () -> service.auditHistory(0)).getMessage());
    }
}
