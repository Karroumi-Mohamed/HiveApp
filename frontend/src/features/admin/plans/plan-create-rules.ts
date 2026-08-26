import type { Plan } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";

/**
 * Creation-flow rules, extracted so the wizard's decisions are testable: which permission the
 * chosen starting point actually requires, and what a source plan prefills.
 */

/** Duplicating exercises the duplicate operation, not creation — the backend checks that node. */
export function requiredCreationPermission(hasSource: boolean) {
  return hasSource ? adminPermissions.plansDuplicate : adminPermissions.plansCreate;
}

/** Send operators only to a route that their read permissions can actually mount. */
export function createdPlanDestination(
  planId: string,
  access: { readDetail: boolean; listFeatures: boolean; listPlans: boolean },
) {
  if (access.readDetail && access.listFeatures) return `/admin/plans/${planId}/features`;
  if (access.readDetail) return `/admin/plans/${planId}`;
  if (access.listPlans) return "/admin/plans";
  return "/admin";
}

export type PlanDraftFields = {
  name: string;
  description: string;
  price: string;
  currencyCode: string;
  billingCycle: Plan["billingCycle"];
};

export function prefillFromSource(source: Plan): PlanDraftFields {
  return {
    name: source.name,
    description: source.description ?? "",
    price: String(source.price),
    currencyCode: source.currencyCode,
    billingCycle: source.billingCycle === "FOREVER" ? "MONTHLY" : source.billingCycle,
  };
}
