package com.attendiq.web;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Profile("local")
@ConditionalOnProperty(name = "attendiq.demo-seed.enabled", havingValue = "true")
public class LocalDemoAccountSeeder implements CommandLineRunner {
    private static final Logger LOG = LoggerFactory.getLogger(LocalDemoAccountSeeder.class);
    private static final int STUDENT_COUNT = 450;
    private static final int STAFF_COUNT = 50;
    private static final String STUDENT_REGISTER_PREFIX = "ATTENDIQ-DEMO-STU-";
    private static final String[] ALPHABET = "abcdefghijklmnopqrstuvwxyz".split("");
    private static final Department[] DEPARTMENTS = {
            new Department("cse", "Computer Science and Engineering"),
            new Department("it", "Information Technology"),
            new Department("ece", "Electronics and Communication Engineering"),
            new Department("aids", "Artificial Intelligence and Data Science"),
            new Department("mech", "Mechanical Engineering")
    };
    private static final int[] JOINING_YEARS = {2022, 2023, 2024, 2025};

    private final JdbcTemplate db;
    private final FirebaseAuth firebaseAuth;
    private final InstitutionalIdentityPolicy identityPolicy;
    private final TransactionTemplate transaction;
    private final ConfigurableApplicationContext context;
    private final String firebaseProjectId;
    private final String authEmulatorHost;
    private final String demoPassword;

    public LocalDemoAccountSeeder(JdbcTemplate db, FirebaseAuth firebaseAuth,
            InstitutionalIdentityPolicy identityPolicy, PlatformTransactionManager transactionManager,
            ConfigurableApplicationContext context,
            @Value("${attendiq.firebase.project-id}") String firebaseProjectId,
            @Value("${FIREBASE_AUTH_EMULATOR_HOST:}") String authEmulatorHost,
            @Value("${ATTENDIQ_DEMO_PASSWORD:}") String demoPassword) {
        this.db = db;
        this.firebaseAuth = firebaseAuth;
        this.identityPolicy = identityPolicy;
        this.transaction = new TransactionTemplate(transactionManager);
        this.context = context;
        this.firebaseProjectId = firebaseProjectId;
        this.authEmulatorHost = authEmulatorHost;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(String... args) throws Exception {
        validateLocalConfiguration();
        List<Long> courseIds = db.query("SELECT subject_id FROM subjects ORDER BY subject_id",
                (rs, row) -> rs.getLong(1));
        if (courseIds.isEmpty()) {
            throw new IllegalStateException("Seed the course catalog before generating demo accounts.");
        }

        List<StudentSeed> students = generateStudents(identityPolicy.domain());
        List<StaffSeed> staff = generateStaff(identityPolicy.domain());
        for (StudentSeed student : students) {
            identityPolicy.identify(student.email());
            ensureFirebaseAccount(student.email(), student.fullName());
        }
        for (StaffSeed member : staff) {
            identityPolicy.identify(member.email());
            ensureFirebaseAccount(member.email(), member.fullName());
        }

        transaction.executeWithoutResult(status -> persistDirectory(students, staff));
        LOG.info("Seeded {} demo students and {} demo staff, enrolled across {} existing courses.",
                students.size(), staff.size(), courseIds.size());
        context.close();
    }

    static List<StudentSeed> generateStudents(String domain) {
        String normalizedDomain = domain.trim().toLowerCase(Locale.ROOT);
        int currentYear = Year.now().getValue();
        List<StudentSeed> students = new ArrayList<>(STUDENT_COUNT);
        for (int index = 0; index < STUDENT_COUNT; index++) {
            int departmentIndex = index % DEPARTMENTS.length;
            int yearIndex = index / DEPARTMENTS.length % JOINING_YEARS.length;
            int initialIndex = index / (DEPARTMENTS.length * JOINING_YEARS.length) % ALPHABET.length;
            int joiningYear = JOINING_YEARS[yearIndex];
            Department department = DEPARTMENTS[departmentIndex];
            String number = String.format(Locale.ROOT, "%04d", index + 1);
            int semester = Math.min(8, Math.max(1, (currentYear - joiningYear) * 2 + 1));
            students.add(new StudentSeed(STUDENT_REGISTER_PREFIX + number, "AttendIQ Demo Student " + number,
                    "attendiqdemo." + ALPHABET[initialIndex] + "." + joiningYear + "."
                            + department.code() + "@" + normalizedDomain,
                    department.name(), semester, joiningYear + "-" + (joiningYear + 4),
                    joiningYear, department.code()));
        }
        return List.copyOf(students);
    }

    static List<StaffSeed> generateStaff(String domain) {
        String normalizedDomain = domain.trim().toLowerCase(Locale.ROOT);
        List<StaffSeed> staff = new ArrayList<>(STAFF_COUNT);
        for (int index = 0; index < STAFF_COUNT; index++) {
            String number = String.format(Locale.ROOT, "%03d", index + 1);
            staff.add(new StaffSeed("AttendIQ Demo Staff " + number,
                    alphaCode(index + 1) + ".attendiqdemo@" + normalizedDomain));
        }
        return List.copyOf(staff);
    }

    private void validateLocalConfiguration() {
        if (authEmulatorHost.isBlank() || !firebaseProjectId.startsWith("demo-")) {
            throw new IllegalStateException("Demo account seeding is allowed only with a demo Firebase project and Auth Emulator.");
        }
        if (demoPassword.length() < 8) {
            throw new IllegalStateException("Set ATTENDIQ_DEMO_PASSWORD to a local-only password of at least 8 characters.");
        }
    }

    private void ensureFirebaseAccount(String email, String displayName) throws FirebaseAuthException {
        try {
            firebaseAuth.getUserByEmail(email);
            return;
        } catch (FirebaseAuthException exception) {
            if (exception.getAuthErrorCode() != AuthErrorCode.USER_NOT_FOUND) {
                throw exception;
            }
        }
        firebaseAuth.createUser(new UserRecord.CreateRequest()
                .setEmail(email)
                .setPassword(demoPassword)
                .setEmailVerified(true)
                .setDisplayName(displayName));
    }

    private void persistDirectory(List<StudentSeed> students, List<StaffSeed> staff) {
        List<Object[]> studentRows = students.stream()
                .map(student -> new Object[] { student.registerNumber(), student.fullName(), student.email(),
                        student.program(), student.semester(), student.batchLabel() })
                .toList();
        db.batchUpdate("""
                INSERT IGNORE INTO students(register_number,full_name,email,program,semester,batch_label)
                VALUES(?,?,?,?,?,?)
                """, studentRows);

        List<Object[]> staffRows = staff.stream()
                .map(member -> new Object[] { member.fullName(), member.email() })
                .toList();
        db.batchUpdate("INSERT IGNORE INTO faculty(full_name,email) VALUES(?,?)", staffRows);
        db.update("""
                INSERT IGNORE INTO enrollments(student_id,subject_id)
                SELECT st.student_id,s.subject_id FROM students st CROSS JOIN subjects s
                WHERE st.register_number LIKE ?
                """, STUDENT_REGISTER_PREFIX + "%");

        Integer storedStudents = db.queryForObject("SELECT COUNT(*) FROM students WHERE register_number LIKE ?",
                Integer.class, STUDENT_REGISTER_PREFIX + "%");
        if (storedStudents == null || storedStudents != STUDENT_COUNT) {
            throw new IllegalStateException("Could not register all generated demo students; check for conflicting register numbers.");
        }
    }

    private static String alphaCode(int value) {
        StringBuilder code = new StringBuilder();
        while (value > 0) {
            value--;
            code.insert(0, (char) ('a' + value % 26));
            value /= 26;
        }
        return code.toString();
    }

    record StudentSeed(String registerNumber, String fullName, String email, String program, int semester,
            String batchLabel, int joiningYear, String department) {}

    record StaffSeed(String fullName, String email) {}

    private record Department(String code, String name) {}
}
