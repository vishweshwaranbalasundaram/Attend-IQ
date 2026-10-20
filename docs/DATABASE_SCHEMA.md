# Attend-IQ Database Schema

## Database and Tables

Attend-IQ connects to the MySQL database `attendiq_java` by default. SQL values are supplied through `PreparedStatement` parameters.

| Table | Purpose |
| --- | --- |
| `students` | Student identity and unique registration number |
| `faculty` | Faculty identity, email, and nullable PBKDF2 password hash |
| `subjects` | Subject identity and assigned faculty |
| `enrollments` | Student/subject many-to-many relationship |
| `class_sessions` | Dated class meetings by subject and period |
| `attendance_records` | One status per student/session |
| `attendance_audit_log` | Successful attendance marks, corrections, and deletions |
| `login_attempts` | Failed-login counter and temporary lock expiry per faculty account |
| `faculty_auth_audit_log` | Successful, failed, and locked login events; stores no password or hash |
| `schema_migrations` | Manually applied schema version history |

`faculty_auth_audit_log.attempted_faculty_id` also records unknown IDs, so it intentionally has no foreign key. `login_attempts.faculty_id` references `faculty` and is deleted with the faculty row.

## Attendance Rules

- Unique keys prevent duplicate enrollment, subject/date/period sessions, and attendance per student/session.
- Foreign keys preserve relationships. History protects attendance, enrollment, and session records from unsafe deletion.
- Attendance updates/deletes and their audit rows commit in one transaction.
- Corrections and deletions require a matching faculty password and the existing service check that the faculty member is assigned to the session.
- Login attempt state is updated transactionally with a password-free authentication audit event. Five consecutive failed attempts lock the account for five minutes; success clears the counter.

Attendance percentage is `100 * (PRESENT + ON_DUTY) / scheduled sessions`, counting sessions from enrollment through today. Unmarked sessions count in the denominator. Zero scheduled sessions returns 0%.

## Fresh Setup

1. Execute `src/main/resources/schema.sql` in MySQL Workbench, or start MySQL through `docker compose up -d mysql` as described in [TESTING.md](TESTING.md).
2. Set the JDBC URL, username, and password in local environment variables.
3. Verify database connectivity from the application.

The baseline schema includes the latest V1–V5 tables and optional demo rows. It uses `CREATE TABLE IF NOT EXISTS` and `INSERT IGNORE`; it does not drop or reset data.

## Existing Database Migration

Back up the database, then apply missing migrations in order. A V1 database needs V2, V3, V4, and V5:

1. `database/migrations/V2__student_name_search_index.sql`
2. `database/migrations/V3__attendance_audit_log.sql`
3. `database/migrations/V4__faculty_password_hash.sql`
4. `database/migrations/V5__faculty_login_lockout_and_audit.sql`

The application does not run migrations automatically. Apply V5 only after V4 has added `faculty.password_hash`. Existing rows retain a null password hash until a trusted administrator provisions them. New faculty set a password during creation. The initial-password setup menu for an existing null-hash account is disabled unless `ATTENDIQ_ALLOW_INITIAL_PASSWORD_SETUP=true` is set in a trusted setup session.

The authentication audit table stores only the attempted faculty ID, event type, and timestamp. Passwords and password hashes are never written to it.
