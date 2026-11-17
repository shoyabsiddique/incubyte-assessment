---
title: 'Story 1.3: Money and exchange-rate conversion'
type: 'feature'
created: '2026-11-18'
status: 'in-review'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '0486d3633fa54faf16a62bad5d634c73b063372f'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-incubyte-assessment-2026-11-17/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Salaries in eight currencies must be compared in USD without rounding errors or each module inventing its own conversion. There is no money type, no country-to-currency source and no exchange-rate data.

**Approach:** Add a framework-free money kernel in `shared.domain` (`Money`, `Country`, `Rounding`, `ExchangeRates` snapshot) and the `exchange_rate` table with seeded rates owned by `compensation`, with a small api to load the current snapshot. Everything is built test-first, domain tests need no Spring or database.

## Boundaries & Constraints

**Always:** `BigDecimal` only, `MathContext.DECIMAL128` for arithmetic, unrounded internally; `Rounding` is the only place that rounds (HALF_UP, 2 decimal places) and only for presentation. Rates are strictly greater than 0; USD is always 1 and cannot be supplied as anything else. A missing or non-positive rate throws, never silently skips. `Country` is the single country-to-currency source: India INR, USA USD, UK GBP, Germany EUR, Singapore SGD, UAE AED, Canada CAD, Australia AUD. `shared.domain` imports no framework (existing ArchUnit rules must pass). Table `exchange_rate`: `currency` CHAR(3) primary key, `rate_to_usd` DECIMAL(19,8) with CHECK > 0, `version` BIGINT for optimistic locking; one Flyway migration `V2__exchange_rate.sql` also inserts the initial rates. Entity, repository and snapshot service live in `compensation`; other modules use only `compensation.api`.

**Never:** No endpoint, no editing of rates, no audit (Epic 3); no floating-point types in any money path; no conversion code outside the shared `ExchangeRates`; no stored USD values.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Convert | 100 INR with INR rate 0.012 | 1.2 USD, computed unrounded | N/A |
| USD identity | 250 USD | Same amount, rate 1 | N/A |
| Present | Money 1.005 USD | Display value 1.01 (HALF_UP); stored value unchanged | N/A |
| Missing rate | Convert EUR when snapshot lacks EUR | Throws `MissingExchangeRateException` naming the currency | Request fails loudly |
| Bad rate | Snapshot built with rate 0, negative, or USD not 1 | Rejected at construction | `IllegalArgumentException` |
| Currency mismatch | Add 5 USD to 5 EUR | Rejected | `IllegalArgumentException` |
| Country lookup | `Country.INDIA.currency()` | INR; each country maps to exactly one currency | N/A |
| Seeded data | Fresh database after migrations | 8 rows, one per supported currency, all rates > 0, USD = 1 | N/A |

</frozen-after-approval>

## Code Map

- `backend/src/main/java/com/acme/salary/shared/` -- currently only `ClockConfig` and `web/CorrelationIdFilter`; add `shared/domain/` for the pure kernel
- `backend/src/test/java/com/acme/salary/architecture/` -- existing ArchUnit rules already forbid framework imports in `..domain..`, so `shared.domain` is covered automatically
- `backend/src/main/resources/db/migration/V1__baseline.sql` -- baseline; add `V2__exchange_rate.sql`
- `backend/src/main/java/com/acme/salary/compensation/{api,application,persistence}/` -- empty packages (`package-info.java`) to fill
- `backend/src/test/java/com/acme/salary/AbstractMariaDbIntegrationTest.java` -- base for the persistence integration test

## Tasks & Acceptance

**Execution:**
- [x] `backend/src/test/.../shared/domain/*Test.java` -- write failing tests first for `Money`, `Rounding`, `Country`, `ExchangeRates` covering every matrix row -- test-first
- [x] `backend/src/main/.../shared/domain/` -- implement `Money`, `Rounding`, `Country`, `ExchangeRates`, `MissingExchangeRateException` -- the kernel
- [x] `backend/src/main/resources/db/migration/V2__exchange_rate.sql` -- create table, CHECK, insert eight illustrative initial rates -- reference data
- [x] `backend/src/test/.../compensation/ExchangeRateSnapshotIT.java` -- failing integration test: snapshot from the database has all eight currencies, USD 1, converts a known amount -- proves migration, entity mapping and `ddl-auto=validate`
- [x] `compensation/{persistence,application,api}` -- JPA entity with `@Version`, repository, `ExchangeRateService` implementing `ExchangeRateQuery.snapshot()` -- load the current snapshot

**Acceptance Criteria:**
- Given the matrix, when the unit tests run, then all pass without a Spring context or database.
- Given a fresh MariaDB, when the application starts, then Flyway applies V2, Hibernate validates the entity, and the snapshot contains exactly the eight supported currencies.
- Given the ArchUnit suite, when run, then no rule is weakened and all still pass.

## Implementation Notes

The initial rates are illustrative demo values (approximate, not live), editable later in Epic 3.

## Review Triage Log

| Finding | Verdict | Route | Evidence |
|---|---|---|---|
| Snapshot accepted a table missing a supported currency; the failure only appeared at the first conversion | medium | patch (done) | `ExchangeRates.requireRatesFor` plus the service check at load time; unit test names every missing currency |
| Database check that USD is always 1 untested; the XXX insert in the positive-rate test could pollute the shared database | medium | patch (done) | New integration test updates USD to 1.1 and expects rejection, with cleanup in `finally` for both tests |
| Rates with more than 8 decimal places accepted in memory but would round when stored | medium | patch (done) | `ExchangeRates.of` rejects them (trailing zeros ignored); tests added |
| Javadoc said arithmetic was exact and never rounded, but DECIMAL128 rounds at 34 digits | low | patch (done) | `Money` and `Rounding` documentation now state the 34-digit context |
| `MissingExchangeRateException` did not expose which currencies | low | patch (done) | `currencies()` added, tested |
| `Country.supportedCurrencies()` rebuilt on every call; null/blank `fromDisplayName` untested; import order and a fully-qualified `HashMap` in a test | low | patch (done) | Constant set, two assertions, tidy-ups |
| Bad currency row breaks the snapshot with a raw exception | low | rejected | Only reachable by hand-editing the table; the `Currency` error already names the bad code |
| Null arguments give bare NullPointerExceptions; `Money.times` accepts zero or negative factors | low | rejected | Programmer errors; a money type must allow negative amounts for differences |
| `Rounding.display` always uses 2 decimal places | low | rejected | Fixed by the spec; all eight currencies use 2; revisit only if a 0- or 3-decimal currency is added |
| Snapshot not enforced once per request | low | rejected | Callers load it once; request-scoped caching belongs to the stories that serve requests |
| Demo rates live in a versioned migration with no audit columns | low | rejected | Rates become editable and audited in Epic 3 |
| CHECK constraints not enforced on MariaDB before 10.2 or MySQL before 8.0.16 | medium (unverified) | defer | Only matters if Hostinger runs an old engine; settle with the open engine/version check |
