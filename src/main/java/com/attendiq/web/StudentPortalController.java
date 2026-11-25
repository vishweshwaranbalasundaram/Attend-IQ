package com.attendiq.web;

import com.attendiq.AttendanceCalculator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student")
public class StudentPortalController {
    private final JdbcTemplate db;
    public StudentPortalController(JdbcTemplate db){this.db=db;}

    @GetMapping("/dashboard")
    public Map<String,Object> dashboard(@AuthenticationPrincipal AccountPrincipal principal){
        Map<String,Object> profile=db.queryForMap("SELECT student_id id,register_number registerNumber,full_name fullName,email,program,semester,batch_label batch FROM students WHERE student_id=?",principal.accountId());
        profile.put("yearOfJoining",principal.yearOfJoining());
        profile.put("emailDepartment",principal.department());
        List<Map<String,Object>> courses=db.queryForList("SELECT s.subject_id courseId,s.subject_code courseCode,s.subject_name courseTitle,s.department,COUNT(cs.session_id) classesHeld,COALESCE(SUM(CASE WHEN ar.status IN ('PRESENT','LATE','ON_DUTY') THEN 1 ELSE 0 END),0) classesAttended FROM enrollments e JOIN subjects s ON s.subject_id=e.subject_id LEFT JOIN class_sessions cs ON cs.subject_id=s.subject_id AND cs.class_date>=DATE(e.enrolled_at) AND cs.class_date<=CURRENT_DATE LEFT JOIN attendance_records ar ON ar.session_id=cs.session_id AND ar.student_id=e.student_id WHERE e.student_id=? GROUP BY s.subject_id,s.subject_code,s.subject_name,s.department ORDER BY s.subject_code",principal.accountId());
        long totalHeld=0,totalAttended=0;
        for(Map<String,Object> course:courses){long held=((Number)course.get("classesHeld")).longValue(),attended=((Number)course.get("classesAttended")).longValue();totalHeld+=held;totalAttended+=attended;double percentage=AttendanceCalculator.percentage(attended,held);course.put("percentage",percentage);course.put("status",percentage>=75?"SAFE":percentage>=65?"WARNING":"CRITICAL");course.put("eligibility",eligibility(percentage));course.put("classesNeeded",AttendanceCalculator.classesNeeded(attended,held,75));course.put("safeBunksRemaining",safeBunks(attended,held));}
        double overall=AttendanceCalculator.percentage(totalAttended,totalHeld);
        return Map.of("student",profile,"overallPercentage",overall,"eligibility",eligibility(overall),"classesHeld",totalHeld,"classesAttended",totalAttended,"classesNeeded",AttendanceCalculator.classesNeeded(totalAttended,totalHeld,75),"safeBunksRemaining",safeBunks(totalAttended,totalHeld),"courses",courses,"threshold",75);
    }

    private static String eligibility(double percentage){return percentage>=75?"ELIGIBLE":percentage>=65?"CONDONATION":"DETAINED_RISK";}
    private static long safeBunks(long attended,long held){return AttendanceCalculator.safeBunksRemaining(attended,held,75);}

    @GetMapping("/history")
    public List<Map<String,Object>> history(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(name="courseId",required=false) Long courseId,@RequestParam(name="from",required=false) LocalDate from,
            @RequestParam(name="to",required=false) LocalDate to){
        if(from!=null&&to!=null&&to.isBefore(from))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"End date must be on or after start date.");
        StringBuilder sql=new StringBuilder("SELECT cs.class_date classDate,cs.period_number period,s.subject_id courseId,s.subject_code courseCode,s.subject_name courseTitle,ar.status,ar.marked_at markedAt FROM attendance_records ar JOIN class_sessions cs ON cs.session_id=ar.session_id JOIN subjects s ON s.subject_id=cs.subject_id WHERE ar.student_id=?");
        List<Object> args=new ArrayList<>();args.add(principal.accountId());
        if(courseId!=null){sql.append(" AND s.subject_id=?");args.add(courseId);}if(from!=null){sql.append(" AND cs.class_date>=?");args.add(from);}if(to!=null){sql.append(" AND cs.class_date<=?");args.add(to);}sql.append(" ORDER BY cs.class_date DESC,cs.period_number DESC");
        return db.queryForList(sql.toString(),args.toArray());
    }

    @GetMapping("/audit")
    public List<Map<String,Object>> audit(@AuthenticationPrincipal AccountPrincipal principal) {
        return db.queryForList("""
                SELECT cs.class_date classDate,cs.period_number period,s.subject_code courseCode,
                       'ATTENDANCE_CHANGE' eventType,al.previous_status oldStatus,al.new_status newStatus,
                       al.staff_email actorEmail,al.reason,al.recorded_at recordedAt
                FROM attendance_audit_log al
                JOIN class_sessions cs ON cs.session_id=al.session_id
                JOIN subjects s ON s.subject_id=cs.subject_id
                WHERE al.student_id=?
                UNION ALL
                SELECT cs.class_date,cs.period_number,s.subject_code,'ATTENDANCE_CHANGE',
                       al.old_status,al.new_status,al.actor_email,al.reason,al.`timestamp`
                FROM attendance_audit_logs al
                JOIN class_sessions cs ON cs.session_id=al.session_id
                JOIN subjects s ON s.subject_id=cs.subject_id
                WHERE al.student_id=?
                ORDER BY recordedAt DESC
                LIMIT 100
                """, principal.accountId(), principal.accountId());
    }
}
