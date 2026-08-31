import { describe, expect, test } from "bun:test";
import { addOnLifecycleActions, addOnPlanCompatibilityIssue, addOnStatusLabel } from "./add-on-lifecycle";

describe("Add-on lifecycle", () => {
  test("a draft is editable, deletable and publishable", () => {
    expect(addOnLifecycleActions("DRAFT")).toEqual(["EDIT", "DELETE", "PUBLISH"]);
  });

  test("published states use sales controls and revisions instead of returning to draft", () => {
    expect(addOnLifecycleActions("ACTIVE")).toEqual(["REVISE", "PAUSE", "ARCHIVE"]);
    expect(addOnLifecycleActions("INACTIVE")).toEqual(["REVISE", "RESUME", "ARCHIVE"]);
  });

  test("archive is terminal", () => {
    expect(addOnLifecycleActions("ARCHIVED")).toEqual([]);
    expect(addOnStatusLabel("ARCHIVED")).toBe("Archivé");
  });

  test("plan compatibility rejects unpublished plans and mismatched commercial terms", () => {
    const plan = { status: "ACTIVE" as const, currencyCode: "USD", billingCycle: "MONTHLY" as const };

    expect(addOnPlanCompatibilityIssue("USD", "MONTHLY", plan)).toBeNull();
    expect(addOnPlanCompatibilityIssue("MAD", "MONTHLY", plan)).toBe("CURRENCY_MISMATCH");
    expect(addOnPlanCompatibilityIssue("USD", "YEARLY", plan)).toBe("CYCLE_MISMATCH");
    expect(addOnPlanCompatibilityIssue("USD", "MONTHLY", { ...plan, status: "DRAFT" })).toBe("PLAN_NOT_ACTIVE");
  });
});
