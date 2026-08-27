import type { SubscriptionChangeOperation, SubscriptionStatus } from "@/api/contracts";
import type { StatusTone } from "@/components/patterns/status-badge";

type StatusPresentation = { label: string; tone: StatusTone };

/** Shared French vocabulary for subscription state across both portals. */
export const subscriptionStatusPresentation: Record<SubscriptionStatus, StatusPresentation> = {
  ACTIVE: { label: "Actif", tone: "success" },
  TRIALING: { label: "Essai", tone: "info" },
  PAST_DUE: { label: "Impayé", tone: "danger" },
  SUSPENDED: { label: "Suspendu", tone: "warning" },
  CANCELLED: { label: "Annulé", tone: "neutral" },
  EXPIRED: { label: "Expiré", tone: "neutral" },
};

export const subscriptionChangeStatusPresentation: Record<SubscriptionChangeOperation["status"], StatusPresentation> = {
  AWAITING_CONFIRMATION: { label: "Paiement à confirmer", tone: "warning" },
  PENDING: { label: "Planifié", tone: "info" },
  APPLIED: { label: "Appliqué", tone: "success" },
  NEEDS_ATTENTION: { label: "Intervention requise", tone: "danger" },
  CANCELLED: { label: "Annulé", tone: "neutral" },
};
