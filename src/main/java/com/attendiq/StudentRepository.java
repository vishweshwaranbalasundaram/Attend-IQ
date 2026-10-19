package com.attendiq;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface StudentRepository {
    List<Student> listStudents() throws SQLException;

    Optional<Student> findStudent(long studentId) throws SQLException;

        List<Student> searchStudents(String searchTerm) throws SQLException;

    Student createStudent(String registerNumber, String fullName, String email)
            throws SQLException;

    Student updateStudent(long studentId, String registerNumber, String fullName, String email)
            throws SQLException;

    boolean deleteStudent(long studentId) throws SQLException;
}