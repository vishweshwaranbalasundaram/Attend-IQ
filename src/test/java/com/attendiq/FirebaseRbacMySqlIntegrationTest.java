package com.attendiq;

import com.attendiq.web.AttendIqWebApplication;
import com.attendiq.web.FirebaseIdTokenVerifier;
import com.google.firebase.auth.FirebaseToken;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = AttendIqWebApplication.class, properties = {
        "attendiq.auth.dev-auth-bypass=false",
        "FIREBASE_AUTH_EMULATOR_HOST=localhost:9099"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "ATTENDIQ_RUN_MYSQL_IT", matches = "true")
class FirebaseRbacMySqlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @MockBean FirebaseIdTokenVerifier firebase;

    private String studentEmail;
    private long studentId;
    private long facultyId;
    private long courseId;

    @BeforeEach
    void createInstitutionalAccounts() throws Exception {
        String suffix = UUID.randomUUID().toString().replaceAll("[^a-f]", "a");
        String fixtureSuffix = suffix.substring(0, 18).toUpperCase();
        studentEmail = "learner." + suffix.substring(0, 1) + ".2025.cse@example.edu";
        String staffEmail = "k." + "faculty" + suffix + "@example.edu";
        db.update("INSERT INTO faculty(full_name,email) VALUES(?,?)", "RBAC Faculty", staffEmail);
        facultyId = db.queryForObject("SELECT faculty_id FROM faculty WHERE email=?", Long.class, staffEmail);
        db.update("INSERT INTO students(register_number,full_name,email,program,semester,batch_label) VALUES(?,?,?,?,?,?)",
                "RB" + fixtureSuffix, "RBAC Student", studentEmail, "Computing", 3, "2024-2028");
        studentId = db.queryForObject("SELECT student_id FROM students WHERE email=?", Long.class, studentEmail);
        db.update("INSERT INTO subjects(subject_code,subject_name,department,faculty_id) VALUES(?,?,?,?)",
                "RB" + fixtureSuffix, "RBAC Course", "Computing", facultyId);
        courseId = db.queryForObject("SELECT subject_id FROM subjects WHERE faculty_id=?", Long.class, facultyId);
        db.update("INSERT INTO enrollments(student_id,subject_id) VALUES(?,?)", studentId, courseId);
        FirebaseToken validToken = token(studentEmail, true, "STAFF");
        FirebaseToken unverifiedToken = token(studentEmail, false, "STUDENT");
        FirebaseToken badFormatToken = token("student@example.edu", true, "STUDENT");
        FirebaseToken staffToken = token(staffEmail, true, "STUDENT");
        when(firebase.verify("student-id-token")).thenReturn(validToken);
        when(firebase.verify("unverified-id-token")).thenReturn(unverifiedToken);
        when(firebase.verify("bad-format-id-token")).thenReturn(badFormatToken);
        when(firebase.verify("staff-id-token")).thenReturn(staffToken);
    }

    @AfterEach
    void removeInstitutionalAccounts() {
        if (studentId > 0) db.update("DELETE FROM enrollments WHERE student_id=?", studentId);
        if (courseId > 0) db.update("DELETE FROM subjects WHERE subject_id=?", courseId);
        if (studentId > 0) db.update("DELETE FROM students WHERE student_id=?", studentId);
        if (facultyId > 0) db.update("DELETE FROM faculty WHERE faculty_id=?", facultyId);
    }

    @Test
    void requiresVerifiedFirebaseIdentityAndUsesDatabaseRoleInsteadOfTokenOrHeaders() throws Exception {
        mvc.perform(get("/api/student/dashboard"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/student/dashboard").header("Authorization", "Bearer unverified-id-token"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/student/dashboard").header("Authorization", "Bearer bad-format-id-token"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer student-id-token")
                        .header("X-User-Role", "STAFF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.yearOfJoining").value(2025))
                .andExpect(jsonPath("$.department").value("cse"));
        mvc.perform(get("/api/staff/courses").header("Authorization", "Bearer student-id-token"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/student/dashboard").header("Authorization", "Bearer student-id-token"))
                .andExpect(status().isOk());
        verify(firebase, times(3)).verify("student-id-token");
    }

    @Test
    void allowsLocalFrontendCorsPreflightWithoutAuthentication() throws Exception {
        mvc.perform(options("/api/auth/me")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @Test
    void staffTrendsWorkWithMySqlOnlyFullGroupByMode() throws Exception {
        mvc.perform(get("/api/staff/reports/trends").header("Authorization", "Bearer staff-id-token"))
                .andExpect(status().isOk());
    }

    private static FirebaseToken token(String email, boolean verified, String untrustedRole)
            throws ReflectiveOperationException {
        Map<String, Object> claims = Map.of(
                "sub", email,
                "email", email,
                "email_verified", verified,
                "role", untrustedRole);
        Constructor<FirebaseToken> constructor = FirebaseToken.class.getDeclaredConstructor(Map.class);
        constructor.setAccessible(true);
        return constructor.newInstance(claims);
    }
}
