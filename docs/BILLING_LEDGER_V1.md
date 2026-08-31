# HiveApp Billing Ledger V1

**Date:** 2026-08-31
**Status:** implementation contract for Phase 13

This contract implements `BILLING-FLOW-001` and the remaining `BILLING-003` scope. It does not
change accepted product prices, invent tax/FX/proration, or let payment transport decide
entitlement.

## 1. Records and vocabulary

- An **Invoice** is an immutable numbered amount-due document for one Account, currency, accepted
  period, and commercial operation.
- An **Invoice line** is immutable readable evidence for one accepted Plan, AddOn, capacity package,
  or explicit commercial adjustment. It retains source type/code/name/version/Price identity,
  quantity, unit amount, and line amount.
- A **Payment attempt** is one idempotent attempt to settle an Invoice. `PENDING`, `SUCCEEDED`,
  `FAILED`, and pre-dispatch `CANCELLED` describe transport/settlement evidence, not price
  calculation.
- A **Manual settlement** is a succeeded Payment recorded by an authorized operator with an exact
  external reference and reason.
- A **Credit** reduces what the Account owes without pretending money moved back through a provider.
- A **Refund** is an idempotent return of a succeeded Payment. It may be pending, succeeded, or
  failed. Aggregate succeeded refunds cannot exceed the refundable succeeded amount.
- An **Outbox command** is the durable intent to perform one provider charge or refund outside the
  database transaction that created it.
- A **Provider event** is idempotent inbound evidence. It never becomes authority merely because it
  names a successful payment.

## 2. Invoice creation and itemization

The first producer is a positive-price reviewed subscription change. The same factory later serves
renewal.

1. The accepted target snapshot supplies exact Plan/AddOn/package names, revisions, Price IDs,
   currency, cycle, quantities, and unit prices.
2. The Invoice stores one immutable snapshot line for every component.
3. If an accepted Policy or Offer changes the component subtotal, one explicit signed commercial
   adjustment line reconciles the itemized subtotal to the accepted final recurring amount.
4. Line totals must equal the Invoice total exactly in one currency. No implicit FX or rounding
   correction exists.
5. A zero-amount accepted period may create a `SETTLED_ZERO` invoice for evidence, but never a fake
   Payment.
6. Invoice numbering is generated once and protected by a database uniqueness constraint. A number
   is an immutable human reference, not an authorization secret.

Invoice evidence never changes when a product, Price, Policy, Offer, or subscription later changes.
Corrections append Credit/Refund records or a new Invoice.

## 3. Settlement and entitlement boundary

- Creating an Invoice or a pending attempt does not activate paid entitlement.
- A succeeded trusted provider confirmation or authorized manual settlement may finalize the
  checkout exactly once under the Account/subscription lock.
- A simulator `SUCCESS` remains attempt telemetry and cannot confirm settlement.
- The Payment amount and currency must match the remaining payable Invoice balance. V1 does not
  accept partial settlement or overpayment.
- Replaying the same idempotency key/reference returns the same record; reusing it for different
  money or Invoice data is a stable conflict.
- Payment failure records the failed attempt and leaves the Invoice open. Renewal recovery later
  maps repeated failure to explicit past-due/grace/restricted lifecycle states without deleting
  data.

## 4. Transactional outbox and reconciliation

Provider calls never run inside the transaction that persists Invoice/Payment/Refund intent.

1. One transaction persists the financial record and a unique pending outbox command.
2. A worker claims a bounded command in its own transaction.
3. The provider call runs with no database transaction and carries the financial idempotency key.
4. A second transaction records the provider result and finishes or reschedules the command.
5. A crash after the provider call may replay the same idempotency key; the adapter contract must
   make that replay safe.

A manual settlement or checkout cancellation may cancel an automatic provider intent only while
its outbox command is still pending. Payment attempt, outbox command, and Invoice are changed in the
same transaction. Once dispatch starts, the operator must reconcile the provider result rather than
risk a second collection or claim that an in-flight charge was cancelled.

Outbox payloads contain only internal record IDs and stable operation type. They never contain
tokens, passwords, customer codes, raw exceptions, or mutable serialized business objects.
Provider callbacks are stored by `(provider, eventId)` before processing. Unknown or mismatched
evidence is retained for reconciliation but cannot settle an Invoice.

## 5. Credits and refunds

- Credits require Invoice, amount/currency, reason, source, actor, and optional external reference.
- Refunds require one succeeded Payment, amount/currency, reason, actor, idempotency key, and provider
  or manual evidence.
- Issued Credits and succeeded Refunds are append-only. Failed provider Refund attempts remain
  evidence and may be retried through a new outbox attempt bound to the same Refund.
- `succeeded refund total <= succeeded payment amount` is checked under a Payment lock and protected
  against concurrent requests.
- V1 does not automatically change entitlement after a Credit or Refund. A commercial correction is
  a separate reviewed subscription operation.

## 6. Authorization and privacy

Admin permissions are separate for Invoice list/detail, Payment evidence, manual settlement, Credit
issuance, Refund preview/create, Refund identity/reference evidence, reconciliation, and retry.
Permission checks run before target existence. Account identity and provider/manual references are
separately readable where they increase privacy or fraud risk.

Client APIs expose only the authenticated Account's readable Invoice lines, status, totals, and safe
Payment/Refund state. They omit operator identity/reason, provider event payloads, internal
idempotency keys, and reconciliation detail.

## 7. Operational APIs and UI

Admin surfaces provide bounded/filterable Invoice and Payment tables, Account financial timeline,
Invoice detail/lines, attempt history, manual settlement, Credit, Refund review/confirmation,
outbox/reconciliation status, and stable drill-down from subscription operations. Backend actions
and blockers are authoritative; the UI never manufactures lifecycle availability.

Client surfaces provide bounded own Invoice history and detail with download-ready immutable
document data. PDF rendering and tax-compliant jurisdictional numbering remain separate outputs;
the ledger contract must be complete before a document is labelled a tax Invoice.

## 8. Verification gate

Closure requires exact itemization, immutable history, unique numbering, currency isolation,
zero-amount behavior, manual idempotency, duplicate provider event handling, outbox crash/replay,
concurrent refund ceiling, pending-versus-collected wording, permission-before-existence, bounded
query counts, client isolation, audit provenance, and mounted French/Arabic workflows.

Automatic tax, FX, metered charging, automatic proration, automatic refunds, partial payments, and
jurisdiction-specific fiscal documents remain explicitly deferred.
