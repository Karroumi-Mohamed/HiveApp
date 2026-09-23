import type { ComparedPlan, PlanFeature, RegistryFeature } from "@/api/contracts";

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export function comparisonSelection(raw: string | null): string[] | null {
  const ids = raw?.split(",") ?? [];
  return ids.length >= 2 &&
    ids.length <= 3 &&
    ids.every((id) => uuid.test(id)) &&
    new Set(ids.map((id) => id.toLowerCase())).size === ids.length
    ? ids.map((id) => id.toLowerCase())
    : null;
}

export type Capacity = { kind: "FINITE"; limit: number } | { kind: "UNLIMITED" | "UNDEFINED" | "NOT_APPLICABLE" };
export function capacityOf(feature: PlanFeature | undefined, resource: string): Capacity {
  if (feature?.mode !== "INCLUDED") return { kind: "NOT_APPLICABLE" };
  const quota = feature.quotaConfigs.find((entry) => entry.resource === resource);
  if (!quota) return { kind: "UNDEFINED" };
  if (quota.mode === "UNLIMITED") return { kind: "UNLIMITED" };
  return quota.mode === "FINITE" && quota.limit !== null
    ? { kind: "FINITE", limit: quota.limit }
    : { kind: "UNDEFINED" };
}

function signature(feature: PlanFeature | undefined) {
  if (!feature) return "ABSENT";
  return JSON.stringify([
    feature.mode,
    feature.quotaConfigs
      .map((quota) => [quota.resource, quota.mode, quota.mode === "UNLIMITED" ? null : quota.limit])
      .sort((a, b) => String(a[0]).localeCompare(String(b[0]))),
  ]);
}

export function comparisonFeatures(plans: ComparedPlan[], catalog: RegistryFeature[]) {
  const definitions = new Map(catalog.map((feature) => [feature.code, feature]));
  const byPlan = plans.map((plan) => new Map(plan.features.map((feature) => [feature.featureCode, feature])));
  const codes = [...new Set(plans.flatMap((plan) => plan.features.map((feature) => feature.featureCode)))].sort();
  return codes.map((code) => {
    const features = byPlan.map((plan) => plan.get(code));
    const definition = definitions.get(code);
    const resources = [
      ...new Set([
        ...(definition?.quotaSchema.map((slot) => slot.resource) ?? []),
        ...features.flatMap((feature) => feature?.quotaConfigs.map((quota) => quota.resource) ?? []),
      ]),
    ].sort();
    return { code, definition, features, resources, changed: new Set(features.map(signature)).size > 1 };
  });
}

export type ComparisonFeature = ReturnType<typeof comparisonFeatures>[number];
