# ACME Salary Management

A salary management tool for the HR Manager of a 10,000-employee, multi-country organisation. It lets them browse and search employees, add and edit people, revise salaries without losing history, keep exchange rates current, see how ACME pays in USD, review potential pay anomalies, plan raises with a read-only simulator, and read a full audit trail of every change.

- **Backend:** Java 21, Spring Boot 4, Gradle, JPA, Flyway, MariaDB, JWT sign-in, Testcontainers.
- **Frontend:** Angular 22 with Angular Material, signals and RxJS.
- **Tests:** JUnit 5, Vitest (through `ng test`), Playwright end-to-end, ArchUnit architecture rules.

## Live demo

| | |
| --- | --- |
| App | _to be added after the first deployment_ |
| API health | _to be added_ `…/actuator/health` |
| Demo video | _to be added_ |
| Demo login | `priya.nair@acme.example` (password set by `DEMO_HR_PASSWORD` when the seed runs) |

The API runs on a free Render instance, which sleeps when idle: the first request after a quiet period can take 30 to 60 seconds while it wakes up.

## Documents

- [One-page requirements](docs/requirements.md)
- [Full product requirements (PRD)](_bmad-output/planning-artifacts/prds/prd-incubyte-assessment-2026-11-17/prd.md)
- [Architecture spine](_bmad-output/planning-artifacts/architecture/architecture-incubyte-assessment-2026-11-17/ARCHITECTURE-SPINE.md)
- [Epics and stories](_bmad-output/planning-artifacts/epics.md)
- [Design notes, measurements, AI use and limitations](docs/design-notes.md)
- [Analytics notes](_bmad-output/implementation-artifacts/epic-4-analytics-notes.md) and [deferred work](_bmad-output/implementation-artifacts/deferred-work.md)

## Run it locally

You need Docker, Java 21 and Node 24.

1. **Database** (port 3306; use another host port such as 13306 if it is taken, and adjust `DB_URL`):

   ```bash
   docker run -d --name salary-db -p 3306:3306 -e MARIADB_DATABASE=salary -e MARIADB_USER=salary -e MARIADB_PASSWORD=salary -e MARIADB_ROOT_PASSWORD=root mariadb:11.4
   ```

2. **API with the demo data** (the `seed` profile loads 10,000 employees, the exchange rates and the demo HR accounts once; running it again changes nothing):

   ```bash
   cd backend
   SPRING_PROFILES_ACTIVE=local,seed ./gradlew bootRun
   ```

   The `local` profile defaults to the database above. See [.env.example](.env.example) for every setting.

3. **Frontend:**

   ```bash
   cd frontend
   npm ci
   npm start
   ```

   Open http://localhost:4200 and sign in as `priya.nair@acme.example` with the local demo password `LocalDemo#2026`.

## Run the tests

```bash
cd backend && ./gradlew test            # fast tests, no Docker
cd backend && ./gradlew integrationTest # real MariaDB in Testcontainers, needs Docker
cd frontend && npx ng test --watch=false
cd frontend && npm run format:check
cd e2e && npm ci && npm test            # needs the database, API and frontend above running
```

Locally Playwright uses your installed Chrome; in CI it installs Chromium.

## Deploy

The API and database are hosted separately from the static frontend. Nothing deploys unless every check on `main` is green.

1. **Database (Hostinger MariaDB/MySQL).** Create the database and user. Enable Remote MySQL and add the outbound IP addresses of your Render service plus your own machine (for the one-off seed). Use TLS in the JDBC URL if the host supports it. Confirm the engine enforces `CHECK` constraints (MariaDB 10.2+ or MySQL 8.0.16+).
2. **API (Render).** Create a web service from [render.yaml](render.yaml) (Docker, free plan, auto-deploy off). Set `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` (at least 32 bytes; `openssl rand -base64 48`) and `CORS_ALLOWED_ORIGINS` (the exact frontend origin). The `prod` profile refuses to start if any is missing or the key is too short. The connection pool is capped at 5. Flyway creates the schema on first start and `/actuator/health` reports `UP`.
3. **Seed once** from your machine, with your IP allowed on Hostinger:

   ```bash
   cd backend
   SPRING_PROFILES_ACTIVE=prod,seed DB_URL=… DB_USER=… DB_PASSWORD=… JWT_SECRET=… CORS_ALLOWED_ORIGINS=… DEMO_HR_PASSWORD=… ./gradlew bootRun
   ```

   Stop it once it has logged that it seeded the data.
4. **Frontend (Netlify).** Create a site from this repository; [frontend/netlify.toml](frontend/netlify.toml) sets the build. Set `API_BASE_URL` to `https://<your-service>.onrender.com/api/v1`; the build writes it into `config.json`, so the address is not baked into the code. Deep links such as `/employees/…` work through the redirect rule.
5. **Pipeline.** Add the repository secrets `RENDER_DEPLOY_HOOK_URL` (Render deploy hook) and `NETLIFY_BUILD_HOOK_URL` (Netlify build hook). On a push to `main`, [the pipeline](.github/workflows/ci.yml) runs the backend, frontend and end-to-end checks and builds the API image, and only then calls the two hooks. Pull requests and other branches run the checks and never deploy.

## Project layout

```
backend/    Spring Boot API (modular monolith: employees, compensation, audit, analytics, simulator, auth)
frontend/   Angular app
e2e/        Playwright tests against the real stack
docs/       Design notes
_bmad-output/  PRD, architecture, epics, story specs and review notes
```
