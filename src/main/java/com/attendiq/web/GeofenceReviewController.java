package com.attendiq.web;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/staff/geofence-flags")
public class GeofenceReviewController {
    private final JdbcTemplate db;

    public GeofenceReviewController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal AccountPrincipal staff,
            @RequestParam(name = "status", defaultValue = "OPEN") String status) {
        String filter = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("OPEN", "REVIEWED", "ALL").contains(filter)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose OPEN, REVIEWED, or ALL geofence flags.");
        }
        return db.queryForList("""
                SELECT gf.flag_id id,gf.class_session_id classSessionId,st.student_id studentId,
                       st.full_name fullName,st.register_number registerNumber,s.subject_code courseCode,
                       s.subject_name courseTitle,cs.class_date classDate,cs.period_number period,
                       gf.flag_reason reason,gf.distance_meters distanceMeters,gf.accuracy_meters accuracyMeters,
                       gf.review_status status,gf.created_at createdAt
                FROM attendance_geofence_flags gf
                JOIN attendance_qr_sessions q ON q.qr_session_id=gf.qr_session_id
                JOIN class_sessions cs ON cs.session_id=gf.class_session_id
                JOIN subjects s ON s.subject_id=cs.subject_id
                JOIN students st ON st.student_id=gf.student_id
                WHERE s.faculty_id=? AND (?='ALL' OR gf.review_status=?)
                ORDER BY gf.created_at DESC
                LIMIT 100
                """, staff.accountId(), filter, filter);
    }

    @PutMapping("/{flagId}/review")
    @Transactional
    public Map<String, Object> review(@PathVariable("flagId") long flagId,
            @AuthenticationPrincipal AccountPrincipal staff) {
        if (flagId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid geofence flag is required.");
        }
        List<String> statuses = db.query("""
                SELECT gf.review_status
                FROM attendance_geofence_flags gf
                JOIN class_sessions cs ON cs.session_id=gf.class_session_id
                JOIN subjects s ON s.subject_id=cs.subject_id
                WHERE gf.flag_id=? AND s.faculty_id=?
                FOR UPDATE
                """, (rs, row) -> rs.getString(1), flagId, staff.accountId());
        if (statuses.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Geofence flag was not found for this staff account.");
        }
        if ("OPEN".equals(statuses.getFirst())) {
            db.update("""
                    UPDATE attendance_geofence_flags
                    SET review_status='REVIEWED',reviewed_by_faculty_id=?,reviewed_at=CURRENT_TIMESTAMP(6)
                    WHERE flag_id=? AND review_status='OPEN'
                    """, staff.accountId(), flagId);
        }
        return Map.of("id", flagId, "status", "REVIEWED");
    }
}
