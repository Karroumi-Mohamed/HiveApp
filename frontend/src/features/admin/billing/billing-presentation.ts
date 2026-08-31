import type {
  BillingInvoiceStatus,
  BillingLineType,
  BillingOutboxOperation,
  BillingOutboxStatus,
  BillingPaymentStatus,
  BillingProviderEventStatus,
  BillingProviderPaymentStatus,
  BillingRefundStatus,
  BillingTimelineEntryType,
} from "@/api/contracts";
import type { StatusTone } from "@/components/patterns/status-badge";

type Presentation = { label: string; tone: StatusTone };

export const invoiceStatusPresentation: Record<BillingInvoiceStatus, Presentation> = {
  OPEN: { label: "Ouverte", tone: "warning" },
  SETTLED: { label: "Réglée", tone: "success" },
  SETTLED_ZERO: { label: "Soldée sans paiement", tone: "success" },
  CANCELLED: { label: "Annulée", tone: "neutral" },
};

export const paymentStatusPresentation: Record<BillingPaymentStatus, Presentation> = {
  PENDING: { label: "En attente", tone: "info" },
  SUCCEEDED: { label: "Réussi", tone: "success" },
  FAILED: { label: "Échoué", tone: "danger" },
  CANCELLED: { label: "Annulé", tone: "neutral" },
};

export const refundStatusPresentation: Record<BillingRefundStatus, Presentation> = {
  PENDING: { label: "En attente", tone: "info" },
  SUCCEEDED: { label: "Remboursé", tone: "success" },
  FAILED: { label: "Échoué", tone: "danger" },
};

export const outboxStatusPresentation: Record<BillingOutboxStatus, Presentation> = {
  PENDING: { label: "À envoyer", tone: "info" },
  PROCESSING: { label: "En cours", tone: "info" },
  PROCESSED: { label: "Traité", tone: "success" },
  FAILED: { label: "À contrôler", tone: "danger" },
  CANCELLED: { label: "Annulé", tone: "neutral" },
};

export const providerEventStatusPresentation: Record<BillingProviderEventStatus, Presentation> = {
  RECEIVED: { label: "Reçu", tone: "info" },
  APPLIED: { label: "Rapproché", tone: "success" },
  UNMATCHED: { label: "Sans commande", tone: "warning" },
  MISMATCHED: { label: "Incohérent", tone: "danger" },
};

export const billingOperationLabel: Record<BillingOutboxOperation, string> = {
  CHARGE: "Encaissement",
  REFUND: "Remboursement",
};

export const billingLineTypeLabel: Record<BillingLineType, string> = {
  PLAN: "Forfait",
  ADD_ON: "Add-on",
  QUOTA_PACKAGE: "Pack de capacité",
  COMMERCIAL_ADJUSTMENT: "Ajustement commercial",
};

export const providerPaymentStatusPresentation: Record<BillingProviderPaymentStatus, Presentation> = {
  SUCCESS: { label: "Confirmé", tone: "success" },
  FAILED: { label: "Refusé", tone: "danger" },
  PENDING: { label: "En attente", tone: "info" },
};

export const billingCycleLabel = {
  MONTHLY: "Mensuel",
  YEARLY: "Annuel",
  FOREVER: "Permanent",
} as const;

export const billingTimelineTypeLabel: Record<BillingTimelineEntryType, string> = {
  INVOICE: "Document émis",
  PAYMENT: "Paiement",
  CREDIT: "Avoir",
  REFUND: "Remboursement",
};

export function billingTimelineStateLabel(type: BillingTimelineEntryType, status: string) {
  if (type === "CREDIT") return "Émis";
  const states: Record<string, string> = {
    OPEN: "À régler",
    SETTLED: "Réglé",
    SETTLED_ZERO: "Soldé",
    CANCELLED: "Annulé",
    PENDING: "En attente",
    SUCCEEDED: type === "REFUND" ? "Remboursé" : "Encaissé",
    FAILED: "Échoué",
  };
  return states[status] ?? status;
}

export function billingErrorMessage(error: unknown, fallback = "L’opération de facturation a échoué.") {
  return error instanceof Error && error.message ? error.message : fallback;
}
