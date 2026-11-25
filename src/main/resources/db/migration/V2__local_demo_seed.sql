INSERT INTO faculty(full_name,email,password_hash) VALUES ('Demo Faculty','faculty@attendiq.local',NULL);
INSERT INTO subjects(subject_code,subject_name,faculty_id) VALUES ('CS101','Computer Science',1),('MA101','Mathematics',1);
INSERT INTO students(register_number,full_name,email) VALUES ('STU001','Aarav Sharma','aarav@example.test'),('STU002','Maya Iyer','maya@example.test'),('STU003','Noah Patel','noah@example.test');
INSERT IGNORE INTO enrollments(student_id,subject_id) VALUES (1,1),(2,1),(3,1),(1,2),(2,2);
INSERT IGNORE INTO class_sessions(subject_id,class_date,period_number) VALUES (1,CURRENT_DATE,1),(2,CURRENT_DATE,1);
