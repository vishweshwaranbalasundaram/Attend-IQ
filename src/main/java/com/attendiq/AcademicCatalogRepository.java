package com.attendiq;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AcademicCatalogRepository {
    List<Faculty> listFaculty() throws SQLException;

        Optional<Faculty> findFaculty(long facultyId) throws SQLException;

    Faculty createFaculty(String fullName, String email) throws SQLException;

    Faculty updateFaculty(long facultyId, String fullName, String email) throws SQLException;

    boolean deleteFaculty(long facultyId) throws SQLException;

    List<Subject> listSubjects() throws SQLException;

        Optional<Subject> findSubject(long subjectId) throws SQLException;

    List<Subject> subjectsForFaculty(long facultyId) throws SQLException;

    Subject createSubject(String subjectCode, String subjectName, long facultyId)
            throws SQLException;

    Subject updateSubject(long subjectId, String subjectCode, String subjectName, long facultyId)
            throws SQLException;

    boolean deleteSubject(long subjectId) throws SQLException;

    List<Enrollment> listEnrollments() throws SQLException;

        List<Subject> subjectsForStudent(long studentId) throws SQLException;

        List<Student> studentsForSubject(long subjectId) throws SQLException;

    Enrollment createEnrollment(long studentId, long subjectId) throws SQLException;

    boolean deleteEnrollment(long studentId, long subjectId) throws SQLException;

    List<ClassSession> listSessions() throws SQLException;

        Optional<ClassSession> findSession(long sessionId) throws SQLException;

            List<ClassSession> sessionsForSubjectAndDates(long subjectId, LocalDate from, LocalDate to)
                throws SQLException;

    ClassSession createSession(long subjectId, LocalDate classDate, int periodNumber)
            throws SQLException;

    ClassSession updateSession(long sessionId, long subjectId, LocalDate classDate,
                               int periodNumber) throws SQLException;

    boolean deleteSession(long sessionId) throws SQLException;

    AttendancePercentage attendancePercentage(long studentId, long subjectId)
            throws SQLException;

    List<SubjectAttendanceSummary> subjectAttendanceSummaries() throws SQLException;

    List<ShortageReport> shortageReports(int targetPercentage) throws SQLException;
}