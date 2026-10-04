import type { Field, RecordData, Choice } from "./types";
import { get } from "./types";
import { read } from "@/data/gateway";
import { adminApi } from "@/api/admin-api";
import { adminOfferApi } from "@/api/admin-offer-api";
import { adminPermissions as p } from "@/auth/permissions";
import { can } from "@/data/session";
import { label, money } from "@/lib/format";
export const select = (
  key: string,
  title: string,
  values: string[],
  required = true,
): Field => ({
  key,
  label: title,
  type: "select",
  required,
  options: values.map((value) => ({ value, label: label(value) })),
});
export const text = (key: string, title: string, required = false): Field => ({
  key,
  label: title,
  required,
});
export const dt = (key: string, title: string, required = false): Field => ({
  key,
  label: title,
  type: "datetime",
  required,
});
export const num = (
  key: string,
  title: string,
  required = false,
  min = 0,
): Field => ({ key, label: title, type: "number", required, min });
export const decimal = (
  key: string,
  title: string,
  required = true,
): Field => ({ key, label: title, type: "decimal", required });
export const checks = (
  key: string,
  title: string,
  values: string[],
): Field => ({
  key,
  label: title,
  type: "choices",
  options: values.map((value) => ({ value, label: label(value) })),
});
export const currencies = ["MAD", "EUR", "USD", "GBP"];
export const cycles = ["MONTHLY", "YEARLY"];
export const subscriptionStatuses = [
  "ACTIVE",
  "TRIALING",
  "PAST_DUE",
  "SUSPENDED",
  "CANCELLED",
];
export const commercialFields: Field[] = [
  text("name", "Name", true),
  { key: "description", label: "Description", type: "textarea", full: true },
  decimal("price", "Initial price"),
  select("currencyCode", "Currency", currencies),
  select("billingCycle", "Billing cycle", cycles),
];
export const baseDefaults = {
  name: "",
  description: null,
  price: "0.00",
  currencyCode: "MAD",
  billingCycle: "MONTHLY",
};
function choices(result: any, id = "id", display = "name") {
  return {
    options: (result.content || result).map((x: any) => ({
      value: String(x[id]),
      label: String(x[display] || x.code || x.id),
    })),
    totalPages: result.totalPages || 1,
  };
}
export const accountField = (
  key = "accountIds",
  title = "Customers",
  one = false,
  kind = "subscriptions",
): Field => ({
  key,
  label: title,
  type: one ? "choice" : "choices",
  full: true,
  required: one,
  resolve: async (ids) => {
    if (!ids.length) return [];
    const lookup: Record<
      string,
      { permission: string; load: () => Promise<any> }
    > = {
      campaigns: {
        permission: p.campaignsResolveAccountChoices,
        load: () => adminApi.resolveCommercialCampaignAccountChoices(ids),
      },
      segments: {
        permission: p.segmentsResolveAccountChoices,
        load: () => adminApi.resolveCommercialSegmentAccountChoices(ids),
      },
      policies: {
        permission: p.commercialPoliciesResolveAccountChoices,
        load: () => adminApi.resolveCommercialPolicyAccountChoices(ids),
      },
      offers: {
        permission: p.offersResolveAccountChoices,
        load: () => adminOfferApi.resolveAccountChoices(ids),
      },
      subscriptions: {
        permission: p.subscriptionsResolveAccountChoices,
        load: () => adminApi.resolveSubscriptionAccounts(ids),
      },
    };
    const endpoint = lookup[kind]!;
    return can(endpoint.permission)
      ? choices(await read(endpoint.permission, endpoint.load)).options
      : [];
  },
  load: async (search, page) => {
    if (kind === "campaigns")
      return choices(
        await read(p.campaignsChooseAccounts, () =>
          adminApi.commercialCampaignAccountChoices({
            query: search,
            page,
            size: 20,
          }),
        ),
      );
    if (kind === "segments")
      return choices(
        await read(p.segmentsChooseAccounts, () =>
          adminApi.commercialSegmentAccountChoices({
            query: search,
            page,
            size: 20,
          }),
        ),
      );
    if (kind === "policies")
      return choices(
        await read(p.commercialPoliciesChooseAccounts, () =>
          adminApi.commercialPolicyAccountChoices({
            query: search,
            page,
            size: 20,
          }),
        ),
      );
    if (kind === "offers")
      return choices(
        await read(p.offersChooseAccounts, () =>
          adminOfferApi.accountChoices({ search, page, size: 20 }),
        ),
      );
    return choices(
      await read(p.subscriptionsChooseAccounts, () =>
        adminApi.chooseSubscriptionAccounts({ query: search, page, size: 20 }),
      ),
    );
  },
});
export const planField = (
  key: string,
  title: string,
  byCode = false,
  multiple = false,
): Field => ({
  key,
  label: title,
  type: multiple ? "choices" : "choice",
  full: true,
  required: !multiple,
  resolve: async (ids) => {
    if (
      !ids.length ||
      !can(byCode ? p.plansResolveChoiceCodes : p.plansResolveChoices)
    )
      return [];
    const x = await read(
      byCode ? p.plansResolveChoiceCodes : p.plansResolveChoices,
      () =>
        byCode
          ? adminApi.selectedPlanCodeChoices(ids)
          : adminApi.selectedPlanChoices(ids),
    );
    return x.map((r) => ({
      value: byCode ? r.code : r.id,
      label: r.name + " · revision " + r.revisionNumber,
    }));
  },
  load: async (search, page) => {
    const result = await read(p.plansChoose, () =>
      adminApi.planChoices({ search, page, size: 20 }),
    );
    return {
      options: result.content.map((r) => ({
        value: byCode ? r.code : r.id,
        label:
          r.name + " · revision " + r.revisionNumber + " · " + label(r.status),
      })),
      totalPages: result.totalPages,
    };
  },
});
export const addOnField = (
  key: string,
  title: string,
  byCode = false,
  multiple = true,
): Field => ({
  key,
  label: title,
  type: multiple ? "choices" : "choice",
  full: true,
  resolve: async (ids) => {
    if (
      !ids.length ||
      !can(byCode ? p.addOnsResolveChoiceCodes : p.addOnsResolveChoices)
    )
      return [];
    const x = await read(
      byCode ? p.addOnsResolveChoiceCodes : p.addOnsResolveChoices,
      () =>
        byCode
          ? adminApi.selectedAddOnCodeChoices(ids)
          : adminApi.selectedAddOnChoices(ids),
    );
    return x.map((r) => ({
      value: byCode ? r.code : r.id,
      label: r.name + " · revision " + r.revisionNumber,
    }));
  },
  load: async (search, page) =>
    choices(
      await read(p.addOnsChoose, () =>
        adminApi.addOnChoices({ search, page, size: 20 }),
      ),
      byCode ? "code" : "id",
    ),
});
export const featureField = (key = "featureCode"): Field => ({
  key,
  label: "Feature",
  type: "choice",
  required: true,
  full: true,
  load: async (search) => {
    const modules = await read(p.registryFeatureCatalog, () =>
      adminApi.featureCatalog("PLAN_ASSIGNABLE"),
    );
    const features = modules
      .flatMap((m) => m.features)
      .filter((f) =>
        (f.displayName + " " + f.code)
          .toLowerCase()
          .includes(search.toLowerCase()),
      );
    return choices(features, "code", "displayName");
  },
});
export const priceField = (
  key: string,
  title: string,
  ownerKey?: string,
  ownerType?: string,
): Field => ({
  key,
  label: title,
  type: "choice",
  required: true,
  full: true,
  contextKeys: ownerKey ? [ownerKey] : undefined,
  resolve: async (ids) =>
    can(p.priceBooksRead)
      ? Promise.all(
          ids.map(async (id) => {
            const x = await read(p.priceBooksRead, () =>
              adminApi.productPrice(id),
            );
            return {
              value: x.id,
              label:
                x.productName +
                " · " +
                money(x.amount, x.currencyCode) +
                " · " +
                label(x.billingCycle),
            };
          }),
        )
      : [],
  load: async (search, page, data) => {
    const result = await read(p.priceBooksList, () =>
      adminApi.productPrices({
        page,
        size: 20,
        search,
        ownerId: ownerKey ? get(data, ownerKey) : undefined,
        ownerType: ownerType as any,
      }),
    );
    return {
      options: result.content.map((x) => ({
        value: x.id,
        record: x,
        label:
          x.productName +
          " · " +
          money(x.amount, x.currencyCode) +
          " · " +
          label(x.billingCycle) +
          " · " +
          label(x.status),
      })),
      totalPages: result.totalPages,
    };
  },
});
export const quotaResourceField = (
  key = "resource",
  required = true,
): Field => ({
  key,
  label: "Quota resource",
  type: "choice",
  required,
  contextKeys: ["featureCode"],
  full: true,
  load: async (search, _page, data) => {
    if (!data.featureCode) return { options: [], totalPages: 1 };
    const modules = await read(p.registryFeatureCatalog, () =>
      adminApi.featureCatalog("PLAN_ASSIGNABLE"),
    );
    const feature = modules
      .flatMap((x) => x.features)
      .find((x) => x.code === data.featureCode);
    return {
      options: (feature?.quotaSchema || [])
        .filter((x) =>
          (x.resource + " " + x.unit)
            .toLowerCase()
            .includes(search.toLowerCase()),
        )
        .map((x) => ({
          value: x.resource,
          label: label(x.resource) + " · " + x.unit,
        })),
      totalPages: 1,
    };
  },
});
export const permissionField = (key = "permissionIds"): Field => ({
  key,
  label: "Permissions",
  type: "choices",
  full: true,
  load: async (search) => {
    const result = await read(
      p.rolesListGrantable,
      adminApi.grantableRolePermissions,
    );
    return choices(
      result.filter((x) =>
        (x.name + " " + x.code).toLowerCase().includes(search.toLowerCase()),
      ),
    );
  },
});
export const quotaFields: Field[] = [
  quotaResourceField(),
  select("mode", "Limit", ["FINITE", "UNLIMITED"]),
  { ...num("limit", "Quantity", true), show: (d) => d.mode === "FINITE" },
];
export const planFeatureFields: Field[] = [
  featureField(),
  select("mode", "Availability", [
    "INCLUDED",
    "OPTIONAL_ADD_ON",
    "BLOCKED_FOR_PLAN",
  ]),
  {
    key: "quotaConfigs",
    label: "Quota",
    type: "array",
    fields: quotaFields,
    defaults: { resource: "", mode: "FINITE", limit: 0 },
    full: true,
  },
];
export const compactHistory = [
  { key: "action", label: "Action" },
  { key: "outcome", label: "Outcome", format: "status" as const },
  { key: "reason", label: "Reason" },
  { key: "occurredAt", label: "Date", format: "date" as const },
];
export const root = (d: RecordData) => ({ ...d.summary, ...d });
export const allowed = (key: string, states?: string[]) => (d: RecordData) =>
  Array.isArray(d.availableActions)
    ? d.availableActions.includes(key)
    : states
      ? states.includes(d.status || d.state)
      : true;
export const fieldDefaults = (fields: Field[]) =>
  Object.fromEntries(
    fields.map((f) => [
      f.key,
      f.type === "choices" || f.type === "array"
        ? []
        : f.type === "checkbox"
          ? false
          : null,
    ]),
  );

export const capacityField = (
  key: string,
  title: string,
  byCode = false,
  multiple = false,
): Field => ({
  key,
  label: title,
  type: multiple ? "choices" : "choice",
  required: !multiple,
  full: true,
  load: async (search, page) => {
    const x = await read(p.quotaPackagesChoose, () =>
      adminApi.quotaPackageChoices({ search, page, size: 20 }),
    );
    return {
      options: x.content.map((r) => ({
        value: byCode ? r.code : r.id,
        label: r.name,
      })),
      totalPages: x.totalPages,
    };
  },
});
