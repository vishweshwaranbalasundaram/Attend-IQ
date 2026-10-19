package com.attendiq;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class DashboardRepository {
    public DashboardSummary load(int targetPercentage) throws SQLException {
        String sql = "SELECT "
                + "(SELECT COUNT(*) FROM students) AS students, "
                + "(SELECT COUNT(*) FROM faculty) AS faculty, "
                + "(SELECT COUNT(*) FROM subjects) AS subjects, "
                + "(SELECT COUNT(*) FROM enrollments) AS enrollments, "
                + "(SELECT COUNT(*) FROM class_sessions WHERE class_date = CURRENT_DATE) AS sessions_today, "
                + "(SELECT COUNT(*) FROM attendance_records ar JOIN class_sessions cs "
                + "ON cs.session_id = ar.session_id JOIN enrollments e ON e.student_id = ar.student_id "
                + "AND e.subject_id = cs.subject_id AND cs.class_date >= DATE(e.enrolled_at) "
                + "WHERE cs.class_date = CURRENT_DATE "
                + "AND ar.status = 'PRESENT') AS present_today, "
                + "(SELECT COUNT(*) FROM attendance_records ar JOIN class_sessions cs "
                + "ON cs.session_id = ar.session_id JOIN enrollments e ON e.student_id = ar.student_id "
                + "AND e.subject_id = cs.subject_id AND cs.class_date >= DATE(e.enrolled_at) "
                + "WHERE cs.class_date = CURRENT_DATE "
                + "AND ar.status = 'ABSENT') AS absent_today, "
                + "(SELECT COUNT(*) FROM attendance_records ar JOIN class_sessions cs "
                + "ON cs.session_id = ar.session_id JOIN enrollments e ON e.student_id = ar.student_id "
                + "AND e.subject_id = cs.subject_id AND cs.class_date >= DATE(e.enrolled_at) "
                + "WHERE cs.class_date = CURRENT_DATE "
                + "AND ar.status = 'ON_DUTY') AS on_duty_today, "
                + "(SELECT COUNT(*) FROM enrollments e JOIN class_sessions cs "
                + "ON cs.subject_id = e.subject_id AND cs.class_date = CURRENT_DATE "
                + "AND cs.class_date >= DATE(e.enrolled_at)) AS expected_today, "
                + "(SELECT COUNT(*) FROM attendance_records ar JOIN class_sessions cs "
                + "ON cs.session_id = ar.session_id JOIN enrollments e ON e.student_id = ar.student_id "
                + "AND e.subject_id = cs.subject_id AND cs.class_date >= DATE(e.enrolled_at) "
                + "WHERE cs.class_date = CURRENT_DATE) AS marked_today";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("Dashboard query returned no summary row.");
            }
            long present = result.getLong("present_today");
            long absent = result.getLong("absent_today");
            long onDuty = result.getLong("on_duty_today");
            long unmarked = Math.max(0, result.getLong("expected_today")
                    - result.getLong("marked_today"));
            long shortage = countStudentsBelowTarget(connection, targetPercentage);
            return new DashboardSummary(result.getLong("students"), result.getLong("faculty"),
                    result.getLong("subjects"), result.getLong("enrollments"),
                    result.getLong("sessions_today"), present, absent, onDuty, unmarked,
                    shortage, targetPercentage, recentActivity(connection));
        }
    }

            private List<AttendanceAuditRecord> recentActivity(Connection connection) throws SQLException {
            String sql = "SELECT audit_id, session_id, student_id, event_type, previous_status, "
                + "new_status, reason, claimed_faculty_id, recorded_at "
                + "FROM attendance_audit_log ORDER BY audit_id DESC LIMIT 5";
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet results = statement.executeQuery()) {
                List<AttendanceAuditRecord> events = new java.util.ArrayList<>();
                while (results.next()) {
                String previous = results.getString("previous_status");
                String next = results.getString("new_status");
                long claimedId = results.getLong("claimed_faculty_id");
                Long facultyId = results.wasNull() ? null : claimedId;
                events.add(new AttendanceAuditRecord(results.getLong("audit_id"),
                    results.getLong("session_id"), results.getLong("student_id"),
                    results.getString("event_type"),
                    previous == null ? null : AttendanceStatus.valueOf(previous),
                    next == null ? null : AttendanceStatus.valueOf(next),
                    results.getString("reason"), facultyId,
                    results.getTimestamp("recorded_at").toLocalDateTime()));
                }
                return events;
            }
            }

    private long countStudentsBelowTarget(Connection connection, int targetPercentage)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM ("
                + "SELECT e.student_id, e.subject_id FROM enrollments e "
                + "JOIN class_sessions cs ON cs.subject_id = e.subject_id "
                + "AND cs.class_date >= DATE(e.enrolled_at) AND cs.class_date <= CURRENT_DATE "
                + "LEFT JOIN attendance_records ar ON ar.session_id = cs.session_id "
                + "AND ar.student_id = e.student_id "
                + "GROUP BY e.student_id, e.subject_id "
                + "HAVING COUNT(cs.session_id) > 0 AND "
                + "100.0 * SUM(CASE WHEN ar.status IN ('PRESENT', 'ON_DUTY') "
                + "THEN 1 ELSE 0 END) / COUNT(cs.session_id) < ?"
                + ") shortage";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, targetPercentage);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }
}