import { describe, expect, test } from "bun:test";
import { canRunOfferAction, offerActionReason, offerPeriodLabel } from "./offer-rules";

describe("Offer operational rules", () => {
  test("requires both backend availability and the narrow permission", () => {
    const state = { availableActions: ["PUBLISH" as const] };
    expect(canRunOfferAction("PUBLISH", state, (permission) => permission === "platform.offers.publish")).toBe(true);
    expect(canRunOfferAction("PUBLISH", state, () => false)).toBe(false);
    expect(canRunOfferAction("RETIRE", state, () => true)).toBe(false);
  });

  test("renders all blocker reasons without exposing enum codes", () => {
    const reason = offerActionReason("PUBLISH", {
      blockedActions: { PUBLISH: ["CAMPAIGN_NOT_ACTIVE", "WINDOW_NOT_STARTED"] },
    });
    expect(reason).toContain("campagne");
    expect(reason).not.toContain("CAMPAIGN_NOT_ACTIVE");
  });

  test("uses half-open Offer windows", () => {
    expect(offerPeriodLabel("2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z", Date.parse("2026-02-01T00:00:00Z"))).toBe(
      "Terminée",
    );
  });
});
