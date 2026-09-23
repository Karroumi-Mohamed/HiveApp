# HiveApp Review and Fix Ledger

This is the living defect and design-debt ledger for the source-first HiveApp review.

**Architecture decision:** HiveApp is one organized monolithic application. Package/domain boundaries exist to keep the monolith understandable; they are not microservices and must not be reviewed or redesigned as microservices.

Do not fix entries immediately. First inspect the relevant entities, repositories, services, security policies, migrations, tests, and UI usage. At the end of the review, confirm each issue, determine dependencies, prioritize it, and implement fixes in a controlled order.

## Status meanings

- `OBSERVED`: directly visible in the source currently reviewed.
- `VERIFY`: suspicious, but later layers may already enforce or intentionally explain it.
- `DECISION`: requires a clear product or architecture decision before implementation.
- `CONFIRMED`: verified across the relevant layers and ready to fix.
- `FIXED`: implemented and tested.

## Review scope completed

- Standalone Permissionizer `src/main/java`: reviewed.
- HiveApp product-level documents: reviewed as secondary, potentially conflicting evidence.
- HiveApp backend package structure: mapped.
- HiveApp entity layer: 22 JPA entities, `BaseEntity`, and directly referenced stored enums/quota value types reviewed.
- HiveApp DTO and mapper folders: 65 DTOs and 7 MapStruct mappers reviewed.
- Identity service interfaces: `AuthService` and `IdentityService` reviewed.
- Identity service implementations: `AuthServiceImpl` and `IdentityServiceImpl` reviewed.
- Remaining identity folders reviewed: API, repository, event, and identity security. The complete `identity` area is now scanned.
- Remaining platform admin folders reviewed: repositories, service contracts/implementations, API controllers, security details loader, seeder, and package metadata. The complete `platform.admin` area is now scanned.
- Remaining client account folders reviewed: repositories, service contracts/implementations, APIs, and events. The complete `platform.client.account` area is now scanned.
- Remaining client company and member folders reviewed: repositories, service contracts/implementations, and APIs. The complete `platform.client.company` and `platform.client.member` areas are now scanned.
- Remaining client role and invitation folders reviewed: repositories, service contracts/implementations, and APIs. The complete `platform.client.role` and `platform.client.invitation` areas are now scanned.
- Remaining client collaboration folders reviewed: repositories, service contract/implementation, and API. The complete `platform.client.collaboration` area is now scanned.
- Complete registry area reviewed: code definitions, definition validation/collection, seeders, repositories, catalogs, service implementation, and APIs.
- Complete client plan/subscription area reviewed: repositories, admin/client APIs, seeder, snapshots, billing, entitlement, quota/usage support, and service implementations.
- Complete shared security area reviewed: JWT filters/provider, request context, Permissionizer policy order/configuration, effective-permission calculation, CORS, and error handlers.
- Remaining shared/bootstrap folders reviewed: application/config, email implementations, payment placeholders, domain-event marker, and global exception/API-error handling. Main Java source is now structurally covered.
- Backend test tree mapped and high-risk registry/security/plan test behaviors indexed. The latest local Surefire reports (2026-07-14) show 206 tests, 0 failures, 0 errors, and 0 skipped; this was an existing report, not a rerun during this review.

---

## Entity-layer findings

### TENANCY-001 — Establish `Account` as the canonical tenant boundary

**Status:** `IMPLEMENTED — 2026-07-16`

**Evidence**

- `Company.account`, `Member.account`, `Role.account`, `Subscription.account`, invitations, and collaborations all organize data beneath `Account`.
- A `Company` belongs to an `Account`; it is not the top-level tenant in the current entity model.
- Some older documents describe `Company` as the absolute isolation boundary, which conflicts with the source.

**Risk**

If future HR, payroll, and accounting modules disagree about whether tenancy is scoped by account or company, authorization and data queries can leak or mix data. Cross-module integration will also become inconsistent.

**Required resolution**

- Declare `Account` as the SaaS tenant/workspace boundary.
- Declare `Company` as the business/legal operational scope inside an account.
- Enforce the accepted rule that one user has at most one active client Account membership.
- Require future business records to carry the appropriate account/company scope.
- Align naming in backend context, APIs, UI, and surviving documentation.

**Implementation evidence — 2026-07-16**

- `SecurityContextService` now selects only an active membership and continues to use `Account` as `currentAccountId`; inactive membership still fails as 401 rather than degrading into an unscoped context.
- Company, member, role, invitation, and collaboration repositories expose account- or participant-scoped ID lookups.
- Service write/read paths use those scoped queries, so a foreign tenant ID is treated as absent (404) instead of loading the row globally and revealing that it exists.
- Existing HTTP isolation suites now lock non-disclosing behavior for companies, members, roles, invitations, and B2B collaborations.

---

### TENANCY-002 — `Account.ownerId` is an unverified raw UUID relationship

**Status:** `IMPLEMENTED — 2026-07-16`

**Evidence**

- `Account.ownerId` is a UUID column rather than a JPA/database relationship to `User` or `Member`.
- The column is unique, so one owner UUID can own only one account.
- Ownership is also represented indirectly by `Member.isOwner`.
- Client access and refresh tokens issued by `AuthServiceImpl` contain user identity and `tokenType=CLIENT`, but no selected account/member identity. Context selection must therefore happen later in the request pipeline.

**Risk**

The account owner UUID and owner-member row can drift apart. The database cannot guarantee that the UUID references an existing user or that the same user has an active owner membership. The unique constraint also prevents one user from owning multiple workspaces, whether intended or not.

**Required fix direction**

Use one authoritative ownership model with referential integrity. Single-workspace client membership is now an accepted product rule, so enforce user membership uniqueness at the database level and keep owner identity consistent with the Account's owner Member.

**Implementation evidence — 2026-07-16**

- `Account.ownerId` was replaced by a required lazy `User owner` relationship using the existing `owner_id` column, named foreign-key metadata, and the existing uniqueness contract.
- Workspace provisioning assigns the loaded `User` entity and creates the matching owner `Member` in the same transaction.
- An owner-member persistence callback rejects a member whose user differs from the account owner.
- Member creation and legacy invitation acceptance reject a user who already has an active membership in another account; security-context selection uses only the active membership.
- Tests cover the ownership mapping, owner/member consistency, duplicate active-membership rejection, and the legacy cross-workspace acceptance path.

**Future production hardening — not a current blocker**

The current unpublished application uses disposable in-memory H2 schemas generated from JPA, so `fk_accounts_owner` is recreated from the entity mapping on every run. One-active-membership behavior is enforced in application flows and tested. When HiveApp gains a persistent production database, `CONFIG-001` must introduce versioned migrations and a database-level concurrent-write constraint without preventing historical inactive membership rows; that deployment hardening does not block the current batch.

---

### RBAC-001 — Company scope is represented twice for role assignment

**Status:** `IMPLEMENTED FOR CURRENT PLATFORM SHELL — 2026-07-17`

**Evidence**

- `Role` has an optional `company`.
- `MemberRole` independently has an optional `company`.

**Risk**

A company-scoped role can be assigned with a null or different `MemberRole.company`. Code may use one scope while authorization uses the other, producing incorrect grants or cross-company access.

**Decided product architecture**

- Add an explicit template hierarchy: Platform templates usable by eligible tenants, Account templates reusable throughout one Account, and Company templates limited to one Company. Department is never a template/security level.
- Give every template an owning/availability boundary and prevent lower-scope managers from editing, shadowing, replacing, or broadening higher-level templates.
- Put the actual authorization effect scope on the member-role assignment: Account or Company only.
- Require every assignment scope to be contained by both the template boundary and the actor's management/delegation scope.
- Allow multiple assignments per member, including reuse of the same template at different Account/Company scopes, but enforce a unique exact assignment key. Calculate effective permissions only for the requested target scope; never globally union assignments from unrelated Companies.
- Keep ownership as protected status rather than a role. Separate code-owned system templates from platform-admin-published starter templates if both are supported.
- Make published Platform templates immutable/versioned. Accounts retain their current version until an authorized actor explicitly adopts a newer version, or copy one into an independent Account custom template.
- Build role-management UX around safety: show template origin/version/boundary/editability, separate template from assignment scope, compare permission diffs, preview affected members/scopes, explain blocked grants, and provide keep/adopt/copy choices before mutation.
- Retirement stops new use without erasing existing assignments. Adoption and shared-role edits validate atomically and produce actor-aware audit history.

The current duplicate nullable Company fields must be replaced or renamed into explicit concepts: template boundary versus assignment effect scope. An Account template may be assigned Account-wide or to a Company; a Company template may be assigned only to its owning Company. Enforce containment in service validation and database constraints where possible.

**Implementation evidence — 2026-07-17**

- `Role.templateBoundary` and `Role.boundaryCompany` now express Account/Company template availability independently from assignment effect.
- `MemberRole.effectScope` and `MemberRole.scopeCompany` now express the exact Account/Company authorization effect, with a stable exact-scope uniqueness key.
- Account templates can be reused at Account or Company effect scope. Company templates are rejected outside their boundary Company.
- Effective-permission resolution includes Account assignments plus only the requested Company assignments and overrides; it no longer unions unrelated Companies.
- Non-owner mutation from a Company context is limited to a template bounded to that Company. B2B role operations retain the collaboration-Company boundary.
- Published Platform starter templates remain a future platform-admin source/adoption model; the current tenant shell intentionally implements only assignable Account and Company templates.
- Because the application is unpublished and its H2 database is disposable, the generated entity/schema mappings were updated directly without introducing versioned migration history.

---

### RBAC-002 — Inactive client roles still grant permissions

**Status:** `IMPLEMENTED — 2026-07-16`

**Evidence**

`MemberRoleRepository.existsByMemberIdAndPermissionCode()` joins member assignments, roles, role permissions, and permissions, but does not require `Role.isActive=true`.

**Risk**

Deactivating a client role does not remove its authorization effect even though the entity and product language treat role activation as meaningful. Users may retain access that administrators believe was revoked.

**Required fix direction**

Filter inactive roles in the runtime authorization query and add request-level tests proving access stops immediately after role deactivation. Also decide whether inactive roles may be newly assigned.

---

### RBAC-003 — Client role assignment and direct grants have no actor permission ceiling

**Status:** `IMPLEMENTED — 2026-07-17`

**Evidence**

- `MemberServiceImpl.assignRole()` verifies tenant/company ownership but does not verify that the acting member may delegate every permission contained in the role.
- `grantPermissionOverride()` verifies that the permission is client-role-grantable, but does not verify that the acting member holds that permission.
- `RoleServiceImpl.addPermissionToRole()` checks code grantability and plan entitlement but not whether the acting non-owner holds the permission being added.
- Invitation role pre-assignment also lacks an actor permission-ceiling check.
- `CollaborationServiceImpl.grantPermission()` checks code B2B-delegatability and provider plan entitlement but not whether the acting provider member personally holds the delegated permission.
- Permissionizer checks whether the actor has `platform.staff.assign_role` or `platform.staff.grant_permission`; it does not by itself constrain the permissions being delegated.

**Risk**

A non-owner manager with staff-management permission can assign or directly grant permissions more powerful than their own, including granting them to an accomplice or potentially themselves.

**Required decision and fix direction**

Implement the decided owner/delegation model:

- The Account owner is an authorization invariant with every code-active permission available to their Account, including all Company scopes; this should not depend on mutable role-permission rows.
- Model ownership as protected Account membership status, never as a permission/role that can be granted or edited. Ordinary permission-management authority must not allow a manager to modify the owner's authority or assign ownership to another member.
- Plan entitlement and feature lifecycle still limit paid/disabled capabilities, while baseline Account/subscription administration remains available.
- Ownership never grants platform-admin, cross-Account, or undelegated provider-side B2B access.
- Owners may delegate within active subscription entitlement; non-owners may delegate only permissions they effectively hold and that are code-declared grantable.
- Ordinary `DENY` overrides cannot remove owner authority.
- Enforce exactly one active owner per Account. Owner deletion/deactivation and ordinary edits must not remove ownership.
- Add a dedicated atomic ownership-transfer operation whose target is an active member of the same Account, with current-owner confirmation/re-authentication, concurrency protection, authorization-session refresh/revocation, and full audit. Do not model co-owners through multiple owner flags.
- Mark owner-only actions as non-grantable in the code-owned Permissionizer registry. Exclude them from role/override/delegation pickers and reject crafted API attempts to grant them. Permission management itself may remain delegatable under the non-owner ceiling.

Enforce these rules in Permissionizer/runtime authorization, role assignment, direct GRANT overrides, and B2B delegation, with boundary and abuse tests.

**Implementation evidence — 2026-07-17**

- A central `DelegationCeilingService` evaluates the authenticated actor's effective permissions at the requested Account/Company effect scope.
- Normal role assignment and member-creation initial assignments require the actor to hold the scoped management action and every permission contained in the role.
- Direct GRANT/DENY override mutation requires the matching scoped management action and delegated permission. Owners remain unaffected by overrides, and ordinary role/override APIs reject the owner as a target.
- Role permission addition, activation, and duplication enforce the ceiling at every currently affected assignment scope, or at the template's default boundary when it has no assignments.
- B2B permission grants require the provider actor to personally hold the delegated permission in the collaboration Company.
- Owner-only action classification/picker exclusion remains owned by the later registry action-metadata work (`REGISTRY-008`/`REGISTRY-010`); atomic ownership transfer remains a separate future flow.

---

### RBAC-004 — Removing a member role ignores company scope

**Status:** `IMPLEMENTED — 2026-07-17`

**Evidence**

- Role assignment accepts an optional `companyId`.
- The delete API accepts only member ID and role ID.
- `deleteByMemberIdAndRoleId()` deletes every assignment for that member/role pair regardless of company.

**Risk**

Removing a role intended for Company A can also remove the same role assignment from Company B or account-wide scope.

**Required resolution**

Make removal scope explicit, or deliberately define the operation as "remove this role from all scopes" and name/confirm it accordingly. Prefer precise assignment IDs or member+role+scope keys.

**Implementation evidence — 2026-07-17**

- Role removal now requires an explicit `ACCOUNT` or `COMPANY` effect scope and requires `companyId` exactly for Company removal.
- Repository deletion targets only the exact member/role/scope key and returns not found when that assignment does not exist; sibling Company and Account assignments are retained.
- Focused unit and request-level integration tests cover exact-scope removal and the updated API contract.

---

### DATA-001 — Join/grant tables may allow duplicate relationships

**Status:** `RESOLVED FOR CURRENT GENERATED SCHEMA — 2026-07-16`

**Evidence**

No explicit composite uniqueness constraints are declared on these entities:

- `AdminUserRole(admin_user_id, admin_role_id)`
- `AdminRolePermission(admin_role_id, permission_id)`
- `RolePermission(role_id, permission_id)`
- `MemberRole(member_id, role_id, company_id)`
- `MemberPermissionOverride(member_id, company_id, permission_id)`
- `CollaborationPermission(collaboration_id, permission_id)`
- `PlanFeature(plan_id, feature_id)`

`AdminUser.user` is modeled as `@OneToOne`, but the actual database uniqueness must also be verified in migrations.

Admin role/user services use `exists...` pre-checks before inserting assignments, but those checks do not protect against concurrent inserts. `AdminRoleServiceImpl.createAdminRole()` also relies directly on the database unique role-name constraint without translating it locally.

**Risk**

Duplicate permission grants, assignments, overrides, or plan features can produce incorrect counts, ambiguous updates, duplicate UI rows, and harder revocation logic. Service-level existence checks alone are vulnerable to concurrent requests.

**Verify later**

- SQL migrations/schema output.
- Repository existence checks.
- Transaction boundaries and concurrency tests.

**Possible fix direction**

Add database unique constraints matching the domain invariants, then make service operations idempotent and translate constraint violations into clear API errors. Nullable company scope may require a PostgreSQL-specific null-safe strategy.

**Implementation evidence — 2026-07-16**

- Verification confirmed that every listed join/grant entity lacked explicit composite uniqueness.
- Generated schemas now constrain `AdminUserRole`, `AdminRolePermission`, `RolePermission`, `MemberPermissionOverride`, `CollaborationPermission`, and `PlanFeature` by their domain pairs/triples. The AdminUser-to-User one-to-one join is explicitly unique.
- `MemberRole` uses a derived, non-null `scope_key`: the zero UUID represents Account scope and a Company ID represents Company scope. Uniqueness on member, role, and this key closes the nullable-company loophole portably.
- Member and role assignment services retain friendly pre-checks, flush inside the transaction, and translate a losing database race to a clear 409 conflict.
- Integration tests prove duplicate account-scoped MemberRole and RolePermission inserts are rejected. The complete generated schema starts successfully under H2.

Versioned forms of these constraints will be created when `CONFIG-001` establishes the first persistent production schema; no Flyway history is maintained during the current disposable in-memory stage.

---

### TENANCY-003 — Cross-account relationship invariants are not visible in the entity model

**Status:** `IMPLEMENTED — 2026-07-16`

**Evidence**

The entity mappings permit references that may belong to unrelated accounts:

- A `Role.company` may not belong to `Role.account`.
- `MemberRole` may combine a member, role, and company from different accounts.
- `MemberPermissionOverride` may combine unrelated member/company/permission context.
- An invitation's role or company may not belong to its account.
- A department manager may be a member of a different account.
- A collaboration's company may not belong to the expected provider account.

**Risk**

Missing validation in even one write path could create cross-tenant access or corrupt authorization state.

**Verify later**

Inspect every create/update service and abuse test covering these relationships. Confirm which invariants are enforced by database foreign keys, service queries, permission policies, and request context.

**Possible fix direction**

Centralize same-account/same-company invariant checks, query related records through tenant-scoped repositories, and add request-level negative tests for cross-account identifiers.

**Verification and implementation evidence — 2026-07-16**

- Verification confirmed that service checks covered several normal flows but the entity model itself permitted invalid combinations.
- `TenantInvariant` now provides identity-safe same/different entity checks for persistence callbacks.
- `@PrePersist` and `@PreUpdate` checks protect owner members, role/company scope, member-role assignments, member overrides, invitations, department parents/managers, and collaboration provider/company/client relationships.
- Tenant-owned service inputs are loaded through scoped repository methods before mutation.
- Ten focused entity/service tests plus the existing HTTP isolation suites cover valid and mismatched relationships.
- The complete backend suite passes: 241 tests, 0 failures, 0 errors, 0 skipped.

---

### ORG-001 — Department entity cannot support the decided generic Group model

**Status:** `PARTIALLY RESOLVED — MONEY/CURRENCY FOUNDATION IMPLEMENTED 2026-08-10`

**Evidence**

- `Department` contains Company, optional parent Department, name, description, and manager.
- Only `DepartmentRepository.findAllByCompanyId()` exists; there is no Department service, controller, DTO, mapper, registry feature, Permissionizer action set, or reviewed test coverage.
- `Member` has no Company or Department placement, and no join entity connects members to Departments.
- No current query can safely list the members of one Department or its subtree.
- `Department.manager` can reference any `Member` at the entity level; same-Account/same-Company manager validity is not structurally enforced.

**Risk**

Expanding this entity would hard-code one organizational vocabulary, one manager, and one membership shape. It cannot represent customer-created roots such as Divisions, reusable nested structures, multiple Group memberships, or a different position per membership.

**Required direction**

- Replace/migrate the special Department model to a generic Company-owned Group with name, optional parent, ordering, lifecycle, and audit metadata.
- Add many-to-many Group membership with a position/title stored on each membership. One member may belong to several Groups with different positions.
- Permit placement of any same-Account member without requiring a Company role/access assignment; placement must never grant Company access. Reject cross-Account members.
- Keep position/title optional and free-text, with no coded security behavior. Do not require a primary Group/position in the shell; HR may model official employment placement separately.
- Auto-create a deletable root Group named `Departments` during Company organization initialization; its name has no code meaning.
- Allow multiple roots and unlimited practical nesting while preventing cycles and cross-Company parents.
- Enforce normalized/case-insensitive sibling-name uniqueness, including root siblings, with database protection where possible.
- Allow Group deletion only when it has no children and no member memberships. Return blocking details so the UI can guide the administrator to move/remove contents first; never cascade-delete that structure or its members.
- Support safe rename and same-Company subtree moves. Validate normalized sibling-name uniqueness, cycles, and destination ownership before mutation while preserving memberships and positions.
- Support explicit sibling display ordering with safe reorder commands. Ordering has no authorization meaning.
- Forbid cross-Company Group moves. Cross-Company reuse must copy only a member-free structure through an eligible template.
- Treat membership at each Group as explicit. Descendant-inclusive views are query/display behavior, not hidden ancestor memberships.
- Removing a Group membership must affect only that placement/position and must never change member activation, roles, exceptions, or operational access.
- Add reusable whole-structure templates that exclude real members, credentials, roles, and permissions. Preview all generated names and create atomically; any conflict blocks the whole instantiation.
- Support Platform, Account, and Company template ownership boundaries. Instantiation produces an independent structure; template edits must not mutate previously created Groups.
- Keep `Member` Account-owned and operational access independent through Account/Company roles and B2B delegation.
- Design Group deletion/reparenting, template conflict UX, archive/history, and limits during implementation.

**Implementation evidence — 2026-07-16**

- The special `Department` entity/repository are removed. `OrganizationGroup` now provides a Company-owned, auditable, ordered, multi-root tree with optional parent, active/archived lifecycle, normalized sibling-name uniqueness, and persistence-time same-Company validation.
- Company creation initializes one ordinary, deletable `Departments` root. No code assigns special behavior to that name.
- `GroupMembership` supports several explicit Group placements for one Account-owned Member, each with its own optional free-text position. Same-Account validation is enforced without requiring a Company role; cross-Account placement is rejected.
- Permissionizer-protected APIs cover list/create/update/move/reorder/archive/restore/delete, direct and descendant membership views, member placement updates, and template operations. Cross-Company moves, cycles, archived mutations, inactive Companies, and B2B access outside the collaboration target Company are blocked.
- Empty-only deletion reports child and membership blockers and never cascades into children or members. Reordering and hierarchy changes preserve explicit placements.
- Platform, Account, and Company template ownership is represented. Client creation is limited to Account/Company templates, while Platform creation is reserved for platform administration. Templates copy only structure metadata and position suggestions; preview reports conflicts, instantiation is atomic and independent, and real members/roles/credentials/permissions are excluded.
- The current disposable H2 schema is generated directly from these mappings. Per project decision, no Flyway history is maintained until persistent production-schema work begins.
- Mapping, service, Permissionizer boundary, tenant, lifecycle, membership, template, atomic-conflict, deletion, and HTTP isolation coverage passes in the complete backend suite: 285 tests, 0 failures, 0 errors, 0 skipped.

---

### ORG-002 — Organization Groups must stay outside automatic authorization

**Status:** `IMPLEMENTED — 2026-07-16`

**Context**

Generic Groups mirror customer organization folders. Names, positions, nesting, and membership must not become hidden security rules.

**Required direction**

- Group is only organizational data: member grouping, per-membership position/title, and parent/child folders.
- Do not infer permissions from Group name, position, nesting, membership, or template origin.
- Group membership grants no functionality or management authority.
- Group create/update/move/template operations may themselves require an Account/Company-level application permission, but changing the structure never changes a member's own effective permissions.
- Keep authorization scopes at Account and Company, plus separate B2B collaboration delegation where applicable.
- If future target-aware management references one member, set, or Group, model that as a separate explicit assignment with visible impact; never infer it automatically.
- Add regression tests proving Group membership, position, rename, and reparenting have no effect on the member's own roles/permissions.

**Implementation evidence — 2026-07-16**

- Organization persistence and services have no role, permission, override, or effective-permission mutation path. Group names, positions, nesting, membership, ordering, lifecycle, and template origin remain display/organization data only.
- Organization operations themselves use a dedicated `platform.organization` feature with unique Permissionizer action keys and an explicitly enabled guard boundary.
- Account/Company ownership checks and the B2B target-Company boundary control which organization data may be operated on without deriving authority from the Group tree.
- Regression coverage compares effective permissions before and after membership creation, position change, rename, and reparenting and proves the set is unchanged.

---

### AUTHZ-006 — Shell authorization cannot target one managed entity or subgroup

**Status:** `DESIGN DEFERRED BY PRODUCT DECISION`

**Evidence**

- `PermissionPolicy.evaluate()` accepts a generic context and `PermissionGuard` supports an explicit context, but the automatic Spring interceptor calls the no-argument-context check before method execution and does not pass service arguments.
- `HiveAppPermissionContext` has actor, Account, target Company, collaboration, and B2B state only; it has no target member/entity or management target set.
- `UserRolePolicy` validates actor permissions in Company context but never resolves whether the requested target is inside an explicitly managed set.
- Current member services validate current Account/same Account but not manager-to-target coverage. Account-wide list queries cannot represent a restricted management subgroup.

**Risk**

A generic management permission authorizes the action without defining which member or business records the actor may manage. Applying only UI filtering would leave direct IDs, searches, exports, bulk actions, and future module endpoints exposed to over-broad management.

**Required fix-phase direction**

- Choose the smallest explicit target model during implementation: single member, reusable management set, Group-target assignment, manager relationship, Company-wide target, or a deliberate combination. Group membership alone must never grant management.
- Keep Permissionizer annotation checks as coarse action gates, then resolve the target member/record and apply a consistent HiveApp target-management policy before reads/writes.
- For business records, resolve the record owner/subject member before evaluating management coverage.
- Access-management commands also enforce permission grantability, entitlement, protected targets, no self-escalation, and the actor delegation ceiling.
- Apply target restrictions in repository queries for list/search/count/export/bulk flows, not by post-filtering UI data.
- Target-assignment changes require impact preview, authorization refresh/cache invalidation where relevant, and actor-aware audit.
- Add direct-target, unrelated-member, subgroup, moved-member, Company-admin, owner-target, crafted-ID, list/export, and delegation-ceiling tests.
- Keep the Permissionizer core generic unless implementation demonstrates a concrete need for method-argument-aware context integration.

**Batch 5.3 design boundary — 2026-08-10**

- Do not add caller-supplied target IDs to `HiveAppPermissionContext` or authorization headers; the service must load the tenant-owned entity before a target check.
- Keep Permissionizer annotations as coarse action gates and use a HiveApp target authorizer for the loaded target when this capability is introduced.
- The same resolved target restriction must become a repository predicate for list/search/count/export/bulk operations; UI filtering is never the security boundary.
- Any management assignment is a separate, explicit security object. Organization Group names, hierarchy, membership, and positions never become authority automatically.
- Per the existing product decision to defer the manager-target building block, choose the concrete single-member/reusable-set persistence model with the first real module workflow rather than inventing an unused shell abstraction now.

---

### RBAC-006 — Direct member overrides cannot support the decided exception lifecycle

**Status:** `IMPLEMENTED — 2026-07-17`

**Evidence**

- `MemberPermissionOverride.decision` and its request/DTO use an unexplained boolean instead of `GRANT`/`DENY` language.
- The entity stores no reason, creator, expiry, lifecycle/result history, or actor-aware audit.
- Scope is limited to Company and cannot express the decided Account-wide exception scope.
- Current services do not prevent self-escalation or enforce the non-owner delegation ceiling.
- Current effective-permission calculation does not provide a source-aware explanation suitable for an access-detail UI.

**Required fix direction**

- Keep roles as the normal access mechanism and model direct overrides as explicit exceptions.
- Replace boolean decision with `GRANT`/`DENY`; require reason for both and expiry for `GRANT`, with optional expiry for `DENY`.
- Store explicit Account/Company scope and enforce target containment, actor delegation ceiling, no self-grant, protected targets, entitlement, feature lifecycle, and owner-only exclusions.
- Make applicable `DENY` win over role/direct grants. Enforce expiry and inactive-scope checks at authorization time.
- Build a source-aware effective-access read model showing role sources, grants, denies, scope, reason, creator, expiry/effect status, and history.
- Audit create/edit/revoke/expire/failed attempts and add cross-scope, expired-grant, deny-precedence, self-escalation, owner-target, and entitlement tests.

**Implementation evidence — 2026-07-17**

- The persisted boolean is replaced by explicit `GRANT`/`DENY`, with Account/Company scope and exact-scope uniqueness.
- Both decisions require a reason and creator. Grants require a future expiry; denies may be permanent or expire. Runtime resolution ignores expired exceptions and exceptions inside inactive Companies.
- Creation and removal reject owner targets and self-mutation, validate tenant/scope containment and code grantability, and enforce the actor's management action plus scoped delegation ceiling. Stale exception cleanup remains possible when a permission leaves entitlement.
- Exception reads expose decision, scope, Company, reason, creator, expiry, timestamps, and current effect for the future admin UI.
- Applicable active denies remove authority from both roles and direct grants. Owners remain protected from ordinary exceptions.
- Central actor-event history and failed-attempt audit remain part of the shared `AUDIT-001` work rather than a private exception log.
- Entity, service, Permissionizer-policy, effective-permission, lifecycle, abuse, and request-level scope coverage passes in the complete backend suite: 311 tests, 0 failures, 0 errors, 0 skipped.

---

### SUBSCRIPTION-001 — Subscription lifecycle terminology is inconsistent

**Status:** `RESOLVED — 2026-08-10`

**Evidence**

- `SubscriptionStatus` contains `ACTIVE`, `PAST_DUE`, `CANCELLED`, and `TRIALING`.
- Product documents refer to `EXPIRED`, but that value does not exist in the current enum.

**Risk**

Policies, UI filters, API contracts, tests, and documentation may handle terminal subscription states differently. An unknown or assumed state can accidentally grant or deny access.

**Required resolution**

Inspect plan policies, subscription services, migrations, tests, and frontend status handling. Either introduce a precisely defined expiration state or remove the stale terminology everywhere.

**Implementation evidence — 2026-08-10**

- `SubscriptionStatus` now has the canonical `TRIALING`, `ACTIVE`, `PAST_DUE`, `SUSPENDED`, `CANCELLED`, and `EXPIRED` vocabulary.
- `EXPIRED` is used for a trial that reaches its deadline; `CANCELLED` remains an explicit/replacement end, and paid expiry enters `PAST_DUE` until later recurring collection/recovery supplies a trusted payment outcome.
- Usable-subscription queries consistently mean `ACTIVE` or `TRIALING`; terminal/non-entitling states cannot occupy the Account's usable-subscription slot.

---

### SUBSCRIPTION-002 — Entitlement and override JSON is stored as untyped strings

**Status:** `RESOLVED — 2026-08-10`

**Evidence**

- `Subscription.customOverrides` and `Subscription.entitlementSnapshot` are JSON columns represented as `String`.
- Other JSON fields use typed values, such as `Feature.quotaSchema` and `PlanFeature.quotaConfigs`.
- Typed records already exist for the same subscription data: `SubscriptionOverrides`, `SubscriptionEntitlementSnapshot`, and `SubscriptionFeatureSnapshot`. The entity boundary therefore discards types that the application immediately needs to restore.

**Risk**

String-based JSON weakens compile-time guarantees, makes schema evolution and validation harder, and can allow billing, runtime entitlement, and UI read models to interpret the same snapshot differently.

**Verify later**

- Snapshot/override DTOs and serialization helpers.
- Billing, plan entitlement, quota, role-picker, and B2B policy consumers.
- Backward compatibility strategy for existing rows.

**Possible fix direction**

Use one versioned typed snapshot model and one shared parser/validator. Consider normalized tables only if querying, auditing, or migrations make JSON unsuitable.

**Implementation evidence — 2026-08-10**

- `Subscription.customOverrides` and `Subscription.entitlementSnapshot` are typed Hibernate JSON attributes rather than application-level strings.
- Both records carry an explicit schema version, normalize missing version `0` to the current version for the unpublished schema, and reject unsupported versions.
- Billing, entitlement, quota, catalog, B2B, and provisioning consumers now share the typed boundary. Reader tests cover null, structured, malformed, round-trip, and unsupported-version inputs.
- No Flyway migration or legacy backfill was added because HiveApp is unpublished and uses a disposable in-memory database; the generated schema is updated directly as agreed.

---

### PLAN-001 — Plan persistence constraints are weak at the entity level

**Status:** `VERIFIED AND RESOLVED — 2026-08-10`

**Evidence**

- `Plan.price` has no declared precision/scale and is nullable.
- `Plan.billingCycle` is nullable.
- `Plan.isActive` lacks `nullable = false`.
- `PlanFeature.addOnPrice` has precision/scale, but valid negative/null semantics depend on service rules.

**Risk**

Invalid plan rows can break price calculation, catalog display, subscription snapshot creation, or billing-cycle assumptions.

**Verify later**

Review migrations, request validation, plan services, seeders, and database tests before changing the schema.

**Implementation evidence — 2026-08-10**

- Verification confirmed that `plans(code)` already has a database unique constraint; an integration test now proves a duplicate insert fails even when the service pre-check is bypassed.
- Price and currency were made non-null with explicit precision in Batch 4.1. `billing_cycle`, lifecycle `status`, and optimistic-lock `version` are non-null in the generated schema, with direct SQL rejection tests.
- No Flyway migration was added under the agreed unpublished/disposable-H2 policy.

---

### PLAN-006 — Boolean plan state cannot implement the decided lifecycle

**Status:** `PARTIALLY RESOLVED — LIFECYCLE FOUNDATION IMPLEMENTED 2026-08-10`

**Evidence**

- `Plan` stores only `isActive`, so unfinished drafts, temporarily unavailable plans, and terminal historical plans cannot be distinguished.
- Existing admin operations toggle that boolean without lifecycle transition validation or an archived read-only state.
- Subscription snapshots prevent template changes from automatically updating existing customers, but current plan state does not clearly express whether new selection, editing, restoration, or deletion is valid.

**Risk**

Admins can expose unfinished plans, edit something intended as immutable history, or disable/delete the provisioning plan that registration requires. UI labels would have to guess lifecycle meaning from one flag.

**Required fix direction**

- Replace/migrate `isActive` to a constrained `DRAFT`, `ACTIVE`, `INACTIVE`, `ARCHIVED` lifecycle with explicit authorized transition commands.
- Validate non-empty feature composition, quotas, price/currency/billing cycle, and default-plan invariants before activation.
- Block new subscriptions to draft/inactive/archived plans. Preserve existing subscription snapshots until an explicit subscriber plan change occurs.
- Make archive terminal/read-only; reuse requires duplication into a new draft and a new valid code.
- Protect the configured default provisioning plan from deactivation, archive, or deletion until a valid active replacement is installed atomically.
- Add transition, invalid activation, default replacement, existing-snapshot continuity, archived mutation, authorization, concurrent selection/transition, and audit tests.

**Implementation evidence — 2026-08-10**

- `Plan.status` now uses explicit `DRAFT`, `ACTIVE`, `INACTIVE`, and terminal `ARCHIVED` states; creation starts in DRAFT and bootstrap templates start ACTIVE.
- Status changes use a validated command. Draft activation requires at least one valid included feature, draft-to-inactive and return-to-draft are rejected, archived plans are read-only/terminal, and only ACTIVE plans can receive new subscriptions.
- FREE remains the protected provisioning default and now uses a zero-priced MONTHLY recurring cycle so compatible paid monthly AddOns can be offered. Perpetual licensing remains deliberately deferred.
- Optimistic locking protects concurrent Plan edits. Focused lifecycle and full integration tests cover invalid transitions, activation composition, default protection, provisioning continuity, and persisted constraints.
- Atomic configurable replacement of the provisioning default, audit records, and the active-template revision workflow remain under PLAN-007/later lifecycle work.

---

### COMMUNICATION-001 — Unify notices, warnings and private messages

**Status:** `HISTORICAL DELIVERY — CONVERSATION INTERPRETATION SUPERSEDED BY COMMUNICATION-002`

- Shared `NOTICE`, `WARNING`, and `MESSAGE` channels are implemented with independent service/marketing purpose and in-app/email transport. Client actions are read/archive, acknowledge-seeing, and optional private reply; none execute a subscription change, accept a price or resolve a business problem.
- Admin API `/api/admin/customer-communications` supports drafts, version-checked edits, selected-Account lookup/audience review, explicit publish/schedule, withdrawal, paginated delivery/read/acknowledgement/reply results, failed/suppressed-email retry, replies and thread closure/reopening. Client API `/api/v1/communications` supports the own-Account inbox, detail, per-user interactions, private replies and owner-controlled marketing opt-ins. Authorities are distinct from the existing credential-email monitor.
- Content-version and repricing workflows index safe source-linked entries and reuse their original read receipts; required-email gates and legacy detail APIs remain authoritative. Capacity reductions as well as removed Features produce warnings. Backfill is bounded to 100 per source per pass, with one source-row-locked transaction per entry. Recipient counts use grouped queries, not one count query per row.
- The admin composer stages content → selected Accounts → review, saves a draft, then publishes separately. The client inbox separates three channels and read/acknowledge/reply controls. Shared tables, pagination, dialogs and session-scoped query caches are reused; private bodies are redacted from audit payloads.
- Live verification in a disposable H2 environment: save/publish a message, client receipt/reply, admin reply/close, client unread/closed state, Arabic dark mode at a narrow viewport with no horizontal overflow. Temporary servers/tabs are removed after verification; no email is sent to real recipients.
- Verification: clean full backend suite **945 passed, 1 skipped, no failures/errors**. After the final source-warning/recipient-state fixes, focused communication, Plan application/rollout and repricing regressions passed **63 tests, 1 skipped**; the combined Surefire inventory is **946 passed, 1 skipped**. Frontend **435 tests passed**, with lint, typecheck and production build passing. Security coverage includes cross-Account and B2B isolation, separate publish/marketing authorities, source-specific read permissions, private reply/audit boundaries, idempotent receipt/reply commands, stale versions, cancelled/expired delivery claims and marketing opt-outs.
- Production schema migration/validation and real SMTP deployment remain explicit gates; this is not a promise of automatic Campaign/Offer sends, push/SMS, attachments, client-initiated tickets, real-time chat, legal consent evidence or a throughput benchmark.

### COMMUNICATION-002 — Contextual, scoped and event-driven notifications (not conversations)

**Status:** `IMPLEMENTED AND LOCALLY VERIFIED — PRODUCTION VERIFICATION GATES OPEN 2026-09-22`

- Correct COMMUNICATION-001's product interpretation: remove live chat/replies/threads and reuse its durable delivery and per-user interaction infrastructure for one-way information, warnings, offers and contextual business notifications.
- Expand beyond account broadcasts: personal/member, own-account and platform-operator recipients, source-authorized content/actions, account-internal sending and separate B2B-party notifications. Freeze bounded audiences for sends; recheck live membership/authorization on visibility and dispatch. Notifications never confer authority or execute their source action.
- Implement typed, idempotent transactional event publishing with actual membership/B2B/billing/operational integrations, retaining Plan/repricing notices and source receipt identities. Future tasks/approvals integrate through the same contract; their business modules are not fabricated here.
- Add contextual sending/history, notification inboxes for clients and operators, source-aware presentation/actions and scoped preferences. Retain opt-in marketing and normal security-email boundaries. Do not equate an acknowledged warning with a resolved domain problem.
- Verification required: cross-account/admin-client/B2B isolation, revoked membership/permissions, rollback and duplicate events, concurrent fan-out/read/delivery, scheduled/expired/cancelled entries, retry/lease recovery, source-action authorization, schema upgrade and database constraints, bounded paging, frontend tests/build and real browser flows. Record actual production deployment gates rather than treating H2 tests as a production certification.
- Backend implementation: registered typed producers, transactional event outbox, PostgreSQL advisory idempotency locks (bounded process locks for H2 only), database audience constraints, bounded retry/backoff, email claim recovery, per-user receipts/preferences, and paginated client/operator APIs. Versioned operator recovery covers event processing and automatic event email separately; retries cannot replay the original payment/collaboration action. Internal send commands freeze content and all recipients, not just an individual recipient's delivery.
- Real integrations cover member creation/access status, B2B request/status transitions including automatic resume, confirmed/manual invoice settlement and failed charge attempts, plus existing Plan/repricing notices. Tests cover a typed future-task extension without fabricating a task module. Offer announcements require an actual eligible public Offer and separate marketing authority/consent; dispatch rechecks availability in an isolated read transaction so an unavailable Offer can be suppressed without rolling back the delivery state.
- Frontend implementation: shared client/operator inbox, contextual business actions, unread header shortcut, reviewed selected-member send, per-topic optional preferences, Offer-to-composer shortcut, safe operational delivery/retry tables, French/Arabic interface copy, explicit refresh and foreground polling. No chat/reply UI. The UI/UX and React skills guided reuse of existing components and progressive disclosure rather than a separate messaging product.
- Browser verification on a disposable H2 database: real admin/client login; member creation appearing in the event delivery table and client inbox; manual warning acknowledgement leaving it unresolved/non-archivable; sending to two reviewed members yielding an accepted result and a personal inbox entry; unread count and authorized member-screen action; French light/dark and Arabic narrow-width layouts with no horizontal overflow. No real external email was sent.
- Automated verification: full backend suite **963 passed, 15 skipped, no failures/errors** (the skips include opt-in PostgreSQL checks). After automatic-email recovery was added, the focused suite passed **16 tests, 15 opt-in PostgreSQL skips**; the combined Surefire inventory is **964 passed, 16 skipped, no failures/errors**. Frontend **441 tests passed**, plus complete lint, typecheck and production build. Coverage includes rollback, concurrent idempotent publication/delivery, frozen complete send audiences, company/personal/Account/operator isolation, revoked membership/source access, acknowledgement versus resolution, optional preferences versus mandatory warnings, stale retry commands, Offer retirement during email claim, and reviewed UI sends. Email recovery tests prove metadata-only results, required reason/version, cross-surface rejection, live recipient revalidation and refusal to resend a resolved warning; the UI test exercises the separate email recovery endpoint.
- Production release gates remain **open**, not waived by local tests: run the PostgreSQL concurrency/integration and repeatable-upgrade tests; restore a representative backup and apply/validate the SQL with old writers stopped; validate SMTP credentials, sender configuration, lease recovery and actual delivery in staging; set monitoring for failed/old pending events and emails, tune rate/backlog limits with representative load, and establish retention/backup/recovery policy. No throughput or exactly-once external-email claim is made. Delivered events/send-command deduplication records are not automatically purged: deleting them blindly could re-enable duplicate sends. Automatic producer bodies currently use French text; the UI is localized, but a locale-aware producer-template policy remains a follow-up before multilingual outbound deployment.
- PostgreSQL verification is opt-in and isolated in randomly named schemas: from `backend`, set `HIVEAPP_NOTIFICATION_TEST_PG_URL` to a JDBC PostgreSQL test-database URL, plus `HIVEAPP_NOTIFICATION_TEST_PG_USER`/`HIVEAPP_NOTIFICATION_TEST_PG_PASSWORD`, then run `mvn -Dtest=NotificationPostgresIntegrationTest,NotificationPostgresMigrationTest test`. The tests create/drop only their own schemas; the credentials must be for a disposable test database with schema-creation rights, never production. These tests are skipped without those variables. Docker/OrbStack was stopped locally; it was not started without the requested user approval.
- Delivery commits: decision/scope `f4ba257`, backend `3cb970a`, frontend `412b0fa`. Only the existing Decisions/TOFIX planning files were updated. No subagents or remote push; temporary verification servers/tabs were stopped/closed.

### COMMUNICATION-003 — Final integration audit and Permissionizer follow-up

**Status:** `OPEN — ACCEPTED FOR IMPLEMENTATION 2026-09-22`

The final read audit found gaps beyond the locally verified happy paths in COMMUNICATION-002. Fix each item in a separate tested commit, in dependency order; preserve tenant/source authorization and existing commercial consent boundaries.

- [x] **AUTH-1 — Typed permission references:** notification definitions/guards use generated Permissionizer references; strings remain at persistence/API boundaries. Generated large-map type arguments are explicit to avoid javac inference stalls when consuming the generated tree. Verified 29 notification/publication tests, the Permissionizer suite and a 150-action same-compilation regression.
- [x] **AUTH-2 — Explicit manual enforcement:** read aliases now use the standard `PermissionGuard.check` failure contract. A real Permissionizer interceptor test rejects every public operation in the three notification-facing services before domain access; a read-alias test proves reuse of the intended grant (2 tests passed).
- [x] **AUTH-3 — Registry validation:** notification permissions are checked against the installed registry at startup and whenever a definition is used. Unknown extension permissions fail explicitly; registered future-module permissions remain supported (3 catalogue and 14 integration tests passed).
- [x] **AUTH-4 — Mandatory policy ordering:** documented first-decision-wins at chain construction and added a configured-chain regression using real runtime and plan policies. Disabled/unknown actions, absent entitlement and scoped role denial beat ordinary grants; an entitlement never grants by itself (23 policy tests passed). Recipient-specific email context remains explicit.
- [x] **CONTRACT-1 — Action destinations:** ACTION/OFFER publication requires a resource, source permission and internal code-owned destination; malformed/external paths are rejected before persistence. Future-module registration and missing-resource regressions pass (5 focused tests).
- [x] **LIFECYCLE-1 — Cancelled billing:** checkout cancellation withdraws client/operator warnings transactionally, including events delivered after cancellation. Cancelled history stays readable but cannot be acknowledged or emailed; no receipt is invented. Billing-ledger and notification integration suites passed (19 tests).
- [x] **DELIVERY-1 — Email-only preferences:** optional feed preferences no longer block authorized direct detail; email includes the item link and company scope when applicable. Marketing links to its source without overriding in-app consent; payment failures open invoice operations. Backend notification/publication suites (28 tests), UI wiring (12 tests) and typecheck passed.
- [x] **DELIVERY-2 — Expired queue entries:** the bounded due query now picks expired/withdrawn pending items even during backoff; claim processing marks them terminal without transport. Regression covers pending backoff and abandoned sending leases (17 notification integration tests passed).
- [x] **LIFECYCLE-2 — Offer availability:** bounded inbox/detail projections recheck current Offer eligibility, deduplicated per account/Offer in each page. Unavailable/withdrawn items lose their action and show the correct state; source endpoints still enforce acceptance. Backend eligibility regression and 17 communication UI tests passed.
  Integration follow-ups: defer lookup construction to avoid the subscription → source adapter → Offer service cycle; use a non-throwing availability query on the caller's connection instead of borrowing a nested connection. Expected unavailability cannot roll back durable email suppression. Full Spring startup and 26 Offer/state tests passed, including retirement reflected in inbox detail and persisted suppression.
- [x] **FLOW-1 — B2B party-specific messages:** requester receives a request-sent notice and provider receives the incoming action. Both pending notifications resolve on the next authoritative transition; source permissions remain unchanged. Audience and catalogue tests passed (4 tests).
- [x] **FLOW-2 — Internal provenance/history:** server-derived sender names accompany internal notices; separately authorized, bounded sent history exposes only the actor's own-Account announcements and aggregate delivery counts. No recipient identities/read surveillance or reconstructed legacy history. Notification integration/boundary/Offer suites (21 tests), 13 UI wiring tests and typecheck passed. Upgrade SQL includes provenance fields/indexes.
- [x] **OPS-1 — Diagnosable recovery:** operations expose safe failure categories, creation/update times, event correlation and separately authorized billing links. Transport-only readers receive no message/recipient payload or business link; withdrawn events cannot retry. Privacy and notification integration tests (19), 13 UI tests and typecheck passed.
- [x] **UI-1 — Live detail state:** selected detail now polls in the foreground alongside the list and hides stale content on a failed refresh. A UI regression verifies refresh removes obsolete acknowledgement actions (communication wiring suite passed).
- [x] **CONTRACT-2 — Priority:** persisted `NORMAL`/`HIGH` priority is exposed independently of kind/topic. Code-owned definitions assign urgency; manual service warnings are high, marketing cannot request it. UI shows Important only while relevant. Catalogue/integration suites (22 tests), 13 UI tests and typecheck passed; upgrade backfills service warnings.
- [x] **CONTENT-1 — Localized automatic content:** automatic producers use typed French/Arabic templates; API content follows request locale and email follows the recipient's explicit language preference (French fallback). Manual content is unchanged. Template, locale/isolation, guard and producer tests (23), 14 UI wiring tests and typecheck passed; no arbitrary translation service or external payload processing is used.

Additional UX checks: payment-failure links should lead to the relevant operational billing context, not merely a printable document; preference controls must explain supported/mandatory delivery; maintain accessible responsive sender/history/recovery screens.

Production PostgreSQL/SMTP/load/retention gates in COMMUNICATION-002 remain open until actually exercised; no throughput, delivery or migration certification follows from source changes alone. Do not create additional planning documents or push commits.

### PLAN-007 — Active plan edits have no revision or subscriber-effect workflow
<!-- Shared notifications are tracked independently in COMMUNICATION-002; Plan notice delivery alone is not the communications product. -->

**Status:** `PLAN VERSION APPLICATION WORKFLOW RESOLVED 2026-09-22 — BROADER RENEWAL/GRACE AND PRODUCTION DEPLOYMENT GATES REMAIN OPEN`

**Original evidence — before the immutable revision foundation**

- `updatePlan()`, `assignFeature()`, `updateFeature()`, and `removeFeature()` mutate a Plan/PlanFeature directly without checking lifecycle or creating a commercial revision.
- Existing subscription snapshots remain unchanged, but the backend has no revision lineage explaining that new and old customers now received different terms from the same mutable template identity.
- Plan creation can inherit another plan's composition, but there is no explicit duplicate/revision command and no rule excluding subscribers/history/Account overrides because those relationships are simply outside that copy helper.
- Admin APIs can list current subscribers and manually create/update one Account subscription, but there is no selected/bulk plan-change operation, renewal policy, scheduling, notification, usage-aware plan-wide preview, or execution job.

**Risk**

Admins can change what Plan X means for future customers without a durable revision boundary, while having no safe platform feature to apply, schedule, explain, cancel, or monitor corresponding changes for existing customers.

**Required fix direction**

- Make active commercial configuration immutable. Support explicit revision/duplicate into `DRAFT` with new identity/code and optional lineage to the source plan.
- Permit live metadata-only edits separately and audit them.
- Copy commercial configuration only; never copy subscribers, subscription/audit history, or Account overrides.
- Add publish/effect targeting for future-only, one/selected/all Accounts immediately, next renewal, and scheduled bulk plan changes. Subscriber changes create new validated snapshots/history rather than mutating old terms in place.
- Introduce feature-owned usage/impact contributors and mandatory preview/recheck for access, price, quota conflicts, dependent data/workflows, grace/remediation, and notification state.
- Add an explicit renewal-management surface independent of new-sale activation: continue/change/end/manual-review policy, per-Account exceptions, restricted post-end state, scheduling/cancellation, job progress, idempotent retry, partial failure, and audit.
- Build communication as a reusable capability attachable to these operations: audience, template/message, channel, timing, delivery status, and retry. Do not hard-code individual marketing strategies.
- Use clear product terminology such as `change subscribers to another plan`; reserve `migration` for technical database/schema work.
- Add active-mutation rejection, revision-copy boundaries, future-only publish, selected/renewal/bulk targeting, usage conflict, stale preview, concurrent renewal, notification, retry, and audit tests.

**Implementation evidence — 2026-08-10**

- Published (`ACTIVE`, `INACTIVE`, or `ARCHIVED`) Plan commercial configuration is immutable. Basics and feature composition can change only while the Plan is a `DRAFT`; published changes require an explicit draft revision.
- Every Plan has a durable lineage UUID, unique revision number, optional source Plan, and creation reason. `revise` continues the source lineage; `duplicate` starts an independent lineage; both copy only Plan-owned feature modes and quota configuration into a new draft.
- Generic creation is now explicitly empty rather than silently inheriting FREE. Subscription snapshots store the Plan revision number as their definition version, so accepted terms identify the exact published revision.
- Selected/filtered bulk subscriber changes, scheduled execution jobs, renewal policy, reusable communications, and audit remain later operational work. Current subscribers continue changing only through the existing one-Account previewed operation.

**Historical gap and reopened discussion — 2026-09-21 (superseded by final delivery below)**

- The accepted implementation plan lives in `PLAN-FLOW-005` of `FLOW_DECISIONS.md`, covering `PLAN-013`, this issue and `PLAN-011`. It includes family/version coexistence, explicit public-version selection, preserved financial terms, grouped conflict resolution, simpler admin views, backend/test/frontend delivery slices and verification gates. Acceptance on 2026-09-21 authorizes implementation, not a claim of completed delivery.
- The 2026-08-10 implementation note above is historical: selected-Account scheduled change jobs have since shipped under `PLAN-011`. They do not yet provide a content-only Plan-version rollout that preserves the paid period and existing agreed financial terms without another checkout.
- The remaining subscriber-effect rules under `PLAN-FLOW-005` were accepted on 2026-09-21. The immutable version foundation remains unchanged. Content-only execution and population-backend foundations are tracked below; the complete notified administrator workflow remains open.
- Settle retained pricing/financial provenance versus new entitlement version, full-subscription add-on/pack compatibility, accepted Offer/policy terms and private agreements, usage conflicts, pending-operation concurrency, audience selection and timing. Publication must remain separate from changing subscribers and from stopping previous-version sales.
- Implement Plan-owned composition/base-limit changes first, including compatibility checks against purchased extensions. Do not silently widen this phase into editing add-on or capacity-package definitions.
- Required regression coverage after approval: all three timings, paid-period preservation, no implicit checkout, unchanged price/promotion evidence, duplicate paid features, missing quota owners, finite/unlimited and below-usage transitions, dependency/exclusion failures, active private agreements, stale previews, concurrent renewal/repricing, cancellation, idempotent retry and mixed per-Account outcomes. Verify authorization remains enforced after both entitlement gain and loss.
- Delivery order after design approval: backend, backend tests, then frontend workflow/tests/browser verification. Keep this issue partially resolved until the operational flow is actually delivered and verified.

**Content-only execution foundation — 2026-09-22**

- Added dedicated `preview_version_application` / `apply_version` permission nodes and single-Account `version-preview` / `apply-version` APIs. Immediate application requires actor-bound, expiring evidence, current catalogue/registry fences, an Account lock, a reason and an idempotent command ID. It neither calls the payment activation flow nor creates a checkout.
- Schema V5 separates retained financial Plan provenance from effective content version. Applying content keeps the subscription ID, financial-terms identity, current amount/currency/cycle, paid-period identity/boundaries, purchased extensions and accepted promotion evidence. Append-only content evidence links prior evidence, source operation, original financial terms, planned/actual time and billing period. Subsequent normal renewal and explicit extension edits retain that financial source instead of mislabeling a V1 tariff as V2's price.
- The assessor reuses catalogue compatibility, registry veto, policy restrictions and usage checks. Duplicate paid capabilities, missing/nonfinite quota owners, below-usage reductions, changed purchases, private agreements and another pending commercial operation are conflicts, not automatic removals or repricing.
- The internal reviewed executor covers now, individual successful renewal and a fixed not-before date, with fresh actor/runtime authorization and reviewed-term checks. Scheduled instructions do not occupy the normal renewal/payment slot. The population APIs are the next checkpoint below; notifications, full load verification and their frontend remain **OPEN**.
- Deployment must supply DDL for the public-version/content-evidence tables and new nullable subscription/operation columns before using production `ddl-auto=validate`; disposable local/test schema generation is not a production migration.
- Verification: the full backend suite passes **909 tests** (0 failures/errors). Coverage includes future invoices identifying the retained financial definition, successful paid renewal, later extension edits, authorization gain/loss, stale reviews, pending repricing and idempotent replay. This verifies the execution foundation, not the still-open population/UI workflow.

**Content-only population backend — 2026-09-22**

- Added `/api/admin/plan-version-applications` create/assess, list/detail/results, confirm, cancel, retry and identity-resolution APIs with separate permission nodes. Source scope is one version or its family; audience is selected, filtered or all matching current subscriptions. Lifecycle/currency/cycle/search filters and exclusions are explicit. Audience membership freezes before asynchronous assessment; later arrivals are not added.
- Reused the existing job tables/scheduler, with typed content payloads and per-Account immutable assessments. Freeze is bounded at 10,000 scalar targets, with batched writes; assessment/execution passes are bounded at 50 Accounts, counts are aggregate queries, and results are paged. The existing scheduler's configurable default cadence is one second; future waiting jobs defer until their next eligible check instead of busy-polling every Account.
- Partial confirmation is explicit. Excluded/already-on-target outcomes stay visible. Competing confirmed jobs are blocked. Cancellation preserves applied results and cancels only unfinished work; technical failures can retry the original instruction, while authorization/usage/terms conflicts require new review. Result payloads do not expose internal accepted billing/promotion snapshots; identity resolution remains separately authorized.
- Verification: the full backend suite passes **919 tests** (0 failures/errors), including 10 new rollout integration tests. Coverage includes a 251-Account frozen review (51 assessed, 200 explicitly excluded), mixed outcomes, paid-term preservation, cancellation after partial success, fixed-date/renewal waiting, stale review, concurrent duplicate workers, revoked actors and technical retry. The 100/1,000/10,000-Account performance benchmark remains a separate verification gate; batching is not itself a throughput claim.
- Still **OPEN**: notification policy/delivery gating and client notice projection, guided frontend and subscriber presets, remaining compatibility/security/load audits and browser verification. Do not mark `PLAN-007` complete from these operational APIs alone. Production DDL must also accommodate the typed job/item columns and indexes; legacy purchase selection/assessment columns become nullable with mutually exclusive application validation.

Content-notification checkpoint (2026-09-22):

- Backend support now includes Account-private in-app notices, optional email and explicitly required email dispatch, using the existing durable dispatcher rather than a second scheduler. Required-dispatch failure preserves the old version; explicit retries retain delivery attempts and resume only notification-blocked instructions. Current-owner verification is rechecked, cancellations preserve applied outcomes, and read receipts are idempotent and never consent.
- The content target must retain subscription-portal access. Removing that capability is an explicit review conflict so clients do not lose access to their own commercial notices; no runtime Plan veto is bypassed.
- Verification: the full suite passed **923 tests**, with one opt-in benchmark skipped. A subsequent focused run passed 16 tests after adding recipient-verification rechecks and abandoned-mail lease/stale-completion coverage. Client scoping/read receipts, required-email failure/retry, optional suppressed email and cancelled future notices are covered; the frontend is not yet accepted.
- The explicit pre-notification 100/1,000/10,000-Account benchmark passed on local H2: audience freeze **356/211/1,132 ms**, assessment **6,157/27,709/276,521 ms**, application **5,685/22,917/395,067 ms**. Scheduler passes were invoked without their normal delay. This measures the population engine before notice creation was added, not production PostgreSQL or VPS request capacity; final notice-inclusive performance remains a separate verification gate.

Management-read checkpoint (2026-09-22):

- Added separately authorized, bounded family-subscriber presets (all/current/other versions, confirmed pending changes, unresolved conflicts), including retained amount and period end. Family search escapes wildcard characters; projections and counts do not load every subscription snapshot.
- Added family-wide version/application audit history with date/type/actor filters, batch actor labels and no raw audit payloads. Conflict groups now include a typed resolution category and result pages can filter by the primary blocker.
- Verification: **19 rollout integration tests passed**, with the opt-in scale test skipped, including family isolation, client rejection, page bounds, pending/cancel/retry behavior, protected history payloads, system actors and conflict filtering. Guided UI and final end-to-end/load verification remain in progress; this checkpoint does not close the overall workflow.

Final backend verification (2026-09-22):

- Client notices and operator impact now expose both added and removed capabilities. Older persisted impact JSON without `addedFeatures` remains readable as an empty list. Explicit exclusions outside the frozen audience fail validation instead of being silently ignored.
- The final full Maven run passed **930 tests**, with **0 failures/errors** and **1 opt-in benchmark skipped** (931 discovered). The separate notice-inclusive 100/1,000/10,000-Account benchmark also passed: freeze **240/116/493 ms**, assessment **2,610/10,230/208,512 ms**, application **2,862/14,322/373,878 ms**, and 20-row subscriber reads **44/9/27 ms**. Each applied Account had exactly one persisted notice. These are local H2 measurements with scheduler passes invoked directly, not isolated production PostgreSQL, real SMTP delivery or VPS throughput evidence.

**Final operator/client delivery and acceptance — 2026-09-22**

- The previous checkpoint references to unbuilt notices, audience UI, management presets, history and load verification are superseded. The accepted Plan-specific workflow is delivered; the larger original renewal/grace umbrella is not silently closed.
- Administrators choose a source version/family, target published version, selected/filtered/all audience, explicit exclusions, timing and notice policy. The guided flow remains local until analysis; analysis freezes the audience, then an expiring actor-bound review requires confirmation (including explicit partial-success acknowledgement). No direct save skips the review or changes the customer's financial terms.
- Family subscriber views expose current/other versions, pending changes and accounts to review. Result tables are paged, permission-gate identities independently, group conflicts, show gained/lost features and before/after capacity, and link to separately authorized subscription operations. Background progress refreshes through the final result; successful retry clears an obsolete conflict filter. Cancel, technical retry and notification retry remain distinct audited actions with required reasons.
- Client notices are Account-private, separately permissioned from pricing notices, and show the version change and retained financial terms without internal administrative reasons. Read-only clients can inspect them without acquiring mark-read rights. Marking read is explicit, idempotent and not acceptance/consent.
- Final frontend verification: **425 tests passed**, **0 failed**, lint (313 files), TypeScript checks and production build succeeded. Tests cover restricted/read-only routes, secondary-query gating, stale/partial review, history/system actors, final result refresh/filter reset, Arabic copy and client notice/receipt permissions.
- Real browser on isolated disposable ports 5173/8081 verified V1/V2 coexistence, version creation/comparison/public-choice separation, explicit V1→V2 application with unchanged amount and paid period, client notice/mark-read, and required email suppression preserving V2 instead of applying a V1 return. Explicit email retry resumed the same instruction and displayed its updated row. Desktop/narrow layouts, light/dark and the new French/Arabic surfaces were checked. Older shared French-only pages and seeded English names are not claimed fully localized; no real email was delivered by the dev fallback.
- **Remaining deployment gates:** provide/review production DDL and indexes for public version, content evidence/notices and typed job/item data; validate upgrade/backward compatibility on PostgreSQL before `ddl-auto=validate`. Configure and verify real SMTP transport before promising required-dispatch operations. Run environment-specific load/capacity tests; the 10,000-account bound and local background timings are not a 1,000 requests/second claim.
- **Remaining product scope:** broader renewal policy/expiring exceptions and feature-specific grace/remediation stay open under their existing decisions. Conflicts preserve the old version; this release does not invent grace or remove purchased products. Add-on and capacity-package definition-change workflows are the explicitly later phase. Existing financial repricing remains a separate reviewed operation.

---

### BILLING-001 — Client self-service activates paid plans without payment or approval

**Status:** `RESOLVED FOR CLIENT ACTIVATION — 2026-08-10`

**Evidence**

`SubscriptionServiceImpl.applyChange()` locks the account, validates usage conflicts, cancels the current usable subscription, and inserts a new `ACTIVE` subscription for the selected plan. There is no checkout session, payment confirmation, invoice, admin approval, contract check, or internal "unpaid/pending" state. Seeded PRO and ENTERPRISE plans have non-zero prices.

A `PaymentGateway` abstraction and always-successful `DevPaymentGateway` exist, but no production code calls them.

**Risk**

Any client member granted `platform.subscription.apply` can activate a paid entitlement immediately. `currentPrice` is a calculation, not collected money, but the authorization system treats the subscription as active.

**Required fix direction**

Until real billing exists, paid client changes must remain pending until a real payment, contract, or authorized operator confirmation event. Only deliberately configured free changes may auto-activate; a clearly marked non-commercial demo mode may be supported separately. Never present calculated price as paid revenue.

- Limit commercial detail/actions to the Account owner and separately authorized Account-scoped subscription actors. Ordinary members receive only work-relevant capability/usage visibility.
- Restrict client selection to active, publicly sellable, code-valid plan/add-on/quota options. Never allow self-service internal/non-sellable features, arbitrary exceptions, unlimited/custom quotas, free paid features, or negotiated pricing.
- Store the request, confirmation source, actor, before/after snapshot, and effective time. Revalidate authorization, commercial confirmation, eligibility, and impact under the Account lock immediately before activation.
- Offer both **now** and **at renewal** for upgrades and downgrades. Run the same effective feature/quota/workflow/usage impact preview for either direction; a more expensive plan can still remove a capability. Immediate execution rechecks under the Account lock, while renewal creates a cancellable pending operation and rechecks at cutoff. No plan change may silently delete customer data.

**Implementation evidence — 2026-08-10**

- A positive-price client request now creates a durable `AWAITING_CONFIRMATION` change operation and one checkout containing the Account, requester, exact Money amount/currency, before/target snapshots, timing, and gateway-attempt evidence. The current entitlement remains unchanged.
- Only an explicitly zero-priced change can activate without checkout. Immediate activation and renewal execution share the same Account lock, current-subscription, Plan/AddOn/package-version, and feature-owned impact rechecks.
- The guarded admin subscription surface can list Account change operations and manually confirm external settlement/contract evidence. Confirmation stores its operator, source, unique reference, reason, and time; retrying the same reference is idempotent.
- Confirmed immediate changes activate only after the final recheck. Confirmed future-renewal changes remain pending until their effective time and are rechecked again by the renewal processor. Unconfirmed renewal checkouts can be cancelled without changing entitlement.
- This closes unpaid client activation; it does not claim collected revenue or implement a provider/webhook, invoice/tax ledger, refund/credit workflow, or recurring payment recovery. Those remain separate billing work.

---

### QUOTA-002 — Client quota overrides can request unlimited capacity for free

**Status:** `RESOLVED FOR SELF-SERVICE — 2026-08-10`

**Evidence**

- `QuotaOverride.limit == null` means unlimited.
- Client `previewChange()`/`applyChange()` accept quota overrides through `SubscriptionChangeRequest`.
- Validation permits a null limit and does not require the plan quota's `pricePerUnit` to be present.
- Conflict detection skips null limits.
- `BillingCalculator` charges nothing when the requested limit is null or the base quota has no `pricePerUnit`.
- `QuotaEnforcer` converts the null override to `OVERRIDE_UNLIMITED` and bypasses the quota check.

**Risk**

A client can turn a fixed, non-bumpable quota—including FREE-plan member/company limits—into unlimited capacity without charge.

**Required fix direction**

Client self-service may select only versioned predefined quota packages explicitly offered by the effective Plan/AddOn, within repeatability and maximum-purchase rules. Unlimited/custom-negotiated exceptions are operator-only and explicit, never inferred from null. Validate entitlement, quota ownership, package version, effective limit, usage impact, pricing/currency/cycle, and payment/approval before activation.

**Implementation evidence — 2026-08-10**

- Removed arbitrary `QuotaOverride` values from client and admin subscription-selection contracts. Requests now contain only quota package code plus a positive quantity.
- Selection requires an ACTIVE package explicitly attached to the effective Plan or selected AddOn, a finite included owner for the exact feature/resource pair, matching currency/cycle, and quantity within repeatability/maximum rules.
- Unlimited capacity is an explicit included-limit mode and cannot be requested through package selection. Custom/negotiated Account exceptions remain a separate operator-only future capability.
- Package price is included in the immutable subscription snapshot and recurring calculation. Payment/approval before commercial activation remains tracked by BILLING-001 rather than being misrepresented as solved here.

---

### SUBSCRIPTION-003 — Subscription periods and lifecycle transitions are not implemented

**Status:** `PARTIALLY RESOLVED — RENEWAL/GRACE AND ONE-ACCOUNT LIFECYCLE IMPLEMENTED 2026-08-31`

**Evidence**

- No production code sets `Subscription.currentPeriodEnd`.
- Entitlement expiration is checked only when that field is non-null, so current subscriptions never expire.
- `PAST_DUE` and `TRIALING` have no creation/transition workflow; cancellation occurs only as part of replacement.
- There is no renew, cancel-at-period-end, immediate cancel, suspend, restore, payment-failure, or expiration command.
- `BillingCycle` does not drive period creation or renewal.

**Risk**

The current model looks commercially complete but behaves as permanent manual entitlement. UI lifecycle controls, revenue, renewal, and access revocation would be misleading.

**Required fix direction**

Define the lifecycle state machine and actor/event for every transition. Store unambiguous period instants, preserve history, and test runtime authorization at renewal, expiry, past-due, cancellation, and restoration boundaries.

**Implementation evidence — 2026-08-10**

- Every new subscription now receives UTC `Instant` period bounds derived from its billing cycle, and every period stores its immutable entitlement snapshot separately from the mutable current pointer.
- A scheduled lifecycle worker expires due trials, completes and renews zero-priced periods, places paid periods in `PAST_DUE` without pretending payment succeeded, and closes replacement/cancel-at-period-end history.
- Immediate and at-renewal plan changes are explicit operations. Renewal changes stay pending and cancellable, revalidate commercial availability and usage at execution, and enter `NEEDS_ATTENTION` instead of silently applying when conditions changed.
- Tests cover trial expiry, free renewal/history, paid payment-due behavior, immediate replacement, pending renewal creation, and cancellation. Suspension/restoration, payment recovery, customer cancellation commands, grace policy, and communications remain later operator/billing flows rather than being claimed here.

**Implementation evidence — 2026-08-31**

- A paid period boundary creates one system-owned same-terms renewal operation, immutable Invoice,
  Payment attempt, and durable provider command through the ordinary billing ledger; an explicit
  pending at-renewal change remains authoritative and suppresses the default renewal.
- Failed or pending collection moves the current subscription to `PAST_DUE` with persisted
  `pastDueAt` and `graceEndsAt`. Entitlement remains valid only before that exact deadline, after
  which scheduled processing moves it to non-entitled `SUSPENDED` without deleting customer data.
- Trusted provider evidence or separately authorized manual settlement activates the already
  invoiced next period from its original renewal boundary. Manual settlement can recover a failed
  Checkout/operation while retaining the failed automatic Payment evidence.
- Current admin/client read models include recovery timestamps.
- One-Account cancel-at-period-end, keep-renewing, immediate cancellation, operator suspension,
  restoration, and collection-grace extension use independent permissions, actor/action/version-
  bound signed reviews, required reasons, locked mutation, and append-only bounded history.
- Collection suspension cannot be restored through an operator status flip. Operator suspension
  retains its prior entitled state only for bounded restoration before term end.
- Suspension and immediate cancellation revoke every current Account member access and refresh
  session; restoration does not reactivate old tokens. Terminal subscriptions remain readable.
- Reviewed trial/new-entitlement creation, population lifecycle jobs, communications, and
  filtered/Plan populations remain open.

---

### SUBSCRIPTION-004 — Trial subscriptions are authorized but invisible to client subscription flows

**Status:** `RESOLVED — 2026-08-10`

**Evidence**

`PlanEntitlementService` and `QuotaEnforcer` fall back from ACTIVE to TRIALING, but `SubscriptionServiceImpl.getSubscription()` only loads ACTIVE. Catalog, preview, apply, and `/subscriptions/me` all depend on that ACTIVE-only method.

**Risk**

A trial account can use entitled APIs yet receive "subscription not found" when trying to view or manage the subscription.

**Required fix direction**

Create one authoritative "usable subscription" query/state rule and apply it consistently across authorization, quota, admin, and client flows. Define what trial users may change and how conversion works.

**Implementation evidence — 2026-08-10**

- Repository and service reads use the shared `ACTIVE`-then-`TRIALING` usable-subscription rule.
- Client subscription/catalog DTOs expose the trial status and UTC start/end bounds; integration coverage proves a trialing Account can read and use the same subscription surface consistently.
- The direct admin trial-creation route was retired in Phase 10 together with other subscriber-affecting shortcuts. A reviewed, first-class trial operation remains under `PLAN-011` for Phase 12 rather than reopening the unsafe route.

---

### SUBSCRIPTION-005 — Admin overrides can grant out-of-plan features with no defined price

**Status:** `RESOLVED BY CONTRACT CHANGE — 2026-08-10`

**Evidence**

`updateOverrides()` validates that an added feature is code-defined, plan-assignable, active, and non-internal, but does not require it to exist in the account's plan composition. `PlanEntitlementService` treats the override as entitled. When no snapshot/current `PlanFeature` contains that feature, `BillingCalculator` finds no add-on price and adds zero.

**Risk**

An administrator can grant arbitrary sellable features for free with no reason, expiry, approval, contract price, or audit contract. This may be intentional for negotiated customers, but the commercial meaning is undefined.

**Required decision/fix direction**

Either restrict overrides to add-ons configured on the subscription's plan, or model negotiated exceptions explicitly with price, currency, reason, approver, effective dates, and audit history. Never infer zero price from missing configuration.

**Implementation evidence — 2026-08-10**

- Verification found the old arbitrary-feature override path had already been removed by the AddOn/quota-package work.
- Both client and admin selection contracts now accept only configured AddOn identities and quota-package identities/quantities; they cannot name a raw feature or arbitrary quota.
- Selected items must be active, Plan-compatible, currency/cycle-compatible, and priced. Their exact item prices are captured in the target snapshot and recurring calculation.
- Negotiated operator exceptions are intentionally not inferred from this selection model and require a separate future contract if the product chooses to support them.

---

### SUBSCRIPTION-006 — Legacy subscriptions without snapshots receive optional add-ons automatically

**Status:** `RESOLVED — FAIL-CLOSED SNAPSHOTS 2026-08-10`

**Evidence**

When `entitlementSnapshot` is absent, `PlanEntitlementService.snapshotEntitles()` falls back to `findByPlanIdAndFeature_Code(...).isPresent()`. It does not check whether the `PlanFeature` is included (`addOnPrice == null`) or an unselected optional add-on (`addOnPrice != null`).

**Risk**

Any legacy/malformed subscription without a snapshot is entitled to every optional feature configured on its plan. Quota fallback can likewise read optional-feature limits.

**Required fix direction**

Migrate every usable subscription to a validated versioned snapshot. Until migration is complete, legacy fallback must distinguish included features from purchased add-ons and fail closed on ambiguous state.

**Implementation evidence — 2026-08-10**

- Entitlement, quota enforcement, and billing no longer reconstruct access or price from the current mutable Plan/AddOn definitions when a snapshot is missing.
- Snapshot and period persistence are mandatory for newly provisioned, trial, immediate-replacement, and renewal-replacement subscriptions; malformed/missing state fails closed.
- No production-row repair or compatibility fallback was retained because there is no deployed database to migrate. The disposable H2 schema is rebuilt with the mandatory columns.

---

### SUBSCRIPTION-007 — Downgrade safety is centralized, incomplete, and fails open for new modules

**Status:** `RESOLVED FOR PLAN-CHANGE SAFETY — 2026-08-10`

**Evidence**

- `SubscriptionUsageService` hard-codes selected current platform feature codes.
- Unknown/future features and quota slots return usage `0`.
- Workspace feature removal itself is not assigned aggregate member/company usage.
- Several counts load all rows and include inactive records.
- Future HR/payroll/accounting features cannot contribute usage without editing this central class.

**Risk**

Removing a feature or lowering a quota may be approved while dependent business data still exists. Conversely, inactive records may block changes forever. New monolith folders will silently lack downgrade protection unless someone remembers this switch.

**Required fix direction**

Introduce feature-owned usage/impact contributors collected centrally. Each sellable feature must declare how to count active usage, detect destructive entitlement loss, and explain remediation. Unknown usage must block destructive changes rather than return zero.

Apply this impact engine to every plan change, not only one labeled a downgrade: both upgrade and downgrade allow immediate or renewal-time execution, and either can remove a capability. Immediate conflicts require an explicit grace/exception/restriction/remediation choice; renewal-time changes remain pending/cancellable and revalidate at execution. Preserve data unless a separate authorized purge flow is chosen.

**Implementation evidence — 2026-08-10**

- The centralized feature-code switch was removed. Workspace, Company, staff, roles, organization groups, B2B collaboration, and subscription-management folders now own their impact contributors.
- Contributors count active domain data and measure owned quota slots. Removing a feature with no contributor produces `IMPACT_UNKNOWN`; reducing a quota without a measurement produces `QUOTA_USAGE_UNKNOWN`. Both block instead of assuming zero.
- The same analyzer runs for every immediate or renewal change, regardless of upgrade/downgrade label. Immediate conflicts reject mutation; renewal operations are cancellable and repeat validation at cutoff, moving to `NEEDS_ATTENTION` on conflicts or stale commercial items.
- Customer data is never deleted by a plan change. Grace/restriction/exception choices and their UI remain later operational flows.

---

### QUOTA-004 — Current arbitrary overrides cannot represent the decided quota-package model

**Status:** `PARTIALLY RESOLVED — VERSIONED PACKAGE FOUNDATION IMPLEMENTED 2026-08-10`

**Evidence**

- `QuotaLimitEntry` combines a nullable limit and nullable per-unit price; null limit means unlimited, even though missing/invalid configuration is not distinguishable from a deliberate unlimited commercial promise.
- Client plan-change input accepts arbitrary `QuotaOverride` values rather than selecting a configured package/version. It can request any finite value or unlimited.
- There is no quota-package entity/version, package capacity/price, single-versus-repeatable setting, maximum purchases, Plan/AddOn availability, or selected-package history.
- Usage is centrally hard-coded for selected resources; unknown future resources return zero instead of requiring a feature-owned usage contributor.
- Effective quota and billing do not retain itemized included, purchased-package, and Account-exception sources, so UI/audit cannot explain how the final limit was produced.

**Risk**

Customers can request capacity the operator never offered, unlimited access can be granted accidentally or for free, unknown module usage can bypass reductions, and concurrent requests may over-allocate the final slot. The replacement UI cannot present trustworthy available packages, usage, excess, or price sources.

**Required fix direction**

- Give every quota a code-owned feature-qualified identity, type/unit, measurement contract, and feature-owned usage contributor. Match and persist `featureCode + resource`; reject unknown usage rather than returning zero.
- Replace arbitrary client quota values with versioned predefined packages attached to the Plan/AddOn that owns the included quota. Store capacity, price, repeatability, maximum purchases, lifecycle, and availability.
- Use explicit finite, zero, and `UNLIMITED` modes; treat null as missing/invalid. Keep unlimited and negotiated/custom changes operator-only until their pricing is deliberately designed.
- Calculate effective capacity as one included owner plus purchased packages plus a source-aware Account grant/restriction exception. Reject overlapping included owners and duplicate/excess package selections.
- When a reduction is below current usage, preserve data, block new consumption according to feature-owned restricted behavior, expose excess, and require remediation/grace/exception. Reuse immediate/renewal preview and pending-operation rules.
- Store itemized quota sources and package versions in the immutable subscription snapshot. Return included, purchased, exception, usage, remaining/excess, and pending state separately.
- Enforce allocation transactionally/concurrently and add finite/zero/null/unlimited, package quantity/max, ownership collision, unknown usage, over-limit data preservation, exception precedence, snapshot/version, stale request, and final-slot race tests.

**Implementation evidence — 2026-08-10**

- Added a versioned `QuotaPackage` aggregate with code/name, feature-qualified resource, capacity per unit, Money price, billing cycle, repeatability, maximum quantity, Plan/AddOn ownership, lifecycle, optimistic locking, and database-unique code.
- Added Permissionizer-guarded administration at `/api/admin/quota-packages`; drafts are editable/deletable, ACTIVE packages are immutable, and archive is terminal.
- Activation proves each declared Plan/AddOn owner is active, currency/cycle compatible, and supplies the exact finite included quota. Selection revalidates ownership and limits against the effective Plan/AddOns.
- Subscription overrides now store package identity and quantity. Immutable snapshots retain package definition version, exact feature/resource, capacity, quantity, and itemized price; catalogs expose compatible packages and effective quota previews expose included, purchased, and final capacity.
- Enforcement adds only snapshotted package capacity to the matching feature/resource quota. Arbitrary values, null-as-unlimited overrides, per-unit price inference, duplicate selections, and excess quantities are rejected or no longer representable.
- Feature-owned usage contributors, operator Account exceptions, renewal scheduling/payment, and final-slot distributed concurrency remain in their dedicated later work.

---

### QUOTA-003 — Quota conflict matching loses the owning feature

**Status:** `VERIFIED AND RESOLVED — 2026-08-10`

**Evidence**

`effectiveQuotaLimits()` produces `QuotaLimitEntry` values containing only `resource`, while `quotaOwner()` recovers a feature by returning the first snapshot feature with that resource name. Resource names are only unique inside a feature definition, not globally.

**Risk**

When two modules use the same quota resource name, downgrade preview can check usage for the wrong feature and approve or block incorrectly.

**Required fix direction**

Carry `(featureCode, resource)` as the quota identity through snapshots, effective-limit calculations, conflicts, billing, UI keys, and enforcement.

**Implementation evidence — 2026-08-10**

- Effective quota output now carries `featureCode` and `resource` together; downgrade/change conflict checks no longer recover ownership by taking the first matching resource name.
- Quota package snapshots, catalog quota rows, validation keys, billing items, and runtime enforcement preserve the same compound identity.
- Tests cover conflict reporting and enforcement using the feature-qualified slot.

---

### PLAN-002 — FREE/default plan availability is not protected

**Status:** `RESOLVED FOR THE CURRENT DEFAULT — 2026-08-10`

**Evidence**

- Workspace provisioning expects plan code `FREE`.
- Admin plan operations can deactivate FREE and can hard-delete it when it has no subscription history.
- `PlanSeeder` skips all seeding whenever any plan row exists, even if FREE is missing.
- Earlier provisioning review confirmed missing FREE can leave registration without a subscription.

**Risk**

An ordinary plan-management action or partial database state can break all new workspace entitlement provisioning.

**Required fix direction**

Make the default provisioning plan an explicit configuration/invariant. Prevent disabling/deleting it while referenced by provisioning, validate it at startup, and make workspace creation fail atomically if entitlement provisioning fails.

**Implementation evidence — 2026-08-10**

- `PlanCodes.DEFAULT` is the single backend identifier used by provisioning, administration, inheritance defaults, and bootstrap seeding.
- Admin service and API paths reject both deactivation and deletion of FREE, even when it has no subscription history. Startup fails visibly if an existing FREE row is inactive.
- Missing FREE is created by the bootstrap seeder even when unrelated Plan rows already exist. Registration already fails atomically when a usable FREE entitlement cannot be provisioned.
- Configurable atomic replacement of FREE remains part of the later Plan lifecycle work; the current invariant deliberately protects the one supported default.

---

### PLAN-003 — Seeded plan composition diverges between fresh and existing installations

**Status:** `PARTIALLY RESOLVED — SAFE BOOTSTRAP IMPLEMENTED 2026-08-10`

**Evidence**

- On an empty database, `PlanSeeder` assigns every code definition marked `planAssignable` to FREE, PRO, and ENTERPRISE.
- On any non-empty plan table, it skips completely.
- `clientWorkspace()` automatically marks a feature plan-assignable.

**Risk**

A newly added HR/payroll/accounting feature is automatically included in every tier on a fresh installation, but included in no existing installation. Product composition becomes environment-dependent and a new client feature can accidentally become free.

**Required fix direction**

Separate development demo data from production catalog migrations. Plan composition must change through explicit, versioned product decisions with previews—not by "all client features" convention or database emptiness.

**Implementation evidence — 2026-08-10**

- Bootstrap composition now names an explicit seven-feature shell baseline; discovering a new client feature can no longer silently add it to every plan.
- Seeding validates the complete required registry baseline before writing, creates each missing FREE/PRO/ENTERPRISE template with its composition inside one transaction, and does not skip merely because another Plan row exists.
- Repeated startup preserves existing templates and admin-managed composition instead of overwriting them; unit tests cover fresh, repeated, partial, inactive-default, and missing-feature states.
- This remains bootstrap-only. Versioned production catalog decisions, previews, and revisions remain scheduled for the later Plan revision workflow.

---

### PLAN-004 — Plan-feature uniqueness is enforced only by a race-prone pre-check

**Status:** `RESOLVED — 2026-08-10`

**Evidence**

`assignFeature()` checks `findByPlanIdAndFeature_Code()` before insert, but `plan_features` has no unique constraint on `(plan_id, feature_id)`.

**Risk**

Concurrent admin requests can create duplicate feature rows, making entitlement, pricing, quotas, and picker behavior ambiguous.

**Required fix direction**

Add a database unique constraint, translate conflicts cleanly, and retain the application check only for friendly validation.

**Implementation evidence — 2026-08-10**

- Verification confirmed the existing generated-schema unique constraint on `(plan_id, feature_id)` and an integration test proves duplicate persistence fails when the service pre-check is bypassed.
- The friendly pre-check remains, while `saveAndFlush` now translates a constraint race into `DuplicateResourceException` instead of leaking an ambiguous persistence failure.
- No Flyway migration was added under the agreed pre-production database policy.

---

### PLAN-008 — PlanFeature commercial meaning and subscriber-removal boundaries are implicit

**Status:** `PARTIALLY RESOLVED — EXPLICIT COMMERCIAL MODES IMPLEMENTED 2026-08-10`

**Evidence**

- Current code infers included versus optional add-on from `addOnPrice == null`; there is no explicit PlanFeature commercial mode.
- Absence from `plan_features` effectively means unavailable, but the contract is not represented or explained explicitly.
- Service validation checks registry plan-assignability and selected lifecycle/surface rules, but the decided sellability/dependency model is not a durable complete contract.
- Existing snapshots correctly avoid automatic template rewrites, but current admin feature mutation APIs provide no separate operation for intentionally removing an entitlement from current subscribers.

**Risk**

Null pricing carries business meaning, new/internal features may enter product tiers accidentally, dependency-invalid plans can be configured, and an implementation may confuse future-plan editing with revoking access that customers already possess.

**Required fix direction**

- Model PlanFeature mode explicitly as `INCLUDED` or `OPTIONAL_ADD_ON`; absence means unavailable for that plan. Add an explicit `BLOCKED_FOR_PLAN` availability exclusion that prevents inheritance/bundle insertion until deliberately removed. Validate add-on selection/pricing without using null as the mode discriminator.
- Enforce one `(plan, feature)` row in the database and service.
- Admit only active code-declared client-facing, plan-assignable, commercially sellable features. Reject internal/platform-control/admin surfaces.
- Add code-owned feature dependency declarations and validate complete composition on edit and activation; expose dependency explanations/actions to the UI.
- Keep draft/revision feature removal future-only. Build a different target-aware subscriber-entitlement removal operation for one/selected/all Accounts with immediate/renewal/scheduled effect, feature-owned usage/data/workflow impact, grace/Account exceptions, communication, confirmation, per-Account status, idempotent retry, and audit.
- Entitlement removal preserves feature data under declared restricted-state behavior. Feature-data purge is a separate authorized lifecycle. Re-adding entitlement restores preserved data only after current entitlement/dependency validation.
- Treat code feature retirement as explicit impact-managed subscriber work; never silently mutate stored snapshots.
- Keep FeatureDefinition price-free. Treat current add-on/quota prices as plan-contextual only and defer the permanent schema until module bundles, feature add-ons, quota packages/overage, negotiated overrides, currency, tax, and billing precedence are decided together.
- Add mode, missing-row, duplicate-race, surface/sellability, dependency, future-only edit, explicit current-subscriber removal, stale preview, and audit tests.

**Implementation evidence — 2026-08-10**

- `PlanFeature.mode` explicitly distinguishes `INCLUDED`, `OPTIONAL_ADD_ON`, and `BLOCKED_FOR_PLAN`; nullable per-feature AddOn price fields and all null-based entitlement inference were removed.
- Only INCLUDED rows may define base quota configuration. Subscription snapshots, entitlement fallback, catalogs, and billing now use explicit mode semantics.
- Database uniqueness and registry/sellability validation remain enforced. AddOn activation and selection require every bundled feature to be OPTIONAL_ADD_ON on the target Plan, and overlapping effective feature ownership is rejected.
- Existing subscriber snapshots remain unchanged by template edits. The separate target-aware current-subscriber removal/revision/audit workflow remains under PLAN-007 and later batches.

---

### PLAN-009 — Plan deletion lacks the decided draft-only impact workflow

**Status:** `RESOLVED FOR CURRENT RETAINED REFERENCES — 2026-08-10`

**Evidence**

- `deletePlan()` blocks only when subscription history exists; it does not require a draft/never-active lifecycle state.
- It does not protect the configured provisioning/default plan.
- It deletes PlanFeature rows and the Plan directly without a dedicated backend impact-preview contract or execution-time preview token/version recheck.
- Current source has no pending plan-change, invoice/payment, external provider, or audit dependency model to consult as those features are added.

**Risk**

An authorized admin can remove a commercially important/default plan merely because no subscription row currently references it, and the future UI cannot explain all blockers or guarantee that only draft-owned configuration is removed.

**Required fix direction**

- Permit hard deletion only for a `DRAFT` that has never been active/used and has no current/historical subscription, scheduled plan change, invoice/payment, external, provisioning-default, or other retained reference.
- If any customer/business history exists, reject hard deletion and offer archive.
- Add a backend impact preview listing blockers and owned configuration counts, deliberate code confirmation, authorization, and transactional execution-time recheck/locking.
- Delete only draft-owned configuration/lineage. Never delete registry features/modules, other plans, Accounts, subscriptions, or customer data.
- Add default-plan, prior-active, historical-only, pending-change, external-reference, concurrent subscription, cross-record safety, authorization, and audit tests.

**Implementation evidence — 2026-08-10**

- The backend exposes a deletion preview with exact Plan/version token, owned-feature count, and blockers for default status, non-draft lifecycle, subscription history, change operations, AddOn/package availability, and retained lineage references.
- Execution requires the exact Plan code, expected optimistic version, and matching preview token, then locks and recomputes the complete preview transactionally. A changed preview returns a conflict.
- Only an unused, unreferenced `DRAFT` is hard-deleted, along with its own PlanFeature rows. Published/used codes remain reserved because their Plan row cannot be hard-deleted; database referential integrity is the final concurrent-reference guard.
- Invoice/provider/external-reference blockers must be added to the same preview contributor when those retained models are introduced. They do not exist in the current schema.

---

### PLAN-010 — Plan duplication has no durable revision identity or code-reservation lifecycle

**Status:** `RESOLVED — 2026-08-10`

**Evidence**

- Plan creation accepts an optional `inheritFromPlanId`; otherwise it implicitly copies `FREE` composition.
- The copy helper duplicates PlanFeature configuration but stores no source/revision lineage or duplication reason.
- New Plan entities currently default active rather than draft.
- Code uniqueness exists at the entity/service level, but source has no normalized code contract or permanent reservation for codes belonging to activated/archived/used plans.

**Risk**

A copied plan can become sellable before review, admins cannot understand its origin, and deleting/reusing an historically meaningful code could confuse snapshots, integrations, support, and audit.

**Required fix direction**

- Normalize/validate immutable plan codes consistently and keep activated/used/archived codes permanently reserved. Permit reuse only after hard deletion of a never-active unused draft.
- Replace ambiguous inheritance with explicit create-empty, duplicate, and revise commands. Require new code/name and always create `DRAFT`.
- Copy only commercial draft configuration, including feature modes/blocks and quota/pricing fields according to the later pricing model.
- Never copy subscribers, customer/payment/audit history, scheduled operations, Account overrides/exceptions, or communications.
- Store source plan/revision lineage and creation reason without linking future edits between source and copy.
- Add code normalization/collision, used-code reservation, unused-draft reuse, default-code protection, draft default, copy-boundary, lineage, and concurrent creation tests.

**Implementation evidence — 2026-08-10**

- Plan codes are normalized and validated once, persisted as immutable unique identities, and translated to a domain conflict on concurrent collision.
- Explicit create-empty, duplicate, and revise commands always create drafts. Duplicate creates a new lineage; revise continues the existing lineage with the next revision; both retain the source and creation reason without sharing future mutations.
- Copying is limited to Plan-owned commercial feature modes/quota configuration. It never copies subscriptions, Account selections/exceptions, checkouts, change operations, periods, or customer data.
- Activated/archived/used Plan rows cannot be deleted, permanently reserving their codes. Only a never-published, unused, unreferenced draft can be deleted and release its code.

---

### PLAN-013 — Product version terminology and Plan version navigation are incomplete

**Status:** `RESOLVED FOR PLAN VERSION MANAGEMENT — 2026-09-22`

**Evidence**

- `Plan.revisionNumber` is the numbered business version within `lineageId`; `sourcePlan` and `creationReason` retain provenance. `revisePlan` creates the next draft in that lineage; duplication starts a separate lineage. The separate JPA `version` counter protects edits against stale writes.
- Plan API/frontend vocabulary uses `revisions`, `revisePlan`, **Révision** and **Réviser**, while the proposed administrator header uses **Version**. Canonical wording and backend/frontend naming need an explicit aligned decision, not a blind replacement of every technical `version` or `revision` reference.
- The Plan list API supports `lineageId` filtering and `revisionNumber` sorting, but the Plan detail page has no dedicated versions destination. Its **Historique** tab renders `CommercialAvailabilityHistory`, which lists availability changes rather than product versions.
- Capacity packages already have a revision panel with lineage pagination and comparison. The product family concept therefore exists, but its navigation is not consistently exposed across products.

**Requested direction**

- Make the header's product-version tag an accessible link to the versions of that same product family once that destination exists. Until then, do not disguise an inert tag as a link or route it to an unrelated availability log.
- Discuss and settle administrator terminology, then align backend business identifiers/API contracts and frontend labels without conflating product version numbers, independent tariff versions, audit events or optimistic-concurrency counters. Assess stored column names, serialized audit/evidence data, permissions, routes and consumers before deciding any rename/migration strategy.
- Design the versions page around the existing lineage: identify the version currently being viewed, show the other versions and their lifecycle states, and allow opening an exact version. Discuss comparison, successor creation/resumption, subscriber-count visibility and permissions before treating them as committed page scope.
- Preserve existing semantics: drafts remain editable; a published version's successor is a separate draft; duplication starts an independent product family; existing subscriber terms do not change automatically.
- Plan regression coverage for same-family navigation, unrelated product exclusion, version ordering/pagination, draft versus published states, least-privilege access and the distinction between business-version numbers and concurrency counters.

**Implementation evidence — version-management backend**

- Added bounded family cards with grouped version/subscriber counts and current tariff reads, paginated same-family versions, composition comparison, audited metadata-only edits and explicit public-version selection. Public selection uses a separate persisted record and the commercial-catalogue lock/version fence; legacy families retain V1 until an operator explicitly selects another version.
- Ordinary client discovery/selection respects that public choice. Existing holdings, exact-product policy paths and authorized Offer operations retain their independent validation; choosing V2 neither changes subscribers nor archives V1. Pausing the selected version never silently promotes another version.
- New DTOs distinguish `productVersionNumber` and `rowVersion`. The Java command is `createPlanVersion`; `/versions` is available while `/revisions`, stored `revision_number`, existing response `revisionNumber` and the persisted `platform.plans.revise` permission remain compatible.
- Read, compare, public-choice and metadata permissions are independent. Family counts and actual price schedules require their secondary read permissions. Active-to-archived Plan commands now enforce the same suspend-sales prerequisite as action discovery.
- Verification: 134 tests passed across the initial Plan/catalogue/subscription/admin-security selection. After compatibility/security refinements, 108 tests passed across `PlanVersionManagementIntegrationTest` (9), `CommercialCatalogMutationCoverageTest` (2), `PlanAdminServiceImplTest` (26), and `AdminControlPlaneSecurityIntegrationTest` (71). Coverage includes public choice/staleness, inactive/direct-only/unpriced rejection, family grouping, diffs, metadata-only invariants, lifecycle parity, client discovery/isolation, restricted secondary data and route compatibility.
- The frontend now groups family cards, links the centered icon-bearing Version tag to a paginated Versions table, resumes an existing draft, compares two versions with unchanged rows opt-in, and exposes explicit public selection and metadata-only edits. Four main sections replace the previous nine; legacy deep links remain valid. Schema code and closed sales panels load on demand. Existing feature-row/add-on/pack presentation is retained.
- Frontend verification: lint/typecheck, 410 tests and production build; real browser on isolated ports 5173/8081 covered catalogue, V1→draft V2 creation, coexistence, two-version comparison, light/dark and Arabic direction. Restricted read/compare/family routes are covered by UI tests. Existing French-only shared navigation, seeded English names and older subpanels have not been relabelled as fully localized.
- Final completion: family-wide history, version-aware subscriber presets, guided application/results and final verification are delivered under `PLAN-007`. Administrator wording is Version; persisted revision columns, legacy serialized properties/routes/permission identities and concurrency counters retain explicit compatibility. This closes the Plan naming/navigation gap, not a wholesale rename of every product or database field.

---

### PLAN-011 — Admin subscriber management is a collection of single-record endpoints, not the decided operational flow

**Status:** `PARTIALLY RESOLVED — CONTENT-VERSION POPULATIONS AND WORKFLOWS DELIVERED 2026-09-22; BROADER LIFECYCLE WORK REMAINS`

**Remaining evidence**

- One-Account immediate and at-renewal changes and explicit selected-Account jobs use the reviewed operation engine. Filtered/Plan-family populations now exist for content-version applications; this does not add every financial/lifecycle command kind to those jobs.
- Reviewed trial creation, cancel-at-period-end, immediate cancellation, suspension, expiry, restoration, correction, and communications are not yet first-class operator commands. Progress, partial results, cancellation, and safe retry are implemented for selected-Account `CHANGE_SELECTION` jobs.
- General negotiated/grace/restricted-state exceptions remain later than the delivered typed commercial-policy effects.
- Dedicated content-only single-Account execution and population/scheduling APIs, notifications, operator/client UI and final local verification are delivered under `PLAN-007` (2026-09-22). The older selected-Account commercial-change job remains separate; production deployment gates are listed at the final `PLAN-007` checkpoint.
- The consolidated `PLAN-FLOW-005` proposal adds grouped actionable conflicts, frozen filtered/family audiences, version-aware preset views, operational APIs, notice reuse and bounded background processing. Verify current lifecycle implementations before relying on this entry's older Phase 12 inventory; do not rebuild capabilities already delivered elsewhere.

**Risk**

An admin UI built over these endpoints would force unsafe UUID-driven changes, hide important subscriber states, mislabel estimates as revenue, overwrite concurrent decisions, and provide no trustworthy way to operate or retry large changes. Exceptions and access shutdowns would be commercially and operationally ambiguous.

**Required fix direction**

- Add minimal Account/subscription lookup and paginated subscriber views with explicit status classification, safe filters, separately authorized owner-email lookup, and no Company/member/business-data leakage. Label calculated values as configured/estimated recurring price.
- Build one-Account and bulk plan changes around a versioned backend preview, immutable affected set, per-Account locking/transactions, partial-success results, idempotent retry, and fresh-preview conflicts. Support immediate, renewal, and scheduled timing with pre-execution cancellation.
- Preserve every purchased state as history. Reversal/correction is another explicit previewed operation, never mutation of old snapshots or a fake rollback.
- Show current snapshot, feature-owned usage/impact, exceptions, history, and pending operations. An immediate conflict must explicitly choose grace, temporary exception, restricted access, or remediation; it must never delete data implicitly.
- Model Account exceptions as source-aware grants/restrictions limited to client-facing sellable features/quotas, with reason, actor, effective/expiry/permanent status, optional contract/approval reference, and explicit retain/remove/replace behavior on plan change.
- Implement distinct, audited cancel-at-period-end, immediate cancel, suspend, expire, and restore transitions. Stop operational/B2B access and invalidate authorization state at the effective time while preserving declared restricted/read-only data; restoration revalidates current eligibility and creates fresh authorization state.
- Add permission, privacy, pagination, concurrency, stale-preview, mixed-result, retry, history, exception, lifecycle, access-revocation, and restoration tests. Treat export plus pricing/billing/notification content as separately designed capabilities rather than pretending they are complete here.

**Implementation evidence — 2026-08-10**

- Plan subscribers are now a bounded page (maximum 100), searchable by Account name and filterable by every subscription status; the default view includes current and historical states rather than silently hiding non-usable subscriptions.
- Results expose Account/subscription operational identity and explicitly label price as configured recurring price. They do not expose Company/member/business data or describe configured amounts as collected revenue.
- Exact Account-owner-email lookup is a separate Permissionizer action and response contract, so ordinary subscriber-list permission does not automatically expose owner email.
- The audited Account workbench now exposes the current exact purchased snapshot, usage/conflicts, history and pending operation; signed preview, required reason, immediate/at-renewal apply, stale-review recovery, pending cancellation, and checkout confirmation all revalidate under the Account/commercial lock order.
- Direct admin create/trial/raw-override mutations and their Permissionizer nodes were removed. Internal registration-time FREE provisioning remains the narrow bootstrap exception; reviewed trial creation belongs to Phase 12.
- Admin responses preserve actor, request/cancellation, checkout and policy provenance. Client responses deliberately expose only safe effective terms, stable attention codes, and checkout state.
- Exact retained Plan/AddOn/package Price-entry identities and quantities survive later catalogue pause/inactivation/direct-only changes; retained items remain visible/removable but cannot be newly selected or increased.
- Selected-Account population previews, immutable affected sets, partial-success jobs/retry, and scheduled execution are now durable operational APIs with admin list/create/detail/results UI and independently authorized identity reveal.
- Filtered/Plan-subscriber content-version populations and their private scheduled/client notices are now implemented. The older broader lifecycle/correction/export inventory remains a separate scope; verify current dedicated lifecycle capabilities before implementing missing operations. Do not infer completion of every subscriber-management feature from the content-version workflow.

---

### PLAN-012 — Current per-feature add-on fields cannot represent the agreed commercial AddOn model

**Status:** `PARTIALLY RESOLVED — FIRST-CLASS ADDON FOUNDATION IMPLEMENTED 2026-08-10`

**Evidence**

- There is no AddOn aggregate/entity, lifecycle, version, plan-availability relation, dependency/exclusion configuration, or add-on management API.
- `PlanFeature.addOnPrice` makes each individual feature either included or separately priced by inference from a nullable value. It cannot sell a whole module or custom multi-feature bundle as one commercial item.
- Client requests select a set of add-on feature codes, and billing sums their individual PlanFeature prices. There is no selected AddOn identity/version or bundle-level price/history.
- Quota limits and per-unit values live inside individual PlanFeature JSON. There are no predefined quota-package products or rule connecting a package to the Plan/AddOn that supplies the capability.
- The current model cannot reject double charging/overlapping quota ownership across a base Plan and multiple bundles because those commercial relationships do not exist.

**Risk**

The backend and replacement UI would force administrators to price technical features individually, cannot sell a complete module/custom bundle cleanly, and cannot reconstruct what bundle or quota package the customer purchased. Combining FREE with paid modules or multiple add-ons would produce ambiguous entitlement, quota, and price calculations.

**Required fix direction**

- Introduce a versioned admin-created AddOn commercial aggregate that can contain a complete sellable module or selected sellable features, included quota definitions, price, allowed/blocked Plans, dependencies, exclusions, and lifecycle. Keep technical Module/Feature definitions price-free.
- Model the Plan as base price plus included feature/module composition and included quotas. Resolve a purchase as Plan plus selected compatible AddOns plus selected quota packages.
- Initially support fixed/non-increasable quotas, predefined priced quota packages, and operator-only negotiated exceptions. Defer customer per-unit and unlimited pricing.
- Reject duplicate paid capabilities and overlapping included-quota ownership. Permit additional capacity only through an explicit quota package tied to an already entitled capability.
- Store immutable Plan/AddOn/package versions, effective feature/quota result, itemized prices, currency, billing cycle, and effective dates in the subscription snapshot/history.
- Reuse immediate/renewal change operations, impact preview, locking, pending jobs, data preservation, communication, audit, and per-Account results for AddOn/package changes.
- Add FREE-plus-add-on, whole-module bundle, custom bundle, dependency/exclusion, duplicate capability, overlapping quota, package stacking, blocked Plan, snapshot/version, immediate/renewal, concurrency, price calculation, and authorization tests.

**Implementation evidence — 2026-08-10**

- Added a versioned `AddOn` aggregate and `AddOnFeature` composition with fixed Money price/currency/cycle, DRAFT/ACTIVE/INACTIVE/ARCHIVED lifecycle, allowed/blocked Plans, dependencies, exclusions, included quota definitions, optimistic locking, and database uniqueness.
- Added Permissionizer-guarded administration at `/api/admin/add-ons` for catalogue, detail, lifecycle, feature composition, safe draft deletion, and immutable published boundaries. Published AddOns now branch into lineage-aware draft revisions; publishing a revision pauses the older active revision for new sales without changing existing subscription snapshots.
- Subscription requests and overrides now select AddOn identities rather than technical feature codes. Validation enforces active state, Plan/currency/cycle availability, dependency/exclusion rules, OPTIONAL_ADD_ON modes, and non-overlapping capabilities.
- Immutable entitlement snapshots retain selected AddOn identity, definition version, price/currency/cycle, effective bundled features and quotas. Billing prices the snapshotted AddOn, and the client catalog exposes compatible AddOn composition and quota details.
- Versioned quota packages now add Plan/AddOn-owned finite capacity with identity/quantity selection, itemized snapshot pricing, catalog visibility, and runtime enforcement.
- Tests cover FREE-compatible recurring cycles, lifecycle/activation, identity selection, AddOn/package snapshot pricing, uniqueness, administration-to-client-catalog flow, and existing-subscription snapshot isolation.
- Payment/approval, renewal scheduling, subscriber-wide impact jobs, operator exceptions, and full audit history remain intentionally assigned to later batches.

---

### PLAN-005 — Purchased subscription terms are not a complete historical snapshot

**Status:** `PARTIALLY RESOLVED — VERSIONED TERM HISTORY IMPLEMENTED 2026-08-10`

**Evidence**

The entitlement snapshot now stores plan code, base price, currency, billing cycle, effective features/quotas, selected AddOn identities/versions/prices, and selected quota-package identities/versions/capacity/quantity/prices. It still does not store a Plan definition version or lineage, effective dates, tax/adjustment terms, or a complete historical change record. The subscription also still points to a mutable `Plan` for other display fields.

**Risk**

The current snapshot is materially safer, but it still cannot reconstruct every term a customer accepted or explain the lineage and effective period of later changes. Cross-cycle aggregation and invoice/payment history also remain separate unresolved concerns.

**Required fix direction**

Snapshot the exact Plan/AddOn/quota-package versions, effective features/quotas, itemized Money prices with ISO currency, exact monthly/yearly cycle, effective period dates, adjustments, and source version/lineage. Preserve entitlement history separately from invoices, confirmed payments, refunds/credits, and provider/manual references. Never aggregate mixed currencies/cycles or label configured price as revenue.

**Implementation evidence — 2026-08-10**

- The versioned snapshot now records Plan identity/name/definition version, base Money/currency/cycle, effective period, effective feature/quota definitions, and selected AddOn/package identity, definition version, quantity/capacity, and item price.
- Each closed/open `SubscriptionPeriod` preserves the exact snapshot effective for that period; each change operation preserves before/target snapshots, requested selection, timing, status, and resulting subscription.
- Billing and runtime entitlement read the immutable snapshot only, so later template changes do not rewrite accepted access or configured price.
- Plan revision lineage remains PLAN-007. Taxes/adjustments, invoices, confirmed payments, refunds/credits, and provider references remain separate billing records under Batch 4.6/later work; configured price is not described as collected revenue.

---

### TIME-001 — Business timestamps mix `Instant` and `LocalDateTime`

**Status:** `IMPLEMENTED — 2026-08-10`

**Evidence**

- The original review found `Instant` in shared auditing/credential expiry while collaboration lifecycle and subscription-period fields used `LocalDateTime`.
- Repository-wide verification now finds no production `LocalDateTime`, `OffsetDateTime`, or `ZonedDateTime` use. Collaboration lifecycle, subscription periods/operations, registry runs, credential expiry, member exceptions, DTOs, API errors, and actor audit events use `Instant`.

**Risk**

Time-zone conversion can make subscription expiration, collaboration activation, and scheduled operations ambiguous across deployments.

**Implementation evidence — 2026-08-10**

- `spring.jpa.properties.hibernate.jdbc.time_zone` is explicitly `UTC` in shared configuration, so development, test, and production profiles use the same Hibernate/JDBC conversion rule.
- The application `Clock` remains `Clock.systemUTC()`. Persisted system events and deadlines use `Instant`; a future civil date/time may use a domain-appropriate local type only when its zone or locale semantics are explicit.
- `UtcTimestampIntegrationTest` changes the JVM default to a non-UTC zone and proves a microsecond-precision `Instant` survives a real JPA/H2 write/read unchanged. It separately proves JSON uses an explicit `Z` offset and round-trips exactly.
- Existing generated schemas already derive the corrected `Instant` mappings. Under the current unpublished/disposable-database decision, no Flyway history was added; the future production baseline must preserve these UTC-compatible timestamp mappings.
- The focused timestamp tests and complete 417-test backend suite pass with zero failures, errors, or skips.

---

### MODULES-001 — Cross-domain data exchange inside the monolith has no confirmed contract yet

**Status:** `RESOLVED — 2026-08-11`

**Decided rule — 2026-08-11**

A domain reaches another domain only through its service:

1. Another domain's **repository** — never.
2. Another domain's **facts** — through an immutable read view.
3. Another domain's **entity** — only through explicitly named service methods, and only to create the row or establish a JPA relationship inside a transaction.

Cross-domain JPA relationships stay. `TENANCY-002` already established that a raw UUID reference is a defect because the database cannot guarantee it points at an existing row; reverting to id-only references would reopen it, and would push this monolith toward a distributed shape the architecture decision explicitly rejects.

**Implementation evidence — 2026-08-11**

- `IdentityService` now exposes `findUserView(..)` for facts and `createUser(..)` / `requireManagedUser(..)` as the only entity doors. `UserView` and `NewUserCommand` carry the cross-domain contract.
- All four bypasses are gone. `AdminSeeder` and `MemberServiceImpl` create identities through identity; `WorkspaceProvisioningServiceImpl` and `AdminUserServiceImpl` take the managed row through the named door; `AdminAuthenticationServiceImpl` stopped consulting identity entirely and resolves the administrator from its own aggregate.
- `MemberCredentialService` persists its own credential-state changes, so no other domain writes the user row.
- `CrossDomainAccessRuleTest` fails the build if any platform class imports an identity repository, turning the rule from a review habit into an enforced invariant.

**Evidence**

- The current entities model the platform shell: identity, tenancy, companies, membership, permissions, plans, and collaboration.
- `Department` is the only early organizational/business entity.
- No reviewed mechanism yet defines how future HR, payroll, and accounting packages exchange data while remaining organized parts of one monolith.

**Risk**

Future business packages may directly import repositories and mutate each other's entities. This creates circular dependencies, unclear ownership, and fragile transactions even though everything runs in one application.

**Required direction to validate during later layers**

- Keep HiveApp as one monolith; do not introduce microservices for this concern.
- Every business area should still have explicit ownership of its records and rules.
- Shared scope should consistently use account and company identity.
- Cross-package reads/writes should use deliberate service methods, application contracts, stable read models, or local domain events where useful rather than arbitrary repository access.
- Define transaction and failure behavior for workflows such as HR employee changes feeding payroll and payroll posting accounting entries.

Do not finalize the integration design until services, events, and current package dependencies are reviewed.

---

### MODULES-002 — Company domain ownership is structurally unclear

**Status:** `RESOLVED — 2026-08-11`

**Decision and evidence — 2026-08-11**

`Company` belongs to the company package. `FLOW_DECISIONS` treats a Company as a business/legal operating scope with its own lifecycle, deletion flow and quota — an aggregate in its own right, not a detail of Account. The entity and its repository moved from `platform.client.account.domain` to `platform.client.company.domain`, joining the services, DTOs and APIs that already lived there. `Company.account` remains a real relationship, so tenant isolation is unchanged. Package and imports only; no schema or logic change.

**Evidence**

- `Company` is stored under `platform.client.account.domain.entity`.
- The separate `platform.client.company` domain currently contains `Department` and company-facing services/APIs.

**Risk**

Future modules may depend on the wrong package or duplicate company concepts because ownership of the aggregate is unclear.

**Required resolution**

After reviewing services and repositories, decide whether Company belongs to the account aggregate, the company module, or a stable shared organizational kernel. Align package ownership without breaking tenant boundaries.

---

### AUDIT-001 — Security and billing changes lack a reviewed actor-aware audit model

**Status:** `IMPLEMENTED — 2026-08-10`

**Evidence**

- `BaseEntity` records only creation and modification timestamps.
- No entity reviewed so far records who changed roles, grants, plan composition, subscription overrides, or collaborations.

**Risk**

The company may be unable to explain who changed customer access, subscription pricing, or delegated permissions. This also makes destructive admin operations difficult to investigate.

**Implementation evidence — 2026-08-10**

- Verification confirmed that the existing JPA auditing recorded only timestamps; there was no actor-aware business audit entity, service, event store, or equivalent history mechanism.
- `AuditLog` is a shared append-only record with event time, actor surface/user, client and target Account, Company, collaboration, Permissionizer action, resource identity, request path/method, redacted request/result summaries, outcome, and safe failure type. Application-level update/delete callbacks reject mutation of persisted entries.
- Every non-read-only mutation carrying both `@Transactional` and `@PermissionNode` is audited centrally. Transaction advice deliberately wraps audit and Permissionizer advice: a successful audit participates in the business transaction and therefore rolls back with it, while a rejected/failed attempt is persisted in `REQUIRES_NEW` after redacting sensitive input and then rethrows the original failure. The pinned advisor chain is transaction → audit → Permissionizer → method, so Permissionizer policy reads execute inside the caller transaction.
- Internal scheduled/nested operations that do not have a user-facing Permissionizer action use the explicit `@AuditedMutation` marker. B2B creation remains isolated in its existing `REQUIRES_NEW` store and records the row-creation event in that same transaction; the outer request separately records its 201/200 outcome.
- Registration, login, activation, initial-password completion, and password-reset lifecycle use explicit actor/subject records after the identity is safely known. Passwords, access/refresh tokens, activation/reset material, share codes, credential hashes, authorization headers, and cookies are always redacted; exception messages are not persisted.
- Platform subscription commands now establish their transaction at the Permissionizer-protected admin boundary, so manual plan assignment, trial creation, override changes, and manual checkout confirmation receive one actor-aware atomic record even though their underlying services are reused.
- The generated development/test schema now includes `audit_log` directly. Per the current unpublished in-memory-database decision, no Flyway history was introduced; a versioned baseline remains production-readiness work.
- `AuditMutationIntegrationTest` proves the advisor order, success atomicity, failed-attempt survival, append-only behavior, redaction, real authenticated Company mutation capture, rejected request capture, and the read-only exclusion. The B2B/billing focused suites and the complete 415-test backend suite pass with zero failures, errors, or skips.
- The deliberate current boundary is mutation auditing, not general security-access auditing. `@Transactional(readOnly = true)` methods bypass the audit aspect, so successful and denied reads are not recorded. `AUDIT-002` owns the product/security decision about adding that separate, potentially high-volume capability.
- An authorized audit query/report API and UI are still future compliance-surface work; this issue's mutation-recording and actor-mapping gap is closed without exposing audit rows through an unsafe generic repository endpoint.

---

### AUDIT-002 — Read access and denied reads are outside the mutation audit boundary

**Status:** `OPEN — PRODUCT SECURITY SCOPE DECISION REQUIRED`

**Evidence**

- `AuditMutationAspect` deliberately returns without recording when the matched transaction is read-only.
- Successful reads and denied read attempts therefore produce no `AuditLog` row. The implemented `AUDIT-001` facility is a mutation audit trail, not a complete security-access trail.

**Risk**

If future security forensics, privacy controls, or compliance obligations require data-access history, the platform cannot currently explain who attempted or completed a sensitive read. Auditing every read indiscriminately would instead create substantial storage, performance, retention, and privacy costs.

**Decision required before implementation**

- Decide whether read-access security forensics is in product scope.
- Define the sensitive resources and surfaces to cover, and whether to record successful reads, denied reads, or both.
- Define event detail, privacy/redaction, retention, volume limits or sampling, and access controls for reviewing the trail.
- Define failure-transaction behavior and abuse/rate-limit handling for repeated denied reads.

Do not turn all read-only methods into audit events by default. If approved, build this as an explicit bounded extension of the shared audit model rather than weakening or overloading the mutation boundary.

---

## DTO and mapper findings

### DTO-001 — Request validation is inconsistent at important write boundaries

**Status:** `IMPLEMENTED FOR CURRENT WRITE BOUNDARIES — 2026-08-11`

**Evidence**

- `CreatePlanRequest` and `UpdatePlanRequest` require a price but do not reject negative values or constrain scale.
- `AssignPlanFeatureRequest` does not reject a negative add-on price.
- Nested quota lists in plan and subscription requests do not use `@Valid`, and the quota records themselves have no Bean Validation constraints.
- `UpdateCompanyRequest` permits every field to be null and has no length/nonblank validation for a supplied name.
- `AcceptInvitationRequest` has conditional registration fields, but the DTO cannot express that first name, last name, and password become required for a new user.
- Several names, codes, descriptions, phone numbers, and addresses have no maximum lengths matching a documented/database contract.

**Risk**

If services do not repeat every validation rule, invalid pricing, quota, company, or registration data can reach persistence. Different endpoints may enforce the same business concept differently.

**Verify later**

- Controller use of `@Valid`.
- Service-level plan, quota, invitation, and company validators.
- Database constraints and request-level negative tests.

**Possible fix direction**

Use Bean Validation for structural input rules and service validators for conditional/domain rules. Add `@Valid` for nested structures and keep one shared validator for preview/apply or create/update pairs.

**Implementation evidence — 2026-08-11**

- Plan, AddOn, quota-package, branch, role, Company, and organization-Group requests now enforce structural bounds before service execution, including non-negative four-decimal prices and three-letter currencies.
- Quota API input is separated from the invariant-enforcing domain value. Nested entries are validated with `@Valid`, then converted once after mode/limit consistency succeeds.
- Controller coverage proves invalid commercial input returns the shared structured validation response instead of reaching persistence.
- The obsolete invitation registration fields are intentionally absent; direct member creation and credential completion own their validation contracts.

---

### DTO-002 — Several response DTOs cannot represent important entity state

**Status:** `IMPLEMENTED FOR CURRENT API SURFACES — 2026-08-11`

**Evidence**

- `CompanyDto` omits `taxId`, `address`, `logoUrl`, and account identity even though create/entity models contain some of those fields. `UpdateCompanyRequest` also cannot update tax ID, address, or logo.
- `MemberDto` omits email and assigned roles, although the product stories describe a member list containing email, roles, and active state.
- `RoleDto` omits `companyId`, `accountId`, and `isActive`, even though role scope and activation affect authorization.
- `CollaborationDto` omits lifecycle timestamps and delegated permission information.
- `SubscriptionDto` exposes only a plan summary and basic status/price, not the effective snapshot, features, overrides, or quota usage described by the client subscription story. Some of this may intentionally come from the separate catalog endpoint.

**Risk**

The UI may be forced to guess state, make extra requests, display incomplete records, or become coupled to multiple inconsistent read models. Company/role scope omissions are especially dangerous because identical names can exist in different scopes.

**Verify later**

Trace each DTO through controllers, services, frontend API clients, and screens. Decide whether each endpoint is intentionally a summary or is incomplete for its promised workflow.

**Implementation evidence — 2026-08-11**

- Company responses expose Account identity and the existing tax/address/logo state.
- Role, collaboration, member credential, and email-delivery read models already expose their relevant scope and lifecycle state from earlier batches.
- Member access management now has a separate authorization-detail response rather than overloading the member-list summary.
- Subscription summary and entitlement/catalog detail remain deliberately separate endpoints; the UI need not infer one from the other.

---

### AUTHZ-DTO-001 — Effective client permission response has no explicit company context

**Status:** `IMPLEMENTED — 2026-08-11`

**Evidence**

- `MemberPermissionDto` contains only member ID, owner flag, and a flat set of permission strings.
- Client role assignments and overrides are company-scoped in the entities.
- `MeController` calls `EffectivePermissionService.getEffectivePermissions(userId, accountId)` with no company ID and returns that flat set.
- For non-owners, `EffectivePermissionService` loads every role assignment and every override for the member without filtering company, then unions them into one set.

**Risk**

If the response is calculated for one company but cached or reused by the UI for another, the UI can show actions the user cannot perform—or hide actions they can perform. Backend enforcement may remain correct while the client experience becomes misleading.

**Required fix direction**

Calculate permissions for an explicit account/company/B2B context and include that context in the response. If the UI needs an overview, return a scope breakdown rather than one flattened authorization set.

**Implementation evidence — 2026-08-11**

`MemberPermissionDto` now identifies the member, Account, and selected Company for the returned permission set. Unit and HTTP integration tests pin the explicit context so a Company-scoped result cannot be mistaken for an Account-wide overview.

---

### DTO-003 — Multiple overlapping registry/catalog DTO families need explicit boundaries

**Status:** `IMPLEMENTED — 2026-08-11`

**Evidence**

The registry exposes several similar model families:

- `ModuleDto` / `FeatureDto`
- `RegistryModuleReadModelDto` / `RegistryFeatureReadModelDto`
- `PublicFeatureCatalogModuleDto` / `PublicFeatureCatalogFeatureDto`
- Permission picker module/feature/permission DTOs
- Client subscription catalog feature models

**Risk**

Specialized read models are appropriate, but older overlapping endpoints can drift in filtering, lifecycle interpretation, names, descriptions, quotas, and security exposure. The frontend may choose the easiest endpoint instead of the correct audience-specific endpoint.

**Verify later**

Map each model to its controller route, audience, authorization rule, and frontend consumer. Remove or clearly mark legacy models only after current usage is known.

**Implementation evidence — 2026-08-11**

- Registry contracts now live in explicit `admin`, `picker`, and `publicapi` namespaces with package-level audience descriptions.
- Controllers and services import only the contract family for their surface.
- The unused legacy `ModuleDto`, `FeatureDto`, and `RegistryMapper` were removed after repository-wide usage verification.

---

### DTO-004 — Client plan catalog quota model contains duplicated fields

**Status:** `IMPLEMENTED — 2026-08-11`

**Evidence**

`ClientPlanCatalogResponse.CatalogQuota` contains `resource` and `unit` while also embedding the full `QuotaSlot`, which itself contains resource, type, and unit.

**Risk**

The response can contain two conflicting values for the same resource/unit, expanding the frontend contract unnecessarily.

**Possible fix direction**

Expose one canonical quota shape plus current plan limit, price, and usage fields.

**Implementation evidence — 2026-08-11**

`CatalogQuota` now carries resource/type/unit only through its canonical `QuotaSlot`. A JSON contract test proves the duplicated top-level `resource` and `unit` fields are absent.

---

### MAPPER-001 — Mappers traverse lazy relationships

**Status:** `RESOLVED — 2026-08-11`

**Evidence**

- `RoleMapper` walks role permissions and each related permission.
- `CollaborationMapper` dereferences both accounts and company.
- `MemberMapper` dereferences user.
- `RegistryMapper` recursively maps module features.

**Risk**

Without deliberate fetch queries and transaction boundaries, mapping can cause `LazyInitializationException` or N+1 database queries, especially on list endpoints.

**Verify later**

Inspect repository fetch strategies, service transaction scopes, generated SQL tests, and list endpoint pagination before changing mapper behavior.

**Verification — 2026-08-11**

Two of the four cited mappers no longer exist: `CollaborationMapper` was removed in Batch 5.2 and `RegistryMapper` in Batch 6.1. The remaining traversals were measured with Hibernate statement statistics rather than reasoned about, and the defect reproduced on every list surface:

- `GET /api/v1/roles` issued 13 statements for one role and 16 for four — one extra statement per role for its permission collection.
- `GET /api/v1/members` issued 10 statements for one member and 12 for three — one extra `User` statement per member.
- `GET /api/admin/plans/{planId}/features` issued 9 statements for one feature and 12 for four — one extra `Feature` statement per plan feature. This surface postdates the original finding and was not cited in it.

`CompanyMapper` and `AccountMapper` were confirmed safe: they read only `account.id` / `owner.id`, which Hibernate serves from the proxy without initializing it. `AddOnRepository` and `QuotaPackageRepository` already carried entity graphs, so their controller traversals were never unsafe.

**Implementation evidence — 2026-08-11**

- The fix is fetch strategy, not mapper truncation: the DTOs legitimately need permission codes, member identity, and feature codes, so the owning queries now load those relationships up front through `@EntityGraph`.
- `RoleRepository.findAllByAccountId` and `findAllByBoundaryCompanyId` load `permissions` and `permissions.permission`. Both have exactly one caller, each a list endpoint.
- `MemberRepository` gains a dedicated `findWithUserByAccountId` for the member list. `findAllByAccountId` is deliberately left ungraphed because `AccountShellServiceImpl` only reads user ids, which cost nothing on a proxy.
- `PlanFeatureRepository.findAllByPlanId` loads `feature`. Nearly all of its eleven callers project the feature code; the two that only count or delete pay one cheap join on a small collection.
- `LazyMappingQueryCountIntegrationTest` asserts statement counts stay constant as rows grow, instead of pinning absolute numbers that authentication and authorization would make brittle. Each assertion was confirmed to fail before its corresponding fix.
- **Correction — 2026-08-11 (found in review by Codex):** the first pass scoped this to *list* read models and left the role **detail** surface untouched. `RoleRepository.findByIdAndAccountId` had no graph and `getRole` is not `@Transactional`, so `GET /api/v1/roles/{id}` walked `permissions` and `permissions.permission` lazily under open-in-view. Measured at 11 statements for a role with one permission and 13 for a role with three — one extra statement per permission. The graph now covers it and `roleDetailStatementCountDoesNotGrowWithThatRolesPermissions` was confirmed to fail without it.
- **The `SubscriptionMapper` exception is closed — 2026-08-11.** It was recorded as bounded because it never multiplied by row count. That reasoning missed the real failure: the projection ran in the *controller*, after the service transaction closed, so `GET /api/v1/subscriptions/me` returned 500 with open-in-view disabled. Mapping moved into `SubscriptionService.getMySubscription(..)` under a read transaction.
- **`previewRoleImpact` was the same defect in a third place**, walking `assignment.getMember()` with no read transaction. It is now `@Transactional(readOnly = true)`.
- **The mask is gone:** `spring.jpa.open-in-view=false` is now set in the shared `application.yaml`, so every profile fails loudly on lazy access outside a transaction instead of absorbing it in a request-scoped session. All three defects above were invisible while it was on. Disabled globally rather than in tests alone because the setting's only externally visible effect is *when* a latent defect surfaces, and the application is unpublished — the cheapest possible moment to expose the rest.

---

### MAPPER-002 — Most API mapping appears to be manual and distributed

**Status:** `RESOLVED — 2026-08-11`

**Evidence**

Only seven MapStruct mappers exist for 65 DTOs. Complex admin, plan, invitation, permission picker, and registry read models must therefore be assembled elsewhere.

This is now confirmed for admin users and roles: both controllers manually construct DTOs and directly call assignment repositories while mapping every returned entity.

**Risk**

Manual mapping is sometimes necessary, but repeated parsing/filtering logic in services can cause inconsistent security filtering and response semantics.

**Verify later**

Locate all DTO constructors in services/controllers. Keep business-aware read-model assembly explicit, but centralize repeated parsing and audience-filter rules.

**Verification — 2026-08-11**

- The one *confirmed* instance is closed. `AdminUserController` and `AdminRoleController` no longer construct DTOs or call assignment repositories; Batch 6.1 moved that assembly into the admin services under `ADMIN-DATA-001`. Five MapStruct mappers now remain, not seven, after the `CollaborationMapper` and `RegistryMapper` removals.
- The manual construction that remains in services is business-aware read-model assembly, exactly what this finding says to keep explicit. `PermissionPickerCatalogService` derives per-audience availability and a source-owned `PermissionUnavailableReason`, which is the behavior `REGISTRY-FLOW-004` decided; `RoleServiceImpl` aggregates assignment counts by scope into an impact model. Neither is field copying, and MapStruct cannot express either without an `@AfterMapping` body containing the same logic plus indirection.
- Mechanical entity→DTO copying does still exist in `PlanAdminController`, `AddOnAdminController`, and `QuotaPackageAdminController`. It exists **because those services return persistence entities**, which is `SERVICE-002` / `SERVICE-003` — whose Batch 6.3 acceptance criterion is literally "Services return DTOs". Consolidating it inside Batch 6.2 would pre-empt that batch's contract design and be redone once the DTO-returning service interfaces exist.

**Decision:** the lazy-safety half was completed under `MAPPER-001`. The remaining consolidation was a symptom of services returning entities, so it was owned by the boundary findings rather than by this one — `SERVICE-002`, `SERVICE-003` and finally `SERVICE-004` removed every controller-side projection. **Closed 2026-08-11:** no controller maps a persistence entity.

**Correction — 2026-08-11 (found in review):** that sentence was written while `SubscriptionController` still called `subscriptionMapper.toDto(...)` on an entity returned by the service, so the claim was false when made and contradicted this batch's own `MAPPER-001` note recording `SubscriptionMapper` as an exception. The controller now calls `SubscriptionService.getMySubscription(..)`, which maps inside a read transaction. Verified by disabling open-in-view.

---

## Service-contract findings

### SERVICE-001 — Identity service exposes its JPA entity as a cross-domain contract

**Status:** `RESOLVED — 2026-08-11`

**Resolution — 2026-08-11**

Resolved under the `MODULES-001` rule. Verification had shown the finding's own fix direction did not fit its only caller: `AdminUserServiceImpl` never read identity fields, it needed the managed row to establish the `AdminUser` `@OneToOne`. The contract now separates the two needs — `findUserView(..)` for facts, `requireManagedUser(..)` for relationship establishment — so the general-purpose `getUserById` accessor is gone without breaking the legitimate case.

Two entity-returning methods exist rather than one, because creation inherently yields a managed row; forcing a re-fetch would add a query without adding safety. Both are explicitly named and documented as the entity door.

**Evidence**

`IdentityService.getUserById(UUID)` returns `Optional<User>` rather than an identity-owned read model or stable contract.

The only current external caller found is `AdminUserServiceImpl`, which uses this entity-returning contract when connecting an existing user to platform administration.

**Risk**

Other packages can become coupled to identity persistence fields and may accidentally mutate a managed entity. Future identity changes then ripple through the monolith.

**Verify later**

Find every `IdentityService` caller and determine whether callers need the complete entity or only identity facts such as ID, email, display name, and active state.

**Possible fix direction**

Expose a small immutable identity view for cross-domain consumers. Keep entity access internal to the identity package unless a transactional domain operation truly requires it.

**Verification — 2026-08-11**

`AdminUserServiceImpl` is still the only external caller, and it does **not** read identity fields — it calls `adminUser.setUser(user)` to establish the `AdminUser.user` `@OneToOne`. That is precisely the "transactional domain operation truly requires it" exception in this finding's own fix direction, so swapping the return type for a read view does not apply as written; `AdminUser` must reference the `User` type regardless.

The larger leak this finding implies is also already present somewhere it does not mention: `AdminSeeder` imports and uses `UserRepository` directly, bypassing `IdentityService` entirely. Narrowing only this one method would leave that untouched.

**Blocked on decision.** How domains should exchange references at all is `MODULES-001`, which is `DECISION` status and owned by Batch 6.4. Resolving `SERVICE-001` properly means deciding that contract first; doing it inside Batch 6.3 would pre-commit 6.4's answer.

---

### SERVICE-002 — Platform admin service contracts expose persistence entities

**Status:** `RESOLVED — 2026-08-11`

**Evidence**

- `AdminRoleService` returns `AdminRole` and lists of `AdminRole`.
- `AdminUserService` returns `AdminUser` and lists of `AdminUser`.
- `AdminSubscriptionService` returns `Subscription`.
- Controllers then query additional repositories to assemble response DTOs.

**Risk**

API behavior depends on lazy entity state and controller-side database access. Transactions, filtering, and response composition are split across layers, making list performance and authorization harder to reason about.

**Possible fix direction**

Keep the monolith, but have application services return complete DTO/read models for API use. Entity-returning methods can remain internal where truly useful.

**Implementation evidence — 2026-08-11**

- `AdminUserService` and `AdminRoleService` already returned `AdminUserResponseDto` / `AdminRoleResponseDto` after Batch 6.1's `ADMIN-DATA-001` work; only `AdminSubscriptionService` still returned `Subscription`.
- Its four entity-returning methods now return `AdminSubscriptionDto` / `SubscriptionDto`, and the `AdminSubscriptionDto` assembly moved out of `SubscriptionAdminController` into the service, where the account and plan relationships resolve inside the service transaction instead of during response rendering.
- `SubscriptionAdminController` no longer injects `SubscriptionMapper`, `SubscriptionOverrideReader`, or `SubscriptionSnapshotReader`, and holds no reference to a persistence entity.

---

### SERVICE-003 — Company and member API services also expose persistence entities

**Status:** `RESOLVED — 2026-08-11`

**Evidence**

`CompanyService` and `MemberService` return JPA entities/lists, and controllers perform DTO mapping after service calls.

**Risk**

This repeats the API/persistence coupling and lazy-loading dependency found in the admin and account areas.

**Possible fix direction**

When API contracts are stabilized, return purpose-built summary/detail read models from application services while keeping internal entity methods package-focused.

**Implementation evidence — 2026-08-11**

- `CompanyService` now returns `CompanyDto` from every method. `CompanyMutationResult` is deleted, mapping moved into `CompanyServiceImpl`, and `CompanyController` holds no mapper and no entity. Its unit test uses the generated `CompanyMapperImpl` so the assertions also cover the projection the service now owns.
- `RoleService` now returns `RoleDto` from every method. The tenant-scoped entity lookup became a private `requireRole` helper shared by the guarded read surface and `previewRoleImpact`; that internal call previously went through `getRole`, whose `@PermissionNode` never applied to it anyway because Spring self-invocation bypasses the proxy. `RoleController` holds no mapper and no entity.
- `MemberService` now returns `MemberDto` from its list and update surfaces, `MemberCreationResult` carries a `MemberDto` instead of a `Member`, and the tenant-scoped lookup became a private helper. `MemberAccessResult` already carried no entity, so the credential-reset surfaces needed no change at all.
- No entity type appears in `CompanyService`, `MemberService`, or `RoleService`, and none of their controllers imports a persistence entity or a mapper.

**Why the first attempt failed, and why the fix was smaller than it looked**

The first attempt also moved the credential-email delivery-status read into the service, which this finding never required. That broke two credential-lifecycle tests and appeared to demand restructuring transactions and auditing. It did not: the finding asks only that services stop returning *entities*, and post-commit response composition is legitimate caller work. Recording the two constraints anyway, because anything that does later move that read must respect both.

**Ordering constraint — relevant only if delivery-status composition is ever moved**

`MemberController` does not merely map entities: it resolves the credential email delivery summary **after** the service transaction commits. `CredentialEmailListener` is a `@TransactionalEventListener(phase = AFTER_COMMIT)`, so a delivery summary read from inside `createMember` / `regenerateInitialAccess` / `resetAccess` always observes `PENDING` instead of `SENT` or `FAILED`. `MemberCredentialLifecycleIntegrationTest` catches this.

Any future move of that read into the service requires the transactional work to be extracted into a collaborator — the shape `CollaborationInitiationStore` already uses — so the summary resolves after commit. The transaction boundary, not the mapping, is what makes the current arrangement correct.

**Second constraint: the extraction must not silently disable auditing.**

`AuditMutationAspect` matches `@annotation(transactional) && @annotation(permissionNode)` on the *same method*. `createMember`, `regenerateInitialAccess`, and `resetAccess` are audited today only because they carry both. Removing `@Transactional` to allow a post-commit summary read would stop auditing member creation and credential resets, and no current test asserts those particular audit rows, so the loss would be silent.

Required shape when this is finished:

- The extracted collaborator carries `@Transactional` plus `@AuditedMutation` with an action code matching what the aspect records today, so the audit row survives the move.
- The service method keeps `@PermissionNode` for the authorization gate and becomes non-transactional, then resolves the delivery summary after the collaborator commits.
- Note that the permission check then runs outside a transaction, so its policy reads no longer join a caller transaction. Confirm that is acceptable against the advisor-order invariant recorded under `AUDIT-001` before relying on it.
- Add a test asserting an `AuditLog` row is still written for member creation, since that is the failure this note exists to prevent.

---

### SERVICE-004 — Plan administration service exposes persistence entities

**Status:** `RESOLVED — 2026-08-11`

**Evidence**

`PlanAdminService` returns `Plan`, `PlanFeature`, `AddOn`, and `QuotaPackage` from 19 methods. `PlanAdminController`, `AddOnAdminController`, and `QuotaPackageAdminController` therefore hold private `toDto` methods and map entities themselves, including a nested `AddOnDto.FeatureItem` projection.

This is the same defect class as `SERVICE-002` and `SERVICE-003`, for a service that neither finding names — `SERVICE-002` lists only the admin role/user/subscription services, and `SERVICE-003` only the company and member services. It is recorded separately rather than silently widening either.

**Risk**

The commercial administration surface keeps API response shape coupled to persistence, which is the coupling Batch 6.3 exists to remove. It is also the surface the future admin panel depends on most heavily.

**Required resolution**

Return read models from `PlanAdminService` and delete the controller-side `toDto` methods, matching what `SERVICE-002` and `SERVICE-003` did. Verify the plan-feature and add-on projections keep the entity graphs added under `MAPPER-001`, so moving the mapping does not reintroduce per-row statements.

**Implementation evidence — 2026-08-11**

- All 21 entity-returning methods on `PlanAdminService` now return read models, including the two `AddOnFeature` surfaces. No entity type remains in the contract.
- The five controller-side `toDto` methods moved into `PlanAdminReadModels`, a component in the plan service package. `PlanAdminController`, `AddOnAdminController` and `QuotaPackageAdminController` no longer import a persistence entity or perform any mapping.
- Method bodies and their `@PermissionNode` / `@Transactional` annotations were left in place; only return expressions were wrapped, so no guarded method changed its identity or moved behind a self-invocation.
- `LazyMappingQueryCountIntegrationTest` still passes, confirming the `MAPPER-001` entity graphs continue to cover the projections now that they run inside the service.

---

### MODULES-003 — Identity depends on member persistence for authentication facts

**Status:** `CONFIRMED`

**Evidence**

`AuthServiceImpl`, `ClientCredentialAuthenticationService`, and `CredentialLifecycleService` all import `com.hiveapp.platform.client.member.domain.repository.MemberRepository`. Identity therefore reads another domain's schema directly, which is the mirror image of the platform-to-identity coupling closed under `MODULES-001`.

Found by `CrossDomainAccessRuleTest` while implementing that rule; the reverse assertion is deliberately not enabled until this is fixed.

**Risk**

Membership is the tenant boundary. Identity resolving it through the member table means a change to membership semantics — the one-active-membership invariant, deactivation, or scoping — must be re-implemented correctly in the authentication path, with nothing forcing the two to agree.

**Why it is not simply MODULES-001 in reverse**

Authentication runs *before* any permission context exists, so the guarded `MemberService` cannot serve it — calling it would evaluate Permissionizer policies against an unauthenticated actor. The fix needs a small unguarded membership lookup owned by the member domain and consumed by identity, not a call into the existing service.

**Required resolution**

Introduce a membership lookup in the member domain exposing only the facts authentication needs (active membership for a user, its account, its active state), have the three identity classes consume it, then enable the reverse assertion in `CrossDomainAccessRuleTest`.

---

### AUTH-001 — Email identity is not canonicalized

**Status:** `RESOLVED — 2026-07-16`

**Evidence**

Registration checks and stores `request.email()` unchanged. Login also searches with the supplied email unchanged. No trimming or lowercase normalization is visible in the identity service implementation.

`UserRepository.findByEmail()` and `existsByEmail()` are exact derived queries and provide no explicit case-insensitive/canonical lookup.

**Risk**

Case and surrounding whitespace can create duplicate-looking users or make login behavior depend on database collation. Email sent to invitations and admin searches may use a different form than authentication.

**Possible fix direction**

Define one email canonicalization policy and apply it before uniqueness checks, persistence, login, invitations, and searches. Preserve a display form separately only if needed.

**Implementation evidence — 2026-07-16**

- `EmailIdentity` defines one trim-and-lowercase policy using `Locale.ROOT`.
- Registration, credential lookup, admin authentication, invitation creation, and persisted `User` email callbacks use the same policy.
- Integration coverage proves a mixed-case registration is stored canonically and can be authenticated with different casing.

---

### AUTH-002 — Refresh-token type and revocation behavior are not visible at the service boundary

**Status:** `RESOLVED FOR CURRENT SINGLE-PROCESS IN-MEMORY STAGE — UPDATED 2026-08-31`

**Evidence**

`AuthServiceImpl.refresh()` validates the supplied JWT and extracts its user ID, but does not explicitly check that the token is a refresh token. It issues a new refresh token without storing, revoking, or marking the previous one as used.

`JwtTokenProvider` signs access and refresh tokens with the same key. Refresh tokens have no token-type claim, and `validateToken()` validates signature/expiry only. Therefore the refresh service accepts a valid access token as input.

**Risk**

An access token is accepted at the refresh endpoint. Stolen refresh tokens also remain reusable until expiration, and logout/password-change revocation is unavailable.

**Required fix direction**

Add explicit audience/token-use claims, require `refresh` use at refresh endpoints, preserve ADMIN versus CLIENT audience, and define rotation/reuse detection/revocation behavior.

**Implementation evidence — 2026-07-16**

- Every token has explicit `CLIENT`/`ADMIN` audience and `ACCESS`/`REFRESH` use plus a unique pair ID.
- `TokenSessionService` registers both members of the pair, consumes each refresh token atomically
  once, rotates it on refresh, rejects reuse/audience mismatch, and revokes both tokens on logout or
  an access-reset operation.
- Both security filters require an audience-matching, currently active access session, so a signed
  token revoked by Account suspension, cancellation, credential reset, or deactivation cannot keep
  authenticating until JWT expiry.
- All session state remains intentionally process-local while the application is unpublished and
  single-process. Restart invalidates every session safely. A shared persistent session store is a
  mandatory deployment prerequisite before multi-instance production, not an unrecorded promise of
  the current generated-schema environment.

---

### AUTH-003 — Client authentication is user-based while authorization is membership/workspace-based

**Status:** `RESOLVED — 2026-07-16`

**Evidence**

- Login authenticates a `User` and checks only `User.isActive` directly.
- Issued client tokens identify the user but not the selected account or member.
- The entity model permits a user to have multiple `Member` records across accounts.
- `UserDetailsServiceImpl` loads only `User` and builds `HiveAppUserDetails` from user ID, email, password hash, and `User.isActive`; it performs no membership/workspace check.
- `MemberRepository.findFirstByUserId()` is used by `SecurityContextService` to select membership without an explicit workspace choice.

**Risk**

If request context later selects an arbitrary membership, the same token may operate in the wrong workspace. A deactivated membership could also remain usable if later security layers do not reject it.

**Required fix direction**

The product decision is one active client Account membership per user. Enforce it with database constraints and invitation/provisioning validation, replace `findFirstByUserId()` with an unambiguous lookup, require active Account and Member state on every client request, and remove multi-workspace-shaped client APIs/UI assumptions. B2B access must remain delegation rather than provider-account membership.

**Implementation evidence — 2026-07-16**

- Batch 1.1 replaced arbitrary membership selection with the single-active-membership contract and rejects a second active workspace membership in application flows.
- `SecurityContextService` resolves authorization context transactionally from the authenticated user, requires active membership, and rejects a suspended direct, client, or provider Account before permission evaluation.
- B2B continues to use collaboration delegation rather than creating provider-account membership.
- Integration coverage proves a suspended workspace returns 403 even when the user still holds a valid access token.

---

### AUTH-004 — Registration uniqueness check is race-prone unless database errors are translated

**Status:** `RESOLVED — 2026-07-16`

**Evidence**

Registration performs `existsByEmail()` followed by `save()`. Two concurrent registrations can both pass the existence check before the database unique constraint rejects one.

**Risk**

The losing request may receive an internal database error rather than the intended duplicate-resource response.

**Verify later**

Inspect global exception handling and registration concurrency tests. Keep the friendly pre-check, but treat the database unique constraint as the final authority.

**Implementation evidence — 2026-07-16**

Registration retains the friendly canonical-email pre-check, then uses `saveAndFlush()` so the unique constraint is evaluated before provisioning. A `DataIntegrityViolationException` is translated to the same structured `DuplicateResourceException`, and a focused test proves provisioning does not run for the losing request.

---

### AUTH-005 — User-details lookup leaks invalid UUID parsing behavior

**Status:** `RESOLVED — 2026-07-16`

**Evidence**

`UserDetailsServiceImpl.loadUserByUsername(String)` calls `UUID.fromString(userId)` before repository lookup and does not translate `IllegalArgumentException` to `UsernameNotFoundException`.

`AdminUserDetailsServiceImpl` repeats the same unhandled UUID parsing behavior.

**Risk**

An invalid authentication principal string can escape through a different exception path than an unknown user, producing inconsistent authentication failure handling or an internal error depending on Spring configuration.

**Possible fix direction**

Parse defensively and translate invalid or unknown identifiers into the same authentication-safe exception.

**Implementation evidence — 2026-07-16**

Both client and admin user-details services translate malformed UUIDs to the same non-disclosing `UsernameNotFoundException` path used for unknown identities. Focused tests prove malformed input never reaches either repository.

---

### EVENT-001 — `UserRegisteredEvent` is unused dead architecture

**Status:** `REMOVED AS OBSOLETE — 2026-07-16`

**Evidence**

- `UserRegisteredEvent` exists and carries user ID, email, event ID, and timestamp.
- No publisher or listener references it anywhere in `backend/src/main/java`.
- `AuthServiceImpl` instead calls workspace provisioning synchronously and explicitly comments that no event is needed.

**Risk**

Dead event types confuse future development about whether registration consequences are synchronous or event-driven. An AI may later add a second provisioning path and create duplicate workspaces.

**Possible fix direction**

Keep synchronous provisioning for the organized monolith if that is the chosen transaction model, and remove the unused event. Retain it only if a concrete listener and failure contract are deliberately introduced.

**Implementation evidence — 2026-07-16**

`UserRegisteredEvent` and its otherwise-unused `DomainEvent` marker are deleted. `AuthServiceImpl.register()` remains the single explicit registration path and calls `WorkspaceProvisioningService.provision()` synchronously inside the registration transaction. Active credential-email events are separate, purpose-specific after-commit delivery events and were retained.

---

## Client account/workspace findings

### ACCOUNT-001 — Registration can succeed with no subscription

**Status:** `RESOLVED — 2026-07-16`

**Evidence**

- `WorkspaceProvisioningServiceImpl.provisionFreeSubscription()` treats a missing FREE plan as a warning and returns normally.
- The account and owner member remain created.
- `AuthServiceImpl.register()` then issues client tokens as if provisioning succeeded.

**Risk**

The user receives a successful registration and an active workspace with no entitlement. Plan policy can deny feature requests, leaving the product apparently broken immediately after signup.

**Required fix direction**

Make the FREE plan a verified startup invariant or fail and roll back registration when required initial entitlement cannot be created. Add an end-to-end registration test proving that every successful registration has exactly one usable subscription snapshot.

**Implementation evidence — 2026-07-16**

- Provisioning now requires an existing active FREE plan before creating the Account, owner Member, or Subscription.
- Missing or inactive FREE configuration throws a controlled conflict and the surrounding registration transaction rolls back the newly inserted User.
- The initial Subscription is flushed with an immutable entitlement snapshot before registration can issue tokens.
- Integration coverage proves every successful registration has exactly one ACTIVE/TRIALING subscription with a non-empty FREE snapshot and that unavailable FREE configuration leaves no User behind.

---

### ACCOUNT-002 — Workspace deactivation consequences are not implemented in the account service

**Status:** `RESOLVED FOR CURRENT STAGE — 2026-07-16`

**Evidence**

`AccountShellServiceImpl.deactivateAccount()` only sets `Account.isActive=false`. It does not directly revoke tokens, deactivate members, cancel/suspend the subscription, collaborations, or other workspace activity.

`SecurityContextService` validates member activity but never checks `Account.isActive`; plan entitlement also does not check account activity.

**Risk**

If request context/security checks do not reject an inactive account on every request, a supposedly deactivated workspace may remain operational. If they do reject it, related records still need clear lifecycle and reactivation semantics.

**Required fix direction**

Make active-account validation a shared request/security invariant and define the lifecycle effects on members, tokens, subscriptions, collaborations, data access, and reactivation.

**Implementation evidence — 2026-07-16**

- Batch 1.2 made active Account state a shared request-context invariant for direct and B2B access; existing access tokens receive 403 immediately after suspension.
- Account deactivation now revokes every current CLIENT refresh session belonging to members of that Account.
- Members, subscriptions, collaborations, and business data are deliberately preserved rather than cascade-mutated. Suspension is an access boundary, allowing a future explicit reactivation operation without reconstructing business state.
- Integration coverage exercises the real deactivation endpoint, immediate access denial, and refresh rejection.

---

### ACCOUNT-003 — Workspace slug creation is check-then-insert and race-prone

**Status:** `RESOLVED — 2026-07-16`

**Evidence**

Slug generation calls `existsBySlug()` and then later inserts the account. Collision fallback repeatedly generates a short `Math.random()` suffix and checks again before insert.

**Risk**

Concurrent registrations can select the same available slug and one will fail at the database unique constraint. The resulting exception may surface as an internal error rather than retrying or returning a controlled conflict.

**Possible fix direction**

Keep the database unique constraint authoritative and use a collision-resistant identifier/retry strategy with translated constraint failures.

**Implementation evidence — 2026-07-16**

The check-then-insert loop and `Math.random()` suffix are removed. Initial slugs combine a bounded readable email prefix with the complete unique User UUID, so two different registrations cannot calculate the same slug. The existing generated-schema unique constraint remains the final persistence invariant. Tests prove identical email prefixes produce distinct user-bound slugs. No Flyway migration is added during the disposable in-memory stage.

---

### ACCOUNT-004 — Workspace provisioning is not explicitly idempotent

**Status:** `RESOLVED BY PRODUCT/IMPLEMENTATION DECISION — 2026-07-16`

**Evidence**

`WorkspaceProvisioningServiceImpl.provision()` does not check whether the user already owns an account or already has an owner member/subscription before inserting all three.

**Risk**

A retried or accidentally duplicated provisioning call relies on database failures rather than returning the existing completed workspace or safely completing missing steps.

**Required decision**

Decide whether provisioning must be exactly-once through the registration transaction or safely idempotent. Test retries and partial-state recovery according to that decision.

**Implementation evidence — 2026-07-16**

- Registration remains intentionally non-idempotent: repeating credentials is a duplicate identity request and must not silently return an existing user's authentication material.
- The internal provisioning operation is retry-safe: if the User already owns a complete workspace, it returns the existing account ID/slug with `created=false` and creates no duplicate Account, owner Member, or usable Subscription.
- An existing but structurally incomplete workspace fails explicitly instead of guessing, reactivating suspended records, or creating a second partial aggregate.
- Atomic registration/provisioning uses one transaction, so ordinary failures roll back rather than requiring an idempotency-log table. No extra database infrastructure is justified at the current stage.

---

### ACCOUNT-005 — Account service contract still exposes a persistence entity

**Status:** `RESOLVED — 2026-07-16`

**Evidence**

`AccountShellService.getAccount()` returns `Account`, and `AccountController` maps it afterward.

**Risk**

This repeats the entity-to-controller coupling found in platform admin, though the current mapper is small.

**Possible fix direction**

Return `AccountDto` or an application read model from the API-facing service when service contracts are cleaned up.

**Implementation evidence — 2026-07-16**

`AccountShellService.getAccount()` now returns `AccountDto`, mapping inside the service transaction. `AccountController` no longer receives or maps a persistence entity, and the unused duplicate `AccountService` interface was removed.

---

### QUOTA-001 — Member/company quota checks are inefficient and race-prone

**Status:** `RESOLVED FOR CURRENT STAGE — 2026-07-16`

**Evidence**

- Company creation loads every company with `findAllByAccountId().size()` to calculate usage.
- Member creation loads every member with `findAllByAccountId().size()`.
- Both perform quota check and insert as separate operations without an account lock visible in these services.
- Both counts include inactive records because repositories do not filter active state.

**Risk**

Large workspaces incur unnecessary row loading. Concurrent requests can both pass the quota check and exceed the purchased limit. Deactivated records may permanently consume quota even if product expectations say otherwise.

**Required resolution**

- Apply the decided member rule: count every non-deactivated member, including newly created/unactivated members and the owner; exclude deactivated historical members. Invitation reservations no longer exist.
- Exclude inactive Companies from the active-Company quota. Reactivation must atomically recheck current quota and plan eligibility.
- Use database count queries matching each lifecycle rule.
- Serialize quota-sensitive creation per account or use an atomic reservation/constraint strategy.
- Add concurrent request tests at the exact quota boundary.

**Implementation evidence — 2026-07-16**

- Member quota usage is now `countByAccountIdAndIsActiveTrue()`, so the owner and every other active member count while deactivated history does not.
- Company quota usage is now `countByAccountIdAndIsActiveTrue()`, so ordinary deactivation releases the active-company slot.
- Both creation services acquire the same Account pessimistic write lock before counting and keep it through insertion, serializing quota decisions without introducing reservation infrastructure.
- Integration coverage proves inactive records release capacity and simultaneous requests at the exact FREE boundary yield one success and one quota rejection rather than exceeding the plan.
- Company reactivation is now implemented by `COMPANY-001` as a distinct quota- and entitlement-checked lifecycle action.

---

### COMPANY-001 — Company deactivation has no defined dependent-data behavior

**Status:** `RESOLVED FOR REVERSIBLE LIFECYCLE — 2026-07-16` (`PERMANENT PURGE DEFERRED`)

**Evidence**

`CompanyServiceImpl.deactivateCompany()` only sets `Company.isActive=false`. Existing departments, company-scoped roles/assignments, member overrides, and collaborations are unchanged. List/read methods still return inactive companies.

`SecurityContextService` loads the requested company but does not check `Company.isActive`; both direct and B2B context can therefore still be built for a deactivated company.

**Risk**

An inactive company may remain usable through permissions or B2B paths unless all runtime services consistently reject it. Future HR, payroll, and accounting data also need preservation/read-only rules.

**Required fix direction**

- Block ordinary operational context, Company-scoped roles/overrides, new delegation, and B2B access for inactive Companies while preserving separately authorized Account-level historical/read-only access.
- Keep deactivation reversible and preserve Groups, memberships, access configuration, audit, and module records. Exclude inactive Companies from active-Company quota; reactivation atomically rechecks quota, plan/module entitlement, Account state, and current assignment/collaboration validity.
- Add a distinct Account-owner-only permanent purge flow that may delete a populated Company's owned data. It must never reuse ordinary `company.delete` manager authority.
- Build a module-contributed impact inventory that identifies deletable Company-owned records, retained/anonymized records, external/shared dependencies, blockers, and module-specific retention requirements. Revalidate it when execution begins.
- Require fresh owner authentication, exact Company-name confirmation, and clear irreversible warnings. Suspend/remove the Company from ordinary operation immediately, schedule purge after seven days, and allow only the Account owner to cancel during that window.
- Treat export/backup as a future enhancement rather than a dependency of the initial purge flow.
- Implement explicit retention policies per data category and jurisdiction. Never substitute indefinite soft deletion for a claimed permanent purge; physically erase or irreversibly anonymize data when no valid retention duty remains.
- Keep legally retained records encrypted and outside ordinary Account access—including after Account compromise—then erase/anonymize them when retention expires.
- Execute large purges as tracked jobs after suspending writes. Preserve a minimal deletion receipt outside the deleted Company. On partial failure, keep the Company suspended, expose the exact failed stage, support idempotent retry, and never report success.
- Add inactive-context, quota/reactivation race, B2B cutoff, owner-only purge, stale preview, shared dependency, retention blocker, partial failure, and cross-tenant deletion tests.

**Implementation evidence — 2026-07-16**

- `SecurityContextService` rejects an inactive Company after verifying the direct member or B2B collaboration participant, so Company-scoped roles, overrides, owner authority, and delegated B2B permissions cannot operate through that scope and the inactive state is not disclosed before participant validation.
- Deactivation remains reversible and preserves Company-owned records, role assignments, overrides, and collaborations. New member-role grants, direct permission grants, role-permission grants, B2B requests/acceptance, and B2B permission grants are blocked while inactive; read/removal paths remain available so administrators can repair stored configuration.
- `POST /api/v1/companies/{id}/reactivate` is protected by the distinct Permissionizer action `platform.company.reactivate`. Reactivation locks the Account and Company, requires an active Account, atomically rechecks active-Company quota, and validates the current plan entitlement of positive member-role, override, and active B2B grants before restoring operation.
- Active-company quota creation and reactivation share the Account pessimistic lock, so reusing a released quota slot prevents later reactivation instead of exceeding the limit. Inactive roles are also excluded from runtime and effective-permission evaluation.
- Tests cover direct inactive-context rejection, B2B cutoff and preserved-grant restoration, reused-quota rejection, lock/validator invocation, inactive-company grant prevention, country-independent lifecycle behavior, and unavailable preserved B2B permissions.
- Permanent purge is deliberately not disguised as ordinary deletion. The owner-only preview/fresh-auth/seven-day job, retention, partial-failure, and deletion-receipt workflow remains deferred to the Phase 5 operational-lifecycle work and does not block reversible Company management or the Group model.

---

### COMPANY-002 — Company identity editing does not implement the decided field-sensitive contract

**Status:** `RESOLVED FOR CURRENT SHELL — 2026-07-16` (`CENTRAL AUDIT DEFERRED TO AUDIT-001`)

**Evidence**

- `CreateCompanyRequest` requires only `name`; `country` is optional despite being the decided minimum jurisdiction field.
- `UpdateCompanyRequest` and `CompanyServiceImpl.updateCompany()` can update only name, legal name, industry, and country. Tax ID, address, and logo cannot be managed through the update flow.
- The update service applies fields directly with no normalization, duplicate-tax warning, sensitive-field impact check, country-dependent-record check, or explicit before/after audit behavior.
- `CompanyDto` omits tax ID, address, and logo, so the management UI cannot reliably show or edit the complete decided identity.

**Risk**

The Company can be created without jurisdiction, legal identity updates may silently invalidate future payroll/accounting assumptions, and the UI cannot manage fields already present on the entity. Global tax-ID uniqueness would also risk cross-tenant information leakage.

**Required fix direction**

- Require nonblank normalized name and country at creation; keep the remaining identity fields optional.
- Keep one Permissionizer Company-update action for all editable profile/legal fields, then perform field-sensitive validation and impact checks inside the service.
- Support display name, logo, industry, address, legal name, and tax ID in coherent command/read DTOs. Apply length/format constraints and actor-aware before/after audit.
- Normalize tax ID and warn—not globally reject—when the same Account/country already contains it. Never reveal another Account's use of the value.
- Allow ordinary country edits only before country-dependent payroll/accounting/tax/legal records exist. Otherwise require a later controlled jurisdiction-change/new-Company flow.
- Add creation-validation, partial-update, blank-field, duplicate-warning, cross-tenant privacy, sensitive-impact, audit, and country-lock tests.

**Implementation evidence — 2026-07-16**

- Company creation now requires bounded, nonblank name and two-letter country inputs. The service trims names and optional text, uppercases country/tax ID, and enforces the same required rules when called outside controller validation.
- Create, patch, and read contracts now consistently expose legal name, tax ID, industry, country, address, and logo URL. Patch supports field-sensitive updates through the existing single `platform.company.update` Permissionizer action.
- Duplicate normalized tax IDs are warnings rather than rejection. Queries are scoped to the same Account and country and exclude the edited Company, with integration coverage proving another Account's use is never disclosed.
- Country changes go through `CompanyCountryChangeGuard`. Business packages that own payroll/accounting/tax/legal records implement the small `CompanyCountryDependency` contract to name a blocker; ordinary edits remain allowed while no country-dependent domain reports records.
- Focused and integration tests cover normalization, complete metadata, required-country validation, partial service updates, duplicate warnings, cross-Account privacy, and a contributed country-dependent blocker.
- Central actor-aware before/after audit infrastructure remains owned by `AUDIT-001`; this batch does not create a second Company-only audit mechanism. A future controlled jurisdiction-change/new-Company flow will build on the dependency guard when such business records exist.

---

### MEMBER-001 — Authorized members can deactivate the workspace owner

**Status:** `RESOLVED — 2026-07-16`

**Evidence**

`MemberServiceImpl.deactivateMember()` prevents actors from deactivating themselves but does not protect `member.isOwner=true` targets.

**Risk**

A delegated staff administrator with `platform.staff.delete` can deactivate the owner membership and potentially lock the owner out of their workspace.

**Required fix direction**

Protect the owner/last owner. If ownership transfer is introduced, require explicit transfer before owner deactivation. Add owner-target abuse tests.

**Implementation evidence — 2026-07-16**

`MemberServiceImpl` now rejects both ordinary self-deactivation and any request targeting `Member.isOwner`, returning an explicit ownership-transfer requirement. Focused tests cover a different actor targeting the owner so the protection does not depend only on the self-check. Ownership transfer remains a deliberate future flow; until then the owner cannot be deactivated.

---

### MEMBER-002 — Direct member addition allows duplicate memberships

**Status:** `RESOLVED FOR CURRENT GENERATED SCHEMA — 2026-07-16`

**Evidence**

- `MemberRepository` provides `existsByAccountIdAndUserId()`.
- `MemberServiceImpl.addMember()` does not call it.
- The `Member` entity has no reviewed composite unique constraint on account and user.

**Risk**

The same user can receive multiple member rows in one workspace, causing ambiguous context selection, duplicated quota usage, conflicting owner/active state, and inconsistent role grants.

**Required fix direction**

Add a database unique constraint for `(account_id, user_id)`, make add/invitation acceptance idempotent or conflict clearly, and translate concurrent insert violations.

**Implementation evidence — 2026-07-16**

- `members(account_id, user_id)` is unique in the generated schema.
- Direct addition checks an existing membership in the requested Account before checking the separate one-active-workspace rule.
- `saveAndFlush()` makes the constraint authoritative and converts a losing insert race to the same clear 409 workspace-membership conflict.
- Account serialization also makes concurrent same-user additions deterministic; integration coverage proves exactly one row and one conflict.
- The obsolete invitation acceptance path is removed in Batch 1.5 rather than extended.

---

### MEMBER-003 — Member role assignment allows duplicates and inactive roles

**Status:** `RESOLVED FOR CURRENT GENERATED SCHEMA — 2026-07-16`

**Evidence**

`MemberServiceImpl.assignRole()` does not check for an existing same-scope assignment and does not reject `role.isActive=false`.

Invitation acceptance also assigns the invitation's stored role without rechecking whether that role is still active.

**Risk**

Duplicate assignments distort UI/counts and complicate precise removal. Inactive roles can be attached even though their expected runtime behavior is unclear.

**Required fix direction**

Enforce a null-safe unique assignment key, define inactive-role assignment behavior, and return an idempotent result or clear conflict.

**Implementation evidence — 2026-07-16**

- Inactive roles are rejected before assignment.
- Account-wide and Company-scoped duplicates are detected through scope-specific repository checks and return a clear 409 conflict.
- The database uses the non-null `scope_key` described under DATA-001, so concurrent account-wide assignments cannot evade uniqueness through nullable `company_id` behavior.
- Flush-time constraint failures are translated to the same conflict. Integration coverage proves the database rejects a duplicate account-scoped assignment.
- Invitation acceptance is intentionally not patched because Batch 1.5 removes that subsystem.

---

### MEMBER-004 — Member DTO cannot support the implemented management flows cleanly

**Status:** `IMPLEMENTED — 2026-08-11`

**Evidence**

The member list controller returns `MemberDto`, which lacks user email, assigned roles, company scopes, and override summary. Yet the same area exposes role assignment and override operations.

**Risk**

The client UI cannot render a trustworthy member-access management screen without additional undocumented queries or guessing.

**Required resolution**

Define separate member summary and member access-detail read models with safe user identity, active/owner status, scoped roles, and overrides.

**Implementation evidence — 2026-08-11**

- `GET /api/v1/members/{id}/authorization`, protected by `platform.staff.read_authorization`, returns the safe member summary plus scoped role assignments and direct overrides.
- Role status, assignment effect scope, Company identity, override decision/effectiveness, and audit identity are explicit.
- Account isolation is enforced before loading the detail, and eager bulk repository methods keep mapping inside the transactional read boundary.

---

### MEMBER-005 — Member deactivation does not implement the decided offboarding lifecycle

**Status:** `RESOLVED FOR THE CURRENT REVERSIBLE LIFECYCLE — 2026-08-12`

**Evidence**

`MemberServiceImpl.deactivateMember()` only sets `Member.isActive=false`. It does not revoke sessions, invalidate unused activation/reset material, explicitly stop effective permission resolution, protect the owner target, update quota semantics, audit a reason, or prepare impact information.

**Required fix direction**

- Treat deactivation as reversible suspension and immediately revoke sessions and all runtime authorization.
- Invalidate temporary/activation/reset credentials while retaining the private permanent password hash.
- Preserve roles, overrides, home placement, identity, audit, and business references; they regain effect only after reactivation revalidates current Account/plan/role/scope rules.
- Exclude inactive members from active-member quota.
- Block owner deactivation until protected ownership transfer completes.
- Continue rejecting self-deactivation for ordinary members; only a separately authorized Account actor within the target member's management scope may deactivate/reactivate membership.
- Permit hard deletion only for a mistaken, never-activated member with no login, audit, approval, or business history and only after a transactional impact check.
- Add actor/reason/result audit and future module-specific reassignment/impact previews rather than deleting or silently reassigning owned records.

**Implementation evidence — 2026-07-16**

- Deactivation remains a reversible state change: the Member row, identity, roles, overrides, and business references are preserved.
- The target is flushed inactive before every current CLIENT refresh session for its User is revoked. Existing access tokens also fail immediately because security context requires an active membership.
- Active-member quota excludes the suspended record, while owner and self-deactivation protections prevent accidental workspace lockout.
- Integration coverage proves both an existing access token and an issued refresh token fail after deactivation.
- Batch 1.5 now invalidates pending email tokens, unused temporary passwords, restricted initial-access sessions, and ordinary refresh sessions during deactivation. Reactivation impact validation, reason/audit capture, hard-delete eligibility, and module-contributed reassignment previews remain in the later lifecycle, audit, and operations batches rather than being approximated here.
- The 2026-08-12 operational pass adds the client reactivation endpoint and UI, locks the Account row before reactivation, rejects suspended Accounts, rechecks the active-member quota, and persists the restored membership.
- Deactivation and reactivation now require a non-blank, bounded reason. The mutation audit captures the actor, target arguments including that reason, outcome, and time; the UI explains the reversible effects before confirmation.
- Permanent hard deletion, ownership transfer, and future business-module reassignment/impact previews remain separate product capabilities because their entities and contracts do not yet exist. They are not silently approximated by the reversible lifecycle.

---

### EVENT-002 — Account event artifacts are unused leftovers

**Status:** `REMOVED AS OBSOLETE — 2026-07-16`

**Evidence**

- `AccountCreatedEvent` has no publisher or listener references.
- `UserRegistrationEventListener.java` contains only comments saying provisioning is now synchronous.
- `AccountService` has no implementation or caller and appears to be another abandoned contract.

**Risk**

These leftovers obscure the chosen synchronous monolith flow and invite future duplicate implementations.

**Possible fix direction**

Remove dead event/listener/service artifacts after confirming tests and external reflection do not depend on them.

**Implementation evidence — 2026-07-16**

`AccountCreatedEvent` and the comment-only `UserRegistrationEventListener` are deleted after repository-wide reference verification. The unused duplicate `AccountService` had already been removed in Batch 1.3. No active publisher, listener, reflection registration, or test depended on these artifacts; the clean full backend suite remains green at 265 tests.

---

## Client role findings

### ROLE-001 — Deleting a role does not deliberately handle member assignments

**Status:** `RESOLVED — 2026-07-17`

**Evidence**

- `RoleServiceImpl.deleteRole()` directly deletes the role after checking only that it is not a system role.
- `Role` cascades its `RolePermission` children but has no owned relationship to `MemberRole` assignments.
- No member-assignment cleanup, affected-count check, replacement flow, or deletion preview is present.

**Risk**

Depending on database foreign keys, deletion will either fail unexpectedly or rely on schema-level cascades that are invisible to the service. Users may lose access without impact visibility.

**Required resolution**

Implement the decided lifecycle. Permit hard deletion only for a never-assigned custom role with no history/reference. Used roles become inactive or archived, stop granting immediately, and retain assignments/audit. Add transactional impact checks plus previews of affected members, Account/Company scopes, lost permissions, and workflow ownership.

**Implementation evidence — 2026-07-17**

- Role mutation and assignment paths share a pessimistic role lock. The persistent `everAssigned` marker is set on first assignment and is never cleared.
- Hard deletion is limited to custom roles with `everAssigned=false` and no current assignment. Used roles return an actionable conflict directing the operator to deactivate or archive instead.
- Impact previews report current assignments, distinct/active members, Account and Company scopes, and permissions gained or lost. Used-role lifecycle tests prove assignments are retained after deactivation and removal does not make the role hard-deletable.

---

### ROLE-002 — System-role permissions can still be modified

**Status:** `RESOLVED — 2026-07-17`

**Evidence**

Update and delete reject `role.isSystemRole=true`, but `addPermissionToRole()` and `removePermissionFromRole()` do not.

**Risk**

An actor can change the effective meaning of a supposedly protected system role while being unable to rename or delete it.

**Required resolution**

System/Platform templates are fully read-only to tenant role-management operations, including permission add/remove. Platform evolution uses immutable versions and explicit Account adoption/copy rather than in-place tenant mutation.

**Implementation evidence — 2026-07-17**

- Every tenant mutation path—metadata update, permission grant/revoke, lifecycle change, and deletion—rejects `isSystemRole=true` with HTTP 403.
- The explicit Duplicate operation may copy a system template into an independent `INACTIVE`, unassigned custom role for tenant adoption; it never changes the source template.
- Integration coverage exercises the system boundary across update, permission mutation, archive, delete, and duplicate.

---

### ROLE-003 — Role activation state is incomplete and internally inconsistent

**Status:** `IMPLEMENTED FOR CURRENT SHELL — 2026-07-17` (`CENTRAL AUDIT DEFERRED TO AUDIT-001`)

**Evidence**

- `Role` contains `isActive`.
- No client role API/service action activates or deactivates roles.
- `RoleDto` does not expose active state.
- Runtime member-role permission query does not filter inactive roles.

**Risk**

The field cannot be managed or understood by clients, yet stale/inactive database values still grant access.

**Required resolution**

Implement `ACTIVE`, `INACTIVE`, and `ARCHIVED` behavior in the API/read models, assignment validation, runtime permission queries, impact UX, and audit. Inactive/archived roles grant nothing and cannot be newly assigned. Reactivation revalidates current version, entitlement, grantability, member, and scope rules.

**Implementation evidence — 2026-07-17**

- `RoleStatus` replaces the unmanaged boolean with `ACTIVE`, `INACTIVE`, and terminal `ARCHIVED`; new and duplicated roles start inactive. Role DTOs expose status, scope, system/custom identity, assignment history, definition revision, and optimistic version.
- Explicit activate/deactivate/archive actions enforce the state machine. Activation revalidates Account/Company activity, a non-empty permission set, client-role grantability, and current plan entitlement. Inactive/archived roles cannot be assigned and grant no runtime permissions.
- Existing assignments are deliberately retained through deactivation/archive. Archived roles are read-only and cannot be reactivated.
- Central actor-aware before/after audit remains owned by `AUDIT-001`; the role model now exposes the version/revision and impact data that audit can record.

---

### ROLE-004 — Role permission keys may not match the declared feature contract

**Status:** `VERIFIED — NO DEFECT — 2026-07-17`

**Evidence**

`RoleServiceImpl.getRole()` declares permission key `read`, while list roles declares `view` and product documents refer primarily to `platform.rbac.view`.

**Risk**

The detail endpoint may generate an undeclared/unmapped permission, fail strict registry seeding, or require a permission no role picker can grant.

**Verification result — 2026-07-17**

`read` and `view` are intentional distinct actions for detail and list access. Every annotated RoleService action is declared in `WorkspaceRolesFeature` and appears as a fully qualified `platform.rbac.*` key in Permissionizer's generated `PlatformPermissions` catalog. Unknown permission codes fail with `ResourceNotFoundException`/HTTP 404, while known but non-client-grantable codes fail grant validation. No Permissionizer modification was required.

---

### ROLE-005 — Shared custom-role edits have no impact workflow

**Status:** `IMPLEMENTED FOR IMPACT WORKFLOW — 2026-07-17` (`DELEGATION CEILING: RBAC-003`; `CENTRAL AUDIT: AUDIT-001`)

**Evidence**

Current role permission add/remove operations mutate the shared role directly. They provide no affected-assignment/member/scope preview, no server-side impact recheck contract, no Duplicate-for-staged-rollout flow, and no reviewed before/after actor-aware audit.

**Required fix direction**

- For Account/Company custom roles, show the affected assignments, members, scopes, and permissions gained/lost before confirmation.
- Recheck authorization, scope, entitlement, delegation ceiling, target protection, and impact transactionally, then apply the edit immediately to every active assignment.
- Invalidate any permission cache/session state that could preserve the old result.
- Provide Duplicate as the explicit staged-rollout path; do not create hidden custom-role versions.
- Audit before/after permission sets, actor, template boundary, affected scopes, and result.
- Keep published Platform templates out of this mutation path; they use immutable versions and explicit adoption.

**Implementation evidence — 2026-07-17**

- Shared-role update, permission grant/revoke, deactivate, archive, and activate operations calculate impact under a role write lock. If assignments exist, the mutation is blocked until the caller supplies the exact previewed role version and assignment count; stale confirmation is rejected and must be previewed again.
- Preview contracts expose assignment/member/scope counts plus current, granted, and lost permissions. Each definition change increments a separate definition revision, while JPA optimistic versioning detects stale role state.
- Duplicate provides the explicit staged-rollout path and creates an independent inactive custom role with copied permissions and no assignments/history. No hidden role versions are introduced.
- Permission mutations validate registry existence, client-role grantability, current plan entitlement, system/custom boundary, archive state, Company activity, Account/B2B scope, and the actor delegation ceiling implemented under `RBAC-003`. Central mutation auditing remains under `AUDIT-001`.
- The complete backend suite passes: 300 tests, 0 failures, 0 errors, 0 skipped.

---

## Invitation findings

### INVITE-000 — Remove the invitation subsystem and replace it with direct member creation

**Status:** `RESOLVED — 2026-07-16`

**Product decision**

Workspace invitation/acceptance is not part of the intended HiveApp flow. Authorized Account actors create members directly, and credential activation is handled separately.

**Required removal/replacement**

- Remove invitation entity, repository, services, controllers, DTOs, events, status enum, email template/service usage, public endpoints, registry feature/permissions, plan assignments, tests, and documentation/UI references.
- Replace duplicated membership admission with one atomic member-creation operation that creates/links `User` and `Member` while enforcing quota, single-Account membership, active scope, initial-role validity, plan entitlement, and actor delegation ceiling.
- Implement the decided credential flow: email activation link when email exists; otherwise generate a strong temporary password, display it once to the authorized manager, and force the member to replace it before any normal application access.
- Give every member a unique username. Keep email optional. If employee number is used as a login identifier, require Account code plus the Account-scoped employee number.
- Add an explicit `passwordChangeRequired`/initial-access state. Sessions issued with that state may only change the password or log out; they must not authorize ordinary HiveApp actions.
- Do not add a standalone password-management page. Place temporary-access status and generate/regenerate/reset actions on the Account's member-management flow and scope every action to that Account.
- An unused temporary password remains valid until first successful use or regeneration. First use consumes it and grants only the restricted password-change session; regeneration replaces it, invalidates the previous value, and displays the replacement once.
- On activation or password replacement, revoke the activation material/temporary credential and invalidate earlier sessions. Hash all stored credentials, rate-limit attempts, and audit creation/reset/activation without secrets.
- An explicit access reset for an activated member must revoke all access/refresh sessions before issuing temporary access. Ordinary profile, employment, role, and scope edits must not silently reset credentials or sessions.
- Authorized Account administrators/managers may manage the temporary-access lifecycle but must never retrieve the member's permanent password, log in using the member's credentials, or have their activity attributed to the member. Protect create/regenerate/reset/unlock/disable operations with Account-scoped Permissionizer permissions.
- Add recovery by one-time reset link for members with verified email. For members without email, do not pretend self-service recovery exists: an authorized Account actor must reset access from the member page and issue a new one-time-visible temporary password.
- Give the Account owner all member-access actions through the owner invariant. Give non-owners only explicit Account-scoped Permissionizer actions and enforce the delegation ceiling.
- If deployment ever contains invitation rows, add an explicit retirement/migration plan rather than leaving redeemable tokens active.

**Implementation evidence — 2026-07-16**

- The invitation entity, repository, services, controllers, DTOs, events/status, registry feature, security routes, usage counting, and tests are deleted. There is no invitation endpoint or redeemable invitation token path.
- `POST /api/v1/members` atomically creates the User, Account-scoped Member, initial credential, and validated initial roles under the Account quota lock. Username is globally unique; email is optional/canonical; employee number is optional and unique inside the Account.
- Email members receive a hashed, one-time, expiring activation link only after commit. Members without email receive a cryptographically strong temporary password in the creation/regeneration response only; only its password hash is stored.
- The first successful temporary-password login consumes that password and issues a single-use restricted token that can only change password or log out. Completing activation/password change clears initial material, revokes earlier sessions, and issues normal access.
- Account-scoped Permissionizer actions protect create/regenerate/reset/unlock. Initial roles are revalidated against Account/Company state, role state/scope, plan entitlement, and the actor's current permission ceiling before any identity is inserted.
- Login supports username, optional email, or Account code plus Account-scoped employee number. Five failed temporary-password attempts lock access until an authorized manager unlocks or regenerates it.
- Activated members with verified email can request a non-disclosing one-time reset link. Without email, an authorized Account actor resets access and receives a new one-time-visible temporary password.
- Focused integration coverage proves one-time use, restricted-token boundaries, employee-number login, regeneration, reset/session revocation, lock/unlock, hashed email activation/reset, replay rejection, and initial-role delegation ceilings. Secret-bearing values are never logged. The clean full backend suite passes all 265 tests with zero failures/errors.
- Audit records remain centralized under `AUDIT-001`; production schema retirement belongs to the future deployment baseline because the current unpublished application uses generated disposable schemas.

The findings below are retained as historical evidence for the removed subsystem; they no longer describe reachable runtime flows.

---

### INVITE-001 — Invitation acceptance bypasses member quota enforcement

**Status:** `REMOVED AS OBSOLETE — 2026-07-16`

**Evidence**

`PublicInvitationServiceImpl.accept()` creates a `Member` directly through `MemberRepository`. It does not call `QuotaEnforcer` or the quota-aware member service.

**Risk**

Workspaces can exceed purchased member limits simply by inviting users instead of adding them through the direct member endpoint.

**Required fix direction**

Use one transactional membership-admission operation for direct add and invitation acceptance. Recheck quota at acceptance time under concurrency control; optionally reserve seats when sending invitations if that becomes the product rule.

---

### INVITE-002 — Invitation token acts as a seven-day login credential for existing users

**Status:** `REMOVED AS OBSOLETE — 2026-07-16`

**Evidence**

- When the invitation email belongs to an existing user, acceptance requires only the invitation token.
- No password or existing authenticated session is required.
- Acceptance issues access and refresh tokens for that existing user.
- Default invitation validity is seven days.

**Risk**

The invitation URL is effectively a magic-login token granting the user's identity, not merely permission to join one workspace. Link leakage can expose every workspace reachable through that user's ambiguous membership context.

**Required decision**

Choose explicitly:

1. Existing users must authenticate before accepting; or
2. Invitations are deliberate magic-login links with shorter lifetime, hashed storage, strict one-time use, audience/scope binding, audit, and clear user communication.

The safer default is authenticated acceptance for existing users.

---

### INVITE-003 — Invitation token is stored in plaintext

**Status:** `REMOVED AS OBSOLETE — 2026-07-16`

**Evidence**

The raw 256-bit invitation token is stored in `Invitation.token` and looked up directly.

**Risk**

A database read leak exposes immediately redeemable invitation/login links. This risk is more severe because acceptance can issue a session for an existing user.

**Possible fix direction**

Store a cryptographic hash of the token, send the raw value only in the email, and compare by hash. Preserve one-time-use and expiry checks.

---

### INVITE-004 — Stored invitation role/scope is not safely revalidated on acceptance

**Status:** `REMOVED AS OBSOLETE — 2026-07-16`

**Evidence**

At acceptance, the service does not recheck that:

- The account and company remain active.
- The role remains active and belongs to the same account/company.
- The role's permissions remain within plan entitlement.
- The original inviter/actor was allowed to delegate every permission in the role.

**Risk**

State can change during the seven-day invitation window, yet acceptance applies the stale assignment.

**Required fix direction**

Revalidate all current invariants transactionally at acceptance. Return an actionable conflict requiring the workspace administrator to issue a corrected invitation.

---

### INVITE-005 — Invitation email normalization and duplicate protection are incomplete

**Status:** `REMOVED AS OBSOLETE — 2026-07-16`

**Evidence**

- Existing-user lookup uses the request email before lowercasing it.
- Invitation persistence/check lowercases the email.
- User registration/login do not yet canonicalize emails.
- Duplicate pending invitations use a check-then-insert query without a reviewed database uniqueness strategy.

**Risk**

Case variants can bypass existing-member detection or produce inconsistent user matching. Concurrent sends can create multiple pending tokens.

**Required fix direction**

Use the same canonical email policy as identity and enforce the intended pending-invitation uniqueness at the database/transaction level.

---

### INVITE-006 — Expiration maintenance scans other accounts and mutates inside a read-only flow

**Status:** `REMOVED AS OBSOLETE — 2026-07-16`

**Evidence**

- Listing one account's invitations queries every expired pending invitation globally, filters the account in memory, and saves each match separately.
- Public `validate()` is marked `@Transactional(readOnly=true)`, but `findValidInvitation()` attempts to mark an expired invitation and save it.

**Risk**

Expiration work grows across all tenants, creates many transactions/queries, and may fail to persist during read-only validation depending on transaction behavior.

**Possible fix direction**

Use account-scoped bulk update/query or a scheduled expiration job. Keep validation read-only, or perform state transition in a clearly writable command.

---

### INVITE-007 — Email delivery and database commit are not coordinated

**Status:** `REMOVED AS OBSOLETE — 2026-07-16`

**Evidence**

Invitation is saved and email is sent inside the same database transaction. The default base URL is `http://localhost:3000` when configuration is absent.

**Risk**

Email can be delivered before a later database rollback, leaving an unusable link. Missing production configuration can send localhost links.

**Required resolution**

Require a valid environment-specific public URL. Decide retry/idempotency behavior and send after commit or through a simple monolith-compatible outbox/job mechanism if delivery reliability matters.

---

## B2B collaboration findings

### COLLAB-001 — Duplicate collaborations are allowed

**Status:** `RESOLVED — 2026-08-10`

**Evidence**

- `CollaborationRepository` defines a lookup by client, provider, company, and status.
- `initiateCollaboration()` never uses it and always inserts a new PENDING collaboration.
- No reviewed database uniqueness constraint prevents duplicate pending/active relationships.

**Risk**

The same two accounts and company can have several pending or active collaboration IDs with different permission grants. Runtime access is collaboration-ID-specific, so users and UI can operate on the wrong relationship.

**Required resolution**

Permit at most one live (`PENDING`, `ACTIVE`, or `SUSPENDED`) collaboration for a client/provider/company tuple. Enforce it with a transaction-safe database strategy. After normalizing purpose whitespace and treating capabilities as an unordered set, every new record returns 201, an identical live retry returns the existing relationship with 200, changed details conflict, and a request after a terminal state creates a new historical record. A concurrent uniqueness loser must re-read the winner with 200 rather than return 500.

---

### COLLAB-002 — B2B delegation lacks an actor permission ceiling

**Status:** `RESOLVED — 2026-08-10`

**Evidence**

Grant checks that a permission is B2B-delegatable and included in the provider account's plan, but not that the acting provider member has that permission.

**Risk**

A staff member with B2B grant-management permission can delegate provider actions they cannot personally perform.

**Required fix direction**

The provider owner may delegate within current provider entitlement and code-declared B2B eligibility. A non-owner additionally needs delegation-management permission and may delegate only permissions they effectively hold. Runtime must also require that the acting external member has a client-side Account-scoped B2B operator permission for the delegated action.

---

### COLLAB-003 — Collaboration operations do not revalidate active account/company state

**Status:** `RESOLVED — 2026-08-10`

**Evidence**

Initiate, accept, and grant operations do not explicitly require the client account, provider account, or target company to be active. Existing collaborations also remain linked when a company is deactivated.

**Risk**

New or existing delegated access may be created/continued against a deactivated business scope unless another security layer consistently blocks it.

**Required fix direction**

Centralize current provider Account, external Account, target Company, active collaboration, current provider entitlement, current code B2B eligibility, exact persisted grant, and external-member operator checks. Any failed condition blocks runtime immediately while preserving relationship/grant history for audit.

---

### COLLAB-004 — `SUSPENDED` status has no management flow

**Status:** `RESOLVED — 2026-08-10`

**Evidence**

`CollaborationStatus` contains `SUSPENDED`, but the service/API supports only initiate, accept, and revoke. There is no suspend, resume, reason, expiry, or actor model.

**Risk**

Database state can contain a status the UI cannot explain or manage, and future AI changes may assign inconsistent meaning to it.

**Required resolution**

Implement the decided lifecycle: either participant may permanently end the relationship; only the provider may suspend/resume delegated access. Suspension requires a reason, preserves but disables grants, and may have an explicitly configured review/automatic-resume time. Revocation freezes grants as non-reusable history. Add `REJECTED` separately from revocation.

---

### COLLAB-005 — Concurrent lifecycle actions can overwrite each other

**Status:** `RESOLVED — 2026-08-10`

**Evidence**

Collaboration has no optimistic `@Version`, and accept/revoke use normal entity reads without a write lock. Duplicate permission checks are also check-then-insert.

**Risk**

Concurrent accept/revoke or duplicate grant requests can produce last-write-wins state, duplicate grants, or misleading success responses.

**Required fix direction**

Use optimistic or command-specific locking, database uniqueness for grants/live relationships, and explicit conflict responses. Make request/cancel/accept/reject/suspend/resume/revoke and grant/revoke-permission idempotent or return a clear already-applied/conflict result. Refresh UI state from the authoritative backend after every command.

---

### COLLAB-006 — UI cannot read the collaboration's currently granted permissions

**Status:** `RESOLVED — 2026-08-10`

**Evidence**

- Service has `getPermissions(collaborationId)`.
- Controller exposes grant, revoke, and grantable catalog, but no endpoint for current grants.
- `CollaborationDto` contains only IDs and status.
- Permission picker DTOs contain no selected/current-grant state.

**Risk**

A management UI cannot reliably show what is currently delegated before adding or revoking permissions.

**Required fix direction**

Expose an authorized collaboration detail/current-grants read model, preferably combined with safe grantable-state information for the provider management screen.

---

### COLLAB-007 — Collaboration service exposes persistence entities

**Status:** `RESOLVED — 2026-08-10`

**Evidence**

`CollaborationService` returns `Collaboration`, `CollaborationPermission`, and lists of entities; controller mapping happens afterward.

**Risk**

This repeats lazy-loading and API/persistence coupling already found in other areas.

**Possible fix direction**

Return complete list/detail/current-grant read models from API-facing application operations.

---

### COLLAB-008 — B2B discovery and lifecycle APIs cannot implement the decided management flow

**Status:** `PARTIALLY RESOLVED — 2026-08-10` (`AUDIT IMPLEMENTED; REUSABLE COMMUNICATIONS OPEN`)

**Evidence**

- Initiation accepts only a raw Company UUID. There is no provider-controlled share link/code, discoverability setting, code regeneration, privacy-safe resolution, or minimum identity confirmation surface.
- A request stores no purpose/message or requested capabilities and exposes no distinct pending-request cancellation command.
- `CollaborationStatus` lacks `REJECTED`; service/controller operations support only initiate, accept, general revoke, grant, and permission revoke. A pending request may therefore be treated like a revoked former collaboration.
- `SUSPENDED` exists in the enum but there are no suspend/resume commands, reason, actor, review/automatic-resume time, or transition contract.
- No reviewed collaboration workflow records complete lifecycle reasons/actors or delivers the decided in-app notifications. The read models expose too little context for safe provider/external management.

**Risk**

The replacement UI would still require users to exchange database UUIDs, cannot distinguish refusal from termination, cannot safely pause access, and cannot explain who requested or changed a relationship. Operators may act on the wrong Company or assume grants are active when lifecycle/entitlement rules have stopped them.

**Required fix direction**

- Add provider-controlled Company share codes/links with enable/disable/regenerate behavior. Resolve them through a privacy-minimal confirmation response; do not create broad global Company search.
- Store request identities, target Company, purpose/message, optional non-binding requested capabilities, actor, and timestamps. Add authorized external request/cancel and provider accept/reject operations protected by Account-scoped Permissionizer actions.
- Implement explicit `PENDING`, `ACTIVE`, `SUSPENDED`, `REJECTED`, and `REVOKED` transitions. Either participant may permanently end; only the provider suspends/resumes. New requests after a terminal state create new history and never resurrect old grants.
- Preserve grants as disabled configuration during suspension and frozen history after revocation. Runtime always revalidates both Accounts, Company, collaboration, entitlement, current code eligibility, exact provider grant, and external-member operator authority.
- Add detail/list models showing both Account identities, Company, status/reason/effective dates, requested capabilities, current and inactive historical grants, allowed next actions, and source-visible access blockers without exposing unrelated business data.
- Audit every command and attach reusable in-app/customer communication events. Add privacy, expired/regenerated code, duplicate tuple, transition authorization, stale version, retry/idempotency, deactivation, entitlement loss, feature retirement, actor permission, and notification tests.

**Implementation evidence — 2026-08-10**

- A nullable live-tuple key with a database uniqueness constraint permits only one PENDING, ACTIVE, or SUSPENDED relationship for each client/provider/company tuple. Terminal CANCELLED, REJECTED, and REVOKED records remain as history, and a later request creates a new record.
- Provider actors use Company-owned, SHA-256-hashed share codes. Raw codes are returned only on generation, have no automatic expiry, remain valid until the provider disables or regenerates them, and resolve only to privacy-minimal Account/Company identity fields. A code identifies a Company but grants no access; provider acceptance remains mandatory. There is no broad Company search.
- Providers can inspect resolution/request counts and last-use times for the current code; rotation resets this usage metadata. Identical live retries normalize purpose whitespace and compare requested capabilities as an unordered set. Changed details conflict, a post-terminal request creates new history, and a concurrent constraint loser safely re-reads the winning relationship.
- Requests persist purpose, optional non-binding requested permission codes, requester identity, and time. Separate accept, reject, cancel, suspend, resume, and either-participant revoke commands enforce explicit states, participant boundaries, reasons, and expected versions.
- Suspension preserves grants while blocking runtime. Providers explicitly choose no schedule, a review time, or automatic resume; a locked monolith scheduler resumes only due relationships whose client Account, provider Account, and Company are still active and whose provider remains entitled to the resume action.
- Collaboration and grant rows use optimistic versions. Database constraints remain authoritative for live tuples and permission pairs, and duplicate/stale operations return explicit conflicts.
- Permission revocation marks a grant inactive instead of deleting it. Detail/current-grant DTOs show configured versus currently usable state, lifecycle blockers, permitted state actions, timestamps, and both participant/Company identities. A terminal relationship freezes its grants as non-reusable history.
- Initiate, accept, suspend/resume, grant/revoke-permission, catalog, and share-code operations recheck applicable active Account/Company state. Runtime context already rejects inactive Accounts/Companies and requires the exact active relationship.
- Provider grant writes enforce code-declared B2B eligibility, current provider entitlement, and the acting provider member's delegation ceiling. External-member operator scoping remains explicitly assigned to AUTHZ-002 in Batch 5.3.
- API-facing collaboration services now exchange DTOs only; the MapStruct persistence-entity mapper was removed.
- Every protected collaboration command now receives the central actor/scope/action/resource/outcome audit record from `AUDIT-001`; row creation is additionally atomic with its isolated insert transaction, and automatic resume is recorded as a system batch action. Reusable in-app/customer communication events remain the only unfinished part of COLLAB-008. The clean full backend suite passes 415 tests with zero failures, errors, or skips.

---

## Platform admin findings

### ADMIN-001 — Unconditional startup seeder creates and logs a known SuperAdmin password

**Status:** `IMPLEMENTED — 2026-07-16`

**Evidence**

- `AdminSeeder` runs on every `ApplicationReadyEvent` with no development profile or configuration guard.
- It creates `admin@hiveapp.com` with the hardcoded password `admin123`.
- It logs the email and plaintext password after creation.

**Risk**

Any deployment without a pre-existing user of that email can start with publicly known SuperAdmin credentials. Logging the credentials further exposes them through application logs. This is a critical security issue.

**Required fix direction**

- Never ship a fixed production credential.
- Restrict development seeding to an explicit local/dev profile.
- For real bootstrap, require externally supplied one-time credentials or a controlled initialization process.
- Never log passwords.
- Add a production-profile startup test proving the account is not created.

**Implementation evidence — 2026-07-16**

- `AdminSeeder` is created only when `hiveapp.admin.bootstrap.enabled=true`.
- Bootstrap credentials are external configuration and validated; no password is logged.
- Development bootstrap is opt-in, and production bootstrap defaults to disabled.
- Unit and production-profile tests cover creation, disabled bootstrap, invalid credentials, and collision behavior.

---

### ADMIN-002 — Admin seeding stops when the user exists, even if the admin record does not

**Status:** `RESOLVED — 2026-07-16`

**Evidence**

`AdminSeeder` returns immediately when `admin@hiveapp.com` exists in `users`; it does not verify that a corresponding `AdminUser` exists.

**Risk**

A normal client registration using that email can prevent intended development bootstrap while still leaving no platform administrator. Conversely, automatically promoting an existing account would also be unsafe, so the desired behavior must be explicit.

**Possible fix direction**

Replace email-based implicit bootstrap with an explicit, environment-controlled initialization contract.

**Implementation evidence — 2026-07-16**

Bootstrap now checks the explicit `AdminUser` record. If the configured email belongs to a normal user, startup refuses the unsafe implicit promotion instead of silently returning or granting SuperAdmin authority. The behavior is covered by a focused unit test.

---

### ADMIN-AUTH-001 — Admin refresh tokens have no matching admin refresh flow

**Status:** `RESOLVED — 2026-07-16`

**Evidence**

- Admin login returns both an ADMIN access token and a refresh token.
- There is no `/api/admin/auth/refresh` endpoint.
- The only refresh endpoint is `/api/v1/auth/refresh`, whose `AuthServiceImpl` always issues new tokens with `tokenType=CLIENT`.

**Risk**

The admin refresh token cannot renew an ADMIN session. Passing it to the available refresh endpoint may turn it into a CLIENT token, depending on token validation. The UI will fail after access-token expiration or use an unsafe workaround.

**Required fix direction**

Give refresh tokens an explicit audience/token type and implement separate, validated ADMIN and CLIENT refresh behavior—or one refresh service that preserves and validates the original audience.

**Implementation evidence — 2026-07-16**

`POST /api/admin/auth/refresh` rotates only an active ADMIN refresh session and issues another ADMIN pair. Client tokens, access tokens, and reused admin refresh tokens are rejected. `POST /api/admin/auth/logout` revokes the supplied active admin refresh session.

---

### ADMIN-AUTH-002 — Admin login duplicates authentication logic and skips DTO validation

**Status:** `RESOLVED — 2026-07-16`

**Evidence**

- `AdminAuthController.login()` accepts `LoginRequest` without `@Valid`.
- It performs repository lookup and password matching directly instead of using the authentication service/manager used by client login.
- It repeats token issuance and email logging inside the controller.

**Risk**

Admin and client authentication can drift in normalization, validation, throttling, failure handling, token claims, password policy, and future account-lock behavior.

**Possible fix direction**

Centralize credential verification and token issuance while preserving explicit ADMIN versus CLIENT audience checks. Keep controllers thin and apply `@Valid` consistently.

**Implementation evidence — 2026-07-16**

`AdminAuthController` now validates request DTOs and delegates login, refresh, and logout to `AdminAuthenticationService`. Client and admin login share canonical credential verification, while the service explicitly verifies active `AdminUser` state and requests ADMIN-scoped tokens.

---

### ADMIN-RBAC-001 — Non-SuperAdmin protection is incomplete for destructive admin actions

**Status:** `RESOLVED — 2026-07-16`

**Evidence**

- Only a SuperAdmin may create another SuperAdmin.
- Self-deactivation is blocked.
- A non-SuperAdmin with the relevant permission can still deactivate another administrator, including a SuperAdmin; `toggleActive()` has no target-SuperAdmin protection.
- Granting and role assignment enforce the actor's permission ceiling, but permission revocation and role removal do not apply a corresponding target/ceiling rule.

**Risk**

A delegated administrator may be able to disable the platform's recovery administrator or sabotage roles beyond their authority, even if they cannot grant themselves those permissions.

**Decision**

Destructive admin operations obey two explicit boundaries: only a SuperAdmin may modify a SuperAdmin, and a delegated administrator may manage only roles and permissions inside their own permission ceiling. SuperAdmins retain authority over other administrators. Self-deactivation remains blocked, so an active SuperAdmin cannot remove the currently authenticated recovery administrator.

**Implementation evidence — 2026-07-16**

- `AdminMutationAuthorizer` centralizes target protection and permission-ceiling checks instead of duplicating partial rules across services.
- Admin activation changes and role assignment/removal now protect SuperAdmin targets.
- Role metadata updates, activation changes, permission grants/revocations, and admin role assignment/removal all enforce the acting administrator's permission ceiling.
- Focused tests cover delegated-admin denial, SuperAdmin authority, inactive actors, protected SuperAdmin targets, role activation above the actor ceiling, denied revocation above the ceiling, and allowed revocation inside it.
- The complete backend suite passes: 222 tests, 0 failures, 0 errors, 0 skipped.

---

### ADMIN-DATA-001 — Admin list endpoints perform query-per-row mapping

**Status:** `IMPLEMENTED — 2026-08-11`

**Evidence**

- `AdminRoleController.getAll()` loads all roles, then calls `findAllByAdminRoleId()` once per role.
- `AdminUserController.getAll()` loads all admin users, then calls `findAllByAdminUserId()` once per user.
- Mapping then dereferences lazy permission, role, and user relationships.

**Risk**

List cost grows linearly with extra queries and may depend on Open Session in View to avoid lazy-loading failures. This will become visible in the admin UI as records grow.

**Possible fix direction**

Build dedicated read queries/projections with the required relationships, assemble responses transactionally, and add query-count tests for list endpoints.

**Implementation evidence — 2026-08-11**

Admin user and role lists now fetch one bounded entity page and one bulk relationship set for that page. Mapping occurs transactionally in the service; controllers no longer issue one assignment query per row. Unit tests pin the bulk call and reject the former per-row repository path.

---

### ADMIN-DATA-002 — Admin users and roles are returned without pagination

**Status:** `IMPLEMENTED — 2026-08-11`

**Evidence**

Both list services call `findAll()` and controllers return complete lists.

**Risk**

Large installations will load and map every administrator/role and all related assignments in one request.

**Possible fix direction**

Introduce pagination/search before these collections can grow significantly, while keeping small bootstrap deployments simple.

**Implementation evidence — 2026-08-11**

`GET /api/admin/users` and `GET /api/admin/roles` accept zero-based `page` and bounded `size` parameters (`1..100`, default `20`). Both return the shared stable `PageResponse` contract rather than exposing Spring Data's internal serialization shape. HTTP integration coverage pins the page metadata and requested bound.

---

## Shared infrastructure and API findings

### BILLING-002 — The only payment gateway bean always reports fake success

**Status:** `RESOLVED — DEV/TEST SIMULATOR ISOLATED 2026-08-10`

**Evidence**

`DevPaymentGateway` is an unconditional `@Service`; every charge/refund returns `SUCCESS` with a generated DEV transaction ID. It is currently unused, and no environment condition prevents it from loading outside development.

**Risk**

Future AI-generated code may wire the existing `PaymentGateway` and appear to complete billing while charging nothing. A production deployment could silently activate paid entitlements against simulated success.

**Required fix direction**

Restrict the fake gateway to an explicit local/test profile. Production startup must fail when real collection is enabled without a configured provider. Never let calculated price or the dev gateway create a confirmed payment. Real/manual settlement must support idempotency, asynchronous confirmation, pending/success/failure/partial-refund/refund states, reconciliation, and durable provider/manual references before paid entitlement activation.

**Implementation evidence — 2026-08-10**

- `DevPaymentGateway` loads only in `dev` and `test`; its pending/success/failure outcome is explicit and configurable, with pending as the default.
- Simulator results are stored only as attempt telemetry. Even simulated `SUCCESS` cannot confirm a checkout or activate paid entitlement.
- Every payment request carries a stable idempotency key. Production startup fails when collection is enabled without any gateway explicitly marked trusted for settlement.
- Manual settlement has durable, idempotent confirmation evidence. A real provider adapter, asynchronous webhook/reconciliation, refunds/credits, invoices, and recurring recovery remain later integrations.

---

### BILLING-003 — HiveApp has a financial ledger and operating UI but no real provider or fiscal implementation

**Status:** `PARTIAL — LEDGER, OPERATIONAL APIS, RECOVERY, TIMELINE AND COMMERCIAL DOCUMENT IMPLEMENTED; REAL PROVIDER/FISCAL RULES OPEN`

**Evidence**

- Exact ISO-currency Money, independently entered monthly/yearly Price-book entries, overlap protection, and immutable accepted price identity are implemented and audited under `PRICEBOOK-001`.
- Subscription preview itemizes configured recurring terms by one currency/cycle and does not describe them as settlement or revenue.
- Positive reviewed subscription changes now persist immutable numbered Invoice/InvoiceLine evidence, provider/manual Payment attempts, Credits, Refund intents, and durable idempotent outbox commands.
- Price preview, amount due, provider attempt, trusted/manual settlement, entitlement activation, Credit, and Refund are distinct facts. Pre-dispatch cancellation and manual settlement cannot leave an automatic charge runnable.
- Fine-grained admin Invoice/search/detail/payment/manual-settlement/Credit/Refund/reconciliation reads and Account-isolated client Invoice history/detail are mounted. Account identity, Payment evidence, and sensitive references remain separately authorized.
- Verified provider event ingestion/deduplication, mismatch retention/reprocessing, and evidence-gated failed-charge retry are implemented.
- The admin Billing workbench exposes bounded Invoice filters/detail, independently authorized Account and Payment evidence, manual settlement, Credit, provider/manual Refund review, failed-charge retry, outbox reconciliation, and provider-event reprocessing. The client subscription hub exposes bounded own-Account Invoice history and safe detail.
- Paid renewal, failure-to-grace recovery, operator lifecycle control, Account financial timeline, editable Account billing profile, immutable Invoice party snapshots, and printable commercial-document output are implemented.
- A concrete signed provider adapter and jurisdiction-specific tax, numbering, and fiscal-document rules are still absent.

**Risk**

The current commercial document is intentionally not a tax Invoice: it identifies missing issuer/customer/tax/numbering requirements rather than manufacturing compliance. Until a real signed provider adapter exists, production collection cannot be enabled. The durable records, transport boundary, verified-event recovery, immutable document snapshots, and separated financial timeline prevent calculated prices or unverified callback claims from masquerading as collected value.

**Required fix direction**

- Preserve the implemented ISO Money and immutable exact monthly/yearly Price-book contracts; keep one currency/cycle per subscription and never perform implicit FX.
- Preserve the implemented permission-separated operational APIs and Account-isolated client projections for immutable accepted Invoice lines, Payment evidence, Credits, Refunds, and transport state.
- Keep preview, amount due/Invoice, pending attempt, trusted/manual settlement, entitlement activation, Credit, Refund, and collected-value analytics distinct. Only trusted/manual succeeded settlement counts as collected money.
- Preserve the implemented verified-event ingress contract: provider adapters authenticate before ingestion; duplicate, unknown, mismatched, and in-flight evidence remains idempotent and privacy-separated; ambiguous charge retry requires provider non-capture evidence.
- At renewal, apply the selected new price version for the new period. For immediate mid-period changes, initially support no automatic proration plus explicit audited operator adjustment/credit; defer automatic tax, discounts, metered charging, proration, FX, and automated refunds.
- Preserve the implemented payment-failure transition to `PAST_DUE`, configured grace, and eventual restricted/suspended access without data deletion. Reconciliation/webhook handling remains idempotent and authorization-safe.
- Store exact purchased terms in subscription history independently from financial records. Add currency mismatch, cycle mismatch, annual exact-price, zero-price, immutable version, itemization, pending-versus-paid, duplicate event, failed renewal/grace, manual settlement, adjustment, refund-state, and mixed-total reporting tests.

**Implementation evidence — 2026-08-10**

- Added an immutable ISO-currency `Money` value type with exact minor-unit validation, normalized currency codes, same-currency arithmetic, and explicit rejection of implicit FX.
- Plan base prices, PlanFeature add-on prices, quota-unit prices, Subscription current prices, entitlement snapshots, previews, catalogs, admin/client DTOs, and `PaymentRequest` now carry explicit currency.
- Entity lifecycle validation and billing configuration validation reject missing/invalid currencies and mixed Plan/add-on/quota/subscription amounts. Plan currency cannot change after monetary composition or subscription history exists; unpriced composition may safely be reused across currencies.
- `BillingCalculator` now returns `Money` for persistence and rejects mixed-currency calculations. Seeded prices carry explicit ISO currency (initially USD; MAD from 2026-09-21), and focused plus integration tests cover arithmetic, precision, persistence/API exposure, mixed-currency rejection, and safe plan-currency changes.
- Phase 9 completed independently entered immutable monthly/yearly Price-book entries, exact-decimal APIs, overlap-safe activation, current-selection pause, and exact accepted-price snapshot identity for Plans, AddOns, and capacity packages.
- On 2026-08-31, Phase 13 added immutable itemized Invoices, provider/manual Payment evidence, Credits, concurrency-capped Refund intents, replay-safe provider commands outside database transactions, and atomic pre-dispatch cancellation. The full backend suite passed with 774 tests.
- The next Phase 13 slice mounted bounded/filterable admin Billing APIs and own-Account client Invoice history/detail; added permission-before-existence, nested privacy, cross-surface, cross-Account, and provider-versus-manual Refund tests; and fixed nested Spring access denials to return the stable `PERMISSION_DENIED` 403 contract rather than 500.
- Verified provider events are now stored before processing with `(provider,eventId)` deduplication and digest-conflict detection, matched through financial idempotency plus operation/money/reference/state checks, retained for operator attention on mismatch, and explicitly reprocessable. Failed-charge recovery creates a new Payment/outbox attempt and requires operator/provider evidence for ambiguous transport failures.
- The frontend now replaces the Billing placeholder with permission-separated admin Invoice, payment/adjustment, reconciliation-command, and provider-event workflows plus client Invoice history/detail. Route/query tests prove Invoice-list, reconciliation-only, Payment-only, and Account-identity boundaries independently, and exact monetary values remain strings through the UI.
- Account financial timeline APIs now project Invoice, Payment, Credit, and Refund facts directly from their authoritative tables with bounded filters and separate admin/client permissions. Account billing profiles are editable, while issued Invoices retain immutable Account and issuer identity snapshots. Admin and client UIs mount the timeline/profile independently and render a printable jurisdiction-neutral commercial document with explicit fiscal-completeness warnings.
- No Flyway history was added because the application is unpublished and currently uses a disposable generated H2 schema, per the agreed pre-production database policy.

**Bootstrap currency follow-up — 2026-09-21**

- The six demo Plans, three Add-ons, six capacity packs, and their authoritative compatibility
  tariffs now seed in MAD. Numeric demo amounts and monthly cycles are unchanged; this is not FX
  conversion or an update to existing purchases. Multicurrency support remains available.
- Seeders continue to preserve existing records. The change applies when bootstrapping a fresh
  database; no migration or running development-server restart is part of this change.
- Regression coverage checks all seeded product/tariff currencies and amounts. Seed-dependent
  integration fixtures now use MAD; independent USD and mixed-currency tests remain intact.
- Verification ran in an isolated copy to avoid a concurrent development build replacing test
  classes. The 875-test run passed except for one catalogue assertion that incorrectly required
  independent USD fixtures to use MAD too. After scoping that assertion to each product's actual
  currency, all 15 client self-service tests passed on rerun; the other 860 tests had passed.

**Remaining scope**

A concrete signed provider adapter and jurisdiction-specific tax calculation, fiscal numbering, validation, and final fiscal-document output remain in Phase 13. Billing workbenches/history, provider-event deduplication/recovery, paid and zero-amount renewal evidence, grace/past-due recovery, Account financial timeline/profile, commercial-document output, fine-grained admin and Account-isolated client APIs, immutable Price books, and the core Invoice/Payment/Credit/Refund/outbox persistence boundary are implemented and are no longer part of this finding's remaining scope.

---

### REPRICING-001 — No focused price-only workflow for existing subscribers

**Status:** `IMPLEMENTED — 2026-09-08`

- Catalogue tariff replacement already preserves current contracts, correctly. Existing selected
  jobs apply one full product selection, which is not safe for changing just one component's price
  across customers with different Add-ons and quantities.
- Delivered a separate price-only workflow: exact old-tariff/filtered/frozen-Segment audiences and
  exclusions, per-Account preserved snapshots, first-eligible-renewal timing, signed review,
  protected special terms, ordinary settlement, paginated outcomes and private client notices.
- Admin cancellation/technical retry and independent durable email delivery are implemented.
  Billing failure/recovery stays with the original invoice, never a duplicate repricing charge.
- Evidence and bounds: `docs/SUBSCRIPTION_REPRICING_V1.md` completion record and
  `IMPLEMENTATION_PLAN.md` Batch 12.3. Catalogue publication still never opts current subscribers in.

---

### PRICEBOOK-001 — Commercial products support only one price and billing cycle

**Status:** `IMPLEMENTED — 2026-08-26`

**Evidence**

- `Plan`, `AddOn`, and `QuotaPackage` each persist one amount, one ISO currency, and one `BillingCycle` directly on the product revision.
- Create/update DTOs require that single tuple, and client/admin catalogue DTOs expose only it.
- A product therefore cannot honestly offer independent monthly and yearly prices at the same time. Creating a second product row would split product identity, composition, history, and analytics.

**Risk**

Operators cannot model ordinary monthly/yearly choices, scheduled price changes, or a paused price without cloning the whole product. Mutating the tuple risks conflating product definition with price history, while cloned products make adoption and reporting misleading.

**Required fix direction**

- Add immutable versioned Price-book entries owned by an exact Plan/AddOn/quota-package revision, with amount, ISO currency, monthly/yearly cycle, effective window, lifecycle, optimistic version, and audit.
- Permit independently entered monthly and yearly values; never derive annual price automatically.
- Enforce non-overlapping active applicability for one owner/currency/cycle and select exact compatible entries during preview/checkout.
- Snapshot selected price-entry identity and itemized amount. Pausing/new versions affect future selection only; existing snapshots remain unchanged.
- Replace product CRUD/UI single-price assumptions with price management, availability, history, and activation preview. Keep zero-price recurring entries valid and `FOREVER` deferred.

**Backend implementation evidence — 2026-08-26**

- `ProductPrice` now owns immutable Money/cycle/effective-window terms for one exact Plan, AddOn, or quota-package revision, with lineage, source revision, optimistic version, and `DRAFT`/`ACTIVE`/`INACTIVE`/terminal `ARCHIVED` lifecycle.
- Activation locks the product owner and rejects overlapping half-open applicability windows; list/detail APIs expose backend-derived blockers and valid next actions without scanning the complete active Price book.
- Fine-grained admin APIs cover paginated search/filter/sort, create/edit, activation preview, activate/pause/reactivate/revise/archive/delete, and bounded actor-aware history with required lifecycle reasons and stable conflict codes.
- Client catalogue and subscription-change contracts expose/select exact applicable entries. Snapshot schema V2 stores the Plan/AddOn/package price identities and immutable amounts while schema V1 remains readable.
- Existing subscription overrides, scheduled activation, and renewal preserve snapshot terms even after a selected price expires or is paused; future selections use the authoritative resolver.
- Disposable-H2 compatibility backfill preserves the current legacy product columns while seeding one authoritative entry per published tuple. Durable production migration and database-native exclusion constraints remain deferred with the standing persistence decision.
- An independent adversarial backend audit added exact admin price selection, same-Plan cycle changes, an atomic scheduled-replacement flow, a least-privilege assignable-price catalogue, immutable published product terms, and exact current-price identity. The final integrated JDK 21 baseline passes 529 tests.
- Commercial API `BigDecimal` components carry an `@ExactDecimal` contract and serialize as non-exponential plain-decimal strings. Contract tests cover maximum 15+4 precision, string requests, numeric-request migration compatibility, and prevent unannotated commercial decimal components.
- The admin Price-book list, guided create, detail, terms/lifecycle, replacement and history flows are implemented and linked from Plan/Add-on/capacity-package details. Admin subscription operations and client self-service select exact applicable entries rather than inferring a cycle or amount.
- An independent frontend audit removed all commercial-money `number` coercion, pinned exact comparison/formatting beyond JavaScript's safe integer range, corrected functional table sorting, separated history-only access, permission-gated product links, and made stale client changes recoverable. Biome, TypeScript, the production build and 136 frontend tests pass.

---

### PRICEBOOK-002 — Plan overview still presents a legacy single price as the complete offer

**Status:** `FIXED — 2026-09-09`

**Evidence**

- The Plan detail's **Synthèse → Configuration commerciale → Prix** in
  `frontend/src/features/admin/plans/admin-plans-page.tsx` renders `data.price`,
  `data.currencyCode`, and `data.billingCycle`, not the authoritative Price-book options.
- The system supports independent monthly/yearly tariffs and client selection, but this summary
  cannot reveal additional published tariffs. During the demo, Enterprise displayed only
  `99,99 $US / mois`, making the administrator question whether dual cycles were supported.
- At the demo, Enterprise's bootstrap data contained one USD monthly tariff (bootstrap currency
  changed to MAD on 2026-09-21); that seed choice is separate from the display bug. The **Tarifs**
  tab is the place to inspect the actual configured entries.

**Required fix direction**

- Summarize the Plan's currently applicable published tariffs by explicit currency and billing
  cycle, with a clear link to **Tarifs**. Do not present the legacy tuple as the complete offer.
- Keep one-tariff, monthly-plus-yearly, multiple-currency, no-applicable-tariff, loading, error, and
  permission-restricted states honest. Do not infer yearly prices, convert currencies, or leak
  tariffs when the actor lacks the required read permission.
- Audit equivalent Add-on/pack summaries for the same assumption before claiming they are fixed.
  Add regression coverage for a Plan with both monthly and yearly entries.
- Implemented a shared current-tariff summary on Plan, Add-on and capacity-pack details. The
  server filters applicability at its own clock; a bounded bulk owner filter also supports cards
  without per-card requests. Currency/cycle options remain independent and exact.
- Loading, error/retry, missing permission, empty and truncated results are explicit. No legacy
  amount fallback, currency conversion or inferred annual price is used. Two backend integration
  tests and four mounted UI regressions pass; full frontend verification passes (386 tests).

---

### PRICEBOOK-003 — Tariff warnings and labels lack administrator-facing action context

**Status:** `FIXED — 2026-09-09`

**Evidence**

- `ProductPriceAdminServiceImpl.generalBlockers` returns `ACTIVE_MUST_BE_PAUSED` for an active
  tariff. The detail page renders it under **État opérationnel**, and the lifecycle tab under
  **Points à traiter**, as if a healthy tariff needs corrective action.
- The actual rule is specific: an active tariff must be paused **before archiving**. Creating a
  revision is allowed while active. The current text, **Suspendez d’abord la vente de ce tarif**,
  omits the operation it refers to, leaving the administrator unsure why anything must be paused.
- The demo also exposed confusion around **révision du produit** versus **révision du tarif** and
  generic section/action labels. The user requests a broader administrator-facing vocabulary review;
  equivalent screens must be inspected rather than assumed to have the same defect.

**Required fix direction**

- Associate blockers with the action they prevent. Show the pause requirement at the archive
  action, with wording such as **Pour archiver ce tarif, suspendez d’abord sa vente**. Do not show
  ordinary active status as a page-level warning or block unrelated valid actions.
- Reserve page-level warnings for genuine availability/configuration problems. Keep backend
  enforcement and permission-specific disabled reasons intact.
- Review commercial headings, navigation and action labels for explicit product/price scope and
  intent; distinguish opening a product version, creating a price version, adding another billing
  option, and managing existing tariffs without relying on explanatory paragraphs everywhere.
- Add regression coverage proving an active, otherwise valid tariff has no misleading global
  pause warning and retains its legitimate actions. Deferred while the demo continues; no UI or
  lifecycle behavior is changed by this note.

**Implementation evidence — 2026-09-09**

- Shared presentation now separates availability warnings from per-action lifecycle prerequisites;
  the archive-only pause reason cannot appear as a global warning or disable unrelated actions.
- Tariff detail labels distinguish **Voir le produit**, **Version du tarif**, **Conditions et
  actions**, and **Préparer un nouveau tarif**. Replacement confirmation explains that preparing
  a draft does not change sales or existing subscriptions.
- Typecheck and 23 focused tariff tests pass, including healthy active/archived presentation,
  action-specific blocker selection, permissions, and signed activation evidence. The separate
  continuous-replacement lifecycle change is delivered in `PRICEBOOK-004`: **Changer le tarif**
  now supersedes the intermediate draft/pause-first actions described above. Contextual reasons
  and the distinction between product and tariff remain; standalone tariff pause is retired.

---

### PRICEBOOK-004 — Replace manual tariff lifecycle coordination with continuous price changes

**Status:** `FIXED — 2026-09-10`

**Evidence and decision**

- Overlap rejection is implemented, but it does not prevent gaps caused by separately pausing
  the current tariff and activating another, or by ending a tariff without a replacement.
- The existing atomic scheduled-replacement flow joins a direct successor to its current source,
  but requires a future start. It does not deliver the agreed immediate replacement or a complete
  change/cancel flow that restores uninterrupted coverage.
- During the demo, the user chose a simpler current-price/replacement model over managing
  independent validity windows. The authoritative amendment is in `FLOW_DECISIONS.md`,
  `COMMERCIAL-FLOW-003`. It supersedes the earlier normal lifecycle flow described in older specs.

**Required implementation**

- One current tariff and at most one confirmed future replacement per offered exact product
  revision/currency/cycle. Keep the current tariff open-ended until a replacement is confirmed.
- **Changer le tarif → Maintenant / À une date** performs an atomic, reviewed boundary handoff.
  Server-authoritative time, exact amounts, owner locks, current authorization, signed evidence,
  stale conflicts, idempotency, and before/after audit must cover every write path.
- Changing/cancelling a future replacement atomically preserves current coverage. Refuse stale
  cancellation once the handoff has occurred; never rewrite elapsed history or accepted snapshots.
  Draft creation/edit/deletion alone never changes sellable coverage.
- Show current tariff, scheduled change, and history. Remove standalone pause/end-date management
  from the normal tariff flow; intentional sales stops belong to explicit product availability.
  Guard or retire equivalent legacy API paths so they cannot bypass the invariant.
- Keep monthly/yearly and currencies independent, and preserve explicit existing-subscriber
  repricing. Do not require unoffered options to have a price.
- Review published Offer dependencies: Offers pin exact prices and the current resolver can make
  them unavailable when those prices cease to be selectable. Define visible impact and safe
  handling before implementation; no automatic retargeting/repricing of Offers is approved.
- Test immediate/future handoff, adjacency at the exact instant, failed atomic writes, concurrent
  replacement attempts, schedule edits/cancellation races, permissions, legacy bypasses, draft
  isolation, Offer dependencies, and unchanged subscriber terms; verify the real admin flow.

**Implementation evidence — 2026-09-09**

- Added signed current-price preview and separate change/reschedule/cancel APIs, actor-specific
  permissions, locked recomputation, exact boundary writes, immutable replay receipts and audit.
- A scheduled change uses a fresh immutable successor. Rescheduling archives the unstarted old
  successor; cancelling restores the current price's open-ended coverage. Neither edits accepted
  subscription snapshots nor automatically changes a published Offer's pinned price.
- Published Offer dependencies are explicit blockers: current price needed after the cutoff, or
  future price needed by an Offer being changed/cancelled. The preview exposes a count, not
  otherwise unauthorized Offer details. Operators must resolve these through the Offer workflow.
- Retired the legacy standalone pause and unsigned scheduling paths. Independent finite-ended
  draft publication is rejected, including capacity-pack composite publication. Existing finite
  history/copy drafts are retained and require explicit review, not an automatic migration.
- UI exposes **Changer le tarif**, **Maintenant / À une date**, **Modifier le changement** and
  **Annuler le changement**, with current/scheduled/history labels, live evidence expiry,
  disabled confirmation until reviewed, stale-review recovery and same-key network retries.
  Current prices remain visible even when many recent draft/history entries exist. All actions
  remain gated by backend availability and their exact permissions.
- Related `PRICEBOOK-002`, `PRICEBOOK-003`, `PRICEBOOK-005`, `MARKETING-002` and `UI-003` are fixed
  in separate commits. No new specification document was created.

**Final verification — 2026-09-10**

- Full backend suite: **874 tests, zero failures/errors**. Includes 15 dedicated continuous-change
  integration tests for exact boundaries/money, unchanged subscriber terms, immutable history,
  Offer blockers, concurrent operations, audit-failure rollback and receipt retries. Separate
  security tests pin the new permissions and retired legacy bypasses.
- Full frontend suite: **398 tests, zero failures**, plus typecheck, Biome and production build.
  Mounted regressions cover fresh review, edited-field invalidation, exact local-date/decimal input,
  cancellation copy, denied permissions, failed successor loading and same-key network recovery.
- Isolated authenticated browser flow: 99.99 → 100.25 immediately; schedule 120; revise that
  schedule to 125.50 with a new date; cancel and verify 100.25 continues with no end date. Both
  rescheduling boundaries move together. Only disposable QA data was changed.
- Review repeats current/new amounts and the actual cancellation effect; on small screens only
  the dialog body scrolls, leaving the title/actions visible. Immediate success opens the new
  current tariff. Verified dark/desktop and narrow layouts; no horizontal page overflow.
- Rechecked restored Plan cards against the changed current tariff and confirmed Segment list
  and creation have no origin control. User demo servers/data were not restarted or modified.

---

### PRICEBOOK-005 — Bootstrap tariffs display 1970 as a commercial start date

**Status:** `FIXED — 2026-09-09`

- The compatibility backfill used `Instant.EPOCH`, which the list and detail rendered as
  1 January 1970. This was a technical sentinel, not an administrator-selected sales date.
- New bootstrap tariffs use the injected server clock when created. Existing rows are never
  rewritten or reactivated by the backfill. Legacy compatibility entries with the epoch display
  **Date historique non renseignée**, not a fabricated date; real dated entries retain their date.
- Verified by the 13-test Price-book integration class (including bootstrap timestamps and
  idempotency) and 24 frontend tariff tests, including epoch-versus-real-date presentation.

---

### COMMERCIAL-002 — Product administration lists are unbounded and operationally inconsistent

**Status:** `IMPLEMENTED AND INDEPENDENTLY AUDITED — 2026-08-27`

**Evidence**

- The Plan, Add-on, and quota-package list endpoints return unbounded `List` payloads, unlike the stable `PageResponse` contract already used by Price books, subscribers, roles, and operators.
- These product lists lack a common search/filter/sort contract and are reused as selectors, causing UI code to fetch complete catalogues merely to choose one product.
- Product lifecycle and history capabilities are inconsistent: Plans and Add-ons have revision flows, while quota packages do not.

**Risk**

Catalogue growth makes list screens and selectors increasingly slow, forces duplicated frontend filtering, and makes page boundaries unstable. Inconsistent operational contracts also encourage broad read permissions where a narrow product chooser is sufficient.

**Required fix direction**

- Replace admin Plan/Add-on/quota-package lists with bounded `PageResponse` APIs supporting validated search, lifecycle/visibility filters, safe sorting, deterministic tie-breakers, and constant-query read models.
- Add narrow chooser endpoints/read permissions for workflows that need selectable products without granting access to full commercial administration.
- Return backend-derived `availableActions`, blockers, subscriber/attachment counts, and revision identity needed by operational tables; do not reconstruct lifecycle rules in the UI.
- Migrate the admin pages to the shared URL-backed table/filter/action patterns and retain explicit mobile alternatives, access-denied, loading, empty, and failure states.

**Implementation evidence — 2026-08-27**

- Plan, AddOn, and capacity-package administration now use bounded `PageResponse` search/filter/sort contracts with validated allowlists, deterministic tie-breakers, backend-derived actions/blockers/counts, and constant-query tests.
- Separately authorized narrow chooser/selected-item resolution endpoints support editors without granting the broader operational catalogue. Permission-hidden retained references remain identifiable without leaking product data or becoming newly selectable.
- The admin web surfaces use shared URL-backed responsive tables, source-owned actions/reasons, scoped query keys, explicit loading/empty/error/access-denied states, and mobile alternatives; no operational screen fetches the full catalogue merely to render a selector.
- Subscription administration now has a bounded Account workbench with minimum Account/latest-subscription facts, narrow chooser resolution, server filters/sorts, and deterministic latest-history selection. Ordinary rows neither expose nor search/sort by owner email; exact owner-email lookup uses a distinct Permissionizer action and response contract.
- Live browser verification exposed and closed a composed-trigger defect that made a signed Price-book activation control look usable while discarding the dialog event. The permission-aware button now forwards trigger props and a rendered regression test pins the real interaction.

---

### QUOTA-005 — Published capacity packages have no successor-revision workflow

**Status:** `IMPLEMENTED AND INDEPENDENTLY AUDITED — 2026-08-27`

**Evidence**

- Published quota packages are now correctly immutable outside `DRAFT`, but `QuotaPackage` has no lineage/source/revision fields and the admin API has no revise operation.
- An operator can pause or archive a published package, but cannot create a traceable successor that copies its definition and attachments for a safe change.

**Risk**

Fixing published immutability without a revision path leaves normal commercial maintenance stranded or encourages unrelated duplicate products that lose lineage and comparison history.

**Required fix direction**

- Add immutable quota-package lineage, source revision, revision number, and creation reason using the established Plan/Add-on revision model.
- Provide a draft-successor command that copies capacity definition, compatibility targeting, sales visibility, and current price-book starting point without changing existing subscription snapshots.
- Add comparison, activation blockers, history, safe archive/delete rules, optimistic concurrency, and admin UI actions consistent with Plan/Add-on revisions.

**Implementation evidence — 2026-08-27**

- Capacity packages now retain lineage, source revision, revision number, creation reason, and optimistic version; only one open successor may be created concurrently.
- Revision copies the capacity definition, targeting/visibility, attachments, and editable starting Price-book terms while existing subscription snapshots keep their exact prior package/price identities.
- Operational APIs and UI cover revise, compare, lifecycle blockers, activation review, history, safe delete/archive, and paginated revision/history traversal rather than silently truncating the first page.

---

### COMMERCIAL-001 — Extension targeting and Account commercial policy are encoded as scattered special cases

**Status:** `IMPLEMENTED AND INDEPENDENTLY AUDITED THROUGH PHASE 10 — 2026-08-27`

**Evidence**

- AddOns and quota packages carry Plan-code allow/block sets, while Plans have no explicit CLOSED/ALLOW_LIST/OPEN_COMPATIBLE extension policy or public/direct-only sales visibility.
- Subscription overrides represent selected AddOns/packages but cannot model a reasoned time window, targeting source, priority, approval, renewal instruction, price adjustment, free period, or quota bonus.
- Compatibility logic exists in checkout/snapshot services, but there is no reusable target preview or policy lifecycle for one Account, selected Accounts, a Segment, or Plan subscribers.

**Risk**

Operators must request new code for each commercial exception or encode business strategy as an untraceable override. Marketing can be accidentally coupled to entitlement internals, conflicts have no deterministic precedence, and expiry/history cannot be explained to support or customers.

**Required fix direction**

- **Implemented:** explicit Plan extension policy and product sales visibility with one backend-computed mandatory-compatibility resolver, client/operator audience privacy, locked final revalidation, immutable snapshot identity, reasoned previewed mutations, and typed history. The independent audit removed the legacy unguarded Plan catalogue and a cross-feature Permissionizer-policy bypass. The full backend baseline is 549 tests.
- **Implemented:** database-bounded operational product catalogues, narrow choosers, shared admin/client extension UI, exact Price-book selection, signed reviewed writes, and capacity-package revision operations. The compatibility resolver still intentionally loads the complete bounded product/active-price set before response paging and fails closed above its safety ceiling; replacing that internal catalogue-wide evaluation remains a scaling refinement, not a hidden paginated query.
- **Implemented and independently audited 2026-08-27:** the subscription Account workbench provides change catalogue selection, signed preview, mandatory operator reason, explicit apply, stale-evidence recovery, reasoned cancellation, manual checkout confirmation, and bounded deterministic admin/client history. Durable request/cancellation origin, actor, reason, and time are admin-only; the client projection remains privacy-safe. The frontend audit removed a broad `subscriptions.read` route dependency that blocked independently authorized operations, prevents exact no-op previews, distinguishes awaiting payment from applied entitlement, confirms client cancellation, and shares dependency/exclusion rules across both configurators.
- **Implemented and independently audited 2026-08-27:** immutable typed policy revisions/lifecycle, one-Account/explicit-set/Plan-revision targets, typed price/discount/quota/product effects, deterministic direct-over-broad and restriction-over-grant precedence, fine-grained bounded admin APIs, separate owner identity, signed activation review, immutable audiences, optimistic/concurrent lifecycle, history, and audit. Activation authorizes the reusable definition only and never mutates subscribers or settlement.
- **Implemented and independently audited 2026-08-27:** active policy evaluation is part of the exact one-Account subscription preview/apply path. Accepted terms persist exact winning policy/effect provenance and expose privacy-separated admin/client explanations. Fixed recurring price, one non-stacking bounded discount, quota bonuses, blocks, and dependency-safe bounded AddOn/package grants are covered by backend and mounted frontend regressions.
- Retained historical prices/products remain honest after later catalogue changes; signed review evidence is recomputed under locks; unknown internal errors and operator/provider provenance are not leaked to clients.
- Safe Segment targeting and the complete operational Campaign backend/admin UI are independently audited. Offer and redemption control is implemented with automated/security verification; authenticated browser QA remains the `MARKETING-001` closure gate. Selected/filtered/scheduled subscriber-job execution, free periods, renewal instructions, retry/progress/cancellation cutoff and lifecycle commands remain `PLAN-011` Phase 12 rather than hidden policy-activation side effects.

### SPECIAL-AGREEMENT-001 — Negotiated fixed terms are fragmented across unrelated tools

**Status:** `IMPLEMENTED AND VERIFIED — 2026-09-03`

**Problem**

- Manual settlement can settle an existing Invoice, Offers can discount or grant selected products,
  and Policies can provide Account-specific price/capacity effects, but no single reviewed operation
  expresses a negotiated fixed entitlement term.
- Offer and Campaign end dates govern acceptance, not Account entitlement duration.
- Subscription periods are recurring monthly/yearly periods; one month, multiple calendar months,
  or exact custom terms with a declared completion instruction are not an operator workflow.
- A gift, manually received payment, and custom contractual amount must remain distinct financial
  facts instead of being approximated by a mutable `paid` flag.

**Required resolution**

- Implement `docs/SPECIAL_COMMERCIAL_AGREEMENTS_V1.md`: exact one-Account content/term/price,
  private finite quota bonuses, provider/manual/zero settlement, scheduled start, explicit end
  instruction, signed review, Account locking, durable execution, history, analytics, privacy, and
  the guided administrator workbench flow.
- Preserve immutable Invoice/Payment evidence. There is no API that flips an Account or Invoice to
  paid without a checkout/invoice/reference/reason, and complimentary access creates no fake
  Payment or collected value.

**Implementation evidence — 2026-09-03**

- The backend now owns one immutable Account agreement aggregate with exact selection snapshots,
  finite private quota bonuses, exact calendar/custom terms, catalogue/custom/complimentary totals,
  provider/manual/no-payment settlement, and one explicit completion instruction.
- Preview and confirmation are separated by actor/Account/subscription/catalogue/registry-bound
  signed evidence. A unique live-Account constraint, Account locking, stale-preview handling, and
  existing dependency/exclusion/usage vetoes close concurrent and stale application paths.
- Positive agreements create ordinary Invoice evidence and trusted provider or referenced manual
  settlement; complimentary agreements create a settled zero Invoice without a Payment attempt or
  collected-value entry. Agreement detail deliberately omits protected payment references.
- Future start, exact activation, cancellation, all four completion choices, retry/manual attention,
  and append-only system Activities for actual start/end attempts are implemented through the
  subscription lifecycle.
- Admin operations include Account/global bounded lists, detail actions, manual settlement and
  currency-separated analytics. The five-step workbench and client-safe terms view were verified in
  authenticated light/dark and Arabic RTL layouts; the client contract excludes operator identity,
  reason, settlement route, references, and internal failure detail.

---

### MARKETING-001 — Close authenticated browser evidence for the implemented Offer control plane

**Status:** `IMPLEMENTED — BACKEND/UI AUTOMATED AND SECURITY VERIFIED 2026-08-31; AUTHENTICATED BROWSER QA PENDING`

**Evidence**

- Safe Segment backend/admin UI now exists with explicit Account or closed typed-criteria audiences, bounded preview/count, immutable signed activation/frozen Accounts, lifecycle/revisions/compare/history/ownership, privacy-separated identity access, and Policy target integration.
- The Campaign backend now provides revision lineages, PUBLIC/explicit-Account/exact-Segment audiences, signed scheduling, immutable targeted audience/provenance, lifecycle automation, ownership, comparison/history, truthful actions/blockers, bounded APIs, and separately authorized identity resolution. It passed repeated independent backend audits on 2026-08-28, including audit-attribution, query-bound, and permission-before-existence regressions.
- The Campaign admin UI now provides the table, guided editor, authoritative operation state, audience/evidence, revisions/compare, history, owner management, lifecycle dialogs, and safe least-privilege navigation. An independent frontend audit verified narrow queries, stale retries, responsive/mobile/RTL layout, and permission combinations on 2026-08-28.
- Production Offer lineages, immutable revisions, permanent codes, signed eligibility/publication evidence, exact selections, Policy/Offer price evaluation, durable Redemptions, lineage limits, client/operator idempotent acceptance, results, and privacy-separated identities now exist behind fine-grained operational APIs.
- Admin list/builder/detail/operations/results/redemptions/revisions/history/owner/application and client catalogue/code/preview/accept/history/detail surfaces are implemented. Automated verification covers permission-independent routes, exact-price privacy, stable signed evidence, code secrecy, concurrency, capacity and idempotency; authenticated rendered French/Arabic workflows remain to be recorded.

**Risk**

Without the remaining authenticated browser evidence, a mounted interaction or responsive/RTL defect could still block an otherwise correct API workflow. Treating current redemption counters as revenue or durable conversion analytics would also overstate what Phase 11 delivers; settlement and time-series facts remain later phases.

**Required fix direction**

- Run authenticated browser workflows for French desktop/mobile and Arabic RTL: create and preview a draft, publish through signed evidence, inspect operations/results/history, apply to one Account, resolve a private code, accept from the client portal, and read the privacy-safe redemption.
- Verify browser-visible loading, empty, denied, stale-evidence, paid-checkout-blocked, and retry states without exposing internal identifiers or raw codes.
- Keep bulk execution and lifecycle recovery in Phase 12 and durable conversion/revenue series in Phase 14 rather than manufacturing them from current Offer totals.

---

### MARKETING-002 — Segment-level origin is a misleading manual choice

**Status:** `FIXED — 2026-09-09`

**Evidence**

- The segment editor asks the administrator to choose **Origine**: **Manuelle**, **Importée**,
  or **Support**. The choice is stored as `CommercialSegmentSource`; it does not import a list
  or determine audience membership. List filters, summaries, and detail/review surfaces repeat it.
- The values mix a creation method with a business reason. A segment could contain both manually
  selected Accounts and Accounts selected through a future list-import flow, so one segment-wide
  label cannot honestly describe how every Account was added.

**Required fix direction**

- Follow the amendment to `MARKETING-FLOW-001` in `FLOW_DECISIONS.md`. Remove the origin selector
  and misleading segment-origin filters/presentation across editor, review, list/mobile, and
  detail surfaces. Review the write/read contract and legacy metadata rather than leaving an
  unexplained required field that new API consumers must invent.
- Preserve actor/history evidence, business reasons, existing membership, and frozen audience
  snapshots. Do not reinterpret old segment labels as trustworthy per-Account provenance.
- If a real import flow is designed later, capture its provenance automatically per Account
  inclusion/addition event; allow an explicit segment to mix imported and manual selections.
  Import formats/matching, mixed criteria/set selection, and implementation of import itself
  are outside this fix and are not being claimed as existing functionality.
- Verify that create/edit/review/list/detail work without the origin choice and that audience
  resolution, authorization, and historical evidence remain unchanged.

**Implementation evidence — 2026-09-09**

- Removed the selector, review/detail/mobile presentation, list filter, sortable key, URL state,
  and source fields from public create/update/summary contracts. Old saved URL filters are discarded.
- Existing internal values are preserved as legacy metadata, never converted into per-Account
  provenance; new administrative creation supplies its internal default automatically. No import
  feature, audience rule, or additional specification document was added.
- Verification: 114 backend integration tests across Segments, Campaigns, admin security, and
  subscriber repricing; 380 frontend tests, typecheck, Biome, and production build pass. New
  regressions cover source-free requests/responses, stale-link cleanup, and mounted edit/review.

---

### UI-003 — Restore spacious Plan catalogue cards

**Status:** `FIXED — 2026-09-09`

- Restored the earlier catalogue-card structure for Plans, which normally number only a handful.
  Add-on/package catalogues retain the shared table pattern; consistency does not require every
  product surface to have identical density.
- Cards show independent authoritative current tariffs, included Feature and subscription counts,
  lifecycle and explained actions. Availability/version remain in the detail page. No technical codes or whole-card click
  target. One bulk price request serves the visible page, with explicit denied/loading/error and
  partial-result states. Server-side search/filter/sort/pagination remain intact.
- Three rendered-component regressions cover facts/navigation, permissions and draft revision
  eligibility. Browser checked in light/dark themes, desktop and narrow widths, filtering and
  opening a Plan. No horizontal overflow at the narrow viewport.

**Readability follow-up — 2026-09-19**

- Applied the visually approved compact card: moderate exact price without an inner panel, compact
  icon-assisted counts and a separated footer with labelled Dupliquer/Réviser/Ouvrir actions.
  The grid adapts to available content width rather than squeezing cards at viewport breakpoints.
  Removed repeated tariff headings, routine readiness text and
  secondary metadata; meaningful missing-price/composition warnings remain visible. Mutation
  permissions and backend action gates are unchanged.
- Currency appears once per catalogue price. Multiple cycles/currencies and exact decimal
  precision have regression coverage; neither prices nor configured currencies were changed.
  Mounted regressions verify labelled navigation, keyboard-accessible denial explanations and
  unavailable draft revisions. Full frontend verification: **404 tests, zero failures**,
  typecheck, Biome and production build pass.
- Browser checked light/dark themes, RTL and a 375px narrow viewport, with no card/page horizontal
  overflow and successful Plan navigation. Existing demo servers/data were not restarted or changed.

### UI-001 — Shared section tabs lack complete keyboard and panel semantics

**Status:** `CONFIRMED — DEFERRED TO PHASE 15 CONSISTENCY PASS`

**Evidence**

- `frontend/src/components/patterns/section-tabs.tsx` renders tab roles but does not provide roving focus with Arrow/Home/End keys or stable `aria-controls`/`tabpanel` associations.
- The pattern is reused across operational detail pages, so per-page fixes would duplicate behavior and remain inconsistent in RTL.

**Risk**

Keyboard and assistive-technology users cannot navigate or understand the tab/panel relationship consistently, and later pages may copy the incomplete contract.

**Required fix direction**

- Upgrade the shared pattern to one tabbable active tab, Arrow/Home/End navigation with RTL-aware direction, stable tab/panel IDs and `aria-controls`, and an associated `tabpanel` contract.
- Migrate every consumer through the reusable API and add focused keyboard, RTL, and accessibility tests during the Phase 15 consistency pass.

---

### UI-002 — Plan detail routes incorrectly display access denial for authorized operators

**Status:** `FIXED — 2026-09-08`

**Original evidence**

- `PlanDetailPage` defaults its tab to `overview`, but the final truthy-tab fallback rendered `PermissionState` for that valid overview too, including for SuperAdmins.
- The static `features`, `schema`, and `subscribers` routes did not populate `useParams().tab` and passed no explicit tab, so those URLs also resolved to the broken overview instead of their own panels.

**Fix and verification**

- The fallback now rejects only tabs other than `overview`. Static detail routes pass their tab explicitly; dynamic tabs still use the URL parameter.
- The actual plan-detail route definitions are shared with rendering tests. Existing route and panel permission gates remain unchanged; no SuperAdmin bypass or broader access was introduced.
- Thirteen regression tests cover the overview, static routes, tab navigation, dynamic prices, unknown tabs, and denied deep links without protected data requests. Six original rendering cases reproduced the defects before the fix.
- Frontend verification: 370 tests pass, Biome checks 283 files, typecheck and production build pass. These stages were run directly because the existing local Bun wrapper prevents the aggregate `bun run verify` script from starting.
- Browser verification on the logged-in SuperAdmin session confirms Synthèse, Fonctionnalités, Schéma, Abonnés, Tarifs, Compatibilité, Disponibilité, Historique, and Cycle de vie open their respective panels. No commercial data or backend authorization rules changed.

---

### ANALYTICS-001 — Commercial dashboards have no durable fact model or operational drill-down

**Status:** `IMPLEMENTED — 2026-08-31; FINAL PHASE 15 AUDIT PENDING`

**Original evidence**

- `/admin/analytics` is a placeholder page.
- Existing summary amounts come from configured current subscription prices rather than invoice/payment/credit/refund evidence.
- No append-only commercial event/fact contract records Offer eligibility/redemption, policy execution, product adoption/churn, renewal outcomes, or near/over-quota states for bounded time-series queries.

**Risk**

Decorative totals may be mislabeled as revenue, mixed currency/cycle values can be combined, historical graphs can change when mutable records change, and operators cannot drill into the Accounts/events behind a number.

**Required fix direction**

- Build durable commercial facts from subscription periods/operations and the invoice/payment/credit/refund ledgers, plus append-only events where no authoritative state transition already exists.
- Add bounded timezone/interval/filter-aware summary and time-series APIs with explicit currency/cycle dimensions, completeness time, pagination, and no mixed-money total.
- Make every summary/chart link to a filtered operational table. Expose missing/incomplete data honestly and keep sensitive settlement evidence under separate permissions.
- Add event idempotency, historical stability, time-bound validation, mixed-currency, permission/privacy, query-count, and realistic drill-down tests.
- Follow the frozen `docs/COMMERCIAL_ANALYTICS_V1.md` contract. Keep current product holdings separate from historical adoption/churn, and defer historical near-quota pressure until a cadence-based append-only usage snapshot exists instead of issuing per-Account live business-table reads from the dashboard.

**Implemented backend evidence (2026-08-31)**

- `platform.analytics` now declares independent summary, financial-series, subscription-series, Offer-series, and operations permissions.
- Bounded admin endpoints aggregate immutable financial evidence, durable lifecycle/Offer events, accepted-operation snapshots, and the normalized current-holdings projection without persisting a competing analytics truth.
- Money remains split by currency and billing cycle, exact decimal strings cross the API boundary, provisional buckets and read watermarks are explicit, and aggregate responses omit Account identity.
- The operational attention queue is stably paginated and remains readable when legacy lifecycle rows lack their newer transition timestamps.
- `/admin/analytics` now mounts only the independently authorized summary, financial, subscription, Offer, and operations tabs; a summary-only operator never requests identity-bearing or detailed series.
- The range bar is URL-backed and exposes money dimensions only where the endpoint accepts them. Exact decimal totals remain strings through frontend summation and formatting.
- Accessible SVG series and adjacent tables share the same data; failed queries remain retryable instead of becoming zero, provisional buckets are explicit, and no-activity ranges collapse meaningless all-zero tables into honest empty states.
- Authenticated browser QA covered light and dark desktop layouts, narrow-screen overflow, and RTL direction. The reusable tab scrollbar is hidden without disabling horizontal touch/keyboard scrolling.
- Exact cross-surface drill-down consistency and the final shared tab keyboard/panel semantics remain part of Phase 15 rather than being claimed complete here.

---

### OPERATIONS-001 — Audit, delivery, and health evidence have no safe operational surface

**Status:** `IMPLEMENTED AND VERIFIED — 2026-08-31`

**Evidence**

- `AuditLog` already records append-only mutation evidence, but `/admin/activities` is a placeholder and no bounded authorized general query API exists.
- `EmailDelivery` records safe delivery status without tokens or bodies, but `/admin/communications` promises content preview and resend that the security model deliberately cannot provide.
- `/admin/observability` is a placeholder. Normalized errors and audit evidence have no common request correlation id, and no safe health/backlog or external-log availability contract exists.

**Risk**

Operators cannot investigate real platform behavior from the UI. Implementing the placeholders literally would either expose sensitive generic repositories/logs or falsely claim that unstored credential messages can be previewed and replayed.

**Required fix direction**

- Implement the separated Activities, Communications, and Observability surfaces exactly as frozen in `docs/PLATFORM_OPERATIONS_V1.md`.
- Keep identities, audit payloads, delivery failure evidence, health, backlogs, and log access independently authorized.
- Add bounded correlation ids across HTTP response/error, MDC, and new audit records.
- Keep `AUDIT-002`, export, generic resend/content preview, and internal raw-log storage explicitly deferred.

**Implementation evidence — 2026-08-31**

- `platform.activities`, `platform.communications`, and `platform.observability` now expose separate service-boundary permissions for safe metadata and each sensitive evidence class. SuperAdmin remains the authority root; frontend visibility is not the security boundary.
- Activities provide bounded filtering, stable pagination, safe detail, optional bulk-resolved actor/Account identity, and separately fetched redacted payload evidence. Communications provide durable status/purpose summaries and delivery rows, with recipient identity and failure evidence fetched only under their narrow permissions.
- Observability exposes safe component states, aggregate billing/provider/email backlog evidence, owning-workbench links, and only the configured external log-provider destination. It never exposes raw logs, stack traces, environment values, message bodies, action URLs, or credentials.
- Every request receives a bounded correlation identifier that is returned in `X-Request-ID` and `ApiError`, scoped to logging MDC, and persisted on new mutation audit rows. Tests cover accepted/replaced identifiers, error propagation, MDC cleanup, and audit persistence.
- The three former placeholders are real French-first workbenches using shared tables/actions on desktop and compact records on narrow screens. Queries mount by exact permission, payload/identity/failure reads do not piggyback on list access, and loading/error/empty/retry states remain explicit.
- The complete backend suite passes 812 tests; the complete frontend suite passes 346 tests. Authenticated browser QA covered desktop, narrow, light/dark, RTL, Activity investigation, Communications, and health/backlog navigation.
- `AUDIT-002`, export, generic email replay/content preview, built-in raw-log storage, traces, alerting, and incident workflow remain explicit deferrals rather than implied missing buttons.

---

### EMAIL-001 — Missing SMTP silently becomes token logging and apparent delivery success

**Status:** `IMPLEMENTED — 2026-08-10`

**Evidence**

- The former invitation sender and secret-bearing fallback logging have been removed.
- `LoggingEmailServiceImpl` is now restricted to non-production profiles and records only destination/purpose/workspace/expiry, never the action URL or token.
- Production has no logging fallback. An explicit startup validator rejects a missing or blank `spring.mail.host` with a configuration-specific failure before credential-email components are wired.
- Credential emails are requested transactionally and sent after commit. Before this batch, a failure left the member pending but existed only in logs; callers and managers had no durable status or failure metric.

**Risk**

Before this batch, a delivery failure could leave access pending with no durable explanation or UI feedback. Any future automatic retry would also risk replaying a secret-bearing link or rotating credentials without an explicit request unless a stronger outbox policy is designed.

**Implementation evidence — 2026-08-10**

- Each credential email now creates an `EmailDelivery` row in the identity transaction with Account, recipient User/address, purpose, and `PENDING` status. Raw tokens, action URLs, message bodies, provider exception messages, and reusable credentials are never stored.
- The synchronous `AFTER_COMMIT` listener records `SENT`, `FAILED`, or non-production `SUPPRESSED` in an isolated transaction. SMTP exceptions become bounded failure codes (`MESSAGE_CONSTRUCTION_FAILED`, `AUTHENTICATION_FAILED`, `TRANSPORT_FAILED`, or `UNEXPECTED_FAILURE`) rather than provider details. The original exception and stack trace are emitted once to operational ERROR logs for diagnosis, but never persisted in `EmailDelivery` or `AuditLog`.
- `EmailService` returns an explicit transport outcome. The development logging transport reports `SUPPRESSED`, never delivered; production still has no logging fallback, and `EmailStartupValidator` fails startup clearly when `spring.mail.host` is missing or blank.
- Member creation, regeneration, and reset responses expose the current delivery result plus total/failed attempt counts. `GET /api/v1/members/{id}/access`, protected by `platform.staff.read_access` and Account-scoped lookup, exposes the latest credential and delivery state for later UI visits.
- Safe retry uses the existing Permissionizer-protected regenerate/reset actions. It creates a new delivery history row and rotates the credential token; the failed link is never reused. Self-service reset remains non-disclosing, while an authorized Account actor can inspect the member status.
- Automatic background retry is deliberately absent: safely retrying would require either persisting reusable secret-bearing content or rotating access without an operator/user request. A future provider-backed encrypted outbox may add that only with an explicit delivery policy.
- SMTP success/failure/suppression, diagnostic exception logging without credential-link leakage, explicit production startup validation, unknown-outcome safety, immediate durable failure feedback, aggregate metrics, token-rotating recovery, and cross-Account status isolation are covered. The complete 427-test backend suite passes with zero failures, errors, or skips.
- The generated disposable schema includes `email_deliveries` directly. No Flyway history was added under the current unpublished-database policy.

---

### EMAIL-002 — Invitation HTML embeds unescaped user/configuration values and hard-codes expiry text

**Status:** `RESOLVED BY REPLACEMENT — 2026-07-16`

**Evidence**

`SmtpEmailServiceImpl` inserts inviter name, workspace name, and acceptance URL directly into HTML/text attributes via `String.formatted()`. It also states "expires in 7 days" even though expiry is configurable.

**Risk**

Names containing markup can alter the email body, malformed/configured URLs can break link attributes, and changed expiry configuration produces false instructions.

**Required fix direction**

Use a safe template/escaping mechanism, validate the configured HTTPS frontend origin in production, and render the actual expiry deadline/duration supplied by the invitation workflow.

**Implementation evidence — 2026-07-16**

The replacement activation/reset template HTML-escapes member name, workspace name, action URL, and ISO expiry deadline. `ActivationProperties` validates an HTTP(S) origin, forbids user-info/query/fragment, normalizes trailing slashes, requires HTTPS in production, and rejects non-positive expiry. A focused template test proves escaping and verifies the configured absolute deadline replaces the old hard-coded seven-day copy.

---

### API-ERROR-001 — Error responses lack stable machine-readable business codes

**Status:** `RESOLVED — 2026-08-11`

**Implementation evidence — 2026-08-11**

- `ApiError` carries a stable `ErrorCode`; clients branch on it instead of matching message text, which is now free to be reworded or translated.
- Every construction site supplies one — all 17 handlers in `GlobalExceptionHandler` plus `ContextDetectionFilter`, `AccessDeniedHandler` and `AuthEntryPoint`. No response can be emitted without a code.
- Codes were derived from the handlers that already exist, not invented speculatively. The concrete win is that `409` now splits into `RESOURCE_ALREADY_EXISTS`, `DATA_CONFLICT`, `INVALID_STATE` and `OPERATION_BLOCKED`, which `GlobalExceptionHandlerTest` asserts stay distinct.
- Contract recorded on the enum: once shipped, a constant is never renamed or repurposed.

**Evidence**

`ApiError` exposes HTTP status, a generic label, free-text message, timestamp, and optional string details. Most business conflicts are distinguished only by English messages. Quota metadata is encoded as strings such as `resource: ...` rather than structured fields.

**Risk**

An admin/client UI must parse messages to decide whether to show retry, impact resolution, upgrade, forbidden, or validation flows. Copy changes can break behavior, localization is difficult, and support correlation is weak.

**Required fix direction**

Add stable error codes and structured metadata for expected business outcomes, plus request/correlation identifiers. Keep human messages localized/presentational and never require UI logic to parse them.

---

### TEST-001 — Green tests preserve important unsafe behavior and omit critical negative cases

**Status:** `IN PROGRESS — 2026-07-16`

**Evidence**

The latest local report is green (213 tests on 2026-07-16), and the suite has useful isolation, policy, quota, subscription-integrity, and control-plane coverage. Batches 0.1–0.2 added focused configuration/admin-bootstrap negative tests, explicit guard-boundary tests, and six standalone Permissionizer tests. However:

- client self-service integration explicitly expects applying PRO to return `201` and activate it without payment;
- admin security tests explicitly expect every current feature activation request to be rejected;
- no reviewed test covers a null/unlimited client quota override;
- no reviewed test uses a low-privilege client employee against an account-level B2B delegation;
- no reviewed test proves an account-wide deny still applies with company context;
- no reviewed test rejects inactive account/company context;
- seeder tests do not reconcile removed/stale features or permissions;
- no HiveApp startup test proves Permissionizer verification/interception fails closed;
- no reviewed test rejects an access token at the refresh endpoint;
- no reviewed test protects FREE/default plan from disable/delete/missing state.

**Risk**

"All tests pass" currently means the code matches its present assumptions, not that the product/admin/client flows are safe or complete. Fixes may require changing tests that encode shortcuts rather than treating every current assertion as desired behavior.

**Required fix direction**

Before implementation, convert each confirmed security/entitlement/lifecycle finding into an abuse or invariant test. Classify existing tests as durable requirement, temporary MVP behavior, or behavior to replace.

**Batch 0.1 execution evidence — 2026-07-16**

- The full HiveApp backend suite passes: 213 tests, zero failures/errors/skips.
- The standalone Permissionizer suite passes: 6 tests, including overload collection, fail-fast collection failures, package-guard interception characterization, and fatal startup alignment.
- This finding remains open because the broader negative-case list above has not yet been implemented.

---

## Security and runtime authorization findings

### CONFIG-001 — The only application configuration is destructive development configuration

**Status:** `PARTIALLY IMPLEMENTED — 2026-07-16`

**Evidence**

The main `application.yaml` uses:

- in-memory H2;
- `hibernate.ddl-auto: create-drop`;
- SQL logging;
- a fixed JWT signing secret committed in source;
- localhost-only invitation/CORS assumptions.

No production profile/configuration is present in the reviewed resources.

**Risk**

Data disappears on restart, logs can expose sensitive SQL, and anyone with the committed secret can forge valid tokens in any deployment that reuses it. The backend is not deployable as a persistent company system in this state.

**Required fix direction**

Separate explicit local/test and production profiles. Production must use externally managed secrets, persistent database configuration, versioned migrations, safe logging, environment-specific URLs/CORS, and startup validation that rejects development defaults.

**Batch 0.1 implementation evidence — 2026-07-16**

- Common configuration no longer embeds H2, `create-drop`, SQL logging, a JWT secret, or a localhost frontend URL.
- Explicit `dev`, `test`, and `prod` profiles now isolate destructive H2 settings to development/tests.
- The production profile uses externally supplied PostgreSQL connection values, JWT secret, and frontend URL, with `ddl-auto: validate`.
- A production-profile context test proves admin bootstrap is disabled by default.
- The remaining part of this finding is a versioned Flyway/Liquibase migration baseline; production schema creation/upgrades are not yet implemented.

---

### AUTHZ-001 — HiveApp explicitly disables Permissionizer guard verification

**Status:** `IMPLEMENTED — 2026-07-16`

**Evidence**

`SecurityConfig.permissionsLoader()` builds the Permissionizer guard and calls `.skipVerification()` before `.initialize()`.

The current compiled artifact does contain generated Permissionizer metadata for 12 feature roots and 72 action paths, and reviewed feature service methods are annotated. However, runtime startup deliberately does not verify that guarded code is actually intercepted/aligned.

**Risk**

A missing AOP/agent interceptor, proxy edge case, or future unverified guarded service can start successfully with no method enforcement. Annotation presence alone is not proof of runtime protection.

**Required fix direction**

Remove `skipVerification()` after fixing any underlying verification problem. Add production-startup and request-level tests proving guarded methods fail closed when interception/configuration is missing.

**Batch 0.2 implementation evidence — 2026-07-16**

- `SecurityConfig` no longer calls `skipVerification()`.
- Spring interceptor creation is an explicit initialization dependency, so verification cannot run before interception registration.
- PermissionGuard configuration resets preserve installed interception mechanisms across Spring test/application contexts.
- The broad `com.hiveapp.platform` node is structural-only (`guard = OFF`); all 12 permission-bearing service implementations explicitly declare `guard = ON`.
- Boundary tests prove infrastructure does not inherit a synthetic permission and an explicitly guarded service resolves its exact action path.
- Existing HTTP security tests prove unauthorized client/admin requests are denied after activation.
- Non-blocking cleanup remains: Spring warns that the inherited final `AbstractFeatureService.featureDefinitions()` method cannot be proxied on guarded service classes. It is registry-contribution infrastructure rather than a permission action; later separate that contribution from guarded service proxies or add a deliberate advisor exclusion instead of treating the warning as authorization coverage.

---

### AUTHZ-002 — Any active member of a B2B client account can use all delegated collaboration permissions

**Status:** `PARTIALLY IMPLEMENTED — SAFE ACCOUNT-WIDE OPERATOR CEILING`

**Evidence**

- B2B context selects an active member of the client account.
- `B2bCollaborationPolicy` runs before `PlanPolicy` and `UserRolePolicy`.
- Before Batch 5.3, once the collaboration row contained the requested permission and the provider account was entitled, the B2B policy returned `GRANTED` immediately.
- It did not check whether the acting client member's roles/overrides allowed that delegated action.

**Risk**

A low-privilege employee in the client workspace can exercise every permission delegated to the client account for that provider company.

**Required decision/fix direction**

Separate account-level delegation from actor-level use. A safe default requires both: the provider delegated the action to the client account, and the acting client member is authorized by a client-side role/assignment to use that delegated action. Define owner and B2B-operator exceptions explicitly.

**Implementation evidence — 2026-08-10**

- `B2bCollaborationPolicy` now grants only after the exact provider grant, current provider entitlement, current code eligibility, and external-actor authority all pass.
- External authority is evaluated against the client Account with no provider Company scope. The existing role/exception resolver preserves owner authority, active-role behavior, expiry, and Account-deny precedence.
- An ordinary external member is denied before assignment and allowed after an Account-scoped role containing the exact delegated action is assigned; request-level coverage exercises the complete flow.
- No B2B-specific duplicate role entity and no organization-Group-derived authority were introduced.

---

### AUTHZ-007 — B2B operator selection requires widening the member's internal Account authority

**Status:** `CONFIRMED — REFINEMENT DEFERRED`

**Evidence**

- `B2bCollaborationPolicy` evaluates external-actor authority with the client Account as `currentAccountId` and `targetCompanyId=null`.
- `UserRolePolicy` therefore resolves only Account-scoped roles and exceptions. A Company-scoped assignment cannot nominate an operator for a foreign provider Company.
- The same permission code is an ordinary internal workspace permission. Granting it at Account scope can authorize the member over all Companies owned by the external Account, subject to that Account's own plan/runtime gates.
- There is no member/role assignment whose effect scope is one Collaboration.

**Current safety boundary**

The provider grant still bounds the B2B blast radius. An operator receives no access to an undelegated permission, provider Company, or collaboration. If several collaborations separately delegate the same action, however, the Account-scoped operator grant can satisfy the actor ceiling for all of them. The defect is the operator-selection lever: enabling external work necessarily widens the member's internal Account authority.

Example: an audit firm cannot let a junior employee read one client's delegated books without also granting that employee the same action across the audit firm's own Company scope.

**Likely refinement direction**

- Extend the existing MEMBER-FLOW-003 assignment-effect model with `COLLABORATION` alongside `ACCOUNT` and `COMPANY`.
- Reuse role templates, member-role assignments, lifecycle, duplicate protection, impact preview, actor delegation ceilings, and deny rules rather than introducing a parallel B2B role system.
- Bind a Collaboration-scoped assignment to an exact active participant relationship and intersect its role permissions with the provider's current grants at runtime.
- Keep organization Groups outside authorization. Define picker, lifecycle, history, and owner behavior when this refinement is scheduled; do not redesign it inside Batch 5.3.

---

### AUTHZ-003 — Existing B2B grants are not revalidated against current code delegation rules

**Status:** `IMPLEMENTED — 2026-08-10`

**Evidence**

Grant creation calls `PermissionGrantValidator.requireB2bDelegatable()`, but runtime `B2bCollaborationPolicy` checks only the persisted collaboration-permission row and provider plan entitlement. It does not verify that the action remains in the current definition's `b2bDelegatableActions`.

**Risk**

Removing B2B eligibility from code does not revoke or block an existing delegated permission. Stale/corrupt rows can grant actions that the current source says are not delegatable.

**Required fix direction**

Runtime must intersect persisted grants with the current code-owned B2B action allowlist and preserve invalidated rows as inactive history. Automated retirement/migration reporting remains part of the separately deferred `REGISTRY-001` lifecycle rather than this runtime authorization fix.

**Implementation evidence — 2026-08-10**

- Runtime already called `PermissionGrantValidator.isB2bRuntimeEligible()` before consulting persisted grants; focused policy coverage now locks that short-circuit.
- Collaboration grant DTOs and access blockers use the same dynamic check. A formerly valid row becomes `currentlyActive=false` with a code-eligibility blocker while remaining available as historical configuration.
- This implements immediate fail-closed behavior without contradicting the product rule that security history is preserved rather than silently deleted.

---

### AUTHZ-004 — Account-wide member overrides do not apply inside company context

**Status:** `IMPLEMENTED — 2026-07-17`

**Evidence**

- `UserRolePolicy` loads overrides with `findAllByMemberIdAndCompanyId(memberId, targetCompanyId)`.
- With a selected company, this excludes account-wide overrides whose company is null.
- The role query, by contrast, applies both exact-company and null/account-wide assignments.

**Risk**

An account-wide DENY can be bypassed by sending a company context, while an account-wide GRANT unexpectedly disappears. Override and role scope semantics conflict.

**Required decision/fix direction**

Define precedence among account-wide and company-specific decisions. Query both applicable scopes and resolve conflicts deterministically—normally a specific deny/decision rule documented and tested across all combinations.

**Implementation evidence — 2026-07-17**

- Applicable-exception queries return Account scope plus only the exact selected Company scope.
- Account grants and denies cascade into Company evaluation; Company exceptions never affect Account or sibling-Company evaluation.
- Any applicable active deny wins over Account/Company grants and roles in both Permissionizer runtime policy and effective-permission reads.
- Expired exceptions and exceptions belonging to inactive Companies have no authorization effect.
- End-to-end tests verify Account cascade, exact Company isolation, deny precedence, lifecycle rules, and source-aware exception responses.

---

### AUTHZ-005 — Tenant and B2B context headers are absent from CORS configuration

**Status:** `IMPLEMENTED — 2026-08-10`

**Evidence**

Runtime context depends on `X-Company-ID` and `X-Is-B2B`, but `SecurityConfig` allows only standard authorization/content headers in CORS.

**Risk**

A browser frontend hosted on an allowed different origin cannot pass preflight for company-scoped or B2B requests. Development through a same-origin proxy may hide the failure.

**Required fix direction**

Add the exact context headers to environment-specific CORS configuration and test real browser preflight. Prefer an explicit selected-workspace/company contract rather than proliferating ad hoc headers.

**Implementation evidence — 2026-08-10**

- `SecurityConfig` explicitly allows the two headers actually consumed by context detection: `X-Company-ID` and `X-Is-B2B`.
- A browser-style preflight integration test requests both headers from an allowed frontend origin and verifies both appear in `Access-Control-Allow-Headers`.

---

## Registry and Permissionizer integration findings

### REGISTRY-001 — Removed code definitions and annotations remain as live database rows

**Status:** `CONFIRMED — DEFERRED BY PRODUCT DECISION`

**Evidence**

- `FeatureSeeder` only creates or updates definitions returned by `FeatureDefinitionCollector`; it never reconciles features or modules that disappeared from code.
- `PermissionSeeder` only creates missing permission rows. It never archives or deletes permissions no longer returned by `PermissionCollector`.
- Existing permission rows are not updated when an annotation's name, description, resource, or action metadata changes.
- The legacy registry catalog and permission-picker services read database rows with `findAll()`.

**Risk**

A renamed or removed feature/action can survive indefinitely. It can remain visible, assigned to plans/roles, or returned by catalogs even though source code no longer declares it. Future reuse of the same key could silently inherit old grants. Registry state therefore drifts from the intended code-owned contract.

**Required fix direction**

Define a synchronization lifecycle for removed definitions and actions. Prefer explicit retirement/tombstone state with startup reporting and a controlled migration of plan, role, direct-member, admin-role, and B2B references. Update code-owned metadata on every seed. Do not hard-delete referenced rows without an impact plan.

Current priority decision: do not build alias/replacement flags or advanced rename automation now. Treat deployed feature/permission codes as stable developer contracts and revisit this issue only when an actual removal/rename requirement appears. This deferral does not authorize removing security annotations from live methods or reusing old codes silently.

---

### REGISTRY-002 — Stale permission actions still pass role-grant validation

**Status:** `IMPLEMENTED — 2026-07-17`

**Evidence**

`PermissionGrantValidator` resolves a permission to the feature definition and then:

- for client roles, checks only `definition.clientRoleGrantable()`;
- for platform admin roles, checks only `definition.platformAdminRoleGrantable()`;
- `FeatureDefinition.ownsPermission()` checks only the three-segment prefix/shape, not whether the action is currently declared by a `@PermissionNode` method.

Only B2B delegation is narrowed to an explicit action set.

**Risk**

Any old database permission under a currently grantable feature remains selectable and grantable after its annotation is removed. The database, rather than the current Permissionizer source, becomes authoritative for action existence.

**Required fix direction**

Build an authoritative current action set from a strict Permissionizer collection result and require membership in that set for all grant targets. Catalogs must exclude retired/orphaned actions, and startup must report every stale grant before retirement.

**Implementation evidence — 2026-07-17**

- `CurrentRegistrySnapshot` holds only the action codes from the last fully validated and successfully synchronized source snapshot.
- `PermissionGrantValidator` rejects any client-role, platform-admin-role, or B2B grant code absent from that snapshot before applying audience rules.
- Registry catalogs and client/B2B permission pickers filter database rows through the same action set, so an orphaned row is neither visible nor grantable.
- Advanced rename/removal migration remains explicitly deferred under `REGISTRY-001`; action codes remain stable developer contracts.

---

### REGISTRY-003 — A partial or empty Permissionizer collection is accepted as successful seeding

**Status:** `IMPLEMENTED — 2026-07-17`

**Evidence**

`PermissionSeeder` validates only entries that `PermissionCollector.collect()` returned. It does not require at least one action for every guarded feature, compare the collected action set with service methods, or reconcile missing database permissions. The standalone collector was already observed swallowing several discovery/class-loading failures.

**Risk**

Broken discovery can produce a deceptively successful startup with missing new permissions while old rows remain. The admin UI and authorization data may then disagree with the actual annotations.

**Required fix direction**

Make collection diagnostics explicit and startup-fatal in production when indexes/classes cannot be read, codes are ambiguous, guarded features unexpectedly lack actions, or the complete registry graph is invalid. Validate the whole discovered snapshot before writing. Add guarded-service/action-set integration tests and persist a structured synchronization report rather than relying only on logs.

**Implementation evidence — 2026-07-17**

- `RegistrySnapshotFactory` validates the complete definition/action graph before database writes and computes a deterministic canonical SHA-256 hash.
- Guarded actions are independently reflected from Spring target classes and compared exactly with Permissionizer collection output. A missing single action fails even when its feature still has other actions.
- Empty definitions/actions, duplicate actions, malformed paths, missing definitions, module mismatches, and guarded features without actions are startup-fatal.
- Failed discovery is recorded safely in a separate transaction and is never installed as the current runtime grant snapshot.

---

### REGISTRY-004 — Current feature activation cannot represent the four decided operational controls

**Status:** `IMPLEMENTED — 2026-07-17`

**Evidence**

- `RegistryServiceImpl.updateFeatureActive()` rejects every definition whose `operationsActivationToggleable` flag is false.
- No production `FeatureDefinition` calls `.operationsActivationToggleable()`; the only usages outside the definition/service are tests.
- Consequently, `PATCH /api/admin/registry/features/{id}/active` currently rejects every real feature.
- `Feature` stores only one `isActive` value. Current consumers use it for public catalog visibility, plan-feature validation, and client plan-change selection, while no independent new-sale, new-grant, or emergency-runtime state exists.

**Risk**

An admin UI that displays activation controls promises a capability that does not exist. Making the current flag toggleable would also overload one value with different commercial, authorization, and runtime meanings: a visibility change could unexpectedly affect sale/selection, while an intended emergency shutdown might fail to stop existing runtime use consistently.

**Required fix direction**

- Replace the ambiguous update-active contract with separately permissioned public-visibility, new-sale, new-grant, and emergency-runtime operations. Define code-owned eligibility for which features may expose each operator control.
- Public visibility affects only public catalog surfaces. New-sale availability blocks future plan/subscription selection only. New-grant availability blocks future role/override/B2B grants only. Existing customer/grant changes use separate explicit operations.
- Emergency shutdown must fail closed at API authorization, invalidate relevant cached authorization, preserve data/configuration/history, require reason/scope/timing/impact/communication confirmation, and be fully audited. Restoration revalidates all current eligibility rather than reviving stale sessions.
- Until these states and runtime consumers exist, render registry activation as read-only and do not turn on the existing overloaded flag for production features.
- Add independence tests proving each control changes only its declared surface, plus emergency cutoff, stale token/cache, restoration, authorization, audit, and concurrency tests.

**Implementation evidence — 2026-07-17**

- `Feature` now stores independent `publicVisible`, `newSalesEnabled`, `newGrantsEnabled`, and `runtimeEnabled` state. The ambiguous `/active` operation is removed and each replacement endpoint has its own Permissionizer action and code-owned eligibility rule.
- Catalog/feature mutation acquires the registry synchronization lock and a pessimistic Feature lock. A real change records a durable `FeatureOperationalChange` containing actor, control, before/after values, reason, confirmations, immediate timing, and timestamp, then bumps the catalog revision; typed history is available to an authorized operator.
- Public visibility affects public listing only; new-sale state affects future plan/subscription selection; new-grant state blocks future client-role/override/B2B grants without revoking existing grants.
- `FeatureRuntimePolicy` executes before actor-specific policies and rechecks persisted runtime state for every action. Emergency cutoff therefore denies stale-token use immediately, while restoration still requires the current action/classification, entitlement, and actor policy to pass.
- Emergency changes require explicit impact and communication confirmation. Unit and integration tests cover control independence, ineligible controls, authorization, audit mapping, version invalidation, immediate cutoff, and restoration.
- No Flyway migration was added because the application is unpublished and uses disposable generated H2 mappings.

---

### REGISTRY-005 — The two public catalog implementations disagree and one mutates JPA entities

**Status:** `IMPLEMENTED — 2026-07-17`

**Evidence**

- `RegistryServiceImpl.getPublicCatalog()` returns raw `Module` entities and replaces each selected module's `features` collection inside a stream `peek()`.
- `PublicFeatureCatalogService` returns DTOs built from code definitions, but does not check `Module.isActive()`.
- The legacy implementation checks module activity; the DTO implementation does not.
- Removed code definitions can still appear in the raw database-driven catalog, while the DTO catalog excludes them.

**Risk**

The response depends on which endpoint a client uses. Mutating entity relationships merely to shape a response risks persistence side effects, immutable collection replacement, lazy-loading failures, and recursive/overshared serialization.

**Required fix direction**

Keep one versioned registry snapshot and explicit DTO read models per audience. Remove raw-entity API responses and query exact read models without mutating entities. Public catalog exposes only public-visible sellable items; other audiences apply their explicit operational, entitlement, and grantability rules. Apply module/feature operational state consistently and test that catalogs cannot disagree.

**Implementation evidence — 2026-07-17**

- The raw JPA public/inventory catalog path and response-time mutation of `Module.features` are removed. Admin inventory, feature catalogs, permission catalogs, and the public catalog return typed DTOs.
- Catalogs originate from current code definitions/current registry actions and join persisted operational state only for the audience-specific filters; stale database-only actions cannot reappear.
- Public results consistently require an active module, public/beta lifecycle, public visibility, new-sale availability, and runtime availability. Plan and permission audiences use their own sale/grant/runtime rules rather than sharing one overloaded flag.
- Public and admin integration tests verify the independent filters and typed contract.

---

### REGISTRY-006 — Permission-picker construction scales as permission-by-permission entitlement checks

**Status:** `IMPLEMENTED — 2026-07-17`

**Evidence**

`PermissionPickerCatalogService` loads every permission, then calls `planEntitlementService.isPermissionEntitled(accountId, permissionCode)` separately for each row before grouping the results.

**Risk**

Depending on the entitlement implementation, opening a role or B2B permission picker can cause a query chain per permission. This will worsen as HR/payroll/accounting add actions.

**Possible fix direction**

Resolve the account's effective entitled feature/action set once, then join/filter the current permission catalog in memory or in a purpose-built query. Add query-count and large-catalog tests.

**Implementation evidence — 2026-07-17**

- `PlanEntitlementService.entitledFeatureCodes()` resolves the account's active/trial subscription once, using its entitlement snapshot or plan features plus added overrides.
- `PermissionPickerCatalogService` loads registry permissions/features in bulk, obtains the entitled feature-code set once, and performs audience, state, entitlement, and selection filtering in memory.
- Interaction tests prove bulk entitlement resolution is called once and the former per-permission entitlement method is never called during picker construction.

---

### REGISTRY-007 — Existing feature rows are not fully repaired from their code definition

**Status:** `IMPLEMENTED — 2026-07-17`

**Evidence**

For an existing feature, `FeatureSeeder` overwrites lifecycle status, quota schema, and sort order but does not restore its module relationship. `PermissionSeeder` detects a wrong module only when it processes a collected action for that feature.

**Risk**

Corrupt or migrated registry data may remain inconsistent, especially for a feature with no collected actions. Code ownership is only partial.

**Possible fix direction**

Synchronize all code-owned relationships and metadata deterministically, and verify the complete registry graph after seeding.

**Implementation evidence — 2026-07-17**

- Existing Features repair module relationship, lifecycle status, quota schema, and sort order from code definitions.
- Existing Permissions repair Feature relationship, name, description, resource, and action from collected annotations.
- Admin-owned activation values are preserved, and synchronization reports count only rows whose code-owned fields actually changed.
- Integration coverage corrupts both layers and verifies complete repair from the validated snapshot.

---

### REGISTRY-008 — Client-role grantability is feature-wide, including destructive and commercial actions

**Status:** `IMPLEMENTED — 2026-07-17`

**Evidence**

- `FeatureDefinition.clientWorkspace()` marks the whole feature client-role grantable.
- `PermissionGrantValidator` and `PermissionPickerCatalogService` then allow every persisted action owned by that feature.
- There is no client-role action allowlist/denylist equivalent to `b2bDelegatableActions`.
- Current examples include `platform.workspace.delete` and `platform.subscription.apply`, alongside ordinary read operations.

**Risk**

Routine custom-role configuration can delegate account deactivation or subscription-changing authority without an explicit owner/billing safety category. A feature-level flag is too coarse for actions with materially different impact.

**Required decision/fix direction**

Classify eligibility per action in HiveApp feature/registry definitions without changing the Permissionizer library. Explicitly support owner-only and client-role, platform-admin-role, and B2B eligibility combinations. Every audience catalog and grant validator must use the same current action classification, while services still enforce target/resource invariants and protected owner boundaries.

**Implementation evidence — 2026-07-17**

- `FeatureDefinition` now owns action-level owner-only classification alongside the existing explicit B2B allowlist and derives client-role/platform-admin eligibility per action.
- `platform.workspace.delete` and `platform.subscription.apply` are owner-only and are excluded from ordinary client-role pickers and grants; B2B delegation remains limited to explicitly listed actions.
- Registry snapshot validation rejects classifications that name undiscovered actions or conflict between owner-only and B2B categories.
- Permission grant validation, picker catalogs, effective permissions, `UserRolePolicy`, and `B2bCollaborationPolicy` all recheck the same current action classification and runtime state. This product metadata remains in HiveApp; Permissionizer was not modified.

---

### REGISTRY-009 — Startup synchronization is split, non-atomic across registry layers, and not inspectable

**Status:** `PARTIAL — ATOMIC SINGLE-WRITER SYNC IMPLEMENTED 2026-07-17; ROLLING-DEPLOYMENT GENERATION GUARD OPEN`

**Evidence**

- `FeatureSeeder` and `PermissionSeeder` are separate `ApplicationReadyEvent` listeners with separate transactions. Ordering runs features first, but a later permission validation/write failure can leave the feature transaction committed.
- Each application instance runs the listeners; there is no reviewed database-backed single-writer lock or synchronization version protecting concurrent multi-node startup.
- Both seeders report only log counters. There is no persisted synchronization run, snapshot version, admin-safe summary, developer detail, or pre-deployment validation result.
- `FeatureSeeder` updates lifecycle/quota/sort metadata but does not repair every code-owned relationship; `PermissionSeeder` skips metadata updates for existing valid rows.
- Permission discovery can be partial while still returning a collection, as recorded in `REGISTRY-003` and Permissionizer findings.

**Risk**

HiveApp can start with features committed but permissions incomplete, different nodes racing to seed, or stale code-owned metadata, while operators see no durable evidence of what the deployment discovered or changed. Authorization, plan configuration, and permission pickers can then disagree.

**Required fix direction**

- Build one registry-snapshot validation and synchronization coordinator. Discover/validate the complete module-feature-action graph first, then apply all code-owned rows/metadata/relationships in one transaction.
- Use a database-backed lock and synchronization version/hash so one application node writes and other nodes wait/read the completed authoritative result. Make retries idempotent.
- Keep code-owned identity, display metadata, relationships, current actions, and grant-target eligibility separate from admin-owned visibility, sale, grant, and emergency-runtime states; synchronization must never reset those admin choices.
- Persist each run with build/version, snapshot hash, timestamps, status, discovered/created/updated/invalid/missing details, and safe failure information. Provide a platform-admin operational summary and restricted developer diagnostics.
- Reuse the same validator for CI/pre-deployment dry runs. Add partial-collector, mid-write rollback, existing-row repair, admin-state preservation, concurrent-node, retry, report-authorization, and snapshot-version tests.
- Keep advanced code rename/removal migration deferred under the current stable-code decision; this synchronization work must not introduce Permissionizer alias/replacement flags.

**Implementation evidence — 2026-07-17**

- The separate feature/permission startup listeners are replaced by one ordered coordinator. Discovery and graph validation complete before the transactional writers run.
- A committed singleton lock row plus pessimistic database locking serializes concurrent nodes; simultaneous first-start inserts converge through the unique lock key. Retry is idempotent.
- Feature and permission writes plus the success report share one transaction. A simulated permission-layer failure proves Feature/module writes and the success report roll back together.
- Each success/failure stores build version, deterministic snapshot hash when available, timestamps, status, discovered/created/updated counts, and bounded safe details.
- `GET /api/admin/registry/synchronization/latest` is protected by the dedicated `platform.registry.sync_status` Permissionizer action and returns an admin-safe DTO.
- No Flyway history is introduced while the project uses disposable generated H2 mappings. The complete backend suite passes: 328 tests, 0 failures, 0 errors, 0 skipped.

**Remaining deployment-order gap — 2026-08-27**

- The database lock serializes writers but does not prove that the writer represents the newest deployed build. During a rolling deployment, an older node can acquire the lock after a newer node and overwrite the authoritative registry snapshot/hash with older code metadata.
- Before multi-version production rollout, synchronization must reject a desired deployment/build generation older than the committed authoritative generation (or require an equivalent explicit rollout authority). Add newer-then-older node ordering, retry, and rollback tests. This does not require permission aliases or a Permissionizer grammar change.

---

### REGISTRY-010 — Catalog and permission-picker contracts are neither uniformly audience-specific nor versioned

**Status:** `IMPLEMENTED — 2026-07-17`

**Evidence**

- HiveApp has multiple registry/public/picker DTO families and two public-catalog paths whose filtering already disagrees, while a legacy path returns/mutates JPA entities.
- `PermissionPickerCatalogService` provides generic client-role and B2B lists but action eligibility remains feature-wide for client roles; platform-admin, owner-only, and high-risk client actions lack one action-level classification contract.
- Picker DTOs contain available definitions only. They carry no registry snapshot version/hash, unavailable-current-selection state, or backend reason explaining why a current grant cannot be selected again.
- Role/collaboration writes do not submit a catalog version, so a UI can save a choice after entitlement, grant availability, emergency state, or registry eligibility changed.

**Risk**

Different screens can show different truths, high-risk actions may appear in ordinary role pickers, unavailable existing grants can disappear without explanation, and stale UI choices can be applied against changed security/commercial rules.

**Required fix direction**

- Generate public, platform inventory, plan, client-role, platform-admin-role, and B2B DTOs from one current versioned registry snapshot, applying only that audience's visibility, sale, entitlement, action-eligibility, collaboration, and delegation-ceiling rules.
- Return current selections separately from available choices. Preserve unavailable selected actions in authorized management views as disabled items with stable machine reasons and plain explanations; never allow them as new selections.
- Add action-level owner-only/client-role/platform-admin-role/B2B classification to HiveApp definitions and use it consistently in catalogs, validators, and service invariants. Do not add this product classification to Permissionizer itself.
- Include registry version/hash in picker responses and mutation requests. Reject stale writes with a refresh-required conflict; publish a new version and invalidate relevant caches after synchronization or operational-control changes.
- Remove raw JPA catalog responses and duplicate public contracts. Add cross-audience leakage, destructive-action eligibility, unavailable-current-grant, stale write, entitlement change, emergency shutdown, B2B actor ceiling, cache invalidation, and query-count tests.

**Implementation evidence — 2026-07-17**

- Client-role and B2B picker endpoints return `PermissionPickerCatalogDto` with `registryVersion`, audience, `availableChoices`, and `currentSelections` rather than a bare generic catalog.
- Current selections remain visible when unavailable, with stable reasons for missing current registry actions, lost entitlement, audience ineligibility, paused new grants, or emergency runtime shutdown. They are not offered as new choices.
- Registry versions use deterministic snapshot hash plus a persisted monotonic catalog revision. Successful synchronization changes and operator-control changes publish a new version.
- Role-permission and B2B grant writes submit the picker version and fail with a refresh-required conflict when stale; removals remain available for safe cleanup.
- Tests cover stale role writes without mutation, B2B versioned grants, unavailable selections, bulk entitlement resolution, action leakage, emergency runtime changes, and catalog revision changes. The complete backend suite passes: 338 tests, 0 failures, 0 errors, 0 skipped.

---

### REGISTRY-011 — Runtime permission checks repeatedly rebuild the static feature catalogue

**Status:** `FIXED — 2026-09-08`

**Evidence**

- `AdminPermissionResolver` checks every registry permission for a SuperAdmin. Each call through `PermissionGrantValidator.findDefinition()` invokes `FeatureDefinitionCollector.collectByCode()`, collecting and validating the entire code-owned catalogue again.
- Contributor-root validation calls `PermissionResolver.resolveClassPath()`. Package-annotation lookup repeatedly attempts `Class.forName(...package-info)` with the context class loader, including missing metadata; those misses repeat class-loader/filesystem work.
- Feature runtime/new-grant flags are also read permission by permission, even when many permissions belong to the same feature.
- Local `/api/admin/me` measurements ranged from about 4.6 seconds to 9.0 seconds; the opt-in browser skeleton timer measured about 9.3 seconds for the profile/permissions stage and 0.02 seconds for dashboard data. Direct API requests reproduced about 9 seconds with profiling disabled. The browser preflight was about 3 ms.
- A Java Flight Recorder investigation captured 227 of 228 samples inside the profile resolver in the catalogue/package-metadata lookup path, including 208 samples resolving filesystem paths. These are sample counts, not exact invocation counts or a throughput benchmark.
- The same grant validator is reused by role presets, role permission changes, SuperAdmin effective-permission views, and client/B2B eligibility checks. Every cold admin page also waits for the profile endpoint.

**Risk**

Static discovery work scales with permission count and request volume, delaying navigation and consuming server capacity even with almost no business data. Caching complete access decisions as a shortcut would instead risk stale grants, revocations or emergency feature controls.

**Required fix direction and verification**

- Reuse immutable validated code definitions; publish a complete replacement atomically when the installed registry snapshot changes. Preserve startup validation and fail-closed behavior for absent definitions/actions.
- Cache Permissionizer's static package metadata, including missing annotations, without sharing results across incompatible class loaders or retaining retired application loaders.
- Bulk-read mutable feature controls for a permission-set evaluation; do not retain users' effective permissions, mutable entities or operational flags across requests.
- Test current grant/runtime flags, action removal, snapshot replacement, role revocation, concurrent reads/initialization and class-loader isolation. Assert bounded discovery/query work rather than fragile wall-clock thresholds in unit tests.
- Run both Java suites and compare the real profile endpoint before/after.

**Implementation evidence — 2026-09-08**

- Runtime permission validation now uses the immutable definition/action index published atomically by `CurrentRegistrySnapshot`. SuperAdmin permission sets and bulk role/preset validation read live feature controls in one projection query rather than one query per permission. User permissions and mutable feature controls are not cached across requests.
- Permissionizer caches package-annotation hits and misses per class-loader identity, using weak loader/annotation references and explicit cache reset; unexpected discovery failures still propagate.
- Regression coverage verifies removed actions/definitions, snapshot replacement and concurrent publication, fresh grant/runtime controls, uncached role revocation, bounded query work, concurrent package lookup, and class-loader isolation.
- Full verification: 835 backend tests and 12 Permissionizer tests, with zero failures/errors/skips. `git diff --check` passed.
- A separate local validation server returned the same 300 admin permissions as the old server. Side-by-side profile timings were 5,395 ms before versus 7.7 ms after; other warm fixed requests took about 4–5 ms. A bounded 200-request/10-concurrent smoke test had zero failures. These local results are not a production VPS throughput guarantee.
- The user approved committing the tested fix. The validation server was stopped without restarting the user's backend or discarding its in-memory development data.

---

## Permissionizer findings to verify against HiveApp integration

These were observed in the standalone Permissionizer source and must later be checked against how HiveApp uses it.

### PERM-001 — Package-level guarded nodes may not be intercepted

**Status:** `MITIGATED IN HIVEAPP — LIBRARY LIMITATION DEFERRED`

`PackageGuardInterceptionTest` proves that the resolver returns `shouldCheck=true` for a class under a guarded package while the current Spring `@Around` pointcut is never entered for its unannotated method. Batch 0.2 audited HiveApp and removed its reliance on this behavior: `com.hiveapp.platform` is structural-only (`guard = OFF`), while the 12 permission-bearing service classes explicitly use `guard = ON` and are matched by the current pointcut. Generic package-level interception remains a standalone Permissionizer limitation and must not be advertised as supported until implemented safely.

### PERM-002 — HiveApp bypasses startup guard-alignment verification

**Status:** `FIXED — 2026-07-16`

PermissionGuard verification previously caught and logged its own security exception, and HiveApp called `.skipVerification()` explicitly. Verification failures now propagate, with tests proving guarded definitions fail initialization without an interceptor and succeed after Spring interception registration. HiveApp no longer skips verification and makes interceptor creation an initialization dependency.

### PERM-003 — Overloaded methods share the processor's element key

**Status:** `FIXED — 2026-07-16`

The annotation processor previously identified methods using class plus method name without parameter types, so overloaded methods collapsed. Processor keys now include erased parameter signatures, and a compilation test proves overloaded methods produce distinct entries.

### PERM-004 — Reflection and collection failures are silently swallowed

**Status:** `FIXED — 2026-07-16`

`PermissionCollector` previously ignored indexed-root loading, index-reading, and reflection/invocation failures. These paths now throw `PermissionCollectionException`; focused tests prove a missing indexed root and a throwing permission method fail closed. An absent optional `descriptions()` method on an explicitly supplied external root remains allowed.

### PERM-005 — Policy order is security-significant

**Status:** `FIXED — 2026-07-16`

HiveApp configures: Admin → B2B → Plan → User Role → Spring authorities. Direct client access is correctly plan-gated before role evaluation. B2B intentionally short-circuits before client role evaluation, which creates the actor-level delegation gap recorded in `AUTHZ-002`. Keep order under explicit integration tests.

**Implementation evidence — 2026-07-16**

- `PermissionPolicyOrderTest` exercises the actual chain registered by `SecurityConfig`, rather than a separately reconstructed policy list.
- The test locks the exact execution order: Admin → B2B → Plan → User Role → Spring-authority fallback.
- Separate cases prove that an Admin grant, B2B denial, Plan denial, or User Role denial short-circuits every later policy as intended.
- The Spring-authority fallback is proven to run only after every domain policy abstains and cannot override a User Role denial.
- The existing B2B early-grant behavior is deliberately preserved and remains tracked as the separate actor-level authorization defect `AUTHZ-002`.
- The focused seven-test policy-order suite and complete 229-test backend suite pass with no failures, errors, or skips.

---

## Later review order

1. Registry definitions, validation, collection, and seeders.
2. Permission seeding and Permissionizer-to-feature mapping.
3. Repositories and database migrations for entity invariants.
4. Security context and ordered permission policies.
5. Account/workspace provisioning and identity flow.
6. Roles, members, overrides, and invitations.
7. Plans, subscriptions, quotas, and billing.
8. B2B collaboration.
9. API contracts and error handling.
10. Admin frontend behavior against verified backend capabilities.
11. Tests and cross-module architecture before implementing fixes.
