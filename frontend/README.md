# Frontend

Angular 22 single-page app with Angular Material. Pages are standalone components loaded lazily per feature (see `src/app/app.routes.ts`).

| Task | Command (run in `frontend/`) |
| --- | --- |
| Install dependencies | `npm ci` |
| Run the dev server | `npm start` |
| Unit tests (once) | `npx ng test --watch=false` |
| Check formatting | `npm run format:check` (fix with `npm run format`) |
| Production build | `npx ng build` |

Requires Node 24. Business calculations never happen here: all numbers come from the API.

## Configuration and sign-in

- The API address is read at startup from `public/config.json` (`apiBaseUrl`), so one build can run
  against any environment. Replace that file when deploying.
- The access token is kept in memory only. Reloading the page signs you out, by design.
- Locally, start the backend with the `local` profile (it creates two demo HR Manager accounts, see
  `.env.example` for the password setting) and run `npm start`.
