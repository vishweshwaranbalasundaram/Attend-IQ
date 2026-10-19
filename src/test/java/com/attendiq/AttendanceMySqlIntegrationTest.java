package com.attendiq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class AttendanceMySqlIntegrationTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "ATTENDIQ_RUN_MYSQL_IT", matches = "true")
    void persistsAttendanceAndEnforcesRulesInDedicatedMysqlDatabase() throws Exception {
        try (Connection connection = Database.getConnection()) {
            assertTrue(connection.getCatalog().endsWith("_test"),
                    "Integration tests require a database name ending in _test.");
        }

        JdbcAcademicRepository studentRepository = new JdbcAcademicRepository();
        JdbcAcademicCatalogRepository catalog = new JdbcAcademicCatalogRepository();
        AttendanceService attendance = new AttendanceService(new AttendanceRepository());
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        long facultyId = 0;
        long otherFacultyId = 0;
        long studentId = 0;
        long unenrolledStudentId = 0;
        long subjectId = 0;
        long sessionId = 0;

        try {
            Faculty faculty = catalog.createFaculty("Integration Faculty " + token,
                    token + "@faculty.test");
            facultyId = faculty.facultyId();
            otherFacultyId = catalog.createFaculty("Other Faculty " + token,
                    "other" + token + "@faculty.test").facultyId();
            long createdFacultyId = facultyId;
            Student student = studentRepository.createStudent("IT" + token,
                    "Integration Student " + token, token + "@student.test");
            studentId = student.studentId();
            unenrolledStudentId = studentRepository.createStudent("IU" + token,
                    "Unenrolled Student " + token, "un" + token + "@student.test").studentId();
            long fixtureStudentId = studentId;
            long fixtureUnenrolledStudentId = unenrolledStudentId;
            Subject subject = catalog.createSubject("IT" + token,
                    "Integration Subject " + token, facultyId);
            subjectId = subject.subjectId();
            long fixtureSubjectId = subjectId;
            catalog.createEnrollment(fixtureStudentId, fixtureSubjectId);
            assertTrue(catalog.subjectsForStudent(fixtureStudentId).stream()
                    .anyMatch(value -> value.subjectId() == fixtureSubjectId));
            assertTrue(catalog.studentsForSubject(fixtureSubjectId).stream()
                    .anyMatch(value -> value.studentId() == fixtureStudentId));
            assertThrows(IllegalStateException.class,
                    () -> studentRepository.deleteStudent(fixtureStudentId));
            ClassSession session = catalog.createSession(subjectId, LocalDate.now(), 1);
            sessionId = session.sessionId();
            assertEquals(1, studentRepository.searchStudents("Integration Student " + token).size());
            assertEquals(0.0, catalog.attendancePercentage(fixtureStudentId,
                    fixtureSubjectId).percentage());
            ClassSession updatedSession = catalog.updateSession(sessionId, subjectId,
                    LocalDate.now(), 2);
            assertEquals(2, updatedSession.periodNumber());
            sessionId = updatedSession.sessionId();
            long createdSessionId = sessionId;
            long createdStudentId = studentId;
            long createdUnenrolledStudentId = unenrolledStudentId;
            long createdOtherFacultyId = otherFacultyId;

            AttendanceRecord saved = attendance.markAttendance(createdSessionId, createdStudentId,
                    AttendanceStatus.PRESENT);
            assertEquals(createdStudentId, saved.studentId());
            assertEquals(1, attendance.auditHistory(createdSessionId).size());
            AttendanceService restartedService = new AttendanceService(new AttendanceRepository());
            assertEquals(AttendanceStatus.PRESENT,
                    restartedService.getAttendance(createdSessionId, createdStudentId)
                            .orElseThrow().status());

            assertEquals("23000", assertThrows(SQLException.class,
                    () -> attendance.markAttendance(createdSessionId, createdStudentId,
                            AttendanceStatus.PRESENT)).getSQLState());
            assertEquals(AttendanceStatus.PRESENT,
                    attendance.getAttendance(createdSessionId, createdStudentId).orElseThrow().status(),
                    "A rejected duplicate must not alter the committed attendance row.");
            assertThrows(IllegalStateException.class,
                    () -> catalog.deleteEnrollment(fixtureStudentId, fixtureSubjectId));
            assertThrows(IllegalStateException.class,
                    () -> catalog.deleteSession(createdSessionId));
            assertEquals("Student " + createdUnenrolledStudentId + " is not enrolled in subject "
                            + subjectId + ".",
                    assertThrows(IllegalArgumentException.class, () -> attendance.markAttendance(
                            createdSessionId, createdUnenrolledStudentId,
                            AttendanceStatus.PRESENT)).getMessage());
            assertFalse(attendance.getAttendance(createdSessionId, createdUnenrolledStudentId)
                    .isPresent(), "Rejected enrollment must leave no attendance row.");
            assertEquals("Class session " + Long.MAX_VALUE + " does not exist.",
                    assertThrows(IllegalArgumentException.class, () -> attendance.markAttendance(
                            Long.MAX_VALUE, createdStudentId, AttendanceStatus.PRESENT)).getMessage());
            assertEquals("Faculty is not assigned to this class session.",
                    assertThrows(SecurityException.class, () -> attendance.updateAttendanceStatus(
                            createdSessionId, createdStudentId, AttendanceStatus.ABSENT,
                            createdOtherFacultyId, "unauthorized attempt")).getMessage());

            attendance.updateAttendanceStatus(createdSessionId, createdStudentId,
                    AttendanceStatus.ON_DUTY,
                    createdFacultyId, "Verified duty certificate");
            assertEquals(AttendanceStatus.ON_DUTY,
                    attendance.getAttendance(createdSessionId, createdStudentId).orElseThrow().status());
            assertEquals("Verified duty certificate", attendance.auditHistory(createdSessionId)
                    .get(1).reason());
            assertTrue(attendance.deleteAttendance(createdSessionId, createdStudentId, createdFacultyId,
                    "Duplicate session entered in error"));
            assertFalse(attendance.getAttendance(createdSessionId, createdStudentId).isPresent());
            assertEquals(3, attendance.auditHistory(createdSessionId).size());

            assertSingleConcurrentAttendance(attendance, createdSessionId, createdStudentId);
            assertThrows(IllegalStateException.class, () -> catalog.deleteSession(createdSessionId));
            assertThrows(IllegalStateException.class,
                    () -> studentRepository.deleteStudent(createdStudentId));
            assertThrows(IllegalStateException.class, () -> catalog.deleteFaculty(createdFacultyId));
            DashboardSummary dashboard = new DashboardRepository().load(75);
            assertTrue(dashboard.sessionsToday() >= 1);
            assertTrue(dashboard.presentToday() + dashboard.onDutyToday() >= 1);
            assertTrue(dashboard.recentActivity().size() >= 4);
        } finally {
            cleanup(sessionId, studentId, unenrolledStudentId, subjectId, facultyId, otherFacultyId);
        }
    }

        private void assertSingleConcurrentAttendance(AttendanceService attendance, long sessionId,
                                                                                                  long studentId) throws Exception {
                ExecutorService workers = Executors.newFixedThreadPool(2);
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                try {
                        Future<Boolean> first = workers.submit(() -> tryMark(attendance, sessionId,
                                        studentId, ready, start));
                        Future<Boolean> second = workers.submit(() -> tryMark(attendance, sessionId,
                                        studentId, ready, start));
                        ready.await();
                        start.countDown();
                        assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0),
                                        "The database unique key must allow only one concurrent insert.");
                } finally {
                        workers.shutdownNow();
                }
        }

        private boolean tryMark(AttendanceService attendance, long sessionId, long studentId,
                                                        CountDownLatch ready, CountDownLatch start) throws Exception {
                ready.countDown();
                start.await();
                try {
                        attendance.markAttendance(sessionId, studentId, AttendanceStatus.PRESENT);
                        return true;
                } catch (SQLException exception) {
                        if ("23000".equals(exception.getSQLState())) {
                                return false;
                        }
                        throw exception;
                }
        }

    private void cleanup(long sessionId, long studentId, long unenrolledStudentId,
                        long subjectId, long facultyId, long otherFacultyId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                delete(connection, "DELETE FROM attendance_records WHERE session_id = ?", sessionId);
                delete(connection, "DELETE FROM attendance_audit_log WHERE session_id = ?", sessionId);
                delete(connection, "DELETE FROM class_sessions WHERE session_id = ?", sessionId);
                delete(connection, "DELETE FROM enrollments WHERE student_id IN (?, ?)",
                        studentId, unenrolledStudentId);
                delete(connection, "DELETE FROM subjects WHERE subject_id = ?", subjectId);
                delete(connection, "DELETE FROM students WHERE student_id IN (?, ?)",
                        studentId, unenrolledStudentId);
                delete(connection, "DELETE FROM faculty WHERE faculty_id IN (?, ?)",
                        facultyId, otherFacultyId);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void delete(Connection connection, String sql, long... ids) throws SQLException {
        if (ids.length == 0 || ids[0] == 0) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < ids.length; index++) {
                statement.setLong(index + 1, ids[index]);
            }
            statement.executeUpdate();
        }
    }
}