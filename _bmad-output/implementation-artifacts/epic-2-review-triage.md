# Epic 2 review triage (stories 2.1 to 2.4)

Two independent reviewers (backend, frontend and e2e) read the Epic 2 diff. Verification: backend fast and integration suites, 159 frontend tests, and Playwright against the real stack (sign-in smoke, add then edit then audit trail, duplicate email) all pass.

## Patched
- Dates outside 1900 to 2100 (for example `+99999-01-01`) are rejected with `VALIDATION_FAILED`; a database integrity failure is reported as a duplicate email only when the work email key was violated.
- `page` in the audit API is capped, so a huge page number gives 400 `INVALID_PAGE` instead of an overflow 500.
- A token without a valid subject or email claim gives 401 instead of 500.
- Edit from the audit tab now switches to the Details tab so the form is visible.
- A server error on one field no longer blocks submit after the user edits a different field.
- The audit diff lists only changed fields and no longer rewrites upper-case free text (DEVOPS).

## Deferred
- AD-5 ArchUnit rule: match all repository, EntityManager and JdbcTemplate writes, check per method, scan every application package, and verify callers of `@AuditedByCaller` classes audit.
- Concurrency integration tests (parallel creates, same-email race, two stale edits) and mapping lock timeouts to 409.
- Employee ID allocation scans the table under the lock; have the seed set `last_value` and decide the width limit.
- `/employees/{id}/audit` returns an empty page for an unknown employee (route lives in `audit`); revisit with AD-9.
- Audit date filters are UTC days while the table shows local time; label them UTC or send instants.
- "Changed by" offers only users seen so far; add a users endpoint.
- Table and paginator are unmounted while loading (focus loss); keep them mounted.
- Move focus to the first invalid field or banner after a failed submit.
- `AuditHistoryComponent` does not react to a changed `employeeId` input.
- Truncate audit timestamps to microseconds; entity create goes through `merge`.
- E2E: duplicate-email test depends on a seeded employee, created employees are not cleaned up, and there is no conflict test.
