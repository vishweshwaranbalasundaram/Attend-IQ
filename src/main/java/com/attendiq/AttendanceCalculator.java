package com.attendiq;

public final class AttendanceCalculator {
    private AttendanceCalculator() {}

    public static double percentage(long attended, long total) {
        if (attended < 0 || total < 0 || attended > total) {
            throw new IllegalArgumentException("Attendance counts must satisfy 0 <= attended <= total.");
        }
        return total == 0 ? 0.0 : attended * 100.0 / total;
    }

    public static long classesNeeded(long attended, long total, int targetPercentage) {
        if (targetPercentage < 0 || targetPercentage > 100) {
            throw new IllegalArgumentException("Target percentage must be between 0 and 100.");
        }
        double current = percentage(attended, total);
        if (current >= targetPercentage) {
            return 0;
        }
        if (targetPercentage == 100) {
            return total == attended ? 0 : -1;
        }
        double remaining = targetPercentage * (double) total - 100.0 * attended;
        return (long) Math.ceil(remaining / (100 - targetPercentage));
    }
}