# Attend-IQ Core Testing

## Default Test Suite

Run from the repository root with Java 25 and Maven 3.9+:

```powershell
mvn clean test
mvn clean verify
```

Pure unit tests do not require MySQL:

- `StudentServiceTest`: required fields, email validation, normalization, and invalid IDs.
- `AttendanceCalculatorTest`: percentages, zero sessions, target forecasts, impossible targets, invalid counts.
- `ConsoleInputTest`: valid values, empty/malformed input, range checks, and date parsing.
- `ConsoleStyleTest`, `ConsoleTableTest`, `ConsoleUITest`: no-color output, ANSI styling, truncation/alignment, and progress bars.
- `CsvExporterTest`: quoting, embedded newline/quote escaping, and no-overwrite behavior.
- `BulkAttendanceServiceTest`: existing rows, duplicate roster items, and partial failure reporting.
- `AttendancePolicyTest`: configurable target validation.
- `ConsoleStyleTest`, `ConsoleTableTest`, `ConsoleUITest`: ANSI/no-color behavior, responsive tables, and progress bars.
- `CsvExporterTest`: UTF-8 quoting, embedded quotes/newlines, and overwrite safety.
- `BulkAttendanceServiceTest`: existing rows, duplicate roster items, and partial-failure reporting.

The latest recorded Surefire result for this working tree was 14 unit tests passed, 0 failed, and 1 MySQL integration test skipped.

## MySQL Integration Test

`AttendanceMySqlIntegrationTest` is opt-in. It exercises real persistence, retrieval through a new connection, duplicate/concurrent rejection, enrollment/session validation, faculty assignment checks, transactional audit history, dashboard aggregates, protected deletes, and cleanup of unique test fixtures.

1. Create the dedicated test schema by executing `src/test/resources/schema-test.sql` in MySQL Workbench. It creates `attendiq_java_test` with no demo rows.
2. Configure environment variables in PowerShell:

```powershell
$env:ATTENDIQ_DB_URL = 'jdbc:mysql://localhost:3306/attendiq_java_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
$env:ATTENDIQ_DB_USER = 'YOUR_TEST_DB_USER'
$env:ATTENDIQ_DB_PASSWORD = 'YOUR_TEST_DB_PASSWORD'
$env:ATTENDIQ_RUN_MYSQL_IT = 'true'
```

3. Run only the integration test:

```powershell
mvn -Dtest=AttendanceMySqlIntegrationTest test
```

The test checks the connected catalog name ends with `_test` before writing. Do not set the integration flag while targeting `attendiq_java`, a personal database, or production.

## Current Verification Limits

The unit suite and full Maven test compilation have been executed. The real MySQL integration test has not been run in this environment because the dedicated `_test` schema and test credentials were not configured. No MySQL success is inferred from the unit suite.
