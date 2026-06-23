# HiveApp Frontend

React 19, TypeScript, Vite, Bun, Bootstrap 5, Bootstrap Icons, React Router, and Axios.

## Local Development

Backend must be running on `http://localhost:8080`.

```sh
bun install
bun run dev -- --host 127.0.0.1
```

The Vite dev server proxies `/api` to the backend. Admin login currently uses:

```text
admin@hiveapp.com
admin123
```

## Build

```sh
bun run build
```
