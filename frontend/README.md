# Frontend

Angular 22 single-page app with Angular Material. Pages are standalone components loaded lazily per feature (see `src/app/app.routes.ts`).

| Task | Command (run in `frontend/`) |
| --- | --- |
| Install dependencies | `npm ci` |
| Run the dev server | `npm start` |
| Unit tests (once) | `npx ng test --watch=false` |
| Production build | `npx ng build` |

Requires Node 24. Business calculations never happen here: all numbers come from the API.
