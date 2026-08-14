import type { BillingCycle, PlanStatus } from "@/api/contracts";
import type { StatusTone } from "@/components/patterns/status-badge";

/** Shared plan presentation vocabulary, so every plan surface renders the same words. */

export const planTone: Record<PlanStatus, StatusTone> = {
  DRAFT: "info",
  ACTIVE: "success",
  INACTIVE: "warning",
  ARCHIVED: "neutral",
};

export const statusText: Record<PlanStatus, string> = {
  DRAFT: "Brouillon",
  ACTIVE: "Actif",
  INACTIVE: "Inactif",
  ARCHIVED: "Archivé",
};

export const cycleText: Record<BillingCycle, string> = { MONTHLY: "mois", YEARLY: "an", FOREVER: "à vie" };

/**
 * The cycles an operator may pick for new commercial items. PLAN-FLOW-008 explicitly defers
 * perpetual (`FOREVER`) licences, so it is displayable for legacy data but never offered.
 */
export const selectableCycles: BillingCycle[] = ["MONTHLY", "YEARLY"];

export const featureModePresentation: Record<string, { label: string; tone: StatusTone }> = {
  INCLUDED: { label: "Incluse", tone: "success" },
  OPTIONAL_ADD_ON: { label: "Add-on optionnel", tone: "info" },
  BLOCKED_FOR_PLAN: { label: "Bloquée", tone: "neutral" },
};

export const money = (value: number, currency: string) =>
  new Intl.NumberFormat("fr-MA", { style: "currency", currency }).format(value);
