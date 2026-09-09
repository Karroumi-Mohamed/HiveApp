import type { SortingState } from "@tanstack/react-table";
import type {
  ProductPrice,
  ProductPriceAction,
  ProductPriceActivationPreview,
  ProductPriceBillingCycle,
  ProductPriceBlocker,
  ProductPriceOwnerType,
  ProductPriceReplacementBlocker,
  ProductPriceStatus,
} from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { isCommercialAmount } from "@/lib/exact-decimal";

export const productPriceStatus: Record<
  ProductPriceStatus,
  { label: string; tone: "info" | "success" | "warning" | "neutral" }
> = {
  DRAFT: { label: "Brouillon", tone: "info" },
  ACTIVE: { label: "En vente", tone: "success" },
  INACTIVE: { label: "Vente suspendue", tone: "warning" },
  ARCHIVED: { label: "Archivé", tone: "neutral" },
};

export const productPriceOwner: Record<ProductPriceOwnerType, string> = {
  PLAN: "Forfait",
  ADD_ON: "Add-on",
  QUOTA_PACKAGE: "Pack de capacité",
};

export function productPriceOwnerReadPermission(ownerType: ProductPriceOwnerType): string {
  if (ownerType === "PLAN") return adminPermissions.plansReadDetail;
  if (ownerType === "ADD_ON") return adminPermissions.addOnsReadDetail;
  return adminPermissions.quotaPackagesReadDetail;
}

export const productPriceCycle: Record<ProductPriceBillingCycle, string> = {
  MONTHLY: "Mensuel",
  YEARLY: "Annuel",
};

export const productPriceBlocker: Record<ProductPriceBlocker, string> = {
  OWNER_NOT_ACTIVE: "Le produit doit être actif avant que ce tarif puisse être mis en vente.",
  EFFECTIVE_WINDOW_EXPIRED: "La période de validité de ce tarif est terminée.",
  ACTIVE_WINDOW_OVERLAP: "Un tarif actif couvre déjà cette devise, ce cycle et cette période.",
  SUCCESSOR_ALREADY_EXISTS: "Une révision plus récente existe déjà.",
  WRONG_LIFECYCLE_STATE: "L’état actuel ne permet pas cette opération.",
  ACTIVE_MUST_BE_PAUSED: "Pour archiver ce tarif, suspendez d’abord sa vente.",
  ARCHIVED_TERMINAL: "Un tarif archivé est définitif.",
};

/** Lifecycle prerequisites belong beside their action, not above a healthy price. */
export function productPriceAvailabilityWarnings(price: Pick<ProductPrice, "status" | "blockers">) {
  if (price.status === "ARCHIVED") return [];
  return price.blockers.filter((blocker) =>
    ["OWNER_NOT_ACTIVE", "EFFECTIVE_WINDOW_EXPIRED", "ACTIVE_WINDOW_OVERLAP"].includes(blocker),
  );
}

export const productPriceReplacementBlocker: Record<ProductPriceReplacementBlocker, string> = {
  CURRENT_NOT_ACTIVE: "Le tarif actuel n’est plus en vente.",
  SUCCESSOR_NOT_DRAFT: "La nouvelle révision doit encore être un brouillon.",
  SUCCESSOR_NOT_DIRECT_REVISION: "Le brouillon doit provenir directement du tarif qu’il remplace.",
  COMMERCIAL_TUPLE_MISMATCH: "La devise et le cycle doivent rester identiques pour un remplacement planifié.",
  CUTOFF_NOT_FUTURE: "Le début du nouveau tarif doit être dans le futur.",
  CURRENT_DOES_NOT_COVER_CUTOFF: "Le tarif actuel ne couvre pas la date de remplacement choisie.",
  OWNER_NOT_ACTIVE: "Le produit lié doit être actif.",
  OTHER_ACTIVE_WINDOW_OVERLAP: "Un autre tarif actif chevauche la période du nouveau tarif.",
};

export function isProductPriceReplacementDraft(price: Pick<ProductPrice, "status" | "sourcePriceId">) {
  return price.status === "DRAFT" && price.sourcePriceId !== null;
}

const actionPermission: Record<ProductPriceAction, string> = {
  EDIT_DRAFT: adminPermissions.priceBooksUpdateDraft,
  PREVIEW_ACTIVATION: adminPermissions.priceBooksPreviewActivation,
  ACTIVATE: adminPermissions.priceBooksActivate,
  PAUSE: adminPermissions.priceBooksPause,
  REACTIVATE: adminPermissions.priceBooksReactivate,
  REVISE: adminPermissions.priceBooksRevise,
  ARCHIVE: adminPermissions.priceBooksArchive,
  DELETE_DRAFT: adminPermissions.priceBooksDeleteDraft,
};

export function canUseProductPriceAction(
  price: Pick<ProductPrice, "availableActions">,
  action: ProductPriceAction,
  can: (permission: string) => boolean,
) {
  return price.availableActions.includes(action) && can(actionPermission[action]);
}

export function productPriceActivationReady(
  price: Pick<ProductPrice, "id" | "version">,
  preview: ProductPriceActivationPreview | undefined,
  now = Date.now(),
) {
  const expiresAt = preview ? Date.parse(preview.expiresAt) : Number.NaN;
  return Boolean(
    preview?.activatable &&
      preview.previewToken &&
      preview.priceEntryId === price.id &&
      preview.expectedVersion === price.version &&
      Number.isFinite(expiresAt) &&
      expiresAt > now,
  );
}

export type ProductPriceActivationReviewState = Readonly<{
  data: ProductPriceActivationPreview | undefined;
  isFetching: boolean;
  isError: boolean;
}>;

/** Retained query data is not reviewed evidence while it is refreshing or after refresh failed. */
export function productPriceActivationReviewReady(
  price: Pick<ProductPrice, "id" | "version">,
  review: ProductPriceActivationReviewState,
  now = Date.now(),
) {
  return !review.isFetching && !review.isError && productPriceActivationReady(price, review.data, now);
}

export function productPriceActionReason(
  price: Pick<ProductPrice, "availableActions" | "blockers">,
  action: ProductPriceAction,
  can: (permission: string) => boolean,
) {
  if (!can(actionPermission[action])) return "Votre rôle n’autorise pas cette opération.";
  if (!price.availableActions.includes(action)) {
    const relevant = price.blockers.find((blocker) => {
      if (blocker === "ARCHIVED_TERMINAL") return true;
      if (blocker === "ACTIVE_MUST_BE_PAUSED") return action === "ARCHIVE";
      if (blocker === "SUCCESSOR_ALREADY_EXISTS") return action === "REVISE";
      return action === "ACTIVATE" || action === "REACTIVATE" || action === "PREVIEW_ACTIVATION";
    });
    return relevant ? productPriceBlocker[relevant] : "Cette opération n’est pas disponible dans cet état.";
  }
  return undefined;
}

export type ProductPriceDraftFields = Readonly<{
  amount: string;
  currencyCode: string;
  billingCycle: ProductPriceBillingCycle;
  effectiveFrom: string;
  effectiveUntil: string;
}>;

export type ProductPriceDraftErrors = Partial<Record<keyof ProductPriceDraftFields, string>>;

export function validateProductPriceDraft(fields: ProductPriceDraftFields): ProductPriceDraftErrors {
  const errors: ProductPriceDraftErrors = {};
  if (!isCommercialAmount(fields.amount)) {
    errors.amount = "Utilisez au maximum 15 chiffres et 4 décimales.";
  }
  if (!/^[A-Za-z]{3}$/.test(fields.currencyCode.trim())) {
    errors.currencyCode = "Saisissez un code devise ISO à 3 lettres.";
  }
  const from = Date.parse(fields.effectiveFrom);
  if (!fields.effectiveFrom || Number.isNaN(from)) errors.effectiveFrom = "Choisissez le début de validité.";
  if (fields.effectiveUntil) {
    const until = Date.parse(fields.effectiveUntil);
    if (Number.isNaN(until)) errors.effectiveUntil = "Choisissez une date de fin valide.";
    else if (!Number.isNaN(from) && until <= from) errors.effectiveUntil = "La fin doit être postérieure au début.";
  }
  return errors;
}

export function resolveSortingUpdate(
  update: SortingState | ((current: SortingState) => SortingState),
  current: SortingState,
): SortingState {
  return typeof update === "function" ? update(current) : update;
}

const historyAction: Record<string, string> = {
  CREATE: "Brouillon créé",
  CREATE_DRAFT: "Brouillon créé",
  UPDATE: "Conditions modifiées",
  UPDATE_DRAFT: "Conditions modifiées",
  ACTIVATE: "Tarif mis en vente",
  PAUSE: "Vente suspendue",
  REACTIVATE: "Tarif remis en vente",
  REVISE: "Révision créée",
  SCHEDULE_REPLACEMENT: "Remplacement programmé",
  ARCHIVE: "Tarif archivé",
  DELETE: "Brouillon supprimé",
  DELETE_DRAFT: "Brouillon supprimé",
};

export function productPriceHistoryLabel(action: string): string {
  const key = (action.split(".").at(-1) ?? action).replaceAll("-", "_").toUpperCase();
  return historyAction[key] ?? key.replaceAll("_", " ").toLocaleLowerCase("fr");
}

export function localDateTimeValue(value?: string | null) {
  if (!value) return "";
  const date = new Date(value);
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

export function instantFromLocalValue(value: string) {
  return new Date(value).toISOString();
}
