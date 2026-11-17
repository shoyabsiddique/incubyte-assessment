---
status: final
stepsCompleted: [1, 2, 3, 4]
inputDocuments:
  - _bmad-output/planning-artifacts/prds/prd-incubyte-assessment-2026-11-17/prd.md
  - _bmad-output/planning-artifacts/architecture/architecture-incubyte-assessment-2026-11-17/ARCHITECTURE-SPINE.md
  - _bmad-output/planning-artifacts/architecture/architecture-incubyte-assessment-2026-11-17/DESIGN-NOTES.md
---

# ACME Salary Management - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for ACME Salary Management, decomposing the requirements from the PRD and Architecture requirements into implementable stories. No UX design contract exists for this project.

## Requirements Inventory

### Functional Requirements

FR1: An HR Manager can sign in with email and password and sign out; wrong credentials are rejected generically; every API call except sign-in requires authentication (401 otherwise); passwords are stored only as salted hashes.
FR2: An HR Manager can search Employees by name, email or Employee ID and filter by country, department, job title, employment type and status, with sorting and server-side pagination; combined filters return only matching Employees.
FR3: An HR Manager can open an Employee to see the profile, the Current Compensation Record with its USD Equivalent, and the salary history.
FR4: An HR Manager can add an Employee with profile fields and an initial Compensation Record, and edit profile fields; work email and Employee ID are unique; required fields are validated server-side; the Original Currency matches the Employee's country at creation.
FR5: An HR Manager can revise an Employee's salary with a new annual gross base salary and effective start date; the previous record is closed the day before the new start; the start date must be strictly after the latest record's start; salary must be positive.
FR6: Each salary revision, Exchange Rate edit and Employee profile edit writes an Audit Entry (user ID, entity type and ID, operation, previous and new values, timestamp) in the same transaction as the change; an HR Manager can view an Employee's audit trail and a global trail filtered by user, entity type and date; entries cannot be edited or deleted.
FR7: An HR Manager can see count, average, median, minimum and maximum salary in USD Equivalent grouped by country, department or job title and filtered by any of them; the median is computed on USD Equivalent values.
FR8: An HR Manager can see the top and bottom N paid Employees in each department.
FR9: An HR Manager can view and edit the Exchange Rate for each currency; edits are audited and restate all USD Equivalents.
FR10: An HR Manager can list Potential Anomalies (Employee, Peer Group, salary, Peer Group median, percentage deviation) filterable by country, department and direction; flagged only when strictly more than 20% from the Peer Group median and the group has at least 5 Employees; labelled "potential anomalies for HR review".
FR11: An HR Manager can simulate an increment percentage for the organization, a country or a department and see current annual payroll, additional projected expenditure and projected annual payroll in USD; a Simulation never writes anything.
FR12: A deterministic seed script creates demo HR Manager accounts, Exchange Rates and 10,000 Employees with the specified country counts (India 3,500; USA 2,500; UK 1,000; Germany 800; Singapore 700; UAE 600; Canada 500; Australia 400), realistic salaries, a few deliberate outliers and some multi-record salary histories.

### NonFunctional Requirements

NFR1: Performance: list, search and analytics respond in under 500 ms at 10,000 Employees on a warm deployed instance; the seed completes in under a minute.
NFR2: Correctness: monetary values use exact decimal types, never floating point.
NFR3: Quality: core logic (revision, audit, USD conversion, anomaly rule, simulator) is covered by fast, deterministic unit tests written test-first.
NFR4: Security: hashed passwords, authenticated API, server-side validation, no sensitive personal data stored.
NFR5: Operability: deployed and publicly reachable, with a demo video.

### Additional Requirements

- AR1 (greenfield scaffold, Epic 1 Story 1): Gradle project for Spring Boot 4.1.x on Java 21 with `spring-boot-starter-flyway`; Angular 22 workspace (Node 24, TypeScript 6.0) with Angular Material; repo layout `backend/`, `frontend/`, `e2e/`, `docs/`, `.github/workflows/`.
- AR2: Modular monolith with package-by-feature `com.acme.salary.<module>.{api,web,application,domain,persistence}`; modules employees, compensation, analytics, simulator, auth, audit, shared; acyclic dependency graph enforced by ArchUnit (AD-1, AD-2).
- AR3: Domain packages are framework-free and use an injected `Clock`; one `shared.Money` type with one conversion function; rate snapshot per request; DECIMAL(19,4) amounts, DECIMAL(19,8) rates; HALF_UP 2dp presentation rounding (AD-1, AD-3).
- AR4: Salary history is append-and-close with `effective_end` null for the open record, unique `(employee_id, effective_start)`, CHECK end >= start, and `@Version` on `compensation_record` so concurrent revisions return 409 (AD-4).
- AR5: Explicit transactional audit via `AuditTrail.record` (`Propagation.MANDATORY`), one entry per operation, field allowlists, `subjectEmployeeId`, JSON payloads; ArchUnit rule that writing services depend on `AuditTrail`; integration tests prove audit-failure rollback (AD-5).
- AR6: Analytics, anomalies and simulator share one projection over the `v_active_compensation` view filtered by `Clock` date; domain-layer calculations; in-memory cache is the first sanctioned optimisation (AD-6).
- AR7: Exchange Rates owned by `compensation`, optimistic locking, seeded via Flyway reference data (AD-7, AD-11).
- AR8: Spring Security with BCrypt and stateless 1-hour JWT, no refresh; 401/403 as Problem Details; user ID from principal only (AD-8).
- AR9: API contract: `/api/v1`, money `{amount, currency}`, percent as plain number, page envelope, whitelisted sorts, RFC 7807 with `code`, route ownership per module, server-generated Employee ID `EMP-000001`, `@Version` on Employees and Exchange Rates (AD-9).
- AR10: Frontend: Angular standalone components, lazy feature routes, Material, RxJS services, debounced search, auth interceptor (skipping login), route guard, JWT in memory only, API base URL from deployed `config.json` (AD-10).
- AR11: Flyway is the only schema path (`ddl-auto=validate`); seed runner is a separate idempotent `seed` profile run from a developer machine; env-var config, `.env.example`, JWT key of at least 256 bits required in prod, CORS limited to the frontend origin; Hikari pool max 5; TLS to DB where supported (AD-11).
- AR12: CI on every push: backend build, domain unit tests, Testcontainers integration tests (same engine as Hostinger MariaDB/MySQL, no H2), frontend tests and production build; Playwright smoke (login, employee search, simulation) before deploy; deploy `main` only when green; API on Render (Docker), SPA on static hosting, DB on Hostinger (AD-12, AD-13).
- AR13: Observability: Actuator `/actuator/health` and `/actuator/info`, structured JSON logs, correlation ID on every request and log line.
- AR14: README with local setup and deployment instructions; `.env.example`; tests committed before implementation in small commits; first milestone is a working end-to-end app before deployment polish.

### UX Design Requirements

None: no UX design contract exists. UI behaviour follows the PRD and AR10.

### FR Coverage Map

FR1: Epic 1 - Sign in/out, authenticated API
FR2: Epic 1 - Search, filter, sort and page 10,000 Employees
FR3: Epic 1 - View an Employee with Current salary, USD Equivalent and history
FR4: Epic 2 - Add and edit Employees
FR5: Epic 3 - Revise a salary with history preserved
FR6: Epic 2 - Audit infrastructure and trails (extended to salary and rate changes in Epic 3)
FR7: Epic 4 - Salary statistics by country, department, job title
FR8: Epic 4 - Highest and lowest paid per department
FR9: Epic 3 - View and edit Exchange Rates
FR10: Epic 5 - Potential anomaly review
FR11: Epic 6 - Increment simulator
FR12: Epic 1 - Deterministic 10,000-Employee seed
NFR1: Epics 1, 4 and 7 - measured against the 500 ms target
NFR2-NFR4: Epic 1 onward - enforced by shared Money, test-first stories and security stories
NFR5: Epic 7 - Public deployment and demo video

## Epic List

### Epic 1: Sign in and browse the workforce (walking skeleton)
The HR Manager signs in and searches, filters and opens any of 10,000 seeded Employees, seeing salaries in local currency and USD. Includes project scaffolding, CI and the seed.
**FRs covered:** FR1, FR2, FR3, FR12

### Epic 2: Manage employee records with an audit trail
The HR Manager adds and edits Employees and can see exactly who changed what, per Employee and globally.
**FRs covered:** FR4, FR6

### Epic 3: Revise salaries and manage exchange rates
The HR Manager revises salaries without losing history, edits exchange rates, and every change is audited atomically.
**FRs covered:** FR5, FR9 (audit parts of FR6)

### Epic 4: Understand how ACME pays
The HR Manager sees pay statistics by country, department and job title in USD, and the highest- and lowest-paid in each department.
**FRs covered:** FR7, FR8

### Epic 5: Review potential pay anomalies
The HR Manager sees Employees paid more than 20% from their peer group median, for review.
**FRs covered:** FR10

### Epic 6: Plan raises with a simulator
The HR Manager projects the payroll impact of an increment for the organization, a country or a department, without changing any data.
**FRs covered:** FR11

### Epic 7: Public deployment and demo
The app is live for reviewers: automated deploy from `main`, a README with setup and deployment steps, and a demo video.
**FRs covered:** NFR5 (operability), AR12, AR14

## Epic 1: Sign in and browse the workforce (walking skeleton)

The HR Manager signs in and searches, filters and opens any of 10,000 seeded Employees, seeing salaries in local currency and USD. Every story is written test-first: failing tests are committed before the implementation.

### Story 1.1: Backend skeleton

As a developer,
I want a working, tested backend skeleton with the module structure enforced,
So that every later story starts from a consistent base.

**Requirements:** AR1, AR2, AR13, NFR3

**Acceptance Criteria:**

**Given** a fresh clone
**When** the backend build runs
**Then** a Spring Boot 4.1 / Java 21 Gradle project with the module packages (`employees`, `compensation`, `analytics`, `simulator`, `auth`, `audit`, `shared`, each with `api/web/application/domain/persistence`) builds and its tests pass
**And** an ArchUnit test fails if a `domain` package imports Spring, JPA or Jackson, if a module imports another module's non-`api` package, or if a dependency cycle exists

**Given** the running API
**When** `/actuator/health` and `/actuator/info` are requested
**Then** both respond without authentication
**And** every response carries a correlation ID header and every log line is structured JSON containing that ID

**Given** a Testcontainers base class using the same engine as Hostinger (MariaDB/MySQL)
**When** a Flyway baseline migration is applied in an integration test
**Then** the migration succeeds and `ddl-auto=validate` passes

### Story 1.2: Frontend skeleton and CI pipeline

As a developer,
I want a working Angular skeleton and automated checks on every push,
So that every later change is tested before it can break the product.

**Requirements:** AR1, AR10, AR12, AR14

**Acceptance Criteria:**

**Given** an Angular 22 workspace with Material and lazy-loaded feature routes
**When** `ng test` and `ng build` run
**Then** both pass for a placeholder shell component

**Given** a push to any branch
**When** GitHub Actions runs
**Then** it executes backend unit and integration tests and frontend tests and build, and fails the run if any fail

**Given** the repository
**When** inspected
**Then** `.env.example` documents all configuration, `.gitignore` excludes secrets and build output, and the layout has `backend/`, `frontend/`, `e2e/` and `docs/`

### Story 1.3: Money and exchange-rate conversion

As an HR Manager,
I want salaries from different countries converted to USD consistently,
So that I can compare pay across countries.

**Requirements:** FR3, NFR2, AR3

**Acceptance Criteria:**

**Given** `shared.Money` with amount and ISO currency
**When** arithmetic is performed
**Then** `BigDecimal` is used throughout with `MathContext.DECIMAL128` and no floating-point type appears in any money path
**And** presentation rounding is HALF_UP to 2 decimal places only, via one `Rounding` helper

**Given** an Exchange Rate snapshot with a rate greater than 0 for each supported currency (USD fixed at 1)
**When** a Money value is converted to USD
**Then** the result equals amount × rate, unrounded internally
**And** a missing rate or a non-positive rate fails loudly instead of being skipped

**Given** the supported countries and currencies (India INR, USA USD, UK GBP, Germany EUR, Singapore SGD, UAE AED, Canada CAD, Australia AUD)
**When** the `Country` enum is queried
**Then** each country returns its currency from this single source

**Given** a Flyway reference-data migration
**When** it runs
**Then** the `exchange_rate` table (`DECIMAL(19,8)`, `@Version`) contains one initial rate per supported currency

### Story 1.4: Sign in and sign out

As an HR Manager,
I want to sign in with my email and password,
So that only authorised people can see salary data.

**Requirements:** FR1, NFR4, AR8

**Acceptance Criteria:**

**Given** an `app_user` migration and a seeded demo HR account with a BCrypt-hashed password
**When** valid credentials are posted to `POST /api/v1/auth/login`
**Then** a JWT valid for about 1 hour is returned
**And** the stored password is only a salted hash

**Given** wrong credentials, unknown email or malformed input
**When** login is attempted
**Then** the response is a Problem Details 401 with one generic message that does not reveal which field was wrong

**Given** any other API endpoint
**When** called without a valid token
**Then** a Problem Details 401 is returned, produced by the shared error handler
**And** `/actuator/health` and `/actuator/info` stay public

**Given** the Angular app
**When** an HR Manager signs in
**Then** the token is held in memory only (never `localStorage`/`sessionStorage`), an interceptor attaches it, and a route guard protects every page
**And** a 401 on any call except login redirects to the sign-in page, and a reload requires signing in again

**Given** a signed-in user
**When** they choose sign out
**Then** the in-memory token is discarded and protected pages are no longer reachable

### Story 1.5: Seed 10,000 employees

As an HR Manager,
I want realistic demo data for all 10,000 employees,
So that I can explore the product with data that looks like ACME's.

**Requirements:** FR12, NFR1, AR11

**Acceptance Criteria:**

**Given** Flyway migrations for `employee` (UUID `CHAR(36)` key, server-generated `EMP-000001` ids, unique work email, `@Version`) and `compensation_record` (`DECIMAL(19,4)`, ISO currency, `effective_start`, nullable `effective_end`, unique `(employee_id, effective_start)`, CHECK end >= start, `@Version`)
**When** the migrations run
**Then** the schema validates against the entities and the constraints are enforced by integration tests

**Given** the `seed` Spring profile
**When** the seed runner executes against an empty database
**Then** exactly 10,000 Employees exist with India 3,500, USA 2,500, UK 1,000, Germany 800, Singapore 700, UAE 600, Canada 500, Australia 400, each with the currency of their country
**And** one Current Compensation Record exists per Employee, roughly 8 departments and 25 job titles are used, and salaries are realistic per country and job title

**Given** the same fixed seed value
**When** the seed runs twice on empty databases
**Then** the data is identical, and a second run on a populated database changes nothing (idempotent)

**Given** the generated data
**When** inspected
**Then** it includes a few deliberate salary outliers within peer groups of at least 5, some Employees with multiple Compensation Records, and a mix of employment statuses
**And** the seed completes in under a minute locally using batched inserts, creates the demo HR accounts, and writes no audit entries (documented baseline)

### Story 1.6: Browse and search employees

As an HR Manager,
I want to search and filter the employee directory,
So that I can find any employee quickly.

**Requirements:** FR2, NFR1, AR9

**Acceptance Criteria:**

**Given** `GET /api/v1/employees`
**When** called with `page`, `size` and a whitelisted `sort`
**Then** it returns `{items, page, size, totalElements, totalPages}` with a hard maximum page size, and never loads all 10,000 records into memory

**Given** a `q` search term and filters for country, department, job title, employment type and status
**When** applied together
**Then** only Employees matching the search (name, email or Employee ID) and all filters are returned
**And** sorting by salary uses USD equivalent

**Given** each list item
**When** returned
**Then** it includes the Current salary as `{amount, currency}` and its USD equivalent, obtained through `compensation.api` without cross-module table access

**Given** the Angular directory page
**When** the user types in search
**Then** requests are debounced, results update, and filters, sorting and paging work
**And** the page shows loading, empty and error states

**Given** the seeded data on a warm instance
**When** a page is requested
**Then** it responds in under 500 ms

### Story 1.7: View an employee

As an HR Manager,
I want to open an employee and see their profile and pay history,
So that I understand their current pay and how it got there.

**Requirements:** FR3, AR12

**Acceptance Criteria:**

**Given** `GET /api/v1/employees/{id}` for an existing Employee
**When** called
**Then** it returns the profile (Employee ID, name, work email, job title, department, country, city, employment type, hire date, status) and the Current Compensation Record with its USD equivalent

**Given** `GET /api/v1/employees/{id}/salary-history`
**When** called
**Then** every Compensation Record is returned newest first with amount, currency, effective start and effective end (null for the open record)

**Given** an unknown id
**When** requested
**Then** a Problem Details 404 is returned

**Given** the Angular employee page reached from the directory
**When** opened
**Then** it shows the profile, the Current salary in local currency and USD, and the salary history, with loading and error states
**And** a Playwright project in `e2e/` is set up, runs in CI, and its smoke test covers sign-in, searching for an employee and opening their page

## Epic 2: Manage employee records with an audit trail

The HR Manager adds and edits Employees and can see exactly who changed what, per Employee and globally. Audit is explicit and transactional (AD-5).

### Story 2.1: Audit trail foundation

As an HR Manager,
I want every important change recorded atomically with the change itself,
So that I can trust the history of who changed what.

**Requirements:** FR6, AR5

**Acceptance Criteria:**

**Given** an `audit_entry` Flyway migration (user id, entity type, entity id, subject employee id nullable, operation, `before` and `after` JSON, timestamp)
**When** the migration runs
**Then** the schema validates and no update or delete path exists for audit entries in code or API

**Given** `AuditTrail.record(AuditEvent)` in `audit.api`
**When** called inside a transaction
**Then** one entry is stored with entity type in {EMPLOYEE, COMPENSATION_RECORD, EXCHANGE_RATE}, operation in {CREATE, UPDATE, REVISE}, the timestamp from the injected `Clock`, and `before`/`after` containing only the allowlisted fields of that entity
**And** money values appear as `{amount, currency}` decimal strings

**Given** `record` is called with no active transaction
**When** executed
**Then** it fails (`Propagation.MANDATORY`) rather than opening its own transaction

**Given** an integration test with a business write followed by an audit write forced to fail
**When** the transaction completes
**Then** the business write is rolled back and no partial data remains
**And** an ArchUnit rule fails the build if an application service that writes through a repository does not depend on `AuditTrail`

### Story 2.2: Add an employee

As an HR Manager,
I want to add a new employee with their starting salary,
So that the directory stays complete as people join.

**Requirements:** FR4, FR6

**Acceptance Criteria:**

**Given** `POST /api/v1/employees` with profile fields (first name, last name, work email, job title, department, country, city, employment type, hire date, status) and an initial annual gross base salary with effective start date
**When** valid
**Then** an Employee with a server-generated `EMP-` id and an open Compensation Record are created in one transaction, orchestrated by `employees` through `compensation.api`
**And** exactly one audit entry (EMPLOYEE / CREATE, with the initial salary in `after`) is written in the same transaction

**Given** a work email that already exists (compared case-insensitively)
**When** submitted
**Then** a Problem Details 409 with code `DUPLICATE_EMPLOYEE_EMAIL` is returned and nothing is stored or audited

**Given** missing or invalid fields, a non-positive salary, or a currency that does not match the country
**When** submitted
**Then** a Problem Details 400 with code `VALIDATION_FAILED` lists each field error
**And** department and job title are trimmed and whitespace-collapsed before storage

**Given** the Angular add-employee form
**When** submitted successfully
**Then** the user lands on the new employee's page
**And** validation and conflict errors are shown next to the relevant fields

### Story 2.3: Edit an employee profile

As an HR Manager,
I want to correct or update an employee's profile,
So that department, title, location and status stay accurate.

**Requirements:** FR4, FR6

**Acceptance Criteria:**

**Given** `PUT /api/v1/employees/{id}` with changed profile fields
**When** valid
**Then** the Employee is updated and one audit entry (EMPLOYEE / UPDATE) records only the changed allowlisted fields with previous and new values, in the same transaction

**Given** an edit that changes nothing
**When** submitted
**Then** no audit entry is written

**Given** a stale `version` in the request
**When** submitted
**Then** a Problem Details 409 with code `CONCURRENT_MODIFICATION` is returned and nothing changes

**Given** an edit of the work email to one already used
**When** submitted
**Then** a 409 `DUPLICATE_EMPLOYEE_EMAIL` is returned

**Given** an edit of the country
**When** saved
**Then** the Employee's salary currency is unchanged and the change is audited

**Given** the Angular edit form on the employee page
**When** saved
**Then** the page shows the updated profile, and a conflict message offers to reload the latest data

### Story 2.4: View audit history

As an HR Manager,
I want to see the audit trail for an employee and across the organisation,
So that I can answer who changed what and when.

**Requirements:** FR6

**Acceptance Criteria:**

**Given** `GET /api/v1/employees/{id}/audit`
**When** called
**Then** it returns a paginated list, newest first, of every entry whose subject is that Employee, showing user, timestamp, operation, entity type and the before/after values

**Given** `GET /api/v1/audit` with optional `userId`, `entityType`, `from` and `to` filters
**When** applied together
**Then** only matching entries are returned, paginated with the standard page envelope

**Given** the audit API
**When** inspected
**Then** it exposes no create, update or delete endpoint for entries

**Given** the Angular Audit History page and the audit tab on the employee page
**When** opened
**Then** entries display with readable user, timestamp and a changed-fields diff, filters work, and loading, empty and error states are shown

## Epic 3: Revise salaries and manage exchange rates

The HR Manager revises salaries without losing history, edits exchange rates, and every change is audited atomically (AD-3, AD-4, AD-5, AD-7).

### Story 3.1: Revise a salary

As an HR Manager,
I want to give an employee a new salary from a chosen date,
So that pay changes are recorded without losing the previous history.

**Requirements:** FR5, FR6, AR4

**Acceptance Criteria:**

**Given** the domain operation `revise(latestRecord, newAmount, effectiveStart)` with an injected `Clock`
**When** `effectiveStart` is strictly after the latest record's start and the amount is positive
**Then** the latest record is closed with `effective_end = effectiveStart − 1 day` and a new open record (null end) is created in the same currency as the latest record
**And** unit tests, written first and needing no database, cover equal start (rejected), earlier start (rejected), zero or negative amount (rejected), and a future-dated start

**Given** `POST /api/v1/employees/{id}/salary-revisions` with an amount and effective start
**When** valid
**Then** the previous and new records are saved and exactly one audit entry (COMPENSATION_RECORD / REVISE, subject = that Employee, before and after amounts as `{amount, currency}`) is written in the same transaction
**And** the new record is Current once its start date arrives (today for a present date)

**Given** an invalid revision (date not after the latest start, non-positive amount, unknown Employee)
**When** submitted
**Then** a Problem Details 400, 404 or 409 with a specific `code` such as `REVISION_DATE_INVALID` is returned and nothing changes or is audited

**Given** two revisions for the same Employee submitted at the same time
**When** both reach the database
**Then** exactly one succeeds and the other receives a 409 `REVISION_OVERLAP`, proven by an integration test on the real database engine, leaving no two open records

**Given** the audit write is forced to fail in an integration test
**When** a revision is submitted
**Then** the previous record is not closed and no new record exists

### Story 3.2: Revise a salary from the employee page

As an HR Manager,
I want to revise salary from the employee's page and see the history update,
So that I can complete a pay change in one place.

**Requirements:** FR5

**Acceptance Criteria:**

**Given** the employee page
**When** the user chooses to revise salary
**Then** a form asks for the new amount (currency shown, not editable) and effective date, and shows the current salary for reference

**Given** valid input
**When** submitted
**Then** the Current salary, USD equivalent and salary history refresh to show the closed and new records, and the audit tab shows the new entry

**Given** API validation errors or a 409 conflict
**When** returned
**Then** the form shows the specific message next to the field or a conflict banner, and keeps the user's input

**Given** the Playwright suite
**When** run
**Then** it covers revising a salary and seeing the history change

### Story 3.3: View and edit exchange rates

As an HR Manager,
I want to keep exchange rates up to date,
So that USD comparisons across countries stay accurate.

**Requirements:** FR9, FR6, AR7

**Acceptance Criteria:**

**Given** `GET /api/v1/exchange-rates`
**When** called
**Then** one rate per supported currency is returned with its version, USD shown as fixed at 1

**Given** `PUT /api/v1/exchange-rates/{currency}` with a rate and the current version
**When** the rate is greater than 0
**Then** it is saved and exactly one audit entry (EXCHANGE_RATE / UPDATE, no subject employee, previous and new rate) is written in the same transaction

**Given** a rate of zero, negative or non-numeric, an unknown currency, or an attempt to edit USD
**When** submitted
**Then** a Problem Details 400 is returned and nothing changes or is audited

**Given** a stale version
**When** submitted
**Then** a 409 `CONCURRENT_MODIFICATION` is returned

**Given** a rate edit
**When** employees are listed afterwards
**Then** their USD equivalents use the new rate, without any stored USD value being updated

**Given** the Angular Exchange Rates page
**When** a rate is edited and saved
**Then** the new value and a confirmation are shown, and validation and conflict errors are displayed

## Epic 4: Understand how ACME pays

The HR Manager sees pay statistics by country, department and job title in USD, and the highest- and lowest-paid in each department. All figures come from one shared read model (AD-6).

### Story 4.1: Salary statistics API

As an HR Manager,
I want pay statistics grouped by country, department or job title,
So that I can see how ACME pays without building pivot tables.

**Requirements:** FR7, AR6, NFR1

**Acceptance Criteria:**

**Given** a Flyway migration creating the read-only view `v_active_compensation`, owned by `analytics`
**When** the projection query runs with today's date from the injected `Clock`
**Then** it returns one row per active Employee (status ACTIVE only) with country, department, job title, employee id, Current amount and currency, and uses no `CURDATE()`

**Given** the domain statistics calculator, with no framework imports
**When** given projection rows and one exchange-rate snapshot
**Then** it returns count, average, median, minimum and maximum in USD per group
**And** unit tests, written first, cover an odd-sized group, an even-sized group (median is the exact mean of the two middle values), a single-row group, an empty group, and mixed currencies

**Given** `GET /api/v1/analytics/salary-stats?groupBy=country|department|jobTitle` with optional `country`, `department` and `jobTitle` filters
**When** called
**Then** the response holds one entry per group with the statistics above, the median is computed on USD equivalents of all rows (not an average of medians), and amounts are `{amount, currency}` with HALF_UP 2 decimal places only at presentation
**And** when a country filter is set, local-currency statistics are included alongside USD

**Given** an invalid `groupBy` or unknown filter value
**When** called
**Then** a Problem Details 400 is returned

**Given** the 10,000 seeded Employees on a warm instance
**When** the endpoint is called
**Then** the response time is recorded by a performance test and noted in the design notes

### Story 4.2: Compensation dashboard

As an HR Manager,
I want a dashboard showing how ACME pays across countries, departments and job titles,
So that I can answer pay questions in seconds.

**Requirements:** FR7

**Acceptance Criteria:**

**Given** the Angular dashboard
**When** opened
**Then** it shows total pay and statistics per country in USD by default, answering "how much does ACME pay in each country"

**Given** the grouping selector and the country, department and job-title filters
**When** the user picks "job title" and filters to "Software Engineer"
**Then** the table shows average, median, minimum and maximum for Software Engineers per country, answering "average salary for Software Engineers across countries"

**Given** a single country filter
**When** applied
**Then** local-currency figures appear alongside USD

**Given** loading, empty and error conditions
**When** they occur
**Then** the dashboard shows clear states and never partial numbers
**And** a Playwright check covers opening the dashboard and applying a filter

### Story 4.3: Highest- and lowest-paid per department

As an HR Manager,
I want to see the highest- and lowest-paid employees in each department,
So that I can spot the extremes of our pay range.

**Requirements:** FR8

**Acceptance Criteria:**

**Given** `GET /api/v1/analytics/pay-extremes?department=&n=`
**When** called with a department and `n` (default 5, hard maximum)
**Then** the top `n` and bottom `n` active Employees by USD equivalent are returned with name, Employee ID, job title, country, local salary and USD equivalent
**And** ties are broken by Employee ID so results are deterministic, proven by a unit test written first

**Given** no department filter
**When** called
**Then** the result is grouped per department

**Given** a department with fewer than 2n Employees
**When** called
**Then** no Employee appears in both lists

**Given** the Angular page
**When** a department is chosen
**Then** the highest and lowest lists are shown side by side, each name links to the employee page, and loading, empty and error states are handled

### Story 4.4: Keep analytics fast

As an HR Manager,
I want analytics to respond quickly,
So that exploring pay data feels instant.

**Requirements:** NFR1, AR6

**Acceptance Criteria:**

**Given** the performance results recorded in Stories 4.1 and 4.3 against 10,000 seeded Employees on a warm instance
**When** analytics responses exceed 500 ms
**Then** an in-memory cache of the projection is added, invalidated on every Employee, Compensation Record and Exchange Rate write, and used for a single API instance
**And** if responses are already under 500 ms, this story is closed by recording the measurements in the design notes without adding a cache

**Given** the cache is added
**When** a salary is revised or an exchange rate is edited
**Then** the next analytics response reflects the change, proven by a test comparing cached and uncached results
**And** cached and uncached results are identical for the same data

## Epic 5: Review potential pay anomalies

The HR Manager sees Employees paid more than 20% from their peer group's median, framed as items for review rather than evidence of unfair pay (AD-6).

### Story 5.1: Potential anomaly detection API

As an HR Manager,
I want the system to flag salaries that sit far from comparable employees,
So that I can review them for fairness.

**Requirements:** FR10, AR6

**Acceptance Criteria:**

**Given** the domain anomaly detector, with no framework imports, working from the shared analytics projection and one exchange-rate snapshot
**When** it groups Employees by Peer Group (country + department + job title, trimmed and compared case-insensitively)
**Then** an Employee is flagged only when their USD-equivalent salary is strictly more than 20% above or strictly more than 20% below the group median
**And** unit tests, written first, cover exactly 20% (not flagged), just over 20% above and below (flagged), a group of 4 (no flags), a group of 5 (flags allowed), an even-sized group median, and a group mixing currencies after a country edit

**Given** inactive Employees (ON_LEAVE or TERMINATED)
**When** peer groups are built
**Then** they are excluded from both the group median and the flags

**Given** `GET /api/v1/analytics/anomalies` with optional `country`, `department` and `direction` (above or below) filters, `page`, `size` and a whitelisted `sort`
**When** called
**Then** each item contains the Employee, Peer Group, salary as `{amount, currency}` and USD equivalent, Peer Group median in USD, group size and signed percentage deviation, in the standard page envelope
**And** the response carries a label text identifying results as "potential anomalies for HR review"

**Given** the seeded data
**When** the endpoint is called with no filters
**Then** at least the deliberate seeded outliers are returned and no group smaller than 5 appears
**And** the response time is within the 500 ms target on a warm instance, or recorded for Story 4.4

### Story 5.2: Review anomalies screen

As an HR Manager,
I want a screen listing potential anomalies with the reasoning shown,
So that I can decide which cases deserve a closer look.

**Requirements:** FR10

**Acceptance Criteria:**

**Given** the Angular anomalies page
**When** opened
**Then** it shows the heading "Potential anomalies for HR review", a short explanation of the rule (more than 20% from the peer group median, groups of at least 5), and the list with each deviation as a signed percentage

**Given** the country, department and direction filters
**When** applied
**Then** the list updates, answering "are employees in the same role and location paid fairly" for the chosen scope

**Given** an item in the list
**When** clicked
**Then** the user opens that employee's page where salary history and audit trail are available

**Given** loading, empty (no flags) and error conditions
**When** they occur
**Then** each shows a clear state, and an empty result is worded as "no potential anomalies found" rather than a verdict of fairness

**Given** the Playwright suite
**When** run
**Then** it covers opening the anomalies page and following a flagged employee to their page

## Epic 6: Plan raises with a simulator

The HR Manager projects the payroll impact of a pay increment for the organisation, a country or a department, without changing any data (AD-1, AD-6).

### Story 6.1: Increment simulation API

As an HR Manager,
I want to see what a pay increment would cost,
So that I can plan raises with confidence.

**Requirements:** FR11, AR6

**Acceptance Criteria:**

**Given** the domain simulator, with no framework imports, working from the shared analytics projection (active Employees only) and one exchange-rate snapshot
**When** given a percentage and a scope (organisation, one country, or one department)
**Then** it returns current annual payroll in USD for the scope, additional projected expenditure = projected − current, and projected annual payroll = current × (1 + percentage / 100)
**And** unit tests, written first, cover 0%, 10% for the whole organisation, a country scope, a department scope, a scope with no Employees (all zeros), and unrounded internal arithmetic with HALF_UP 2 decimal places applied only to the presented values

**Given** `POST /api/v1/simulations` with `percent` as a plain number (10 means 10%), a `scope` of `ORGANIZATION`, `COUNTRY` or `DEPARTMENT`, and a `value` for the latter two
**When** valid
**Then** the response contains current payroll, additional expenditure, projected payroll (each `{amount, currency: "USD"}`), the number of Employees in scope, and the scope echoed back

**Given** a percentage outside 0 to 100 inclusive `[ASSUMPTION: increments only, capped at 100%]`, a missing `value` for a country or department scope, or an unknown country or department
**When** submitted
**Then** a Problem Details 400 with code `VALIDATION_FAILED` is returned

**Given** any valid or invalid simulation
**When** it runs
**Then** it executes in a read-only transaction, no Compensation Record, Employee or Exchange Rate changes, and no audit entry is written, proven by an integration test that compares row counts and contents before and after

### Story 6.2: Salary increment simulator screen

As an HR Manager,
I want to adjust a percentage and scope and instantly see the payroll impact,
So that I can compare raise scenarios in one sitting.

**Requirements:** FR11

**Acceptance Criteria:**

**Given** the Angular simulator page
**When** opened
**Then** it offers a percentage input, a scope selector (whole organisation, country or department) with a value picker where needed, and clearly states that the simulation is read-only and changes no salaries

**Given** a valid percentage and scope
**When** the user runs the simulation
**Then** current annual payroll, additional projected expenditure and projected annual payroll are shown in USD, along with the number of Employees in scope
**And** a 10% raise for the whole organisation answers "what happens to annual payroll if everyone gets a 10% raise"

**Given** a change to the percentage or scope
**When** applied
**Then** the results update and previous values are not left on screen as if current

**Given** validation errors, loading or server errors
**When** they occur
**Then** each is shown clearly without clearing the user's inputs

**Given** the Playwright suite
**When** run
**Then** it covers running a 10% organisation-wide simulation and checking that projected payroll equals current payroll × 1.10

## Epic 7: Public deployment and demo

The app is live for reviewers: the API and database are deployed, the frontend is published, `main` deploys automatically only when everything is green, and a README and demo video explain how to run and use it (AD-11, AD-12).

### Story 7.1: Deploy the API and database

As a reviewer,
I want the API running on a public URL against the hosted database,
So that I can use the product without installing anything.

**Requirements:** NFR1, NFR5, AR11, AR12

**Acceptance Criteria:**

**Given** a multi-stage `Dockerfile` for the API
**When** built in CI
**Then** it produces a runnable image that starts with the `prod` profile and reads all configuration from environment variables (database URL and credentials, JWT signing key, allowed frontend origin)

**Given** the `prod` profile with no JWT signing key, or one shorter than 256 bits
**When** the API starts
**Then** it refuses to start with a clear message

**Given** the Render service and the Hostinger MariaDB/MySQL database, with the Render outbound IP ranges allow-listed for remote access and TLS enabled where the host supports it
**When** the API starts
**Then** Flyway migrates the schema, the connection pool is capped at 5, and `/actuator/health` reports UP at the public URL

**Given** an empty production database
**When** the seed runner is executed once from a developer machine with its IP allowed
**Then** 10,000 Employees, exchange rates and the demo HR accounts exist, and running it again changes nothing
**And** signing in through the public API succeeds and the employee list returns data

**Given** the deployed instance
**When** analytics and list endpoints are measured warm
**Then** the results are recorded in the design notes against the 500 ms target, and Story 4.4's caching decision is applied if needed

### Story 7.2: Publish the frontend and gate deployment on green checks

As a reviewer,
I want the web app online and always matching tested code,
So that what I see is what passed the checks.

**Requirements:** AR10, AR12

**Acceptance Criteria:**

**Given** the Angular production build
**When** published to static hosting
**Then** deep links such as `/employees/…` load the app, and the API base URL is read from a deployed `config.json` rather than baked into the build

**Given** the API
**When** called from the published frontend origin
**Then** CORS allows only that configured origin, and calls from any other origin are rejected

**Given** a push to `main`
**When** the pipeline runs
**Then** it runs backend unit and integration tests (Testcontainers), frontend tests and build, and a Playwright smoke test (sign in, search for an employee, run a simulation) against the built app
**And** deployment of the API and frontend starts only if all of those pass, and any failure stops the deployment

**Given** a pull request or a branch other than `main`
**When** the pipeline runs
**Then** it runs the checks but does not deploy

### Story 7.3: README and design documentation

As a reviewer,
I want clear setup, run and deployment instructions,
So that I can run, evaluate and understand the project quickly.

**Requirements:** AR14

**Acceptance Criteria:**

**Given** the repository README
**When** read
**Then** it states what the product does, links to the PRD, architecture spine, design notes and epics, and lists the live URLs and demo login

**Given** a developer with Docker, Java 21 and Node 24
**When** following the README
**Then** they can start the database, run the API, run the seed and open the frontend locally, and run backend and frontend tests, using documented commands that work

**Given** the deployment section
**When** followed
**Then** it covers creating the Render service, allow-listing its IPs on Hostinger, setting every environment variable from `.env.example`, running the seed, and publishing the frontend, and it states the free-tier cold start behaviour

**Given** the design documentation
**When** reviewed
**Then** it includes the tradeoffs, the measured performance results, how AI tools were used (with the prompts or instructions kept in the repository), and the known limitations

### Story 7.4: Demo video and submission

As the candidate,
I want a short demo and a complete submission,
So that reviewers can see the product working and how it was built.

**Requirements:** NFR5, AR14

**Acceptance Criteria:**

**Given** the deployed app
**When** the demo is recorded
**Then** it shows sign-in, browsing and searching 10,000 employees, revising a salary and its audit entry, the five demo questions answered (pay per country, average Software Engineer salary by country, anomalies, highest- and lowest-paid per department, a 10% raise simulation), and editing an exchange rate

**Given** the finished video
**When** published
**Then** its link is in the README

**Given** the repository
**When** checked before submission
**Then** the commit history shows incremental, test-first commits, no secrets are committed, all artifacts are present, and the repository link is ready to send by replying to the original email
