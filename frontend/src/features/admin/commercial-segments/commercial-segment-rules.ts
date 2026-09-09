import type {
  BillingCycle,
  CommercialSegmentAction,
  CommercialSegmentBlocker,
  CommercialSegmentDetail,
  CommercialSegmentKind,
  CommercialSegmentPreview,
  CommercialSegmentProductHolding,
  CommercialSegmentProductType,
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

export const segmentActionLabel: Record<CommercialSegmentAction, string> = {
  EDIT_DRAFT: "Modifier le brouillon",
  DUPLICATE: "Dupliquer",
  COUNT: "Compter l’audience",
  PREVIEW: "Vérifier l’audience",
  READ_SAMPLE_IDENTITIES: "Lire l’échantillon identifié",
  ACTIVATE: "Activer",
  REVISE: "Créer une révision",
  COMPARE: "Comparer",
  ARCHIVE: "Archiver",
  DELETE_DRAFT: "Supprimer le brouillon",
  READ_HISTORY: "Lire l’historique",
  READ_REVISIONS: "Lire les révisions",
  READ_ACTIVATIONS: "Lire les activations",
  READ_ACTIVATION_AUDIENCE: "Lire l’audience figée",
  READ_ACTIVATION_IDENTITIES: "Lire les identités de l’audience",
  READ_OWNER: "Lire le responsable",
  REASSIGN_OWNER: "Réassigner le responsable",
};

const segmentHistoryActionLabel: Record<string, string> = {
  CREATE: "Brouillon créé",
  UPDATE: "Brouillon modifié",
  UPDATE_DRAFT: "Brouillon modifié",
  DUPLICATE: "Copie indépendante créée",
  REVISE: "Révision créée",
  ACTIVATE: "Segment activé",
  ARCHIVE: "Segment archivé",
  DELETE_DRAFT: "Brouillon supprimé",
  REASSIGN_OWNER: "Responsable réassigné",
};

export function segmentHistoryAction(action: string) {
  const key = (action.split(".").at(-1) ?? action).replaceAll("-", "_").toUpperCase();
  return (
    segmentHistoryActionLabel[key] ??
    key
      .toLowerCase()
      .replaceAll("_", " ")
      .replace(/^./, (value) => value.toUpperCase())
  );
}

export const subscriptionStatusLabel: Record<SubscriptionStatus, string> = {
  TRIALING: "Essai",
  ACTIVE: "Actif",
  PAST_DUE: "Paiement en retard",
  SUSPENDED: "Suspendu",
  CANCELLED: "Annulé",
  EXPIRED: "Expiré",
};

/** Segment criteria describe the current subscription, never terminal history. */
export const segmentCurrentSubscriptionStatuses = [
  "TRIALING",
  "ACTIVE",
  "PAST_DUE",
  "SUSPENDED",
] as const satisfies readonly SubscriptionStatus[];

export const billingCycleLabel: Record<BillingCycle, string> = {
  MONTHLY: "Mensuel",
  YEARLY: "Annuel",
  FOREVER: "Perpétuel historique",
};

export type CommercialSegmentDraft = {
  name: string;
  description: string;
  kind: CommercialSegmentKind;
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
  Record<"name" | "reason" | "audience" | "statuses" | "createdWindow" | "currencies" | "products", string>
>;

const earliestAccountDate = Date.parse("2000-01-01T00:00:00Z");
const maximumAccountDateRange = 366 * 20 * 24 * 60 * 60 * 1000;
const oneDay = 24 * 60 * 60 * 1000;

export function emptyCommercialSegmentDraft(): CommercialSegmentDraft {
  return {
    name: "",
    description: "",
    kind: "EXPLICIT_ACCOUNTS",
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

export function validateCommercialSegmentDraft(
  draft: CommercialSegmentDraft,
  now = Date.now(),
): CommercialSegmentDraftErrors {
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
    if (
      draft.subscriptionStatuses.some(
        (status) =>
          !segmentCurrentSubscriptionStatuses.includes(status as (typeof segmentCurrentSubscriptionStatuses)[number]),
      )
    ) {
      errors.statuses = "Un segment courant ne peut pas cibler un abonnement annulé ou expiré.";
    }
  }
  if (draft.kind === "TYPED_CRITERIA") {
    const from = draft.accountCreatedFrom ? new Date(draft.accountCreatedFrom).getTime() : null;
    const until = draft.accountCreatedUntil ? new Date(draft.accountCreatedUntil).getTime() : null;
    if (
      (from !== null && (!Number.isFinite(from) || from < earliestAccountDate || from > now + oneDay)) ||
      (until !== null && (!Number.isFinite(until) || until < earliestAccountDate || until > now + oneDay))
    ) {
      errors.createdWindow = "Les dates de création doivent être comprises entre 2000 et demain.";
    } else if (from !== null && until !== null) {
      if (from >= until) errors.createdWindow = "La date de fin doit suivre la date de début.";
      else if (until - from > maximumAccountDateRange) {
        errors.createdWindow = "La période de création ne peut pas dépasser 20 ans.";
      }
    }
  }
  return errors;
}

export function hasDraftErrors(errors: CommercialSegmentDraftErrors) {
  return Object.values(errors).some(Boolean);
}

export function reviewedSegmentActivationReady(
  segment: Pick<CommercialSegmentDetail, "summary">,
  preview:
    | Pick<
        CommercialSegmentPreview,
        "segmentId" | "criteriaVersion" | "expiresAt" | "previewToken" | "activatable" | "blockers"
      >
    | null
    | undefined,
  now = Date.now(),
) {
  if (!preview) return false;
  const expiresAt = Date.parse(preview.expiresAt);
  return (
    preview.activatable &&
    preview.blockers.length === 0 &&
    Boolean(preview.previewToken.trim()) &&
    preview.segmentId === segment.summary.id &&
    preview.criteriaVersion === segment.summary.version &&
    Number.isFinite(expiresAt) &&
    expiresAt > now
  );
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
