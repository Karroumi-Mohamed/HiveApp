import type { SubscriptionChangeInput, SubscriptionChangePreview } from "@/api/contracts";
import { ApiError } from "@/api/http";

export function subscriptionChangeFailureMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return "Le changement n’a pas pu être vérifié. Réessayez.";
  if (error.status === 409) return `${error.message} Les tarifs ont été rechargés ; prévisualisez à nouveau.`;
  return error.message;
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
  preview: SubscriptionChangePreview | null,
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
