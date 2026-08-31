import { describe, expect, test } from "bun:test";
import { listStateFromSorting, readCommercialListState, writeCommercialListState } from "./commercial-list-state";

describe("commercial list URL state", () => {
  test("rejects invalid pages and preserves unrelated deep-link parameters", () => {
    const current = new URLSearchParams("page=-2&size=999&tab=history&q=  Flex  ");
    const state = readCommercialListState(current);
    expect(state.page).toBe(0);
    expect(state.size).toBe(20);
    expect(state.search).toBe("Flex");
    const next = writeCommercialListState(current, { ...state, page: 2 });
    expect(next.get("page")).toBe("2");
    expect(next.get("tab")).toBe("history");
  });

  test("a server sort resets the page", () => {
    const state = readCommercialListState(new URLSearchParams("page=4"));
    expect(listStateFromSorting(state, [{ id: "name", desc: false }])).toMatchObject({
      page: 0,
      sort: "name",
      direction: "asc",
    });
  });
});
