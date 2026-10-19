package com.attendiq;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Creates database connections using environment variables.
 * Never place real database passwords in source code.
 */
public final class Database {
    private Database() {}

    public static Connection getConnection() throws SQLException {
        String url = env("ATTENDIQ_DB_URL",
                "jdbc:mysql://localhost:3306/attendiq_java?useSSL=false&serverTimezone=UTC");
        String user = env("ATTENDIQ_DB_USER", "root");
        String password = env("ATTENDIQ_DB_PASSWORD", "");
        return DriverManager.getConnection(url, user, password);
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null ? fallback : value;
    }
}
