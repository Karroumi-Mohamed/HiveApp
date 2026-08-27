import type { SubscriptionChangeOperation } from "@/api/contracts";
import { ApiError } from "@/api/http";

export function normalizedOperatorReason(value: string): string {
  return value.trim();
}

export function operatorReasonError(value: string): string | null {
  const normalized = normalizedOperatorReason(value);
  if (!normalized) return "Saisissez la justification de cette opération.";
  if (normalized.length > 2000) return "La justification ne peut pas dépasser 2 000 caractères.";
  return null;
}

export function subscriptionOperationCanBeCancelled(operation: Pick<SubscriptionChangeOperation, "status">) {
  return operation.status === "PENDING" || operation.status === "AWAITING_CONFIRMATION";
}

export function adminChangeNeedsFreshReview(error: unknown): boolean {
  return Boolean(
    error &&
      typeof error === "object" &&
      "code" in error &&
      (error as { code?: string }).code === "STALE_RESOURCE_VERSION",
  );
}

export function operatorSubscriptionMutationFailureMessage(error: unknown, fallback: string) {
  if (!(error instanceof ApiError)) return fallback;
  switch (error.code) {
    case "FORBIDDEN":
    case "PERMISSION_DENIED":
      return "Votre rôle n’autorise pas cette opération.";
    case "INVALID_REQUEST":
    case "VALIDATION_FAILED":
    case "BUSINESS_RULE_VIOLATED":
      return "Les données ne sont pas applicables. Corrigez la sélection puis réessayez.";
    case "INVALID_STATE":
    case "STALE_RESOURCE_VERSION":
      return "Les données ont changé. Rechargez la page puis réessayez.";
    default:
      return fallback;
  }
}

export const subscriptionOperationOriginLabel = {
  CLIENT: "Portail client",
  PLATFORM_ADMIN: "Administration plateforme",
  SYSTEM: "Système",
} as const;
