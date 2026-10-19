package com.attendiq;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public class AttendanceService {
    private final AttendanceRepository repository;

    public AttendanceService(AttendanceRepository repository) {
        this.repository = repository;
    }

    public AttendanceRecord markAttendance(long sessionId, long studentId,
                                           AttendanceStatus status) throws SQLException {
        if (sessionId <= 0 || studentId <= 0) {
            throw new IllegalArgumentException("Session ID and student ID must be positive.");
        }
        if (status == null) {
            throw new IllegalArgumentException("Attendance status is required.");
        }

        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long subjectId = repository.findSessionSubject(connection, sessionId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Class session " + sessionId + " does not exist."));

                if (!repository.isStudentEnrolled(connection, studentId, subjectId)) {
                    throw new IllegalArgumentException(
                            "Student " + studentId + " is not enrolled in subject " + subjectId + ".");
                }

                long attendanceId = repository.insert(connection, sessionId, studentId, status);
                AttendanceRecord saved = repository.findBySessionAndStudent(
                                connection, sessionId, studentId)
                        .orElseThrow(() -> new SQLException("Saved record could not be retrieved."));
                connection.commit();
                return new AttendanceRecord(attendanceId, saved.sessionId(), saved.studentId(),
                        saved.registerNumber(), saved.studentName(), saved.status(), saved.markedAt());
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public Optional<AttendanceRecord> getAttendance(long sessionId, long studentId)
            throws SQLException {
        try (Connection connection = Database.getConnection()) {
            return repository.findBySessionAndStudent(connection, sessionId, studentId);
        }
    }
}
