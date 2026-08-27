import type {
  BillingCycle,
  CommercialSegmentAction,
  CommercialSegmentBlocker,
  CommercialSegmentDetail,
  CommercialSegmentKind,
  CommercialSegmentProductHolding,
  CommercialSegmentProductType,
  CommercialSegmentSource,
  CommercialSegmentStatus,
  CommercialSegmentWriteInput,
  SubscriptionStatus,
} from "@/api/contracts";
import { ApiError } from "@/api/http";

export const segmentStatus: Record<CommercialSegmentStatus, { label: string; tone: "neutral" | "info" | "success" }> = {
  DRAFT: { label: "Brouillon", tone: "info" },
  ACTIVE: { label: "Actif", tone: "success" },
  ARCHIVED: { label: "Archivé", tone: "neutral" },
};

export const segmentKind: Record<CommercialSegmentKind, string> = {
  EXPLICIT_ACCOUNTS: "Sélection de comptes",
  TYPED_CRITERIA: "Critères dynamiques",
};

export const segmentSource: Record<CommercialSegmentSource, string> = {
  MANUAL: "Manuelle",
  IMPORTED: "Importée",
  SUPPORT: "Support",
};

export const segmentProductType: Record<CommercialSegmentProductType, string> = {
  PLAN: "Forfait",
  ADD_ON: "Add-on",
  QUOTA_PACKAGE: "Pack de capacité",
};

export const segmentBlocker: Record<CommercialSegmentBlocker, string> = {
  EMPTY_AUDIENCE: "Aucun compte ne correspond à cette définition.",
  AUDIENCE_EXCEEDS_ACTIVATION_LIMIT: "L’audience dépasse la limite de sécurité d’une activation.",
  NOT_DRAFT: "Cette opération exige un brouillon.",
  NOT_ACTIVE: "Cette opération exige une révision active.",
  ALREADY_ARCHIVED: "Cette révision est déjà archivée.",
  NOT_LATEST_REVISION: "Une révision plus récente existe déjà.",
  HAS_POLICY_REFERENCES: "Des politiques commerciales utilisent encore ce segment.",
  HAS_ACTIVATION_HISTORY: "Une activation historique empêche la suppression physique.",
  ACTIVE_SUCCESSOR_EXISTS: "Une révision active plus récente existe déjà.",
};

export const subscriptionStatusLabel: Record<SubscriptionStatus, string> = {
  TRIALING: "Essai",
  ACTIVE: "Actif",
  PAST_DUE: "Paiement en retard",
  SUSPENDED: "Suspendu",
  CANCELLED: "Annulé",
  EXPIRED: "Expiré",
};

export const billingCycleLabel: Record<BillingCycle, string> = {
  MONTHLY: "Mensuel",
  YEARLY: "Annuel",
  FOREVER: "Perpétuel historique",
};

export type CommercialSegmentDraft = {
  name: string;
  description: string;
  kind: CommercialSegmentKind;
  source: CommercialSegmentSource;
  reason: string;
  explicitAccountIds: string[];
  currentPlanRevisionIds: string[];
  subscriptionStatuses: SubscriptionStatus[];
  currencyCodes: string[];
  billingCycles: BillingCycle[];
  accountCreatedFrom: string;
  accountCreatedUntil: string;
  productHoldings: CommercialSegmentProductHolding[];
};

export type CommercialSegmentDraftErrors = Partial<
  Record<"name" | "reason" | "audience" | "createdWindow" | "currencies" | "products", string>
>;

export function emptyCommercialSegmentDraft(): CommercialSegmentDraft {
  return {
    name: "",
    description: "",
    kind: "EXPLICIT_ACCOUNTS",
    source: "MANUAL",
    reason: "",
    explicitAccountIds: [],
    currentPlanRevisionIds: [],
    subscriptionStatuses: [],
    currencyCodes: [],
    billingCycles: [],
    accountCreatedFrom: "",
    accountCreatedUntil: "",
    productHoldings: [],
  };
}

function localDate(value: string | null) {
  if (!value) return "";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "";
  const offset = parsed.getTimezoneOffset() * 60_000;
  return new Date(parsed.getTime() - offset).toISOString().slice(0, 16);
}

export function draftFromCommercialSegment(segment: CommercialSegmentDetail): CommercialSegmentDraft {
  const criteria = segment.definition.criteria;
  return {
    name: segment.summary.name,
    description: segment.description ?? "",
    kind: segment.summary.kind,
    source: segment.summary.source,
    reason: segment.reason,
    explicitAccountIds: [...segment.definition.explicitAccountIds],
    currentPlanRevisionIds: [...(criteria?.currentPlanRevisionIds ?? [])],
    subscriptionStatuses: [...(criteria?.subscriptionStatuses ?? [])],
    currencyCodes: [...(criteria?.currencyCodes ?? [])],
    billingCycles: [...(criteria?.billingCycles ?? [])],
    accountCreatedFrom: localDate(criteria?.accountCreatedFrom ?? null),
    accountCreatedUntil: localDate(criteria?.accountCreatedUntil ?? null),
    productHoldings: [...(criteria?.productHoldings ?? [])],
  };
}

function instant(value: string) {
  return value ? new Date(value).toISOString() : null;
}

function unique<T>(values: T[]) {
  return [...new Set(values)];
}

export function toCommercialSegmentWriteInput(draft: CommercialSegmentDraft): CommercialSegmentWriteInput {
  const typed = draft.kind === "TYPED_CRITERIA";
  return {
    name: draft.name.trim(),
    description: draft.description.trim() || null,
    kind: draft.kind,
    source: draft.source,
    reason: draft.reason.trim(),
    definition: {
      explicitAccountIds: typed ? [] : unique(draft.explicitAccountIds),
      criteria: typed
        ? {
            currentPlanRevisionIds: unique(draft.currentPlanRevisionIds),
            subscriptionStatuses: unique(draft.subscriptionStatuses),
            currencyCodes: unique(draft.currencyCodes.map((value) => value.trim().toUpperCase()).filter(Boolean)),
            billingCycles: unique(draft.billingCycles),
            accountCreatedFrom: instant(draft.accountCreatedFrom),
            accountCreatedUntil: instant(draft.accountCreatedUntil),
            productHoldings: uniqueHoldings(draft.productHoldings),
          }
        : null,
    },
  };
}

function uniqueHoldings(values: CommercialSegmentProductHolding[]) {
  const byKey = new Map<string, CommercialSegmentProductHolding>();
  for (const value of values) {
    const code = value.code.trim();
    if (code) byKey.set(`${value.type}:${code}`, { ...value, code });
  }
  return [...byKey.values()];
}

export function validateCommercialSegmentDraft(draft: CommercialSegmentDraft): CommercialSegmentDraftErrors {
  const errors: CommercialSegmentDraftErrors = {};
  if (!draft.name.trim()) errors.name = "Le nom est obligatoire.";
  if (!draft.reason.trim()) errors.reason = "Le motif métier est obligatoire.";
  if (draft.kind === "EXPLICIT_ACCOUNTS" && !draft.explicitAccountIds.length) {
    errors.audience = "Sélectionnez au moins un compte.";
  }
  if (draft.kind === "TYPED_CRITERIA") {
    const hasCriteria =
      draft.currentPlanRevisionIds.length > 0 ||
      draft.subscriptionStatuses.length > 0 ||
      draft.currencyCodes.length > 0 ||
      draft.billingCycles.length > 0 ||
      Boolean(draft.accountCreatedFrom) ||
      Boolean(draft.accountCreatedUntil) ||
      draft.productHoldings.length > 0;
    if (!hasCriteria) errors.audience = "Ajoutez au moins un critère fermé.";
    if (draft.currencyCodes.some((value) => !/^[A-Za-z]{3}$/.test(value.trim()))) {
      errors.currencies = "Chaque devise doit utiliser exactement trois lettres.";
    }
    if (draft.productHoldings.some((holding) => !holding.code.trim())) {
      errors.products = "Chaque produit sélectionné doit avoir un code.";
    }
  }
  if (draft.accountCreatedFrom && draft.accountCreatedUntil) {
    const from = new Date(draft.accountCreatedFrom);
    const until = new Date(draft.accountCreatedUntil);
    if (from >= until) errors.createdWindow = "La date de fin doit suivre la date de début.";
  }
  return errors;
}

export function hasDraftErrors(errors: CommercialSegmentDraftErrors) {
  return Object.values(errors).some(Boolean);
}

export function actionBlockers(
  segment: Pick<CommercialSegmentDetail["summary"], "blockedActions">,
  action: CommercialSegmentAction,
) {
  return segment.blockedActions[action] ?? [];
}

export function segmentMutationMessage(error: unknown) {
  if (error instanceof ApiError && error.code === "STALE_ACTIVATION_PREVIEW") {
    return "La vérification a expiré ou la définition a changé. Vérifiez de nouveau l’audience.";
  }
  if (error instanceof ApiError && error.code === "STALE_RESOURCE_VERSION") {
    return "Le segment a changé. Rechargez sa nouvelle version avant de réessayer.";
  }
  if (error instanceof ApiError && error.code === "DRAFT_SUCCESSOR_EXISTS") {
    return "Cette lignée possède déjà un brouillon de révision.";
  }
  if (error instanceof ApiError) return "L’opération n’a pas pu être exécutée.";
  return error instanceof Error && error.message ? error.message : "L’opération n’a pas pu être exécutée.";
}

export function validSegmentId(value: string | null | undefined) {
  return value && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value) ? value : "";
}
