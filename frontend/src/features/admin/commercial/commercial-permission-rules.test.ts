import { describe, expect, test } from "bun:test";
import { adminPermissions } from "@/auth/permissions";
import {
  canOpenCommercialProduct,
  canReviseCommercialProduct,
  commercialProductPermission,
} from "./commercial-permission-rules";

describe("commercial chooser permissions", () => {
  test("uses the backend's bounded chooser permissions instead of full catalogue list permissions", () => {
    expect(commercialProductPermission("PLAN", "choose")).toBe(adminPermissions.plansChoose);
    expect(commercialProductPermission("ADD_ON", "choose")).toBe(adminPermissions.addOnsChoose);
    expect(commercialProductPermission("QUOTA_PACKAGE", "choose")).toBe(adminPermissions.quotaPackagesChoose);
  });

  test("distinguishes UUID hydration from saved code hydration", () => {
    expect(commercialProductPermission("PLAN", "resolve")).toBe(adminPermissions.plansResolveChoices);
    expect(commercialProductPermission("PLAN", "resolveCodes")).toBe(adminPermissions.plansResolveChoiceCodes);
    expect(commercialProductPermission("ADD_ON", "resolveCodes")).toBe(adminPermissions.addOnsResolveChoiceCodes);
    expect(commercialProductPermission("QUOTA_PACKAGE", "resolveCodes")).toBe(
      adminPermissions.quotaPackagesResolveChoiceCodes,
    );
  });
});

describe("commercial row actions", () => {
  test("list access alone never manufactures a working detail action", () => {
    const can = (permission: string) => permission === adminPermissions.plansList;
    expect(canOpenCommercialProduct(can, "PLAN")).toBe(false);
  });

  test("revision needs both its mutation permission and a readable destination", () => {
    const reviseOnly = (permission: string) => permission === adminPermissions.addOnsRevise;
    const complete = (permission: string) =>
      [adminPermissions.addOnsRevise, adminPermissions.addOnsReadDetail].includes(
        permission as (typeof adminPermissions)[keyof typeof adminPermissions],
      );
    expect(canReviseCommercialProduct(reviseOnly, "ADD_ON")).toBe(false);
    expect(canReviseCommercialProduct(complete, "ADD_ON")).toBe(true);
  });
});
