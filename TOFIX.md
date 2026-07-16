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

**Status:** `CONFIRMED — DESIGN DECIDED`

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

---

### RBAC-002 — Inactive client roles still grant permissions

**Status:** `CONFIRMED`

**Evidence**

`MemberRoleRepository.existsByMemberIdAndPermissionCode()` joins member assignments, roles, role permissions, and permissions, but does not require `Role.isActive=true`.

**Risk**

Deactivating a client role does not remove its authorization effect even though the entity and product language treat role activation as meaningful. Users may retain access that administrators believe was revoked.

**Required fix direction**

Filter inactive roles in the runtime authorization query and add request-level tests proving access stops immediately after role deactivation. Also decide whether inactive roles may be newly assigned.

---

### RBAC-003 — Client role assignment and direct grants have no actor permission ceiling

**Status:** `CONFIRMED`

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

---

### RBAC-004 — Removing a member role ignores company scope

**Status:** `CONFIRMED`

**Evidence**

- Role assignment accepts an optional `companyId`.
- The delete API accepts only member ID and role ID.
- `deleteByMemberIdAndRoleId()` deletes every assignment for that member/role pair regardless of company.

**Risk**

Removing a role intended for Company A can also remove the same role assignment from Company B or account-wide scope.

**Required resolution**

Make removal scope explicit, or deliberately define the operation as "remove this role from all scopes" and name/confirm it accordingly. Prefer precise assignment IDs or member+role+scope keys.

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

**Status:** `CONFIRMED`

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

---

### ORG-002 — Organization Groups must stay outside automatic authorization

**Status:** `DECIDED PRODUCT BOUNDARY`

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

---

### AUTHZ-006 — Shell authorization cannot target one managed entity or subgroup

**Status:** `CONFIRMED`

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

---

### RBAC-006 — Direct member overrides cannot support the decided exception lifecycle

**Status:** `CONFIRMED`

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

---

### SUBSCRIPTION-001 — Subscription lifecycle terminology is inconsistent

**Status:** `VERIFY`

**Evidence**

- `SubscriptionStatus` contains `ACTIVE`, `PAST_DUE`, `CANCELLED`, and `TRIALING`.
- Product documents refer to `EXPIRED`, but that value does not exist in the current enum.

**Risk**

Policies, UI filters, API contracts, tests, and documentation may handle terminal subscription states differently. An unknown or assumed state can accidentally grant or deny access.

**Required resolution**

Inspect plan policies, subscription services, migrations, tests, and frontend status handling. Either introduce a precisely defined expiration state or remove the stale terminology everywhere.

---

### SUBSCRIPTION-002 — Entitlement and override JSON is stored as untyped strings

**Status:** `VERIFY`

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

---

### PLAN-001 — Plan persistence constraints are weak at the entity level

**Status:** `VERIFY`

**Evidence**

- `Plan.price` has no declared precision/scale and is nullable.
- `Plan.billingCycle` is nullable.
- `Plan.isActive` lacks `nullable = false`.
- `PlanFeature.addOnPrice` has precision/scale, but valid negative/null semantics depend on service rules.

**Risk**

Invalid plan rows can break price calculation, catalog display, subscription snapshot creation, or billing-cycle assumptions.

**Verify later**

Review migrations, request validation, plan services, seeders, and database tests before changing the schema.

---

### PLAN-006 — Boolean plan state cannot implement the decided lifecycle

**Status:** `CONFIRMED`

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

---

### PLAN-007 — Active plan edits have no revision or subscriber-effect workflow

**Status:** `CONFIRMED`

**Evidence**

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

---

### BILLING-001 — Client self-service activates paid plans without payment or approval

**Status:** `CONFIRMED`

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

---

### QUOTA-002 — Client quota overrides can request unlimited capacity for free

**Status:** `CONFIRMED`

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

---

### SUBSCRIPTION-003 — Subscription periods and lifecycle transitions are not implemented

**Status:** `CONFIRMED`

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

---

### SUBSCRIPTION-004 — Trial subscriptions are authorized but invisible to client subscription flows

**Status:** `CONFIRMED`

**Evidence**

`PlanEntitlementService` and `QuotaEnforcer` fall back from ACTIVE to TRIALING, but `SubscriptionServiceImpl.getSubscription()` only loads ACTIVE. Catalog, preview, apply, and `/subscriptions/me` all depend on that ACTIVE-only method.

**Risk**

A trial account can use entitled APIs yet receive "subscription not found" when trying to view or manage the subscription.

**Required fix direction**

Create one authoritative "usable subscription" query/state rule and apply it consistently across authorization, quota, admin, and client flows. Define what trial users may change and how conversion works.

---

### SUBSCRIPTION-005 — Admin overrides can grant out-of-plan features with no defined price

**Status:** `DECISION`

**Evidence**

`updateOverrides()` validates that an added feature is code-defined, plan-assignable, active, and non-internal, but does not require it to exist in the account's plan composition. `PlanEntitlementService` treats the override as entitled. When no snapshot/current `PlanFeature` contains that feature, `BillingCalculator` finds no add-on price and adds zero.

**Risk**

An administrator can grant arbitrary sellable features for free with no reason, expiry, approval, contract price, or audit contract. This may be intentional for negotiated customers, but the commercial meaning is undefined.

**Required decision/fix direction**

Either restrict overrides to add-ons configured on the subscription's plan, or model negotiated exceptions explicitly with price, currency, reason, approver, effective dates, and audit history. Never infer zero price from missing configuration.

---

### SUBSCRIPTION-006 — Legacy subscriptions without snapshots receive optional add-ons automatically

**Status:** `CONFIRMED`

**Evidence**

When `entitlementSnapshot` is absent, `PlanEntitlementService.snapshotEntitles()` falls back to `findByPlanIdAndFeature_Code(...).isPresent()`. It does not check whether the `PlanFeature` is included (`addOnPrice == null`) or an unselected optional add-on (`addOnPrice != null`).

**Risk**

Any legacy/malformed subscription without a snapshot is entitled to every optional feature configured on its plan. Quota fallback can likewise read optional-feature limits.

**Required fix direction**

Migrate every usable subscription to a validated versioned snapshot. Until migration is complete, legacy fallback must distinguish included features from purchased add-ons and fail closed on ambiguous state.

---

### SUBSCRIPTION-007 — Downgrade safety is centralized, incomplete, and fails open for new modules

**Status:** `CONFIRMED`

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

---

### QUOTA-004 — Current arbitrary overrides cannot represent the decided quota-package model

**Status:** `CONFIRMED`

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

---

### QUOTA-003 — Quota conflict matching loses the owning feature

**Status:** `OBSERVED`

**Evidence**

`effectiveQuotaLimits()` produces `QuotaLimitEntry` values containing only `resource`, while `quotaOwner()` recovers a feature by returning the first snapshot feature with that resource name. Resource names are only unique inside a feature definition, not globally.

**Risk**

When two modules use the same quota resource name, downgrade preview can check usage for the wrong feature and approve or block incorrectly.

**Required fix direction**

Carry `(featureCode, resource)` as the quota identity through snapshots, effective-limit calculations, conflicts, billing, UI keys, and enforcement.

---

### PLAN-002 — FREE/default plan availability is not protected

**Status:** `CONFIRMED`

**Evidence**

- Workspace provisioning expects plan code `FREE`.
- Admin plan operations can deactivate FREE and can hard-delete it when it has no subscription history.
- `PlanSeeder` skips all seeding whenever any plan row exists, even if FREE is missing.
- Earlier provisioning review confirmed missing FREE can leave registration without a subscription.

**Risk**

An ordinary plan-management action or partial database state can break all new workspace entitlement provisioning.

**Required fix direction**

Make the default provisioning plan an explicit configuration/invariant. Prevent disabling/deleting it while referenced by provisioning, validate it at startup, and make workspace creation fail atomically if entitlement provisioning fails.

---

### PLAN-003 — Seeded plan composition diverges between fresh and existing installations

**Status:** `CONFIRMED`

**Evidence**

- On an empty database, `PlanSeeder` assigns every code definition marked `planAssignable` to FREE, PRO, and ENTERPRISE.
- On any non-empty plan table, it skips completely.
- `clientWorkspace()` automatically marks a feature plan-assignable.

**Risk**

A newly added HR/payroll/accounting feature is automatically included in every tier on a fresh installation, but included in no existing installation. Product composition becomes environment-dependent and a new client feature can accidentally become free.

**Required fix direction**

Separate development demo data from production catalog migrations. Plan composition must change through explicit, versioned product decisions with previews—not by "all client features" convention or database emptiness.

---

### PLAN-004 — Plan-feature uniqueness is enforced only by a race-prone pre-check

**Status:** `CONFIRMED`

**Evidence**

`assignFeature()` checks `findByPlanIdAndFeature_Code()` before insert, but `plan_features` has no unique constraint on `(plan_id, feature_id)`.

**Risk**

Concurrent admin requests can create duplicate feature rows, making entitlement, pricing, quotas, and picker behavior ambiguous.

**Required fix direction**

Add a database unique constraint, translate conflicts cleanly, and retain the application check only for friendly validation.

---

### PLAN-008 — PlanFeature commercial meaning and subscriber-removal boundaries are implicit

**Status:** `CONFIRMED`

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

---

### PLAN-009 — Plan deletion lacks the decided draft-only impact workflow

**Status:** `CONFIRMED`

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

---

### PLAN-010 — Plan duplication has no durable revision identity or code-reservation lifecycle

**Status:** `CONFIRMED`

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

---

### PLAN-011 — Admin subscriber management is a collection of single-record endpoints, not the decided operational flow

**Status:** `CONFIRMED`

**Evidence**

- The plan subscriber endpoint returns one unpaginated list and includes only `ACTIVE` and `TRIALING`; `PAST_DUE`, cancelled history, search, filters, and controlled owner-email lookup are absent.
- Plan detail exposes counts and sums `currentPrice`, but this is configured recurring price rather than proven collected revenue.
- Admin subscription lookup and mutation require a raw Account UUID. The API supports only get, create/replace one subscription, and overwrite its override payload.
- There is no backend preview token/version, selected/filtered bulk population, scheduled or renewal-time execution, per-Account job result, conflict handling, idempotent retry, cancellation cutoff, or correction operation.
- Account exceptions have no durable reason, actor, effective/expiry dates, approval/contract reference, grant-versus-restrict meaning, or explicit retain/remove/replace decision during a plan change.
- The status enum contains only `ACTIVE`, `PAST_DUE`, `CANCELLED`, and `TRIALING`; there are no distinct commands/transitions for cancel-at-period-end, immediate cancel, suspend, expire, or restore.

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

---

### PLAN-012 — Current per-feature add-on fields cannot represent the agreed commercial AddOn model

**Status:** `CONFIRMED`

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

---

### PLAN-005 — Purchased subscription terms are not a complete historical snapshot

**Status:** `CONFIRMED`

**Evidence**

The entitlement snapshot stores plan code, base price, selected features, add-on prices, and quota configuration. It does not store billing cycle, currency, tax/price version, effective dates, or source template version. The subscription still points to a mutable `Plan` for other display fields.

**Risk**

After plan edits, the system cannot reliably reconstruct the full terms a customer accepted or compare recurring values across monthly/yearly plans. Admin "recurring price" totals sum raw prices without cycle normalization.

**Required fix direction**

Snapshot the exact Plan/AddOn/quota-package versions, effective features/quotas, itemized Money prices with ISO currency, exact monthly/yearly cycle, effective period dates, adjustments, and source version/lineage. Preserve entitlement history separately from invoices, confirmed payments, refunds/credits, and provider/manual references. Never aggregate mixed currencies/cycles or label configured price as revenue.

---

### TIME-001 — Business timestamps mix `Instant` and `LocalDateTime`

**Status:** `VERIFY`

**Evidence**

- `BaseEntity` and invitation expiration use `Instant`.
- Collaboration timestamps and subscription period end use `LocalDateTime`.
- DTOs expose `LocalDateTime` directly for subscription period end in admin, client catalog, subscriber, and subscription responses, so clients receive no offset or zone information.

**Risk**

Time-zone conversion can make subscription expiration, collaboration activation, and scheduled operations ambiguous across deployments.

**Possible fix direction**

Use `Instant` for persisted system events and deadlines unless a field explicitly represents a local civil time with a stored zone.

---

### MODULES-001 — Cross-domain data exchange inside the monolith has no confirmed contract yet

**Status:** `DECISION`

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

**Status:** `VERIFY`

**Evidence**

- `Company` is stored under `platform.client.account.domain.entity`.
- The separate `platform.client.company` domain currently contains `Department` and company-facing services/APIs.

**Risk**

Future modules may depend on the wrong package or duplicate company concepts because ownership of the aggregate is unclear.

**Required resolution**

After reviewing services and repositories, decide whether Company belongs to the account aggregate, the company module, or a stable shared organizational kernel. Align package ownership without breaking tenant boundaries.

---

### AUDIT-001 — Security and billing changes lack a reviewed actor-aware audit model

**Status:** `VERIFY`

**Evidence**

- `BaseEntity` records only creation and modification timestamps.
- No entity reviewed so far records who changed roles, grants, plan composition, subscription overrides, or collaborations.

**Risk**

The company may be unable to explain who changed customer access, subscription pricing, or delegated permissions. This also makes destructive admin operations difficult to investigate.

**Verify later**

Search events, audit infrastructure, service logging, and database history before concluding that no audit mechanism exists.

---

## DTO and mapper findings

### DTO-001 — Request validation is inconsistent at important write boundaries

**Status:** `VERIFY`

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

---

### DTO-002 — Several response DTOs cannot represent important entity state

**Status:** `VERIFY`

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

---

### AUTHZ-DTO-001 — Effective client permission response has no explicit company context

**Status:** `CONFIRMED`

**Evidence**

- `MemberPermissionDto` contains only member ID, owner flag, and a flat set of permission strings.
- Client role assignments and overrides are company-scoped in the entities.
- `MeController` calls `EffectivePermissionService.getEffectivePermissions(userId, accountId)` with no company ID and returns that flat set.
- For non-owners, `EffectivePermissionService` loads every role assignment and every override for the member without filtering company, then unions them into one set.

**Risk**

If the response is calculated for one company but cached or reused by the UI for another, the UI can show actions the user cannot perform—or hide actions they can perform. Backend enforcement may remain correct while the client experience becomes misleading.

**Required fix direction**

Calculate permissions for an explicit account/company/B2B context and include that context in the response. If the UI needs an overview, return a scope breakdown rather than one flattened authorization set.

---

### DTO-003 — Multiple overlapping registry/catalog DTO families need explicit boundaries

**Status:** `VERIFY`

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

---

### DTO-004 — Client plan catalog quota model contains duplicated fields

**Status:** `VERIFY`

**Evidence**

`ClientPlanCatalogResponse.CatalogQuota` contains `resource` and `unit` while also embedding the full `QuotaSlot`, which itself contains resource, type, and unit.

**Risk**

The response can contain two conflicting values for the same resource/unit, expanding the frontend contract unnecessarily.

**Possible fix direction**

Expose one canonical quota shape plus current plan limit, price, and usage fields.

---

### MAPPER-001 — Mappers traverse lazy relationships

**Status:** `VERIFY`

**Evidence**

- `RoleMapper` walks role permissions and each related permission.
- `CollaborationMapper` dereferences both accounts and company.
- `MemberMapper` dereferences user.
- `RegistryMapper` recursively maps module features.

**Risk**

Without deliberate fetch queries and transaction boundaries, mapping can cause `LazyInitializationException` or N+1 database queries, especially on list endpoints.

**Verify later**

Inspect repository fetch strategies, service transaction scopes, generated SQL tests, and list endpoint pagination before changing mapper behavior.

---

### MAPPER-002 — Most API mapping appears to be manual and distributed

**Status:** `VERIFY`

**Evidence**

Only seven MapStruct mappers exist for 65 DTOs. Complex admin, plan, invitation, permission picker, and registry read models must therefore be assembled elsewhere.

This is now confirmed for admin users and roles: both controllers manually construct DTOs and directly call assignment repositories while mapping every returned entity.

**Risk**

Manual mapping is sometimes necessary, but repeated parsing/filtering logic in services can cause inconsistent security filtering and response semantics.

**Verify later**

Locate all DTO constructors in services/controllers. Keep business-aware read-model assembly explicit, but centralize repeated parsing and audience-filter rules.

---

## Service-contract findings

### SERVICE-001 — Identity service exposes its JPA entity as a cross-domain contract

**Status:** `VERIFY`

**Evidence**

`IdentityService.getUserById(UUID)` returns `Optional<User>` rather than an identity-owned read model or stable contract.

The only current external caller found is `AdminUserServiceImpl`, which uses this entity-returning contract when connecting an existing user to platform administration.

**Risk**

Other packages can become coupled to identity persistence fields and may accidentally mutate a managed entity. Future identity changes then ripple through the monolith.

**Verify later**

Find every `IdentityService` caller and determine whether callers need the complete entity or only identity facts such as ID, email, display name, and active state.

**Possible fix direction**

Expose a small immutable identity view for cross-domain consumers. Keep entity access internal to the identity package unless a transactional domain operation truly requires it.

---

### SERVICE-002 — Platform admin service contracts expose persistence entities

**Status:** `OBSERVED`

**Evidence**

- `AdminRoleService` returns `AdminRole` and lists of `AdminRole`.
- `AdminUserService` returns `AdminUser` and lists of `AdminUser`.
- `AdminSubscriptionService` returns `Subscription`.
- Controllers then query additional repositories to assemble response DTOs.

**Risk**

API behavior depends on lazy entity state and controller-side database access. Transactions, filtering, and response composition are split across layers, making list performance and authorization harder to reason about.

**Possible fix direction**

Keep the monolith, but have application services return complete DTO/read models for API use. Entity-returning methods can remain internal where truly useful.

---

### SERVICE-003 — Company and member API services also expose persistence entities

**Status:** `OBSERVED`

**Evidence**

`CompanyService` and `MemberService` return JPA entities/lists, and controllers perform DTO mapping after service calls.

**Risk**

This repeats the API/persistence coupling and lazy-loading dependency found in the admin and account areas.

**Possible fix direction**

When API contracts are stabilized, return purpose-built summary/detail read models from application services while keeping internal entity methods package-focused.

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

**Status:** `RESOLVED FOR CURRENT IN-MEMORY STAGE — 2026-07-16`

**Evidence**

`AuthServiceImpl.refresh()` validates the supplied JWT and extracts its user ID, but does not explicitly check that the token is a refresh token. It issues a new refresh token without storing, revoking, or marking the previous one as used.

`JwtTokenProvider` signs access and refresh tokens with the same key. Refresh tokens have no token-type claim, and `validateToken()` validates signature/expiry only. Therefore the refresh service accepts a valid access token as input.

**Risk**

An access token is accepted at the refresh endpoint. Stolen refresh tokens also remain reusable until expiration, and logout/password-change revocation is unavailable.

**Required fix direction**

Add explicit audience/token-use claims, require `refresh` use at refresh endpoints, preserve ADMIN versus CLIENT audience, and define rotation/reuse detection/revocation behavior.

**Implementation evidence — 2026-07-16**

- Every token now has explicit `CLIENT`/`ADMIN` audience and `ACCESS`/`REFRESH` use; refresh tokens also have a unique token ID.
- `TokenSessionService` registers refresh sessions, consumes each token atomically once, rotates it on refresh, rejects reuse and audience mismatch, and revokes it on logout.
- Both security filters require an audience-matching access token, so refresh tokens cannot authenticate API requests and access tokens cannot be exchanged at refresh endpoints.
- Current refresh-session state is intentionally process-local while the application is unpublished and uses disposable in-memory data. Restart invalidates all refresh sessions safely. A shared persistent session store is deployment hardening for future multi-instance production, not a current Flyway task.

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

**Status:** `CONFIRMED`

**Evidence**

The member list controller returns `MemberDto`, which lacks user email, assigned roles, company scopes, and override summary. Yet the same area exposes role assignment and override operations.

**Risk**

The client UI cannot render a trustworthy member-access management screen without additional undocumented queries or guessing.

**Required resolution**

Define separate member summary and member access-detail read models with safe user identity, active/owner status, scoped roles, and overrides.

---

### MEMBER-005 — Member deactivation does not implement the decided offboarding lifecycle

**Status:** `RESOLVED FOR THE CURRENT AVAILABLE LIFECYCLE — 2026-07-16`

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

**Status:** `CONFIRMED`

**Evidence**

- `RoleServiceImpl.deleteRole()` directly deletes the role after checking only that it is not a system role.
- `Role` cascades its `RolePermission` children but has no owned relationship to `MemberRole` assignments.
- No member-assignment cleanup, affected-count check, replacement flow, or deletion preview is present.

**Risk**

Depending on database foreign keys, deletion will either fail unexpectedly or rely on schema-level cascades that are invisible to the service. Users may lose access without impact visibility.

**Required resolution**

Implement the decided lifecycle. Permit hard deletion only for a never-assigned custom role with no history/reference. Used roles become inactive or archived, stop granting immediately, and retain assignments/audit. Add transactional impact checks plus previews of affected members, Account/Company scopes, lost permissions, and workflow ownership.

---

### ROLE-002 — System-role permissions can still be modified

**Status:** `CONFIRMED`

**Evidence**

Update and delete reject `role.isSystemRole=true`, but `addPermissionToRole()` and `removePermissionFromRole()` do not.

**Risk**

An actor can change the effective meaning of a supposedly protected system role while being unable to rename or delete it.

**Required resolution**

System/Platform templates are fully read-only to tenant role-management operations, including permission add/remove. Platform evolution uses immutable versions and explicit Account adoption/copy rather than in-place tenant mutation.

---

### ROLE-003 — Role activation state is incomplete and internally inconsistent

**Status:** `CONFIRMED`

**Evidence**

- `Role` contains `isActive`.
- No client role API/service action activates or deactivates roles.
- `RoleDto` does not expose active state.
- Runtime member-role permission query does not filter inactive roles.

**Risk**

The field cannot be managed or understood by clients, yet stale/inactive database values still grant access.

**Required resolution**

Implement `ACTIVE`, `INACTIVE`, and `ARCHIVED` behavior in the API/read models, assignment validation, runtime permission queries, impact UX, and audit. Inactive/archived roles grant nothing and cannot be newly assigned. Reactivation revalidates current version, entitlement, grantability, member, and scope rules.

---

### ROLE-004 — Role permission keys may not match the declared feature contract

**Status:** `VERIFY`

**Evidence**

`RoleServiceImpl.getRole()` declares permission key `read`, while list roles declares `view` and product documents refer primarily to `platform.rbac.view`.

**Risk**

The detail endpoint may generate an undeclared/unmapped permission, fail strict registry seeding, or require a permission no role picker can grant.

**Verify later**

Compare all annotated role keys with `WorkspaceRolesFeature`, generated Permissionizer output, permission seeding, and tests.

---

### ROLE-005 — Shared custom-role edits have no impact workflow

**Status:** `CONFIRMED`

**Evidence**

Current role permission add/remove operations mutate the shared role directly. They provide no affected-assignment/member/scope preview, no server-side impact recheck contract, no Duplicate-for-staged-rollout flow, and no reviewed before/after actor-aware audit.

**Required fix direction**

- For Account/Company custom roles, show the affected assignments, members, scopes, and permissions gained/lost before confirmation.
- Recheck authorization, scope, entitlement, delegation ceiling, target protection, and impact transactionally, then apply the edit immediately to every active assignment.
- Invalidate any permission cache/session state that could preserve the old result.
- Provide Duplicate as the explicit staged-rollout path; do not create hidden custom-role versions.
- Audit before/after permission sets, actor, template boundary, affected scopes, and result.
- Keep published Platform templates out of this mutation path; they use immutable versions and explicit adoption.

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

**Status:** `CONFIRMED`

**Evidence**

- `CollaborationRepository` defines a lookup by client, provider, company, and status.
- `initiateCollaboration()` never uses it and always inserts a new PENDING collaboration.
- No reviewed database uniqueness constraint prevents duplicate pending/active relationships.

**Risk**

The same two accounts and company can have several pending or active collaboration IDs with different permission grants. Runtime access is collaboration-ID-specific, so users and UI can operate on the wrong relationship.

**Required resolution**

Permit at most one live (`PENDING` or `ACTIVE`) collaboration for a client/provider/company tuple. Enforce it with a transaction-safe database strategy; retries return the existing relationship or an explicit conflict. A new request after rejection/revocation creates a new historical record rather than mutating the terminal one.

---

### COLLAB-002 — B2B delegation lacks an actor permission ceiling

**Status:** `CONFIRMED`

**Evidence**

Grant checks that a permission is B2B-delegatable and included in the provider account's plan, but not that the acting provider member has that permission.

**Risk**

A staff member with B2B grant-management permission can delegate provider actions they cannot personally perform.

**Required fix direction**

The provider owner may delegate within current provider entitlement and code-declared B2B eligibility. A non-owner additionally needs delegation-management permission and may delegate only permissions they effectively hold. Runtime must also require that the acting external member has a client-side Account-scoped B2B operator permission for the delegated action.

---

### COLLAB-003 — Collaboration operations do not revalidate active account/company state

**Status:** `CONFIRMED`

**Evidence**

Initiate, accept, and grant operations do not explicitly require the client account, provider account, or target company to be active. Existing collaborations also remain linked when a company is deactivated.

**Risk**

New or existing delegated access may be created/continued against a deactivated business scope unless another security layer consistently blocks it.

**Required fix direction**

Centralize current provider Account, external Account, target Company, active collaboration, current provider entitlement, current code B2B eligibility, exact persisted grant, and external-member operator checks. Any failed condition blocks runtime immediately while preserving relationship/grant history for audit.

---

### COLLAB-004 — `SUSPENDED` status has no management flow

**Status:** `CONFIRMED`

**Evidence**

`CollaborationStatus` contains `SUSPENDED`, but the service/API supports only initiate, accept, and revoke. There is no suspend, resume, reason, expiry, or actor model.

**Risk**

Database state can contain a status the UI cannot explain or manage, and future AI changes may assign inconsistent meaning to it.

**Required resolution**

Implement the decided lifecycle: either participant may permanently end the relationship; only the provider may suspend/resume delegated access. Suspension requires a reason, preserves but disables grants, and may have an explicitly configured review/automatic-resume time. Revocation freezes grants as non-reusable history. Add `REJECTED` separately from revocation.

---

### COLLAB-005 — Concurrent lifecycle actions can overwrite each other

**Status:** `CONFIRMED`

**Evidence**

Collaboration has no optimistic `@Version`, and accept/revoke use normal entity reads without a write lock. Duplicate permission checks are also check-then-insert.

**Risk**

Concurrent accept/revoke or duplicate grant requests can produce last-write-wins state, duplicate grants, or misleading success responses.

**Required fix direction**

Use optimistic or command-specific locking, database uniqueness for grants/live relationships, and explicit conflict responses. Make request/cancel/accept/reject/suspend/resume/revoke and grant/revoke-permission idempotent or return a clear already-applied/conflict result. Refresh UI state from the authoritative backend after every command.

---

### COLLAB-006 — UI cannot read the collaboration's currently granted permissions

**Status:** `CONFIRMED`

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

**Status:** `OBSERVED`

**Evidence**

`CollaborationService` returns `Collaboration`, `CollaborationPermission`, and lists of entities; controller mapping happens afterward.

**Risk**

This repeats lazy-loading and API/persistence coupling already found in other areas.

**Possible fix direction**

Return complete list/detail/current-grant read models from API-facing application operations.

---

### COLLAB-008 — B2B discovery and lifecycle APIs cannot implement the decided management flow

**Status:** `CONFIRMED`

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

**Status:** `OBSERVED`

**Evidence**

- `AdminRoleController.getAll()` loads all roles, then calls `findAllByAdminRoleId()` once per role.
- `AdminUserController.getAll()` loads all admin users, then calls `findAllByAdminUserId()` once per user.
- Mapping then dereferences lazy permission, role, and user relationships.

**Risk**

List cost grows linearly with extra queries and may depend on Open Session in View to avoid lazy-loading failures. This will become visible in the admin UI as records grow.

**Possible fix direction**

Build dedicated read queries/projections with the required relationships, assemble responses transactionally, and add query-count tests for list endpoints.

---

### ADMIN-DATA-002 — Admin users and roles are returned without pagination

**Status:** `VERIFY`

**Evidence**

Both list services call `findAll()` and controllers return complete lists.

**Risk**

Large installations will load and map every administrator/role and all related assignments in one request.

**Possible fix direction**

Introduce pagination/search before these collections can grow significantly, while keeping small bootstrap deployments simple.

---

## Shared infrastructure and API findings

### BILLING-002 — The only payment gateway bean always reports fake success

**Status:** `CONFIRMED`

**Evidence**

`DevPaymentGateway` is an unconditional `@Service`; every charge/refund returns `SUCCESS` with a generated DEV transaction ID. It is currently unused, and no environment condition prevents it from loading outside development.

**Risk**

Future AI-generated code may wire the existing `PaymentGateway` and appear to complete billing while charging nothing. A production deployment could silently activate paid entitlements against simulated success.

**Required fix direction**

Restrict the fake gateway to an explicit local/test profile. Production startup must fail when real collection is enabled without a configured provider. Never let calculated price or the dev gateway create a confirmed payment. Real/manual settlement must support idempotency, asynchronous confirmation, pending/success/failure/partial-refund/refund states, reconciliation, and durable provider/manual references before paid entitlement activation.

---

### BILLING-003 — HiveApp has price calculations but no decided Money, price-book, invoice, or payment ledger

**Status:** `CONFIRMED`

**Evidence**

- Plan, PlanFeature, current subscription price, and quota pricing use bare `BigDecimal` values without currency. No Money value type or same-currency validation exists.
- `BillingCycle` labels prices, but no period creation/renewal engine uses it; admin totals can sum monthly and yearly values as though comparable.
- Billing calculation produces one current number from base plan, feature add-ons, and quota overrides rather than an immutable versioned itemized Plan/AddOn/package price book.
- No reviewed Invoice, InvoiceLine, Payment, Refund/Credit, adjustment, provider event/reference, reconciliation, or idempotency model exists.
- Price preview, entitlement activation, amount due, settlement, and revenue are not represented as distinct facts.

**Risk**

The UI could display mixed-cycle/mixed-meaning totals as revenue, paid access could activate without money, price edits could rewrite accepted terms, retries could eventually duplicate charges, and support/accounting could not explain or reconcile what an Account owed or paid.

**Required fix direction**

- Introduce a Money type using ISO currency and safe decimal/minor-unit rules. One subscription uses one currency/cycle; reject incompatible Plan/AddOn/package/adjustment items and never perform implicit FX conversion.
- Build immutable exact monthly/yearly price-book versions and itemized calculations for Plan, AddOns, packages, adjustments, and later tax. Zero-priced recurring Plans are valid; perpetual commercial licensing is deferred.
- Separate preview from amount due/invoice, pending transaction, confirmed payment/manual settlement, and refund/credit. Only confirmed settlement counts as collected money/revenue.
- Add versioned invoices/lines and idempotent payment/refund records with provider/manual references and `PENDING`, `SUCCEEDED`, `FAILED`, `PARTIALLY_REFUNDED`, and `REFUNDED` behavior. Zero-price renewals create no fake payment.
- At renewal, apply the selected new price version for the new period. For immediate mid-period changes, initially support no automatic proration plus explicit audited operator adjustment/credit; defer automatic tax, discounts, metered charging, proration, FX, and automated refunds.
- Connect payment failure to `PAST_DUE`, configured grace, and eventual restricted/suspended access without data deletion. Reconciliation/webhook handling must be idempotent and authorization-safe.
- Store exact purchased terms in subscription history independently from financial records. Add currency mismatch, cycle mismatch, annual exact-price, zero-price, immutable version, itemization, pending-versus-paid, duplicate event, failed renewal/grace, manual settlement, adjustment, refund-state, and mixed-total reporting tests.

---

### EMAIL-001 — Missing SMTP silently becomes token logging and apparent delivery success

**Status:** `PARTIALLY RESOLVED — 2026-07-16`

**Evidence**

- The former invitation sender and secret-bearing fallback logging have been removed.
- `LoggingEmailServiceImpl` is now restricted to non-production profiles and records only destination/purpose/workspace/expiry, never the action URL or token.
- Production has no logging fallback; SMTP activation is selected explicitly by `spring.mail.host`, so a missing transport leaves the required `EmailService` dependency unsatisfied at startup.
- Credential emails are requested transactionally and sent after commit. A delivery failure leaves the member in a pending state that an authorized Account actor can regenerate, but persistent delivery status and automatic retry do not exist yet.

**Risk**

A delivery failure can still require a manager to regenerate access manually because persistent delivery status, retry scheduling, and UI feedback are not implemented.

**Required fix direction**

Complete `EMAIL-001` later with persistent delivery attempt/status, safe retry/resend, and Account-member UI feedback. Keep it independent from the already-correct post-commit identity transaction.

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

**Status:** `OBSERVED`

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

**Status:** `CONFIRMED`

**Evidence**

- B2B context selects an active member of the client account.
- `B2bCollaborationPolicy` runs before `PlanPolicy` and `UserRolePolicy`.
- Once the collaboration row contains the requested permission and the provider account is entitled, the B2B policy returns `GRANTED` immediately.
- It never checks whether the acting client member's roles/overrides allow that delegated action.

**Risk**

A low-privilege employee in the client workspace can exercise every permission delegated to the client account for that provider company.

**Required decision/fix direction**

Separate account-level delegation from actor-level use. A safe default requires both: the provider delegated the action to the client account, and the acting client member is authorized by a client-side role/assignment to use that delegated action. Define owner and B2B-operator exceptions explicitly.

---

### AUTHZ-003 — Existing B2B grants are not revalidated against current code delegation rules

**Status:** `CONFIRMED`

**Evidence**

Grant creation calls `PermissionGrantValidator.requireB2bDelegatable()`, but runtime `B2bCollaborationPolicy` checks only the persisted collaboration-permission row and provider plan entitlement. It does not verify that the action remains in the current definition's `b2bDelegatableActions`.

**Risk**

Removing B2B eligibility from code does not revoke or block an existing delegated permission. Stale/corrupt rows can grant actions that the current source says are not delegatable.

**Required fix direction**

Runtime must intersect persisted grants with the current code-owned B2B action allowlist. Registry synchronization must report and retire grants invalidated by code changes.

---

### AUTHZ-004 — Account-wide member overrides do not apply inside company context

**Status:** `CONFIRMED`

**Evidence**

- `UserRolePolicy` loads overrides with `findAllByMemberIdAndCompanyId(memberId, targetCompanyId)`.
- With a selected company, this excludes account-wide overrides whose company is null.
- The role query, by contrast, applies both exact-company and null/account-wide assignments.

**Risk**

An account-wide DENY can be bypassed by sending a company context, while an account-wide GRANT unexpectedly disappears. Override and role scope semantics conflict.

**Required decision/fix direction**

Define precedence among account-wide and company-specific decisions. Query both applicable scopes and resolve conflicts deterministically—normally a specific deny/decision rule documented and tested across all combinations.

---

### AUTHZ-005 — Tenant and B2B context headers are absent from CORS configuration

**Status:** `OBSERVED`

**Evidence**

Runtime context depends on `X-Company-ID` and `X-Is-B2B`, but `SecurityConfig` allows only standard authorization/content headers in CORS.

**Risk**

A browser frontend hosted on an allowed different origin cannot pass preflight for company-scoped or B2B requests. Development through a same-origin proxy may hide the failure.

**Required fix direction**

Add the exact context headers to environment-specific CORS configuration and test real browser preflight. Prefer an explicit selected-workspace/company contract rather than proliferating ad hoc headers.

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

**Status:** `CONFIRMED`

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

---

### REGISTRY-003 — A partial or empty Permissionizer collection is accepted as successful seeding

**Status:** `CONFIRMED`

**Evidence**

`PermissionSeeder` validates only entries that `PermissionCollector.collect()` returned. It does not require at least one action for every guarded feature, compare the collected action set with service methods, or reconcile missing database permissions. The standalone collector was already observed swallowing several discovery/class-loading failures.

**Risk**

Broken discovery can produce a deceptively successful startup with missing new permissions while old rows remain. The admin UI and authorization data may then disagree with the actual annotations.

**Required fix direction**

Make collection diagnostics explicit and startup-fatal in production when indexes/classes cannot be read, codes are ambiguous, guarded features unexpectedly lack actions, or the complete registry graph is invalid. Validate the whole discovered snapshot before writing. Add guarded-service/action-set integration tests and persist a structured synchronization report rather than relying only on logs.

---

### REGISTRY-004 — Current feature activation cannot represent the four decided operational controls

**Status:** `CONFIRMED`

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

---

### REGISTRY-005 — The two public catalog implementations disagree and one mutates JPA entities

**Status:** `OBSERVED`

**Evidence**

- `RegistryServiceImpl.getPublicCatalog()` returns raw `Module` entities and replaces each selected module's `features` collection inside a stream `peek()`.
- `PublicFeatureCatalogService` returns DTOs built from code definitions, but does not check `Module.isActive()`.
- The legacy implementation checks module activity; the DTO implementation does not.
- Removed code definitions can still appear in the raw database-driven catalog, while the DTO catalog excludes them.

**Risk**

The response depends on which endpoint a client uses. Mutating entity relationships merely to shape a response risks persistence side effects, immutable collection replacement, lazy-loading failures, and recursive/overshared serialization.

**Required fix direction**

Keep one versioned registry snapshot and explicit DTO read models per audience. Remove raw-entity API responses and query exact read models without mutating entities. Public catalog exposes only public-visible sellable items; other audiences apply their explicit operational, entitlement, and grantability rules. Apply module/feature operational state consistently and test that catalogs cannot disagree.

---

### REGISTRY-006 — Permission-picker construction scales as permission-by-permission entitlement checks

**Status:** `OBSERVED`

**Evidence**

`PermissionPickerCatalogService` loads every permission, then calls `planEntitlementService.isPermissionEntitled(accountId, permissionCode)` separately for each row before grouping the results.

**Risk**

Depending on the entitlement implementation, opening a role or B2B permission picker can cause a query chain per permission. This will worsen as HR/payroll/accounting add actions.

**Possible fix direction**

Resolve the account's effective entitled feature/action set once, then join/filter the current permission catalog in memory or in a purpose-built query. Add query-count and large-catalog tests.

---

### REGISTRY-007 — Existing feature rows are not fully repaired from their code definition

**Status:** `OBSERVED`

**Evidence**

For an existing feature, `FeatureSeeder` overwrites lifecycle status, quota schema, and sort order but does not restore its module relationship. `PermissionSeeder` detects a wrong module only when it processes a collected action for that feature.

**Risk**

Corrupt or migrated registry data may remain inconsistent, especially for a feature with no collected actions. Code ownership is only partial.

**Possible fix direction**

Synchronize all code-owned relationships and metadata deterministically, and verify the complete registry graph after seeding.

---

### REGISTRY-008 — Client-role grantability is feature-wide, including destructive and commercial actions

**Status:** `CONFIRMED`

**Evidence**

- `FeatureDefinition.clientWorkspace()` marks the whole feature client-role grantable.
- `PermissionGrantValidator` and `PermissionPickerCatalogService` then allow every persisted action owned by that feature.
- There is no client-role action allowlist/denylist equivalent to `b2bDelegatableActions`.
- Current examples include `platform.workspace.delete` and `platform.subscription.apply`, alongside ordinary read operations.

**Risk**

Routine custom-role configuration can delegate account deactivation or subscription-changing authority without an explicit owner/billing safety category. A feature-level flag is too coarse for actions with materially different impact.

**Required decision/fix direction**

Classify eligibility per action in HiveApp feature/registry definitions without changing the Permissionizer library. Explicitly support owner-only and client-role, platform-admin-role, and B2B eligibility combinations. Every audience catalog and grant validator must use the same current action classification, while services still enforce target/resource invariants and protected owner boundaries.

---

### REGISTRY-009 — Startup synchronization is split, non-atomic across registry layers, and not inspectable

**Status:** `CONFIRMED`

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

---

### REGISTRY-010 — Catalog and permission-picker contracts are neither uniformly audience-specific nor versioned

**Status:** `CONFIRMED`

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
