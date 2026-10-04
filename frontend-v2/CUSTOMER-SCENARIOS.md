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

| Customer             | Starting scenario                                                                                          | Path to explore                                                                                                                                                  |
| -------------------- | ---------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Atlas Logistics      | Flex with Organization Tools, B2B Collaboration, two member capacity units and one company capacity unit   | Customer → Subscription → Change subscription → held configuration and impact review; Catalog → Subscribers; Operations → Executions → scheduled Scale migration |
| Marina Studio        | Pro with extra member capacity; historical payment recorded for a Business change pending until renewal    | Customer → Changes → Details → timing and recorded evidence; Customer → Billing → corresponding invoice                                                          |
| Cedar Commerce       | Business active; Scale plus 100 members awaiting payment                                                   | Customer → Changes → Details → awaiting-confirmation state and checkout evidence; Customer → Billing → invoice. Payment actions are deferred.                    |
| Rif Services         | Pro suspended through a reviewed lifecycle action                                                          | Customer → Subscription → Restore access → review; Changes → Lifecycle history                                                                                   |
| Sahara Manufacturing | Business set to cancel at period end                                                                       | Customer → Subscription → Keep renewing → review; Changes → Lifecycle history                                                                                    |
| Medina Retail        | Pro with an invoice, manual payment, MAD 5 credit and MAD 3 manual refund                                  | Customer → Billing → invoice → payment, credit and refund evidence; financial history                                                                            |
| Atlas Foundation     | Active three-month complimentary Enterprise agreement, restoring prior terms when it ends                  | Customer → Agreements → terms and end instruction; Customer → Billing → zero-value invoice                                                                       |
| Mosaic Consulting    | Pro active; three-month Enterprise agreement at MAD 240 awaiting manual settlement                         | Customer → Agreements → priced terms, pending state and planned restoration. Collection and settlement controls are deferred.                                    |
| Northstar Partners   | Flex upgraded through the Partner Pro onboarding offer, with complimentary Pro terms and ten extra members | Customer → Subscription and Changes; Commercial → Campaigns → frozen audience → related offer → redemption and accepted terms                                    |
| Nouri Ventures       | Free account targeted by the mixed customer upgrade job                                                    | Operations → Executions → Subscription changes → result → Customer → Changes → recorded pending outcome                                                          |
| Bay Operations       | Free account targeted by the same upgrade job                                                              | Operations → Executions → Subscription changes → per-customer result → Customer → Changes and Billing                                                            |

The mixed job also targets Rif Services. Its suspended subscription produces a real assessment conflict; the two eligible accounts have recorded `AWAITING_PAYMENT` results. These pending results do not mean payment integration is available. A second reviewed job is scheduled two days after initial seeding for Atlas Logistics. Its execution time is preserved on subsequent starts.

The historical seeded payment, credit and refund records remain available as ledger evidence. They are not evidence of money moving through a bank. Seed references and reasons explicitly contain `LOCAL_SEED` or `LOCAL-SEED`. The current UI does not expose collection, settlement, refunds or payment recovery while payment handling is deferred. No production payment-provider callback is fabricated.

For a subscription change, select **Change subscription** from a customer. The configuration starts with held terms and keeps retained add-ons and capacity visible. Changing the plan preserves selections; removals must be explicit. Continue through Timing and Review to inspect current versus target terms and conflicts. A role with preview access can review without apply access. A paid immediate change can remain awaiting confirmation rather than changing access immediately.

For a population, select customers in the directory or choose **Change subscriptions** with bulk-preview access. The flow uses one complete replacement configuration for all selected accounts; confirm that replacement intent before reviewing. Ready and blocked results are pageable before confirmation when result-read access is granted, and customer names require separate identity access. Filters for account activity, subscription presence, subscription status and plan code persist in the directory URL. Changing filters clears the selection; paging within the same filtered view preserves it.

New agreements follow **Configuration → Term and pricing → Review**. Review shows the term, amount, entitlements, end instruction and expected resulting state. A positive-value agreement can be authored as awaiting manual settlement; it does not collect payment or expose settlement execution. Complimentary and zero-total agreements use the no-amount-due path.

## Scope

The backend supports the subscription, lifecycle, agreement, commercial and execution workflows described above. The seed helper uses identity/workspace provisioning, owner permissions and quota checks, subscription previews/applications, lifecycle previews, agreements, historical billing adjustments, campaign scheduling, offer publication/acceptance and durable job APIs' underlying services. The helper does not add or change an API contract or business database tables/columns. The subscription UUID mappings also declare their existing lifecycle check constraints separately from column types, allowing Hibernate schema validation and avoiding invalid H2 update statements.

Payment handling remains outside the current UI delivery scope, and the development collection setting remains disabled. Email transport requires deployment configuration and verification. Trial provisioning, past-due renewal collection, repricing, plan content rollouts, commercial policies and segments are not populated by this seed. The customer records can be used to review the supported administration flows for those resources separately.

## Recorded seed verification

Authenticated API reads confirmed **11 customers, 14 invoices, 2 agreements, 1 active campaign, 1 published offer with an applied redemption, and 2 jobs**. The immediate mixed job finished with two `AWAITING_PAYMENT` results and one real suspended-subscription conflict. The other job remained scheduled. The two agreements are respectively `ACTIVE` and `AWAITING_SETTLEMENT`; Medina Retail's settled invoice includes its MAD 5 credit and MAD 3 refund.

A second seed run preserved every customer, invoice, agreement, campaign, offer and job ID, all subscription prices/lifecycle states, change counts and scheduled execution times. The customer directory and Cedar Commerce's pending state were verified in the earlier live UI. That earlier version's settlement controls have since been removed from the current delivery scope. Backend compilation passed with test compilation/execution skipped. Existing test files were not read or run.

The final backend restart passed Hibernate schema validation against the same persistent database. Authenticated API reviews also confirmed that restoring Rif Services and scheduling Atlas Logistics' Scale renewal have no blockers; these reviews did not apply additional changes.

The customer change table wraps long reasons and opens contextual details with request/cancellation provenance and recorded evidence. Shared text-action buttons use the application's styling. Current production typechecking passes; final integration verification is recorded in the implementation report.
