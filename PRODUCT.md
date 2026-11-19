# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users
One HR Manager at ACME, a 10,000-employee organisation employing people in eight countries (India, USA, UK, Germany, Singapore, UAE, Canada, Australia). They work at a desk on a laptop or desktop, answering pay questions for leadership and keeping salary data correct. Phones are a courtesy (the app must work and not break, not be optimised). The product is also evaluated by assessment reviewers who click through it, so its first minutes must be self-explanatory.

## Product Purpose
Lets the HR Manager browse and search every employee, add and edit people, revise salaries without losing history, keep exchange rates current, see how ACME pays by country, department and job title in USD, find the highest and lowest paid in each department, review potential pay anomalies, plan raises with a read-only simulator, and read an audit trail of every change. Success is answering a pay question in seconds and being able to trust the answer.

## Positioning
Trustworthy pay data: every change is audited in the same transaction, salary history is never overwritten, every USD figure is converted at one exchange-rate snapshot at read time, and anomalies are framed as prompts for human review, never as findings of unfair pay.

## Operating Context
Single signed-in HR Manager account (JWT held in memory, so a reload signs out). Data is a seeded demo workforce of 10,000 employees with illustrative exchange rates. Deployed as a static Angular app plus a Spring Boot API and a MariaDB database; free-tier hosting means the API can take 30 to 60 seconds to wake up.

## Capabilities and Constraints
- Employees: search, filter, sort, page; add, edit with optimistic locking; salary history with Current marker.
- Compensation: revise a salary from a date, future-dated revisions shown as Scheduled; exchange rates edited one at a time, USD fixed at 1.
- Analytics (active employees only, USD): statistics, extremes, anomalies (more than 20% from the peer-group median, groups of at least 5), simulator.
- Audit history: who changed what and when, with changed-field diffs.
- Terms: Peer Group is country, department and job title; Current salary is the record covering today.
- Undecided: login throttling, token refresh, cancelling a scheduled revision, role-based access.

## Brand Commitments
Product name "ACME Salary Management". No logo, brand colours or voice guide exist; none were requested.

## Evidence on Hand
Seeded demo data only: no real customers, testimonials or benchmarks exist. Measured API response times (80 to 150 ms locally) are in docs/design-notes.md; deployed numbers are not yet recorded. Do not invent any proof content.

## Product Principles
- Trust before decoration: numbers, units and dates are exact, consistent and never ambiguous.
- Frame, do not accuse: anomalies and extremes are items for review, worded neutrally.
- Show what changed: every edit is visible afterwards, in history and in the audit trail.
- Read-only means read-only: the simulator and analytics never change data and say so.
- Familiar beats clever: an operational tool should behave the way its users expect from tools of its kind.

## Accessibility & Inclusion
Target WCAG 2.2 AA: keyboard operable, visible focus, 4.5:1 text contrast, and meaning never carried by colour alone (for example anomaly deviations carry a sign as well as a colour).
