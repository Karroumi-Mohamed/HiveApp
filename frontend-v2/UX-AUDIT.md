# Frontend v2 Workflow Parity and UX Audit

Reviewed 4 October 2026. This report is for the product manager and implementation team. It compares functioning workflows in `frontend` with their equivalents in `frontend-v2`, checks the API and backend support behind them, and recommends improvements to administrative work.

**Frontend v2 does not yet preserve all old workflows.** It has substantial admin coverage and reasonable broad navigation groups, but the implemented hierarchy still follows entities more than administrative tasks. Several actions, permission-specific paths, investigation details, and useful list controls were lost. Some workflows also became harder because specialized workbenches were replaced with generic forms. Most corrections can use existing backend contracts. A wholesale backend rewrite is not justified by the findings.

The next iteration should improve task completion within the main areas: preserve current customer terms, make review meaningful, connect exceptions to their resolutions, and show actions appropriate to the record and operator.

## Current delivery scope and UX plan

The user clarified after this audit that payment has not been added yet. Financial code paths described below are source-level observations, not evidence that payment is ready for the current product. Payment collection, settlement, refunds, charge recovery and provider reconciliation are deferred. They must not be presented as finished product actions or replaced with demonstration behavior. Existing invoice/agreement records can remain visible only where the current backend capability is verified.

The current UX plan prioritizes five daily areas: Overview, Customers, Catalog, Commercial and Operations, with Settings as secondary navigation. Billing should not occupy a full primary area merely to house payment scaffolding. A dedicated billing workspace can be introduced when verified global billing work warrants it.

| Stage | Deliverable | Intended user sequence |
| --- | --- | --- |
| 1 Restore task integrity | Correct retention, single-customer authority, owner mapping, legal actions, notification links/restoration and useful filters | Every visible action matches current backend support, record state and operator authority. |
| 2 Customer workspace | Compact customer summary, subscription/agreements/history contexts, contextual communication and a prefilled single-customer editor | Open customer → edit current terms → choose timing → inspect differences → confirm → see the exact outcome in the customer record. |
| 3 Catalog and commercial workbenches | Family/revision browsing, comparison, effective feature inspection and grouped offer/agreement editors | Open related record → define terms progressively → review business effect → save/activate through existing commands. |
| 4 Operations work queue | Actionable exceptions, execution history with kind filters, delivery investigation and direct subject links | See issue → open its subject → resolve permitted problem → verify outcome without losing queue position. |
| 5 Access and personal settings | Eligible role selection, grouped permission editing, reviewed impact and one personal preference screen | Set up identity → choose access → review → provision/invite in the order supported by the backend. |
| 6 Consistency and navigation review | Preserved filter/scroll state, stable return paths, readable field errors, focused route changes, localization and responsive layouts | Complete representative tasks in context, including restricted-role and empty/error states. |

Use full-width lists, compact summary strips and contextual panels. Keep optional controls behind disclosures; keep the next meaningful action visible. Avoid bento layouts and promotional copy. The design should reduce repeated selection and context switching, rather than simply reduce route counts.

Most immediate stages use existing APIs. Atomic operator onboarding, complete population impact review and a unified operation queue may justify additive backend work. General configurable automation is a separate future capability; improve the existing scheduled-job sequence now.

## Navigation structure: what needs to change

The five proposed daily areas are a starting point, not an endorsement of the current sidebar and tabs. Moving independent pages behind one tab bar reduces sidebar length but does not establish a usable workflow. The current implementation has the following structural problems:

| Problem | Consequence for administrative work | Proposed correction |
| --- | --- | --- |
| Operations contains nine peer tabs spanning changes, repricing, plan applications, messages, personal notifications, attention, activity, delivery and system status | Creating customer communications, monitoring executions, handling issues and inspecting infrastructure compete at one navigation level. Subscription changes is the default even when another task needs attention. | Use Work queue, Executions and Delivery as primary local views. Execution kind becomes a filter or an interim selector. Customer communication authoring moves to Customers; personal Inbox remains in the header. Audit and system diagnostics remain reachable as secondary views. |
| Commercial is four independent entity lists | Campaign → audience → offer → activation/outcome is not connected. Operators repeatedly leave the record and reselect relationships. | Keep Offers and Campaigns easy to discover; provide linked audience/rule views, related offers on campaigns, and creation/application with the originating context preselected. Retain global audience and rule indices for cross-record work. |
| Customer actions leave the record without a reliable return path | Message opens Operations, Offers opens Commercial, and Change opens a standalone route. Generic backlinks return to the resource index rather than the originating customer or filtered list. | Preserve the customer workspace as the task context. Use focused editors or full-page flows with an explicit return destination. Confirmation should return to the relevant customer section or exact execution result. Keep global indices for bulk and cross-customer work. |
| Billing is still primary despite payment being deferred | Agreements are grouped with provider/payment infrastructure. Removing Billing without relocating the supported indices would strand global agreement discovery. | Put global Agreements under Customers and agreement detail in the customer record. Keep verified invoice/profile information accessible in customer context. Introduce a primary Billing area when actual global billing work justifies it. |
| Entry points depend on inconsistent permissions and shallow search | A permitted task can lack an obvious entry point; header destinations may resolve to another available tab. The command menu cannot locate plans, offers, agreements or executions directly. | Make navigation reflect independently authorized tasks and record projections. Provide permission-aware direct destinations, clear unavailable states and cross-resource lookup as backend support permits. |

Evidence: [Operations tabs](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/OperationsView.vue:51), [Commercial hub](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/router.ts:35), [campaign sections](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/commercial.ts:338), [customer task links](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/CustomerView.vue:207), [generic backlink](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/ResourceView.vue:320), [Billing tabs](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/BillingView.vue:16), [header and command destinations](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/App.vue:259).

The proposed hierarchy is:

| Sidebar area | Local work and related context |
| --- | --- |
| Overview | Concise summaries and attention links that open the exact filtered queue or subject. |
| Customers | Directory, Agreements and Communications. Within a customer: Subscription, Agreements, Changes and Activity, plus verified invoice/profile information. |
| Catalog | Plans, Add-ons, Capacity and Prices. Within a product: features/compatibility, prices, subscribers, versions and history. Start revision application and repricing from their source records, then inspect execution in Operations. |
| Commercial | Offers and Campaigns, with discoverable audience and commercial-rule views. Link campaign/audience usage and preselect related records when starting the next task. |
| Operations | Work queue, Executions and Delivery. Filter executions by kind and state; retain audit and diagnostics in secondary navigation. |
| Settings, secondary | Operators, Roles and Platform controls. Personal preferences belong with Profile; personal notifications belong in the header Inbox. |

Catalog is the strongest existing grouping: distinct product types and a global price index support real cross-product work. It still needs better family/version comparison and context-preserving return paths. It should not be compressed merely to reduce the number of tabs.

The key handoff is **source record → configuration → meaningful review → permitted execution → exact outcome → return to source**. For example, a plan revision should lead to version comparison, affected-customer selection, impact review, confirmation and linked outcomes. Operators should not have to independently locate each page and recreate that context.

Most placement and return-path corrections are frontend work. A unified execution list and complete work queue need additive backend projections or an explicit interim kind selector. Current plan applications require a plan selection, and the current Attention projection does not cover every actionable exception; hiding their existing entry points would reduce capability reachability. This proposed hierarchy has not been implemented by this audit.

## Scope and evidence

This is a production-source audit of routes, rendered actions, forms, API calls, DTOs, controllers and relevant service/domain rules. Test files and suites were excluded. Existing screenshots of the plan and customer Changes views were inspected. The local servers were not running during this audit; no fresh interactive execution or financial mutation was performed. “Present” below means the UI is wired to the capability, not that every state combination has been executed successfully.

The old application includes both platform administration and a tenant/customer portal. V2 currently implements platform administration. The client portal is still in the original frontend: company/member/organization/client-role/B2B management, tenant authentication, self-service subscription/offer flows and account settings have no equivalent v2 routes. This is a migration scope boundary, but it means v2 cannot replace the entire old application. See [old portal routes](/Users/Hemmi/Projects/HiveApp/frontend/src/App.tsx:568) and [v2 routes](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/router.ts:12).

Old admin Accounts, Collaborations and Role templates were placeholders. Their absence is not a loss of working functionality: [old routes](/Users/Hemmi/Projects/HiveApp/frontend/src/App.tsx:465), [placeholder implementations](/Users/Hemmi/Projects/HiveApp/frontend/src/features/admin/admin-placeholder-pages.tsx:25).

## Workflow comparison

| Workflow | V2 equivalent and coverage | Remaining gap |
| --- | --- | --- |
| Admin sign-in and credential recovery | Login and activation/reset/verification/initial-password routes; refresh and logout | Present. Legacy admin completion links have aliases. |
| Overview and analytics | Platform Overview and Reports | Present; overview attention links exist. Default metrics and actions need stronger permission/context handling. |
| Customer discovery | Customers, status/search/sort, owner-email lookup, selection | Present; account-active, has-subscription, plan and broader sorting controls are missing. |
| Single-customer subscription change | Customer → Change subscriptions | Partial: forced through bulk authority; current and retained holdings are not initialized or fully represented. |
| Bulk and scheduled changes | Four-step change wizard → execution record | Present; cancellation state rules, abandoned previews and full preconfirmation review need correction. |
| Subscription lifecycle | Customer → Manage lifecycle | Present: suspend, restore, cancel now/end, keep renewing, extend grace, preview and reason. Backend action availability is used here. |
| Individual pending change settlement/cancel | Customer → Changes | Present; the investigation/provenance/payment detail path is missing. |
| Customer billing | Billing profile, invoices and financial timeline | Present; timeline and transaction evidence need contextual links and readable projections. |
| Special agreements | Customer → Agreements; Billing → Agreements | Create/review/confirm/settle/cancel/retry/resolve present; complex terms need better sequencing. |
| Invoice operations | Invoice details/document/print, manual settlement, credit, refund and charge recovery | Present for combined permissions; independent payment-only access and some evidence were lost. |
| Reconciliation and provider events | Billing → Payment commands / Provider events | Present, including event reprocessing; command evidence and connected resolution paths are thin. |
| Plans and revisions | Catalog → Plans → grouped record views | CRUD, composition, availability, lifecycle, revision and subscriber operations present; cross-family comparison and schema inspection missing. |
| Add-ons and capacity | Catalog record contexts | CRUD, compatibility, dependencies, lifecycle, revisions/history present; filtering and selected historical-choice labels need improvement. |
| Prices and repricing | Product → Prices; Price → Reprice subscribers | Price lifecycle/replacement/scheduling and repricing present; important list filters/sorting and notice permission mapping need correction. |
| Plan content applications | Plan → Apply revision; Operations → Plan applications | Assessment, confirm, cancel, retry, impact and notice recovery present; source/target context and assessment sequencing need improvement. |
| Campaigns, segments and policies | Commercial grouped lists and records | Most definition/lifecycle/audience/history/revision capabilities present; owner reassignment is broken and narrow operation access regressed. |
| Offers and redemptions | Commercial → Offers; customer-context application | Most lifecycle/application/redemption evidence present; private-code removal, restricted operation access and staged definition review lost. |
| Customer communications | Customer → Message; Operations → Messages | Draft/edit/publish/cancel/recipients/delivery/retry present; authoring is a long generic form. |
| Personal inbox and delivery | Header notification link; Operations views | Read/ack/archive and delivery recovery present; contextual Open action and archive restoration missing. |
| Operators and roles | Settings record/list actions | Broad CRUD, bulk access, role assignment, impact/history present; eligibility and preset-only authority regressions remain. |
| Registry and platform controls | Settings → Feature controls; Operations → System status | Present, including controls/runtime/history and health/backlogs/log/registry projections. |
| Personal settings and access diagnosis | Profile and notification preference records | Notification settings present with more navigation; interface French/Arabic/RTL, appearance and own permission inventory missing. |
| Configurable automation recipes | No current equivalent | Neither old UI nor current backend provides a general persisted trigger/condition/action recipe engine. Scheduling existing jobs is present. |

The domain implementations are [catalog metadata](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/catalog.ts:1), [commercial metadata](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/commercial.ts:1), [billing metadata](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/billing.ts:1), [operations metadata](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/operations.ts:1), [settings metadata](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/settings.ts:1), and the specialized [customer](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/CustomerView.vue:1), [agreement](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/AgreementView.vue:1) and [change](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/ChangeView.vue:1) views.

## Priority 1 Restore reliable task completion

### Preserve current and retained subscription terms

An ordinary customer change now behaves like rebuilding the complete target subscription. V2 starts with empty add-ons and quantities, clears them on plan selection, and only displays options in the selected public plan. The notice says unselected existing holdings will be removed. A plan/price adjustment can consequently propose unintended removal; a held withdrawn product may not be available to select at all. Backend review remains a safeguard, but the UI should make the intended change explicit before that review.

The old workbench initializes current plan/price/holdings and handles `retainedAddOns`, `retainedQuotaPackages`, removability, quantity editability and price currency/cycle compatibility. Compare [old initialization](/Users/Hemmi/Projects/HiveApp/frontend/src/features/admin/subscriptions/admin-subscription-change-workbench.tsx:283), [v2 initial values](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/ChangeView.vue:43), [reset](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/ChangeView.vue:131), [option rendering and removal notice](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/ChangeView.vue:415), and [backend retention projection](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/client/plan/service/impl/SubscriptionServiceImpl.java:452).

Recommendation: prefill the current subscription for a single customer. Keep retained terms visible with their permitted operations. For a population, explicitly choose “preserve each customer's compatible holdings” or “replace complete configuration.” Show removals and quota reductions as named differences. Restoring single-customer retention needs no API change; a population-preservation mode may require an explicit backend command/projection rather than guessing one shared configuration.

### Restore independent single-customer authority

All v2 changes use bulk job preview/confirm, even from a customer record. The wizard rejects operators without both bulk permissions. The old single preview/apply path remains supported by the backend and uses narrower authority. Giving staff bulk access to compensate would change the intended access model.

Evidence: [v2 permission gate](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/ChangeView.vue:269), [old preview/apply](/Users/Hemmi/Projects/HiveApp/frontend/src/features/admin/subscriptions/admin-subscription-change-workbench.tsx:428), [existing backend commands](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/client/plan/api/SubscriptionAdminController.java:227).

Recommendation: share the configuration/review components, but execute a single-customer task through single preview/apply and a population task through the job API. Skip audience selection when launched from a customer. No contract change needed.

### Preserve independently authorized operation surfaces

Campaign/Offer operators without full-definition read access could previously load narrow operations projections and perform their permitted lifecycle/application tasks. V2 only provides full detail or editable-definition alternatives; its restricted summary disables all actions. Similarly, invoice payments now render only inside a successfully loaded invoice, so a payment-only role loses the independently authorized payment surface.

Evidence: [old campaign projection](/Users/Hemmi/Projects/HiveApp/frontend/src/features/admin/commercial-campaigns/admin-commercial-campaign-detail-page.tsx:909), [old offer projection](/Users/Hemmi/Projects/HiveApp/frontend/src/features/admin/commercial-offers/admin-offer-detail-page.tsx:910), [v2 campaign loading](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/commercial.ts:249), [v2 offer loading](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/commercial.ts:1083), [action suppression](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/ResourceView.vue:231), [invoice loading](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/InvoiceView.vue:21). Existing narrow APIs: [campaign operations](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/client/plan/api/CommercialCampaignAdminController.java:70), [offer operations](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/client/plan/api/CommercialOfferAdminController.java:82), [invoice payments](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/client/plan/api/BillingAdminController.java:125).

Recommendation: load authorized record projections independently and compose them in the same workspace. Do not make definition read or invoice read an accidental prerequisite for a narrower backend capability. No schema change needed.

### Repair commercial owner reassignment

The shared owner chooser maps choices using `r.id`; the backend returns `adminUserId`. This produces undefined choice values for campaign, segment and policy ownership. Segment/policy forms also reuse the campaign chooser permission, coupling unrelated capabilities.

Evidence: [default mapper](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/commercial.ts:60), [shared owner field](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/commercial.ts:78), [actual DTO](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/client/plan/dto/CommercialCampaignViews.java:127). The offer-specific mapping already uses the correct field at [commercial.ts:1190](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/commercial.ts:1190).

Recommendation: fix the identifier mapping immediately. Use scoped owner-choice permissions/projections for each domain; dedicated segment/policy choosers are a justified additive API improvement if their current contracts lack them.

## Priority 2 Restore missing controls and investigation paths

| Finding | Evidence | Recommended correction |
| --- | --- | --- |
| Notifications have no contextual Open action | [Old subject link](/Users/Hemmi/Projects/HiveApp/frontend/src/features/communications/notification-inbox.tsx:267); [new inbox](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/operations.ts:441) | Render authorized `actionPath`/source destinations as “Open invoice/change/customer.” Apply route mapping and current action state. |
| Archived notifications cannot be restored | [New fixed true archive argument](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/operations.ts:491); [backend archive flag](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/communication/OperatorNotificationController.java:73) | Offer Restore on archived items; send `archived=false`. |
| Job cancellation uses incorrect states | [New active predicate](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/OperationView.vue:24); [button](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/OperationView.vue:98); [backend rule](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/client/plan/domain/entity/SubscriptionChangeJob.java:315) | Allow PREVIEWED/QUEUED/SCHEDULED; do not offer cancel while RUNNING. |
| Repricing notice retry uses execution-retry permission | [UI gate](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/operations.ts:229); [backend email authority](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/admin/service/impl/AdminSubscriptionServiceImpl.java:116) | Gate notice sending/retry using `repricingEmail`; keep execution retry separate. |
| Cross-family plan comparison missing | [Old comparison](/Users/Hemmi/Projects/HiveApp/frontend/src/features/admin/plans/plan-comparison-page.tsx:313); [v2 revision-only action](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/catalog.ts:208); [backend comparison](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/client/plan/api/PlanAdminController.java:96) | Catalog selection → comparison workspace, with a feature/quota/price difference table. |
| Plan entitlement/schema inspection missing | [Old schema view](/Users/Hemmi/Projects/HiveApp/frontend/src/features/admin/plans/admin-plan-schema.tsx:34); [new generic record sections](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/ResourceView.vue:437) | Preserve this task inside Features: explain feature → resource/quota → extension → permission relationships. |
| Existing offer private code cannot be removed | [V2 KEEP/REPLACE only](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/commercial.ts:1126); [backend REMOVE contract](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/client/plan/dto/CommercialOfferRequests.java:38) | Offer Keep / Replace / Remove when lineage terms are editable. |
| Preset-only role creators cannot create | [New custom-create gate](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/settings.ts:199); [old independent authorities](/Users/Hemmi/Projects/HiveApp/frontend/src/features/admin/roles/admin-roles-page.tsx:180); [separate backend command](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/admin/api/AdminRoleController.java:61) | Choose permission and write handler by creation mode. |
| Role actions ignore availableActions/grant ceiling/state | [New actions](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/settings.ts:257); [backend action projection](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/admin/service/impl/AdminRoleServiceImpl.java:742) | Use authoritative action availability and legal transition targets. |
| Operator role picker includes ineligible roles | [New picker](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/settings.ts:8); [old assignability filter](/Users/Hemmi/Projects/HiveApp/frontend/src/features/admin/operators/admin-operator-role-picker.tsx:70); [backend active-role requirement](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/admin/service/impl/AdminUserServiceImpl.java:327) | Select active, assignable roles; display held inactive roles separately. |
| Operator forms offer protected self/privilege actions | [Superadmin checkbox](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/settings.ts:50); [record actions](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/settings.ts:104); [backend rules](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/admin/service/impl/AdminUserServiceImpl.java:129) | Gate privileged creation and self actions; use credential-aware Activate/Deactivate/Resend access labels. Backend enforcement is intact; these are manufactured failures. |
| Role confirmation uses stale record version | [Permission update](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/settings.ts:271); [status update](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/settings.ts:288); [old reviewed version](/Users/Hemmi/Projects/HiveApp/frontend/src/features/admin/roles/admin-role-detail-page.tsx:283) | Submit the reviewed preview version; invalidate review when inputs change. |
| Change payment/provenance details no longer reachable | [Old operation details](/Users/Hemmi/Projects/HiveApp/frontend/src/features/admin/subscriptions/admin-subscriptions-page.tsx:179); [new row](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/CustomerView.vue:509) | Expandable change detail: requester/canceller, accepted terms, checkout/settlement refs, amount, gateway failure and recovery. |
| Payment failure/reference details omitted | [Old payment evidence](/Users/Hemmi/Projects/HiveApp/frontend/src/features/admin/billing/admin-invoice-detail-page.tsx:657); [new payment row](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/InvoiceView.vue:208) | Payment evidence drawer/expander with authorized references, failure and recovery context. |
| Own effective-permission diagnosis omitted | [Old own-access inventory](/Users/Hemmi/Projects/HiveApp/frontend/src/features/admin/me/admin-me-page.tsx:64); [new Profile](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/ProfileView.vue:49) | Add an expandable “My access” inventory using existing session permissions. |
| Interface language/RTL and appearance missing | [Old preferences](/Users/Hemmi/Projects/HiveApp/frontend/src/features/communications/personal-settings-page.tsx:22); [old i18n](/Users/Hemmi/Projects/HiveApp/frontend/src/app/i18n.ts:7); [new language only for notifications](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/ProfileView.vue:71) | Restore French/Arabic interface translation, RTL and appearance preference independently of notification language. |

These corrections predominantly use existing APIs. Independent narrow choosers and consistent operator action projections are exceptions where additive backend work can improve the design.

### Restore useful list controls without restoring page sprawl

V2 retains status filters for roles, invoices, catalog/commercial lifecycle and jobs through [central filter definitions](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/index.ts:17). It also intentionally disables unsupported generic search on many operational lists at [index.ts:49](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/index.ts:49). Those are not missing-filter or ineffective-search bugs.

The real losses are richer filters and sorting. Generic requests send page/search/status and declared filters, but no user-controlled sort/direction: [ResourceList](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/components/ResourceList.vue:55). Missing controls include price product/owner/currency/cycle; campaign audience/source; segment kind; policy target/source/effective date; offer discovery/acceptance; product sales visibility; operator active state; customer account/plan constraints; agreement text search; and job-result status/sort.

Examples of old paths and current backend support: [price filters](/Users/Hemmi/Projects/HiveApp/frontend/src/features/admin/price-books/admin-product-prices-page.tsx:223), [campaign filters](/Users/Hemmi/Projects/HiveApp/frontend/src/features/admin/commercial-campaigns/admin-commercial-campaigns-page.tsx:199), [policy filters](/Users/Hemmi/Projects/HiveApp/frontend/src/features/admin/commercial-policies/admin-commercial-policies-page.tsx:229), [backend price query](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/client/plan/api/ProductPriceAdminController.java:85), [backend customer query](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/client/plan/api/SubscriptionAdminController.java:74), [backend agreement query](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/client/plan/api/SubscriptionAdminController.java:354), [backend result query](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/client/plan/api/SubscriptionChangeJobAdminController.java:81).

Recommendation: one compact filter row with key domain filters, a secondary filter disclosure, sortable relevant columns and URL state. Add reusable saved views later; they do not require extra sidebar destinations.

## Better sequences and page layouts

### Customer changes

Recommended single-customer sequence: Customer → current configuration with edits → timing and payment impact → before/after review → apply → customer Changes with the exact result selected. Keep customer identity and current status visible throughout. Audience selection is unnecessary here.

Recommended population sequence: filtered/selected customers → compatibility and preservation policy → target terms → timing → full ready/blocked review → confirm → results with direct resolution actions. V2 currently uses the first customer's catalog at [ChangeView:82](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/ChangeView.vue:82); preview validates all accounts later. This is order-dependent discovery and can hide valid options or discover incompatibility too late. A population-aware selection projection is worthwhile backend work.

Full paginated results and protected identities are already readable before confirmation: [service results](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/client/plan/service/impl/SubscriptionChangeJobServiceImpl.java:172). Both old job creation and new wizard show only a sample, so that limitation is inherited. V2 additionally labels sample results with short UUIDs at [ChangeView:560](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/ChangeView.vue:560). Use authorized names, ready/blocked filters and all conflict details before approval; preserve the narrow identity permission boundary.

Preview, schedule, running, awaiting payment, pending renewal, applied, cancelled and failed need distinct next actions. [OperationView:133](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/OperationView.vue:133) calls every inactive status “Completed,” including previews/cancelled records. Its progress calculation includes pending renewal/payment as processed at [line 53](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/OperationView.vue:53), so 100% does not mean all customers are applied. Show processing completion separately from business completion and make settlement/renewal follow-up visible.

The stored draft contains configuration, not a resumable signed review. The job detail contract also omits a token at [SubscriptionChangeJobModels:99](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/client/plan/dto/SubscriptionChangeJobModels.java:99). Persisted PREVIEWED jobs should have a clear cancel/re-review path; avoid leaving records that look completed and cannot be acted on. Initially, recreate the assessment from its stored selection through the existing preview flow and disclose that replacement. Resuming the same durable job needs an explicit backend re-review/token refresh command. Do not blindly persist/reuse expired or actor-bound tokens.

### Catalog and plan revision application

Keep family-based browsing and local Features/Prices/Subscribers/Versions/History. Restore comparison as a task in that context, using added/removed/changed features, quota differences and retained pricing rather than a recursive record dump. “Show unchanged” can be secondary.

A record's Apply revision link should make the viewed revision the intended target, then ask which earlier version/family population receives it. It currently preloads the viewed record into `sourcePlanId` and leaves target blank: [record entry](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/ResourceView.vue:424), [form defaults](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/ExecutionCreateView.vue:24). Constrain source/target choices to the same family and legal active target before the operator submits.

The current “Review” button creates an ASSESSING record then navigates to a generic detail page. This does not execute prematurely: [backend create](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/client/plan/service/PlanVersionRolloutOperations.java:47) freezes an assessment, and [confirm](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/client/plan/service/PlanVersionRolloutOperations.java:161) is separate. Keep assessment progress, differences, blocked customers and confirmation within one connected workbench, explaining the durable assessment stage briefly.

### Commercial authoring

The old offer editor had scoped steps and visible definition review; v2 renders all fields in a generic editor, then performs preview and write inside Save without showing a separate successful assessment. See [old stages](/Users/Hemmi/Projects/HiveApp/frontend/src/features/admin/commercial-offers/admin-offer-editor.tsx:547), [new editor](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/ResourceView.vue:381), [preview immediately followed by write](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/commercial.ts:1134).

Recommended sequence on one page: purpose/campaign/audience → product and price selection → commercial effects → validity/redemption limits → review → save draft or publish through the appropriate existing command. Keep a compact terms/price summary visible. Expand optional effects only when selected. Show issues beside their fields and present the business interpretation of the definition before saving.

Campaign → related offers → create offer with campaign preselected is a useful missing connection. Segment → campaigns/policies using this activation would also make impact easier to understand. Efficient contextual usage projections/filtering may need backend additions; avoid scanning every page of all commercial records in the browser.

### Agreements and deferred payment recovery

Recommended agreement sequence: term dates → subscription terms → agreed price/settlement → end behavior → reviewed outcome. Hide follow-on pricing unless the chosen end behavior requires it. Keep term length, amount, settlement status, end result and the next available action at the top. Put provenance and full evidence in expandable sections.

For a later payment implementation, the recommended recovery sequence is: exception → invoice/payment → captured/remaining/refundable amount and provider evidence → allowed recovery → confirmation → updated result and linked customer/change. Invoice/payment currency should be derived and displayed, rather than offered as an unrelated editable choice: [credit/refund fields](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/billing.ts:6). Retain preview, blocker, idempotency and audit mechanisms. This sequence is deferred from the current UI delivery.

### Operations as a work queue

[OperationsView:51](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/OperationsView.vue:51) has nine peer tabs mixing execution types, personal notifications, authoring, attention, delivery, audit and diagnostics. This relocates some navigation overload into a horizontal strip.

Recommended local structure: **Work queue** as the default, **Executions** with a kind filter, **Delivery**, and secondary **Audit/System** views. Keep the personal Inbox in the header and customer communication entry in customer context. The work queue should show subject, reason, age/due time, assigned responsibility where supported, and one useful next action. Existing attention data covers only part of this; a unified execution/exception read model would support a complete queue.

Plan application lists currently require choosing a plan before anything is shown: [OperationsView:179](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/OperationsView.vue:179). Add an explicit empty-state instruction initially; a global execution index removes that prerequisite later. Keep full-width lists and compact summary strips, avoiding a grid of unrelated dashboard cards.

### Operator onboarding and personal preferences

Recommended onboarding: identity → eligible access roles → access method → review → create/invite → delivery result. The current create flow gathers no roles and sends/shows initial access before assignment: [new fields](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/settings.ts:42), [backend create](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/admin/service/impl/AdminUserServiceImpl.java:155). Atomic creation with role IDs and deferred invitation is a justified additive backend command.

Permission editing should group permissions by domain with descriptions, selected changes and impact. The old editor did this at [admin-permission-editor.tsx:99](/Users/Hemmi/Projects/HiveApp/frontend/src/features/admin/roles/admin-permission-editor.tsx:99); the new generic picker flattens that task. Selection identity must remain readable after search/page changes. The shared picker can fall back to “Selected record” when a chosen record is not in its current/resolved options: [FieldInput:23](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/components/FieldInput.vue:23).

Move personal notification topics/channels/language into Profile → Notifications as one inline matrix. V2 currently requires a resource record and Edit/Save per topic at [settings.ts:472](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/settings.ts:472), while the old UI had inline toggles at [notification-settings.tsx:63](/Users/Hemmi/Projects/HiveApp/frontend/src/features/communications/notification-settings.tsx:63). Personal preferences should not behave like platform resource administration.

## Shared interaction issues

- **Generic evidence overwhelms decisions.** [Facts:34](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/components/Facts.vue:34) chooses the first six scalar keys for array tables and recursively reveals the remainder. This is acceptable supporting evidence, but cannot reliably prioritize money, removed entitlements, blockers or next actions. Use domain-specific summaries and differences for normal work; retain full Facts under Evidence.
- **Every mutation receives the same confirmation treatment.** [ActionDialog:205](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/components/ActionDialog.vue:205) requires an acknowledgement even for Mark read/Archive. Use direct low-impact actions with feedback/undo where the backend supports reversal; preserve explicit review for financial, access and irreversible changes.
- **Action drafts can disappear on modal dismissal.** [AppDialog:22](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/components/AppDialog.vue:22) allows Escape/backdrop close and [ActionDialog:155](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/components/ActionDialog.vue:155) closes without a dirty check. Long action forms need draft protection; ordinary resource editors already have guards.
- **Bulk selection lacks a named affected-record review.** [ResourceList:77](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/components/ResourceList.vue:77) retains selection across query/page changes and displays only a count at [line 168](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/components/ResourceList.vue:168). Preserve useful cross-page selection, but disclose hidden selections and show affected names before an access/bulk action.
- **Back paths discard working context.** Resource detail back links go to the resource base at [ResourceView:320](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/ResourceView.vue:320), dropping originating filters/page; router scrolling always resets at [router.ts:11](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/router.ts:11). Restore list state/scroll and return customer-started tasks to the exact customer tab/result.
- **Picker labels are not connected to choice controls.** [FieldInput:158](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/components/FieldInput.vue:158) uses `for=id`, but its choice summary at [line 209](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/components/FieldInput.vue:209) has no corresponding identified input. Give custom pickers an explicit accessible name including field and selected value/count.
- **Validation is mostly one form-wide string.** [validateFields:147](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/types.ts:147) returns the first issue, and [ResourceView:389](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/ResourceView.vue:389) renders it at the bottom. Attach errors to fields, focus the first invalid field, and retain a linked summary for complex forms.
- **Navigation destinations are not consistently permission-gated.** Header System status/Notifications are unconditional at [App:259](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/App.vue:259); search Enter always targets Customers at [line 282](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/App.vue:282), even if the visible customer action is hidden. Check destination capability consistently and choose a usable landing area for restricted operators.
- **Route changes do not move focus to the main view.** [App route watcher](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/App.vue:115) closes navigation/search but does not focus the new heading/main. Provide deliberate route focus and preserve native link behavior in command results.
- **Operational dates omit the year.** [format.ts:3](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/lib/format.ts:3) always returns day/month, optionally time. Agreements and future schedules can cross years. Show year and timezone where they affect interpretation; localize number/date formatting with interface language.
- **Residual copy is still promotional.** “A clear plan of action” remains in the change summary at [ChangeView:657](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/views/ChangeView.vue:657). Use “Change summary.” Replace long explanations with short labels, relevant warnings, and optional help.

The shared app already has useful foundations: native dialog behavior, visible focus styling, a skip link, reduced-motion support, URL-based tabs/filters, request-generation guards, explicit loading/error states and protected result identities. Preserve them while improving task-specific components. The accessibility review used the current [Web Interface Guidelines](https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md).

## Backend and API recommendations

### Existing contracts should be used first

Retention handling, single-customer changes, narrow campaign/offer operations, independent payments, comparison, code removal, notification restore/linking, richer list queries, preset creation, role action projections, preconfirmation results and notice permissions are already supported. Correct their UI orchestration before expanding the backend.

The metadata layer uses broad `Record<string, any>` values at [types.ts:2](/Users/Hemmi/Projects/HiveApp/frontend-v2/src/resources/types.ts:2). That made mistakes such as `id` versus `adminUserId` and mismatched action permissions easy to introduce despite typed API clients. Keep shared layout components, but use typed domain commands, typed choice adapters, separate preview/execute permissions and explicit action requirements. This is a frontend architecture refactor with substantial value.

### Additive changes worth considering

| Proposed capability | Why it improves UX | Backend work |
| --- | --- | --- |
| Consistent action availability and blockers | Prevents dead-end menus and duplicated state/permission rules | Extend existing lifecycle/agreement/role projection pattern to jobs, billing, repricing, messages and operators. |
| Population selection and impact projection | Removes first-customer ordering and makes preservation/removal visible before review | Audience-aware eligible options, compatibility reasons and effective entitlement/money differences. Preserve per-account execution checks. |
| Unified execution and exception index | Supports one coherent work queue and all-plan application discovery | Permission-aware read model across changes, repricing, content applications, agreements and delivery, with typed subjects and next actions. |
| Re-review of saved subscription jobs | Lets an operator resume a durable preview safely | Refresh/reassess the saved population under current catalog, actor and subscription versions, then issue a new signed review. |
| Typed navigation subjects | Keeps notifications and attention connected as route layouts evolve | Return resource type/ID/context/action state rather than relying only on legacy frontend route strings. Existing mapper can bridge during migration. |
| Atomic operator onboarding | Completes access setup before first invitation | Create with eligible role IDs and defer credential delivery until the reviewed setup is complete. |
| Admin customer onboarding | Lets a manager create a real account without a seeder or client impersonation | Explicit admin command/permission/audit, owner identity and invitation, account provisioning and optional initial terms. |
| Customer workspace support projections | Makes a customer record explain actual companies/members/usage and service impact | Narrow admin read/support APIs over tenant data; respect existing tenant boundaries. |
| Commercial usage projections | Connects campaign/segment/policy/offer tasks | Related records, published activation usage and impact without global browser scans. |
| Cross-resource search | Directly opens customers, invoices, prices, offers and runs | Permission-aware search endpoint with typed destinations and relevant identifiers. |
| Integration readiness | Makes email/provider recovery choices operationally accurate | Narrow delivery/provider readiness and supported action projections; avoid exposing secrets. |
| Persisted automation recipes | Enables “when X, if Y, do Z” with controllable execution | Trigger/condition/audience/action/delay policy, activation, idempotent execution, run history and recovery. This is a new product capability. |

Admin customer creation deserves a clear distinction: existing [client registration](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/identity/api/AuthController.java:32) provisions an owner/account/free subscription through [WorkspaceProvisioningServiceImpl:53](/Users/Hemmi/Projects/HiveApp/backend/src/main/java/com/hiveapp/platform/client/account/service/impl/WorkspaceProvisioningServiceImpl.java:53). It is not a platform-admin onboarding API. The local seeder uses internal capabilities; its existence does not establish a manager-facing create flow. The old admin account page was also a placeholder, so this is a new capability rather than a parity regression.

There is no need to rewrite the backend wholesale. Preserve authoritative previews, optimistic versions, granular permissions, audited reasons, idempotency and durable jobs. Add read models and narrow commands where they remove real UX constraints. A general automation engine would need new persistence and API contracts; the other additions can largely be backward-compatible.

## Market references and design application

These are design references, not claims that Hive implements the referenced products' capabilities.

- **Chargebee:** subscription records connect status, product terms and contextual actions; subscription lists support filtering and bulk work; creation selects the customer and product terms with a charge breakdown. Apply that organization to Hive's customer changes and financial review, using its own backend semantics. [Official subscription UI/workflows and screenshots](https://www.chargebee.com/docs/billing/2.0/subscriptions/subscriptions).
- **Stripe:** subscription modification separates billing effects and pending payment from configuration changes, and supports previewing billing impact. Apply a visible impact review and explicit awaiting-payment follow-through. [Modify subscriptions](https://docs.stripe.com/billing/subscriptions/change).
- **Stripe search:** direct search spans customers, invoices, products and financial records with filters. Apply typed, permission-aware direct record results rather than a page launcher presented as full search. [Dashboard search](https://docs.stripe.com/dashboard/search).
- **Stripe automations:** workflows have triggers, filters, actions and delays. Apply this model only if Hive adds a genuine persisted automation capability, with review and run history. [How automations work](https://docs.stripe.com/billing/automations).
- **Linear:** structured headers, filters, display modes and record panels establish hierarchy with restrained application chrome. Use stable full-width list/detail/workbench layouts. Its article explicitly distinguishes visual redesign from deeper navigation changes, which is relevant to evaluating Hive's reduced sidebar. [Official redesign and interface imagery](https://linear.app/now/how-we-redesigned-the-linear-ui).

The reference image search did not return usable results in this pass; the official linked pages provide interface imagery. Local screenshots were inspected for the actual current layout, not used as proof of action behavior.

## Suggested implementation order

1. Restore current/retained subscription terms and single-customer authority; repair narrow operation access and commercial owner mapping.
2. Correct missing actions and state/permission rules: notification Open/Restore, job cancellation, repricing email, role/preset/operator eligibility and reviewed versions.
3. Restore comparison, inspection, change provenance and the useful list filters/sorting within existing workspaces. Defer payment evidence/recovery work until payment is implemented.
4. Replace generic forms/evidence with specialized subscription, offer, agreement and access workbenches. Preserve compact global navigation.
5. Introduce the operational work queue and improve connected return paths, personal preferences, localization and accessibility.
6. Add backend read models/onboarding commands as justified above; implement automation recipes only after their business rules and lifecycle are defined.

Completion should be judged by task outcomes and permitted roles: a one-customer operator must not require bulk authority; a price change must visibly preserve/remove holdings as intended; a blocked result must lead directly to its available resolution; restricted commercial roles must retain their authorized tasks; and a manager must understand the reviewed commercial terms before committing them. Payment-specific completion criteria are deferred. Reducing route or sidebar counts alone is insufficient.

Documentation also needs reconciliation: [DESIGN.md:44](/Users/Hemmi/Projects/HiveApp/frontend-v2/DESIGN.md:44) says no backend source edits were needed, although the current development setup includes the customer seeder and Subscription UUID/check mapping changes. That does not imply an API schema change, but the manager-facing documentation should accurately distinguish UI API compatibility from backend development helpers.
