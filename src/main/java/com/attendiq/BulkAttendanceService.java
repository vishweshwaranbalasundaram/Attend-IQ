package com.attendiq;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BulkAttendanceService {
    private final AttendanceService attendanceService;

    public BulkAttendanceService(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    public BulkAttendanceResult mark(long sessionId, List<BulkAttendanceRequest> requests)
            throws SQLException {
        if (sessionId <= 0) {
            throw new IllegalArgumentException("Session ID must be positive.");
        }
        if (requests == null) {
            throw new IllegalArgumentException("Bulk attendance requests are required.");
        }
        Set<Long> seenStudents = new HashSet<>();
        List<Long> saved = new ArrayList<>();
        List<Long> skipped = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (BulkAttendanceRequest request : requests) {
            if (request == null || request.studentId() <= 0 || request.status() == null) {
                failures.add("Invalid student or status in bulk request.");
                continue;
            }
            if (!seenStudents.add(request.studentId())) {
                failures.add("Student " + request.studentId() + " appears more than once in the roster.");
                continue;
            }
            try {
                if (attendanceService.getAttendance(sessionId, request.studentId()).isPresent()) {
                    skipped.add(request.studentId());
                    continue;
                }
                attendanceService.markAttendance(sessionId, request.studentId(), request.status());
                saved.add(request.studentId());
            } catch (SQLException | IllegalArgumentException exception) {
                failures.add("Student " + request.studentId() + ": " + safeMessage(exception));
            }
        }
        return new BulkAttendanceResult(saved, skipped, failures);
    }

    private String safeMessage(Exception exception) {
        if (exception instanceof SQLException) {
            return "database operation failed";
        }
        String message = exception.getMessage();
        return message == null ? "request was rejected" : message;
    }
}