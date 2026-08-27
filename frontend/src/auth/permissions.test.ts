import { describe, expect, test } from "bun:test";
import {
  adminCommercialPolicyDetailSurfacePermissions,
  adminOverviewSurfacePermissions,
  adminPermissions,
  adminPriceBookDetailSurfacePermissions,
  adminProfileCan,
  adminSubscriptionDetailSurfacePermissions,
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
      clientPermissions.subscriptionCatalog,
      clientPermissions.subscriptionReadChanges,
    ]);
  });

  test("the operator subscription detail route does not require broad subscription read access", () => {
    expect(adminSubscriptionDetailSurfacePermissions).toEqual([
      adminPermissions.subscriptionsRead,
      adminPermissions.subscriptionsChooseChangeOptions,
      adminPermissions.subscriptionsReadChanges,
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
});
