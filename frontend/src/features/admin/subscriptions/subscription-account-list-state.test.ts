import { describe, expect, test } from "bun:test";
import {
  readSubscriptionAccountListState,
  subscriptionAccountQuery,
  subscriptionAccountStateFromSorting,
  writeSubscriptionAccountListState,
} from "./subscription-account-list-state";

describe("subscription account list state", () => {
  test("rejects unsupported URL filters and server sort fields", () => {
    const state = readSubscriptionAccountListState(
      new URLSearchParams(
        "page=-4&size=500&accountStatus=deleted&subscription=UNKNOWN&sort=planName&direction=sideways",
      ),
    );
    expect(state).toEqual({
      search: "",
      accountStatus: "all",
      subscription: "all",
      page: 0,
      size: 20,
      sort: "name",
      direction: "asc",
    });
  });

  test("maps honest subscription and account filters to the backend contract", () => {
    expect(
      subscriptionAccountQuery({
        ...readSubscriptionAccountListState(new URLSearchParams()),
        accountStatus: "inactive",
        subscription: "PAST_DUE",
      }),
    ).toMatchObject({ accountActive: false, subscriptionStatus: "PAST_DUE", hasSubscription: undefined });
    expect(
      subscriptionAccountQuery({
        ...readSubscriptionAccountListState(new URLSearchParams()),
        subscription: "none",
      }),
    ).toMatchObject({ subscriptionStatus: undefined, hasSubscription: false });
  });

  test("keeps owner identity out of the ordinary Account URL and request contract", () => {
    const state = readSubscriptionAccountListState(new URLSearchParams("q=Acme&sort=ownerEmail&direction=desc"));
    const query = subscriptionAccountQuery(state);

    expect(state.sort).toBe("name");
    expect(query.query).toBe("Acme");
    expect(Object.hasOwn(query, "ownerEmail")).toBeFalse();
    expect(query.sort).toBe("name");
  });

  test("sorting resets the page and URL writes preserve unrelated deep-link state", () => {
    const current = readSubscriptionAccountListState(new URLSearchParams("page=4"));
    const sorted = subscriptionAccountStateFromSorting(current, [{ id: "createdAt", desc: true }]);
    expect(sorted).toMatchObject({ page: 0, sort: "createdAt", direction: "desc" });
    const params = writeSubscriptionAccountListState(new URLSearchParams("tab=history"), sorted);
    expect(params.get("tab")).toBe("history");
    expect(params.get("sort")).toBe("createdAt");
    expect(params.get("direction")).toBe("desc");
  });
});
