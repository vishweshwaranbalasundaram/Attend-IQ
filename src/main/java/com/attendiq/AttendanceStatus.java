package com.attendiq;

public enum AttendanceStatus {
    PRESENT, ABSENT, ON_DUTY;

    public static AttendanceStatus fromMenuChoice(int choice) {
        return switch (choice) {
            case 1 -> PRESENT;
            case 2 -> ABSENT;
            case 3 -> ON_DUTY;
            default -> throw new IllegalArgumentException("Choose 1, 2, or 3.");
        };
    }
}
