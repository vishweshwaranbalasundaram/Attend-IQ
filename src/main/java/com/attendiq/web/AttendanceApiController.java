package com.attendiq.web;

import com.attendiq.AttendanceCalculator;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/staff")
public class AttendanceApiController {
    private final JdbcTemplate db;
    private final InstitutionalIdentityPolicy identityPolicy;
    public AttendanceApiController(JdbcTemplate db,InstitutionalIdentityPolicy identityPolicy){this.db=db;this.identityPolicy=identityPolicy;}

    @GetMapping("/dashboard")
    public Map<String,Object> dashboard(@AuthenticationPrincipal AccountPrincipal staff){
        return db.queryForMap("SELECT (SELECT COUNT(*) FROM subjects WHERE faculty_id=?) courses,"
                +"(SELECT COUNT(DISTINCT e.student_id) FROM enrollments e JOIN subjects s ON s.subject_id=e.subject_id WHERE s.faculty_id=?) students,"
                +"(SELECT COUNT(*) FROM class_sessions cs JOIN subjects s ON s.subject_id=cs.subject_id WHERE s.faculty_id=?) sessions,"
                +"(SELECT COALESCE(ROUND(100.0*SUM(ar.status IN ('PRESENT','LATE','ON_DUTY'))/NULLIF(COUNT(ar.attendance_id),0),1),0) FROM attendance_records ar JOIN class_sessions cs ON cs.session_id=ar.session_id JOIN subjects s ON s.subject_id=cs.subject_id WHERE s.faculty_id=?) attendancePercentage",
                staff.accountId(),staff.accountId(),staff.accountId(),staff.accountId());
    }

    @GetMapping("/courses")
    public List<Map<String,Object>> courses(@AuthenticationPrincipal AccountPrincipal staff){
        return db.queryForList("SELECT subject_id id,subject_code code,subject_name name,department FROM subjects WHERE faculty_id=? ORDER BY subject_name",staff.accountId());
    }

    @PostMapping("/courses") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> createCourse(@AuthenticationPrincipal AccountPrincipal staff,@RequestBody CourseRequest request){
        if(blank(request.code())||blank(request.name()))bad("Course code and title are required.");
        db.update("INSERT INTO subjects(subject_code,subject_name,department,faculty_id) VALUES(?,?,?,?)",request.code().trim(),request.name().trim(),blank(request.department())?request.name().trim():request.department().trim(),staff.accountId());
        return db.queryForMap("SELECT subject_id id,subject_code code,subject_name name,department FROM subjects WHERE subject_id=LAST_INSERT_ID()");
    }

    @PutMapping("/courses/{id}")
    public Map<String,Object> updateCourse(@PathVariable("id") long id,@AuthenticationPrincipal AccountPrincipal staff,@RequestBody CourseRequest request){
        if(blank(request.code())||blank(request.name()))bad("Course code and title are required.");requireCourse(id,staff.accountId());
        db.update("UPDATE subjects SET subject_code=?,subject_name=?,department=? WHERE subject_id=? AND faculty_id=?",request.code().trim(),request.name().trim(),blank(request.department())?request.name().trim():request.department().trim(),id,staff.accountId());
        return db.queryForMap("SELECT subject_id id,subject_code code,subject_name name,department FROM subjects WHERE subject_id=?",id);
    }

    @DeleteMapping("/courses/{id}") @Transactional
    public void deleteCourse(@PathVariable("id") long id,@AuthenticationPrincipal AccountPrincipal staff){
        requireCourse(id,staff.accountId());Integer sessions=db.queryForObject("SELECT COUNT(*) FROM class_sessions WHERE subject_id=?",Integer.class,id);
        if(sessions!=null&&sessions>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"This course has session history and cannot be removed.");
        db.update("DELETE FROM enrollments WHERE subject_id=?",id);db.update("DELETE FROM subjects WHERE subject_id=?",id);
    }

    @GetMapping("/students")
    public List<Map<String,Object>> students(@AuthenticationPrincipal AccountPrincipal staff){return db.queryForList("SELECT DISTINCT st.student_id id,st.register_number registerNumber,st.full_name fullName,st.email,st.program,st.semester,st.batch_label batch FROM students st JOIN enrollments e ON e.student_id=st.student_id JOIN subjects s ON s.subject_id=e.subject_id WHERE s.faculty_id=? ORDER BY st.register_number",staff.accountId());}

    @PostMapping("/students") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> createStudent(@AuthenticationPrincipal AccountPrincipal staff,@RequestBody StudentRequest r){
        validateStudent(r);db.update("INSERT INTO students(register_number,full_name,email,program,semester,batch_label) VALUES(?,?,?,?,?,?)",r.registerNumber().trim(),r.fullName().trim(),r.email().trim().toLowerCase(),r.program().trim(),r.semester(),r.batch().trim());
        long id=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);syncEnrollments(staff,id,r.courseIds());
        return db.queryForMap("SELECT student_id id,register_number registerNumber,full_name fullName,email,program,semester,batch_label batch FROM students WHERE student_id=?",id);
    }

    @PutMapping("/students/{id}") @Transactional
    public Map<String,Object> updateStudent(@PathVariable("id") long id,@AuthenticationPrincipal AccountPrincipal staff,@RequestBody StudentRequest r){
        validateStudent(r);requireStudentScope(id,staff.accountId());int changed=db.update("UPDATE students SET register_number=?,full_name=?,email=?,program=?,semester=?,batch_label=? WHERE student_id=?",r.registerNumber().trim(),r.fullName().trim(),r.email().trim().toLowerCase(),r.program().trim(),r.semester(),r.batch().trim(),id);
        if(changed==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Student was not found.");syncEnrollments(staff,id,r.courseIds());return db.queryForMap("SELECT student_id id,register_number registerNumber,full_name fullName,email,program,semester,batch_label batch FROM students WHERE student_id=?",id);
    }

    @DeleteMapping("/students/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStudent(@PathVariable("id") long id,@AuthenticationPrincipal AccountPrincipal staff){requireStudentScope(id,staff.accountId());Integer history=db.queryForObject("SELECT COUNT(*) FROM attendance_audit_log WHERE student_id=?",Integer.class,id);if(history!=null&&history>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"This student has attendance audit history and cannot be removed.");if(db.update("DELETE FROM students WHERE student_id=?",id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Student was not found.");}

    @GetMapping("/students/{id}/courses")
    public List<Long> studentCourses(@PathVariable("id") long id,@AuthenticationPrincipal AccountPrincipal staff){requireStudentScope(id,staff.accountId());return db.query("SELECT e.subject_id FROM enrollments e JOIN subjects s ON s.subject_id=e.subject_id WHERE e.student_id=? AND s.faculty_id=?",(rs,n)->rs.getLong(1),id,staff.accountId());}

    @PutMapping("/students/{id}/courses") @Transactional
    public Map<String,Object> updateStudentCourses(@PathVariable("id") long id,@AuthenticationPrincipal AccountPrincipal staff,@RequestBody CourseEnrollmentRequest request){requireStudentScope(id,staff.accountId());syncEnrollments(staff,id,request.courseIds());return Map.of("enrolled",request.courseIds()==null?0:request.courseIds().size());}

    @GetMapping("/sessions")
    public List<Map<String,Object>> sessions(@AuthenticationPrincipal AccountPrincipal staff,@RequestParam(name="courseId") long courseId){requireCourse(courseId,staff.accountId());return db.queryForList("SELECT cs.session_id id,cs.subject_id courseId,s.subject_code courseCode,s.subject_name course,cs.class_date classDate,cs.period_number period FROM class_sessions cs JOIN subjects s ON s.subject_id=cs.subject_id WHERE s.subject_id=? AND s.faculty_id=? ORDER BY cs.class_date DESC,cs.period_number DESC",courseId,staff.accountId());}

    @PostMapping("/sessions") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> openSession(@AuthenticationPrincipal AccountPrincipal staff,@RequestBody SessionRequest r){
        if(r.courseId()<=0||r.date()==null||r.period()<1||r.period()>8)bad("Choose a course, date and period from 1 to 8.");requireCourse(r.courseId(),staff.accountId());
        db.update("INSERT IGNORE INTO class_sessions(subject_id,class_date,period_number) VALUES(?,?,?)",r.courseId(),r.date(),r.period());
        return db.queryForMap("SELECT session_id id,subject_id courseId,class_date classDate,period_number period FROM class_sessions WHERE subject_id=? AND class_date=? AND period_number=?",r.courseId(),r.date(),r.period());
    }

    @GetMapping("/sessions/{sessionId}/roster")
    public List<Map<String,Object>> roster(@PathVariable("sessionId") long sessionId,@AuthenticationPrincipal AccountPrincipal staff,@RequestParam(name="batch",required=false) String batch){
        requireOwnedSession(sessionId,staff.accountId());
        if(blank(batch))return db.queryForList("SELECT st.student_id id,st.register_number registerNumber,st.full_name fullName,st.email,st.program,st.semester,st.batch_label batch,ar.status,ar.marked_at markedAt FROM class_sessions cs JOIN enrollments e ON e.subject_id=cs.subject_id JOIN students st ON st.student_id=e.student_id LEFT JOIN attendance_records ar ON ar.session_id=cs.session_id AND ar.student_id=st.student_id WHERE cs.session_id=? ORDER BY st.register_number",sessionId);
        return db.queryForList("SELECT st.student_id id,st.register_number registerNumber,st.full_name fullName,st.email,st.program,st.semester,st.batch_label batch,ar.status,ar.marked_at markedAt FROM class_sessions cs JOIN enrollments e ON e.subject_id=cs.subject_id JOIN students st ON st.student_id=e.student_id LEFT JOIN attendance_records ar ON ar.session_id=cs.session_id AND ar.student_id=st.student_id WHERE cs.session_id=? AND st.batch_label=? ORDER BY st.register_number",sessionId,batch);
    }

    @GetMapping("/sessions/{sessionId}/audit")
    public List<Map<String,Object>> audit(@PathVariable("sessionId") long sessionId,@AuthenticationPrincipal AccountPrincipal staff){
        requireOwnedSession(sessionId,staff.accountId());
        return db.queryForList("""
                SELECT st.register_number registerNumber,st.full_name fullName,al.event_type eventType,
                       al.previous_status previousStatus,al.new_status newStatus,al.staff_email actorEmail,
                       al.reason,al.recorded_at recordedAt,NULL ipAddress
                FROM attendance_audit_log al JOIN students st ON st.student_id=al.student_id
                WHERE al.session_id=?
                UNION ALL
                SELECT st.register_number,st.full_name,'IMMUTABLE_EVENT',al.old_status,al.new_status,
                       al.actor_email,al.reason,al.`timestamp`,al.ip_address
                FROM attendance_audit_logs al JOIN students st ON st.student_id=al.student_id
                WHERE al.session_id=?
                ORDER BY recordedAt DESC
                """,sessionId,sessionId);
    }

    @PutMapping("/sessions/{sessionId}/attendance") @Transactional
    public Map<String,Object> updateAttendance(@PathVariable("sessionId") long sessionId,@AuthenticationPrincipal AccountPrincipal staff,@RequestBody BulkRequest request,HttpServletRequest servletRequest){
        requireOwnedSession(sessionId,staff.accountId());if(request.records()==null||request.records().isEmpty())bad("Add at least one attendance status.");
        Set<Long> seen=new HashSet<>();int saved=0;
        for(AttendanceItem item:request.records()){
            if(item.studentId()<=0||item.status()==null||!List.of("PRESENT","LATE","ABSENT","ON_DUTY","UNMARKED").contains(item.status()))bad("Each student needs a valid attendance status.");
            if(!seen.add(item.studentId()))bad("A student appears more than once in this update.");
            Integer enrolled=db.queryForObject("SELECT COUNT(*) FROM class_sessions cs JOIN enrollments e ON e.subject_id=cs.subject_id WHERE cs.session_id=? AND e.student_id=?",Integer.class,sessionId,item.studentId());
            if(enrolled==null||enrolled==0)bad("A selected student is not enrolled in this course.");
            List<String> previous=db.query("SELECT status FROM attendance_records WHERE session_id=? AND student_id=? FOR UPDATE",(rs,n)->rs.getString(1),sessionId,item.studentId());
            String old=previous.isEmpty()?null:previous.getFirst();
            if(item.status().equals("UNMARKED")){
                if(old!=null){db.update("DELETE FROM attendance_records WHERE session_id=? AND student_id=?",sessionId,item.studentId());db.update("INSERT INTO attendance_audit_log(session_id,student_id,event_type,previous_status,new_status,reason,claimed_faculty_id,staff_email) VALUES(?,?,'DELETED',?,NULL,?,?,?)",sessionId,item.studentId(),old,"Reset through Attend-IQ staff portal",staff.accountId(),staff.email());insertImmutableAudit(sessionId,item.studentId(),staff.email(),old,null,"Staff cleared attendance through the portal",servletRequest.getRemoteAddr());saved++;}
                continue;
            }
            db.update("INSERT INTO attendance_records(session_id,student_id,status) VALUES(?,?,?) ON DUPLICATE KEY UPDATE status=VALUES(status),marked_at=CURRENT_TIMESTAMP",sessionId,item.studentId(),item.status());
            db.update("INSERT INTO attendance_audit_log(session_id,student_id,event_type,previous_status,new_status,reason,claimed_faculty_id,staff_email) VALUES(?,?,?,?,?,?,?,?)",sessionId,item.studentId(),old==null?"MARKED":"CORRECTED",old,item.status(),old==null?null:"Edited through Attend-IQ staff portal",staff.accountId(),staff.email());
            insertImmutableAudit(sessionId,item.studentId(),staff.email(),old,item.status(),old==null?"Staff marked attendance":"Staff edited attendance through the portal",servletRequest.getRemoteAddr());saved++;
        }
        return Map.of("updated",saved);
    }

    @GetMapping("/reports/defaulters")
    public List<Map<String,Object>> defaulters(@AuthenticationPrincipal AccountPrincipal staff,@RequestParam(name="threshold",defaultValue="75") int threshold){
        if(threshold<1||threshold>100)bad("Threshold must be from 1 to 100.");
        List<Map<String,Object>> all=db.queryForList("SELECT st.student_id studentId,st.register_number registerNumber,st.full_name fullName,st.email,st.program,st.batch_label batch,s.subject_id courseId,s.subject_code courseCode,s.subject_name courseTitle,COUNT(cs.session_id) classesHeld,COALESCE(SUM(ar.status IN ('PRESENT','LATE','ON_DUTY')),0) classesAttended FROM subjects s JOIN enrollments e ON e.subject_id=s.subject_id JOIN students st ON st.student_id=e.student_id JOIN class_sessions cs ON cs.subject_id=s.subject_id AND cs.class_date>=DATE(e.enrolled_at) AND cs.class_date<=CURRENT_DATE LEFT JOIN attendance_records ar ON ar.session_id=cs.session_id AND ar.student_id=st.student_id WHERE s.faculty_id=? GROUP BY st.student_id,s.subject_id ORDER BY s.subject_code,st.register_number",staff.accountId());
        List<Map<String,Object>> result=new ArrayList<>();for(Map<String,Object> row:all){long held=((Number)row.get("classesHeld")).longValue(),attended=((Number)row.get("classesAttended")).longValue();double percent=AttendanceCalculator.percentage(attended,held);if(percent<threshold){row.put("percentage",percent);row.put("classesNeeded",AttendanceCalculator.classesNeeded(attended,held,threshold));result.add(row);}}return result;
    }

    @GetMapping("/reports/trends")
    public List<Map<String,Object>> trends(@AuthenticationPrincipal AccountPrincipal staff){
        return db.queryForList("""
                SELECT MIN(CASE DAYOFWEEK(cs.class_date)
                    WHEN 1 THEN 'Sunday' WHEN 2 THEN 'Monday' WHEN 3 THEN 'Tuesday'
                    WHEN 4 THEN 'Wednesday' WHEN 5 THEN 'Thursday' WHEN 6 THEN 'Friday'
                    ELSE 'Saturday' END) AS dayOfWeek,cs.period_number AS `period`,
                    COUNT(DISTINCT cs.session_id) sessions,
                    COUNT(ar.attendance_id) marks,
                    COALESCE(ROUND(100.0*SUM(ar.status IN ('PRESENT','LATE','ON_DUTY'))
                        /NULLIF(COUNT(ar.attendance_id),0),1),0) attendancePercentage
                FROM class_sessions cs JOIN subjects s ON s.subject_id=cs.subject_id
                LEFT JOIN attendance_records ar ON ar.session_id=cs.session_id
                WHERE s.faculty_id=? GROUP BY DAYOFWEEK(cs.class_date),cs.period_number
                ORDER BY DAYOFWEEK(cs.class_date),cs.period_number
                """,staff.accountId());
    }

    @GetMapping("/reports/attendance.csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(name="sessionId") long sessionId,@AuthenticationPrincipal AccountPrincipal staff){
        requireOwnedSession(sessionId,staff.accountId());List<Map<String,Object>> records=roster(sessionId,staff,null);StringBuilder csv=new StringBuilder("Register ID,Name,Program,Batch,Status,Marked At,Marked By\r\n");
        for(Map<String,Object> row:records){csv.append(csv(row.get("registerNumber"))).append(',').append(csv(row.get("fullName"))).append(',').append(csv(row.get("program"))).append(',').append(csv(row.get("batch"))).append(',').append(csv(row.get("status"))).append(',').append(csv(row.get("markedAt"))).append(',').append(csv(staff.email())).append("\r\n");}
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=attendiq-session-"+sessionId+".csv").contentType(new MediaType("text","csv",StandardCharsets.UTF_8)).body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void validateStudent(StudentRequest r){if(r==null||blank(r.registerNumber())||blank(r.fullName())||blank(r.email())||blank(r.program())||r.semester()<1||r.semester()>12||blank(r.batch()))bad("Register ID, name, institutional email, program, semester and batch are required.");try{if(!"STUDENT".equals(identityPolicy.identify(r.email()).role()))bad("Student email must match the institutional student email format.");}catch(IllegalArgumentException e){bad(e.getMessage());}}
    private void insertImmutableAudit(long sessionId,long studentId,String actorEmail,String oldStatus,String newStatus,String reason,String ip){db.update("INSERT INTO attendance_audit_logs(session_id,student_id,actor_email,old_status,new_status,reason,ip_address) VALUES(?,?,?,?,?,?,?)",sessionId,studentId,actorEmail,oldStatus,newStatus,reason,ip==null||ip.isBlank()?null:ip.substring(0,Math.min(ip.length(),45)));}
    private void syncEnrollments(AccountPrincipal staff,long studentId,List<Long> courseIds){if(courseIds==null)return;Integer exists=db.queryForObject("SELECT COUNT(*) FROM students WHERE student_id=?",Integer.class,studentId);if(exists==null||exists==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Student was not found.");List<Long> owned=db.query("SELECT subject_id FROM subjects WHERE faculty_id=?",(rs,n)->rs.getLong(1),staff.accountId());Set<Long> selected=new HashSet<>(courseIds);if(selected.size()!=courseIds.size()||!owned.containsAll(selected))bad("Choose only courses assigned to your staff account.");for(Long courseId:owned)db.update("DELETE FROM enrollments WHERE student_id=? AND subject_id=?",studentId,courseId);for(Long courseId:selected)db.update("INSERT INTO enrollments(student_id,subject_id) VALUES(?,?)",studentId,courseId);}
    private void requireStudentScope(long studentId,long staffId){Integer count=db.queryForObject("SELECT COUNT(*) FROM enrollments e JOIN subjects s ON s.subject_id=e.subject_id WHERE e.student_id=? AND s.faculty_id=?",Integer.class,studentId,staffId);if(count==null||count==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Student was not found in your course roster.");}
    private void requireCourse(long courseId,long facultyId){Integer count=db.queryForObject("SELECT COUNT(*) FROM subjects WHERE subject_id=? AND faculty_id=?",Integer.class,courseId,facultyId);if(count==null||count==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Course was not found for this staff account.");}
    private void requireOwnedSession(long sessionId,long facultyId){Integer count=db.queryForObject("SELECT COUNT(*) FROM class_sessions cs JOIN subjects s ON s.subject_id=cs.subject_id WHERE cs.session_id=? AND s.faculty_id=?",Integer.class,sessionId,facultyId);if(count==null||count==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Class session was not found for this staff account.");}
    private static boolean blank(String s){return s==null||s.isBlank();}
    private static void bad(String message){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private static String csv(Object value){String text=value==null?"":value.toString();return "\""+text.replace("\"","\"\"")+"\"";}
    public record CourseRequest(String code,String name,String department){}
    public record StudentRequest(String registerNumber,String fullName,String email,String program,int semester,String batch,List<Long> courseIds){}
    public record CourseEnrollmentRequest(List<Long> courseIds){}
    public record SessionRequest(long courseId,LocalDate date,int period){}
    public record AttendanceItem(long studentId,String status){}
    public record BulkRequest(List<AttendanceItem> records){}
}
