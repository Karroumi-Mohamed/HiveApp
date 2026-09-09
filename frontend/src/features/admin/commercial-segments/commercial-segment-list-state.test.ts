import { describe, expect, test } from "bun:test";
import {
  readCommercialSegmentListState,
  segmentListStateFromSorting,
  writeCommercialSegmentListState,
} from "./commercial-segment-list-state";

describe("commercial segment list URL state", () => {
  test("drops obsolete origin filters and origin sorting from saved links", () => {
    const params = new URLSearchParams("source=IMPORTED&sort=source&tab=audience");
    const state = readCommercialSegmentListState(params);
    expect(state).not.toHaveProperty("source");
    expect(state.sort).toBe("updatedAt");
    const next = writeCommercialSegmentListState(params, state);
    expect(next.has("source")).toBe(false);
    expect(next.get("tab")).toBe("audience");
  });

  test("bounds unknown filters, pagination, page size and sorting", () => {
    const state = readCommercialSegmentListState(
      new URLSearchParams(
        "q=%20Renewal%20&status=UNKNOWN&kind=SCRIPT&source=ROBOT&page=-1&size=500&sort=dropTable&direction=sideways",
      ),
    );
    expect(state).toMatchObject({
      search: "Renewal",
      status: "ALL",
      kind: "ALL",
      page: 0,
      size: 20,
      sort: "updatedAt",
      direction: "desc",
    });
  });

  test("bounds oversized search and unsafe page values", () => {
    const state = readCommercialSegmentListState(
      new URLSearchParams(`q=${"x".repeat(300)}&page=999999999999999999999`),
    );
    expect(state.search).toHaveLength(180);
    expect(state.page).toBe(0);
  });

  test("writes state without destroying sibling route parameters", () => {
    const current = new URLSearchParams("tab=audience");
    const next = writeCommercialSegmentListState(current, {
      ...readCommercialSegmentListState(current),
      status: "ACTIVE",
      kind: "TYPED_CRITERIA",
      includeArchived: true,
      page: 3,
    });
    expect(next.get("tab")).toBe("audience");
    expect(next.get("status")).toBe("ACTIVE");
    expect(next.get("kind")).toBe("TYPED_CRITERIA");
    expect(next.get("archived")).toBe("true");
    expect(next.get("page")).toBe("3");
  });

  test("only accepts server-supported table sorting and resets pagination", () => {
    const state = { ...readCommercialSegmentListState(new URLSearchParams()), page: 5 };
    expect(segmentListStateFromSorting(state, [{ id: "name", desc: false }])).toMatchObject({
      page: 0,
      sort: "name",
      direction: "asc",
    });
    expect(segmentListStateFromSorting(state, [{ id: "notAColumn", desc: false }])).toEqual(state);
  });
});
