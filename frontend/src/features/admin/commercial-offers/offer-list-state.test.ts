import { describe, expect, test } from "bun:test";
import { offerListStateFromSorting, readOfferListState, writeOfferListState } from "./offer-list-state";

describe("Offer list state", () => {
  test("rejects unsupported filters and sort fields", () => {
    const state = readOfferListState(new URLSearchParams("status=ACTIVE&discovery=SECRET&sort=owner&page=-1"));
    expect(state.status).toBe("ALL");
    expect(state.discovery).toBe("ALL");
    expect(state.sort).toBe("createdAt");
    expect(state.page).toBe(0);
  });

  test("round-trips supported operational filters", () => {
    const state = readOfferListState(
      new URLSearchParams(
        "q=renewal&status=PUBLISHED&discovery=CODE_ONLY&acceptance=OPERATOR_ONLY&archived=true&page=2&size=50&sort=name&direction=asc",
      ),
    );
    expect(readOfferListState(writeOfferListState(new URLSearchParams(), state))).toEqual(state);
  });

  test("sorting resets pagination", () => {
    const state = readOfferListState(new URLSearchParams("page=4"));
    expect(offerListStateFromSorting(state, [{ id: "name", desc: false }])).toMatchObject({
      page: 0,
      sort: "name",
      direction: "asc",
    });
  });
});
