# HiveApp — Enterprise UI/UX Specification
> Version 2.1 — updated for the Bootstrap 5 admin shell, 68 discovered permissions, invitation flow, and /me endpoints

---

## 0. Guiding Principles

1. **Every action is permission-aware.** No action is rendered unconditionally.
   - **Hidden** — user has zero permission in that namespace (section removed from nav/page entirely).
   - **Disabled + tooltip** — user has the parent namespace permission but not this specific action (shows intent, explains why).
   - **Active** — user has the exact permission.
2. **Context-aware dashboards.** The dashboard morphs based on who is logged in and what they can see. A SuperAdmin sees global health. A plan-restricted admin sees only their slice.
3. **Never hard-code permission strings in JSX.** All permission checks go through a single `usePermission` hook fed from a server-fetched permission set.
4. **One source of truth for permissions.** On login, immediately fetch `GET /api/v1/me/permissions` (client) or `GET /api/admin/me` (admin). Flatten into a `Set<string>` stored in Zustand. Re-fetch only on token refresh or after a 403.
5. **Optimistic UI + server validation.** Mutations are optimistic. On 403 the UI rolls back and shows a toast: *"You no longer have permission for this action."*
6. **Invite-only member addition.** Members join via email invitation, not direct creation. The "Add Member" flow opens an invite drawer, not a user-creation form.
7. **French and Arabic first.** All visible UI copy must be available in French and Arabic. Arabic switches the document to RTL and layouts must use logical start/end spacing where direction matters.
8. **Low-density operational UI.** Do not fill pages with explanatory paragraphs. Prefer concise labels, filters, status chips, compact metrics, detail panels, and action controls. Long explanations belong in docs, not on repeated admin screens.

---

## 1. Technology Stack

| Concern | Choice | Why |
|---|---|---|
| Framework | React 19 + TypeScript | Concurrent features, strict typing |
| Routing | React Router | Simple guarded admin routes for the current shell |
| Server state | Route-local API loaders first; TanStack Query only when repeated cache/invalidation complexity appears | Avoid framework weight before the flows need it |
| Client state | React context + session storage for auth; introduce Zustand only for broader app-wide state | Keeps the first admin shell small and explicit |
| Component primitives | Bootstrap 5 | Stable operational components, tables, forms, alerts, RTL-compatible layout rules |
| Forms | Native controlled forms first; React Hook Form + Zod for complex create/edit flows | Match form complexity to risk |
| Table | Bootstrap tables for current feature/admin lists; TanStack Table only when sorting/virtualization/server filtering is required | Keep list UIs readable without premature abstraction |
| Notifications | Bootstrap alerts/toasts | No additional dependency needed yet |
| Icons | Bootstrap Icons | Matches Bootstrap 5 visual language |
| Date | date-fns | Lightweight |
| Charts | Recharts when charts are introduced | Use only for real analytics screens |
| HTTP client | Axios | Interceptors for admin/client JWTs and consistent API errors |

---

## 2. Permission Architecture

### 2.1 Auth store (Zustand)

```ts
// stores/auth.ts
interface AuthSlice {
  token: string | null
  refreshToken: string | null
  tokenType: "CLIENT" | "ADMIN" | null
  userId: string | null
  isOwner: boolean             // client slice — workspace owner bypass
  isSuperAdmin: boolean        // admin slice — full bypass
  permissions: Set<string>     // flattened leaf paths e.g. "platform.staff.read"
  isLoaded: boolean
}
```

**Login flow (client):**
1. `POST /api/v1/auth/login` → `{ accessToken, refreshToken, expiresIn }`
2. Decode JWT, store `userId` + `tokenType = "CLIENT"`
3. `GET /api/v1/me/permissions` → `{ memberId, isOwner, permissions: string[] }`
4. Set `permissions = new Set(permissions)`, `isOwner`, `isLoaded = true`
5. Every `usePermission` call unblocks

**Login flow (admin):**
1. `POST /api/admin/auth/login` → same token shape
2. `GET /api/admin/me` → `{ id, email, isSuperAdmin, isActive, permissions: string[] }`
3. Set `isSuperAdmin`, `permissions`, `isLoaded = true`

---

### 2.2 `usePermission` hook

```ts
export function usePermission(path: string): boolean {
  const { permissions, isOwner, isSuperAdmin, isLoaded } = useAuthStore()
  if (!isLoaded) return false
  if (isOwner || isSuperAdmin) return true   // full bypass
  return permissions.has(path)
}

// Namespace check — has ANY permission under this prefix
export function useHasNamespace(prefix: string): boolean {
  const { permissions, isOwner, isSuperAdmin } = useAuthStore()
  if (isOwner || isSuperAdmin) return true
  for (const p of permissions) {
    if (p.startsWith(prefix)) return true
  }
  return false
}
```

---

### 2.3 `<CanDo>` component

```tsx
interface CanDoProps {
  perm: string
  fallback?: "hide" | "disable"   // default: "hide"
  tooltip?: string
  children: React.ReactNode
}

export function CanDo({ perm, fallback = "hide", tooltip, children }: CanDoProps) {
  const allowed = usePermission(perm)
  if (allowed) return <>{children}</>
  if (fallback === "hide") return null
  return (
    <Tooltip content={tooltip ?? "You don't have permission for this action"}>
      <span className="pointer-events-none opacity-40 select-none">
        {children}
      </span>
    </Tooltip>
  )
}
```

---

### 2.4 Route guards (TanStack Router)

```ts
beforeLoad: ({ context }) => {
  if (!context.auth.isLoaded) throw redirect({ to: "/loading" })
  if (!context.auth.token) throw redirect({ to: "/login" })
  if (!context.auth.hasNamespace("platform.staff")) {
    throw redirect({ to: "/app" })   // to dashboard, not a hard 403 wall
  }
}
```

---

### 2.5 Permission constants — full `P` object

All 62 leaf permission paths. Mirror of the backend-generated `PlatformPermissions.java`.
**Never hardcode strings anywhere else — always reference `P.*`.**

```ts
// lib/permissions.ts
export const P = {

  // ── Workspace ────────────────────────────────────────────
  WORKSPACE_READ:              "platform.workspace.read",
  WORKSPACE_DELETE:            "platform.workspace.delete",

  // ── Invitations ──────────────────────────────────────────
  INVITATIONS_SEND:            "platform.invitations.send",
  INVITATIONS_LIST:            "platform.invitations.list",
  INVITATIONS_REVOKE:          "platform.invitations.revoke",

  // ── Staff (Members) ──────────────────────────────────────
  STAFF_READ:                  "platform.staff.read",
  STAFF_ADD:                   "platform.staff.add",
  STAFF_UPDATE:                "platform.staff.update",
  STAFF_DELETE:                "platform.staff.delete",
  STAFF_ASSIGN_ROLE:           "platform.staff.assign_role",
  STAFF_REMOVE_ROLE:           "platform.staff.remove_role",
  STAFF_GRANT_PERM:            "platform.staff.grant_permission",
  STAFF_REVOKE_PERM:           "platform.staff.revoke_permission",
  STAFF_READ_OVERRIDES:        "platform.staff.read_overrides",

  // ── RBAC (Roles) ─────────────────────────────────────────
  RBAC_VIEW:                   "platform.rbac.view",
  RBAC_VIEW_COMPANY:           "platform.rbac.view_company",
  RBAC_CREATE:                 "platform.rbac.create",
  RBAC_UPDATE:                 "platform.rbac.update",
  RBAC_DELETE:                 "platform.rbac.delete",
  RBAC_GRANT:                  "platform.rbac.grant",
  RBAC_REVOKE:                 "platform.rbac.revoke",

  // ── Company ──────────────────────────────────────────────
  COMPANY_CREATE:              "platform.company.create",
  COMPANY_READ_ALL:            "platform.company.read_all",
  COMPANY_READ_SINGLE:         "platform.company.read_single",
  COMPANY_UPDATE:              "platform.company.update",
  COMPANY_DELETE:              "platform.company.delete",

  // ── B2B Collaboration ────────────────────────────────────
  B2B_REQUEST:                 "platform.b2b.request",
  B2B_ACCEPT:                  "platform.b2b.accept",
  B2B_REVOKE:                  "platform.b2b.revoke",
  B2B_VIEW:                    "platform.b2b.view",
  B2B_VIEW_INCOMING:           "platform.b2b.view_incoming",
  B2B_GRANT_PERM:              "platform.b2b.grant_permission",
  B2B_REVOKE_PERM:             "platform.b2b.revoke_permission",

  // ── Subscription (client) ────────────────────────────────
  SUB_READ:                    "platform.subscription.read",
  SUB_CATALOG:                 "platform.subscription.catalog",
  SUB_PREVIEW:                 "platform.subscription.preview",
  SUB_APPLY:                   "platform.subscription.apply",

  // ── Admin: Users ─────────────────────────────────────────
  ADMIN_USERS_READ:            "platform.admin_users.read",
  ADMIN_USERS_READ_DETAIL:     "platform.admin_users.read_detail",
  ADMIN_USERS_CREATE:          "platform.admin_users.create",
  ADMIN_USERS_TOGGLE:          "platform.admin_users.toggle_active",
  ADMIN_USERS_ASSIGN_ROLE:     "platform.admin_users.assign_role",
  ADMIN_USERS_REMOVE_ROLE:     "platform.admin_users.remove_role",

  // ── Admin: Roles ─────────────────────────────────────────
  ADMIN_ROLES_READ:            "platform.roles.read",
  ADMIN_ROLES_READ_DETAIL:     "platform.roles.read_detail",
  ADMIN_ROLES_CREATE:          "platform.roles.create",
  ADMIN_ROLES_UPDATE:          "platform.roles.update",
  ADMIN_ROLES_TOGGLE:          "platform.roles.toggle_active",
  ADMIN_ROLES_GRANT_PERM:      "platform.roles.grant_permission",
  ADMIN_ROLES_REVOKE_PERM:     "platform.roles.revoke_permission",

  // ── Admin: Subscriptions ─────────────────────────────────
  ADMIN_SUBS_READ:             "platform.admin.subscriptions.read",
  ADMIN_SUBS_CREATE:           "platform.admin.subscriptions.create",
  ADMIN_SUBS_OVERRIDES:        "platform.admin.subscriptions.update_overrides",

  // ── Plans ────────────────────────────────────────────────
  PLANS_LIST:                  "platform.plans.list",
  PLANS_CREATE:                "platform.plans.create",
  PLANS_TOGGLE:                "platform.plans.toggle_active",
  PLANS_LIST_FEATURES:         "platform.plans.list_features",
  PLANS_ASSIGN_FEATURE:        "platform.plans.assign_feature",
  PLANS_UPDATE_FEATURE:        "platform.plans.update_feature",
  PLANS_REMOVE_FEATURE:        "platform.plans.remove_feature",

  // ── Registry ─────────────────────────────────────────────
  REGISTRY_READ:               "platform.registry.read",
  REGISTRY_CATALOG:            "platform.registry.catalog",
  REGISTRY_FEATURE_CATALOG:    "platform.registry.feature_catalog",
  REGISTRY_PERMISSION_CATALOG: "platform.registry.permission_catalog",
  REGISTRY_UPDATE_ACTIVE:      "platform.registry.update_active",

} as const

export type PermissionPath = typeof P[keyof typeof P]
```

---

## 3. API Reference (endpoints used by UI)

### 3.1 Auth

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/auth/register` | — | Register + auto-provision workspace + FREE plan |
| POST | `/api/v1/auth/login` | — | Client login → CLIENT JWT |
| POST | `/api/v1/auth/refresh` | — | Refresh token |
| POST | `/api/admin/auth/login` | — | Admin login → ADMIN JWT |

### 3.2 Permission bootstrap (call immediately after login)

| Method | Path | Auth | Returns |
|--------|------|------|---------|
| GET | `/api/v1/me/permissions` | CLIENT JWT | `{ memberId, isOwner, permissions: string[] }` |
| GET | `/api/admin/me` | ADMIN JWT | `{ id, email, isSuperAdmin, isActive, permissions: string[] }` |

The admin shell now depends on `POST /api/admin/auth/login` and `GET /api/admin/me`; both are part of the current backend admin flow. Client screens must use the client login and client `/me/permissions` flow separately.

### 3.3 Client endpoints

| Method | Path | Permission required |
|--------|------|---------------------|
| GET | `/api/v1/accounts/me` | authenticated |
| DELETE | `/api/v1/accounts/me` | `platform.workspace.delete` |
| GET | `/api/v1/plans` | — (public) |
| GET | `/api/v1/subscriptions/me` | `platform.subscription.read` |
| GET | `/api/v1/subscriptions/catalog` | `platform.subscription.catalog` |
| POST | `/api/v1/subscriptions/preview` | `platform.subscription.preview` |
| POST | `/api/v1/subscriptions/apply` | `platform.subscription.apply` |
| POST | `/api/v1/companies` | `platform.company.create` |
| GET | `/api/v1/companies` | `platform.company.read_all` |
| GET | `/api/v1/companies/:id` | `platform.company.read_single` |
| PATCH | `/api/v1/companies/:id` | `platform.company.update` |
| DELETE | `/api/v1/companies/:id` | `platform.company.delete` |
| GET | `/api/v1/members` | `platform.staff.read` |
| POST | `/api/v1/members` | `platform.staff.add` |
| PATCH | `/api/v1/members/:id` | `platform.staff.update` |
| DELETE | `/api/v1/members/:id` | `platform.staff.delete` |
| POST | `/api/v1/members/:id/roles` | `platform.staff.assign_role` |
| DELETE | `/api/v1/members/:id/roles/:roleId` | `platform.staff.remove_role` |
| POST | `/api/v1/members/:id/permissions` | `platform.staff.grant_permission` |
| DELETE | `/api/v1/members/:id/permissions/:code` | `platform.staff.revoke_permission` |
| GET | `/api/v1/members/:id/permissions?companyId=` | `platform.staff.read_overrides` |
| GET | `/api/v1/roles` | `platform.rbac.view` |
| GET | `/api/v1/roles/:id` | `platform.rbac.view` |
| POST | `/api/v1/roles` | `platform.rbac.create` |
| PUT | `/api/v1/roles/:id` | `platform.rbac.update` |
| DELETE | `/api/v1/roles/:id` | `platform.rbac.delete` |
| POST | `/api/v1/roles/:id/permissions` | `platform.rbac.grant` |
| DELETE | `/api/v1/roles/:id/permissions/:code` | `platform.rbac.revoke` |
| POST | `/api/v1/invitations` | `platform.invitations.send` |
| GET | `/api/v1/invitations` | `platform.invitations.list` |
| DELETE | `/api/v1/invitations/:id` | `platform.invitations.revoke` |
| GET | `/api/v1/invitations/validate?token=` | — (public) |
| POST | `/api/v1/invitations/accept` | — (public) |
| POST | `/api/v1/collaborations/initiate` | `platform.b2b.request` |
| PATCH | `/api/v1/collaborations/:id/accept` | `platform.b2b.accept` |
| DELETE | `/api/v1/collaborations/:id` | `platform.b2b.revoke` |
| GET | `/api/v1/collaborations` | `platform.b2b.view` |
| GET | `/api/v1/collaborations/incoming` | `platform.b2b.view_incoming` |
| POST | `/api/v1/collaborations/:id/permissions` | `platform.b2b.grant_permission` |
| DELETE | `/api/v1/collaborations/:id/permissions/:code` | `platform.b2b.revoke_permission` |

### 3.4 Admin endpoints

| Method | Path | Permission required |
|--------|------|---------------------|
| GET | `/api/admin/users` | `platform.admin_users.read` |
| GET | `/api/admin/users/:id` | `platform.admin_users.read_detail` |
| POST | `/api/admin/users` | `platform.admin_users.create` |
| POST | `/api/admin/users/:id/toggle-active` | `platform.admin_users.toggle_active` |
| POST | `/api/admin/users/:id/roles` | `platform.admin_users.assign_role` |
| DELETE | `/api/admin/users/:id/roles/:roleId` | `platform.admin_users.remove_role` |
| GET | `/api/admin/roles` | `platform.roles.read` |
| GET | `/api/admin/roles/:id` | `platform.roles.read_detail` |
| POST | `/api/admin/roles` | `platform.roles.create` |
| PUT | `/api/admin/roles/:id` | `platform.roles.update` |
| POST | `/api/admin/roles/:id/toggle-active` | `platform.roles.toggle_active` |
| POST | `/api/admin/roles/:id/permissions` | `platform.roles.grant_permission` |
| DELETE | `/api/admin/roles/:id/permissions/:permId` | `platform.roles.revoke_permission` |
| GET | `/api/admin/plans` | `platform.plans.list` |
| POST | `/api/admin/plans` | `platform.plans.create` |
| PATCH | `/api/admin/plans/:id/active` | `platform.plans.toggle_active` |
| GET | `/api/admin/plans/:id/features` | `platform.plans.list_features` |
| POST | `/api/admin/plans/:id/features` | `platform.plans.assign_feature` |
| PUT | `/api/admin/plans/:id/features/:fid` | `platform.plans.update_feature` |
| DELETE | `/api/admin/plans/:id/features/:fid` | `platform.plans.remove_feature` |
| GET | `/api/admin/subscriptions/account/:id` | `platform.admin.subscriptions.read` |
| POST | `/api/admin/subscriptions/account/:id` | `platform.admin.subscriptions.create` |
| PATCH | `/api/admin/subscriptions/account/:id/overrides` | `platform.admin.subscriptions.update_overrides` |
| GET | `/api/admin/registry/feature-catalog` | `platform.registry.feature_catalog` |
| GET | `/api/admin/registry/permission-catalog` | `platform.registry.permission_catalog` |
| GET | `/api/admin/registry/inventory` | `platform.registry.read` |
| GET | `/api/admin/registry/catalog` | `platform.registry.catalog` |
| PATCH | `/api/admin/registry/features/:id/active` | `platform.registry.update_active` |

---

## 4. Admin Panel

Base URL: `/admin`
All routes: require `tokenType === "ADMIN"` in decoded JWT.
Nav items hide entirely if user has zero permissions in that namespace.

### 4.1 Layout

```
┌──────────────────────────────────────────────────────────────┐
│  HIVEAPP ADMIN                             [avatar] [logout]  │
├───────────────┬──────────────────────────────────────────────┤
│               │                                              │
│  Sidebar      │  Main content area                           │
│  (collapsible │                                              │
│  on mobile)   │                                              │
│               │                                              │
│ ○ Dashboard   │                                              │
│ ▼ Admin Access│ — expands if has admin user or role access   │
│   ○ Members   │ — platform admin operators                   │
│   ○ Roles     │ — platform admin permission roles            │
│ ▼ Plans       │  — expands only if has platform.plans.*      │
│   ○ Templates │  — plan templates and active state           │
│   ○ Features  │  — plan-feature inclusion matrix             │
│ ○ Subscriptions│ — only if has platform.admin.subscriptions.*│
│ ○ Features    │  — only if has platform.registry.*           │
│               │                                              │
│ ───────────── │                                              │
│ ○ My Access   │  — non-SuperAdmin only                       │
└───────────────┴──────────────────────────────────────────────┘
```

**SuperAdmin:** gold `SUPER` chip next to avatar. All nav items always visible.

---

### 4.2 Admin Dashboard `/admin`

Stat cards render only if user can see the relevant data.

```
┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Admin Users  │  │ Admin Roles  │  │ Active Plans │  │ Total Subs   │
│     12       │  │      5       │  │      4       │  │    1,203     │
│ +2 this week │  │ 2 inactive   │  │ 1 inactive   │  │ 98% active   │
└──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘
[P.ADMIN_USERS_READ]  [P.ADMIN_ROLES_READ]  [P.PLANS_LIST]  [P.ADMIN_SUBS_READ]

┌────────────────────────────────────┐  ┌──────────────────────────────────┐
│ Subscription Tier Distribution     │  │ Registry Status                  │
│ (donut chart: FREE / PRO / ENT)    │  │ PUBLIC: 18   BETA: 3  INTERNAL: 2│
└────────────────────────────────────┘  └──────────────────────────────────┘
[only if P.ADMIN_SUBS_READ]              [only if P.REGISTRY_READ]

┌──────────────────────────────────────────────────────────────┐
│ My Permissions  (non-SuperAdmin only)                        │
│ Tag cloud of all granted permissions, grouped by domain      │
└──────────────────────────────────────────────────────────────┘
```

---

### 4.3 Admin Access Group `/admin/access/*`

The admin-access group manages people who can operate the platform control plane. It is not client member management and it is not workspace RBAC. Client members remain under the client workspace surface; platform admin members are backed by `AdminUser`, `AdminRole`, and `AdminRolePermission`.

`/admin/access` redirects to `/admin/access/members`. The sidebar exposes the group only when the current admin is a SuperAdmin or has at least one of `platform.admin_users.read` or `platform.roles.read`.

| Route | Purpose | Required read permission |
|-------|---------|--------------------------|
| `/admin/access/members` | Platform admin operators, status, SuperAdmin flag, assigned admin roles | `platform.admin_users.read` |
| `/admin/access/roles` | Platform admin roles and their platform-control permission matrix | `platform.roles.read` |

### 4.4 Admin Members `/admin/access/members`

This page answers who can enter the admin control plane and which platform admin roles they carry. It must not present client accounts, workspace members, or B2B collaborators.

Primary data sources:

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/admin/users` | Lists admin users with email, active state, SuperAdmin flag, and assigned admin role summaries |
| GET | `/api/admin/roles` | Lists active/inactive admin roles so assign-role controls can avoid already assigned roles |
| POST | `/api/admin/users` | Promotes an existing platform user to admin |
| POST | `/api/admin/users/:id/toggle-active` | Activates or deactivates another admin user |
| POST | `/api/admin/users/:id/roles` | Assigns an active admin role to an admin user |
| DELETE | `/api/admin/users/:id/roles/:roleId` | Removes an admin role from an admin user |

Current UX contract:

```
Admin Access / Members
--------------------------------------------------------------
[New Admin Member] [Refresh]

[metric: members] [metric: active] [metric: super admins] [metric: roles]

Search by email, user id, or assigned role

Operator row:
  avatar initial | email | user id | active/inactive | SuperAdmin | current session
  assigned roles as removable chips
  action buttons: assign role, toggle active
```

| Action | UI permission gate | Backend rule |
|--------|--------------------|--------------|
| Create admin member | SuperAdmin or `platform.admin_users.create` | Non-SuperAdmin cannot create a SuperAdmin; duplicate admin-user records are rejected |
| Toggle active | SuperAdmin or `platform.admin_users.toggle_active` | An admin cannot deactivate their own admin user |
| Assign role | SuperAdmin or `platform.admin_users.assign_role` | Target role must be active; non-SuperAdmin can assign only roles whose permissions are already within their own active-role permission ceiling |
| Remove role | SuperAdmin or `platform.admin_users.remove_role` | Backend remains authoritative even if a button is hidden or disabled |

The current create dialog accepts an existing `userId` because the backend create endpoint is `POST /api/admin/users { userId, isSuperAdmin }`. A later email search or invite flow may improve UX, but it must still resolve to an existing user and keep the same backend promotion rules.

### 4.5 Admin Roles `/admin/access/roles`

This page manages platform-admin roles. It is the permission-delegation screen for the admin control plane and must source available permissions from the registry permission catalog, not from hard-coded strings or broad namespace guesses.

Primary data sources:

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/admin/roles` | Lists roles with assigned permission summaries |
| GET | `/api/admin/registry/permission-catalog?audience=PLATFORM_ADMIN_ROLE_GRANTABLE` | Returns only platform-control permissions grantable to admin roles |
| POST | `/api/admin/roles` | Creates an admin role |
| PUT | `/api/admin/roles/:id` | Updates admin-role name and description |
| POST | `/api/admin/roles/:id/toggle-active` | Activates or deactivates an admin role |
| POST | `/api/admin/roles/:id/permissions` | Grants one registry permission to the role |
| DELETE | `/api/admin/roles/:id/permissions/:permId` | Revokes one registry permission from the role |

Current UX contract:

```
Admin Access / Roles
--------------------------------------------------------------
[New Admin Role] [Refresh]

[metric: roles] [metric: active] [metric: selected permissions]

Left index:
  searchable role list with status and permission count

Right detail:
  selected role name, description, active state
  edit metadata action
  activate/deactivate action
  permission matrix grouped by feature from PLATFORM_ADMIN_ROLE_GRANTABLE catalog
```

| Action | UI permission gate | Backend rule |
|--------|--------------------|--------------|
| Create role | SuperAdmin or `platform.roles.create` | Role is created as an admin-role entity only |
| Edit role metadata | SuperAdmin or `platform.roles.update` | Only name and description are editable |
| Toggle role active | SuperAdmin or `platform.roles.toggle_active` | Inactive roles cannot be newly assigned to users |
| Grant permission | SuperAdmin or `platform.roles.grant_permission` | Permission must belong to a platform-admin-role grantable feature; non-SuperAdmin can grant only permissions they already hold |
| Revoke permission | SuperAdmin or `platform.roles.revoke_permission` | Backend validates role and permission identity |

The permission matrix must show only `PLATFORM_ADMIN_ROLE_GRANTABLE` permissions. It must not include client-role permissions, B2B-delegatable permissions, public catalog flags, or plan-only controls. If the catalog endpoint is unavailable but the role list loads, the page may show the role's currently assigned permission list read-only; it must not synthesize a grant matrix.

### 4.6 Admin Access Detail Behavior

The current admin access UI keeps member-role assignment and role-permission management inside grouped index/detail pages instead of separate `/admin/users/:id` and `/admin/roles/:id` screens. Dedicated detail routes may be added later when they carry additional operational value, such as audit trails, effective permission diffs, or login/session history.

Until then, the grouped pages must still respect detail-level backend permissions when they call detail endpoints. A list response may include role and permission summaries needed for the visible workflow, but it must not become a shortcut for unrelated private user data or audit records.

---

### 4.7 Plans Group `/admin/plans/*`

**Requires:** `platform.plans.list`

The sidebar exposes Plans as a grouped billing area, not as one overloaded page. `/admin/plans` redirects to `/admin/plans/templates`.

Current child pages:

| Route | Purpose |
|-------|---------|
| `/admin/plans/templates` | Plan template list, selected plan summary, active state, included feature composition |
| `/admin/plans/features` | Plan-assignable feature matrix showing which plans include each feature and quota values per plan |

The Plans pages are operational management screens, not pricing landing pages and not feature registry duplicates. They are for platform admins who need to understand which sellable plan templates exist, whether a plan can currently be selected, which features and quota values are included in each template, and where feature coverage differs across plans.

Primary data sources:

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/admin/plans` | Plan template list |
| PATCH | `/api/admin/plans/:id/active?active=true|false` | Activate or deactivate a plan template when `platform.plans.toggle_active` is granted |
| GET | `/api/admin/plans/:id/features` | Included feature composition and configured quota values |
| GET | `/api/admin/registry/feature-catalog?audience=PLAN_ASSIGNABLE` | Plan-assignable feature definitions used to label included features and show assignable-but-not-included options |

Plan template structure:

```
Plan templates                             [Refresh]
─────────────────────────────────────────────────────────────────
Plan list                       Selected plan inspector
FREE     $0     7 features       PRO
PRO      $29.99 7 features       $29.99 / Monthly        [Deactivate]
ENT      $99.99 7 features

                                 Summary: included features, quota-configured features, status

                                 Composition list:
                                 Workspace
                                 platform.workspace
                                 Included     members 10  companies 5

                                 B2B Collaboration
                                 platform.b2b
                                 Included     no quota config
```

The plan list should be a selectable list, not a wide table. It shows plan code/name, price, billing cycle, active state, and feature count when `platform.plans.list_features` is available. Selecting a row updates the right-side detail without navigating away.

The selected plan header shows the plan name, code, price, billing cycle, and active state. The active toggle is rendered only when the current admin has `platform.plans.toggle_active`; otherwise the state is read-only. Deactivating a plan means clients should not be able to newly select/buy that plan template. It does not silently rewrite existing subscriptions.

Included features are shown as a composition list. Each row shows the business feature label, immutable feature code, add-on price, and plan quota config values. Quota config values are plan-specific; quota slot declarations still come from the feature definition. Unlimited quota is rendered explicitly when the backend sends `limit = null`.

Quota pricing must be presented as a quota policy, not as a generic "quota price". Each declared quota resource has an included limit and an optional extra-unit price:

- `limit = null` means unlimited for that plan; the extra-unit price is not applicable.
- `limit = number` and `pricePerUnit = null` means a fixed included limit; the quota cannot be self-service bumped.
- `limit = number` and `pricePerUnit = amount` means the plan includes the limit, and usage above that limit is billed per extra unit.

Admin labels should use **Included limit**, **Extra unit price**, **Fixed limit**, **Unlimited**, and **Paid expansion**. The UI must disable or clear extra-unit pricing when the included limit is blank/unlimited, because billing ignores extra-unit prices without a finite base limit.

The page must provide real management actions when permissions allow:

- `platform.plans.create`: create a new plan template using code, name, optional description, price, billing cycle, and a source plan to inherit from.
- `platform.plans.assign_feature`: add a plan-assignable feature to the selected plan.
- `platform.plans.update_feature`: edit add-on price and quota config values for an included feature.
- `platform.plans.remove_feature`: remove an included feature from the selected plan after confirmation.

New plan creation must not default to an empty composition. The create dialog should default the inheritance source to the FREE plan when present, otherwise to the first available plan. The backend also defaults to FREE inheritance when `inheritFromPlanId` is not provided and FREE exists. Inheritance copies the source plan's included features, add-on prices, and quota policies into the new plan template; admins can then adjust the copied composition explicitly.

Quota config forms are generated from the selected feature's declared quota schema. The UI must not allow arbitrary quota resource names that are not declared by the feature definition. The form should render one compact row per quota resource, with the quota policy badge next to the resource name and the two editable fields beside it.

The plan-feature matrix is the place to compare feature coverage across plan templates. It uses `/api/admin/registry/feature-catalog?audience=PLAN_ASSIGNABLE`, `/api/admin/plans`, and `/api/admin/plans/:id/features`. It must not show platform-control/internal-only features, deprecated non-assignable features, or permissions as assignable plan units.

Plan-feature matrix structure:

```
Plan features                              [Refresh]
─────────────────────────────────────────────────────────────────
[Search feature]

Workspace
platform.workspace       Permissions 2   Quotas 2
  FREE        Included   members 3   companies 1
  PRO         Included   members 10  companies 5
  ENTERPRISE  Included   members unlimited companies unlimited

B2B Collaboration
platform.b2b             Permissions 8   Quotas 0
  FREE        Included
  PRO         Included
  ENTERPRISE  Included
```

Avoid horizontally-scrolling comparison tables for this area. Use wrapping plan cells or stacked cells so the page remains usable on normal laptop widths and mobile screens.

Every write flow must validate against the backend feature registry rules. The UI may hide invalid options, but backend validators remain the authority.

---

### 4.9 Subscriptions `/admin/subscriptions`

**Requires:** `platform.admin.subscriptions.read`

```
Subscriptions          [Search by account email or ID: _________________]
─────────────────────────────────────────────────────────────────
┌──────────────────┬──────────┬──────────┬──────────┬──────────────────────┐
│ Account          │ Plan     │ Status   │ Price    │ Actions              │
├──────────────────┼──────────┼──────────┼──────────┼──────────────────────┤
│ acme@corp.io     │ PRO      │ ACTIVE   │ $49.00   │ [View] [Override]    │
│ startup@x.com    │ FREE     │ TRIALING │ $0.00    │ [View] [Assign Plan] │
└──────────────────┴──────────┴──────────┴──────────┴──────────────────────┘

[Override] = CanDo(P.ADMIN_SUBS_OVERRIDES)
[Assign Plan] = CanDo(P.ADMIN_SUBS_CREATE)
```

---

### 4.10 Subscription Detail `/admin/subscriptions/:accountId`

```
← Back

Subscription: acme@corp.io
─────────────────────────────────────────────────────────────────
Plan:   PRO       Status: ACTIVE        Price: $49.00/mo
                                 [Reassign Plan] ←CanDo(P.ADMIN_SUBS_CREATE)

CUSTOM OVERRIDES                  [Edit Overrides] ←CanDo(P.ADMIN_SUBS_OVERRIDES)
─────────────────────────────────────────────────────────────────
Added Features:  [b2b]  [custom_reports]
Quota Bumps:     members → 50  (plan default: 10)

EFFECTIVE FEATURE SET  (plan + overrides)
─────────────────────────────────────────────────────────────────
✓ workspace   ✓ rbac   ✓ staff   ✓ company
✓ subscription  ✓ b2b  ✓ custom_reports
```

**Edit Overrides drawer:**
```
Custom Overrides — acme@corp.io
────────────────────────────────────────
Added Features (beyond plan):
  [✓] b2b              [✓] custom_reports
  [ ] advanced_analytics

Quota Bumps:
  members   plan: 10   override: [50__]
  companies plan: 5    override: [____]  (blank = no override)

[Cancel]  [Save]
```

---

### 4.11 Features `/admin/features`

**Requires:** `platform.registry.feature_catalog`

The backend namespace is `platform.registry`, but the admin-facing label is **Features** or **Platform Features**. Do not call this page "Registry" in navigation, headings, empty states, or help text. Admins should understand features as platform capabilities that can affect plans, public catalogs, permissions, quotas, and B2B delegation.

Primary data source:

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/admin/registry/feature-catalog?audience=ALL` | Full feature read model for the Platform Features page |
| GET | `/api/admin/registry/permission-catalog?audience=PLATFORM_ADMIN_ROLE_GRANTABLE` | Admin-role permission picker source |
| GET | `/api/admin/registry/permission-catalog?audience=CLIENT_ROLE_GRANTABLE` | Client-role permission picker source |
| GET | `/api/admin/registry/permission-catalog?audience=B2B_DELEGATABLE` | B2B permission picker source |
| PATCH | `/api/admin/registry/features/:id/active?active=false` | Activation change for features whose code definition explicitly allows operations activation, only when `platform.registry.update_active` is granted |

The page must separate source-owned facts from managed state:

| Source-owned, not editable | Managed where permitted |
|---------------------------|-------------------------------|
| Feature code | Technical-operations activation for code-declared operations-toggleable features |
| Module code | Sort/display placement if supported |
| Feature surface | Plan inclusion from plan management |
| Permission ownership | Add-on price from plan management |
| Quota slot keys/types | Quota values from plan/subscription management |
| Plan/client/B2B grant eligibility | New catalog, plan, and client UI availability when technical operations deactivate an operations-toggleable feature |
| Lifecycle status (`PUBLIC`, `BETA`, `DEPRECATED`, `INTERNAL`) | |

Current Bootstrap structure:

```
Features
─────────────────────────────────────────────────────────────────
[Search features...]  Filter: [All] [Client features] [Admin features] [Plan eligible] [Catalog visible]

Feature list                       Selected feature inspector
Workspace                          Workspace
platform.workspace                  platform.workspace
Client Workspace  Public           Activation: fixed by code
Permissions 2  Quotas 2

                                  Availability:
                                  Plans yes, Public catalog yes, Client roles yes...

                                  Permissions:
                                  platform.workspace.read       read
                                  platform.workspace.delete     delete

                                  Quotas:
                                  members COUNT persons
                                  companies COUNT companies
```

The page should answer catalog questions, not expose raw implementation trivia:

- Can customers see this feature?
- Can platform admins include this feature in plans?
- Does this feature expose quotas that plans must configure?
- Which permissions come from this feature?
- Can these permissions be granted to platform admin roles, client roles, or B2B collaborators?

The page must not behave like the main dashboard. Do not add dashboard cards for client features, admin features, plan-eligible features, catalog-visible features, or warnings. The feature list and selected inspector already carry those dimensions.

Avoid horizontally-scrolling feature tables. Use a searchable feature list and a selected feature inspector. The inspector may show availability, permissions, quota schema, and the minimum source-owned contract needed to understand the selected feature. Do not fill the inspector with repeated architecture explanations or validation copy unless there is a real warning that needs operator attention.

Lifecycle and activation rules:

- `Feature.status` is code-owned lifecycle. Admin UI must display it but must not render it as an editable dropdown.
- `PUBLIC` means released for normal use on allowed surfaces when the feature is active.
- `BETA` means shipped by code as a beta lifecycle. Authorized technical operators may deactivate the feature only if code also declares `operationsActivationToggleable`, but they cannot promote it to `PUBLIC` from UI.
- `DEPRECATED` means code has marked it as legacy. Admins cannot newly assign deprecated features through plan/billing flows.
- `INTERNAL` means code-owned internal/control capability or non-catalog capability. It is not a synonym for a hidden catalog feature.
- `publicCatalogVisible = true` means the feature may appear in public/client catalog surfaces when active and in a released lifecycle.
- `operationsActivationToggleable = true` is the separate code-owned flag that permits operations activation editing. The backend allows this flag only on public-catalog-visible features.
- Authorized technical operators may toggle only `Feature.active` for features where `operationsActivationToggleable = true`. Inactive features are hidden from public/client catalogs and blocked from new billing/self-service configuration by backend validators.
- Public features without `operationsActivationToggleable` are shown as **required** or **fixed by code** and must not render an enabled activation switch. Non-catalog features are also fixed by code.
- Platform-shell features must render locked in the normal admin panel. `platform.company` is a required building block; `platform.b2b` is optional but still release/technical-operations controlled. If runtime suspension or a kill switch is added later, it belongs in a guarded technical operations surface with reason, expiry, audit, and explicit backend enforcement.

---

### 4.12 My Access `/admin/my-access`

**Shown to non-SuperAdmin only.**

```
My Access
─────────────────────────────────────────────────────────────────
Your admin roles:
  [Billing Ops]  [Support L1]

Your effective permissions:

▼ platform.admin.subscriptions
  ✓ read   ✓ create   ✓ update_overrides

▼ platform.plans
  ✓ list   ✓ list_features   ✗ create   ✗ toggle_active

▼ platform.registry
  ✓ read   ✗ update_active   ✗ catalog

(✗ = exists in namespace but not granted to you)
```

---

## 5. Client Panel

Base URL: `/app`
All routes: require `tokenType === "CLIENT"` in decoded JWT.

### 5.1 Layout

```
┌──────────────────────────────────────────────────────────────┐
│  HIVEAPP              [Workspace Name]    [avatar] [logout]   │
├───────────────┬──────────────────────────────────────────────┤
│  Sidebar      │  Main content                                │
│               │                                              │
│ ○ Dashboard   │                                              │
│ ○ Companies   │  — useHasNamespace("platform.company")       │
│ ○ Members     │  — useHasNamespace("platform.staff")         │
│ ○ Roles       │  — useHasNamespace("platform.rbac")          │
│ ○ Invitations │  — useHasNamespace("platform.invitations")   │
│ ○ Collabs     │  — useHasNamespace("platform.b2b")           │
│ ○ Subscription│  — P.SUB_READ                                │
└───────────────┴──────────────────────────────────────────────┘
```

**Owner:** `OWNER` chip next to avatar. `isOwner=true` → all actions rendered active.

---

### 5.2 Client Dashboard `/app`

```
Welcome back, Ali

┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Companies    │  │ Members      │  │ Active Roles │  │ Collabs      │
│    3 / 5     │  │   8 / 10    │  │      4       │  │     2        │
│ (quota badge)│  │ (quota badge)│  │              │  │ 1 pending    │
└──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘
[P.COMPANY_READ_ALL]  [P.STAFF_READ]  [P.RBAC_VIEW]    [P.B2B_VIEW]

┌──────────────────────────────────────────────────────┐
│ Subscription                                         │
│ Plan: PRO  •  Status: ACTIVE  •  $49/mo              │
│ Features: workspace ✓  rbac ✓  b2b ✓ ...            │
└──────────────────────────────────────────────────────┘
[P.SUB_READ]

QUOTA BARS
──────────────────────────────────────────────────────
Members   ████████░░  8 / 10   [+ Invite Member] ←CanDo(P.INVITATIONS_SEND)
Companies ██░░░░░░░░  3 / 5    [+ Add Company]   ←CanDo(P.COMPANY_CREATE)
```

Quota bars turn red at ≥ 90%. At 100%: button hard-disabled, tooltip `"Quota reached. Upgrade your plan."`

---

### 5.3 Companies `/app/companies`

**Requires:** `platform.company.read_all`

```
Companies                               [+ New Company] ←CanDo(P.COMPANY_CREATE)
─────────────────────────────────────────────────────────────────
┌────────────┬─────────────┬──────────┬─────────┬────────┐
│ Name       │ Industry    │ Country  │ Status  │ Actions│
├────────────┼─────────────┼──────────┼─────────┼────────┤
│ Acme Corp  │ Technology  │ DZ       │ ● Active│ ···    │
│ Beta Ltd   │ Finance     │ FR       │ ● Active│ ···    │
└────────────┴─────────────┴──────────┴─────────┴────────┘

Row actions:
- View details          — always
- Edit                  — CanDo(P.COMPANY_UPDATE, fallback="disable")
- Deactivate            — CanDo(P.COMPANY_DELETE, fallback="disable")
```

---

### 5.4 Members `/app/members`

**Requires:** `platform.staff.read`

```
Members                                  [+ Invite Member] ←CanDo(P.INVITATIONS_SEND)
─────────────────────────────────────────────────────────────────
Quota: 8 / 10   ████████░░

┌──────────────┬──────────────────┬──────────┬───────────────┬────────┐
│ Display Name │ Email            │ Status   │ Roles         │ Actions│
├──────────────┼──────────────────┼──────────┼───────────────┼────────┤
│ Ali Hassan   │ ali@acme.io      │ ● Active │ [HR][Manager] │ ···    │
│ Sara Karim   │ sara@acme.io     │ ● Active │ [OWNER ★]     │ ···    │
│ Bob Smith    │ bob@acme.io      │ ○ Off    │ —             │ ···    │
└──────────────┴──────────────────┴──────────┴───────────────┴────────┘
```

> **Note:** "+ Invite Member" opens the Invite drawer (sends email), NOT a direct creation form.
> After invite is sent, member appears in Members list only after they accept.

**Row action menu:**
- View Profile — always
- Edit display name — `CanDo(P.STAFF_UPDATE, fallback="disable")`
- Assign Role — `CanDo(P.STAFF_ASSIGN_ROLE, fallback="disable")`
- Remove Role — `CanDo(P.STAFF_REMOVE_ROLE, fallback="disable")`
- Grant/Deny Permission — `CanDo(P.STAFF_GRANT_PERM, fallback="disable")`
- Deactivate — `CanDo(P.STAFF_DELETE, fallback="disable")`

---

### 5.5 Member Detail `/app/members/:id`

```
← Back

[Avatar] Ali Hassan                      ● Active
ali@acme.io

                    [Edit Name] ←CanDo(P.STAFF_UPDATE)
                    [Deactivate] ←CanDo(P.STAFF_DELETE)

ASSIGNED ROLES                           [+ Assign Role] ←CanDo(P.STAFF_ASSIGN_ROLE)
─────────────────────────────────────────────────────────────────
┌──────────┬─────────────┬────────────────────┬──────────────────────────┐
│ Role     │ Company     │ Permissions (count)│                          │
├──────────┼─────────────┼────────────────────┼──────────────────────────┤
│ HR       │ Acme Corp   │ 5 permissions      │ [Remove] ←CanDo(P.STAFF_REMOVE_ROLE)│
│ Manager  │ (workspace) │ 8 permissions      │ [Remove] ←CanDo          │
└──────────┴─────────────┴────────────────────┴──────────────────────────┘

DIRECT PERMISSION OVERRIDES              [+ Add Override] ←CanDo(P.STAFF_GRANT_PERM)
─────────────────────────────────────────────────────────────────
Scope: [All Companies ▼]

┌───────────────────────────┬──────────┬────────────────────────────────┐
│ Permission                │ Decision │                                │
├───────────────────────────┼──────────┼────────────────────────────────┤
│ platform.company.delete   │ ✓ GRANT  │ [Revoke] ←CanDo(P.STAFF_REVOKE_PERM)│
│ platform.b2b.request      │ ✗ DENY   │ [Revoke] ←CanDo                │
└───────────────────────────┴──────────┴────────────────────────────────┘

Only shown if: CanDo(P.STAFF_READ_OVERRIDES)

EFFECTIVE PERMISSIONS  (roles union + overrides applied)
─────────────────────────────────────────────────────────────────
[expandable, grouped by namespace]
platform.company.*  ✓ create  ✓ read_all  ✓ read_single  ✓ update  ✓ delete (override)
platform.staff.*    ✓ read    ✓ add       ✗ delete (no role grant)
```

**"Add Override" drawer:**
```
Direct Permission Override
────────────────────────────────────────
Member:     Ali Hassan
Company:    [Acme Corp ▼]  (scope, required)

Permission: [searchable dropdown — full permission list from registry]
Decision:   [● GRANT  ○ DENY]

[Cancel]  [Save Override]
```

---

### 5.6 Roles `/app/roles`

**Requires:** `platform.rbac.view`

```
Roles                                    [+ New Role] ←CanDo(P.RBAC_CREATE)
─────────────────────────────────────────────────────────────────
Scope filter: [All ▼] [Workspace-wide] [Acme Corp ▼]

┌──────────┬──────────────┬─────────────┬──────────┬────────┐
│ Name     │ Scope        │ Permissions │ System   │ Actions│
├──────────┼──────────────┼─────────────┼──────────┼────────┤
│ HR       │ Acme Corp    │ 5           │          │ ···    │
│ Manager  │ Workspace    │ 8           │          │ ···    │
│ Owner    │ Workspace    │ (all)       │ ★ System │ view   │
└──────────┴──────────────┴─────────────┴──────────┴────────┘

System roles: view-only, no edit/delete.
```

---

### 5.7 Role Detail `/app/roles/:id`

```
← Back

HR Role — Acme Corp scope
          [Edit Name] ←CanDo(P.RBAC_UPDATE)  [Delete] ←CanDo(P.RBAC_DELETE)

PERMISSION BRICKS                        [+ Grant] ←CanDo(P.RBAC_GRANT)
──────────────────────────────────────────────────────────────────────────
▼ Staff (platform.staff.*)
  [✓] read           [✓] add          [✓] update
  [ ] delete         [ ] assign_role  [ ] remove_role
  [ ] grant_perm     [ ] revoke_perm  [ ] read_overrides

▼ Company (platform.company.*)
  [✓] read_all       [✓] read_single  [ ] create
  [ ] update         [ ] delete

▼ RBAC (platform.rbac.*)
  [✓] view           [ ] view_company [ ] create
  [ ] update         [ ] delete       [ ] grant   [ ] revoke

▼ B2B (platform.b2b.*)
  [ ] request        [ ] accept       [ ] revoke
  [ ] view           [ ] view_incoming
  [ ] grant_permission  [ ] revoke_permission

▼ Invitations (platform.invitations.*)
  [ ] send           [ ] list         [ ] revoke

▼ Subscription (platform.subscription.*)
  [ ] read

▼ Workspace (platform.workspace.*)
  [ ] read           [ ] delete

Each toggle = CanDo(P.RBAC_GRANT or P.RBAC_REVOKE, fallback="disable")
System roles: all toggles hard-disabled, banner "System roles are read-only"
```

---

### 5.8 Invitations `/app/invitations`

**Requires:** `platform.invitations.list`

```
Invitations                              [+ Invite Member] ←CanDo(P.INVITATIONS_SEND)
─────────────────────────────────────────────────────────────────
Status filter: [Pending ▼]

┌──────────────────┬─────────────────────┬───────────┬────────────────┬────────┐
│ Invited Email    │ Invited By          │ Role      │ Expires        │ Actions│
├──────────────────┼─────────────────────┼───────────┼────────────────┼────────┤
│ john@corp.io     │ Ali Hassan          │ HR        │ in 6 days      │ [Revoke]←CanDo│
│ mary@startup.com │ Sara Karim          │ —         │ in 3 days      │ [Revoke]←CanDo│
│ old@x.com        │ Ali Hassan          │ Manager   │ Expired        │ [Re-invite]←CanDo│
└──────────────────┴─────────────────────┴───────────┴────────────────┴────────┘

[Revoke] = CanDo(P.INVITATIONS_REVOKE, fallback="disable")
[Re-invite] on expired = revoke + send new. CanDo(P.INVITATIONS_SEND and P.INVITATIONS_REVOKE)
```

**"Invite Member" drawer:**
```
Invite to Workspace
────────────────────────────────────────
Email address:  [_______________________]

Assign role (optional):
  [dropdown — workspace roles]

Company scope (optional):
  [dropdown — workspace companies]

─────────────────────────────────────────
An email with an accept link will be sent.
The link expires in 7 days.

[Cancel]  [Send Invite →]
```

---

### 5.9 Public: Accept Invitation `/invite/accept?token=...`

**No auth required. Separate public route outside `/app`.**

**Step 1 — token validation (GET /api/v1/invitations/validate?token=...):**
```
You've been invited to join
──────────────────────────────────────────────────
Workspace:   Acme Corp
Invited by:  Ali Hassan
Expires:     May 9, 2026

[Continue →]
```
If token invalid/expired/revoked: show error card with explanation, no continue button.

**Step 2a — Existing user (requiresRegistration = false):**
```
Welcome back
──────────────────────────────────────────────────
You're already on HiveApp.
Click below to join Acme Corp with your existing account.

[Join Workspace →]
```
Calls POST /api/v1/invitations/accept with just `{ token }`, gets JWT back, redirects to `/app`.

**Step 2b — New user (requiresRegistration = true):**
```
Create your account
──────────────────────────────────────────────────
Email:      ali@acme.io  (pre-filled, read-only)

First name: [___________]
Last name:  [___________]
Password:   [___________]  (min 8 chars)

[Create Account & Join →]
```
Calls POST /api/v1/invitations/accept with `{ token, firstName, lastName, password }`.
On success: JWT issued, redirect to `/app`.

---

### 5.10 B2B Collaborations `/app/collaborations`

**Requires:** `platform.b2b.view`

```
Collaborations
─────────────────────────────────────────────────────────────────
Tabs: [Outgoing (we are client)] [Incoming (we are provider)]

OUTGOING                                [+ Request Collaboration] ←CanDo(P.B2B_REQUEST)
─────────────────────────────────────────────────────────────────
┌──────────────────┬──────────────┬──────────┬────────────────────────────┐
│ Provider Company │ Provider Acct│ Status   │ Actions                    │
├──────────────────┼──────────────┼──────────┼────────────────────────────┤
│ Beta Ltd         │ beta.io      │ ACTIVE   │ [Revoke] ←CanDo(P.B2B_REVOKE)│
│ Gamma Inc        │ gamma.io     │ PENDING  │ [Revoke] ←CanDo            │
└──────────────────┴──────────────┴──────────┴────────────────────────────┘

INCOMING  (requires P.B2B_VIEW_INCOMING to render this tab)
─────────────────────────────────────────────────────────────────
┌──────────────────┬──────────────┬──────────┬──────────────────────────────────┐
│ Client Company   │ Client Acct  │ Status   │ Actions                          │
├──────────────────┼──────────────┼──────────┼──────────────────────────────────┤
│ Delta Corp       │ delta.io     │ PENDING  │ [Accept] ←CanDo(P.B2B_ACCEPT)    │
│                  │              │          │ [Revoke] ←CanDo(P.B2B_REVOKE)    │
│ Echo Ltd         │ echo.io      │ ACTIVE   │ [Manage Perms]  [Revoke] ←CanDo  │
└──────────────────┴──────────────┴──────────┴──────────────────────────────────┘
```

**Manage Permissions drawer (provider side):**
```
B2B Permissions — Delta Corp
────────────────────────────────────────
Grant Delta Corp's members access to your resources:

▼ Staff
  [ ] platform.staff.read
▼ Company
  [✓] platform.company.read_all
  [ ] platform.company.read_single
▼ RBAC
  [ ] platform.rbac.view

Each toggle: CanDo(P.B2B_GRANT_PERM or P.B2B_REVOKE_PERM, fallback="disable")
[Cancel]  [Save]
```

---

### 5.11 Subscription `/app/subscription`

**Requires:** `platform.subscription.read`

```
Your Subscription
─────────────────────────────────────────────────────────────────
Plan:    PRO                     Status: ● ACTIVE
Price:   $49.00/month            Billing: MONTHLY

PLAN OPTIONS
─────────────────────────────────────────────────────────────────
FREE          $0/month       Current plan
PRO           $49/month      [Preview]
ENTERPRISE    Contact sales   [Preview]

INCLUDED FEATURES
─────────────────────────────────────────────────────────────────
✓ Workspace Management     Members: 10  Companies: 5
✓ Company Management
✓ Team Members (Staff)
✓ Role Management (RBAC)
✓ Subscription View
✓ B2B Collaborations       (+$9.99 add-on)

QUOTA USAGE
─────────────────────────────────────────────────────────────────
Members    ████████░░  8 / 10
Companies  ██░░░░░░░░  3 / 5

PREVIEW PANEL
─────────────────────────────────────────────────────────────────
Target Plan: PRO
Add-ons:     B2B Collaborations [x]
Quota:       Members [10__]   Companies [5__]
New Price:   $58.99/month
Conflicts:   none

[Apply change]
```

The page must call `GET /api/v1/subscriptions/catalog` to render plan options, feature rows, add-on prices, quota limits, selected overrides, and current usage. Changing a plan, add-on, or quota input calls `POST /api/v1/subscriptions/preview`; the preview response is the source of truth for effective features, quota limits, calculated price, and conflicts. `POST /api/v1/subscriptions/apply` is enabled only when preview returns `immediateAllowed=true`.

If preview returns `QUOTA_BELOW_USAGE` or `FEATURE_IN_USE`, the relevant control is disabled or marked with the returned message and apply is unavailable. The current backend applies changes immediately with internal confirmation; checkout, invoices, proration, and scheduled downgrades are not part of this screen until those backend flows exist.

---

## 6. Cross-cutting UX Patterns

### 6.1 403 Handling

When any mutation returns 403:
1. Roll back optimistic update
2. Re-fetch `GET /api/v1/me/permissions` (permissions may have changed mid-session)
3. Toast: `"Permission denied. Your access may have changed."`
4. If entire page namespace is now empty, redirect to dashboard with banner

### 6.2 Permission-loading skeleton

While `isLoaded = false`, render content as skeleton. Never flash content that may be unauthorized.

```tsx
if (!auth.isLoaded) return <PageSkeleton />
```

### 6.3 Empty state vs. hidden state

- **Hidden** — nav item removed entirely, user doesn't know feature exists
- **Empty state** — user CAN read but zero data exists yet. Show illustrated empty state + CTA (itself permission-gated)

```
No roles yet.
[+ Create your first role]  ← CanDo(P.RBAC_CREATE, fallback="hide")
```

### 6.4 Quota-exhausted state

```tsx
const atQuota = memberCount >= memberQuota
<Button
  disabled={!canInvite || atQuota}
  title={atQuota ? "Member quota reached. Upgrade your plan." : undefined}
>
  + Invite Member
</Button>
```

### 6.5 SuperAdmin vs. regular admin

| Element | SuperAdmin | Regular Admin |
|---|---|---|
| Avatar badge | Gold `SUPER` chip | None |
| Nav items | All always visible | Only namespaces with ≥1 perm |
| Permission matrix toggles | Always active | Disabled if lacking grant/revoke |
| Dashboard | Full global stats | Filtered to visible domains |
| My Access page | Hidden (not needed) | Shown |

### 6.6 Workspace owner vs. member

| Element | Owner | Member |
|---|---|---|
| Avatar badge | `OWNER` chip | None |
| All action buttons | Always active | Controlled by roles |
| Invite Members | Always | Requires `platform.invitations.send` |
| Manage Roles | Always | Requires `platform.rbac.*` |
| Manage B2B | Always | Requires `platform.b2b.*` |

### 6.7 Invitation status badges

| Status | Badge |
|--------|-------|
| PENDING | Blue `Pending` |
| ACCEPTED | Green `Accepted` |
| EXPIRED | Gray `Expired` |
| REVOKED | Red `Revoked` |

---

## 7. Routing Structure

```
/                        ← redirect to /app or /admin based on tokenType
/invite/accept           ← PUBLIC — invitation accept flow (no auth)

/admin
  /login                 ← AdminAuthController
  /                      ← Dashboard (context-aware)
  /my-access             ← non-SuperAdmin only
  /access                ← redirects to /admin/access/members
  /access/members        ← requires platform.admin_users.read
  /access/roles          ← requires platform.roles.read
  /plans                 ← requires platform.plans.list
  /plans/:id
  /subscriptions         ← requires platform.admin.subscriptions.read
  /subscriptions/:accountId
  /features              ← requires platform.registry.feature_catalog

/app
  /login                 ← AuthController
  /register              ← AuthController (register → auto-provision)
  /                      ← Dashboard
  /companies             ← requires platform.company.read_all
  /companies/:id
  /members               ← requires platform.staff.read
  /members/:id
  /roles                 ← requires platform.rbac.view
  /roles/:id
  /invitations           ← requires platform.invitations.list
  /collaborations        ← requires platform.b2b.view
  /subscription          ← requires platform.subscription.read
```

Every route guard: insufficient namespace → redirect to dashboard, never hard 403 wall.

---

## 8. Backend Endpoints Not Yet Built

These two endpoints are **required before the permission-aware UI can function.**
The rest of the backend is complete.

### `GET /api/v1/me/permissions`
**Auth:** CLIENT JWT
**Response:**
```json
{
  "memberId": "uuid",
  "isOwner": true,
  "permissions": [
    "platform.staff.read",
    "platform.staff.add",
    "platform.company.read_all",
    "..."
  ]
}
```
Logic: if `member.isOwner` → return all 40 client permission paths. Otherwise: union of all role-granted permissions + whitelist overrides − blacklist overrides for current account context.

### `GET /api/admin/me`
**Auth:** ADMIN JWT
**Response:**
```json
{
  "id": "uuid",
  "email": "admin@hiveapp.com",
  "isSuperAdmin": true,
  "isActive": true,
  "permissions": [
    "platform.admin_users.read",
    "platform.roles.read",
    "..."
  ]
}
```
Logic: if `isSuperAdmin` → return all 22 admin permission paths. Otherwise: union of all AdminRole-granted AdminPermission codes.
