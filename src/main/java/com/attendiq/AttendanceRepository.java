package com.attendiq;

import java.sql.*;
import java.util.Optional;

public class AttendanceRepository {

    public boolean isStudentEnrolled(Connection connection, long studentId, long subjectId)
            throws SQLException {
        String sql = """
                SELECT 1 FROM enrollments
                WHERE student_id = ? AND subject_id = ?
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
        String sql = "SELECT subject_id FROM class_sessions WHERE session_id = ?";
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
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        }
        throw new SQLException("Insert succeeded but no attendance ID was returned.");
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
                Timestamp timestamp = rs.getTimestamp("marked_at");
                return Optional.of(new AttendanceRecord(
                        rs.getLong("attendance_id"),
                        rs.getLong("session_id"),
                        rs.getLong("student_id"),
                        rs.getString("register_number"),
                        rs.getString("full_name"),
                        AttendanceStatus.valueOf(rs.getString("status")),
                        timestamp.toLocalDateTime()));
            }
        }
    }
}
