# Attend-IQ V2 Architecture

Attend-IQ keeps its original Java attendance CLI and adds a browser-based portal backed by the same MySQL academic records. The portal uses Firebase identity, a Spring Boot API, and a Next.js client; local development and production use different runtime configurations.

## Application layers

| Layer | Responsibility | Main implementation |
|---|---|---|
| Web client | Student and staff workspaces, profile, attendance summaries, QR scan and staff tools | `web/` - Next.js, React, TypeScript |
| Identity | Institutional Firebase sign-in, email verification, Auth blocking checks, profile storage | `functions/`, Firebase Authentication and Storage |
| REST API | Token verification, role resolution, scoped queries and attendance workflows | `src/main/java/com/attendiq/web/` - Spring Boot and Spring Security |
| Persistence | MySQL records, constraints, transactions and schema upgrades | JDBC, `JdbcTemplate`, Flyway migrations |
| Original CLI | Console-based academic catalog and attendance workflows | `src/main/java/com/attendiq/` |

## Identity and authorization

The browser obtains a Firebase ID token and sends it to the API as a bearer token. `FirebaseAuthenticationFilter` verifies the token through `FirebaseIdTokenVerifier`, applies the institutional identity policy, and resolves the account and role from the database directory. Role headers and other client-supplied role values are not authorization inputs. The API controllers apply staff ownership checks and student account scoping.

Firebase Auth blocking functions validate institutional account creation and sign-in. `DEV_AUTH_BYPASS` defaults to false and the API refuses to start with the bypass enabled under the `prod` profile. Use the Firebase Emulator Suite for local Firebase flows.

## Attendance and persistence

The web API uses `JdbcTemplate` and explicit SQL; it does not use JPA entities or Spring Data repositories. Transactional services coordinate QR attendance, OD review, and attendance changes. MySQL unique keys and foreign keys provide additional persistence-level integrity. Flyway applies the versioned schema from `src/main/resources/db/migration/`.

The original CLI repositories use JDBC and prepared statements. Both application surfaces use the academic catalog and attendance tables, while legacy and portal audit events are retained in separate tables for compatibility.

## Runtime targets

- **Local:** Docker Compose provides MySQL and the application services. Firebase Auth and Storage emulators can be used for local identity and profile-media testing.
- **Production target:** Firebase Hosting serves the frontend and routes API traffic to Cloud Run. Cloud Run connects to Cloud SQL for MySQL with the Cloud SQL Connector.
- **Provisioning:** Firebase and Google Cloud resources, IAM, secrets, and project-specific settings must be configured by the operator; repository configuration alone does not provision them.

See [the architecture diagram](../.github/modernize/assessment/engines/facts/architecture-diagram.md) and [the data architecture](../.github/modernize/assessment/engines/facts/data-architecture.md) for diagrams and component details.
