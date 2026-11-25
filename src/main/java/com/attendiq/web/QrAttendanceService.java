package com.attendiq.web;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QrAttendanceService {
    private static final long TOKEN_ROTATION_SECONDS = 30;

    private final JdbcTemplate db;
    private final QrTokenSigner signer;
    private final int defaultSessionMinutes;
    private final int graceMinutes;
    private final int lateCutoffMinutes;
    private final double campusLatitude;
    private final double campusLongitude;
    private final int geofenceRadiusMeters;
    private final int maxAccuracyMeters;

    public QrAttendanceService(JdbcTemplate db, QrTokenSigner signer,
            @Value("${attendiq.attendance.session-minutes:10}") int defaultSessionMinutes,
            @Value("${attendiq.attendance.grace-minutes:5}") int graceMinutes,
            @Value("${attendiq.attendance.late-cutoff-minutes:10}") int lateCutoffMinutes,
            @Value("${attendiq.attendance.campus-lat}") double campusLatitude,
            @Value("${attendiq.attendance.campus-lng}") double campusLongitude,
            @Value("${attendiq.attendance.geofence-radius-meters:150}") int geofenceRadiusMeters,
            @Value("${attendiq.attendance.max-gps-accuracy-meters:50}") int maxAccuracyMeters) {
        this.db = db;
        this.signer = signer;
        this.defaultSessionMinutes = defaultSessionMinutes;
        this.graceMinutes = graceMinutes;
        this.lateCutoffMinutes = lateCutoffMinutes;
        this.campusLatitude = campusLatitude;
        this.campusLongitude = campusLongitude;
        this.geofenceRadiusMeters = geofenceRadiusMeters;
        this.maxAccuracyMeters = maxAccuracyMeters;
        GeoFence.validateCoordinates(campusLatitude, campusLongitude);
        if (defaultSessionMinutes < 1 || defaultSessionMinutes > 60 || graceMinutes < 0
                || lateCutoffMinutes < graceMinutes || geofenceRadiusMeters < 1 || maxAccuracyMeters < 1) {
            throw new IllegalArgumentException("Attendance session and geofence configuration is invalid.");
        }
    }

    @Transactional
    public Map<String, Object> start(long classSessionId, Integer requestedDuration, AccountPrincipal staff) {
        int duration = requestedDuration == null ? defaultSessionMinutes : requestedDuration;
        if (duration < 1 || duration > 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "QR session duration must be from 1 to 60 minutes.");
        }
        requireOwnedClassSession(classSessionId, staff.accountId());
        Integer existing = db.queryForObject("SELECT COUNT(*) FROM attendance_qr_sessions WHERE class_session_id=?",
                Integer.class, classSessionId);
        if (existing != null && existing > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A QR attendance session already exists for this class.");
        }
        Instant startedAt = Instant.now();
        Instant closesAt = startedAt.plus(Duration.ofMinutes(duration));
        try {
            db.update("""
                    INSERT INTO attendance_qr_sessions
                        (class_session_id,created_by_faculty_id,started_at,closes_at,grace_minutes,
                         late_cutoff_minutes,campus_latitude,campus_longitude,geofence_radius_meters,max_accuracy_meters)
                    VALUES (?,?,?,?,?,?,?,?,?,?)
                    """, classSessionId, staff.accountId(), Timestamp.from(startedAt), Timestamp.from(closesAt),
                    graceMinutes, lateCutoffMinutes, campusLatitude, campusLongitude, geofenceRadiusMeters, maxAccuracyMeters);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A QR attendance session already exists for this class.");
        }
        return issueForClass(classSessionId, staff.accountId());
    }

    @Transactional
    public Map<String, Object> current(long classSessionId, AccountPrincipal staff) {
        return issueForClass(classSessionId, staff.accountId());
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Map<String, Object> scan(ScanRequest request, AccountPrincipal student, String ipAddress) {
        if (request == null || request.token() == null || request.token().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A QR attendance token is required.");
        }
        GeoFence.validateCoordinates(request.latitude(), request.longitude());
        if (!Double.isFinite(request.accuracyMeters()) || request.accuracyMeters() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid GPS accuracy measurement is required.");
        }
        String tokenHash = signer.hash(request.token());
        List<Map<String, Object>> tokenRows = db.queryForList("""
                SELECT t.qr_session_id qrSessionId,t.token_bucket tokenBucket,t.expires_at expiresAt,
                       q.class_session_id classSessionId,q.started_at startedAt,q.closes_at closesAt,
                       q.closed_at closedAt,q.grace_minutes graceMinutes,q.late_cutoff_minutes lateCutoffMinutes,
                       q.campus_latitude campusLatitude,q.campus_longitude campusLongitude,
                       q.geofence_radius_meters radiusMeters,q.max_accuracy_meters maxAccuracyMeters
                FROM attendance_qr_tokens t JOIN attendance_qr_sessions q ON q.qr_session_id=t.qr_session_id
                WHERE t.token_hash=? AND t.expires_at>CURRENT_TIMESTAMP(6)
                """, tokenHash);
        if (tokenRows.isEmpty()) throw new ResponseStatusException(HttpStatus.GONE, "This QR code has expired or is invalid.");
        Map<String, Object> token = tokenRows.getFirst();
        long qrSessionId = number(token, "qrSessionId");
        long bucket = number(token, "tokenBucket");
        if (!signer.verifies(request.token(), qrSessionId, bucket)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The QR signature is invalid.");
        }
        Map<String, Object> session;
        try {
            session = db.queryForMap("""
                    SELECT qr_session_id qrSessionId,class_session_id classSessionId,started_at startedAt,
                           closes_at closesAt,closed_at closedAt,grace_minutes graceMinutes,
                           late_cutoff_minutes lateCutoffMinutes,campus_latitude campusLatitude,
                           campus_longitude campusLongitude,geofence_radius_meters radiusMeters,
                           max_accuracy_meters maxAccuracyMeters
                    FROM attendance_qr_sessions WHERE qr_session_id=? FOR UPDATE
                    """, qrSessionId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.GONE, "This attendance session is no longer available.");
        }
        Instant now = Instant.now();
        if (session.get("closedAt") != null || !instant(session.get("closesAt")).isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "This attendance session has closed.");
        }
        if (!instant(token.get("expiresAt")).isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "This QR code has expired.");
        }
        long classSessionId = number(session, "classSessionId");
        Integer enrolled = db.queryForObject("""
                SELECT COUNT(*) FROM enrollments WHERE student_id=? AND subject_id=
                    (SELECT subject_id FROM class_sessions WHERE session_id=?)
                """, Integer.class, student.accountId(), classSessionId);
        if (enrolled == null || enrolled == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not enrolled in this class.");
        }
        double allowedAccuracy = number(session, "maxAccuracyMeters");
        double distance = GeoFence.distanceMeters(request.latitude(), request.longitude(),
                decimal(session, "campusLatitude"), decimal(session, "campusLongitude"));
        double radius = number(session, "radiusMeters");
        for (GeofenceReviewPolicy.FlagReason reason : GeofenceReviewPolicy.softFlags(
                distance, request.accuracyMeters(), radius, allowedAccuracy)) {
            db.update("""
                    INSERT IGNORE INTO attendance_geofence_flags
                        (qr_session_id,class_session_id,student_id,flag_reason,distance_meters,accuracy_meters)
                    VALUES(?,?,?,?,?,?)
                    """, qrSessionId, classSessionId, student.accountId(), reason.name(), distance,
                    request.accuracyMeters());
        }
        if (request.accuracyMeters() > allowedAccuracy) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "GPS accuracy is too low. Move to a location with accuracy of " + (int) allowedAccuracy + " meters or better.");
        }
        if (distance > radius) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are outside the attendance geofence.");
        }
        List<String> existing = db.query("SELECT status FROM attendance_records WHERE session_id=? AND student_id=? FOR UPDATE",
                (rs, row) -> rs.getString(1), classSessionId, student.accountId());
        if (!existing.isEmpty()) {
            return Map.of("status", existing.getFirst(), "alreadyRecorded", true,
                    "distanceMeters", Math.round(distance));
        }
        long elapsedSeconds = Math.max(0, Duration.between(instant(session.get("startedAt")), now).getSeconds());
        long elapsedMinutes = elapsedSeconds / 60;
        String status = elapsedMinutes <= number(session, "graceMinutes") ? "PRESENT"
                : elapsedMinutes <= number(session, "lateCutoffMinutes") ? "LATE" : "ABSENT";
        db.update("INSERT INTO attendance_records(session_id,student_id,status) VALUES(?,?,?)",
                classSessionId, student.accountId(), status);
        db.update("""
                INSERT INTO attendance_audit_logs(session_id,student_id,actor_email,old_status,new_status,reason,ip_address)
                VALUES(?,?,?,NULL,?,'QR geofence check-in',?)
                """, classSessionId, student.accountId(), student.email(), status, safeIp(ipAddress));
        return Map.of("status", status, "alreadyRecorded", false, "distanceMeters", Math.round(distance));
    }

    @Scheduled(fixedDelayString = "${attendiq.attendance.close-sweep-ms:15000}")
    @Transactional
    public void closeExpiredSessions() {
        List<Long> dueSessions = db.query("""
                SELECT qr_session_id FROM attendance_qr_sessions
                WHERE closed_at IS NULL AND closes_at<=CURRENT_TIMESTAMP(6)
                ORDER BY closes_at LIMIT 100
                """, (rs, row) -> rs.getLong(1));
        for (Long qrSessionId : dueSessions) {
            List<Map<String, Object>> lockedRows = db.queryForList("""
                    SELECT qr_session_id,class_session_id,created_by_faculty_id
                    FROM attendance_qr_sessions WHERE qr_session_id=? AND closed_at IS NULL FOR UPDATE
                    """, qrSessionId);
            if (lockedRows.isEmpty()) continue;
            Map<String, Object> row = lockedRows.getFirst();
            long classSessionId = ((Number) row.get("class_session_id")).longValue();
            List<Long> missing = db.query("""
                    SELECT e.student_id FROM enrollments e
                    JOIN class_sessions cs ON cs.subject_id=e.subject_id
                    LEFT JOIN attendance_records ar ON ar.session_id=cs.session_id AND ar.student_id=e.student_id
                    WHERE cs.session_id=? AND ar.attendance_id IS NULL
                    FOR UPDATE
                    """, (rs, index) -> rs.getLong(1), classSessionId);
            for (Long studentId : missing) {
                db.update("INSERT INTO attendance_records(session_id,student_id,status) VALUES(?,?,'ABSENT')",
                        classSessionId, studentId);
                db.update("""
                        INSERT INTO attendance_audit_logs(session_id,student_id,actor_email,old_status,new_status,reason,ip_address)
                        SELECT ?,? ,f.email,NULL,'ABSENT','Automatic absence at QR session close',NULL
                        FROM faculty f WHERE f.faculty_id=?
                        """, classSessionId, studentId, row.get("created_by_faculty_id"));
            }
            db.update("UPDATE attendance_qr_sessions SET closed_at=CURRENT_TIMESTAMP(6) WHERE qr_session_id=?",
                    qrSessionId);
        }
    }

    private Map<String, Object> issueForClass(long classSessionId, long facultyId) {
        List<Map<String, Object>> rows = db.queryForList("""
                SELECT q.qr_session_id qrSessionId,q.closes_at closesAt,q.closed_at closedAt
                FROM attendance_qr_sessions q JOIN class_sessions cs ON cs.session_id=q.class_session_id
                JOIN subjects s ON s.subject_id=cs.subject_id
                WHERE q.class_session_id=? AND s.faculty_id=? FOR UPDATE
                """, classSessionId, facultyId);
        if (rows.isEmpty()) {
            Integer ownsSession = db.queryForObject("""
                    SELECT COUNT(*) FROM class_sessions cs JOIN subjects s ON s.subject_id=cs.subject_id
                    WHERE cs.session_id=? AND s.faculty_id=?
                    """, Integer.class, classSessionId, facultyId);
            if (ownsSession == null || ownsSession == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Class session was not found for this staff account.");
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Start a QR attendance session for this class first.");
        }
        Map<String, Object> row = rows.getFirst();
        Instant now = Instant.now();
        Instant closesAt = instant(row.get("closesAt"));
        if (row.get("closedAt") != null || !closesAt.isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "This attendance session has closed.");
        }
        long qrSessionId = number(row, "qrSessionId");
        long bucket = now.getEpochSecond() / TOKEN_ROTATION_SECONDS;
        String token = signer.issue(qrSessionId, bucket);
        Instant expiresAt = Instant.ofEpochSecond(Math.min((bucket + 1) * TOKEN_ROTATION_SECONDS, closesAt.getEpochSecond()));
        db.update("""
                INSERT INTO attendance_qr_tokens(qr_session_id,token_bucket,token_hash,expires_at)
                VALUES(?,?,?,?)
                ON DUPLICATE KEY UPDATE token_hash=VALUES(token_hash),expires_at=VALUES(expires_at)
                """, qrSessionId, bucket, signer.hash(token), Timestamp.from(expiresAt));
        return Map.of("token", token, "expiresAt", expiresAt.toString(), "rotationSeconds", TOKEN_ROTATION_SECONDS,
                "closesAt", closesAt.toString());
    }

    private void requireOwnedClassSession(long classSessionId, long facultyId) {
        Integer count = db.queryForObject("""
                SELECT COUNT(*) FROM class_sessions cs JOIN subjects s ON s.subject_id=cs.subject_id
                WHERE cs.session_id=? AND s.faculty_id=?
                """, Integer.class, classSessionId, facultyId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Class session was not found for this staff account.");
        }
    }

    private static long number(Map<String, Object> row, String name) {
        return ((Number) row.get(name)).longValue();
    }

    private static double decimal(Map<String, Object> row, String name) {
        return ((Number) row.get(name)).doubleValue();
    }

    private static Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof LocalDateTime dateTime) return dateTime.toInstant(ZoneOffset.UTC);
        throw new IllegalStateException("Unsupported JDBC timestamp value: "
                + (value == null ? "null" : value.getClass().getName()));
    }

    private static String safeIp(String ip) {
        return ip == null || ip.isBlank() ? null : ip.substring(0, Math.min(ip.length(), 45));
    }

    public record ScanRequest(String token, double latitude, double longitude, double accuracyMeters) {}
}
