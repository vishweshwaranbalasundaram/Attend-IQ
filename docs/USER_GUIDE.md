# Attend-IQ User Guide

Attend-IQ is a Java console program backed by MySQL. It uses a local faculty password login for attendance corrections and deletions.

## Start the application

Use JDK 25+, Maven 3.9+, and MySQL 8+. From the `java-attendance` project directory, configure the session variables shown in the [README setup](../README.md#setup-and-run-windows-powershell), then run:

```powershell
mvn clean verify
mvn exec:java
```

For a new local database, execute `src/main/resources/schema.sql` in MySQL Workbench or use `docker compose up -d mysql`. For an existing V1 database, back it up and apply V2 through V5 in order from `database/migrations/`. The application does not automatically migrate an existing database.

## Navigate menus

The main menu contains Dashboard, Student Management, Faculty Management, Subject Management, Enrollment Management, Class Session Management, Attendance Management, Reports & Analytics, Export Reports, System & Database, Help & Shortcuts, and Exit. Enter `0` to exit; `12` is also accepted for compatibility. Enter `0` in a submenu to return to the main menu.

The dashboard queries current database totals, today's scheduled sessions and enrolled-roster status, the configured below-target count, and recent attendance audit events. Its quick actions invoke the same application services as the corresponding menus. Database failures are reported rather than shown as invented zero values.

## Manage academic records

- **Students:** list, add, update, delete, view by ID, and search by name or registration-number prefix. Deletion is protected when enrollment or attendance history exists.
- **Faculty:** list, add with an initial password, update, delete, view, inspect assigned subjects, and set/change passwords. Configured accounts must authenticate before changing passwords. Initial setup for a legacy null-hash account requires a trusted session with `ATTENDIQ_ALLOW_INITIAL_PASSWORD_SETUP=true`.
- **Subjects:** list, add, update, delete, and view. Subjects require an existing faculty member; related enrollments and sessions protect deletion.
- **Enrollments:** add or remove a student/subject relationship, list all relationships, or inspect subjects for a student and students in a subject. Attendance history protects the relationship from removal.
- **Class sessions:** create, list, view, update, delete, or filter by subject and date range. Sessions with attendance history cannot be changed or deleted.

Tables page long result sets. Press Enter for another page or `q` to stop. Empty results have an explicit empty state.

## Record attendance

For a single mark, select a session, choose a student from the enrolled roster, choose `PRESENT`, `ABSENT`, or `ON_DUTY`, review the summary, and confirm. The service checks the session and enrollment, then commits the insert. A unique database constraint prevents a second mark for the same student/session; use the correction workflow to change an existing mark.

Bulk marking displays the enrolled roster and existing records, asks for a status for each unmarked student, previews the proposal, then requests confirmation. Existing records are skipped. Each accepted row commits independently, so partial successes and failures are reported accurately.

To correct or delete a mark, provide the faculty ID assigned to the subject, its password, a reason, and confirmation. Password input is hidden when the app runs in a real terminal. Five failed logins lock the account for five minutes. Successful attendance changes and their audit rows commit together.

## Reports and attendance policy

Reports include student/subject percentages, subject summaries, shortages, and filtered attendance history. `PRESENT` and `ON_DUTY` count as attended. `ABSENT` and unmarked scheduled sessions do not count as attended; unmarked sessions are separately visible and remain in the denominator. The percentage is `100 × (present + on-duty) / scheduled sessions`. Zero sessions yields 0% with an explanatory message. The default shortage threshold is 75%; configure `ATTENDIQ_ATTENDANCE_TARGET` to an integer from 1 to 100.

## Export CSV

Choose Export Reports and select a student list, attendance filter, subject summary, or shortage report. Enter a destination or press Enter to use the timestamped `exports/` default. Values are quoted and escaped as UTF-8 CSV. Existing files require explicit overwrite confirmation.

## Database diagnostics and troubleshooting

System & Database can test connectivity, display Java/database server versions and the catalog name, and show the last successful in-process connection test. Passwords are never displayed. Configure `ATTENDIQ_DB_URL`, `ATTENDIQ_DB_USER`, and `ATTENDIQ_DB_PASSWORD` in the PowerShell session that starts Maven. For plain output set `ATTENDIQ_COLOR=never` or `NO_COLOR=1`; use `ATTENDIQ_COLOR=always` to force ANSI colors.

- **Connection refused:** start MySQL and check the host/port in `ATTENDIQ_DB_URL`.
- **Access denied:** check the configured account and password.
- **Unknown database/table:** execute the schema or documented versioned migrations manually.
- **Duplicate or integrity error:** inspect identifiers and related records; application safeguards preserve history.

For integration-test safety and commands, see [TESTING.md](TESTING.md). The integration test must target a separately prepared database whose name ends in `_test`.
