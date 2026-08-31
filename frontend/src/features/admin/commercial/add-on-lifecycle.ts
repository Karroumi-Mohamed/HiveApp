import type { AddOn, BillingCycle, Plan } from "@/api/contracts";

export type AddOnLifecycleAction = "EDIT" | "DELETE" | "PUBLISH" | "PAUSE" | "RESUME" | "REVISE" | "ARCHIVE";
export type AddOnPlanCompatibilityIssue = "PLAN_NOT_ACTIVE" | "CURRENCY_MISMATCH" | "CYCLE_MISMATCH";

export function addOnPlanCompatibilityIssue(
  currencyCode: string,
  billingCycle: BillingCycle,
  plan: Pick<Plan, "status" | "currencyCode" | "billingCycle">,
): AddOnPlanCompatibilityIssue | null {
  if (plan.status !== "ACTIVE") return "PLAN_NOT_ACTIVE";
  if (plan.currencyCode !== currencyCode) return "CURRENCY_MISMATCH";
  if (plan.billingCycle !== billingCycle) return "CYCLE_MISMATCH";
  return null;
}

export function addOnLifecycleActions(status: AddOn["status"]): AddOnLifecycleAction[] {
  switch (status) {
    case "DRAFT":
      return ["EDIT", "DELETE", "PUBLISH"];
    case "ACTIVE":
      return ["REVISE", "PAUSE", "ARCHIVE"];
    case "INACTIVE":
      return ["REVISE", "RESUME", "ARCHIVE"];
    case "ARCHIVED":
      return [];
  }
}

export function addOnStatusLabel(status: AddOn["status"]): string {
  switch (status) {
    case "DRAFT":
      return "Brouillon";
    case "ACTIVE":
      return "En vente";
    case "INACTIVE":
      return "Vente suspendue";
    case "ARCHIVED":
      return "Archivé";
  }
}
