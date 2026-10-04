import { money, label } from "@/lib/format";
import { adminApi as api } from "@/api/admin-api";
import { adminOfferApi as offers } from "@/api/admin-offer-api";
import { adminPermissions as p } from "@/auth/permissions";
import { can } from "@/data/session";
import { read } from "@/data/gateway";
import type {
  Resource,
  Action,
  Field,
  Column,
  RecordData,
  Section,
} from "./types";
import {
  capacityField,
  text,
  dt,
  num,
  decimal,
  select,
  checks,
  accountField,
  planField,
  addOnField,
  priceField,
  featureField,
  quotaResourceField,
  root,
  allowed,
  compactHistory,
  currencies,
  cycles,
  subscriptionStatuses,
} from "./fields";
const columns: Column[] = [
  { key: "name", label: "Name" },
  { key: "status", label: "Status", format: "status" },
  { key: "revisionNumber", label: "Revision" },
  { key: "startsAt", label: "Starts", format: "date" },
  { key: "endsAt", label: "Ends", format: "date" },
];
const reason: Field = {
  key: "reason",
  label: "Reason",
  type: "textarea",
  required: true,
  full: true,
};
const common: Field[] = [
  text("name", "Name", true),
  { key: "description", label: "Description", type: "textarea", full: true },
  reason,
];
const choose = (
  key: string,
  title: string,
  permission: string,
  load: (q: string, page: number) => Promise<any>,
  value: (r: any) => string = (r) => r.id,
): Field => ({
  key,
  label: title,
  type: "choice",
  required: true,
  full: true,
  load: async (q, page) => {
    const x = await read(permission, () => load(q, page));
    return {
      options: x.content.map((r: any) => ({
        value: value(r),
        label: r.name || r.displayName || r.email,
      })),
      totalPages: x.totalPages,
    };
  },
});
const ownerField = choose(
  "ownerAdminUserId",
  "Owner",
  p.campaignsChooseOwners,
  (query, page) =>
    api.commercialCampaignOwnerChoices({ query, page, size: 20 }),
);
const ownerAction = (
  permission: string,
  load: (id: string) => Promise<any>,
  save: (id: string, i: any) => Promise<any>,
  field: Field = ownerField,
): Action => ({
  key: "owner",
  label: "Change owner",
  permission,
  visible: allowed("REASSIGN_OWNER"),
  fields: [field],
  preview: (d) => load(d.id),
  execute: (d, i, r) =>
    save(d.id, { ...i, version: d.version, lineageVersion: r?.lineageVersion }),
});
function lifecycle(
  key: string,
  permission: string,
  label: string,
  execute: (id: string, i: any) => Promise<any>,
  previewPermission?: string,
  preview?: (id: string) => Promise<any>,
  tokenKey = "previewToken",
): Action {
  return {
    key,
    label,
    permission,
    visible: allowed(key),
    destructive: key === "DELETE_DRAFT",
    preview:
      preview && previewPermission
        ? (d) => read(previewPermission, () => preview(d.id))
        : undefined,
    execute: (d, i, r) =>
      execute(d.id, {
        version: d.version,
        reason: i.reason,
        ...(r ? { [tokenKey]: r.previewToken } : {}),
      }),
  };
}
function branches(
  base: string,
  duplicatePermission: string,
  revisePermission: string,
  duplicate: (id: string, i: any) => Promise<any>,
  revise: (id: string, i: any) => Promise<any>,
): Action[] {
  return [
    {
      key: "duplicate",
      label: "Duplicate",
      permission: duplicatePermission,
      visible: allowed("DUPLICATE"),
      fields: [text("name", "Name", true)],
      defaults: (d) => ({ name: d.name + " copy" }),
      execute: (d, i) => duplicate(d.id, { ...i, version: d.version }),
      destination: (r) =>
        base + "/" + (r.id || r.summary?.id || r.offerId || r.campaignId),
    },
    {
      key: "revise",
      label: "New revision",
      permission: revisePermission,
      visible: allowed("REVISE"),
      execute: (d, i) => revise(d.id, { version: d.version, reason: i.reason }),
      destination: (r) =>
        base + "/" + (r.id || r.summary?.id || r.offerId || r.campaignId),
    },
  ];
}
function history(
  permission: string,
  load: (id: string, page: number) => Promise<any>,
): Section {
  return {
    key: "history",
    label: "History",
    permission,
    load: (d, page) => load(d.id, page),
    columns: compactHistory,
  };
}
function revisions(
  base: string,
  permission: string,
  load: (id: string, page: number) => Promise<any>,
  comparePermission: string,
  compare: (id: string, target: string) => Promise<any>,
): Section {
  return {
    key: "revisions",
    label: "Revisions",
    permission,
    load: (d, page) => load(d.id, page),
    columns: [
      {
        key: "revisionNumber",
        label: "Revision",
        link: (r) => base + "/" + r.id,
      },
      { key: "code", label: "Code" },
      { key: "status", label: "Status", format: "status" },
      { key: "createdAt", label: "Created", format: "date" },
    ],
    actions: (row, d) => [
      {
        key: "compare",
        label: "Compare",
        permission: comparePermission,
        reason: false,
        readOnly: true,
        preview: () => compare(d.id, row.id),
        execute: async () => undefined,
      },
    ],
  };
}
const campaignFields: Field[] = [
  ...common,
  dt("startsAt", "Starts", true),
  dt("endsAt", "Ends", true),
  select("source", "Source", [
    "MARKETING",
    "SALES",
    "RETENTION",
    "SUPPORT",
    "MANUAL",
  ]),
  select("audience.mode", "Audience", [
    "PUBLIC",
    "EXPLICIT_ACCOUNTS",
    "SEGMENT",
  ]),
  {
    ...accountField(
      "audience.explicitAccountIds",
      "Customers",
      false,
      "campaigns",
    ),
    show: (d) => d.audience?.mode === "EXPLICIT_ACCOUNTS",
  },
  {
    ...choose(
      "segmentSelection",
      "Segment activation",
      p.campaignsChooseSegments,
      (query, page) =>
        api.commercialCampaignSegmentChoices({ query, page, size: 20 }),
      (r) => r.id + ":" + r.activationId,
    ),
    show: (d) => d.audience?.mode === "SEGMENT",
  },
];
const campaigns: Resource = {
  key: "campaigns",
  title: "Campaigns",
  singular: "Campaign",
  base: "/commercial/campaigns",
  listPermission: p.campaignsList,
  readPermission: p.campaignsRead,
  list: (q) => api.commercialCampaigns(q),
  detail: api.commercialCampaign,
  alternateDetail: [
    {
      permission: p.campaignsReadEditableDefinition,
      load: api.commercialCampaignEditableDefinition,
    },
  ],
  normalize: (d) => ({
    ...root(d),
    segmentSelection:
      d.audience?.segmentId + ":" + d.audience?.segmentActivationId,
  }),
  columns,
  createPermission: p.campaignsCreate,
  editPermission: p.campaignsUpdate,
  editable: allowed("UPDATE"),
  fields: campaignFields,
  defaults: {
    source: "MANUAL",
    audience: {
      mode: "PUBLIC",
      explicitAccountIds: [],
      segmentId: null,
      segmentActivationId: null,
    },
  },
  save: (id, i, d) => {
    const [segmentId, segmentActivationId] = (i.segmentSelection || "").split(
      ":",
    );
    delete i.segmentSelection;
    i.audience = {
      explicitAccountIds: [],
      segmentId: null,
      segmentActivationId: null,
      ...i.audience,
      ...(i.audience.mode === "SEGMENT"
        ? { segmentId, segmentActivationId }
        : {}),
    };
    return id
      ? api.updateCommercialCampaign(id, { ...i, version: d!.version } as any)
      : api.createCommercialCampaign(i as any);
  },
  actions: [
    ...branches(
      "/commercial/campaigns",
      p.campaignsDuplicate,
      p.campaignsRevise,
      api.duplicateCommercialCampaign,
      api.reviseCommercialCampaign,
    ),
    lifecycle(
      "SCHEDULE",
      p.campaignsSchedule,
      "Schedule",
      api.scheduleCommercialCampaign,
      p.campaignsPreviewSchedule,
      api.previewCommercialCampaignSchedule,
    ),
    lifecycle("PAUSE", p.campaignsPause, "Pause", api.pauseCommercialCampaign),
    lifecycle(
      "RESUME",
      p.campaignsResume,
      "Resume",
      api.resumeCommercialCampaign,
    ),
    lifecycle("END", p.campaignsEnd, "End", api.endCommercialCampaign),
    lifecycle(
      "ARCHIVE",
      p.campaignsArchive,
      "Archive",
      api.archiveCommercialCampaign,
    ),
    {
      ...lifecycle(
        "DELETE_DRAFT",
        p.campaignsDeleteDraft,
        "Delete draft",
        api.deleteCommercialCampaign,
      ),
      destination: () => "/commercial",
    },
    ownerAction(
      p.campaignsReassignOwner,
      (id) => read(p.campaignsOwner, () => api.commercialCampaignOwner(id)),
      api.reassignCommercialCampaignOwner,
    ),
  ],
  sections: [
    history(p.campaignsHistory, api.commercialCampaignHistory),
    revisions(
      "/commercial/campaigns",
      p.campaignsRevisions,
      api.commercialCampaignRevisions,
      p.campaignsCompare,
      api.compareCommercialCampaigns,
    ),
    {
      key: "audience",
      label: "Audience",
      permission: p.campaignsReadAudience,
      load: async (d, page) => {
        const result = await api.commercialCampaignAudience(d.id, page);
        if (can(p.campaignsReadAudienceIdentities)) {
          const identities = await read(p.campaignsReadAudienceIdentities, () =>
            api.commercialCampaignAudienceIdentities(d.id, page),
          );
          return { ...result, accounts: identities.accounts };
        }
        return result;
      },
      columns: [
        {
          key: "accountId",
          label: "Customer",
          link: (r) => "/customers/" + r.accountId,
        },
        { key: "accountName", label: "Name" },
      ],
    },
    {
      key: "identities",
      label: "Customer identities",
      permission: p.campaignsReadAudienceIdentities,
      load: (d, page) => api.commercialCampaignAudienceIdentities(d.id, page),
    },
    {
      key: "owner",
      label: "Owner",
      permission: p.campaignsOwner,
      load: (d) => api.commercialCampaignOwner(d.id),
    },
  ],
};
const criteria: Field[] = [
  planField("definition.criteria.currentPlanRevisionIds", "Plans", false, true),
  checks(
    "definition.criteria.subscriptionStatuses",
    "Subscription statuses",
    subscriptionStatuses,
  ),
  checks("definition.criteria.currencyCodes", "Currencies", currencies),
  checks("definition.criteria.billingCycles", "Billing cycles", cycles),
  dt("definition.criteria.accountCreatedFrom", "Customer created from"),
  dt("definition.criteria.accountCreatedUntil", "Customer created until"),
  {
    key: "definition.criteria.productHoldings",
    label: "Product holdings",
    type: "array",
    fields: [
      select("type", "Product type", ["PLAN", "ADD_ON", "QUOTA_PACKAGE"]),
      {
        ...planField("code", "Plan", true),
        show: (d) => d.type === "PLAN",
        contextKeys: ["type"],
      },
      {
        ...addOnField("code", "Add-on", true, false),
        required: true,
        show: (d) => d.type === "ADD_ON",
        contextKeys: ["type"],
      },
      {
        ...capacityField("code", "Capacity package", true),
        show: (d) => d.type === "QUOTA_PACKAGE",
        contextKeys: ["type"],
      },
    ],
    full: true,
  },
];
const segments: Resource = {
  key: "segments",
  title: "Segments",
  singular: "Segment",
  base: "/commercial/segments",
  listPermission: p.segmentsList,
  readPermission: p.segmentsRead,
  list: (q) => api.commercialSegments(q),
  detail: api.commercialSegment,
  normalize: root,
  columns: [
    { key: "name", label: "Name" },
    { key: "status", label: "Status", format: "status" },
    { key: "kind", label: "Audience type" },
    { key: "latestActivationAccountCount", label: "Activated customers" },
    { key: "revisionNumber", label: "Revision" },
  ],
  createPermission: p.segmentsCreate,
  editPermission: p.segmentsUpdateDraft,
  editable: allowed("EDIT_DRAFT"),
  fields: [
    ...common,
    select("kind", "Audience type", ["EXPLICIT_ACCOUNTS", "TYPED_CRITERIA"]),
    {
      ...accountField(
        "definition.explicitAccountIds",
        "Customers",
        false,
        "segments",
      ),
      show: (d) => d.kind === "EXPLICIT_ACCOUNTS",
    },
    ...criteria.map((f) => ({
      ...f,
      show: (d: RecordData) => d.kind === "TYPED_CRITERIA",
    })),
  ],
  defaults: {
    kind: "EXPLICIT_ACCOUNTS",
    definition: {
      explicitAccountIds: [],
      criteria: {
        currentPlanRevisionIds: [],
        subscriptionStatuses: [],
        currencyCodes: [],
        billingCycles: [],
        accountCreatedFrom: null,
        accountCreatedUntil: null,
        productHoldings: [],
      },
    },
  },
  save: (id, i, d) => {
    i.definition = { explicitAccountIds: [], criteria: null, ...i.definition };
    return id
      ? api.updateCommercialSegment(id, { ...i, version: d!.version } as any)
      : api.createCommercialSegment(i as any);
  },
  actions: [
    ...branches(
      "/commercial/segments",
      p.segmentsDuplicate,
      p.segmentsRevise,
      api.duplicateCommercialSegment,
      api.reviseCommercialSegment,
    ),
    lifecycle(
      "ACTIVATE",
      p.segmentsActivate,
      "Activate",
      api.activateCommercialSegment,
      p.segmentsPreview,
      api.previewCommercialSegment,
    ),
    lifecycle(
      "ARCHIVE",
      p.segmentsArchive,
      "Archive",
      api.archiveCommercialSegment,
    ),
    {
      ...lifecycle(
        "DELETE_DRAFT",
        p.segmentsDeleteDraft,
        "Delete draft",
        api.deleteCommercialSegment,
      ),
      destination: () => "/commercial?view=segments",
    },
    ownerAction(
      p.segmentsReassignOwner,
      (id) => read(p.segmentsReadOwner, () => api.commercialSegmentOwner(id)),
      api.reassignCommercialSegmentOwner,
    ),
  ],
  sections: [
    {
      key: "count",
      label: "Audience count",
      permission: p.segmentsCount,
      load: (d) => api.countCommercialSegment(d.id),
    },
    {
      key: "audience",
      label: "Audience preview",
      permission: p.segmentsPreview,
      load: (d) => api.previewCommercialSegment(d.id),
    },
    {
      key: "identities",
      label: "Customer identities",
      permission: p.segmentsReadSampleIdentities,
      load: (d) => api.commercialSegmentIdentitySample(d.id),
    },
    history(p.segmentsReadHistory, api.commercialSegmentHistory),
    revisions(
      "/commercial/segments",
      p.segmentsReadRevisions,
      api.commercialSegmentRevisions,
      p.segmentsCompare,
      api.compareCommercialSegments,
    ),
    {
      key: "activations",
      label: "Activations",
      permission: p.segmentsReadActivations,
      load: (d, page) => api.commercialSegmentActivations(d.id, page),
      columns: [
        { key: "activationNumber", label: "Activation" },
        { key: "affectedAccountCount", label: "Customers" },
        { key: "recordedAt", label: "Date", format: "date" },
      ],
      actions: (row, d) => [
        {
          key: "audience",
          label: "Audience",
          permission: p.segmentsReadActivationAudience,
          readOnly: true,
          paginated: true,
          reason: false,
          preview: (_, i) =>
            api.commercialSegmentActivationAudience(d.id, row.id, i.page),
          execute: async () => undefined,
        },
        {
          key: "identities",
          label: "Customer names",
          permission: p.segmentsReadActivationIdentities,
          readOnly: true,
          paginated: true,
          reason: false,
          preview: (_, i) =>
            api.commercialSegmentActivationIdentities(d.id, row.id, i.page),
          execute: async () => undefined,
        },
      ],
    },
    {
      key: "owner",
      label: "Owner",
      permission: p.segmentsReadOwner,
      load: (d) => api.commercialSegmentOwner(d.id),
    },
  ],
};
const productEffect = (d: RecordData) =>
  [
    "ALLOW_PRODUCT_SELECTION",
    "BLOCK_PRODUCT_SELECTION",
    "GRANT_ADD_ON",
    "GRANT_QUOTA_PACKAGE",
  ].includes(d.type);
const effectFields: Field[] = [
  select("type", "Effect", [
    "FIXED_SUBSCRIPTION_PRICE",
    "FIXED_DISCOUNT",
    "PERCENTAGE_DISCOUNT",
    "ALLOW_PRODUCT_SELECTION",
    "BLOCK_PRODUCT_SELECTION",
    "ADDITIVE_QUOTA_BONUS",
    "GRANT_ADD_ON",
    "GRANT_QUOTA_PACKAGE",
    "BLOCK_FEATURE",
  ]),
  {
    ...select("productType", "Product type", [
      "PLAN",
      "ADD_ON",
      "QUOTA_PACKAGE",
    ]),
    show: (d) =>
      ["ALLOW_PRODUCT_SELECTION", "BLOCK_PRODUCT_SELECTION"].includes(d.type),
  },
  {
    ...planField("productRevisionId", "Plan"),
    show: (d) => productEffect(d) && d.productType === "PLAN",
  },
  {
    ...addOnField("productRevisionId", "Add-on", false, false),
    required: true,
    show: (d) =>
      d.type === "GRANT_ADD_ON" ||
      (productEffect(d) && d.productType === "ADD_ON"),
  },
  {
    ...capacityField("productRevisionId", "Capacity package"),
    show: (d) =>
      d.type === "GRANT_QUOTA_PACKAGE" ||
      (productEffect(d) && d.productType === "QUOTA_PACKAGE"),
  },
  {
    ...featureField(),
    show: (d) => ["ADDITIVE_QUOTA_BONUS", "BLOCK_FEATURE"].includes(d.type),
  },
  {
    ...quotaResourceField("quotaResource"),
    show: (d) => d.type === "ADDITIVE_QUOTA_BONUS",
  },
  {
    ...num("quantityDelta", "Quantity", true, 1),
    show: (d) => d.type === "ADDITIVE_QUOTA_BONUS",
  },
  {
    ...decimal("amount", "Amount"),
    show: (d) =>
      ["FIXED_SUBSCRIPTION_PRICE", "FIXED_DISCOUNT"].includes(d.type),
  },
  {
    ...select("currencyCode", "Currency", currencies),
    show: (d) =>
      ["FIXED_SUBSCRIPTION_PRICE", "FIXED_DISCOUNT"].includes(d.type),
  },
  {
    ...select("billingCycle", "Billing cycle", cycles),
    show: (d) => d.type === "FIXED_SUBSCRIPTION_PRICE",
  },
  {
    ...decimal("percentage", "Percentage"),
    show: (d) => d.type === "PERCENTAGE_DISCOUNT",
  },
  {
    ...decimal("maximumAmount", "Maximum discount"),
    show: (d) => d.type === "PERCENTAGE_DISCOUNT",
  },
  {
    ...select("maximumCurrencyCode", "Currency", currencies),
    show: (d) => d.type === "PERCENTAGE_DISCOUNT",
  },
];
const policies: Resource = {
  key: "policies",
  title: "Policies",
  singular: "Policy",
  base: "/commercial/policies",
  listPermission: p.commercialPoliciesList,
  readPermission: p.commercialPoliciesRead,
  list: (q) => api.commercialPolicies(q),
  detail: api.commercialPolicy,
  normalize: root,
  columns: [
    { key: "name", label: "Name" },
    { key: "status", label: "Status", format: "status" },
    { key: "targetLabel", label: "Target" },
    { key: "effectCount", label: "Effects" },
    { key: "effectiveFrom", label: "Starts", format: "date" },
  ],
  createPermission: p.commercialPoliciesCreate,
  editPermission: p.commercialPoliciesUpdateDraft,
  editable: allowed("EDIT_DRAFT"),
  fields: [
    ...common,
    dt("effectiveFrom", "Starts", true),
    dt("effectiveUntil", "Ends"),
    select("source", "Source", [
      "CONTRACT",
      "SALES",
      "MARKETING",
      "RETENTION",
      "SUPPORT",
      "COMPLIANCE",
      "OTHER",
    ]),
    num("priority", "Priority", true),
    text("approvalReference", "Approval reference"),
    text("contractReference", "Contract reference"),
    select("target.kind", "Target", [
      "ACCOUNT",
      "ACCOUNT_SET",
      "SEGMENT",
      "PLAN_REVISION_SUBSCRIBERS",
    ]),
    {
      ...accountField("target.accountId", "Customer", true, "policies"),
      show: (d) => d.target?.kind === "ACCOUNT",
    },
    {
      ...accountField("target.accountIds", "Customers", false, "policies"),
      show: (d) => d.target?.kind === "ACCOUNT_SET",
    },
    {
      ...planField("target.planRevisionId", "Plan revision"),
      show: (d) => d.target?.kind === "PLAN_REVISION_SUBSCRIBERS",
    },
    {
      ...choose(
        "target.segmentReference",
        "Segment",
        p.commercialPoliciesChooseSegments,
        (query, page) =>
          api.commercialPolicySegmentChoices({ query, page, size: 20 }),
      ),
      show: (d) => d.target?.kind === "SEGMENT",
    },
    {
      key: "effects",
      label: "Effects",
      type: "array",
      fields: effectFields,
      defaults: { type: "FIXED_DISCOUNT" },
      full: true,
      required: true,
    },
  ],
  defaults: {
    source: "OTHER",
    priority: 0,
    target: { kind: "ACCOUNT_SET", accountIds: [] },
    effects: [],
    effectiveFrom: new Date().toISOString(),
  },
  save: (id, i, d) => {
    for (const effect of i.effects || []) {
      if (effect.type === "GRANT_ADD_ON") effect.productType = "ADD_ON";
      if (effect.type === "GRANT_QUOTA_PACKAGE")
        effect.productType = "QUOTA_PACKAGE";
    }
    i.target = {
      accountId: null,
      accountIds: [],
      planRevisionId: null,
      segmentReference: null,
      ...i.target,
    };
    return id
      ? api.updateCommercialPolicy(id, { ...i, version: d!.version } as any)
      : api.createCommercialPolicy(i as any);
  },
  actions: [
    ...branches(
      "/commercial/policies",
      p.commercialPoliciesDuplicate,
      p.commercialPoliciesRevise,
      api.duplicateCommercialPolicy,
      api.reviseCommercialPolicy,
    ),
    lifecycle(
      "ACTIVATE",
      p.commercialPoliciesActivate,
      "Activate",
      api.activateCommercialPolicy,
      p.commercialPoliciesPreviewActivation,
      api.previewCommercialPolicyActivation,
      "activationPreviewToken",
    ),
    lifecycle(
      "RESUME",
      p.commercialPoliciesResume,
      "Resume",
      api.resumeCommercialPolicy,
      p.commercialPoliciesPreviewActivation,
      api.previewCommercialPolicyActivation,
      "activationPreviewToken",
    ),
    lifecycle(
      "PAUSE",
      p.commercialPoliciesPause,
      "Pause",
      api.pauseCommercialPolicy,
    ),
    lifecycle("END", p.commercialPoliciesEnd, "End", api.endCommercialPolicy),
    lifecycle(
      "ARCHIVE",
      p.commercialPoliciesArchive,
      "Archive",
      api.archiveCommercialPolicy,
    ),
    {
      ...lifecycle(
        "DELETE_DRAFT",
        p.commercialPoliciesDeleteDraft,
        "Delete draft",
        api.deleteCommercialPolicy,
      ),
      destination: () => "/commercial?view=policies",
    },
    ownerAction(
      p.commercialPoliciesReassignOwner,
      (id) =>
        read(p.commercialPoliciesReadOwner, () =>
          api.commercialPolicyOwner(id),
        ),
      api.reassignCommercialPolicyOwner,
    ),
  ],
  sections: [
    {
      key: "audience",
      label: "Audience",
      permission: p.commercialPoliciesPreviewAudience,
      load: (d, page) => api.commercialPolicyAudience(d.id, page),
      columns: [
        {
          key: "name",
          label: "Customer",
          link: (r) => "/customers/" + r.id,
        },
        { key: "active", label: "Active", format: "boolean" },
      ],
    },
    history(p.commercialPoliciesReadHistory, api.commercialPolicyHistory),
    revisions(
      "/commercial/policies",
      p.commercialPoliciesReadRevisions,
      api.commercialPolicyRevisions,
      p.commercialPoliciesCompare,
      api.compareCommercialPolicies,
    ),
    {
      key: "activations",
      label: "Activations",
      permission: p.commercialPoliciesReadActivations,
      load: (d, page) => api.commercialPolicyActivations(d.id, page),
      columns: [
        { key: "activationNumber", label: "Activation" },
        { key: "affectedAccountCount", label: "Customers" },
        { key: "reason", label: "Reason" },
        { key: "recordedAt", label: "Date", format: "date" },
      ],
      actions: (row, d) => [
        {
          key: "audience",
          label: "Audience",
          permission: p.commercialPoliciesReadActivationAccounts,
          readOnly: true,
          paginated: true,
          reason: false,
          preview: (_, i) =>
            api.commercialPolicyActivationAudience(d.id, row.id, i.page),
          execute: async () => undefined,
        },
      ],
    },
    {
      key: "owner",
      label: "Owner",
      permission: p.commercialPoliciesReadOwner,
      load: (d) => api.commercialPolicyOwner(d.id),
    },
  ],
};
function offerPrice(
  key: string,
  title: string,
  type: "PLAN" | "ADD_ON" | "QUOTA_PACKAGE",
  productKey: string,
): Field {
  const display = (x: any) => ({
    value: x.priceId,
    label:
      x.productName +
      " · " +
      money(x.amount, x.currencyCode) +
      " · " +
      x.billingCycle.toLowerCase(),
    record: x,
  });
  return {
    key,
    label: title,
    type: "choice",
    required: true,
    full: true,
    load: async (search, page) => {
      const result = await read(p.offersChooseProducts, () =>
        offers.productChoices({ ownerType: type, search, page, size: 20 }),
      );
      return {
        options: result.content.map(display),
        totalPages: result.totalPages,
      };
    },
    resolve: async (ids) =>
      ids.length && can(p.offersResolveProductChoices)
        ? (
            await read(p.offersResolveProductChoices, () =>
              offers.resolveProductChoices(ids),
            )
          ).map(display)
        : [],
    onSelect: (d, c) => {
      const keys = productKey.split(".");
      let parent = d;
      for (const k of keys.slice(0, -1)) parent = parent[k] ??= {};
      parent[keys[keys.length - 1]!] = c.record?.productId;
    },
  };
}
const offerQuotaResource: Field = {
  key: "resource",
  label: "Quota resource",
  type: "choice",
  required: true,
  full: true,
  contextKeys: [
    "selection.planPriceId",
    "selection.addOns",
    "selection.quotaPackages",
  ],
  load: async (search, _page, data) => {
    const priceIds = [
      data.selection?.planPriceId,
      ...(data.selection?.addOns || []).map((r: any) => r.priceId),
      ...(data.selection?.quotaPackages || []).map((r: any) => r.priceId),
    ].filter(Boolean);
    if (!priceIds.length) return { options: [], totalPages: 1 };
    const result = await read(p.offersChooseQuotaResources, () =>
      offers.quotaResourceChoices(priceIds, search),
    );
    return {
      options: result.map((x) => ({
        value: x.resource,
        label: label(x.resource) + " · " + x.unit,
        record: x,
      })),
      totalPages: 1,
    };
  },
  onSelect: (d, c) => (d.featureCode = c.record?.featureCode),
};
export const quotaSelection: Field = {
  key: "quotaPackages",
  label: "Capacity packages",
  type: "array",
  fields: [
    {
      key: "quotaPackageId",
      label: "Capacity package",
      hidden: true,
      required: true,
    },
    offerPrice(
      "priceId",
      "Capacity package and price",
      "QUOTA_PACKAGE",
      "quotaPackageId",
    ),
    num("quantity", "Quantity", true, 1),
    select("pricingMode", "Pricing", ["PAID", "FREE"]),
  ],
  defaults: { quantity: 1, pricingMode: "PAID" },
  full: true,
};
const offerFields: Field[] = [
  text("name", "Name", true),
  { key: "description", label: "Description", type: "textarea", full: true },
  {
    ...choose(
      "campaignId",
      "Campaign",
      p.offersChooseCampaigns,
      (search, page) => offers.campaignChoices({ search, page, size: 20 }),
    ),
    show: (d) => !d.id,
    emptyLink: () =>
      can(p.campaignsCreate)
        ? { label: "Create campaign", to: "/commercial/campaigns/new" }
        : undefined,
  },
  dt("startsAt", "Starts", true),
  dt("endsAt", "Ends", true),
  {
    ...select("discovery", "Discovery", ["CATALOG", "CODE_ONLY"]),
    show: (d) => !d.id || d.lineageTermsEditable,
  },
  {
    ...select("acceptance", "Acceptance", [
      "CLIENT_OR_OPERATOR",
      "OPERATOR_ONLY",
    ]),
    show: (d) => !d.id || d.lineageTermsEditable,
  },
  {
    ...text("customerCode", "Customer code"),
    show: (d) =>
      d.discovery === "CODE_ONLY" && (!d.id || d.lineageTermsEditable),
  },
  {
    ...num("globalLimit", "Total redemptions"),
    show: (d) => !d.id || d.lineageTermsEditable,
  },
  {
    ...num("perAccountLimit", "Redemptions per customer"),
    show: (d) => !d.id || d.lineageTermsEditable,
  },
  { key: "selection.planId", label: "Plan", hidden: true, required: true },
  offerPrice(
    "selection.planPriceId",
    "Plan and price",
    "PLAN",
    "selection.planId",
  ),
  select("selection.timing", "Timing", ["IMMEDIATE", "AT_RENEWAL"]),
  {
    key: "selection.addOns",
    label: "Add-ons",
    type: "array",
    fields: [
      { key: "addOnId", label: "Add-on", hidden: true, required: true },
      offerPrice("priceId", "Add-on and price", "ADD_ON", "addOnId"),
      select("pricingMode", "Pricing", ["PAID", "FREE"]),
    ],
    defaults: { pricingMode: "PAID" },
    full: true,
  },
  { ...quotaSelection, key: "selection.quotaPackages" },
  select("effects.discountType", "Discount", [
    "NONE",
    "FIXED",
    "PERCENTAGE_WITH_CAP",
  ]),
  {
    ...decimal("effects.discountAmount", "Discount amount"),
    show: (d) => d.effects?.discountType === "FIXED",
  },
  {
    ...decimal("effects.percentage", "Discount percentage"),
    show: (d) => d.effects?.discountType === "PERCENTAGE_WITH_CAP",
  },
  {
    ...decimal("effects.percentageCap", "Discount cap"),
    show: (d) => d.effects?.discountType === "PERCENTAGE_WITH_CAP",
  },
  {
    key: "effects.finiteQuotaBonuses",
    label: "Quota bonuses",
    type: "array",
    fields: [
      { key: "featureCode", label: "Feature", hidden: true, required: true },
      offerQuotaResource,
      num("quantity", "Quantity", true, 1),
    ],
    full: true,
  },
];
const offerResource: Resource = {
  key: "offers",
  title: "Offers",
  singular: "Offer",
  base: "/commercial/offers",
  listPermission: p.offersList,
  readPermission: p.offersRead,
  list: (q) => offers.list(q),
  detail: async (id) => ({
    ...(await offers.detail(id)),
    ...(can(p.offersReadEditableDefinition)
      ? await read(p.offersReadEditableDefinition, () =>
          offers.editableDefinition(id),
        )
      : {}),
  }),
  columns,
  alternateDetail: [
    {
      permission: p.offersReadEditableDefinition,
      load: offers.editableDefinition,
    },
  ],
  createPermission: p.offersCreate,
  editPermission: p.offersUpdate,
  editable: allowed("UPDATE"),
  fields: offerFields,
  defaults: {
    discovery: "CATALOG",
    acceptance: "OPERATOR_ONLY",
    selection: { timing: "AT_RENEWAL", addOns: [], quotaPackages: [] },
    effects: {
      discountType: "NONE",
      discountAmount: null,
      percentage: null,
      percentageCap: null,
      finiteQuotaBonuses: [],
    },
  },
  save: async (id, i, d) => {
    const body = id
      ? {
          ...i,
          version: d!.version,
          lineageTerms: d!.lineageTermsEditable
            ? {
                expectedLineageVersion: d!.lineageVersion,
                discovery: i.discovery,
                acceptance: i.acceptance,
                globalLimit: i.globalLimit,
                perAccountLimit: i.perAccountLimit,
                customerCodeChange: {
                  mode: i.customerCode ? "REPLACE" : "KEEP",
                  value: i.customerCode || null,
                },
              }
            : null,
        }
      : i;
    const assessment = await read(
      id ? p.offersPreviewUpdateDefinition : p.offersPreviewCreateDefinition,
      () =>
        id
          ? offers.previewUpdateDefinition(id, body as any)
          : offers.previewCreateDefinition(body as any),
    );
    if (!assessment.valid)
      throw new Error(assessment.issues.map((x) => x.message).join(" "));
    return id ? offers.update(id, body as any) : offers.create(body as any);
  },
  actions: [
    ...branches(
      "/commercial/offers",
      p.offersDuplicate,
      p.offersRevise,
      offers.duplicate,
      offers.revise,
    ),
    lifecycle(
      "PUBLISH",
      p.offersPublish,
      "Publish",
      offers.publish,
      p.offersPreviewPublish,
      offers.publicationPreview,
    ),
    lifecycle(
      "RESTORE",
      p.offersRestore,
      "Restore",
      offers.restore,
      p.offersPreviewPublish,
      offers.publicationPreview,
    ),
    lifecycle("RETIRE", p.offersRetire, "Retire", offers.retire),
    lifecycle("ARCHIVE", p.offersArchive, "Archive", offers.archive),
    {
      ...lifecycle(
        "DELETE_DRAFT",
        p.offersDelete,
        "Delete draft",
        offers.deleteDraft,
      ),
      destination: () => "/commercial?view=offers",
    },
    ownerAction(
      p.offersReassignOwner,
      (id) => read(p.offersReadOwner, () => offers.owner(id)),
      offers.reassignOwner,
      {
        ...choose(
          "ownerAdminUserId",
          "Owner",
          p.offersChooseOwners,
          (search, page) => offers.ownerChoices({ search, page, size: 20 }),
          (x) => x.adminUserId,
        ),
        resolve: (ids) =>
          read(p.offersChooseOwners, () =>
            offers.resolveOwnerChoices(ids),
          ).then((items) =>
            items.map((x) => ({
              value: x.adminUserId,
              label: x.displayName || x.email,
            })),
          ),
      },
    ),
    {
      key: "apply",
      label: "Apply to customer",
      permission: p.offersApplyForAccount,
      visible: allowed("APPLY_FOR_ACCOUNT"),
      fields: [accountField("accountId", "Customer", true, "offers")],
      defaults: (d) => ({ accountId: d._accountId }),
      preview: async (d, i) => {
        const x = await read(p.offersPreviewForAccount, () =>
          offers.previewForAccount(d.id, i.accountId),
        );
        return { ...x.preview, eligible: x.eligible, blockers: x.blockers };
      },
      execute: (d, i, r) =>
        offers.applyForAccount(d.id, i.accountId, i._idempotencyKey, {
          previewToken: r!.previewToken,
          reason: i.reason,
        }),
      destination: (_, d) => "/commercial/offers/" + d.id + "?view=redemptions",
    },
  ],
  sections: [
    {
      key: "stats",
      label: "Performance",
      permission: p.offersReadStats,
      load: (d) => offers.stats(d.id),
    },
    {
      key: "redemptions",
      label: "Redemptions",
      permission: p.offersReadRedemptions,
      load: async (d, page) => {
        const result = await offers.redemptions(d.id, { page, size: 20 });
        const identities =
          can(p.offersReadRedemptionIdentities) && result.content.length
            ? await read(p.offersReadRedemptionIdentities, () =>
                offers.resolveRedemptionIdentities(
                  d.id,
                  result.content.map((x) => x.id),
                ),
              )
            : [];
        return {
          ...result,
          content: result.content.map((x) => ({
            ...x,
            ...identities.find((i) => i.redemptionId === x.id),
          })),
        };
      },
      actions: (row, d) => [
        {
          key: "read",
          label: "Accepted terms",
          permission: p.offersReadRedemptionDetail,
          reason: false,
          readOnly: true,
          preview: () => offers.redemption(d.id, row.id),
          execute: async () => undefined,
        },
      ],
      columns: [
        {
          key: "accountName",
          label: "Customer",
          link: (r) =>
            r.accountId
              ? "/customers/" + r.accountId
              : "/commercial/offers/" + r.offerId + "?view=redemptions",
        },
        { key: "status", label: "Status", format: "status" },
        { key: "surface", label: "Channel" },
        { key: "reservedAt", label: "Reserved", format: "date" },
      ],
    },
    history(p.offersHistory, offers.history),
    revisions(
      "/commercial/offers",
      p.offersRevisions,
      offers.revisions,
      p.offersCompare,
      offers.compare,
    ),
    {
      key: "owner",
      label: "Owner",
      permission: p.offersReadOwner,
      load: (d) => offers.owner(d.id),
    },
  ],
};
export const commercialResources = [
  campaigns,
  segments,
  policies,
  offerResource,
];
