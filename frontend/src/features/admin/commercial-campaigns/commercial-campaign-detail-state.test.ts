import { describe, expect, test } from "bun:test";
import {
  boundedCampaignPage,
  boundedCampaignResponsePage,
  withCampaignSearchParam,
} from "./commercial-campaign-detail-state";

describe("commercial campaign detail URL state", () => {
  test("rejects malformed and unbounded pages, then clamps stale server pages", () => {
    expect(boundedCampaignPage("-1")).toBe(0);
    expect(boundedCampaignPage("1.5")).toBe(0);
    expect(boundedCampaignPage("10001")).toBe(0);
    expect(boundedCampaignPage("42")).toBe(42);
    expect(boundedCampaignResponsePage(9, 3)).toBe(2);
    expect(boundedCampaignResponsePage(9, 0)).toBe(0);
  });

  test("identity reveal and pagination remain explicit independent URL state", () => {
    const params = new URLSearchParams("identified=true&page=2");
    expect(withCampaignSearchParam(params, "page", 0).get("identified")).toBe("true");
    expect(withCampaignSearchParam(params, "identified", null).get("page")).toBe("2");
  });
});
