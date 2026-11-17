---
title: 'Story 1.1: Backend skeleton'
type: 'feature'
created: '2026-11-18'
status: 'in-review'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'bbfe9f0ef4655cbcdd1ef67325ff2ad78a6a7894'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-incubyte-assessment-2026-11-17/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** There is no code. Every later story needs a tested backend base with the module structure and its architecture rules already enforced, a way to run integration tests against the same engine as production, and operational basics (health, correlation IDs, structured logs).

**Approach:** Create `backend/` as a Gradle (wrapper) Spring Boot 4.1.x / Java 21 project from the Spring Initializr starter, with the seven module packages, ArchUnit rules, public Actuator health/info, a correlation-ID filter with structured JSON logs, and a Testcontainers MariaDB 11.4 base class proving a Flyway baseline migration and `ddl-auto=validate`. Work test-first, committing failing tests before each implementation.

## Boundaries & Constraints

**Always:** Packages `com.acme.salary.<module>.{api,web,application,domain,persistence}` for employees, compensation, analytics, simulator, auth, audit, plus `shared`. `domain` imports no Spring, JPA or Jackson. Time via injected `Clock` only. Flyway is the only schema path (`ddl-auto=validate`). MariaDB driver and `mariadb:11.4` test image; no H2. Config from environment variables with Spring profiles `local`, `test`, `prod`. Commit tests first, then implementation, in small commits with a descriptive message.

**Never:** No business logic, entities or endpoints beyond health/info; no security configuration yet (Story 1.4); no Angular or CI (Story 1.2); no secrets committed; no H2; do not hand-write placeholder classes only to satisfy empty packages beyond a `package-info.java` per package.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Health | `GET /actuator/health` with no token | 200, status UP, `X-Correlation-Id` header set | N/A |
| Correlation ID supplied | Request with `X-Correlation-Id: abc` | Same value echoed and present in every log line for the request | N/A |
| Correlation ID absent | Request without the header | A generated UUID is returned and logged | N/A |
| Architecture violation | `domain` class importing Spring/JPA/Jackson, a cross-module import of a non-`api` package, or a dependency cycle | ArchUnit test fails with the offending class | Build fails |
| Schema drift | Entity or migration out of sync | Context start fails under `ddl-auto=validate` | Test fails |

</frozen-after-approval>

## Code Map

- Repository is greenfield: only planning docs exist under `_bmad-output/`. No code to reuse; do not modify planning artifacts.
- `backend/` -- new Gradle project root (create via Spring Initializr, keep the Gradle wrapper)
- `backend/src/main/java/com/acme/salary/shared/` -- correlation-ID filter, `Clock` bean
- `backend/src/test/java/com/acme/salary/` -- ArchUnit tests, health test, Testcontainers base class

## Tasks & Acceptance

**Execution:**
- [x] `backend/` (build.gradle, settings.gradle, wrapper) -- generate from Spring Initializr (Boot 4.1.x, Java 21, web, actuator, data-jpa, flyway, validation, MariaDB driver, test deps) and add ArchUnit and Testcontainers MariaDB explicitly -- BOM does not manage ArchUnit
- [x] `backend/src/test/.../ArchitectureTest.java` -- write ArchUnit rules first (domain framework-free, `api`-only cross-module imports, no cycles, module graph from the spine) -- enforces AD-1 and AD-2; commit while red or empty-safe
- [x] `backend/src/main/java/com/acme/salary/<module>/<layer>/package-info.java` -- create the module/layer packages -- structure only
- [x] `backend/src/test/.../HealthEndpointTest.java` and `shared` filter -- test then implement correlation-ID filter, MDC, response header and JSON console logging -- operability
- [x] `backend/src/test/.../AbstractMariaDbIntegrationTest.java` and `src/main/resources/db/migration/V1__baseline.sql` -- Testcontainers MariaDB 11.4 base class with Flyway baseline and `ddl-auto=validate` -- authoritative DB environment
- [x] `backend/src/main/resources/application*.yml`, `.env.example` entries for backend -- profiles and environment-variable config, health/info exposed -- AD-11

**Acceptance Criteria:**
- Given a fresh clone, when `./gradlew build` runs with Docker available, then all tests pass and the ArchUnit rules fail on deliberate violations (verified once, then reverted).
- Given the running API, when `/actuator/health` and `/actuator/info` are requested without authentication, then both return 200 with a correlation ID header and JSON log lines containing that ID.
- Given the Testcontainers base class, when the baseline migration runs, then the context starts with `ddl-auto=validate`.
- Given Docker is not running, when only non-Docker tests are run, then they pass and the Docker-dependent tests are reported as not run, not as passed.

## Implementation Notes


## Review Triage Log

| Finding | Verdict | Route | Evidence |
|---|---|---|---|
| Completion log reports status 200 when the chain throws; MDC may leak if logging throws | medium | patch (done) | Filter now records 500 on failure and clears MDC in a nested finally; test asserts the exception is thrown and MDC cleared |
| Cross-module rule misses classes in a module's root package; layer inversions (application to web, persistence to application/web, api to internals) unchecked; jakarta.* beyond persistence not forbidden in domain; unknown top-level packages unchecked | medium | patch (done) | Rules tightened with fixtures for each; unknown-package check added |
| Architecture checks could be vacuous if BASE were wrong | medium | patch (done) | Test asserts `SalaryApplication` was imported |
| `failing >= 3` assertion weak | low | patch (done) | Replaced with per-pair assertions on rule descriptions |
| Failure test never asserts an exception was thrown | low | patch (done) | `assertThatThrownBy` added |
| No datasource when no profile is set; `test` profile missing; plain-text `.env` not loaded by Spring | medium | patch (done) | `spring.profiles.default=local`, `application-test.properties` and `@ActiveProfiles("test")`, `.env.example` explains how values are supplied |
| No root `.gitignore` for `.env` | medium | patch (done) | Root `.gitignore` added |
| Actuator exposure and health details untested | medium | patch (done) | Three integration tests added: no component details, env/beans/metrics return 404, info has correlation ID |
| Hikari comment says MySQL; `.env.example` password differs from local default; FlywayBaselineIT javadoc overclaims validate | low | patch (done) | Comments and example aligned |
| Wrapper jar not in diff | false | rejected | `git ls-files` shows `gradle-wrapper.jar` is tracked; it was only excluded from the review diff |
| Error/async dispatch lacks correlation ID; nested MDC overwrite | low | rejected | Nested filter runs are prevented by OncePerRequestFilter; error dispatch fix would double-log each request |
| Wrapper `distributionSha256Sum`, formatter, tabs vs spaces, `group` placeholder, JSON logs in local dev, ClockConfig test, blank header test, default local password, tag only on base class | low | rejected | Cosmetic or unlikely, fixes add complexity |
| `check` fails without Docker; skipped tests not reported | low | rejected | By design: `test` is the Docker-free task; integration tests run only through `integrationTest` |
| Actuator stays unauthenticated when auth arrives | low | rejected | Story 1.4 acceptance criteria already keep health/info public deliberately |
| Prod profile fail-fast not tested | medium (unverified) | defer | Config-only guarantee; see deferred-work.md |
| Time zone not pinned for stored timestamps | medium (unverified) | defer | No timestamps are stored yet; settle in Story 1.5 when the first date columns exist |
| No README, compose file or CI | medium | defer | CI is Story 1.2; README and compose belong to Story 7.3 |
