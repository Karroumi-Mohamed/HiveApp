import type { BillingCycle, ExactDecimal, PlanStatus } from "@/api/contracts";
import type { StatusTone } from "@/components/patterns/status-badge";
import { formatExactMoney } from "@/lib/exact-decimal";

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
  OPTIONAL_ADD_ON: { label: "Disponible en add-on", tone: "info" },
  BLOCKED_FOR_PLAN: { label: "Indisponible", tone: "neutral" },
};

/** Compact Plan-table wording; the expanded row remains the source of the complete list. */
export const addOnAvailabilityLabel = (names: string[]) => {
  const [first] = names;
  if (!first) return "Aucun add-on associé";
  return names.length === 1 ? `Via ${first}` : `Via ${first} +${names.length - 1}`;
};

export const money = (value: ExactDecimal, currency: string) => formatExactMoney(value, currency);
