package com.attendiq;

import java.util.List;

public record DashboardSummary(long students, long faculty, long subjects, long enrollments,
                               long sessionsToday, long presentToday, long absentToday,
                               long onDutyToday, long unmarkedToday, long studentsBelowTarget,
                               int targetPercentage, List<AttendanceAuditRecord> recentActivity) {
    public DashboardSummary {
        recentActivity = List.copyOf(recentActivity);
    }
}