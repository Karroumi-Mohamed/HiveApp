import type {
  SubscriptionChangeJobItemStatus,
  SubscriptionChangeJobStatus,
  SubscriptionChangeJobSummary,
} from "@/api/contracts";
import type { StatusTone } from "@/components/patterns/status-badge";

export const changeJobStatusPresentation: Record<SubscriptionChangeJobStatus, { label: string; tone: StatusTone }> = {
  PREVIEWED: { label: "À confirmer", tone: "info" },
  QUEUED: { label: "En file", tone: "info" },
  SCHEDULED: { label: "Planifié", tone: "neutral" },
  RUNNING: { label: "En cours", tone: "warning" },
  COMPLETED: { label: "Terminé", tone: "success" },
  COMPLETED_WITH_ERRORS: { label: "Terminé avec erreurs", tone: "danger" },
  CANCELLED: { label: "Annulé", tone: "neutral" },
};

export const changeJobResultStatusPresentation: Record<
  SubscriptionChangeJobItemStatus,
  { label: string; tone: StatusTone }
> = {
  READY: { label: "Prêt", tone: "info" },
  APPLIED: { label: "Appliqué", tone: "success" },
  PENDING_RENEWAL: { label: "Au renouvellement", tone: "info" },
  AWAITING_PAYMENT: { label: "Paiement requis", tone: "warning" },
  CONFLICT: { label: "Conflit", tone: "danger" },
  FAILED: { label: "Échec", tone: "danger" },
  CANCELLED: { label: "Annulé", tone: "neutral" },
};

export function changeJobCanBeCancelled(job: Pick<SubscriptionChangeJobSummary, "status">) {
  return job.status === "PREVIEWED" || job.status === "QUEUED" || job.status === "SCHEDULED";
}

export function changeJobCanBeRetried(
  job: Pick<SubscriptionChangeJobSummary, "status" | "failedCount" | "conflictCount">,
) {
  return job.status === "COMPLETED_WITH_ERRORS" && job.failedCount + job.conflictCount > 0;
}

export function changeJobProcessedCount(
  job: Pick<
    SubscriptionChangeJobSummary,
    "appliedCount" | "pendingCount" | "awaitingPaymentCount" | "conflictCount" | "failedCount" | "cancelledCount"
  >,
) {
  return (
    job.appliedCount +
    job.pendingCount +
    job.awaitingPaymentCount +
    job.conflictCount +
    job.failedCount +
    job.cancelledCount
  );
}

export function subscriptionChangeJobReasonError(reason: string) {
  const normalized = reason.trim();
  if (!normalized) return "La justification est obligatoire.";
  if (normalized.length > 2000) return "La justification ne peut pas dépasser 2 000 caractères.";
  return null;
}
