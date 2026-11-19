# ACME Salary Management: requirements (one page)

## Goal

ACME's HR team keeps salary data for 10,000 employees in eight countries in Excel files. Give the **HR Manager** one web tool to keep that data correct and to answer "how do we pay people?" in seconds, instead of building pivot tables by hand.

Success means these five questions are answered on screen in seconds, with numbers the Manager can trust:

1. How much does ACME pay in each country?
2. What is the average Software Engineer salary across countries?
3. Whose pay looks out of line with comparable colleagues?
4. Who are the highest- and lowest-paid people in each department?
5. What would a 10% raise cost?

## Scope and features

- **Employees:** browse, search, filter and sort all 10,000; add and edit people; one page per employee.
- **Salary changes:** revise a salary from a date. History is kept, never overwritten, and a future-dated change shows as scheduled.
- **One currency to compare:** every salary is stored in its country's currency and shown in USD at the current exchange rates. The rates are editable, and a change applies everywhere at once.
- **Insights:** a dashboard of charts the Manager configures (any grouping field against headcount, salary or tenure), highest and lowest paid per department, pay anomalies, and a read-only raise simulator.
- **Audit trail:** every change records who, when, and what changed, saved in the same transaction as the change.
- **Also:** sign-in, a seed script for 10,000 employees, a public deployment, automated tests and a demo video.

## Deliberately left out, and why

| Left out | Reason |
| --- | --- |
| Multiple roles, permissions, single sign-on | One persona is asked for. Sign-in is a single HR account; the limits are documented. |
| Payroll runs, tax, benefits, bonuses, equity | The problem is salary data and answering questions about it. Annual base salary is the one pay figure. |
| Live exchange-rate feeds and rate history | A hand-edited current rate keeps every USD figure explainable. Reproducing past USD figures is a later step. |
| Importing the real Excel files | The seed stands in for data. Migrating real spreadsheets needs their layouts. |
| Deleting people or salary records | Records are closed, not deleted, so history and the audit trail stay complete. Leaving is a status. |
| Calling anyone underpaid or unfairly paid | Three fields (country, department, job title) cannot prove unfairness. Anomalies are flagged for human review, never as verdicts. |
| Approval workflows, notifications | Not needed to answer the five questions. |
| A mobile-first design | The Manager works at a desk. Phones work but are not optimised. |
| Login throttling, token refresh, password reset | Out of scope for a demo with one account. Listed as known limitations. |

## Key decisions

- **USD is computed, never stored.** One rate snapshot per request means figures on different screens cannot disagree.
- **Audit is part of the change.** If the audit entry cannot be written, the change is rolled back.
- **Anomaly rule:** more than 20% above or below the median of the same country, department and job title, in groups of at least five active people.
- **Performance target:** analytics responses within 500 ms on 10,000 employees (measured at about 80 to 350 ms locally).

## Assumptions

Demo data is synthetic and exchange rates are illustrative. Hosting uses a free tier, so the first request after idle can take up to a minute.

More detail: [full PRD](../_bmad-output/planning-artifacts/prds/prd-incubyte-assessment-2026-11-17/prd.md), [architecture](../_bmad-output/planning-artifacts/architecture/architecture-incubyte-assessment-2026-11-17/ARCHITECTURE-SPINE.md) and [design notes](design-notes.md).
