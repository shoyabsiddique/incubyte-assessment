# Design notes

## Shape of the system

A modular monolith, `com.acme.salary.<module>.{api,web,application,domain,persistence}`, with the allowed module dependencies enforced by ArchUnit tests: employees use compensation and audit, compensation uses audit, analytics uses compensation, the simulator uses analytics, and everything may use the shared kernel. Domain code imports no framework. Money arithmetic keeps 34 significant digits and is rounded half-up to two decimals only when presented.

## Trade-offs

- **Audit is explicit, not an aspect.** Every business operation calls the audit trail inside its own transaction, which must already exist, so a failed audit write rolls the change back. An ArchUnit rule requires any service that writes to depend on the audit trail. This is more typing than an aspect, and the audit entry can say exactly what happened.
- **Salary history is rows, not overwrites.** A revision closes the open record and opens a new one; a unique key on "open" records and `@Version` make two simultaneous revisions end with one winner and a 409.
- **USD is computed, never stored.** Every USD figure is converted at read time from one exchange-rate snapshot per request, so editing a rate changes every figure without rewriting data.
- **Analytics reads one view.** `v_active_compensation` is the single projection behind statistics, extremes, anomalies and the simulator, so the numbers on different screens cannot disagree.
- **Anomalies are prompts, not verdicts.** More than 20% from the peer group median, groups of at least five, wording on the screen says "for HR review".
- **JWT held in memory.** A page reload signs the user out. That avoids storing a token where scripts can read it, at the cost of convenience.
- **No analytics cache.** Measured times are well under the 500 ms target, so no cache (and no invalidation bugs) was added.
- **One API instance.** Fine for the free tier and this scale; nothing here is shared across instances except the database.

## Measured performance

Measured by the integration tests on 10,000 seeded employees, a real MariaDB 11.4 in Testcontainers, warm, best of five, on a developer laptop. The test fails if any of these goes over 500 ms.

| Endpoint | Time |
| --- | --- |
| `GET /api/v1/analytics/salary-stats?groupBy=jobTitle` | about 150 ms |
| `GET /api/v1/analytics/pay-extremes` | about 120 ms |
| `GET /api/v1/analytics/anomalies` | about 80 ms |

Numbers for the deployed instance (Render free tier, Hostinger database) are to be recorded here after the first deployment; if they exceed 500 ms, add the invalidated in-memory projection cache described in Story 4.4.

## How AI tools were used

The project was built with Claude Code driven by the BMAD method: a PRD, an architecture spine and epics were written first, and each epic was then implemented story by story, tests first, with failing tests committed before the code. The skills and workflow instructions that guided it are in `.claude/skills` and `_bmad`; the planning and review artifacts it produced are in `_bmad-output`. Code was reviewed with independent reviewers for Epics 1 and 2 (the findings and what was done about each are in `_bmad-output/implementation-artifacts`); later epics were verified by the full test suites and the Playwright run rather than a separate review pass. The decisions that were the author's to make (stack, hosting, currency, audit approach, anomaly rule) were made by the author and recorded in the PRD and architecture.

## Known limitations

See [deferred-work.md](../_bmad-output/implementation-artifacts/deferred-work.md) for the full list. The ones a reviewer is most likely to notice:

- No login throttling, token revocation, refresh or password reset; sign-out is client-side.
- A mistaken far-future salary revision cannot be cancelled, only superseded by a later date.
- Terminated employees can still have their salary revised, and a rate edit applies immediately with no size check.
- The production bundle is slightly over the 500 kB initial budget (a warning, not an error).
- The end-to-end tests run against the development server, not the production build.
