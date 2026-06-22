# Client Subscription Self-Service Plan

Prepared on: 2026-06-21

This plan defines the work needed for clients to choose, preview, buy, and later edit their own subscription while preserving the stricter feature, quota, permission, and B2B rules already established in the backend.

The core distinction is that platform admins manage plan templates, while clients manage only their own account subscription. A client must never edit a `Plan` or `PlanFeature` row directly. A client change creates or updates that account's active subscription state after validation and price confirmation.

## Current State

Admin plan management exists. Platform admins can create plan templates, activate or deactivate them, assign code-owned features to plans, configure quota limits, configure feature add-on prices, and apply account-specific subscription overrides.

Client subscription self-service now exists as a first backend version. Clients can read a safe plan catalog, preview a proposed subscription change, and apply an immediately valid change to their own account. The backend still does not integrate a payment provider, model proration, or schedule future downgrades; those remain explicit follow-up product/payment tasks.

The implemented foundation is shared across runtime and UI read models: active subscriptions store an entitlement snapshot, and runtime entitlement, quota enforcement, billing recalculation, role pickers, B2B picker/runtime checks, and `/api/v1/me/permissions` resolve through that snapshot when it exists.

Feature and quota definitions are now code-owned enough to support this work. Plan assignment is validated against feature surfaces, lifecycle status, declared quota slots, duplicate quota configs, negative prices, and invalid quota values. Public catalog metadata also exists for safe plan comparison UI.

## Business Model

Admin plan templates are product catalog configuration. They define what HiveApp sells by default: plan name, base price, billing cycle, included features, default quotas, and optional add-on prices.

Client subscription edits are account-level purchasing decisions. A client can choose a plan, select allowed add-ons, request quota bumps, and confirm a resulting price. That decision affects only the client's account subscription and effective entitlement.

Active subscriptions are explicit snapshots of what was bought. A mutable plan template does not silently rewrite existing customers' effective features, quotas, or price. Existing subscriptions should change only through an explicit client-confirmed change, admin-confirmed change, scheduled renewal migration, or a deliberate compliance action.

## Implementation Tasks

### 2026-06-21 - Subscription Snapshot Foundation

Implemented the persistence shape and runtime read path for active subscription snapshots.

The current model keeps `Plan` and `PlanFeature` as admin templates, then stores a typed JSON snapshot on `Subscription.entitlementSnapshot`. The snapshot captures `planCode`, `basePrice`, feature codes, feature add-on prices, and quota limit entries. That is enough for the current backend to preserve effective access, quota limits, and pricing inputs independently from later plan-template edits. A normalized `SubscriptionFeature`/`SubscriptionQuota` model can still replace the JSON representation later if querying or audit requirements become stronger.

Completed:
- Existing subscription entitlement no longer depends only on mutable `PlanFeature` rows when a snapshot exists.
- `WorkspaceProvisioningServiceImpl` snapshots the FREE plan at registration.
- `SubscriptionServiceImpl` snapshots the selected plan on admin/client service-created subscription replacement.
- `PlanEntitlementService`, `QuotaEnforcer`, and `BillingCalculator` read from the snapshot first and fall back to legacy live plan data only for old rows without a snapshot.
- Integration coverage proves that removing a feature from a plan template does not revoke it from an existing subscription, while a new subscription created after the template change receives the reduced snapshot.
- Runtime B2B and effective-permission tests now mutate active subscription snapshots instead of live plan templates when testing loss of entitlement.

### 2026-06-22 - Client Plan Catalog Read Model

Implemented a client-facing plan catalog read model for the subscription page.

`GET /api/v1/subscriptions/catalog` combines active plans, public feature metadata, included features, quota limits, add-on prices, billing cycle, current subscription state, selected account overrides, and current quota usage where relevant. It does not expose platform-control features, internal feature surfaces, registry IDs, or raw permission lists.

Completed:
- Clients can compare active plans from one endpoint.
- The response distinguishes included features from optional paid add-ons.
- Quota slots include current default limit, price-per-unit when configured, and whether the quota is unlimited.
- Public or beta display state comes from code-owned public catalog visibility plus code-owned lifecycle status, and only active features are shown or offered for new self-service choices.
- Request-level tests prove that platform-control features are excluded and current usage is returned.

### 2026-06-23 - Subscription Preview API

Implemented a preview endpoint that calculates the result of a proposed client change without applying it.

`POST /api/v1/subscriptions/preview` accepts a target plan code, selected add-on feature codes, and requested quota limits. It returns effective features, effective quota limits, current usage conflicts, recurring preview price, selected add-ons, quota overrides, and whether the change can be applied immediately.

Completed:
- Invalid feature codes, platform-control features, internal/deprecated features, and undeclared quota resources are rejected.
- Downgrades that would put current usage above the new limit are marked as not immediately applicable with a `QUOTA_BELOW_USAGE` conflict.
- Price calculation uses admin-configured plan and add-on metadata.
- Preview and apply use the same validator/calculator so UI cannot preview one result and apply another.
- Request-level tests cover inactive plans, control-plane add-ons, internal features, and quota-below-usage conflicts.

### 2026-06-24 - Subscription Change API

Implemented the apply/confirm endpoint for client subscription changes.

`POST /api/v1/subscriptions/apply` is the first backend version of confirmation. It uses internal immediate confirmation rather than a payment provider. Applying a change locks the account subscription, validates the requested change again, rejects conflicts and no-op changes, cancels the previous usable subscription, creates a new active snapshot, recalculates price from the snapshot and overrides, and keeps historical rows for audit.

Completed:
- Only the current account can change its own subscription.
- A member must have the appropriate client subscription permission.
- Two concurrent changes cannot leave two usable active/trialing subscriptions.
- Same-plan no-op changes are rejected unless they change add-ons or quotas.
- Failed validation does not mutate the active subscription.
- Request-level tests cover successful replacement, conflict rejection without mutation, and concurrent changes leaving exactly one usable subscription.

### 2026-06-25 - Downgrade, Add-On, And Quota Edge Cases

Implemented the immediate-apply edge rules and tests that are available before payment-provider and scheduled-change support.

Current rules:
- Admin deactivates a plan: new client previews/apply attempts reject it, existing subscribers keep current snapshot.
- Admin changes plan price: existing subscribers keep snapshot price until explicit change or renewal migration.
- Admin removes a feature from a plan template: existing subscriptions keep snapshot unless explicitly migrated or revoked.
- Client downgrades below current member/company usage: preview reports a conflict and apply is rejected without mutation.
- Client removes an add-on or feature currently in use: preview reports `FEATURE_IN_USE` and apply is rejected.
- Client increases quota: allowed immediately after internal confirmation.
- Client decreases quota: allowed only if current usage fits; otherwise blocked.
- Payment or external checkout failure is not modeled yet because there is no payment provider integration.

### 2026-06-26 - Runtime Entitlement Migration

Move entitlement and quota resolution to the accepted subscription snapshot model.

`PlanEntitlementService`, `/api/v1/me/permissions`, quota enforcement, role permission picker, B2B delegation picker, and runtime B2B provider entitlement must all resolve from the same effective subscription state.

Current backend status:
- Effective permissions and runtime policies agree through `PlanEntitlementService`.
- Quota enforcement reads subscription snapshot quotas before legacy plan template quotas.
- Billing override recalculation reads snapshot base/add-on/quota prices before legacy plan template prices.
- Existing B2B grants stop working if the provider's active subscription snapshot no longer includes the delegated feature.
- Client role and B2B permission pickers hide permissions that the relevant active snapshot does not entitle.
- Subscription preview/apply endpoints now use the same validation, snapshot, usage-conflict, and billing calculation paths.

### 2026-06-27 - Request-Level Abuse Tests

Added integration tests for client subscription self-service.

Covered:
- Client catalog exposes safe plan data, hides platform-control features, and includes current usage.
- Client cannot select an inactive plan.
- Client cannot select a platform-control feature as an add-on.
- Client cannot select an internal feature.
- Client cannot downgrade below current quota usage; preview reports the conflict and apply does not mutate the subscription.
- Client apply creates a replacement snapshot and leaves one usable subscription.
- Concurrent change requests serialize to one usable subscription.
- Unit coverage covers additional validator cases such as selecting an included feature as an add-on and quota conflict calculation.

Still intentionally open:
- Cross-account mutation is structurally avoided because client endpoints derive `accountId` from `HiveAppContextHolder`; future endpoints that accept account IDs must add direct abuse tests.
- Payment-provider failure, proration, invoices, and scheduled downgrade behavior need tests when those product flows are implemented.

## Documentation Tasks

Update documentation alongside implementation, not after the fact.

Required documentation updates:
- `docs/architecture.md`: add the final subscription snapshot model, entitlement resolution rules, and client self-service endpoints.
- `docs/CLIENT_USER_STORIES.md`: replace the old "clients can only view subscriptions" rule with catalog, preview, and apply flows.
- `docs/ADMIN_USER_STORIES.md`: clarify that admin plan edits affect templates, while admin subscription operations affect a specific account.
- `docs/UI_SPEC.md`: update the subscription page from "upgrade links elsewhere" to real preview/apply flows.
- `backend/TEST_PLAN_PLATFORM_SHELL.md`: add covered/not-covered status for every client subscription self-service abuse case.
