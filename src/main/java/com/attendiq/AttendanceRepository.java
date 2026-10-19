package com.attendiq;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AttendanceRepository {

    public boolean isStudentEnrolled(Connection connection, long studentId, long subjectId)
            throws SQLException {
        String sql = """
                SELECT 1 FROM enrollments
                WHERE student_id = ? AND subject_id = ?
            FOR UPDATE
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setLong(2, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Optional<Long> findSessionSubject(Connection connection, long sessionId)
            throws SQLException {
        String sql = "SELECT subject_id FROM class_sessions WHERE session_id = ? FOR UPDATE";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getLong("subject_id")) : Optional.empty();
            }
        }
    }

    public long insert(Connection connection, long sessionId, long studentId,
                       AttendanceStatus status) throws SQLException {
        String sql = """
                INSERT INTO attendance_records (session_id, student_id, status)
                VALUES (?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, sessionId);
            ps.setLong(2, studentId);
            ps.setString(3, status.name());
            ps.executeUpdate();
            long attendanceId;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Insert succeeded but no attendance ID was returned.");
                }
                attendanceId = keys.getLong(1);
            }
            insertAudit(connection, sessionId, studentId, "MARKED", null, status, null, null);
            return attendanceId;
        }
    }

    public Optional<AttendanceRecord> findBySessionAndStudent(
            Connection connection, long sessionId, long studentId) throws SQLException {
        String sql = """
                SELECT a.attendance_id, a.session_id, a.student_id, s.register_number,
                       s.full_name, a.status, a.marked_at
                FROM attendance_records a
                JOIN students s ON s.student_id = a.student_id
                WHERE a.session_id = ? AND a.student_id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            ps.setLong(2, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(readAttendance(rs));
            }
        }
    }

    public List<AttendanceRecord> listByStudent(long studentId) throws SQLException {
        return listRecords("", "WHERE a.student_id = ? ORDER BY a.marked_at DESC",
                statement -> statement.setLong(1, studentId));
    }

    public List<AttendanceRecord> listBySession(long sessionId) throws SQLException {
        return listRecords("", "WHERE a.session_id = ? ORDER BY s.register_number",
                statement -> statement.setLong(1, sessionId));
    }

    public List<AttendanceRecord> listBySubjectAndDates(long subjectId, LocalDate from,
                                                         LocalDate to) throws SQLException {
        return listRecords("JOIN class_sessions cs ON cs.session_id = a.session_id",
                "WHERE cs.subject_id = ? AND cs.class_date BETWEEN ? AND ? "
                        + "ORDER BY cs.class_date, s.register_number", statement -> {
                    statement.setLong(1, subjectId);
                    statement.setDate(2, java.sql.Date.valueOf(from));
                    statement.setDate(3, java.sql.Date.valueOf(to));
                });
    }

    public void updateStatus(long sessionId, long studentId, AttendanceStatus status,
                     long facultyId, String reason) throws SQLException {
        inAuthorizedSessionTransaction(sessionId, facultyId, connection -> {
            AttendanceStatus previousStatus;
            try (PreparedStatement existing = connection.prepareStatement(
                "SELECT status FROM attendance_records "
                            + "WHERE session_id = ? AND student_id = ? FOR UPDATE")) {
                existing.setLong(1, sessionId);
                existing.setLong(2, studentId);
                try (ResultSet results = existing.executeQuery()) {
                    if (!results.next()) {
                        throw new IllegalArgumentException("Attendance record does not exist.");
                    }
                    previousStatus = AttendanceStatus.valueOf(results.getString("status"));
                }
            }
            String sql = "UPDATE attendance_records SET status = ? "
                    + "WHERE session_id = ? AND student_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, status.name());
                statement.setLong(2, sessionId);
                statement.setLong(3, studentId);
                statement.executeUpdate();
            }
            insertAudit(connection, sessionId, studentId, "CORRECTED", previousStatus,
                    status, reason, facultyId);
            return null;
        });
    }

    public boolean deleteAttendance(long sessionId, long studentId, long facultyId, String reason)
            throws SQLException {
        return inAuthorizedSessionTransaction(sessionId, facultyId, connection -> {
            AttendanceStatus previousStatus;
            try (PreparedStatement existing = connection.prepareStatement(
                    "SELECT status FROM attendance_records WHERE session_id = ? AND student_id = ? FOR UPDATE")) {
                existing.setLong(1, sessionId);
                existing.setLong(2, studentId);
                try (ResultSet results = existing.executeQuery()) {
                    if (!results.next()) {
                        throw new IllegalArgumentException("Attendance record does not exist.");
                    }
                    previousStatus = AttendanceStatus.valueOf(results.getString("status"));
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM attendance_records WHERE session_id = ? AND student_id = ?")) {
                statement.setLong(1, sessionId);
                statement.setLong(2, studentId);
                boolean deleted = statement.executeUpdate() == 1;
                if (!deleted) {
                    throw new IllegalArgumentException("Attendance record does not exist.");
                }
                insertAudit(connection, sessionId, studentId, "DELETED", previousStatus,
                        null, reason, facultyId);
                return true;
            }
        });
    }

    public List<AttendanceAuditRecord> auditHistory(long sessionId) throws SQLException {
        String sql = "SELECT audit_id, session_id, student_id, event_type, previous_status, "
                + "new_status, reason, claimed_faculty_id, recorded_at "
                + "FROM attendance_audit_log WHERE session_id = ? ORDER BY audit_id";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sessionId);
            try (ResultSet results = statement.executeQuery()) {
                List<AttendanceAuditRecord> history = new ArrayList<>();
                while (results.next()) {
                    history.add(readAudit(results));
                }
                return history;
            }
        }
    }

    private void insertAudit(Connection connection, long sessionId, long studentId,
                             String eventType, AttendanceStatus previousStatus,
                             AttendanceStatus newStatus, String reason, Long facultyId)
            throws SQLException {
        String sql = "INSERT INTO attendance_audit_log (session_id, student_id, event_type, "
                + "previous_status, new_status, reason, claimed_faculty_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sessionId);
            statement.setLong(2, studentId);
            statement.setString(3, eventType);
            statement.setString(4, previousStatus == null ? null : previousStatus.name());
            statement.setString(5, newStatus == null ? null : newStatus.name());
            statement.setString(6, reason == null || reason.isBlank() ? null : reason);
            if (facultyId == null) {
                statement.setNull(7, java.sql.Types.BIGINT);
            } else {
                statement.setLong(7, facultyId);
            }
            statement.executeUpdate();
        }
    }

    private AttendanceAuditRecord readAudit(ResultSet results) throws SQLException {
        String previous = results.getString("previous_status");
        String next = results.getString("new_status");
        long actor = results.getLong("claimed_faculty_id");
        Long claimedFaculty = results.wasNull() ? null : actor;
        return new AttendanceAuditRecord(results.getLong("audit_id"),
                results.getLong("session_id"), results.getLong("student_id"),
                results.getString("event_type"),
                previous == null ? null : AttendanceStatus.valueOf(previous),
                next == null ? null : AttendanceStatus.valueOf(next), results.getString("reason"),
                claimedFaculty, results.getTimestamp("recorded_at").toLocalDateTime());
    }

    private <T> T inAuthorizedSessionTransaction(long sessionId, long facultyId,
                                                   SqlWork<T> work) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String authorizationSql = "SELECT cs.session_id FROM class_sessions cs "
                        + "JOIN subjects s ON s.subject_id = cs.subject_id "
                        + "WHERE cs.session_id = ? AND s.faculty_id = ? FOR UPDATE";
                try (PreparedStatement authorization = connection.prepareStatement(authorizationSql)) {
                    authorization.setLong(1, sessionId);
                    authorization.setLong(2, facultyId);
                    try (ResultSet results = authorization.executeQuery()) {
                        if (!results.next()) {
                            throw new SecurityException(
                                    "Faculty is not assigned to this class session.");
                        }
                    }
                }
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private List<AttendanceRecord> listRecords(String extraJoin, String filter,
                                               StatementBinder binder) throws SQLException {
        String sql = "SELECT a.attendance_id, a.session_id, a.student_id, s.register_number, "
                + "s.full_name, a.status, a.marked_at FROM attendance_records a "
                + "JOIN students s ON s.student_id = a.student_id " + extraJoin + " " + filter;
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet results = statement.executeQuery()) {
                List<AttendanceRecord> records = new ArrayList<>();
                while (results.next()) {
                    records.add(readAttendance(results));
                }
                return records;
            }
        }
    }

    private AttendanceRecord readAttendance(ResultSet results) throws SQLException {
        Timestamp timestamp = results.getTimestamp("marked_at");
        return new AttendanceRecord(results.getLong("attendance_id"),
                results.getLong("session_id"), results.getLong("student_id"),
                results.getString("register_number"), results.getString("full_name"),
                AttendanceStatus.valueOf(results.getString("status")),
                timestamp.toLocalDateTime());
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }
}
