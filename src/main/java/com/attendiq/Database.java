package com.attendiq;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Creates database connections using environment variables.
 * Never place real database passwords in source code.
 */
public final class Database {
    private static volatile LocalDateTime lastSuccessfulConnectionCheck;

    private Database() {}

    public static Connection getConnection() throws SQLException {
        String url = env("ATTENDIQ_DB_URL",
                "jdbc:mysql://localhost:3306/attendiq_java?useSSL=false&serverTimezone=UTC");
        String user = env("ATTENDIQ_DB_USER", "root");
        String password = env("ATTENDIQ_DB_PASSWORD", "");
        return DriverManager.getConnection(url, user, password);
    }

    public static boolean testConnection() throws SQLException {
        try (Connection connection = getConnection()) {
            boolean valid = connection.isValid(5);
            if (valid) {
                lastSuccessfulConnectionCheck = LocalDateTime.now();
            }
            return valid;
        }
    }

    public static Optional<LocalDateTime> lastSuccessfulConnectionCheck() {
        return Optional.ofNullable(lastSuccessfulConnectionCheck);
    }

    public static DatabaseDiagnostics diagnostics() throws SQLException {
        try (Connection connection = getConnection()) {
            var metadata = connection.getMetaData();
            return new DatabaseDiagnostics(connection.getCatalog(), metadata.getDatabaseProductName(),
                    metadata.getDatabaseProductVersion());
        }
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null ? fallback : value;
    }
}
