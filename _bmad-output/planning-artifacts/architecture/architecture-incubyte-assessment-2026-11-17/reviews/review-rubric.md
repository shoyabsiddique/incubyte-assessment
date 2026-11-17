# Rubric Review: ARCHITECTURE-SPINE.md (ACME Salary Management)

Reviewed against: PRD `prd-incubyte-assessment-2026-11-17/prd.md` (FR-1..FR-12, NFRs, assumptions).
Reviewer stance: spine is not edited. Findings are ordered by severity within each section.

## Verdict

**Revise before handing to the level below.** The spine is strong on domain-level consistency (pure core, money, append-and-close history, single analytics projection, API conventions) and maps all twelve FRs. It fails the checklist in three places: (1) deployment, environments and operations are neither decided, deferred nor raised as open questions; (2) AD-4 contains a defect that contradicts its own DB constraints and does not actually prevent "two current salaries"; (3) AD-2's module rule contradicts what AD-6, FR-3 and FR-4 require, and several Rules are convention-only with no enforcement mechanism. None needs a redesign; all are fixable with a few added or tightened ADs and an Open Questions section.

## Checklist scorecard

| Checklist item | Result | Note |
| --- | --- | --- |
| Fixes real divergence points, misses none | Partial | Domain/API/money well covered. Misses: deployment mechanics, cross-module read and transaction ownership, concurrency, country-to-currency source, rate precision, ID generation. |
| Every AD Rule enforceable and prevents its stated divergence | Partial | AD-1 (mostly), AD-9, AD-11 (mostly) fine. AD-2, AD-4, AD-5, AD-10, AD-12 are weak or defective (see table). |
| Nothing in Deferred lets two units diverge | Mostly pass | One risk: "SQL aggregation after profiling" can split median logic between SQL and Java. Real gaps are things not listed anywhere (see F-1). |
| Covers FR-1..FR-12 | Pass (with seams) | All mapped. Seams: FR-3/FR-4 cross-module composition, FR-8 ranking currency, FR-12 seed vs AD-4/AD-5. |
| Every dimension decided, deferred or open (esp. deployment/env/ops) | **Fail** | No Open Questions section exists. Deployment/environments/operations largely absent. |

## Findings

### F-1 [HIGH] Deployment, environments and operations are undecided and unacknowledged

The PRD requires "deployed and publicly reachable" and under 500 ms "on the deployed instance", and FR-12 requires a seed that completes in under a minute. The spine shows an Azure diagram and a CI/CD arrow, but decides almost nothing a builder needs, and none of it is under Deferred or an Open Question:

- **How and where the seed runs in the deployed environment.** AD-11 says the seed is a separate runner, never a migration, but not how it is invoked against Azure PostgreSQL (CLI profile, one-off CI job, startup flag, local machine pointed at Azure), nor whether it is re-runnable or guarded in `prod`.
- **Reference data placement.** Exchange Rates and demo HR accounts must exist for the app and for Testcontainers tests. Migration or seed runner is unstated; this directly splits test and prod behaviour.
- **Who runs Flyway** (app startup, CI step, deploy step) and migration safety on a live DB. Also no `spring.jpa.hibernate.ddl-auto=validate/none` rule, so Hibernate could mutate schema outside Flyway and defeat AD-11.
- **Secrets and config in Azure:** where `JWT` signing secret, DB credentials and CORS origin live (App Service settings vs Key Vault), JWT algorithm/key handling, and DB network access (public with firewall vs private) are not stated.
- **Frontend runtime config:** how the Angular build learns the API base URL per environment (build-time environments file vs runtime config) is undecided. This is a classic cross-environment divergence.
- **Environments:** `local`/`test`/`prod` profiles only. No statement on whether there is a staging/preview environment, and what the public demo environment's data and credentials policy is (demo logins on a public URL).
- **CI/CD gates:** "build, test, deploy main" is stated, but not whether deploy is gated on tests, where Playwright e2e runs (against CI-local stack or the deployed URL), or how frontend and backend deploys are ordered/versioned.
- **Infrastructure provisioning** (IaC vs portal), region, App Service/DB tier (relevant to the 500 ms NFR and cold starts), and health/readiness endpoints for Azure (Actuator appears only inside a Deferred bullet).
- **Operational basics:** log destination for structured JSON logs and correlation IDs; basic login throttling for a public endpoint (NFR Security).

Recommendation: add an AD (or a short "Deployment & Environments" section) deciding seed invocation, migration runner, secret store, API base URL config, CI gates and e2e target; move the rest into Deferred or a new Open Questions list.

### F-2 [HIGH] AD-4 revision rule is internally inconsistent and does not prevent two "current" records

- **Equal-start collision.** The Rule says a revision "cannot start before the current record's start" (matches PRD FR-5), which permits a new start equal to the current start. Closing the previous record then sets `end = start - 1 day`, violating the stated `effective_end >= effective_start` constraint, and the new row would also violate `unique (employee_id, effective_start)`. The Rule should require start strictly after the current record's start (PRD wording is ambiguous; the spine must settle it and the PRD or its assumption should be aligned).
- **Overlap not prevented.** The listed constraints (unique start, end >= start) do not stop overlapping ranges, so the stated Prevents ("two current salaries for one employee") is not actually guaranteed by the database. A `daterange` exclusion constraint (`btree_gist`) or an equivalent decided mechanism is needed, or the Prevents should be weakened to "prevented by domain code only".
- **Open-ended record representation** (NULL `effective_end` vs sentinel date) is not decided; every query and the domain "covers today" check depend on it.
- **Future-dated revisions** are allowed (PRD assumption), but the Rule does not say what happens when a future-dated record already exists and another revision arrives, or which record is "current" meanwhile.
- **"Today" definition** (server UTC vs company/employee-country time zone) is undecided; it affects "current", analytics and tests.
- **Concurrency:** "Several HR Managers share the system" yet there is no optimistic-locking/version decision for two simultaneous revisions or profile edits. Neither decided nor deferred.

### F-3 [HIGH/MEDIUM] AD-2 contradicts AD-6, FR-3 and FR-4, and is not mechanically enforceable

- **AD-6 vs AD-2.** AD-6 mandates "one repository query" returning a projection that joins Current Compensation Records with Employees (country, department, job title). AD-2 forbids reaching into another module's tables, entities or repositories. The spine does not say which module owns that query or how `simulator` and `analytics` obtain it (analytics service interface presumably, but unstated).
- **FR-3 and FR-4 span modules.** Viewing an Employee composes employees + compensation + exchange rates; adding an Employee creates the profile and the initial Compensation Record plus audit in one transaction. The spine does not say which module orchestrates or owns the transaction boundary, which is exactly where two units will diverge.
- **No published-API package.** The naming convention defines only `web/application/domain/persistence`, yet AD-1 and AD-2 rely on a module's "public API" and "application service interface". With no `api` (or equivalent) package, the ArchUnit rule is not definable. AD-2 itself names no ArchUnit check, so only AD-1's domain rule is enforced.
- **`shared` is unbounded.** The repo layout has a `shared` package (exception handler, `Rounding`, correlation ID) that is not in AD-2's module list and has no dependency rule.
- **Exchange-rate ownership is inconsistent.** AD-2 lists six modules with no exchange-rates module; the Capability Map puts FR-9 in `compensation`; AD-5 refers to an "exchange rates" application service; the frontend has an `exchange-rates` feature. Pick one owner.

### F-4 [MEDIUM] AD-5 (audit) is enforced only by tests, with gaps in scope and content

- The Rule's enforcement is "integration tests prove a failing audit write rolls back". Nothing prevents a new application service from forgetting to call `AuditTrail.record`. A structural guard (for example an ArchUnit rule that persistence writes to audited entities are only reachable from services that depend on `AuditTrail`, or a single save-and-audit seam) is not specified.
- "`before`/`after` exclude sensitive fields" does not define which fields (name? work email?). PRD says audit holds "no more personal data than needed". An explicit allowlist per entity type is a divergence point.
- Scope ambiguity: AD-5 binds FR-4, but the PRD audits only profile edits, salary revisions and rate edits. Is adding an Employee (with its initial Compensation Record) audited? Undecided.
- FR-12 seed: AD-4 says no code path writes outside `revise`, yet the seed must insert closed historical records directly. The spine should state that the seed runner is an allowed bypass (and does not write audit entries), or it contradicts AD-4/AD-5.

### F-5 [MEDIUM] Money and analytics details still leave room for divergence

- **Exchange rate precision:** `rate_to_usd` has no stated scale/precision (INR to USD needs many decimals); `NUMERIC(19,4)` is specified for amounts only.
- **`BigDecimal` division and `MathContext`:** medians (even-sized groups), percentage deviation and rate multiplication need a decided scale/`MathContext`; otherwise `divide` throws or two units round differently. "One shared `Rounding` helper" covers presentation only.
- **Median definition** for even-sized groups (mean of the two middle values) is not stated; AD-1 lists a "median" in domain but does not require one shared implementation.
- **FR-8 ranking currency:** highest/lowest paid per department spans countries; the spine says anomalies use original currency but is silent on FR-8 (USD equivalent presumably) and on tie-breaking.
- **Sorting/filtering by salary in FR-2 lists:** USD Equivalent is computed at read time and AD-6 forbids SQL aggregation, yet server-side sort/pagination by salary is not addressed.
- **Country-to-currency mapping** (FR-4 validation: "currency must match country") has no single source of truth (DB table, domain constant, frontend list). Seed, validation and the UI can each hold their own.
- **Employee ID generation** (user-entered vs generated, format) is undecided though it is a unique business key (AD-9) that FR-4 and the seed must both produce.
- FR-7 "local currency shown alongside when a single country is selected" is not reflected in any AD.

### F-6 [MEDIUM] Several Rules are convention-only (weak enforceability)

| AD | Enforceable? | Gap |
| --- | --- | --- |
| AD-1 | Mostly | "another module's non-public API" undefined (see F-3). |
| AD-2 | No | No mechanical check named; contradicts AD-6 (F-3). |
| AD-3 | Partly | No automated ban on `float`/`double` in domain/persistence (ArchUnit/static analysis); `MathContext` missing (F-5). |
| AD-4 | Partly | Defective (F-2). |
| AD-5 | Test-only | See F-4. |
| AD-6 | Convention | Nothing stops another module adding its own aggregate SQL; ties to Deferred note below. |
| AD-7 | Partly | Single PK per currency is good; country-currency source and rate scale undecided. |
| AD-8 | Mostly | Should name default-deny security config plus a test that every route except login returns 401; JWT secret/algorithm source unspecified. |
| AD-9 | Mostly | Max page size value and allowed sort-field whitelist not given. |
| AD-10 | No | No lint/test for "no `localStorage`/`sessionStorage`", "no calculations in components". |
| AD-11 | Mostly | Missing `ddl-auto` rule and CI check that Flyway migrates a clean DB. |
| AD-12 | Process only | "Tests written before implementation" cannot be verified from artifacts. |

### F-7 [MEDIUM/LOW] AD-12 H2 fallback undermines the Testcontainers rule

The ER diagram uses `jsonb` and `NUMERIC` semantics; H2 differs on both. A "temporary local fallback" that can pass tests PostgreSQL would fail is a divergence between local and CI/prod. Either ban it in CI or remove it.

### F-8 [LOW] No Open Questions section; stack versions loosely pinned

- The spine has inline `[ASSUMPTION]` tags (Java, Angular) but no Open Questions list, so undecided dimensions in F-1 to F-5 have nowhere to live. The checklist requires each dimension to be decided, deferred or open.
- Stack entries "current stable" for Gradle, Node, Playwright, Angular and Spring Boot ("4.1.x ... confirm") are not reproducible; pin toolchain/Node versions (Gradle toolchain, `.nvmrc`) at scaffold so local, CI and Azure match. Cause of drift between units.

## Deferred list assessment

- Caching and SQL aggregation (AD-6): acceptable only if aggregation is moved wholesale behind the same domain interface; otherwise one endpoint may compute medians in SQL while another uses Java. Add a guard: any SQL-side stat must be validated against the domain implementation in a test.
- Refresh tokens/revocation, RBAC, sign-up, Excel import, rate history, external monitoring, backups/HA: acceptable deferrals that map to PRD non-goals or assumptions. Note that "Azure managed defaults" is a decision, not a deferral, and should name the tier assumptions.
- Missing from Deferred and not decided anywhere: everything in F-1 and the concurrency item in F-2.

## FR coverage check

| FR | Mapped | Seam / issue |
| --- | --- | --- |
| FR-1 | auth / AD-8 | JWT secret source, login throttling not decided |
| FR-2 | employees / AD-2,3,5,9 | Salary sort/filter, search/index strategy not addressed |
| FR-3 | employees + compensation | Cross-module composition owner undecided (F-3) |
| FR-4 | employees | Cross-module transaction, ID generation, country-currency mapping, audit scope (F-3, F-4, F-5) |
| FR-5 | compensation / AD-4 | Equal-start defect, concurrency (F-2) |
| FR-6 | audit / AD-5 | Sensitive-field allowlist, enforcement (F-4) |
| FR-7 | analytics | Median/MathContext, local-currency display (F-5) |
| FR-8 | analytics | Ranking currency and tie-breaking undecided |
| FR-9 | compensation (AD-7) | Module ownership inconsistent; rate scale |
| FR-10 | analytics / AD-6 | Well specified; flags use original currency |
| FR-11 | simulator | Data access path to projection undecided (F-3) |
| FR-12 | seed runner | Bypasses AD-4/AD-5 unstated; deployed invocation undecided (F-1, F-4) |

## Strengths worth keeping

- Single projection plus domain-computed analytics (AD-6) removes the most likely analytics/anomaly/simulator disagreement, and the original-currency anomaly comparison is a sharp, rate-independent decision.
- Money rule (AD-3) with source-of-truth original currency and USD computed at read time is clear and PRD-aligned.
- Audit-in-same-transaction (AD-5) with explicit service calls instead of AOP matches PRD FR-6 closely.
- API, auth and frontend conventions (AD-8 to AD-10) cover the common per-module drift points and are concise.
