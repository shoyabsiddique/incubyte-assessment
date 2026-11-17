# e2e

Playwright smoke tests that drive the real stack in a browser.

## Run locally

1. Start the database, the API (`SPRING_PROFILES_ACTIVE=local,seed`) and the UI (`npm start` in `frontend`).
2. `cd e2e && npm ci && npm test`

Locally the installed Chrome is used (no browser download). In CI (`CI=true`) Chromium is installed first.

| Variable | Default |
| --- | --- |
| `E2E_BASE_URL` | `http://localhost:4200` |
| `E2E_EMAIL` | the local demo HR Manager account |
| `E2E_PASSWORD` | the documented local demo password |

The smoke test signs in, searches EMP-000001, opens the employee, checks both salaries and the history, and signs out.
