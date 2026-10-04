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

| Area       | Connected capabilities                                                                                                                                                     |
| ---------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Overview   | Subscription and financial summary; revenue, subscription activity, offer outcomes and product holdings reports; attention and activity                                    |
| Customers  | Search, selection and customer context; subscription configuration and lifecycle; billing profile, invoices, financial history, changes, agreements, messages and offers   |
| Catalog    | Plan families and revisions; features and quotas; extension compatibility; capacity packages; price lifecycle and scheduled replacement; subscribers and history           |
| Commercial | Campaign, segment, policy and offer authoring; lifecycle reviews; audience snapshots; ownership; revisions; customer application and redemption evidence                   |
| Billing    | Invoices and invoice documents; credits, settlement and refund reviews; agreements; payment commands and provider events                                                   |
| Operations | Subscription changes, repricing and plan applications; individual outcomes and recovery; messages, notifications and delivery; attention, audit evidence and system status |
| Settings   | Operators, roles and role presets; effective permissions; feature controls and history; notification preferences and profile                                               |

The sidebar contains six working areas plus Settings. Records and their related actions stay within these areas. For example, a plan leads directly to its prices and subscriber applications, and a customer leads to their subscription, invoice, agreement or reviewed change. Editing catalog definitions and moving existing subscriptions use distinct backend operations.

The subscription-change flow is **customers → target configuration → timing and reason → backend impact review → confirmation → execution results**. Repricing and plan application use their own backend state machines. Available actions, expiry, blockers, permissions and concurrency checks remain authoritative on the server.

The account and company client portal remains in `frontend`; this application covers platform administration. Original placeholder pages for platform accounts, collaborations and role templates are not presented as developed capabilities. Existing backend scheduled executions are exposed; a configurable trigger-and-recipe automation engine is not implemented in the backend.

## Build and deployment

```sh
npm run typecheck
npm run build
```

Node 22.12+ is supported. The deployable output is `dist/`. Serve `index.html` for client-side routes and proxy `/api` to the backend, or set `VITE_API_URL` at build time with a correctly configured backend origin. `npm run preview` serves built assets; configure an API proxy when using it.

Set the backend activation URL to the deployed frontend origin. The local launcher sets it to `http://127.0.0.1:5173`. Existing activation, password-reset and email-verification link paths are supported. Access and refresh tokens use tab-scoped session storage; sign-out requests token revocation.

**No API schema change was required for this version.** The optional customer scenario seeder is an added backend development helper; existing business services and contracts remain intact. Subscription UUID mappings now declare the existing lifecycle check constraints separately from their column types so Hibernate can validate the persistent database on restart. The original React frontend remains intact. Domain API clients and contracts are copied into this application so it can run independently.

## Verification and operational limits

Vue/TypeScript checking and the production build pass. Live authenticated reads, seeded catalog records, reporting, selectors, local views, draft guards, reload/session restoration and responsive navigation were reviewed. The persistent database was checked across a backend restart. Existing tests were excluded from reading and execution, as requested.

Consequential financial, access-management and email-delivery mutations were not submitted during browser review. The customer seeder exercises backend provisioning, signed previews, applications and manual development ledger flows. These records support UI acceptance, but do not establish production payment-provider or email-delivery readiness. The current local backend uses development billing and email configuration; trusted external integrations still require deployment configuration and verification.

See [DESIGN.md](DESIGN.md) for the UX analysis, references, backend capability mapping and recommended follow-up changes. Current screenshots are in `previews/`.
