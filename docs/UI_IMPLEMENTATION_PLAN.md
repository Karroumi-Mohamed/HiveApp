# HiveApp UI Implementation Plan

Status: UI-0 through UI-4 implemented for the current backend surface as of 2026-08-12. UI-5 remains a deliberate product/backend design phase. Product-flow decisions remain in `FLOW_DECISIONS.md`; backend gaps remain in `TOFIX.md` and `IMPLEMENTATION_PLAN.md`.

## Architecture fixed before screens

- Bun full-stack development server with HTML imports; no Vite.
- React 19 and TypeScript.
- Tailwind CSS 4 and locally owned shadcn/Radix primitives.
- Phosphor Icons for application icons.
- React Router, TanStack Query, TanStack Table, React Hook Form, and Zod.
- i18next with explicit French default and Arabic opt-in.
- Native `fetch` through typed admin and client API clients.
- One application, two isolated session/context boundaries for `/admin` and `/app`.

## Phase UI-0 — Foundation and design approval

**Completed.** Semantic tokens, French-first locale handling, RTL direction, isolated session/query clients, responsive shells, shared operational patterns, error feedback, tests, and production build are in place.

1. Replace the generated Bun demo with a design-system preview.
2. Establish color, typography, spacing, radius, motion, and direction tokens.
3. Configure French/Arabic language and direction handling.
4. Establish router, query client, error contract, and test environment.
5. Build the responsive shell, page header, status badge, table state, form state, empty state, and impact/warning examples.
6. Review the preview visually before wiring production screens.

Exit: light and dark themes, LTR and RTL, mobile and desktop, keyboard focus, type checks, component tests, and production build pass.

## Phase UI-1 — Platform admin access

**Completed.** Admin authentication, current-access view, permission-filtered navigation, operator management, role lifecycle, assignment, and catalogue-driven permission management are wired to live APIs.

1. Admin login and admin session bootstrap.
2. Admin shell navigation filtered by effective permissions.
3. Admin users: paginated list, search, create/promote, activate/deactivate, role assignment/removal.
4. Admin roles: list/inspector, create/edit, lifecycle, catalogue-driven permission picker.
5. My Access for non-SuperAdmin operators.

The two admin access screens are the first complete vertical slice because they prove authentication, pagination, errors, mutations, permission-aware UX, forms, and catalogue-driven authorization without depending on Permissionizer v2.

## Phase UI-2 — Plans and subscriptions

**Completed for current APIs.** Plans, features, quotas, subscribers, account lookup, subscriptions, overrides, checkout confirmation, add-ons, and quota packages use stable read models and pagination.

1. Plan templates list/inspector and lifecycle.
2. Plan feature composition and quota policies.
3. Plan subscribers.
4. Account subscription lookup, plan replacement, overrides, and checkout confirmation.
5. Add-ons and quota packages as supported by the current backend flows.

The subscriber and account directory endpoints now use the stable `PageResponse` contract.

## Phase UI-3 — Platform features and operations

**Completed for current APIs.** Registry inventory, operational controls, visibility/sales/grant/runtime state, and typed synchronization history are exposed without rendering raw JSON.

1. Platform Features list/inspector sourced from the registry.
2. Feature lifecycle/availability display and allowed technical activation actions.
3. Last synchronization and operational result surfaces where current endpoints support them.

## Phase UI-4 — Client shell

**Completed for current APIs.** Authentication/activation, Company context, organization structures, direct member creation and access lifecycle, roles, authorization assignments, subscription changes, and B2B collaboration workflows are wired.

1. Client login/activation and separate member session bootstrap.
2. Account/Company context switcher and centralized context headers.
3. Companies, organization groups/templates, members, roles, and subscription surfaces.
4. B2B collaborations and provider/requester workflows.
5. Permission-aware navigation and contextual cache invalidation.

Removed invitation flows must not be recreated. Member creation and temporary-credential activation follow the current product decisions.

## Phase UI-5 — Target-aware management

**Deferred by design.** The current backend has delegation ceilings but no target-management assignment model; the UI therefore does not imply Department/Group-derived authority.

This is a later product/backend design, not a UI inference.

- Delegation ceiling continues to constrain what permissions an actor can grant.
- A management-assignment model will constrain which members/resources the actor may manage.
- Candidate targets may include member, explicit reusable set, organization group, Company, or Collaboration.
- Organization groups remain structural groupings and grant nothing automatically.
- Permissionizer v2 may provide uniform resource-rule evaluation, but HiveApp owns the assignment data model and screens.

Exit: management scope, impact preview, group-movement effects, owner protection, and audit behavior have explicit backend contracts before controls are exposed.

## Shared quality gates

- No raw permission catalogue embedded in UI code.
- No direct API calls outside configured API clients/query functions.
- No client shell context omitted from contextual query keys.
- No screen without loading, empty, error, denied, and mutation-result behavior.
- French is complete before a feature is considered done; Arabic layout remains functional as translations are added.
- Component tests use `bun test`; type checking and production build run for every UI batch.
- Responsive and keyboard checks are part of each screen, not a final cleanup phase.

## Legacy document policy

`UI_SPEC.md` is an inventory reference only. A useful screen or interaction may be carried forward after checking current flows and endpoints. Old stack choices, invitation flows, permission constants/counts, and route/API claims do not become requirements by copying them.

## Current boundaries

- French is the complete working language. Arabic direction, fonts, and layout behavior are implemented; Arabic copy remains a later translation pass.
- Target-aware member/resource management is UI-5 and is not inferred from organization groups.
- Ownership transfer, permanent purge, invoices/payments/refunds, and other capabilities without current backend contracts are not fabricated in the UI.
- The current deliverable is complete against the backend surface, not a claim that every future HiveApp business module is implemented.
