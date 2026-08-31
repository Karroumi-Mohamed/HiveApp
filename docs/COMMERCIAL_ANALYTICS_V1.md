# Commercial Analytics V1

**Decision date:** 2026-08-31  
**Status:** implementation contract

## 1. Purpose

The platform administration shell needs a commercial operations surface that answers:

- what was invoiced, collected, credited, and refunded;
- which subscriptions are healthy, past due, suspended, cancelled, or expiring;
- which products are currently held and which subscription changes added or removed them;
- which Offers were reserved, applied, cancelled, or failed;
- which Accounts require operational attention.

This is an operational control surface, not a tax ledger, general business-intelligence warehouse, or provider observability system.

## 2. Truth boundaries

Analytics read the existing authoritative facts:

| Question | Authoritative source | Event time |
| --- | --- | --- |
| Invoiced value | non-cancelled `BillingInvoice` | `issuedAt` |
| Collected value | trusted succeeded `BillingPaymentAttempt` | `completedAt` |
| Credited value | `BillingCredit` | `issuedAt` |
| Refunded value | succeeded `BillingRefund` | `completedAt` |
| Subscription lifecycle | `SubscriptionLifecycleEvent` | `effectiveAt` |
| Current subscription health | current `Subscription` rows | query watermark |
| Product additions/removals | applied `SubscriptionChangeOperation` before/after snapshots | operation update time |
| Current product holdings | `SubscriptionCurrentHolding` | query watermark |
| Offer outcomes | `CommercialOfferRedemption` | the outcome-specific timestamp |

Configured Plan or subscription prices are never called revenue, collected value, or settlement. They may be reported only as **configured recurring value**, grouped by currency and billing cycle and clearly labelled as a current-state estimate.

Money from different currencies is never summed. Monthly and yearly commercial terms are never combined into one unlabeled recurring value. Exact decimal strings cross the API boundary; frontend code does not coerce them through binary floating-point arithmetic.

## 3. Query contract

Every historical endpoint accepts:

- `from`: inclusive instant;
- `until`: exclusive instant;
- `timezone`: IANA timezone used for bucket boundaries and labels;
- `interval`: `DAY`, `WEEK`, or `MONTH`;
- optional `currencyCode` and `billingCycle` where applicable.

Rules:

- `until` must be after `from`;
- ranges are bounded to 366 days;
- the timezone must be a valid IANA zone;
- day/week/month buckets are computed in that timezone, then represented by their UTC start/end instants;
- the current, potentially incomplete bucket is explicitly marked provisional;
- empty facts are returned as known zero only when the source query completed successfully;
- responses include `generatedAt` and `completeThrough`. `completeThrough` is a read watermark, not a promise that pending provider work has reached a terminal state;
- pending provider commands and pending Payments are reported separately so financial finality is not implied.

## 4. Permissions

Analytics is a separate platform-control Feature: `platform.analytics`.

| Permission | Allows | Does not allow |
| --- | --- | --- |
| `read_summary` | high-level separated commercial totals and current subscription counts | Account identity, provider evidence |
| `read_financial_series` | invoiced/collected/credited/refunded time series | Payment references, manual evidence |
| `read_subscription_series` | lifecycle and product adoption/churn series | Account identity |
| `read_offer_series` | Offer outcome series | Campaign audiences or customer identity |
| `read_operations` | bounded operational drill-down with Account identity and navigation | provider references or client business data |

The overview degrades by permission. Holding `read_summary` does not silently grant any detailed series or operational identity. Existing Billing, Offer, Campaign, Plan, and subscription permissions remain authoritative on their own pages.

## 5. Admin API

Base path: `/api/admin/analytics`.

### `GET /overview`

Requires `read_summary`.

Returns:

- one financial total group per exact currency + billing-cycle dimension;
- current subscription status counts;
- renewal/past-due attention counts;
- Offer outcome counts when independently permitted;
- configured recurring value as a separate, explicitly current-state section;
- availability flags for independently protected sections;
- `generatedAt`, `completeThrough`, and pending-finality counts.

### `GET /financial-series`

Requires `read_financial_series`.

Returns complete buckets for each exact currency + billing-cycle dimension, with separate decimal-string values for:

- invoiced;
- collected;
- credited;
- refunded.

It never returns a synthetic `revenue` field.

### `GET /subscription-series`

Requires `read_subscription_series`.

Returns per-bucket lifecycle transition counts and product addition/removal counts. Product series are keyed by immutable product type + normalized code. Current holdings are a separate snapshot, not rewritten into historical buckets.

### `GET /offer-series`

Requires `read_offer_series`.

Returns per-bucket reserved/applied/cancelled/failed counts. It exposes opaque Offer lineage/revision identifiers and safe names only; it does not expose Account identity or audience membership.

### `GET /attention`

Requires `read_operations`.

Returns stable paginated operational rows, initially:

- past-due subscriptions with age band and grace deadline;
- suspended subscriptions with safe lifecycle reason;
- open Invoices and failed/pending collection state;
- subscription operations needing attention.

Every row has a typed destination to an existing operational detail surface. Sorting and filters are allowlisted; no raw field name reaches persistence.

### `GET /product-holdings`

Requires `read_subscription_series` for aggregate counts. Account-level rows require `read_operations`.

Returns current Plan/AddOn/capacity-package holdings grouped by immutable type + normalized code. Historical adoption/churn remains sourced from applied operation snapshots.

## 6. Operational dashboard UX

The `/admin/analytics` page is an operational dashboard, not a wall of cards.

- A compact range bar controls preset/custom range, timezone, interval, currency, and billing cycle.
- The money section names four distinct measures: **Facturé**, **Encaissé**, **Avoirs**, **Remboursé**.
- Accessible SVG charts use the same values as the adjacent table and expose text summaries; color is not the only distinction.
- Current subscription health and historical lifecycle movement are visually separated.
- Every summary provides a direct action to its exact filtered Invoice/subscription/Offer table when the actor has permission.
- Missing permission, source failure, and incomplete current bucket are visibly different from zero.
- Account names/emails appear only in the separately protected attention table.
- Dark mode, French-first copy, Arabic/RTL, keyboard interaction, reduced motion, and narrow-screen tables are first-class requirements.

## 7. Performance and stability

- Aggregations execute in a bounded number of database queries independent of result count.
- Time series return at most 367 buckets per dimension and reject an excessive number of dimensions rather than truncating silently.
- Operational drill-down is paginated with stable time/id ordering.
- Historical financial values come only from immutable or append-only ledger fields.
- Applied subscription snapshots are compared as sets of immutable product identities; later catalogue edits cannot rewrite old additions/removals.
- Current holdings are explicitly a present-time projection and are never presented as historical fact.

## 8. Deferred boundaries

V1 does not claim:

- accounting revenue recognition, tax reporting, MRR/ARR normalization, cohort LTV, or FX conversion;
- automatic anomaly detection or forecasting;
- arbitrary cross-client business-data exploration;
- raw application logs, traces, exceptions, or infrastructure health;
- historical near-quota pressure before a durable usage-snapshot contract exists.

Current quota-package adoption is supported. Near/over-quota analytics requires a later append-only usage snapshot taken with a declared cadence and completeness watermark; querying every Account's live business tables from a dashboard would be slow, privacy-heavy, and historically unstable.

## 9. Required verification

- exact inclusive/exclusive boundaries in at least two timezones, including DST;
- day/week/month bucket construction;
- mixed currency and billing-cycle separation;
- cancelled Invoice, untrusted Payment, failed Refund, and pending provider exclusions;
- independently enforced summary/financial/subscription/Offer/operations permissions;
- no Account identity in aggregate responses;
- stable product history after catalogue edits;
- pagination and stable ordering of attention rows;
- query-count ceilings;
- frontend exact-decimal handling, permission degradation, loading/error/empty/provisional states, keyboard, RTL, dark/light, and narrow-screen behavior.
