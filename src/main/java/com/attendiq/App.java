package com.attendiq;

import java.sql.SQLException;
import java.util.Optional;
import java.util.Scanner;

public class App {
    private static final Scanner INPUT = new Scanner(System.in);
    private static final AttendanceService SERVICE =
            new AttendanceService(new AttendanceRepository());

    public static void main(String[] args) {
        System.out.println("==================================");
        System.out.println("        AttendIQ - Java MVP       ");
        System.out.println("==================================");
        System.out.println("Database URL: " + safeUrl());
        while (true) {
            System.out.println("""

                    1. Mark attendance
                    2. Retrieve attendance record
                    0. Exit
                    """);
            int choice = readInt("Choose: ");
            try {
                switch (choice) {
                    case 1 -> markAttendance();
                    case 2 -> retrieveAttendance();
                    case 0 -> {
                        System.out.println("AttendIQ closed.");
                        return;
                    }
                    default -> System.out.println("Invalid choice. Choose 0, 1, or 2.");
                }
            } catch (IllegalArgumentException e) {
                System.out.println("Validation error: " + e.getMessage());
            } catch (SQLException e) {
                if ("23000".equals(e.getSQLState())) {
                    System.out.println("Duplicate or constraint violation: this student may already have a mark for this session.");
                } else {
                    System.out.println("Database error: " + e.getMessage());
                }
            }
        }
    }

    private static void markAttendance() throws SQLException {
        long sessionId = readLong("Class session ID: ");
        long studentId = readLong("Student ID: ");
        System.out.println("1. PRESENT  2. ABSENT  3. ON_DUTY");
        AttendanceStatus status = AttendanceStatus.fromMenuChoice(readInt("Status: "));
        AttendanceRecord saved = SERVICE.markAttendance(sessionId, studentId, status);
        System.out.printf("Saved successfully. Attendance ID=%d, student=%s (%s), status=%s%n",
                saved.attendanceId(), saved.studentName(), saved.registerNumber(), saved.status());
    }

    private static void retrieveAttendance() throws SQLException {
        long sessionId = readLong("Class session ID: ");
        long studentId = readLong("Student ID: ");
        Optional<AttendanceRecord> result = SERVICE.getAttendance(sessionId, studentId);
        if (result.isEmpty()) {
            System.out.println("No attendance record found for that session and student.");
            return;
        }
        AttendanceRecord record = result.get();
        System.out.printf("Attendance ID: %d%nSession ID: %d%nStudent: %s (%s)%nStatus: %s%nMarked at: %s%n",
                record.attendanceId(), record.sessionId(), record.studentName(),
                record.registerNumber(), record.status(), record.markedAt());
    }

    private static int readInt(String prompt) {
        return Math.toIntExact(readLong(prompt));
    }

    private static long readLong(String prompt) {
        System.out.print(prompt);
        String value = INPUT.nextLine().trim();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter a valid whole number.");
        }
    }

    private static String safeUrl() {
        String url = System.getenv("ATTENDIQ_DB_URL");
        return url == null ? "jdbc:mysql://localhost:3306/attendiq_java" : url;
    }
}
