---
title: ACME Salary Management
status: draft
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
- **Compensation Record** — one salary period for an Employee: annual gross base salary, original currency, effective start date, effective end date. An Employee has many; at most one is **Current** (no end date).
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
Each salary modification writes an Audit Entry with the authenticated user's ID, timestamp, previous value and new value, in the same transaction as the change.
**Consequences (testable):**
- If the audit write fails, the salary change is rolled back.
- An HR Manager can view the audit trail of an Employee and a global trail filtered by user and date; entries cannot be edited or deleted through the application.

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

Payroll processing and disbursement, tax calculations, attendance, leave management, performance management, bonuses, stock compensation, benefits administration, banking information, government identification data. Also: live exchange-rate integration, public sign-up, role-based permissions, statistical anomaly models, applying a Simulation to real salaries, and bulk import of Excel files `[NOTE FOR PM: reasonable v2 item since Excel is today's source]`.

The product stays focused on compensation management, analytics, auditability and basic compensation planning.

## 7. MVP Scope

**In scope:** FR-1 to FR-12 above.
**Out of scope for MVP:** everything in §6; audit of Employee profile edits (only salary and exchange-rate changes are audited); charts beyond what the demo questions need.

## 8. Success Metrics

- **SM-1:** All five demo questions answerable in the UI in under 3 clicks each, with results in under 500 ms. Validates FR-7, FR-8, FR-10, FR-11.
- **SM-2:** 100% of salary revisions have a matching Audit Entry (no history overwritten). Validates FR-5, FR-6.
- **SM-3:** 10,000 seeded Employees in the specified country counts; reports reconcile to the seed. Validates FR-12, FR-7.
- **SM-C1 (do not optimize):** number of flagged anomalies. More flags is not better; the rule must stay explainable to HR.

## 9. Open Questions

1. Which job titles and departments does the seed use, and how many? `[ASSUMPTION: about 8 departments, 25 job titles]`
2. Should an employee ever be on a currency other than their country's? `[ASSUMPTION: no]`

## 10. Assumptions Index

- §4.4 Analytics include only Employees with active status.
- §4.4/FR-9 One current exchange rate per currency; edits restate all USD Equivalents; rate edits are audited.
- §9 Seed uses about 8 departments and 25 job titles.
- §9 Currency always matches country.
- FR-5 Backdating before the Current record's start date is not allowed; future-dated revisions are allowed `[ASSUMPTION]` and "Current" means the record whose range covers today.
