# HiveApp — Enterprise UI/UX Specification
> Version 2.0 — updated to match backend v2 (62 permission bricks, invitation flow, /me endpoints)

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

---

## 1. Technology Stack

| Concern | Choice | Why |
|---|---|---|
| Framework | React 19 + TypeScript | Concurrent features, strict typing |
| Routing | TanStack Router | File-based, type-safe params, beforeLoad guards |
| Server state | TanStack Query v5 | Stale-while-revalidate, optimistic mutations |
| Client state | Zustand | Auth slice, permission set slice |
| Component primitives | shadcn/ui (Radix UI + Tailwind) | Accessible, unstyled-first, composable |
| Forms | React Hook Form + Zod | Schema-driven, zero re-renders |
| Table | TanStack Table v8 | Virtual, sortable, filterable, server-side |
| Notifications | Sonner | Toast stack |
| Icons | Lucide React | Consistent, tree-shakeable |
| Date | date-fns | Lightweight |
| Charts | Recharts | Composable, Tailwind-friendly |
| HTTP client | ky | Tiny fetch wrapper with interceptors |

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

  // ── Admin: Users ─────────────────────────────────────────
  ADMIN_USERS_READ:            "platform.admin.users.read",
  ADMIN_USERS_CREATE:          "platform.admin.users.create",
  ADMIN_USERS_TOGGLE:          "platform.admin.users.toggle_active",
  ADMIN_USERS_ASSIGN_ROLE:     "platform.admin.users.assign_role",
  ADMIN_USERS_REMOVE_ROLE:     "platform.admin.users.remove_role",

  // ── Admin: Roles ─────────────────────────────────────────
  ADMIN_ROLES_READ:            "platform.admin.roles.read",
  ADMIN_ROLES_CREATE:          "platform.admin.roles.create",
  ADMIN_ROLES_UPDATE:          "platform.admin.roles.update",
  ADMIN_ROLES_TOGGLE:          "platform.admin.roles.toggle_active",
  ADMIN_ROLES_GRANT_PERM:      "platform.admin.roles.grant_permission",
  ADMIN_ROLES_REVOKE_PERM:     "platform.admin.roles.revoke_permission",

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
  REGISTRY_UPDATE_STATUS:      "platform.registry.update_status",

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

> ⚠️ These two endpoints are **not yet implemented** in the backend. They must be built before the UI auth flow works. See Section 8.

### 3.3 Client endpoints

| Method | Path | Permission required |
|--------|------|---------------------|
| GET | `/api/v1/accounts/me` | authenticated |
| DELETE | `/api/v1/accounts/me` | `platform.workspace.delete` |
| GET | `/api/v1/plans` | — (public) |
| GET | `/api/v1/subscriptions/me` | `platform.subscription.read` |
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
| GET | `/api/admin/users` | `platform.admin.users.read` |
| GET | `/api/admin/users/:id` | `platform.admin.users.read` |
| POST | `/api/admin/users` | `platform.admin.users.create` |
| POST | `/api/admin/users/:id/toggle-active` | `platform.admin.users.toggle_active` |
| POST | `/api/admin/users/:id/roles` | `platform.admin.users.assign_role` |
| DELETE | `/api/admin/users/:id/roles/:roleId` | `platform.admin.users.remove_role` |
| GET | `/api/admin/roles` | `platform.admin.roles.read` |
| GET | `/api/admin/roles/:id` | `platform.admin.roles.read` |
| POST | `/api/admin/roles` | `platform.admin.roles.create` |
| PUT | `/api/admin/roles/:id` | `platform.admin.roles.update` |
| POST | `/api/admin/roles/:id/toggle-active` | `platform.admin.roles.toggle_active` |
| POST | `/api/admin/roles/:id/permissions` | `platform.admin.roles.grant_permission` |
| DELETE | `/api/admin/roles/:id/permissions/:permId` | `platform.admin.roles.revoke_permission` |
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
| GET | `/api/admin/registry/inventory` | `platform.registry.read` |
| GET | `/api/admin/registry/catalog` | `platform.registry.catalog` |
| PATCH | `/api/admin/registry/features/:id/status` | `platform.registry.update_status` |

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
│ ○ Users       │  — only if has platform.admin.users.*        │
│ ○ Roles       │  — only if has platform.admin.roles.*        │
│ ○ Plans       │  — only if has platform.plans.*              │
│ ○ Subscriptions│ — only if has platform.admin.subscriptions.*│
│ ○ Registry    │  — only if has platform.registry.*           │
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

### 4.3 Admin Users `/admin/users`

**Requires:** `platform.admin.users.read`

```
Admin Users                              [+ Promote User] ←CanDo(P.ADMIN_USERS_CREATE)
─────────────────────────────────────────────────────────────────────────
Search: [______________]   Status: [All ▼]

┌──────┬──────────────────┬──────────┬───────────────────┬───────┬────────┐
│ Name │ Email            │ Status   │ Roles             │ Super │ Actions│
├──────┼──────────────────┼──────────┼───────────────────┼───────┼────────┤
│ Ali  │ ali@hiveapp.com  │ ● Active │ [Billing][Support]│       │ ···    │
│ Sara │ sara@hive.com    │ ○ Inactive│ [Super Admin]    │ ★     │ ···    │
└──────┴──────────────────┴──────────┴───────────────────┴───────┴────────┘
```

**Row action menu (···):**
- View details — always
- Toggle Active/Inactive — `CanDo(P.ADMIN_USERS_TOGGLE, fallback="disable")`
- Assign Role — `CanDo(P.ADMIN_USERS_ASSIGN_ROLE, fallback="disable")`
- Remove Role (inline per role) — `CanDo(P.ADMIN_USERS_REMOVE_ROLE, fallback="disable")`

**"Promote User" drawer:**
```
Promote to Admin
────────────────────────────────────────
Search existing platform user by email:
[___________________________]
[user card: avatar / name / email]

Super Admin?  [ Toggle — OFF by default ]
  ⚠ Super Admins bypass all permission checks

[Cancel]  [Promote →]
```

---

### 4.4 Admin User Detail `/admin/users/:id`

```
← Back to Users

┌──────────────────────────────────────────────────────────────┐
│  [Avatar]  Ali Hassan                         ● Active        │
│  ali@hiveapp.com                                             │
│  User ID: 550e8400-...                                       │
│                                     [Toggle Active] ←CanDo   │
└──────────────────────────────────────────────────────────────┘

ASSIGNED ROLES                               [+ Assign Role] ←CanDo
─────────────────────────────────────────────────────────────────
┌─────────────┬──────────┬───────────────────────────────────────┐
│ Role        │ Status   │                                       │
├─────────────┼──────────┼───────────────────────────────────────┤
│ Billing Ops │ ● Active │ [Remove] ←CanDo(P.ADMIN_USERS_REMOVE_ROLE) │
│ Support L1  │ ● Active │ [Remove] ←CanDo                       │
└─────────────┴──────────┴───────────────────────────────────────┘

EFFECTIVE PERMISSIONS  (union of all assigned roles)
─────────────────────────────────────────────────────────────────
platform.admin.subscriptions.read
platform.admin.subscriptions.create
platform.plans.list
... (expandable tree, grouped by namespace)
```

---

### 4.5 Admin Roles `/admin/roles`

**Requires:** `platform.admin.roles.read`

```
Admin Roles                              [+ New Role] ←CanDo(P.ADMIN_ROLES_CREATE)
─────────────────────────────────────────────────────────────────
┌────────────────┬─────────────┬──────────────┬─────────┬────────┐
│ Name           │ Description │ Permissions  │ Status  │ Actions│
├────────────────┼─────────────┼──────────────┼─────────┼────────┤
│ Billing Ops    │ Manage subs │ 4            │ ● Active│ ···    │
│ Support L1     │ Read-only   │ 2            │ ● Active│ ···    │
│ Registry Mgr   │ Catalog ops │ 3            │ ○ Off   │ ···    │
└────────────────┴─────────────┴──────────────┴─────────┴────────┘
```

Row actions: Edit name/description `CanDo(P.ADMIN_ROLES_UPDATE)`, Toggle Active `CanDo(P.ADMIN_ROLES_TOGGLE)`, Manage Permissions `CanDo(P.ADMIN_ROLES_GRANT_PERM or P.ADMIN_ROLES_REVOKE_PERM)`

---

### 4.6 Admin Role Detail `/admin/roles/:id`

**The cornerstone screen.** A non-super admin who has `roles.read` but not `roles.grant_permission` sees this with all toggles grayed out.

```
← Back to Roles

Billing Ops                  ● Active
"Manage billing and subscription operations"
                      [Edit] ←CanDo(P.ADMIN_ROLES_UPDATE)
                      [Toggle Active] ←CanDo(P.ADMIN_ROLES_TOGGLE)

PERMISSION MATRIX
─────────────────────────────────────────────────────────────────
Toggle each permission on/off.
Grayed = you lack grant/revoke permission yourself.
─────────────────────────────────────────────────────────────────

▼ Admin: Subscriptions
  [✓] read               — View account subscription
  [✓] create             — Manually assign a plan
  [✓] update_overrides   — Apply custom overrides

▼ Admin: Users
  [ ] read               [ ] create         [ ] toggle_active
  [ ] assign_role        [ ] remove_role

▼ Admin: Roles
  [ ] read               [ ] create         [ ] update
  [ ] toggle_active      [ ] grant_permission  [ ] revoke_permission

▼ Plans
  [✓] list               [✓] list_features
  [ ] create             [ ] assign_feature
  [ ] toggle_active      [ ] update_feature   [ ] remove_feature

▼ Registry
  [✓] read               [ ] catalog        [ ] update_status

Each toggle calls POST/DELETE /api/admin/roles/:id/permissions
CanDo wrapper on every toggle: disabled if user lacks grant_permission or revoke_permission
```

---

### 4.7 Plans `/admin/plans`

**Requires:** `platform.plans.list`

```
Plans                                    [+ New Plan] ←CanDo(P.PLANS_CREATE)
─────────────────────────────────────────────────────────────────
┌───────┬──────┬──────────┬────────────┬──────────┬─────────┐
│ Code  │ Name │ Price    │ Billing    │ Features │ Status  │
├───────┼──────┼──────────┼────────────┼──────────┼─────────┤
│ FREE  │ Free │ $0       │ FOREVER    │ 2        │ ● Active│
│ PRO   │ Pro  │ $49/mo   │ MONTHLY    │ 6        │ ● Active│
│ ENT   │ Ent. │ $199/mo  │ MONTHLY    │ 12       │ ● Active│
└───────┴──────┴──────────┴────────────┴──────────┴─────────┘
```

---

### 4.8 Plan Detail `/admin/plans/:id`

```
← Back to Plans

PRO Plan — $49.00/month                  ● Active
                            [Toggle Active] ←CanDo(P.PLANS_TOGGLE)

FEATURES                     [+ Assign Feature] ←CanDo(P.PLANS_ASSIGN_FEATURE)
──────────────────────────────────────────────────────────────────────────────
┌──────────────┬────────────┬──────────────────────┬──────────┬─────────────┐
│ Feature Code │ Add-on $   │ Quota Configs        │          │             │
├──────────────┼────────────┼──────────────────────┼──────────┼─────────────┤
│ workspace    │ —          │ members: 10          │ [Edit]   │ [Remove]    │
│              │            │ companies: 5         │ ←CanDo   │ ←CanDo      │
│ rbac         │ —          │ —                    │ [Edit]   │ [Remove]    │
│ staff        │ —          │ —                    │ [Edit]   │ [Remove]    │
│ company      │ —          │ —                    │ [Edit]   │ [Remove]    │
│ subscription │ —          │ —                    │ [Edit]   │ [Remove]    │
│ b2b          │ $9.99      │ —                    │ [Edit]   │ [Remove]    │
└──────────────┴────────────┴──────────────────────┴──────────┴─────────────┘

[Edit] = CanDo(P.PLANS_UPDATE_FEATURE, fallback="disable")
[Remove] = CanDo(P.PLANS_REMOVE_FEATURE, fallback="disable")
```

**Assign Feature drawer:**
```
Assign Feature to PRO
──────────────────────────────────────
Feature: [dropdown — from registry catalog]
Add-on price: [$___.__]  (optional)
Quota configs:
  + Add quota slot
  [slot: members | limit: 10]
  [slot: companies | limit: 5]

[Cancel]  [Assign]
```

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

### 4.11 Registry `/admin/registry`

**Requires:** `platform.registry.read`

```
Platform Registry
─────────────────────────────────────────────────────────────────
Tabs: [Inventory (full)] [Public Catalog]

INVENTORY
▼ Core Module
  ├─ workspace     INTERNAL  [→ PUBLIC] ←CanDo(P.REGISTRY_UPDATE_STATUS)
  ├─ company       PUBLIC    [→ BETA]   [→ INTERNAL]  ←CanDo
  └─ subscription  PUBLIC

▼ Collaboration Module
  ├─ b2b           BETA      [→ PUBLIC] [→ INTERNAL]  ←CanDo
  └─ ...

Status chip colors:
  PUBLIC   = green badge
  BETA     = amber badge
  INTERNAL = gray badge
```

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
  ✓ read   ✗ update_status   ✗ catalog

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

[Upgrade Plan →]   ← links to pricing page / contact sales
```

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
  /users                 ← requires platform.admin.users.read
  /users/:id
  /roles                 ← requires platform.admin.roles.read
  /roles/:id
  /plans                 ← requires platform.plans.list
  /plans/:id
  /subscriptions         ← requires platform.admin.subscriptions.read
  /subscriptions/:accountId
  /registry              ← requires platform.registry.read

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
    "platform.admin.users.read",
    "platform.admin.roles.read",
    "..."
  ]
}
```
Logic: if `isSuperAdmin` → return all 22 admin permission paths. Otherwise: union of all AdminRole-granted AdminPermission codes.
