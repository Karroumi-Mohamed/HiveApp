# HiveApp Backend Test Plan: Platform Shell

This document defines the test hardening plan for the backend platform shell. Its purpose is to protect the architecture around identity, context, permissions, feature definitions, plans, quotas, subscriptions, admin control-plane actions, client workspace actions, and B2B delegated access.

The platform shell is not safe because a few happy-path tests pass. It is safe only when the edge cases that could break isolation, billing entitlement, quota enforcement, role boundaries, and B2B delegation are covered at the right layers. This plan therefore treats tests as part of the security architecture, not as a secondary cleanup task.

## 1. Testing Model

The backend needs three layers of tests.

Unit tests should cover pure decisions and service invariants. These are fast tests for things like feature definition validation, permission grant eligibility, quota enforcement calls, role/member/account consistency checks, and B2B policy decisions.

Repository and slice tests should cover query behavior where correctness depends on database joins. This matters for admin permission resolution, active subscription lookup, member-role-company scoping, collaboration lookup, and permission override precedence.

Full Spring integration tests should cover request-level behavior: JWT authentication, context detection, Permissionizer AOP enforcement, HiveApp permission context supplier, policy ordering, transaction boundaries, validation, and the actual HTTP status returned to clients/admins.

## 2. Current Coverage Snapshot

Current focused tests cover the new foundation:

```text
FeatureDefinitionTest
FeatureDefinitionCollectorTest
FeatureDefinitionCollectorRootValidationTest
PermissionSeederTest
PermissionGrantValidatorTest
B2bCollaborationPolicyTest
MemberServiceImplTest
RoleServiceImplTest
AdminRoleServiceImplTest
PlanAdminServiceImplTest
PlatformShellSecurityIntegrationTest
CompanyIsolationIntegrationTest
MemberIsolationIntegrationTest
RoleIsolationIntegrationTest
InvitationIsolationIntegrationTest
MemberPermissionSecurityIntegrationTest
B2bCollaborationSecurityIntegrationTest
```

Those tests prove the new direction is working, but they are not enough to close the platform shell. They cover unit-level invariants, critical service/policy boundaries, request-level token/surface separation, the first client resource isolation cases, member override/lifecycle boundaries, and the first complete B2B lifecycle abuse path. The remaining work is broader request-level and abuse-case coverage.

## 3. Feature And Permission Registry

The registry is the spine of plan management, role grants, B2B delegation, frontend capability rendering, and admin inspection. Tests must prove that registry state cannot drift away from code-owned definitions.

Required unit tests:

```text
feature code must be exactly <module>.<feature>
duplicate feature codes fail
duplicate quota slots inside one feature fail
surface-specific feature bases reject the wrong surface
guarded feature service root must match its feature definition code
guarded feature service must contribute exactly one feature definition
PermissionGrantValidator rejects unknown permission feature codes
PermissionGrantValidator rejects permission codes not owned by the declared feature
PermissionGrantValidator allows only client-role grantable features for client roles
PermissionGrantValidator allows only platform-admin-role grantable features for admin roles
PermissionGrantValidator allows only explicitly B2B-delegatable actions for B2B
```

Required seeder tests:

```text
FeatureSeeder creates modules from FeatureDefinition module codes
FeatureSeeder creates or updates features from FeatureDefinition declarations
FeatureSeeder syncs feature surface and quota schema from code
FeatureSeeder preserves admin-managed lifecycle/status where intended
PermissionSeeder skips structural nodes only
PermissionSeeder persists exactly-three-segment action permissions
PermissionSeeder rejects deeper action paths such as platform.roles.permission.grant. Covered at unit level.
PermissionSeeder fails when an action permission has no declared feature. Covered at unit level.
PermissionSeeder fails when a declared feature belongs to a different module or a persisted permission is linked to the wrong feature. Covered at unit level.
PermissionSeeder links permission.resource to the feature code and permission.action to the action segment. Covered at unit level.
```

Required integration tests:

```text
application startup with all current feature contributors produces zero missing-feature action permissions
registry inventory returns current modules, features, surfaces, quota schema, and permission counts
registry catalog does not expose platform-control features as client catalog features
changing registry status cannot make a platform-control feature plan-assignable or role-grantable
```

## 4. Permissionizer And Context

The intended Spring integration path is Spring AOP with Permissionizer's context supplier hook. Byte Buddy auto-guard is not the active HiveApp path.

Required tests:

```text
PermissionGuard is initialized without withAutoGuard
PermissionGuard receives HiveAppPermissionContext from the Spring supplier
guarded service methods are intercepted by Spring AOP
context-null behavior denies or abstains according to policy rules without granting access accidentally
ADMIN JWT does not receive CLIENT context accidentally
CLIENT JWT does not receive ADMIN permissions accidentally
public endpoints are not blocked by Permissionizer guards
```

Current coverage includes active Spring AOP denial on admin feature actions after enabling the guarded `platform` package root. Public invitation validation and acceptance have been split into a capability-token service outside the guarded invitation feature service, with request-level regression coverage proving revoked-token and new-invitee validation responses remain public-flow responses rather than permission denials.

## 5. Authentication And Context Detection

Authentication tests must prove that the actor, account, company, B2B flag, and collaboration context cannot be spoofed or mixed.

Required integration tests:

```text
inactive User cannot log in
inactive Member cannot log in or build client context
inactive Account cannot build client context if account deactivation is intended to block access
admin login returns ADMIN token and client login returns CLIENT token
CLIENT token cannot access /api/admin/**
ADMIN token cannot access client workspace endpoints through client policies
refresh token cannot revive an inactive user/member
context detection rejects a companyId not owned by the current workspace
context detection rejects B2B request without provider company
context detection rejects B2B request without active collaboration
B2B context stores actorUserId, clientAccountId, provider currentAccountId, targetCompanyId, and exact collaborationId
```

Known hardening decision to verify: account deactivation must consistently block client context and login if that is the intended business rule.

## 6. Admin Control Plane

Admin flows operate the platform itself. Tests must prove that platform administration cannot bleed into client workspace data and that admin role grants cannot create accidental privilege escalation.

Required unit and integration tests:

```text
SuperAdmin bypass grants platform-control permissions
non-super admin receives permissions through active AdminRole -> AdminRolePermission -> Permission
inactive admin user cannot authenticate or act
inactive admin role contributes no permissions
admin role grant rejects client-workspace permissions
admin role grant rejects unknown permissions
admin role revoke removes effect immediately
admin cannot deactivate themselves
admin cannot grant a permission they do not already hold unless SuperAdmin
admin user assignment rejects missing/inactive roles
admin APIs never expose client company/member/role business data outside explicit admin subscription/account management
```

`AdminControlPlaneSecurityIntegrationTest` now covers the enforced abuse boundaries: a limited admin cannot read ungranted registry, role-detail, or user-detail actions; cannot grant a permission or assign a role exceeding their held permission ceiling; cannot create a SuperAdmin; and cannot self-deactivate. It also proves that a deactivated admin's previously issued token stops working and that making a control-plane feature `PUBLIC` does not make it assignable to a client plan.

Required repository tests:

```text
AdminUserRepository permission queries traverse unified Permission rows
legacy admin_permissions table is not required
active-role filtering works in admin permission lookup
```

## 7. Client Workspace Isolation

Most abuse risk comes from ID confusion: one account passing another account's memberId, roleId, companyId, collaborationId, planId, featureId, or invitation token. Service tests and HTTP tests must cover this directly.

Required tests:

```text
account endpoints reject access to any account other than currentAccountId
company create/list/get/update/delete reject account or company from another workspace. Covered for list/read/update/delete and foreign context selection at request level.
role list/get/create/update/delete reject roles from another workspace. Covered for read/update/delete/create at request level.
company-scoped roles reject companyId from another workspace. Covered for role creation and role assignment at request level.
member list/add/update/deactivate reject members from another workspace. Covered for list/update/delete at request level.
member role assignment rejects role from another workspace. Covered at request level.
member role assignment rejects company from another workspace. Covered at request level.
member role assignment rejects company-scoped role assigned outside its company
member permission override rejects permission not client-role grantable. Covered at request level.
member permission override rejects memberId or companyId from another workspace. Covered for grant/read/revoke at request level.
owner effective permissions include only client-role grantable permissions and only permissions entitled by the account's current subscription. Covered at service level.
non-owner effective permissions from roles and direct overrides are filtered by the account's current subscription entitlement.
`/api/v1/me/permissions` remains accessible as an authenticated read model even when an unrelated feature is not entitled. Covered at request level by removing `platform.b2b` from the active plan and proving B2B permissions disappear while other entitled permissions remain.
member cannot deactivate themselves. Enforced and covered at request level.
deactivated member cannot act even if old JWT exists. Enforced in context validation and covered at request level.
deactivated company cannot be used as a valid company context if that is the intended lifecycle rule
invitation list/revoke reject invitations from another workspace. Covered at request level.
invitation send rejects roleId from another workspace. Covered at request level.
invitation send rejects companyId from another workspace. Covered at request level.
invitation send persists and validates company scope so accepted invites cannot bypass company-scoped role rules. Covered for send-time scope validation at request level; acceptance coverage remains required.
```

## 8. Plan, Subscription, And Entitlement

Plan and subscription tests must prove that billing configuration controls access, but cannot grant control-plane power.

Required plan tests:

```text
PlanSeeder seeds only planAssignable feature definitions
PlanSeeder seeds workspace quota defaults for FREE, PRO, and ENTERPRISE
FeatureSeeder initializes newly declared public-catalog features as PUBLIC and control-plane features as INTERNAL while preserving later administrator status changes. Covered at unit and request levels through billing configuration behavior.
plan assignment rejects platform-control features. Covered at service validation level.
plan assignment rejects INTERNAL and DEPRECATED features. Enforced by BillingConfigurationValidator; request-level coverage remains required.
plan assignment rejects unknown features. Enforced by BillingConfigurationValidator; request-level coverage remains required.
plan assignment rejects quota configs for unknown quota resources. Covered at service validation level.
plan assignment rejects quota configs on features without quota slots. Covered at service validation level.
plan update validates quota configs the same way as assignment. Covered at service level.
inactive plan cannot be assigned to a new subscription. Enforced and covered at service level.
BillingCycle.FOREVER is allowed only for FREE if that rule is accepted
```

Required entitlement tests:

```text
active subscription grants included plan features. Covered at policy level, including perpetual subscriptions with no period end.
trialing subscription grants included plan features. Covered at policy level while the trial period remains unexpired.
cancelled subscription denies feature-gated requests. Covered at policy level because only active or trialing subscriptions are considered.
expired subscription denies feature-gated requests. Enforced and covered at policy level for non-null `currentPeriodEnd`.
missing subscription denies feature-gated requests. Covered at policy level.
`PlanPolicy` and `/api/v1/me/permissions` share the same `PlanEntitlementService`, so frontend effective permissions do not include plan-denied actions. Covered at unit/service and request level.
plan feature removal semantics are explicit and tested: either existing subscriptions lose the feature dynamically, or subscription snapshots preserve it
subscription addedFeatures cannot add platform-control features. Covered at service validation level.
subscription addedFeatures cannot add unknown features. Covered at service validation level.
subscription quotaOverrides cannot reference unknown quota resources. Covered at service validation level.
subscription quotaOverrides cannot reference non-quota features. Enforced by BillingConfigurationValidator; request-level coverage remains required.
subscription override writes persist a valid declared workspace quota increase only after validation. Covered at service level.
subscription restrictive overrides, if supported, deny a feature even when plan includes it
administrator plan replacement cancels existing ACTIVE/TRIALING subscriptions before storing the new ACTIVE entitlement. Covered at service and request levels.
administrator same-plan active reassignment is rejected. Covered at service level.
database rejects more than one ACTIVE/TRIALING subscription for the same account. Covered at persistence integration level.
database rejects a usable subscription whose usable-account slot is null or inconsistent with its account. Covered through raw persistence integration testing.
cancelled subscription history releases the usable slot for a replacement. Covered at persistence integration level.
simultaneous administrator plan assignments serialize and leave exactly one usable subscription. Covered at request level.
```

Accepted runtime rule: a subscription in `ACTIVE` or `TRIALING` state grants entitlement only when `currentPeriodEnd` is null, representing a non-expiring entitlement such as the seeded FREE plan, or lies in the future. An expired period denies feature-gated actions even if status cleanup has not yet run.

Known semantic gap: the docs previously said removing a feature from a plan does not retroactively revoke it from existing subscriptions. The current dynamic plan policy may not behave that way unless subscriptions snapshot features or overrides preserve access. This must be decided and tested.

## 9. Quotas

Quota checks must happen inside services, not only in controllers, because service methods are the business boundary.

Required tests:

```text
member add checks WorkspaceFeature.members quota inside MemberService. Covered at service and FREE-plan request levels.
company create checks WorkspaceFeature.companies quota inside CompanyService. Covered at service and FREE-plan request levels.
quota check is not called after an account/company/member ownership failure. Covered at service level for member and company creation.
quota check uses the current account, not a request-supplied account. Covered by service ownership-before-quota tests and controller-derived account scope.
quota unlimited value permits creation. Covered at QuotaEnforcer unit level.
quota exact limit denies when current usage is equal to limit. Covered at QuotaEnforcer unit level and by FREE-plan HTTP 402 cases.
quota override increases the effective limit. Covered at QuotaEnforcer unit level and through a priced PRO administrator override request.
quota override cannot decrease below current usage unless the business rule permits it with future denial only
quota usage count excludes deactivated records only if the accepted product rule says inactive records no longer consume quota
```

Current request-level coverage confirms seeded FREE limits of one company and three total workspace members, including the owner; PRO limits company creation to five unless a priced administrator-applied override raises the limit; and ENTERPRISE company creation is not constrained by the workspace company slot. A limit breach is returned through the API contract as HTTP `402` with quota details.

## 10. Role And Permission Overrides

Client roles and direct overrides are dangerous because they create delegated power inside a workspace. Tests must prove precedence and scope.

Required tests:

```text
owner bypass grants client workspace permissions but does not include platform-control permissions
role grants apply only in the member's workspace
company-scoped role grant applies only for matching company context
workspace-scoped role grant applies across companies if that is intended
direct DENY override beats role GRANT
direct DENY override beats direct GRANT if duplicate conflict can exist
direct GRANT works without role grant
revoking direct override removes effect immediately
deleting role removes member-role effects immediately
granting a permission to a role rejects platform-control permissions
granting a permission to a role rejects B2B-only permissions if they are not client-role grantable
```

## 11. B2B Collaboration

B2B is a cross-account access path, so tests must assume hostile ID mixing.

Required service and policy tests:

```text
client cannot initiate collaboration with its own account. Covered at request level.
client can request collaboration only for a provider-owned company
provider only can accept incoming collaboration. Covered at request level.
provider only can grant or revoke collaboration permissions. Grant and non-participant revoke are covered at request level.
either participant can revoke collaboration if that remains the accepted rule
non-participant cannot read collaboration permissions
B2bCollaborationPolicy denies when collaborationId is null
B2bCollaborationPolicy checks permission against exact collaborationId
permission delegated to collaboration A does not authorize collaboration B with same provider/company
revoked collaboration denies immediately. Covered at request level.
pending collaboration denies delegated access and permission grants. Covered at request level.
provider's plan entitlement is checked for delegated feature if that rule is accepted
client's plan entitlement is checked for B2B shell feature if that rule is accepted
B2B delegation rejects platform-control permissions. Covered at request level.
B2B delegation rejects client-workspace features not marked b2bDelegatable
B2B action set is explicit; broad platform.company.* delegation is rejected. Covered for company create/read_all/update/delete at request level.
B2B permission grants are scoped per collaboration/company; one provider/client pair can have different permissions for Company 1 and Company 2. Covered for `platform.company.read_single` at request level.
B2B resource access rejects a path/resource company id that does not match the active B2B context company. Covered at request level.
```

## 12. Invitations

Invitation flows mix public token endpoints and authenticated workspace endpoints. Tests must cover both.

Required tests:

```text
sending invitation rejects accountId outside current workspace
sending invitation rejects roleId outside current workspace. Covered with a `403` response at request level.
duplicate pending invitation to same email is rejected. Covered with a `409` response at request level.
revoked invitation cannot be accepted. Covered with a `409` response at request level.
expired invitation cannot be accepted
accepted invitation cannot create duplicate member
accepting invitation with existing user joins correct workspace
accepting invitation with new user creates User and Member for correct workspace
invitation token cannot be used to join a different workspace
role preassignment on accept respects workspace ownership
```

## 13. Request-Level Abuse Matrix

Each protected endpoint should eventually have an integration test row for these abuse classes:

```text
no token -> 401
wrong token type -> 403. Covered for client-to-admin and admin-to-client protected requests.
missing permission -> 403
feature not in plan -> 403
inactive actor whose token no longer yields a usable workspace context -> 401
foreign accountId -> 403
foreign memberId/roleId/companyId/collaborationId -> 403
malformed context identifier -> 400
unknown context resource -> 404
invalid lifecycle transition -> 409
inactive/deleted target -> 404 or 403 according to endpoint contract
valid actor and valid permission -> 2xx
```

This matrix is intentionally repetitive. It is how we catch IDOR bugs and policy ordering mistakes.

## 14. API Error Contract

Backend errors need stable semantics because the frontend must distinguish invalid authentication, denied authorization, invalid user input, and rejected lifecycle operations. Custom exceptions should carry business meaning from service boundaries into consistent JSON `ApiError` responses.

The contract currently introduced for the hardened flows is:

```text
UnauthorizedException -> 401: authentication or actor context is no longer usable, including inactive membership.
ForbiddenException -> 403: the authenticated actor is not allowed to access a resource or collaboration.
InvalidRequestException -> 400: request or context data is malformed and cannot be processed.
InvalidPermissionGrantException -> 400: the requested permission cannot be granted on the requested surface.
InvalidStateException -> 409: a lifecycle transition is not valid in the current resource state.
DuplicateResourceException -> 409: creating or assigning a duplicate resource conflicts with current state.
DataIntegrityViolationException -> 409: a database-enforced invariant rejects a conflicting write.
ResourceNotFoundException -> 404: a referenced resource or selected context does not exist.
```

This contract is active for API-surface token separation, client workspace ownership checks, member, role, and invitation scope validation, B2B collaboration lifecycle enforcement, context resolution, and permission-surface validation. Inactive member access remains `401` because an existing access token no longer yields a usable workspace actor context.

## 15. Priority Order

The next backend work should proceed in this order:

```text
1. Add integration test scaffolding for authenticated client/admin requests with seeded feature definitions and permissions. Started with `PlatformShellIntegrationTestSupport`.
2. Cover request-level token type separation for /api/admin/** and /api/v1/**. Covered for authentication presence, valid same-surface tokens, valid wrong-surface tokens, and malformed tokens in `PlatformShellSecurityIntegrationTest`.
3. Cover cross-account isolation for company, member, role, invitation, and collaboration IDs. Covered for current company, member, role, invitation, and initial B2B request flows; extend as new endpoints are added.
4. Cover plan/subscription/quota enforcement at service and HTTP levels.
5. Cover admin role grant escalation and self-deactivation rules. Covered by `AdminControlPlaneSecurityIntegrationTest`.
6. Cover subscription override validation.
7. Cover B2B abuse cases with multiple collaborations sharing provider/client across different companies and with different clients. Started with lifecycle, participant, action-level grant-surface, exact company-scope, and revocation access tests.
8. Switch strict permission seeder violations from warning/skip to startup failure once the suite proves current registry cleanliness. Done.
```

## 16. Completion Criteria

The platform shell hardening phase is complete when the backend has passing tests proving:

```text
no control-plane feature can be assigned to client plans
no control-plane permission can be granted to client roles
no control-plane permission can be delegated through B2B
no client workspace permission can be granted to platform admin roles unless explicitly allowed by architecture
no request can cross account boundaries by swapping IDs
no B2B request can reuse permissions from a different collaboration
quota enforcement lives in services and cannot be bypassed by alternate callers
subscription state controls entitlement consistently
feature and quota registry state is code-owned and startup-validated
Permissionizer receives context through the Spring context supplier path and enforces guarded platform actions
```
