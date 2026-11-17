---
title: 'Story 1.4: Sign in and sign out'
type: 'feature'
created: '2026-11-18'
status: 'in-progress'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '13f26db98f3249a5381737d9e338f876691d3374'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-incubyte-assessment-2026-11-17/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Anyone can call the API and open every page. HR Managers need to sign in with email and password so only authorised people see salary data, and every later endpoint needs a trustworthy user identity.

**Approach:** Add an `auth` module with an `app_user` table, BCrypt-hashed passwords, `POST /api/v1/auth/login` returning a one-hour stateless JWT (Spring Security's built-in JWT support, HS256) and `GET /api/v1/auth/me`. Every other endpoint requires the token except login and health/info; every auth failure is an RFC 7807 Problem Details response with a `code`. In Angular, add a real sign-in page, an in-memory token, an interceptor, a route guard and sign-out.

## Boundaries & Constraints

**Always:** Passwords stored only as BCrypt hashes. Token: HS256, 1 hour, subject = user UUID, plus email claim; the user ID used anywhere comes from the token principal only. The signing secret comes from configuration `app.security.jwt-secret`: at least 32 bytes or startup fails; `prod` has no default (environment variable `JWT_SECRET`), `local` and `test` get clearly labelled development secrets. Failed login (wrong password, unknown email, blank or missing fields) returns the same 401 Problem Details with code `INVALID_CREDENTIALS` and message "Invalid email or password"; unknown emails still perform a BCrypt comparison so timing does not reveal them; a body that is not valid JSON returns 400. Missing or invalid token returns 401 `UNAUTHENTICATED` and 403 `ACCESS_DENIED` in the same Problem Details shape from one shared handler. `/actuator/health` and `/actuator/info` stay public. CORS allows only origins listed in `app.cors.allowed-origins` (default `http://localhost:4200` for local). Frontend: token held in memory only (never web storage); API base URL read at startup from `public/config.json`; interceptor skips the login call when redirecting on 401; every route except `auth` and not-found is guarded; reload loses the session.

**Never:** No refresh tokens, roles, registration, password reset or remember-me; no token revocation endpoint (sign-out is client-side, the token expires on its own); no password hashes or demo credentials in migrations; no secrets in the repository; no business endpoints beyond `me`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Login ok | Valid email and password | 200 with `accessToken`, `tokenType` Bearer, `expiresIn` 3600 | N/A |
| Wrong password / unknown email / blank fields | Any of these | 401 `INVALID_CREDENTIALS`, identical body for all | No hint which part was wrong |
| Malformed body | Not JSON | 400 Problem Details `MALFORMED_REQUEST` | N/A |
| Protected call, no token | `GET /api/v1/auth/me` | 401 `UNAUTHENTICATED` Problem Details | N/A |
| Protected call, valid token | Bearer token | 200 with the user's id and email | N/A |
| Expired or tampered token | Bad signature or past expiry | 401 `UNAUTHENTICATED` | N/A |
| Health | `/actuator/health` without token | 200 | N/A |
| Weak secret | `jwt-secret` under 32 bytes or absent in prod | Application refuses to start with a clear message | Startup failure |
| UI sign-in | Valid credentials | Token kept in memory, navigates to dashboard, toolbar shows email and Sign out | Error text shown on failure, form stays |
| UI guard | Unauthenticated visit to `/employees` | Redirected to `/auth` | N/A |
| UI 401 | API answers 401 on a non-login call | Token discarded, redirected to `/auth` | N/A |
| UI sign out | Click Sign out | Token discarded, `/auth` shown, protected pages unreachable | N/A |

</frozen-after-approval>

## Code Map

- Backend packages exist as empty `auth/{api,web,application,domain,persistence}`; `shared/web/CorrelationIdFilter` and `ClockConfig` exist. ArchUnit rules apply (auth may use only `shared`; domain framework-free).
- `backend/src/main/resources/application*.properties` -- add `app.security.*` and `app.cors.*`
- `backend/src/test/java/com/acme/salary/AbstractMariaDbIntegrationTest.java` -- base for the HTTP integration tests (profile `test`)
- `frontend/src/app/app.ts`, `app.html`, `app.routes.ts`, `features/auth/auth.page.ts` -- shell, routes and the placeholder sign-in page to replace
- `frontend/src/app/app.spec.ts`, `app.routes.spec.ts` -- existing tests that assume an always-open app and must be adapted

## Tasks & Acceptance

**Execution:**
- [ ] Backend tests first: password and token domain/unit tests, then `AuthenticationIT` covering every matrix row over real HTTP and MariaDB -- test-first
- [ ] `backend/build.gradle`, `V3__app_user.sql`, `auth/*` -- security dependencies; `app_user` table (UUID `CHAR(36)`, unique case-insensitive email, BCrypt hash, display name); user entity and repository; login and me use cases; JWT issue and decode; security filter chain; Problem Details handler, entry point and access-denied handler in `shared/web`; CORS; `JwtSettings` validation -- backend feature
- [ ] Demo HR accounts -- a seeder in `auth` that runs under the `local` and `seed` profiles only, creates two accounts if absent with the password from `DEMO_HR_PASSWORD` (local default is a documented dev value, required in `seed`); hashes at runtime, nothing in migrations -- AD-11 puts accounts in the seed runner, not a migration
- [ ] Frontend tests first, then `core/config`, `core/auth` (service, interceptor, guard), sign-in page, shell changes (nav and Sign out only when signed in), guarded routes, `public/config.json`, adapted existing specs -- frontend feature
- [ ] `.env.example`, `frontend/README.md` -- document `JWT_SECRET`, `DEMO_HR_PASSWORD`, `app.cors.allowed-origins` and the config file

**Acceptance Criteria:**
- Given the matrix, when backend unit and integration tests run, then all rows pass with Docker running.
- Given the Angular tests and production build, when run, then all pass and the token never touches `localStorage` or `sessionStorage` (asserted by a test).
- Given the ArchUnit suite, when run, then it still passes unchanged.
- Given the running pair of apps, when a demo account signs in, then pages load, and after sign out or reload the user must sign in again.

## Implementation Notes

Deviation from the epic wording: accounts are created by a profile-gated seeder instead of the migration so no credential ever sits in version control (AD-11). The Story 1.5 seed runner will reuse this seeder for the `seed` profile.
