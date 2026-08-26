import { describe, expect, test } from "bun:test";
import type { CatalogPrice, ProductPrice } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import {
  currentCatalogPrice,
  defaultCatalogPrice,
  initialCatalogPlanCode,
  matchingCatalogPrice,
  pruneCommercialSelection,
} from "@/features/commercial/catalog-price-rules";
import {
  canUseProductPriceAction,
  isProductPriceReplacementDraft,
  productPriceActionReason,
  validateProductPriceDraft,
} from "./product-price-rules";

const price = (id: string, billingCycle: "MONTHLY" | "YEARLY", amount: number): CatalogPrice => ({
  priceEntryId: id,
  amount,
  currencyCode: "MAD",
  billingCycle,
  effectiveFrom: "2026-01-01T00:00:00Z",
  effectiveUntil: null,
});

describe("product price lifecycle actions", () => {
  const draft = {
    availableActions: ["EDIT_DRAFT", "PREVIEW_ACTIVATION", "ACTIVATE"],
    blockers: [],
  } as Pick<ProductPrice, "availableActions" | "blockers">;

  test("requires both the backend action and the exact operator permission", () => {
    expect(
      canUseProductPriceAction(draft, "ACTIVATE", (value) => value === adminPermissions.priceBooksActivate),
    ).toBeTrue();
    expect(canUseProductPriceAction(draft, "PAUSE", () => true)).toBeFalse();
    expect(canUseProductPriceAction(draft, "ACTIVATE", () => false)).toBeFalse();
  });

  test("explains permission and backend blockers separately", () => {
    expect(productPriceActionReason(draft, "ACTIVATE", () => false)).toContain("rôle");
    expect(
      productPriceActionReason({ availableActions: [], blockers: ["OWNER_NOT_ACTIVE"] }, "ACTIVATE", () => true),
    ).toContain("produit");
  });

  test("offers scheduled replacement only on a direct successor draft", () => {
    expect(isProductPriceReplacementDraft({ status: "DRAFT", sourcePriceId: "source" })).toBeTrue();
    expect(isProductPriceReplacementDraft({ status: "DRAFT", sourcePriceId: null })).toBeFalse();
    expect(isProductPriceReplacementDraft({ status: "ACTIVE", sourcePriceId: "source" })).toBeFalse();
  });
});

describe("product price draft validation", () => {
  test("rejects invalid money and an inverted effective window", () => {
    const errors = validateProductPriceDraft({
      amount: "12.12345",
      currencyCode: "MA",
      billingCycle: "MONTHLY",
      effectiveFrom: "2026-08-20T12:00",
      effectiveUntil: "2026-08-20T11:00",
    });

    expect(errors.amount).toBeTruthy();
    expect(errors.currencyCode).toBeTruthy();
    expect(errors.effectiveUntil).toBeTruthy();
  });

  test("accepts a bounded or open-ended valid price", () => {
    expect(
      validateProductPriceDraft({
        amount: "120.5000",
        currencyCode: "mad",
        billingCycle: "YEARLY",
        effectiveFrom: "2026-08-20T12:00",
        effectiveUntil: "",
      }),
    ).toEqual({});
  });
});

describe("client catalogue price selection", () => {
  const monthly = price("monthly", "MONTHLY", 12);
  const yearly = price("yearly", "YEARLY", 100);

  test("defaults predictably and matches extensions by selected terms", () => {
    expect(defaultCatalogPrice([yearly, monthly])?.priceEntryId).toBe("monthly");
    expect(matchingCatalogPrice([yearly], monthly)).toBeNull();
    expect(matchingCatalogPrice([yearly, monthly], yearly)?.priceEntryId).toBe("yearly");
  });

  test("prunes add-ons that cannot be sold with the selected currency and cycle", () => {
    expect(
      pruneCommercialSelection(
        ["roles", "b2b", "removed"],
        [
          { code: "roles", prices: [monthly] },
          { code: "b2b", prices: [yearly] },
        ],
        monthly,
      ),
    ).toEqual(["roles"]);
  });

  test("falls back when the current plan is intentionally absent from the eligible catalogue", () => {
    const plans = [{ code: "VISIBLE" }, { code: "OTHER" }];
    expect(initialCatalogPlanCode(plans, "VISIBLE")).toBe("VISIBLE");
    expect(initialCatalogPlanCode(plans, "DIRECT_ONLY")).toBe("VISIBLE");
    expect(initialCatalogPlanCode([], "DIRECT_ONLY")).toBe("");
  });

  test("restores the exact current price before considering its commercial tuple", () => {
    const sameAmountYearly = price("yearly", "YEARLY", 12);
    expect(
      currentCatalogPrice([monthly, sameAmountYearly], {
        planPriceEntryId: "yearly",
        currentPriceCurrencyCode: "MAD",
        billingCycle: "MONTHLY",
      })?.priceEntryId,
    ).toBe("yearly");
    expect(
      currentCatalogPrice([monthly, sameAmountYearly], {
        planPriceEntryId: null,
        currentPriceCurrencyCode: "MAD",
        billingCycle: "YEARLY",
      })?.priceEntryId,
    ).toBe("yearly");
  });
});
