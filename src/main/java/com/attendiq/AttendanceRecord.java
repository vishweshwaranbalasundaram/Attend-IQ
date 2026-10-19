package com.attendiq;

import java.time.LocalDateTime;

public record AttendanceRecord(
        long attendanceId,
        long sessionId,
        long studentId,
        String registerNumber,
        String studentName,
        AttendanceStatus status,
        LocalDateTime markedAt) {}
