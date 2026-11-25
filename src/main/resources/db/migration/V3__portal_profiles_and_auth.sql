ALTER TABLE students
    ADD COLUMN program VARCHAR(120) NOT NULL DEFAULT 'General Engineering',
    ADD COLUMN semester TINYINT NOT NULL DEFAULT 1,
    ADD COLUMN batch_label VARCHAR(30) NOT NULL DEFAULT '2025-2029';

ALTER TABLE subjects
    ADD COLUMN department VARCHAR(120) NOT NULL DEFAULT 'General Engineering';

ALTER TABLE attendance_audit_log
    ADD COLUMN staff_email VARCHAR(160) NULL;

CREATE TABLE auth_otp (
    email VARCHAR(160) PRIMARY KEY,
    role VARCHAR(16) NOT NULL,
    otp_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    failed_attempts TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_auth_otp_role CHECK (role IN ('STUDENT','STAFF'))
);
