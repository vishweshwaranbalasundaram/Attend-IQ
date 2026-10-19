# Attend-IQ Core Demo Script

## Before the Demo

1. Start MySQL and execute `src/main/resources/schema.sql` in MySQL Workbench.
2. Set `ATTENDIQ_DB_URL`, `ATTENDIQ_DB_USER`, and `ATTENDIQ_DB_PASSWORD` in PowerShell as described in the root README.
3. Run `mvn clean verify` and start the app with `mvn exec:java`.

## Walkthrough

1. Show the live dashboard and explain that the figures are queried from MySQL.
2. Test the database connection through System & Database.
3. List students, subjects, and sessions; identify demo IDs `1`.
4. Confirm student `1` is enrolled in subject `1`.
5. Mark attendance for session `1`, student `1`, status `PRESENT`; inspect and confirm the preview.
6. Retrieve the saved record and explain the transaction and unique constraint.
7. Open reports and explain Present + On-duty plus unmarked sessions in the denominator.
8. Export a report and demonstrate that an existing CSV requires confirmation before overwrite.
9. Correct attendance with a reason and inspect the audit history. Explain that the claimed faculty ID is not authenticated.
10. Demonstrate bulk marking using a fresh session; explain per-row commits and partial results.
11. Exit with main-menu option `0` (`12` remains accepted for compatibility).

Use a fresh session/fixture for repeat demos or remove only the demo attendance record through its explicit menu operation. Do not reset or drop the database during the demonstration.
