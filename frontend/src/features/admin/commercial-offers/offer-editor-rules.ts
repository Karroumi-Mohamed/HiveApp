import type {
  OfferAcceptance,
  OfferCreateInput,
  OfferDiscountType,
  OfferDiscovery,
  OfferEditableDefinition,
  OfferEffects,
  OfferPricingMode,
  OfferSelection,
  OfferUpdateInput,
} from "@/api/offer-contracts";

export type OfferEditorStep = "scope" | "selection" | "effect" | "limits" | "review";
export const offerEditorSteps: Array<{ value: OfferEditorStep; label: string }> = [
  { value: "scope", label: "Cadre" },
  { value: "selection", label: "Produits" },
  { value: "effect", label: "Avantage" },
  { value: "limits", label: "Accès & capacité" },
  { value: "review", label: "Vérification" },
];

export type OfferProductDraft = { productId: string; priceId: string; pricingMode: OfferPricingMode; quantity: number };
export type OfferDraft = {
  name: string;
  description: string;
  campaignId: string;
  startsAt: string;
  endsAt: string;
  discovery: OfferDiscovery;
  acceptance: OfferAcceptance;
  customerCodeMode: "KEEP" | "REPLACE" | "REMOVE";
  customerCode: string;
  globalLimit: string;
  perAccountLimit: string;
  plan: OfferProductDraft;
  addOns: OfferProductDraft[];
  quotaPackages: OfferProductDraft[];
  timing: "IMMEDIATE" | "AT_RENEWAL";
  discountType: OfferDiscountType;
  discountAmount: string;
  percentage: string;
  percentageCap: string;
  finiteQuotaBonuses: Array<{ featureCode: string; resource: string; quantity: number }>;
};

export type OfferDraftErrors = Partial<
  Record<"name" | "campaign" | "window" | "code" | "selection" | "discount" | "limits", string>
>;

export function toLocalDateTime(value: string | Date) {
  const date = typeof value === "string" ? new Date(value) : value;
  if (Number.isNaN(date.getTime())) return "";
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

export function emptyOfferDraft(now = Date.now()): OfferDraft {
  return {
    name: "",
    description: "",
    campaignId: "",
    startsAt: toLocalDateTime(new Date(now + 60 * 60 * 1000)),
    endsAt: toLocalDateTime(new Date(now + 30 * 24 * 60 * 60 * 1000)),
    discovery: "CATALOG",
    acceptance: "CLIENT_OR_OPERATOR",
    customerCodeMode: "REPLACE",
    customerCode: "",
    globalLimit: "",
    perAccountLimit: "1",
    plan: { productId: "", priceId: "", pricingMode: "PAID", quantity: 1 },
    addOns: [],
    quotaPackages: [],
    timing: "AT_RENEWAL",
    discountType: "NONE",
    discountAmount: "",
    percentage: "",
    percentageCap: "",
    finiteQuotaBonuses: [],
  };
}

export function offerDraftFromDefinition(definition: OfferEditableDefinition): OfferDraft {
  return {
    name: definition.name,
    description: definition.description ?? "",
    campaignId: definition.campaign.id,
    startsAt: toLocalDateTime(definition.startsAt),
    endsAt: toLocalDateTime(definition.endsAt),
    discovery: definition.discovery,
    acceptance: definition.acceptance,
    customerCodeMode: "KEEP",
    customerCode: "",
    globalLimit: definition.globalLimit == null ? "" : String(definition.globalLimit),
    perAccountLimit: definition.perAccountLimit == null ? "" : String(definition.perAccountLimit),
    plan: {
      productId: definition.selection.planId,
      priceId: definition.selection.planPriceId,
      pricingMode: definition.resolvedSelection.plan.pricingMode,
      quantity: 1,
    },
    addOns: definition.selection.addOns.map((item) => ({
      productId: item.addOnId,
      priceId: item.priceId,
      pricingMode: item.pricingMode,
      quantity: 1,
    })),
    quotaPackages: definition.selection.quotaPackages.map((item) => ({
      productId: item.quotaPackageId,
      priceId: item.priceId,
      pricingMode: item.pricingMode,
      quantity: item.quantity,
    })),
    timing: definition.selection.timing,
    discountType: definition.effects.discountType,
    discountAmount: definition.effects.discountAmount ?? "",
    percentage: definition.effects.percentage ?? "",
    percentageCap: definition.effects.percentageCap ?? "",
    finiteQuotaBonuses: definition.effects.finiteQuotaBonuses.map((bonus) => ({ ...bonus })),
  };
}

function positiveInteger(value: string) {
  if (!value) return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : Number.NaN;
}

function positiveDecimal(value: string) {
  if (!value) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : Number.NaN;
}

export function validateOfferDraft(draft: OfferDraft, customerCodeConfigured = false): OfferDraftErrors {
  const errors: OfferDraftErrors = {};
  if (!draft.name.trim()) errors.name = "Le nom est obligatoire.";
  if (!draft.campaignId) errors.campaign = "Sélectionnez une campagne.";
  const startsAt = Date.parse(draft.startsAt);
  const endsAt = Date.parse(draft.endsAt);
  if (!Number.isFinite(startsAt) || !Number.isFinite(endsAt) || endsAt <= startsAt)
    errors.window = "La fin doit être postérieure au début.";
  if (draft.discovery === "CODE_ONLY") {
    const keepsExisting = draft.customerCodeMode === "KEEP" && customerCodeConfigured;
    if (!keepsExisting && !draft.customerCode.trim()) errors.code = "Un code privé est obligatoire.";
  }
  if (!draft.plan.productId || !draft.plan.priceId) errors.selection = "Sélectionnez un forfait et son prix exact.";
  if (
    new Set([...draft.addOns, ...draft.quotaPackages].map((item) => item.priceId)).size !==
    draft.addOns.length + draft.quotaPackages.length
  )
    errors.selection = "Un même prix ne peut être sélectionné deux fois.";
  if (draft.quotaPackages.some((item) => !Number.isSafeInteger(item.quantity) || item.quantity <= 0))
    errors.selection = "La quantité de chaque pack doit être positive.";
  const globalLimit = positiveInteger(draft.globalLimit);
  const perAccountLimit = positiveInteger(draft.perAccountLimit);
  if (Number.isNaN(globalLimit) || Number.isNaN(perAccountLimit))
    errors.limits = "Les capacités doivent être des entiers positifs.";
  if (globalLimit && perAccountLimit && perAccountLimit > globalLimit)
    errors.limits = "La limite par compte ne peut pas dépasser la capacité globale.";
  if (draft.discountType === "FIXED" && !positiveDecimal(draft.discountAmount))
    errors.discount = "Saisissez une réduction fixe positive.";
  if (draft.discountType === "PERCENTAGE_WITH_CAP") {
    const percentage = positiveDecimal(draft.percentage);
    const cap = positiveDecimal(draft.percentageCap);
    if (!percentage || percentage > 100 || !cap)
      errors.discount = "Saisissez un pourcentage entre 0 et 100 et un plafond positif.";
  }
  return errors;
}

export function hasOfferDraftErrors(errors: OfferDraftErrors) {
  return Object.keys(errors).length > 0;
}

function effects(draft: OfferDraft): OfferEffects {
  return {
    discountType: draft.discountType,
    discountAmount: draft.discountType === "FIXED" ? draft.discountAmount : null,
    percentage: draft.discountType === "PERCENTAGE_WITH_CAP" ? draft.percentage : null,
    percentageCap: draft.discountType === "PERCENTAGE_WITH_CAP" ? draft.percentageCap : null,
    finiteQuotaBonuses: draft.finiteQuotaBonuses.map((bonus) => ({ ...bonus })),
  };
}

function selection(draft: OfferDraft): OfferSelection {
  return {
    planId: draft.plan.productId,
    planPriceId: draft.plan.priceId,
    addOns: draft.addOns.map((item) => ({
      addOnId: item.productId,
      priceId: item.priceId,
      pricingMode: item.pricingMode,
    })),
    quotaPackages: draft.quotaPackages.map((item) => ({
      quotaPackageId: item.productId,
      priceId: item.priceId,
      quantity: item.quantity,
      pricingMode: item.pricingMode,
    })),
    timing: draft.timing,
  };
}

function limit(value: string) {
  return value ? Number(value) : null;
}

export function toOfferCreateInput(draft: OfferDraft): OfferCreateInput {
  return {
    name: draft.name.trim(),
    description: draft.description.trim() || null,
    campaignId: draft.campaignId,
    startsAt: new Date(draft.startsAt).toISOString(),
    endsAt: new Date(draft.endsAt).toISOString(),
    discovery: draft.discovery,
    acceptance: draft.acceptance,
    customerCode: draft.discovery === "CODE_ONLY" ? draft.customerCode.trim() : null,
    globalLimit: limit(draft.globalLimit),
    perAccountLimit: limit(draft.perAccountLimit),
    selection: selection(draft),
    effects: effects(draft),
  };
}

export function toOfferUpdateInput(draft: OfferDraft, definition: OfferEditableDefinition): OfferUpdateInput {
  return {
    version: definition.version,
    name: draft.name.trim(),
    description: draft.description.trim() || null,
    startsAt: new Date(draft.startsAt).toISOString(),
    endsAt: new Date(draft.endsAt).toISOString(),
    selection: selection(draft),
    effects: effects(draft),
    lineageTerms: definition.lineageTermsEditable
      ? {
          expectedLineageVersion: definition.lineageVersion,
          discovery: draft.discovery,
          acceptance: draft.acceptance,
          globalLimit: limit(draft.globalLimit),
          perAccountLimit: limit(draft.perAccountLimit),
          customerCodeChange: {
            mode: draft.customerCodeMode,
            value: draft.customerCodeMode === "REPLACE" ? draft.customerCode.trim() : null,
          },
        }
      : null,
  };
}

export function offerDraftFingerprint(draft: OfferDraft) {
  return JSON.stringify(draft);
}
