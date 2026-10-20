-- V5: track faculty login failures and keep a password-free authentication audit trail.
USE attendiq_java;

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

INSERT IGNORE INTO schema_migrations (version, description)
VALUES ('V5', 'Faculty login lockout and authentication audit');
