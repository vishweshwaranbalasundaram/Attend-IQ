package com.attendiq;

import java.sql.SQLException;
import java.util.List;

public class StudentService {
    private final StudentRepository repository;

    public StudentService(StudentRepository repository) {
        this.repository = repository;
    }

    public List<Student> listStudents() throws SQLException {
        return repository.listStudents();
    }

    public Student findStudent(long studentId) throws SQLException {
        requirePositiveId(studentId);
        return repository.findStudent(studentId).orElseThrow(
                () -> new IllegalArgumentException("Student " + studentId + " does not exist."));
    }

    public List<Student> searchStudents(String searchTerm) throws SQLException {
        return repository.searchStudents(required(searchTerm, "Search term", 120));
    }

    public Student createStudent(String registerNumber, String fullName, String email)
            throws SQLException {
        return repository.createStudent(required(registerNumber, "Register number", 30),
                required(fullName, "Full name", 120), normalizeEmail(email));
    }

    public Student updateStudent(long studentId, String registerNumber, String fullName,
                                 String email) throws SQLException {
        requirePositiveId(studentId);
        return repository.updateStudent(studentId,
                required(registerNumber, "Register number", 30),
                required(fullName, "Full name", 120), normalizeEmail(email));
    }

    public boolean deleteStudent(long studentId) throws SQLException {
        requirePositiveId(studentId);
        return repository.deleteStudent(studentId);
    }

    private String required(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " must be at most " + maxLength + " characters.");
        }
        return normalized;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String normalized = email.trim();
        if (normalized.length() > 160 || !normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Enter a valid email address of at most 160 characters.");
        }
        return normalized;
    }

    private void requirePositiveId(long studentId) {
        if (studentId <= 0) {
            throw new IllegalArgumentException("Student ID must be positive.");
        }
    }
}