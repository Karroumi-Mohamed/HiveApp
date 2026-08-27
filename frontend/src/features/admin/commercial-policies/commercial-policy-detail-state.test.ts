import { describe, expect, test } from "bun:test";
import {
  boundedPolicyPage,
  boundedPolicyResponsePage,
  validPolicyIdentity,
  withPolicySearchParam,
} from "./commercial-policy-detail-state";

describe("commercial policy detail URL state", () => {
  test("rejects malformed, fractional, negative and unbounded pages", () => {
    expect(boundedPolicyPage(null)).toBe(0);
    expect(boundedPolicyPage("-1")).toBe(0);
    expect(boundedPolicyPage("1.5")).toBe(0);
    expect(boundedPolicyPage("Infinity")).toBe(0);
    expect(boundedPolicyPage("1000001")).toBe(0);
    expect(boundedPolicyPage("42")).toBe(42);
  });

  test("clamps a stale last page to the final server page", () => {
    expect(boundedPolicyResponsePage(9, 3)).toBe(2);
    expect(boundedPolicyResponsePage(9, 0)).toBe(0);
    expect(boundedPolicyResponsePage(1, 3)).toBe(1);
  });

  test("accepts only a complete UUID before enabling identity-bearing queries", () => {
    expect(validPolicyIdentity("5ce8c602-2da0-4a24-a07c-df10d04a5c50")).toBe("5ce8c602-2da0-4a24-a07c-df10d04a5c50");
    expect(validPolicyIdentity("5ce8c602-2da0-4a24-a07c-df10d04a5c5")).toBe("");
    expect(validPolicyIdentity("../../owner")).toBe("");
  });

  test("updates one deep-link parameter without destroying the others", () => {
    const params = new URLSearchParams("activation=abc&accountsPage=2");
    expect(withPolicySearchParam(params, "page", 3).toString()).toContain("activation=abc");
    expect(withPolicySearchParam(params, "accountsPage", 0).has("activation")).toBeTrue();
    expect(withPolicySearchParam(params, "accountsPage", 0).has("accountsPage")).toBeFalse();
  });
});
