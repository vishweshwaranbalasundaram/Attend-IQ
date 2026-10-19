package com.attendiq;

public record AttendancePercentage(long studentId, long subjectId, long presentSessions,
                                   long absentSessions, long onDutySessions,
                                   long unmarkedSessions, long totalSessions,
                                   double percentage) {
    public long attendedSessions() {
        return presentSessions + onDutySessions;
    }
}