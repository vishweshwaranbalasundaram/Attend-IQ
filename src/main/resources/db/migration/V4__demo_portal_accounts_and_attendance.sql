UPDATE students SET email=LOWER(CONCAT(register_number,'@students.attendiq.edu'))
WHERE email IS NULL OR email LIKE '%@example.test';
UPDATE faculty SET email='faculty@attendiq.edu'
WHERE email LIKE '%@attendiq.local';
UPDATE subjects SET faculty_id=(SELECT faculty_id FROM faculty WHERE email='faculty@attendiq.edu')
WHERE subject_code IN ('CS101','MA101');

INSERT IGNORE INTO students(register_number,full_name,email,program,semester,batch_label) VALUES
    ('STU004','Leah Martin','stu004@students.attendiq.edu','Information Technology',5,'2023-2027'),
    ('STU005','Ishan Rao','stu005@students.attendiq.edu','Electronics and Communication Engineering',3,'2024-2028'),
    ('STU006','Zara Thomas','stu006@students.attendiq.edu','Mechanical Engineering',7,'2022-2026');

UPDATE students SET program=CASE
    WHEN register_number IN ('STU001') THEN 'Computer Science and Engineering'
    WHEN register_number='STU002' THEN 'Artificial Intelligence and Data Science'
    WHEN register_number='STU003' THEN 'Information Technology'
    ELSE program END,
    semester=CASE WHEN register_number='STU001' THEN 5 WHEN register_number='STU002' THEN 3 WHEN register_number='STU003' THEN 7 ELSE semester END,
    batch_label=CASE WHEN register_number='STU001' THEN '2023-2027' WHEN register_number='STU002' THEN '2024-2028' WHEN register_number='STU003' THEN '2022-2026' ELSE batch_label END;

UPDATE subjects SET department=CASE
    WHEN subject_code='CS101' THEN 'Computer Science and Engineering'
    WHEN subject_code='MA101' THEN 'Mathematics'
    ELSE subject_name END;

INSERT IGNORE INTO enrollments(student_id,subject_id)
SELECT st.student_id,s.subject_id FROM students st CROSS JOIN subjects s
WHERE st.register_number LIKE 'STU00%' AND s.subject_code IN ('CS101','MA101');

UPDATE enrollments e JOIN students st ON st.student_id=e.student_id JOIN subjects s ON s.subject_id=e.subject_id
SET e.enrolled_at=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 30 DAY)
WHERE st.register_number LIKE 'STU00%' AND s.subject_code IN ('CS101','MA101');

INSERT IGNORE INTO class_sessions(subject_id,class_date,period_number)
SELECT s.subject_id,DATE_SUB(CURRENT_DATE,INTERVAL seq.day_offset DAY),1
FROM subjects s CROSS JOIN (
    SELECT 0 day_offset UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) seq WHERE s.subject_code IN ('CS101','MA101');

INSERT IGNORE INTO attendance_records(session_id,student_id,status)
SELECT cs.session_id,st.student_id,
    CASE WHEN st.register_number='STU002' AND DATEDIFF(CURRENT_DATE,cs.class_date)=2 THEN 'ON_DUTY'
         WHEN DATEDIFF(CURRENT_DATE,cs.class_date) < CASE st.register_number
             WHEN 'STU001' THEN 8 WHEN 'STU002' THEN 7
             WHEN 'STU003' THEN 6 WHEN 'STU004' THEN 9 ELSE 5 END THEN 'PRESENT'
         ELSE 'ABSENT' END
FROM class_sessions cs JOIN subjects s ON s.subject_id=cs.subject_id
JOIN enrollments e ON e.subject_id=s.subject_id
JOIN students st ON st.student_id=e.student_id
WHERE s.subject_code IN ('CS101','MA101') AND st.register_number LIKE 'STU00%';
