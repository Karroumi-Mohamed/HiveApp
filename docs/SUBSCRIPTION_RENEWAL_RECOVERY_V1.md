# HiveApp Subscription Renewal and Recovery V1

**Date:** 2026-08-31  
**Status:** implementation contract for Phase 13 renewal recovery

This contract completes the recurring-payment boundary already decided in `BILLING-FLOW-001`,
`PLAN-FLOW-005`, and `SUBSCRIPTION-FLOW-003`. It does not reprice an accepted subscription,
silently move an Account to another Plan, or treat a provider attempt as settlement.

## 1. Default renewal instruction

Unless an explicit pending at-renewal subscription operation says otherwise, a current
subscription renews from its exact accepted snapshot and price. Plan new-sale status and later
Price-book edits do not rewrite those terms.

At the period boundary, processing order is:

1. apply a confirmed pending at-renewal operation;
2. honour `cancelAtPeriodEnd`;
3. expire a trial;
4. renew an exactly zero-priced period with no Payment attempt;
5. otherwise create one system-owned same-terms renewal operation, Invoice, Payment attempt, and
   durable provider command.

The pending-Account uniqueness rule makes this idempotent and prevents a default renewal from
competing with an explicit customer/operator change.

## 2. Failed and pending collection

A paid subscription whose next period is not settled at the boundary becomes `PAST_DUE`.
`pastDueAt` records that boundary and `graceEndsAt` records the access deadline. V1 uses the
platform setting `hiveapp.subscriptions.renewal-grace` with a 72-hour default. A future reasoned
Account exception may extend a deadline; it must be a separate audited operation, never a mutable
hidden override.

During grace:

- the accepted snapshot remains the only entitlement source;
- operational access continues only until `graceEndsAt`;
- the expired paid period is retained as `PAYMENT_DUE` history;
- the open Invoice and every failed/retried Payment remain visible;
- no data is deleted and no fake renewal period is opened.

When grace expires without trusted settlement, the subscription becomes `SUSPENDED`. It retains
its current commercial identity and history but grants no Plan entitlement. Account/member records,
Companies, roles, collaboration declarations, and customer data remain stored; suspension is not
purge or cancellation.

## 3. Recovery

Provider-confirmed or authorized manual settlement activates the already invoiced next period.
The renewal starts at the original period boundary, so a grace interval is part of that billed
period rather than free time inserted into the billing cadence. Recovery closes the old current
subscription state, opens a fresh `ACTIVE` period from the accepted snapshot, and never resurrects
old authentication sessions.

Every retry creates a new Payment attempt and outbox command. Definitive decline and ambiguous
transport recovery retain the evidence rules in `BILLING_LEDGER_V1.md`.

## 4. Read and authorization boundary

`ACTIVE` and `TRIALING` are entitled through their current period. `PAST_DUE` is entitled only
before its persisted grace deadline. `SUSPENDED`, `CANCELLED`, and `EXPIRED` are never entitled.
Current-subscription read models still expose `PAST_DUE` and `SUSPENDED` so the client and operator
can understand and recover the state; ordinary entitlement checks remain fail-closed.

## 5. Remaining lifecycle operations

This renewal recovery does not collapse the separately decided commands. Reviewed
cancel-at-period-end, immediate cancellation, operator suspension, restoration, trial creation,
plan-wide renewal instructions, and reasoned Account grace exceptions remain distinct Phase 12
operations with their own permissions, previews, actor/reason provenance, and UI.

## 6. Verification gate

Required coverage includes idempotent duplicate scheduling, explicit pending-change precedence,
zero-price evidence, Invoice itemization, provider failure, grace access at both sides of the exact
deadline, automatic suspension, late settlement recovery, Account isolation, permission behavior,
period history, and audit.
