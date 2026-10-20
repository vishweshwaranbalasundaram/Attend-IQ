package com.attendiq;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class StudentServiceTest {
    @Test
    void createStudentNormalizesFieldsBeforePersistence() throws SQLException {
        FakeAcademicRepository repository = new FakeAcademicRepository();
        StudentService service = new StudentService(repository);

        service.createStudent(" REC26CS002 ", " Example Student ", " student@example.edu ");

        assertEquals("REC26CS002", repository.registerNumber);
        assertEquals("Example Student", repository.fullName);
        assertEquals("student@example.edu", repository.email);
    }

    @Test
    void createStudentRejectsBlankRegisterNumber() {
        StudentService service = new StudentService(new FakeAcademicRepository());

        assertEquals("Register number is required.", assertThrows(IllegalArgumentException.class,
            () -> service.createStudent("  ", "Example Student", null)).getMessage());
    }

    @Test
    void createStudentRejectsMalformedEmail() {
        StudentService service = new StudentService(new FakeAcademicRepository());

        assertEquals("Enter a valid email address of at most 160 characters.",
            assertThrows(IllegalArgumentException.class,
                () -> service.createStudent("REC26CS002", "Example Student",
                    "not-an-email")).getMessage());
    }

    @Test
    void findStudentRejectsNonPositiveId() {
        StudentService service = new StudentService(new FakeAcademicRepository());

        assertEquals("Student ID must be positive.",
            assertThrows(IllegalArgumentException.class,
                () -> service.findStudent(0)).getMessage());
    }

    @Test
    void delegatesListSearchUpdateAndDeleteWithNormalizedFields() throws SQLException {
        FakeAcademicRepository repository = new FakeAcademicRepository();
        StudentService service = new StudentService(repository);

        assertEquals(List.of(), service.listStudents());
        assertEquals(List.of(), service.searchStudents("  REC26  "));
        service.updateStudent(2, " REC26CS003 ", " Updated Student ", " ");
        assertEquals("REC26CS003", repository.registerNumber);
        assertEquals("Updated Student", repository.fullName);
        assertNull(repository.email);
        assertTrue(service.deleteStudent(2));
    }

    @Test
    void rejectsMissingStudentAndInvalidFieldLengths() {
        StudentService service = new StudentService(new FakeAcademicRepository());

        assertEquals("Student 9 does not exist.", assertThrows(IllegalArgumentException.class,
                () -> service.findStudent(9)).getMessage());
        assertNotNull(assertThrows(IllegalArgumentException.class,
                () -> service.searchStudents(" ")));
        assertEquals("Register number must be at most 30 characters.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.createStudent("x".repeat(31), "Name", null)).getMessage());
        assertEquals("Enter a valid email address of at most 160 characters.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.createStudent("REG", "Name", "x".repeat(161))).getMessage());
        assertEquals("Student ID must be positive.", assertThrows(IllegalArgumentException.class,
                () -> service.deleteStudent(-1)).getMessage());
    }

    private static final class FakeAcademicRepository implements StudentRepository {
        private String registerNumber;
        private String fullName;
        private String email;

        @Override
        public List<Student> listStudents() {
            return List.of();
        }

        @Override
        public Optional<Student> findStudent(long studentId) {
            return Optional.empty();
        }

        @Override
        public List<Student> searchStudents(String searchTerm) {
            return List.of();
        }

        @Override
        public Student createStudent(String registerNumber, String fullName, String email) {
            this.registerNumber = registerNumber;
            this.fullName = fullName;
            this.email = email;
            return new Student(2, registerNumber, fullName, email);
        }

        @Override
        public Student updateStudent(long studentId, String registerNumber, String fullName,
                                     String email) {
            this.registerNumber = registerNumber;
            this.fullName = fullName;
            this.email = email;
            return new Student(studentId, registerNumber, fullName, email);
        }

        @Override
        public boolean deleteStudent(long studentId) {
            return true;
        }
    }
}
