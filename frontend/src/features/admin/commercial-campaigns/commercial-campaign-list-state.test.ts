import { describe, expect, test } from "bun:test";
import {
  campaignListStateFromSorting,
  readCampaignListState,
  writeCampaignListState,
} from "./commercial-campaign-list-state";

describe("commercial campaign list URL state", () => {
  test("bounds untrusted URL values and keeps only server-supported sort fields", () => {
    const state = readCampaignListState(
      new URLSearchParams(
        "q=renewal&status=ACTIVE&audience=SEGMENT&source=SALES&page=4&size=50&sort=owner.email&direction=asc",
      ),
    );
    expect(state).toMatchObject({
      search: "renewal",
      status: "ACTIVE",
      audienceMode: "SEGMENT",
      source: "SALES",
      page: 4,
      size: 50,
      sort: "createdAt",
      direction: "asc",
    });
    expect(readCampaignListState(new URLSearchParams("page=-1&size=500&status=DELETED"))).toMatchObject({
      page: 0,
      size: 20,
      status: "ALL",
    });
  });

  test("serializes canonical filters without destroying unrelated URL state", () => {
    const state = readCampaignListState(new URLSearchParams());
    const written = writeCampaignListState(new URLSearchParams("tab=summary"), {
      ...state,
      status: "PAUSED",
      includeArchived: true,
      page: 2,
    });
    expect(written.get("tab")).toBe("summary");
    expect(written.get("status")).toBe("PAUSED");
    expect(written.get("archived")).toBe("true");
    expect(written.get("page")).toBe("2");
  });

  test("ignores a client-only sort before it reaches the backend", () => {
    const state = readCampaignListState(new URLSearchParams());
    expect(campaignListStateFromSorting(state, [{ id: "frozenAccountCount", desc: false }])).toBe(state);
    expect(campaignListStateFromSorting(state, [{ id: "startsAt", desc: false }])).toMatchObject({
      sort: "startsAt",
      direction: "asc",
      page: 0,
    });
  });
});
