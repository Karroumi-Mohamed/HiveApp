import { describe, expect, test } from "bun:test";
import { adjacentCampaignEditorSteps, shouldBlockCampaignEditorNavigation } from "./commercial-campaign-editor-state";

describe("commercial campaign editor navigation", () => {
  test("does not wrap Previous from the first step to Review", () => {
    expect(adjacentCampaignEditorSteps("definition")).toEqual({
      previous: undefined,
      next: "audience",
    });
    expect(adjacentCampaignEditorSteps("review")).toEqual({
      previous: "calendar",
      next: undefined,
    });
  });

  test("allows URL-backed step changes but protects a dirty editor when leaving", () => {
    expect(
      shouldBlockCampaignEditorNavigation(true, false, "/admin/campaigns/new", "/admin/campaigns/new"),
    ).toBeFalse();
    expect(shouldBlockCampaignEditorNavigation(true, false, "/admin/campaigns/new", "/admin/campaigns")).toBeTrue();
    expect(shouldBlockCampaignEditorNavigation(false, false, "/admin/campaigns/new", "/admin/campaigns")).toBeFalse();
    expect(shouldBlockCampaignEditorNavigation(true, true, "/admin/campaigns/new", "/admin/campaigns")).toBeFalse();
  });
});
