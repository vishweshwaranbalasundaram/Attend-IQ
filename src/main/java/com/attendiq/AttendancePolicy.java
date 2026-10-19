package com.attendiq;

public final class AttendancePolicy {
    private static final int DEFAULT_TARGET = 75;

    private AttendancePolicy() {}

    public static int targetPercentage() {
        return parseTarget(System.getenv("ATTENDIQ_ATTENDANCE_TARGET"));
    }

    static int parseTarget(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_TARGET;
        }
        try {
            int value = Integer.parseInt(configured.trim());
            if (value < 1 || value > 100) {
                throw new IllegalArgumentException("ATTENDIQ_ATTENDANCE_TARGET must be 1-100.");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("ATTENDIQ_ATTENDANCE_TARGET must be an integer from 1-100.");
        }
    }
}