# Epic 1 Context: Sign in and browse the workforce (walking skeleton)

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

The HR Manager signs in and searches, filters and opens any of 10,000 seeded Employees, seeing salaries in local currency and USD. The epic is the walking skeleton: scaffolding, CI, money, auth, seed data, directory and employee view, all built test-first so later epics extend a working app.

## Stories

- Story 1.1: Backend skeleton
- Story 1.2: Frontend skeleton and CI pipeline
- Story 1.3: Money and exchange-rate conversion
- Story 1.4: Sign in and sign out
- Story 1.5: Seed 10,000 employees
- Story 1.6: Browse and search employees
- Story 1.7: View an employee

## Requirements & Constraints

- Salaries are exact decimals, never floating point; USD is derived at read time from editable rates.
- Lists must be server-side paginated and respond in under 500 ms on a warm instance at 10,000 Employees.
- Passwords are hashed, every API call except sign-in needs authentication, and no sensitive personal data is stored.
- Core logic is covered by fast, deterministic unit tests written before the implementation; commits are small and incremental.

## Technical Decisions

- Modular monolith, package-by-feature: `com.acme.salary.<module>.{api,web,application,domain,persistence}` for modules employees, compensation, analytics, simulator, auth, audit, plus `shared`. `api` is the only package other modules may import; the module graph is acyclic (employees to compensation and audit; simulator to analytics; everything may use shared).
- `domain` packages are framework-free (no Spring, JPA, Jackson) and take time from an injected `Clock`. ArchUnit enforces the layering, the `api`-only rule and the absence of cycles.
- Stack: Java 21, Spring Boot 4.1.x with `spring-boot-starter-flyway`, Gradle wrapper, MariaDB/MySQL matching Hostinger (engine and version still to be confirmed), Flyway-only schema changes with `ddl-auto=validate`, Testcontainers for integration tests (no H2), Angular 22 with Material for the frontend.
- API conventions: `/api/v1`, Problem Details errors with a `code`, page envelope, UUID keys stored as `CHAR(36)`, money as `{amount, currency}` decimal strings.
- Operations: Actuator health and info are public; a correlation ID is on every request and every structured JSON log line; configuration comes from environment variables and Spring profiles with a committed `.env.example`; the prod profile refuses to start without a 256-bit JWT key; connection pool capped at 5.
- Repository layout: `backend/`, `frontend/`, `e2e/`, `docs/`, `.github/workflows/`.

## Cross-Story Dependencies

- 1.1 and 1.2 give every later story its build and CI base; 1.3 provides Money and rates used by 1.6 and 1.7; 1.4 provides the `app_user` table and JWT used by the frontend guard and all later endpoints; 1.5 creates the employee and compensation tables the directory and employee view read.
- Epics 2 to 7 build on this epic: audit (Epic 2) and revisions (Epic 3) extend the same tables, and analytics (Epics 4 to 6) read the compensation data seeded here.
