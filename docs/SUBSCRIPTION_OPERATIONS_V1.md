# HiveApp Subscription Operations V1

**Date:** 2026-08-31
**Status:** selected-Account `CHANGE_SELECTION` and one-Account lifecycle controls implemented
2026-08-31; reviewed trial creation and population lifecycle jobs remain open

This contract implements `PLAN-011`, `PLAN-FLOW-010`, and `SUBSCRIPTION-FLOW-002..004` without
reopening their product decisions. It extends the audited one-Account subscription-change engine;
it does not create a privileged replacement path.

## 1. Operational vocabulary

- A **review** is a short-lived, actor-bound assessment. It changes nothing.
- A **job** is the durable operator command created from an accepted review.
- A **target snapshot** is the immutable set of Accounts selected by ID, filter, or source
  population at review time. Later membership changes never rewrite it.
- A **result** is one Account's independently committed outcome. Partial success is normal and
  visible.
- **Retry** re-evaluates only retryable results against current subscription/catalogue/registry
  state. It never replays a stale token or overwrites a concurrent customer change.
- **Correction** is a new reviewed job. Completed history is never edited or rolled back.

## 2. Job kinds

V1 exposes distinct typed commands:

1. `CHANGE_SELECTION` — exact Plan/Price, AddOn/Price, package/Price quantities and immediate or
   next-renewal timing through the existing subscription-change engine.
2. `CREATE_TRIAL` — a reviewed finite trial with an exact sellable selection and explicit end.
3. `CANCEL_AT_PERIOD_END` — stop renewal while preserving entitlement until the current period end.
4. `CANCEL_IMMEDIATELY` — terminate entitlement now and retain data/history.
5. `SUSPEND` — stop operational entitlement now for a bounded reason; optional review time is
   metadata, not automatic restoration.
6. `EXPIRE` — terminate a completed trial/term through a distinct auditable outcome.
7. `RESTORE` — create fresh current entitlement only after current Account, product, usage, and
   dependency revalidation. Old sessions are never resurrected.

The durable job protocol currently delivers `CHANGE_SELECTION`. One-Account
`CANCEL_AT_PERIOD_END`, `KEEP_RENEWING`, `CANCEL_IMMEDIATELY`, `SUSPEND`, `RESTORE`, and
`EXTEND_GRACE` are separately delivered as signed, reasoned lifecycle commands. Population-wide
lifecycle work will reuse the job target/result/retry contract; the one-Account commands remain the
authoritative per-Account executor rather than being replaced by a privileged batch path.

## 3. Targeting

The review accepts exactly one closed target kind:

- `SELECTED_ACCOUNTS`: explicit bounded Account IDs;
- `FILTERED_ACCOUNTS`: the same safe Account/subscription filters used by the operational table;
- `PLAN_SUBSCRIBERS`: exact Plan revision plus current/historical status filters where the command
  supports them.

The backend resolves and stores Account IDs and observed current-subscription identity/version.
The review returns total/ready/conflict counts and bounded samples; Account identity is separately
authorized. A hard safety ceiling rejects an unbounded population instead of silently truncating.

## 4. Timing and cancellation

- Job execution is `NOW` or an explicit future `SCHEDULED` instant.
- A `CHANGE_SELECTION` job independently chooses `IMMEDIATE` or `AT_RENEWAL` for each Account's
  resulting subscription operation.
- `PREVIEWED`, `QUEUED`, and `SCHEDULED` jobs are cancellable. Cancellation after processing starts
  prevents unstarted results; running and completed results remain recorded.
- Scheduler claiming is lock-based and idempotent. A crash leaves durable retryable state; it does
  not manufacture success.

## 5. Per-Account execution

Each result runs in its own transaction:

1. lock the Account;
2. re-read the current subscription and commercial/registry versions;
3. create a fresh internal operator preview using the frozen requested selection;
4. apply that exact fresh preview through the audited one-Account engine;
5. persist `APPLIED`, `PENDING_RENEWAL`, `AWAITING_PAYMENT`, `CONFLICT`, `FAILED`, or `CANCELLED`;
6. retain linked subscription-operation identity and a safe stable outcome code.

No result stores raw exception messages, signed tokens, passwords, offer codes, idempotency keys,
or sensitive Account/member fields.

## 6. Authorization and privacy

Permissions are split by surface: preview, create/confirm, list, detail, results, retry, cancel,
identity reveal, and lifecycle kind. Execution runs as `SYSTEM` only after a stored authorized
command, while retaining the requesting operator as provenance. Execution still re-runs business,
catalogue, Account, subscription, and entitlement validation.

Ordinary job/result reads are opaque. Account names and owner identities require separate reveal
authority. Client APIs expose only their own pending/completed operation and lifecycle state, never
the population job, other Accounts, operator identity/reason, or internal failure detail.

## 7. Operational APIs and UI

Admin APIs provide bounded list/filter/sort, preview, confirm, detail, progress counters, paginated
results, separately authorized identities, cancel, retry, and correction-from-result. The UI uses a
guided population/command/timing/review flow, then a progress page with honest partial outcomes and
drill-down to the existing Account workbench.

The list and result table use `PageResponse`, stable error codes, URL-backed filters, responsive
rows, and shared table/action patterns. Estimated/configured recurring value is never labelled
revenue, invoice, payment, or collected value.

## 8. Verification gate

Closure requires permission-before-existence tests, immutable audience tests, stale and concurrent
Account changes, mixed success, crash/retry/idempotency, cancellation races, scheduled claiming,
bounded queries, no N+1 results, client privacy, audit provenance, and mounted French/Arabic UI
workflows. Phase 13 settlement and Phase 14 analytics remain separate.

## 9. Implementation evidence — 2026-08-31

**2026-09-08 extension:** `docs/SUBSCRIPTION_REPRICING_V1.md` defines a separate price-only
subscriber operation. It must not reuse the shared full-selection request to overwrite differing
Account products. Existing selected-Account jobs remain unchanged; the repricing contract adds
exact tariff audiences, exclusions, preserved per-Account terms, renewal timing, and notices.

The first slice is implemented across backend and admin UI:

- a reviewed explicit population of 1–500 Account IDs is stored durably with the exact commercial
  selection, operator, reason, execution time, catalogue revision, registry version, and assessment
  fingerprint;
- confirmation queues or schedules the frozen job; each Account is reassessed and executed in its
  own transaction through the existing one-Account engine;
- mixed `APPLIED`, `PENDING_RENEWAL`, `AWAITING_PAYMENT`, `CONFLICT`, `FAILED`, and `CANCELLED`
  results are retained, paginated, retryable only where safe, and never flattened into false
  all-or-nothing success;
- list, detail, results, Account-identity reveal, cancel, and retry are separate Permissionizer
  surfaces with permission-before-existence security tests;
- the admin portal provides a guided Account chooser, shared commercial selector, reason/schedule,
  signed population review, explicit confirmation, progress/results, identity reveal, and guarded
  cancel/retry using shared table and action patterns;
- identity wiring tests prove ordinary result access neither requests nor renders Account identity.

Verification at this checkpoint: 763 backend tests and 314 frontend tests, with frontend typecheck,
Biome, production build, and `git diff --check` green. Authenticated French/Arabic browser evidence
is still pending because no credential was entered during the automated run.

The second slice adds the one-Account lifecycle control plane:

- every command requires its own Permissionizer node, a separate preview permission, a short-lived
  actor/action/version-bound review, and a non-empty operator reason;
- operator suspension is distinct from collection suspension. Only operator suspension can be
  restored directly, and collection recovery requires settlement or an explicit grace extension;
- immediate cancellation closes outstanding subscription changes, preserves the terminal
  subscription read model, and revokes every current Account member access and refresh session;
- suspension also revokes sessions. Restoration never resurrects those tokens, so members must
  authenticate again;
- append-only lifecycle events retain before/after state, deadline changes, effective time, actor,
  and reason through a bounded independently permissioned history surface;
- the admin detail page renders only backend-available actions intersected with the exact operator
  permissions, then requires the signed review before confirmation.

Still open in Phase 12: `FILTERED_ACCOUNTS`, `PLAN_SUBSCRIBERS`, reviewed trial/new-entitlement
creation, population lifecycle jobs, correction-from-result, communication delivery state, and the
client-facing pending-job projection. The Account commercial/financial timeline is Phase 13.
