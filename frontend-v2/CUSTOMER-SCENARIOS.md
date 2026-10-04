# Connected customer scenarios

These are persistent development records in the backend database. The UI uses its normal authenticated APIs. There is no frontend fixture store or alternate data mode.

## Seed or restart

With the existing backend stopped, run:

```sh
cd frontend-v2
./scripts/start-backend.sh --seed-customers
```

Java 21 and Maven are required. The seeder is disabled by default, available only under `dev` without `prod`, and refuses a non-H2 datasource. The launcher generates an owner password in the ignored, owner-only `.env.backend.local`. Subsequent ordinary starts preserve the records:

```sh
./scripts/start-backend.sh
```

Running the seeder again preserves existing customers, subscription terms, financial records, lifecycle actions, campaign/offer revisions and executions. Each customer's setup is one transaction. Commercial setup commits stages independently to respect the backend's offer reservation and acceptance transactions. An interrupted run can resume missing stages. It does not reset a customer after an administrator changes it.

## Try the flows

Use the existing platform administrator login and open **Customers**. Search by the names below. Each account has a billing profile, a company, an owner and staff members. Atlas Logistics has two companies. Owners use `<customer-key>@customers.hive.example`; staff have no email address, so their creation does not send invitations externally.

| Customer | Starting scenario | Path to explore |
| --- | --- | --- |
| Atlas Logistics | Flex with Organization Tools, B2B Collaboration, two member capacity units and one company capacity unit | Customer → Overview → configuration and quota usage; Catalog → subscribers; Operations → scheduled Scale migration |
| Marina Studio | Pro with extra member capacity; Business change paid and pending until renewal | Customer → Changes → current and future terms; Billing → corresponding invoice and manual settlement evidence |
| Cedar Commerce | Business active; Scale plus 100 members awaiting payment | Customer → Changes → pending checkout → manual settlement; follow the resulting subscription and invoice |
| Rif Services | Pro suspended through a reviewed lifecycle action | Customer → Overview → restore access; Changes → lifecycle history |
| Sahara Manufacturing | Business set to cancel at period end | Customer → Overview → keep renewing; Changes → cancellation history |
| Medina Retail | Pro with an invoice, manual payment, MAD 5 credit and MAD 3 manual refund | Customer → Billing → invoice → payment, credit and refund evidence; financial history |
| Atlas Foundation | Active three-month complimentary Enterprise agreement, restoring prior terms when it ends | Customer → Agreements → terms, zero-value invoice and end instruction |
| Mosaic Consulting | Pro active; three-month Enterprise agreement at MAD 240 awaiting manual settlement | Customer → Agreements → settlement → activation → eventual restoration |
| Northstar Partners | Flex upgraded through the Partner Pro onboarding offer, with complimentary Pro terms and ten extra members | Customer → Overview and Changes; Commercial → campaign → frozen audience → offer → redemption and accepted terms |
| Nouri Ventures | Free account targeted by the mixed customer upgrade job | Operations → Subscription changes → result → customer → pending checkout |
| Bay Operations | Free account targeted by the same upgrade job | Operations → Subscription changes → per-customer result and billing |

The mixed job also targets Rif Services. Its suspended subscription produces a real assessment conflict; the two eligible accounts proceed to payment confirmation through the actual worker. A second reviewed job is scheduled two days after initial seeding for Atlas Logistics. Its execution time is preserved on subsequent starts.

For pending checkouts, record a **local** settlement reference and a reason to try completion. These development manual payment/refund records are ledger entries, not evidence of money moving through a bank. Seed references and reasons explicitly contain `LOCAL_SEED` or `LOCAL-SEED`. No production payment-provider callback is fabricated.

## Scope

The backend already supports these flows. The added helper uses identity/workspace provisioning, owner permissions and quota checks, subscription previews/applications, lifecycle previews, agreements, billing adjustments, campaign scheduling, offer publication/acceptance and durable job APIs' underlying services. It does not add or change an API contract or business database tables/columns. The subscription UUID mappings also declare their existing lifecycle check constraints separately from column types, allowing Hibernate schema validation and avoiding invalid H2 update statements.

The existing development payment collection setting remains disabled. Production payment and email transport still require deployment configuration and verification. Trial provisioning, past-due renewal collection, repricing, plan content rollouts, commercial policies and segments are not populated by this seed; the customer records can be used to exercise the supported administration flows for those resources separately.

## Verified local run

Authenticated API reads confirmed **11 customers, 14 invoices, 2 agreements, 1 active campaign, 1 published offer with an applied redemption, and 2 jobs**. The immediate mixed job finished with two `AWAITING_PAYMENT` results and one real suspended-subscription conflict. The other job remained scheduled. The two agreements are respectively `ACTIVE` and `AWAITING_SETTLEMENT`; Medina Retail's settled invoice includes its MAD 5 credit and MAD 3 refund.

A second seed run preserved every customer, invoice, agreement, campaign, offer and job ID, all subscription prices/lifecycle states, change counts and scheduled execution times. The customer directory and Cedar Commerce's pending checkout controls were verified in the live UI. Backend compilation passed with test compilation/execution skipped. Existing test files were not read or run.

The final backend restart passed Hibernate schema validation against the same persistent database. Authenticated API reviews also confirmed that restoring Rif Services and scheduling Atlas Logistics' Scale renewal have no blockers; these reviews did not apply additional changes.

The populated customer change table wraps long reasons so settlement controls remain visible at the normal desktop width. Shared text-action buttons now use the application's styling. Vue/TypeScript checking and the frontend production build pass.
