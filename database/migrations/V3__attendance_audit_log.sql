CREATE TABLE IF NOT EXISTS schema_migrations (
    version VARCHAR(32) PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
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

INSERT IGNORE INTO schema_migrations (version, description)
VALUES ('V3', 'Add transactional attendance audit history');