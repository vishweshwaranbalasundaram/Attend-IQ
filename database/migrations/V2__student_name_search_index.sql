CREATE TABLE IF NOT EXISTS schema_migrations (
  version VARCHAR(32) PRIMARY KEY,
  description VARCHAR(255) NOT NULL,
  applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

SET @index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'students'
      AND index_name = 'idx_students_full_name'
);
SET @migration_sql = IF(@index_exists = 0,
    'CREATE INDEX idx_students_full_name ON students (full_name)', 'SELECT 1');
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'enrollments'
      AND index_name = 'idx_enrollments_subject_student'
);
SET @migration_sql = IF(@index_exists = 0,
    'CREATE INDEX idx_enrollments_subject_student ON enrollments (subject_id, student_id)',
    'SELECT 1');
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'class_sessions'
      AND index_name = 'idx_class_sessions_subject_date'
);
SET @migration_sql = IF(@index_exists = 0,
    'CREATE INDEX idx_class_sessions_subject_date ON class_sessions (subject_id, class_date)',
    'SELECT 1');
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'attendance_records'
      AND index_name = 'idx_attendance_student_session'
);
SET @migration_sql = IF(@index_exists = 0,
    'CREATE INDEX idx_attendance_student_session ON attendance_records (student_id, session_id)',
    'SELECT 1');
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

INSERT IGNORE INTO schema_migrations (version, description)
VALUES ('V2', 'Add indexes for student search and attendance lookups');