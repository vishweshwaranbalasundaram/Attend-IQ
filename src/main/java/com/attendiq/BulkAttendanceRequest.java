package com.attendiq;

public record BulkAttendanceRequest(long studentId, AttendanceStatus status) {}