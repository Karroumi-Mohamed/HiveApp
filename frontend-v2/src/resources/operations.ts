import { adminOfferApi as offers } from "@/api/admin-offer-api";
import { adminApi as api } from "@/api/admin-api";
import { communicationApi as communications } from "@/api/communication-api";
import { repricingApi } from "@/api/repricing-api";
import { planApplicationApi } from "@/api/plan-application-api";
import { adminPermissions as p } from "@/auth/permissions";
import { read } from "@/data/gateway";
import { can } from "@/data/session";
import type { Resource, Field, Action } from "./types";
import { destination } from "@/lib/destination";
import {
  text,
  dt,
  select,
  num,
  checks,
  accountField,
  planField,
  priceField,
  featureField,
  root,
  compactHistory,
  currencies,
  cycles,
  subscriptionStatuses,
} from "./fields";
const resultColumns = [
  {
    key: "accountName",
    label: "Customer",
    link: (r: any) => (r.accountId ? "/customers/" + r.accountId : undefined),
  },
  { key: "status", label: "Status", format: "status" as const },
  { key: "blocker", label: "Issue" },
  { key: "delivery", label: "Email" },
];
const recipients: Field = {
  key: "accountIds",
  label: "Customers",
  type: "choices",
  required: true,
  full: true,
  load: async (search, page) => {
    const x = await read(p.customerCommunicationsChoose, () =>
      communications.choices(search, page),
    );
    return {
      options: x.content.map((r) => ({ value: r.id, label: r.name })),
      totalPages: x.totalPages,
    };
  },
};
const messageFields: Field[] = [
  text("messageTitle", "Title", true),
  {
    key: "messageBody",
    label: "Message",
    type: "textarea",
    required: true,
    full: true,
  },
  select("kind", "Type", ["NOTICE", "WARNING", "ACTION", "OFFER"]),
  select("purpose", "Purpose", ["SERVICE", "MARKETING"]),
  recipients,
  { key: "email", label: "Send email", type: "checkbox" },
  {
    key: "offerId",
    label: "Offer",
    type: "choice",
    show: (d) => d.kind === "OFFER",
    required: true,
    load: async (search, page) => {
      const result = await read(p.offersList, () =>
        offers.list({ search, page, size: 20 }),
      );
      return {
        options: result.content.map((x) => ({ value: x.id, label: x.name })),
        totalPages: result.totalPages,
      };
    },
  },
  dt("availableAt", "Available from"),
  dt("expiresAt", "Expires"),
  select("originalLanguage", "Original language", ["fr", "ar"], false),
  { key: "translations.fr.messageTitle", label: "French title" },
  {
    key: "translations.fr.messageBody",
    label: "French message",
    type: "textarea",
    full: true,
  },
  { key: "translations.ar.messageTitle", label: "Arabic title" },
  {
    key: "translations.ar.messageBody",
    label: "Arabic message",
    type: "textarea",
    full: true,
  },
];
const messages: Resource = {
  key: "messages",
  title: "Customer messages",
  singular: "Message",
  base: "/operations/messages",
  listPermission: p.customerCommunicationsRead,
  readPermission: p.customerCommunicationsRead,
  list: (q) => communications.list(q.page),
  detail: communications.detail,
  normalize: (d) => ({ ...d, name: d.messageTitle }),
  columns: [
    { key: "messageTitle", label: "Title" },
    { key: "kind", label: "Type" },
    { key: "purpose", label: "Purpose" },
    { key: "state", label: "State", format: "status" },
    { key: "availableAt", label: "Available", format: "date" },
  ],
  createPermission: p.customerCommunicationsCreate,
  editPermission: p.customerCommunicationsEdit,
  editable: (d) => d.state === "DRAFT",
  fields: messageFields,
  defaults: {
    kind: "NOTICE",
    purpose: "SERVICE",
    accountIds: [],
    email: false,
    offerId: null,
    availableAt: null,
    expiresAt: null,
    originalLanguage: "fr",
    translations: {},
  },
  save: (id, i, d) => {
    if (i.translations)
      for (const language of ["fr", "ar"])
        if (
          !i.translations[language]?.messageTitle &&
          !i.translations[language]?.messageBody
        )
          delete i.translations[language];
    return id
      ? communications.edit(id, d!.version, i as any)
      : communications.create(i as any);
  },
  actions: [
    {
      key: "publish",
      label: "Publish",
      permission: p.customerCommunicationsPublish,
      visible: (d) =>
        d.state === "DRAFT" &&
        (d.purpose !== "MARKETING" || can(p.customerCommunicationsMarketing)),
      execute: (d, i) => communications.publish(d.id, d.version, i.reason),
    },
    {
      key: "cancel",
      label: "Cancel publication",
      permission: p.customerCommunicationsCancel,
      visible: (d) => d.state !== "CANCELLED",
      destructive: true,
      execute: (d, i) => communications.cancel(d.id, d.version, i.reason),
    },
  ],
  sections: [
    {
      key: "recipients",
      label: "Recipients",
      permission: p.customerCommunicationsChoose,
      load: (d) => communications.selectedRecipients(d.id),
      columns: [
        { key: "name", label: "Customer", link: (r) => "/customers/" + r.id },
      ],
    },
    {
      key: "results",
      label: "Delivery results",
      permission: p.customerCommunicationsResults,
      load: (d, page) => communications.results(d.id, page),
      columns: [
        {
          key: "accountName",
          label: "Customer",
          link: (r) => "/customers/" + r.accountId,
        },
        { key: "visible", label: "In-app", format: "boolean" },
        { key: "emailDelivery", label: "Email" },
        { key: "readers", label: "Read" },
        { key: "acknowledgements", label: "Acknowledged" },
      ],
      actions: (r) => [
        {
          key: "retry",
          label: "Retry email",
          permission: p.customerCommunicationsRetry,
          visible: () => r.emailDelivery === "FAILED",
          execute: () => communications.retry(r.id),
        },
      ],
    },
  ],
};
const repricing: Resource = {
  key: "repricing",
  title: "Repricing",
  singular: "Repricing",
  base: "/operations/repricing",
  listPermission: p.repricingList,
  readPermission: p.repricingRead,
  list: (q) => repricingApi.list(q.page),
  detail: repricingApi.detail,
  normalize: root,
  columns: [
    { key: "productName", label: "Product" },
    { key: "status", label: "Status", format: "status" },
    { key: "sourcePrice", label: "Current price", format: "money" },
    { key: "targetPrice", label: "Target price", format: "money" },
    { key: "targetCount", label: "Customers" },
    { key: "conflictCount", label: "Conflicts" },
  ],
  actions: [
    {
      key: "cancel",
      label: "Cancel",
      permission: p.repricingCancel,
      visible: (d) => d.status !== "CANCELLED",
      destructive: true,
      execute: (d, i) => repricingApi.cancel(d.id, i.reason),
    },
    {
      key: "notices",
      label: "Retry notices",
      permission: p.repricingRetry,
      execute: (d, i) => repricingApi.retryNotices(d.id, i.reason),
    },
  ],
  sections: [
    {
      key: "results",
      label: "Results",
      permission: p.repricingResults,
      load: async (d, page) => {
        const x = await repricingApi.results(d.id, page);
        const identities =
          can(p.repricingIdentities) && x.content.length
            ? await read(p.repricingIdentities, () =>
                repricingApi.identities(
                  d.id,
                  x.content.map((r) => r.id),
                ),
              )
            : [];
        return {
          ...x,
          content: x.content.map((r) => ({
            ...r,
            ...identities.find((i) => i.itemId === r.id),
          })),
        };
      },
      columns: resultColumns,
      actions: (r, d) => [
        {
          key: "retry",
          label: "Retry",
          permission: p.repricingRetry,
          visible: () => r.status === "CONFLICT",
          execute: (_, i) => repricingApi.retry(d.id, r.id, i.reason),
        },
        {
          key: "cancel",
          label: "Cancel",
          permission: p.repricingCancel,
          visible: () =>
            ["READY", "PENDING", "AWAITING_PAYMENT"].includes(r.status),
          execute: (_, i) => repricingApi.cancel(d.id, i.reason, r.id),
        },
      ],
    },
  ],
};
const rollout: Resource = {
  key: "rollouts",
  title: "Plan rollouts",
  singular: "Plan rollout",
  base: "/operations/rollouts",
  listPermission: p.plansListApplications,
  readPermission: p.plansReadApplication,
  list: (q) => planApplicationApi.list(q.planId, q.page),
  detail: planApplicationApi.detail,
  normalize: root,
  columns: [
    { key: "id", label: "Rollout" },
    { key: "status", label: "Status", format: "status" },
    { key: "createdAt", label: "Created", format: "date" },
  ],
  actions: [
    {
      key: "confirm",
      label: "Confirm rollout",
      permission: p.plansConfirmApplication,
      visible: (d) => d.status === "PREVIEWED" && !d.reviewInvalidated,
      fields: [
        {
          key: "applyReadyOnly",
          label: "Apply ready customers only",
          type: "checkbox",
        },
      ],
      reason: false,
      preview: (d) => planApplicationApi.detail(d.id),
      execute: (d, i, r) =>
        planApplicationApi.confirm(d.id, r!.previewToken, !!i.applyReadyOnly),
    },
    {
      key: "cancel",
      label: "Cancel",
      permission: p.plansCancelApplication,
      visible: (d) => !["COMPLETED", "CANCELLED"].includes(d.status),
      destructive: true,
      execute: (d, i) => planApplicationApi.cancel(d.id, i.reason),
    },
    {
      key: "retry",
      label: "Retry failures",
      permission: p.plansRetryApplication,
      visible: (d) => d.status === "COMPLETED_WITH_ERRORS",
      execute: (d, i) => planApplicationApi.retry(d.id, i.reason),
    },
  ],
  sections: [
    {
      key: "results",
      label: "Results",
      permission: p.plansApplicationResults,
      load: async (d, page) => {
        const x = await planApplicationApi.results(d.id, page);
        const identities =
          can(p.plansApplicationIdentities) && x.content.length
            ? await read(p.plansApplicationIdentities, () =>
                planApplicationApi.identities(
                  d.id,
                  x.content.map((r) => r.id),
                ),
              )
            : [];
        return {
          ...x,
          content: x.content.map((r) => ({
            ...r,
            ...identities.find((i) => i.itemId === r.id),
          })),
        };
      },
      columns: [
        ...resultColumns.slice(0, 2),
        { key: "outcomeCode", label: "Outcome" },
        { key: "notice.emailDelivery", label: "Email", format: "status" },
        { key: "attempts", label: "Attempts" },
      ],
      actions: (r, d) => [
        {
          key: "impact",
          label: "Review impact",
          permission: p.plansApplicationResults,
          readOnly: true,
          reason: false,
          preview: () =>
            Promise.resolve({
              impact: r.impact,
              conflicts: r.executionConflicts,
              outcome: r.outcomeCode,
            }),
          execute: async () => undefined,
        },
        {
          key: "retry-notice",
          label: "Retry notice",
          permission: p.plansRetryNotices,
          visible: () => r.notice?.emailDelivery === "FAILED",
          execute: (_, i) =>
            planApplicationApi.retryNotices(d.id, [r.notice.id], i.reason),
        },
      ],
    },
  ],
};
const activity: Resource = {
  key: "activity",
  title: "Activity",
  singular: "Activity",
  base: "/operations/activity",
  listPermission: p.activitiesRead,
  readPermission: p.activitiesRead,
  list: (q) => api.activities(q),
  detail: api.activity,
  columns: [
    { key: "action", label: "Action" },
    { key: "resourceType", label: "Resource" },
    { key: "outcome", label: "Outcome", format: "status" },
    { key: "occurredAt", label: "Date", format: "date" },
  ],
  sections: [
    {
      key: "payload",
      label: "Evidence",
      permission: p.activitiesReadPayload,
      load: (d) => api.activityPayload(d.id),
    },
  ],
};
const delivery: Resource = {
  key: "delivery",
  title: "Email delivery",
  singular: "Delivery",
  base: "/operations/delivery",
  listPermission: p.communicationsRead,
  readPermission: p.communicationsRead,
  list: (q) => api.communications(q),
  detail: api.communication,
  columns: [
    { key: "purpose", label: "Purpose" },
    { key: "status", label: "Status", format: "status" },
    { key: "failureCode", label: "Failure" },
    { key: "attemptedAt", label: "Last attempt", format: "date" },
    { key: "createdAt", label: "Created", format: "date" },
  ],
  sections: [
    {
      key: "recipient",
      label: "Recipient",
      permission: p.communicationsReadRecipientIdentity,
      load: (d) => api.communicationRecipient(d.id),
    },
    {
      key: "failure",
      label: "Failure evidence",
      permission: p.communicationsReadFailureEvidence,
      load: (d) => api.communicationFailureEvidence(d.id),
    },
  ],
};
const inbox: Resource = {
  key: "inbox",
  title: "Notifications",
  singular: "Notification",
  base: "/operations/inbox",
  listPermission: p.notificationsRead,
  readPermission: p.notificationsRead,
  list: (q) =>
    communications.inbox(
      undefined,
      q.archive === "ARCHIVED",
      q.readState === "UNREAD",
      q.page,
      undefined,
      true,
    ),
  filters: [
    { key: "archive", label: "Inbox views", values: ["ARCHIVED"] },
    { key: "readState", label: "Read states", values: ["UNREAD"] },
  ],
  detail: (id) => communications.item(id, true),
  normalize: (d) => ({ ...d, name: d.messageTitle }),
  columns: [
    { key: "messageTitle", label: "Title" },
    { key: "topic", label: "Topic" },
    { key: "availableAt", label: "Received", format: "date" },
    { key: "read", label: "Read", format: "boolean" },
  ],
  actions: [
    ...(["read", "acknowledge", "archive"] as const).map<Action>((a) => ({
      key: a,
      label:
        a === "read"
          ? "Mark read"
          : a === "archive"
            ? "Archive"
            : "Acknowledge",
      permission:
        a === "read"
          ? p.notificationsMarkRead
          : a === "archive"
            ? p.notificationsArchive
            : p.notificationsAcknowledge,
      visible: (d) =>
        a === "read"
          ? !d.read
          : a === "archive"
            ? d.canArchive
            : d.canAcknowledge,
      reason: false,
      execute: (d) => communications.interact(d.id, a, true, true),
    })),
  ],
};
const jobs: Resource = {
  key: "jobs",
  title: "Subscription changes",
  singular: "Change",
  base: "/operations/changes",
  listPermission: p.subscriptionsListChangeJobs,
  readPermission: p.subscriptionsReadChangeJob,
  list: (q) => api.subscriptionChangeJobs(q),
  detail: api.subscriptionChangeJob,
  columns: [
    { key: "reason", label: "Change", link: (r) => "/operations/" + r.id },
    { key: "status", label: "Status", format: "status" },
    { key: "targetCount", label: "Customers" },
    { key: "createdAt", label: "Created", format: "date" },
    { key: "executeAt", label: "Scheduled", format: "date" },
  ],
};
const attention: Resource = {
  key: "attention",
  title: "Attention",
  singular: "Attention",
  base: "/operations/attention",
  listPermission: p.analyticsReadOperations,
  readPermission: p.analyticsReadOperations,
  list: (q) => api.commercialAttention(q),
  detail: async () => undefined,
  listOnly: true,
  columns: [
    {
      key: "accountName",
      label: "Customer",
      link: (r) => (r.accountId ? "/customers/" + r.accountId : "/operations"),
    },
    { key: "type", label: "Issue" },
    { key: "reason", label: "Reason" },
    { key: "status", label: "Status", format: "status" },
    { key: "dueAt", label: "Due", format: "date" },
    { key: "amount", label: "Amount", format: "money" },
    {
      key: "recordId",
      label: "Review",
      link: (r) => destination(r.destination),
    },
  ],
};
const providerEvents: Resource = {
  key: "provider-events",
  title: "Provider events",
  singular: "Event",
  base: "/operations/provider-events",
  listPermission: p.billingListProviderEvents,
  readPermission: p.billingListProviderEvents,
  list: (q) => api.billingProviderEvents(q),
  detail: async () => undefined,
  listOnly: true,
  columns: [
    { key: "provider", label: "Provider" },
    { key: "operation", label: "Operation" },
    { key: "processingStatus", label: "Status", format: "status" },
    { key: "attentionReason", label: "Issue" },
    { key: "amount", label: "Amount", format: "money" },
    { key: "createdAt", label: "Received", format: "date" },
  ],
  listActions: [
    {
      key: "evidence",
      label: "Details",
      permission: p.billingListProviderEvents,
      readOnly: true,
      reason: false,
      preview: (d) => Promise.resolve(d),
      execute: async () => undefined,
    },
    {
      key: "reprocess",
      label: "Reprocess",
      permission: p.billingReconcileProviderEvent,
      reason: false,
      visible: (d) =>
        ["RECEIVED", "UNMATCHED", "MISMATCHED"].includes(d.processingStatus),
      execute: (d) => api.reprocessBillingProviderEvent(d.id),
    },
  ],
};
const providerCommands: Resource = {
  key: "provider-commands",
  title: "Payment commands",
  singular: "Command",
  base: "/operations/provider-commands",
  listPermission: p.billingListReconciliation,
  readPermission: p.billingListReconciliation,
  list: (q) => api.billingReconciliation(q),
  detail: async () => undefined,
  listOnly: true,
  columns: [
    { key: "operation", label: "Operation" },
    { key: "status", label: "Status", format: "status" },
    { key: "attemptCount", label: "Attempts" },
    { key: "nextAttemptAt", label: "Next attempt", format: "date" },
  ],
};
const notificationEvents: Resource = {
  key: "notification-events",
  title: "Notification delivery",
  singular: "Notification event",
  base: "/operations/notification-events",
  listPermission: p.notificationsDelivery,
  readPermission: p.notificationsDelivery,
  list: (q) => communications.events(q.status, q.page),
  detail: async () => undefined,
  listOnly: true,
  columns: [
    { key: "type", label: "Type" },
    { key: "state", label: "Status", format: "status" },
    { key: "attempts", label: "Attempts" },
    { key: "failureCode", label: "Failure" },
    { key: "nextAttemptAt", label: "Next attempt", format: "date" },
  ],
  listActions: [
    {
      key: "retry",
      label: "Retry",
      permission: p.notificationsRetry,
      visible: (d) => d.canRetry,
      execute: (d, i) => communications.retryEvent(d.id, d.version, i.reason),
    },
  ],
};
const notificationEmails: Resource = {
  ...notificationEvents,
  key: "notification-emails",
  title: "Notification emails",
  base: "/operations/notification-emails",
  list: (q) => communications.notificationEmails(q.status, q.page),
  listActions: [
    {
      key: "retry",
      label: "Retry",
      permission: p.notificationsRetry,
      visible: (d) => d.canRetry,
      execute: (d, i) =>
        communications.retryNotificationEmail(d.id, d.version, i.reason),
    },
  ],
};
export const operationResources = [
  messages,
  repricing,
  rollout,
  activity,
  delivery,
  inbox,
  jobs,
  attention,
  providerEvents,
  providerCommands,
  notificationEvents,
  notificationEmails,
];
export const executionFields: Record<string, Field[]> = {
  repricing: [
    priceField("sourcePriceId", "Current price"),
    priceField("targetPriceId", "Target price", "_sourceProductId"),
    select("audience", "Audience", [
      "TARIFF_HOLDERS",
      "SELECTED",
      "FILTERED",
      "SEGMENT",
    ]),
    { ...accountField(), show: (d) => d.audience === "SELECTED" },
    {
      ...planField("planId", "Plan"),
      required: false,
      show: (d) => d.audience === "FILTERED",
    },
    {
      ...select(
        "subscriptionStatus",
        "Subscription status",
        subscriptionStatuses,
        false,
      ),
      show: (d) => d.audience === "FILTERED",
    },
    {
      key: "segmentId",
      label: "Segment",
      type: "choice",
      required: true,
      load: async (search, page) => {
        const result = await read(p.segmentsList, () =>
          api.commercialSegments({ search, page, size: 20 }),
        );
        return {
          options: result.content.map((x) => ({ value: x.id, label: x.name })),
          totalPages: result.totalPages,
        };
      },
      show: (d) => d.audience === "SEGMENT",
    },
    accountField("excludedAccountIds", "Exclude customers"),
    dt("notBefore", "Apply no earlier than"),
    { key: "email", label: "Send email notices", type: "checkbox" },
    {
      key: "reason",
      label: "Reason",
      type: "textarea",
      required: true,
      full: true,
    },
  ],
  rollouts: [
    planField("sourcePlanId", "Source revision"),
    planField("targetPlanId", "Target revision"),
    select("scope", "Scope", ["VERSION", "FAMILY"]),
    select("audience", "Audience", ["ALL", "SELECTED", "FILTERED"]),
    { ...accountField(), show: (d) => d.audience === "SELECTED" },
    accountField("excludedAccountIds", "Exclude customers"),
    {
      ...checks("statuses", "Statuses", subscriptionStatuses),
      show: (d) => d.audience === "FILTERED",
    },
    {
      ...text("search", "Customer search"),
      show: (d) => d.audience === "FILTERED",
    },
    {
      ...select("currency", "Currency", currencies, false),
      show: (d) => d.audience === "FILTERED",
    },
    {
      ...select("billingCycle", "Billing cycle", cycles, false),
      show: (d) => d.audience === "FILTERED",
    },
    select("application.timing", "Timing", ["NOW", "AT_RENEWAL", "AT_DATE"]),
    {
      ...dt("application.notBefore", "Apply at", true),
      show: (d) => d.application?.timing === "AT_DATE",
    },
    select("notificationPolicy", "Notices", [
      "IN_APP",
      "EMAIL",
      "EMAIL_REQUIRED",
    ]),
    {
      key: "application.reason",
      label: "Reason",
      type: "textarea",
      required: true,
      full: true,
    },
  ],
};
