import { describe, expect, test } from "bun:test";
import type { Plan } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { prefillFromSource, requiredCreationPermission } from "./plan-create-rules";
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
});

describe("source prefill", () => {
  test("copies the commercial fields and proposes a new code", () => {
    expect(prefillFromSource(plan())).toEqual({
      code: "FREE_COPY",
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
