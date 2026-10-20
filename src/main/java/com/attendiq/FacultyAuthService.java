package com.attendiq;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

/** Faculty password storage, verification, lockout, and authentication audit logging. */
public final class FacultyAuthService {
    private final LoginAttemptPolicy lockoutPolicy;

    public FacultyAuthService() {
        this(Clock.systemUTC());
    }

    public FacultyAuthService(Clock clock) {
        this.lockoutPolicy = new LoginAttemptPolicy(clock);
    }

    public void setPassword(long facultyId, char[] password) throws SQLException {
        if (facultyId <= 0) {
            throw new IllegalArgumentException("Faculty ID must be positive.");
        }
        String hash = PasswordHasher.hash(password);
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE faculty SET password_hash = ? WHERE faculty_id = ?")) {
                statement.setString(1, hash);
                statement.setLong(2, facultyId);
                if (statement.executeUpdate() == 0) {
                    throw new IllegalArgumentException("Faculty " + facultyId + " was not found.");
                }
                try (PreparedStatement reset = connection.prepareStatement(
                        "UPDATE login_attempts SET failed_attempts = 0, locked_until = NULL "
                                + "WHERE faculty_id = ?")) {
                    reset.setLong(1, facultyId);
                    reset.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public boolean hasPassword(long facultyId) throws SQLException {
        if (facultyId <= 0) {
            return false;
        }
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password_hash FROM faculty WHERE faculty_id = ?")) {
            statement.setLong(1, facultyId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getString("password_hash") != null;
            }
        }
    }

    /**
     * Checks credentials and records every attempt. Five failures lock the account for five minutes.
     * Unknown and non-positive IDs are audited but do not create lockout rows.
     */
    public boolean authenticate(long facultyId, char[] password) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String hash = findPasswordHash(connection, facultyId);
                if (hash == null && !facultyExists(connection, facultyId)) {
                    logAttempt(connection, facultyId, "LOGIN_FAILURE");
                    connection.commit();
                    return false;
                }

                ensureAttemptRow(connection, facultyId);
                LoginAttemptPolicy.AttemptState attempt = readAttemptState(connection, facultyId);
                if (lockoutPolicy.isLocked(attempt.lockedUntil())) {
                    logAttempt(connection, facultyId, "LOGIN_LOCKED");
                    connection.commit();
                    return false;
                }

                if (PasswordHasher.verify(password, hash)) {
                    saveAttemptState(connection, facultyId, new LoginAttemptPolicy.AttemptState(0, null));
                    logAttempt(connection, facultyId, "LOGIN_SUCCESS");
                    connection.commit();
                    return true;
                }

                LoginAttemptPolicy.AttemptState failed = lockoutPolicy.recordFailure(
                        attempt.failedAttempts(), attempt.lockedUntil());
                saveAttemptState(connection, facultyId, failed);
                logAttempt(connection, facultyId, "LOGIN_FAILURE");
                connection.commit();
                return false;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private String findPasswordHash(Connection connection, long facultyId) throws SQLException {
        if (facultyId <= 0) {
            return null;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT password_hash FROM faculty WHERE faculty_id = ? FOR UPDATE")) {
            statement.setLong(1, facultyId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString("password_hash") : null;
            }
        }
    }

    private boolean facultyExists(Connection connection, long facultyId) throws SQLException {
        if (facultyId <= 0) {
            return false;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM faculty WHERE faculty_id = ?")) {
            statement.setLong(1, facultyId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private void ensureAttemptRow(Connection connection, long facultyId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO login_attempts (faculty_id) VALUES (?) "
                        + "ON DUPLICATE KEY UPDATE faculty_id = VALUES(faculty_id)")) {
            statement.setLong(1, facultyId);
            statement.executeUpdate();
        }
    }

    private LoginAttemptPolicy.AttemptState readAttemptState(Connection connection, long facultyId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT failed_attempts, locked_until FROM login_attempts "
                        + "WHERE faculty_id = ? FOR UPDATE")) {
            statement.setLong(1, facultyId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("Login-attempt state was not initialized.");
                }
                Timestamp timestamp = rows.getTimestamp("locked_until");
                return new LoginAttemptPolicy.AttemptState(rows.getInt("failed_attempts"),
                        timestamp == null ? null : timestamp.toInstant());
            }
        }
    }

    private void saveAttemptState(Connection connection, long facultyId,
                                  LoginAttemptPolicy.AttemptState state) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE login_attempts SET failed_attempts = ?, locked_until = ? "
                        + "WHERE faculty_id = ?")) {
            statement.setInt(1, state.failedAttempts());
            if (state.lockedUntil() == null) {
                statement.setTimestamp(2, null);
            } else {
                statement.setTimestamp(2, Timestamp.from(state.lockedUntil()));
            }
            statement.setLong(3, facultyId);
            statement.executeUpdate();
        }
    }

    private void logAttempt(Connection connection, long facultyId, String eventType)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO faculty_auth_audit_log (attempted_faculty_id, event_type) "
                        + "VALUES (?, ?)")) {
            statement.setLong(1, facultyId);
            statement.setString(2, eventType);
            statement.executeUpdate();
        }
    }
}
