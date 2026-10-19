package com.attendiq;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class AcademicCatalogService {
    private final AcademicCatalogRepository repository;

    public AcademicCatalogService(AcademicCatalogRepository repository) {
        this.repository = repository;
    }

    public List<Faculty> listFaculty() throws SQLException {
        return repository.listFaculty();
    }

    public Faculty findFaculty(long facultyId) throws SQLException {
        return repository.findFaculty(id(facultyId)).orElseThrow(
                () -> new IllegalArgumentException("Faculty " + facultyId + " does not exist."));
    }

    public Faculty createFaculty(String fullName, String email) throws SQLException {
        return repository.createFaculty(required(fullName, "Full name", 120), email(email));
    }

    public Faculty updateFaculty(long facultyId, String fullName, String email)
            throws SQLException {
        return repository.updateFaculty(id(facultyId), required(fullName, "Full name", 120),
                email(email));
    }

    public boolean deleteFaculty(long facultyId) throws SQLException {
        return repository.deleteFaculty(id(facultyId));
    }

    public List<Subject> listSubjects() throws SQLException {
        return repository.listSubjects();
    }

    public Subject findSubject(long subjectId) throws SQLException {
        return repository.findSubject(id(subjectId)).orElseThrow(
                () -> new IllegalArgumentException("Subject " + subjectId + " does not exist."));
    }

    public List<Subject> subjectsForFaculty(long facultyId) throws SQLException {
        return repository.subjectsForFaculty(id(facultyId));
    }

    public Subject createSubject(String subjectCode, String subjectName, long facultyId)
            throws SQLException {
        return repository.createSubject(required(subjectCode, "Subject code", 20),
                required(subjectName, "Subject name", 120), id(facultyId));
    }

    public Subject updateSubject(long subjectId, String subjectCode, String subjectName,
                                 long facultyId) throws SQLException {
        return repository.updateSubject(id(subjectId), required(subjectCode, "Subject code", 20),
                required(subjectName, "Subject name", 120), id(facultyId));
    }

    public boolean deleteSubject(long subjectId) throws SQLException {
        return repository.deleteSubject(id(subjectId));
    }

    public List<Enrollment> listEnrollments() throws SQLException {
        return repository.listEnrollments();
    }

    public List<Subject> subjectsForStudent(long studentId) throws SQLException {
        return repository.subjectsForStudent(id(studentId));
    }

    public List<Student> studentsForSubject(long subjectId) throws SQLException {
        return repository.studentsForSubject(id(subjectId));
    }

    public Enrollment createEnrollment(long studentId, long subjectId) throws SQLException {
        return repository.createEnrollment(id(studentId), id(subjectId));
    }

    public boolean deleteEnrollment(long studentId, long subjectId) throws SQLException {
        return repository.deleteEnrollment(id(studentId), id(subjectId));
    }

    public List<ClassSession> listSessions() throws SQLException {
        return repository.listSessions();
    }

    public ClassSession findSession(long sessionId) throws SQLException {
        return repository.findSession(id(sessionId)).orElseThrow(
                () -> new IllegalArgumentException("Class session " + sessionId + " does not exist."));
    }

    public List<ClassSession> sessionsForSubjectAndDates(long subjectId, LocalDate from,
                                                          LocalDate to) throws SQLException {
        if (to == null || from == null || to.isBefore(from)) {
            throw new IllegalArgumentException(
                    "Enter a valid date range with the end on or after the start.");
        }
        return repository.sessionsForSubjectAndDates(id(subjectId), from, to);
    }

    public ClassSession createSession(long subjectId, LocalDate date, int periodNumber)
            throws SQLException {
        return repository.createSession(id(subjectId), date(date), period(periodNumber));
    }

    public ClassSession updateSession(long sessionId, long subjectId, LocalDate date,
                                      int periodNumber) throws SQLException {
        return repository.updateSession(id(sessionId), id(subjectId), date(date),
                period(periodNumber));
    }

    public boolean deleteSession(long sessionId) throws SQLException {
        return repository.deleteSession(id(sessionId));
    }

    public AttendancePercentage attendancePercentage(long studentId, long subjectId)
            throws SQLException {
        return repository.attendancePercentage(id(studentId), id(subjectId));
    }

    public List<SubjectAttendanceSummary> subjectAttendanceSummaries() throws SQLException {
        return repository.subjectAttendanceSummaries();
    }

    public List<ShortageReport> shortageReports(int targetPercentage) throws SQLException {
        if (targetPercentage < 1 || targetPercentage > 100) {
            throw new IllegalArgumentException("Target percentage must be between 1 and 100.");
        }
        return repository.shortageReports(targetPercentage);
    }

    private long id(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("IDs must be positive.");
        }
        return value;
    }

    private String required(String value, String label, int maximum) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        String normalized = value.trim();
        if (normalized.length() > maximum) {
            throw new IllegalArgumentException(label + " must be at most " + maximum + " characters.");
        }
        return normalized;
    }

    private String email(String value) {
        String normalized = required(value, "Email", 160);
        if (!normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        return normalized;
    }

    private LocalDate date(LocalDate value) {
        if (value == null) {
            throw new IllegalArgumentException("Class date is required.");
        }
        return value;
    }

    private int period(int value) {
        if (value < 1 || value > 12) {
            throw new IllegalArgumentException("Period must be between 1 and 12.");
        }
        return value;
    }
}