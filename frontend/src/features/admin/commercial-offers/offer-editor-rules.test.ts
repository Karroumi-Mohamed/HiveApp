import { describe, expect, test } from "bun:test";
import { emptyOfferDraft, toLocalDateTime, toOfferCreateInput, validateOfferDraft } from "./offer-editor-rules";

describe("Offer editor rules", () => {
  test("requires an exact plan price and private code", () => {
    const draft = { ...emptyOfferDraft(), name: "Privée", campaignId: "campaign", discovery: "CODE_ONLY" as const };
    expect(validateOfferDraft(draft)).toMatchObject({ selection: expect.any(String), code: expect.any(String) });
  });

  test("does not emit hidden discount fields", () => {
    const draft = {
      ...emptyOfferDraft(),
      name: "Essai",
      campaignId: "campaign",
      plan: { productId: "plan", priceId: "price", pricingMode: "PAID" as const, quantity: 1 },
      discountType: "NONE" as const,
      discountAmount: "50",
      percentage: "10",
      percentageCap: "100",
    };
    expect(toOfferCreateInput(draft).effects).toMatchObject({
      discountAmount: null,
      percentage: null,
      percentageCap: null,
    });
  });

  test("rejects a per-account limit larger than global capacity", () => {
    const draft = { ...emptyOfferDraft(), globalLimit: "5", perAccountLimit: "6" };
    expect(validateOfferDraft(draft).limits).toContain("dépasser");
  });

  test("preserves a campaign instant when converting it for a local date-time field", () => {
    const instant = new Date("2026-08-31T14:25:00.000Z");
    expect(new Date(toLocalDateTime(instant)).getTime()).toBe(instant.getTime());
  });
});
