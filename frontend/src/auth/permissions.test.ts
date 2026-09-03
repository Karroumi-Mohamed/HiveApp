import { describe, expect, test } from "bun:test";
import {
  adminActivitiesSurfacePermissions,
  adminBillingSurfacePermissions,
  adminCommercialCampaignDetailSurfacePermissions,
  adminCommercialCampaignEditPermissions,
  adminCommercialPolicyDetailSurfacePermissions,
  adminCommunicationsSurfacePermissions,
  adminInvoiceDetailSurfacePermissions,
  adminObservabilitySurfacePermissions,
  adminOfferDetailSurfacePermissions,
  adminOfferEditPermissions,
  adminOverviewSurfacePermissions,
  adminPermissions,
  adminPriceBookDetailSurfacePermissions,
  adminProfileCan,
  adminSubscriptionDetailSurfacePermissions,
  adminSubscriptionJobDetailSurfacePermissions,
  clientOfferSurfacePermissions,
  clientPermissions,
  clientProfileCan,
  clientSubscriptionSurfacePermissions,
} from "./permissions";

describe("session permission bypasses", () => {
  test("a regular admin has only explicitly granted permissions", () => {
    const profile = { isSuperAdmin: false, permissions: [adminPermissions.plansList] };

    expect(adminProfileCan(profile, adminPermissions.plansList)).toBeTrue();
    expect(adminProfileCan(profile, adminPermissions.plansReadDetail)).toBeFalse();
  });

  test("a super admin bypasses individual permission nodes", () => {
    expect(adminProfileCan({ isSuperAdmin: true, permissions: [] }, adminPermissions.registryRuntime)).toBeTrue();
  });

  test("a regular member has only explicitly granted permissions", () => {
    const profile = { isOwner: false, permissions: [clientPermissions.subscriptionCatalog] };

    expect(clientProfileCan(profile, clientPermissions.subscriptionCatalog)).toBeTrue();
    expect(clientProfileCan(profile, clientPermissions.subscriptionRead)).toBeFalse();
  });

  test("an account owner bypasses individual permission nodes", () => {
    expect(clientProfileCan({ isOwner: true, permissions: [] }, clientPermissions.subscriptionRead)).toBeTrue();
  });

  test("the subscription route includes every independently readable surface", () => {
    expect(clientSubscriptionSurfacePermissions).toEqual([
      clientPermissions.subscriptionRead,
      clientPermissions.subscriptionReadSpecialAgreements,
      clientPermissions.subscriptionCatalog,
      clientPermissions.subscriptionReadChanges,
      clientPermissions.subscriptionListInvoices,
      clientPermissions.subscriptionReadFinancialTimeline,
      clientPermissions.subscriptionReadBillingProfile,
    ]);
  });

  test("billing routes preserve independently readable operational surfaces", () => {
    expect(adminBillingSurfacePermissions).toEqual([
      adminPermissions.billingListInvoices,
      adminPermissions.billingListReconciliation,
      adminPermissions.billingListProviderEvents,
    ]);
    expect(adminInvoiceDetailSurfacePermissions).toEqual([
      adminPermissions.billingReadInvoice,
      adminPermissions.billingReadInvoiceDocument,
      adminPermissions.billingReadAccountIdentity,
      adminPermissions.billingReadPayments,
      adminPermissions.billingManualSettlement,
      adminPermissions.billingPreviewChargeRetry,
    ]);
  });

  test("operations routes require their base metadata permission", () => {
    expect(adminActivitiesSurfacePermissions).toEqual([adminPermissions.activitiesRead]);
    expect(adminCommunicationsSurfacePermissions).toEqual([adminPermissions.communicationsRead]);
  });

  test("observability preserves every independently readable surface", () => {
    expect(adminObservabilitySurfacePermissions).toEqual([
      adminPermissions.observabilityReadHealth,
      adminPermissions.observabilityReadBacklogs,
      adminPermissions.observabilityReadLogAccess,
    ]);
  });

  test("the operator subscription detail route does not require broad subscription read access", () => {
    expect(adminSubscriptionDetailSurfacePermissions).toEqual([
      adminPermissions.subscriptionsRead,
      adminPermissions.subscriptionsChooseChangeOptions,
      adminPermissions.subscriptionsReadChanges,
      adminPermissions.subscriptionsReadLifecycleActions,
      adminPermissions.subscriptionsReadLifecycleHistory,
      adminPermissions.billingReadAccountTimeline,
      adminPermissions.billingReadAccountProfile,
      adminPermissions.subscriptionsReadSpecialAgreements,
      adminPermissions.subscriptionsReadSpecialAgreement,
      adminPermissions.subscriptionsPreviewSpecialAgreement,
    ]);
  });

  test("job results remain reachable without inheriting job-definition access", () => {
    expect(adminSubscriptionJobDetailSurfacePermissions).toEqual([
      adminPermissions.subscriptionsReadChangeJob,
      adminPermissions.subscriptionsReadChangeJobResults,
    ]);
  });

  test("the admin overview remains available to registry-sync-only operators", () => {
    expect(adminOverviewSurfacePermissions).toEqual([
      adminPermissions.accessOverview,
      adminPermissions.plansOverview,
      adminPermissions.registrySync,
    ]);
  });

  test("price history remains reachable without price-detail access", () => {
    expect(adminPriceBookDetailSurfacePermissions).toEqual([
      adminPermissions.priceBooksRead,
      adminPermissions.priceBooksReadHistory,
    ]);
  });

  test("commercial-policy evidence and identity surfaces do not inherit detail access", () => {
    expect(adminCommercialPolicyDetailSurfacePermissions).toEqual([
      adminPermissions.commercialPoliciesRead,
      adminPermissions.commercialPoliciesReadRevisions,
      adminPermissions.commercialPoliciesCompare,
      adminPermissions.commercialPoliciesReadHistory,
      adminPermissions.commercialPoliciesReadActivations,
      adminPermissions.commercialPoliciesReadActivationAccounts,
      adminPermissions.commercialPoliciesPreviewAudience,
      adminPermissions.commercialPoliciesReadOwner,
    ]);
  });

  test("Campaign evidence, history, comparison and owner surfaces remain independently reachable", () => {
    expect(adminCommercialCampaignDetailSurfacePermissions).toEqual([
      adminPermissions.campaignsRead,
      adminPermissions.campaignsReadOperations,
      adminPermissions.campaignsCompare,
      adminPermissions.campaignsRevisions,
      adminPermissions.campaignsHistory,
      adminPermissions.campaignsPreviewSchedule,
      adminPermissions.campaignsReadAudience,
      adminPermissions.campaignsReadAudienceIdentities,
      adminPermissions.campaignsOwner,
    ]);
  });

  test("Campaign editing uses the narrow editable-definition contract", () => {
    expect(adminCommercialCampaignEditPermissions).toEqual([
      adminPermissions.campaignsUpdate,
      adminPermissions.campaignsReadEditableDefinition,
    ]);
    expect(adminCommercialCampaignEditPermissions).not.toContain(adminPermissions.campaignsRead);
  });

  test("Offer operational evidence remains reachable without broad definition access", () => {
    expect(adminOfferDetailSurfacePermissions).toEqual([
      adminPermissions.offersRead,
      adminPermissions.offersReadOperations,
      adminPermissions.offersRevisions,
      adminPermissions.offersCompare,
      adminPermissions.offersHistory,
      adminPermissions.offersPreviewPublish,
      adminPermissions.offersReadOwner,
      adminPermissions.offersReadStats,
      adminPermissions.offersReadRedemptions,
      adminPermissions.offersPreviewForAccount,
    ]);
  });

  test("Offer editing uses definition-specific permissions", () => {
    expect(adminOfferEditPermissions).toEqual([
      adminPermissions.offersUpdate,
      adminPermissions.offersReadEditableDefinition,
      adminPermissions.offersPreviewUpdateDefinition,
    ]);
    expect(adminOfferEditPermissions).not.toContain(adminPermissions.offersRead);
  });

  test("the client Offer hub exposes catalogue, private code and history independently", () => {
    expect(clientOfferSurfacePermissions).toEqual([
      clientPermissions.subscriptionOfferCatalog,
      clientPermissions.subscriptionOfferCode,
      clientPermissions.subscriptionOfferHistory,
    ]);
  });
});
