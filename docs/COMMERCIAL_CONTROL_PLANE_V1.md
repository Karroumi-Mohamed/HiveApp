# HiveApp Commercial Control Plane V1

**Decision date:** 2026-08-26  
**Status:** canonical implementation target  
**Applies to:** platform-admin commercial operations, client subscription self-service, billing records, and commercial analytics

This document defines the commercial system HiveApp is building. `FLOW_DECISIONS.md` remains the product-decision ledger, `TOFIX.md` remains the gap register, and `IMPLEMENTATION_PLAN.md` remains the execution map. Older plan/billing CDC files are historical and must not override this document.

## 1. Product vocabulary

HiveApp keeps technical capability, commercial packaging, customer entitlement, capacity, and money separate.

| Concept | Owner | Meaning |
|---|---|---|
| Feature / permission / quota definition | Code and registry | What the application can do, what may be authorized, and what can be measured |
| Plan | Platform operator | The required base commercial product for an Account |
| Add-on | Platform operator | An optional sellable bundle of Features and included capacity |
| Capacity package | Platform operator | A priced increase to one code-defined quota already supplied by the Plan or an Add-on |
| Price-book entry | Platform operator | One immutable price for one product revision, currency, billing cycle, and effective window |
| Subscription snapshot | System | The exact products, versions, prices, entitlements, limits, and effective period accepted by one Account |
| Commercial policy | Authorized platform operator | A reasoned, time-bounded Account or audience exception that changes commercial availability, price, or capacity within platform safety boundaries |
| Segment | Platform operator | A reusable Account audience selected explicitly or through safe typed criteria |
| Campaign | Platform operator | The lifecycle and delivery container for one or more offers to a Segment or selected Accounts |
| Offer | Platform operator | A customer-facing proposed commercial change; it changes nothing until accepted or explicitly applied by an authorized operator |
| Invoice / payment / credit / refund | System and authorized operators | Financial records distinct from entitlement and price previews |

The commercial target is always the **Account**. Companies remain operational/legal scopes inside the Account and are not separate subscribers.

## 2. Composition and effective result

An Account purchases:

```text
one Plan
+ selected compatible Add-ons
+ selected compatible capacity packages
+ accepted or operator-applied commercial adjustments
= one immutable subscription snapshot
```

The effective commercial result is resolved in this order:

1. Plan entitlements, included capacity, and selected price.
2. Purchased Add-ons and capacity packages.
3. Account contractual terms and explicit commercial policies.
4. Accepted temporary or conditional offer effects.
5. Platform hard safety ceilings and registry availability.
6. Account governance restrictions.

Later layers may restrict an earlier result. No commercial layer may expose an internal/non-sellable Feature, bypass authorization, exceed a platform hard ceiling, or delete customer data.

## 3. Plan extension policy

Every Plan revision declares how it may be extended:

- `CLOSED`: no Add-on or capacity purchase beyond the Plan snapshot.
- `ALLOW_LIST`: only explicitly attached compatible Add-ons/packages may be sold.
- `OPEN_COMPATIBLE`: any active public extension that passes all compatibility rules may be sold unless explicitly blocked.

Mandatory compatibility is always computed and cannot be overridden by marketing:

- every supplied Feature is active, client-facing, plan-assignable, and commercially sellable;
- dependencies are recursively satisfied;
- exclusions and duplicate paid capabilities are rejected;
- included quota ownership does not overlap;
- a capacity package targets an already entitled quota;
- product currency and billing cycle match the selected subscription terms;
- all referenced product revisions are published and currently sellable.

Optional catalogue targeting narrows a technically compatible audience. It never makes an incompatible product valid.

## 4. Price books

Price is not a mutable field on a published product definition.

- A Plan, Add-on, or capacity-package revision may have independent `MONTHLY` and `YEARLY` prices in one or more supported currencies.
- A yearly price is entered independently; it is never inferred as monthly multiplied by twelve.
- A zero-price recurring entry is valid and still creates normal periods.
- Each price-book entry has `DRAFT`, `ACTIVE`, `INACTIVE`, or `ARCHIVED` lifecycle, an effective-from instant, optional effective-until instant, immutable amount/currency/cycle once activated, and actor-aware history.
- At most one active applicable entry exists for one product revision, currency, cycle, and instant.
- API monetary amounts use exact plain-decimal JSON strings with a separate ISO currency code. Browser code never converts authoritative amounts through IEEE-754 `number` arithmetic.
- New subscriptions select an active entry. Existing snapshots retain the selected entry forever unless an explicit subscription-change operation selects another.
- Disabling a price stops new selection; it never rewrites an existing snapshot.
- `FOREVER`, implicit foreign exchange, automatic tax, automatic proration, metered charging, and customer-selectable unlimited pricing remain unsupported until separately decided and implemented.

## 5. Commercial policies

Commercial policies are not permissions and are not arbitrary scripts. They are typed, auditable operations over a bounded target.

Target kinds are deliberately closed rather than user-authored expressions. Through Phase 11.1, the delivered target kinds are:

- one Account;
- an explicit set of Accounts;
- subscribers of a selected Plan revision;
- an exact active Segment revision/activation with an immutable Account audience.

A reusable typed Segment is the Phase 11.1 extension of this same target contract. It is implemented and independently audited. The Phase 11.2 Campaign backend is also implemented and independently audited; its admin UI, Offers, and redemption remain.

Delivered Phase 10 effects are:

- allow or block selection of a Plan/Add-on/package;
- fixed Money discount or percentage discount with an explicit maximum amount;
- fixed recurring subscription price;
- block a client Feature;
- additive finite quota bonus;
- grant a selected Add-on/package for a bounded period;

Included free periods and renewal instructions—continue, change to a selected snapshot, end at period end, or manual review—remain Phase 12 operation-engine work.

Every policy has a reason, owner, start/end, status, priority, source, optional approval/contract reference, preview, affected-set snapshot, and audit. Conflicting effects resolve deterministically; direct Account policy wins over Segment policy, restrictions win over grants at equal priority, and platform hard limits always win.

Policy activation requires a fresh backend preview. It approves and snapshots a reusable policy definition and its audience; it does not alter any subscriber. Editing an active policy creates a revision, and only a separately reviewed subscription preview/apply operation can accept an applicable policy into an Account's terms. Neither action rewrites evidence used for earlier subscriptions or invoices.

V1 deliberately keeps policy execution narrow and explainable:

- discounts reduce the subscription subtotal in one explicit currency; surcharges and arbitrary line-level adjustments are deferred;
- discounts do not stack: one winning compatible discount is selected by target specificity, priority, then stable policy identity;
- activation snapshots the exact Account audience, so later Segment membership changes do not rewrite an approved execution;
- an approved scheduled execution runs as `SYSTEM` from immutable activation evidence even if the initiating operator later loses access; changing or cancelling it still requires current permission;
- a policy start or end never silently rewrites an active subscription snapshot. It controls whether a new preview or scheduled operation may use the policy; already accepted terms change only through an explicit now/renewal/scheduled subscription operation;
- an expiring temporary product or quota grant therefore resolves through the declared subscription operation and remediation rules, never by deleting entitlement or customer data mid-request.

## 6. Segments, campaigns, and offers

Segments use typed fields only; they never accept SQL or expression code. V1 supports explicit Accounts and safe criteria based on commercial data such as current Plan, subscription state, currency/cycle, Account creation date, and current product holdings.

Safe Segments are implemented and independently audited as of 2026-08-27. Their signed activation freezes exact Account identities and provenance. An archived Segment cannot be selected for new work. `DRAFT`, `ACTIVE`, and `PAUSED` Commercial Policies that still reference it block archive; `ENDED` and `ARCHIVED` Policy history does not, because accepted Policy activations retain their own frozen audience and source Segment activation.

Campaign lifecycle:

```text
DRAFT -> SCHEDULED -> ACTIVE -> PAUSED -> ACTIVE -> ENDED -> ARCHIVED
```

The delivered Campaign backend owns one PUBLIC, explicit-Account, or exact active-Segment audience. Scheduling requires short-lived actor-bound review evidence and freezes targeted Account identities plus the selected Segment activation, catalogue/registry versions, evaluation window, fingerprint, and reason. PUBLIC deliberately stores no platform Account snapshot. Draft Segment references block Segment archive; after scheduling the immutable audience is self-contained, while retained Campaign provenance continues to block destructive Segment deletion.

An Offer lineage belongs permanently to one exact Campaign revision. It owns lineage-wide global/per-Account limits, permanent customer-code reservation, discovery mode (`CATALOG` or `CODE_ONLY`), and acceptance channel (`CLIENT_OR_OPERATOR` or `OPERATOR_ONLY`). Published revisions are immutable and pin exact Plan/AddOn/package and Price-entry identities plus typed compatible effects. The Campaign owns the audience and every Offer inherits it; a public Campaign does not snapshot the entire platform.

- A public offer appears only to eligible Accounts.
- A targeted offer is visible only to its snapshotted audience.
- A code is a discovery input, never a credential. Authenticated Account-scoped lookup returns one generic unavailable response and never records the raw code.
- An offer preview explains the resulting products, entitlements, quotas, billing cycle, adjustments, amount due, and effective time.
- Acceptance revalidates eligibility, price, capacity, and conflicts under an Account lock.
- Retries are idempotent. One Account cannot redeem beyond the configured limit.
- Pausing/ending a campaign stops new redemption; it never reverses completed subscription snapshots.
- A Phase 11 operator may apply an offer to one Account through the same previewed subscription-operation engine, with reason and exact outcome. Selected/filtered/bulk application belongs to Phase 12.
- Offer selection is explicit and opt-in. An Offer never becomes an always-applicable Commercial Policy and carries its own immutable redemption/provenance evidence.
- One combined commercial evaluation uses an applicable Policy fixed price as the base and compares the selected Policy and Offer discount by actual compatible monetary reduction. The larger wins without stacking; Policy wins a tie; restrictions and platform hard limits remain vetoes.
- Once published, a customer-facing code is globally reserved permanently.
- Global capacity is reserved when the subscription operation is created. Cancellation or failure before application releases capacity while retaining the historical redemption attempt.
- The Offer window controls new acceptance. A later discount reversion requires an explicit Phase 12 scheduled operation; expiry never silently rewrites accepted terms.
- Published Offer revisions are immutable. Retirement reversibly stops new redemption; archive is terminal.
- Initial Offer redemption supports current `ACTIVE` and `TRIALING` subscriptions. Recovery for other lifecycle states is Phase 12.

## 7. Subscription operations

Every subscriber-affecting action is an operation, not an edit to historical data.

Delivered for one Account through Phase 10:

- now;
- at the Account's next renewal;

The reviewed workbench stores the before/target snapshots, conflicts, exact accepted products and prices, policy provenance, actor, reason, and signed review evidence. It revalidates concurrent state under the Account and commercial lock order before applying.

Phase 12 extends this same operation contract with:

- execution at a scheduled instant;
- first-class reviewed trial and subscription-lifecycle commands;
- selected Accounts and immutable backend-resolved filtered populations;
- per-Account progress/results, resumable retry, cancellation cutoff, correction, and communication state.

The complete target vocabulary is therefore:

- one Account;
- selected Accounts;
- an immutable backend-resolved filtered population.

Bulk execution is transactional per Account, idempotent, resumable, cancellable before cutoff, and rechecks concurrent subscription changes; those job semantics are Phase 12 rather than a claim about the delivered one-Account surface.

No operation silently deletes data. Over-limit or removed-entitlement outcomes require remediation, grace, a time-bounded exception, or declared restricted behavior.

## 8. Billing records

Entitlement activation, price preview, amount due, settlement, and revenue are separate facts.

- An invoice is an immutable numbered commercial document with Account, currency, period, status, totals, and versioned lines derived from the subscription snapshot plus explicit adjustments.
- Invoice lines retain source product/price/policy identity and human-readable snapshots.
- A payment records a pending/succeeded/failed settlement attempt with idempotency key, method/provider type, external or manual reference, amount, actor/source, and timestamps.
- A manual settlement requires dedicated permission, evidence/reference, and reason.
- Credits and refunds are separate records linked to invoice/payment lines. They never mutate the original invoice or payment.
- Refund totals cannot exceed settled refundable amounts. Duplicate provider events and retries are idempotent.
- Only succeeded settlement contributes to collected-value analytics.
- Zero-amount invoices may settle automatically without creating a fake payment.
- Failed renewal enters `PAST_DUE`; grace and later restricted access are explicit policy/state transitions.

Tax calculation and provider-specific accounting remain extension points and are not presented as complete in V1.

## 9. Operational analytics

Dashboard numbers must be derived from durable facts, not mutable template prices.

V1 records/query models cover:

- active/trialing/past-due/cancelled Accounts over time;
- configured recurring value by currency and cycle;
- invoiced, settled, credited, and refunded values separately;
- Plan/Add-on/package adoption and churn;
- offer views, eligibility, acceptance, failure, and resulting subscription changes;
- policy audience size, execution results, and expiry;
- renewal success/failure and aging past-due Accounts;
- capacity-package adoption and Accounts near/over quota.

Every chart endpoint accepts a bounded time range, timezone, interval, and safe filters. It returns explicit series labels, currency/cycle dimensions, completeness timestamp, and no mixed-currency total. Operational tables remain the drill-down source behind summary metrics.

## 10. Security and audit

- Every read/mutation surface has a distinct Permissionizer node; sensitive identity and settlement evidence use narrower permissions than ordinary list access.
- UI permission checks only hide or disable controls. The backend always enforces authorization, target scope, lifecycle, stale version, and commercial invariants.
- Reviewed cross-aggregate mutations use short-lived signed evidence binding operation kind, exact target/version, actor, commercial-catalogue revision, registry version, evaluation/expiry, and assessment fingerprint. Apply reauthorizes and recomputes under locks; the evidence is neither authorization nor payment and never appears in URLs/logs/audit/errors.
- Registry-affecting commercial writes lock the commercial catalogue before the registry singleton and product/price rows. Subscription finalization locks the Account before those commercial/registry locks and exact product/price rows. A strictly row-local mutation may use an optimistic version plus locked recomputation when it cannot affect a different target or result.
- Stale, malformed, expired, cross-actor, or cross-operation review evidence returns a stable conflict and performs no write.
- Bulk actions never trust client-submitted hidden populations; the backend snapshots and signs/resolves the affected set.
- Audit stores actor, action, target, reason, before/after identifiers, outcome, and correlation id without secrets or raw provider payloads.
- Client APIs expose only their Account's eligible catalogue, effective terms, conflicts, stable `attentionCode`, safe checkout state, snapshots, offers, invoices, and operations. They do not expose operator identities, internal request/cancellation provenance, raw attention reasons, provider/manual-settlement evidence, or full policy internals. Admin contracts retain the operational actor, reason, request/cancellation, checkout, and policy evidence required to investigate and act.

## 11. Admin UX contract

Commercial screens are operational tools, not entity CRUD forms.

- Lists use the shared paginated table contract with search, filters, sorting, saved URL state, selection, bulk actions where safe, loading/empty/error states, and mobile alternatives.
- Details show current state, effective dates, dependencies, usage/financial impact, history, and allowed next actions returned by the backend.
- Creation/editing uses guided steps with an unsaved-change guard and a final backend preview.
- Dangerous or subscriber-affecting actions name the target and effect, expose blockers, require explicit confirmation, and return a trackable operation.
- Price, entitlement, invoice, payment, and collected-value vocabulary never blur together.
- French is primary; Arabic/RTL is supported through logical layout properties and direction-aware controls.

## 12. Implementation slices

1. Price-book entries and product extension/sales policy.
2. Commercial policies and Account targeting.
3. Segments, campaigns, offers, and client redemption.
4. Operational subscription jobs and renewal instructions.
5. Invoice/payment/credit/refund ledgers and reconciliation-safe APIs.
6. Commercial fact events, analytics queries, and dashboards.
7. Cross-surface consistency, performance, accessibility, and adversarial security review.

Each slice requires backend and frontend real-life workflow audits before the next slice is considered complete.

**Delivered through Phase 10 (2026-08-27), with independent backend and frontend audits:** operational Plan/AddOn/capacity-package/Price-book catalogues; product revision and availability/visibility controls; exact-price one-Account subscription review/apply for immediate and at-renewal changes; quota-package revisions; bounded Account discovery; signed reviewed mutations; typed commercial-policy definition, precedence, activation, evaluation, accepted-effect provenance, and admin/client surfaces. Activation is approval of a reusable rule, never subscriber application.

Existing Accounts retain the exact accepted Plan/AddOn/package revision and Price-book-entry identities, amounts, currency/cycle, and package quantity even if those catalogue items later become paused, inactive, direct-only, or otherwise unavailable for new selection. A retained item remains visible and removable, but cannot be newly selected or increased unless it is currently eligible.

Typed Segments and the Campaign backend are complete and independently audited through Phase 11.2. The Campaign admin UI, Offers, and redemption remain Phase 11.2; reviewed trial/lifecycle commands, free periods, renewal instructions, selected/filtered/scheduled subscriber execution and job retry/progress/cutoff handling remain Phase 12; settlement ledgers remain Phase 13; durable analytics remain Phase 14. None of those remaining capabilities is represented as complete here.
