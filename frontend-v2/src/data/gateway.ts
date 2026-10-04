import { adminApi } from "@/api/admin-api";
import { adminPermissions as p } from "@/auth/permissions";
import type {
  AccountBillingProfileInput,
  CreatePlanInput,
  SubscriptionChangeJobPreviewInput,
  SubscriptionChangeInput,
  SubscriptionLifecycleAction,
} from "@/api/contracts";
import { can, invalidate } from "./session";
export async function read<T>(
  permission: string,
  operation: () => Promise<T>,
): Promise<T> {
  if (!can(permission))
    throw new Error("You do not have permission for this action.");
  return operation();
}
export async function write<T>(
  permission: string,
  operation: () => Promise<T>,
): Promise<T> {
  const result = await read(permission, operation);
  invalidate();
  return result;
}
export const gateway = {
  lifecycleActions: (id: string) =>
    read(p.subscriptionsReadLifecycleActions, () =>
      adminApi.subscriptionLifecycleActions(id),
    ),
  previewLifecycle: (
    id: string,
    action: SubscriptionLifecycleAction,
    graceEndsAt?: string,
  ) =>
    read(p.subscriptionsPreviewLifecycle, () =>
      adminApi.previewSubscriptionLifecycle(id, { action, graceEndsAt }),
    ),
  applyLifecycle: (
    id: string,
    action: SubscriptionLifecycleAction,
    input: {
      previewToken: string;
      reason: string;
      graceEndsAt?: string | null;
    },
  ) =>
    write(
      {
        CANCEL_AT_PERIOD_END: p.subscriptionsCancelAtPeriodEnd,
        KEEP_RENEWING: p.subscriptionsKeepRenewing,
        CANCEL_IMMEDIATELY: p.subscriptionsCancelImmediately,
        SUSPEND: p.subscriptionsSuspend,
        RESTORE: p.subscriptionsRestore,
        EXTEND_GRACE: p.subscriptionsExtendGrace,
      }[action],
      () => adminApi.applySubscriptionLifecycle(id, action, input),
    ),
  chooseAccounts: (
    query: Parameters<typeof adminApi.chooseSubscriptionAccounts>[0] = {},
  ) =>
    read(p.subscriptionsChooseAccounts, () =>
      adminApi.chooseSubscriptionAccounts(query),
    ),
  resolveAccounts: (ids: string[]) =>
    read(p.subscriptionsResolveAccountChoices, () =>
      adminApi.resolveSubscriptionAccounts(ids),
    ),
  overview: () => read(p.plansOverview, adminApi.commercialOverview),
  analytics: (days = 30) =>
    read(p.analyticsReadSummary, () =>
      adminApi.commercialAnalyticsOverview({
        from: new Date(Date.now() - days * 86400000).toISOString(),
        until: new Date().toISOString(),
        timezone: "Africa/Casablanca",
        interval: "DAY",
      }),
    ),
  series: (days = 30) =>
    read(p.analyticsReadFinancialSeries, () =>
      adminApi.commercialFinancialSeries({
        from: new Date(Date.now() - days * 86400000).toISOString(),
        until: new Date().toISOString(),
        timezone: "Africa/Casablanca",
        interval: "DAY",
      }),
    ),
  accounts: (query: Parameters<typeof adminApi.accounts>[0] = {}) =>
    read(p.subscriptionsSearch, () => adminApi.accounts(query)),
  subscription: (id: string) =>
    read(p.subscriptionsRead, () => adminApi.subscription(id)),
  catalog: (id: string) =>
    read(p.subscriptionsChooseChangeOptions, () =>
      adminApi.subscriptionChangeCatalog(id),
    ),
  changes: (id: string, page = 0) =>
    read(p.subscriptionsReadChanges, () =>
      adminApi.subscriptionChanges(id, { page, size: 20 }),
    ),
  agreements: (id: string, page = 0) =>
    read(p.subscriptionsReadSpecialAgreements, () =>
      adminApi.specialAgreements(id, { page, size: 20 }),
    ),
  profile: (id: string) =>
    read(p.billingReadAccountProfile, () => adminApi.billingProfile(id)),
  saveProfile: (id: string, input: AccountBillingProfileInput) =>
    write(p.billingUpdateAccountProfile, () =>
      adminApi.updateBillingProfile(id, input),
    ),
  invoices: (query: Parameters<typeof adminApi.billingInvoices>[0] = {}) =>
    read(p.billingListInvoices, () => adminApi.billingInvoices(query)),
  invoice: (id: string) =>
    read(p.billingReadInvoice, () => adminApi.billingInvoice(id)),
  settle: (id: string, input: { reason: string; reference: string }) =>
    write(p.billingManualSettlement, () =>
      adminApi.settleBillingInvoiceManually(id, input),
    ),
  timeline: (id: string, page = 0) =>
    read(p.billingReadAccountTimeline, () =>
      adminApi.billingFinancialTimeline(id, { page, size: 20 }),
    ),
  plans: (query: Parameters<typeof adminApi.operationalPlans>[0] = {}) =>
    read(p.plansList, () => adminApi.operationalPlans(query)),
  plan: (id: string) => read(p.plansReadDetail, () => adminApi.plan(id)),
  planFeatures: (id: string) =>
    read(p.plansListFeatures, () => adminApi.planFeatures(id)),
  createPlan: (input: CreatePlanInput) =>
    write(p.plansCreate, () => adminApi.createPlan(input)),
  addOns: () =>
    read(p.addOnsList, () => adminApi.operationalAddOns({ size: 50 })),
  quotaPackages: () =>
    read(p.quotaPackagesList, () =>
      adminApi.operationalQuotaPackages({ size: 50 }),
    ),
  prices: (ownerId?: string) =>
    read(p.priceBooksList, () => adminApi.productPrices({ ownerId, size: 50 })),
  campaigns: () =>
    read(p.campaignsList, () => adminApi.commercialCampaigns({ size: 50 })),
  segments: () =>
    read(p.segmentsList, () => adminApi.commercialSegments({ size: 50 })),
  policies: () =>
    read(p.commercialPoliciesList, () =>
      adminApi.commercialPolicies({ size: 50 }),
    ),
  activities: (accountId?: string) =>
    read(p.activitiesRead, () =>
      adminApi.activities({ targetAccountId: accountId, size: 20 }),
    ),
  health: () => read(p.observabilityReadHealth, adminApi.observabilityHealth),
  backlogs: () =>
    read(p.observabilityReadBacklogs, adminApi.observabilityBacklogs),
  communications: () =>
    read(p.communicationsRead, () => adminApi.communications({ size: 30 })),
  reconciliation: () =>
    read(p.billingListReconciliation, () =>
      adminApi.billingReconciliation({ size: 30 }),
    ),
  providerEvents: () =>
    read(p.billingListProviderEvents, () =>
      adminApi.billingProviderEvents({ size: 30 }),
    ),
  users: () => read(p.usersRead, () => adminApi.users({ size: 50 })),
  roles: () => read(p.rolesRead, () => adminApi.roles({ size: 50 })),
  jobs: (query: Parameters<typeof adminApi.subscriptionChangeJobs>[0] = {}) =>
    read(p.subscriptionsListChangeJobs, () =>
      adminApi.subscriptionChangeJobs(query),
    ),
  job: (id: string) =>
    read(p.subscriptionsReadChangeJob, () =>
      adminApi.subscriptionChangeJob(id),
    ),
  jobResults: (id: string, pageIndex = 0) =>
    read(p.subscriptionsReadChangeJobResults, () =>
      adminApi.subscriptionChangeJobResults(id, {
        page: pageIndex,
        size: 20,
      }),
    ),
  jobIdentities: (id: string, ids: string[]) =>
    read(p.subscriptionsReadChangeJobResultIdentities, () =>
      adminApi.resolveSubscriptionChangeJobIdentities(id, ids),
    ),
  previewJob: (input: SubscriptionChangeJobPreviewInput) =>
    read(p.subscriptionsPreviewChangeJob, () =>
      adminApi.previewSubscriptionChangeJob(input),
    ),
  confirmJob: (id: string, token: string) =>
    write(p.subscriptionsConfirmChangeJob, () =>
      adminApi.confirmSubscriptionChangeJob(id, token),
    ),
  cancelJob: (id: string, reason: string) =>
    write(p.subscriptionsCancelChangeJob, () =>
      adminApi.cancelSubscriptionChangeJob(id, reason),
    ),
  retryJob: (id: string, reason: string) =>
    write(p.subscriptionsRetryChangeJob, () =>
      adminApi.retrySubscriptionChangeJob(id, reason),
    ),
  previewChange: (id: string, selection: SubscriptionChangeInput) =>
    read(p.subscriptionsPreviewChange, () =>
      adminApi.previewSubscriptionChange(id, selection),
    ),
};
