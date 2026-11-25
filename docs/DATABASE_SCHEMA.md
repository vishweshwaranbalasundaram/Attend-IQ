# Attend-IQ V2 Database Schema

Attend-IQ uses MySQL 8 for both the original CLI and web portal. Flyway migrations in `src/main/resources/db/migration/` are the authoritative schema history; the web application uses JDBC and `JdbcTemplate`, not ORM-generated DDL.

## Schema and migrations

| Migration | Purpose |
|---|---|
| `V1__initial_schema.sql` | Academic catalog, students, faculty, enrollments, sessions, attendance, legacy audit and login tables |
| `V2__local_demo_seed.sql` | Local academic and demo seed rows |
| `V3__portal_profiles_and_auth.sql` | Profile fields and OTP authentication records |
| `V4__demo_portal_accounts_and_attendance.sql` | Local portal account and attendance demo data |
| `V5__firebase_qr_and_od.sql` | QR sessions and token hashes, portal audit events, and OD/medical requests |
| `V6__geofence_review_flags.sql` | Review queue for geofence-related attendance flags |

The standard Flyway lifecycle applies pending migrations at application startup. `V2` and `V4` contain demo-oriented data; do not treat their sample rows as production accounts.

## Table catalog

| Table | Purpose |
|---|---|
| `students` | Student identity, registration number, program, semester and cohort |
| `faculty` | Staff identity and legacy password-hash field |
| `subjects` | Courses and assigned faculty |
| `enrollments` | Student-to-subject association |
| `class_sessions` | Dated class meetings, period and subject |
| `attendance_records` | One attendance status per student and class session |
| `attendance_audit_log` | Legacy attendance mark, correction and deletion events |
| `attendance_audit_logs` | Portal audit entries with actor email, reason and IP address |
| `attendance_qr_sessions` | Timed QR session settings and closure timestamps |
| `attendance_qr_tokens` | Rotating token bucket, hash and expiry; raw QR tokens are not stored |
| `on_duty_requests` | OD and medical requests and staff review state |
| `attendance_geofence_flags` | Geofence review flags, distance, accuracy and review state |
| `auth_otp` | Email, role, OTP hash, expiry and failed-attempt count |
| `login_attempts` | Legacy faculty login lockout state |
| `faculty_auth_audit_log` | Legacy password-free faculty login events |

## Integrity and transaction notes

- Primary keys identify rows; unique keys prevent duplicate registration numbers, emails, enrollments, class periods, attendance entries, QR token hashes, and repeated token buckets within a QR session.
- Foreign keys connect students, faculty, courses, enrollments, sessions, attendance, QR sessions, OD requests, and geofence flags.
- Attendance percentage calculations treat `PRESENT`, `LATE`, and `ON_DUTY` as attended statuses.
- The portal uses Spring-managed transactions for multi-write attendance and workflow operations. The console repository also owns explicit JDBC transactions for attendance writes.
- `attendance_audit_log` is the legacy CLI audit table. `attendance_audit_logs` is the portal audit table; both are queried by the student audit view.
- The application has no configured application-level cache.

## Setup and operations

Local development uses the MySQL service from Docker Compose. Production configuration targets MySQL on Cloud SQL through the Cloud SQL Connector. Configure connection credentials and project identifiers through the environment; do not commit local `.env` files or cloud credentials.

The `.sql` files under `src/main/resources/db/migration/` are managed by Flyway. Avoid manually applying them to a live database outside a planned migration procedure. Back up the database and test upgrades against a disposable copy before production rollout.

See [the detailed entity model and database configuration](../.github/modernize/assessment/engines/facts/data-architecture.md).
