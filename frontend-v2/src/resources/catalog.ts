import { label } from "@/lib/format";
import { adminApi as api } from "@/api/admin-api";
import { adminPermissions as p } from "@/auth/permissions";
import { read } from "@/data/gateway";
import { planApplicationApi } from "@/api/plan-application-api";
import { can } from "@/data/session";
import type {
  Action,
  Field,
  Resource,
  RecordData,
  Section,
  Column,
} from "./types";
import {
  commercialFields,
  baseDefaults,
  text,
  num,
  dt,
  decimal,
  select,
  planField,
  addOnField,
  featureField,
  planFeatureFields,
  quotaFields,
  allowed,
  compactHistory,
  currencies,
  cycles,
  priceField,
  capacityField,
  quotaResourceField,
} from "./fields";
const productColumns: Column[] = [
  { key: "name", label: "Name" },
  { key: "code", label: "Code" },
  { key: "revisionNumber", label: "Revision" },
  { key: "status", label: "Status", format: "status" },
  { key: "publishedPriceCount", label: "Published prices" },
  { key: "salesVisibility", label: "Visibility" },
];
const priceColumns: Column[] = [
  {
    key: "productName",
    label: "Product",
    link: (r) => "/catalog/prices/" + r.id,
  },
  { key: "amount", label: "Amount", format: "money" },
  { key: "currencyCode", label: "Currency" },
  { key: "billingCycle", label: "Cycle" },
  { key: "status", label: "Status", format: "status" },
  { key: "effectiveFrom", label: "Effective from", format: "date" },
];
const visibility = select("salesVisibility", "Sales visibility", [
  "PUBLIC",
  "DIRECT_ONLY",
]);
const extension = select("extensionPolicy", "Extensions", [
  "CLOSED",
  "ALLOW_LIST",
  "OPEN_COMPATIBLE",
]);
const productPrices = (type: string): Section => ({
  key: "prices",
  label: "Prices",
  permission: p.priceBooksList,
  load: (d, page) =>
    api.productPrices({
      ownerId: d.id,
      ownerType: type as any,
      page,
      size: 20,
    }),
  columns: priceColumns,
});
const branchDestination = (r: RecordData) =>
  "/catalog/plans/" + (r.id || r.planId);
const planActions: Action[] = [
  {
    key: "feature",
    label: "Add feature",
    permission: p.plansAssignFeature,
    visible: allowed("MANAGE_COMPOSITION", ["DRAFT"]),
    fields: planFeatureFields,
    defaults: () => ({ mode: "INCLUDED", quotaConfigs: [] }),
    reason: false,
    execute: (d, i) => api.assignPlanFeature(d.id, d.version, i as any),
  },
  {
    key: "metadata",
    label: "Edit metadata",
    permission: p.plansUpdateMetadata,
    fields: [
      text("name", "Name", true),
      { key: "description", label: "Description", type: "textarea" },
    ],
    defaults: (d) => ({ name: d.name, description: d.description }),
    execute: (d, i) =>
      api.updatePlanMetadata(d.id, { ...i, expectedVersion: d.version } as any),
  },
  {
    key: "availability",
    label: "Availability",
    permission: p.commercialUpdatePlanPolicy,
    fields: [extension, visibility],
    defaults: (d) => ({
      extensionPolicy: d.extensionPolicy,
      salesVisibility: d.salesVisibility,
    }),
    preview: (d, i) =>
      read(p.commercialPreviewPlanPolicy, () =>
        api.previewPlanAvailability(d.id, i.extensionPolicy, i.salesVisibility),
      ),
    execute: (d, i, r) =>
      api.updatePlanAvailability(d.id, {
        ...i,
        expectedVersion: d.version,
        previewToken: r!.previewToken,
      } as any),
  },
  ...(["ACTIVATE", "DEACTIVATE", "ARCHIVE"] as const).map<Action>((a) => ({
    key: a,
    label:
      a === "DEACTIVATE"
        ? "Deactivate"
        : a === "ACTIVATE"
          ? "Activate"
          : "Archive",
    permission: p.plansTransition,
    visible: allowed(
      a,
      a === "ACTIVATE"
        ? ["DRAFT", "INACTIVE"]
        : a === "DEACTIVATE"
          ? ["ACTIVE"]
          : ["DRAFT", "INACTIVE"],
    ),
    preview:
      a === "ACTIVATE"
        ? (d) =>
            read(p.plansPreviewActivation, () =>
              api.previewPlanActivation(d.id),
            )
        : undefined,
    execute: (d, i, r) =>
      api.changePlanLifecycle(d.id, {
        action: a,
        expectedVersion: d.version,
        reason: i.reason,
        activationPreviewToken: r?.previewToken,
      }),
  })),
  ...(["duplicate", "revision"] as const).map<Action>((a) => ({
    key: a,
    label: a === "duplicate" ? "Duplicate" : "New revision",
    permission: a === "duplicate" ? p.plansDuplicate : p.plansRevise,
    fields: commercialFields,
    defaults: (d) => ({
      ...d,
      name: a === "duplicate" ? d.name + " copy" : d.name,
    }),
    execute: (d, i) =>
      a === "duplicate"
        ? api.duplicatePlan(d.id, d.version, i as any)
        : api.createPlanVersion(d.id, d.version, i as any),
    destination: branchDestination,
  })),
  {
    key: "public",
    label: "Set public revision",
    permission: p.plansSelectPublicVersion,
    visible: (d) => d.status === "ACTIVE" && d.salesVisibility === "PUBLIC",
    preview: async (d) => {
      const x = await read(p.plansListVersions, () => api.planVersions(d.id));
      return {
        catalogRevision: x.catalogRevision,
        currentPublicRevision: x.publicPlanId,
        targetRevision: d.revisionNumber,
      };
    },
    execute: async (d, i, r) =>
      api.selectPlanPublicVersion(d.id, {
        expectedVersion: d.version,
        expectedCatalogRevision: r!.catalogRevision,
        reason: i.reason,
      }),
  },
  {
    key: "delete",
    label: "Delete draft",
    permission: p.plansDelete,
    visible: allowed("DELETE_DRAFT", ["DRAFT"]),
    destructive: true,
    fields: [text("confirmationName", "Plan name", true)],
    preview: (d) =>
      read(p.plansPreviewDelete, () => api.previewPlanDeletion(d.id)),
    execute: (d, i, r) =>
      api.deletePlan(d.id, {
        confirmationName: i.confirmationName,
        expectedVersion: d.version,
        previewToken: r!.previewToken,
      }),
    destination: () => "/catalog",
  },
];
async function featureNames(items: RecordData[]) {
  if (!can(p.registryFeatureCatalog))
    return items.map((x) => ({ ...x, featureLabel: x.featureCode }));
  try {
    const modules = await read(p.registryFeatureCatalog, () =>
      api.featureCatalog("PLAN_ASSIGNABLE"),
    );
    const names = new Map(
      modules.flatMap((x) => x.features).map((x) => [x.code, x.displayName]),
    );
    return items.map((x) => ({
      ...x,
      featureLabel:
        names.get(x.featureCode) && names.get(x.featureCode) !== x.featureCode
          ? names.get(x.featureCode)
          : label(x.featureCode?.split(".").at(-1)),
    }));
  } catch {
    return items.map((x) => ({ ...x, featureLabel: x.featureCode }));
  }
}
const featureColumns: Column[] = [
  { key: "featureLabel", label: "Feature" },
  { key: "mode", label: "Availability" },
  { key: "quotaConfigs", label: "Quota" },
];
const plans: Resource = {
  key: "plans",
  title: "Plans",
  singular: "Plan",
  base: "/catalog/plans",
  listPermission: p.plansList,
  listPermissions: [p.plansList, p.plansListFamilies],
  readPermission: p.plansReadDetail,
  list: async (q) => {
    if (can(p.plansListFamilies) && q.group !== "revisions" && !q.status) {
      const result = await read(p.plansListFamilies, () => api.planFamilies(q));
      return {
        ...result,
        content: result.content.map((x) => ({
          ...x.publicVersion,
          revisionNumber: x.publicVersion.productVersionNumber,
          publishedPriceCount: x.pricesVisible ? x.currentPrices.length : null,
          currentSubscriberCount: x.currentSubscriberCount,
        })),
      };
    }
    return api.operationalPlans(q);
  },
  detail: async (id) => ({
    ...(await api.plan(id)),
    ...(can(p.plansReadOperations)
      ? await read(p.plansReadOperations, () => api.planOperations(id))
      : {}),
  }),
  columns: productColumns,
  detailKeys: [
    "code",
    "description",
    "revisionNumber",
    "extensionPolicy",
    "salesVisibility",
    "currentSubscriberCount",
    "historicalSubscriberCount",
    "warnings",
  ],
  createPermission: p.plansCreate,
  editPermission: p.plansUpdate,
  editable: allowed("EDIT_DRAFT", ["DRAFT"]),
  fields: [
    ...commercialFields.map((f) =>
      ["price", "currencyCode", "billingCycle"].includes(f.key)
        ? { ...f, show: (d: RecordData) => !d.id }
        : f,
    ),
    { ...extension, show: (d) => !d.id },
    { ...visibility, show: (d) => !d.id },
    {
      key: "features",
      show: (d) => !d.id,
      label: "Features",
      type: "array",
      fields: planFeatureFields,
      defaults: { mode: "INCLUDED", quotaConfigs: [] },
      full: true,
    },
  ],
  defaults: {
    ...baseDefaults,
    extensionPolicy: "CLOSED",
    salesVisibility: "DIRECT_ONLY",
    features: [],
  },
  save: (id, i, d) =>
    id
      ? api.updatePlan(id, {
          price: d!.price,
          currencyCode: d!.currencyCode,
          billingCycle: d!.billingCycle,
          ...i,
          expectedVersion: d!.version,
        } as any)
      : api.createPlan(i as any),
  actions: planActions,
  links: (d) => [
    ...(can(p.plansCompareVersions)
      ? [
          {
            label: "Compare revisions",
            to: "/catalog/plans/" + d.id + "?view=revision-comparison",
          },
        ]
      : []),
    ...(can(p.plansCompare)
      ? [
          {
            label: "Compare plans",
            to: "/catalog/plans/" + d.id + "?view=comparison",
          },
        ]
      : []),
  ],
  sections: [
    {
      key: "feature-map",
      label: "Effective capabilities",
      group: { key: "features", label: "Features and compatibility" },
      permission: p.plansListFeatures,
      load: async (d) => ({ planId: d.id }),
    },
    {
      key: "comparison",
      label: "Compare plans",
      group: { key: "comparisons", label: "Comparison" },
      permission: p.plansCompare,
      load: async (d) => ({ planId: d.id }),
    },
    {
      key: "revision-comparison",
      label: "Compare revisions",
      group: { key: "comparisons", label: "Comparison" },
      permission: p.plansCompareVersions,
      load: async (d) => ({ planId: d.id }),
    },
    {
      key: "features",
      label: "Features",
      permission: p.plansListFeatures,
      load: async (d) => featureNames(await api.planFeatures(d.id)),
      columns: featureColumns,
      actions: (row, d) => [
        {
          key: "edit",
          label: "Edit",
          permission: p.plansUpdateFeature,
          visible: () => allowed("MANAGE_COMPOSITION", ["DRAFT"])(d),
          fields: planFeatureFields,
          defaults: () => row,
          reason: false,
          execute: (_, i) =>
            api.updatePlanFeature(d.id, row.id, d.version, i as any),
        },
        {
          key: "remove",
          label: "Remove",
          permission: p.plansRemoveFeature,
          visible: () => allowed("MANAGE_COMPOSITION", ["DRAFT"])(d),
          destructive: true,
          execute: () => api.removePlanFeature(d.id, row.id, d.version),
        },
      ],
    },
    productPrices("PLAN"),
    {
      key: "family-subscribers",
      label: "Family subscribers",
      permission: p.plansListFamilySubscribers,
      load: (d, page) =>
        planApplicationApi.subscribers(d.id, { view: "ALL", page, size: 20 }),
      columns: [
        {
          key: "accountName",
          label: "Customer",
          link: (r) => "/customers/" + r.accountId,
        },
        { key: "productVersionNumber", label: "Revision" },
        { key: "status", label: "Status", format: "status" },
        {
          key: "retainedTotal",
          label: "Recurring amount",
          format: "money",
          currencyKey: "currency",
        },
        { key: "periodEnd", label: "Renewal", format: "date" },
      ],
    },
    {
      key: "rollouts",
      label: "Applications",
      permission: p.plansListApplications,
      load: (d, page) => planApplicationApi.list(d.id, page),
      columns: [
        {
          key: "reason",
          label: "Application",
          link: (r) => "/operations/rollouts/" + r.id,
        },
        { key: "status", label: "Status", format: "status" },
        { key: "createdAt", label: "Created", format: "date" },
      ],
    },
    {
      key: "version-history",
      label: "Version history",
      permission: p.plansReadVersionHistory,
      load: (d, page) => planApplicationApi.history(d.id, { page }),
      columns: [
        { key: "action", label: "Action" },
        { key: "kind", label: "Type" },
        { key: "actorLabel", label: "Operator" },
        { key: "productVersionNumber", label: "Revision" },
        { key: "occurredAt", label: "Date", format: "date" },
      ],
    },

    {
      key: "subscribers",
      label: "Subscribers",
      permission: p.plansListSubscribers,
      load: (d, page) => api.planSubscribers(d.id, { page, size: 20 }),
      columns: [
        {
          key: "accountName",
          label: "Customer",
          link: (r) => "/customers/" + r.accountId,
        },
        { key: "status", label: "Status", format: "status" },
        { key: "currentPeriodEnd", label: "Renewal", format: "date" },
      ],
    },
    {
      key: "revisions",
      label: "Revisions",
      permission: p.plansListVersions,
      load: async (d, page) => {
        const x = await api.planVersions(d.id, { page, size: 20 });
        return {
          ...x.versions,
          content: x.versions.content.map((revision) => ({
            ...revision,
            comparisonLabel: can(p.plansCompareVersions)
              ? revision.id === d.id
                ? "Current"
                : "Compare"
              : "—",
            comparisonPath:
              "/catalog/plans/" +
              d.id +
              "?view=revision-comparison&compare=" +
              revision.id,
          })),
        };
      },
      columns: [
        ...productColumns.map((c) =>
          c.key === "name"
            ? { ...c, link: (r: RecordData) => "/catalog/plans/" + r.id }
            : c,
        ),
        {
          key: "comparisonLabel",
          label: "Comparison",
          link: (r: RecordData) =>
            can(p.plansCompareVersions) && r.comparisonLabel === "Compare"
              ? r.comparisonPath
              : undefined,
        },
      ],
    },
    {
      key: "compatibility",
      label: "Compatible extensions",
      permission: p.commercialInspectCompatibility,
      load: (d, page) => api.inspectPlanCompatibility(d.id, { page, size: 20 }),
      columns: [
        { key: "name", label: "Extension" },
        { key: "type", label: "Type" },
        { key: "available", label: "Available", format: "boolean" },
        { key: "blockers", label: "Restrictions" },
      ],
    },
    {
      key: "history",
      label: "Availability history",
      permission: p.commercialReadHistory,
      load: (d, page) => api.commercialAvailabilityHistory(d.id, page),
      columns: compactHistory,
    },
  ],
};
const restrictions: Field[] = [
  planField("allowedPlanCodes", "Allowed plans", true, true),
  planField("blockedPlanCodes", "Blocked plans", true, true),
  addOnField("dependencyCodes", "Required add-ons", true),
  addOnField("exclusionCodes", "Excluded add-ons", true),
];
function extensionResource(quota: boolean): Resource {
  const key = quota ? "capacity" : "addons",
    base = "/catalog/" + key;
  const fields: Field[] = [
    ...commercialFields.map((f) =>
      ["price", "currencyCode", "billingCycle"].includes(f.key)
        ? { ...f, show: (d: RecordData) => !d.id }
        : f,
    ),
    ...(quota
      ? [
          featureField(),
          quotaResourceField(),
          num("capacityPerUnit", "Capacity per unit", true, 1),
          { key: "repeatable", label: "Repeatable", type: "checkbox" as const },
          num("maximumQuantity", "Maximum quantity", false, 1),
          planField("allowedPlanCodes", "Allowed plans", true, true),
          addOnField("allowedAddOnCodes", "Allowed add-ons", true),
        ]
      : restrictions),
    visibility,
  ];
  const ops = quota ? p.quotaPackagesReadOperations : p.addOnsReadOperations;
  const transition = quota ? p.quotaPackagesTransition : p.addOnsTransition;
  const detail = quota ? api.quotaPackage : api.addOn;
  const operations = quota ? api.quotaPackageOperations : api.addOnOperations;
  return {
    key,
    title: quota ? "Capacity packages" : "Add-ons",
    singular: quota ? "Capacity package" : "Add-on",
    base,
    listPermission: quota ? p.quotaPackagesList : p.addOnsList,
    readPermission: quota ? p.quotaPackagesReadDetail : p.addOnsReadDetail,
    list: (q) =>
      quota ? api.operationalQuotaPackages(q) : api.operationalAddOns(q),
    detail: async (id) => ({
      ...(await detail(id)),
      ...(can(ops) ? await read<any>(ops, () => operations(id)) : {}),
    }),
    columns: productColumns,
    detailKeys: quota
      ? [
          "code",
          "description",
          "revisionNumber",
          "featureCode",
          "resource",
          "capacityPerUnit",
          "repeatable",
          "maximumQuantity",
          "allowedPlanCodes",
          "allowedAddOnCodes",
          "salesVisibility",
        ]
      : [
          "code",
          "description",
          "revisionNumber",
          "allowedPlanCodes",
          "blockedPlanCodes",
          "dependencyCodes",
          "exclusionCodes",
          "salesVisibility",
        ],
    createPermission: quota ? p.quotaPackagesCreate : p.addOnsCreate,
    editPermission: quota ? p.quotaPackagesUpdate : p.addOnsUpdate,
    editable: allowed("EDIT_DRAFT", ["DRAFT"]),
    fields,
    defaults: {
      ...baseDefaults,
      allowedPlanCodes: [],
      blockedPlanCodes: [],
      dependencyCodes: [],
      exclusionCodes: [],
      allowedAddOnCodes: [],
      salesVisibility: "DIRECT_ONLY",
      repeatable: false,
    },
    save: (id, i, d) =>
      id
        ? quota
          ? api.updateQuotaPackage(id, {
              price: d!.price,
              currencyCode: d!.currencyCode,
              billingCycle: d!.billingCycle,
              ...i,
              expectedVersion: d!.version,
            } as any)
          : api.updateAddOn(id, {
              price: d!.price,
              currencyCode: d!.currencyCode,
              billingCycle: d!.billingCycle,
              ...i,
              expectedVersion: d!.version,
            } as any)
        : quota
          ? api.createQuotaPackage(i as any)
          : api.createAddOn(i as any),
    actions: [
      ...(["ACTIVATE", "DEACTIVATE", "ARCHIVE"] as const).map<Action>((a) => ({
        key: a,
        label:
          a === "ACTIVATE"
            ? "Activate"
            : a === "DEACTIVATE"
              ? "Deactivate"
              : "Archive",
        permission: transition,
        visible: allowed(
          a,
          a === "ACTIVATE"
            ? ["DRAFT", "INACTIVE"]
            : a === "DEACTIVATE"
              ? ["ACTIVE"]
              : ["DRAFT", "INACTIVE"],
        ),
        preview:
          a === "ACTIVATE"
            ? (d) =>
                read<any>(
                  quota
                    ? p.quotaPackagesPreviewActivation
                    : p.addOnsPreviewActivation,
                  () =>
                    quota
                      ? api.previewQuotaPackageActivation(d.id)
                      : api.previewAddOnActivation(d.id),
                )
            : undefined,
        execute: (d, i, r) =>
          quota
            ? api.changeQuotaPackageLifecycle(d.id, {
                action: a,
                expectedVersion: d.version,
                reason: i.reason,
                activationPreviewToken: r?.previewToken,
              })
            : api.changeAddOnLifecycle(d.id, {
                action: a,
                expectedVersion: d.version,
                reason: i.reason,
                activationPreviewToken: r?.previewToken,
              }),
      })),
      {
        key: "revise",
        label: "New revision",
        permission: quota ? p.quotaPackagesRevise : p.addOnsRevise,
        visible: allowed("REVISE", ["ACTIVE", "INACTIVE"]),
        execute: (d, i) =>
          quota
            ? api.reviseQuotaPackage(d.id, d.version, i.reason)
            : api.reviseAddOn(d.id, d.version),
        destination: (r) =>
          base +
          "/" +
          (r.id || r.successor?.id || r.revision?.id || r.successorId),
      },
      {
        key: "visibility",
        label: "Sales visibility",
        permission: quota
          ? p.commercialUpdateQuotaVisibility
          : p.commercialUpdateAddOnVisibility,
        fields: [visibility],
        defaults: (d) => d,
        preview: (d, i) =>
          read(
            quota
              ? p.commercialPreviewQuotaVisibility
              : p.commercialPreviewAddOnVisibility,
            () =>
              quota
                ? api.previewQuotaPackageVisibility(d.id, i.salesVisibility)
                : api.previewAddOnVisibility(d.id, i.salesVisibility),
          ),
        execute: (d, i, r) =>
          quota
            ? api.updateQuotaPackageVisibility(d.id, {
                ...i,
                expectedVersion: d.version,
                previewToken: r!.previewToken,
              } as any)
            : api.updateAddOnVisibility(d.id, {
                ...i,
                expectedVersion: d.version,
                previewToken: r!.previewToken,
              } as any),
      },
      ...(!quota
        ? [
            {
              key: "feature",
              label: "Add feature",
              permission: p.addOnsAssignFeature,
              visible: allowed("MANAGE_COMPOSITION", ["DRAFT"]),
              fields: [
                featureField(),
                {
                  key: "quotaConfigs",
                  label: "Quota",
                  type: "array" as const,
                  fields: quotaFields,
                  full: true,
                },
              ],
              reason: false,
              execute: (d: RecordData, i: RecordData) =>
                api.assignAddOnFeature(d.id, d.version, i as any),
            },
          ]
        : []),
      ...(quota
        ? [
            {
              key: "compare",
              label: "Compare revision",
              permission: p.quotaPackagesCompare,
              fields: [capacityField("target", "Compare with")],
              readOnly: true,
              reason: false,
              preview: (d: RecordData, i: RecordData) =>
                api.compareQuotaPackage(d.id, i.target),
              execute: async () => undefined,
            } as Action,
          ]
        : []),
      {
        key: "delete",
        label: "Delete draft",
        permission: quota ? p.quotaPackagesDelete : p.addOnsDelete,
        visible: allowed("DELETE_DRAFT", ["DRAFT"]),
        destructive: true,
        execute: (d) =>
          quota
            ? api.deleteQuotaPackage(d.id, d.version)
            : api.deleteAddOn(d.id, d.version),
        destination: () => "/catalog?view=" + key,
      },
    ],
    sections: [
      productPrices(quota ? "QUOTA_PACKAGE" : "ADD_ON"),
      ...(!quota
        ? [
            {
              key: "features",
              label: "Features",
              permission: p.addOnsReadDetail,
              load: (d: RecordData) => featureNames(d.features || []),
              columns: featureColumns,
              actions: (row: RecordData, d: RecordData): Action[] => [
                {
                  key: "edit",
                  label: "Edit",
                  permission: p.addOnsUpdateFeature,
                  visible: () => d.status === "DRAFT",
                  fields: [
                    featureField(),
                    {
                      key: "quotaConfigs",
                      label: "Quota",
                      type: "array",
                      fields: quotaFields,
                      full: true,
                    },
                  ],
                  defaults: () => row,
                  reason: false,
                  execute: (_, i) =>
                    api.updateAddOnFeature(d.id, row.id, d.version, i as any),
                },
                {
                  key: "remove",
                  label: "Remove",
                  permission: p.addOnsRemoveFeature,
                  visible: () => d.status === "DRAFT",
                  destructive: true,
                  execute: () =>
                    api.removeAddOnFeature(d.id, row.id, d.version),
                },
              ],
            },
          ]
        : []),
      {
        key: "history",
        label: "History",
        permission: quota ? p.quotaPackagesHistory : p.commercialReadHistory,
        load: (d, page) =>
          quota
            ? api.quotaPackageHistory(d.id, page)
            : api.commercialAvailabilityHistory(d.id, page),
        columns: compactHistory,
      },
    ],
  };
}
const priceFields: Field[] = [
  {
    ...select("ownerType", "Product type", ["PLAN", "ADD_ON", "QUOTA_PACKAGE"]),
    show: (d) => !d.id,
  },
  {
    ...planField("ownerId", "Plan"),
    show: (d) => !d.id && d.ownerType === "PLAN",
  },
  {
    ...addOnField("ownerId", "Add-on", false, false),
    show: (d) => !d.id && d.ownerType === "ADD_ON",
  },
  {
    key: "ownerId",
    label: "Capacity package",
    type: "choice",
    required: true,
    show: (d) => !d.id && d.ownerType === "QUOTA_PACKAGE",
    load: async (search, page) => {
      const x = await read(p.quotaPackagesChoose, () =>
        api.quotaPackageChoices({ search, page, size: 20 }),
      );
      return {
        options: x.content.map((r) => ({ value: r.id, label: r.name })),
        totalPages: x.totalPages,
      };
    },
  },
  decimal("amount", "Amount"),
  select("currencyCode", "Currency", currencies),
  select("billingCycle", "Billing cycle", cycles),
  dt("effectiveFrom", "Effective from", true),
  dt("effectiveUntil", "Effective until"),
];
const prices: Resource = {
  key: "prices",
  title: "Prices",
  singular: "Price",
  base: "/catalog/prices",
  listPermission: p.priceBooksList,
  readPermission: p.priceBooksRead,
  list: (q) => api.productPrices(q),
  detail: async (id) => {
    const current = await api.productPrice(id);
    const successors = can(p.priceBooksList)
      ? await read(p.priceBooksList, () =>
          api.productPrices({ sourcePriceId: id, size: 20 }),
        )
      : { content: [] };
    const scheduled = successors.content.find(
      (s) => s.status === "ACTIVE" && Date.parse(s.effectiveFrom) > Date.now(),
    );
    return {
      ...current,
      scheduledSuccessorId: scheduled?.id,
      scheduledSuccessorVersion: scheduled?.version,
    };
  },
  normalize: (d) => ({
    ...d,
    name: d.productName,
    ownerId: d.productId,
    ownerType: d.productType,
  }),
  columns: priceColumns,
  detailKeys: [
    "productName",
    "productCode",
    "amount",
    "currencyCode",
    "billingCycle",
    "effectiveFrom",
    "effectiveUntil",
    "revisionNumber",
  ],
  createPermission: p.priceBooksCreate,
  editPermission: p.priceBooksUpdateDraft,
  editable: (d) => d.status === "DRAFT",
  fields: priceFields,
  defaults: {
    ownerType: "PLAN",
    currencyCode: "MAD",
    billingCycle: "MONTHLY",
    amount: "0.00",
    effectiveFrom: new Date().toISOString(),
  },
  save: (id, i, d) =>
    id
      ? api.updateProductPrice(id, { ...i, version: d!.version } as any)
      : api.createProductPrice(i.ownerType, i.ownerId, i as any),
  actions: [
    ...(["activate", "reactivate"] as const).map<Action>((a) => ({
      key: a,
      label: a === "activate" ? "Activate" : "Reactivate",
      permission:
        a === "activate" ? p.priceBooksActivate : p.priceBooksReactivate,
      visible: allowed(a === "activate" ? "ACTIVATE" : "REACTIVATE"),
      preview: (d) =>
        read(p.priceBooksPreviewActivation, () =>
          api.previewProductPriceActivation(d.id),
        ),
      execute: (d, i, r) =>
        a === "activate"
          ? api.activateProductPrice(d.id, {
              version: d.version,
              reason: i.reason,
              activationPreviewToken: r!.previewToken,
            })
          : api.reactivateProductPrice(d.id, {
              version: d.version,
              reason: i.reason,
              activationPreviewToken: r!.previewToken,
            }),
    })),
    {
      key: "pause",
      label: "Pause",
      permission: p.priceBooksPause,
      visible: allowed("PAUSE"),
      execute: (d, i) => api.pauseProductPrice(d.id, d.version, i.reason),
    },
    {
      key: "revise",
      label: "New revision",
      permission: p.priceBooksRevise,
      visible: allowed("REVISE"),
      execute: (d, i) => api.reviseProductPrice(d.id, d.version, i.reason),
      destination: (r) => "/catalog/prices/" + r.id,
    },
    {
      key: "archive",
      label: "Archive",
      permission: p.priceBooksArchive,
      visible: allowed("ARCHIVE"),
      execute: (d, i) => api.archiveProductPrice(d.id, d.version, i.reason),
    },
    {
      key: "delete",
      label: "Delete draft",
      permission: p.priceBooksDeleteDraft,
      visible: allowed("DELETE_DRAFT"),
      destructive: true,
      execute: (d) => api.deleteProductPrice(d.id, d.version),
      destination: () => "/catalog?view=prices",
    },
    ...(["CHANGE", "RESCHEDULE", "CANCEL"] as const).map<Action>((a) => ({
      key: a,
      label:
        a === "CHANGE"
          ? "Change price"
          : a === "RESCHEDULE"
            ? "Reschedule change"
            : "Cancel scheduled change",
      permission:
        a === "CHANGE"
          ? p.priceBooksChange
          : a === "RESCHEDULE"
            ? p.priceBooksRescheduleChange
            : p.priceBooksCancelChange,
      visible: allowed(
        a === "CHANGE"
          ? "CHANGE_PRICE"
          : a === "RESCHEDULE"
            ? "RESCHEDULE_CHANGE"
            : "CANCEL_CHANGE",
      ),
      fields:
        a === "CANCEL"
          ? []
          : [
              select("timing", "Timing", ["NOW", "SCHEDULED"]),
              decimal("amount", "New amount"),
              {
                ...dt("effectiveFrom", "Effective from", true),
                show: (i) => i.timing === "SCHEDULED",
              },
            ],
      defaults: (d) => ({ timing: "NOW", amount: d.amount }),
      preview: (d, i) =>
        read(p.priceBooksPreviewChange, () =>
          api.previewProductPriceChange(d.id, {
            ...i,
            operation: a,
            currentVersion: d.version,
            scheduledPriceId: d.scheduledSuccessorId,
            scheduledVersion: d.scheduledSuccessorVersion,
          } as any),
        ),
      execute: (d, i, r) =>
        api.confirmProductPriceChange(d.id, {
          change: {
            ...i,
            operation: a,
            currentVersion: d.version,
            scheduledPriceId: d.scheduledSuccessorId,
            scheduledVersion: d.scheduledSuccessorVersion,
          } as any,
          previewToken: r!.previewToken,
          idempotencyKey: i._idempotencyKey,
        }),
      destination: (r) =>
        "/catalog/prices/" + (r.successorPrice?.id || r.previousPrice?.id),
    })),
    {
      key: "replace",
      label: "Schedule replacement",
      permission: p.priceBooksScheduleReplacement,
      fields: [priceField("currentPriceId", "Current price")],
      preview: async (d, i) => {
        const current = await read(p.priceBooksRead, () =>
          api.productPrice(i.currentPriceId),
        );
        return {
          ...(await read(p.priceBooksPreviewReplacement, () =>
            api.previewProductPriceReplacement(d.id, {
              currentPriceId: current.id,
              currentVersion: current.version,
              successorVersion: d.version,
            }),
          )),
          currentVersion: current.version,
        };
      },
      execute: (d, i, r) =>
        api.scheduleProductPriceReplacement(d.id, {
          currentPriceId: i.currentPriceId,
          currentVersion: r!.currentVersion,
          successorVersion: d.version,
          reason: i.reason,
        }),
    },
  ],
  sections: [
    {
      key: "history",
      label: "History",
      permission: p.priceBooksReadHistory,
      load: (d, page) => api.productPriceHistory(d.id, page),
      columns: compactHistory,
    },
  ],
};
export const catalogResources: Resource[] = [
  plans,
  extensionResource(false),
  extensionResource(true),
  prices,
];
