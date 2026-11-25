package com.attendiq;

import com.attendiq.web.AttendIqWebApplication;
import com.attendiq.web.QrAttendanceService;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpStatusCode;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=AttendIqWebApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="ATTENDIQ_RUN_MYSQL_IT",matches="true")
class AuthPortalMySqlIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired QrAttendanceService qrAttendance;
    private final RestTemplate http=createHttpClient();
    private String studentEmail,staffEmail,registerNumber,courseCode;
    private long studentId,facultyId,courseId,sessionId;
    private long absentStudentId;
    private String base(){return "http://localhost:"+port+"/api";}
    private static RestTemplate createHttpClient(){
        RestTemplate client=new RestTemplate(new SimpleClientHttpRequestFactory());
        client.setErrorHandler(new DefaultResponseErrorHandler(){
            @Override public boolean hasError(HttpStatusCode statusCode){return false;}
        });
        return client;
    }

    @BeforeEach void prepareAccountsAndRoster(){
        String suffix=UUID.randomUUID().toString().replaceAll("[^a-f]", "a");
        studentEmail="student."+suffix+".2025.cse@example.edu";
        staffEmail="i.staff"+suffix+"@example.edu";
        String fixtureSuffix=suffix.substring(0,16).toUpperCase();
        registerNumber="IT"+fixtureSuffix;courseCode="IT"+fixtureSuffix;
        db.update("INSERT INTO faculty(full_name,email) VALUES(?,?)","Integration Faculty",staffEmail);
        facultyId=db.queryForObject("SELECT faculty_id FROM faculty WHERE email=?",Long.class,staffEmail);
        db.update("INSERT INTO students(register_number,full_name,email,program,semester,batch_label) VALUES(?,?,?,?,?,?)",registerNumber,"Integration Student",studentEmail,"Computing",3,"2024-2028");
        studentId=db.queryForObject("SELECT student_id FROM students WHERE register_number=?",Long.class,registerNumber);
        db.update("INSERT INTO students(register_number,full_name,email,program,semester,batch_label) VALUES(?,?,?,?,?,?)",
                registerNumber+"B","Second Integration Student","absent."+suffix+".2025.cse@example.edu","Computing",3,"2024-2028");
        absentStudentId=db.queryForObject("SELECT student_id FROM students WHERE register_number=?",Long.class,registerNumber+"B");
        db.update("INSERT INTO subjects(subject_code,subject_name,department,faculty_id) VALUES(?,?,?,?)",courseCode,"Integration Course","Computing",facultyId);
        courseId=db.queryForObject("SELECT subject_id FROM subjects WHERE subject_code=?",Long.class,courseCode);
        db.update("INSERT INTO enrollments(student_id,subject_id) VALUES(?,?)",studentId,courseId);
        db.update("INSERT INTO enrollments(student_id,subject_id) VALUES(?,?)",absentStudentId,courseId);
    }

    @AfterEach void cleanFixture(){
        if(sessionId>0){
            db.update("DELETE FROM attendance_audit_logs WHERE session_id=?",sessionId);
            db.update("DELETE FROM attendance_qr_tokens WHERE qr_session_id IN (SELECT qr_session_id FROM attendance_qr_sessions WHERE class_session_id=?)",sessionId);
            db.update("DELETE FROM attendance_geofence_flags WHERE qr_session_id IN (SELECT qr_session_id FROM attendance_qr_sessions WHERE class_session_id=?)",sessionId);
            db.update("DELETE FROM attendance_qr_sessions WHERE class_session_id=?",sessionId);
            db.update("DELETE FROM attendance_audit_log WHERE session_id=?",sessionId);
            db.update("DELETE FROM attendance_records WHERE session_id=?",sessionId);
            db.update("DELETE FROM class_sessions WHERE session_id=?",sessionId);
        }
        if(studentId>0){db.update("DELETE FROM on_duty_requests WHERE student_id IN (?,?)",studentId,absentStudentId);db.update("DELETE FROM attendance_audit_log WHERE student_id IN (?,?)",studentId,absentStudentId);db.update("DELETE FROM attendance_records WHERE student_id IN (?,?)",studentId,absentStudentId);}
        if(studentId>0){db.update("DELETE FROM enrollments WHERE student_id=?",studentId);db.update("DELETE FROM students WHERE student_id=?",studentId);}
        if(absentStudentId>0){db.update("DELETE FROM enrollments WHERE student_id=?",absentStudentId);db.update("DELETE FROM students WHERE student_id=?",absentStudentId);}
        if(courseId>0)db.update("DELETE FROM subjects WHERE subject_id=?",courseId);
        if(facultyId>0)db.update("DELETE FROM faculty WHERE faculty_id=?",facultyId);
        if(studentEmail!=null)db.update("DELETE FROM auth_otp WHERE email=?",studentEmail);
        if(staffEmail!=null)db.update("DELETE FROM auth_otp WHERE email=?",staffEmail);
    }

    @Test void otpRoleAccessAttendanceMathAndSessionPersistenceWorkEndToEnd(){
        ResponseEntity<Map> badDomain=post("/auth/request-otp",Map.of("email","someone@gmail.com","role","STUDENT"),null);
        assertEquals(HttpStatus.BAD_REQUEST,badDomain.getStatusCode());

        Map studentOtp=post("/auth/request-otp",Map.of("email",studentEmail,"role","STUDENT"),null).getBody();
        assertNotNull(studentOtp.get("previewOtp"));
        ResponseEntity<Map> wrongPortal=post("/auth/request-otp",Map.of("email",studentEmail,"role","STAFF"),null);
        assertEquals(HttpStatus.FORBIDDEN,wrongPortal.getStatusCode());
        Map studentSession=post("/auth/verify-otp",Map.of("email",studentEmail,"code",studentOtp.get("previewOtp")),null).getBody();
        String studentToken=(String)studentSession.get("token");
        assertEquals("STUDENT",((Map)studentSession.get("account")).get("role"));
        assertEquals(HttpStatus.FORBIDDEN,get("/staff/dashboard",studentToken).getStatusCode());
        assertEquals(HttpStatus.OK,get("/student/dashboard",studentToken).getStatusCode());

        Map staffOtp=post("/auth/request-otp",Map.of("email",staffEmail,"role","STAFF"),null).getBody();
        Map staffSession=post("/auth/verify-otp",Map.of("email",staffEmail,"code",staffOtp.get("previewOtp")),null).getBody();
        String staffToken=(String)staffSession.get("token");
        assertEquals(HttpStatus.FORBIDDEN,get("/staff/courses",studentToken).getStatusCode());
        Map created=post("/staff/sessions",Map.of("courseId",courseId,"date",LocalDate.now().toString(),"period",8),staffToken).getBody();
        sessionId=((Number)created.get("id")).longValue();
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM class_sessions WHERE session_id=?",Integer.class,sessionId));
        Map qr=post("/staff/sessions/"+sessionId+"/qr",Map.of(),staffToken).getBody();
        String qrToken=(String)qr.get("token");
        ResponseEntity<Map> outside=post("/student/scan",Map.of("token",qrToken,"latitude",0.01,"longitude",0.0,"accuracyMeters",5),studentToken);
        assertEquals(HttpStatus.FORBIDDEN,outside.getStatusCode());
        ResponseEntity<Map> inaccurate=post("/student/scan",Map.of("token",qrToken,"latitude",0.0,"longitude",0.0,"accuracyMeters",100),studentToken);
        assertEquals(HttpStatus.BAD_REQUEST,inaccurate.getStatusCode());
        Map scan=post("/student/scan",Map.of("token",qrToken,"latitude",0.0,"longitude",0.0,"accuracyMeters",5),studentToken).getBody();
        assertEquals("PRESENT",scan.get("status"));
        assertFalse((Boolean)scan.get("alreadyRecorded"));
        Map replay=post("/student/scan",Map.of("token",qrToken,"latitude",0.0,"longitude",0.0,"accuracyMeters",5),studentToken).getBody();
        assertTrue((Boolean)replay.get("alreadyRecorded"));
        db.update("UPDATE attendance_qr_tokens SET expires_at=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 1 SECOND) WHERE qr_session_id=(SELECT qr_session_id FROM attendance_qr_sessions WHERE class_session_id=?)",sessionId);
        assertEquals(HttpStatus.GONE,post("/student/scan",Map.of("token",qrToken,"latitude",0.0,"longitude",0.0,"accuracyMeters",5),studentToken).getStatusCode());
        ResponseEntity<Map> marked=put("/staff/sessions/"+sessionId+"/attendance",Map.of("records",new Object[]{Map.of("studentId",studentId,"status","PRESENT")}),staffToken);
        assertEquals(HttpStatus.OK,marked.getStatusCode());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM attendance_records WHERE session_id=? AND student_id=? AND status='PRESENT'",Integer.class,sessionId,studentId));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM attendance_audit_log WHERE session_id=? AND staff_email=?",Integer.class,sessionId,staffEmail));
        db.update("UPDATE attendance_qr_sessions SET started_at=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 20 MINUTE),closes_at=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 1 SECOND) WHERE class_session_id=?",sessionId);
        qrAttendance.closeExpiredSessions();
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM attendance_records WHERE session_id=? AND student_id=? AND status='ABSENT'",Integer.class,sessionId,absentStudentId));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM attendance_audit_logs WHERE session_id=? AND student_id=? AND new_status='ABSENT'",Integer.class,sessionId,absentStudentId));
        Map dashboard=get("/student/dashboard",studentToken).getBody();
        assertEquals(100.0,((Number)dashboard.get("overallPercentage")).doubleValue());
    }

    private ResponseEntity<Map> post(String path,Object body,String token){return http.exchange(base()+path,HttpMethod.POST,entity(body,token),Map.class);}
    private ResponseEntity<Map> put(String path,Object body,String token){return http.exchange(base()+path,HttpMethod.PUT,entity(body,token),Map.class);}
    private ResponseEntity<Map> get(String path,String token){return http.exchange(base()+path,HttpMethod.GET,entity(null,token),Map.class);}
    private HttpEntity<Object> entity(Object body,String token){HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);if(token!=null)h.setBearerAuth(token);return new HttpEntity<>(body,h);}
}
