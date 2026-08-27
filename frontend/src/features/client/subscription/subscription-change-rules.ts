import type { SubscriptionChangeInput, SubscriptionChangePreview } from "@/api/contracts";
import { ApiError } from "@/api/http";

export function subscriptionChangeFailureMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return "Le changement n’a pas pu être vérifié. Réessayez.";
  switch (error.code) {
    case "STALE_RESOURCE_VERSION":
      return "L’abonnement ou le catalogue a changé. Les données ont été rechargées ; prévisualisez à nouveau.";
    case "INVALID_STATE":
      return "Cette opération n’est plus possible dans l’état actuel. Rechargez l’abonnement.";
    case "INVALID_REQUEST":
    case "VALIDATION_FAILED":
    case "BUSINESS_RULE_VIOLATED":
      return "La sélection n’est plus applicable. Corrigez-la puis prévisualisez à nouveau.";
    case "FORBIDDEN":
    case "PERMISSION_DENIED":
      return "Votre rôle n’autorise pas cette opération.";
    default:
      return "Le changement n’a pas pu être traité. Réessayez.";
  }
}

type SubscriptionChangeConflict = SubscriptionChangePreview["conflicts"][number];

/** Operational conflict copy is keyed only by the backend's stable code. */
export function subscriptionChangeConflictText(conflict: SubscriptionChangeConflict): string {
  const feature = conflict.featureCode ?? "cette fonctionnalité";
  const resource = conflict.resource ? ` / ${conflict.resource}` : "";
  switch (conflict.code) {
    case "IMPACT_UNKNOWN":
      return `L’impact de la suppression de ${feature} ne peut pas être mesuré. Le changement reste bloqué.`;
    case "FEATURE_IN_USE":
      return conflict.currentUsage === null
        ? `${feature} est encore utilisée.`
        : `${feature} est encore utilisée par ${conflict.currentUsage.toLocaleString("fr-MA")} élément(s) actif(s).`;
    case "QUOTA_USAGE_UNKNOWN":
      return `L’utilisation de ${feature}${resource} ne peut pas être mesurée. La réduction reste bloquée.`;
    case "QUOTA_BELOW_USAGE":
      return `La limite demandée pour ${feature}${resource} est inférieure à l’utilisation actuelle.`;
    default:
      return "Cette sélection a un impact non résolu. Modifiez-la avant de continuer.";
  }
}

/** Stable comparison key for the exact commercial selection covered by signed preview evidence. */
export function subscriptionChangeSelectionKey(selection: SubscriptionChangeInput): string {
  return JSON.stringify({
    targetPlanCode: selection.targetPlanCode,
    addOnCodes: [...new Set(selection.addOnCodes)].sort(),
    quotaPackages: [...selection.quotaPackages]
      .map(({ packageCode, quantity }) => ({ packageCode, quantity }))
      .sort((left, right) => left.packageCode.localeCompare(right.packageCode)),
    timing: selection.timing,
    planPriceSelection: selection.planPriceSelection,
  });
}

export function subscriptionChangePreviewIsCurrent(
  preview: { expiresAt: string; targetPlanCode: string } | null,
  selection: SubscriptionChangeInput | null,
  previewSelectionKey: string | null,
  now = Date.now(),
): boolean {
  if (!preview || !selection || !previewSelectionKey) return false;
  const expiresAt = Date.parse(preview.expiresAt);
  return (
    Number.isFinite(expiresAt) &&
    expiresAt > now &&
    preview.targetPlanCode === selection.targetPlanCode &&
    previewSelectionKey === subscriptionChangeSelectionKey(selection)
  );
}

/**
 * Detects the exact common no-op before asking the server to sign a review. Historical prices
 * without a stable price id are deliberately left to the backend because the client cannot prove
 * their monetary identity from the request alone.
 */
export function subscriptionChangeSelectionMatchesCurrent(
  selection: SubscriptionChangeInput | null,
  current: {
    planCode: string;
    planPriceEntryId: string | null;
    addOnCodes: readonly string[];
    quotaPackages: ReadonlyArray<{ packageCode: string; quantity: number }>;
  } | null,
) {
  if (!selection || !current?.planPriceEntryId) return false;
  if (
    selection.targetPlanCode !== current.planCode ||
    selection.planPriceSelection.priceEntryId !== current.planPriceEntryId
  ) {
    return false;
  }
  const currentAddOns = new Set(current.addOnCodes);
  if (
    selection.addOnCodes.length !== currentAddOns.size ||
    !selection.addOnCodes.every((code) => currentAddOns.has(code))
  ) {
    return false;
  }
  const currentPackages = new Map(current.quotaPackages.map((item) => [item.packageCode, item.quantity]));
  return (
    selection.quotaPackages.length === currentPackages.size &&
    selection.quotaPackages.every((item) => currentPackages.get(item.packageCode) === item.quantity)
  );
}
