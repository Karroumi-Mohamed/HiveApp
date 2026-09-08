# Existing-subscriber tariff changes V1

**Decision date:** 2026-09-08  
**Status:** implemented; verification record below

## 1. Two independent operations

Catalogue tariff replacement changes new purchases only. Existing Accounts keep their exact
accepted prices, including future renewals, until a separately authorized subscriber operation
changes them. Publishing/replacing a tariff never creates such an operation implicitly.

`Appliquer aux abonnés existants` is a separate action from an exact Plan, Add-on, or capacity-pack
tariff. It selects an old tariff and a new tariff for the same product revision, currency, and
billing cycle. Switching product revisions, currency, monthly/yearly cadence, or quantities remains
an ordinary subscription change. Both price increases and decreases use this same flow.

## 2. Price-only invariant

For each Account, copy its own accepted snapshot and replace only the selected component's price
identity and amount. Preserve Plan/feature definitions, entitlements, limits, Add-ons, other prices,
pack quantities, and capacities. Pack changes alter unit price, not quantity. Never use one shared
full product selection for a heterogeneous population. No paid period or issued invoice is edited.

Fixed/negotiated/complimentary agreements and accepted Policy/Offer pricing are protected. Expose
the reason and exclude them from ordinary repricing; changing those contracts requires their
existing separate signed agreement/subscription review. This operation must not clear discount
provenance or infer how a negotiated total should react to one component changing.

## 3. Audience and evidence

Choose exactly one audience: selected Accounts; all current holders of the exact old tariff;
holders filtered by exact Plan revision and subscription status; or an active Segment's frozen
audience intersected with holders of that tariff. All support explicit Account exclusions.

Resolve on the backend and freeze the reviewed Account set. Reject an oversized population rather
than truncate it silently. Paginate review/results and bulk-resolve identities under separate
authority. Retain the exact source subscription/terms identity, before/target snapshots, price
versions, Segment activation when applicable, actor, reason, and per-Account result.

Preview changes no entitlement or payment. Confirmation requires short-lived actor-bound signed
evidence. Never trust amounts, counts, eligibility, or a target snapshot supplied by the browser.

## 4. Timing and execution

Choose next renewal, or the first renewal on/after an explicit instant. A calendar date is not an
instruction to interrupt already-paid monthly/yearly terms. Ordinary intervening same-terms
renewals may proceed; a customer/operator commercial change invalidates the affected instruction.

At execution lock/recheck the Account, the accepted terms, target tariff, pending operations, and
agreement/lifecycle state. Conflicts are visible per Account, never overwritten. Cancel before
renewal processing starts; retries never repeat a completed charge or silently rebase to different customer
terms. A correction requires a new review. Cancelled/expired/non-renewing and exceptional terms
remain protected rather than being revived by repricing.

A due positive price uses the existing invoice/payment/settlement and grace/recovery engine. Zero
uses its zero-price period path. Scheduling, notification, an attempted payment, and actual
settlement are different facts. Neither an admin confirmation nor a notification marks money paid.

## 5. Communication

Confirmation creates a durable Account-scoped in-app price-change notice with old/new component
price, resulting recurring total, currency/cycle, effective renewal, and operation status. Only
the owner or an authorized Account-scoped subscription reader may see it. Ordinary members, other
Accounts, and unrelated administrators do not receive financial terms.

Optional email delivery supplements the in-app notice, using a durable retryable delivery record
and the existing mail transport. Dispatch outside the commercial transaction; do not call delivery
successful before the transport reports it. Cancellation/failure must remain visible. Read state
is per recipient and never consent. Delivery failure is exposed separately from commercial outcome.

This is a reusable typed notice foundation with a price-change use case, not a support chat,
arbitrary broadcast editor, or marketing-campaign replacement. Preserve the existing credential
delivery monitor; commercial notices must not masquerade as credential emails.

## 6. UI and operational APIs

Guided page: **Tarif → Abonnés → Date et notification → Révision**. Enter through the tariff
detail, linked from product tariff tabs. Reuse Account/Segment selection, DataTable, pagination,
inline actions, exact-money rendering, loading/error/denied states, and unsaved-change protection.

Review: Account, old/new component amount, quantity, old/new recurring total, effective date,
eligibility/conflict reason. Confirm only after a successful current preview. Clear the review on
input change or stale evidence. The result page supports list/filter/progress, per-Account results,
identity reveal, safe cancellation/retry, and navigation to the Account subscription.

Separate permissions: audience/preview, confirm, list/detail/results, identity reveal, cancel,
retry, and optional email dispatch. Client notice reads/mark-read use Account-scoped subscription
authority with permission-before-existence checks. All list APIs use stable `PageResponse`.

## 7. Verification gate

- Catalogue-only replacement leaves current subscriptions and renewals unchanged.
- Plan/Add-on/pack price-only changes preserve all other snapshot fields and pack quantities.
- Currency/cycle/product mismatch, stale evidence, protected agreements/discounts, conflicting
  pending operations, unauthorized actors, and cross-Account reads fail closed.
- Exact old-tariff targeting, filters, frozen Segments, exclusions, bounds, and paginated results.
- Next/first-eligible monthly/yearly renewal, intervening unchanged renewal, customer change,
  double processing, cancel race, retry, payment failure/recovery, zero-price transition.
- No HTTP/email dispatch inside the subscription mutation; durable notice and honest delivery
  state; financial privacy and per-recipient read state.
- Mounted route/form/permission tests and browser QA, then full backend/frontend checks.

## 8. Completion record

Implemented 2026-09-08. Catalogue publication remains independent of subscriber repricing.

### Delivered surfaces

- Admin: **Clients → Tarifs des abonnés**, or **Grille tarifaire → a tariff → Appliquer aux
  abonnés existants**. The four-step wizard stages the request, calculates a signed five-minute
  review, and confirms only that reviewed population. Results use the shared paginated table and
  action-cell patterns, with a separately authorized bulk identity lookup.
- Client: **Abonnement → Notifications** shows the Account's price notices and per-user read state.
  Cancellation and failed payment are explicit; marking a notice read never accepts a contract.
- Admin API: `/api/admin/subscription-repricing` provides preview, confirm, paginated list/detail/
  results, bulk identities, whole-job/per-result cancellation, technical execution retry, and
  failed-email retry. Separate `platform.subscriptions.*_repricing` nodes guard these operations;
  result and identity reads use `read_repricing_results` and `read_repricing_identities`.
- Client API: `GET /api/v1/subscriptions/notices` and `POST /api/v1/subscriptions/notices/{id}/read`,
  guarded by `workspace.subscription.read_price_notices` and `mark_price_notice_read`.

### Operational boundaries

- One review supports up to **500 Accounts**, with paginated results (maximum page size 100).
  Larger audiences fail explicitly and must be split; no silent truncation. Filters currently
  cover exact Plan revision and subscription status, plus the existing frozen Segment mechanism.
- A persisted commercial-terms identity survives unchanged ordinary renewals but changes on
  commercial edits, even when an amount is later changed back. Execution and settlement both
  recheck the accepted evidence. Product/currency/cadence changes still use ordinary operations.
- Cancellation stops only `READY`/`PENDING` results. Once an invoice/renewal has started, its
  payment/recovery/refund controls belong to **Facturation**; repricing never erases that evidence.
  Only technical failures before an operation was created can retry the original instruction.
  Changed terms require a new review. Failed payment is shown independently and can recover
  through the existing billing engine without a second repricing instruction.
- In-app notices are durable. Email goes only to the current verified Account owner, through a
  separately authorized option and a background dispatcher outside database transactions. Missing
  transport/unverified email is `SUPPRESSED`, not `SENT`. Abandoned claims have bounded recovery;
  failed delivery can be retried explicitly. Transport delivery is **at least once**: an interrupted
  send may be delivered twice; no exactly-once email promise is made.
- Startup backfills the indexed current-holding price IDs in bounded pages using only accepted
  subscription snapshots. Historical rows lacking an exact accepted tariff ID are not guessed
  from today's catalogue. This is a projection repair, not a contract or price migration.
- The existing provider adapter, tax/fiscal, and regulatory communication deferrals remain open.
  No live payment was collected and no load-capacity claim is made by this batch.

### Verification

The full-suite result is recorded in `IMPLEMENTATION_PLAN.md`, Batch 12.3. Added coverage includes
component-only Plan/Add-on/pack copying, monthly/yearly timing, exact tariff and Segment targeting,
exclusions, stale/tampered evidence, reverted commercial edits, protected terms, independent
permissions, cross-Account privacy, intervening ordinary renewals, concurrent execution, cancellation,
technical retry, zero-price activation, declined-payment recovery, and durable email claims.

Mounted frontend tests cover a failed/missing/expired review, Enter/submit gating, token-only
confirmation, and results-only authority without identity reads. A browser-build regression checks
the configured API origin when no `process` global exists. Browser QA on isolated local data
completed selection → preview → confirmation → client notice → mark-read → per-Account cancellation
→ persisted cancelled notice, with admin/client visual inspection. The user's existing backend
and its in-memory data were not restarted or modified for this walkthrough.
