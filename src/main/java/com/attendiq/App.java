package com.attendiq;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class App {
    private static final ConsoleInput INPUT = new ConsoleInput(System.in);
    private static final AttendanceService SERVICE =
            new AttendanceService(new AttendanceRepository());
    private static final StudentService STUDENTS =
            new StudentService(new JdbcAcademicRepository());
    private static final AcademicCatalogService CATALOG =
            new AcademicCatalogService(new JdbcAcademicCatalogRepository());
    private static final ConsoleStyle STYLE = new ConsoleStyle();
    private static final ConsoleUI UI = new ConsoleUI(STYLE);
    private static final DashboardRepository DASHBOARD = new DashboardRepository();
    private static final BulkAttendanceService BULK = new BulkAttendanceService(SERVICE);
    private static final CsvExporter CSV = new CsvExporter();
    private static final DateTimeFormatter EXPORT_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public static void main(String[] args) {
        boolean connected;
        try {
            connected = Database.testConnection();
        } catch (SQLException exception) {
            connected = false;
        }
        UI.header(connected ? "ONLINE" : "OFFLINE");
        if (!connected) {
            UI.warning("MySQL is unavailable. Database menus will show a safe error until connection is restored.");
        }
        while (true) {
            UI.mainMenu(List.of("Dashboard", "Student Management", "Faculty Management",
                    "Subject Management", "Enrollment Management", "Class Session Management",
                    "Attendance Management", "Reports & Analytics", "Export Reports",
                    "System & Database", "Help & Shortcuts", "Exit"));
            try {
                int choice = readInt("Choose: ");
                switch (choice) {
                    case 1 -> showDashboard();
                    case 2 -> manageStudents();
                    case 3 -> manageFaculty();
                    case 4 -> manageSubjects();
                    case 5 -> manageEnrollments();
                    case 6 -> manageSessions();
                    case 7 -> attendanceMenu();
                    case 8 -> reportsMenu();
                    case 9 -> exportMenu();
                    case 10 -> systemMenu();
                    case 11 -> helpMenu();
                    case 12 -> {
                        UI.success("Attend-IQ closed.");
                        return;
                    }
                    default -> System.out.println("Invalid menu choice.");
                }
            } catch (ConsoleInputClosedException e) {
                System.out.println("Console input closed. Attend-IQ exited.");
                return;
            } catch (IllegalArgumentException e) {
                UI.error(e.getMessage());
            } catch (SQLException e) {
                if ("23000".equals(e.getSQLState())) {
                    UI.warning("Database constraint prevented the operation. Check for duplicates "
                            + "or related history.");
                } else {
                    UI.error("Database operation failed. Check MySQL and configured account permissions.");
                }
            } catch (IOException e) {
                UI.error("File operation failed. Check the destination path and permissions.");
            } catch (SecurityException | IllegalStateException e) {
                UI.warning(e.getMessage());
            }
        }
    }

    private static void showDashboard() throws SQLException {
        int target = AttendancePolicy.targetPercentage();
        DashboardSummary summary = DASHBOARD.load(target);
        UI.section("LIVE DASHBOARD | TODAY: " + LocalDate.now());
        UI.panel("ACADEMIC OVERVIEW", List.of(
                "Students       " + summary.students(),
                "Faculty        " + summary.faculty(),
                "Subjects       " + summary.subjects(),
                "Enrollments    " + summary.enrollments(),
                "Sessions today " + summary.sessionsToday()));
        UI.panel("TODAY'S ATTENDANCE | ENROLLED ROSTERS", List.of(
                "Present       " + summary.presentToday(),
                "Absent        " + summary.absentToday(),
                "On duty       " + summary.onDutyToday(),
            "Unmarked      " + summary.unmarkedToday(),
            "Below target  " + summary.studentsBelowTarget() + " at " + target + "%"));
        long expectedToday = summary.presentToday() + summary.absentToday()
            + summary.onDutyToday() + summary.unmarkedToday();
        double todayRate = expectedToday == 0 ? 0.0
            : (summary.presentToday() + summary.onDutyToday()) * 100.0 / expectedToday;
        UI.info("Today's attended rate (Present + On-duty / enrolled roster): "
            + UI.progressBar(todayRate, 24));
        UI.panel("QUICK ACTIONS", List.of(
                "7  Attendance management",
                "2  Search/manage students",
                "6  Create a class session",
                "8  Reports and shortage analysis",
                "10 Test database and inspect server version"));
        List<String> activity = summary.recentActivity().stream().map(event ->
            "#" + event.auditId() + " " + event.eventType() + " session " + event.sessionId()
                + ", student " + event.studentId() + " at " + event.recordedAt()).toList();
        UI.panel("RECENT ATTENDANCE ACTIVITY", activity.isEmpty()
            ? List.of("No attendance activity has been recorded yet.") : activity);
    }

    private static void attendanceMenu() throws SQLException {
        UI.menu("ATTENDANCE MANAGEMENT", List.of("Mark one student", "Bulk mark session roster",
                "Retrieve one record", "Correct or delete attendance", "View attendance history"));
        switch (readInt("Attendance action: ")) {
            case 1 -> markAttendance();
            case 2 -> bulkMarkAttendance();
            case 3 -> retrieveAttendance();
            case 4 -> correctAttendance();
            case 5 -> viewAttendanceHistory();
            case 0 -> { }
            default -> UI.warning("Choose an attendance action from the list.");
        }
    }

    private static void reportsMenu() throws SQLException {
        UI.menu("REPORTS & ANALYTICS", List.of("Student and subject attendance",
                "Subject summaries", "Below-target students", "Attendance history filters"));
        switch (readInt("Report: ")) {
            case 1 -> showAttendancePercentage();
            case 2 -> showSubjectSummaries();
            case 3 -> showShortageReports();
            case 4 -> viewAttendanceHistory();
            case 0 -> { }
            default -> UI.warning("Choose a report from the list.");
        }
    }

    private static void showShortageReports() throws SQLException {
        int target = AttendancePolicy.targetPercentage();
        List<ShortageReport> reports = CATALOG.shortageReports(target);
        if (reports.isEmpty()) {
            UI.success("No enrolled student/subject pairs are below " + target + "%.");
            return;
        }
        List<List<String>> rows = reports.stream().map(report -> List.of(
                report.registerNumber(), report.studentName(), report.subjectCode(),
                String.format("%.1f%%", report.percentage()),
                report.sessionsNeeded() < 0 ? "Unreachable" : String.valueOf(report.sessionsNeeded())))
                .toList();
        ConsoleTable.printPaged(List.of("Register", "Student", "Subject", "Attendance", "Needed"),
                rows, INPUT, STYLE);
    }

    private static void showSubjectSummaries() throws SQLException {
        List<SubjectAttendanceSummary> summaries = CATALOG.subjectAttendanceSummaries();
        List<List<String>> rows = summaries.stream().map(summary -> List.of(
                summary.subjectCode(), summary.subjectName(),
                String.valueOf(summary.presentRecords()), String.valueOf(summary.absentRecords()),
                String.valueOf(summary.onDutyRecords()), String.valueOf(summary.unmarkedSessions()),
                String.format("%.1f%%", summary.percentage()))).toList();
        ConsoleTable.printPaged(List.of("Code", "Subject", "Present", "Absent", "On duty",
                "Unmarked", "Attendance"), rows, INPUT, STYLE);
        for (SubjectAttendanceSummary summary : summaries) {
            UI.info(summary.subjectCode() + " " + UI.progressBar(summary.percentage(), 20));
        }
    }

    private static void exportMenu() throws SQLException, IOException {
        UI.menu("EXPORT REPORTS", List.of("Student list", "Attendance by student",
                "Attendance by session", "Attendance by subject/date range",
                "Subject summaries", "Shortage report"));
        switch (readInt("Export: ")) {
            case 1 -> exportStudents();
            case 2 -> exportAttendanceByStudent();
            case 3 -> exportAttendanceBySession();
            case 4 -> exportAttendanceBySubjectRange();
            case 5 -> exportSubjectSummaries();
            case 6 -> exportShortages();
            case 0 -> { }
            default -> UI.warning("Choose an export from the list.");
        }
    }

    private static void exportStudents() throws SQLException, IOException {
        List<List<String>> rows = STUDENTS.listStudents().stream().map(student -> List.of(
                String.valueOf(student.studentId()), student.registerNumber(), student.fullName(),
                student.email() == null ? "" : student.email())).toList();
        export("students", List.of("student_id", "register_number", "full_name", "email"), rows);
    }

    private static void exportAttendanceByStudent() throws SQLException, IOException {
        List<AttendanceRecord> records = SERVICE.listByStudent(readLong("Student ID: "));
        export("attendance-student", attendanceHeaders(), attendanceRows(records));
    }

    private static void exportAttendanceBySession() throws SQLException, IOException {
        List<AttendanceRecord> records = SERVICE.listBySession(readLong("Session ID: "));
        export("attendance-session", attendanceHeaders(), attendanceRows(records));
    }

    private static void exportAttendanceBySubjectRange() throws SQLException, IOException {
        long subjectId = readLong("Subject ID: ");
        LocalDate from = readDate("Start date (YYYY-MM-DD): ");
        LocalDate to = readDate("End date (YYYY-MM-DD): ");
        List<AttendanceRecord> records = SERVICE.listBySubjectAndDates(subjectId, from, to);
        export("attendance-subject", attendanceHeaders(), attendanceRows(records));
    }

    private static void exportSubjectSummaries() throws SQLException, IOException {
        List<List<String>> rows = CATALOG.subjectAttendanceSummaries().stream().map(summary -> List.of(
                String.valueOf(summary.subjectId()), summary.subjectCode(), summary.subjectName(),
                String.valueOf(summary.presentRecords()), String.valueOf(summary.absentRecords()),
                String.valueOf(summary.onDutyRecords()), String.valueOf(summary.unmarkedSessions()),
                String.format("%.2f", summary.percentage()))).toList();
        export("subject-attendance", List.of("subject_id", "subject_code", "subject_name",
                "present", "absent", "on_duty", "unmarked", "percentage"), rows);
    }

    private static void exportShortages() throws SQLException, IOException {
        int target = AttendancePolicy.targetPercentage();
        List<List<String>> rows = CATALOG.shortageReports(target).stream().map(report -> List.of(
                report.registerNumber(), report.studentName(), report.subjectCode(),
                String.valueOf(report.attendedSessions()), String.valueOf(report.totalSessions()),
                String.format("%.2f", report.percentage()), String.valueOf(report.sessionsNeeded())))
                .toList();
        export("shortage-report", List.of("register_number", "student_name", "subject_code",
                "attended", "scheduled", "percentage", "sessions_needed"), rows);
    }

    private static List<String> attendanceHeaders() {
        return List.of("attendance_id", "session_id", "student_id", "register_number",
                "student_name", "status", "marked_at");
    }

    private static List<List<String>> attendanceRows(List<AttendanceRecord> records) {
        return records.stream().map(record -> List.of(String.valueOf(record.attendanceId()),
                String.valueOf(record.sessionId()), String.valueOf(record.studentId()),
                record.registerNumber(), record.studentName(), record.status().name(),
                record.markedAt().toString())).toList();
    }

    private static void export(String name, List<String> headers, List<List<String>> rows)
            throws IOException {
        String entered = readText("Destination path (blank for exports/" + name + ".csv): ");
        Path path = entered.isBlank()
                ? Path.of("exports", name + "-" + LocalDateTime.now().format(EXPORT_TIMESTAMP) + ".csv")
                : Path.of(entered);
        boolean overwrite = Files.exists(path)
                && UI.confirm(INPUT, "File exists at " + path + ". Overwrite it?");
        if (Files.exists(path) && !overwrite) {
            UI.info("Export cancelled; existing file was preserved.");
            return;
        }
        CSV.write(path, headers, rows, overwrite);
        UI.success("Exported " + rows.size() + " records to " + path.toAbsolutePath().normalize());
    }

    private static void bulkMarkAttendance() throws SQLException {
        long sessionId = readLong("Class session ID: ");
        ClassSession session = CATALOG.findSession(sessionId);
        Subject subject = CATALOG.findSubject(session.subjectId());
        Faculty faculty = CATALOG.findFaculty(subject.facultyId());
        List<Student> roster = CATALOG.studentsForSubject(subject.subjectId());
        List<AttendanceRecord> existing = SERVICE.listBySession(sessionId);
        Set<Long> markedStudentIds = new HashSet<>();
        existing.forEach(record -> markedStudentIds.add(record.studentId()));

        UI.panel("SESSION " + session.sessionId(), List.of("Subject: " + subject.subjectCode()
                        + " - " + subject.subjectName(), "Date: " + session.classDate()
                        + " | Period: " + session.periodNumber(), "Assigned faculty: " + faculty.fullName(),
                "Roster: " + roster.size() + " | Already marked: " + markedStudentIds.size()));
        if (roster.isEmpty()) {
            UI.info("No students are enrolled in this subject.");
            return;
        }
        UI.section("ENROLLED ROSTER");
        showStudents(roster);
        UI.section("ALREADY RECORDED ATTENDANCE");
        showAttendanceRecords(existing);

        List<BulkAttendanceRequest> requests = new ArrayList<>();
        List<Long> skipped = new ArrayList<>();
        for (Student student : roster) {
            if (markedStudentIds.contains(student.studentId())) {
                skipped.add(student.studentId());
                continue;
            }
            int choice = readInt(student.registerNumber() + " | " + student.fullName()
                    + " | 1 Present, 2 Absent, 3 On-duty, 0 Skip: ");
            if (choice == 0) {
                skipped.add(student.studentId());
                continue;
            }
            requests.add(new BulkAttendanceRequest(student.studentId(),
                    AttendanceStatus.fromMenuChoice(choice)));
        }
        if (requests.isEmpty()) {
            UI.info("No new attendance changes were proposed.");
            return;
        }
        List<List<String>> preview = requests.stream().map(request -> {
            Student student = roster.stream().filter(value -> value.studentId() == request.studentId())
                    .findFirst().orElseThrow();
            return List.of(student.registerNumber(), student.fullName(), request.status().name());
        }).toList();
        ConsoleTable.printPaged(List.of("Register", "Student", "Proposed status"), preview, INPUT, STYLE);
        if (!UI.confirm(INPUT, "Save each proposed attendance record? Existing records will be skipped.")) {
            UI.info("Bulk attendance cancelled; no new rows were written.");
            return;
        }

        BulkAttendanceResult result = BULK.mark(sessionId, requests);
        List<Long> allSkipped = new ArrayList<>(skipped);
        allSkipped.addAll(result.skippedStudentIds());
        UI.success("Bulk marking finished. Saved: " + result.savedStudentIds().size()
                + ", skipped: " + allSkipped.size() + ", failed: " + result.failures().size());
        if (!allSkipped.isEmpty()) {
            UI.info("Skipped student IDs: " + allSkipped);
        }
        result.failures().forEach(UI::error);
    }

    private static void systemMenu() throws SQLException {
        UI.menu("SYSTEM & DATABASE", List.of("Test MySQL connection", "Show runtime/database info",
            "Show last successful connection check"));
        switch (readInt("System action: ")) {
            case 1 -> {
                if (Database.testConnection()) {
                    UI.success("MySQL connection is valid.");
                } else {
                    UI.error("MySQL connection is not valid.");
                }
            }
            case 2 -> {
                DatabaseDiagnostics diagnostics = Database.diagnostics();
                UI.panel("RUNTIME", List.of("Java " + Runtime.version(),
                        diagnostics.serverName() + " " + diagnostics.serverVersion(),
                        "Database " + diagnostics.databaseName(),
                        "Credentials are hidden and loaded from environment variables."));
            }
            case 3 -> UI.info("Last successful check: "
                    + Database.lastSuccessfulConnectionCheck().map(Object::toString)
                    .orElse("not checked in this application run"));
            case 0 -> { }
            default -> UI.warning("Choose a system action from the list.");
        }
    }

    private static void helpMenu() {
        UI.panel("HELP & SHORTCUTS", List.of(
                "Enter the number shown to open a menu option.",
                "Enter 0 in a submenu to return to the main menu.",
                "Enter 12 in the main menu to exit.",
                "Attendance correction uses the assigned faculty ID as an academic check only.",
                "Set ATTENDIQ_COLOR=never for plain text or ATTENDIQ_COLOR=always to force ANSI colors.",
                "MySQL integration tests require the separate attendiq_java_test database."));
    }

    private static void manageStudents() throws SQLException {
        UI.menu("STUDENT MANAGEMENT", List.of("List students", "Add student", "Update student",
            "Delete student", "View student", "Search name/register number"));
        switch (readInt("Student action: ")) {
            case 1 -> showStudents(STUDENTS.listStudents());
            case 2 -> createStudent();
            case 3 -> updateStudent();
            case 4 -> UI.info(STUDENTS.deleteStudent(readLong("Student ID: "))
                ? "Student deleted." : "Student not found.");
            case 5 -> showStudents(List.of(STUDENTS.findStudent(readLong("Student ID: "))));
            case 6 -> showStudents(STUDENTS.searchStudents(
                readText("Name or registration prefix: ")));
            case 0 -> { }
            default -> UI.warning("Choose a student action from the list.");
        }
    }

        private static void createStudent() throws SQLException {
        Student student = STUDENTS.createStudent(readText("Register number: "),
            readText("Full name: "), readText("Email (optional): "));
        UI.success("Student created.");
        showStudents(List.of(student));
        }

        private static void updateStudent() throws SQLException {
        Student student = STUDENTS.updateStudent(readLong("Student ID: "),
            readText("Register number: "), readText("Full name: "),
            readText("Email (optional): "));
        UI.success("Student updated.");
        showStudents(List.of(student));
        }

        private static void showStudents(List<Student> students) {
        List<List<String>> rows = students.stream().map(student -> List.of(
            String.valueOf(student.studentId()), student.registerNumber(), student.fullName(),
            student.email() == null ? "-" : student.email())).toList();
        ConsoleTable.printPaged(List.of("ID", "Register", "Student", "Email"), rows, INPUT, STYLE);
        }

    private static void manageFaculty() throws SQLException {
        UI.menu("FACULTY MANAGEMENT", List.of("List faculty", "Add faculty", "Update faculty",
            "Delete faculty", "View faculty", "Assigned subjects"));
        switch (readInt("Faculty action: ")) {
            case 1 -> showFaculty(CATALOG.listFaculty());
            case 2 -> showFaculty(List.of(CATALOG.createFaculty(
                readText("Full name: "), readText("Email: "))));
            case 3 -> showFaculty(List.of(CATALOG.updateFaculty(readLong("Faculty ID: "),
                readText("Full name: "), readText("Email: "))));
            case 4 -> UI.info(CATALOG.deleteFaculty(readLong("Faculty ID: "))
                ? "Faculty deleted." : "Faculty not found.");
            case 5 -> showFaculty(List.of(CATALOG.findFaculty(readLong("Faculty ID: "))));
            case 6 -> showSubjects(CATALOG.subjectsForFaculty(readLong("Faculty ID: ")));
            case 0 -> { }
            default -> UI.warning("Choose a faculty action from the list.");
        }
    }

        private static void showFaculty(List<Faculty> faculty) {
        List<List<String>> rows = faculty.stream().map(member -> List.of(
            String.valueOf(member.facultyId()), member.fullName(), member.email())).toList();
        ConsoleTable.printPaged(List.of("ID", "Faculty", "Email"), rows, INPUT, STYLE);
        }

    private static void manageSubjects() throws SQLException {
        UI.menu("SUBJECT MANAGEMENT", List.of("List subjects", "Add subject", "Update subject",
            "Delete subject", "View subject"));
        switch (readInt("Subject action: ")) {
            case 1 -> showSubjects(CATALOG.listSubjects());
            case 2 -> showSubjects(List.of(CATALOG.createSubject(readText("Subject code: "),
                readText("Subject name: "), readLong("Assigned faculty ID: "))));
            case 3 -> showSubjects(List.of(CATALOG.updateSubject(readLong("Subject ID: "),
                readText("Subject code: "), readText("Subject name: "),
                readLong("Assigned faculty ID: "))));
            case 4 -> UI.info(CATALOG.deleteSubject(readLong("Subject ID: "))
                ? "Subject deleted." : "Subject not found.");
            case 5 -> showSubjects(List.of(CATALOG.findSubject(readLong("Subject ID: "))));
            case 0 -> { }
            default -> UI.warning("Choose a subject action from the list.");
        }
    }

        private static void showSubjects(List<Subject> subjects) {
        List<List<String>> rows = subjects.stream().map(subject -> List.of(
            String.valueOf(subject.subjectId()), subject.subjectCode(), subject.subjectName(),
            String.valueOf(subject.facultyId()))).toList();
        ConsoleTable.printPaged(List.of("ID", "Code", "Subject", "Faculty ID"), rows, INPUT, STYLE);
        }

    private static void manageEnrollments() throws SQLException {
        UI.menu("ENROLLMENT MANAGEMENT", List.of("List all enrollments", "Enroll student",
            "Remove enrollment", "Subjects for student", "Students in subject"));
        switch (readInt("Enrollment action: ")) {
            case 1 -> showEnrollments(CATALOG.listEnrollments());
            case 2 -> {
            Enrollment enrollment = CATALOG.createEnrollment(readLong("Student ID: "),
                readLong("Subject ID: "));
            UI.success("Student enrolled.");
            showEnrollments(List.of(enrollment));
            }
            case 3 -> UI.info(CATALOG.deleteEnrollment(readLong("Student ID: "),
                readLong("Subject ID: ")) ? "Enrollment removed." : "Enrollment not found.");
            case 4 -> showSubjects(CATALOG.subjectsForStudent(readLong("Student ID: ")));
            case 5 -> showStudents(CATALOG.studentsForSubject(readLong("Subject ID: ")));
            case 0 -> { }
            default -> UI.warning("Choose an enrollment action from the list.");
        }
    }

        private static void showEnrollments(List<Enrollment> enrollments) {
        List<List<String>> rows = enrollments.stream().map(enrollment -> List.of(
            String.valueOf(enrollment.studentId()), String.valueOf(enrollment.subjectId()),
            enrollment.enrolledAt().toString())).toList();
        ConsoleTable.printPaged(List.of("Student ID", "Subject ID", "Enrolled at"),
            rows, INPUT, STYLE);
        }

    private static void manageSessions() throws SQLException {
        UI.menu("CLASS SESSION MANAGEMENT", List.of("List sessions", "Create session",
            "Update session", "Delete session", "View session", "Filter subject/date range"));
        switch (readInt("Session action: ")) {
            case 1 -> showSessions(CATALOG.listSessions());
            case 2 -> showSessions(List.of(CATALOG.createSession(readLong("Subject ID: "),
                readDate("Class date (YYYY-MM-DD): "), readInt("Period (1-12): "))));
            case 3 -> showSessions(List.of(CATALOG.updateSession(readLong("Session ID: "),
                readLong("Subject ID: "), readDate("Class date (YYYY-MM-DD): "),
                readInt("Period (1-12): "))));
            case 4 -> UI.info(CATALOG.deleteSession(readLong("Session ID: "))
                ? "Session deleted." : "Session not found.");
            case 5 -> showSessions(List.of(CATALOG.findSession(readLong("Session ID: "))));
            case 6 -> showSessions(CATALOG.sessionsForSubjectAndDates(readLong("Subject ID: "),
                    readDate("Start date (YYYY-MM-DD): "),
                readDate("End date (YYYY-MM-DD): ")));
            case 0 -> { }
            default -> UI.warning("Choose a class-session action from the list.");
        }
    }

        private static void showSessions(List<ClassSession> sessions) {
        List<List<String>> rows = sessions.stream().map(session -> List.of(
            String.valueOf(session.sessionId()), String.valueOf(session.subjectId()),
            session.classDate().toString(), String.valueOf(session.periodNumber()),
            session.createdAt().toString())).toList();
        ConsoleTable.printPaged(List.of("ID", "Subject", "Date", "Period", "Created at"),
            rows, INPUT, STYLE);
        }

    private static void showAttendancePercentage() throws SQLException {
        int target = AttendancePolicy.targetPercentage();
        AttendancePercentage result = CATALOG.attendancePercentage(
                readLong("Student ID: "), readLong("Subject ID: "));
        if (result.totalSessions() == 0) {
            System.out.println("No scheduled sessions are available for this enrollment yet.");
            return;
        }
        long classesNeeded = AttendanceCalculator.classesNeeded(result.attendedSessions(),
            result.totalSessions(), target);
        UI.panel("ATTENDANCE SUMMARY", List.of("Present: " + result.presentSessions(),
                "Absent: " + result.absentSessions(), "On duty: " + result.onDutySessions(),
                "Unmarked: " + result.unmarkedSessions(),
                "Attended: " + result.attendedSessions() + " / " + result.totalSessions(),
                "Target: " + String.format("%.2f%% / %d%%", result.percentage(), target)));
        UI.info(UI.progressBar(result.percentage(), 32));
            UI.info(classesNeeded < 0 ? "The target cannot be reached after a missed session."
                : classesNeeded + " additional attended sessions needed for " + target + "%.");
    }

    private static void correctAttendance() throws SQLException {
        System.out.println("1. Update status  2. Delete record  0. Back");
        int action = readInt("Attendance action: ");
        if (action == 0) {
            return;
        }
        long sessionId = readLong("Session ID: ");
        long studentId = readLong("Student ID: ");
        Optional<AttendanceRecord> current = SERVICE.getAttendance(sessionId, studentId);
        if (current.isEmpty()) {
            UI.warning("No attendance record exists for that session and student.");
            return;
        }
        long facultyId = readLong("Assigned faculty ID: ");
        UI.info("The faculty ID is an assignment check only; this console does not authenticate identity.");
        switch (action) {
            case 1 -> {
                System.out.println("1. PRESENT  2. ABSENT  3. ON_DUTY");
                AttendanceStatus next = AttendanceStatus.fromMenuChoice(readInt("New status: "));
                String reason = readText("Correction reason: ");
                UI.panel("ATTENDANCE CORRECTION", List.of("Previous status: "
                        + current.orElseThrow().status(), "New status: " + next, "Reason: " + reason));
                if (!UI.confirm(INPUT, "Apply correction?")) {
                    UI.info("Correction cancelled.");
                    return;
                }
                SERVICE.updateAttendanceStatus(sessionId, studentId, next, facultyId, reason);
                UI.success("Attendance corrected and audit history recorded.");
            }
            case 2 -> {
                String reason = readText("Deletion reason: ");
                UI.panel("ATTENDANCE DELETION", List.of("Status: "
                        + current.orElseThrow().status(), "Reason: " + reason));
                if (!UI.confirm(INPUT, "Delete record and keep an audit record?")) {
                    UI.info("Deletion cancelled.");
                    return;
                }
                SERVICE.deleteAttendance(sessionId, studentId, facultyId, reason);
                UI.success("Attendance deleted and audit history recorded.");
            }
            default -> System.out.println("Invalid attendance action.");
        }
    }

    private static void viewAttendanceHistory() throws SQLException {
        System.out.println("1. By student  2. By session  3. By subject/date range "
            + "4. Session audit history  0. Back");
        switch (readInt("History filter: ")) {
                case 1 -> showAttendanceRecords(SERVICE.listByStudent(readLong("Student ID: ")));
                case 2 -> showAttendanceRecords(SERVICE.listBySession(readLong("Session ID: ")));
                case 3 -> showAttendanceRecords(SERVICE.listBySubjectAndDates(readLong("Subject ID: "),
                    readDate("Start date (YYYY-MM-DD): "),
                    readDate("End date (YYYY-MM-DD): ")));
                case 4 -> printAuditHistory(readLong("Session ID: "));
            case 0 -> { }
            default -> System.out.println("Invalid history filter.");
        }
    }

        private static void showAttendanceRecords(List<AttendanceRecord> records) {
        List<List<String>> rows = records.stream().map(record -> List.of(
            String.valueOf(record.attendanceId()), String.valueOf(record.sessionId()),
            String.valueOf(record.studentId()), record.registerNumber(), record.studentName(),
            record.status().name(), record.markedAt().toString())).toList();
        ConsoleTable.printPaged(List.of("ID", "Session", "Student ID", "Register", "Student",
            "Status", "Marked at"), rows, INPUT, STYLE);
    }

        private static void printAuditHistory(long sessionId) throws SQLException {
        List<AttendanceAuditRecord> history = SERVICE.auditHistory(sessionId);
        List<List<String>> rows = history.stream().map(event -> List.of(
            String.valueOf(event.auditId()), String.valueOf(event.studentId()), event.eventType(),
            event.previousStatus() == null ? "-" : event.previousStatus().name(),
            event.newStatus() == null ? "-" : event.newStatus().name(),
            event.reason() == null ? "-" : event.reason(),
            event.claimedFacultyId() == null ? "not recorded" : event.claimedFacultyId().toString(),
            event.recordedAt().toString())).toList();
        ConsoleTable.printPaged(List.of("Audit", "Student", "Event", "From", "To", "Reason",
            "Claimed faculty ID", "Recorded at"), rows, INPUT, STYLE);
        UI.warning("Claimed faculty ID is not an authenticated identity.");
        }

    private static void markAttendance() throws SQLException {
        long sessionId = readLong("Class session ID: ");
        ClassSession session = CATALOG.findSession(sessionId);
        Subject subject = CATALOG.findSubject(session.subjectId());
        Faculty faculty = CATALOG.findFaculty(subject.facultyId());
        UI.panel("CLASS SESSION", List.of("Subject: " + subject.subjectCode() + " - "
                + subject.subjectName(), "Date: " + session.classDate()
                + " | Period: " + session.periodNumber(), "Assigned faculty: " + faculty.fullName()));
        List<Student> roster = CATALOG.studentsForSubject(subject.subjectId());
        if (roster.isEmpty()) {
            UI.info("No students are enrolled in this subject; attendance cannot be marked.");
            return;
        }
        showStudents(roster);
        long studentId = readLong("Enrolled student ID: ");
        if (roster.stream().noneMatch(student -> student.studentId() == studentId)) {
            UI.error("That student is not on this subject's enrolled roster.");
            return;
        }
        if (SERVICE.getAttendance(sessionId, studentId).isPresent()) {
            UI.warning("Attendance already exists for this student/session; use correction explicitly.");
            return;
        }
        UI.menu("ATTENDANCE STATUS", List.of("PRESENT", "ABSENT", "ON_DUTY"));
        AttendanceStatus status = AttendanceStatus.fromMenuChoice(readInt("Status: "));
        Student student = roster.stream().filter(value -> value.studentId() == studentId)
                .findFirst().orElseThrow();
        UI.panel("CONFIRM ATTENDANCE", List.of("Session: " + session.sessionId(),
                "Student: " + student.fullName() + " (" + student.registerNumber() + ")",
                "Status: " + status));
        if (!UI.confirm(INPUT, "Save attendance?")) {
            UI.info("Attendance marking cancelled.");
            return;
        }
        AttendanceRecord saved = SERVICE.markAttendance(sessionId, studentId, status);
        UI.success("Attendance committed.");
        showAttendanceRecords(List.of(saved));
    }

    private static void retrieveAttendance() throws SQLException {
        long sessionId = readLong("Class session ID: ");
        long studentId = readLong("Student ID: ");
        Optional<AttendanceRecord> result = SERVICE.getAttendance(sessionId, studentId);
        if (result.isEmpty()) {
            UI.info("No attendance record found for that session and student.");
            return;
        }
        showAttendanceRecords(List.of(result.get()));
    }

    private static int readInt(String prompt) {
        return INPUT.readInt(prompt);
    }

    private static long readLong(String prompt) {
        return INPUT.readLong(prompt);
    }

    private static String readText(String prompt) {
        return INPUT.readText(prompt);
    }

    private static LocalDate readDate(String prompt) {
        return INPUT.readDate(prompt);
    }

}
