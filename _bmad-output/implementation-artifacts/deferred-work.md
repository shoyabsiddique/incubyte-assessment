- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-backend-skeleton.md`
  summary: Add a test that the `prod` profile fails to start when DB_URL, DB_USER or DB_PASSWORD is missing.
  evidence: `application-prod.properties` relies on placeholders without defaults; a default added later would silently weaken fail-fast and no test would notice.
- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-backend-skeleton.md`
  summary: Pin the time zone (JDBC and JVM) to UTC when the first date or timestamp columns are created.
  evidence: ClockConfig is UTC but nothing aligns Hibernate or the MariaDB session; revision dates and audit timestamps could drift (settle in Story 1.5).
- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-backend-skeleton.md`
  summary: Document and provide a way to start local MariaDB (README plus a docker compose file) matching the `local` profile defaults.
  evidence: `application-local.properties` assumes MariaDB on localhost:3306; nothing in the repo starts it (Story 7.3).
