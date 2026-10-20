package com.attendiq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class FacultyAuthServiceIntegrationTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "ATTENDIQ_RUN_MYSQL_IT", matches = "true")
    void authenticatesAuditsAndLocksFacultyInDedicatedMysqlDatabase() throws Exception {
        try (Connection connection = Database.getConnection()) {
            assertTrue(connection.getCatalog().endsWith("_test"),
                    "Integration tests require a database name ending in _test.");
        }

        JdbcAcademicCatalogRepository catalog = new JdbcAcademicCatalogRepository();
        FacultyAuthService auth = new FacultyAuthService();
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Faculty configured = catalog.createFaculty("Auth Test " + token, token + "@auth.test");
        Faculty unconfigured = catalog.createFaculty("Null Hash " + token,
                "null" + token + "@auth.test");
        long unknownFacultyId = Long.MAX_VALUE;
        char[] correctPassword = "correct-horse-battery".toCharArray();

        try {
            auth.setPassword(configured.facultyId(), correctPassword);
            assertTrue(auth.hasPassword(configured.facultyId()));
            assertFalse(auth.hasPassword(unknownFacultyId));
            assertThrows(IllegalArgumentException.class,
                    () -> auth.setPassword(0, correctPassword));
            assertThrows(IllegalArgumentException.class,
                    () -> auth.setPassword(unknownFacultyId, correctPassword));
            assertTrue(auth.authenticate(configured.facultyId(), correctPassword));
            assertFalse(auth.authenticate(configured.facultyId(), "wrong-password".toCharArray()));
            assertFalse(auth.authenticate(unknownFacultyId, correctPassword));
            assertFalse(auth.authenticate(unconfigured.facultyId(), correctPassword));

            for (int failure = 0; failure < 4; failure++) {
                assertFalse(auth.authenticate(configured.facultyId(), "wrong-password".toCharArray()));
            }
            assertFalse(auth.authenticate(configured.facultyId(), correctPassword),
                    "The account must remain locked even when the correct password is supplied.");
            assertEquals(5, failedAttempts(configured.facultyId()));
            assertTrue(lockedUntil(configured.facultyId()).isAfter(Instant.now()));
            assertEquals(1, countAudit(configured.facultyId(), "LOGIN_SUCCESS"));
            assertEquals(5, countAudit(configured.facultyId(), "LOGIN_FAILURE"));
            assertEquals(1, countAudit(configured.facultyId(), "LOGIN_LOCKED"));
            assertEquals(1, countAudit(unknownFacultyId, "LOGIN_FAILURE"));
            assertEquals(1, countAudit(unconfigured.facultyId(), "LOGIN_FAILURE"));
            assertFalse(storedHash(configured.facultyId()).contains("correct-horse-battery"));
        } finally {
            PasswordHasher.wipe(correctPassword);
            cleanup(configured.facultyId(), unconfigured.facultyId(), unknownFacultyId);
        }
    }

    private int failedAttempts(long facultyId) throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT failed_attempts FROM login_attempts WHERE faculty_id = ?")) {
            statement.setLong(1, facultyId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }

    private Instant lockedUntil(long facultyId) throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT locked_until FROM login_attempts WHERE faculty_id = ?")) {
            statement.setLong(1, facultyId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                Timestamp value = rows.getTimestamp(1);
                assertTrue(value != null);
                return value.toInstant();
            }
        }
    }

    private int countAudit(long facultyId, String eventType) throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM faculty_auth_audit_log "
                             + "WHERE attempted_faculty_id = ? AND event_type = ?")) {
            statement.setLong(1, facultyId);
            statement.setString(2, eventType);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private String storedHash(long facultyId) throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password_hash FROM faculty WHERE faculty_id = ?")) {
            statement.setLong(1, facultyId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getString(1);
            }
        }
    }

    private void cleanup(long configuredId, long unconfiguredId, long unknownId) throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement audit = connection.prepareStatement(
                     "DELETE FROM faculty_auth_audit_log WHERE attempted_faculty_id IN (?, ?, ?)")) {
            audit.setLong(1, configuredId);
            audit.setLong(2, unconfiguredId);
            audit.setLong(3, unknownId);
            audit.executeUpdate();
        }
        try (Connection connection = Database.getConnection();
             PreparedStatement faculty = connection.prepareStatement(
                     "DELETE FROM faculty WHERE faculty_id IN (?, ?)")) {
            faculty.setLong(1, configuredId);
            faculty.setLong(2, unconfiguredId);
            faculty.executeUpdate();
        }
    }
}
