import { describe, expect, test } from "bun:test";
import {
  effectiveAtInstant,
  policyListStateFromSorting,
  readCommercialPolicyListState,
  writeCommercialPolicyListState,
} from "./commercial-policy-list-state";

describe("commercial policy list URL state", () => {
  test("bounds unsupported filters, pagination and sorting", () => {
    const state = readCommercialPolicyListState(
      new URLSearchParams(
        "q=%20Renewal%20&status=UNKNOWN&target=ACCOUNT_SET&effectiveAt=2026-02-30&page=-1&size=500&sort=dropTable",
      ),
    );
    expect(state).toMatchObject({
      search: "Renewal",
      status: "ALL",
      targetKind: "ACCOUNT_SET",
      effectiveAt: "",
      page: 0,
      size: 20,
      sort: "updatedAt",
    });
  });

  test("writes bounded state without destroying unrelated route state", () => {
    const current = new URLSearchParams("tab=history");
    const state = readCommercialPolicyListState(current);
    const next = writeCommercialPolicyListState(current, {
      ...state,
      status: "ACTIVE",
      includeArchived: true,
      page: 2,
    });
    expect(next.get("tab")).toBe("history");
    expect(next.get("status")).toBe("ACTIVE");
    expect(next.get("archived")).toBe("true");
    expect(next.get("page")).toBe("2");
  });

  test("server sorting resets the page", () => {
    const state = { ...readCommercialPolicyListState(new URLSearchParams()), page: 5 };
    expect(policyListStateFromSorting(state, [{ id: "priority", desc: false }])).toMatchObject({
      page: 0,
      sort: "priority",
      direction: "asc",
    });
  });

  test("effective-date filtering emits a stable instant", () => {
    expect(effectiveAtInstant("not-a-date")).toBeUndefined();
    expect(effectiveAtInstant("")).toBeUndefined();
    expect(effectiveAtInstant("2026-08-27")).toMatch(/^2026-08-27T/);
  });
});
