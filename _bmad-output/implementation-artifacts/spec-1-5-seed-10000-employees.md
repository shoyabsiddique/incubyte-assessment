---
title: 'Story 1.5: Seed 10,000 employees'
type: 'feature'
created: '2026-11-18'
status: 'in-review'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '40c871ededd7b3bc594ea0052d9d852112130166'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-incubyte-assessment-2026-11-17/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** There are no employees. Every later story (directory, analytics, anomalies, simulator) needs the `employee` and `compensation_record` tables and realistic, reproducible data for 10,000 people in eight countries.

**Approach:** Add migrations for both tables (with their constraints) and JPA entities so the schema is validated, then a separate seed runner under the `seed` profile: a framework-free generator (fixed random seed, deterministic ids) plus a JDBC writer using multi-row inserts in one transaction, skipping when employees already exist. The seed is the single documented bypass of the salary-history and audit rules and writes no audit entries.

## Boundaries & Constraints

**Always:** `employee`: UUID `CHAR(36)` key, unique `employee_id` of the form `EMP-000001`, unique case-insensitive work email, country stored as the `Country` enum name, status and employment type constrained by CHECK, `version` BIGINT, indexes for search and filters. `compensation_record`: `DECIMAL(19,4)` amount greater than 0, currency with a foreign key to `exchange_rate`, `effective_start` and nullable `effective_end` with CHECK end >= start, unique `(employee_id, effective_start)`, foreign key to `employee`, `version`. Exactly 10,000 Employees: India 3,500, USA 2,500, UK 1,000, Germany 800, Singapore 700, UAE 600, Canada 500, Australia 400, each paid in their country's currency; 8 departments and 25 job titles; one open current record per Employee, about 15% with 1 to 3 earlier closed records forming a gapless chain; about 92% ACTIVE with some ON_LEAVE and TERMINATED; natural pay spread stays within 20% of a peer group's median, plus 16 deliberate outliers (8 high, 8 low) among active Employees in peer groups of 8 or more. Same seed value gives identical data including ids and emails; a populated database is left untouched; all inserts commit together or not at all. The generator is framework-free (`seed.domain`) and takes an `ExchangeRates` snapshot; the writer uses `compensation.api` and `shared` only. `seed` is a known bootstrap package: no module may depend on it, and it may use only `shared` and other modules' `api`. Hibernate and the JVM are pinned to UTC.

**Never:** No endpoints or screens (Story 1.6), no audit entries, no employee creation by the application (Epic 2), no migration containing seed rows, no real personal data (names are generated), no weakening of existing ArchUnit rules.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Seed empty DB | `seed` profile, fixed seed | 10,000 Employees with the exact country counts and about 13,000 records, in under a minute | N/A |
| Re-run | Same DB again | No rows added or changed | Logs that seeding was skipped |
| Same seed twice | Two empty DBs | Byte-identical data (a content hash matches) | N/A |
| Different seed | Another seed value | Data differs, constraints still satisfied | N/A |
| Partial failure | Error midway through writing | Nothing committed | Transaction rolls back |
| Bad data | Duplicate employee id or email, non-positive amount, end before start, unknown currency, orphan record, invalid status | Rejected by the database | Constraint violation |
| Outliers | Generated data | 16 deliberate outliers; flagging by the 20%-of-median rule over groups of 5 or more finds between 12 and 40 Employees | N/A |

</frozen-after-approval>

## Code Map

- `backend/src/main/resources/db/migration/` -- V1 baseline, V2 `exchange_rate`, V3 `app_user`; add V4 `employee` and V5 `compensation_record`
- `backend/src/main/java/com/acme/salary/{employees,compensation}/{persistence,api}/` -- empty packages; entities and `EmploymentType` / `EmploymentStatus` enums (in `employees.api`) go here
- `backend/src/main/java/com/acme/salary/shared/domain/` -- `Country`, `ExchangeRates`, `Money` to reuse; `compensation.api.ExchangeRateQuery` gives the snapshot
- `backend/src/main/java/com/acme/salary/auth/application/DemoAccountSeeder.java` -- already seeds HR accounts under `local` and `seed`; leave as is
- `backend/src/test/java/com/acme/salary/architecture/ArchitectureRules.java` -- add the `seed` package rules and known-package allowance, with fixtures

## Tasks & Acceptance

**Execution:**
- [x] Tests first: `SeedGeneratorTest` (counts, currencies, ids, emails, titles and departments, history chains, determinism, outliers, plausibility), schema integration test for every constraint row, `EmployeeSeedIT` (counts, determinism hash, idempotence, rollback, time budget), architecture rule tests -- test-first
- [x] `V4__employee.sql`, `V5__compensation_record.sql`, entities, enums -- schema and mapping validated by `ddl-auto=validate`
- [x] `seed/domain` generator and catalog; `seed/application` runner and multi-row writer -- the seed
- [x] `ArchitectureRules`, `application.properties`, UTC pinning, `.env.example`, backend notes -- rules and docs

**Acceptance Criteria:**
- Given an empty database, when the runner executes, then exactly 10,000 Employees exist in the stated counts, each with one open record, and it finishes in under 60 seconds on a local container.
- Given the same seed twice or a second run, then data is identical or unchanged respectively.
- Given the full test suite and ArchUnit, when run, then everything passes.

## Implementation Notes

Seeding is run with `SPRING_PROFILES_ACTIVE=local,seed` (or `prod,seed`). Multi-row inserts keep the number of round trips small for the remote Hostinger database.

## Review Triage Log

| Finding | Verdict | Route | Evidence |
|---|---|---|---|
| Seed not reproducible across JVM launches: headcount iterated in an order that changes with every launch (all three reviewers) | high | patch (done) | Reproduced first: three separate JVM launches gave three different hashes. Fixed with an ordered map; the same launches now give one hash, pinned in `SeedGeneratorTest` together with the first employee's id, email and country |
| No database guard against a second open record for one employee | high | patch (done) | Generated `open_marker` column with a unique key in V5; tests for rejecting a second open record and allowing a long closed chain |
| Deliberate-outlier count only bounded loosely (12 to 40); silent shortfall possible | medium | patch (done) | Generator throws if fewer than 16 are placed; test now requires exactly 16 flagged, proving the natural spread flags nobody |
| Stored rows never compared with generated data; swapped columns would pass | medium | patch (done) | `storedRowsMatchTheGeneratedDataFieldByField` checks first, chunk-boundary (499, 500, 501), middle and last employees with all their records |
| Seed profile gating and runner execution unverified | medium | patch (done) | Test shows the runner bean is absent without the profile; `EmployeeSeedProfileIT` starts the app with `seed` and finds 10,000 employees and the 2 demo accounts |
| Check-then-act race when two processes seed at once | medium | patch (done) | A duplicate-key failure while the table is now populated is treated as "someone else seeded"; test simulates it |
| Currency checked for only 4 of 8 countries; country and type CHECKs untested | medium | patch (done) | Test derives the expected currency from `Country` for all 8; schema tests for unknown country and type and for accepting every `Country` value |
| UTC check only compared a property string; JVM zone set only in `main` | medium | patch (done) | Gradle pins `user.timezone=UTC` for tests and `bootRun`; test asserts the JVM zone and the Hibernate setting |
| Email fallback could collide; non-rising history not guarded; log of a possibly null count | low | patch (done) | Fallback loops until unique; generator throws instead of producing a bad history; runner requires a non-null count |
| "Identifiers not random" test only checked uniqueness | low | patch (done) | Renamed to what it checks; the pinned first id and golden hash now prove determinism |
| Terminated employees keep an open record | low | patch (done) | Documented in the V5 migration comment; analytics only read ACTIVE employees |
| Entity mapping untested beyond startup validation; extra indexes slow inserts; three public constructors; raw SQL coupling of the seed; wall-clock budget may flake; packet size limits | low | rejected | Startup runs `ddl-auto=validate` over both entities; the indexes serve Story 1.6 filters; the seed is the one documented bypass; 500-row statements are about 125 KB |
| Guard against running `seed` on a production database | medium (product decision) | rejected | Seeding Hostinger with `prod,seed` is the intended Story 7.1 flow; documented, not blocked |
