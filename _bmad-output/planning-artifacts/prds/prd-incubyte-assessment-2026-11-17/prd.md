---
title: ACME Salary Management
status: final
created: 2026-11-17
updated: 2026-11-17
---

# PRD: ACME Salary Management

## 1. Vision

ACME pays 10,000 employees across eight countries, and every salary lives in Excel. The HR Manager has no single source of truth, cannot compare pay across currencies, and answers each compensation question by hand-building a pivot table.

ACME Salary Management is a web application that becomes the system of record for compensation and turns it into answers. A month after adoption, the HR Manager manages every salary from one place, sees how ACME pays by country, department and job title, spots people paid far from their peers, and answers a compensation question in seconds.

**Priorities, in order:** (1) compensation visibility and analytics, (2) data consistency across countries and currencies, (3) potential pay anomalies, (4) efficient employee record management. Where scope competes, a reliable end-to-end product beats extra features.

## 2. Target User

**HR Manager** at ACME. Several HR Managers share the system; all have equal rights.

**Jobs to be done:** manage employee and salary records centrally; understand pay distribution; compare pay across countries in one currency; find pay that deserves review; know who changed what and when; plan raises.

**Questions the software must answer in seconds (demo set)**
1. How much does ACME pay in each country?
2. What is the average salary for Software Engineers across countries?
3. Are employees in the same role and location paid fairly?
4. Who are the highest- and lowest-paid employees in each department?
5. What happens to annual payroll if everyone gets a 10% raise?

## 3. Glossary

- **Employee** — a person's profile: Employee ID, first name, last name, work email, job title, department, country, city, employment type, hire date, employment status.
- **Compensation Record** — one salary period for an Employee: annual gross base salary, original currency, effective start date, effective end date. An Employee has many; the one whose date range covers today is **Current**, and at most one record is Current at a time.
- **Salary Revision** — creating a new Current Compensation Record and closing the previous one. Records are never overwritten.
- **Original Currency** — the currency a salary is stored in (the Employee's local currency).
- **Reporting Currency** — USD. **USD Equivalent** = salary × the Exchange Rate for its Original Currency.
- **Exchange Rate** — an admin-editable rate to USD per currency, seeded at install. No live feed.
- **Peer Group** — Employees sharing country, department and job title.
- **Potential Anomaly** — an Employee whose Current annual base salary is more than 20% above or below the median of their Peer Group (Peer Group of at least 5).
- **Audit Entry** — immutable log of one salary modification: user ID, timestamp, previous value, new value.
- **Simulation** — a read-only projection of payroll under a percentage increment.

## 4. Features

### 4.1 Authentication
**Description:** HR Managers sign in with email and password. There is no public registration; accounts are seeded. All signed-in users have the same rights.

#### FR-1: Sign in and out
An HR Manager can sign in with email and password and sign out.
**Consequences (testable):**
- Wrong credentials are rejected without revealing which field was wrong.
- Every API call except sign-in requires an authenticated session; unauthenticated calls return 401.
- Passwords are stored only as salted hashes.

### 4.2 Employee Management
**Description:** Search, view, add and update Employee profiles and their salaries from one interface over 10,000 records.

#### FR-2: Search and list Employees
An HR Manager can search by name, email or Employee ID and filter by country, department, job title, employment type and status, with sorting and pagination.
**Consequences (testable):**
- Lists are paginated server-side; a page of results returns without loading all 10,000 records.
- Combining filters returns only Employees matching all of them.

#### FR-3: View an Employee
An HR Manager can open an Employee to see the profile, the Current Compensation Record with its USD Equivalent, and the salary history.

#### FR-4: Add and edit an Employee
An HR Manager can add an Employee with profile fields and an initial Compensation Record, and edit profile fields.
**Consequences (testable):**
- Work email and Employee ID are unique; duplicates are rejected with a clear message.
- Required fields are validated server-side; the Original Currency must match the Employee's country.

### 4.3 Salary Revisions and Audit
**Description:** Salary changes are revisions, not overwrites. Every change is attributable.

#### FR-5: Revise a salary
An HR Manager can revise an Employee's salary by entering a new annual gross base salary and effective start date.
**Consequences (testable):**
- The previous Current Compensation Record gets an effective end date the day before the new start date; the new record becomes Current.
- Effective start date cannot precede the Current record's start date.
- Salary must be a positive amount.

#### FR-6: Audit Entries
Each salary revision, Exchange Rate edit and Employee profile edit writes an Audit Entry with the authenticated user's ID, entity type and ID, operation, previous values, new values and timestamp, in the same transaction as the change. Audit entries hold no more personal data than needed.
**Consequences (testable):**
- If the audit write fails, the salary change is rolled back.
- An HR Manager can view the audit trail of an Employee and a global trail filtered by user, entity type and date; entries cannot be edited or deleted through the application.

### 4.4 Compensation Analytics
**Description:** Distribution of Current salaries across countries, departments and job titles, all in USD Equivalent so countries are comparable. Local currency is shown alongside where a single country is selected. Analytics cover Employees with active employment status. `[ASSUMPTION: terminated or on-leave staff excluded]`

#### FR-7: Salary statistics
An HR Manager can see count, average, median, minimum and maximum salary grouped by country, department or job title, and filtered by any of them.
**Consequences (testable):**
- Median is computed on the USD Equivalent values, not the average of group medians.
- Answers the demo questions 1 and 2 without manual work.

#### FR-8: Highest- and lowest-paid
An HR Manager can see the top and bottom N paid Employees in each department.

#### FR-9: Exchange Rates
An HR Manager can view and edit the Exchange Rate for each currency. Edits take effect on subsequent reports and are recorded in the audit trail. `[ASSUMPTION: one current rate per currency, no rate history; changing a rate restates all USD Equivalents]`

### 4.5 Pay Anomalies
**Description:** A review aid, not evidence of unfair pay. Flags appear only where the data is sufficient.

#### FR-10: Potential Anomaly list
An HR Manager can list Potential Anomalies with the Employee, Peer Group, salary, Peer Group median and percentage deviation, filterable by country, department and direction (above/below).
**Consequences (testable):**
- An Employee is flagged only when strictly more than 20% above or below the median.
- No flags are generated for a Peer Group with fewer than 5 Employees.
- The UI labels results "potential anomalies for HR review".
- Demo question 3 is answered here.

### 4.6 Increment Simulator
**Description:** A read-only what-if for basic compensation planning.

#### FR-11: Simulate an increment
An HR Manager can enter an increment percentage and a scope (whole organization, one country, or one department) and see current annual payroll, additional projected expenditure and projected annual payroll in USD.
**Consequences (testable):**
- Projected = current × (1 + percentage); additional = projected − current.
- Running a Simulation never writes to any Compensation Record or Audit Entry.
- Demo question 5 is answered here.

### 4.7 Seed Data
#### FR-12: Seed script
A script creates demo HR Manager accounts, Exchange Rates and 10,000 Employees with Compensation Records in these counts: India (INR) 3,500; USA (USD) 2,500; UK (GBP) 1,000; Germany (EUR) 800; Singapore (SGD) 700; UAE (AED) 600; Canada (CAD) 500; Australia (AUD) 400.
**Consequences (testable):**
- Deterministic: the same seed value produces the same data.
- Salary ranges are realistic per country and job title, and include a few deliberate outliers so anomalies are demonstrable.
- Some Employees have multiple Compensation Records to show revision history.

## 5. Non-Functional Requirements

- **Performance:** list, search and analytics responses feel instant (target under 500 ms at 10,000 Employees on the deployed instance); the seed script completes in under a minute.
- **Correctness:** monetary values use exact decimal types, never floating point.
- **Quality:** core logic (revision, audit, USD conversion, anomaly rule, simulator) is covered by fast, deterministic unit tests, written test-first.
- **Security:** hashed passwords, authenticated API, server-side validation, no sensitive personal data stored.
- **Operability:** deployed and publicly reachable, with a demo video.

## 6. Non-Goals

Each item is left out deliberately, with the reason.

- **Payroll processing, disbursement, tax calculations, banking information:** a different product with regulatory weight; this one plans and analyzes pay, it does not pay people.
- **Attendance, leave, performance management, benefits administration:** other HR domains that dilute the compensation focus.
- **Bonuses and stock compensation:** v1 models annual gross base salary only, so comparisons stay clean and explainable.
- **Government identification data and sensitive personal data:** not needed for compensation questions; storing it adds risk for no value.
- **Live exchange-rate integration:** seeded, editable rates are predictable and testable, and avoid an external dependency.
- **Public sign-up and role-based permissions:** the user base is a small set of trusted HR Managers; accounts are seeded.
- **Statistical anomaly models (z-scores):** a plain 20%-from-median rule is something HR can explain and defend.
- **Applying a Simulation to real salaries:** keeps planning safe; real changes go through audited revisions.
- **Bulk Excel import:** `[NOTE FOR PM: reasonable v2 item since Excel is today's source]`; v1 ships with seeded data.

The product stays focused on compensation management, analytics, auditability and basic compensation planning.

## 7. MVP Scope

**In scope:** FR-1 to FR-12 above.
**Out of scope for MVP:** everything in §6; charts beyond what the demo questions need.

## 8. Success Metrics

- **SM-1:** All five demo questions answerable in the UI in under 3 clicks each, with results in under 500 ms. Validates FR-7, FR-8, FR-10, FR-11.
- **SM-2:** 100% of salary revisions have a matching Audit Entry (no history overwritten). Validates FR-5, FR-6.
- **SM-3:** 10,000 seeded Employees in the specified country counts; reports reconcile to the seed. Validates FR-12, FR-7.
- **SM-C1 (do not optimize):** number of flagged anomalies. More flags is not better; the rule must stay explainable to HR.

## 9. Open Questions

None blocking. Items below were resolved as assumptions and are open to revisit.

## 10. Assumptions Index

- §4.4 Analytics include only Employees with active status.
- FR-9 One current exchange rate per currency; edits restate all USD Equivalents; rate edits are audited.
- FR-12 Seed uses about 8 departments and 25 job titles.
- FR-4 An Employee's currency always matches their country.
- FR-5 Backdating before the Current record's start date is not allowed; future-dated revisions are allowed and take effect on their start date.
