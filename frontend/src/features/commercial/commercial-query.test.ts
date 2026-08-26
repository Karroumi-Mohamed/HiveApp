import { describe, expect, test } from "bun:test";
import { QueryClient } from "@tanstack/react-query";
import {
  adminCommercialKeys,
  clientCommercialKeys,
  commercialQueryEnabled,
  invalidateAdminCommercial,
  invalidateClientCommercial,
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
    const currentChanges = clientCommercialKeys.changes(current);
    const otherCatalog = clientCommercialKeys.catalog(other);
    queryClient.setQueryData(currentCatalog, { plans: [] });
    queryClient.setQueryData(currentChanges, []);
    queryClient.setQueryData(otherCatalog, { plans: [] });

    await invalidateClientCommercial(queryClient);

    expect(queryClient.getQueryState(currentCatalog)?.isInvalidated).toBeTrue();
    expect(queryClient.getQueryState(currentChanges)?.isInvalidated).toBeTrue();
    expect(queryClient.getQueryState(otherCatalog)?.isInvalidated).toBeTrue();
  });

  test("admin mutations invalidate the overview and the affected commercial family", async () => {
    const queryClient = new QueryClient();
    const overview = adminCommercialKeys.overview();
    const planList = adminCommercialKeys.plans.list();
    const planDetail = adminCommercialKeys.plans.detail("plan-1");
    const addOnList = adminCommercialKeys.addOns.list();
    queryClient.setQueryData(overview, { totalPlans: 1 });
    queryClient.setQueryData(planList, []);
    queryClient.setQueryData(planDetail, { id: "plan-1" });
    queryClient.setQueryData(addOnList, []);

    await invalidateAdminCommercial(queryClient, adminCommercialKeys.plans.all());

    expect(queryClient.getQueryState(overview)?.isInvalidated).toBeTrue();
    expect(queryClient.getQueryState(planList)?.isInvalidated).toBeTrue();
    expect(queryClient.getQueryState(planDetail)?.isInvalidated).toBeTrue();
    expect(queryClient.getQueryState(addOnList)?.isInvalidated).toBeFalse();
  });
});
