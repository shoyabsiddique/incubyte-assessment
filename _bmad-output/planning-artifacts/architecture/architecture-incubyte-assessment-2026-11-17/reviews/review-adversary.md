# Adversarial Review — ARCHITECTURE-SPINE.md (ACME Salary Management)

Method: for each hole, two units one level down (modules, stories, or backend vs frontend) that each follow every AD literally and still do not fit together. Each finding ends with the AD that would close it. Spine not edited.

## Verdict

**Not ready as a build substrate; fix 5 blocking holes first.** The ADs govern how a single module is built well (pure domain, Money, append-and-close, transactional audit). They say almost nothing about the seams between modules, which is where independently built units collide. Two ADs actively contradict each other (AD-2 vs AD-6), and the dependency graph between modules is unspecified, so a cycle (employees <-> compensation) is the natural outcome. The wire contract (AD-9) fixes style but not shapes, so backend and Angular stories can both comply and still not connect.

Severity: B = blocking (will cause rework or wrong data), M = major, m = minor.

---

## F1 (B) AD-2 vs AD-6: the analytics projection has no legal owner

**Pair:** `analytics` story vs `employees`/`compensation` stories.
- AD-6 says ONE repository query returns country, department, job title, amount, currency, ids for active employees' Current records. That is a join of the employees table (country, department, job title, status) and the compensation table (amount, currency, dates).
- AD-2 says a module never touches another module's tables, entities or repositories; only application service interfaces.
- Unit A (analytics) writes the join in an analytics repository: AD-6 satisfied, AD-2 violated. Unit B (analytics) obeys AD-2 and fetches 10,000 employees from `employees` service and 10,000 records from `compensation` service, then joins in memory: AD-2 satisfied, AD-6 ("one repository query") violated, and 2 x 10,000 rows per request threatens the 500 ms target.
- Both compliant readings are defensible; ArchUnit can only enforce one.

**Extension:** `simulator` needs the same projection. Does it call analytics' application service (making analytics' projection DTO public API) or duplicate the query (a second "one query")? FR-10 also needs employee names, which the projection (ids only) lacks, so the anomaly story adds an N+1 or a second cross-module read.

**Fix (new AD):** name the owner of the cross-module read model. Recommended: `employees` and `compensation` publish a read-only `CurrentCompensationView` (a published interface plus DTO, in a named public package); `analytics` owns the single projection built from it; `simulator` and anomalies consume analytics' projection service, never their own query. State the projection fields including names. Or explicitly carve out: "analytics persistence may read employees/compensation tables via a database view owned by migration X" and amend AD-2 accordingly.

## F2 (B) No dependency direction between modules; employees <-> compensation cycle is the default

**Pair:** `employees` module vs `compensation` module.
- FR-4 add-employee creates the profile and an initial Compensation Record: employees' service must call compensation's service.
- Revising a salary (FR-5) needs the employee to exist, to be non-terminated if that matters, and its country for the currency check: compensation's service must call employees' service.
- Each only calls the other "through its application service interface", so AD-2 is satisfied twice and the module graph has a cycle. Spring wires it with setter injection or `@Lazy`, or one story quietly reads the other's repository.
- Also unspecified: who orchestrates the create-employee transaction (employees calling compensation, or a third coordinator) and whether the initial record goes through the `revise` operation (AD-4 says no other code path inserts) or a separate "open history" operation.

**Fix:** add an AD with the module DAG, enforced by ArchUnit `slices().should().beNotCyclic()` on module packages. Suggested: `audit` and `shared` depend on nothing; `employees` -> `compensation` (employee creation calls `compensation.openHistory`) is wrong direction for validation, so instead make compensation depend on a tiny `EmployeeLookup` port published by employees only if employees does NOT depend on compensation, and move create-with-initial-record into an orchestrating application service. Pick one, write it down, and define `openHistory` as the second sanctioned write path in AD-4.

## F3 (B) Audit entries: entity identity and payload shape are uncontracted, so Employee audit trail misses salary changes

**Pair:** `compensation` story (writer) vs `audit` story (reader/FR-6 UI) vs `employees` story (writer).
- AD-5 gives `record(userId, entityType, entityId, operation, before, after)` and nothing on vocabulary or types.
- Compensation writes `entityType="COMPENSATION_RECORD"`, `entityId=<record uuid>`. Employees writes `entityType="EMPLOYEE"`, `entityId=<employee uuid or business Employee ID>`. Rate edits write `entityId=<currency code>`.
- FR-6 requires "the audit trail of an Employee". The audit reader filters by `(entityType=EMPLOYEE, entityId)`: salary revisions never appear, because they are filed under the record id. The reader could be changed to join, but audit may not touch other modules' tables (AD-2), so it cannot map record id to employee.
- `before`/`after` are jsonb with no schema: compensation serializes `amount` as a JSON number (Jackson `BigDecimal`), contradicting AD-9's decimal strings and silently losing precision in any JS consumer; employees writes full profile snapshots while AD-5 says only changed fields. The Angular audit screen cannot render a diff generically.
- A revision touches two rows (closes one, inserts one). One audit entry or two? FR-6 says one entry with previous and new value; the compensation story may write two.
- `AuditTrail.record` is called from other modules' `@Transactional` methods; with `REQUIRES_NEW` (a common default choice for "audit must not be lost") AD-5's "both roll back" fails. Propagation is not specified.
- Entry display needs the actor's name/email: audit would need auth's service; unspecified.

**Fix (tighten AD-5):** (a) entry carries `subjectEmployeeId` (nullable for rate edits) in addition to entity type/id, so the per-employee trail is a single indexed filter; (b) closed vocabularies for `entityType` and `operation` as enums in a shared public package; (c) `before`/`after` are `Map<String,String>` of changed fields only, money as `{amount: decimal string, currency}`, dates ISO; (d) one entry per business operation (revision = one entry); (e) `record` requires `Propagation.MANDATORY`; (f) actor shown via a stored `userEmail` snapshot or an auth lookup, decide which.

## F4 (B) "Current" and the revise operation: two current salaries are still constructible

**Pair:** `compensation` domain story vs `analytics`/`employees` read stories vs seed.
- AD-4: "Current = the record whose range covers today", constraints are unique `(employee_id, effective_start)` and `effective_end >= effective_start`. Nothing says how the open record is represented: `effective_end IS NULL`, or a sentinel `9999-12-31`. Employees' read query tests `effective_end IS NULL`; the analytics query tests `start <= today AND end >= today`; the seed writes the sentinel. Each is correct for its own reading of the AD; they disagree on the same rows.
- Boundary bug inside the AD: "cannot start before the current record's start" permits start == current start. Then closing sets `end = start - 1 < start`, violating the AD's own constraint and failing at the DB (or, in domain code that skips the check, producing a negative-length record). Needs `start > current.start`.
- Future-dated revisions (PRD allows): after revising with a future start F, the old record has `end = F-1` (still Current today) and a pending record exists. A second revision with start between today's record start and F: which record does it close, and is "the current record's start" today's record or the latest? Story A (latest-record semantics) and story B (today's-record semantics) both pass FR-5's tests and diverge; one produces overlapping ranges.
- Gaps and overlaps: unique `(employee_id, effective_start)` does not prevent overlap. Two HR managers revising the same employee concurrently (the PRD says several share the system) each close "the current record" and insert a new one with different starts: two open-ended records, i.e. exactly the "two current salaries" AD-4 claims to prevent. No version column/optimistic locking and no exclusion constraint is required.
- "Today": `LocalDate.now()` in domain code breaks AD-1 purity and is timezone-ambiguous (Azure UTC vs HR in India: Current flips at different times for the UI and the analytics job).
- `revise(employee, ...)` takes an `employee` in the compensation domain; AD-1 forbids importing employees' domain. What is the parameter type?

**Fix (tighten AD-4):** open record = `effective_end IS NULL` (state it; seed and all queries use it); `newStart > latest.start` strictly; define semantics for future-dated revisions (revise always operates on the latest open record; at most one future record, or forbid revising while a future record exists); add a PostgreSQL exclusion constraint on `daterange(effective_start, effective_end, '[]')` per employee plus `@Version` on the history root; inject a `Clock` (UTC business date) into domain operations; the domain type is `CompensationHistory(employeeId, records)`, not Employee.

## F5 (B) Country/currency invariant and exchange-rate ownership have no single owner

**Pair:** `employees` story (profile edit) vs `compensation` story vs `seed` vs Angular forms.
- FR-4: the Original Currency must match the Employee's country. Who owns the country-to-currency map? Employees validates at creation; compensation validates on revise (or not); the seed hard-codes its own (the eight pairs) as a third copy; the Angular add-employee form needs it to pre-fill currency (AD-10 forbids duplicating business rules, so it needs an endpoint nobody owns).
- Profile edit (FR-4 allows editing profile fields) can change `country`. Employees module passes (the profile is valid); compensation records keep the old currency. Now the Employee is in Germany with INR records, the Peer Group (country+dept+title) is still "single-country" per AD-6, but its currency is not, and AD-6's "comparison uses original-currency amounts, so flags never depend on exchange rates" silently compares INR to EUR numbers. Nothing in the spine blocks or handles a country change.
- `revise(employee, newAmount, effectiveStart)` has no currency parameter. Does a revision inherit the previous currency, or take the country's? Story A inherits, Story B derives from country; they agree until the above edit.
- Exchange rates: AD-2 has no exchange-rate module; the map puts it in `compensation`, AD-5 lists "exchange rates" as a separate caller. Where does `ExchangeRate` live in packages? Does a record's `currency` FK to `exchange_rate`? (ER diagram implies yes.) Seed order and migrations then depend on that choice.
- Rate rules missing: `rate_to_usd > 0` (zero or negative rate makes salaries vanish and passes Bean Validation if only `@NotNull`); scale (NUMERIC(19,4) is fine for money, but INR ~0.0119 USD needs more digits; one migration author picks (19,4), another (19,8), and the USD-equivalent figures differ); behaviour when a currency has no rate row (analytics skips the row silently; simulator throws 500; employee detail shows null): three modules, three behaviours.

**Fix (new AD "Country, currency, rates"):** one `CountryCurrency` registry in the shared kernel (ISO country code, currency) used by employees validation, compensation, seed and an `/api/v1/reference/countries` endpoint for Angular; profile edit that changes country is rejected unless the current-record currency still matches (or defined as "country is immutable after creation"); revise inherits currency from the open record; rate must be `> 0`, `NUMERIC(19,8)`, FK from `compensation_record.currency`; any conversion with a missing rate fails the whole request with a 500-class Problem Details naming the currency (never skip silently).

## F6 (B) Conversion and Money are specified as rules but not as a shared type: three conversions, three roundings

**Pair:** `analytics` vs `simulator` vs `employees` (FR-3 USD equivalent) stories.
- AD-1 puts "currency-conversion logic" in `domain` packages; AD-1 also forbids a domain from importing another module's non-public API; AD-3 names a shared `Rounding` helper but no shared Money/Currency/RateTable type; the repo tree lists `shared` but AD-2's module list does not, and no AD says whether domain packages may import `shared`.
- Result: compensation defines `Money(BigDecimal, Currency)`; analytics defines `Salary(BigDecimal, String)`; simulator defines `Amount`. Each has its own `toUsd(rateTable)`. Each is internally correct; the figures for the same salary differ in the sixth decimal (one divides, one multiplies, `MathContext` differs; `BigDecimal.divide` without a scale throws on non-terminating results), so SM-3 "reports reconcile" fails.
- "Reads rates at request time" (AD-7): per row, or one snapshot per request? A rate edit mid-request makes the analytics total and the simulator's "current payroll" differ within one HTTP call chain. Needs "one `RateSnapshot` per request, passed into domain functions".
- Presentation rounding: "applied for presentation only". Rounding at the API boundary (strings come out rounded) or in Angular? AD-10 says Angular computes nothing, so the API rounds. Then `additional = projected - current` displayed from rounded fields may not equal the rounded exact difference; Story A sends rounded figures, Story B sends unrounded and lets Angular `number` pipe round (JS `Number` of a decimal string: precision loss, AD-3 violated on the client).

**Fix (new/tightened AD-3):** the shared kernel (`com.acme.salary.shared.money`: `Money`, `CurrencyCode`, `RateSnapshot`, `Rounding`) is the only Money type; domain packages may import `shared.money` and nothing else outside their module (amend the ArchUnit rule); `convertToUsd(Money, RateSnapshot)` is the single conversion function (multiply by rate, no intermediate rounding); one snapshot per request; API returns money as decimal strings rounded HALF_UP to 2dp; Angular treats money as opaque strings (no `parseFloat`).

## F7 (M) Wire contracts: AD-9 fixes style, not shapes, so Angular and the API both comply and still do not connect

**Pair:** backend module story vs the Angular feature story consuming it.
- Money: "decimal strings with a currency code": nested `{amount, currency}` or flat `amount` + `currency`? Compensation returns nested `salary`; employees returns flat `amount`/`currency`; analytics returns `averageUsd` (no currency field). Angular needs one `Money` type.
- Percentages: simulator request `incrementPercent: 10` (the user's "10%") vs fraction `0.10`; anomaly `deviation` as `25.0` (percent) vs `0.25`. The Angular simulator form (percent input) and the anomaly table pick one each; Backend picks the other. Both match AD-9.
- Pagination envelope: AD-9 gives request params (`page`, `size`, `sort`) and a max size but not the response: Spring `Page` JSON (`content`, `number`, `totalElements`) vs custom `{items, page, total}`; 0-based vs 1-based pages; and whether a size over the max is clamped or rejected with 400.
- `sort`: names of sortable fields are DTO properties or entity properties? Sorting the employee list by salary is impossible in `employees` (it does not own the table, AD-2) and meaningless across currencies in SQL; FR-2 only says "sorting". Whitelist needed.
- Problem Details: extension member for per-field errors unnamed (`errors` vs `fieldErrors` vs `invalid-params`), and no stable `type`/`code` for duplicate Employee ID vs duplicate email, which FR-4 requires distinct clear messages for. The Angular form needs to bind each to a control.
- 401 produced by the Spring Security filter chain (entry point) bypasses the `@ControllerAdvice` shared handler: an empty 401 body for expired tokens, Problem Details elsewhere. The AD-10 interceptor also redirects on every 401, including the login endpoint's own wrong-password 401, so a failed login reloads the sign-in page and loses the message AD-8 mandates.
- Correlation ID header name unspecified (`X-Request-Id` vs `X-Correlation-Id`); with CORS locked to one origin (AD-11) the header must also be in `exposedHeaders`, or the SPA cannot read it.
- Aggregate reads: the employee detail screen (FR-3) needs profile + current record + USD equivalent + history. Does `GET /employees/{id}` compose all (employees web layer calling compensation's service) or does Angular call `/employees/{id}` and `/employees/{id}/compensation` separately? Ownership of the URL space under `/employees/{id}/...` for the compensation controller and the audit controller is not assigned.

**Fix:** extend AD-9 with the concrete shapes: `Money`, `Page<T>`, `ProblemDetail` extension (`errors: [{field, code, message}]`, stable `code` values), percentage as percent numeric string vs fraction (choose one), whitelist of sort keys per endpoint, header name, route-ownership table (`/employees` -> employees; `/employees/{id}/salary-revisions` and `/employees/{id}/compensation` -> compensation; `/employees/{id}/audit` -> audit), a security entry point and access-denied handler that emit the same Problem Details, and "interceptor ignores 401 from `/auth/login`". Prefer an OpenAPI contract generated from the controllers and an Angular client generated from it, so the two sides cannot drift.

## F8 (M) Seed runner is an unowned second writer that bypasses AD-2, AD-4 and AD-5

**Pair:** seed runner vs every module with an invariant.
- AD-11: the seed is a separate deterministic runner with batched inserts. Batched inserts means JDBC or JPA batching straight into employees, compensation, exchange_rate and app_user tables: it violates AD-2 (not through application services) and re-implements AD-4 (closing ranges for multi-record histories, FR-12) in its own code. The revise rules then exist twice; the seed's version has no future-dated or sentinel handling consistency (see F4).
- Audit: SM-2 says "100% of salary revisions have a matching Audit Entry". Seeded history records have none; a verification query for SM-2 over the demo data fails (or the seed writes fake audit rows in some other format, per F3).
- Determinism (FR-12): "fixed random seed" fixes the data but UUID primary keys (AD-9) generated with `UUID.randomUUID()` and dates relative to `LocalDate.now()` (hire dates, effective starts) differ on each run and each day; "same seed produces the same data" is untestable. Employee IDs: system-generated by sequence (format?) or entered by the HR Manager? PRD says unique and rejected on duplicate (implies entered), seed generates `EMP-00001`-style; a live-created employee collides or violates a validation pattern that the seed does not obey.
- Seeded accounts: BCrypt cost with 10,000 inserts is irrelevant, but passwords in the seed runner vs `.env.example` (AD-11 forbids secrets in the repo): where do demo account credentials come from?
- The seed runner has no package in `com.acme.salary...` and is outside AD-1/AD-2's ArchUnit rules.

**Fix (new AD-11 clause):** the seed is a client of the application layer or of a dedicated `seed` package that is allowed to use persistence adapters, but must build histories using the domain `CompensationHistory` factory (same invariants); declare the exception to AD-2 explicitly; UUIDs from a seeded `Random` (name-based UUIDs); all dates are offsets from a fixed `seedDate`; define the Employee ID format and who generates it; state that seeded history carries no audit entries and amend SM-2 to "revisions made through the API"; demo credentials from environment variables.

## F9 (M) Reference vocabularies have no owner: department, job title, country, status, employment type

**Pair:** `employees` filter story vs `analytics` grouping story vs Angular filter dropdowns.
- Peer Group = equal country + department + job title. They are free text on the profile. "Software Engineer " (trailing space), "Software engineer" or a typo splits a peer group below the 5 threshold and silently suppresses flags. Employees (profile edit) accepts it; analytics groups by string equality; nothing normalizes.
- Status: analytics filters "active"; employees defines the enum; analytics' query hard-codes `'ACTIVE'` vs `'Active'` vs `1` (three plausible choices). PRD names "active", "terminated", "on-leave" assumptions but not the enum.
- Filter dropdowns for FR-2, FR-7, FR-10, FR-11 (country, department, job title, plus simulator scope) need the distinct values. Employees serves its own list; analytics serves another (only active staff, so a department with only terminated staff disappears from one list and not the other); the simulator's "one department" scope picks a third.
- Country as ISO alpha-2 vs full name ("IN" vs "India"): shown in UI, used as key in analytics; FR-12 lists names.

**Fix:** AD with canonical forms (country = ISO 3166 alpha-2, trimmed and case-normalized free text with a unique index on normalized form, or a controlled department/job-title lookup table owned by `employees`); status/employment-type enums defined once in employees' published API; one `/api/v1/reference/*` endpoint set owned by employees used by every Angular filter.

## F10 (M) Concurrent edits and idempotency: no versioning anywhere

**Pair:** two Angular sessions (or two stories' API tests) editing one entity.
- "Several HR Managers share the system." Two edit the same employee profile: last write wins; AD-5's audit before/after for the second write records a `before` the editor never saw. Two edit the same exchange rate: ditto. No `version` column, ETag or `If-Match` requirement in AD-9.
- Double-submit of a revision (retry after timeout) produces a second revision if the start dates differ and a confusing unique-constraint 500 if equal; no idempotency or conflict-to-409 mapping.

**Fix:** add `@Version` on profile, rate and history aggregates; API returns `version` and requires it on updates (`409` with Problem Details on mismatch); map unique/exclusion violations to 409.

## F11 (m) Smaller holes worth one line each

- **H2 fallback vs PostgreSQL features (AD-12):** jsonb audit columns and the daterange exclusion constraint above do not exist on H2; Flyway migrations written for Postgres will not run on the "temporary local fallback". State that migrations are Postgres-only and H2 is dropped, or avoid Postgres-only features.
- **Simulator vs analytics definitions:** the simulator's "current payroll" and FR-7's sum (count x average) must use the same projection, active-only, same rates; spec says both read from "one projection" but the simulator scope filters (country/department) are applied in SQL in one story and in the domain in another unless AD-6 says all filtering is in the domain (it says computation is, not filtering).
- **Top/bottom N (FR-8):** ordering currency (USD across a multi-country department), tie-break rule, `N` maximum: unspecified; two stories give different "lowest 5" for ties.
- **Anomaly list size:** computed in memory per request (AD-6); is the result paginated under AD-9's server-side paging, or returned whole? A frontend table built for pages receives an unbounded list.
- **Deviation arithmetic:** median of even-sized groups and `deviation = (salary - median) / median` need an explicit `MathContext`/scale; "strictly more than 20%" tested on a rounded value vs the exact fraction gives different flags at the boundary (unit tests agree with whichever the author picked).
- **JWT identity type:** principal user id as UUID vs email string; `AuditTrail.record(userId, ...)` parameter type is unspecified.
- **Logout:** stateless JWT with no revocation means "sign out" is client-only; fine for v1 but say so, or the e2e test for "signed out token is rejected" will be written by one story and fail in another.

---

## Recommended AD changes (condensed)

1. **New AD-13 Module graph and shared kernel:** acyclic dependency DAG (ArchUnit slice check); `shared` contents enumerated (Money, CurrencyCode, RateSnapshot, Rounding, enums, Problem Details, Page); domain may import `shared`; cross-module read model owner named (closes F1, F2, F6).
2. **Tighten AD-2/AD-6:** one cross-module read interface and one analytics projection owner; simulator and anomalies consume it; filtering in the domain.
3. **Tighten AD-4:** open record = `effective_end IS NULL`; strict `newStart > latest.start`; future-dated semantics; exclusion constraint; `@Version`; injected `Clock`; `CompensationHistory` keyed by employee id; sanctioned second write path `openHistory`.
4. **Tighten AD-5:** audit entity identity (add `subjectEmployeeId`), closed vocabularies, payload shape, one entry per operation, `Propagation.MANDATORY`, actor display.
5. **New AD Country/currency/rates:** registry owner, profile-edit rule, revise inherits currency, rate `> 0` and scale, missing-rate behaviour, one snapshot per request.
6. **Tighten AD-9:** concrete JSON shapes (Money, Page, Problem Details with codes, percentage), sort whitelist, header names, route ownership, filter-chain errors in the same shape, contract-first OpenAPI with generated Angular client.
7. **Tighten AD-11:** seed uses domain factories, exception to AD-2 declared, deterministic UUIDs and dates, Employee ID format and generator, credentials from environment, seeded history has no audit entries.
8. **New AD Reference data:** canonical forms and owner for country, department, job title, status; shared lookup endpoints.
