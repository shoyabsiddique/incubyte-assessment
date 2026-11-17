---
title: 'Story 1.2: Frontend skeleton and CI pipeline'
type: 'feature'
created: '2026-11-18'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '11f2c5e5281d7d74da92358cd0186ec97bc72be0'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-incubyte-assessment-2026-11-17/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** There is no frontend and nothing runs the tests automatically. Later stories need an Angular base with the planned feature routes and a pipeline that fails any push that breaks the backend or frontend.

**Approach:** Create `frontend/` with Angular 22 (current stable 22.2.x, Node 24), Angular Material and the CLI's default test runner, a placeholder shell with lazy-loaded feature routes, and a GitHub Actions workflow running backend unit and Testcontainers integration tests plus frontend tests and a production build on every push. Add the `e2e/` and `docs/` folders. Work test-first and commit small steps.

## Boundaries & Constraints

**Always:** Standalone components, lazy-loaded routes per feature (dashboard, employees, compensation, anomalies, simulator, audit, exchange-rates, auth), Angular Material, TypeScript strict mode. Frontend files only under `frontend/`; workflow at `.github/workflows/ci.yml`. CI uses pinned major versions of actions, Java 21, Node 24, runs on every push and pull request, and fails if any job fails. The API base URL is not baked into the build (it will come from `config.json` in a later story). `.gitignore` excludes `node_modules`, `dist`, `.angular` and build output. Each directory in the root layout exists in git with a README line explaining its purpose.

**Never:** No API calls, authentication, real feature pages or business logic (later stories); no deployment steps (Epic 7); no Playwright project yet (Story 1.7 creates it in `e2e/`); no secrets in the workflow; no E2E in CI yet.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Unit tests | `ng test` run once | Shell component and route tests pass | Failing test fails the run |
| Build | `ng build` production | Succeeds and emits a deployable bundle | Type or template error fails the build |
| Lazy route | Navigating to `/employees` (and the other feature paths) | The feature's placeholder component loads lazily | Unknown path shows a not-found placeholder |
| CI green | Push where all checks pass | Backend unit, backend integration and frontend jobs all succeed | N/A |
| CI red | Push with a failing test in any job | The workflow run fails and names the failing job | Run marked failed |

</frozen-after-approval>

## Code Map

- Repo already has `backend/` (Story 1.1; `./gradlew test` for fast tests, `./gradlew integrationTest` needs Docker), root `.gitignore` and `.env.example`. Do not change backend behavior.
- `frontend/` -- new Angular workspace (scaffold with the Angular CLI, directory `frontend`)
- `frontend/src/app/app.routes.ts` -- lazy feature routes
- `frontend/src/app/shell/` -- placeholder shell with Material toolbar and side navigation
- `.github/workflows/ci.yml` -- pipeline
- `e2e/`, `docs/` -- layout placeholders with README

## Tasks & Acceptance

**Execution:**
- [x] `frontend/` -- scaffold Angular 22 workspace (standalone, strict, scss, routing) and add Angular Material with a theme -- foundation for all UI stories
- [x] `frontend/src/app/app.routes.spec.ts` and `shell/*.spec.ts` -- write failing tests first for the shell and for each lazy route resolving its placeholder -- test-first
- [x] `frontend/src/app/app.routes.ts`, `shell/`, `features/*` -- implement shell and eight lazy placeholder features plus not-found -- structure for later stories
- [x] `.github/workflows/ci.yml` -- jobs for backend unit tests, backend integration tests (Docker on the runner), and frontend test + production build; cache Gradle and npm; trigger on push and pull_request -- automation
- [x] `.gitignore`, `e2e/README.md`, `docs/README.md`, `frontend/README.md` -- ignore rules and layout notes -- layout requirement

**Acceptance Criteria:**
- Given a fresh clone, when `npm ci`, `ng test` (single run) and `ng build` run in `frontend/`, then all pass.
- Given the running app, when each feature path is opened, then its placeholder loads from a separate lazy chunk and an unknown path shows a not-found view.
- Given the workflow file, when parsed, then it has three jobs, pinned action major versions, Java 21 and Node 24, and no secrets; a deliberately failing test would fail its job (reasoned from the config, since no remote exists to run it on).
- Given the repository root, when listed, then `backend/`, `frontend/`, `e2e/`, `docs/` and `.github/workflows/` exist.

## Implementation Notes


## Spec Change Log

- Shell lives in `src/app/app.ts` and `app.html` (not a `shell/` folder) because the root component is the shell; the Code Map path was a planning guess.

## Review Triage Log

| Finding | Verdict | Route | Evidence |
|---|---|---|---|
| `strict` and `strictTemplates` missing from the generated tsconfig although the spec requires strict mode | high | patch (done) | Confirmed in `tsconfig.json`; both enabled, build and 32 tests still pass |
| Active navigation link not asserted; no accessible current-page indication | medium | patch (done) | Links now set `aria-current="page"`; test navigates to `/employees` and expects only that link marked |
| Browser tab title is the scaffold default; no per-route titles | medium | patch (done) | `index.html` title fixed; every route has a title; tests assert `document.title` for all 8 pages |
| Unknown nested path (`/employees/123/unknown`) not covered | low | patch (done) | Test added, falls through to not-found |
| No `h1`; `mat-toolbar color` has no effect under the Material 3 theme | low | patch (done) | Toolbar title is an `h1`; toolbar colours now set with theme variables |
| CI: no job timeouts; `cancel-in-progress` cancels runs on main; no formatting check; no Node engines field; `.angular`, `node_modules`, `dist` not ignored at root; unused Material Symbols stylesheet requested | low to medium | patch (done) | `timeout-minutes: 20` on all jobs, cancellation skipped on main, `format:check` step and scripts, `engines`, root ignore rules, stylesheet link removed |
| `package-lock.json` and `public/` missing | false | rejected | Both are tracked (`git ls-files`); they were left out of the review diff on purpose |
| `gradlew` may lack the executable bit | false | rejected | `git ls-files -s backend/gradlew` shows mode 100755 |
| Action versions "unusual" | false | rejected | Majors read from the GitHub releases API (checkout 7, setup-java 6, setup-node 7, gradle actions 6) |
| Duplicate runs on PR branches; actions not pinned by SHA; budgets loose; `auth` page class naming; main.ts bootstrap fallback; app.config untested; provideRouter options | low | rejected | Cosmetic or unlikely; fixes add configuration without a named harm |
| Roboto loaded from Google at runtime (privacy for an HR tool, offline use) | medium | defer | Self-hosting needs a font package decision; see deferred-work.md |
| Sidenav fixed at 240px with no narrow-screen behaviour; no skip-to-content link; no lazy-chunk failure handling | low to medium | defer | Real but out of this story's skeleton scope; see deferred-work.md |
| CI workflow has never run | medium (unverified) | defer | No GitHub remote exists; confirm with the first green run |
