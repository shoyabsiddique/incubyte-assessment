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
