# Attend-IQ — Academic Project Report

## Problem Statement

Manual attendance workflows can be slow to reconcile, allow duplicate entries, and make it difficult to verify whether a student belongs to the class being marked. Attend-IQ Core demonstrates a small database-backed workflow that validates the class session and enrollment before recording a mark.

## Objectives

- Demonstrate Java OOP, JDBC, SQL, Maven, and MySQL persistence.
- Provide console-based CRUD for academic entities.
- Record attendance only for valid sessions and enrolled students.
- Prevent duplicate student/session marks at the database level.
- Produce transparent attendance summaries and shortage estimates.
- Keep credentials out of source code and protect historical records from accidental deletion.

## Scope

The Java 25 console application implements student, faculty, subject, enrollment, and class-session management; attendance create/read/update/delete; bulk marking; attendance history; dashboard aggregates; percentage/shortage reports; CSV exports; and a colored, paginated terminal interface. It has a local faculty password login for corrections and deletions, and no GUI or web API.

## Functional Requirements

- Create, view, list, update, and safely delete students, faculty, subjects, and class sessions.
- Enroll students in subjects and inspect enrollments by student or subject.
- Mark and retrieve attendance; search history by student, session, or subject/date interval.
- Verify session and enrollment; constrain status; reject duplicates.
- Allow a faculty ID assigned to the subject to correct/delete attendance.
- Calculate per-student and per-subject attendance plus below-threshold forecasts.

## Non-Functional Requirements

- JDBC parameters are bound with `PreparedStatement`.
- SQL and resources are contained in repository/data-access classes.
- Multi-step attendance and protected delete operations use transactions and rollback.
- Database constraints remain authoritative for uniqueness and references.
- Configuration uses environment variables; no passwords are committed.
- The baseline schema and V2/V3/V4/V5 migrations do not drop application data.
- V3 audit inserts share transactions with successful attendance changes; catalog edits and failed attempts are not audited.
- V3 audit inserts share attendance write transactions; successful attendance changes are audited.
- V5 locks a faculty account for five minutes after five failed logins and records password-free authentication audit events.

## OOP / DBMS Demonstration

`App` and `ConsoleInput` form the presentation layer. `StudentService`, `AcademicCatalogService`, and `AttendanceService` implement use cases. Repository interfaces abstract data operations and JDBC classes implement them, demonstrating abstraction and polymorphism through composition. Records and the status enum constrain returned domain data. No inheritance hierarchy is added without a genuine is-a relationship.

The normalized MySQL schema uses generated primary keys, foreign keys, unique student registration and subject codes, a composite enrollment key, a unique class-session schedule, and a unique attendance/session key. Attendance marking uses a transaction and verifies the session subject and enrollment before insert.

## Attendance Calculation

`100 * (present + on-duty) / scheduled sessions`, counting sessions from enrollment date through today. Missing marks appear separately as unmarked and remain in the denominator; zero sessions returns 0%. The shortage report computes the minimum number of consecutive attended sessions needed to reach the configured threshold.

## Test Status

On 2026-10-19, the earlier `mvn clean verify` run had 28 tests pass and one opt-in MySQL test skip. The updated GitHub Actions workflow provisions MySQL 8.4 and runs the integration test against `attendiq_java_test`; the updated suite has not yet been run locally.

## Limitations and Future Work

- Faculty passwords gate correction/deletion, but the local console login is not a production identity system.
- MySQL integration coverage is available but requires a dedicated test schema and credentials.
- Attendance records do not include late status in Core; the schema enum is intentionally unchanged.
- No login, student portal, graphical UI, or deployment setup is included in this console-only edition.
- Audit history does not cover catalog CRUD or failed attendance-authorization attempts; login failures are recorded separately.
- A true authentication/authorization layer would be required before exposing this to real users.
