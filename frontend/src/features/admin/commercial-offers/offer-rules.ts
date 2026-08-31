import type {
  OfferAcceptance,
  OfferAction,
  OfferBlocker,
  OfferDiscovery,
  OfferEligibilityBlocker,
  OfferStatus,
} from "@/api/offer-contracts";

export const offerStatus: Record<OfferStatus, { label: string; tone: "neutral" | "info" | "success" | "warning" }> = {
  DRAFT: { label: "Brouillon", tone: "info" },
  PUBLISHED: { label: "Publiée", tone: "success" },
  RETIRED: { label: "Retirée", tone: "warning" },
  ARCHIVED: { label: "Archivée", tone: "neutral" },
};

export const offerDiscovery: Record<OfferDiscovery, string> = {
  CATALOG: "Catalogue",
  CODE_ONLY: "Code privé",
};

export const offerAcceptance: Record<OfferAcceptance, string> = {
  CLIENT_OR_OPERATOR: "Client ou opérateur",
  OPERATOR_ONLY: "Opérateur uniquement",
};

export const offerBlocker: Record<OfferBlocker, string> = {
  NOT_DRAFT: "Cette opération exige un brouillon.",
  NOT_PUBLISHED: "Cette opération exige une offre publiée.",
  NOT_RETIRED: "Cette opération exige une offre retirée.",
  NOT_LATEST_REVISION: "Une révision plus récente existe déjà.",
  DRAFT_SUCCESSOR_EXISTS: "Un brouillon successeur existe déjà.",
  PUBLISHED_SUCCESSOR_EXISTS: "Une révision publiée plus récente existe déjà.",
  HAS_DERIVED_OFFERS: "Une autre offre a été créée depuis cette offre.",
  CAMPAIGN_NOT_LIVE: "La campagne liée n’est pas exploitable.",
  WINDOW_ENDED: "La période de l’offre est terminée.",
  WINDOW_OUTSIDE_CAMPAIGN: "La période dépasse celle de la campagne.",
  CODE_REQUIRED: "Un code client est obligatoire.",
  INVALID_SELECTION: "La sélection commerciale n’est plus valide.",
  INVALID_LIFECYCLE_STATE: "L’état actuel ne permet pas cette opération.",
  CAMPAIGN_NOT_ACTIVE: "La campagne n’est pas active.",
  WINDOW_NOT_STARTED: "La période de l’offre n’a pas commencé.",
};

export const offerEligibilityBlocker: Record<OfferEligibilityBlocker, string> = {
  ACCOUNT_INACTIVE: "Le compte est inactif.",
  OFFER_NOT_PUBLISHED: "L’offre n’est pas publiée.",
  OFFER_WINDOW_NOT_STARTED: "L’offre n’a pas encore commencé.",
  OFFER_WINDOW_ENDED: "L’offre est terminée.",
  CAMPAIGN_NOT_ACTIVE: "La campagne n’est pas active.",
  ACCOUNT_OUTSIDE_AUDIENCE: "Le compte ne fait pas partie de l’audience.",
  NO_ACTIVE_SUBSCRIPTION: "Le compte n’a pas d’abonnement actif.",
  GLOBAL_CAPACITY_EXHAUSTED: "La capacité globale est épuisée.",
  ACCOUNT_CAPACITY_EXHAUSTED: "La limite de ce compte est atteinte.",
  OUTSTANDING_SUBSCRIPTION_OPERATION: "Une autre modification d’abonnement est en cours.",
  SELECTION_UNAVAILABLE: "Un produit ou un prix sélectionné n’est plus disponible.",
  POLICY_CONFLICT: "Une règle commerciale empêche l’application.",
  IMMEDIATE_CHANGE_CONFLICT: "Le changement immédiat entre en conflit avec l’abonnement actuel.",
  PAID_CHECKOUT_UNAVAILABLE: "Le paiement automatisé n’est pas encore disponible.",
  NO_CHANGE: "L’offre ne modifierait pas cet abonnement.",
};

export const offerActionPermission: Record<OfferAction, string> = {
  READ_DEFINITION: "platform.offers.read",
  UPDATE: "platform.offers.update",
  DUPLICATE: "platform.offers.duplicate",
  REVISE: "platform.offers.revise",
  REVISIONS: "platform.offers.revisions",
  COMPARE: "platform.offers.compare",
  HISTORY: "platform.offers.history",
  PREVIEW_PUBLICATION: "platform.offers.preview_publish",
  PUBLISH: "platform.offers.publish",
  RETIRE: "platform.offers.retire",
  RESTORE: "platform.offers.restore",
  ARCHIVE: "platform.offers.archive",
  DELETE_DRAFT: "platform.offers.delete",
  OWNER: "platform.offers.read_owner",
  REASSIGN_OWNER: "platform.offers.reassign_owner",
  READ_STATS: "platform.offers.read_stats",
  READ_REDEMPTIONS: "platform.offers.read_redemptions",
  READ_REDEMPTION_DETAIL: "platform.offers.read_redemption_detail",
  READ_REDEMPTION_IDENTITIES: "platform.offers.read_redemption_identities",
  PREVIEW_FOR_ACCOUNT: "platform.offers.preview_for_account",
  APPLY_FOR_ACCOUNT: "platform.offers.apply_for_account",
};

export function canRunOfferAction(
  action: OfferAction,
  state: { availableActions: OfferAction[] },
  can: (permission: string) => boolean,
) {
  return state.availableActions.includes(action) && can(offerActionPermission[action]);
}

export function offerActionReason(
  action: OfferAction,
  state: { blockedActions: Partial<Record<OfferAction, OfferBlocker[]>> },
) {
  return state.blockedActions[action]?.map((blocker) => offerBlocker[blocker]).join(" ") || null;
}

export function offerPeriodLabel(startsAt: string, endsAt: string, now = Date.now()) {
  const start = Date.parse(startsAt);
  const end = Date.parse(endsAt);
  if (now < start) return "À venir";
  if (now >= end) return "Terminée";
  return "En cours";
}

export function newIdempotencyKey() {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}
