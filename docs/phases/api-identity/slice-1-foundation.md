# api-identity slice 1 — foundation

Stage: `api-identity` (Phase 1). Slice 1 only. Schema, authorization, and auth endpoints were not started.

Date: 2026-09-25

## Done

- Feature packages under `com.coachingcenter.api`: `common`, `auth`, `users`. No global `controllers`, `services`, or `repositories` package.
- Profiles `dev`, `staging`, and `prod`. Database URL, username, password, JWT secret, and email settings are environment variables. `.env` is gitignored. No credentials were committed.
- `spring.jpa.hibernate.ddl-auto=validate`. Flyway is enabled. No migration has been added yet (that is slice 2).
- Shared error body: `code`, `message`, optional `fieldErrors`, `timestamp`, `requestId`. One code names one condition.
- Shared list envelope: `content`, `page`, `size`, `totalElements`, `totalPages`. List endpoints are expected to use `page` and `size`.
- `BaseEntity`: UUID `id`, `createdAt`, `updatedAt`, nullable `deletedAt`. No domain entity extends it yet.
- Request id filter. The id is echoed on `X-Request-Id`, stored for the error body, and put in the logging MDC. Console logs use ECS JSON.
- CORS allows only the web origin for that profile. Dev defaults to `http://localhost:3000`. Staging and prod require `WEB_ORIGIN`.
- Stateless security filter chain that returns the shared error body for 401 and 403. A `UserDetailsService` bean is present so Spring Security does not generate and log a password at startup.
- `ApiApplicationTests` (context load with no database) was removed.
- Testcontainers PostgreSQL stays on the test classpath for CI. Local `*IT` runs use an already-running PostgreSQL instance when `TEST_DB_URL`, `TEST_DB_USER`, and `TEST_DB_PASSWORD` are set. Neither mode uses H2. Surefire includes `*IT.java`.

## Tested

| Check | Result |
|---|---|
| `ApiExceptionHandlerTest` — invalid JSON body returns `400`, code `VALIDATION_FAILED`, `fieldErrors`, and the request id | Passed |
| `PageResponseTest` — page envelope maps a Spring Data page | Passed |
| `mvn clean compile` | Passed |
| `FoundationIT` — boot against local PostgreSQL 17.9 (`coaching_center_test`), confirm `ddl-auto=validate`, unauthenticated error body, request id, and CORS | Passed |

## Findings

- Spring Boot 4.1 uses Jackson 3. `com.fasterxml.jackson.databind.ObjectMapper` is not on the classpath. The error writer uses `tools.jackson.databind.ObjectMapper`.
- `spring.jackson.serialization.write-dates-as-timestamps` does not bind on this Boot version. That property was removed. `spring.jackson.default-property-inclusion=non_null` does bind, so a null `fieldErrors` or `requestId` is omitted from the JSON.
- Maven Surefire does not run classes named `*IT` unless the plugin includes them. The include was added because the stage test plan uses that suffix.
- An incremental compile once left record classes out of `target/classes` (`NoClassDefFoundError` for `FieldErrorDetail` and `PageResponse`). `mvn clean compile` wrote them. Later test runs used a clean compile.

## Failed, and why

`FoundationIT` failed before the Spring context started:

`Could not find a valid Docker environment.`

That run used Testcontainers, which starts its own PostgreSQL container and therefore needs Docker. Docker is not part of this project's local setup. The requirement is a real PostgreSQL database, not a container. Local `*IT` runs now connect to an already-running instance when `TEST_DB_URL`, `TEST_DB_USER`, and `TEST_DB_PASSWORD` are set. CI still starts PostgreSQL with Testcontainers, because GitHub Actions runners already have Docker. H2 is not used in either mode.

An earlier `@WebMvcTest` for the validation case returned `404` because the probe controller was not mapped, and that slice logged a generated security password. That test was rewritten as a standalone `MockMvc` test so it checks the handler without loading the security auto-configuration. That rewritten test passed. It does not replace `FoundationIT`.

## Next

`FoundationIT` passed against the local PostgreSQL 17 service. Slice 2 is the Flyway schema. Nothing further is blocked on Docker or on installing PostgreSQL.

How the suite reaches PostgreSQL:

- Local: PostgreSQL 17 is already running (`postgresql-x64-17`). `*IT` tests use it when `TEST_DB_URL`, `TEST_DB_USER`, and `TEST_DB_PASSWORD` are set. This run used database `coaching_center_test` on `127.0.0.1`. Localhost auth is `trust`, so the password value is not a stored credential and was not committed.
- CI: leave those variables unset. `.github/workflows/api.yml` (slice 8) keeps Testcontainers-managed PostgreSQL on the GitHub Actions runner.
