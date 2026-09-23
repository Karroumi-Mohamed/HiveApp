import { describe, expect, test } from "bun:test";
import type { ComparedPlan, PlanFeature, RegistryFeature } from "@/api/contracts";
import { capacityOf, comparisonFeatures, comparisonSelection } from "./plan-comparison-model";

const ids = [
  "7f31acf0-5101-4341-ac59-7ac88d29ce45",
  "7f31acf0-5101-4341-ac59-7ac88d29ce46",
  "7f31acf0-5101-4341-ac59-7ac88d29ce47",
];
const feature: PlanFeature = { id: "a", featureCode: "platform.staff", mode: "INCLUDED", quotaConfigs: [] };
const plan = (features: PlanFeature[]) => ({ features }) as ComparedPlan;

describe("plan catalogue comparison", () => {
  test("only two or three distinct exact-version IDs are accepted", () => {
    expect(comparisonSelection(ids.slice(0, 2).join(","))).toEqual(ids.slice(0, 2));
    expect(comparisonSelection(ids.join(","))).toEqual(ids);
    for (const value of [
      null,
      "",
      ids[0] ?? "",
      `${ids[0]},${ids[0]?.toUpperCase()}`,
      `${ids.join(",")},${ids[0]}`,
      "bad,values",
    ])
      expect(comparisonSelection(value)).toBeNull();
  });
  test("zero, unlimited, missing and non-applicable never collapse together", () => {
    expect(capacityOf(feature, "members")).toEqual({ kind: "UNDEFINED" });
    expect(capacityOf(undefined, "members")).toEqual({ kind: "NOT_APPLICABLE" });
    expect(capacityOf({ ...feature, mode: "OPTIONAL_ADD_ON" }, "members")).toEqual({ kind: "NOT_APPLICABLE" });
    expect(
      capacityOf({ ...feature, quotaConfigs: [{ resource: "members", mode: "FINITE", limit: 0 }] }, "members"),
    ).toEqual({ kind: "FINITE", limit: 0 });
    expect(
      capacityOf({ ...feature, quotaConfigs: [{ resource: "members", mode: "UNLIMITED", limit: null }] }, "members"),
    ).toEqual({ kind: "UNLIMITED" });
    expect(
      capacityOf({ ...feature, quotaConfigs: [{ resource: "members", mode: "FINITE", limit: null }] }, "members"),
    ).toEqual({ kind: "UNDEFINED" });
  });
  test("unrelated mapping IDs and quota order do not produce false differences", () => {
    const quotas = [
      { resource: "a", mode: "FINITE" as const, limit: 2 },
      { resource: "b", mode: "UNLIMITED" as const, limit: null },
    ];
    const rows = comparisonFeatures(
      [
        plan([{ ...feature, quotaConfigs: quotas }]),
        plan([{ ...feature, id: "different", quotaConfigs: [...quotas].reverse() }]),
      ],
      [],
    );
    expect(rows[0]?.changed).toBe(false);
    expect(rows[0]?.resources).toEqual(["a", "b"]);
  });
  test("a third plan, optional modes and missing features participate in the diff", () => {
    const rows = comparisonFeatures([plan([feature]), plan([{ ...feature, mode: "OPTIONAL_ADD_ON" }]), plan([])], []);
    expect(rows[0]?.changed).toBe(true);
    expect(rows[0]?.features[2]).toBeUndefined();
  });
  test("registry definitions reveal missing resource configuration without guessing unlimited", () => {
    const registry = [
      { code: feature.featureCode, quotaSchema: [{ resource: "members", unit: "membres" }] },
    ] as RegistryFeature[];
    const rows = comparisonFeatures([plan([feature]), plan([feature])], registry);
    expect(rows[0]?.resources).toEqual(["members"]);
    expect(comparisonFeatures([plan([feature]), plan([feature])], [])[0]?.definition).toBeUndefined();
  });
});
