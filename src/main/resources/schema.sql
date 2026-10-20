CREATE DATABASE IF NOT EXISTS attendiq_java
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE attendiq_java;

CREATE TABLE IF NOT EXISTS students (
    student_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    register_number VARCHAR(30) NOT NULL UNIQUE,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(160) UNIQUE
);

CREATE TABLE IF NOT EXISTS faculty (
    faculty_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NULL
);

CREATE TABLE IF NOT EXISTS subjects (
    subject_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    subject_code VARCHAR(20) NOT NULL UNIQUE,
    subject_name VARCHAR(120) NOT NULL,
    faculty_id BIGINT NOT NULL,
    CONSTRAINT fk_subject_faculty FOREIGN KEY (faculty_id)
        REFERENCES faculty(faculty_id)
);

CREATE TABLE IF NOT EXISTS enrollments (
    student_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    enrolled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id, subject_id),
    CONSTRAINT fk_enrollment_student FOREIGN KEY (student_id)
        REFERENCES students(student_id) ON DELETE CASCADE,
    CONSTRAINT fk_enrollment_subject FOREIGN KEY (subject_id)
        REFERENCES subjects(subject_id) ON DELETE CASCADE,
    INDEX idx_enrollments_subject_student (subject_id, student_id)
);

CREATE TABLE IF NOT EXISTS class_sessions (
    session_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    subject_id BIGINT NOT NULL,
    class_date DATE NOT NULL,
    period_number TINYINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_subject FOREIGN KEY (subject_id)
        REFERENCES subjects(subject_id),
    CONSTRAINT uq_subject_date_period UNIQUE (subject_id, class_date, period_number),
    CONSTRAINT chk_period CHECK (period_number BETWEEN 1 AND 12),
    INDEX idx_class_sessions_subject_date (subject_id, class_date)
);

CREATE TABLE IF NOT EXISTS attendance_records (
    attendance_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    status ENUM('PRESENT', 'ABSENT', 'ON_DUTY') NOT NULL,
    marked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attendance_session FOREIGN KEY (session_id)
        REFERENCES class_sessions(session_id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_student FOREIGN KEY (student_id)
        REFERENCES students(student_id) ON DELETE CASCADE,
    CONSTRAINT uq_attendance_per_student_session UNIQUE (session_id, student_id),
    INDEX idx_attendance_student_session (student_id, session_id)
);

CREATE TABLE IF NOT EXISTS attendance_audit_log (
    audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    event_type ENUM('MARKED', 'CORRECTED', 'DELETED') NOT NULL,
    previous_status ENUM('PRESENT', 'ABSENT', 'ON_DUTY') NULL,
    new_status ENUM('PRESENT', 'ABSENT', 'ON_DUTY') NULL,
    reason VARCHAR(1000) NULL,
    claimed_faculty_id BIGINT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attendance_audit_session FOREIGN KEY (session_id)
        REFERENCES class_sessions(session_id),
    CONSTRAINT fk_attendance_audit_student FOREIGN KEY (student_id)
        REFERENCES students(student_id),
    CONSTRAINT fk_attendance_audit_faculty FOREIGN KEY (claimed_faculty_id)
        REFERENCES faculty(faculty_id),
    INDEX idx_attendance_audit_session_student (session_id, student_id),
    INDEX idx_attendance_audit_recorded_at (recorded_at)
);

CREATE TABLE IF NOT EXISTS schema_migrations (
    version VARCHAR(32) PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS login_attempts (
    faculty_id BIGINT PRIMARY KEY,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_login_attempt_faculty FOREIGN KEY (faculty_id)
        REFERENCES faculty(faculty_id) ON DELETE CASCADE,
    CONSTRAINT chk_login_failed_attempts CHECK (failed_attempts >= 0)
);

CREATE TABLE IF NOT EXISTS faculty_auth_audit_log (
    audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    attempted_faculty_id BIGINT NOT NULL,
    event_type ENUM('LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGIN_LOCKED') NOT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_faculty_auth_audit_attempted (attempted_faculty_id, recorded_at),
    INDEX idx_faculty_auth_audit_recorded_at (recorded_at)
);

INSERT IGNORE INTO schema_migrations (version, description) VALUES
    ('V1', 'AttendIQ Core baseline schema'),
    ('V2', 'Student name search index'),
    ('V3', 'Attendance audit log'),
    ('V4', 'Faculty password hash'),
    ('V5', 'Faculty login lockout and authentication audit');

-- Optional demo records. Run once after creating the schema.
INSERT IGNORE INTO faculty (faculty_id, full_name, email)
VALUES (1, 'Demo Faculty', 'faculty@example.edu');

INSERT IGNORE INTO students (student_id, register_number, full_name, email)
VALUES (1, 'REC26CS001', 'Demo Student', 'student@example.edu');

INSERT IGNORE INTO subjects (subject_id, subject_code, subject_name, faculty_id)
VALUES (1, 'CS23333', 'Object Oriented Programming', 1);

INSERT IGNORE INTO enrollments (student_id, subject_id) VALUES (1, 1);

INSERT IGNORE INTO class_sessions (session_id, subject_id, class_date, period_number)
VALUES (1, 1, CURRENT_DATE, 1);
