UPDATE faculty
SET email=CONCAT('k.arun@', '${allowed_email_domain}')
WHERE full_name='Demo Faculty' AND email LIKE 'k.arun@%';

UPDATE students SET email=CASE register_number
    WHEN 'STU001' THEN CONCAT('aarav.s.2023.cse@', '${allowed_email_domain}')
    WHEN 'STU002' THEN CONCAT('maya.i.2024.aids@', '${allowed_email_domain}')
    WHEN 'STU003' THEN CONCAT('noah.p.2022.it@', '${allowed_email_domain}')
    WHEN 'STU004' THEN CONCAT('leah.m.2023.it@', '${allowed_email_domain}')
    WHEN 'STU005' THEN CONCAT('ishan.r.2024.ece@', '${allowed_email_domain}')
    WHEN 'STU006' THEN CONCAT('zara.t.2022.mech@', '${allowed_email_domain}')
    ELSE email
END
WHERE register_number IN ('STU001','STU002','STU003','STU004','STU005','STU006');
