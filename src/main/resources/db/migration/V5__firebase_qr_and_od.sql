ALTER TABLE attendance_records
    MODIFY COLUMN status ENUM('PRESENT', 'LATE', 'ABSENT', 'ON_DUTY') NOT NULL;

ALTER TABLE attendance_audit_log
    MODIFY COLUMN previous_status ENUM('PRESENT', 'LATE', 'ABSENT', 'ON_DUTY') NULL,
    MODIFY COLUMN new_status ENUM('PRESENT', 'LATE', 'ABSENT', 'ON_DUTY') NULL;

UPDATE faculty SET email='k.arun@rajalakshmi.edu.in'
WHERE email='faculty@attendiq.edu';

UPDATE students SET email=CASE register_number
    WHEN 'STU001' THEN 'aarav.s.2023.cse@rajalakshmi.edu.in'
    WHEN 'STU002' THEN 'maya.i.2024.aids@rajalakshmi.edu.in'
    WHEN 'STU003' THEN 'noah.p.2022.it@rajalakshmi.edu.in'
    WHEN 'STU004' THEN 'leah.m.2023.it@rajalakshmi.edu.in'
    WHEN 'STU005' THEN 'ishan.r.2024.ece@rajalakshmi.edu.in'
    WHEN 'STU006' THEN 'zara.t.2022.mech@rajalakshmi.edu.in'
    ELSE email
END
WHERE register_number IN ('STU001','STU002','STU003','STU004','STU005','STU006');

CREATE TABLE attendance_qr_sessions (
    qr_session_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    class_session_id BIGINT NOT NULL UNIQUE,
    created_by_faculty_id BIGINT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    closes_at DATETIME(6) NOT NULL,
    closed_at DATETIME(6) NULL,
    grace_minutes SMALLINT NOT NULL,
    late_cutoff_minutes SMALLINT NOT NULL,
    campus_latitude DECIMAL(10,7) NOT NULL,
    campus_longitude DECIMAL(10,7) NOT NULL,
    geofence_radius_meters SMALLINT NOT NULL,
    max_accuracy_meters SMALLINT NOT NULL,
    CONSTRAINT fk_qr_class_session FOREIGN KEY (class_session_id)
        REFERENCES class_sessions(session_id),
    CONSTRAINT fk_qr_faculty FOREIGN KEY (created_by_faculty_id)
        REFERENCES faculty(faculty_id),
    CONSTRAINT chk_qr_session_times CHECK (closes_at > started_at),
    CONSTRAINT chk_qr_session_grace CHECK (grace_minutes >= 0 AND late_cutoff_minutes >= grace_minutes),
    INDEX idx_qr_session_close (closed_at, closes_at)
);

CREATE TABLE attendance_qr_tokens (
    token_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    qr_session_id BIGINT NOT NULL,
    token_bucket BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_qr_token_session FOREIGN KEY (qr_session_id)
        REFERENCES attendance_qr_sessions(qr_session_id) ON DELETE CASCADE,
    CONSTRAINT uq_qr_token_bucket UNIQUE (qr_session_id, token_bucket),
    CONSTRAINT uq_qr_token_hash UNIQUE (token_hash),
    INDEX idx_qr_token_expiry (expires_at)
);

CREATE TABLE attendance_audit_logs (
    audit_log_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    actor_email VARCHAR(254) NOT NULL,
    old_status ENUM('PRESENT', 'LATE', 'ABSENT', 'ON_DUTY') NULL,
    new_status ENUM('PRESENT', 'LATE', 'ABSENT', 'ON_DUTY') NULL,
    reason VARCHAR(1000) NOT NULL,
    ip_address VARCHAR(45) NULL,
    `timestamp` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_audit_log_session FOREIGN KEY (session_id)
        REFERENCES class_sessions(session_id),
    CONSTRAINT fk_audit_log_student FOREIGN KEY (student_id)
        REFERENCES students(student_id),
    INDEX idx_audit_log_student_time (student_id, `timestamp`),
    INDEX idx_audit_log_session_time (session_id, `timestamp`)
);

CREATE TABLE on_duty_requests (
    request_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    request_type ENUM('ON_DUTY', 'MEDICAL') NOT NULL,
    start_date DATE NOT NULL,
    start_period TINYINT NOT NULL,
    end_date DATE NOT NULL,
    end_period TINYINT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    proof_metadata VARCHAR(1000) NULL,
    status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    reviewed_by_faculty_id BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_od_student FOREIGN KEY (student_id)
        REFERENCES students(student_id),
    CONSTRAINT fk_od_reviewer FOREIGN KEY (reviewed_by_faculty_id)
        REFERENCES faculty(faculty_id),
    CONSTRAINT chk_od_start_period CHECK (start_period BETWEEN 1 AND 12),
    CONSTRAINT chk_od_end_period CHECK (end_period BETWEEN 1 AND 12),
    CONSTRAINT chk_od_date_range CHECK (end_date >= start_date),
    INDEX idx_od_status_created (status, created_at),
    INDEX idx_od_student_range (student_id, start_date, end_date)
);
