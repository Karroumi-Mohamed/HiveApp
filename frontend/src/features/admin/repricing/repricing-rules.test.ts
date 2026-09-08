import { describe, expect, test } from "bun:test";
import type { ProductPrice } from "@/api/contracts";
import type { RepricingPreview } from "@/api/repricing-api";
import { compatibleTariff, currentRepricingPreview, repricingBlocker } from "./repricing-rules";

describe("price-only review rules", () => {
  const source = {
    id: "old",
    productId: "plan",
    productType: "PLAN",
    currencyCode: "MAD",
    billingCycle: "MONTHLY",
  } as ProductPrice;
  const target = { ...source, id: "new", status: "ACTIVE" } as ProductPrice;
  test("only a different active tariff for the exact same product and cadence is selectable", () => {
    expect(compatibleTariff(source, target)).toBeTrue();
    for (const change of [
      { id: "old" },
      { productId: "other" },
      { productType: "ADD_ON" },
      { billingCycle: "YEARLY" },
      { currencyCode: "USD" },
      { status: "DRAFT" },
    ]) {
      expect(compatibleTariff(source, { ...target, ...change } as ProductPrice)).toBeFalse();
    }
  });
  test("missing, expired, changed or wholly blocked reviews cannot confirm", () => {
    const preview = { expiresAt: new Date(2000).toISOString(), summary: { readyCount: 1 } } as RepricingPreview;
    expect(currentRepricingPreview(preview, "same", "same", 1000)).toBeTrue();
    expect(currentRepricingPreview(null, "same", "same", 1000)).toBeFalse();
    expect(currentRepricingPreview(preview, "old", "new", 1000)).toBeFalse();
    expect(currentRepricingPreview(preview, "same", "same", 2000)).toBeFalse();
    expect(
      currentRepricingPreview({ ...preview, summary: { ...preview.summary, readyCount: 0 } }, "same", "same", 1000),
    ).toBeFalse();
  });
  test("protected agreements are explained without exposing internal identifiers", () => {
    expect(repricingBlocker("PROTECTED_TERMS")).toBe("Accord particulier ou remise protégée");
    expect(repricingBlocker("unknown-internal-id")).toBe("Conditions à revoir");
  });
});
