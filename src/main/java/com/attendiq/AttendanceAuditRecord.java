package com.attendiq;

import java.time.LocalDateTime;

public record AttendanceAuditRecord(long auditId, long sessionId, long studentId, String eventType,
                                    AttendanceStatus previousStatus, AttendanceStatus newStatus,
                                    String reason, Long claimedFacultyId, LocalDateTime recordedAt) {}
