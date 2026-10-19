# AttendIQ — Java OOP + MySQL MVP

This module is intentionally separate from the existing Python AttendIQ application. It is the first academic vertical slice: validate enrollment, validate a class session, save attendance through JDBC, retrieve it, and reject duplicate marks.

## Requirements

- JDK 25 or newer
- Maven 3.9+
- MySQL 8+
- VS Code with Extension Pack for Java (optional)

## 1. Create the database

Open MySQL Workbench, open `src/main/resources/schema.sql`, and execute the entire script. It creates the `attendiq_java` database and demo records:
- Student ID `1`
- Subject ID `1`
- Class session ID `1`
- Student 1 is enrolled in subject 1

If you already have data with those IDs, use a fresh development database or adjust the demo IDs.

## 2. Configure connection settings (Windows PowerShell)

Use environment variables in the same terminal where you run Maven. Replace the password with your local MySQL password; don't commit it.

```powershell
$env:ATTENDIQ_DB_URL = "jdbc:mysql://localhost:3306/attendiq_java?useSSL=false&serverTimezone=UTC"
$env:ATTENDIQ_DB_USER = "root"
$env:ATTENDIQ_DB_PASSWORD = "YOUR_MYSQL_PASSWORD"
```

If your MySQL root account has no password, set the password variable to an empty string.

## 3. Run the app

From this directory:

```powershell
mvn clean compile
mvn exec:java
```

Try:
- Mark attendance: session `1`, student `1`, status `1` (PRESENT).
- Retrieve attendance: session `1`, student `1`.
- Mark the same student/session again: the database unique constraint should reject the duplicate.

The demo session is dated using `CURRENT_DATE` when the SQL script runs.

## OOP and DBMS concepts demonstrated

- **Encapsulation / separation of concerns:** `AttendanceService` owns the workflow; `AttendanceRepository` owns SQL.
- **Enum:** `AttendanceStatus` restricts valid values.
- **Record:** `AttendanceRecord` is an immutable data carrier.
- **JDBC:** `Connection`, `PreparedStatement`, `ResultSet`, and generated keys.
- **Transactions:** attendance validation and insert use a transaction with rollback on failure.
- **Relational design:** primary keys, foreign keys, a composite enrollment key, and unique constraints.
- **Duplicate prevention:** `UNIQUE(session_id, student_id)` is enforced by MySQL, not just the UI.
- **CRUD foundation:** this first milestone implements create and read for attendance. Student/subject CRUD will be added next.

## Current scope and limitations

This is a starter MVP, not the finished project. It does not yet implement login, faculty authorization, student/subject CRUD menus, attendance percentage reports, GUI, or automated integration tests. The database validates foreign keys and duplicate records; faculty authorization must be added before using this with real users.

Keep the original Python app unchanged. Add this folder beside it as `java-attendance/`.
