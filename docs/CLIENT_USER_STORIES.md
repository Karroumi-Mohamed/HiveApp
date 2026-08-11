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
| AUTH-04 | As a user, I can fetch my permission list with its explicit Account and selected Company context so the UI can render permission-aware screens | `GET /api/v1/me/permissions` | CLIENT JWT |

**Constraints:**
- Registration auto-provisions: User + Account (workspace) + Member (owner) + Subscription (FREE plan)
- Login checks `user.isActive` and `member.isActive` — inactive accounts are rejected
- Refresh tokens are long-lived; access tokens are short-lived
- `GET /api/v1/me/permissions` returns permissions granted to the current member for the response's explicit Account/Company context (role union + overrides applied)

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

## 3. Direct Initial Access

HiveApp has no invitation subsystem. An owner or authorized manager creates the employer-managed User and Member directly through the member flow, then the member completes one of two initial-access methods.

| # | Story | Endpoint | Permission |
|---|-------|----------|------------|
| ACC-01 | As an email member, I can use my one-time activation link to choose my own password | `POST /api/v1/auth/activation/complete` | — (one-time token) |
| ACC-02 | As a member without email, I can use the manager-provided temporary password once and receive a restricted token | `POST /api/v1/auth/login` | — (public authentication) |
| ACC-03 | As a temporary-password member, I can replace it before accessing the workspace | `POST /api/v1/auth/initial-password/change` | restricted CLIENT token |
| ACC-04 | As a verified-email member, I can request a non-disclosing password-reset link and complete it | `POST /api/v1/auth/password-reset/request` / `complete` | — (public/one-time token) |

**Constraints:**
- Email is optional. Email members receive a hashed one-time link; other members receive a one-time-visible temporary password that the manager cannot retrieve later.
- Managers can regenerate unused access, reset activated access, or unlock failed temporary access, but never see a permanent member password.
- Credential emails are sent only after the identity transaction commits. Delivery status is persisted without the raw link or token.
- A failed delivery is retried by generating a new token through the protected manager action; HiveApp never stores a reusable credential email for replay.

---

## 4. Members (Staff)

**Requires namespace:** `platform.staff`

| # | Story | Endpoint | Permission |
|---|-------|----------|------------|
| STF-01 | As an owner or authorized member, I can list member identity, ownership, credential, and active-state summaries for my workspace | `GET /api/v1/members` | `platform.staff.read` |
| STF-02 | As an owner or authorized member, I can directly create an employer-managed User and Member with initial roles | `POST /api/v1/members` | `platform.staff.create` |
| STF-03 | As an owner or authorized member, I can update a member's display name | `PATCH /api/v1/members/:id` | `platform.staff.update` |
| STF-04 | As an owner or authorized member, I can deactivate a member, revoking their workspace access | `DELETE /api/v1/members/:id` | `platform.staff.delete` |
| STF-05 | As an owner or authorized member, I can assign a role to a member, optionally scoped to a specific company | `POST /api/v1/members/:id/roles` | `platform.staff.assign_role` |
| STF-06 | As an owner or authorized member, I can remove a role from a member | `DELETE /api/v1/members/:id/roles/:roleId` | `platform.staff.remove_role` |
| STF-07 | As an owner or authorized member, I can grant a direct permission override (GRANT or DENY) to a member for a specific company scope | `POST /api/v1/members/:id/permissions` | `platform.staff.grant_permission` |
| STF-08 | As an owner or authorized member, I can revoke a direct permission override from a member | `DELETE /api/v1/members/:id/permissions/:permissionCode` | `platform.staff.revoke_permission` |
| STF-09 | As an owner or authorized member, I can view all direct permission overrides for a member scoped to a specific company | `GET /api/v1/members/:id/permissions` | `platform.staff.read_overrides` |
| STF-10 | As an owner or authorized member, I can regenerate an unactivated member's access with a new one-time credential | `POST /api/v1/members/:id/access/regenerate` | `platform.staff.regenerate_access` |
| STF-11 | As an owner or authorized member, I can reset an activated member's access or unlock temporary access | `POST /api/v1/members/:id/access/reset` / `unlock` | `platform.staff.reset_access` / `unlock_access` |
| STF-12 | As an owner or authorized member, I can inspect credential state and safe email-delivery status/attempt counts | `GET /api/v1/members/:id/access` | `platform.staff.read_access` |
| STF-13 | As an owner or authorized member, I can inspect one member's scoped role assignments and direct permission overrides in one management read model | `GET /api/v1/members/:id/authorization` | `platform.staff.read_authorization` |

**Constraints:**
- `STF-02` creates both identity and membership atomically; members do not self-enroll and no invitation is created.
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
| B2B-03 | As either participant, I can permanently end an active or suspended collaboration | `DELETE /api/v1/collaborations/:id` | `platform.b2b.revoke` |
| B2B-04 | As an owner or authorized member, I can view all outgoing collaborations I have initiated | `GET /api/v1/collaborations` | `platform.b2b.view` |
| B2B-05 | As an owner or authorized member, I can view all incoming collaboration requests targeting my workspace | `GET /api/v1/collaborations/incoming` | `platform.b2b.view_incoming` |
| B2B-06 | As a provider, I can grant a specific permission brick to an active collaboration, allowing the partner's members to act on my resources | `POST /api/v1/collaborations/:id/permissions` | `platform.b2b.grant_permission` |
| B2B-07 | As a provider, I can revoke a previously granted permission from a collaboration | `DELETE /api/v1/collaborations/:id/permissions/:permissionCode` | `platform.b2b.revoke_permission` |
| B2B-08 | As a provider, I can view the permission bricks that are safe and available to delegate for an active collaboration | `GET /api/v1/collaborations/:id/permission-catalog` | `platform.b2b.permission_catalog` |
| B2B-09 | As a provider, I can generate/rotate, enable/disable, or inspect current usage of a Company discovery code | `POST/PATCH/GET /api/v1/collaborations/companies/:companyId/share-code` | `platform.b2b.regenerate_share_code` / `platform.b2b.manage_share_code` / `platform.b2b.read_share_code` |
| B2B-10 | As an external Account actor, I can resolve a valid code to privacy-minimal provider/Company identity before requesting | `POST /api/v1/collaborations/share-code/resolve` | `platform.b2b.resolve_share_code` |
| B2B-11 | As either participant, I can inspect relationship detail and configured/current grants | `GET /api/v1/collaborations/:id` and `GET /api/v1/collaborations/:id/permissions` | `platform.b2b.read_detail` / `platform.b2b.read_permissions` |
| B2B-12 | As the provider, I can reject a pending request; as the requester, I can cancel it | `PATCH /api/v1/collaborations/:id/reject` or `/cancel-request` | `platform.b2b.reject` / `platform.b2b.cancel_request` |
| B2B-13 | As the provider, I can suspend access with a reason and optional review/automatic-resume schedule, then resume it | `PATCH /api/v1/collaborations/:id/suspend` or `/resume` | `platform.b2b.suspend` / `platform.b2b.resume` |

**Constraints:**
- B2B management requires the `platform.b2b` feature to be on the actor account's active plan. This includes initiating, accepting, revoking, listing, granting, revoking delegated permissions, and viewing the B2B permission catalog.
- Passive B2B delegated resource access does not require `platform.b2b` on the client account. A partner member may use a provider-granted resource permission even when their home workspace cannot manage B2B flows.
- Collaboration is always between two workspaces for a specific company — not a blanket workspace-to-workspace trust
- B2B permissions are checked by `B2bCollaborationPolicy`, which uses the exact active `collaborationId` resolved into `HiveAppPermissionContext`; a permission delegated to one collaboration must not authorize a different collaboration between the same provider and company.
- Runtime B2B resource access also checks the provider account's current entitlement to the delegated permission. If the provider loses the feature, existing delegated access stops.
- The B2B permission catalog is provider-only and active-collaboration-only. It returns only permissions whose feature is enabled for the provider account and whose action is explicitly listed as B2B-delegatable in code.
- B2B delegation is intentionally narrow right now. The only explicitly B2B-delegatable feature is `platform.company`, and the only action currently exposed is `platform.company.read_single`, so this must be revisited before broader B2B product flows are exposed.
- The provider grant is only one authorization layer. Runtime also evaluates the external actor through their home Account's existing Account-scoped role/direct-exception rules. The owner is allowed by the protected owner rule; ordinary members need the exact effective permission, and an applicable Account deny wins. Provider Company scope and organization Groups never grant that client-side authority.
- This current operator lever is safe but broader than the intended management UX: selecting an ordinary operator also gives them the same permission over their home Account's Companies and satisfies the actor ceiling for every collaboration that independently delegates that action. AUTHZ-007 tracks a future `COLLABORATION` role-assignment effect scope so reusable roles can nominate operators without widening internal Company authority. The provider's exact grant continues to block undelegated actions, Companies, and collaborations.
- A stored collaboration grant that becomes non-delegatable in current code stops working immediately and is shown as inactive historical configuration instead of being silently deleted.
- Revoking a collaboration immediately removes all partner access — no grace period
- Company share codes are reusable discovery identifiers, are stored only as hashes, and remain valid until disabled or regenerated. They do not expose broad Company search or unrelated business data.
- Providers can inspect resolution/request counts and last-use times for the current code so unexpected use is visible; rotating the code resets these counters.
- New collaboration records return 201. Identical live requests compare collapsed purpose whitespace and requested capabilities as an unordered set and return the existing relationship with 200. Changed details conflict; after a terminal relationship, a new request creates a new historical record.
- CANCELLED, REJECTED, and REVOKED relationships remain historical. A later request creates a new record and never reuses former grants.

---

## 8. Subscription

**Requires namespace:** `platform.subscription`

| # | Story | Endpoint | Permission |
|---|-------|----------|------------|
| SUB-01 | As any member, I can view my workspace's current subscription — plan name, status, price, billing cycle, features, and quota usage | `GET /api/v1/subscriptions/me` | `platform.subscription.read` |
| SUB-02 | As any member, I can browse the self-service plan catalog with safe feature metadata, included features, add-ons, quota limits, selected overrides, and current usage | `GET /api/v1/subscriptions/catalog` | `platform.subscription.catalog` |
| SUB-03 | As any member with subscription access, I can preview a plan/add-on/quota change and see price plus conflicts before anything mutates | `POST /api/v1/subscriptions/preview` | `platform.subscription.preview` |
| SUB-04 | As any member with subscription access, I can apply an immediately valid plan/add-on/quota change to my own workspace subscription | `POST /api/v1/subscriptions/apply` | `platform.subscription.apply` |

**Constraints:**
- Client subscription self-service never edits `Plan` or `PlanFeature` rows. Admins manage plan templates; clients manage only their own active account subscription.
- Catalog/preview/apply derive `accountId` from request context. A client cannot choose another account id for these flows.
- Catalog exposes only active, plan-assignable client workspace features whose code-owned lifecycle is `PUBLIC` or `BETA`; platform-control, inactive, internal, and deprecated features are hidden.
- Preview and apply share the same validator, usage-conflict checks, snapshot creation, and billing calculation. Apply revalidates under an account lock.
- Downgrades or add-on removals that would leave current usage outside the selected entitlement are rejected until usage is reduced.
- Applying a change cancels the previous usable `ACTIVE`/`TRIALING` subscription and creates a new active entitlement snapshot.
- External checkout/payment confirmation, proration, invoices, cancellation scheduling, and scheduled downgrades are not implemented yet.
- Quota usage is derived from live counts (member count, company count) vs. plan limits
- A TRIALING subscription grants the same feature access as the plan it is trialing
- An EXPIRED or CANCELLED subscription results in PlanPolicy denying all feature-gated requests

---

## 9. What Does NOT Exist (by design)

| Missing action | Reason |
|----------------|--------|
| Change own password | No `PATCH /api/v1/me` endpoint — not implemented |
| Email verification on register | No verification gate — not implemented |
| Switch between multiple workspaces | `ContextDetectionFilter` uses `findFirstByUserId` — multi-workspace switching is broken by design (known limitation) |
| External checkout, invoices, or proration | Subscription self-service currently applies internally without a payment provider |
| Scheduled subscription downgrade | Immediate changes exist; future effective-at-renewal changes are not modeled yet |
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
POST   /api/v1/auth/logout
POST   /api/v1/auth/activation/complete
POST   /api/v1/auth/initial-password/change
POST   /api/v1/auth/initial-password/logout
POST   /api/v1/auth/password-reset/request
POST   /api/v1/auth/password-reset/complete

GET    /api/v1/me/permissions

GET    /api/v1/accounts/me
DELETE /api/v1/accounts/me

GET    /api/v1/members
POST   /api/v1/members
PATCH  /api/v1/members/:id
DELETE /api/v1/members/:id
GET    /api/v1/members/:id/access
GET    /api/v1/members/:id/authorization
POST   /api/v1/members/:id/access/regenerate
POST   /api/v1/members/:id/access/reset
POST   /api/v1/members/:id/access/unlock
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
