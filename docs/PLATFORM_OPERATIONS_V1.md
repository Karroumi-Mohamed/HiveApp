# Platform Operations V1

**Status:** Frozen implementation contract  
**Date:** 2026-08-31  
**Scope:** Platform-admin Activities, credential-email delivery operations, and production observability

## 1. Purpose and boundaries

HiveApp needs three operational surfaces, not one unrestricted "logs" page:

| Surface | Question answered | Authoritative source |
|---|---|---|
| Activities | Who changed what, where, when, and with what outcome? | Append-only `AuditLog` mutation evidence |
| Communications | Was a credential email requested and delivered? | Durable `EmailDelivery` status rows |
| Observability | Is the application healthy, are internal queues progressing, and where can an operator investigate further? | Safe health/backlog projections plus an external log-provider boundary |

These surfaces must not expose secrets, raw credentials, email bodies, provider payloads, arbitrary database rows, stack traces, environment values, or unbounded application logs.

This contract does not change `AUDIT-002`. V1 exposes the mutation evidence already recorded by `AUDIT-001`; it does not begin recording successful or denied reads.

## 2. Activities

### 2.1 Permissions

`platform.activities` declares independent permissions:

- `read`: list and inspect safe activity metadata;
- `read_actor_identity`: resolve an actor id to the minimum platform identity needed for investigation;
- `read_account_identity`: resolve a target Account id to its name and slug;
- `read_payload`: inspect the already-sanitized request/result summaries and bounded failure type.

`read` never implies any identity or payload permission. SuperAdmin remains the authority root.

### 2.2 API

- `GET /api/admin/activities`
  - default range: the previous 24 hours;
  - maximum range: 366 days;
  - filters: `from`, `until`, outcome, actor surface, action prefix, resource type/id, actor id, and target Account id;
  - stable newest-first pagination by event time then id;
  - page size 1–100;
  - optional actor/Account labels appear only when their corresponding permissions are held.
- `GET /api/admin/activities/{id}` returns the same safe metadata for one event.
- `GET /api/admin/activities/{id}/payload` is separately protected and returns only the redacted summaries already persisted by the audit subsystem.

The API has no update or delete endpoint. Audit evidence remains append-only. V1 has no bulk export: a later export must define its own permission, streaming bound, redaction, retention, and download audit.

### 2.3 UI

The Activities page is a searchable investigation table, not analytics cards:

- compact date, outcome, surface, resource, action, actor-id, and Account-id filters;
- human-readable action/outcome first, technical action code available as secondary evidence;
- failed events visually distinct without relying only on color;
- a detail panel containing safe request path/method, resource/scope identifiers, correlation id when available, and permission-gated payload;
- explicit loading, empty, error, and retry states;
- no fabricated actor or Account label when identity permission is absent.

## 3. Communications

### 3.1 Product truth

HiveApp deliberately does not persist credential-email bodies, action URLs, or raw tokens. The page therefore reports delivery evidence; it cannot preview content or replay the same email.

A new activation, verification, or reset message must be issued through the owning operator/member workflow. That operation rotates or creates credentials under its existing authorization and audit rules. A generic "retry" button on `EmailDelivery` is prohibited.

### 3.2 Permissions

`platform.communications` declares:

- `read`: summary and safe delivery metadata;
- `read_recipient_identity`: recipient user id and email;
- `read_failure_evidence`: bounded failure category and attempt timestamps.

Delivery metadata never grants access to the target Account, operator, or member record.

### 3.3 API and UI

- `GET /api/admin/communications/summary` returns counts by durable delivery status and purpose.
- `GET /api/admin/communications` supports a bounded range, status, purpose, Account id, recipient user id, and stable pagination.
- `GET /api/admin/communications/{id}` returns one delivery record under the same field-level permission rules.

The UI is a status workbench with filters, a delivery timeline, identity fields only when allowed, and a clear explanation that regeneration belongs to the relevant identity-management page. `SUPPRESSED` means the development transport intentionally did not deliver; it must never be labelled sent.

## 4. Observability

### 4.1 Correlation contract

Every HTTP request receives a bounded correlation id. A valid incoming `X-Request-ID` may be preserved; otherwise HiveApp generates one. The id is:

- placed in logging MDC for the request lifetime;
- returned in `X-Request-ID`;
- included in normalized `ApiError` responses;
- attached to new `AuditLog` records.

It is evidence and a search key, never an authorization credential.

### 4.2 Permissions and API

`platform.observability` declares:

- `read_health`: safe application/database readiness and dependency-configuration state;
- `read_backlogs`: bounded counts/oldest-age for provider events, billing outbox commands, and failed/pending email delivery;
- `read_log_access`: whether an external log provider is configured and its safe investigation destination.

Endpoints:

- `GET /api/admin/observability/health`
- `GET /api/admin/observability/backlogs`
- `GET /api/admin/observability/log-access`

Health responses expose named component states and safe operator guidance, never connection strings, credentials, environment values, exception text, or stack traces. Backlog reads are aggregate and bounded. A log destination is configuration, not a replacement for authorization; when absent the UI says external logs are not configured.

### 4.3 External logs

HiveApp does not copy raw production logs into its business database. Log search belongs behind an adapter for an external provider such as Loki/OpenSearch. V1 may expose configuration/availability and correlated navigation, but must not fabricate an in-memory log history.

## 5. Performance and privacy

- Every list is server-paginated and range-bounded.
- Actor and Account identities are bulk-resolved only when the caller has the relevant permission; no hidden identity query runs otherwise.
- Filters are allowlisted and normalized; no client field name becomes a persistence sort/expression.
- Activity payloads render as escaped text/JSON, never HTML.
- Counts use database aggregation and do not load every row.
- Permission checks exist at service boundaries; hiding a frontend tab is not authorization.

## 6. Required verification

- independent permission tests for metadata, actor identity, Account identity, payload, recipient identity, failure evidence, health, backlogs, and log access;
- cross-Account and cross-surface privacy tests;
- default/maximum date-range and page-size validation;
- stable pagination with equal timestamps;
- bounded query-count tests proving no per-row identity lookup;
- correlation-id generation, valid propagation, invalid replacement, response-header, `ApiError`, MDC cleanup, and audit persistence tests;
- no raw token, action URL, body, exception message, stack trace, secret, or provider payload in any response;
- frontend filter/query-state, permission degradation, detail, loading/error/empty, keyboard, dark/light, narrow-screen, and RTL tests;
- authenticated browser verification of realistic investigation and delivery-failure flows.

## 7. Explicit deferrals

- read-access and denied-read audit (`AUDIT-002`);
- automatic deletion/retention policy before compliance requirements are chosen;
- audit CSV/JSON export;
- generic credential-email resend or stored-content preview;
- a built-in raw-log store/viewer;
- traces, metrics-series storage, alerts, and incident workflow beyond the external-provider boundary.
