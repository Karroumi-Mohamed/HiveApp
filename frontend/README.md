# HiveApp frontend

React 19 + TypeScript interface for the isolated platform-admin (`/admin`) and client-workspace (`/app`) shells. French is the working language; the layout and token system are RTL-ready for Arabic.

## Commands

```bash
bun install
bun dev
bun run verify
bun run build
bun start
```

`bun dev` serves the application on `http://localhost:3000`; the HiveApp backend is expected on `http://localhost:8080` unless `BUN_PUBLIC_API_URL` is set.

The active UI rules are in `../docs/UI_DESIGN_SYSTEM.md`. `../docs/UI_SPEC.md` is historical inventory only.
