# HiveApp Commercial Offers V1

**Status:** backend and admin/client UI implemented and automated/security verified 2026-08-31; authenticated browser QA pending

This document fixes the Phase 11 Offer boundary before implementation. It extends
`COMMERCIAL_CONTROL_PLANE_V1.md` and `MARKETING-FLOW-002`; it does not replace either ledger.

## 1. Meaning and boundaries

An Offer is an explicit, opt-in commercial proposal under one exact Campaign revision. It is not
an always-applicable Commercial Policy, a mutable price, a permission, a coupon credential, or an
invoice/payment.

Phase 11 delivers complete one-Account client/operator Offer application. Selected, filtered,
bulk, scheduled, retry/correction, lifecycle-recovery, communication, free-period, and temporary
reversion jobs belong to Phase 12. Settlement belongs to Phase 13. Impression, conversion,
revenue, churn, and time-series analytics belong to Phase 14.

## 2. Stable lineage and immutable revisions

One Offer lineage owns:

- one immutable internal business code;
- one exact Campaign revision for its whole lifetime;
- `CATALOG` or `CODE_ONLY` discovery;
- `CLIENT_OR_OPERATOR` or `OPERATOR_ONLY` acceptance;
- optional permanently reserved normalized customer code;
- lineage-wide global and per-Account limits;
- accumulated reservation/application usage and optimistic locking.

Duplicating creates a new lineage. Revising preserves the lineage and its code, limits, and usage.
Publishing a successor atomically retires the previous published revision. Campaign duplication or
revision never silently copies or moves Offers; that is an explicit reviewed operator action.

Revision lifecycle:

```text
DRAFT -> PUBLISHED <-> RETIRED -> ARCHIVED
```

- Only a draft is editable or hard-deletable.
- Published revisions are immutable.
- Retirement reversibly stops new reservations.
- Archive is terminal.
- Window expiry does not rewrite lifecycle state.
- Existing reservations may complete after successor publication, retirement, Campaign pause, or
  Campaign end.

## 3. Discovery, codes, and privacy

`CATALOG` Offers may appear in the authenticated eligible catalogue. `CODE_ONLY` Offers never do.
An optional code on a catalogue Offer provides a second discovery path, not different authority.

The first publication permanently reserves the normalized code for the lineage. Normalization is
trim, uppercase, then validation against `[A-Z0-9][A-Z0-9_-]{2,63}`. A successor may reuse it; a
different lineage never may, even after retirement/archive.

Code resolution:

- is authenticated and Account-scoped;
- uses a request body, never a URL;
- returns the same client-safe unavailable result for unknown, ineligible, exhausted, retired,
  expired, or wrong-audience codes;
- never logs, audits, analyzes, or returns the raw submitted code;
- never bypasses Campaign audience, Offer status/window, product compatibility, subscription
  authority, hard restrictions, or capacity.

## 4. Exact selection and effects

A published revision pins one complete target selection:

- exact Plan revision and Price-book entry;
- exact AddOn revisions and Price-book entries;
- exact capacity-package revisions, Price-book entries, and quantities;
- `IMMEDIATE` or `AT_RENEWAL` timing;
- optional finite quota bonus;
- optional explicit free AddOn/package grants;
- at most one fixed-Money or percentage-with-cap Offer discount.

Publication proves the selection is compatible and its Price entries cover the Offer window.
Offer effects cannot block products/Features, introduce an arbitrary fixed recurring price, tax,
proration, refunds, free periods, or renewal instructions.

Only an exact product pinned by a targeted frozen Campaign may overlay `DIRECT_ONLY` visibility.
PUBLIC or merely `CODE_ONLY` Offers cannot. Nothing bypasses product/price lifecycle, registry
assignment, dependencies, exclusions, currency/cycle, quota ownership, or hard Policy rules.

## 5. One authoritative commercial result

Pricing produces one combined evaluation and one final recurring price:

1. catalogue subtotal;
2. selected applicable Policy fixed base, if any;
3. selected Policy discount candidate;
4. Offer discount candidate;
5. winner and reason;
6. exact final recurring price.

Discounts never stack. The greater actual compatible reduction wins; Policy wins an exact tie.
The accepted subscription operation and entitlement snapshot store the complete combined
calculation and exact Policy/Offer provenance. Billing consumes that same result rather than
calculating a second truth.

## 6. Eligibility

New acceptance requires all of:

- authenticated authorized actor and active Account;
- Campaign `ACTIVE`;
- Offer `PUBLISHED` and inside its window;
- Account inside the frozen targeted Campaign audience, or a PUBLIC Campaign;
- discovery/code and acceptance-channel requirements satisfied;
- current `ACTIVE` or `TRIALING` subscription;
- exact selection and Price/registry compatibility;
- no hard Policy/governance restriction;
- no conflicting outstanding subscription operation;
- available lineage-wide global/per-Account capacity;
- a non-no-op result.

Preview does not reserve capacity. It returns signed short-lived evidence bound to Account/current
subscription version, actor, Campaign/Offer revisions, exact selection, combined evaluation,
catalogue/registry versions, and request fingerprint.

## 7. Redemption and idempotency

Redemption states are:

```text
RESERVED -> APPLIED
RESERVED -> CANCELLED
RESERVED -> FAILED
```

Only `RESERVED` and `APPLIED` consume capacity. Cancellation or failure before application
releases capacity exactly once without deleting history. Durable Redemption evidence stores
Campaign/Offer lineage/revision, Account, surface/actor, linked subscription operation, hashed
idempotency key, request fingerprint, immutable accepted commercial evaluation, state, reason,
and reservation/application/release timestamps.

- Same Account + key + fingerprint returns the original result.
- Same key with different input returns stable conflict.
- Concurrent identical acceptance creates one reservation; the creator receives 201 and the replay
  receives 200.
- A committed replay remains readable after the Offer later closes.

Acceptance lock order is:

```text
Account -> commercial catalogue -> Campaign revision -> Offer lineage -> Offer revision
        -> registry -> exact products/prices
```

Redemption does not bump the global catalogue revision.

## 8. Authorization

Offer authority supplements, never replaces, subscription authority.

- Client acceptance requires Offer acceptance authority plus existing subscription-apply authority;
  monetary/entitlement mutation is owner-only in V1.
- Operator application requires Offer preview/apply authority plus existing platform subscription
  preview/apply authority.
- Safe aggregate capacity/outcome counts are separate from Account identity drill-down.
- Client responses never expose internal blockers, Campaign evidence, owners/operators, global
  capacity, other Accounts, or operator reasons.

## 9. Operational API contract

Admin APIs provide bounded list/search/filter/sort; detail; draft create/update/duplicate/revise;
revisions/compare/history; signed publication preview/apply; retire/restore/archive/delete-draft;
separate owner read/reassignment; exact Campaign/product/Price/owner choosers and resolvers;
one-Account eligibility/terms preview and reviewed apply; operational counts; bounded Redemption
history; and separately authorized Account identities.

Client APIs provide eligible catalogue/detail, privacy-safe body-based code resolution, exact signed
preview, idempotent acceptance, and bounded own Redemption history/detail.

## 10. Required verification

Implementation is incomplete until tests prove lifecycle/immutability; one-draft concurrency;
permanent concurrent code reservation; lineage-wide limits; exact Money/currency/cycle; public vs
targeted privacy; direct-only rules; signed-evidence substitution/expiry/staleness; combined
Policy/Offer precedence; zero-price, paid checkout, immediate and renewal flows; idempotent races;
final-slot capacity races; release/consume exactly once; pause/end/retire behavior; Account-lock
isolation; historical snapshot retention; bounded/no-N+1 APIs; permission/preset coverage; and no
token/raw-code/idempotency leakage.
