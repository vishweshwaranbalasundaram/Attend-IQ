CREATE TABLE attendance_geofence_flags (
    flag_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    qr_session_id BIGINT NOT NULL,
    class_session_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    flag_reason ENUM('LOW_ACCURACY', 'NEAR_GEOFENCE_EDGE') NOT NULL,
    distance_meters DECIMAL(9,2) NOT NULL,
    accuracy_meters DECIMAL(8,2) NOT NULL,
    review_status ENUM('OPEN', 'REVIEWED') NOT NULL DEFAULT 'OPEN',
    reviewed_by_faculty_id BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_geofence_flag_qr_session FOREIGN KEY (qr_session_id)
        REFERENCES attendance_qr_sessions(qr_session_id),
    CONSTRAINT fk_geofence_flag_class_session FOREIGN KEY (class_session_id)
        REFERENCES class_sessions(session_id),
    CONSTRAINT fk_geofence_flag_student FOREIGN KEY (student_id)
        REFERENCES students(student_id),
    CONSTRAINT fk_geofence_flag_reviewer FOREIGN KEY (reviewed_by_faculty_id)
        REFERENCES faculty(faculty_id),
    CONSTRAINT uq_geofence_flag_attempt UNIQUE (qr_session_id, student_id, flag_reason),
    INDEX idx_geofence_flag_review (review_status, created_at),
    INDEX idx_geofence_flag_qr_session (qr_session_id, created_at)
);
