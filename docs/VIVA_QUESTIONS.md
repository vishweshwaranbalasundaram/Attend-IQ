# Attend-IQ Viva Questions

1. **Why JDBC?** It exposes SQL, prepared statements, transactions, and MySQL persistence directly in Java.
2. **Why separate services and repositories?** Services enforce application rules; JDBC repositories own SQL and persistence.
3. **How is SQL injection avoided?** User values are bound through `PreparedStatement` parameters.
4. **How are duplicate attendance records prevented?** A unique `(session_id, student_id)` key is the final authority, including concurrent inserts.
5. **Why use transactions for attendance?** The attendance change and audit row must either both commit or both roll back.
6. **What does enrollment represent?** It resolves the many-to-many relationship between students and subjects and rejects duplicate enrollment.
7. **How is attendance percentage calculated?** `(PRESENT + ON_DUTY) / scheduled sessions`; unmarked sessions remain in the denominator.
8. **What does the application do with zero sessions?** It returns 0% instead of dividing by zero.
9. **How are attendance corrections authorized?** The console verifies the faculty ID/password, then the service checks that faculty is assigned to the class session.
10. **How are faculty passwords stored?** PBKDF2-HMAC-SHA256 with 210,000 iterations and a random 16-byte salt. Only the formatted hash is stored.
11. **How does login lockout work?** Five failed attempts lock the faculty account for five minutes. The pure lockout policy uses an injected `Clock`, so expiry tests need no sleeps or database.
12. **What is written to the authentication audit trail?** Login success, failure, and locked events with the attempted faculty ID and timestamp. Passwords and hashes are not stored.
13. **How can a faculty member change a password?** Use Faculty Management → Set/change faculty password and confirm the new password. Existing configured accounts must authenticate with the current password.
14. **How are existing accounts initialized?** A trusted administrator provisions them; the console’s initial setup option requires `ATTENDIQ_ALLOW_INITIAL_PASSWORD_SETUP=true` and should only be enabled for a trusted setup session.
15. **How is persistence verified in CI?** GitHub Actions starts MySQL 8.4, creates the isolated `_test` database, and runs opt-in integration tests.
16. **What does JaCoCo report?** Line coverage for the Maven test run. CI prints each service class and enforces the 70% aggregate service-line target.
17. **How can a reviewer start MySQL locally?** `docker compose up -d mysql` starts only the database service and loads `schema.sql` on first initialization.
18. **Does the project use Spring or a web frontend?** No. It is a Java console application with JDBC, MySQL, Maven, and JUnit 5.
