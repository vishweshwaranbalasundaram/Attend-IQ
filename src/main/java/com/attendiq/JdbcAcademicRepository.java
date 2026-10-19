package com.attendiq;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcAcademicRepository implements StudentRepository {
    @Override
    public List<Student> listStudents() throws SQLException {
        String sql = "SELECT student_id, register_number, full_name, email "
                + "FROM students ORDER BY register_number";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            List<Student> students = new ArrayList<>();
            while (results.next()) {
                students.add(readStudent(results));
            }
            return students;
        }
    }

    @Override
    public Optional<Student> findStudent(long studentId) throws SQLException {
        String sql = "SELECT student_id, register_number, full_name, email "
                + "FROM students WHERE student_id = ?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(readStudent(results)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Student> searchStudents(String searchTerm) throws SQLException {
        String sql = "SELECT student_id, register_number, full_name, email FROM students "
                + "WHERE register_number LIKE ? OR full_name LIKE ? ORDER BY register_number";
        String pattern = searchTerm + "%";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, pattern);
            statement.setString(2, pattern);
            try (ResultSet results = statement.executeQuery()) {
                List<Student> students = new ArrayList<>();
                while (results.next()) {
                    students.add(readStudent(results));
                }
                return students;
            }
        }
    }

    @Override
    public Student createStudent(String registerNumber, String fullName, String email)
            throws SQLException {
        String sql = "INSERT INTO students (register_number, full_name, email) VALUES (?, ?, ?)";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, registerNumber);
            statement.setString(2, fullName);
            statement.setString(3, email);
            statement.executeUpdate();
            long studentId;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Student insert succeeded but returned no ID.");
                }
                studentId = keys.getLong(1);
            }
            return findStudent(connection, studentId).orElseThrow(
                    () -> new SQLException("Created student could not be retrieved."));
        }
    }

    @Override
    public Student updateStudent(long studentId, String registerNumber, String fullName,
                                 String email) throws SQLException {
        String sql = "UPDATE students SET register_number = ?, full_name = ?, email = ? "
                + "WHERE student_id = ?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, registerNumber);
            statement.setString(2, fullName);
            statement.setString(3, email);
            statement.setLong(4, studentId);
                statement.executeUpdate();
            return findStudent(connection, studentId).orElseThrow(
                    () -> new IllegalArgumentException("Student " + studentId + " does not exist."));
        }
    }

    @Override
    public boolean deleteStudent(long studentId) throws SQLException {
        String lockSql = "SELECT student_id FROM students WHERE student_id = ? FOR UPDATE";
        String referencesSql = "SELECT EXISTS(SELECT 1 FROM enrollments WHERE student_id = ?) "
            + "OR EXISTS(SELECT 1 FROM attendance_records WHERE student_id = ?) "
            + "OR EXISTS(SELECT 1 FROM attendance_audit_log WHERE student_id = ?)";
        String deleteSql = "DELETE FROM students WHERE student_id = ?";
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement lock = connection.prepareStatement(lockSql)) {
                lock.setLong(1, studentId);
                try (ResultSet results = lock.executeQuery()) {
                    if (!results.next()) {
                        connection.rollback();
                        return false;
                    }
                }
            }
            try {
                try (PreparedStatement references = connection.prepareStatement(referencesSql)) {
                    references.setLong(1, studentId);
                    references.setLong(2, studentId);
                    references.setLong(3, studentId);
                    try (ResultSet results = references.executeQuery()) {
                        if (results.next() && results.getBoolean(1)) {
                            throw new IllegalStateException(
                                    "Cannot delete a student with enrollments or attendance history.");
                        }
                    }
                }
                try (PreparedStatement delete = connection.prepareStatement(deleteSql)) {
                    delete.setLong(1, studentId);
                    boolean deleted = delete.executeUpdate() == 1;
                    connection.commit();
                    return deleted;
                }
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private Optional<Student> findStudent(Connection connection, long studentId)
            throws SQLException {
        String sql = "SELECT student_id, register_number, full_name, email "
                + "FROM students WHERE student_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(readStudent(results)) : Optional.empty();
            }
        }
    }

    private Student readStudent(ResultSet results) throws SQLException {
        return new Student(results.getLong("student_id"), results.getString("register_number"),
                results.getString("full_name"), results.getString("email"));
    }
}