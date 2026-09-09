# HiveApp Product Flow and Decision Ledger

This file records product questions, operational strategies, side effects, and complete admin/client flows discovered during the source-first review.

It is intentionally separate from `TOFIX.md`:

- `TOFIX.md` records defects, inconsistencies, security risks, and technical debt.
- `FLOW_DECISIONS.md` records what the product should do, alternative strategies, consequences, and decisions that must be made before implementation.

Do not let an implementation shortcut silently become a product rule. A flow is complete only when its validation, effects, failure behavior, reversal strategy, authorization, audit, UI feedback, and tests are defined.

## Status meanings

- `OPEN`: product decision has not been made.
- `VERIFY SOURCE`: existing backend/frontend behavior must be inspected first.
- `PROPOSED`: a preferred strategy is documented but not approved.
- `DECIDED`: the behavior has been explicitly accepted.
- `IMPLEMENTED`: implemented and verified end to end.

## Architecture boundary

HiveApp is one organized monolithic application. HR, payroll, accounting, and future business areas remain folders/domains inside the same application. Their data exchange should be deliberate and understandable, but this ledger must not turn that requirement into microservices.

---

## Standard review questions for every admin feature

As each admin feature is scanned, answer all of these:

1. Who may see the feature?
2. Who may perform each action?
3. What information does the admin need before acting?
4. What are the prerequisites and validation rules?
5. What records and runtime behavior change?
6. What other features or flows are affected?
7. Is the action immediate, scheduled, or previewed first?
8. Can it be reversed? If yes, how? If no, why not?
9. What happens to historical data?
10. What happens to active users and in-flight requests?
11. What concurrency or duplicate-request behavior is required?
12. What audit record is required?
13. What warnings and affected counts must the UI show?
14. What happens when part of the operation fails?
15. Which backend and end-to-end tests prove the behavior?

---

## Admin capability audit

This table will be updated as the relevant source folders are reviewed.

| Admin area | Known purpose | Source review | Main open flow questions |
|---|---|---:|---|
| Admin authentication | Separate ADMIN access | Reviewed | Refresh, recovery, bootstrap, last-SuperAdmin safety |
| Admin users | Create platform operators and control their access | Reviewed | Deactivation effects, SuperAdmin protection, recovery from total operator lockout |
| Admin roles | Group platform-control permissions | Reviewed | Safe grant/revoke ceiling, deletion, inactive-role effects, audit |
| Platform features | Inspect and operationally control code-defined capabilities | Not fully reviewed | Which states are editable, activation effects, subscribed-user effects |
| Plans | Define sellable templates, included features, prices, and quotas | Operational catalogue, pricing, billing and analytics implemented through Phase 14 | Final cross-surface drill-down and consistency audit |
| Subscriptions | Manage purchased entitlement and reviewed population changes | One-Account changes, lifecycle/recovery, selected-Account jobs, billing and analytics implemented through Phase 14 | Filtered/Plan populations and final cross-surface audit |

---

# Tenant and membership decisions

## TENANT-FLOW-001 — One client workspace per user

**Status:** `DECIDED`

Decision accepted on 2026-07-14:

- `Account` is the HiveApp client workspace/tenant.
- A user identity may be an active member of only one client Account.
- Membership is employer-managed: it is created directly by an authorized account owner, manager, or member-management role.
- A member uses the application to perform assigned work; the member does not independently manage multiple personal workspaces.
- `Company` remains a business/legal operating scope inside the Account.
- B2B access never makes the user a member of the provider Account; the user remains a member of the client Account and uses explicitly delegated collaboration access.
- The backend must enforce this invariant instead of selecting an arbitrary first membership.

Implementation consequences:

- Add a database uniqueness rule preventing multiple client memberships for one user.
- Replace `findFirstByUserId()` with an unambiguous membership lookup.
- Client authentication/request context must require that single membership to be active and its Account active.
- Member creation must reject an identity already attached to another Account unless a future transfer flow explicitly permits it.
- Remove workspace-switcher assumptions from the client UI.

Still to decide separately: whether a person changing employers can transfer/reuse the same login identity or must receive a new identity.

---

## OPERATOR-IDENTITY-FLOW-001 — Platform operator identity and credentials

**Status:** `DECIDED`

A platform operator is created directly, with their own identity. Operators are never searched
for, selected from, promoted out of, or imported from the client user pool. The two carry
different trust levels, so they are different identities even when they are the same human — a
person who is both a HiveApp employee and a member of a customer's company holds two logins.

`users.kind` (`CLIENT` | `PLATFORM`) records the side. Member creation writes `CLIENT`; platform
administration writes `PLATFORM`. An `AdminUser` may only reference a `PLATFORM` identity.

One `users` table still backs both, deliberately. Email uniqueness is a database guarantee on
that single table; splitting it would downgrade that to a cross-table application check that can
race. The credential state machine lives there too and must not be duplicated.

**Initial access**

1. Creation requires an explicit initial-access method. **Email activation is the default and
   recommended choice** when the address is a real receiving mailbox. The alternative is a
   one-time-visible temporary password for a placeholder/non-receiving login address; choosing it
   sends no email and does not verify the address.
2. Only the selected credential is issued. An activation link and temporary password must never
   be minted together: two live credentials, one of which must travel out of band, is the pair
   that leaks.
3. The link opens the admin activation page, which sets the password and **issues no session**.
   The operator then signs in through the normal admin login. An emailed link must never by
   itself produce an authenticated admin session.
4. If the email never arrives, an authorized operator may resend it, or explicitly generate a
   temporary password. Resending issues a fresh token, so the previous link stops working.

Resend and temporary-access generation are separate permissions from creation, and separate from
each other: re-triggering delivery to the operator's own inbox is a lesser act than producing a
password that the acting administrator must then hand over.

Operator activation is a distinct endpoint from client activation rather than a branch inside it.
The client path requires an active workspace membership an operator does not have, and the two
trust models must not be able to drift into each other.

**Recovery**

An operator whose email is verified recovers themselves through the admin forgot-password flow.
Verification means exactly one thing: they followed a link sent to that address. Completing a
credential with a temporary password verifies nothing, because the address was never exercised —
an operator may hold a placeholder that receives no mail at all.

So the two populations recover differently, deliberately:

- **Verified email** — self-service reset by email.
- **Unverified or non-receiving email** (for example a `name@hiveapp.com` placeholder) — an
  authorized colleague generates a temporary password and hands it over directly. Temporary
  passwords are never emailed.

**Still open — secondary or changeable operator email.** An operator set up with a
non-receiving address can never reach self-service recovery, and today has no way to move to one.
Options not yet decided: allowing the operator's email to be changed (and re-verified), or adding
a separate recovery address distinct from the login identifier. Until decided, those operators
depend entirely on a colleague.

**Still open — total operator lockout.** Self-service recovery narrows this considerably, but if
every operator is locked out *and* unverified, the bootstrap seeder does nothing when its admin
already exists, so the only way back is direct database access. The bootstrap password itself
still works and is never rotated or expired, which is both the practical escape hatch today and a
standing risk: a permanent SuperAdmin credential living in deployment configuration.

---

## ACCOUNT-OWNER-FLOW-001 — Account owner authority

**Status:** `DECIDED`

The Account owner is the authority root of their own Account and can perform anything that any other member of that Account can perform.

- The owner automatically has every code-active permission currently available to the Account, across all Companies inside it, including baseline Account administration and security recovery.
- This authority is an owner invariant evaluated by Permissionizer, not a normal configurable role that depends on stored permission rows. Newly introduced Account permissions therefore apply to the owner without manually updating a role.
- Ownership itself is not a permission and never appears as a grantable role permission. No manager can grant, copy, revoke, or edit it.
- Subscription/feature availability still applies: ownership does not unlock a paid or disabled feature that the Account does not have. Baseline actions needed to manage the Account and its subscription remain available.
- Ownership does not grant platform-administrator authority, access to another Account, or provider-side B2B operations that were not delegated through an active collaboration.
- The owner may grant any currently entitled and code-grantable permission to others. Non-owners may grant only permissions they effectively hold and are allowed to delegate.
- Ordinary permission-management actions may be delegated to a manager. Even with that authority, the manager cannot target the owner's effective authority, assign ownership to another member, or manufacture an owner-equivalent role containing protected owner-only actions.
- A direct `DENY` override cannot remove owner authority. Restricting the owner requires a deliberate ownership-transfer or Account-security flow, not ordinary role editing.
- Owner status cannot be deleted, deactivated, or replaced accidentally. Ownership transfer is a separate audited operation and the Account must retain an active owner.
- Each Account has exactly one owner; co-owners are not supported. Managers receive broad authority through Permissionizer permissions without becoming additional owners.

**Ownership transfer boundaries**

- Transfer is allowed only to an active member of the same Account.
- Only the current owner can initiate a normal ownership transfer. Receiving ordinary role/permission-management authority never allows a manager to initiate or approve it as the owner.
- The current owner must explicitly confirm/re-authenticate the transfer; it cannot occur through ordinary member or role editing.
- The backend changes old and new ownership atomically under concurrency protection so the Account can never end with zero or multiple owners.
- Refresh/revoke affected authorization sessions so cached permissions do not preserve or delay owner authority.
- Record the initiating owner, previous owner, new owner, time, and result in audit history.
- Platform emergency recovery, if later supported, must be a separate highly restricted and audited operation.

**Permissionizer model**

- Separate ordinary Account permissions from code-declared owner-only actions.
- Permission pickers, role APIs, direct overrides, and delegation APIs must never return or accept owner-only actions as grantable entries.
- Permission management remains an ordinary delegatable capability, subject to the actor's delegation ceiling and protected-target rules.
- Authorization checks for owner-only actions require current ownership from the Account membership context; possessing a similarly named role or permission row is insufficient.

Still to decide: which ordinary roles/permissions the previous owner keeps after a successful transfer.

---

# Company management decisions

## COMPANY-FLOW-001 — Company lifecycle and deactivation

**Status:** `DECIDED — PURGE RETENTION/DELAY DETAILS LATER`

Current source only toggles `Company.isActive=false`. A complete product decision must define the effects.

The reviewed request-context code does not reject an inactive company, so direct company-scoped and B2B access can currently continue after deactivation.

### Deactivation and reactivation

- Deactivation is reversible suspension, not deletion.
- Immediately block operational reads/writes through ordinary Company context, new access delegation, Company-scoped roles and overrides, and B2B collaboration access.
- Preserve Groups, memberships, roles, overrides, collaborations, audit, and business-module records. Historical access is read-only and available only through a separately authorized Account-level flow.
- An inactive Company does not consume the plan's active-Company quota.
- Reactivation rechecks the current plan, active-Company quota, Account state, module entitlements, and validity of stored assignments/collaborations. It never restores old sessions or bypasses current policy.

### Permanent owner-initiated deletion

Permanent deletion is allowed even for a populated Company, but it is a separate Account-owner-only purge flow—not an ordinary Company edit or a delegated manager permission.

- Before confirmation, the backend returns a categorized impact inventory with counts and examples for Groups/memberships, roles/overrides, collaborations, files, audit, and every installed business module.
- The UI clearly distinguishes data that will be deleted, retained/anonymized for audit or legal reasons, and external/shared dependencies that block deletion or require resolution.
- Require fresh owner authentication, exact Company-name confirmation, and an explicit irreversible-action confirmation.
- Immediately suspend and remove the Company from ordinary UI/API operation, then schedule irreversible purge after a seven-day recovery window. Only the Account owner may cancel during that window.
- Data export/backup is a valuable later enhancement but is not required for the initial purge implementation and must not be presented as already available.
- The backend rechecks ownership, impact, dependencies, and retention constraints at execution time; a stale UI preview is never authority to delete.
- Never silently delete records owned by another Company/Account or data under a legal/contractual retention hold. Resolve, preserve, or anonymize those records according to their owning module's policy.
- Do not define deletion as indefinite hidden storage. Company-owned data disappears operationally immediately, while physical erasure or irreversible anonymization follows a documented data-category/jurisdiction retention policy.
- Records under a valid legal retention duty remain encrypted and inaccessible to ordinary Account users until the duty expires; they are then erased/anonymized according to policy. A hacked Account must not regain access to this retained archive.
- Large deletion runs execute as a tracked job: suspend the Company first, prevent new writes, record progress/failure, and retain a minimal Account/platform-level deletion receipt after Company data is gone.
- If any purge stage fails, keep the Company suspended, expose the exact stage/error to the owner/platform operator, and support safe retry. Never report a partially completed purge as successful.

Still to decide during implementation: future export format, module/jurisdiction-specific retention schedules, and the exact treatment of each shared cross-Company record type.

---

## COMPANY-FLOW-002 — Company identity and editing

**Status:** `DECIDED — MODULE-SPECIFIC IMPACTS ADDED LATER`

- Initial creation requires only a nonblank display name and country/legal jurisdiction. Legal name, tax/registration ID, industry, address, and logo may be completed later.
- One Company-edit permission covers display name, logo, industry, address, legal name, and tax ID. Do not create a second permission merely for legal fields.
- Inside that same authorized update flow, legal-name and tax-ID changes receive stronger validation, an explicit impact warning, and detailed before/after audit because downstream payroll/accounting/legal documents may depend on them.
- Normalize a supplied tax ID for comparison and warn when the same Account already has that value in the same country. Do not enforce global platform uniqueness or leak whether another Account uses it.
- Country may be edited while the Company has no country-dependent payroll, accounting, tax, or legal records. Once such records exist, ordinary editing is blocked and the UI directs the actor to a controlled jurisdiction-change process or new-Company flow.
- Every update remains tenant checked and field validated. Blank display name/country is rejected, and ordinary fields still receive appropriate length/format validation and audit.

---

# Organization-group decisions

## ORG-GROUP-FLOW-001 — Generic nested groups mirror the real organization

**Status:** `DECIDED — IMPLEMENTATION DETAILS LATER`

The platform does not hard-code different Department, Division, Team, or Subteam entities. It provides one generic Group building block inside a Company.

```text
Departments
  HR
    Recruitment Team

Divisions
  Operations
```

- A Group has a customer-chosen name and optional parent Group.
- Parent selection creates a visual folder/tree structure, but names and nesting carry no code-defined business meaning.
- Multiple root Groups are allowed.
- A default Group named `Departments` is created when a Company's organization structure is initialized, but it is deletable like other Groups.
- The customer may create roots such as `Divisions`, children such as `HR`, and deeper children such as `X Team` without code changes.
- Parent cycles and cross-Company parent references are forbidden.
- Sibling names must be unique under the same parent using normalized/case-insensitive comparison. Root Groups are siblings of each other.
- A Group can be deleted only after it has no child Groups and no member memberships.
- The management flow must show what prevents deletion and help the administrator move/remove those contents first; deletion must never silently delete members or a child structure.
- A Group may be renamed when its new normalized name is unique among its siblings. Renaming changes no membership, role, permission, or template.
- A Group and its complete subtree may be moved under another Group in the same Company. Reject cycles and destination sibling-name conflicts; preserve all memberships and positions.
- Sibling Groups have an administrator-controlled display order. Reordering is presentational and has no authorization or business meaning.
- A Group cannot be moved across Companies. Its member-free structure may instead be copied through a template into another eligible Company.

**Group membership and positions**

- Members remain Account-owned identities; Groups only mirror organizational placement inside a Company.
- A member may belong to multiple Groups.
- Each Group membership carries its own customer-entered position/title, so one member may be `HR Manager` in HR and `Interviewer` in Recruitment Team.
- Any member of the same Account may be placed in a Company's Groups even when that member has no application role or login access for that Company. Placement never grants that access.
- The position/title is optional free text. The UI may offer suggestions, but the shell does not impose a coded position catalog or attach behavior to a title.
- The shell does not require or infer a primary Group/position. It shows all explicit placements; a future HR module may separately model an official job assignment.
- Membership is explicit at each Group. Membership in a child Group does not automatically create direct membership in its ancestors; organization views may deliberately include descendants.
- Removing a Group membership removes only that one placement and its position. It never deactivates the member or changes Account/Company roles, direct exceptions, other Group memberships, or operational access.
- Group membership, position, and nesting do not grant or remove application permissions.
- Operational access remains separate through Account/Company roles, direct exceptions, and B2B delegation.

**Structure templates**

- Customers may save/reuse templates containing an entire Group structure.
- HiveApp may publish Platform structure templates, an Account may own templates reusable across its Companies, and a Company may own local templates.
- Templates describe folders/nesting and optional position definitions, never real member assignments, credentials, roles, or permissions.
- Instantiating a template creates an independent Group structure. Later edits to the source template never silently mutate structures already created from it.
- Before creating from a template, the backend previews and validates every resulting sibling name.
- Instantiation is atomic: if any generated Group conflicts with an existing sibling, no partial structure is created.
- Example: a template whose root is `Departments` cannot be instantiated unchanged when a root `Departments` already exists.

Conflict UX—block, rename during preview, or copy with a generated suffix—and template lifecycle are deferred to the implementation phase.

**Current source gap**

The existing `Department` entity is too specific and incomplete: it has Company, parent Department, name, description, and one manager, but no generic Group membership/position model, APIs, services, templates, or complete lifecycle. It likely needs replacement or migration rather than expansion as a special Department authorization concept.

---

## ORG-GROUP-FLOW-002 — Groups have no automatic authorization effect

**Status:** `DECIDED`

- Groups never own permissions, roles, features, or security authority.
- Adding/removing/moving a member or Group never changes that member's own effective permissions.
- Group names such as `Manager`, `HR`, or `Administrators` have no special code meaning.
- An actor may need an Account/Company permission to edit organization data, but Group membership itself grants nothing.
- Any future manager-target feature that references a Group must be a separate explicit assignment with visible impact; it must never be inferred silently from Group name, position, nesting, or membership.

---

# Manager-target decisions

## MANAGEMENT-FLOW-001 — Target-aware management is required; target model is deferred

**Status:** `REQUIREMENT DECIDED — BUILDING BLOCK DEFERRED TO FIX PHASE`

For any operation targeting another member or that member's business record, the shell eventually needs to combine:

```text
actor holds the action permission
+ actor is explicitly allowed to manage this target member/entity
+ delegation ceiling permits the requested access change, when permissions are being managed
```

The exact target building block is intentionally not finalized yet. Later implementation may support one or more explicit target forms:

- one selected member;
- an explicit reusable management set;
- a separate manager-to-member relationship;
- a Group-target assignment, where the assignment—not Group membership alone—creates management coverage;
- Company-wide authority for selected administrative roles.

Groups remain organization folders and never grant authority automatically. If a future management assignment targets a Group, moving members may change management coverage and therefore requires explicit impact preview/audit; it still does not change the moved member's own roles or permissions.

Business modules define their own target-aware actions such as review, approve, assign, or correct. There is no universal manager bypass. Access management also enforces no self-escalation, owner/protected-target rules, entitlement, grantability, and delegation ceiling.

**Current shell gap confirmed from source**

- Permissionizer can theoretically evaluate a supplied target context, but the automatic interceptor does not pass service arguments.
- HiveApp context contains Account/Company/B2B information only, not target member/entity or a management target set.
- Current policies verify the actor's permission but cannot restrict it to one entity or subgroup.

Likely fix-phase direction: keep annotations as the coarse action gate, then resolve the target in HiveApp and apply a target-aware policy before reads/writes. List/search/count/export queries must filter by the same target rule. Permissionizer itself probably remains generic unless implementation proves otherwise.

---

# Member and access-management decisions

## MEMBER-FLOW-001 — Adding a person to a workspace

**Status:** `DECIDED — SUPERSEDED BY DIRECT CREATION`

The earlier invitation proposal is rejected. Authorized Account actors create members directly. User identity, membership, optional organizational Group memberships/positions, initial Account/Company-scoped roles, quota checks, and credential activation are handled atomically according to `MEMBER-CREATE-FLOW-001` and `MEMBER-CREATE-FLOW-002` below.

---

## MEMBER-FLOW-002 — Member deactivation and reactivation

**Status:** `DECIDED — DOWNSTREAM MODULE EFFECTS ADDED LATER`

Deactivation is reversible suspension of Account membership, not deletion of the person's history.

- Immediately block login, revoke access/refresh sessions, and make every Account permission ineffective.
- Invalidate unused activation links, temporary passwords, and password-reset material. Do not expose or change the member's private permanent password.
- Preserve membership identity, Group memberships and positions, roles, overrides, audit, and business-record references for history and possible reactivation.
- Inactive members do not consume the active-member subscription quota.
- Never hard-delete a member with login, audit, approval, or business activity. A mistaken never-activated member with no dependent history may be eligible for a separate impact-checked hard-delete flow.
- The Account owner cannot be deactivated. Ownership must first be transferred through the protected ownership flow.
- Ordinary members cannot deactivate or remove their own membership. Membership is employer-managed, so deactivation/reactivation requires a separately authorized Account actor acting within scope.
- Reactivation does not resurrect old sessions. Revalidate Account status, quota, current plan entitlement, role status, scope validity, and credential state before restoring access.
- Roles/overrides may remain stored, but only currently active, entitled, and valid assignments regain effect after reactivation.
- Record actor, reason, time, target member, session revocation, and result in audit history.

When HR, payroll, accounting, approvals, or other business modules are designed, add explicit impact preview and reassignment rules for records owned by the departing member. Do not silently reassign or delete business history.

---

## MEMBER-FLOW-003 — Scoped role assignment

**Status:** `DECIDED — INITIAL TEMPLATE CATALOG OPEN`

Recommended model: a role is a named permission template; a separate assignment applies that template to a member at an explicit scope.

**Role-template hierarchy**

1. **Platform templates:** global templates created by HiveApp code or an authorized platform administrator. They are available to eligible Accounts and may be assigned at Account or Company scope.
2. **Account templates:** created by the Account owner or an actor with Account-scoped role-management authority. They are reusable anywhere inside that Account.
3. **Company templates:** created by an actor with role-management authority for one Company. They are reusable only in that Company.

Ownership remains protected Account status and is never represented by any template.

Higher-level authority may administer templates below it inside the same tenant boundary. Lower-level managers cannot edit, shadow, replace, or broaden higher-level templates.

**Three scopes must remain distinct**

1. **Creator authority scope:** where the actor is allowed to administer roles.
2. **Template availability boundary:** the hierarchy level that owns the template and the lower scopes where it may be used: Platform, Account, or Company.
3. **Assignment effect scope:** where this particular member receives the role's actions: Account or Company.

The assignment effect must fit inside both the template boundary and the assigning actor's authority.

**Multiple assignments**

- One member may hold multiple role assignments at different Account and Company scopes.
- The same role template may be assigned to the same member more than once when each assignment has a different valid scope.
- Exact duplicates of member + template/version + scope type + scope target are rejected or returned idempotently.
- Effective permissions are calculated for the current target scope. Permissions from Company A must never appear while operating in Company B merely because the same member holds both assignments.
- Applicable role permissions are combined only within the requested scope, then applicable direct `DENY` exceptions win over role/direct grants.
- Access-detail UX groups assignments by scope and can show the effective result for a selected Account/Company context.

Example:

```text
Template: Company Manager
Permissions: member.read, member.update, access.assign

Assignment 1: Ahmed -> Company A
Assignment 2: Sara  -> Company B
```

The same Account-level template is reused, but Ahmed receives no authority in Company B and Sara receives none in Company A.

A Company-scoped role manager may create a template reusable only inside that Company. Group placement never affects template availability or assignment scope.

**Custom-role safety rules**

- A custom role may contain only current, code-grantable, subscription-entitled permissions that its creator effectively holds and may delegate.
- It cannot contain ownership or owner-only actions.
- A narrower actor cannot create an Account/Company-wide role or broaden an existing template boundary.
- Role managers cannot increase their own access through role creation/assignment.
- The backend validates scope containment and target protection on every create, edit, assign, and remove operation.
- Role edits show affected assignment/member counts because changing a shared template changes every active assignment immediately.
- Account and Company custom-role edits apply immediately to every active assignment after an authorized actor confirms the impact preview. They do not create implicit versions.
- For staged rollout, duplicate the role, edit the copy, and deliberately reassign selected members/scopes.
- Duplicate same-member, same-role, same-scope assignments are rejected or returned idempotently.
- Assignment removal targets one exact assignment/scope, never every similarly named assignment accidentally.

Still to confirm: whether HiveApp should ship built-in roles beyond protected ownership, and which minimal roles belong in that initial catalog.

**Platform-template publishing and adoption**

- Platform templates are immutable/versioned once published. Editing creates a new version; it never silently mutates existing customer access.
- An Account may keep its current version, explicitly adopt a newer version, or copy a Platform template into an independent Account custom template when customization is needed.
- Adoption is an explicit authorized action and applies atomically only after current entitlement, grantability, and scope validation.
- Retiring a Platform version removes it from new selection but does not erase existing assignments or silently change customer assignments.

**Required role UX**

Role safety must be understandable in the UI, not only enforced by backend rules.

- Every template clearly shows its origin (`Platform`, `Account`, or `Company`), version, boundary, editability, status, and where it may be assigned.
- Assignment UI separates the selected template from the assignment's effect scope and states whether that scope is Account or Company.
- A Platform update screen compares permission additions/removals, unavailable or newly entitled features, description changes, and old/new versions.
- Before adoption or any shared-role edit, preview affected assignments, members, Companies, and permissions gained/lost.
- Offer explicit actions such as keep current version, adopt new version, or copy/customize; do not hide an automatic version change behind Save.
- Explain blocked permissions and scope violations in business language while keeping the backend authoritative.
- Record template creation, publication, copy, adoption, edit, status change, assignment, and removal in audit history.

---

## MEMBER-FLOW-004 — Direct permission overrides

**Status:** `DECIDED`

Roles are the normal access-management method. Direct member overrides exist only as visible, controlled exceptions.

**Direct `GRANT`**

- Used for exceptional temporary access rather than replacing a reusable role.
- Requires a reason and expiry time.
- Must be code-grantable, currently entitled, effectively held/delegatable by the actor, and assigned at an equal or narrower scope than the actor's authority.

**Direct `DENY`**

- Removes one permission otherwise inherited from roles within the applicable scope.
- Requires a reason; expiry is optional so a deliberate long-term separation-of-duty restriction can remain.
- Wins over every applicable role or direct grant for that permission/scope.

**Common rules**

- Replace the unexplained boolean with an explicit `GRANT`/`DENY` decision.
- Every exception has an Account or Company scope consistent with the rest of authorization.
- Owners cannot be denied Account authority through an override, and owner-only actions can never be directly granted.
- Non-owners cannot create exceptions for themselves, exceed their delegation/scope ceiling, bypass plan entitlement, or modify protected broader-authority targets.
- Expiry is enforced during authorization even if background cleanup has not run.
- Deactivated members, Accounts, Companies, features, or permissions do not regain access through an override.
- Create, edit, revoke, expire, and failed attempts record actor, target, permission, decision, scope, reason, expiry, and result.

**Required effective-access UX**

- Member access detail shows permissions grouped by source: role/template assignments, direct grants, and direct denies.
- Every exception displays scope, reason, creator, creation time, expiry/status, and whether it currently has effect.
- Preview the effective change before saving, including permissions gained/lost and affected target scope.
- Expired or blocked exceptions remain inspectable in history but are clearly separated from active access.
- Permission pickers exclude owner-only/non-grantable entries and explain entitlement or delegation restrictions.

---

## MEMBER-FLOW-005 — Member quota semantics

**Status:** `DECIDED`

Member quota counts every non-deactivated membership:

- Newly created and awaiting first activation: counts immediately.
- Activated member: counts.
- Account owner: counts as a member.
- Deactivated historical member: does not count.
- A mistaken never-used membership that qualifies for hard deletion stops counting after deletion.

There is no invitation/pending-invitation reservation because the invitation subsystem is removed. Quota is consumed when direct member creation commits, not when the member first logs in.

The backend must use an active/non-deactivated database count and enforce check-plus-create atomically under concurrency. The UI shows current usage, limit, remaining seats, and the fact that unactivated created members consume seats before confirmation.

---

## ROLE-FLOW-001 — Role lifecycle

**Status:** `DECIDED`

Roles/templates have an explicit lifecycle:

- `ACTIVE`: available for permitted new assignments and grants current effective permissions.
- `INACTIVE`: temporarily disabled, unavailable for new assignment, and grants nothing immediately. Existing assignments remain stored for history/restoration.
- `ARCHIVED`: retired from normal management/selection and grants nothing. Historical assignments and audit remain visible from history/impact views.

Reactivation restores only assignments that still pass current template version, subscription entitlement, code grantability, member status, and scope validity. Old sessions/cached permission results must not bypass a lifecycle change.

Platform/system templates are read-only to customers. The Platform retires/version-controls them through the publishing flow rather than tenant deletion.

---

## ROLE-FLOW-002 — Role deletion

**Status:** `DECIDED`

- Hard deletion is allowed only for an unused custom role/template that has never been assigned and has no audit/business reference.
- Once a custom role has assignment/history, use `INACTIVE` for temporary shutdown or `ARCHIVED` for retirement; never hard-delete its historical identity.
- Deactivation/archive stops its permissions immediately while preserving assignments and history.
- The backend performs a transactional impact recheck; the UI preview is informative but not the security boundary.

Before any lifecycle or destructive action, show:

- Assigned member count.
- Account and Company scopes affected.
- Permissions that users will lose.
- Whether workflows/approvals may become unowned.
- Whether the role is inherited from Platform or a higher template boundary and therefore not editable/deletable here.

Platform/system templates are never hard-deleted by customers. A Platform version can be retired from new use while existing references remain inspectable.

---

## ROLE-FLOW-003 — Permission grant/revoke flow

**Status:** `DECIDED`

1. Open role detail with current scope/status.
2. Load only code-grantable and subscription-entitled permissions.
3. Mark permissions the acting user may delegate.
4. Preview affected assignments, members, scopes, and permissions gained/lost for every addition/removal.
5. Enforce actor delegation ceiling on the backend.
6. Confirm that custom-role edits apply to all current assignments; offer Duplicate instead when the actor wants a staged rollout.
7. Save atomically with duplicate protection and a fresh server-side impact recheck.
8. Runtime permission effect changes immediately and invalidates any permission cache/session state that could preserve the old result.
9. Record before/after permissions, actor, template, boundary, affected scopes, action, and result in audit.

Platform templates do not follow this in-place edit behavior after publication; they use immutable versions and explicit Account adoption.

---

# Member creation and credential activation decisions

## MEMBER-CREATE-FLOW-001 — Remove workspace invitations

**Status:** `DECIDED`

The invitation subsystem is not part of the intended product and will be removed.

- Members are created directly inside the authenticated Account by an authorized account owner, manager, or member-management role.
- Creation supplies the member's identity/profile, company scope, initial roles, and required employment data.
- The backend creates the `User` identity and `Member` relationship atomically.
- Member quota, one-Account-per-user, active account/company, role scope, plan entitlement, and actor delegation ceiling are checked before commit.
- No pending invitation, acceptance, revoke, resend, invitation-token, or magic-login workflow remains.
- Remove invitation entities/repositories/services/controllers/DTOs/events, registry feature/permissions, plan composition, tests, documentation, and future UI entries.

Credential activation remains a separate security flow. The system must never email, log, or display the member's chosen reusable password. The approved activation method is defined below.

## MEMBER-CREATE-FLOW-002 — Initial access credentials

**Status:** `DECIDED`

Every member receives a unique username. Email is optional and, when present, may also be used as a login identifier.

Initial access depends on whether email is available:

1. **Email available:** send a one-time activation link that lets the member choose their password.
2. **No email:** generate a strong temporary password and show it once to the authorized manager, who gives it to the member. On successful login, the member is forced to choose a private password.

Employee number is optional and unique inside the Account. Using it to log in requires the Account code plus employee number so numbers may safely repeat across Accounts.

**Required security behavior**

- Persist only password hashes; never store or log any plaintext credential.
- The temporary password is visible only once at creation/reset and cannot be retrieved later.
- The temporary password has no time-based expiry. It remains valid until its first successful use or until an authorized Account actor regenerates it.
- First successful use consumes the temporary password and produces only the restricted password-change session. If that session is lost before completion, an authorized Account actor must regenerate access.
- Regeneration immediately invalidates the previous unused temporary password and shows the replacement once.
- Mark the identity as `passwordChangeRequired` until the member chooses a permanent password.
- While that marker is active, issue only a restricted credential/session that can access password-change and logout operations; do not allow normal HiveApp operations.
- Activation links and temporary credentials are one-use in effect: completing activation/password change revokes them and invalidates earlier sessions.
- Rate-limit failed attempts and lock or suspend initial access after repeated failures.
- Audit who created or reset access, when it occurred, and whether activation completed, but never record the secret.
- Managers may trigger an access reset but may never view the current password.
- Resetting access for an activated member immediately revokes all of that member's existing sessions before issuing new temporary access. Editing the member's profile, employment data, roles, or scope does not reset credentials or sessions unless the operator explicitly chooses a security action that requires it.
- A manager must never authenticate using the member's credentials or cause manager activity to be recorded as if the member performed it. Any future act-on-behalf feature must preserve both actor and subject identities in the audit trail.

**Account management placement**

- Initial-access management is an action on a member inside that member's Account; it is not a global or standalone password-management page.
- The Account's authorized administrators/managers may see the member's activation state and actions such as generate/regenerate temporary access or reset access.
- The Account owner has these capabilities automatically. Other administrators/managers receive them only through Account-scoped Permissionizer permissions.
- They may manage only the temporary access lifecycle. Once the member has chosen a permanent password, it is private and no longer manageable or visible; a later reset creates a new temporary credential instead.
- The UI may show status, generation/reset time, expiry policy, and the actor who performed the action, but never the stored secret.
- Permissionizer permissions must protect each create, regenerate, reset, unlock, and disable action within the current Account.

**Password recovery**

- If the member has a verified email, they may request a one-time password-reset link and choose a new password themselves.
- If the member has no email, there is no self-service recovery channel. An authorized Account administrator/manager resets access from that member's Account page and receives a new one-time-visible temporary password to give the member.
- The owner receives all member-access actions automatically. Non-owner access is controlled by separate Account-scoped Permissionizer actions for member creation, initial-access regeneration, activated-member reset, unlock, and disable.
- Exact permission codes and default manager-role composition are implementation work; they must follow these product boundaries and the non-owner delegation ceiling.

---

# B2B collaboration decisions

## B2B-FLOW-001 — Finding and requesting the correct provider company

**Status:** `IMPLEMENTED — 2026-08-10`

In this flow the **provider Account** owns the Company being shared; the **external client Account** receives delegated access to work in it.

- A provider Company creates a share code/link instead of being exposed through broad global Company search. Exact verified business identifiers may be added later.
- The provider controls whether the Company accepts incoming requests and may disable/regenerate its code. Regeneration invalidates the old code without changing existing collaborations.
- A Company share code is reusable and has no implicit expiry. It remains valid until the provider disables or regenerates it; only its SHA-256 hash is stored.
- The request shows both Account identities, target Company, purpose/message, and optional requested capabilities. Requested capabilities are non-binding; the provider chooses actual grants after acceptance.
- An authorized external Account actor may request or cancel a pending request. An authorized provider actor may accept or reject it. These are Account-scoped Permissionizer actions.
- At most one pending, active, or suspended collaboration exists for the same external Account/provider Account/Company tuple.
- Request creation has an explicit three-way retry contract after purpose whitespace is trimmed/collapsed and requested capabilities are compared as an unordered set:
  - any call that creates a new relationship record returns it with `201 Created`, whether it is the tuple's first record or follows terminal history;
  - an identical retry of a live relationship returns the existing record with `200 OK`;
  - the same live tuple with different normalized purpose or capabilities returns `409 Conflict`;
- If identical concurrent requests race at the database constraint, the winning creator returns `201 Created` and the losing insert re-reads and returns the winner with `200 OK`; it must not surface a persistence error.

---

## B2B-FLOW-002 — Collaboration lifecycle

**Status:** `CORE LIFECYCLE AND AUDIT IMPLEMENTED — COMMUNICATIONS OPEN`

Possible states need precise transitions and effects:

```text
PENDING ──accept──> ACTIVE <──resume── SUSPENDED
   │  │               │                    ▲
cancel reject      revoke              suspend
   ▼  ▼               ▼                    │
CANCELLED REJECTED  REVOKED ───────────────┘ (no transition back)
```

- `CANCELLED` means the requester withdrew a pending request, `REJECTED` means the provider refused it, and `REVOKED` means a previously accepted relationship ended.
- Either participant may permanently revoke/end the relationship. Only the provider may suspend/resume delegated access because it owns the shared Company; the external Account controls its own workers through its roles.
- Suspension requires a reason and may have an explicit scheduled review or resume time. Automatic resume occurs only when explicitly chosen during suspension.
- Rejection/revocation preserve immutable history. A later request creates a new collaboration record and never silently restores old permissions.
- Suspension preserves configured grants but disables runtime use. Revocation freezes them as historical facts that cannot be reused.
- Audit every request, cancellation, acceptance, rejection, suspension, resumption, revocation, and permission change. Important changes create in-app notifications; other channels use the reusable communication capability when configured.

Runtime delegated access must require an ACTIVE collaboration.

---

## B2B-FLOW-003 — Permission delegation

**Status:** `PARTIALLY IMPLEMENTED — SAFE ACCOUNT-WIDE OPERATOR CEILING; COLLABORATION SCOPE OPEN`

1. Provider opens collaboration detail.
2. UI shows currently granted permissions and separately shows eligible additions.
3. Backend filters by code-declared B2B grantability and provider plan entitlement.
4. Owner may delegate within entitlement; non-owner may delegate only permissions they effectively hold.
5. Changes apply immediately to runtime access.
6. Backend records actor, collaboration, permission, action, and result.
7. Client/provider receive clear visibility according to product policy.

Do not expose a grant/revoke UI until current grants can be read reliably.

Two authorization layers are required at use time:

1. The provider account delegated the action for this active collaboration/company.
2. The acting person in the client account has a client-side B2B/operator role allowing them to use that delegation.

Both layers are enforced. For the client-side operator layer, HiveApp currently evaluates the actor through the external Account's existing Account-scoped role/direct-exception resolver, with no foreign provider Company inserted into that scope. The external Account owner is allowed by the protected owner rule; ordinary members need an effective Account-scoped grant, and an applicable Account-scoped deny still wins. Organization Groups remain unrelated to authorization.

This is a safe ceiling but an incomplete operator-management model. The provider's exact collaboration grant still bounds B2B access: it does not expose an undelegated action, Company, or collaboration. However, selecting an ordinary operator requires granting the same permission internally at Account scope, which also authorizes that action over the external Account's own Companies and makes it usable in every collaboration that separately delegates the same action. The open refinement is a Collaboration assignment effect scope that reuses role templates, assignments, and delegation ceilings without creating a parallel B2B role system.

The provider owner may delegate any currently entitled, code-declared B2B action. A non-owner additionally needs delegation-management permission and may delegate only actions they effectively hold. The external Account owner or authorized role manager separately decides which external members may use available B2B delegation.

---

## B2B-FLOW-004 — Entitlement and company lifecycle effects

**Status:** `IMPLEMENTED — 2026-08-10`

Define behavior when:

- Provider loses the feature from subscription.
- Target company is deactivated.
- Provider workspace is deactivated.
- Client workspace is deactivated.
- Permission becomes deprecated/non-delegatable in code.
- Collaboration is suspended or revoked.

Decided safety rule: every use requires active provider and external Accounts, active target Company, active collaboration, current provider plan entitlement, an active code-defined B2B-delegatable action, its exact persisted grant, and the acting external member's B2B operator permission. Runtime access stops immediately when any condition is absent. Preserve grant/history records for audit rather than silently deleting them.

Suspension disables configured grants without deleting them. Revocation freezes them as non-reusable history. If a feature/action becomes inactive, deprecated, non-delegatable, or unentitled, runtime stops immediately while the relationship and former grant remain explainable in history.

Current source verifies active collaboration, both active Accounts, active Company, provider entitlement, current code-declared B2B eligibility, the exact active persisted grant, exact Company scope, and the external member's Account-scoped operator authority. A stored grant that loses code eligibility remains visible as inactive history rather than remaining usable or being silently deleted.

---

## B2B-FLOW-005 — Concurrent and duplicate operations

**Status:** `IMPLEMENTED — 2026-08-10`

- At most one live collaboration per client/provider/company.
- Granting the same permission is idempotent or returns a clear conflict.
- Accept versus revoke/suspend uses version/locking conflict detection.
- Retried commands cannot duplicate grants or change a terminal state unexpectedly.
- UI refreshes from backend state after every command.
- Treat `PENDING`, `ACTIVE`, and `SUSPENDED` as the one live relationship slot for a client/provider/company tuple. Suspension retains grants and can resume, so releasing its slot could let a newer relationship collide with it when resumed. A new request after cancellation/rejection/revocation creates new history rather than mutating the terminal record.
- Make request, cancel, accept, reject, suspend, resume, revoke, grant, and permission revoke idempotent or return an explicit already-applied/conflict result.

---

# Plan management decision pack

Plans are a major product area, not basic CRUD. A plan is a sellable template; a subscription is an account's effective purchased state. Those two concepts must remain separate in backend behavior and admin UI.

Current entity-level facts already observed:

- `Plan` has code, name, description, price, billing cycle, and active flag.
- `PlanFeature` links a plan to a code-owned feature and stores add-on/quota pricing configuration.
- `Subscription` points to a plan but also stores an entitlement snapshot, overrides, status, current price, and period end.
- Successful registration currently expects a FREE plan.

The Plan/Product catalogues and one-Account subscription workbench have been implemented and independently audited through Phases 9 and 10. Phase 12.1 now also delivers reviewed selected-Account change jobs with durable partial results, scheduling, cancellation, retry, and privacy-separated identity reads. Filtered/Plan-subscriber populations, trial/lifecycle commands, settlement ledgers, reconciliation, and durable analytics remain their numbered later phases.

## Plan administration capability map

This review must define both what exists now and the complete admin product to build.

**Delivered operational foundation**

- Create a blank Plan or explicit duplicate, compose Feature-owned quotas, revise published products, compare revisions, and manage lifecycle/availability/visibility with backend-derived actions and blockers.
- Manage independently versioned AddOns, capacity packages, and immutable monthly/yearly Price-book entries rather than mutable price columns.
- Inspect bounded product catalogues, Plan subscribers, warnings, impact/history, current and historical subscription terms, and narrow Account/owner lookup surfaces.
- Review and apply one-Account immediate or at-renewal subscription changes using exact prices, typed product selections, usage conflicts, policy-adjusted terms, required reason, and short-lived signed evidence.
- Preserve exact accepted snapshots and provenance; later catalogue or policy changes do not rewrite them.
- Direct admin subscription create/trial/raw-override mutations are retired. Registration-time internal FREE provisioning is the sole bootstrap exception.

**Admin product capabilities that must be decided and built**

1. Plan creation, duplication/revision, validation, lifecycle, default-plan replacement, archive, and safe deletion.
2. Metadata, pricing, billing-cycle, included-feature, optional-add-on, dependency, and quota editing.
3. An explicit effect target for each commercial change: future subscriptions only, selected Accounts immediately, change at renewal, or a scheduled bulk plan change.
4. Backend impact preview using current subscriber snapshots and feature-owned usage contributors: affected Accounts, access lost/gained, current usage above proposed limits, data/workflows at risk, price/revenue change, and remediation choices.
5. Subscriber operations: grandfathering, per-Account negotiated overrides, opt-in upgrade, grace periods, immediate/renewal/scheduled change to another plan, cancellation/non-renewal, and exception handling.
6. Change scheduling, customer notification state, cancellation before effect, execution progress, idempotent retry, partial-failure handling, and audit/history.
7. Emergency safety controls that temporarily throttle/disable a costly or unsafe operation without pretending a commercial subscriber plan change occurred.

Every following PLAN flow must state the current backend behavior, the intended admin capabilities, the customer/subscriber effect, required UI preview, and implementation gaps.

### Operations, not hard-coded commercial strategies

HiveApp provides composable, reusable platform operations; HiveApp operators, product owners, and marketing/business decision-makers choose how and when to combine them.

- Available effect timing must include future subscriptions only, one/selected/all Accounts immediately, each Account's next renewal, or a scheduled date.
- Available outcomes must include keep current terms, change to another plan, stop renewal/end after the paid period, temporary grace, Account exception, and restricted/manual-review state.
- Communication is a reusable platform capability: select audience, message/template/channel, timing, delivery status, and retry. A commercial strategy may attach it to a plan operation rather than requiring new code for each campaign.
- Immediate direct replacement is intentionally available after impact preview and confirmation; the platform does not hard-code that renewal-time change is always the correct business choice.
- These capabilities remain organized services/folders inside the monolith, not separate microservices.
- Configurable strategy never bypasses platform invariants: authorization, tenant isolation, valid plan/feature composition, usage/data safety, billing/contract/legal constraints, concurrency protection, and audit.

Product documentation and UI use **change subscribers to another plan** rather than the ambiguous word **migrate**. Internal engineering may still use migration for database/schema transformations.

## PLAN-FLOW-001 — Plan lifecycle model

**Status:** `DECIDED`

### Question

Is `isActive` enough, or does plan management require explicit draft, active, inactive, and archived states?

### Decided model

- `DRAFT`: editable and not selectable by clients.
- `ACTIVE`: selectable for new subscriptions.
- `INACTIVE`: temporarily unavailable for new selection; existing snapshots continue.
- `ARCHIVED`: historical and read-only; retained for reporting and subscription history.

- Activation validates a non-empty coherent feature composition, quota definitions, price, currency/billing-cycle rules, and default-plan invariants. An invalid or unfinished draft cannot activate.
- Making a plan inactive blocks new subscriptions but never rewrites or disables existing subscription snapshots.
- An inactive plan may return to active only after current validation passes.
- Archived is terminal and read-only. It cannot be restored; an administrator duplicates it into a new draft with a new identity/code when reuse is needed.
- Existing subscribers of an inactive/archived template continue from their saved snapshots until an explicit plan change, renewal change, cancellation, or entitlement-removal policy acts on them.
- The configured provisioning/default plan—currently `FREE`—cannot be deactivated, archived, or hard-deleted until another valid active plan atomically replaces it as the default.
- Every lifecycle transition requires authority, backend impact preview/recheck where subscribers/default provisioning are affected, and actor-aware audit.

---

## PLAN-FLOW-002 — Plan deletion strategy

**Status:** `DECIDED`

### Core question

When should an admin be allowed to delete a plan, and what happens to related data?

### Strategy options

1. **Never hard delete:** archive every plan. Safest history, but leaves test/mistake records forever.
2. **Hard delete unused; archive used:** delete only plans with no subscription history; archive anything ever purchased. Balanced and recommended.
3. **Soft delete everything:** keep a deleted flag. Simple history, but semantics overlap with inactive/archive and queries become error-prone.

### Decided direction

Use **hard delete unused; archive used**.

Hard delete is allowed only when all are true:

- The plan is still `DRAFT` and has never been active/used.
- No active or trialing subscriptions.
- No cancelled, past-due, historical, or otherwise retained subscriptions.
- No pending subscription or scheduled plan-change references.
- No invoices/payment records or external provider references when billing is added.
- The plan is not required for workspace provisioning, such as the current FREE plan.
- The backend can safely remove its `PlanFeature` configuration.
- Deletion removes only records owned by this draft, such as its feature modes, exclusions, quota configuration, and lineage link. It never deletes code-registry features/modules, another plan, Account data, or subscription data.

If any historical business relationship exists, deletion is refused and archive is offered instead.

### Required admin deletion flow

1. Admin opens Plan Detail → Danger Zone.
2. UI requests a backend-generated impact preview.
3. Backend returns:
   - Whether hard delete is allowed.
   - Blocking reasons.
   - Current subscriber count.
   - Historical subscription count.
   - Plan-feature and quota configuration counts.
   - Pending scheduled plan-change/external reference counts when those exist.
   - Recommended alternative: deactivate or archive.
4. UI explains the exact effect; it does not invent effects itself.
5. Admin confirms using the immutable plan code or similarly deliberate confirmation.
6. Backend rechecks all conditions inside the command transaction to prevent races.
7. Backend performs deletion or rejects because conditions changed.
8. An actor-aware audit record is written.
9. UI refreshes the list and displays the recorded result.

### Effects by action

| Action | New purchases | Existing subscriptions | History | Template editing |
|---|---|---|---|---|
| Deactivate | Blocked | Continue from snapshots | Preserved | Usually allowed with warnings |
| Archive | Blocked | Continue until explicitly changed/cancelled | Preserved | Read-only or tightly restricted |
| Hard delete | Impossible if any subscription history exists | None may exist | No business history to preserve | Plan and owned configuration removed |

### Required tests

- Delete an unused draft succeeds.
- Delete removes only owned plan configuration.
- Delete FREE/default provisioning plan is rejected.
- Delete with active subscription is rejected.
- Delete with only cancelled/history subscription is rejected.
- Concurrent subscription creation versus delete cannot produce an orphan.
- Non-authorized admin cannot preview or delete.
- Audit identifies actor, plan, time, reason, and outcome.

---

## PLAN-FLOW-003 — Plan code and identity

**Status:** `DECIDED`

### Decided rules

- Plan code is immutable after creation.
- Display name and description are editable.
- Code format, length, reserved values, and uniqueness are validated consistently.
- External integrations use plan ID/code according to one documented contract.
- Renaming a plan never changes existing snapshot identity.
- Code input is normalized and validated consistently before uniqueness checks; exact allowed characters/length are implementation details.
- Codes belonging to any activated, archived, or otherwise used plan remain permanently reserved even after the plan stops being offered.
- A code from a hard-deleted, never-active, unused draft may be reused because no customer/business identity ever depended on it.
- Reserved/default codes such as the configured provisioning plan receive additional protection.

---

## PLAN-FLOW-004 — Creating and duplicating plans

**Status:** `DECIDED — PRICING FIELDS FOLLOW LATER PRICING MODEL`

### Required creation flow

Creation should be guided rather than one large form:

1. Basics: code, name, description, price, billing cycle.
2. Starting point: empty, duplicate existing, or inherit a recommended base such as FREE.
3. Feature composition: included versus optional add-ons.
4. Quotas: included limits, unlimited values, and bump pricing.
5. Review: show exactly what a customer receives and pays.
6. Save as draft.
7. Activate only after validation passes.

### Duplicate/revision rules

- The actor supplies a new unique code and name before duplication/revision is created.
- The result always starts as `DRAFT`, regardless of the source plan's lifecycle.
- Copy only Plan-owned commercial configuration: description as a starting value, billing/price fields, feature modes (including optional-add-on slots), explicit plan blocks, dependency-satisfying composition, and base quota configuration. Pricing-related fields remain editable draft values and follow the later finalized pricing model.
- Do not copy incoming AddOn or quota-package attachments. Their `allowedPlanCodes` belong to those separate commercial items; copying them would silently mutate existing items as a side effect of creating a Plan. Operators attach compatible extensions to the new draft explicitly after reviewing it.
- Never copy subscribers, subscription/payment/audit history, scheduled subscriber operations, Account-specific overrides/exceptions, or customer communication history.
- Store source plan/revision identity and creation reason/type so admins can see where the draft came from. This lineage never makes later source edits mutate the copy.

---

## PLAN-FLOW-005 — Editing a template versus changing customers

**Status:** `DECIDED`

- An `ACTIVE` plan's commercial configuration—price, billing cycle, included features, add-ons, and quotas—is immutable. Changing it creates a new `DRAFT` revision/plan identity rather than mutating what was sold.
- Harmless display metadata such as plan name and description may update directly without a revision, with ordinary audit.
- A revision copies plan configuration, including price/billing cycle, feature composition, add-on pricing, and quota configuration. It never copies subscribers, subscription/audit history, or Account-specific negotiated overrides.
- Publishing a valid revision makes it available for future subscriptions. Affecting existing customers is a separate explicit operation with one selected effect target: one/selected/all Accounts immediately, each Account's next renewal, or a scheduled previewed bulk plan change.
- Existing subscriptions remain pinned to their stored entitlement/price snapshots until that explicit operation creates a new snapshot/state for them.
- Every subscriber-affecting operation requires backend-generated impact preview and execution-time recheck: affected Accounts, access gained/lost, price change, current usage above proposed quotas, dependent data/workflows, remediation/grace needs, and notification state.

### Renewal-management decisions

- New-sale availability and existing renewal are independent controls. Making Plan X inactive stops new selection only; existing subscriptions keep renewing unless a separate renewal policy changes them.
- A plan-wide renewal policy supports continue current terms, change to a selected replacement plan, end after the current paid period, or hold for manual review.
- Ending without replacement places the Account into an explicit restricted/read-only subscription state. It does not silently assign `FREE`, remove data, or leave authorization undefined.
- Selected Accounts may receive a reasoned, expiring exception to the plan-wide renewal policy. The exception and its actor are visible and audited.
- Scheduled renewal actions require affected-Account/usage preview, notification tooling, cancellation before cutoff, grace or temporary override for approved conflicts, execution progress, idempotent retry, per-Account result, and audit.
- The same reusable change engine supports an authorized immediate plan replacement when operators deliberately choose it; renewal is not the only allowed effective time.

---

## PLAN-FLOW-006 — Feature composition and add-ons

**Status:** `DECIDED FOUNDATION — ADVANCED PRICING LATER`

### Confirmed current behavior

- `addOnPrice = null` means included; non-null means optional and included only when selected.
- Existing subscription snapshots keep their feature composition when a template changes.
- Application code rejects duplicate assignment before insert, but the database does not enforce uniqueness.
- Every `clientWorkspace` feature is automatically plan-assignable; fresh demo seeding puts all of them in every tier.
- Existing installations are not synchronized when new features appear.

### Decided composition model

- If a code-owned feature has no PlanFeature entry in a plan, it is unavailable in that plan: neither included nor purchasable as an add-on.
- A stored PlanFeature has an explicit commercial mode: `INCLUDED` or `OPTIONAL_ADD_ON`. Do not infer the mode from whether add-on price is null.
- An administrator may also explicitly mark a feature `BLOCKED_FOR_PLAN`. This is a plan-availability exclusion, not an entitlement or price: inheritance, duplication, or adding a module bundle cannot insert that feature until an authorized edit deliberately removes the block.
- An included feature is part of the base plan snapshot. An optional add-on is available for deliberate selection/purchase and must have valid pricing/selection rules.
- A feature may appear at most once in a plan, enforced by both friendly service validation and a database uniqueness constraint.
- Only code-registry features explicitly declared active, client-facing, plan-assignable, and commercially sellable may be configured. Platform-control, internal, or admin-only features are forbidden.
- Feature dependencies declared by code must be satisfied. The backend rejects invalid composition; the UI explains missing dependencies and may offer to add them.
- Removing a feature while preparing a new draft/revision changes future offers only. Existing subscription snapshots retain it.
- Removing/revoking the feature from current subscribers is supported, but only through a separate explicit subscriber-entitlement operation with its own target, effective time, impact preview, confirmation, communication, per-Account result, and audit. Normal plan editing never performs it implicitly.
- If a code-owned feature becomes internal, deprecated, inactive, or non-sellable, new plan composition stops using it immediately; existing subscriber treatment requires that same explicit retirement/removal operation rather than silent snapshot mutation.

### Current-subscriber feature-removal operation

- Target one Account, selected Accounts, or all current subscribers that hold the feature.
- Choose immediate, next-renewal, or scheduled effective time.
- Every sellable feature must contribute its own usage/impact information, including active users, owned records, workflows, integrations, dependencies, and available remediation.
- Removing entitlement disables use/access according to the feature's declared restricted-state behavior but preserves feature data. Permanent feature-data deletion is a separate explicit purge operation with its own authority, impact, retention, and confirmation rules.
- Support grace periods and reasoned Account exceptions, plus tracked progress, idempotent retry, per-Account result, communication, and audit.
- Re-adding the entitlement restores access to preserved data after current entitlement/dependency validation. Data separately purged through the distinct purge flow cannot be restored by re-adding access.

### Commercial plan/add-on/quota foundation

- A code-owned FeatureDefinition has no universal customer price.
- A Plan is the base commercial item: base price, included features/whole modules, and included quotas.
- An AddOn is a platform-admin-created commercial item. It may contain a complete technical module or a custom selection of sellable features, plus included quotas, its own price, and the plans for which it is allowed. Customers therefore see one add-on concept whether the product is marketed as a module or a smaller bundle.
- A zero-price/FREE Plan may combine with paid add-ons. Effective features are the union of the Plan and selected compatible AddOns.
- Every Plan/AddOn declares the included quota for capabilities it supplies and which extra quota choices it allows. Initial customer-selectable modes are fixed/no increase and predefined priced packages; negotiated changes remain operator-only. Per-unit and explicitly priced unlimited options are deferred.
- A purchase is `Plan + selected AddOns + selected quota packages`. Recurring configured price is the sum of those selected commercial items under the later currency/billing rules.
- Reject duplicate paid capability and overlapping included-quota definitions in the first implementation. If a Plan already includes a feature/module, the customer cannot buy the same capability again; a separately declared capacity package may still increase its quota.
- Multiple AddOns require compatible dependencies, exclusions, and quota ownership. Backend preview explains incompatibility before purchase.
- The immutable subscription snapshot records the Plan, AddOn versions, selected packages, effective features, effective quotas, item prices, currency, billing cycle, and effective dates.
- `DRAFT` is the only editable AddOn definition state. Publishing is permanent: `ACTIVE` and `INACTIVE` mean respectively on sale and sales paused for a published immutable revision, while `ARCHIVED` is terminal and explicitly irreversible.
- Changing a published AddOn creates the next copied `DRAFT` revision in the same lineage. Publishing that draft atomically pauses any older active revision for new sales; existing subscription snapshots remain unchanged. A never-published draft is deleted rather than archived.
- Add/change/remove AddOn operations support immediate or renewal-time execution using the same authorization, impact preview, conflict handling, pending-operation, data-preservation, history, communication, and audit rules as Plan changes.
- Advanced discounts, proration/refunds, per-unit charging, unlimited pricing, tax, and exact billing precedence remain for `PLAN-FLOW-007` and `PLAN-FLOW-008`.
---

## PLAN-FLOW-007 — Quota management

**Status:** `DECIDED FOUNDATION — PER-UNIT/UNLIMITED PRICING LATER`

### Confirmed current behavior

- `null` means unlimited and zero is a real limit.
- A plan quota with `pricePerUnit = null` is described as non-bumpable.
- The client API nevertheless accepts finite or unlimited overrides for it.
- Unlimited overrides bypass conflict checks and runtime enforcement and calculate no extra charge.
- Runtime usage is currently implemented only for selected platform resources; unknown future resources report zero.

### Required concepts

- Code defines quota resource/type/unit.
- Admin defines the plan's limit and optional bump price.
- Subscription snapshot captures the purchased values.
- Runtime usage and billing must resolve the same effective quota.

### Decided quota model

- Code owns each quota's stable feature-qualified identity, type/unit, measurement definition, and feature-owned usage contributor. Platform admins configure commercial limits/options only for code-defined measurable quotas.
- Persist quota identity as `featureCode + resource`; never match only the resource name. Initial implementation permits one owning feature per quota. Explicit cross-feature shared quotas may be designed later.
- Do not use null as a commercial value: a finite number is an explicit limit, zero is a valid limit that permits no new consumption, `UNLIMITED` is an explicit mode, and missing/null configuration is invalid/absent rather than silently unlimited.
- Initial customer-facing modes are fixed/non-increasable and predefined priced packages. Platform admins configure package capacity, price, whether it is repeatable, and maximum purchases. Operator-only negotiated Account exceptions remain supported. Automatic per-unit charging and customer-selectable unlimited pricing are deferred.
- Effective capacity is the Plan/AddOn included limit plus purchased packages, followed by any explicit Account grant/restriction exception. Exceptions require source, reason, actor, effective time, expiry/permanent status, and later commercial treatment.
- A Plan and AddOn cannot both own the same included quota. A separately declared capacity package may increase an already entitled capability; overlapping included definitions are rejected.
- Every feature defines what counts as active/current usage, including inactive/archived/retained-data treatment. The central service aggregates contributors and must fail closed when usage is unknown.
- Lowering capacity below usage never deletes data. Existing data remains available under the feature's restricted/over-limit behavior, new consumption is blocked, excess is shown, and the Account must remediate, use grace, or receive an exception. Immediate and renewal-time changes remain available through explicit impact handling.
- Quota allocation/consumption is concurrency-safe so simultaneous requests cannot both consume the final slot.
- Quota packages are versioned commercial items and the subscription snapshot stores selected package versions, included/purchased/exception capacity, effective limit, price, currency/cycle, and dates.
- UI/API distinguish included capacity, purchased packages, Account exceptions, current usage, remaining/excess amount, and pending changes.

### Required safety

Preview every quota reduction against feature-owned current usage. Do not silently delete data or pretend unknown usage is zero; require an explicit immediate/renewal strategy with remediation, grace, exception, or restricted over-limit behavior.

---

## PLAN-FLOW-008 — Pricing and billing-cycle changes

**Status:** `DECIDED FOUNDATION — PRICE BOOKS AND COMMERCIAL OFFERS REOPENED 2026-08-26`

### Confirmed current behavior

- Price is only calculated and stored; no money is collected or approved.
- No currency, tax, invoice, proration, refund, or renewal workflow exists.
- Billing cycle does not create a period end; current subscriptions therefore do not expire.
- Raw monthly and yearly prices would be summed together in the current admin recurring-price total.

### Decided pricing and billing foundation

- Every monetary value uses a Money type with ISO currency plus safe decimal/minor-unit representation and currency-aware rounding; never floating point or a number without currency.
- One subscription has one currency and billing cycle. Its Plan, AddOns, quota packages, and adjustments must provide compatible prices. Currency conversion is not performed implicitly.
- Initially support exact `MONTHLY` and `YEARLY` price books. A yearly price is independently configured, not monthly price multiplied by twelve. A zero-price Plan uses the same recurring period model and may combine with paid AddOns/packages. Defer perpetual/`FOREVER` commercial licenses.
- Preserve itemized calculation: Plan + AddOns + quota packages + explicit adjustments + later tax = amount due. A final total without source lines is insufficient.
- Active commercial prices are immutable versions. New purchases use the new version; existing subscription snapshots retain their price until an explicit immediate/renewal customer-change operation selects new terms.
- At-renewal changes use the new complete price from the next period. Immediate mid-period changes initially support no automatic proration plus authorized operator-entered adjustment/credit. Automatic proration/refunds come later.
- Immediate paid entitlement still requires real payment/contract confirmation or authorized operator approval; a price preview never proves settlement.
- Keep price preview, invoice/amount due, confirmed payment, and refund/credit as distinct records/states. Only confirmed payment/manual settlement counts as collected money/revenue.
- Payment states include at least `PENDING`, `SUCCEEDED`, `FAILED`, `PARTIALLY_REFUNDED`, and `REFUNDED`, with provider/manual references and idempotency so retries cannot charge twice.
- Failed renewal moves to `PAST_DUE`; configured grace may continue access, then declared restricted/suspended behavior applies without deleting data. Zero-priced renewal requires no fake payment and renews after current eligibility validation.
- The V1 safety baseline persists the grace deadline and derives it from the platform renewal-grace setting (72 hours by default). A later per-Account extension is a separate reasoned, expiring, audited exception; changing Plan or Price rows never rewrites an already persisted deadline.
- Percentage/fixed discounts and offer codes are now in scope only through the typed, versioned, auditable Offer/Commercial Policy model in `MARKETING-FLOW-002`; they are never mutable price fields or arbitrary checkout inputs. Automatic tax, metered usage charging, automatic proration, foreign-exchange conversion, and automated refunds remain deferred. Keep extension points/item types without presenting those capabilities as working.
- Authorized manual adjustments require reason, actor, before/after calculation, and audit. Marking an amount paid requires a distinct confirmed manual settlement or provider event.

### Rule until real billing exists

Do not label internally calculated prices as completed payment or revenue. UI/APIs distinguish configured price, preview, amount due/invoice, pending transaction, confirmed settlement, and refund/credit.

---

## PLAN-FLOW-009 — Plan subscriber visibility

**Status:** `DECIDED — EXPORT DEFERRED`

### Confirmed current behavior

- The subscriber list returns only ACTIVE and TRIALING subscriptions.
- Detail counts all historical rows but does not list cancelled/past-due history.
- "Current recurring price" is a sum of calculated values, not collected revenue.
- The list is unpaginated.

### Proposed admin view

Plan Detail should show account-level subscription information only:

- Account ID/name.
- Subscription ID/status.
- Snapshot plan code.
- Current recurring price.
- Current period end.
- Relevant warning/scheduled-change state.

It should not expose companies, members, roles, invitations, or business-module data merely because an admin is inspecting a plan.

### Decided view behavior

- Show every status explicitly. `ACTIVE`, `TRIALING`, and `PAST_DUE` remain operational/current categories with distinct warnings; `CANCELLED` is historical. Future states are classified explicitly rather than silently hidden.
- Keep this surface Account/subscription-level only; it does not reveal Companies, members, roles, or business records.
- Label current calculated amounts as configured/estimated recurring price. Never call them revenue or collected payment until actual payment records prove that meaning.
- Paginate and filter by Account name/ID, plan, subscription status, and—under a separately authorized identity lookup—owner email.
- Export is a later separately permissioned feature. The initial operational UI/API must not fake export completeness.

---

## PLAN-FLOW-010 — Bulk subscriber plan changes

**Status:** `DECIDED — PRICING/NOTIFICATION CONTENT LATER`

A bulk subscriber plan change is a separate explicit operation, never a side effect of template editing.

### Proposed flow

1. Select source plan/subscriber population.
2. Select target plan or new snapshot strategy.
3. Backend previews affected accounts.
4. Detect feature loss, add-on loss, quotas below usage, price changes, billing-cycle changes, and inactive accounts.
5. Admin resolves exclusions/conflicts.
6. Show final counts and financial/entitlement effects.
7. Require explicit confirmation.
8. Process transactionally per account or in controlled batches with resumable status.
9. Record actor and per-account outcome.
10. Notify customers when product policy requires it.

### Decided execution behavior

- Target all subscribers or an explicitly filtered/selected population; preview records the backend selection criteria/version and final affected Account set.
- Process each Account transactionally in controlled batches. Partial success is allowed and must expose exact per-Account success/conflict/failure state with idempotent retry.
- Lock/version-check each Account/subscription at execution so concurrent self-service or admin changes are never overwritten. Changed Accounts become visible conflicts requiring fresh preview/decision.
- Do not promise a magical rollback after execution. Reversal/correction is another explicit previewed plan change that preserves both histories.
- Support immediate, each-Account renewal, and scheduled effective times. Pending operations may be cancelled before their cutoff/effective execution; started/completed Accounts remain recorded.

---

# Commercial control-plane decisions

The canonical cross-area contract is `docs/COMMERCIAL_CONTROL_PLANE_V1.md`. These decisions add capabilities to the existing Plan/AddOn/quota/subscription model; they do not relax immutable published revisions, snapshot-pinned subscriber terms, or explicit subscriber-change operations.

## COMMERCIAL-FLOW-001 — Technical capabilities and commercial products remain separate

**Status:** `DECIDED — 2026-08-26`

- Code and the registry own Feature, permission, and quota identities and safety metadata.
- Platform operators own Plan, AddOn, capacity-package, Price-book, policy, Campaign, and Offer records.
- A Feature has no universal price. A commercial product revision references only currently sellable registry capabilities.
- A subscription belongs to an Account and resolves one Plan plus selected compatible AddOns/packages and explicit commercial effects into an immutable snapshot.
- Authorization, entitlement, quota enforcement, commercial policy, and settlement remain separate layers. Success in one layer never implies success in another.
- Marketing may narrow or propose compatible products and adjustments; it cannot make an internal/non-sellable Feature purchasable or bypass authorization, dependency, quota-ownership, currency/cycle, payment, or platform-safety rules.

## COMMERCIAL-FLOW-002 — Plan extension and sales-visibility policy

**Status:** `DECIDED — 2026-08-26`

Every Plan revision declares one extension policy:

- `CLOSED`: the Plan cannot be extended through customer purchase.
- `ALLOW_LIST`: only explicitly attached compatible AddOns and capacity packages are available.
- `OPEN_COMPATIBLE`: any active public extension that passes compatibility may be sold unless explicitly blocked.

Product visibility is independently `PUBLIC` or `DIRECT_ONLY`. Direct-only products remain usable by authorized operators and targeted Offers but never appear in the ordinary client catalogue.

Compatibility is always backend-computed from registry sellability, dependency/exclusion rules, duplicate-capability prevention, included-quota ownership, already-entitled quota ownership for packages, active product revisions, and exact currency/cycle support. Optional Plan/Segment/Account targeting can only narrow this set. The backend catalogue and preview explain why an item is unavailable; the UI does not reproduce the rules.

Changing either policy on a published product requires a new commercial revision or a separately audited sales-availability operation. Existing snapshots remain unchanged.

## COMMERCIAL-FLOW-003 — Immutable multi-cycle price books

**Status:** `DECIDED — 2026-08-26`

- Plan, AddOn, and capacity-package revisions no longer have only one usable price/cycle. Each may expose independently entered `MONTHLY` and `YEARLY` Price-book entries for supported currencies.
- A yearly price is never derived automatically from monthly. Zero-price recurring entries are valid.
- Price entries use `DRAFT`, `ACTIVE`, `INACTIVE`, and terminal `ARCHIVED`. Amount, currency, cycle, owner revision, and effective-from time are immutable after activation.
- The original lifecycle permits pausing an active entry without changing subscriber snapshots. The 2026-09-09 amendment below supersedes standalone pause as the normal administrator workflow; a changed amount still creates a new entry/version.
- At most one entry is applicable for one product revision, currency, cycle, and instant. Activation validates overlap transactionally.
- Every API Money amount is an exact plain-decimal JSON string with a separate ISO currency code; frontend display/input must preserve it without `number`, `parseFloat`, or implicit IEEE-754 arithmetic.
- Subscription preview and checkout select exact compatible entries and snapshot every item. No implicit currency conversion or mixed-cycle total exists.
- Existing subscribers change prices only through an immediate/renewal/scheduled subscription operation. A template edit never reprices them.

### Amendment — Continuous tariff replacement

**Status:** `IMPLEMENTED AND VERIFIED — 2026-09-10 (PRICEBOOK-004)`

- For each offered product revision / currency / billing-cycle option, keep one currently
  applicable tariff and at most one confirmed future replacement. Monthly and yearly options
  remain independent; this does not require creating options that the product does not offer.
- The normal action is **Changer le tarif**, choosing **Maintenant** or **À une date**. The
  administrator does not manage two independent start/end dates or manually stop the old tariff.
- Confirmation atomically ends the old applicability window exactly when the new one begins.
  Both changes succeed or neither does: no overlap and no gap, including under concurrent writes.
  The current tariff otherwise has no manually entered end date.
- A draft does not affect sales and never automatically becomes approved because another tariff
  ends. A confirmed scheduled replacement takes effect at its boundary without further approval.
- Before that boundary, the replacement can be changed or cancelled through a reviewed operation.
  Cancellation restores uninterrupted current-tariff coverage atomically; changing the date moves
  both boundaries together. Elapsed history and accepted customer terms are never rewritten.
- Present **Tarif actuel**, **Changement programmé** when present, and previous tariffs in history.
  Remove standalone tariff pause/end-date controls from the normal flow. Stopping sales remains
  a separate deliberate product-availability operation, not an accidental missing-price interval.
- This explicitly revises the earlier pause-first lifecycle workflow, not merely its labels.
  Backend write paths, legacy actions, authorization, signed preview, audit, idempotency, and stale
  evidence handling must enforce the same coverage invariant; hiding buttons alone is insufficient.
- Catalogue replacement does not reprice existing subscribers or retarget published Offers.
  Existing-subscriber changes remain the explicit `SUBSCRIPTION-FLOW-005` operation. Offer-price
  dependencies block a change if a published Offer pins the current price beyond the new cutoff,
  or pins a scheduled price being replaced/cancelled. Review shows the affected count; operators
  retire/revise those Offers through their separate authorized flow. No automatic retargeting is approved.
- The API separates `preview_change`, `change`, `reschedule_change`, and `cancel_change` permissions.
  Confirmation binds the actor, exact request, current/scheduled versions, registry/catalogue
  evidence and an idempotency key. A receipt replays the original result after response loss;
  reusing its key for a different actor or intent is rejected. Audit and receipt failures roll
  back the boundary handoff. The commercial-role preset includes the new controls; existing
  manually configured roles are not silently granted new permissions.
- Legacy standalone pause and unsigned replacement commands reject with a conflict. First-price
  activation also rejects an independently entered finite end, including composite capacity-pack
  publication. Historical finite windows and imported/copied draft terms are not silently rewritten:
  drafts must be reviewed into a valid continuous option before publication. No background data
  migration or automatic publication of a copied schedule is implied by this amendment.

**Verification — 2026-09-10:** full backend suite (874 tests), frontend suite (398 tests),
typecheck, Biome and production build pass. Isolated authenticated browser checks cover immediate
change, scheduling, amount/date rescheduling and cancellation restoring an open-ended current
tariff. Dark/desktop and narrow-dialog checks preserve visible review/confirmation controls.
Existing subscriber snapshots, Offer dependencies, concurrent writes, rollback, authorization,
stale evidence and same-key retry behavior have regression coverage. No demo/production data
migration is included.

**Catalogue presentation clarification — 2026-09-09:** Plans use spacious comparison cards for
the expected small catalogue, restoring the earlier design. Keep server-side filtering and
pagination, shared actions and authoritative price options. Add-on/package and high-volume
operational lists retain reusable tables; consistent controls do not require identical layouts.

## COMMERCIAL-FLOW-004 — Typed Account commercial policies and precedence

**Status:** `IMPLEMENTED THROUGH PHASE 10 — INDEPENDENTLY AUDITED 2026-08-27`

Commercial policies provide reusable operator tools without hard-coding a business strategy.

- Delivered targets are one Account, explicit Accounts, subscribers of one Plan revision, or an active exact Segment revision/activation. The backend snapshots the affected set for activation and preserves accepted provenance, including the source Segment activation.
- Delivered effects are product allow/block, Feature block, fixed recurring subscription price, fixed-Money discount, percentage discount with explicit maximum, finite quota bonus, and bounded AddOn/package grant. Free recurring periods and renewal instructions remain Phase 12.
- Every policy has source, reason, owner, actor, lifecycle, priority, effective window, expiry or explicit permanence, optional approval/contract reference, preview version, and audit.
- Direct Account policy outranks Segment policy. At equal priority, restriction wins over grant. Purchased extensions precede commercial adjustments, while platform hard safety ceilings and Account governance restrictions always win.
- One-Account and explicit Account-set targets have the same direct-target specificity and both outrank Plan-revision and Segment audiences. A Plan-revision audience includes current `TRIALING`, `ACTIVE`, `PAST_DUE`, and `SUSPENDED` subscribers; terminal subscription history is excluded.
- An active policy is immutable; editing creates a draft revision. Expiry stops future effect and never rewrites historical snapshots, invoices, or operations.
- Activation approves the exact immutable policy revision and snapshots its Account audience; it does not itself alter subscriptions, entitlements, prices, invoices, or payments. A Segment-targeted policy requires an active immutable Segment activation. `DRAFT`, `ACTIVE`, and `PAUSED` policy references keep the Segment reusable and therefore block its archive; `ENDED` and `ARCHIVED` policy history does not, because the policy activation already retains the frozen audience and Segment provenance.
- Time-bounded AddOn and capacity-package grants require an explicit end. Platform hard limits remain non-overridable, and every accepted subscription operation snapshots the exact winning policy revision/effects so later expiry or revision cannot rewrite accepted terms.
- Policy activation and later Account application are separate. One-Account immediate/at-renewal application is delivered through the signed subscription preview/apply operation, including locked recomputation and accepted-effect provenance. Selected/filtered/scheduled application, per-Account job results, retry, and cancellation cutoff remain Phase 12.

## COMMERCIAL-FLOW-005 — Reviewed commercial writes use operation-bound evidence

**Status:** `DECIDED — 2026-08-27`

- A reviewed cross-aggregate mutation carries short-lived signed evidence that binds the operation kind, exact target and optimistic version, actor, commercial-catalogue revision, registry snapshot version, evaluation time/expiry, and assessment fingerprint.
- Apply never treats the evidence as authorization or payment. It reauthorizes the actor, takes the documented aggregate locks, re-resolves current registry/commercial state, and rejects stale, malformed, expired, cross-actor, or cross-operation evidence with a stable conflict and no write.
- Lock order is consistent: registry-affecting commercial writes lock the commercial catalogue before the registry singleton and product rows; subscription finalization locks the Account before those commercial/registry locks and exact product/price rows.
- A strictly row-local mutation may instead use an exact optimistic version plus locked recomputation when it cannot select or affect a different aggregate, audience, or result.
- Signed review evidence is never placed in URLs, logs, audit payloads, analytics, or user-facing errors.

## MARKETING-FLOW-001 — Safe reusable Account segments

**Status:** `IMPLEMENTED — INDEPENDENTLY AUDITED 2026-08-27`

- A Segment is either an explicit Account set or typed commercial criteria. It never accepts SQL, scripts, or permission/business-record predicates.
- Initial criteria may use current Plan, subscription status, currency/cycle, Account creation date, and purchased commercial products.
- Preview returns a bounded sample, total, criteria version, and evaluation time. Activation snapshots membership for a Campaign execution; later Account changes do not silently rewrite an already scheduled audience.
- Segment list/detail APIs support pagination, search, lifecycle, duplication, impact preview, safe delete/archive, and history. Sensitive Account identity fields remain separately authorized.
- Delivered operations include bounded list/search, an explicit-or-criteria builder, preview/count, separately authorized identity resolution, signed activation, immutable revisions, compare/history, owner transfer, and lifecycle control. Commercial Policies may target an exact active Segment activation through a dedicated executable reference chooser.
- Archiving excludes a Segment from new selection. It is blocked by `DRAFT`, `ACTIVE`, or `PAUSED` policy references, but not by `ENDED` or `ARCHIVED` policy history, whose accepted activation already preserves its own frozen audience and Segment provenance.

### Amendment — Segment origin is not an administrator choice

**Status:** `IMPLEMENTED — 2026-09-09 (MARKETING-002)`

- Remove the manually assigned segment-level **Origine** (`MANUAL` / `IMPORTED` / `SUPPORT`)
  concept from normal segment authoring and management. It does not select Accounts or perform
  an import, and a segment need not have one uniform membership source.
- If importing an Account list is implemented later, it must be a real Account-selection flow.
  Imported entries and manually selected entries may coexist in the same explicit Account set.
  Record how an Account was added on the membership/addition event, automatically from that
  operation, not as a label on the entire segment or on the Account globally. Preserve provenance
  when the same Account is encountered through more than one operation; never infer it from a
  legacy segment label.
- Support-related intent belongs in the business reason. Actor/audit evidence and immutable
  audience snapshots remain independent and must be preserved.
- This decision does not implement an import feature, choose its file/matching contract, or
  change the existing explicit-set versus typed-criteria audience model. Only the misleading
  origin controls are removed; import remains a future independently designed workflow.

## MARKETING-FLOW-002 — Campaigns, offers, and redemption

**Status:** `IMPLEMENTED — CAMPAIGNS AUDITED 2026-08-28; OFFER BACKEND AND ADMIN/CLIENT UI VERIFIED 2026-08-31; AUTHENTICATED BROWSER QA PENDING`

- Campaign lifecycle is `DRAFT`, `SCHEDULED`, `ACTIVE`, `PAUSED`, `ENDED`, and terminal `ARCHIVED`.
- Delivered Campaign operations include bounded list/search/filter/sort, guided draft terms and audience configuration, duplication/revision/compare/history, separately authorized ownership and Account identity lookup, signed schedule review, pause/resume/end/archive, safe draft deletion, and isolated scheduled start/end processing. The admin UI exposes these as permission-independent surfaces, uses narrow operation/edit/evidence contracts rather than broad detail reads, and preserves stale-review, loading, error, mobile, and RTL behavior.
- A targeted schedule freezes the exact explicit Accounts or exact active Segment activation selected at review. Its immutable evidence binds actor, Campaign/version, commercial-catalogue revision, registry snapshot, evaluation/expiry, fingerprint, reason, and schedule window. A public Campaign deliberately stores no platform-wide Account population; eligibility remains dynamic at Offer preview/acceptance.
- Only a draft Campaign still depends on its source Segment for future scheduling and therefore blocks Segment archive. Once scheduled, the frozen audience is self-contained; retained Campaign history still blocks destructive Segment deletion so provenance cannot be orphaned.
- Offers are immutable published revisions in a stable lineage. The lineage owns its exact Campaign revision, permanent customer-code reservation, discovery mode, acceptance channel, and lineage-wide global/per-Account limits; publishing a successor never resets usage.
- Discovery is either `CATALOG` or `CODE_ONLY`. Acceptance is independently `CLIENT_OR_OPERATOR` or `OPERATOR_ONLY`. A code never grants authority or bypasses the Campaign audience, and code resolution returns one generic unavailable result for unknown, ineligible, exhausted, retired, expired, or wrong-audience input without logging the raw code.
- Every Offer revision pins one complete compatible target selection: exact Plan/Price entry, exact AddOn/Price entries, exact capacity-package/Price entries and quantities, timing, optional finite quota bonus, optional free product grants, and at most one fixed or percentage Offer discount. It cannot carry restriction effects, arbitrary fixed recurring price, tax, proration, refund, or free-period behavior.
- Fixed/percentage discounts exist only as Offer/Policy effects with Money/currency compatibility, bounded duration/redemptions, reason/source, and immutable evidence. They are not arbitrary client-supplied checkout values.
- Public offers are visible only to currently eligible Accounts. Targeted offers are visible only to their snapshotted audience. Direct-only products may appear through an authorized targeted offer.
- Preview explains resulting products, features, quotas, exact itemized price, adjustment, amount due, and timing. Acceptance revalidates under an Account lock and is idempotent.
- Pausing/ending stops new redemption but never reverses completed changes or cancels an already reserved operation. Phase 11 operator application targets exactly one Account through the same reviewed subscription-operation engine; selected/filtered/bulk execution belongs to Phase 12 jobs.
- The Campaign owns the audience and every Offer revision inherits it. A public Campaign does not snapshot the whole platform; eligibility is resolved at preview/acceptance time.
- Scheduling freezes the Campaign audience and timing, not an Offer set. Each Offer lineage belongs permanently to one exact Campaign revision; Campaign revision/duplication never silently copies or moves Offers.
- Offer selection is explicit and opt-in. An Offer is not activated as an always-applicable Commercial Policy and must retain its own immutable redemption/provenance evidence.
- One authoritative combined commercial evaluation computes catalogue subtotal, the selected Policy fixed base, selected Policy discount candidate, Offer discount candidate, winner, and final recurring price. The greater actual compatible reduction wins without stacking; Policy wins an exact tie. Restrictions and platform hard limits remain vetoes.
- Published customer-facing codes are globally reserved permanently. A redemption reserves global capacity when its subscription operation is created; cancellation or failure before application releases capacity without deleting the historical attempt.
- Initial redemption supports current `ACTIVE` or `TRIALING` subscriptions only. Past-due, suspended, cancelled, and win-back lifecycle recovery belongs to Phase 12.
- The Offer eligibility window controls new acceptance. A temporary discount that later reverts requires an explicit scheduled Phase 12 subscription operation rather than silent expiry mutation.
- Published Offer revisions are immutable. Retirement is a reversible stop for new redemption; archive is terminal.
- Delivered Offer operations include bounded list/search/filter/sort, narrow definition and operation reads, guided exact-product authoring, backend definition/publication previews, immutable revisions and comparison, owner transfer, results, privacy-separated redemption identities, client catalogue/body-only private-code resolution, signed preview, idempotent acceptance, and one-Account operator application. The client projection contains readable product terms and omits internal Campaign, lineage, product, and Price identities.

## BILLING-FLOW-001 — Invoice, settlement, credit, and refund ledgers

**Status:** `DECIDED — 2026-08-26`

- Price preview, invoice/amount due, payment attempt, confirmed settlement, credit, refund, and collected-value analytics are distinct records.
- Invoices are immutable numbered documents containing Account, period, currency, status, totals, and versioned lines that retain source product/price/policy identifiers and readable snapshots.
- Payment attempts are idempotent and record pending/succeeded/failed state, amount, method/provider type, external or manual reference, source/actor, and timestamps. Manual settlement needs a dedicated permission, evidence/reference, and reason.
- Credit and refund records append corrections; they never mutate original invoice/payment evidence. Refund totals cannot exceed eligible settled amounts.
- Zero-amount invoices may settle without a fake payment. Only succeeded settlement contributes to collected-value reporting.
- Provider callbacks/reconciliation are idempotent and never activate entitlement merely because a price calculation succeeded. Failed renewal enters explicit past-due/grace/restricted lifecycle without deleting data.
- Checkout cancellation and manual settlement cancel an automatic charge only before provider dispatch, atomically cancelling its Payment intent and outbox command. Once dispatch begins, reconciliation is required before either action so the platform cannot double-collect or falsely report a provider charge as cancelled.
- Provider Refund transport is available only for a trusted provider-collected Payment. A cash, bank-transfer, or otherwise manual return is recorded as a separately authorized succeeded manual Refund with external evidence; it never fabricates or sends a provider Refund command.
- A callback reaches the ledger only through a provider adapter that already verified its signature. HiveApp stores normalized evidence and a payload digest before processing, matches only by the financial idempotency key, and retains unknown/mismatched evidence without changing money or entitlement; no unsigned generic webhook is exposed.
- Charge recovery never reopens a failed Payment. It creates a new Payment/outbox attempt with operator provenance; an ambiguous transport failure additionally requires recorded provider non-capture evidence before retry.
- Automatic tax, FX, metered billing, automatic proration, and automatic refunds remain deferred and must not appear as implemented.

## COMMERCIAL-FLOW-006 — One-Account special commercial agreements

**Status:** `IMPLEMENTED — 2026-09-03`

- An authorized platform administrator may create one reviewed special agreement for one Account
  without publishing a new reusable catalogue product.
- The agreement snapshots one exact Plan/Price selection, exact AddOns and capacity packages,
  optional finite private quota bonuses, an exact entitlement start/end, and one explicit end
  instruction.
- Pricing is catalogue total, exact custom fixed-term total, or complimentary zero. A custom amount
  is contractual evidence and may differ from catalogue value; it does not mutate the product's
  Price book.
- A positive amount is settled through either the provider flow or an authorized manual settlement
  with unique reference and reason. Complimentary access is zero-priced and must never be recorded
  as a succeeded Payment or collected revenue.
- Duration may be one month, a positive number of calendar months, or exact UTC start/end instants.
  An irregular period is never automatically prorated.
- At the deadline the system must execute exactly one reviewed instruction: continue on captured
  follow-on terms, restore the pre-agreement terms, end access, or require manual review. It must not
  silently fall into ordinary renewal.
- Future start and end execution are durable, idempotent, Account-locked, retryable where safe, and
  auditable. Hard registry, entitlement, authorization, dependency, usage, and data-preservation
  rules remain vetoes.
- The administrator flow is **Créer un accord spécial** inside the Account subscription workbench;
  Offers and Policies remain reusable marketing/rule tools rather than hidden agreement records.
- The complete contract is `docs/SPECIAL_COMMERCIAL_AGREEMENTS_V1.md`.
- Delivered as independent preview/create/read/cancel/retry/manual-review/analytics permissions,
  immutable agreement and financial evidence, scheduler-driven start/end transitions, a five-step
  admin workbench, global and Account operations tables, and a privacy-safe client terms surface.

## ANALYTICS-FLOW-001 — Durable commercial facts and truthful analytics

**Status:** `DECIDED — 2026-08-26`

- Analytics derive from durable subscription periods, operations, invoice/payment/credit/refund records, and append-only commercial events—not mutable template totals.
- Initial metrics cover subscription state over time, configured recurring value, invoiced/settled/credited/refunded value separately, product adoption/churn, Offer eligibility/redemption, policy execution, renewal outcomes, past-due aging, and capacity-package/near-quota adoption.
- Every endpoint takes a bounded time range, timezone, interval, and safe filters; returns completeness time and dimensions; and never sums different currencies or cycles into one unlabeled number.
- Summary cards/charts link to the filtered operational table that explains the number. Missing/incomplete data is shown honestly rather than as zero.
- Analytics read permissions do not imply access to sensitive payment references, owner email, or client business records.
- The implementation contract is recorded in `docs/COMMERCIAL_ANALYTICS_V1.md`: V1 reads the authoritative ledger/lifecycle/operation/redemption records directly, exposes independently protected summary, financial, subscription, Offer, and operational surfaces, and uses explicit read watermarks rather than claiming provider finality.
- Backend and admin dashboard implementation completed on 2026-08-31. The five read permissions remain independent through route mounting, requests, and visible tabs; Account identity is confined to the operations queue, while aggregate views use exact strings and honest error/empty/provisional states.
- Historical near/over-quota reporting is deferred until a cadence-based append-only usage-snapshot contract exists. V1 may report current capacity-package holdings, but it must not scan every Account's live business tables and present that unstable result as historical quota pressure.

## OPERATIONS-FLOW-001 — Activities, communications, and logs are different products

**Status:** `IMPLEMENTED — 2026-08-31`

- Activities expose the existing append-only mutation/security evidence under separate metadata, actor-identity, Account-identity, and payload permissions. They do not begin recording reads; `AUDIT-002` remains open.
- Communications expose durable credential-email delivery status. Raw tokens, links, and bodies are never stored, so the page cannot preview or replay an old message. A new credential message must originate from its authorized operator/member workflow.
- Observability exposes safe health and aggregate backlog evidence plus an external-log-provider boundary. HiveApp does not copy raw production logs, stack traces, secrets, or environment data into the business database.
- Every request receives a bounded correlation id returned in errors/responses and attached to new audit evidence so operators can cross-reference an activity with external logs.
- The complete permission, API, privacy, performance, UI, and deferral contract is frozen in `docs/PLATFORM_OPERATIONS_V1.md`.
- The backend and admin UI now implement that contract with independent evidence permissions, bounded lists, bulk identity enrichment, safe aggregate health/backlogs, permission-gated detail requests, compact mobile records, and authenticated light/dark/RTL browser verification. Raw logs and generic credential-message replay remain deliberately absent.

# Account subscription administration

## SUBSCRIPTION-FLOW-001 — Finding the correct account

**Status:** `DECIDED`

Admins should not normally paste a raw UUID. Provide authorized paginated lookup by Account name, exact ID, plan/status, and owner email where identity-search permission allows it. Results expose only the minimum Account/subscription identity required to select safely, never Companies, members, or business data.

## SUBSCRIPTION-FLOW-002 — Replacing an account's plan

**Status:** `IMPLEMENTED FOR ONE ACCOUNT — IMMEDIATE/AT RENEWAL — 2026-08-27`

Required flow:

1. Select account.
2. Display current subscription snapshot and overrides.
3. Select target plan/add-ons/quotas.
4. Preview entitlement, usage conflicts, and price.
5. Choose immediate or at-renewal effect.
6. Confirm.
7. Revalidate under an account lock.
8. Create history-preserving replacement state.
9. Audit and display the result.

Additional decisions:

- The detail surface includes the current purchased snapshot, current feature-owned usage, overrides/exceptions, subscription history, and pending operations.
- Authorized operators may change the plan immediately. Usage conflicts require an explicit result—grace, temporary exception, restricted state, or remediation—not automatic data deletion.
- End/preserve the old subscription state and create a new validated snapshot/history entry; never mutate historical purchased terms in place.
- Authorized manual corrections are supported but require a reason, before/after detail, and complete audit.
- Selected/filtered populations, arbitrary scheduled instants, reviewed trial/lifecycle commands, job progress, retry, cutoff cancellation, and communication state remain Phase 12.

## SUBSCRIPTION-FLOW-005 — Explicit price-only changes for existing subscribers

**Status:** `IMPLEMENTED — 2026-09-08`

- Catalogue price changes affect new purchases only; existing Accounts retain accepted renewal
  prices unless a distinct reviewed subscriber operation explicitly targets them.
- Select one/selected Accounts, exact old-tariff holders, filtered holders, or a frozen Segment,
  with exclusions. Freeze the reviewed population and preserve each Account's other terms.
- Replace only the selected Plan/Add-on/pack price, retaining quantities and entitlements. Currency,
  cycle, and product changes remain separate operations; special agreements and accepted discount
  terms require their own explicit review, never a blanket overwrite.
- Apply at each Account's next renewal or first renewal on/after a date. Preserve paid periods,
  issue normal financial evidence, and require trusted settlement for positive charges.
- Provide an Account-private in-app notice, optional durable email, per-Account outcomes,
  cancellation, safe retry, and conflict revalidation. Reading a notice is not consent.
- The exact implementation and verification contract is `docs/SUBSCRIPTION_REPRICING_V1.md`.
- V1 reviews are bounded to 500 Accounts. Cancellation ends at renewal processing; an issued
  financial operation remains under billing recovery/refund controls. Changed commercial terms
  require a new review, while a pre-operation technical failure may retry its original evidence.

## SUBSCRIPTION-FLOW-003 — Account-specific exceptions

**Status:** `TYPED POLICY EFFECTS IMPLEMENTED — GENERAL EXCEPTIONS LATER`

- Phase 10 typed policies can grant/block eligible products, block client Features, apply a fixed recurring price or one bounded non-stacking discount, add finite quota, and grant bounded AddOns/packages within code-owned safety boundaries. They never enable internal, platform-control, inactive, or non-sellable capabilities.
- Every accepted effect retains policy revision/source, reason, owner/actor, target specificity, priority, effective window, and exact result provenance. Later expiry or revision cannot rewrite accepted terms.
- Subscription preview exposes the winning policy-adjusted terms and conflicts; apply recomputes them under locks and snapshots the result. Restrictions outrank grants at equal specificity/priority, and direct targets outrank Plan/Segment audiences.
- General negotiated exceptions, grace/restricted-state remediation, free periods, and renewal instructions remain later work rather than raw override fields.

## SUBSCRIPTION-FLOW-004 — Cancellation, suspension, and expiration

**Status:** `DECIDED — BILLING/NOTICE DETAILS LATER`

These are separate business actions and need separate effects:

- Cancel at period end.
- Cancel immediately.
- Suspend for failed payment/compliance.
- Expire after trial/term.
- Restore/reactivate.

For each action define feature access, read-only access, data retention, B2B effects, token behavior, billing, customer notification, and reversal.

- Keep cancel-at-period-end, immediate cancellation, suspension, expiration, and restoration as distinct commands/states with explicit transitions.
- Cancellation/suspension stops operational entitlement and B2B use at its effective time while preserving data in a declared restricted/read-only state; it never silently purges customer data.
- Revoke/refresh affected authorization and sessions/caches so old access cannot outlive the state change.
- Restoration creates no old-session resurrection and revalidates current Account state, plan eligibility, entitlement, usage/remediation, exceptions, and B2B rules.

---

# Client subscription self-service

Client operation can be simpler than admin operation, but backend rules remain authoritative.

## CLIENT-SUB-FLOW-001 — Safe client flow

**Status:** `DECIDED — PAYMENT/PRICING DETAILS LATER`

1. View current purchased snapshot and usage.
2. Compare active public plans and allowed add-ons.
3. Select desired changes.
4. Preview exact effective features, quotas, conflicts, and price.
5. Complete payment/confirmation appropriate to the current product stage.
6. Backend revalidates under an account lock.
7. Apply immediately or schedule according to downgrade rules.
8. Preserve previous subscription history.
9. Show clear confirmation and effective date.

Access and commercial boundaries:

- The Account owner and separately authorized Account-scoped subscription actors may view price, contract/history, exceptions, and pending changes. Ordinary members see only capability/usage information relevant to their work.
- Only the owner or an Account-scoped subscription manager may request plan, add-on, quota, cancellation, or renewal changes.
- Until real payment/checkout exists, a paid change remains pending until a real payment, contract, or authorized operator confirmation event. Explicitly free changes may auto-activate only when configuration deliberately permits it; never pretend payment succeeded.
- Client self-service offers only code-valid, active, publicly sellable plan/add-on/quota options. It cannot create arbitrary exceptions, unlimited quotas, internal/non-sellable features, free paid features, or negotiated prices.

## CLIENT-SUB-FLOW-002 — Upgrade versus downgrade

**Status:** `DECIDED — PRORATION/REFUND DETAILS LATER`

- Both an upgrade and a downgrade offer the same effective-time choices: **now** or **at renewal**. The UI must not force timing merely because it labels a change an upgrade or downgrade.
- Every plan change receives the same complete impact preview. A commercially larger plan can still remove or lower a capability, so backend comparison of effective features, quotas, price, dependent workflows, and usage—not the plan's name or price—determines impact.
- **Now** requires current authorization/commercial confirmation and an Account-locked recheck immediately before applying. Any conflict requires an explicit result such as grace, temporary exception, restricted access, or usage remediation; no data is silently deleted.
- **At renewal** creates a visible cancellable pending operation, gives the Account time to remediate, and revalidates current state at the execution cutoff. New conflicts become explicit rather than being overwritten.
- Pricing work still defines proration, refunds/credits, failed payment, and exact renewal billing. Reusable communication work defines notification content/channels.

---

# Cross-feature effect checklist

Every plan/subscription action must be checked against:

- Permission entitlement.
- Member and company quotas.
- Role permission picker contents.
- Existing role grants.
- B2B delegation picker contents.
- Existing B2B delegated access.
- Client navigation and visible features.
- API runtime authorization.
- Billing/price calculations.
- Active tokens and request context.
- Audit/history/reporting.
- Future HR/payroll/accounting records that depend on the feature.

The backend must define the effect. The UI must explain it; it must not invent it.

---

# Registry and permission-catalog operations

## REGISTRY-FLOW-001 — Removing or renaming a feature/action in code

**Status:** `DEFERRED — STABLE CODE VALUES ASSUMED`

Current product decision: feature and Permissionizer codes are developer-owned stable values with no expected normal reason to rename/delete them after use. Do not add alias flags or advanced replacement support to Permissionizer now. Revisit the migration/retirement flow only if a real code-removal or published-integration requirement appears.

Removing a `FeatureDefinition` or `@PermissionNode` is a production data migration, not merely a code cleanup. Required flow:

1. Detect the removed/renamed key during startup or deployment validation.
2. Show all affected plans, subscriptions, client roles, member overrides, admin roles, invitations, and B2B grants.
3. Choose replacement, retirement, or cancellation for each use.
4. Prevent new grants immediately while preserving an auditable retired record.
5. Migrate/revoke existing references deliberately.
6. Deploy enforcement and catalog changes together.
7. Verify no active reference still depends on the retired key.

Decide whether a renamed permission can have a temporary alias and how long compatibility lasts. Never silently reuse a retired permission key for a different meaning.

## REGISTRY-FLOW-002 — Feature visibility versus operational access

**Status:** `DECIDED`

The current code exposes an activation endpoint but marks no real feature toggleable. For each client/public feature decide separately:

- Can admins hide it from the public catalog?
- Can it still be sold/assigned to a plan?
- Do existing subscribers keep access?
- Are new role/B2B grants blocked?
- Are existing grants ignored, revoked, or preserved for reactivation?
- Does API authorization stop immediately or only UI visibility change?
- What warning, impact preview, confirmation, and audit entry are required?

Decided model: do not use one `active` flag to mean catalog visibility, sale availability, grant availability, and runtime shutdown. They are independent platform-admin operations:

1. **Public visibility:** hide/show the feature in public product/catalog surfaces only. Existing plans, subscriptions, grants, and runtime access remain unchanged; separately authorized private/contract offers may still use it.
2. **New-sale availability:** block adding/selecting the feature in future plan/subscription offers. Existing plan configuration and subscriber snapshots continue; changing existing customers uses the separate subscriber-operation flow.
3. **New-grant availability:** block new client-role, override, and B2B grants while preserving existing grants. Disabling/removing existing grants is a separate impact-previewed operation.
4. **Emergency runtime availability:** immediately block actual feature use while preserving configuration, entitlements, grants, and data. Require explicit dangerous-action permission, reason, affected-scope preview, temporary/permanent timing, communication options, authorization/cache invalidation, and complete audit. Restoration revalidates current Account, subscription, feature, permission, and B2B state before access returns.

## REGISTRY-FLOW-003 — Registry synchronization report

**Status:** `DECIDED`

Every deployment/startup should produce an inspectable result:

- definitions/actions discovered from code;
- rows created and metadata updated;
- stale rows proposed for retirement;
- references blocking retirement;
- collector/index failures;
- guarded services with no discovered actions;
- database rows not backed by code.

Decided synchronization behavior:

- Refuse production startup when Permissionizer/feature discovery is partial or corrupt, codes are duplicate/ambiguous, guarded features unexpectedly have no actions, or the registry graph cannot be validated.
- Validate first, then synchronize modules, features, actions, and relationships atomically. Any validation/write failure rolls back the complete synchronization result.
- Code owns codes, display metadata, module/feature/action relationships, current action existence, and grant-target eligibility. Startup synchronizes those values. It never overwrites platform-admin public-visibility, new-sale, new-grant, or emergency-runtime choices.
- Persist an inspectable report containing discovered/created/updated/invalid/missing counts and details, validation failures, result, version, timestamp, and application build. Platform admins receive an operational summary; sensitive collector/stack details remain developer-only.
- Coordinate concurrent application startup with a database-backed lock/version so only one node writes and the others consume the completed result. The operation is repeatable/idempotent and the same validation can run as a pre-deployment/CI check.

The advanced removal/rename migration described in `REGISTRY-FLOW-001` remains deferred under the stable-code assumption.

## REGISTRY-FLOW-004 — One catalog contract per audience

**Status:** `DECIDED`

Keep explicit DTO read models for:

- public product catalog;
- platform-admin feature inventory;
- plan feature picker;
- client-role permission picker;
- platform-admin-role permission picker;
- B2B delegation picker.

All share one current versioned code-owned registry snapshot, then apply audience-specific rules. Raw JPA entities are never response contracts.

- Classify grantability at action level in HiveApp registry definitions—not in the Permissionizer library. Each action explicitly declares owner-only and/or client-role, platform-admin-role, and B2B eligibility as applicable.
- Public catalog exposes only public-visible sellable items. Platform inventory exposes authorized operational state. Plan picker exposes active globally sellable plan options. Client-role picker additionally requires current Account entitlement and client-role eligibility. Platform-admin-role picker requires admin-role eligibility. B2B picker additionally requires provider entitlement, B2B eligibility, active collaboration context, and the provider actor's delegation ceiling.
- Management pickers show available choices separately from current selections. A selected action that is no longer available remains visible but disabled with a plain source-owned reason such as not entitled, new grants disabled, emergency shutdown, or not B2B-delegatable; it is never silently hidden or newly assignable.
- Dedicated DTOs expose only audience-required fields and a registry snapshot version/hash. Write requests include that version; stale picker submissions are rejected with a refresh-required conflict rather than applying against changed eligibility.
- Registry synchronization and operational controls publish a new snapshot version and invalidate relevant catalog/authorization caches immediately.

---

## Decisions log

Record accepted decisions here with date, reason, and affected source areas.

| Date | Decision | Reason | Affected areas |
|---|---|---|---|
| 2026-09-09 | Remove manually assigned segment origin; any future import records Account-inclusion provenance through the actual operation | A segment can mix imported-list and manual selections, while selecting an origin label currently performs no import and mixes creation method with support intent | Segment authoring/list/detail/review and public API metadata; MARKETING-002 implemented and verified, no import implementation approved |
| 2026-09-09 | Replace manual tariff pause/date coordination with one current tariff and at most one reviewed immediate or scheduled replacement per offered product/currency/cycle, preserving continuous coverage | Prevent accidental sales gaps without another administrator workflow; cancellation must keep the current price available, while product sales stops and existing-subscriber changes remain explicit separate operations | Price-book lifecycle/API, applicability windows, replacement/cancellation, product availability, admin UX, audit and tests; PRICEBOOK-004 implemented and verified 2026-09-10 |
| 2026-08-28 | Make Offer discovery and acceptance explicit, bind limits/codes to the lineage and exact Campaign/product/price revisions, keep Phase 11 application one-Account, and choose the greater Policy-or-Offer reduction without stacking | Codes must not become credentials; revisions must not reset limits or drift with catalogue changes; one authoritative price result avoids competing truths; bulk execution and recovery need the Phase 12 job/lifecycle engine | Offer lineage/revisions/codes/capacity, Campaign relation, subscription evaluation/operations/snapshots, client/admin authorization and UI, analytics boundaries |
| 2026-08-28 | Freeze exact targeted Campaign audiences at scheduling, keep public Campaign audiences dynamic, and release the source Segment after schedule while retaining immutable provenance | Targeted delivery must not drift after operator review, but snapshotting every Account for a public Campaign would be expensive and misleading; stored schedule evidence makes published history independent without allowing destructive provenance loss | Campaign scheduling/evidence/lifecycle, Segment archive/delete blockers, audience privacy, Offer eligibility, admin UI and tests |
| 2026-08-27 | Let only reusable/live `DRAFT`, `ACTIVE`, or `PAUSED` policy references block Segment archive; terminal policy history keeps its frozen audience and provenance but releases the Segment | An accepted policy activation is historically independent of the later Segment lifecycle, while a policy that can still be activated or resumed must retain a valid selectable Segment | Segment lifecycle and blockers, policy Segment resolver/activation, retained audience provenance, admin actions and tests |
| 2026-08-27 | Make Campaign the audience owner and Offers explicit opt-in immutable revisions with their own redemption evidence, never always-applicable active Policies | Offer acceptance, limits, codes, eligibility privacy, and commercial attribution require explicit provenance; representing an Offer as a Policy would silently affect ordinary subscription previews without selection | Campaign audience snapshots, Offer lifecycle/effects/codes, subscription preview/apply, redemption history, client/admin UI and analytics |
| 2026-08-27 | Use one non-stacking selected Offer discount over other discount candidates, permanently reserve published codes, reserve redemption capacity at operation creation with pre-application release, and keep retirement reversible while archive is terminal | Deterministic pricing, race-safe limits, reusable codes, and operational pause/recovery must remain explainable without rewriting completed history | Offer validation/publication, pricing precedence, redemption concurrency/idempotency, subscription operations, audit and lifecycle UI |
| 2026-08-27 | Retire direct admin subscription create, trial, and raw-override mutations; require signed reviewed operation evidence for subscriber-affecting admin changes, with internal registration-time FREE provisioning as the sole bootstrap exception | A privileged shortcut can bypass exact product/price/policy review, usage conflicts, concurrency checks, provenance, and history; trial creation must therefore return only as a first-class reviewed Phase 12 operation | Admin subscription API and Permissionizer nodes, Account workbench, registration provisioning, trial/lifecycle operations, audit and frontend flows |
| 2026-08-27 | Separate commercial-policy approval from subscriber application and freeze each accepted audience/effect provenance | Activating a reusable rule is not consent to rewrite every current subscription; immutable audience snapshots plus explicit reviewed subscription operations preserve operator intent, concurrency safety, and historical terms | Commercial-policy lifecycle/activation, Account audiences, subscription preview/apply, policy explanations, future bulk execution and settlement |
| 2026-08-27 | Bind reviewed cross-aggregate commercial writes to short-lived signed evidence and revalidate under one lock order | A browser-supplied count or stale preview version cannot prove which registry/catalogue state, actor, target, and result were reviewed; operation-bound evidence plus locked recomputation prevents substitution and TOCTOU writes without turning the token into authorization | Plan/AddOn/package/Price activation, availability and deletion, subscription changes, future policies/offers/bulk operations, audit and frontend confirmation flows |
| 2026-08-26 | Keep V1 commercial-policy pricing to one non-stacking subtotal discount and make policy audiences/execution evidence immutable | Subtotal-only fixed/percentage discounts avoid ambiguous line allocation and surcharges; static affected sets and authorized `SYSTEM` execution keep scheduled work reproducible without silently following later Segment or operator-permission changes | Commercial-policy effects and precedence, audience snapshots, scheduled execution, subscription operations, invoices, client explanations and audit |
| 2026-08-26 | Carry API monetary amounts as exact plain-decimal strings rather than JSON numbers | Java `BigDecimal` values can exceed JavaScript's exact integer/fraction range; converting immutable prices through IEEE-754 can silently alter accepted commercial terms | Money DTOs and snapshots, Price-book and subscription APIs, frontend contracts, formatting and tests |
| 2026-08-26 | Build the commercial control plane as separate technical capabilities, products, prices, policies, offers, subscription operations, and financial records | Combining these meanings created unclear UI, unsafe implicit effects, and totals that could be mistaken for settlement; separation keeps each operation explainable and auditable | Registry, Plans/AddOns/packages, price books, subscriptions, policies, marketing, billing, analytics, admin/client UI |
| 2026-08-26 | Give every Plan revision an explicit CLOSED, ALLOW_LIST, or OPEN_COMPATIBLE extension policy plus independent PUBLIC/DIRECT_ONLY product visibility | Operators need strict and extensible commercial strategies without marketing being able to override mandatory compatibility or expose private products | Plan/AddOn/package catalogue, compatibility previews, targeted offers, client self-service |
| 2026-08-26 | Replace the single-price product assumption with immutable independently entered monthly/yearly price-book entries | A product commonly supports monthly, yearly, or both; annual price is a commercial decision, while existing subscribers must retain accepted prices | Plan/AddOn/package pricing, checkout, snapshots, renewals, admin price-book UI |
| 2026-08-26 | Reopen discounts only as bounded typed Offer/Commercial Policy effects | Marketing control is required, but arbitrary checkout discounts or mutable base prices would bypass evidence, targeting, and currency/settlement rules | Segments, campaigns, offers, commercial policies, price preview, subscription operations, audit |
| 2026-08-26 | Derive commercial analytics from durable operational and financial facts and never mix configured, invoiced, settled, credited, or refunded amounts | Admin decisions require truthful drillable measures rather than decorative cards or mutable configured totals mislabeled as revenue | Commercial events/read models, billing ledger, dashboard charts, operational tables and permissions |
| 2026-08-14 | Duplicate only Plan-owned composition and require explicit AddOn/quota-package attachment to the new draft | Incoming attachments are owned by separate commercial items through `allowedPlanCodes`; copying them would silently widen those existing items while creating a Plan | Plan duplication service and wizard, AddOn/quota-package management, schema visualization, audit |
| 2026-07-14 | HiveApp remains one organized monolith | Company/product direction | Entire backend architecture |
| 2026-07-14 | One active client Account membership per user | Members are employer-managed workers, not users managing multiple personal workspaces | Identity, membership, invitations, request context, B2B, client UI |
| 2026-07-14 | Account owner automatically has every permission available within their Account | The owner is the tenant authority root and must be able to do anything another Account member can do | Permissionizer policies, owner invariant, roles, overrides, delegation, member security |
| 2026-07-14 | Each Account has exactly one owner and ownership changes only through a protected transfer | A single final authority avoids ambiguous co-owner control while managers can receive broad delegated permissions | Account/member model, ownership transfer, Permissionizer, sessions, audit |
| 2026-07-14 | Ownership is protected status, not a grantable permission | Managers may manage ordinary permissions but must never modify the owner or assign ownership to another member | Permissionizer grantability, permission pickers, roles, overrides, ownership transfer |
| 2026-07-15 | Use generic nested Groups instead of hard-coded Department/Division/Team entity types | Customer-created folder-like structures can mirror simple or complex organizations without code-defined semantics | Organization Group model, parent tree, Company setup, directory UI, migrations |
| 2026-07-15 | Allow multiple Group memberships with a separate position/title on each membership | One member may hold different organizational positions in several Groups | Group membership model, member creation/detail, directory UI, history |
| 2026-07-15 | Auto-create a deletable `Departments` Group and support reusable whole-structure templates with sibling-name uniqueness | Provides a useful default without forcing it, while templates speed customer setup safely | Company initialization, Group templates, validation, conflict preview, atomic creation |
| 2026-07-15 | Keep Groups outside automatic authorization and defer the explicit manager-target building block | Organizational mirroring must stay simple while future target-aware management remains possible and auditable | Groups, target-aware policies, Permissionizer integration, management UX/audit |
| 2026-07-15 | Make Company deactivation reversible while allowing a separate Account-owner permanent purge with full impact disclosure | Operational closure should preserve history, but the customer owner must retain an explicit path to destroy Company-owned data safely | Company lifecycle, plan quota, authorization context, B2B, all module deletion policies, export, audit |
| 2026-07-15 | Require Company name and country initially, then use one edit permission with field-sensitive safeguards | Company setup should stay simple while legal identity changes remain understandable and auditable without permission proliferation | Company DTOs, validation, update service, impact checks, audit, payroll/accounting integration |
| 2026-07-15 | Use DRAFT, ACTIVE, INACTIVE, and terminal ARCHIVED plan states while protecting the configured default plan | Admins need distinct unfinished, sellable, paused, and historical behavior without silently changing subscriber snapshots or breaking provisioning | Plan schema/lifecycle, admin UI, subscription catalog, provisioning invariant, audit |
| 2026-07-15 | Revise active commercial plans through new drafts and require explicit target-aware subscriber changes | What is offered to future customers must stay separate from what existing Accounts already purchased, while admins still need controlled renewal and plan-change tools | Plan revisions, snapshots, impact preview, renewal policy, selected/bulk plan changes, notifications, audit |
| 2026-07-15 | Provide composable plan operations, including immediate/renewal/scheduled changes, instead of hard-coded marketing strategies | Operators own commercial decisions while the platform supplies reusable actions, communication, preview, execution, and safety boundaries | Plan/subscription operations, renewal, bulk change jobs, communications, documentation terminology, audit |
| 2026-07-15 | Represent plan features as included or optional add-ons, with absence meaning unavailable, and separate current-subscriber removal from plan editing | Commercial composition needs explicit meaning while changes to already purchased access must remain deliberate and auditable | PlanFeature model, registry validation, dependencies, subscription snapshots, entitlement-removal operations, UI/audit |
| 2026-07-15 | Keep features price-free globally, allow explicit plan feature blocks, and defer the complete bundle/add-on/quota pricing model | Prices and eligibility depend on the particular plan/module/subscription offer; premature schema choices would hard-code an unfinished commercial strategy | Plan feature exclusions, module bundles, add-ons, quota pricing, billing, snapshots, future admin UI |
| 2026-07-15 | Hard-delete only unused drafts and archive every plan with customer/business history | Mistakes should be removable without destroying purchased terms, registry definitions, or customer data | Plan danger zone, dependency preview, default-plan protection, archive, transactional deletion, audit |
| 2026-07-15 | Keep plan codes immutable/reserved after use and create duplicates/revisions as traceable new drafts | Stable commercial identity and explicit lineage prevent customer history from being confused with mutable copies | Plan code validation/reservation, duplication, revision lineage, draft workflow, admin UI/audit |
| 2026-07-15 | Make current-subscriber feature removal a targetable, schedulable entitlement operation separate from data purge | Operators need direct and soft access-removal tools without normal plan editing silently changing customers or deleting their records | Subscriber entitlement jobs, feature impact contributors, grace/exceptions, restricted state, restoration, purge boundary, audit |
| 2026-07-15 | Build account-level and bulk subscriber changes as versioned, history-preserving operations with partial-success tracking | Operators need powerful direct/scheduled actions without overwriting concurrent customer changes or hiding failures | Plan subscriber view, lookup, impact preview, bulk jobs, locks, retries, correction flow, audit |
| 2026-07-15 | Model Account exceptions and subscription cancel/suspend/expire/restore as explicit source-aware states | Customer-specific access and lifecycle actions must remain understandable, reversible where allowed, and separate from data purge | Overrides/exceptions, subscription state machine, restricted access, sessions/B2B, history/audit |
| 2026-07-15 | Keep client subscription self-service inside explicit Account authority and real commercial confirmation | A client-facing button must not fabricate payment or allow arbitrary internal/unlimited/negotiated entitlement | Subscription visibility/management permissions, pending commercial changes, sellable-option validation, audit |
| 2026-07-15 | Use provider-controlled Company share links and a complete history-preserving B2B lifecycle | External collaboration must be discoverable without global Company leakage and remain understandable through rejection, suspension, and revocation | B2B discovery, requests, lifecycle, grants, notifications, audit |
| 2026-08-10 | Include `SUSPENDED` in the single live collaboration slot | A suspended relationship retains grants and can resume; releasing the slot could allow a newer relationship that collides when the suspended one resumes | Collaboration state model, live-tuple database constraint, request retries, lifecycle UI and tests |
| 2026-08-13 | Create platform operators outright instead of promoting existing users, and never import identities from the client pool | Selection implies a trusted pool, and client members are not one; the two sides carry different trust levels, so they are different identities even for the same human | `users.kind` discriminator, operator creation endpoint, candidate search removal, operator credentials, create-operator UI |
| 2026-08-13 | Keep one `users` table for both sides rather than separating client and platform identity tables | Email uniqueness is a database guarantee on one table; splitting downgrades it to a cross-table application check that can race, and would duplicate the credential state machine — the copy that misses the next fix | Identity model, credential lifecycle, uniqueness enforcement, `AdminUser` invariant |
| 2026-08-13 | Default operator creation to admin-side email activation while requiring an explicit temporary-password alternative for non-receiving login addresses | Operators now have a dedicated activation endpoint that does not depend on client membership; explicit selection avoids emailing placeholders and never creates two live credentials together | Operator creation API/UI, activation and temporary-password completion, email verification/recovery, tests |
| 2026-08-10 | Company share codes do not expire automatically and are not credentials | A code identifies a Company but grants no access; the provider must still accept each request, so it remains valid until disabled/regenerated and only its SHA-256 hash is stored | Share-code persistence, discovery/request APIs, provider usage metadata, security documentation and tests |
| 2026-08-10 | Return `201 Created` whenever collaboration initiation creates a row and `200 OK` only when it returns an identical existing row | Callers must be able to distinguish creation from idempotent retrieval consistently, including the concurrent uniqueness race | Collaboration initiation API, controller outcome mapping, client retry handling and concurrency tests |
| 2026-07-15 | Require both provider delegation and external-member authorization for every B2B action | An Account-level grant must not give every external employee the ability to use it | Permissionizer B2B policies, provider delegation ceiling, external operator roles, runtime revalidation |
| 2026-08-10 | Reuse the external Account's existing Account-scoped role/exception resolver as the immediate B2B operator ceiling, with Collaboration-scoped assignment left as an explicit refinement | The immediate ceiling closes account-wide delegation to every employee and preserves owner, active-role, expiry, and deny precedence; a Collaboration effect scope is still needed to nominate external operators without widening their internal Company authority | B2B runtime policy, Account roles and direct exceptions, future Collaboration-scoped assignments, operator settings UX and tests |
| 2026-08-10 | Order audited mutation advice as transaction → audit → Permissionizer → method | Success history must share the mutation transaction while denied and failed mutations remain catchable by the audit layer; making transaction advice outermost also places Permissionizer policy database reads inside the caller transaction | Spring transaction configuration, audit aspect, Permissionizer advisor interaction, policy reads and advisor-order integration tests |
| 2026-07-15 | Offer both now and at-renewal timing for client upgrades and downgrades | Timing is an operator/customer choice; actual feature/quota impact, not the plan label or price direction, determines required safeguards | Client plan-change preview, pending renewal operations, conflict handling, Account locking, history/audit |
| 2026-07-15 | Defer permission-code rename/removal migration machinery and treat annotation codes as stable | Permission codes have no expected normal reason to change after a function is guarded; adding aliases/replacement flags to Permissionizer is premature | Registry retirement flow, Permissionizer scope, future developer migrations |
| 2026-07-15 | Separate public visibility, new-sale availability, new-grant availability, and emergency runtime shutdown | Hiding or discontinuing a feature must not accidentally change current customer access, while operators still need a deliberate emergency stop | Feature registry lifecycle, catalogs, plan validation, grant validation, runtime authorization, communications/audit |
| 2026-07-15 | Make registry synchronization validated, atomic, inspectable, and single-writer across application nodes | A partial Permissionizer scan or half-completed seed can create an ambiguous security catalog even when startup appears successful | Feature/permission collection, startup synchronization, database locking, sync reports, CI/deployment validation |
| 2026-07-15 | Use one versioned registry snapshot with separate audience-specific DTO catalogs and action-level eligibility | Public sales, plan composition, client roles, admin roles, and B2B delegation need different safe views without inventing different sources of truth | Registry DTOs, catalogs/pickers, grant validation, stale-write handling, caches, UI reasons |
| 2026-07-15 | Use Plan plus admin-created AddOns plus predefined quota packages as the commercial foundation | Customers need to combine a base plan with whole modules or custom feature bundles and chosen capacity without pricing technical features globally | Plan/AddOn model, feature/module composition, quota packages, subscription snapshots, change previews, pricing |
| 2026-08-22 | Separate permanent AddOn publication from reversible sales availability and revise published AddOns through new drafts | Returning a sold definition to draft would rewrite what customers bought, while treating draft and inactive as interchangeable leaves no safe editing path; lineage revisions preserve both operator flexibility and purchased snapshots | AddOn lineage/revisions, lifecycle API/UI, subscription catalog, audit and tests |
| 2026-07-16 | Use explicit feature-owned quotas with fixed limits, predefined packages, and operator exceptions | Capacity must be measurable, safely enforceable, and commercially understandable without arbitrary client overrides or ambiguous null/unlimited values | Quota definitions, Plan/AddOn configuration, packages, usage contributors, exceptions, snapshots, UI/enforcement |
| 2026-07-16 | Use one-currency exact monthly/yearly price books, immutable itemized price versions, and separate invoice/payment records | Configured recurring price and entitlement changes must remain distinct from actual money collection while leaving a safe path to real billing | Money/prices, Plan/AddOn/package versions, subscriptions, invoices, payments/refunds, renewal/past-due, admin UI/audit |
| 2026-08-31 | Distinguish operator suspension from collection suspension and never restore collection failure by status flip | Operator intervention may be reversible within the paid term, while collection recovery requires settlement or an explicit reviewed grace extension | Subscription lifecycle, recovery, admin actions, client state, audit |
| 2026-08-31 | Revoke all Account client sessions on subscription suspension or immediate cancellation and never resurrect them on restoration | Commercial access removal must take effect immediately; restoring entitlement must require fresh authentication rather than reviving a bearer token issued under the old state | JWT access/refresh sessions, subscription lifecycle, Account access, tests |
| 2026-08-31 | Treat terminal subscription restoration as a new reviewed entitlement operation | A cancelled or expired historical subscription is immutable commercial evidence; reopening it would bypass current product, pricing, usage, and settlement validation | Trial/new-entitlement creation, cancellation, history, commercial previews |
| 2026-08-31 | Keep Account billing profiles editable but freeze Account and platform issuer identity on every issued Invoice | Subscription billing belongs to the Account, while later profile or platform-configuration edits must not rewrite historical document evidence | Account billing profile, Invoice snapshots, financial timeline, commercial documents |
| 2026-08-31 | Label printable V1 Invoice output as a commercial billing document until jurisdictional fiscal rules exist | A polished PDF/print layout does not by itself supply tax calculation, compliant numbering, legally required party fields, or jurisdiction validation | Invoice document API/UI, wording, completeness warnings, deferred tax/fiscal work |
| 2026-07-14 | Keep Member directly under Account with organizational Groups independent from Account/Company access scopes | Supports ordinary employees and agency staff without confusing organizational placement with authority | Member placement, Groups, scoped roles, B2B |
| 2026-07-14 | Treat member deactivation as reversible access suspension with retained history | Offboarding must stop access immediately without destroying audit or future business-module records | Member lifecycle, sessions, quota, credentials, roles, audit, future modules |
| 2026-07-14 | Members cannot deactivate their own employer-managed membership | Account membership represents administratively managed work access, not a personal workspace subscription | Member lifecycle, scoped administration, APIs, audit |
| 2026-07-14 | Use hierarchical Platform, Account, and Company role templates with separately scoped assignments | Reusable templates must support broad governance and local management without tying security to the Department organization tree | Role entities, assignments, Permissionizer policies, custom roles, UI, audit |
| 2026-07-14 | Version Platform templates and require explicit Account adoption with impact UX | Platform changes must not silently change customer permissions; customers need understandable control and preview | Platform role publishing, versioning, adoption, role diff UI, entitlement validation, audit |
| 2026-07-14 | Use active, inactive, and archived role lifecycle; hard-delete only never-used custom roles | Access changes must be immediate while historical assignments and audit remain trustworthy | Role lifecycle, assignments, permission resolution, deletion impact UI, audit |
| 2026-07-14 | Use roles normally and allow controlled direct GRANT/DENY exceptions | One-off access and separation-of-duty restrictions are useful but must remain scoped, expiring where appropriate, visible, and auditable | Member overrides, effective permissions, Permissionizer policies, access-detail UI, audit |
| 2026-07-14 | Count every non-deactivated member toward quota, including unactivated members | A directly created membership reserves a real workspace seat; historical deactivated members should not consume capacity | Member creation, quota enforcement, activation state, usage UI, concurrency tests |
| 2026-07-14 | Allow multiple role assignments per member and calculate permissions per requested scope | Agency and multi-Company work requires different authority in different contexts without global permission leakage | Member-role assignments, effective permission resolution, Permissionizer context, access-detail UI, tests |
| 2026-07-14 | Apply custom-role edits immediately to all assignments after impact preview; duplicate for staged rollout | Local custom roles need predictable live administration without the complexity of hidden versions | Role editing, effective permission resolution, impact UI, duplication, caches, audit |
| 2026-07-14 | Remove workspace invitation feature; create members directly | Membership is an employer-managed administrative action | Member creation, identity activation, registry, plans, backend APIs, tests, future UI |
| 2026-07-14 | Use email activation when available; otherwise use a one-time-visible temporary password with forced change | Email is optional, while members still need secure manager-provisioned initial access | Authentication, member creation, access reset, session restriction, audit, future UI |
| 2026-07-14 | Keep unused temporary passwords valid until first use or regeneration and manage them from the Account member flow | Temporary access belongs to an employer-managed member, not a separate password administration product | Account member detail, authentication, Permissionizer actions, audit |
| 2026-07-14 | Revoke all member sessions when access is explicitly reset | A credential reset may indicate lost or compromised access; existing sessions must not bypass it | Authentication, refresh tokens, Account member security actions, audit |
| 2026-07-14 | Allow email self-service recovery; require Account-managed reset when no email exists | Recovery must use an available verified channel without making email mandatory | Authentication, member detail actions, Permissionizer, audit, future UI |
