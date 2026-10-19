# Attend-IQ Viva Questions

1. **Why use JDBC?** It provides direct Java-to-relational-database access and makes SQL, prepared statements, and transaction behavior visible for this academic project.
2. **Why are services separate from repositories?** Services enforce business rules; repositories own SQL and persistence.
3. **How is SQL injection avoided?** User-supplied values are bound as parameters in `PreparedStatement` calls.
4. **How are duplicate attendance records prevented?** A unique constraint on `(session_id, student_id)` is the final authority, including concurrent inserts.
5. **Why does marking use a transaction?** Session lookup, enrollment validation, insert, and read-back must succeed as one unit; failures roll back.
6. **What does the enrollment table represent?** It resolves the many-to-many relationship between students and subjects and has a composite primary key to reject duplicate enrollment.
7. **What is the attendance percentage formula?** `100 * (PRESENT + ON_DUTY) / scheduled sessions` for sessions from enrollment through today. Unmarked sessions are visible and count in the denominator.
8. **What happens when there are no sessions?** The report returns 0%, avoiding division by zero.
9. **How does the app demonstrate abstraction and polymorphism?** Services depend on repository interfaces, while JDBC classes provide concrete implementations.
10. **Why is inheritance not used?** The current domain has no justified is-a relationship; adding a hierarchy only to claim inheritance would be artificial.
11. **Does the faculty ID prompt provide secure authorization?** No. It checks assignment rules for an academic demonstration but does not authenticate the person entering the ID.
12. **How are passwords managed?** MySQL credentials are read from environment variables and are not stored in Java source or committed documentation.
13. **How is data kept after the app exits?** Data is committed to MySQL, not held only in process memory.
14. **How do safe deletes work?** The app refuses to delete students, enrollments, subjects, or sessions when history/dependencies would be lost; foreign keys provide an additional guard.
15. **Which tests use MySQL?** `AttendanceMySqlIntegrationTest` is opt-in and requires the dedicated `attendiq_java_test` database; pure service, calculator, and input tests run without MySQL.
