import { describe, expect, test } from "bun:test";
import type { SubscriptionChangeInput, SubscriptionChangePreview } from "@/api/contracts";
import { ApiError } from "@/api/http";
import {
  subscriptionChangeFailureMessage,
  subscriptionChangePreviewIsCurrent,
  subscriptionChangeSelectionKey,
} from "./subscription-change-rules";

const selection: SubscriptionChangeInput = {
  targetPlanCode: "PRO",
  addOnCodes: ["B2B", "HR"],
  quotaPackages: [
    { packageCode: "MEMBERS", quantity: 2 },
    { packageCode: "STORAGE", quantity: 1 },
  ],
  timing: "AT_RENEWAL",
  planPriceSelection: { priceEntryId: "price-1", currencyCode: "MAD", billingCycle: "MONTHLY" },
};

const preview = {
  targetPlanCode: "PRO",
  expiresAt: "2026-08-27T12:05:00Z",
} as SubscriptionChangePreview;

describe("subscription price failure feedback", () => {
  test("turns a stale exact-price conflict into a recoverable instruction", () => {
    const error = new ApiError(409, "STALE_RESOURCE_VERSION", "Ce tarif a changé.");
    expect(subscriptionChangeFailureMessage(error)).toContain("prévisualisez à nouveau");
  });

  test("keeps a backend validation message visible", () => {
    const error = new ApiError(400, "INVALID_REQUEST", "Cette devise n’est plus proposée.");
    expect(subscriptionChangeFailureMessage(error)).toBe("Cette devise n’est plus proposée.");
  });

  test("treats unordered add-ons and packages as the same signed selection", () => {
    const reordered = {
      ...selection,
      addOnCodes: ["HR", "B2B"],
      quotaPackages: [...selection.quotaPackages].reverse(),
    };
    expect(subscriptionChangeSelectionKey(reordered)).toBe(subscriptionChangeSelectionKey(selection));
  });

  test("rejects expired evidence and evidence for a changed selection", () => {
    const key = subscriptionChangeSelectionKey(selection);
    expect(subscriptionChangePreviewIsCurrent(preview, selection, key, Date.parse("2026-08-27T12:04:59Z"))).toBeTrue();
    expect(subscriptionChangePreviewIsCurrent(preview, selection, key, Date.parse("2026-08-27T12:05:00Z"))).toBeFalse();
    expect(
      subscriptionChangePreviewIsCurrent(
        preview,
        { ...selection, quotaPackages: [{ packageCode: "MEMBERS", quantity: 3 }] },
        key,
        Date.parse("2026-08-27T12:04:59Z"),
      ),
    ).toBeFalse();
  });
});
