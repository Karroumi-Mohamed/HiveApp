# HiveApp Special Commercial Agreements V1

**Decision date:** 2026-09-03  
**Status:** canonical implementation target  
**Applies to:** one-Account administrator-negotiated subscription terms, fixed entitlement periods,
manual settlement, complimentary access, and scheduled term completion

This document extends `COMMERCIAL_CONTROL_PLANE_V1.md`, `SUBSCRIPTION_OPERATIONS_V1.md`, and
`BILLING_LEDGER_V1.md`. It does not replace Offers, Commercial Policies, ordinary subscription
changes, or the financial ledger.

## 1. Product meaning

A **special agreement** is an explicit contract between HiveApp and one Account. It lets an
authorized platform administrator assemble and apply exceptional terms without publishing a new
catalogue product and without pretending that a gift is a payment.

It may contain:

- one exact Plan and Price-book entry;
- exact AddOn and capacity-package revisions and quantities;
- finite Account-specific quota bonuses;
- an exact start and end for the special entitlement term;
- catalogue, custom-total, or complimentary pricing;
- provider or manual settlement for a positive amount;
- one explicit instruction for what happens when the term ends.

It may not expose an internal Feature, bypass registry or dependency/exclusion rules, exceed a
platform hard limit, grant arbitrary permissions, delete customer data, or mark unpaid/free access
as collected revenue.

The commercial target is always the Account, never an individual member or Company.

## 2. Relationship to existing commercial tools

| Tool | Purpose |
|---|---|
| Commercial Policy | Reusable time-bounded rule for an Account/audience; it changes future evaluations only |
| Campaign and Offer | Discoverable or targeted proposal that a customer/operator may accept |
| Ordinary subscription change | Change one Account now or at renewal using normal recurring terms |
| Special agreement | One Account, negotiated fixed term, exact amount and explicit completion behavior |

A special agreement may be created from an Offer or Policy outcome later, but V1 records its own
immutable agreement evidence. It must not manufacture temporary Policies, Campaigns, or catalogue
products as hidden implementation details.

## 3. Guided administrator flow

The Account subscription workbench exposes **Créer un accord spécial** as a dedicated page, not a
large modal.

1. **Contenu** — select the Plan, AddOns, capacity packages, quantities, and any finite private
   quota bonus.
2. **Période** — start now or at an exact future instant; choose one month, a number of months, or
   exact start/end instants.
3. **Prix** — use the calculated catalogue total, enter one exact custom total, or make the whole
   term complimentary.
4. **Après la période** — continue on reviewed recurring terms, restore the previous terms, end
   access, or require manual review.
5. **Règlement** — online/provider payment, payment already received manually, or no payment for a
   complimentary agreement.
6. **Révision** — backend-computed entitlement, quota, period, money, settlement, and completion
   preview; then explicit confirmation with an operator reason.

The page uses the existing shared commercial selectors and Account workbench patterns. It provides
field-local validation, an unsaved-change guard, keyboard operation, responsive layout, French-first
copy, Arabic/RTL support, stable loading/error states, and one primary action per step.

## 4. Exact term

- The backend persists authoritative UTC `startsAt` and `endsAt`; `endsAt` must be after `startsAt`.
- Presets are UI conveniences only:
  - one month;
  - a positive number of months;
  - exact dates/times.
- A calendar month uses UTC calendar arithmetic, not a fixed number of seconds.
- A future start creates a durable scheduled agreement and changes no entitlement before its start.
- Start and completion processing are lock-based, idempotent, restart-safe, and retain per-attempt
  evidence.
- The Offer/Campaign window remains only an acceptance window. It never substitutes for the
  agreement entitlement period.

## 5. Pricing and settlement

Pricing mode is closed:

- `CATALOGUE_TOTAL` — the backend computes the full fixed-term amount from the selected exact
  Price-book entries and complete billing cycles. An irregular exact period cannot use inferred
  proration and must use a custom total or complimentary price.
- `CUSTOM_TOTAL` — the administrator enters one exact non-negative total and currency for the whole
  fixed term. It is a contractual amount, not a hidden discount or mutable catalogue price.
- `COMPLIMENTARY` — the fixed-term total is exactly zero.

Settlement mode is also closed:

- `PROVIDER` — a positive invoice and provider Payment attempt are created through the durable
  outbox flow; entitlement begins only after trusted settlement.
- `MANUAL` — a positive invoice is created and settled by an authorized operator using a unique
  external/reference value and reason. It bypasses the provider, not the ledger.
- `NONE` — valid only for a zero-total agreement. A zero-amount Invoice/period settles without a
  fake Payment.

`CUSTOM_TOTAL` may be lower than, equal to, or higher than catalogue total. The preview must show
both values and call out the variance. Only succeeded positive settlement contributes to collected
value. Complimentary value remains separately analyzable and is never reported as revenue.

## 6. Private capacity and product terms

- V1 may grant finite quota bonuses for quota definitions already owned by the selected Plan or
  selected AddOn. It cannot invent a new quota definition.
- Existing AddOns/packages may be included in the agreement regardless of public catalogue
  visibility when an authorized operator can assign them and all mandatory compatibility rules
  pass.
- A quota bonus is Account-specific agreement evidence, not a new global named package. If the same
  bundle becomes reusable, an administrator should publish a normal AddOn/capacity package or an
  Offer instead.
- Every accepted term snapshots exact product revisions, Price entries, quantities, quota bonuses,
  catalogue total, agreed total, currency, and registry/catalogue versions.

## 7. End instructions

Every agreement chooses exactly one instruction:

1. `CONTINUE_REVIEWED_TERMS` — continue with an exact reviewed follow-on selection and recurring
   price (catalogue, custom, or zero) captured with the agreement.
2. `RESTORE_PREVIOUS_TERMS` — restore the exact pre-agreement snapshot and recurring amount,
   subject to current hard safety and usage checks.
3. `END_ACCESS` — end subscription entitlement at the agreement deadline without deleting data.
4. `MANUAL_REVIEW` — stop automatic renewal, retain the Account and all data, and place the
   agreement in an attention state for an authorized operator.

The end instruction is a reviewed future command, not a silent expiry mutation. At execution the
backend locks the Account, verifies that the agreement still owns the current subscription, applies
hard safety and usage checks, and either commits exactly once or records `NEEDS_ATTENTION` with a
safe blocker. It never falls through to ordinary automatic renewal.

A positive follow-on amount creates the normal renewal Invoice/Payment flow. A zero follow-on amount
opens a normal zero-price recurring period without a fake Payment. Manual settlement of a future
follow-on invoice remains a separate authorized action; an agreement cannot claim that future money
was already collected.

## 8. Lifecycle and correction

Agreement lifecycle is:

```text
SCHEDULED -> AWAITING_SETTLEMENT -> ACTIVE -> COMPLETED
     |                 |              |          
     +---------------> CANCELLED      +-------> NEEDS_ATTENTION
```

- A start-now complimentary agreement may move directly to `ACTIVE`.
- A positive agreement is `AWAITING_SETTLEMENT` until trusted provider or manual settlement.
- Cancellation is allowed only before settlement/application and retains immutable history.
- Active terms are never edited in place. A correction is a new reviewed agreement/change.
- Completion and every attention outcome remain readable from the Account commercial timeline.

## 9. API, authorization, and audit

Admin APIs provide:

- operation/chooser reads needed to author an agreement;
- preview and confirm as separate permissions;
- bounded Account agreement list and detail;
- cancel-before-start/settlement;
- separately authorized manual settlement;
- start/end retry only from safe retryable attention states;
- history and financial/subscription timeline links.

Preview evidence is short-lived, actor-bound, Account/subscription-version-bound, catalogue/registry
version-bound, and fingerprints the exact content, term, amount, settlement mode, end instruction,
and optional follow-on terms. Confirmation reauthorizes and recomputes under the Account lock.

Audit records actor, Account/agreement/operation identifiers, reason, exact before/term/follow-on
snapshots, amount variance, schedule, settlement source, external manual reference where authorized,
state transitions, and safe failures. Client APIs expose only the Account's effective terms,
deadlines, invoices, and attention state; they never expose operator identity/reason or manual
settlement evidence.

## 10. Analytics

Durable analytics distinguish:

- agreement count and active/completed/attention outcomes;
- catalogue value versus agreed value by currency;
- complimentary value separately from invoiced and collected value;
- provider versus manual settlement;
- private quota bonuses and selected products;
- scheduled start/end success and attention outcomes.

No mixed-currency total is allowed. Operational counts drill into the bounded agreement table.

## 11. Required verification

Implementation is incomplete until tests cover:

- one-month, multi-month, exact-period, leap/calendar-boundary, and invalid term calculations;
- catalogue/custom/free totals and exact decimal/currency handling;
- manual settlement versus complimentary evidence and collected-value truth;
- future start, restart-safe claiming, duplicate execution, cancellation races, and stale previews;
- all four end instructions, follow-on payment, safety conflicts, and manual-review attention;
- exact product/Price retention, private finite quota bonuses, no internal Feature leakage;
- Account isolation, permission-before-existence, reason/audit redaction, and client privacy;
- bounded/no-N+1 list/detail/history endpoints;
- French/Arabic, LTR/RTL, mobile, keyboard, loading/error, and unsaved-change UI behavior.
