import { describe, expect, test } from "bun:test";
import type { AddOn, PlanFeature, QuotaPackage, RegistryFeature } from "@/api/contracts";
import {
  addOnAppliesToPlan,
  buildPlanSchemaModel,
  quotaLinesOf,
  shouldShowNoCommercialExtensions,
} from "./plan-schema-model";

const PLAN = { code: "FREE", currencyCode: "USD", billingCycle: "MONTHLY" } as const;

const registryFeature = (code: string, overrides: Partial<RegistryFeature> = {}): RegistryFeature =>
  ({
    id: code,
    code,
    moduleCode: "platform",
    featureKey: code,
    displayName: code,
    description: "",
    surface: "CLIENT_WORKSPACE",
    status: "PUBLIC",
    publicVisible: true,
    newSalesEnabled: true,
    newGrantsEnabled: true,
    runtimeEnabled: true,
    registryPresent: true,
    planAssignable: true,
    clientRoleGrantable: true,
    platformAdminRoleGrantable: false,
    b2bDelegatable: false,
    publicCatalogVisible: true,
    publicVisibilityToggleable: true,
    newSalesToggleable: true,
    newGrantsToggleable: true,
    emergencyRuntimeToggleable: true,
    sortOrder: 0,
    quotaSchema: [],
    permissions: [],
    ...overrides,
  }) as RegistryFeature;

const planFeature = (featureCode: string, overrides: Partial<PlanFeature> = {}): PlanFeature => ({
  id: featureCode,
  featureCode,
  mode: "INCLUDED",
  quotaConfigs: [],
  ...overrides,
});

const quotaPackage = (overrides: Partial<QuotaPackage>): QuotaPackage =>
  ({
    id: overrides.code ?? "pkg",
    code: "PKG",
    name: "Pack",
    description: null,
    featureCode: "f",
    resource: "members",
    capacityPerUnit: 5,
    price: 10,
    currencyCode: "USD",
    billingCycle: "MONTHLY",
    repeatable: true,
    maximumQuantity: 4,
    status: "ACTIVE",
    definitionVersion: 1,
    allowedPlanCodes: [],
    allowedAddOnCodes: [],
    ...overrides,
  }) as QuotaPackage;

const addOn = (overrides: Partial<AddOn>): AddOn =>
  ({
    id: overrides.code ?? "a",
    code: "A",
    name: "A",
    description: null,
    price: 5,
    currencyCode: "USD",
    billingCycle: "MONTHLY",
    status: "ACTIVE",
    definitionVersion: 1,
    allowedPlanCodes: [],
    blockedPlanCodes: [],
    dependencyCodes: [],
    exclusionCodes: [],
    features: [],
    ...overrides,
  }) as AddOn;

describe("quota lines", () => {
  const definition = registryFeature("f", {
    quotaSchema: [
      { resource: "members", type: "count", unit: "membres" },
      { resource: "companies", type: "count", unit: "entreprises" },
    ],
  });

  test("a configured limit reads with its unit", () => {
    const lines = quotaLinesOf(
      planFeature("f", {
        quotaConfigs: [
          { resource: "members", mode: "FINITE", limit: 3 },
          { resource: "companies", mode: "UNLIMITED", limit: null },
        ],
      }),
      definition,
    );
    expect(lines).toEqual(["3 membres", "Illimité — entreprises"]);
  });

  test("a missing configuration is an undecided value, never silently unlimited", () => {
    // PLAN-FLOW-007: quota intent must be explicit; activation refuses this state.
    const lines = quotaLinesOf(
      planFeature("f", { quotaConfigs: [{ resource: "members", mode: "FINITE", limit: 3 }] }),
      definition,
    );
    expect(lines).toEqual(["3 membres", "À définir — entreprises"]);
  });

  test("a feature with no quota-able resource says so instead of showing a dash", () => {
    expect(quotaLinesOf(planFeature("f"), registryFeature("f"))).toEqual(["Aucune limite de capacité"]);
  });
});

describe("add-on applicability — mirrors runtime catalogue compatibility", () => {
  const noCatalog = new Map<string, RegistryFeature>();
  const applies = (candidate: AddOn, features: PlanFeature[] = [], all: AddOn[] = [], catalog = noCatalog) =>
    addOnAppliesToPlan(candidate, PLAN, features, [candidate, ...all], catalog);

  test("plan targeting is honoured in both directions", () => {
    expect(applies(addOn({ blockedPlanCodes: ["FREE"] }))).toBe(false);
    expect(applies(addOn({ allowedPlanCodes: ["PRO"] }))).toBe(false);
    expect(applies(addOn({ allowedPlanCodes: ["FREE"] }))).toBe(true);
  });

  test("currency and billing cycle must match the plan", () => {
    expect(applies(addOn({ currencyCode: "MAD" }))).toBe(false);
    expect(applies(addOn({ billingCycle: "YEARLY" }))).toBe(false);
  });

  test("every add-on feature must be declared OPTIONAL_ADD_ON in the plan's composition", () => {
    const candidate = addOn({ features: [{ id: "af", featureCode: "reporting", quotaConfigs: [] }] });
    // Absent from the composition: the plan does not permit the feature (requirePlanSupportsAddOn).
    expect(applies(candidate, [])).toBe(false);
    // Included already: the same capability cannot be sold twice.
    expect(applies(candidate, [planFeature("reporting", { mode: "INCLUDED" })])).toBe(false);
    // Blocked: never available.
    expect(applies(candidate, [planFeature("reporting", { mode: "BLOCKED_FOR_PLAN" })])).toBe(false);
    // Declared optional: exactly the state the backend requires.
    expect(
      applies(
        candidate,
        [planFeature("reporting", { mode: "OPTIONAL_ADD_ON" })],
        [],
        new Map([["reporting", registryFeature("reporting")]]),
      ),
    ).toBe(true);
  });

  test("every add-on feature must exist and remain commercially sellable in the registry", () => {
    const candidate = addOn({ features: [{ id: "af", featureCode: "reporting", quotaConfigs: [] }] });
    const features = [planFeature("reporting", { mode: "OPTIONAL_ADD_ON" })];
    expect(applies(candidate, features)).toBe(false);
    expect(
      applies(
        candidate,
        features,
        [],
        new Map([["reporting", registryFeature("reporting", { planAssignable: false })]]),
      ),
    ).toBe(false);
    expect(
      applies(
        candidate,
        features,
        [],
        new Map([["reporting", registryFeature("reporting", { newSalesEnabled: false })]]),
      ),
    ).toBe(false);
    expect(
      applies(candidate, features, [], new Map([["reporting", registryFeature("reporting", { status: "INTERNAL" })]])),
    ).toBe(false);
    expect(
      applies(
        candidate,
        features,
        [],
        new Map([["reporting", registryFeature("reporting", { status: "DEPRECATED" })]]),
      ),
    ).toBe(false);
    expect(
      applies(candidate, features, [], new Map([["reporting", registryFeature("reporting", { status: "BETA" })]])),
    ).toBe(true);
  });

  test("the add-on itself must be active", () => {
    expect(applies(addOn({ status: "INACTIVE" }))).toBe(false);
  });

  test("dependencies must be ACTIVE and themselves available on this plan", () => {
    const dependency = addOn({ code: "DEP", status: "INACTIVE" });
    const candidate = addOn({ code: "MAIN", dependencyCodes: ["DEP"] });
    expect(applies(candidate, [], [dependency])).toBe(false);

    const activeButForeign = addOn({ code: "DEP", allowedPlanCodes: ["PRO"] });
    expect(applies(candidate, [], [activeButForeign])).toBe(false);

    const availableDependency = addOn({ code: "DEP" });
    expect(applies(candidate, [], [availableDependency])).toBe(true);

    const missing = addOn({ code: "MAIN", dependencyCodes: ["GHOST"] });
    expect(applies(missing, [], [])).toBe(false);
  });

  test("a dependency cycle reads as unavailable instead of recursing forever", () => {
    const first = addOn({ code: "A", dependencyCodes: ["B"] });
    const second = addOn({ code: "B", dependencyCodes: ["A"] });
    expect(addOnAppliesToPlan(first, PLAN, [], [first, second], noCatalog)).toBe(false);
  });

  test("a shared dependency is not mistaken for a cycle across sibling branches", () => {
    const shared = addOn({ code: "SHARED" });
    const left = addOn({ code: "LEFT", dependencyCodes: ["SHARED"] });
    const right = addOn({ code: "RIGHT", dependencyCodes: ["SHARED"] });
    const root = addOn({ code: "ROOT", dependencyCodes: ["LEFT", "RIGHT"] });
    expect(addOnAppliesToPlan(root, PLAN, [], [root, left, right, shared], noCatalog)).toBe(true);
  });
});

describe("schema remote state", () => {
  const settledEmpty = {
    packageNodeCount: 0,
    addOnNodeCount: 0,
    canSeePackages: true,
    canSeeAddOns: true,
    extensionsLoading: false,
    hasFailures: false,
  };

  test("shows the empty state only after both extension queries settle successfully", () => {
    expect(shouldShowNoCommercialExtensions(settledEmpty)).toBe(true);
    expect(shouldShowNoCommercialExtensions({ ...settledEmpty, extensionsLoading: true })).toBe(false);
    expect(shouldShowNoCommercialExtensions({ ...settledEmpty, hasFailures: true })).toBe(false);
    expect(shouldShowNoCommercialExtensions({ ...settledEmpty, canSeeAddOns: false })).toBe(false);
  });
});

describe("schema layout", () => {
  test("feature nodes never overlap, however many quota lines one carries", () => {
    const definition = registryFeature("f1", {
      quotaSchema: [
        { resource: "a", type: "count", unit: "a" },
        { resource: "b", type: "count", unit: "b" },
        { resource: "c", type: "count", unit: "c" },
        { resource: "d", type: "count", unit: "d" },
      ],
    });
    const model = buildPlanSchemaModel({
      plan: PLAN,
      features: [planFeature("f1"), planFeature("f2")],
      catalogFeatures: [definition, registryFeature("f2")],
      packages: [],
      addOns: [],
    });
    const [first, second] = model.rows;
    expect(first).toBeDefined();
    expect(second).toBeDefined();
    if (!first || !second) return;
    expect(second.y).toBeGreaterThanOrEqual(first.y + first.height);
  });

  test("packages appear when attached to this plan directly, whatever their status", () => {
    const model = buildPlanSchemaModel({
      plan: PLAN,
      features: [planFeature("f")],
      catalogFeatures: [
        registryFeature("f", { quotaSchema: [{ resource: "members", type: "count", unit: "membres" }] }),
      ],
      packages: [
        quotaPackage({ code: "ATTACHED_DRAFT", status: "DRAFT", allowedPlanCodes: ["FREE"] }),
        quotaPackage({ code: "OTHER_PLAN", allowedPlanCodes: ["PRO"] }),
      ],
      addOns: [],
    });
    expect(model.packageNodes.map((node) => node.pkg.code)).toEqual(["ATTACHED_DRAFT"]);
    expect(model.packageNodes[0]?.via).toEqual({ type: "feature", code: "f" });
  });

  test("a package sold through an applicable add-on joins the diagram via that add-on", () => {
    const model = buildPlanSchemaModel({
      plan: PLAN,
      features: [planFeature("base"), planFeature("reporting", { mode: "OPTIONAL_ADD_ON" })],
      catalogFeatures: [registryFeature("base"), registryFeature("reporting")],
      packages: [quotaPackage({ code: "VIA_ADDON", featureCode: "reporting", allowedAddOnCodes: ["REPORTS"] })],
      addOns: [addOn({ code: "REPORTS", features: [{ id: "af", featureCode: "reporting", quotaConfigs: [] }] })],
    });
    const viaAddOn = model.packageNodes.find((node) => node.pkg.code === "VIA_ADDON");
    expect(viaAddOn?.via).toEqual({ type: "addOn", code: "REPORTS" });
    expect(viaAddOn?.relatedFeatureCodes).toEqual(["reporting"]);
  });

  test("targeted but unavailable add-ons stay visible and are labelled honestly", () => {
    const model = buildPlanSchemaModel({
      plan: PLAN,
      features: [planFeature("reporting", { mode: "OPTIONAL_ADD_ON" })],
      catalogFeatures: [registryFeature("reporting", { newSalesEnabled: false })],
      packages: [],
      addOns: [addOn({ code: "REPORTS", features: [{ id: "af", featureCode: "reporting", quotaConfigs: [] }] })],
    });
    expect(model.addOnNodes[0]?.availability).toBe("UNAVAILABLE");
    expect(model.addOnNodes[0]?.notes).toContain("Indisponible pour ce forfait");
  });

  test("catalogue-limited operators see targeted add-ons without a false eligibility claim", () => {
    const model = buildPlanSchemaModel({
      plan: PLAN,
      features: [planFeature("reporting", { mode: "OPTIONAL_ADD_ON" })],
      catalogFeatures: [],
      catalogComplete: false,
      packages: [],
      addOns: [addOn({ code: "REPORTS", features: [{ id: "af", featureCode: "reporting", quotaConfigs: [] }] })],
    });
    expect(model.addOnNodes[0]?.availability).toBe("UNKNOWN");
    expect(model.addOnNodes[0]?.notes).toContain("Compatibilité non vérifiée");
  });

  test("extension nodes stack without overlapping across kinds", () => {
    const model = buildPlanSchemaModel({
      plan: PLAN,
      features: [planFeature("f")],
      catalogFeatures: [
        registryFeature("f", { quotaSchema: [{ resource: "members", type: "count", unit: "membres" }] }),
      ],
      packages: [
        quotaPackage({ code: "P1", allowedPlanCodes: ["FREE"] }),
        quotaPackage({ code: "P2", allowedPlanCodes: ["FREE"] }),
      ],
      addOns: [addOn({ code: "A1" })],
    });
    const all = [...model.packageNodes, ...model.addOnNodes].toSorted((a, b) => a.y - b.y);
    for (let index = 1; index < all.length; index += 1) {
      const previous = all[index - 1];
      const current = all[index];
      if (!previous || !current) continue;
      expect(current.y).toBeGreaterThanOrEqual(previous.y + previous.height);
    }
  });

  test("every feature gets a permissions node aligned to it, listing readable actions", () => {
    const model = buildPlanSchemaModel({
      plan: PLAN,
      features: [planFeature("f")],
      catalogFeatures: [
        registryFeature("f", {
          permissions: [
            { id: "p1", code: "f.read", name: "Read", description: "", action: "read", resource: "f" },
            { id: "p2", code: "f.assign_role", name: "Assign", description: "", action: "assign_role", resource: "f" },
          ],
        }),
      ],
      packages: [],
      addOns: [],
    });
    const node = model.permissionsNodes[0];
    expect(node?.featureCode).toBe("f");
    expect(node?.labels).toEqual(["Read", "Assign role"]);
    expect(node?.y).toBe(model.rows[0]?.y ?? -1);
  });
});
