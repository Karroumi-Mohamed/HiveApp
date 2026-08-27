import type { CatalogPrice } from "@/api/contracts";
import { compareExactDecimals } from "@/lib/exact-decimal";

export function initialCatalogPlanCode<T extends { code: string }>(
  plans: readonly T[],
  currentPlanCode?: string | null,
) {
  if (currentPlanCode && plans.some((plan) => plan.code === currentPlanCode)) return currentPlanCode;
  return plans[0]?.code ?? "";
}

export function defaultCatalogPrice(prices: readonly CatalogPrice[], preferred?: CatalogPrice | null) {
  if (preferred && prices.some((price) => price.priceEntryId === preferred.priceEntryId)) return preferred;
  return (
    [...prices].sort((left, right) => {
      if (left.billingCycle !== right.billingCycle) return left.billingCycle === "MONTHLY" ? -1 : 1;
      if (left.currencyCode !== right.currencyCode) return left.currencyCode.localeCompare(right.currencyCode);
      return compareExactDecimals(left.amount, right.amount);
    })[0] ?? null
  );
}

export function currentCatalogPrice(
  prices: readonly CatalogPrice[],
  current: {
    planPriceEntryId?: string | null;
    currentPriceCurrencyCode?: string | null;
    billingCycle?: CatalogPrice["billingCycle"] | null;
  } | null,
) {
  if (current?.planPriceEntryId) {
    const exact = prices.find((price) => price.priceEntryId === current.planPriceEntryId);
    if (exact) return exact;
  }
  if (current?.currentPriceCurrencyCode && current.billingCycle) {
    const tuple = prices.find(
      (price) => price.currencyCode === current.currentPriceCurrencyCode && price.billingCycle === current.billingCycle,
    );
    if (tuple) return tuple;
  }
  return defaultCatalogPrice(prices);
}

export function matchingCatalogPrice(
  prices: readonly CatalogPrice[],
  selection: Pick<CatalogPrice, "currencyCode" | "billingCycle"> | null,
) {
  if (!selection) return null;
  return (
    prices.find(
      (price) => price.currencyCode === selection.currencyCode && price.billingCycle === selection.billingCycle,
    ) ?? null
  );
}

export function pruneCommercialSelection<T extends { code: string; prices: CatalogPrice[] }>(
  selectedCodes: readonly string[],
  products: readonly T[],
  price: CatalogPrice | null,
) {
  const productsByCode = new Map(products.map((product) => [product.code, product]));
  return selectedCodes.filter((code) => {
    const product = productsByCode.get(code);
    return Boolean(product && matchingCatalogPrice(product.prices, price));
  });
}

export function sameStringSet(left: readonly string[], right: readonly string[]) {
  if (left.length !== right.length) return false;
  const rightSet = new Set(right);
  return left.every((value) => rightSet.has(value));
}

/** Hidden entitlements are carried only while editing the current plan; they are never added to a new sale. */
export function preserveRetainedSelection(
  selectable: readonly string[],
  previousSelection: readonly string[],
  retainedCodes: readonly string[],
  editingCurrentPlan: boolean,
): string[] {
  if (!editingCurrentPlan) return [...selectable];
  const retained = new Set(retainedCodes);
  return [...new Set([...selectable, ...previousSelection.filter((code) => retained.has(code))])];
}

type CatalogAddOnLike = {
  code: string;
  name: string;
  dependencyCodes: readonly string[];
  exclusionCodes: readonly string[];
};

/** Shared dependency/exclusion state for the operator and client subscription configurators. */
export function catalogAddOnSelectionState<T extends CatalogAddOnLike>(
  item: T,
  candidates: readonly T[],
  selectedCodes: readonly string[],
) {
  const selected = new Set(selectedCodes);
  const missingDependency = item.dependencyCodes.find(
    (code) => !candidates.some((candidate) => candidate.code === code),
  );
  const excludedBy = candidates.find(
    (candidate) =>
      candidate.code !== item.code &&
      selected.has(candidate.code) &&
      (item.exclusionCodes.includes(candidate.code) || candidate.exclusionCodes.includes(item.code)),
  );
  const requiredBy = candidates.find(
    (candidate) => selected.has(candidate.code) && candidate.dependencyCodes.includes(item.code),
  );
  return {
    selected: selected.has(item.code),
    missingDependency,
    excludedBy,
    requiredBy,
  } as const;
}

/** Selecting an Add-on also selects its complete available dependency closure. */
export function updateCatalogAddOnSelection<T extends CatalogAddOnLike>(
  selectedCodes: readonly string[],
  item: T,
  candidates: readonly T[],
  checked: boolean,
) {
  if (!checked) return selectedCodes.filter((code) => code !== item.code);
  const candidatesByCode = new Map(candidates.map((candidate) => [candidate.code, candidate]));
  const selected = new Set(selectedCodes);
  const pending = [item.code];
  while (pending.length) {
    const code = pending.pop();
    if (!code || selected.has(code)) continue;
    selected.add(code);
    for (const dependencyCode of candidatesByCode.get(code)?.dependencyCodes ?? []) {
      if (!selected.has(dependencyCode)) pending.push(dependencyCode);
    }
  }
  return [...selected];
}
