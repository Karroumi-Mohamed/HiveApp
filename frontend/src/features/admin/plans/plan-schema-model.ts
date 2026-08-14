import type { AddOn, PlanFeature, QuotaPackage, RegistryFeature } from "@/api/contracts";

/**
 * Pure layout model for the plan schema. Everything positional is computed here so the geometry
 * — including that nodes never overlap however many quota lines they carry — is testable without
 * rendering anything.
 */

export const SCHEMA = {
  planX: 0,
  planW: 220,
  featureX: 300,
  featureW: 280,
  extensionX: 660,
  extensionW: 250,
  permissionsX: 990,
  permissionsW: 260,
  rowGap: 18,
  nodePadding: 24,
  lineHeight: 17,
  headerHeight: 22,
} as const;

export type PlanSummary = { code: string; currencyCode: string; billingCycle: string };

export type FeatureNode = {
  feature: PlanFeature;
  definition: RegistryFeature | undefined;
  quotaLines: string[];
  permissionPreview: string[];
  permissionOverflow: number;
  y: number;
  height: number;
};

export type PackageNode = {
  kind: "package";
  pkg: QuotaPackage;
  /** How the package reaches this plan: directly on a feature, or through an add-on. */
  via: { type: "feature" | "addOn"; code: string };
  relatedFeatureCodes: string[];
  resourceLabel: string;
  y: number;
  height: number;
};

export type AddOnNode = {
  kind: "addOn";
  addOn: AddOn;
  featureCodes: string[];
  featureNames: string[];
  availability: "AVAILABLE" | "UNAVAILABLE" | "UNKNOWN";
  /** Compatibility facts worth reading on the node: dependencies and exclusions. */
  notes: string[];
  y: number;
  height: number;
};

export type PermissionsNode = {
  featureCode: string;
  labels: string[];
  overflow: number;
  y: number;
  height: number;
};

export function permissionActionLabelOf(action: string) {
  const spaced = action.replace(/_/g, " ");
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

export function shouldShowNoCommercialExtensions(input: {
  packageNodeCount: number;
  addOnNodeCount: number;
  canSeePackages: boolean;
  canSeeAddOns: boolean;
  extensionsLoading: boolean;
  hasFailures: boolean;
}): boolean {
  return (
    input.packageNodeCount === 0 &&
    input.addOnNodeCount === 0 &&
    input.canSeePackages &&
    input.canSeeAddOns &&
    !input.extensionsLoading &&
    !input.hasFailures
  );
}

/**
 * PLAN-FLOW-007: quota intent is explicit. A resource with no configuration is not "unlimited" —
 * it is an undecided draft value, said as such; only an explicit UNLIMITED reads as unlimited.
 */
export function quotaLinesOf(feature: PlanFeature, definition: RegistryFeature | undefined): string[] {
  if (feature.mode !== "INCLUDED") return [];
  const slots = definition?.quotaSchema ?? [];
  if (slots.length === 0) return ["Sans quota applicable"];
  return slots.map((slot) => {
    const label = slot.unit || slot.resource;
    const config = feature.quotaConfigs.find((entry) => entry.resource === slot.resource);
    if (!config) return `À définir — ${label}`;
    return config.mode === "UNLIMITED" || config.limit === null ? `Illimité — ${label}` : `${config.limit} ${label}`;
  });
}

/**
 * Mirrors SubscriptionServiceImpl.isAddOnCompatibleWithPlan when the complete registry catalogue
 * is available: active lifecycle, targeting, currency/cycle, commercially sellable features,
 * OPTIONAL_ADD_ON composition, and recursively compatible dependencies.
 */
export function addOnAppliesToPlan(
  addOn: AddOn,
  plan: PlanSummary,
  planFeatures: PlanFeature[],
  allAddOns: AddOn[],
  catalogByCode: ReadonlyMap<string, RegistryFeature>,
  visited: Set<string> = new Set(),
): boolean {
  if (visited.has(addOn.code)) return false;
  visited.add(addOn.code);
  try {
    if (addOn.status !== "ACTIVE") return false;
    if (addOn.blockedPlanCodes.includes(plan.code)) return false;
    if (addOn.allowedPlanCodes.length > 0 && !addOn.allowedPlanCodes.includes(plan.code)) return false;
    if (addOn.currencyCode !== plan.currencyCode || addOn.billingCycle !== plan.billingCycle) return false;
    const modes = new Map(planFeatures.map((feature) => [feature.featureCode, feature.mode]));
    for (const feature of addOn.features) {
      if (modes.get(feature.featureCode) !== "OPTIONAL_ADD_ON") return false;
      const definition = catalogByCode.get(feature.featureCode);
      if (
        !definition?.planAssignable ||
        !definition.newSalesEnabled ||
        (definition.status !== "PUBLIC" && definition.status !== "BETA")
      ) {
        return false;
      }
    }
    for (const dependencyCode of addOn.dependencyCodes) {
      const dependency = allAddOns.find((candidate) => candidate.code === dependencyCode);
      if (!dependency || !addOnAppliesToPlan(dependency, plan, planFeatures, allAddOns, catalogByCode, visited)) {
        return false;
      }
    }
    return true;
  } finally {
    // Backend removes the current code after each branch so a shared dependency in a diamond graph
    // is not mistaken for a cycle in a later sibling branch.
    visited.delete(addOn.code);
  }
}

function addOnTargetsPlan(addOn: AddOn, plan: PlanSummary): boolean {
  return (
    !addOn.blockedPlanCodes.includes(plan.code) &&
    (addOn.allowedPlanCodes.length === 0 || addOn.allowedPlanCodes.includes(plan.code))
  );
}

export function buildPlanSchemaModel(input: {
  plan: PlanSummary;
  features: PlanFeature[];
  catalogFeatures: RegistryFeature[];
  /** False means compatibility cannot be proven; targeted add-ons remain visible as UNKNOWN. */
  catalogComplete?: boolean;
  packages: QuotaPackage[];
  addOns: AddOn[];
}) {
  const byCode = new Map(input.catalogFeatures.map((feature) => [feature.code, feature]));

  let featureY = 0;
  const rows: FeatureNode[] = input.features.map((feature) => {
    const definition = byCode.get(feature.featureCode);
    const quotaLines = quotaLinesOf(feature, definition);
    const permissions = definition?.permissions ?? [];
    const permissionPreview = permissions.slice(0, 3).map((permission) => permissionActionLabelOf(permission.action));
    // Node height follows content, so a feature with many quota resources grows instead of
    // spilling over its neighbour.
    const lines = quotaLines.length + (permissionPreview.length ? 1 : 0) + 1;
    const height = SCHEMA.nodePadding + SCHEMA.headerHeight + lines * SCHEMA.lineHeight;
    const node: FeatureNode = {
      feature,
      definition,
      quotaLines,
      permissionPreview,
      permissionOverflow: Math.max(0, permissions.length - permissionPreview.length),
      y: featureY,
      height,
    };
    featureY += height + SCHEMA.rowGap;
    return node;
  });

  const targetedAddOns = input.addOns.filter((addOn) => addOnTargetsPlan(addOn, input.plan));

  // Extensions stack in one column: feature-attached packages first, then add-ons, then the
  // packages sold through those add-ons. Every lifecycle status stays visible — a draft
  // extension is a fact about the plan's surface, dimmed rather than hidden.
  let extensionY = 0;
  const packageNodes: PackageNode[] = [];
  for (const row of rows) {
    if (row.feature.mode !== "INCLUDED") continue;
    for (const pkg of input.packages) {
      if (pkg.featureCode !== row.feature.featureCode) continue;
      if (!pkg.allowedPlanCodes.includes(input.plan.code)) continue;
      const unit = row.definition?.quotaSchema.find((slot) => slot.resource === pkg.resource)?.unit;
      const height = SCHEMA.nodePadding + SCHEMA.headerHeight + 2 * SCHEMA.lineHeight;
      const y = Math.max(row.y, extensionY);
      packageNodes.push({
        kind: "package",
        pkg,
        via: { type: "feature", code: row.feature.featureCode },
        relatedFeatureCodes: [row.feature.featureCode],
        resourceLabel: unit || pkg.resource,
        y,
        height,
      });
      extensionY = y + height + SCHEMA.rowGap;
    }
  }

  const addOnNodes: AddOnNode[] = [];
  for (const addOn of targetedAddOns) {
    const featureCodes = addOn.features.map((feature) => feature.featureCode);
    const featureNames = featureCodes.map((code) => byCode.get(code)?.displayName ?? code);
    const availability =
      input.catalogComplete === false
        ? "UNKNOWN"
        : addOnAppliesToPlan(addOn, input.plan, input.features, input.addOns, byCode)
          ? "AVAILABLE"
          : "UNAVAILABLE";
    const notes = [
      availability === "UNAVAILABLE"
        ? "Indisponible pour ce forfait"
        : availability === "UNKNOWN"
          ? "Compatibilité non vérifiée"
          : null,
      addOn.dependencyCodes.length ? `Dépend de : ${addOn.dependencyCodes.join(", ")}` : null,
      addOn.exclusionCodes.length ? `Exclut : ${addOn.exclusionCodes.join(", ")}` : null,
    ].filter((note): note is string => note !== null);
    const height =
      SCHEMA.nodePadding +
      SCHEMA.headerHeight +
      (1 + Math.min(featureNames.length, 3) + notes.length) * SCHEMA.lineHeight;
    const y = extensionY;
    addOnNodes.push({ kind: "addOn", addOn, featureCodes, featureNames, availability, notes, y, height });
    extensionY = y + height + SCHEMA.rowGap;

    for (const pkg of input.packages) {
      if (!pkg.allowedAddOnCodes.includes(addOn.code)) continue;
      const definition = byCode.get(pkg.featureCode);
      const unit = definition?.quotaSchema.find((slot) => slot.resource === pkg.resource)?.unit;
      const height2 = SCHEMA.nodePadding + SCHEMA.headerHeight + 2 * SCHEMA.lineHeight;
      packageNodes.push({
        kind: "package",
        pkg,
        via: { type: "addOn", code: addOn.code },
        relatedFeatureCodes: featureCodes,
        resourceLabel: unit || pkg.resource,
        y: extensionY,
        height: height2,
      });
      extensionY += height2 + SCHEMA.rowGap;
    }
  }

  // Permissions become graph nodes when a feature is isolated: one node per feature, in its own
  // column, aligned to its feature so only the focused one is ever visible.
  const permissionsNodes: PermissionsNode[] = rows.map((row) => {
    const permissions = row.definition?.permissions ?? [];
    const labels = permissions.slice(0, 8).map((permission) => permissionActionLabelOf(permission.action));
    return {
      featureCode: row.feature.featureCode,
      labels,
      overflow: Math.max(0, permissions.length - labels.length),
      y: row.y,
      height: SCHEMA.nodePadding + SCHEMA.headerHeight + Math.max(labels.length, 1) * SCHEMA.lineHeight,
    };
  });

  const featureBottom = rows.length ? (rows.at(-1)?.y ?? 0) + (rows.at(-1)?.height ?? 0) : 0;
  const permissionsBottom = permissionsNodes.reduce((max, node) => Math.max(max, node.y + node.height), 0);
  const height = Math.max(featureBottom, extensionY ? extensionY - SCHEMA.rowGap : 0, permissionsBottom, 200);
  return { rows, packageNodes, addOnNodes, permissionsNodes, height };
}
