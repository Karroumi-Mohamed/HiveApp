# HiveApp — Client User Stories

All stories are scoped to the **Client Panel** (`/app`). Every protected story maps to a real backend endpoint and a Permissionizer-discovered permission persisted in the unified registry `permissions` table. The client permission sieve runs after the admin sieve and is ordered as: B2B collaboration ceiling when the request is B2B, plan entitlement, then user role/override resolution.

The public feature catalog is available before workspace authorization at `GET /api/v1/features/catalog`. It is intentionally not an authorization endpoint. It gives the frontend safe feature metadata for plan comparison, onboarding, and catalog views, while `/api/v1/me/permissions` and guarded feature APIs remain the runtime authority for a signed-in actor.

---

## Actors

| Actor | Description |
|-------|-------------|
| **Owner** | `member.isOwner = true`. Bypasses all role checks. Full access to their workspace. Created automatically on registration. |
| **Member** | Has a `Member` record linked to the account. Access determined by assigned `Role` → `RolePermission` chains, scoped per company. Direct permission overrides can whitelist or blacklist above/below role grants. |
| **B2B Partner Member** | Member of a foreign workspace. Access to this workspace's resources is limited to what the owner explicitly delegated via a `Collaboration` permission grant. |

---

## 1. Authentication & Onboarding

| # | Story | Endpoint | Permission |
|---|-------|----------|------------|
| AUTH-01 | As a new user, I can register with email, password, first name, and last name. The system auto-creates my workspace and assigns me a FREE subscription | `POST /api/v1/auth/register` | — (public) |
| AUTH-02 | As a registered user, I can log in with email and password and receive a CLIENT JWT scoped to my workspace | `POST /api/v1/auth/login` | — (public) |
| AUTH-03 | As a logged-in user, I can refresh my access token using my refresh token without re-entering credentials | `POST /api/v1/auth/refresh` | — (public) |
| AUTH-04 | As a user, I can fetch my flat permission list so the UI can render permission-aware screens | `GET /api/v1/me/permissions` | CLIENT JWT |

**Constraints:**
- Registration auto-provisions: User + Account (workspace) + Member (owner) + Subscription (FREE plan)
- Login checks `user.isActive` and `member.isActive` — inactive accounts are rejected
- Refresh tokens are long-lived; access tokens are short-lived
- `GET /api/v1/me/permissions` returns all permissions granted to the current member in their current workspace context (role union + overrides applied)

---

## 2. Workspace

| # | Story | Endpoint | Permission |
|---|-------|----------|------------|
| WS-01 | As a member, I can view my workspace's details (name, slug, owner) | `GET /api/v1/accounts/me` | `platform.workspace.read` |
| WS-02 | As an owner, I can permanently deactivate my workspace | `DELETE /api/v1/accounts/me` | `platform.workspace.delete` |

**Constraints:**
- `DELETE /api/v1/accounts/me` is irreversible — no soft delete UI safety check in backend
- No workspace update endpoint exists yet (name/slug cannot be changed via API)
- No ownership transfer endpoint exists — owner is fixed at registration

---

## 3. Invitations

**Requires namespace:** `platform.invitations` (owner-gated in practice — members need explicit role grant)

| # | Story | Endpoint | Permission |
|---|-------|----------|------------|
| INV-01 | As an owner, I can invite a new or existing user to my workspace by email, optionally pre-assigning a role | `POST /api/v1/invitations` | `platform.invitations.send` |
| INV-02 | As an owner, I can list all pending invitations for my workspace | `GET /api/v1/invitations` | `platform.invitations.list` |
| INV-03 | As an owner, I can revoke a pending invitation before it is accepted | `DELETE /api/v1/invitations/:id` | `platform.invitations.revoke` |
| INV-04 | As an invitee, I can validate an invitation token to see the workspace name, inviter, expiry, and whether I need to register | `GET /api/v1/invitations/validate?token=` | — (public) |
| INV-05 | As an invitee with an existing account, I can accept an invitation and immediately join the workspace with a CLIENT JWT | `POST /api/v1/invitations/accept` | — (public) |
| INV-06 | As a new invitee without an account, I can accept an invitation by providing first name, last name, and password — creating my account and joining the workspace in one step | `POST /api/v1/invitations/accept` | — (public) |

**Constraints:**
- Invitations expire after 7 days (configurable via `hiveapp.invitation.expiry-days`)
- Duplicate pending invitations to the same email are rejected — revoke first to resend
- A user who is already a member of the workspace can still accept their invitation — it is idempotent (marks accepted, issues JWT, does not create a duplicate member)
- Invitation email is sent automatically via `EmailService` (logs to console in dev, SMTP in prod)
- If a `roleId` is provided, the role is pre-assigned to the new member on acceptance

---

## 4. Members (Staff)

**Requires namespace:** `platform.staff`

| # | Story | Endpoint | Permission |
|---|-------|----------|------------|
| STF-01 | As an owner or authorized member, I can list all members of my workspace with their display name, email, roles, and active status | `GET /api/v1/members` | `platform.staff.read` |
| STF-02 | As an owner or authorized member, I can add an existing platform user to my workspace by user ID | `POST /api/v1/members` | `platform.staff.add` |
| STF-03 | As an owner or authorized member, I can update a member's display name | `PATCH /api/v1/members/:id` | `platform.staff.update` |
| STF-04 | As an owner or authorized member, I can deactivate a member, revoking their workspace access | `DELETE /api/v1/members/:id` | `platform.staff.delete` |
| STF-05 | As an owner or authorized member, I can assign a role to a member, optionally scoped to a specific company | `POST /api/v1/members/:id/roles` | `platform.staff.assign_role` |
| STF-06 | As an owner or authorized member, I can remove a role from a member | `DELETE /api/v1/members/:id/roles/:roleId` | `platform.staff.remove_role` |
| STF-07 | As an owner or authorized member, I can grant a direct permission override (GRANT or DENY) to a member for a specific company scope | `POST /api/v1/members/:id/permissions` | `platform.staff.grant_permission` |
| STF-08 | As an owner or authorized member, I can revoke a direct permission override from a member | `DELETE /api/v1/members/:id/permissions/:permissionCode` | `platform.staff.revoke_permission` |
| STF-09 | As an owner or authorized member, I can view all direct permission overrides for a member scoped to a specific company | `GET /api/v1/members/:id/permissions` | `platform.staff.read_overrides` |

**Constraints:**
- `STF-02` adds by userId — the primary add flow for owners is via invitations (INV-01), not this endpoint
- Member quota is enforced by plan — adding beyond the quota limit returns 402/403
- Role assignments are company-scoped: a member can have the HR role for Company A and the Manager role for Company B simultaneously
- DENY overrides take precedence over role grants — a member explicitly denied a permission cannot act even if a role grants it
- A member cannot deactivate themselves
- Deactivated members retain their data but cannot authenticate into the workspace

---

## 5. Roles (RBAC)

**Requires namespace:** `platform.rbac`

The current implementation keeps client workspace role management on `platform.rbac` to avoid colliding with platform admin role management on `platform.roles`.

| # | Story | Endpoint | Permission |
|---|-------|----------|------------|
| RBAC-01 | As an owner or authorized member, I can list all roles in my workspace | `GET /api/v1/roles` | `platform.rbac.view` |
| RBAC-02 | As an owner or authorized member, I can view all roles scoped to a specific company | `GET /api/v1/roles` (filtered) | `platform.rbac.view_company` |
| RBAC-03 | As an owner or authorized member, I can view a single role's details and its assigned permission bricks | `GET /api/v1/roles/:id` | `platform.rbac.view` |
| RBAC-04 | As an owner or authorized member, I can create a new role with a name | `POST /api/v1/roles` | `platform.rbac.create` |
| RBAC-05 | As an owner or authorized member, I can update a role's name | `PUT /api/v1/roles/:id` | `platform.rbac.update` |
| RBAC-06 | As an owner or authorized member, I can delete a role | `DELETE /api/v1/roles/:id` | `platform.rbac.delete` |
| RBAC-07 | As an owner or authorized member, I can grant a permission brick to a role | `POST /api/v1/roles/:id/permissions` | `platform.rbac.grant` |
| RBAC-08 | As an owner or authorized member, I can revoke a permission brick from a role | `DELETE /api/v1/roles/:id/permissions/:permissionCode` | `platform.rbac.revoke` |
| RBAC-09 | As an owner or authorized member, I can view the permission bricks that are safe and available to grant to client roles | `GET /api/v1/roles/permission-catalog` | `platform.rbac.permission_catalog` |

**Constraints:**
- Permission bricks available to assign are filtered by `PermissionGrantValidator`: client role management can grant only permissions owned by `CLIENT_WORKSPACE` features that are marked client-role grantable. The permission catalog and grant write path also enforce the account's active plan entitlement, so a role cannot receive permissions for a feature that is not currently enabled.
- Deleting a role removes all member-role assignments for that role — members lose those permissions immediately
- A member who has `rbac.grant` but not `rbac.revoke` can add permissions to a role but cannot remove them
- Roles are workspace-scoped — they cannot be shared across workspaces

---

## 6. Companies

**Requires namespace:** `platform.company`

| # | Story | Endpoint | Permission |
|---|-------|----------|------------|
| CO-01 | As an owner or authorized member, I can create a new company in my workspace | `POST /api/v1/companies` | `platform.company.create` |
| CO-02 | As an owner or authorized member, I can list all companies in my workspace | `GET /api/v1/companies` | `platform.company.read_all` |
| CO-03 | As an owner or authorized member, I can view a single company's details | `GET /api/v1/companies/:id` | `platform.company.read_single` |
| CO-04 | As an owner or authorized member, I can update a company's details | `PATCH /api/v1/companies/:id` | `platform.company.update` |
| CO-05 | As an owner or authorized member, I can deactivate a company | `DELETE /api/v1/companies/:id` | `platform.company.delete` |

**Constraints:**
- Company creation is quota-enforced by plan — creating beyond the quota limit returns an error
- Deactivated companies are soft-deleted — their data is retained
- Role assignments to members are scoped to companies — deactivating a company does not remove member-role records
- No company hard-delete endpoint exists

---

## 7. B2B Collaboration

**Requires namespace:** `platform.b2b`

| # | Story | Endpoint | Permission |
|---|-------|----------|------------|
| B2B-01 | As an owner or authorized member, I can initiate a B2B collaboration request with another workspace for access to one of their companies | `POST /api/v1/collaborations/initiate` | `platform.b2b.request` |
| B2B-02 | As an owner or authorized member, I can accept an incoming collaboration request, granting the requesting workspace access | `PATCH /api/v1/collaborations/:id/accept` | `platform.b2b.accept` |
| B2B-03 | As an owner or authorized member, I can revoke an active or pending collaboration (outgoing or incoming) | `DELETE /api/v1/collaborations/:id` | `platform.b2b.revoke` |
| B2B-04 | As an owner or authorized member, I can view all outgoing collaborations I have initiated | `GET /api/v1/collaborations` | `platform.b2b.view` |
| B2B-05 | As an owner or authorized member, I can view all incoming collaboration requests targeting my workspace | `GET /api/v1/collaborations/incoming` | `platform.b2b.view_incoming` |
| B2B-06 | As a provider, I can grant a specific permission brick to an active collaboration, allowing the partner's members to act on my resources | `POST /api/v1/collaborations/:id/permissions` | `platform.b2b.grant_permission` |
| B2B-07 | As a provider, I can revoke a previously granted permission from a collaboration | `DELETE /api/v1/collaborations/:id/permissions/:permissionCode` | `platform.b2b.revoke_permission` |
| B2B-08 | As a provider, I can view the permission bricks that are safe and available to delegate for an active collaboration | `GET /api/v1/collaborations/:id/permission-catalog` | `platform.b2b.permission_catalog` |

**Constraints:**
- B2B management requires the `platform.b2b` feature to be on the actor account's active plan. This includes initiating, accepting, revoking, listing, granting, revoking delegated permissions, and viewing the B2B permission catalog.
- Passive B2B delegated resource access does not require `platform.b2b` on the client account. A partner member may use a provider-granted resource permission even when their home workspace cannot manage B2B flows.
- Collaboration is always between two workspaces for a specific company — not a blanket workspace-to-workspace trust
- B2B permissions are checked by `B2bCollaborationPolicy`, which uses the exact active `collaborationId` resolved into `HiveAppPermissionContext`; a permission delegated to one collaboration must not authorize a different collaboration between the same provider and company.
- Runtime B2B resource access also checks the provider account's current entitlement to the delegated permission. If the provider loses the feature, existing delegated access stops.
- The B2B permission catalog is provider-only and active-collaboration-only. It returns only permissions whose feature is enabled for the provider account and whose action is explicitly listed as B2B-delegatable in code.
- B2B delegation is intentionally narrow right now. The only explicitly B2B-delegatable feature is `platform.company`, and the only action currently exposed is `platform.company.read_single`, so this must be revisited before broader B2B product flows are exposed.
- A partner member's access is entirely determined by the granted collaboration permissions — their own roles in their home workspace are irrelevant here
- Revoking a collaboration immediately removes all partner access — no grace period

---

## 8. Subscription

**Requires namespace:** `platform.subscription`

| # | Story | Endpoint | Permission |
|---|-------|----------|------------|
| SUB-01 | As any member, I can view my workspace's current subscription — plan name, status, price, billing cycle, features, and quota usage | `GET /api/v1/subscriptions/me` | `platform.subscription.read` |
| SUB-02 | As any member, I can browse the public plan catalog to understand available upgrade options | `GET /api/v1/plans` | — (authenticated, no specific perm) |

**Constraints:**
- Subscription management (upgrades, downgrades, overrides) is admin-only — clients can only view their subscription
- Quota usage is derived from live counts (member count, company count) vs. plan limits
- A TRIALING subscription grants the same feature access as the plan it is trialing
- An EXPIRED or CANCELLED subscription results in PlanPolicy denying all feature-gated requests

---

## 9. What Does NOT Exist (by design)

| Missing action | Reason |
|----------------|--------|
| Change own password | No `PATCH /api/v1/me` endpoint — not implemented |
| Reset forgotten password | No password reset flow — not implemented |
| Email verification on register | No verification gate — not implemented |
| Switch between multiple workspaces | `ContextDetectionFilter` uses `findFirstByUserId` — multi-workspace switching is broken by design (known limitation) |
| Upgrade/downgrade subscription | Client-side — subscription changes are admin-only |
| Transfer workspace ownership | No `PATCH /api/v1/accounts/me/owner` endpoint |
| Leave workspace (member self-removal) | No self-removal endpoint — only owner can deactivate a member |
| Delete account permanently | `DELETE /api/v1/accounts/me` deactivates, does not hard-delete |

---

## 10. Permission Sieve Order (client reminder)

```
Request arrives with CLIENT JWT
        │
        ▼
1. B2bCollaborationPolicy
   ├─ Non-B2B request? → ABSTAIN
   ├─ Missing active collaborationId? → DENIED
   ├─ Exact collaboration does not grant requested permission? → DENIED
   ├─ Provider account not entitled to requested permission? → DENIED
   ├─ Exact collaboration grants requested permission and provider is entitled? → GRANTED
   └─ No delegated permission? → DENIED
        │
        ▼
2. PlanPolicy
   ├─ No active subscription? → DENIED
   ├─ Feature in plan or overrides? → ABSTAIN (pass to next)
   └─ Feature NOT in plan? → DENIED (account not entitled)
        │
        ▼
3. UserRolePolicy
   ├─ member.isOwner? → GRANTED (stop)
   ├─ Direct DENY override exists? → DENIED
   ├─ Direct GRANT override exists? → GRANTED
   ├─ Role grants this permission (company-scoped)? → GRANTED
   └─ No grant found → ABSTAIN → 403
```

B2bCollaborationPolicy only fires when `isB2B = true` in the request context, meaning a partner member is acting on provider resources. For that path, the provider account is the entitlement owner. The client account still needs `platform.b2b` for B2B management endpoints, but not for passive delegated resource reads/actions.

---

## 11. Endpoint Reference (complete client surface)

```
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh

GET    /api/v1/me/permissions

GET    /api/v1/accounts/me
DELETE /api/v1/accounts/me

POST   /api/v1/invitations
GET    /api/v1/invitations
DELETE /api/v1/invitations/:id
GET    /api/v1/invitations/validate?token=       (public)
POST   /api/v1/invitations/accept                (public)

GET    /api/v1/members
POST   /api/v1/members
PATCH  /api/v1/members/:id
DELETE /api/v1/members/:id
POST   /api/v1/members/:id/roles
DELETE /api/v1/members/:id/roles/:roleId
POST   /api/v1/members/:id/permissions
DELETE /api/v1/members/:id/permissions/:permissionCode
GET    /api/v1/members/:id/permissions

GET    /api/v1/roles
GET    /api/v1/roles/:id
POST   /api/v1/roles
PUT    /api/v1/roles/:id
DELETE /api/v1/roles/:id
POST   /api/v1/roles/:id/permissions
DELETE /api/v1/roles/:id/permissions/:permissionCode

POST   /api/v1/companies
GET    /api/v1/companies
GET    /api/v1/companies/:id
PATCH  /api/v1/companies/:id
DELETE /api/v1/companies/:id

POST   /api/v1/collaborations/initiate
PATCH  /api/v1/collaborations/:id/accept
DELETE /api/v1/collaborations/:id
GET    /api/v1/collaborations
GET    /api/v1/collaborations/incoming
POST   /api/v1/collaborations/:id/permissions
DELETE /api/v1/collaborations/:id/permissions/:permissionCode

GET    /api/v1/subscriptions/me
GET    /api/v1/plans
```

**Total: 38 endpoints across 8 domains.**
