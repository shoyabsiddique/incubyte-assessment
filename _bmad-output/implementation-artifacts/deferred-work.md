- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-backend-skeleton.md`
  summary: Add a test that the `prod` profile fails to start when DB_URL, DB_USER or DB_PASSWORD is missing.
  evidence: `application-prod.properties` relies on placeholders without defaults; a default added later would silently weaken fail-fast and no test would notice.
- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-backend-skeleton.md`
  summary: Pin the time zone (JDBC and JVM) to UTC when the first date or timestamp columns are created.
  evidence: ClockConfig is UTC but nothing aligns Hibernate or the MariaDB session; revision dates and audit timestamps could drift (settle in Story 1.5).
- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-backend-skeleton.md`
  summary: Document and provide a way to start local MariaDB (README plus a docker compose file) matching the `local` profile defaults.
  evidence: `application-local.properties` assumes MariaDB on localhost:3306; nothing in the repo starts it (Story 7.3).
- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-frontend-skeleton-and-ci.md`
  summary: Self-host the Roboto font instead of loading it from Google at runtime.
  evidence: `index.html` requests fonts from a third party on every load, a privacy concern for an HR tool and a failure point offline or behind restricted networks.
- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-frontend-skeleton-and-ci.md`
  summary: Make the sidenav responsive (collapse on narrow screens), add a skip-to-content link, and handle lazy-chunk load failures.
  evidence: The shell uses a permanent 240px side navigation and no navigation error handling; fine for desktop demos, a gap for phones and flaky networks (fold into the UI polish work in Epic 7).
- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-frontend-skeleton-and-ci.md`
  summary: Confirm the CI workflow with a first green run once a GitHub remote exists.
  evidence: The workflow was only validated structurally; action versions, Docker for Testcontainers and the Gradle wrapper permissions are unproven until it runs.
- source_spec: `_bmad-output/implementation-artifacts/spec-1-3-money-and-exchange-rates.md`
  summary: Confirm the Hostinger database engine and version enforces CHECK constraints (MariaDB 10.2 or later, MySQL 8.0.16 or later).
  evidence: The exchange_rate table relies on CHECK constraints for positive rates and USD equal to 1; older engines accept the constraint syntax but do not enforce it.
- source_spec: `_bmad-output/implementation-artifacts/spec-1-4-sign-in-and-sign-out.md`
  summary: Add login brute-force protection (throttling or lockout) and audit successful and failed sign-ins.
  evidence: The login endpoint accepts unlimited attempts and records none; relevant once the app is public (Epic 7), and sign-in auditing fits with the audit work in Epic 2.
- source_spec: `_bmad-output/implementation-artifacts/spec-1-4-sign-in-and-sign-out.md`
  summary: Decide token revocation and refresh, issuer/audience claims, secret rotation procedure, password change or reset, and how real HR accounts are created after the demo seed.
  evidence: Sign-out is client-side only and a stolen token stays valid for its full hour; the PRD scopes these out for v1, so document the limits in the README (Story 7.3).
- source_spec: `_bmad-output/implementation-artifacts/spec-1-4-sign-in-and-sign-out.md`
  summary: Sign-in form accessibility (error tied to fields, focus management, per-field messages) and warning before the one-hour session ends.
  evidence: The error is announced with role alert but not linked to inputs; a token expiring mid-form loses input.
- source_spec: `_bmad-output/implementation-artifacts/spec-1-4-sign-in-and-sign-out.md`
  summary: Ensure deployments never ship `config.json` pointing at localhost and serve it with short cache lifetime.
  evidence: The shipped `public/config.json` defaults to http://localhost:8080/api/v1, so a missed override sends production traffic to localhost (Story 7.2).

## From Story 1.7
- "Today" for the current-salary flag is the UTC date; decide whether it should follow the employee's country.
- Per-row currency or missing-rate failures fail the whole history response; degrade per row if more currencies are added.
- A future-dated open salary record is labelled "Present"; label it "Scheduled" once raises exist (Epic 3).
- The detail page makes two requests with separate rate snapshots; a combined endpoint would remove any skew.
- Format amounts by the currency's own decimals (zero-decimal currencies) and carry the employee name in the tab title.
- Trim the id route parameter; make integration test data independent of the shared container and the fixed seed.


## From Epic 3
- A mistaken far-future revision (for example dated 2099) can only be superseded by a later date; there is no cancel or undo for a scheduled revision. Consider a "cancel scheduled revision" action, or an upper bound such as one year ahead.
- Revising the salary of a terminated or on-leave employee is allowed; decide whether terminated employees should be blocked (compensation deliberately does not read employee status, AD-9).
- An exchange rate edit applies immediately to every USD figure with no sanity check; consider a warning when a rate moves by more than a set percentage, and an effective-dated rate history if past USD figures ever need to be reproducible.
- The revision audit entry records the new record's fields and the replaced record's amount and start; the closed record's new end date is implied by the new start and is not stored separately.
- The deterministic lost-race test (`SalaryRevisionIT`) waits three seconds for the second request to queue on the row lock because the test database user has no PROCESS privilege to ask the engine; make it event-driven if that privilege is ever granted to the test user.
- The routes spec's first lazy-load test can exceed the 5 second default on a cold start (seen once, passes on re-run); raise the timeout for that spec if it recurs.
