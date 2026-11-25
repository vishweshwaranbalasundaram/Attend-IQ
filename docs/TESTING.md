# Attend-IQ V2 Testing

## Local builds

Run checks from the repository root with Java 25, Maven 3.9+, Node.js 22, and npm:

```powershell
mvn clean verify
Push-Location web
npm ci
npm run build
Pop-Location
Push-Location functions
npm ci
npm run build
Pop-Location
```

The Java suite covers the existing attendance console/domain behavior plus eligibility math, institutional identity rules, QR signing, geofence policy, and demo account generation. MySQL integration tests are opt-in; the default `mvn clean verify` skips tests annotated for `ATTENDIQ_RUN_MYSQL_IT=true`.

## MySQL integration tests

Use a dedicated database whose name ends with `_test`. The integration suite refuses to use a database without that suffix. CI creates `attendiq_java_test` from `src/test/resources/schema-test.sql` and runs the full Maven verification with the opt-in flag. The suite includes legacy MySQL persistence/authentication checks and the web API OTP/RBAC/session tests.

For a local run, create the isolated database from the test schema, then set the connection details in the current PowerShell session:

```powershell
mysql -h 127.0.0.1 -uroot -p < src/test/resources/schema-test.sql
$env:ATTENDIQ_DB_URL = 'jdbc:mysql://localhost:3306/attendiq_java_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
$env:ATTENDIQ_DB_USER = 'YOUR_TEST_DB_USER'
$env:ATTENDIQ_DB_PASSWORD = 'YOUR_TEST_DB_PASSWORD'
$env:ATTENDIQ_RUN_MYSQL_IT = 'true'
mvn clean verify
```

Never point these tests at a personal or production database. The integration suite writes and removes its own fixture records.

## Docker Compose

Copy `.env.example` to `.env`, provide unique local MySQL passwords and your local Firebase emulator values, then validate and start the stack:

```powershell
docker compose config --quiet
docker compose up --build
```

The web client is available at `http://localhost:3000`. Set `ALLOWED_EMAIL_DOMAIN` and `NEXT_PUBLIC_ALLOWED_EMAIL_DOMAIN` to the same institutional domain, and configure `CAMPUS_LAT` and `CAMPUS_LNG` before using geofence check-in. Do not commit `.env` or Firebase service-account credentials.

## CI

`.github/workflows/verify.yml` runs Java verification against a MySQL service, builds the Next.js application, and type-checks the Firebase Functions package. Treat the remote workflow as passing only after GitHub Actions records a successful run.
