CREATE DATABASE IF NOT EXISTS attendiq_java_test
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE attendiq_java_test;

CREATE TABLE IF NOT EXISTS students (
    student_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    register_number VARCHAR(30) NOT NULL UNIQUE,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(160) UNIQUE
);

CREATE TABLE IF NOT EXISTS faculty (
    faculty_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS subjects (
    subject_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    subject_code VARCHAR(20) NOT NULL UNIQUE,
    subject_name VARCHAR(120) NOT NULL,
    faculty_id BIGINT NOT NULL,
    CONSTRAINT fk_test_subject_faculty FOREIGN KEY (faculty_id) REFERENCES faculty(faculty_id)
);

CREATE TABLE IF NOT EXISTS enrollments (
    student_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    enrolled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id, subject_id),
    CONSTRAINT fk_test_enrollment_student FOREIGN KEY (student_id)
        REFERENCES students(student_id) ON DELETE CASCADE,
    CONSTRAINT fk_test_enrollment_subject FOREIGN KEY (subject_id)
        REFERENCES subjects(subject_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS class_sessions (
    session_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    subject_id BIGINT NOT NULL,
    class_date DATE NOT NULL,
    period_number TINYINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_test_session_subject FOREIGN KEY (subject_id) REFERENCES subjects(subject_id),
    CONSTRAINT uq_test_subject_date_period UNIQUE (subject_id, class_date, period_number),
    CONSTRAINT chk_test_period CHECK (period_number BETWEEN 1 AND 12)
);

CREATE TABLE IF NOT EXISTS attendance_records (
    attendance_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    status ENUM('PRESENT', 'ABSENT', 'ON_DUTY') NOT NULL,
    marked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_test_attendance_session FOREIGN KEY (session_id)
        REFERENCES class_sessions(session_id) ON DELETE CASCADE,
    CONSTRAINT fk_test_attendance_student FOREIGN KEY (student_id)
        REFERENCES students(student_id) ON DELETE CASCADE,
    CONSTRAINT uq_test_attendance_session_student UNIQUE (session_id, student_id)
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
    CONSTRAINT fk_test_audit_session FOREIGN KEY (session_id) REFERENCES class_sessions(session_id),
    CONSTRAINT fk_test_audit_student FOREIGN KEY (student_id) REFERENCES students(student_id),
    CONSTRAINT fk_test_audit_faculty FOREIGN KEY (claimed_faculty_id) REFERENCES faculty(faculty_id)
);

CREATE TABLE IF NOT EXISTS schema_migrations (
    version VARCHAR(32) PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT IGNORE INTO schema_migrations (version, description)
VALUES ('V1', 'AttendIQ Core test schema');