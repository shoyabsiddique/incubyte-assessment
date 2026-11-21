# ACME Salary Management

A salary management tool for the HR Manager of a 10,000-employee, multi-country organisation. It lets them browse and search employees, add and edit people, revise salaries without losing history, keep exchange rates current, see how ACME pays in USD, review potential pay anomalies, plan raises with a read-only simulator, and read a full audit trail of every change.

- **Backend:** Java 21, Spring Boot 4, Gradle, JPA, Flyway, MariaDB, JWT sign-in, Testcontainers.
- **Frontend:** Angular 22 with Angular Material, signals and RxJS.
- **Tests:** JUnit 5, Vitest (through `ng test`), Playwright end-to-end, ArchUnit architecture rules.

## Live demo

| | |
| --- | --- |
| App | _to be added_ (static site on Hostinger) |
| API | `https://incubyte-api.shoyabsiddique.site` (Google Cloud VM, HTTPS through Caddy) |
| API health | `https://incubyte-api.shoyabsiddique.site/actuator/health` |
| API documentation (Swagger) | `https://incubyte-api.shoyabsiddique.site/swagger-ui/index.html` |
| Demo video | _to be added_ |
| Demo login | `priya.nair@acme.example` (password set by `DEMO_HR_PASSWORD` when the seed runs) |

The API runs on an always-on Google Cloud `e2-micro` VM, so there is no cold start. The first request after a restart can still take about a minute while the application starts on its small instance.

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

## API documentation (Swagger)

Open `/swagger-ui/index.html` on the API (locally http://localhost:8080/swagger-ui/index.html). The page and the OpenAPI document at `/v3/api-docs` are public; the data endpoints are not. To try them: call `POST /api/v1/auth/login`, copy `accessToken` from the response, choose **Authorize** and paste it.

## Deploy

Three pieces, each hosted where it fits:

| Piece | Where | Address |
| --- | --- | --- |
| Database | Hostinger MariaDB 11.4 | `srv1743.hstgr.io:3306` |
| API | Google Cloud Compute Engine `e2-micro` (Always Free region such as `us-west1`) | `https://incubyte-api.shoyabsiddique.site` |
| Frontend | Hostinger static hosting (`public_html`) | `https://<your-domain>` |

1. **Database (Hostinger).** Create the database and user. Enable Remote MySQL and add the **static external IP of the VM** plus your own machine (for the one-off seed). Confirm the engine enforces `CHECK` constraints (MariaDB 10.2+ or MySQL 8.0.16+).
2. **VM (Google Cloud).** Create an `e2-micro` in `us-west1`, `us-central1` or `us-east1` with a 30 GB standard disk, allow HTTP and HTTPS, and reserve a static external IP. Add 2 GB of swap (the instance has 1 GB of RAM) and install Docker.
3. **Subdomain.** In the Hostinger DNS zone add an **A** record: name `api`, pointing at the VM's static IP. Remove any other `api` record first.
4. **API container.** On the VM, build the image from [backend/Dockerfile](backend/Dockerfile) and run it with a settings file that is never committed:

   ```bash
   git clone https://github.com/shoyabsiddique/incubyte-assessment.git
   cd incubyte-assessment/backend && sudo docker build -t salary-api .
   sudo docker run -d --name salary-api --restart unless-stopped --env-file ~/api.env -p 127.0.0.1:8080:8080 salary-api
   ```

   `~/api.env` holds `SPRING_PROFILES_ACTIVE=prod`, `DB_URL=jdbc:mariadb://srv1743.hstgr.io:3306/<database>`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` (at least 32 bytes; `openssl rand -base64 48`), `CORS_ALLOWED_ORIGINS` (the exact frontend origin, for example `https://<your-domain>`) and `JAVA_TOOL_OPTIONS=-Xmx384m -XX:+UseSerialGC`. The `prod` profile refuses to start if any required value is missing or the key is too short. The connection pool is capped at 5. Flyway creates the schema on first start.
5. **HTTPS.** Run Caddy next to the API so the subdomain gets a certificate automatically (browsers block an HTTPS page that calls an HTTP API):

   ```bash
   printf 'incubyte-api.shoyabsiddique.site {
  reverse_proxy localhost:8080
}
' > ~/Caddyfile
   sudo docker run -d --name caddy --restart unless-stopped --network host -v ~/Caddyfile:/etc/caddy/Caddyfile -v caddy_data:/data caddy:2
   ```

   Check `https://incubyte-api.shoyabsiddique.site/actuator/health`: it reports `UP`.
6. **Seed once** from your machine, with your IP allowed on Hostinger:

   ```bash
   cd backend
   SPRING_PROFILES_ACTIVE=prod,seed DB_URL=… DB_USER=… DB_PASSWORD=… JWT_SECRET=… CORS_ALLOWED_ORIGINS=… DEMO_HR_PASSWORD=… ./gradlew bootRun
   ```

   Stop it once it has logged that it seeded the data. `DEMO_HR_PASSWORD` is the demo login's password and must not start with `change-me`.
7. **Frontend (Hostinger).** Point the build at the API and upload it:

   ```bash
   cd frontend
   API_BASE_URL=https://incubyte-api.shoyabsiddique.site/api/v1 node scripts/write-config.mjs
   npx ng build
   ```

   Upload the contents of `frontend/dist/frontend/browser` into `public_html`, including the hidden `.htaccess`, which sends deep links such as `/employees/…` to the app. `config.json` can also be edited in place to change the API address without a rebuild. (`git checkout frontend/public/config.json` restores the local default afterwards.)
8. **Updating the API.** On the VM: `git pull`, rebuild the image, then `sudo docker rm -f salary-api` and run it again with the same command.

[frontend/netlify.toml](frontend/netlify.toml) and [render.yaml](render.yaml) are kept as an alternative (Netlify for the frontend, Render's free plan for the API). The `deploy` job in [the pipeline](.github/workflows/ci.yml) only calls Render and Netlify deploy hooks; with the Google Cloud setup above it logs a notice and does nothing. Checks (backend, frontend, end-to-end, image build) still run on every push.

## Project layout

```
backend/    Spring Boot API (modular monolith: employees, compensation, audit, analytics, simulator, auth)
frontend/   Angular app
e2e/        Playwright tests against the real stack
docs/       Design notes
_bmad-output/  PRD, architecture, epics, story specs and review notes
```
