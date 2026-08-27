import { describe, expect, test } from "bun:test";
import {
  adminSubscriptionOperationUrlKeys,
  clientSubscriptionOperationUrlKeys,
  readSubscriptionOperationListState,
  subscriptionOperationQuery,
  subscriptionOperationStateFromSorting,
  writeSubscriptionOperationListState,
} from "./subscription-operation-list-state";

describe("subscription operation URL state", () => {
  test("bounds unsupported server state and keeps admin history separate from the account list", () => {
    const params = new URLSearchParams(
      "page=7&operationPage=-2&operationSize=101&operationSort=sourcePlan&operationDirection=sideways",
    );
    expect(readSubscriptionOperationListState(params, adminSubscriptionOperationUrlKeys)).toEqual({
      page: 0,
      size: 20,
      sort: "createdAt",
      direction: "desc",
    });
    expect(params.get("page")).toBe("7");
  });

  test("round-trips client paging and sort while preserving the selected tab", () => {
    const params = writeSubscriptionOperationListState(
      new URLSearchParams("tab=changes"),
      { page: 2, size: 50, sort: "effectiveAt", direction: "asc" },
      clientSubscriptionOperationUrlKeys,
    );
    expect(params.toString()).toContain("tab=changes");
    expect(readSubscriptionOperationListState(params, clientSubscriptionOperationUrlKeys)).toEqual({
      page: 2,
      size: 50,
      sort: "effectiveAt",
      direction: "asc",
    });
  });

  test("sorting resets the page and produces only backend-supported parameters", () => {
    const state = subscriptionOperationStateFromSorting({ page: 4, size: 20, sort: "createdAt", direction: "desc" }, [
      { id: "status", desc: false },
    ]);
    expect(subscriptionOperationQuery(state)).toEqual({
      page: 0,
      size: 20,
      sort: "status",
      direction: "asc",
    });
  });
});
