package com.attendiq;

public enum AttendanceStatus {
    PRESENT, LATE, ABSENT, ON_DUTY;

    public static AttendanceStatus fromMenuChoice(int choice) {
        return switch (choice) {
            case 1 -> PRESENT;
            case 2 -> ABSENT;
            case 3 -> ON_DUTY;
            case 4 -> LATE;
            default -> throw new IllegalArgumentException("Choose a status from 1 to 4.");
        };
    }
}
