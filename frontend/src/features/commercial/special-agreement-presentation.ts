import type {
  SpecialAgreementEndInstruction,
  SpecialAgreementPricingMode,
  SpecialAgreementStatus,
} from "@/api/contracts";
import type { StatusTone } from "@/components/patterns/status-badge";

export const specialAgreementStatus: Record<SpecialAgreementStatus, { label: string; tone: StatusTone }> = {
  SCHEDULED: { label: "Planifié", tone: "info" },
  AWAITING_SETTLEMENT: { label: "Règlement attendu", tone: "warning" },
  ACTIVE: { label: "En cours", tone: "success" },
  COMPLETED: { label: "Terminé", tone: "neutral" },
  CANCELLED: { label: "Annulé", tone: "danger" },
  NEEDS_ATTENTION: { label: "Décision requise", tone: "warning" },
};

export const specialAgreementPricing: Record<SpecialAgreementPricingMode, string> = {
  CATALOGUE_TOTAL: "Tarif catalogue",
  CUSTOM_TOTAL: "Montant négocié",
  COMPLIMENTARY: "Accord offert",
};

export const specialAgreementEnd: Record<SpecialAgreementEndInstruction, string> = {
  CONTINUE_REVIEWED_TERMS: "Continuer aux conditions validées",
  RESTORE_PREVIOUS_TERMS: "Rétablir les conditions précédentes",
  END_ACCESS: "Mettre fin à l’accès",
  MANUAL_REVIEW: "Demander une décision à l’échéance",
};

export function specialAgreementDateTime(value: string | null) {
  return value
    ? new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value))
    : "—";
}
