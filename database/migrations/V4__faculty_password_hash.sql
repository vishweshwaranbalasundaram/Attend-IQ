-- V4: faculty login support (PBKDF2 password hash stored, never the password).
-- Run once on an existing attendiq_java database.
USE attendiq_java;

ALTER TABLE faculty ADD COLUMN password_hash VARCHAR(255) NULL;

INSERT IGNORE INTO schema_migrations (version, description)
VALUES ('V4', 'Faculty password hash for authenticated corrections');
