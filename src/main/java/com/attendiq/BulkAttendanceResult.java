package com.attendiq;

import java.util.List;

public record BulkAttendanceResult(List<Long> savedStudentIds, List<Long> skippedStudentIds,
                                   List<String> failures) {
    public BulkAttendanceResult {
        savedStudentIds = List.copyOf(savedStudentIds);
        skippedStudentIds = List.copyOf(skippedStudentIds);
        failures = List.copyOf(failures);
    }
}