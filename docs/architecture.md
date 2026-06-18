# HiveApp Architecture: Modules, Features, Permissions, Quotas

This document is the current source of truth for how HiveApp models modules, features, permissions, quotas, plans, subscriptions, role grants, B2B delegation, registry management, and feature visibility. It is intended to be read before adding or changing any business capability in the backend or frontend.

The goal of the architecture is to keep the system strict, predictable, and secure while still giving developers a good experience. HiveApp should not depend on developers remembering scattered manual steps, and it should not depend on editable database flags as the primary security boundary. Code must clearly express what a feature is for, which surface it belongs to, what permissions exist under it, which quotas belong to it, and where it may be exposed.

## 1. Core Mental Model

HiveApp separates product capability from data entities. A feature is not created just because there is an entity such as `Plan`, `Company`, `User`, or `Subscription`. A feature exists because a capability is exposed to a specific kind of actor on a specific surface of the product.

This distinction matters because the same entity can participate in multiple security surfaces. For example, plan-related data is used by platform administrators when they manage the plan catalog, and also by client accounts when they view or change their subscription. Those are not the same feature. Platform plan catalog management is a control-plane capability, while subscription management is a client workspace capability.

The correct model is therefore:

```text
platform.plans        = platform control-plane plan catalog management
platform.subscription = client workspace subscription management
```

The incorrect model is:

```text
platform.plan = one mixed feature containing both admin plan editing and client subscription actions
```

This rule is non-negotiable. Security boundaries follow product surfaces and use cases, not database tables. If two use cases touch the same tables but have different actors, risks, or grant rules, they belong in different feature services. Shared implementation logic should be extracted into internal domain components that do not define a permission boundary.

## 2. Module, Feature, Permission

A module is the top-level product namespace. In the current platform, examples include `platform` and `identity`. A module is an ownership and grouping boundary. It is not billable, not grantable, and not directly checked for access.

A feature is a product capability inside a module. Examples include `platform.company`, `platform.staff`, `platform.plans`, `platform.registry`, and `platform.subscription`. Features are HiveApp business concepts, not Permissionizer concepts. Plans, quotas, role grant surfaces, B2B delegation rules, registry views, and frontend capability areas attach to features.

A permission is a concrete action inside a feature. Permissions are declared and enforced by Permissionizer. HiveApp uses Permissionizer to discover and enforce actions, but HiveApp decides what feature those actions belong to and whether that feature may be exposed to a plan, role, B2B collaboration, registry view, or frontend surface.

The accepted permission path shape is strict:

```text
<module>.<feature>.<action>
```

For example:

```text
platform.company.create
platform.staff.invite
platform.plans.update
platform.registry.update_status
```

Every permission must map to exactly one feature:

```text
platform.company.create -> platform.company
platform.plans.update   -> platform.plans
```

Deeper action paths are intentionally avoided because they make feature mapping ambiguous and fragile. A path such as `platform.roles.permission.grant` should instead be expressed as `platform.roles.grant_permission`. The feature remains `platform.roles`, and the action remains a single segment.

## 3. Permissionizer Boundary

Permissionizer owns permission declaration, generated permission constants, permission collection, permission resolution, and runtime permission enforcement. HiveApp owns modules, features, feature surfaces, quotas, plan composition, subscription overrides, role grant filtering, B2B delegation filtering, registry validation, and frontend capability exposure.

This boundary is important. Permissionizer should not know what a HiveApp feature is. Permissionizer should not decide whether `platform.plans` is a client feature, a control-plane feature, or a B2B-delegatable feature. Those are HiveApp business rules.

The bridge between the two systems is the strict path rule and startup validation. Permissionizer discovers permissions such as `platform.company.create`. HiveApp derives the feature code `platform.company`, verifies that the feature exists in code, verifies the feature belongs to the correct module, and then persists the permission linked to that feature in the registry database.

Runtime interception is enabled at the platform namespace root: `com.hiveapp.platform` is annotated with `@PermissionNode(key = "platform", guard = Guard.ON)`. This root is required for Permissionizer's Spring AOP interceptor to enforce the feature and action annotations beneath it. Permission denial is an authorization outcome and is translated to HTTP `403`, not an unhandled server error. Business failures use typed exceptions and the shared `ApiError` response shape so frontend flows can distinguish invalid input, denied authorization, expired lifecycle state, exhausted quotas, and missing resources.

## 4. Feature Surfaces

The `platform` module contains more than one kind of feature. Some features are part of the platform control plane. Others are part of the client workspace. They share the same module because they belong to the same product namespace, but they do not share the same security or UX surface.

Platform control-plane features include capabilities such as registry management, plan catalog management, platform subscription administration, and platform admin-user management. These features are used by platform administrators to operate HiveApp itself. They are not assignable to client plans, not grantable to client roles, and never delegatable through B2B.

Client workspace features include capabilities such as companies, staff, roles, invitations, B2B collaboration, and subscription self-management. These features are exposed to client accounts and may participate in plans, quotas, client roles, and possibly B2B delegation depending on their feature definition.

The accepted surfaces are:

```text
PLATFORM_CONTROL = platform admin/control-plane shell
CLIENT_WORKSPACE = tenant/client workspace
PUBLIC           = unauthenticated or pre-auth flows
SYSTEM           = background jobs and internal automation
```

Surface is a hard architectural boundary. It must be code-owned and type-safe. It must not be represented only by an editable database boolean such as `clientGrantable = true`. A database field may store the projected surface for registry viewing, filtering, and validation, but the source of truth must be code.

## 5. Type-Safe Feature Definitions

The database stores features generically in one `features` table, but backend code must use type-safe feature definitions. The accepted developer experience is to define a feature once as a small feature definition and then reference that feature from the service that implements the feature boundary.

Conceptually, a feature definition looks like this:

```java
public final class ProjectsFeature {
    public static final String CODE = "platform.projects";

    public static FeatureDefinition definition() {
        return FeatureDefinition.clientWorkspace(CODE)
            .displayName("Projects")
            .quota(PlatformQuotas.PROJECTS)
            .build();
    }
}
```

The feature service references that definition:

```java
@Service
@PermissionNode(key = ProjectsFeature.CODE, guard = PermissionNode.Guard.ON)
public class ProjectService extends ClientWorkspaceFeatureService {

    public ProjectService() {
        super(ProjectsFeature.definition());
    }

    @PermissionNode(key = "create")
    public ProjectDto create(...) {
        ...
    }
}
```

The developer should not also update a separate feature enum, a separate registry file, and a separate mapping table. The feature definition is the source of truth. The service class then binds that feature definition to the Permissionizer root. Startup validation must fail if the feature definition code and the class-level `@PermissionNode` key do not match.

This gives us two important guarantees. First, the feature declaration is colocated with the feature boundary. Second, the feature surface is expressed by the type of base service used. A `ClientWorkspaceFeatureService` cannot accidentally become a platform control-plane feature, and a `PlatformControlFeatureService` cannot be assigned to a client plan.

## 6. Feature Service Boundaries

One guarded feature service should represent one feature and one surface. A service that mixes platform control-plane actions with client workspace actions is not acceptable, even if those actions share repositories or entities.

The correct shape is:

```text
PlanAdminService    -> platform.plans        -> PLATFORM_CONTROL
SubscriptionService -> platform.subscription -> CLIENT_WORKSPACE
ProjectService      -> platform.projects     -> CLIENT_WORKSPACE
RegistryService     -> platform.registry     -> PLATFORM_CONTROL
```

The incorrect shape is:

```text
PlanService -> contains both platform-admin plan catalog edits and client subscription actions
```

An invitation illustrates a related boundary. Sending, listing, and revoking workspace invitations are authenticated `platform.invitations` feature actions. Validating or redeeming an invitation token is a public capability-token flow: authorization is possession and lifecycle validation of that token, not an already-held workspace permission. Therefore the public flow belongs in an unguarded `PublicInvitationService`, outside the guarded invitation feature service, while it still enforces token expiry, revocation, acceptance, and target workspace correctness.

When logic is shared, the shared logic belongs in an internal component such as `PlanPricingEngine`, `BillingCalculator`, `QuotaCalculator`, or a domain service that does not itself define the feature permission boundary. The feature services call those internal components after Permissionizer and HiveApp policies have authorized the feature action.

This rule protects the UX and the security model. If a service mixes surfaces, it becomes unclear which permissions may be shown in the client role UI, which permissions may be assigned to platform admin roles, which features may appear in plan management, and which actions may be delegated through B2B. The architecture avoids that ambiguity by splitting feature services by surface.

## 7. Registry Database Projection

HiveApp keeps generic registry tables for modules, features, and permissions. These tables are runtime projections of code-defined capabilities. They support admin views, plan composition, role screens, subscription overrides, B2B delegation screens, and validation reporting.

The database should not invent capabilities. Platform admins should not create arbitrary module codes, feature codes, or permission codes through the UI. If the codebase does not implement a feature, that feature should not exist as a usable product capability.

Admins may manage business state around discovered features. That includes lifecycle status, sort order, plan inclusion, add-on pricing, quota values per plan, subscription/account overrides, and controlled beta exposure where allowed. Admins must not manage hard code-owned facts such as feature code, module ownership, feature surface, quota schema keys, quota schema types, or permission-to-feature mapping.

For a newly discovered definition, the registry initializes a feature that is declared visible in the public catalog as `PUBLIC`, and initializes a non-public or control-plane feature as `INTERNAL`. Once a feature row exists, startup synchronization must preserve its administrator-managed status.

The database can show that a feature is `CLIENT_WORKSPACE` or `PLATFORM_CONTROL`, but it cannot be the thing that decides it. Code decides the surface. DB state is used for inspection, filtering, auditing, and runtime joins.

## 8. Seeder And Validation Flow

The target seeding flow starts from feature definitions contributed by Spring-managed feature services or feature contributors. The seeder collects feature definitions, creates or updates module rows from module codes, creates or updates feature rows from feature definitions, syncs quota schema from type-safe quota definitions, then links Permissionizer-collected permissions to declared features.

The permission seeder must validate every collected permission. A permission path must have exactly three segments. The first segment is the module code. The first two segments form the feature code. The third segment is the action. The derived module and feature must exist, and the feature must belong to the module.

Invalid registry mappings fail startup. A skipped action permission is not a harmless warning. It means the code knows about an action that the business registry, plans, roles, B2B delegation, and UI do not know about.

The final state should never silently skip a permission because the feature is missing. The correct behavior is to reject the application startup so the developer fixes the feature declaration or permission path.

The current implementation has moved the platform shell onto code-owned `FeatureDefinition` contributors and has removed the old `AppFeature`/`FeatureProvider` registration path. `FeatureSeeder` now reads feature definitions from the collector. The collector rejects duplicate feature codes, rejects guarded feature services that contribute more than one feature definition, and verifies that an annotated service's resolved Permissionizer root equals its feature definition code. `PermissionSeeder` persists strict three-segment action permissions while ignoring structural Permissionizer nodes such as `platform` and `platform.company`; malformed action paths, missing feature definitions, module ownership mismatches, and persisted permissions linked to the wrong feature now fail startup.

## 9. Quotas

Quotas follow the same philosophy as features. The database stores quota schema and quota values generically, but code must use type-safe quota definitions.

A quota belongs to exactly one feature. A quota key is unique within that feature. A plan may define default values for a feature's quotas, and a subscription may override those values for a specific account.

Conceptually, quota definitions look like this:

```java
public final class PlatformQuotas {
    public static final QuotaDefinition STAFF_MEMBERS =
        QuotaDefinition.count("members", "Members");

    public static final QuotaDefinition COMPANY_COUNT =
        QuotaDefinition.count("companies", "Companies");

    public static final QuotaDefinition PROJECTS =
        QuotaDefinition.count("projects", "Projects");
}
```

The feature definition attaches quotas:

```java
FeatureDefinition.clientWorkspace("platform.staff")
    .quota(PlatformQuotas.STAFF_MEMBERS)
    .build();
```

The quota enforcer should prefer typed definitions over raw strings. Instead of checking `"platform.staff"` and `"members"` as loose values, code should eventually call the quota enforcer with a typed quota definition. This gives better developer experience and lets validation catch invalid quota usage at compile time or startup.

The business rule is that features define what can be limited, plans define default limits, and subscription overrides define account-specific exceptions.

Quota enforcement belongs inside the service boundary that creates the limited resource. In the current platform shell, company creation and member addition enforce workspace quotas there rather than relying on controllers. The seeded FREE entitlement permits one company and three total workspace members, including the owner. PRO permits five companies by default and supports priced administrator-applied increases; ENTERPRISE has no company limit. An exhausted quota is exposed as HTTP `402`.

## 10. Plans And Plan Features

Admin users manage the plan catalog. Client users manage their own subscription within the options the platform exposes. These are separate surfaces and should not be implemented as one mixed feature.

Platform admin plan management belongs to a control-plane feature such as `platform.plans`. Admins may create plans, rename plans, update descriptions, change price, change billing cycle, activate or deactivate plans, assign eligible client workspace features, remove features from plans, configure quota values, configure add-on availability and pricing, duplicate plans, and archive or deprecate plans.

Client subscription management belongs to a client workspace feature such as `platform.subscription`. Clients may choose a plan, upgrade or downgrade, enable allowed add-ons, disable add-ons, buy quota bumps, cancel subscriptions, and view billing state if the product allows those operations.

`PlanFeature` is the join between a plan and a feature. It answers whether a plan includes a feature, what the default quota values are for that feature on that plan, and whether the feature is available or priced as an add-on. A `PlanFeature` must reference a declared feature, and its quota configs must match the feature's quota schema.

A platform control-plane feature must never be assignable to a client plan. This is not protected by a DB status alone. It is protected by the code-owned feature surface. If someone attempts to mark `platform.plans` as public in the database, plan assignment must still reject it because `platform.plans` is a control-plane feature.

At runtime, client feature access requires a usable subscription. An `ACTIVE` or `TRIALING` subscription grants its plan and validated add-on entitlements only while `currentPeriodEnd` is null or in the future. A null period end represents a non-expiring entitlement such as the seeded FREE plan. If a period expires before lifecycle cleanup updates the stored status, feature-gated actions are denied.

The runtime entitlement decision is centralized in `PlanEntitlementService`. `PlanPolicy` uses that service to deny feature-gated requests when the account is not entitled. The same service is also used when returning effective client permissions from `/api/v1/me/permissions`, so the frontend does not receive permissions that runtime authorization would deny because of the account's plan, expired subscription, missing subscription, or invalid override state.

An administrator plan assignment is a subscription transition, not an additional simultaneous entitlement. Assigning a different plan cancels all existing `ACTIVE` or `TRIALING` subscriptions for the account before storing the new `ACTIVE` subscription with empty overrides. Assigning an inactive plan is rejected, as is assigning the account's already-active plan again.

The persistence invariant is now one usable subscription per account, where usable means `ACTIVE` or `TRIALING`. `Subscription.usableAccountId` is populated with the account id only for usable states, is constrained to match `account_id` for those states, is null for non-usable history, and is unique in the table. This represents the conditional uniqueness rule without preventing multiple cancelled or past-due history rows. Normal plan assignment also takes a pessimistic account lock and flushes cancellation before replacement insertion, so concurrent plan changes serialize cleanly while the database remains the final protection against invalid writes.

## 11. Subscription Overrides

Subscription overrides are account-specific changes over a plan. They may add an allowed feature beyond the base plan, remove or restrict an included feature for compliance or punitive reasons, override quota limits, override pricing, or grant controlled beta access when the feature definition allows it.

Overrides must be validated against code-owned feature and quota definitions. They cannot reference unknown features or unknown quota keys. They cannot enable platform control-plane features for client accounts. They cannot newly enable deprecated features unless the system explicitly supports grandfathering. Beta exposure must be explicit and auditable.

The subscription layer should not treat all features as equivalent strings. It must understand the feature surface and the feature's allowed use in client subscriptions.

## 12. Feature Management And Registry

Feature management is itself a control-plane capability. In the current platform namespace, it should be represented by a feature such as `platform.registry`.

The registry allows platform admins to inspect discovered modules, features, permissions, feature surfaces, lifecycle status, quota schemas, permission counts, plan usage, orphan mappings, and validation errors. It does not let admins invent capabilities that the code does not implement.

Example registry permissions are:

```text
platform.registry.read
platform.registry.validate
platform.registry.update_status
platform.registry.sync
```

The registry is how the platform operator understands the code-defined product surface. It is not a low-level table editor.

The current backend exposes code-owned registry read models for UI composition. `GET /api/admin/registry/feature-catalog` accepts a `FeatureCatalogAudience` query value and returns modules with feature definitions enriched by persisted registry state, quota schema, and persisted permissions. `PLAN_ASSIGNABLE` returns only features that code declares plan-assignable, so the plan editor cannot accidentally offer platform control-plane features. `PUBLIC_CATALOG` returns features whose code definition allows catalog exposure, with the current registry status included so the UI can distinguish public, beta, internal, and deprecated rows.

`GET /api/admin/registry/permission-catalog` accepts a `PermissionCatalogAudience` query value and returns modules with features and filtered permissions for permission picker screens. `CLIENT_ROLE_GRANTABLE` exposes only client workspace permissions, `PLATFORM_ADMIN_ROLE_GRANTABLE` exposes only platform control-plane permissions, and `B2B_DELEGATABLE` exposes only explicitly listed B2B actions such as `platform.company.read_single`. These read models are UX helpers. The write services still enforce the same rules through `PermissionGrantValidator` and billing validators, so hiding a permission in the picker is not treated as the authorization boundary.

Client-facing grant screens have their own smaller picker endpoints. `GET /api/v1/roles/permission-catalog` returns only permissions that the current account may grant to client workspace roles: the feature must be client-role grantable in code and currently entitled by the account's active subscription. `GET /api/v1/collaborations/{id}/permission-catalog` returns only permissions that the provider may delegate for that exact active collaboration: the caller must be the provider, the collaboration must be active, the feature must be entitled for the provider account, and the action must be explicitly listed as B2B-delegatable in the feature definition. These endpoints exist for good UX and safer API composition; they do not replace write-side validation.

The public/client feature catalog is exposed separately at `GET /api/v1/features/catalog`. It is safe for unauthenticated plan-comparison and product-catalog views. The response contains only module code, feature code, display name, description, lifecycle status, and quota schema. It intentionally excludes registry ids, permissions, grant flags, and feature surfaces. A feature appears only when code marks it `publicCatalogVisible`, the registry row is active, and the registry status is `PUBLIC` or `BETA`. A database status change cannot make a platform control-plane feature appear there because the code-owned feature definition remains the first filter.

## 13. Status And Lifecycle

Status is not the security boundary. Feature type and surface are the security boundary.

Status represents lifecycle or catalog state. The exact enum may evolve, but the accepted meanings are:

```text
ACTIVE/PUBLIC = available for normal use on allowed surfaces
BETA          = implemented but controlled by explicit beta exposure
DEPRECATED    = kept for existing usage, not newly assignable
DISABLED      = unavailable for new usage
```

The old `INTERNAL` label is ambiguous because it does not answer "internal to whom?" or "hidden from which surface?" If the current code keeps `INTERNAL` temporarily, it should be treated as a legacy or transition label. It must not be the primary security decision.

For example, `platform.plans` remains a platform control-plane feature even if a DB row says it is public. Likewise, a client workspace feature can be hidden from a catalog without becoming a control-plane feature. Surface and status answer different questions.

## 14. Role Grants

Role grant screens and services must filter permissions by feature surface and grant context.

The current backend has a `PermissionGrantValidator` for this boundary. Client role grants and direct member permission overrides must be owned by client-role-grantable `CLIENT_WORKSPACE` features. Platform admin role grants must be owned by platform-admin-role-grantable `PLATFORM_CONTROL` features. B2B grants must be owned by explicitly B2B-delegatable client workspace features. This validator is used by the role, member, admin-role, and collaboration services so the decision is not left to frontend filtering or editable database state.

Role and B2B permission writes also enforce subscription entitlement. A permission that is valid for the grant surface is still rejected when the corresponding feature is not enabled by the current account's plan. The picker endpoints use the same entitlement rule so the UI normally never offers denied grants, but the backend write path remains the final authority.

Admin role grants now use the unified registry `Permission` table through `AdminRolePermission`. The old separate `AdminPermission` entity is no longer the desired model.

Client role management may display and grant only permissions whose feature belongs to a client workspace grant surface. It must not show platform control-plane permissions such as `platform.plans.update` or `platform.registry.update_status`.

Platform admin role management may display and grant platform control-plane permissions. It should not blindly expose every client workspace permission unless the system intentionally supports that and has clear semantics for what a platform admin role is allowed to do.

This filtering must be derived from code-owned feature definitions and validated registry metadata. It must not be based on loose string prefix checks such as "anything under platform is safe for clients."

Platform admin delegation also has an actor ceiling. A non-SuperAdmin may grant or assign only permissions already held through active admin roles; publishing a feature or changing registry status does not bypass that ceiling. Only a SuperAdmin may create another SuperAdmin. An admin cannot deactivate their own account, and once another admin deactivates them, an existing admin JWT must no longer authenticate future requests. Reads of role or user detail are separately permissioned actions rather than an accidental side effect of listing access.

## 15. Current Platform Shell Feature Map

The current platform shell feature definitions are:

```text
platform.registry      -> PLATFORM_CONTROL
platform.plans         -> PLATFORM_CONTROL
platform.subscriptions -> PLATFORM_CONTROL
platform.roles         -> PLATFORM_CONTROL, admin role management
platform.admin_users   -> PLATFORM_CONTROL
platform.workspace     -> CLIENT_WORKSPACE
platform.company       -> CLIENT_WORKSPACE, B2B-delegatable only for read_single
platform.staff         -> CLIENT_WORKSPACE
platform.rbac          -> CLIENT_WORKSPACE, client workspace role management
platform.invitations   -> CLIENT_WORKSPACE
platform.subscription  -> CLIENT_WORKSPACE, client subscription read surface
platform.b2b           -> CLIENT_WORKSPACE
```

The names are intentionally surface-based. `platform.roles` is the platform admin role management feature. `platform.rbac` is the client workspace role management feature. `platform.subscriptions` is platform control-plane subscription administration. `platform.subscription` is the client workspace subscription view surface.

## 16. Hardening And Tests

The accepted architecture requires a comprehensive test suite, not a small set of happy-path checks. The backend hardening matrix is tracked in `backend/TEST_PLAN_PLATFORM_SHELL.md`.

The important testing principle is that every security boundary must be tested at the layer where it can break. Feature definitions and grant validation need fast unit tests. Query-dependent permission resolution needs repository or slice tests. JWT type separation, context detection, Permissionizer AOP enforcement, plan policy, B2B exact-collaboration checks, and cross-account ID abuse need full Spring integration tests.

The platform shell should not be considered complete until tests prove that platform-control features cannot be assigned to client plans, client role grants cannot receive platform-control permissions, B2B delegation cannot grant control-plane actions, quota checks live inside services, subscription state gates runtime access consistently, and cross-account IDs cannot be used to read or mutate another workspace.

## 17. B2B Delegation

B2B collaboration is a delegated permission ceiling between accounts. A provider may delegate only permissions that belong to actions explicitly designed for B2B delegation.

The provider account must have the feature enabled. The delegated permission must belong to a client workspace feature, and the specific action must be listed as B2B-delegatable in code. The provider actor must be authorized to delegate that permission. The client actor then receives only the delegated scope.

Platform control-plane permissions are never B2B-delegatable. It must be impossible to delegate plan catalog management, registry management, admin-user management, or other control-plane capabilities to a client through B2B.

B2B grants are scoped to an exact active collaboration, not globally to the provider account. A collaboration is bound to one provider company. If the same client collaborates with the same provider on two companies, each collaboration has its own permission set:

```text
Provider / Company 1 / Client X -> permissions A, B, C
Provider / Company 2 / Client X -> permissions D, E, F
```

A permission granted through the Company 1 collaboration must not authorize Company 2, even if the permission code is the same. Runtime checks must resolve the active B2B context, verify the exact collaboration id, verify the exact delegated permission, and verify the requested resource belongs to the collaboration's target company.

The current conservative company rule is:

```text
B2B allowed:   platform.company.read_single
B2B denied:    platform.company.create
               platform.company.read_all
               platform.company.update
               platform.company.delete
```

If B2B editing is later needed, it must be introduced deliberately. Either `platform.company.update` becomes B2B-delegatable after the allowed fields and audit expectations are defined, or a narrower action/service is created if the external-client workflow is materially different from normal company editing.

## 18. Runtime Enforcement

Runtime enforcement uses Spring Security, HiveApp request context, Permissionizer, and HiveApp policies together.

The request flow is:

```text
1. Spring Security authenticates the actor.
2. ContextDetectionFilter builds HiveAppPermissionContext.
3. HiveAppPermissionContextSupplier exposes that context to Permissionizer.
4. Permissionizer Spring AOP resolves the @PermissionNode action.
5. PermissionGuard evaluates policies with the HiveApp context.
6. HiveApp policies apply control-plane, plan, role, and B2B rules.
```

HiveApp's Spring Boot integration should use Permissionizer's Spring AOP path. HiveApp should not call `.withAutoGuard()` unless the system deliberately switches from Spring AOP to Byte Buddy instrumentation. The Spring path is the intended path for the backend.

The active implementation follows that rule: the platform package root turns guarding on for annotated feature services, HiveApp provides request context to Permissionizer through its Spring context supplier, and Byte Buddy auto-guard is not enabled.

## 19. Frontend Visibility

The frontend needs both enabled features and effective permissions.

Effective permissions returned to the frontend must be a product-usable set, not merely a role/owner set. Account owners may bypass workspace role grants, but they do not bypass billing entitlement. Therefore an owner permission payload includes only client-workspace permissions whose feature is currently entitled for the account. Non-owner permission payloads are built from roles and direct overrides, then filtered by the same entitlement service.

The effective-permissions endpoint is implemented as a read model outside guarded feature services. It must not live inside `platform.staff` or another guarded feature service, because the frontend must be able to ask "what can I use?" even when a specific feature area is not entitled. Authentication and context detection still protect the endpoint; Permissionizer feature checks protect the actual feature actions.

Feature availability controls broad capability areas. If `platform.b2b` is not enabled for the current account, the B2B area should not appear as an available workspace capability. Permission availability controls actions inside available areas. A user may have access to the staff feature but still lack `platform.staff.invite`, so the invite action must be hidden or disabled.

Frontend feature visibility is UX, not authorization. Backend policies remain the authority. The frontend should never assume that hiding a navigation item or button is sufficient security.

Public feature catalog data comes from `GET /api/v1/features/catalog`. Authenticated workspace capability data still comes from effective permissions and entitlement-aware APIs such as `/api/v1/me/permissions`. The public catalog answers "what capabilities may be shown or marketed?" It does not answer "what can this actor do right now?"

## 20. Tests And Validation

The architecture requires tests and startup validation that prove the registry is coherent.

There must be validation that every collected Permissionizer permission maps to a declared feature, that every permission follows `<module>.<feature>.<action>`, that every feature maps to a declared module, and that every feature service's class-level Permissionizer root matches its feature definition code.

Plan tests must prove that `PlanSeeder` and plan admin management use only client-plan-eligible features, and that all quota configs use declared quota keys. These checks must be covered both where the billing validator is unit-tested and where the administrator HTTP API receives real plan-feature assignment and update requests. Subscription override tests must prove that overrides cannot reference unknown features, unknown quota keys, duplicate quota keys, non-quota features, control-plane features, unavailable lifecycle states, or negative limits.

Role tests must prove that client roles cannot grant control-plane permissions. Admin role tests must prove that platform admin permission management uses the platform-control grant surface intentionally. B2B tests must prove that non-delegatable and control-plane permissions cannot be delegated.

Beta and deprecated behavior must also be tested. A beta feature should not appear in public/client catalogs unless explicitly exposed, and a deprecated feature should not be newly assigned unless the system is intentionally grandfathering an existing customer.

## 21. Non-Negotiable Invariants

Permissionizer defines and enforces permissions. HiveApp defines and manages modules, features, quotas, plans, subscriptions, business surfaces, and registry rules.

Permission paths must follow `<module>.<feature>.<action>`. Every permission maps to exactly one declared feature. Every feature belongs to exactly one module. Every quota belongs to exactly one feature.

Features are surface/use-case driven, not entity driven. Control-plane and client-workspace actions must not live in the same guarded feature service. Database state cannot turn a control-plane feature into a client-plan feature, a client-role feature, or a B2B-delegatable feature.

After migration, invalid registry mappings should fail startup. A skipped permission is a broken registry, not a normal state.

## 22. Migration From Current Code

Some older CDC/spec docs still contain assumptions that have since changed, including the old `AppFeature` provider path and the idea that feature status alone controls exposure. The current backend has moved platform shell features to code-owned `FeatureDefinition` contributors, and `PermissionSeeder` now validates the strict action shape while preserving structural Permissionizer nodes as non-persisted parents.

The target architecture is stricter. Feature definitions are code-owned and type-safe. Feature services reference one feature definition. The class-level Permissionizer root equals the feature code. Permission leaves are exactly one action segment. Seeders validate and fail instead of skipping. Feature surface/type controls plan assignment, role grants, B2B delegation, registry filtering, and frontend capability exposure.

The platform shell migration from the old enum/provider path to code-owned feature definitions and typed feature service bases has been completed in the current backend. New capabilities must use the typed definition path and must not reintroduce an alternate feature registry. Strict startup validation is required; action permissions with missing or mismatched feature ownership are not warning-only conditions.
