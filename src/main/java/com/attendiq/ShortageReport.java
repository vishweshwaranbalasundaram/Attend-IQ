package com.attendiq;

public record ShortageReport(long studentId, String registerNumber, String studentName,
                             long subjectId, String subjectCode, String subjectName,
                             long attendedSessions, long totalSessions, double percentage,
                             long sessionsNeeded) {}