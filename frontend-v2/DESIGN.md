# Administration design and capability review

## What changed in the paths

The original administration surface gave many individual resources their own top-level route and sidebar position. This made a catalog task, customer task and execution task feel disconnected even when the backend already related them. The first v2 also contained demonstration data and explanatory copy that made it unsuitable for an operational handoff. Both problems were addressed in this version.

Five daily areas replace feature-by-feature navigation: **Overview, Customers, Catalog, Commercial and Operations**, with Settings secondary. The personal Inbox is a header destination. Billing has no primary sidebar position while payment remains deferred. Full-width tables, compact record summaries and section separators carry the working content. Local views preserve global discovery while records provide related data and contextual actions.

| Area               | Local structure                                                                                                                                        |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Customers          | Directory, Agreements, Communications and Invoices; individual records contain subscription, billing/profile history, changes, agreements and activity |
| Catalog            | Plans, Add-ons, Capacity packages and Prices; plan families/revisions have Features, Prices, Subscribers, Versions, Comparison and History contexts    |
| Commercial         | Offers, Campaigns, Audiences and Commercial rules; campaigns link to their offers, and published audience activations can start a campaign             |
| Operations         | Work queue, Executions and Delivery, with Audit activity secondary; execution and delivery types use selectors                                         |
| Settings / Profile | Operators, Access roles, Platform controls and System status; Profile contains My access, Notifications and Appearance                                 |

The Work queue uses the existing attention endpoint. Executions share a workspace and a kind selector, not a combined read model; plan applications still require a plan revision before listing. System health/backlogs/registry/logs belong under Settings. Customer communication definitions remain under Customers, while delivery evidence and recovery belong under Operations.

| Task                    | Current path and flow                                                                                                                        |
| ----------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| Manage a customer       | Customers → customer record → subscription, billing, changes, agreements or activity                                                         |
| Change one subscription | Customer → prefilled current/retained terms → timing/reason → impact review → single-customer apply → exact customer change                  |
| Change a population     | Directory selection → complete target → replacement acknowledgement → timing/reason → job review → confirmation → execution/results          |
| Maintain a plan         | Catalog → plan family → features, prices, subscribers, versions and history                                                                  |
| Apply plan content      | Plan record with target preselected → source revision/family scope → customers → timing/notices → durable assessment → confirmation/results  |
| Change a tariff         | Price record → current and target price → audience → preview → confirmation → outcomes and recovery                                          |
| Author an offer         | Campaign and dates → products → terms → definition review → save; publication remains a separate lifecycle action                            |
| Create an agreement     | Customer → subscription terms → term/end behavior → review → create → agreement state                                                        |
| Create an operator      | Identity → active assignable roles or explicit assign-later choice → initial access → review → atomic create/role assignment → access result |
| Recover failed work     | Operations → execution/delivery → individual outcome/evidence → permitted retry or cancellation                                              |

Plans default to families, reducing duplicate revision rows. Secondary plan views are grouped beneath Features, Subscribers, Versions and History. Repricing targets are confined to the selected product when its identity can be read. Selectors use backend records and paginated search; forms use typed fields for audiences, products, permissions, quota resources and commercial effects.

Cross-family and revision comparisons have dedicated difference views, and the feature inspector connects entitlements, quota resources, extensions and permissions. Single-customer terms preserve retained holdings explicitly. Bulk selection still uses the first selected customer's catalog and replaces a complete configuration; a population-preservation command is not available.

Payment collection, settlement, refunds, charge recovery and provider reconciliation are deferred. `paymentEnabled = false` hides payment mutation surfaces. Existing authorized invoices/documents, agreements and customer financial history remain readable, including historical payment-related states. No screen or development record establishes provider readiness.

## References

Reference searches included customer records, billing dashboards and modern application design systems. Linear's official interface imagery was inspected. References informed patterns and hierarchy; third-party assets were not copied into the application.

- [Linear — UI redesign](https://linear.app/now/how-we-redesigned-the-linear-ui): quiet chrome, consistent headers and deliberate hierarchy. [Inspected official imagery](https://webassets.linear.app/images/ornj730p/production/73b5bcd3d1d73d0b15322d5cbb57c0aae7ff7b5f-2352x1380.png?auto=format&dpr=2&q=95).
- [Stripe — Customer information and payments](https://support.stripe.com/questions/manage-a-customer-s-information-and-payments): financial and subscription actions connected to the customer record.
- [Stripe — Dashboard search](https://docs.stripe.com/dashboard/search): direct paths to records. Hive's command menu now offers permitted pages/tasks and scoped customer, plan, offer and agreement lookup through existing list APIs; it does not provide a unified search endpoint.
- [Chargebee — Product catalog plans](https://www.chargebee.com/docs/billing/2.0/product-catalog/plans): product and price structure, separated from customer subscription administration.
- [Chargebee — Building blocks](https://www.chargebee.com/docs/billing/2.0/getting-started/building-blocks-overview): related commercial records organized around their purpose.
- [Stripe — Subscription schedules](https://docs.stripe.com/billing/subscriptions/subscription-schedules): explicit timing for future subscription changes.
- [Stripe — Billing automations](https://docs.stripe.com/billing/automations): a reference for trigger-driven automation. Hive currently has scheduled execution primitives, rather than a persisted recipe engine.

## Visual and interaction system

Inter is served locally. Neutral surfaces, dark olive text and a restrained green accent share primitive, semantic and component CSS tokens. Status colors carry consistent meanings. The layout uses tables, compact summaries, grouped record views and separators. Rich values expand into readable evidence; quotas display their resource and limit instead of serializing an object into a cell.

Native controls and modal dialogs provide keyboard behavior. The app includes a skip link, focus indicators, reduced-motion support, a navigation drawer at narrow widths and tables that scroll inside their containers. URL parameters preserve local views, searches and filters. Forms protect unsaved changes; the guided subscription flow additionally preserves its draft within the current session.

Contextual links use validated same-origin `returnTo` paths. Back links restore the originating customer, record or filtered list; the router remembers scroll positions during navigation. Remembered local tab queries retain the current account and return destination. This is session navigation state, not a persisted saved-view service. Meaningful path changes focus the main content.

Profile has one inline notification topic/channel matrix with separate notification-language preferences, a searchable inventory of session permissions, and light/dark/system appearance. Appearance uses semantic theme tokens and browser storage keyed by operator; it is not synchronized through a backend preference API. The interface remains English: French/Arabic notification-language support does not provide full translated UI or RTL parity. Role creation and permission changes use a searchable resource-group editor with selection counts, grantable permissions, explicit removal of unavailable existing grants and the backend’s creation limit. Group selection applies only to the visible matches.

Failures have explicit retry paths. Request generation guards stop old responses from replacing newer route or filter results. Mutations invalidate resource reads. Polling is restricted to active operations and visible pages. Money stays in exact decimal strings outside chart drawing.

## Existing API capabilities used

All paths below are relative to `/api/admin`. Most workflows compose existing contracts. This iteration also extends operator creation with optional `roleIds`; omitting it preserves the prior client behavior. Backend development helpers and persistence mappings already exist in the repository and are distinct from the new request field.

| UI surface                | Existing contract families                                                                                                                                     |
| ------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Overview and reports      | `/plans/overview`, `/analytics/overview`, `/financial-series`, `/subscription-series`, `/offer-series`, `/product-holdings`, `/attention` beneath `/analytics` |
| Customer context          | `/subscriptions/accounts/search`, account chooser/resolution, `/subscriptions/account/{id}` and its change, lifecycle and agreement operations                 |
| Subscription changes      | `/subscription-change-jobs/preview`, confirmation, detail, paginated results, protected identities, retry and cancel                                           |
| Repricing                 | `/subscription-repricing`: preview, confirm, results, protected identities, cancel and recovery                                                                |
| Plan content applications | `/plan-version-applications`: assessment, review, confirm, results, protected identities and recovery; plan family subscribers and version history             |
| Catalog                   | `/plans`, `/add-ons`, `/quota-packages`, `/price-books`: authoring, revisions, composition, compatibility, availability, activation and history                |
| Commercial definitions    | Campaign, segment, commercial-policy and offer CRUD, lifecycle, audience/snapshot, revision, ownership and redemption contracts                                |
| Customer billing records  | `/billing`: authorized account profiles, invoices/documents and financial timeline; payment mutations and provider recovery remain deferred                    |
| Communications            | Customer message definitions and delivery; operator notifications, preferences and delivery recovery; credential-email identity and failure evidence           |
| Administration            | `/users` (additive optional `roleIds` on creation), `/roles`, registry catalog/controls and sync status; `/me`; activation, reset and verification             |
| Operational evidence      | Activity projections and separately protected payload/identity reads; observability health, backlogs and log access                                            |

Permissions are checked per request. Protected account identities, recipients and evidence are not inferred from opaque IDs. Missing permissions and failed reads are not represented as successful empty responses. The backend remains responsible for lifecycle validity, eligibility, concurrency, financial finality and mutation authorization.

Single-customer preview/apply and population-job preview/confirm use their own permissions. Narrow campaign/offer operation projections allow permitted lifecycle work without treating editable-definition access as a prerequisite. A plan-definition edit does not implicitly move existing subscribers; content application and repricing remain explicit reviewed flows.

`POST /users` accepts optional `roleIds` with at most 100 UUIDs. The existing five-argument Java request constructor is preserved. `AdminOperatorProvisioningService` creates the operator and assigns requested roles within one transaction through existing guarded services. Invalid or denied assignments roll back creation; activation delivery runs after commit. The response includes assigned roles without requiring an extra operator-detail read. Existing clients may omit the field, and no new role-assignment table or column is required. This is a backward-compatible request-contract extension, not an absence of API/schema changes.

## Backend improvements recommended

These are remaining capabilities or read-model improvements. Atomic operator creation with role assignment is implemented and is no longer in this backlog.

1. **Audience-aware selection catalog.** Add a bulk catalog projection with compatible choices, coverage counts and reasons across the selected audience. It would remove the first-customer dependency and reduce blocked previews.
2. **Permission-aware customer projection.** Aggregate customer identity, subscription, invoice, agreement and communication summaries without requiring broad access to each domain. Keep sensitive identity and evidence projections independent.
3. **Unified execution index.** Offer one searchable read model across change jobs, repricing, plan applications and scheduled agreement work. Keep the existing domain state machines and per-result permissions authoritative. The current plan-application list requires a plan filter.
4. **Typed destinations.** Return resource type, ID, local view and suggested action instead of legacy frontend URL strings. The v2 currently translates those paths, which is vulnerable to changes in old route names.
5. **Actionable readiness.** Expose whether email delivery, trusted payments and processors are configured, with permitted remediation actions. Health/backlog data alone cannot establish end-to-end integration readiness.
6. **Persisted automation recipes.** If configurable automation is a product requirement, add triggers, conditions, scoped execution, approvals, history and recovery. Reusing scheduled jobs is useful, but does not provide a complete recipe engine.
7. **Consistent action projections and narrow owner choosers.** The shared owner identity mapping now uses `adminUserId`, but segment/policy ownership still relies on campaign owner-choice authority. Add domain-specific segment/policy chooser and resolver projections. Extend authoritative action/blocker projections to domains that still rely on UI predicates.
8. **Efficient audience usage.** Campaigns can list their offers and audience activations can start campaigns. Reverse usage/impact across segments, policies and campaigns still needs targeted backend projections; do not scan all records in the browser.
9. **Cross-resource search.** Scoped customer/plan/offer/agreement lookup is implemented through existing APIs. Add a unified permission-aware endpoint for broader invoice, price and execution lookup if required.
10. **Safe re-review of saved jobs.** Durable subscription previews have cancellation but no complete resume/reassessment command that refreshes actor-bound and expiring review tokens.

Frontend follow-ups include richer catalog/commercial filters and sorting, consistent field-level errors and full French/Arabic interface/RTL parity. Customer search now supports account activity, subscription presence and plan code through the existing endpoint. These should not be presented as completed by the appearance or navigation changes.

## Validation and boundaries

The 4 October 2026 implementation passed the Vue/TypeScript production build and Java 21 backend compilation, with all tests skipped. Authenticated desktop/mobile review covered customer directory and retention, single-customer backend impact review, published offer detail and offer setup, effective plan features, grouped role authoring, queue filtering and execution-to-exact-customer-outcome navigation. An older saved job exposed nullable price selection; its contract and rendering now support the actual backend shape. Mobile navigation closes, moves focus to main content and avoids document-level horizontal overflow.

[README.md](README.md) lists current screenshot artifacts. The implementation-status table in [UX-AUDIT.md](UX-AUDIT.md) records source coverage rather than exhaustive runtime acceptance; restricted-role behavior was reviewed in source. Every state/permission combination has not been exercised live.

Prior browser review did not submit consequential financial, email-delivery or access mutations. The optional development customer seeder populates persisted subscription, ledger, lifecycle, agreement, campaign/offer and durable execution scenarios through backend services; see [CUSTOMER-SCENARIOS.md](CUSTOMER-SCENARIOS.md). Those records support UI acceptance but do not establish production email/payment readiness. Payment-provider acceptance belongs to the deferred payment delivery. The separate client/company portal remains in the original frontend; original administration placeholders are not represented as available capabilities.
