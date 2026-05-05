# PLAN1: Backend Feature Architecture Implementation Plan

This plan converts the accepted HiveApp architecture into backend implementation steps. It focuses on the developer-facing feature model, the business-facing feature registry, permission mapping, quota typing, plan composition, role grants, B2B delegation, and security validation. This file is also used as an implementation ledger; completed work is marked where the backend has already moved from planned architecture to code.

The target state is documented in `docs/architecture.md`. This plan describes how to get there safely from the current backend implementation.

## 1. Goal

HiveApp currently has a partial registry system where features are declared through `AppFeature` enums/providers, permissions are collected from Permissionizer, and `PermissionSeeder` links permissions to features by dropping the last path segment. This has already shown a major flaw: many generated permissions are skipped because their expected feature rows do not exist.

The new implementation must make the feature model harder to misuse. A developer adding a feature should define that feature once, bind it to the guarded service that implements the feature boundary, and let the platform seed, validate, and expose it consistently.

The backend must enforce these rules:

```text
Feature definitions are code-owned and type-safe.
Feature services are surface/use-case driven, not entity driven.
Permissionizer owns permissions, not HiveApp features.
Permission paths must be <module>.<feature>.<action>.
Every permission must map to exactly one declared feature.
Every feature belongs to exactly one module.
Every quota belongs to exactly one feature.
Control-plane features cannot become client-plan/client-role/B2B features through DB state.
```

## 2. Current Problems To Fix

The current backend has these architectural issues:

```text
Feature declarations were separated from the services that implement the feature boundary. This has now been replaced for platform shell services by service-bound FeatureDefinition contributors.
FeatureSeeder depended on AppFeature providers/enums rather than service-bound feature definitions. The old AppFeature/FeatureProvider bridge has now been removed.
PermissionSeeder derived feature code by dropping the last segment, which failed for shallow parent/root nodes and deep permission paths. It now enforces strict action paths and skips only structural nodes.
Invalid action permission mappings now fail startup rather than leaving partial registry state.
Feature status is treated too much like a security/visibility boundary.
Client workspace and platform control-plane surfaces are not strongly modeled.
Plans, roles, B2B, and subscriptions do not yet share one strict feature eligibility model.
Quotas are not fully type-safe in code.
```

This plan fixes the backend foundation first, then migrates business flows onto it.

## 3. Phase 1: Add Feature Definition Primitives

Create the backend primitives that represent the code-owned feature model.

Suggested package:

```text
backend/src/main/java/com/hiveapp/platform/registry/definition
```

Implemented classes:

```text
FeatureDefinition
FeatureSurface
FeatureContributor
FeatureDefinitionException
```

`FeatureSurface` should model the hard product/security surfaces:

```text
PLATFORM_CONTROL
CLIENT_WORKSPACE
PUBLIC
SYSTEM
```

`FeatureDefinition` should contain at least:

```text
code
moduleCode
featureKey
displayName
surface
sortOrder
quotaSlots
defaultStatus/lifecycle
```

The `code` must have the shape:

```text
<module>.<feature>
```

The `moduleCode` and `featureKey` should be derivable from the code and validated. Do not allow a feature definition with more or fewer than two path segments.

`QuotaDefinition` should contain:

```text
key
label
type
unit
```

Quota keys are unique within one feature.

## 4. Phase 2: Add Typed Feature Service Bases

Add typed base classes/interfaces that bind services to feature surfaces.

Suggested package:

```text
backend/src/main/java/com/hiveapp/platform/registry/definition/service
```

Suggested classes:

```text
FeatureService
ClientWorkspaceFeatureService
PlatformControlFeatureService
PublicFeatureService
SystemFeatureService
```

The base type should expose one feature definition:

```java
public interface FeatureContributor {
    FeatureDefinition featureDefinition();
}
```

A conceptual service should look like:

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

The base class should enforce that the feature definition surface matches the base class. For example:

```text
ClientWorkspaceFeatureService requires FeatureSurface.CLIENT_WORKSPACE.
PlatformControlFeatureService requires FeatureSurface.PLATFORM_CONTROL.
```

This is the first layer of developer safety.

## 5. Phase 3: Add Feature Definition Collection And Validation

Create a collector that gathers all Spring beans implementing `FeatureContributor`.

Suggested class:

```text
FeatureDefinitionCollector
```

It should collect definitions and validate:

```text
feature code is <module>.<feature>
feature code is unique
module code is present
feature key is present
surface is present
quota keys are unique within each feature
no feature definition conflicts with another definition
```

It should also validate each guarded service's Permissionizer root:

```text
resolved class-level Permissionizer root == featureDefinition.code
guarded feature service contributes exactly one feature definition
```

If a service contributes `platform.projects`, then the resolved class root must be `platform.projects`. With the current package-root style, that usually means the package contributes `platform` and the service declares:

```java
@PermissionNode(key = "projects")
```

If the values differ, startup fails. If one guarded feature service contributes multiple feature definitions, startup also fails. Aggregate contributors may still exist for non-service definitions, but they must not be used as guarded feature boundaries.

During migration, this validation may first run only for new `FeatureContributor` services. Once all feature services are migrated, it should be mandatory for all guarded feature services.

Current status: the platform shell feature services now contribute `FeatureDefinition` instances through typed service bases. The collector validates duplicate feature codes, definition shape, guarded-service single-definition ownership, and root-to-definition alignment for annotated contributors.

## 6. Phase 4: Adapt FeatureSeeder

Update `FeatureSeeder` so the target source of truth is `FeatureDefinitionCollector`.

The seeder should:

```text
collect feature definitions
derive module rows from feature module codes
create/update module rows
create/update feature rows
sync feature surface into DB if a column exists
sync quota schema from typed quota definitions
preserve admin-managed lifecycle/status where appropriate
```

The previous `FeatureProvider`/`AppFeature` enum system has been removed for the platform shell. New features should not add another provider registry. A feature is declared by a `FeatureDefinition` class and contributed by the guarded service that owns the feature boundary.

Feature existence must come from code. Admins should not create arbitrary feature codes in DB.

## 7. Phase 5: Update PermissionSeeder To Enforce Strict Mapping

Update `PermissionSeeder` to validate strict permission paths.

For each Permissionizer-collected permission:

```text
permission path must have exactly 3 segments
moduleCode = segment 1
featureCode = segment 1 + "." + segment 2
action = segment 3
```

Then validate:

```text
module exists
feature exists
feature belongs to module
permission maps to that feature
permission is not orphaned
```

Invalid examples:

```text
platform
platform.company
platform.company.profile.update
platform.roles.permission.grant
```

Valid alternatives:

```text
platform.company.update_profile
platform.roles.grant_permission
```

Current enforced behavior:

```text
structural nodes such as platform and platform.company are skipped
strict action permissions must have exactly three segments
permissions whose derived feature is missing fail startup
features outside the module encoded in the permission path fail startup
persisted permissions linked to the wrong feature fail startup
```

The current "skipped permissions" state must not remain the final behavior.

## 8. Phase 6: Migrate One Control-Plane Feature First

Migrate a small control-plane feature first to prove the model.

Recommended first target:

```text
platform.registry
```

Why:

```text
It is clearly platform control-plane.
It already exists conceptually.
It is not client-plan assignable.
It is central to validating the rest of the registry.
```

Target shape:

```text
RegistryFeature
RegistryService extends PlatformControlFeatureService
@PermissionNode(key = RegistryFeature.CODE, guard = ON)
```

Permissions should follow strict shape:

```text
platform.registry.read
platform.registry.validate
platform.registry.update_status
platform.registry.sync
```

Avoid roots or action paths that collapse to `platform` or produce deep permission paths.

## 9. Phase 7: Migrate Client Workspace Features

After `platform.registry`, migrate client workspace features.

Initial candidates:

```text
platform.company
platform.staff
platform.roles
platform.invitations
platform.b2b
platform.subscription
```

Each feature should have:

```text
one feature definition
one primary guarded feature service boundary
class-level @PermissionNode matching feature code
method-level @PermissionNode actions with one segment
quotas attached through typed quota definitions if needed
```

Do not let entity boundaries drive the migration. For example, subscription self-management belongs to `platform.subscription`, while plan catalog management belongs to `platform.plans`.

## 10. Phase 8: Split Mixed-Surface Services

Review backend services and split any service that mixes surfaces.

Expected target examples:

```text
PlanAdminService       -> platform.plans        -> PLATFORM_CONTROL
SubscriptionService    -> platform.subscription -> CLIENT_WORKSPACE
RegistryService        -> platform.registry     -> PLATFORM_CONTROL
AdminUserService       -> platform.admin_users  -> PLATFORM_CONTROL
CompanyService         -> platform.company      -> CLIENT_WORKSPACE
MemberService          -> platform.staff        -> CLIENT_WORKSPACE
RoleService            -> platform.roles        -> CLIENT_WORKSPACE
InvitationService      -> platform.invitations  -> CLIENT_WORKSPACE
CollaborationService   -> platform.b2b          -> CLIENT_WORKSPACE
```

Shared logic should move to unguarded internal components:

```text
BillingCalculator
PlanPricingEngine
QuotaCalculator
RegistryValidator
```

The rule is:

```text
One guarded feature service = one feature surface.
```

## 11. Phase 9: Update Plan Management Rules

Plan management must use feature definitions, not loose strings or all registry rows.

Admin plan management should be allowed to:

```text
create plans
rename plans
change price
change billing cycle
activate/deactivate plans
assign eligible client workspace features
remove features
configure quota values
configure add-on prices/availability
archive/deprecate plans
```

But plan management must reject:

```text
unknown features
platform control-plane features
features whose lifecycle forbids new assignment
quota configs with unknown quota keys
quota configs on features without quota schema
```

`PlanSeeder` must use typed feature definitions for seeded plans.

Final rule:

```text
A client plan can include client-workspace plan-eligible features only.
```

Control-plane features such as `platform.plans` and `platform.registry` can never be assigned to a client plan, even if a DB status/flag is changed.

Current status: `PlanSeeder` seeds only plan-assignable feature definitions. `BillingConfigurationValidator` is used by `PlanAdminServiceImpl` for both assignment and update, rejects control-plane or unavailable features, rejects invalid quota schema references and negative values, and prevents feature identity changes through an update request.

## 12. Phase 10: Update Subscription Overrides

Subscription overrides must validate against feature definitions and quota definitions.

Allowed override types may include:

```text
add allowed feature beyond base plan
remove/restrict feature
override quota limit
override price
grant controlled beta access
```

Current status: `SubscriptionServiceImpl` now validates added features and quota overrides through `BillingConfigurationValidator` before persistence. Unknown features, control-plane features, invalid quota resources, unavailable feature registry state, duplicates, and negative values are rejected. Restrictive override semantics and controlled beta exposure remain product decisions if those capabilities are introduced.

Validation must reject:

```text
unknown feature code
unknown quota key
control-plane feature for a client account
deprecated feature unless grandfathered
beta feature without explicit beta exposure permission
quota override on a non-quota feature
```

Pricing recalculation must remain deterministic:

```text
base plan price
+ added feature prices
+ quota bump prices
- removed feature adjustments if supported
= current subscription price
```

## 13. Phase 11: Update Role Grant Rules

Client roles and platform admin roles use different permission surfaces.

Client role management:

```text
may show client-workspace permissions only
must reject control-plane permissions
must reject permissions whose feature is not enabled for the account where required
```

Platform admin role management:

```text
may show platform-control permissions
must be explicit if it ever exposes client-workspace permissions
must not rely on "platform.*" as a safe filter
```

This requires role grant APIs to resolve:

```text
permission -> feature -> feature surface
```

Then apply the correct grant-surface rule.

Current status: `PermissionGrantValidator` now enforces feature-surface rules for client role grants, member direct permission overrides, B2B delegation, and platform admin role grants. Admin roles now store grants against the unified registry `Permission` entity instead of a separate `AdminPermission` entity.

## 14. Phase 12: Update B2B Delegation Rules

B2B delegation is a permission ceiling from provider to client.

The backend must enforce:

```text
provider account has the feature enabled
permission belongs to a B2B-delegatable client workspace action
provider actor has authority to delegate
client receives only the delegated permission ceiling for the exact collaboration/company
control-plane permissions cannot be delegated
```

The B2B permission picker/API must never expose:

```text
platform.registry.*
platform.plans.*
platform.admin_users.*
other platform-control permissions
```

Current status: the backend validates B2B delegated permission grants with `PermissionGrantValidator.requireB2bDelegatable`. B2B delegation is action-specific. `FeatureDefinition` now stores explicit B2B action names, and `PermissionGrantValidator` checks the requested permission action instead of allowing every permission under a B2B-capable feature. `platform.company` currently exposes only `platform.company.read_single` for B2B. `create`, `read_all`, `update`, and `delete` are rejected as B2B grants.

B2B runtime access is also company-scoped. A B2B request is valid only inside the active collaboration resolved from the request context, and company access is limited to the collaboration's target company. The same client may collaborate with the same provider on multiple companies, but each collaboration has its own permission set. Granting permission on Company 1 must not authorize Company 2.

Remaining B2B expansion work:

```text
decide whether any write action should be B2B-delegatable
if writes are needed, define exact fields/actions and audit semantics before exposing them in UI
repeat action-level delegation and resource-scope checks for future B2B-capable features such as documents, invoices, or reports
build provider UI around company -> collaborator -> permission set rather than global client grants
build client UI around an explicit active provider/company context
```

## 15. Phase 13: Update Runtime Policies

After feature definitions and surfaces exist, update policies to use them.

Policy responsibilities:

```text
PlanPolicy:
  gates client workspace features by subscription/plan/overrides
  centralizes entitlement decisions through `PlanEntitlementService`

UserRolePolicy:
  grants actor permissions through owner/member/role/override rules

B2bCollaborationPolicy:
  grants only delegated B2B-safe permissions for the exact active collaborationId resolved in context

AdminPermissionPolicy:
  grants platform control-plane actions for platform admins using AdminRolePermission -> Permission
```

The policies must not treat all `platform.*` permissions as the same kind of thing.

Current status: runtime entitlement logic is centralized in `PlanEntitlementService`. `PlanPolicy` delegates to it, and `EffectivePermissionService` uses the same service before returning `/api/v1/me/permissions`. The effective-permissions read model sits outside guarded feature services so the UI can ask what is available without requiring entitlement to a specific feature area first. Account owners still bypass workspace role grants, but their returned permission set is filtered by the account's current entitlement. Non-owner role/override permissions are also filtered by entitlement before reaching the frontend.

## 16. Phase 14: Update Registry APIs

Registry APIs should expose the code-defined metadata clearly.

Admin inventory should show:

```text
module code
feature code
feature surface
feature lifecycle/status
quota schema
permission count
plan usage
validation status
orphan/missing mapping warnings during migration
```

Public/client catalogs should only show features allowed for that catalog/surface.

Registry management should not allow admins to create arbitrary module or feature codes.

Current status: the admin registry now exposes code-owned read models through `GET /api/admin/registry/feature-catalog` and `GET /api/admin/registry/permission-catalog`. Feature catalogs can be filtered for all features, plan-assignable features, or public-catalog-capable features. Permission catalogs can be filtered for all permissions, client-role-grantable permissions, platform-admin-role-grantable permissions, or B2B-delegatable permissions. The implementation enriches `FeatureDefinition` metadata with persisted registry state and permission rows, and unit tests prove the plan editor, admin-role picker, and B2B picker filters do not expose the wrong surface. Request-level endpoint coverage for these catalog APIs remains a follow-up.

## 17. Phase 15: Tests And Validation

Add tests in layers. This is not a small regression set; it is a platform shell hardening suite. The detailed matrix is tracked in `backend/TEST_PLAN_PLATFORM_SHELL.md`.

Registry validation tests:

```text
every collected permission maps to a declared feature
every permission follows <module>.<feature>.<action>
feature service @PermissionNode root matches feature definition code
duplicate feature definitions fail
duplicate quota keys fail
```

Seeder tests:

```text
features are seeded from FeatureContributor definitions
modules are derived from feature definitions
quota schema is synced
orphan permission warning/failure behavior works
```

Plan tests:

```text
PlanSeeder uses only eligible features
plan assignment rejects control-plane features
plan quota config rejects unknown quota key
```

Subscription override tests:

```text
override rejects unknown feature
override rejects unknown quota
override rejects control-plane feature
override handles beta/deprecated rules
```

Role grant tests:

```text
client role cannot grant platform-control permission
admin role uses platform-control permissions intentionally
permission picker filters by feature surface
```

B2B tests:

```text
cannot delegate control-plane permission
cannot delegate non-B2B feature permission
provider must have feature enabled
delegated ceiling is respected
```

Permissionizer integration tests:

```text
Spring AOP path works without .withAutoGuard()
PermissionGuard receives HiveAppPermissionContext from supplier
context-null scenarios are handled safely
```

Current status: focused unit tests now cover feature definition validation, collector duplicate detection, guarded-service root-to-definition validation, visibility-aware feature seeding that preserves existing admin status, strict permission seeding with startup failure for malformed/missing/mismatched mappings, permission grant surface validation, registry read-model filtering, plan and subscription billing configuration validation, runtime plan entitlement by subscription state and period expiry, frontend effective-permission entitlement filtering, administrator subscription transitions and inactive-plan rejection, role/admin-role grant rejection for wrong surfaces, quota evaluation and override precedence, member and company quota placement inside their services, exact B2B collaboration matching, invalid subscription override parsing, and representative `ApiError` response bodies. Request-level isolation, token-surface, member override, public invitation redemption, initial B2B abuse, seeded FREE quota exhaustion, PRO/ENTERPRISE company quotas, priced PRO administrator quota overrides, admin control-plane escalation, and concurrent administrator plan replacement cases now exist. Spring AOP guard enforcement is active at the `platform` package root; public invitation token acceptance is deliberately outside guarded feature services. Subscription persistence now enforces exactly one `ACTIVE` or `TRIALING` subscription per account by a checked unique usable-account slot. Further action-level B2B product restrictions remain a decision before expanding that UX.

## 18. Recommended Commit/Migration Order

Use small backend-only commits.

Suggested order:

```text
1. Add feature/quota definition primitives. Done for `FeatureDefinition`, `FeatureSurface`, and existing `QuotaSlot` usage.
2. Add typed feature service bases and collector. Done.
3. Bridge FeatureSeeder to new definitions while preserving old providers. Superseded; old providers were removed.
4. Migrate platform.registry as first control-plane feature. Done.
5. Update PermissionSeeder strict validation. Done; action mapping violations now fail startup.
6. Migrate client workspace features one by one. Done for the current platform shell services.
7. Switch PermissionSeeder from warnings to startup failure. Done.
8. Update plan management and PlanSeeder. Done for type-safe plan assignment and quota-config validation, including request-level proof that a published control-plane feature cannot become client-plan assignable.
9. Update subscription overrides and quota validation. Done for configuration validation, administrator plan transitions, persistence, unit-level override precedence, FREE/PRO/ENTERPRISE plus priced PRO override request behavior, and database-enforced uniqueness for concurrent usable-subscription writes. Restrictive/beta override semantics remain product decisions if introduced.
10. Update role grant filtering. Done for grant APIs through `PermissionGrantValidator`.
11. Update B2B delegation filtering. Done for the current platform shell: feature-surface validation, action-specific grant validation, and exact collaboration matching are enforced. New B2B product actions still require explicit feature-definition opt-in before UI exposure.
12. Add/expand tests and remove old AppFeature provider path. In progress; provider path is removed and the current shell now has request-level isolation, entitlement, B2B, invitation, and admin control-plane abuse coverage. Further tests must accompany newly introduced product flows.
```

The first implementation commit should not attempt to refactor all services. Build the infrastructure first, then migrate one small feature, then expand.

## 19. Definition Of Done

This architecture migration is complete when:

```text
No collected permissions are skipped.
Every permission maps to a declared feature.
Every guarded feature service references exactly one FeatureDefinition.
Every feature has a code-owned surface.
Control-plane features cannot be assigned to client plans.
Control-plane permissions cannot be granted to client roles.
Control-plane permissions cannot be delegated through B2B.
Quota configs and overrides reference declared quota definitions.
Registry API exposes validation state clearly.
Backend tests cover registry, plan, role, B2B, quota, and Permissionizer context behavior.
Old AppFeature enum/provider registration is removed or reduced to a compatibility shim with no new usage.
```
