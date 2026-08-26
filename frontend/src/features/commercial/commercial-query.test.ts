import { describe, expect, test } from "bun:test";
import { QueryClient } from "@tanstack/react-query";
import {
  adminCommercialKeys,
  clientCommercialKeys,
  commercialQueryEnabled,
  invalidateAdminCommercial,
  invalidateAdminSubscriptionEntitlement,
  invalidateClientCommercial,
  invalidateCommercialCatalogs,
} from "./commercial-query";

describe("commercial query permissions", () => {
  test("requires both the endpoint permission and local prerequisites", () => {
    const can = (permission: string) => permission === "catalog";

    expect(commercialQueryEnabled(can, "catalog")).toBeTrue();
    expect(commercialQueryEnabled(can, "history")).toBeFalse();
    expect(commercialQueryEnabled(can, "catalog", false)).toBeFalse();
  });
});

describe("commercial query keys and invalidation", () => {
  test("separates client caches by company and B2B context", () => {
    const account = { companyId: null, isB2B: false } as const;
    const company = { companyId: "company-1", isB2B: false } as const;
    const delegated = { companyId: "company-1", isB2B: true } as const;

    expect(clientCommercialKeys.subscription(account)).not.toEqual(clientCommercialKeys.subscription(company));
    expect(clientCommercialKeys.subscription(company)).not.toEqual(clientCommercialKeys.subscription(delegated));
  });

  test("invalidates affected client data across every cached context", async () => {
    const queryClient = new QueryClient();
    const current = { companyId: "company-1", isB2B: false } as const;
    const other = { companyId: "company-2", isB2B: false } as const;
    const currentCatalog = clientCommercialKeys.catalog(current);
    const currentSubscription = clientCommercialKeys.subscription(current);
    const currentChanges = clientCommercialKeys.changes(current);
    const otherCatalog = clientCommercialKeys.catalog(other);
    const adminOverview = adminCommercialKeys.overview();
    const adminSubscription = adminCommercialKeys.subscriptions.detail("account-1");
    const adminPlan = adminCommercialKeys.plans.detail("plan-1");
    queryClient.setQueryData(currentCatalog, { plans: [] });
    queryClient.setQueryData(currentSubscription, { plan: { code: "FREE" } });
    queryClient.setQueryData(currentChanges, []);
    queryClient.setQueryData(otherCatalog, { plans: [] });
    queryClient.setQueryData(adminOverview, {});
    queryClient.setQueryData(adminSubscription, {});
    queryClient.setQueryData(adminPlan, {});

    await invalidateClientCommercial(queryClient);

    expect(queryClient.getQueryState(currentCatalog)?.isInvalidated).toBeTrue();
    expect(queryClient.getQueryState(currentSubscription)?.isInvalidated).toBeTrue();
    expect(queryClient.getQueryState(currentChanges)?.isInvalidated).toBeTrue();
    expect(queryClient.getQueryState(otherCatalog)?.isInvalidated).toBeTrue();
    expect(queryClient.getQueryState(adminOverview)?.isInvalidated).toBeTrue();
    expect(queryClient.getQueryState(adminSubscription)?.isInvalidated).toBeTrue();
    expect(queryClient.getQueryState(adminPlan)?.isInvalidated).toBeTrue();
  });

  test("admin mutations invalidate the overview and the affected commercial family", async () => {
    const queryClient = new QueryClient();
    const overview = adminCommercialKeys.overview();
    const planList = adminCommercialKeys.plans.list();
    const planDetail = adminCommercialKeys.plans.detail("plan-1");
    const addOnList = adminCommercialKeys.addOns.list();
    const clientCatalog = clientCommercialKeys.catalog({ companyId: null, isB2B: false });
    queryClient.setQueryData(overview, { totalPlans: 1 });
    queryClient.setQueryData(planList, []);
    queryClient.setQueryData(planDetail, { id: "plan-1" });
    queryClient.setQueryData(addOnList, []);
    queryClient.setQueryData(clientCatalog, { plans: [] });

    await invalidateAdminCommercial(queryClient, adminCommercialKeys.plans.all());

    expect(queryClient.getQueryState(overview)?.isInvalidated).toBeTrue();
    expect(queryClient.getQueryState(planList)?.isInvalidated).toBeTrue();
    expect(queryClient.getQueryState(planDetail)?.isInvalidated).toBeTrue();
    expect(queryClient.getQueryState(addOnList)?.isInvalidated).toBeFalse();
    expect(queryClient.getQueryState(clientCatalog)?.isInvalidated).toBeTrue();
  });

  test("subscription entitlement mutations also invalidate plan detail and subscriber reads", async () => {
    const queryClient = new QueryClient();
    const overview = adminCommercialKeys.overview();
    const accounts = adminCommercialKeys.subscriptions.accounts({ search: "acme", page: 0 });
    const subscription = adminCommercialKeys.subscriptions.detail("account-1");
    const changes = adminCommercialKeys.subscriptions.changes("account-1");
    const planDetail = adminCommercialKeys.plans.detail("plan-1");
    const subscribers = adminCommercialKeys.plans.subscribers("plan-1", { search: "", status: "all", page: 0 });
    for (const queryKey of [overview, accounts, subscription, changes, planDetail, subscribers]) {
      queryClient.setQueryData(queryKey, {});
    }

    await invalidateAdminSubscriptionEntitlement(queryClient);

    for (const queryKey of [overview, accounts, subscription, changes, planDetail, subscribers]) {
      expect(queryClient.getQueryState(queryKey)?.isInvalidated).toBeTrue();
    }
  });

  test("registry controls invalidate admin and contextual client catalogs", async () => {
    const queryClient = new QueryClient();
    const adminCatalog = adminCommercialKeys.registry.featureCatalog("PLAN_ASSIGNABLE");
    const clientCatalog = clientCommercialKeys.catalog({ companyId: "company-1", isB2B: true });
    const unrelatedPlan = adminCommercialKeys.plans.detail("plan-1");
    queryClient.setQueryData(adminCatalog, []);
    queryClient.setQueryData(clientCatalog, { plans: [] });
    queryClient.setQueryData(unrelatedPlan, {});

    await invalidateCommercialCatalogs(queryClient);

    expect(queryClient.getQueryState(adminCatalog)?.isInvalidated).toBeTrue();
    expect(queryClient.getQueryState(clientCatalog)?.isInvalidated).toBeTrue();
    expect(queryClient.getQueryState(unrelatedPlan)?.isInvalidated).toBeFalse();
  });
});
