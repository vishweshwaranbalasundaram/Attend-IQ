package com.attendiq;

public record SubjectAttendanceSummary(long subjectId, String subjectCode, String subjectName,
                                       long presentRecords, long absentRecords,
                                       long onDutyRecords, long unmarkedSessions,
                                       long expectedRecords, double percentage) {}