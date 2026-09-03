import type { QueryClient, QueryKey } from "@tanstack/react-query";
import type { FeatureCatalogAudience, PermissionCatalogAudience } from "@/api/contracts";

export type ClientCommercialContext = Readonly<{
  companyId: string | null;
  isB2B: boolean;
}>;

export type PermissionChecker = (permission: string) => boolean;

export function commercialQueryEnabled(can: PermissionChecker, permission: string, prerequisite = true) {
  return prerequisite && can(permission);
}

const adminRoot = ["admin", "commercial"] as const;
const plansRoot = [...adminRoot, "plans"] as const;
const addOnsRoot = [...adminRoot, "add-ons"] as const;
const quotaPackagesRoot = [...adminRoot, "quota-packages"] as const;
const priceBooksRoot = [...adminRoot, "price-books"] as const;
const policiesRoot = [...adminRoot, "commercial-policies"] as const;
const segmentsRoot = [...adminRoot, "segments"] as const;
const campaignsRoot = [...adminRoot, "campaigns"] as const;
const offersRoot = [...adminRoot, "offers"] as const;
const subscriptionsRoot = [...adminRoot, "subscriptions"] as const;
const registryRoot = [...adminRoot, "registry"] as const;

export const adminCommercialKeys = {
  all: () => adminRoot,
  overview: () => [...adminRoot, "overview"] as const,
  plans: {
    all: () => plansRoot,
    list: (filters?: Readonly<Record<string, unknown>>) => [...plansRoot, "list", filters ?? {}] as const,
    detail: (planId: string) => [...plansRoot, "detail", planId] as const,
    features: (planId: string) => [...plansRoot, "detail", planId, "features"] as const,
    subscribers: (planId: string, filters: Readonly<{ search: string; status: string; page: number }>) =>
      [...plansRoot, "detail", planId, "subscribers", filters] as const,
    subscriberOwnerLookup: (planId: string, filters: Readonly<{ ownerEmail: string; page: number }>) =>
      [...plansRoot, "detail", planId, "subscriber-owner-lookup", filters] as const,
    deletePreview: (planId: string) => [...plansRoot, "detail", planId, "delete-preview"] as const,
  },
  addOns: {
    all: () => addOnsRoot,
    list: (filters?: Readonly<Record<string, unknown>>) => [...addOnsRoot, "list", filters ?? {}] as const,
    detail: (addOnId: string) => [...addOnsRoot, "detail", addOnId] as const,
  },
  quotaPackages: {
    all: () => quotaPackagesRoot,
    list: (filters?: Readonly<Record<string, unknown>>) => [...quotaPackagesRoot, "list", filters ?? {}] as const,
    detail: (packageId: string) => [...quotaPackagesRoot, "detail", packageId] as const,
    operations: (packageId: string) => [...quotaPackagesRoot, "detail", packageId, "operations"] as const,
    revisions: (lineageId: string, page: number) =>
      [...quotaPackagesRoot, "lineage", lineageId, "revisions", page] as const,
    comparison: (packageId: string, candidateId: string) =>
      [...quotaPackagesRoot, "detail", packageId, "comparison", candidateId] as const,
    history: (packageId: string, page: number) => [...quotaPackagesRoot, "detail", packageId, "history", page] as const,
  },
  priceBooks: {
    all: () => priceBooksRoot,
    list: (filters: Readonly<Record<string, unknown>>) => [...priceBooksRoot, "list", filters] as const,
    detail: (priceId: string) => [...priceBooksRoot, "detail", priceId] as const,
    history: (priceId: string, page: number) => [...priceBooksRoot, "detail", priceId, "history", page] as const,
    activationPreviews: (priceId: string) => [...priceBooksRoot, "detail", priceId, "activation-preview"] as const,
    activationPreview: (priceId: string, version: number) =>
      [...priceBooksRoot, "detail", priceId, "activation-preview", version] as const,
    replacementPreview: (successorId: string, currentVersion: number, successorVersion: number) =>
      [...priceBooksRoot, "detail", successorId, "replacement-preview", currentVersion, successorVersion] as const,
    owner: (ownerType: string, ownerId: string) => [...priceBooksRoot, "owner", ownerType, ownerId] as const,
  },
  policies: {
    all: () => policiesRoot,
    list: (filters: Readonly<Record<string, unknown>>) => [...policiesRoot, "list", filters] as const,
    detail: (policyId: string) => [...policiesRoot, "detail", policyId] as const,
    revisions: (policyId: string, page: number) => [...policiesRoot, "detail", policyId, "revisions", page] as const,
    comparison: (policyId: string, comparedId: string) =>
      [...policiesRoot, "detail", policyId, "comparison", comparedId] as const,
    history: (policyId: string, page: number) => [...policiesRoot, "detail", policyId, "history", page] as const,
    activations: (policyId: string, page: number) =>
      [...policiesRoot, "detail", policyId, "activations", page] as const,
    activationAudience: (policyId: string, activationId: string, page: number) =>
      [...policiesRoot, "detail", policyId, "activations", activationId, "accounts", page] as const,
    audience: (policyId: string, page: number) => [...policiesRoot, "detail", policyId, "audience", page] as const,
    activationPreview: (policyId: string, version: number) =>
      [...policiesRoot, "detail", policyId, "activation-preview", version] as const,
    owner: (policyId: string) => [...policiesRoot, "detail", policyId, "owner"] as const,
    accountChoices: (filters: Readonly<Record<string, unknown>>) =>
      [...policiesRoot, "account-choices", filters] as const,
    segmentChoices: (filters: Readonly<Record<string, unknown>>) =>
      [...policiesRoot, "segment-choices", filters] as const,
  },
  segments: {
    all: () => segmentsRoot,
    list: (filters: Readonly<Record<string, unknown>>) => [...segmentsRoot, "list", filters] as const,
    detail: (segmentId: string) => [...segmentsRoot, "detail", segmentId] as const,
    count: (segmentId: string, version: number) => [...segmentsRoot, "detail", segmentId, "count", version] as const,
    preview: (segmentId: string, version: number) =>
      [...segmentsRoot, "detail", segmentId, "preview", version] as const,
    identitySample: (segmentId: string, version: number) =>
      [...segmentsRoot, "detail", segmentId, "identity-sample", version] as const,
    revisions: (segmentId: string, page: number) => [...segmentsRoot, "detail", segmentId, "revisions", page] as const,
    comparison: (segmentId: string, comparedId: string) =>
      [...segmentsRoot, "detail", segmentId, "comparison", comparedId] as const,
    history: (segmentId: string, page: number) => [...segmentsRoot, "detail", segmentId, "history", page] as const,
    activations: (segmentId: string, page: number) =>
      [...segmentsRoot, "detail", segmentId, "activations", page] as const,
    activationAudience: (segmentId: string, activationId: string, page: number, identities: boolean) =>
      [
        ...segmentsRoot,
        "detail",
        segmentId,
        "activations",
        activationId,
        identities ? "identities" : "accounts",
        page,
      ] as const,
    owner: (segmentId: string) => [...segmentsRoot, "detail", segmentId, "owner"] as const,
    accountChoices: (filters: Readonly<Record<string, unknown>>) =>
      [...segmentsRoot, "account-choices", filters] as const,
  },
  campaigns: {
    all: () => campaignsRoot,
    list: (filters: Readonly<Record<string, unknown>>) => [...campaignsRoot, "list", filters] as const,
    detail: (campaignId: string) => [...campaignsRoot, "detail", campaignId] as const,
    operations: (campaignId: string) => [...campaignsRoot, "detail", campaignId, "operations"] as const,
    editableDefinition: (campaignId: string) =>
      [...campaignsRoot, "detail", campaignId, "editable-definition"] as const,
    schedulePreview: (campaignId: string, version: number) =>
      [...campaignsRoot, "detail", campaignId, "schedule-preview", version] as const,
    audience: (campaignId: string, page: number, identities: boolean) =>
      [...campaignsRoot, "detail", campaignId, "audience", identities ? "identities" : "opaque", page] as const,
    revisions: (campaignId: string, page: number) =>
      [...campaignsRoot, "detail", campaignId, "revisions", page] as const,
    comparison: (campaignId: string, comparedId: string) =>
      [...campaignsRoot, "detail", campaignId, "comparison", comparedId] as const,
    history: (campaignId: string, page: number) => [...campaignsRoot, "detail", campaignId, "history", page] as const,
    owner: (campaignId: string) => [...campaignsRoot, "detail", campaignId, "owner"] as const,
    accountChoices: (filters: Readonly<Record<string, unknown>>) =>
      [...campaignsRoot, "account-choices", filters] as const,
    segmentChoices: (filters: Readonly<Record<string, unknown>>) =>
      [...campaignsRoot, "segment-choices", filters] as const,
    exactSegmentChoice: (segmentId: string, activationId: string) =>
      [...campaignsRoot, "segment-choices", "selected", segmentId, activationId] as const,
    ownerChoices: (filters: Readonly<Record<string, unknown>>) => [...campaignsRoot, "owner-choices", filters] as const,
  },
  offers: {
    all: () => offersRoot,
    list: (filters: Readonly<Record<string, unknown>>) => [...offersRoot, "list", filters] as const,
    detail: (offerId: string) => [...offersRoot, "detail", offerId] as const,
    operations: (offerId: string) => [...offersRoot, "detail", offerId, "operations"] as const,
    editableDefinition: (offerId: string) => [...offersRoot, "detail", offerId, "editable-definition"] as const,
    revisions: (offerId: string, page: number) => [...offersRoot, "detail", offerId, "revisions", page] as const,
    comparison: (offerId: string, comparedId: string) =>
      [...offersRoot, "detail", offerId, "comparison", comparedId] as const,
    history: (offerId: string, page: number) => [...offersRoot, "detail", offerId, "history", page] as const,
    owner: (offerId: string) => [...offersRoot, "detail", offerId, "owner"] as const,
    stats: (offerId: string) => [...offersRoot, "detail", offerId, "stats"] as const,
    redemptions: (offerId: string, filters: Readonly<Record<string, unknown>>) =>
      [...offersRoot, "detail", offerId, "redemptions", filters] as const,
    redemption: (offerId: string, redemptionId: string) =>
      [...offersRoot, "detail", offerId, "redemptions", redemptionId] as const,
    accountChoices: (filters: Readonly<Record<string, unknown>>) =>
      [...offersRoot, "account-choices", filters] as const,
    productChoices: (ownerType: string, filters: Readonly<Record<string, unknown>>) =>
      [...offersRoot, "product-choices", ownerType, filters] as const,
    campaignChoices: (filters: Readonly<Record<string, unknown>>) =>
      [...offersRoot, "campaign-choices", filters] as const,
    ownerChoices: (filters: Readonly<Record<string, unknown>>) => [...offersRoot, "owner-choices", filters] as const,
  },
  subscriptions: {
    all: () => subscriptionsRoot,
    agreementAnalytics: () => [...subscriptionsRoot, "agreements", "analytics"] as const,
    allAgreements: (filters: Readonly<Record<string, unknown>> = {}) =>
      [...subscriptionsRoot, "agreements", "list", filters] as const,
    accounts: (filters: Readonly<Record<string, unknown>>) => [...subscriptionsRoot, "accounts", filters] as const,
    accountOwnerLookup: (filters: Readonly<{ ownerEmail: string; page: number }>) =>
      [...subscriptionsRoot, "account-owner-lookup", filters] as const,
    detail: (accountId: string) => [...subscriptionsRoot, "detail", accountId] as const,
    changeCatalog: (accountId: string) => [...subscriptionsRoot, "detail", accountId, "change-catalog"] as const,
    changes: (accountId: string, filters: Readonly<Record<string, unknown>> = {}) =>
      [...subscriptionsRoot, "detail", accountId, "changes", filters] as const,
    lifecycleActions: (accountId: string) => [...subscriptionsRoot, "detail", accountId, "lifecycle-actions"] as const,
    lifecycleHistory: (accountId: string, page: number) =>
      [...subscriptionsRoot, "detail", accountId, "lifecycle-history", page] as const,
    agreements: (accountId: string, filters: Readonly<Record<string, unknown>> = {}) =>
      [...subscriptionsRoot, "detail", accountId, "agreements", filters] as const,
    agreement: (accountId: string, agreementId: string) =>
      [...subscriptionsRoot, "detail", accountId, "agreements", agreementId] as const,
    jobs: (filters: Readonly<Record<string, unknown>> = {}) => [...subscriptionsRoot, "jobs", filters] as const,
    job: (jobId: string) => [...subscriptionsRoot, "jobs", jobId] as const,
    jobResults: (jobId: string, filters: Readonly<Record<string, unknown>> = {}) =>
      [...subscriptionsRoot, "jobs", jobId, "results", filters] as const,
    jobAccountChoices: (filters: Readonly<Record<string, unknown>>) =>
      [...subscriptionsRoot, "jobs", "account-choices", filters] as const,
  },
  registry: {
    all: () => registryRoot,
    featureCatalog: (audience: FeatureCatalogAudience) => [...registryRoot, "feature-catalog", audience] as const,
    permissionCatalog: (audience: PermissionCatalogAudience) =>
      [...registryRoot, "permission-catalog", audience] as const,
  },
} as const;

const clientRoot = ["client", "commercial"] as const;

function normalizedContext(context: ClientCommercialContext) {
  return { companyId: context.companyId, isB2B: context.isB2B } as const;
}

export const clientCommercialKeys = {
  all: () => clientRoot,
  context: (context: ClientCommercialContext) => [...clientRoot, normalizedContext(context)] as const,
  subscription: (context: ClientCommercialContext) =>
    [...clientRoot, normalizedContext(context), "subscription"] as const,
  agreements: (context: ClientCommercialContext, filters: Readonly<Record<string, unknown>> = {}) =>
    [...clientRoot, normalizedContext(context), "subscription", "agreements", filters] as const,
  catalog: (context: ClientCommercialContext) =>
    [...clientRoot, normalizedContext(context), "subscription", "catalog"] as const,
  changes: (context: ClientCommercialContext, filters: Readonly<Record<string, unknown>> = {}) =>
    [...clientRoot, normalizedContext(context), "subscription", "changes", filters] as const,
  offers: {
    catalogue: (context: ClientCommercialContext, filters: Readonly<Record<string, unknown>>) =>
      [...clientRoot, normalizedContext(context), "offers", "catalogue", filters] as const,
    detail: (context: ClientCommercialContext, offerId: string) =>
      [...clientRoot, normalizedContext(context), "offers", "detail", offerId] as const,
    history: (context: ClientCommercialContext, filters: Readonly<Record<string, unknown>>) =>
      [...clientRoot, normalizedContext(context), "offers", "history", filters] as const,
    redemption: (context: ClientCommercialContext, redemptionId: string) =>
      [...clientRoot, normalizedContext(context), "offers", "redemption", redemptionId] as const,
  },
} as const;

export function adminCommercialInvalidationKeys(affected: readonly QueryKey[]) {
  return [adminCommercialKeys.overview(), ...affected] as const;
}

export async function invalidateAdminCommercial(queryClient: QueryClient, ...affected: QueryKey[]) {
  await Promise.all(
    [
      ...adminCommercialInvalidationKeys(affected),
      // Both authenticated portals share one QueryClient. Admin catalog/subscription writes can
      // change the contextual client catalog or current subscription in the same browser session.
      clientCommercialKeys.all(),
    ].map((queryKey) => queryClient.invalidateQueries({ queryKey })),
  );
}

/** Segment lifecycle and Policy-definition writes share one executable-target graph. */
export async function invalidateCommercialPolicyTargeting(queryClient: QueryClient, ...affected: QueryKey[]) {
  await invalidateAdminCommercial(
    queryClient,
    adminCommercialKeys.segments.all(),
    adminCommercialKeys.policies.all(),
    ...affected,
  );
}

/** Campaign definitions retain exact Segment activations until their reviewed audience is frozen. */
export async function invalidateCommercialCampaignTargeting(queryClient: QueryClient, ...affected: QueryKey[]) {
  await invalidateAdminCommercial(
    queryClient,
    adminCommercialKeys.segments.all(),
    adminCommercialKeys.campaigns.all(),
    ...affected,
  );
}

/**
 * Creating/replacing an account subscription (including checkout confirmation) changes both the
 * subscription inspector/history and the subscriber counts/lists held by plan read models.
 */
export async function invalidateAdminSubscriptionEntitlement(queryClient: QueryClient) {
  await invalidateAdminCommercial(
    queryClient,
    adminCommercialKeys.subscriptions.all(),
    adminCommercialKeys.plans.all(),
  );
}

export async function invalidateClientCommercial(queryClient: QueryClient) {
  // Client apply/cancel operations also change admin overview, account history and plan counts.
  await Promise.all(
    [
      clientCommercialKeys.all(),
      adminCommercialKeys.overview(),
      adminCommercialKeys.subscriptions.all(),
      adminCommercialKeys.plans.all(),
    ].map((queryKey) => queryClient.invalidateQueries({ queryKey })),
  );
}

/** Registry controls can change both admin picker catalogs and every contextual client catalog. */
export async function invalidateCommercialCatalogs(queryClient: QueryClient) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: adminCommercialKeys.registry.all() }),
    queryClient.invalidateQueries({ queryKey: clientCommercialKeys.all() }),
  ]);
}
