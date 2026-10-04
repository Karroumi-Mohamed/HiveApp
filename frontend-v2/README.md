# Hive administration — frontend v2

An independent Vue 3, TypeScript and Vite application in `frontend-v2`. It uses the existing Hive backend and its permission, preview, concurrency and durable execution contracts. There is no React dependency, demo mode, fixture store or fallback to generated business data.

## Run against the existing backend

```sh
cd frontend-v2
npm ci
npm run dev
```

Open **http://127.0.0.1:5173** and sign in with a platform operator. Vite proxies `/api` to `http://localhost:8080`. Override `HIVE_API_PROXY` when starting Vite to target another backend.

## Run the local backend with persistent data

Requires Java 21 and Maven. From a second terminal:

```sh
cd frontend-v2
./scripts/start-backend.sh
```

The launcher runs the existing backend with its existing seeders. It creates a local administrator and catalog only where those records are missing, and stores data in `.data.local/hiveapp.mv.db`. A restart preserves the database. This is the actual application database; screens read it through authenticated APIs.

The local administrator email and generated password are in **`.env.backend.local`**, created with owner-only permissions and excluded from Git. The account currently uses `admin@hive.local`. This file is a local bootstrap credential, not a password-reset mechanism for an existing database.

The standard bootstrap creates plans, extensions, capacity packages, prices, the feature registry and permissions. To also populate connected customer workflows, stop the backend and run:

```sh
./scripts/start-backend.sh --seed-customers
```

This opt-in development seeder creates eleven persistent customers through the backend's provisioning and reviewed commercial services. It includes paid subscriptions, add-ons/capacity, pending and renewal changes, suspended access, cancellation, billing adjustments, agreements, a campaign/offer redemption and durable jobs. Existing scenarios are preserved on subsequent runs. It is restricted to the development profile and local H2 datasource. See [CUSTOMER-SCENARIOS.md](CUSTOMER-SCENARIOS.md) for the customer names and paths to exercise.

For another datasource, supply `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_DRIVER_CLASS_NAME`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`. The launcher defaults to the existing development profile and H2 driver. Configure the backend separately for a production environment.

## Administration flows

| Area       | Connected capabilities                                                                                                                                                        |
| ---------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Overview   | Subscription and offer summaries, reports, product holdings, attention and activity; financial records do not establish payment readiness                                     |
| Customers  | Directory, global agreements, communications and invoices; customer subscription/lifecycle, billing profile and history, changes, agreements and contextual offer application |
| Catalog    | Plan families and revisions; features and quotas; extension compatibility; capacity packages; price lifecycle and scheduled replacement; subscribers and history              |
| Commercial | Offers, campaigns, audiences and commercial rules; staged offer authoring, related campaign offers, audience activations, lifecycle reviews and redemption evidence           |
| Operations | Work queue from the existing attention projection; executions selected by kind; delivery selected by type; secondary audit activity and permitted outcome recovery            |
| Settings   | Operators, access roles/presets, platform controls and system status; Profile contains own access, notification preferences and appearance                                    |

The sidebar contains five daily areas: **Overview, Customers, Catalog, Commercial and Operations**. Settings is secondary navigation; the personal Inbox is in the header. Billing is not a primary workspace in this release. Global agreement, communication and invoice indices remain reachable under Customers. Operations groups changes, repricing and plan revision applications behind an execution-kind selector; it does not yet provide one combined execution list. Plan applications still require a plan revision filter. System health, backlogs, registry status and log access are under Settings.

Contextual links carry a validated, same-origin `returnTo` path. Back links restore the originating customer, record or filtered list, and the router remembers scroll positions during navigation. Local tab state preserves useful filters while retaining the current customer context. These mechanisms do not provide server-persisted saved views.

The subscription-change flow is **customers → target configuration → timing and reason → backend impact review → confirmation → outcome**. A fixed customer skips audience selection, starts from current terms and uses the existing single-customer preview/apply authority when available. Retained add-ons/capacity stay visible with their supported operations. Population changes require explicit acknowledgement that the chosen complete configuration replaces customer holdings; a per-customer preservation mode is not implemented. Repricing and plan application have staged configuration and use their own backend state machines. Available actions, expiry, blockers, permissions and concurrency checks remain authoritative on the server.

Offers use **campaign and dates → products → terms → definition review → save**; lifecycle publication remains a separate permitted action. Agreements use subscription terms, term/end behavior and a distinct review. Operator creation uses **identity → eligible roles → initial access → review → create**, with an explicit option to assign access later. Profile provides inline notification topic/channel preferences and notification language, a searchable effective-permission inventory, and light/dark/system appearance saved locally per operator. Access-role permissions use a searchable grouped editor backed by the grantable-permission API. Full French/Arabic interface translation and RTL parity are still pending.

**Payment is deferred.** `paymentEnabled` is false: payment collection, manual settlement, refunds, charge recovery and provider reconciliation are not delivered product actions. The overview omits collection headlines and financial reports. Existing authorized invoice, agreement and financial-history records remain readable. Development ledger records and payment-related state labels are not evidence that a payment provider is ready.

The account and company client portal remains in `frontend`; this application covers platform administration. Original placeholder pages for platform accounts, collaborations and role templates are not presented as developed capabilities. Existing backend scheduled executions are exposed; a configurable trigger-and-recipe automation engine is not implemented in the backend.

## Build and deployment

```sh
npm run typecheck
npm run build
```

Node 22.12+ is supported. The deployable output is `dist/`. Serve `index.html` for client-side routes and proxy `/api` to the backend, or set `VITE_API_URL` at build time with a correctly configured backend origin. `npm run preview` serves built assets; configure an API proxy when using it.

Set the backend activation URL to the deployed frontend origin. The local launcher sets it to `http://127.0.0.1:5173`. Existing activation, password-reset and email-verification link paths are supported. Access and refresh tokens use tab-scoped session storage; sign-out requests token revocation.

**Operator creation includes an additive API change.** `POST /api/admin/users` accepts optional `roleIds` (up to 100 IDs). Requests that omit the field retain the existing behavior, and the five-argument Java request constructor is preserved for existing callers. The provisioning service creates the operator and assigns requested roles in one transaction through the existing permission-guarded services; a denied/invalid assignment rolls back creation, and activation delivery runs after commit. This adds no database table or column for role assignment, but it is a request-contract extension, not “no schema changes.”

The optional customer scenario seeder remains an added backend development helper. Subscription UUID mappings declare the existing lifecycle check constraints separately from their column types so Hibernate can validate the persistent database on restart. The original React frontend remains intact. Domain API clients and contracts are copied into this application so it can run independently.

## Verification and operational limits

On 4 October 2026, the frontend production build (including Vue/TypeScript checking) and Java 21 backend compilation passed. Test sources and suites were skipped. Authenticated browser review verified the customer directory, existing add-on/capacity retention across plan changes, signed single-customer impact review, published-offer detail, staged offer setup, effective plan features, grouped role authoring, queue filtering, execution results and the exact result-to-customer change path. Desktop and mobile navigation were reviewed; the narrow page had no document-level horizontal overflow, and focus reached the main content after navigation. No consequential changes were confirmed.

Source review covered independent creation/preview/apply authority and constrained access paths. Every possible permission/state combination has not received live acceptance. Earlier verification additionally covered database persistence across a backend restart.

Consequential financial, access-management and email-delivery mutations were not submitted during browser review. The customer seeder exercises backend provisioning, signed previews, applications and manual development ledger flows. These records support UI acceptance, but do not establish production payment-provider or email-delivery readiness. The current local backend uses development billing and email configuration; trusted external integrations still require deployment configuration and verification.

See [DESIGN.md](DESIGN.md) for the current structure, contract boundaries and backend backlog. [UX-AUDIT.md](UX-AUDIT.md) separates implemented changes from the historical findings. Current review captures are [Operations](previews/operations-2026-10-04.jpg), [Overview](previews/overview-2026-10-04.jpg) and [mobile Customers](previews/customers-mobile-2026-10-04.jpg). Older captures predate this iteration.
