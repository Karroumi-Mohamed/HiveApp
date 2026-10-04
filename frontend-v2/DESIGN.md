# Administration design and capability review

## What changed in the paths

The original administration surface gave many individual resources their own top-level route and sidebar position. This made a catalog task, customer task and execution task feel disconnected even when the backend already related them. The first v2 also contained demonstration data and explanatory copy that made it unsuitable for an operational handoff. Both problems were addressed in this version.

Six working areas replace feature-by-feature navigation. Local views provide the resource lists; records contain related data and contextual actions. Full-width tables, record summaries and section separators replace the overview's card mosaic. Page headers use one title. Operational descriptions appear only when they explain a material choice, restriction or outcome.

| Task                          | Current path and flow                                                                                                                     |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| Manage a customer             | Customers → customer record → subscription, billing, changes, agreements or activity                                                      |
| Change subscriptions          | Directory selection or customer record → complete target → timing → impact preview → confirmation → durable job and results               |
| Maintain a plan               | Catalog → plan family → features, prices, subscribers, versions and history                                                               |
| Apply plan content            | Plan record → source and target revision → audience and timing → assessment → confirmation → per-customer outcome                         |
| Change a tariff               | Price record → current and target price → audience → preview → confirmation → outcomes and recovery                                       |
| Manage commercial eligibility | Commercial → segment/campaign/policy/offer → typed definition → authoritative review → lifecycle action → audience or redemption evidence |
| Investigate payment issues    | Overview attention or Billing → invoice/customer → settlement evidence, command or provider event                                         |
| Recover failed work           | Operations → execution/delivery → individual outcome/evidence → permitted retry or cancellation                                           |

Plans default to families, reducing duplicate revision rows. Secondary plan views are grouped beneath Features, Subscribers, Versions and History. Repricing targets are confined to the selected product when its identity can be read. Selectors use backend records and paginated search; forms use typed fields for audiences, products, permissions, quota resources and commercial effects.

## References

Reference searches included customer records, billing dashboards and modern application design systems. Linear's official interface imagery was inspected. References informed patterns and hierarchy; third-party assets were not copied into the application.

- [Linear — UI redesign](https://linear.app/now/how-we-redesigned-the-linear-ui): quiet chrome, consistent headers and deliberate hierarchy. [Inspected official imagery](https://webassets.linear.app/images/ornj730p/production/73b5bcd3d1d73d0b15322d5cbb57c0aae7ff7b5f-2352x1380.png?auto=format&dpr=2&q=95).
- [Stripe — Customer information and payments](https://support.stripe.com/questions/manage-a-customer-s-information-and-payments): financial and subscription actions connected to the customer record.
- [Stripe — Dashboard search](https://docs.stripe.com/dashboard/search): direct paths to records. Hive's command search currently covers areas and permitted customer search; it does not claim a cross-resource search API.
- [Chargebee — Product catalog plans](https://www.chargebee.com/docs/billing/2.0/product-catalog/plans): product and price structure, separated from customer subscription administration.
- [Chargebee — Building blocks](https://www.chargebee.com/docs/billing/2.0/getting-started/building-blocks-overview): related commercial records organized around their purpose.
- [Stripe — Subscription schedules](https://docs.stripe.com/billing/subscriptions/subscription-schedules): explicit timing for future subscription changes.
- [Stripe — Billing automations](https://docs.stripe.com/billing/automations): a reference for trigger-driven automation. Hive currently has scheduled execution primitives, rather than a persisted recipe engine.

## Visual and interaction system

Inter is served locally. Neutral surfaces, dark olive text and a restrained green accent share primitive, semantic and component CSS tokens. Status colors carry consistent meanings. The layout uses tables, compact summaries, grouped record views and separators. Rich values expand into readable evidence; quotas display their resource and limit instead of serializing an object into a cell.

Native controls and modal dialogs provide keyboard behavior. The app includes a skip link, focus indicators, reduced-motion support, a navigation drawer at narrow widths and tables that scroll inside their containers. URL parameters preserve local views, searches and filters. Forms protect unsaved changes; the guided subscription flow additionally preserves its draft within the current session.

Failures have explicit retry paths. Request generation guards stop old responses from replacing newer route or filter results. Mutations invalidate resource reads. Polling is restricted to active operations and visible pages. Money stays in exact decimal strings outside chart drawing.

## Existing API capabilities used

All paths below are relative to `/api/admin`. No API schema changes or backend source edits were needed. The database, bootstrap configuration and development launcher live outside the committed source configuration.

| UI surface                | Existing contract families                                                                                                                                     |
| ------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Overview and reports      | `/plans/overview`, `/analytics/overview`, `/financial-series`, `/subscription-series`, `/offer-series`, `/product-holdings`, `/attention` beneath `/analytics` |
| Customer context          | `/subscriptions/accounts/search`, account chooser/resolution, `/subscriptions/account/{id}` and its change, lifecycle and agreement operations                 |
| Subscription changes      | `/subscription-change-jobs/preview`, confirmation, detail, paginated results, protected identities, retry and cancel                                           |
| Repricing                 | `/subscription-repricing`: preview, confirm, results, protected identities, cancel and recovery                                                                |
| Plan content applications | `/plan-version-applications`: assessment, review, confirm, results, protected identities and recovery; plan family subscribers and version history             |
| Catalog                   | `/plans`, `/add-ons`, `/quota-packages`, `/price-books`: authoring, revisions, composition, compatibility, availability, activation and history                |
| Commercial definitions    | Campaign, segment, commercial-policy and offer CRUD, lifecycle, audience/snapshot, revision, ownership and redemption contracts                                |
| Billing                   | `/billing`: account profile, invoices and documents, payments, credits, refunds, financial timeline, reconciliation and provider events                        |
| Communications            | Customer message definitions and delivery; operator notifications, preferences and delivery recovery; credential-email identity and failure evidence           |
| Administration            | `/users`, `/roles`, registry feature catalog/controls and sync status; `/me`; activation, reset and verification                                               |
| Operational evidence      | Activity projections and separately protected payload/identity reads; observability health, backlogs and log access                                            |

Permissions are checked per request. Protected account identities, recipients and evidence are not inferred from opaque IDs. Missing permissions and failed reads are not represented as successful empty responses. The backend remains responsible for lifecycle validity, eligibility, concurrency, financial finality and mutation authorization.

The subscription change chooser still uses the first selected customer's catalog, because that is the current API. The job preview assesses every selected account. A plan-definition edit does not implicitly move existing subscribers; content application and repricing remain explicit reviewed flows.

## Backend improvements recommended

These recommendations are not implemented by this frontend change.

1. **Audience-aware selection catalog.** Add a bulk catalog projection with compatible choices, coverage counts and reasons across the selected audience. It would remove the first-customer dependency and reduce blocked previews.
2. **Permission-aware customer projection.** Aggregate customer identity, subscription, invoice, agreement and communication summaries without requiring broad access to each domain. Keep sensitive identity and evidence projections independent.
3. **Unified execution index.** Offer one searchable read model across change jobs, repricing, plan applications and scheduled agreement work. Keep the existing domain state machines and per-result permissions authoritative. The current plan-application list requires a plan filter.
4. **Typed destinations.** Return resource type, ID, local view and suggested action instead of legacy frontend URL strings. The v2 currently translates those paths, which is vulnerable to changes in old route names.
5. **Actionable readiness.** Expose whether email delivery, trusted payments and processors are configured, with permitted remediation actions. Health/backlog data alone cannot establish end-to-end integration readiness.
6. **Persisted automation recipes.** If configurable automation is a product requirement, add triggers, conditions, scoped execution, approvals, history and recovery. Reusing scheduled jobs is useful, but does not provide a complete recipe engine.
7. **Consistent action projections and narrow choosers.** Some domains return available actions and review tokens, while others expose less context. Standardize action availability and associated read permissions. Add dedicated owner choosers where policy/segment authoring currently shares campaign owner choices.
8. **Cross-resource search.** Add a permission-aware search endpoint for customers, plans, offers, invoices and executions. Avoid downloading full resource collections to imitate it in the browser.

## Validation and boundaries

Production compilation and Vue/TypeScript checks pass. Authenticated browser and API review used the actual local backend, its existing seeders and persistent database. Catalog data survived restart; no demonstration transactions were inserted. Browser review covers grouped plan views, actual quotas/prices, reports, authoring selectors, local navigation, draft protection and responsive layout. The screenshots in `previews/` show current database-backed screens.

Existing test files and test suites were excluded. Consequential financial, email-delivery and access mutations were not executed in the browser. The optional development customer seeder now populates connected subscription, billing, lifecycle, agreement, campaign/offer and durable execution scenarios through backend services; see [CUSTOMER-SCENARIOS.md](CUSTOMER-SCENARIOS.md). These are persisted development records and support UI acceptance. Production acceptance still requires authorized customer records and configured email/payment providers. The separate client and company portal remains in the original frontend; original administration placeholders are not represented as available capabilities.
