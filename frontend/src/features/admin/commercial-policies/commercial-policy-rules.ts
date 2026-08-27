import type {
  CommercialPolicyActivationPreview,
  CommercialPolicyBlocker,
  CommercialPolicyDetail,
  CommercialPolicyEffectInput,
  CommercialPolicyEffectType,
  CommercialPolicyExecutionBlocker,
  CommercialPolicyProductType,
  CommercialPolicySource,
  CommercialPolicyStatus,
  CommercialPolicyTargetKind,
  CommercialPolicyWriteInput,
  ProductPriceBillingCycle,
  RegistryFeature,
} from "@/api/contracts";
import { ApiError } from "@/api/http";
import { isCommercialAmount } from "@/lib/exact-decimal";

export const policyStatus: Record<
  CommercialPolicyStatus,
  { label: string; tone: "neutral" | "info" | "success" | "warning" }
> = {
  DRAFT: { label: "Brouillon", tone: "info" },
  ACTIVE: { label: "Active", tone: "success" },
  PAUSED: { label: "Suspendue", tone: "warning" },
  ENDED: { label: "Terminée", tone: "neutral" },
  ARCHIVED: { label: "Archivée", tone: "neutral" },
};

export const policySource: Record<CommercialPolicySource, string> = {
  CONTRACT: "Contrat",
  SALES: "Vente",
  MARKETING: "Marketing",
  RETENTION: "Rétention",
  SUPPORT: "Support",
  COMPLIANCE: "Conformité",
  OTHER: "Autre",
};

export const policyTarget: Record<CommercialPolicyTargetKind, string> = {
  ACCOUNT: "Un compte",
  ACCOUNT_SET: "Plusieurs comptes",
  PLAN_REVISION_SUBSCRIBERS: "Abonnés d’une révision de forfait",
  SEGMENT: "Segment (résolution indisponible)",
};

export const effectLabel: Record<CommercialPolicyEffectType, string> = {
  FIXED_SUBSCRIPTION_PRICE: "Prix fixe d’abonnement",
  FIXED_DISCOUNT: "Remise fixe",
  PERCENTAGE_DISCOUNT: "Remise en pourcentage",
  ALLOW_PRODUCT_SELECTION: "Autoriser un produit",
  BLOCK_PRODUCT_SELECTION: "Bloquer un produit",
  ADDITIVE_QUOTA_BONUS: "Ajouter de la capacité",
  GRANT_ADD_ON: "Accorder un add-on",
  GRANT_QUOTA_PACKAGE: "Accorder un pack de capacité",
  BLOCK_FEATURE: "Bloquer une fonctionnalité",
};

export function isPolicyQuotaFeatureChoice(
  feature: Pick<RegistryFeature, "status" | "publicVisible" | "newGrantsEnabled" | "runtimeEnabled">,
) {
  return (
    (feature.status === "PUBLIC" || feature.status === "BETA") &&
    feature.publicVisible &&
    feature.newGrantsEnabled &&
    feature.runtimeEnabled
  );
}

export const policyBlocker: Record<CommercialPolicyBlocker, string> = {
  WRONG_LIFECYCLE_STATE: "Le cycle de vie actuel ne permet pas cette activation.",
  EFFECTIVE_WINDOW_EXPIRED: "La période d’effet est déjà terminée.",
  SEGMENT_RESOLUTION_UNAVAILABLE: "La résolution des segments n’est pas encore connectée.",
  TARGET_NOT_CONFIGURED: "La cible n’est pas configurée.",
  TARGET_ACCOUNT_MISSING: "Le compte ciblé n’existe plus.",
  PLAN_REVISION_MISSING: "La révision de forfait ciblée n’existe plus.",
  EXPLICIT_ACCOUNT_SET_EMPTY: "La sélection de comptes est vide.",
  EXPLICIT_ACCOUNT_MISSING: "Un compte sélectionné n’existe plus.",
  AUDIENCE_EXCEEDS_ACTIVATION_LIMIT: "L’audience dépasse la limite de sécurité d’une activation.",
  LINEAGE_HAS_MULTIPLE_CURRENT_REVISIONS: "La lignée contient plusieurs révisions courantes.",
  NO_EFFECTS: "Aucun effet n’est configuré.",
  INVALID_EFFECT: "Un effet ne respecte pas son contrat typé.",
  PRODUCT_REVISION_MISSING: "Une révision de produit est absente.",
  PRODUCT_REVISION_NOT_ACTIVE: "Une révision de produit n’est pas active.",
  FEATURE_MISSING: "Une fonctionnalité référencée n’existe plus.",
  FEATURE_NOT_COMMERCIALLY_GRANTABLE: "Une fonctionnalité ne peut pas être accordée commercialement.",
  QUOTA_RESOURCE_NOT_DECLARED: "La ressource de quota n’est pas déclarée par la fonctionnalité.",
  AUDIENCE_CURRENCY_MISMATCH: "Les comptes ciblés n’utilisent pas une devise compatible.",
};

export const executionBlocker: Record<CommercialPolicyExecutionBlocker, string> = {
  SCHEDULED_EXECUTION_NOT_AVAILABLE: "L’exécution planifiée des effets n’est pas encore disponible.",
};

export type CommercialPolicyDraftEffect = {
  key: string;
  type: CommercialPolicyEffectType;
  productType: CommercialPolicyProductType | "";
  productRevisionId: string;
  featureCode: string;
  quotaResource: string;
  quantityDelta: string;
  amount: string;
  currencyCode: string;
  billingCycle: ProductPriceBillingCycle | "";
  percentage: string;
  maximumAmount: string;
  maximumCurrencyCode: string;
};

export type CommercialPolicyDraft = {
  name: string;
  description: string;
  effectiveFrom: string;
  effectiveUntil: string;
  source: CommercialPolicySource;
  priority: string;
  reason: string;
  approvalReference: string;
  contractReference: string;
  targetKind: CommercialPolicyTargetKind;
  accountId: string;
  accountIds: string[];
  planRevisionId: string;
  segmentReference: string;
  effects: CommercialPolicyDraftEffect[];
};

export type PolicyDraftErrors = Partial<
  Record<"name" | "window" | "priority" | "reason" | "target" | "effects", string>
> & {
  effectErrors?: Record<string, string>;
};

let effectSequence = 0;
export function newDraftEffect(type: CommercialPolicyEffectType = "FIXED_DISCOUNT"): CommercialPolicyDraftEffect {
  effectSequence += 1;
  return {
    key: `effect-${effectSequence}`,
    type,
    productType: type === "GRANT_ADD_ON" ? "ADD_ON" : type === "GRANT_QUOTA_PACKAGE" ? "QUOTA_PACKAGE" : "",
    productRevisionId: "",
    featureCode: "",
    quotaResource: "",
    quantityDelta: "",
    amount: "",
    currencyCode: "MAD",
    billingCycle: "",
    percentage: "",
    maximumAmount: "",
    maximumCurrencyCode: "MAD",
  };
}

export function localDateTimeValue(value: string | null) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

export function instantFromLocalValue(value: string) {
  const date = new Date(value);
  return date.toISOString();
}

export function emptyCommercialPolicyDraft(): CommercialPolicyDraft {
  const now = new Date();
  now.setSeconds(0, 0);
  return {
    name: "",
    description: "",
    effectiveFrom: localDateTimeValue(now.toISOString()),
    effectiveUntil: "",
    source: "OTHER",
    priority: "0",
    reason: "",
    approvalReference: "",
    contractReference: "",
    targetKind: "ACCOUNT",
    accountId: "",
    accountIds: [],
    planRevisionId: "",
    segmentReference: "",
    effects: [newDraftEffect()],
  };
}

export function draftFromPolicy(policy: CommercialPolicyDetail): CommercialPolicyDraft {
  return {
    name: policy.summary.name,
    description: policy.description ?? "",
    effectiveFrom: localDateTimeValue(policy.summary.effectiveFrom),
    effectiveUntil: localDateTimeValue(policy.summary.effectiveUntil),
    source: policy.summary.source,
    priority: String(policy.summary.priority),
    reason: policy.reason,
    approvalReference: policy.approvalReference ?? "",
    contractReference: policy.contractReference ?? "",
    targetKind: policy.target.kind,
    accountId: policy.target.accountId ?? "",
    accountIds: [...policy.target.accountIds],
    planRevisionId: policy.target.planRevisionId ?? "",
    segmentReference: policy.target.segmentReference ?? "",
    effects: policy.effects.map((effect) => ({
      key: effect.id,
      type: effect.type,
      productType: effect.productType ?? "",
      productRevisionId: effect.productRevisionId ?? "",
      featureCode: effect.featureCode ?? "",
      quotaResource: effect.quotaResource ?? "",
      quantityDelta: effect.quantityDelta == null ? "" : String(effect.quantityDelta),
      amount: effect.amount == null ? "" : String(effect.amount),
      currencyCode: effect.currencyCode ?? "MAD",
      billingCycle: effect.billingCycle ?? "",
      percentage: effect.percentage == null ? "" : String(effect.percentage),
      maximumAmount: effect.maximumAmount == null ? "" : String(effect.maximumAmount),
      maximumCurrencyCode: effect.maximumCurrencyCode ?? "MAD",
    })),
  };
}

function positiveDecimal(value: string, allowZero = false) {
  const normalized = value.trim();
  if (!isCommercialAmount(normalized)) return false;
  return allowZero || !/^0+(?:\.0+)?$/.test(normalized);
}

const validCurrency = (value: string) => /^[A-Za-z]{3}$/.test(value.trim());

export function validateDraftEffect(effect: CommercialPolicyDraftEffect, hasEnd: boolean) {
  switch (effect.type) {
    case "FIXED_SUBSCRIPTION_PRICE":
      return positiveDecimal(effect.amount, true) && validCurrency(effect.currencyCode) && effect.billingCycle
        ? null
        : "Indiquez un montant positif ou nul, une devise et un cycle.";
    case "FIXED_DISCOUNT":
      return positiveDecimal(effect.amount) && validCurrency(effect.currencyCode)
        ? null
        : "Indiquez une remise positive et sa devise.";
    case "PERCENTAGE_DISCOUNT": {
      const percentage = effect.percentage.trim();
      return /^\d{1,3}(?:\.\d{1,4})?$/.test(percentage) &&
        Number(percentage) > 0 &&
        Number(percentage) <= 100 &&
        positiveDecimal(effect.maximumAmount) &&
        validCurrency(effect.maximumCurrencyCode)
        ? null
        : "Indiquez un pourcentage entre 0 et 100 et un plafond monétaire positif.";
    }
    case "ALLOW_PRODUCT_SELECTION":
    case "BLOCK_PRODUCT_SELECTION":
      return effect.productType && effect.productRevisionId ? null : "Choisissez un type et une révision de produit.";
    case "ADDITIVE_QUOTA_BONUS":
      return effect.featureCode &&
        effect.quotaResource &&
        Number.isSafeInteger(Number(effect.quantityDelta)) &&
        Number(effect.quantityDelta) > 0
        ? null
        : "Choisissez une fonctionnalité, une ressource et une quantité entière positive.";
    case "GRANT_ADD_ON":
    case "GRANT_QUOTA_PACKAGE":
      if (!hasEnd) return "Une attribution gratuite exige une date de fin explicite.";
      return effect.productRevisionId ? null : "Choisissez la révision à accorder.";
    case "BLOCK_FEATURE":
      return effect.featureCode ? null : "Choisissez la fonctionnalité à bloquer.";
  }
}

export function validateCommercialPolicyDraft(draft: CommercialPolicyDraft): PolicyDraftErrors {
  const errors: PolicyDraftErrors = {};
  if (!draft.name.trim()) errors.name = "Le nom est obligatoire.";
  const from = Date.parse(draft.effectiveFrom);
  const until = draft.effectiveUntil ? Date.parse(draft.effectiveUntil) : null;
  if (!Number.isFinite(from) || (until !== null && (!Number.isFinite(until) || until <= from))) {
    errors.window = "La date de fin doit être postérieure à la date de début.";
  }
  const priority = Number(draft.priority);
  if (!Number.isInteger(priority) || priority < 0 || priority > 1000) {
    errors.priority = "La priorité doit être un entier entre 0 et 1000.";
  }
  if (!draft.reason.trim()) errors.reason = "Le motif métier est obligatoire.";
  if (draft.targetKind === "ACCOUNT" && !draft.accountId) errors.target = "Choisissez un compte.";
  if (draft.targetKind === "ACCOUNT_SET" && !draft.accountIds.length) errors.target = "Choisissez au moins un compte.";
  if (draft.targetKind === "ACCOUNT_SET" && draft.accountIds.length > 1000) {
    errors.target = "Une politique peut cibler au maximum 1 000 comptes explicites.";
  }
  if (draft.targetKind === "PLAN_REVISION_SUBSCRIBERS" && !draft.planRevisionId) {
    errors.target = "Choisissez une révision de forfait.";
  }
  if (draft.targetKind === "SEGMENT" && !draft.segmentReference.trim()) {
    errors.target = "Indiquez une référence de segment.";
  }
  if (draft.targetKind === "SEGMENT" && draft.segmentReference.trim().length > 100) {
    errors.target = "La référence de segment ne peut pas dépasser 100 caractères.";
  }
  if (!draft.effects.length) errors.effects = "Ajoutez au moins un effet.";
  if (draft.effects.length > 50) errors.effects = "Une politique peut contenir au maximum 50 effets.";
  const effectErrors: Record<string, string> = {};
  for (const effect of draft.effects) {
    const error = validateDraftEffect(effect, Boolean(draft.effectiveUntil));
    if (error) effectErrors[effect.key] = error;
  }
  const currencies = new Set(
    draft.effects
      .map((effect) =>
        effect.type === "PERCENTAGE_DISCOUNT"
          ? effect.maximumCurrencyCode.trim().toUpperCase()
          : effect.type === "FIXED_DISCOUNT" || effect.type === "FIXED_SUBSCRIPTION_PRICE"
            ? effect.currencyCode.trim().toUpperCase()
            : "",
      )
      .filter(Boolean),
  );
  if (currencies.size > 1) errors.effects = "Tous les effets monétaires doivent utiliser la même devise.";
  if (Object.keys(effectErrors).length) errors.effectErrors = effectErrors;
  return errors;
}

function normalizedEffect(effect: CommercialPolicyDraftEffect): CommercialPolicyEffectInput {
  const base: CommercialPolicyEffectInput = {
    type: effect.type,
    productType: null,
    productRevisionId: null,
    featureCode: null,
    quotaResource: null,
    quantityDelta: null,
    amount: null,
    currencyCode: null,
    billingCycle: null,
    percentage: null,
    maximumAmount: null,
    maximumCurrencyCode: null,
  };
  if (effect.type === "FIXED_SUBSCRIPTION_PRICE") {
    return {
      ...base,
      amount: effect.amount.trim(),
      currencyCode: effect.currencyCode.trim().toUpperCase(),
      billingCycle: effect.billingCycle || null,
    };
  }
  if (effect.type === "FIXED_DISCOUNT") {
    return { ...base, amount: effect.amount.trim(), currencyCode: effect.currencyCode.trim().toUpperCase() };
  }
  if (effect.type === "PERCENTAGE_DISCOUNT") {
    return {
      ...base,
      percentage: effect.percentage.trim(),
      maximumAmount: effect.maximumAmount.trim(),
      maximumCurrencyCode: effect.maximumCurrencyCode.trim().toUpperCase(),
    };
  }
  if (effect.type === "ALLOW_PRODUCT_SELECTION" || effect.type === "BLOCK_PRODUCT_SELECTION") {
    return { ...base, productType: effect.productType || null, productRevisionId: effect.productRevisionId || null };
  }
  if (effect.type === "ADDITIVE_QUOTA_BONUS") {
    return {
      ...base,
      featureCode: effect.featureCode.trim(),
      quotaResource: effect.quotaResource.trim(),
      quantityDelta: Number(effect.quantityDelta),
    };
  }
  if (effect.type === "GRANT_ADD_ON" || effect.type === "GRANT_QUOTA_PACKAGE") {
    return {
      ...base,
      productType: effect.type === "GRANT_ADD_ON" ? "ADD_ON" : "QUOTA_PACKAGE",
      productRevisionId: effect.productRevisionId || null,
    };
  }
  return { ...base, featureCode: effect.featureCode.trim() };
}

export function toCommercialPolicyWriteInput(draft: CommercialPolicyDraft): CommercialPolicyWriteInput {
  return {
    name: draft.name.trim(),
    description: draft.description.trim() || null,
    effectiveFrom: instantFromLocalValue(draft.effectiveFrom),
    effectiveUntil: draft.effectiveUntil ? instantFromLocalValue(draft.effectiveUntil) : null,
    source: draft.source,
    priority: Number(draft.priority),
    reason: draft.reason.trim(),
    approvalReference: draft.approvalReference.trim() || null,
    contractReference: draft.contractReference.trim() || null,
    target: {
      kind: draft.targetKind,
      accountId: draft.targetKind === "ACCOUNT" ? draft.accountId : null,
      accountIds: draft.targetKind === "ACCOUNT_SET" ? [...new Set(draft.accountIds)] : [],
      planRevisionId: draft.targetKind === "PLAN_REVISION_SUBSCRIBERS" ? draft.planRevisionId : null,
      segmentReference: draft.targetKind === "SEGMENT" ? draft.segmentReference.trim() : null,
    },
    effects: draft.effects.map(normalizedEffect),
  };
}

export function reviewedActivationReady(
  policy: CommercialPolicyDetail,
  preview: CommercialPolicyActivationPreview | null | undefined,
  now = Date.now(),
) {
  return Boolean(
    preview &&
      preview.policyId === policy.summary.id &&
      preview.expectedVersion === policy.summary.version &&
      Boolean(preview.previewToken) &&
      preview.activatable &&
      preview.blockers.length === 0 &&
      Date.parse(preview.expiresAt) > now,
  );
}

export function policyMutationMessage(error: unknown) {
  if (error instanceof ApiError && error.code === "STALE_ACTIVATION_PREVIEW") {
    return "La vérification n’est plus actuelle. Relisez le nouveau résultat avant de confirmer.";
  }
  if (error instanceof ApiError && error.code === "STALE_RESOURCE_VERSION") {
    return "La politique a changé. Rechargez sa nouvelle version avant de réessayer.";
  }
  if (error instanceof ApiError && error.code === "DRAFT_SUCCESSOR_EXISTS") {
    return "Cette lignée possède déjà un brouillon de révision.";
  }
  if (error instanceof ApiError) return "L’opération n’a pas pu être exécutée.";
  if (!(error instanceof Error)) return "L’opération n’a pas pu être exécutée.";
  return error.message || "L’opération n’a pas pu être exécutée.";
}

export function isPolicyVersionConflict(error: unknown) {
  return error instanceof ApiError && error.code === "STALE_RESOURCE_VERSION";
}
