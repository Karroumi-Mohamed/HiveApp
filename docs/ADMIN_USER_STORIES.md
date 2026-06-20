# HiveApp — Admin User Stories

All stories are scoped to the **Admin Panel** (`/admin`). Every protected story maps to a real backend endpoint and a Permissionizer-discovered permission persisted in the unified registry `permissions` table. SuperAdmin bypass applies globally — a SuperAdmin never needs a role to act.

---

## Actors

| Actor | Description |
|-------|-------------|
| **SuperAdmin** | `isSuperAdmin = true`. Bypasses all permission checks. Full access to everything. |
| **Non-Super Admin** | Has an `AdminUser` record. Access is determined by assigned `AdminRole` → `AdminRolePermission` → registry `Permission` chains. |

---

## 1. Authentication

| # | Story | Endpoint | Permission |
|---|-------|----------|------------|
| A-01 | As an admin, I can log in with email and password and receive an ADMIN JWT | `POST /api/admin/auth/login` | — (public) |
| A-02 | As an admin, I can fetch my own profile, isSuperAdmin flag, and flat permission list so the UI can render permission-aware screens | `GET /api/admin/me` | ADMIN JWT |

---

## 2. Admin User Management

**Requires namespace:** `platform.admin_users`

| # | Story | Endpoint | Permission |
|---|-------|----------|------------|
| U-01 | As an admin, I can list all platform admin users with their email, roles, and active status | `GET /api/admin/users` | `platform.admin_users.read` |
| U-02 | As an admin, I can view a single admin user's full details including their assigned roles | `GET /api/admin/users/:id` | `platform.admin_users.read` |
| U-03 | As a SuperAdmin, I can promote an existing platform user to admin, optionally granting SuperAdmin status | `POST /api/admin/users` | `platform.admin_users.create` |
| U-04 | As an admin, I can toggle another admin user's active status (activate or deactivate) | `POST /api/admin/users/:id/toggle-active` | `platform.admin_users.toggle_active` |
| U-05 | As an admin, I can assign an admin role to an admin user | `POST /api/admin/users/:id/roles` | `platform.admin_users.assign_role` |
| U-06 | As an admin, I can remove an admin role from an admin user | `DELETE /api/admin/users/:id/roles/:roleId` | `platform.admin_users.remove_role` |

**Constraints:**
- An admin cannot deactivate themselves
- SuperAdmin status can only be granted at creation time (no promotion endpoint)
- Assigning a role that is inactive is still permitted — the role's inactivity only affects permission resolution

---

## 3. Admin Role Management

**Requires namespace:** `platform.roles`

| # | Story | Endpoint | Permission |
|---|-------|----------|------------|
| R-01 | As an admin, I can list all admin roles with their name, description, permission count, and active status | `GET /api/admin/roles` | `platform.roles.read` |
| R-02 | As an admin, I can view a single admin role's full permission matrix | `GET /api/admin/roles/:id` | `platform.roles.read` |
| R-03 | As an admin, I can create a new admin role with a name and description | `POST /api/admin/roles` | `platform.roles.create` |
| R-04 | As an admin, I can update an admin role's name or description | `PUT /api/admin/roles/:id` | `platform.roles.update` |
| R-05 | As an admin, I can toggle an admin role active or inactive | `POST /api/admin/roles/:id/toggle-active` | `platform.roles.toggle_active` |
| R-06 | As an admin, I can grant a specific admin permission to a role | `POST /api/admin/roles/:id/permissions` | `platform.roles.grant_permission` |
| R-07 | As an admin, I can revoke a specific admin permission from a role | `DELETE /api/admin/roles/:id/permissions/:permId` | `platform.roles.revoke_permission` |

**Constraints:**
- An admin who has `roles.read` but not `roles.grant_permission` can VIEW the permission matrix but cannot toggle any permission
- Deactivating a role immediately removes its effect on all users who hold it — no permission is granted from an inactive role
- Admin role grants are validated by feature surface: only permissions owned by `PLATFORM_CONTROL` features may be granted to platform admin roles.
- A separate self-escalation guard, such as "an admin cannot grant a permission they do not hold themselves unless SuperAdmin", is still a hardening requirement and must be covered by tests before the admin role UI is treated as production-safe.

---

## 4. Plan Management

**Requires namespace:** `platform.plans`

| # | Story | Endpoint | Permission |
|---|-------|----------|------------|
| P-01 | As an admin, I can list all plans including inactive ones (admins see everything, clients only see active) | `GET /api/admin/plans` | `platform.plans.list` |
| P-02 | As an admin, I can create a new plan with a code, name, price, and billing cycle | `POST /api/admin/plans` | `platform.plans.create` |
| P-03 | As an admin, I can activate or deactivate a plan (inactive plans cannot be assigned to new accounts) | `PATCH /api/admin/plans/:id/active` | `platform.plans.toggle_active` |
| P-04 | As an admin, I can view the features assigned to a specific plan with their quota configs | `GET /api/admin/plans/:id/features` | `platform.plans.list_features` |
| P-05 | As an admin, I can assign a feature to a plan, optionally with an add-on price and quota slot limits | `POST /api/admin/plans/:id/features` | `platform.plans.assign_feature` |
| P-06 | As an admin, I can update a plan feature's add-on price or quota slot configuration | `PUT /api/admin/plans/:id/features/:fid` | `platform.plans.update_feature` |
| P-07 | As an admin, I can remove a feature from a plan | `DELETE /api/admin/plans/:id/features/:fid` | `platform.plans.remove_feature` |

**Constraints:**
- `BillingCycle.FOREVER` is reserved exclusively for the FREE plan — any other plan must use MONTHLY or YEARLY
- Deactivating a plan does not affect existing active subscriptions on that plan
- Only features with status `PUBLIC` or `BETA` should be assignable to plans (INTERNAL features are platform-internal only)
- Client subscription self-service will move active subscriptions toward an explicit snapshot model, documented in `docs/PLAN_CLIENT_SUBSCRIPTION_SELF_SERVICE.md`. Until that model is implemented, plan-feature edits should be treated carefully because runtime entitlement still reads the active subscription's plan composition.

---

## 5. Subscription Management

**Requires namespace:** `platform.subscriptions`

| # | Story | Endpoint | Permission |
|---|-------|----------|------------|
| S-01 | As an admin, I can look up a client account's active subscription by account ID, seeing plan, status, price, and custom overrides | `GET /api/admin/subscriptions/account/:id` | `platform.subscriptions.read` |
| S-02 | As an admin, I can manually assign a plan to a client account (creates or replaces their subscription) | `POST /api/admin/subscriptions/account/:id` | `platform.subscriptions.create` |
| S-03 | As an admin, I can apply custom overrides to a subscription to add features beyond the plan or bump quota limits for a specific account | `PATCH /api/admin/subscriptions/account/:id/overrides` | `platform.subscriptions.update_overrides` |

**Constraints:**
- Subscription lookup is by `accountId` — the admin must know the account ID (sourced from the Subscriptions search page)
- Custom overrides stack on top of the plan — they do not replace the plan's features in the current backend
- Restrictive overrides are planned but not implemented as an enforced entitlement rule yet
- Reassigning a plan (`S-02`) creates a new subscription record, the previous one is superseded

---

## 6. Registry

**Requires namespace:** `platform.registry`

| # | Story | Endpoint | Permission |
|---|-------|----------|------------|
| REG-01 | As an admin, I can view the full internal inventory of all modules and features including INTERNAL ones not visible to clients | `GET /api/admin/registry/inventory` | `platform.registry.read` |
| REG-02 | As an admin, I can view the public-facing feature catalog (PUBLIC and BETA features only) | `GET /api/admin/registry/catalog` | `platform.registry.catalog` |
| REG-03 | As an admin, I can change a feature's visibility status (INTERNAL → BETA → PUBLIC or back) to control what appears in plan assignment dropdowns and client-visible catalogs | `PATCH /api/admin/registry/features/:id/status` | `platform.registry.update_status` |

**Constraints:**
- Modules and features are created automatically by `FeatureSeeder` from code-owned `FeatureDefinition` declarations. `PermissionSeeder` then links Permissionizer-discovered action permissions to those features.
- Setting a feature to INTERNAL hides it from plan assignment and client catalogs but does not remove it from existing plans
- DEPRECATED status exists as a terminal state — deprecated features remain on existing plans but cannot be assigned to new ones

---

## 7. What Does NOT Exist (by design)

| Missing action | Reason |
|----------------|--------|
| Browse all client accounts as a list | No endpoint. Admin reaches accounts via subscription lookup by account ID |
| Create a client account | Accounts are self-provisioned by client registration. Admin cannot create them |
| Delete a client account | No delete endpoint. Deactivation is client-side only (`DELETE /api/v1/accounts/me`) |
| View client business data (companies, members, roles) | Strict isolation — admin namespace cannot touch client ERP data |
| Invite admin users by email | Not implemented. Admin creation requires an existing User ID (`POST /api/admin/users` takes `userId`) |
| Password reset for admins | Not implemented |
| Audit log | Not implemented |

---

## 8. Permission Sieve Order (reminder)

```
Request arrives
     │
     ▼
1. AdminPermissionPolicy
   ├─ isSuperAdmin? → GRANTED (stop)
   └─ traverse AdminUserRole → AdminRole → AdminRolePermission → Permission.code
        ├─ code matches? → GRANTED
        └─ no match → ABSTAIN (not DENIED — falls through)
     │
     ▼
2–4. Client policies (B2bCollaborationPolicy, PlanPolicy, UserRolePolicy)
     — only reached if AdminPermissionPolicy abstains, which means
       the token is a CLIENT token, not ADMIN.
       ADMIN tokens that reach here without a match → 403.
```

---

## 9. Endpoint Reference (complete admin surface)

```
POST   /api/admin/auth/login
GET    /api/admin/me

GET    /api/admin/users
GET    /api/admin/users/:id
POST   /api/admin/users
POST   /api/admin/users/:id/toggle-active
POST   /api/admin/users/:id/roles
DELETE /api/admin/users/:id/roles/:roleId

GET    /api/admin/roles
GET    /api/admin/roles/:id
POST   /api/admin/roles
PUT    /api/admin/roles/:id
POST   /api/admin/roles/:id/toggle-active
POST   /api/admin/roles/:id/permissions
DELETE /api/admin/roles/:id/permissions/:permId

GET    /api/admin/plans
POST   /api/admin/plans
PATCH  /api/admin/plans/:id/active
GET    /api/admin/plans/:id/features
POST   /api/admin/plans/:id/features
PUT    /api/admin/plans/:id/features/:fid
DELETE /api/admin/plans/:id/features/:fid

GET    /api/admin/subscriptions/account/:id
POST   /api/admin/subscriptions/account/:id
PATCH  /api/admin/subscriptions/account/:id/overrides

GET    /api/admin/registry/inventory
GET    /api/admin/registry/catalog
PATCH  /api/admin/registry/features/:id/status
```

**Total: 24 endpoints across 6 domains.**
