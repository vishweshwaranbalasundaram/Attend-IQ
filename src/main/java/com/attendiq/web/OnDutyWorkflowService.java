package com.attendiq.web;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OnDutyWorkflowService {
    private final JdbcTemplate db;

    public OnDutyWorkflowService(JdbcTemplate db) {
        this.db = db;
    }

    @Transactional
    public Map<String, Object> submit(SubmitRequest request, AccountPrincipal student) {
        validate(request);
        db.update("""
                INSERT INTO on_duty_requests
                    (student_id,request_type,start_date,start_period,end_date,end_period,reason,proof_metadata)
                VALUES(?,?,?,?,?,?,?,?)
                """, student.accountId(), request.type(), Date.valueOf(request.startDate()), request.startPeriod(),
                Date.valueOf(request.endDate()), request.endPeriod(), request.reason().trim(), request.proofMetadata());
        long id = db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return db.queryForMap("""
                SELECT request_id id,request_type type,start_date startDate,start_period startPeriod,
                       end_date endDate,end_period endPeriod,reason,proof_metadata proofMetadata,status,created_at createdAt
                FROM on_duty_requests WHERE request_id=?
                """, id);
    }

    public List<Map<String, Object>> ownRequests(AccountPrincipal student) {
        return db.queryForList("""
                SELECT request_id id,request_type type,start_date startDate,start_period startPeriod,
                       end_date endDate,end_period endPeriod,reason,proof_metadata proofMetadata,status,
                       created_at createdAt,reviewed_at reviewedAt
                FROM on_duty_requests WHERE student_id=? ORDER BY created_at DESC
                """, student.accountId());
    }

    public List<Map<String, Object>> staffQueue(AccountPrincipal staff) {
        return db.queryForList("""
                SELECT DISTINCT r.request_id id,r.student_id studentId,st.register_number registerNumber,
                       st.full_name fullName,r.request_type type,r.start_date startDate,r.start_period startPeriod,
                       r.end_date endDate,r.end_period endPeriod,r.reason,r.proof_metadata proofMetadata,
                       r.status,r.created_at createdAt
                FROM on_duty_requests r JOIN students st ON st.student_id=r.student_id
                JOIN enrollments e ON e.student_id=r.student_id
                JOIN subjects s ON s.subject_id=e.subject_id
                WHERE s.faculty_id=? AND r.status='PENDING' ORDER BY r.created_at
                """, staff.accountId());
    }

    @Transactional
    public Map<String, Object> review(long requestId, ReviewRequest review, AccountPrincipal staff, String ipAddress) {
        if (review == null || review.approved() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose whether to approve or reject this request.");
        }
        Map<String, Object> request;
        try {
            request = db.queryForMap("""
                    SELECT r.request_id,r.student_id,r.request_type,r.start_date,r.start_period,
                           r.end_date,r.end_period,r.reason,r.status
                    FROM on_duty_requests r JOIN enrollments e ON e.student_id=r.student_id
                    JOIN subjects s ON s.subject_id=e.subject_id
                    WHERE r.request_id=? AND s.faculty_id=? LIMIT 1 FOR UPDATE
                    """, requestId, staff.accountId());
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request was not found for this staff account.");
        }
        if (!"PENDING".equals(request.get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request has already been reviewed.");
        }
        String status = review.approved() ? "APPROVED" : "REJECTED";
        db.update("""
                UPDATE on_duty_requests SET status=?,reviewed_by_faculty_id=?,reviewed_at=CURRENT_TIMESTAMP(6)
                WHERE request_id=? AND status='PENDING'
                """, status, staff.accountId(), requestId);
        int attendanceUpdates = 0;
        if (review.approved()) {
            long studentId = ((Number) request.get("student_id")).longValue();
            List<Map<String, Object>> sessions = db.queryForList("""
                    SELECT cs.session_id sessionId,ar.status oldStatus
                    FROM class_sessions cs JOIN enrollments e ON e.subject_id=cs.subject_id AND e.student_id=?
                    JOIN subjects s ON s.subject_id=cs.subject_id AND s.faculty_id=?
                    LEFT JOIN attendance_records ar ON ar.session_id=cs.session_id AND ar.student_id=e.student_id
                    WHERE (cs.class_date>? OR (cs.class_date=? AND cs.period_number>=?))
                      AND (cs.class_date<? OR (cs.class_date=? AND cs.period_number<=?))
                    FOR UPDATE
                    """, studentId, staff.accountId(), request.get("start_date"), request.get("start_date"),
                    request.get("start_period"), request.get("end_date"), request.get("end_date"),
                    request.get("end_period"));
            for (Map<String, Object> session : sessions) {
                Object oldStatus = session.get("oldStatus");
                db.update("""
                        INSERT INTO attendance_records(session_id,student_id,status) VALUES(?,?,'ON_DUTY')
                        ON DUPLICATE KEY UPDATE status='ON_DUTY',marked_at=CURRENT_TIMESTAMP
                        """, session.get("sessionId"), studentId);
                db.update("""
                        INSERT INTO attendance_audit_logs(session_id,student_id,actor_email,old_status,new_status,reason,ip_address)
                        VALUES(?,?,?,?,?,?,?)
                        """, session.get("sessionId"), studentId, staff.email(), oldStatus, "ON_DUTY",
                        "Approved " + request.get("request_type") + " request #" + requestId, safeIp(ipAddress));
                attendanceUpdates++;
            }
        }
        return Map.of("requestId", requestId, "status", status, "attendanceUpdated", attendanceUpdates);
    }

    private static void validate(SubmitRequest request) {
        if (request == null || request.type() == null
                || !List.of("ON_DUTY", "MEDICAL").contains(request.type())
                || request.startDate() == null || request.endDate() == null
                || request.startPeriod() < 1 || request.startPeriod() > 12
                || request.endPeriod() < 1 || request.endPeriod() > 12
                || request.reason() == null || request.reason().isBlank() || request.reason().length() > 1000
                || request.proofMetadata() != null && request.proofMetadata().length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide valid request type, date/period range, and reason.");
        }
        if (request.endDate().isBefore(request.startDate())
                || ChronoUnit.DAYS.between(request.startDate(), request.endDate()) > 30
                || request.endDate().equals(request.startDate()) && request.endPeriod() < request.startPeriod()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The request date and period range is invalid.");
        }
    }

    private static String safeIp(String ip) {
        return ip == null || ip.isBlank() ? null : ip.substring(0, Math.min(ip.length(), 45));
    }

    public record SubmitRequest(String type, LocalDate startDate, int startPeriod, LocalDate endDate,
            int endPeriod, String reason, String proofMetadata) {}

    public record ReviewRequest(Boolean approved, String reason) {}
}
