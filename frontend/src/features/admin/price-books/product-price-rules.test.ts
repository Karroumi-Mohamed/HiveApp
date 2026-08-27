import { describe, expect, test } from "bun:test";
import type { CatalogPrice, ProductPrice, ProductPriceActivationPreview } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import {
  currentCatalogPrice,
  defaultCatalogPrice,
  initialCatalogPlanCode,
  matchingCatalogPrice,
  preserveRetainedSelection,
  pruneCommercialSelection,
} from "@/features/commercial/catalog-price-rules";
import {
  canUseProductPriceAction,
  isProductPriceReplacementDraft,
  productPriceActionReason,
  productPriceActivationReady,
  productPriceActivationReviewReady,
  productPriceHistoryLabel,
  productPriceOwnerReadPermission,
  resolveSortingUpdate,
  validateProductPriceDraft,
} from "./product-price-rules";

const price = (id: string, billingCycle: "MONTHLY" | "YEARLY", amount: string): CatalogPrice => ({
  priceEntryId: id,
  amount,
  currencyCode: "MAD",
  billingCycle,
  effectiveFrom: "2026-01-01T00:00:00Z",
  effectiveUntil: null,
});

describe("retained commercial selections", () => {
  test("keeps an already-held hidden add-on only on the current plan", () => {
    expect(preserveRetainedSelection(["PUBLIC"], ["PUBLIC", "HIDDEN"], ["HIDDEN"], true)).toEqual(["PUBLIC", "HIDDEN"]);
    expect(preserveRetainedSelection(["PUBLIC"], ["PUBLIC", "HIDDEN"], ["HIDDEN"], false)).toEqual(["PUBLIC"]);
  });

  test("does not silently re-add a retained item the client removed", () => {
    expect(preserveRetainedSelection(["PUBLIC"], ["PUBLIC"], ["HIDDEN"], true)).toEqual(["PUBLIC"]);
  });
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

    const inactive = { availableActions: ["PREVIEW_ACTIVATION", "REACTIVATE"] } as Pick<
      ProductPrice,
      "availableActions"
    >;
    expect(
      canUseProductPriceAction(inactive, "REACTIVATE", (value) => value === adminPermissions.priceBooksActivate),
    ).toBeFalse();
    expect(
      canUseProductPriceAction(inactive, "REACTIVATE", (value) => value === adminPermissions.priceBooksReactivate),
    ).toBeTrue();
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

  test("activation requires signed evidence for the exact visible revision", () => {
    const reviewed: ProductPriceActivationPreview = {
      priceEntryId: "price-1",
      expectedVersion: 4,
      catalogRevision: 12,
      registryVersion: "registry-v1",
      evaluatedAt: "2026-08-27T05:00:00Z",
      expiresAt: "2026-08-27T05:05:00Z",
      previewToken: "signed-evidence",
      activatable: true,
      blockers: [],
    };
    const reviewedAt = Date.parse("2026-08-27T05:01:00Z");
    expect(productPriceActivationReady({ id: "price-1", version: 4 }, reviewed, reviewedAt)).toBeTrue();
    expect(productPriceActivationReady({ id: "price-1", version: 5 }, reviewed, reviewedAt)).toBeFalse();
    expect(productPriceActivationReady({ id: "price-2", version: 4 }, reviewed, reviewedAt)).toBeFalse();
    expect(
      productPriceActivationReady({ id: "price-1", version: 4 }, { ...reviewed, previewToken: "" }, reviewedAt),
    ).toBeFalse();
    expect(
      productPriceActivationReady({ id: "price-1", version: 4 }, reviewed, Date.parse(reviewed.expiresAt)),
    ).toBeFalse();
    expect(
      productPriceActivationReady(
        { id: "price-1", version: 4 },
        { ...reviewed, expiresAt: "not-an-instant" },
        reviewedAt,
      ),
    ).toBeFalse();
  });

  test("retained evidence cannot be submitted while refreshing or after refresh failed", () => {
    const reviewed: ProductPriceActivationPreview = {
      priceEntryId: "price-1",
      expectedVersion: 4,
      catalogRevision: 12,
      registryVersion: "registry-v1",
      evaluatedAt: "2026-08-27T05:00:00Z",
      expiresAt: "2026-08-27T05:05:00Z",
      previewToken: "signed-evidence",
      activatable: true,
      blockers: [],
    };
    const priceIdentity = { id: "price-1", version: 4 };
    const now = Date.parse("2026-08-27T05:01:00Z");

    expect(
      productPriceActivationReviewReady(priceIdentity, { data: reviewed, isFetching: true, isError: false }, now),
    ).toBeFalse();
    expect(
      productPriceActivationReviewReady(priceIdentity, { data: reviewed, isFetching: false, isError: true }, now),
    ).toBeFalse();
    expect(
      productPriceActivationReviewReady(priceIdentity, { data: reviewed, isFetching: false, isError: false }, now),
    ).toBeTrue();
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

  test("accepts the exact maximum precision without converting it to a number", () => {
    expect(
      validateProductPriceDraft({
        amount: "999999999999999.9999",
        currencyCode: "MAD",
        billingCycle: "MONTHLY",
        effectiveFrom: "2026-08-20T12:00",
        effectiveUntil: "",
      }),
    ).toEqual({});
  });
});

describe("price-book URL and history presentation", () => {
  test("resolves TanStack functional sorting updaters", () => {
    const current = [{ id: "createdAt", desc: true }];
    expect(resolveSortingUpdate(() => [{ id: "amount", desc: false }], current)).toEqual([
      { id: "amount", desc: false },
    ]);
  });

  test("normalizes the backend permission-style audit action", () => {
    expect(productPriceHistoryLabel("platform.price_books.activate")).toBe("Tarif mis en vente");
    expect(productPriceHistoryLabel("platform.price_books.schedule_replacement")).toBe("Remplacement programmé");
    expect(productPriceHistoryLabel("platform.price_books.create")).toBe("Brouillon créé");
  });

  test("requires the linked product's own detail permission", () => {
    expect(productPriceOwnerReadPermission("PLAN")).toBe(adminPermissions.plansReadDetail);
    expect(productPriceOwnerReadPermission("ADD_ON")).toBe(adminPermissions.addOnsReadDetail);
    expect(productPriceOwnerReadPermission("QUOTA_PACKAGE")).toBe(adminPermissions.quotaPackagesReadDetail);
  });
});

describe("client catalogue price selection", () => {
  const monthly = price("monthly", "MONTHLY", "12");
  const yearly = price("yearly", "YEARLY", "100");

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
    const sameAmountYearly = price("yearly", "YEARLY", "12");
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
