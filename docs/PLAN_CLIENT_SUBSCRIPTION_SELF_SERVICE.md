# Client Subscription Self-Service Plan

Prepared on: 2026-06-21

This plan defines the work needed for clients to choose, preview, buy, and later edit their own subscription while preserving the stricter feature, quota, permission, and B2B rules already established in the backend.

The core distinction is that platform admins manage plan templates, while clients manage only their own account subscription. A client must never edit a `Plan` or `PlanFeature` row directly. A client change creates or updates that account's active subscription state after validation and price confirmation.

## Current State

Admin plan management exists. Platform admins can create plan templates, activate or deactivate them, assign code-owned features to plans, configure quota limits, configure feature add-on prices, and apply account-specific subscription overrides.

Client subscription self-service does not exist yet. Clients can view active plans and read their current subscription, but they cannot yet upgrade, downgrade, select add-ons, request quota bumps, preview price, confirm checkout, or schedule a change.

Feature and quota definitions are now code-owned enough to support this work. Plan assignment is validated against feature surfaces, lifecycle status, declared quota slots, duplicate quota configs, negative prices, and invalid quota values. Public catalog metadata also exists for safe plan comparison UI.

## Business Model

Admin plan templates are product catalog configuration. They define what HiveApp sells by default: plan name, base price, billing cycle, included features, default quotas, and optional add-on prices.

Client subscription edits are account-level purchasing decisions. A client can choose a plan, select allowed add-ons, request quota bumps, and confirm a resulting price. That decision affects only the client's account subscription and effective entitlement.

Active subscriptions should become explicit snapshots of what was bought. A mutable plan template should not silently rewrite existing customers' effective features, quotas, or price. Existing subscriptions should change only through an explicit client-confirmed change, admin-confirmed change, scheduled renewal migration, or a deliberate compliance action.

## Implementation Tasks

### 2026-06-21 - Subscription Snapshot Model Decision

Decide and document the persistence shape for active subscription snapshots.

The recommended model is to keep `Plan` and `PlanFeature` as admin templates, then add a subscription-owned snapshot layer. This can be normalized as `SubscriptionFeature` and `SubscriptionQuota`, or stored as a typed JSON snapshot if faster implementation is preferred. The snapshot must capture selected features, selected add-ons, effective quota limits, prices used for calculation, and source plan code/version metadata.

Acceptance criteria:
- Existing subscription entitlement no longer depends only on mutable `PlanFeature` rows.
- The system can explain why a feature or quota is available to an account.
- A plan template edit does not accidentally grant or revoke access for existing active subscriptions.

### 2026-06-22 - Client Plan Catalog Read Model

Implement a client-facing plan catalog read model for the subscription page.

It should combine active plans, public feature metadata, included features, quota limits, add-on prices, billing cycle, current subscription state, and current usage where relevant. It should not expose platform-control features, internal feature surfaces, registry IDs, or raw permission lists.

Acceptance criteria:
- Clients can compare active plans from one endpoint.
- The response distinguishes included features from optional paid add-ons.
- Quota slots include current default limit, price-per-unit when configured, and whether the quota is unlimited.
- Public or beta display state comes from code-owned public catalog visibility plus registry lifecycle status.

### 2026-06-23 - Subscription Preview API

Implement a preview endpoint that calculates the result of a proposed client change without applying it.

Input should include target plan code, selected add-on feature codes, and requested quota limits. Output should include effective features, effective quota limits, current usage conflicts, recurring price, one-time or prorated notes if later supported, and whether the change can be applied immediately.

Acceptance criteria:
- Invalid feature codes, platform-control features, internal/deprecated features, and undeclared quota resources are rejected.
- Downgrades that would put current usage above the new limit are blocked or marked as scheduled-only according to the accepted rule.
- Price calculation uses admin-configured plan and add-on metadata.
- Preview and apply use the same validator/calculator so UI cannot preview one result and apply another.

### 2026-06-24 - Subscription Change API

Implement the apply/confirm endpoint for client subscription changes.

The first backend version may use an internal fake checkout confirmation instead of a payment provider, but it must still model the state transition clearly. Applying a change should lock the account subscription, validate the requested change again, cancel or supersede the previous usable subscription when needed, create the new active snapshot, and keep historical rows for audit.

Acceptance criteria:
- Only the current account can change its own subscription.
- A member must have the appropriate client subscription permission.
- Two concurrent changes cannot leave two usable active/trialing subscriptions.
- Same-plan no-op changes are rejected unless they change add-ons or quotas.
- Failed validation does not mutate the active subscription.

### 2026-06-25 - Downgrade, Add-On, And Quota Edge Cases

Implement explicit rules and tests for edge cases.

Required cases:
- Admin deactivates a plan: new clients cannot choose it, existing subscribers keep current snapshot.
- Admin changes plan price: existing subscribers keep snapshot price until explicit change or renewal migration.
- Admin removes a feature from plan template: existing subscriptions keep snapshot unless explicitly migrated or revoked.
- Client downgrades below current member/company usage: blocked or scheduled until usage is reduced.
- Client removes an add-on currently in use: blocked or scheduled until dependent usage is removed.
- Client increases quota: allowed immediately after price confirmation.
- Client decreases quota: allowed only if current usage fits, otherwise blocked or scheduled.
- Payment or confirmation failure: no entitlement change is applied.

### 2026-06-26 - Runtime Entitlement Migration

Move entitlement and quota resolution to the accepted subscription snapshot model.

`PlanEntitlementService`, `/api/v1/me/permissions`, quota enforcement, role permission picker, B2B delegation picker, and runtime B2B provider entitlement must all resolve from the same effective subscription state.

Acceptance criteria:
- Effective permissions and runtime policies agree.
- Quota enforcement and subscription preview agree.
- Existing B2B grants stop working if the provider's active subscription snapshot no longer includes the delegated feature.
- Client role permission picker hides permissions that the current snapshot does not entitle.

### 2026-06-27 - Request-Level Abuse Tests

Add integration tests for client subscription self-service.

Required cases:
- Client cannot change another account's subscription.
- Client cannot select inactive plan.
- Client cannot select platform-control feature as add-on.
- Client cannot select internal/deprecated feature.
- Client cannot request unknown quota resource.
- Client cannot set negative quota limit.
- Client cannot downgrade below current usage.
- Concurrent change requests serialize.
- Preview and apply return consistent price and entitlement results.

## Documentation Tasks

Update documentation alongside implementation, not after the fact.

Required documentation updates:
- `docs/architecture.md`: add the final subscription snapshot model and entitlement resolution rules.
- `docs/CLIENT_USER_STORIES.md`: replace the current "clients can only view subscriptions" rule once client self-service endpoints exist.
- `docs/ADMIN_USER_STORIES.md`: clarify that admin plan edits affect templates, while admin subscription operations affect a specific account.
- `docs/UI_SPEC.md`: update the subscription page from "upgrade links elsewhere" to real preview/apply flows after backend APIs exist.
- `backend/TEST_PLAN_PLATFORM_SHELL.md`: add covered/not-covered status for every client subscription self-service abuse case.
