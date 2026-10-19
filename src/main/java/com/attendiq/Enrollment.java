package com.attendiq;

import java.time.LocalDateTime;

public record Enrollment(long studentId, long subjectId, LocalDateTime enrolledAt) {}