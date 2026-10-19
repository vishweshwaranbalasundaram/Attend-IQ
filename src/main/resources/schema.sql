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
    email VARCHAR(160) NOT NULL UNIQUE
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
        REFERENCES subjects(subject_id) ON DELETE CASCADE
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
    CONSTRAINT chk_period CHECK (period_number BETWEEN 1 AND 12)
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
    CONSTRAINT uq_attendance_per_student_session UNIQUE (session_id, student_id)
);

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
