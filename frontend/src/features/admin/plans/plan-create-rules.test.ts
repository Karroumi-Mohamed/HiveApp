import { describe, expect, test } from "bun:test";
import type { Plan } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { createdPlanDestination, prefillFromSource, requiredCreationPermission } from "./plan-create-rules";
import { selectableCycles } from "./plan-presentation";

const plan = (overrides: Partial<Plan> = {}): Plan => ({
  id: "p",
  code: "FREE",
  name: "Free Plan",
  description: "Base",
  price: 0,
  currencyCode: "USD",
  billingCycle: "MONTHLY",
  status: "ACTIVE",
  lineageId: "l",
  revisionNumber: 1,
  sourcePlanId: null,
  creationReason: "CREATED",
  ...overrides,
});

describe("creation permission", () => {
  test("a blank start needs the create permission", () => {
    expect(requiredCreationPermission(false)).toBe(adminPermissions.plansCreate);
  });

  test("starting from an existing plan needs the duplicate permission the backend enforces", () => {
    // The regression: the page asked for plansCreate while the duplicate endpoint checks
    // plansDuplicate, so an operator could fill the whole form and be refused at submit.
    expect(requiredCreationPermission(true)).toBe(adminPermissions.plansDuplicate);
  });

  test("a deep-linked source remains a duplication even when its list record is not readable", () => {
    const sourceId = "source-only-visible-in-the-url";

    expect(requiredCreationPermission(Boolean(sourceId))).toBe(adminPermissions.plansDuplicate);
  });
});

describe("post-create destination", () => {
  test("does not send a create-only operator to a read-gated plan route", () => {
    expect(
      createdPlanDestination("plan-1", {
        readDetail: false,
        listFeatures: false,
        listPlans: false,
      }),
    ).toBe("/admin");
  });

  test("chooses the most specific readable route", () => {
    expect(
      createdPlanDestination("plan-1", {
        readDetail: true,
        listFeatures: true,
        listPlans: true,
      }),
    ).toBe("/admin/plans/plan-1/features");
    expect(
      createdPlanDestination("plan-1", {
        readDetail: true,
        listFeatures: false,
        listPlans: true,
      }),
    ).toBe("/admin/plans/plan-1");
    expect(
      createdPlanDestination("plan-1", {
        readDetail: false,
        listFeatures: false,
        listPlans: true,
      }),
    ).toBe("/admin/plans");
  });
});

describe("source prefill", () => {
  test("copies only operator-facing commercial fields", () => {
    expect(prefillFromSource(plan())).toEqual({
      name: "Free Plan",
      description: "Base",
      price: "0",
      currencyCode: "USD",
      billingCycle: "MONTHLY",
    });
  });

  test("a perpetual source falls back to a cycle that is still offered", () => {
    // PLAN-FLOW-008 defers FOREVER, so the wizard must never propose it — even copied.
    const prefilled = prefillFromSource(plan({ billingCycle: "FOREVER" }));
    expect(selectableCycles).toContain(prefilled.billingCycle);
  });
});
