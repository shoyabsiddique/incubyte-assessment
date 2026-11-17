---
title: 'Story 1.5: Seed 10,000 employees'
type: 'feature'
created: '2026-11-18'
status: 'in-progress'
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
- [ ] Tests first: `SeedGeneratorTest` (counts, currencies, ids, emails, titles and departments, history chains, determinism, outliers, plausibility), schema integration test for every constraint row, `EmployeeSeedIT` (counts, determinism hash, idempotence, rollback, time budget), architecture rule tests -- test-first
- [ ] `V4__employee.sql`, `V5__compensation_record.sql`, entities, enums -- schema and mapping validated by `ddl-auto=validate`
- [ ] `seed/domain` generator and catalog; `seed/application` runner and multi-row writer -- the seed
- [ ] `ArchitectureRules`, `application.properties`, UTC pinning, `.env.example`, backend notes -- rules and docs

**Acceptance Criteria:**
- Given an empty database, when the runner executes, then exactly 10,000 Employees exist in the stated counts, each with one open record, and it finishes in under 60 seconds on a local container.
- Given the same seed twice or a second run, then data is identical or unchanged respectively.
- Given the full test suite and ArchUnit, when run, then everything passes.

## Implementation Notes

Seeding is run with `SPRING_PROFILES_ACTIVE=local,seed` (or `prod,seed`). Multi-row inserts keep the number of round trips small for the remote Hostinger database.
