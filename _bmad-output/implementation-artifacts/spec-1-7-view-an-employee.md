---
title: 'Story 1.7: View an employee'
type: 'feature'
created: '2026-11-18'
status: 'done'
route: 'dispatch'
review_loop_iteration: 1
baseline_commit: '048c1f5be4d4ad99ff4f7a426fd26b76f75cda4e'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-incubyte-assessment-2026-11-17/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** From the directory the HR Manager cannot open anyone. They need one page per employee with the profile, current pay in local currency and USD, and the full salary history, and the whole sign-in, search and open flow must be proven end to end in a real browser.

**Approach:** `employees` serves `GET /api/v1/employees/{id}`; `compensation` serves `GET /api/v1/employees/{id}/salary-history` (route ownership from the spine, without reading the employee table). Angular adds the guarded lazy route `employees/:id` with a detail page. A Playwright project in `e2e/` runs a smoke test against the real stack, and CI gets an `e2e` job.

## Boundaries & Constraints

**Always:** Detail response has Employee ID, names, work email, job title, department, country code and name, city, employment type, hire date, status, current salary `{amount, currency}` and its USD equivalent, from one rate snapshot, rounded for display only through `Rounding`. An unknown id or one that is not a UUID gives Problem Details 404 `EMPLOYEE_NOT_FOUND`. History lists every record newest first with id, amount, USD equivalent at current rates, `effectiveStart`, `effectiveEnd` (null for the open record) and `current`, true only for the record whose range covers today (injected `Clock`); an employee with no records at all gives 404 `SALARY_HISTORY_NOT_FOUND`. Whether a period covers a date is a framework-free, unit-tested helper. Compensation never reads the employee table. Frontend: guarded lazy route with a tab title; loading, not-found and error-with-retry states; back link to the directory; the Employees nav item stays highlighted. Playwright: smoke test uses the local demo account with the password from `E2E_PASSWORD` (default: the documented local demo password), the seeded employee EMP-000001, and proves sign-in, search, opening the page, name, both salaries, history and sign-out; locally it uses the installed Chrome, in CI it installs Chromium.

**Never:** No editing, no salary revision (Epics 2 and 3), no audit view, no simulation step in the smoke test yet (Story 6.2), no secrets in CI beyond the documented local demo values, no weakening of ArchUnit rules.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Known employee | id of EMP-000001 | profile plus current salary in AUD and USD | N/A |
| Unknown or malformed id | random UUID, or `abc` | 404 `EMPLOYEE_NOT_FOUND` | N/A |
| History | employee with several records | newest first, gapless chain, one `current` record, open record has null end | N/A |
| No records | employee id with no salary rows | 404 `SALARY_HISTORY_NOT_FOUND` | N/A |
| No token | any call | 401 | N/A |
| Page states | slow, missing, failing responses | loading bar, not-found message with link back, error with retry | N/A |
| Smoke test | real stack, seeded data | signs in, finds and opens Charlotte Smith, sees AUD and USD pay and history, signs out | N/A |

</frozen-after-approval>

## Code Map

- `employees/web/EmployeeDirectoryController` and `application/EmployeeDirectoryService`, `persistence/EmployeeDirectoryStore` -- extend with a single-employee lookup
- `compensation/{api,application,domain,persistence,web}` -- add history query, service, domain helper, controller; `CurrentSalaryService` shows the clock pattern
- `frontend/src/app/features/employees/` -- `EmployeeService` and the directory page already exist; add the detail page; `app.routes.ts` gets the new route
- `.github/workflows/ci.yml`, `e2e/` (README only so far) -- Playwright project and CI job

## Tasks & Acceptance

**Execution:**
- [ ] Backend tests first: domain helper unit test, `EmployeeDetailIT` and history integration tests -- test-first
- [ ] Backend detail and history endpoints -- the API
- [ ] Frontend specs first, then service methods, detail page, route -- the screen
- [ ] `e2e/` project, smoke test, CI job, then run the stack locally and the smoke test against it -- the proof

**Acceptance Criteria:**
- Given the seeded data, when the matrix scenarios run, then all pass.
- Given the local stack, when the Playwright smoke test runs, then it passes.
- Given ArchUnit and all suites, then everything passes.

## Implementation Notes

Delivered: `GET /employees/{id}`, `GET /employees/{id}/salary-history`, `SalaryPeriod`, `MoneyResponse` moved to `shared.web` (one response shape for both modules), the detail page and route, the Playwright smoke test and the CI `e2e` job. The smoke test passed against the real stack locally (MariaDB container on 13306, seeded API, `ng serve`).

## Review triage (three independent reviewers: blind code, edge cases, acceptance audit)

**Patched**
- The page treated any 404 from the history call as "no history", hiding routing faults; now only `SALARY_HISTORY_NOT_FOUND` is an empty history, and any other history failure is the error state (not "employee not found"). Tests added for each.
- The not-found message is announced to assistive technology (`role="status"`).
- CI e2e waited only for `/actuator/health`, which is UP before the seed runs; it now waits until sign-in works and 10,000 employees exist.
- The smoke test now clicks the row whose Employee ID is EMP-000001 (names repeat in the seed) and asserts formatted AUD and USD amounts, not just currency codes.
- `current` via the injected `Clock` had no controlled-clock test; `SalaryHistoryServiceTest` covers the covering record, nothing covering today, overlapping periods, one rate snapshot, and no records.

**Deferred** (recorded in `deferred-work.md`)
- "Today" is the UTC date (AD-4); a per-country business day is a product decision.
- Any signed-in user may read pay: the PRD has one role, HR Manager.
- Per-row currency or missing-rate failures take down the whole response; not reachable with seeded currencies.
- A future-dated open record reads "Present"; scheduled raises arrive with Epic 3.
- The profile and history are separate requests with their own rate snapshots.
- Amounts use the `number` pipe (two decimals), which is wrong for zero-decimal currencies.
- Integration test isolation depends on the shared container and the fixed seed.
- The tab title does not include the employee's name; the id in the URL is not trimmed.
