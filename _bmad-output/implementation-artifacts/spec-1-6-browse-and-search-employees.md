---
title: 'Story 1.6: Browse and search employees'
type: 'feature'
created: '2026-11-18'
status: 'in-progress'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '81180aa7e85e66e315e2cb7fc65a594cad2c318e'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-incubyte-assessment-2026-11-17/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The HR Manager cannot find anyone among 10,000 employees. They need a fast directory they can search, filter, sort and page through, with each person's current pay in local currency and USD.

**Approach:** `employees` serves `GET /api/v1/employees` (server-side paging, search, filters, whitelisted sort) and `GET /api/v1/employees/filters`. Salaries come only through new `compensation.api` queries (current salaries for a batch of ids; ranking ids by current USD), never by reading the compensation table. The Angular Employees page shows a paginated, sortable table with debounced search and filters.

## Boundaries & Constraints

**Always:** Envelope `{items, page, size, totalElements, totalPages}`; `page` starts at 0, `size` defaults to 20 and above 100 is rejected with 400 `PAGE_SIZE_TOO_LARGE`. Sort values are whitelisted (`employeeId`, `name`, `jobTitle`, `department`, `country`, `hireDate`, `status`, `salary`) with `asc` or `desc`; unknown sort or filter values give Problem Details 400 with a code (`INVALID_SORT`, `INVALID_FILTER`, `INVALID_PAGE`). Ties always break by Employee ID so paging is stable. `q` is split on whitespace and every token must match first name, last name, work email or Employee ID (case-insensitive contains); `%`, `_` and backslash in `q` are matched literally. Filters (country, department, jobTitle, employmentType, status) combine with AND. Money is `{amount, currency}` decimal strings; USD equivalent is computed from one rate snapshot per request; display rounding only through `Rounding`. Current salary = the record whose range covers today (injected `Clock`); employees without one show null salary and sort last. Employees never queries the compensation table; large id lists are looked up in chunks of at most 1,000. Request validation is framework-free in `employees.domain`. Frontend: reads the API address from `AppConfigService`; search debounced 300 ms; loading, empty and error states; nothing computed in the browser.

**Never:** No create, edit or delete (Epic 2), no salary revision (Epic 3), no detail page (Story 1.7), no JPA entity leaving `persistence`, no weakening of ArchUnit rules.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Default list | no parameters | page 0, size 20, 10,000 total, 500 pages, sorted by Employee ID | N/A |
| Search | `q=priya nair` | only people matching both tokens | N/A |
| Wildcard | `q=%` | matches only text containing a literal percent sign (none) | N/A |
| Filters | country=INDIA, department, status=ACTIVE | only matching people | invalid value gives 400 `INVALID_FILTER` |
| Sort by salary | `sort=salary,desc` | highest USD equivalent first across all 10,000, ties by Employee ID | N/A |
| Oversized page | `size=101` | 400 `PAGE_SIZE_TOO_LARGE` | N/A |
| Bad sort or page | unknown field, negative page | 400 `INVALID_SORT` or `INVALID_PAGE` | N/A |
| No token | any call | 401 Problem Details | N/A |
| Empty result | filters match nobody | empty items, totals 0 | N/A |
| UI states | slow, failing and empty responses | loading indicator, error message with retry, empty message | N/A |

</frozen-after-approval>

## Code Map

- `employees/{api,application,domain,persistence,web}` -- packages exist; `EmployeeEntity` already maps the table; add repository, specification, service, controller
- `compensation/api/ExchangeRateQuery` -- model for the new salary query api; `compensation/persistence/CompensationRecordEntity` exists
- `shared/domain/{Money,Rounding,ExchangeRates,Country}`, `shared/domain/DomainException` and `ErrorKind` -- reuse; `ApiExceptionHandler` maps them to Problem Details
- `seed/application/EmployeeSeedRunner` -- seeds 10,000 employees for integration tests
- `frontend/src/app/features/employees/` -- new; replaces the placeholder route component; `core/config/AppConfigService`, `core/auth` -- reuse

## Tasks & Acceptance

**Execution:**
- [ ] Backend unit tests first for `DirectoryQuery` parsing and validation, the search-token and wildcard escaping, then `EmployeeDirectoryIT` over HTTP on seeded data -- test-first
- [ ] `compensation.api` salary queries and their application service and repository -- no cross-module table reads
- [ ] `employees` repository with specification, directory service, controller and filters endpoint -- the API
- [ ] Frontend specs first, then service, table component, filters, states, route wiring -- the screen

**Acceptance Criteria:**
- Given the seeded data, when the matrix scenarios run over HTTP, then all pass and warm responses stay under 500 ms.
- Given the Angular tests and build, then they pass.
- Given ArchUnit, then all rules pass unchanged.

## Implementation Notes
