package com.attendiq;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class JdbcAcademicCatalogRepository implements AcademicCatalogRepository {
    @Override
    public List<Faculty> listFaculty() throws SQLException {
        String sql = "SELECT faculty_id, full_name, email FROM faculty ORDER BY full_name";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            List<Faculty> faculty = new ArrayList<>();
            while (results.next()) {
                faculty.add(readFaculty(results));
            }
            return faculty;
        }
    }

    @Override
    public java.util.Optional<Faculty> findFaculty(long facultyId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            return findFaculty(connection, facultyId);
        }
    }

    @Override
    public Faculty createFaculty(String fullName, String email) throws SQLException {
        String sql = "INSERT INTO faculty (full_name, email) VALUES (?, ?)";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, fullName);
            statement.setString(2, email);
            statement.executeUpdate();
                return readGenerated(statement, id -> findFaculty(connection, id).orElseThrow(
                    () -> new SQLException("Created faculty could not be retrieved.")));
        }
    }

    @Override
    public Faculty updateFaculty(long facultyId, String fullName, String email)
            throws SQLException {
        String sql = "UPDATE faculty SET full_name = ?, email = ? WHERE faculty_id = ?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, fullName);
            statement.setString(2, email);
            statement.setLong(3, facultyId);
                statement.executeUpdate();
            return findFaculty(connection, facultyId).orElseThrow(
                    () -> new IllegalArgumentException("Faculty " + facultyId + " does not exist."));
        }
    }

    @Override
    public boolean deleteFaculty(long facultyId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            if (exists(connection,
                    "SELECT EXISTS(SELECT 1 FROM attendance_audit_log WHERE claimed_faculty_id = ?)",
                    facultyId)) {
                throw new IllegalStateException(
                        "Cannot delete faculty referenced by attendance audit history.");
            }
        }
        return delete("DELETE FROM faculty WHERE faculty_id = ?", facultyId);
    }

    @Override
    public List<Subject> listSubjects() throws SQLException {
        String sql = "SELECT subject_id, subject_code, subject_name, faculty_id "
                + "FROM subjects ORDER BY subject_code";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            List<Subject> subjects = new ArrayList<>();
            while (results.next()) {
                subjects.add(readSubject(results));
            }
            return subjects;
        }
    }

    @Override
    public java.util.Optional<Subject> findSubject(long subjectId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            return findSubject(connection, subjectId);
        }
    }

    @Override
    public List<Subject> subjectsForFaculty(long facultyId) throws SQLException {
        String sql = "SELECT subject_id, subject_code, subject_name, faculty_id "
                + "FROM subjects WHERE faculty_id = ? ORDER BY subject_code";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, facultyId);
            try (ResultSet results = statement.executeQuery()) {
                List<Subject> subjects = new ArrayList<>();
                while (results.next()) {
                    subjects.add(readSubject(results));
                }
                return subjects;
            }
        }
    }

    @Override
    public Subject createSubject(String subjectCode, String subjectName, long facultyId)
            throws SQLException {
        String sql = "INSERT INTO subjects (subject_code, subject_name, faculty_id) "
                + "VALUES (?, ?, ?)";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, subjectCode);
            statement.setString(2, subjectName);
            statement.setLong(3, facultyId);
            statement.executeUpdate();
                return readGenerated(statement, id -> findSubject(connection, id).orElseThrow(
                    () -> new SQLException("Created subject could not be retrieved.")));
        }
    }

    @Override
    public Subject updateSubject(long subjectId, String subjectCode, String subjectName,
                                 long facultyId) throws SQLException {
        String sql = "UPDATE subjects SET subject_code = ?, subject_name = ?, faculty_id = ? "
                + "WHERE subject_id = ?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, subjectCode);
            statement.setString(2, subjectName);
            statement.setLong(3, facultyId);
            statement.setLong(4, subjectId);
                statement.executeUpdate();
            return findSubject(connection, subjectId).orElseThrow(
                    () -> new IllegalArgumentException("Subject " + subjectId + " does not exist."));
        }
    }

    @Override
    public boolean deleteSubject(long subjectId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!lockExists(connection, "SELECT subject_id FROM subjects WHERE subject_id = ? FOR UPDATE",
                        subjectId)) {
                    connection.rollback();
                    return false;
                }
                if (exists(connection, "SELECT EXISTS(SELECT 1 FROM enrollments WHERE subject_id = ?) "
                        + "OR EXISTS(SELECT 1 FROM class_sessions WHERE subject_id = ?)",
                        subjectId, subjectId)) {
                    throw new IllegalStateException(
                            "Cannot delete a subject with enrollments or class sessions.");
                }
                boolean deleted = delete(connection, "DELETE FROM subjects WHERE subject_id = ?",
                        subjectId);
                connection.commit();
                return deleted;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public List<Enrollment> listEnrollments() throws SQLException {
        String sql = "SELECT student_id, subject_id, enrolled_at FROM enrollments "
                + "ORDER BY subject_id, student_id";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            List<Enrollment> enrollments = new ArrayList<>();
            while (results.next()) {
                Timestamp enrolledAt = results.getTimestamp("enrolled_at");
                enrollments.add(new Enrollment(results.getLong("student_id"),
                        results.getLong("subject_id"), enrolledAt.toLocalDateTime()));
            }
            return enrollments;
        }
    }

    @Override
    public List<Subject> subjectsForStudent(long studentId) throws SQLException {
        String sql = "SELECT s.subject_id, s.subject_code, s.subject_name, s.faculty_id "
                + "FROM subjects s JOIN enrollments e ON e.subject_id = s.subject_id "
                + "WHERE e.student_id = ? ORDER BY s.subject_code";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            try (ResultSet results = statement.executeQuery()) {
                List<Subject> subjects = new ArrayList<>();
                while (results.next()) {
                    subjects.add(readSubject(results));
                }
                return subjects;
            }
        }
    }

    @Override
    public List<Student> studentsForSubject(long subjectId) throws SQLException {
        String sql = "SELECT s.student_id, s.register_number, s.full_name, s.email "
                + "FROM students s JOIN enrollments e ON e.student_id = s.student_id "
                + "WHERE e.subject_id = ? ORDER BY s.register_number";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, subjectId);
            try (ResultSet results = statement.executeQuery()) {
                List<Student> students = new ArrayList<>();
                while (results.next()) {
                    students.add(new Student(results.getLong("student_id"),
                            results.getString("register_number"), results.getString("full_name"),
                            results.getString("email")));
                }
                return students;
            }
        }
    }

    @Override
    public Enrollment createEnrollment(long studentId, long subjectId) throws SQLException {
        String sql = "INSERT INTO enrollments (student_id, subject_id) VALUES (?, ?)";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            statement.setLong(2, subjectId);
            statement.executeUpdate();
            return new Enrollment(studentId, subjectId, findEnrollmentTime(connection,
                    studentId, subjectId));
        }
    }

    @Override
    public boolean deleteEnrollment(long studentId, long subjectId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String lockSql = "SELECT student_id FROM enrollments "
                        + "WHERE student_id = ? AND subject_id = ? FOR UPDATE";
                try (PreparedStatement lock = connection.prepareStatement(lockSql)) {
                    lock.setLong(1, studentId);
                    lock.setLong(2, subjectId);
                    try (ResultSet results = lock.executeQuery()) {
                        if (!results.next()) {
                            connection.rollback();
                            return false;
                        }
                    }
                }

                String historySql = "SELECT EXISTS(SELECT 1 FROM attendance_records a "
                        + "JOIN class_sessions cs ON cs.session_id = a.session_id "
                    + "WHERE a.student_id = ? AND cs.subject_id = ?) "
                    + "OR EXISTS(SELECT 1 FROM attendance_audit_log al "
                    + "JOIN class_sessions cs ON cs.session_id = al.session_id "
                    + "WHERE al.student_id = ? AND cs.subject_id = ?)";
                try (PreparedStatement history = connection.prepareStatement(historySql)) {
                    history.setLong(1, studentId);
                    history.setLong(2, subjectId);
                    history.setLong(3, studentId);
                    history.setLong(4, subjectId);
                    try (ResultSet results = history.executeQuery()) {
                        if (results.next() && results.getBoolean(1)) {
                            throw new IllegalStateException(
                                    "Cannot remove an enrollment with attendance history.");
                        }
                    }
                }
                boolean deleted = delete(connection,
                        "DELETE FROM enrollments WHERE student_id = ? AND subject_id = ?",
                        studentId, subjectId);
                connection.commit();
                return deleted;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public List<ClassSession> listSessions() throws SQLException {
        String sql = "SELECT session_id, subject_id, class_date, period_number, created_at "
                + "FROM class_sessions ORDER BY class_date DESC, period_number";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            List<ClassSession> sessions = new ArrayList<>();
            while (results.next()) {
                sessions.add(readSession(results));
            }
            return sessions;
        }
    }

    @Override
    public java.util.Optional<ClassSession> findSession(long sessionId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            return findSession(connection, sessionId);
        }
    }

    @Override
    public List<ClassSession> sessionsForSubjectAndDates(long subjectId, LocalDate from,
                                                          LocalDate to) throws SQLException {
        String sql = "SELECT session_id, subject_id, class_date, period_number, created_at "
                + "FROM class_sessions WHERE subject_id = ? AND class_date BETWEEN ? AND ? "
                + "ORDER BY class_date, period_number";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, subjectId);
            statement.setDate(2, java.sql.Date.valueOf(from));
            statement.setDate(3, java.sql.Date.valueOf(to));
            try (ResultSet results = statement.executeQuery()) {
                List<ClassSession> sessions = new ArrayList<>();
                while (results.next()) {
                    sessions.add(readSession(results));
                }
                return sessions;
            }
        }
    }

    @Override
    public ClassSession createSession(long subjectId, LocalDate classDate, int periodNumber)
            throws SQLException {
        String sql = "INSERT INTO class_sessions (subject_id, class_date, period_number) "
                + "VALUES (?, ?, ?)";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, subjectId);
            statement.setDate(2, java.sql.Date.valueOf(classDate));
            statement.setInt(3, periodNumber);
            statement.executeUpdate();
                return readGenerated(statement, id -> findSession(connection, id).orElseThrow(
                    () -> new SQLException("Created session could not be retrieved.")));
        }
    }

    @Override
    public ClassSession updateSession(long sessionId, long subjectId, LocalDate classDate,
                                      int periodNumber) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!lockExists(connection,
                        "SELECT session_id FROM class_sessions WHERE session_id = ? FOR UPDATE",
                        sessionId)) {
                    throw new IllegalArgumentException("Class session " + sessionId + " does not exist.");
                }
                if (exists(connection,
                        "SELECT EXISTS(SELECT 1 FROM attendance_records WHERE session_id = ?)",
                        sessionId)) {
                    throw new IllegalStateException(
                            "Cannot modify a session after attendance is recorded.");
                }
                String sql = "UPDATE class_sessions SET subject_id = ?, class_date = ?, period_number = ? "
                        + "WHERE session_id = ?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, subjectId);
                    statement.setDate(2, java.sql.Date.valueOf(classDate));
                    statement.setInt(3, periodNumber);
                    statement.setLong(4, sessionId);
                    statement.executeUpdate();
                }
                ClassSession updated = findSession(connection, sessionId).orElseThrow(
                        () -> new SQLException("Updated session could not be retrieved."));
                connection.commit();
                return updated;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public boolean deleteSession(long sessionId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!lockExists(connection,
                        "SELECT session_id FROM class_sessions WHERE session_id = ? FOR UPDATE",
                        sessionId)) {
                    connection.rollback();
                    return false;
                }
                if (exists(connection,
                        "SELECT EXISTS(SELECT 1 FROM attendance_records WHERE session_id = ?)",
                        sessionId)) {
                    throw new IllegalStateException(
                            "Cannot delete a session with attendance history.");
                }
                if (exists(connection,
                    "SELECT EXISTS(SELECT 1 FROM attendance_audit_log WHERE session_id = ?)",
                    sessionId)) {
                    throw new IllegalStateException(
                        "Cannot delete a session with attendance audit history.");
                }
                boolean deleted = delete(connection,
                        "DELETE FROM class_sessions WHERE session_id = ?", sessionId);
                connection.commit();
                return deleted;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public AttendancePercentage attendancePercentage(long studentId, long subjectId)
            throws SQLException {
        try (Connection connection = Database.getConnection();
             PreparedStatement enrollment = connection.prepareStatement(
                     "SELECT 1 FROM enrollments WHERE student_id = ? AND subject_id = ?")) {
            enrollment.setLong(1, studentId);
            enrollment.setLong(2, subjectId);
            try (ResultSet results = enrollment.executeQuery()) {
                if (!results.next()) {
                    throw new IllegalArgumentException(
                            "Student is not enrolled in the requested subject.");
                }
            }
            return attendancePercentage(connection, studentId, subjectId);
        }
    }

    private AttendancePercentage attendancePercentage(Connection connection, long studentId,
                                                       long subjectId) throws SQLException {
        String sql = "SELECT COUNT(cs.session_id) AS total_sessions, "
            + "COALESCE(SUM(CASE WHEN ar.status = 'PRESENT' THEN 1 ELSE 0 END), 0) "
            + "AS present_sessions, "
            + "COALESCE(SUM(CASE WHEN ar.status = 'ABSENT' THEN 1 ELSE 0 END), 0) "
            + "AS absent_sessions, "
            + "COALESCE(SUM(CASE WHEN ar.status = 'ON_DUTY' THEN 1 ELSE 0 END), 0) "
            + "AS on_duty_sessions, "
            + "COALESCE(SUM(CASE WHEN ar.attendance_id IS NULL THEN 1 ELSE 0 END), 0) "
            + "AS unmarked_sessions "
                + "FROM class_sessions cs "
                + "JOIN enrollments e ON e.subject_id = cs.subject_id AND e.student_id = ? "
                + "LEFT JOIN attendance_records ar ON ar.session_id = cs.session_id "
            + "AND ar.student_id = e.student_id WHERE cs.subject_id = ? "
                + "AND cs.class_date >= DATE(e.enrolled_at) AND cs.class_date <= CURRENT_DATE";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            statement.setLong(2, subjectId);
            try (ResultSet results = statement.executeQuery()) {
                results.next();
                long total = results.getLong("total_sessions");
                long present = results.getLong("present_sessions");
                long absent = results.getLong("absent_sessions");
                long onDuty = results.getLong("on_duty_sessions");
                long unmarked = results.getLong("unmarked_sessions");
                long attended = present + onDuty;
                double percentage = AttendanceCalculator.percentage(attended, total);
                return new AttendancePercentage(studentId, subjectId, present, absent, onDuty,
                    unmarked, total, percentage);
            }
        }
    }

    @Override
    public List<SubjectAttendanceSummary> subjectAttendanceSummaries() throws SQLException {
        String sql = "SELECT s.subject_id, s.subject_code, s.subject_name, "
                + "COUNT(cs.session_id) AS expected_records, "
                + "COALESCE(SUM(CASE WHEN ar.status = 'PRESENT' THEN 1 ELSE 0 END), 0) "
                + "AS present_records, "
                + "COALESCE(SUM(CASE WHEN ar.status = 'ABSENT' THEN 1 ELSE 0 END), 0) "
                + "AS absent_records, "
                + "COALESCE(SUM(CASE WHEN ar.status = 'ON_DUTY' THEN 1 ELSE 0 END), 0) "
                + "AS on_duty_records, "
                + "COUNT(cs.session_id) - COUNT(ar.attendance_id) AS unmarked_sessions "
                + "FROM subjects s "
                + "LEFT JOIN enrollments e ON e.subject_id = s.subject_id "
                + "LEFT JOIN class_sessions cs ON cs.subject_id = s.subject_id "
                + "AND cs.class_date >= DATE(e.enrolled_at) AND cs.class_date <= CURRENT_DATE "
                + "LEFT JOIN attendance_records ar ON ar.session_id = cs.session_id "
                + "AND ar.student_id = e.student_id "
                + "GROUP BY s.subject_id, s.subject_code, s.subject_name ORDER BY s.subject_code";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            List<SubjectAttendanceSummary> summaries = new ArrayList<>();
            while (results.next()) {
                long expected = results.getLong("expected_records");
                long present = results.getLong("present_records");
                long onDuty = results.getLong("on_duty_records");
                summaries.add(new SubjectAttendanceSummary(results.getLong("subject_id"),
                        results.getString("subject_code"), results.getString("subject_name"),
                        present, results.getLong("absent_records"), onDuty,
                        results.getLong("unmarked_sessions"), expected,
                        AttendanceCalculator.percentage(present + onDuty, expected)));
            }
            return summaries;
        }
    }

    @Override
    public List<ShortageReport> shortageReports(int targetPercentage) throws SQLException {
        String sql = "SELECT st.student_id, st.register_number, st.full_name, s.subject_id, "
                + "s.subject_code, s.subject_name, COUNT(cs.session_id) AS total_sessions, "
                + "COALESCE(SUM(CASE WHEN ar.status IN ('PRESENT', 'ON_DUTY') "
                + "THEN 1 ELSE 0 END), 0) AS attended_sessions "
                + "FROM enrollments e "
                + "JOIN students st ON st.student_id = e.student_id "
                + "JOIN subjects s ON s.subject_id = e.subject_id "
                + "JOIN class_sessions cs ON cs.subject_id = e.subject_id "
                + "AND cs.class_date >= DATE(e.enrolled_at) AND cs.class_date <= CURRENT_DATE "
                + "LEFT JOIN attendance_records ar ON ar.session_id = cs.session_id "
                + "AND ar.student_id = e.student_id "
                + "GROUP BY st.student_id, st.register_number, st.full_name, "
                + "s.subject_id, s.subject_code, s.subject_name "
                + "HAVING 100.0 * SUM(CASE WHEN ar.status IN ('PRESENT', 'ON_DUTY') "
                + "THEN 1 ELSE 0 END) / COUNT(cs.session_id) < ? "
                + "ORDER BY s.subject_code, st.register_number";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, targetPercentage);
            try (ResultSet results = statement.executeQuery()) {
                List<ShortageReport> reports = new ArrayList<>();
                while (results.next()) {
                    long total = results.getLong("total_sessions");
                    long attended = results.getLong("attended_sessions");
                    reports.add(new ShortageReport(results.getLong("student_id"),
                            results.getString("register_number"), results.getString("full_name"),
                            results.getLong("subject_id"), results.getString("subject_code"),
                            results.getString("subject_name"), attended, total,
                            AttendanceCalculator.percentage(attended, total),
                            AttendanceCalculator.classesNeeded(attended, total, targetPercentage)));
                }
                return reports;
            }
        }
    }

    private boolean delete(String sql, long id) throws SQLException {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate() == 1;
        }
    }

    private boolean delete(Connection connection, String sql, long... ids) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < ids.length; index++) {
                statement.setLong(index + 1, ids[index]);
            }
            return statement.executeUpdate() == 1;
        }
    }

    private boolean exists(Connection connection, String sql, long... ids) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < ids.length; index++) {
                statement.setLong(index + 1, ids[index]);
            }
            try (ResultSet results = statement.executeQuery()) {
                return results.next() && results.getBoolean(1);
            }
        }
    }

    private boolean lockExists(Connection connection, String sql, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    private <T> T readGenerated(PreparedStatement statement, EntityReader<T> reader)
            throws SQLException {
        long generatedId;
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) {
                throw new SQLException("Insert succeeded but no generated ID was returned.");
            }
            generatedId = keys.getLong(1);
        }
        return reader.read(generatedId);
    }

    private java.util.Optional<Faculty> findFaculty(Connection connection, long id)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT faculty_id, full_name, email FROM faculty WHERE faculty_id = ?")) {
            statement.setLong(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? java.util.Optional.of(readFaculty(results))
                        : java.util.Optional.empty();
            }
        }
    }

    private java.util.Optional<Subject> findSubject(Connection connection, long id)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT subject_id, subject_code, subject_name, faculty_id "
                        + "FROM subjects WHERE subject_id = ?")) {
            statement.setLong(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? java.util.Optional.of(readSubject(results))
                        : java.util.Optional.empty();
            }
        }
    }

    private java.util.Optional<ClassSession> findSession(Connection connection, long id)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT session_id, subject_id, class_date, period_number, created_at "
                        + "FROM class_sessions WHERE session_id = ?")) {
            statement.setLong(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? java.util.Optional.of(readSession(results))
                        : java.util.Optional.empty();
            }
        }
    }

    private LocalDateTime findEnrollmentTime(Connection connection, long studentId,
                                             long subjectId) throws SQLException {
        String sql = "SELECT enrolled_at FROM enrollments WHERE student_id = ? AND subject_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            statement.setLong(2, subjectId);
            try (ResultSet results = statement.executeQuery()) {
                if (results.next()) {
                    return results.getTimestamp("enrolled_at").toLocalDateTime();
                }
            }
        }
        throw new SQLException("Created enrollment could not be retrieved.");
    }

    private Faculty readFaculty(ResultSet results) throws SQLException {
        return new Faculty(results.getLong("faculty_id"), results.getString("full_name"),
                results.getString("email"));
    }

    private Subject readSubject(ResultSet results) throws SQLException {
        return new Subject(results.getLong("subject_id"), results.getString("subject_code"),
                results.getString("subject_name"), results.getLong("faculty_id"));
    }

    private ClassSession readSession(ResultSet results) throws SQLException {
        return new ClassSession(results.getLong("session_id"), results.getLong("subject_id"),
                results.getDate("class_date").toLocalDate(), results.getInt("period_number"),
                results.getTimestamp("created_at").toLocalDateTime());
    }

    @FunctionalInterface
    private interface EntityReader<T> {
        T read(long id) throws SQLException;
    }
}