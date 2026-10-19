package com.attendiq;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ClassSession(long sessionId, long subjectId, LocalDate classDate,
                           int periodNumber, LocalDateTime createdAt) {}