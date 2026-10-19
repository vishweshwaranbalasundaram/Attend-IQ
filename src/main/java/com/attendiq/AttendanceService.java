package com.attendiq;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
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
        requirePositiveId(sessionId, "Session");
        requirePositiveId(studentId, "Student");
        try (Connection connection = Database.getConnection()) {
            return repository.findBySessionAndStudent(connection, sessionId, studentId);
        }
    }

    public List<AttendanceRecord> listByStudent(long studentId) throws SQLException {
        requirePositiveId(studentId, "Student");
        return repository.listByStudent(studentId);
    }

    public List<AttendanceRecord> listBySession(long sessionId) throws SQLException {
        requirePositiveId(sessionId, "Session");
        return repository.listBySession(sessionId);
    }

    public List<AttendanceRecord> listBySubjectAndDates(long subjectId, LocalDate from,
                                                        LocalDate to) throws SQLException {
        requirePositiveId(subjectId, "Subject");
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException(
                    "Enter a valid date range with the end on or after the start.");
        }
        return repository.listBySubjectAndDates(subjectId, from, to);
    }

    public void updateAttendanceStatus(long sessionId, long studentId, AttendanceStatus status,
                                       long facultyId, String reason) throws SQLException {
        requirePositive(sessionId, studentId, facultyId);
        if (status == null) {
            throw new IllegalArgumentException("Attendance status is required.");
        }
        repository.updateStatus(sessionId, studentId, status, facultyId,
                requiredReason(reason));
    }

    public boolean deleteAttendance(long sessionId, long studentId, long facultyId, String reason)
            throws SQLException {
        requirePositive(sessionId, studentId, facultyId);
        return repository.deleteAttendance(sessionId, studentId, facultyId,
                requiredReason(reason));
    }

    public List<AttendanceAuditRecord> auditHistory(long sessionId) throws SQLException {
        requirePositiveId(sessionId, "Session");
        return repository.auditHistory(sessionId);
    }

    private void requirePositive(long sessionId, long studentId, long facultyId) {
        if (sessionId <= 0 || studentId <= 0 || facultyId <= 0) {
            throw new IllegalArgumentException("Session, student, and faculty IDs must be positive.");
        }
    }

    private void requirePositiveId(long id, String label) {
        if (id <= 0) {
            throw new IllegalArgumentException(label + " ID must be positive.");
        }
    }

    private String requiredReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A correction/deletion reason is required.");
        }
        String normalized = reason.trim();
        if (normalized.length() > 1000) {
            throw new IllegalArgumentException("Reason must be at most 1000 characters.");
        }
        return normalized;
    }
}
