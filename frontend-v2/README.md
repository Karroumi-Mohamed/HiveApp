# HiveApp — React UX rebuild

This directory starts the new **administrator panel** one flow at a time: React, TypeScript and Vite, with Lucide icons and a small shared authentication layout. The first increment is administrator authentication. The original application in `frontend` remains available while further screens are designed. There is no customer frontend or public signup in this directory.

## Run

```sh
cd frontend-v2
npm ci
npm run dev
```

Open http://127.0.0.1:5173. Vite proxies `/api` to the existing backend at http://localhost:8080. Use `HIVE_API_PROXY=http://localhost:8081 npm run dev` for a different backend port. Deployed builds can configure `VITE_API_URL` or use an API reverse proxy.

## First flows

- `/admin/login`: platform operator login.
- `/admin`: authenticated confirmation screen, until the next UX increment is designed.
- Password recovery, invitation activation, initial password and operator email verification use the existing administrator endpoints and email link routes. There is no self-registration for platform administrators. Temporary credentials cannot access the signed-in screen. The administrator session is stored for the current browser tab; logout calls the appropriate revocation endpoint.

The old Vue screens, copied commercial API clients, audits and screenshots have been removed. Existing ignored `.data.local` and `.env.backend.local` files were preserved. This frontend does not launch or alter that database automatically.

## Verify

```sh
npm run typecheck
npm test
npm run build
```

This is the authentication increment only. Other administration flows will be added as they are reviewed.
