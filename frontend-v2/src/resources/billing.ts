import { adminApi as api } from "@/api/admin-api";
import { adminPermissions as p } from "@/auth/permissions";
import { read } from "@/data/gateway";
import { paymentEnabled } from "@/lib/capabilities";
import type { Action, Resource } from "./types";
import { text, decimal, select, currencies, root, allowed } from "./fields";
export const invoiceActions: Action[] = [
  {
    key: "credit",
    label: "Issue credit",
    permission: p.billingIssueCredit,
    fields: [
      decimal("amount", "Amount"),
      select("currencyCode", "Currency", currencies),
      text("source", "Source", true),
      text("externalReference", "Reference"),
    ],
    defaults: (d) => ({
      currencyCode: d.invoice.currencyCode,
      source: "OPERATOR",
    }),
    execute: (d, i) => api.issueBillingCredit(d.invoice.id, i as any),
  },
  {
    key: "retry",
    label: "Retry charge",
    permission: p.billingRetryCharge,
    fields: [
      text("recoveryReference", "Recovery reference", true),
      {
        key: "providerConfirmedNotCaptured",
        label: "Provider confirmed funds were not captured",
        type: "checkbox",
        required: true,
      },
    ],
    preview: (d) =>
      read(p.billingPreviewChargeRetry, async () => {
        const result = await api.previewBillingChargeRetry(d.invoice.id);
        return { ...result, allowed: result.retryAllowed };
      }),
    execute: (d, i) =>
      api.retryBillingCharge(d.invoice.id, i._idempotencyKey, i as any),
  },
];
export const paymentActions: Action[] = [
  {
    key: "refund",
    label: "Refund",
    permission: p.billingCreateRefund,
    fields: [
      decimal("amount", "Amount"),
      select("currencyCode", "Currency", currencies),
    ],
    defaults: (d) => ({ currencyCode: d.currencyCode }),
    preview: (d) =>
      read(p.billingPreviewRefund, async () => {
        const result = await api.previewBillingRefund(d.id);
        return { ...result, allowed: result.providerRefundAllowed };
      }),
    execute: (d, i) =>
      api.requestBillingRefund(d.id, i._idempotencyKey, i as any),
  },
  {
    key: "manual-refund",
    label: "Record manual refund",
    permission: p.billingRecordManualRefund,
    fields: [
      decimal("amount", "Amount"),
      select("currencyCode", "Currency", currencies),
      text("externalReference", "Refund reference", true),
    ],
    defaults: (d) => ({ currencyCode: d.currencyCode }),
    preview: (d) =>
      read(p.billingPreviewRefund, async () => {
        const result = await api.previewBillingRefund(d.id);
        return { ...result, allowed: result.manualRefundAllowed };
      }),
    execute: (d, i) =>
      api.recordManualBillingRefund(d.id, i._idempotencyKey, i as any),
  },
];
const invoices: Resource = {
  key: "invoices",
  title: "Invoices",
  singular: "Invoice",
  base: "/customers/invoices",
  listPermission: p.billingListInvoices,
  readPermission: p.billingReadInvoice,
  list: (q) => api.billingInvoices(q),
  detail: api.billingInvoice,
  columns: [
    { key: "invoiceNumber", label: "Invoice" },
    {
      key: "account.name",
      label: "Customer",
      link: (r) =>
        r.account?.id
          ? "/customers/" + r.account.id + "?view=billing"
          : "/billing/" + r.id,
    },
    { key: "status", label: "Status", format: "status" },
    { key: "totalAmount", label: "Amount", format: "money" },
    { key: "issuedAt", label: "Issued", format: "date" },
    { key: "settledAt", label: "Settled", format: "date" },
  ],
};
export const agreementActions: Action[] = [
  {
    key: "cancel",
    label: "Cancel agreement",
    permission: p.subscriptionsCancelSpecialAgreement,
    visible: (d) => d.availableActions?.cancel,
    destructive: true,
    execute: (d, i) => api.cancelSpecialAgreement(d.accountId, d.id, i.reason),
  },
  {
    key: "retry",
    label: "Retry",
    permission: p.subscriptionsRetrySpecialAgreement,
    visible: (d) =>
      d.availableActions?.retryStart || d.availableActions?.retryEnd,
    execute: (d, i) => api.retrySpecialAgreement(d.accountId, d.id, i.reason),
  },
  {
    key: "resolve",
    label: "Resolve review",
    permission: p.subscriptionsResolveSpecialAgreementManualReview,
    visible: (d) => d.availableActions?.resolveManualReview,
    execute: (d, i) =>
      api.resolveSpecialAgreementManualReview(d.accountId, d.id, i.reason),
  },
  {
    key: "settle",
    label: "Confirm settlement",
    permission: p.subscriptionsConfirmCheckout,
    visible: (d) => paymentEnabled && d.availableActions?.settleManually,
    fields: [text("reference", "Settlement reference", true)],
    execute: (d, i) => api.confirmCheckout(d.checkout.id, { ...i } as any),
  },
];
const agreements: Resource = {
  key: "agreements",
  title: "Agreements",
  singular: "Agreement",
  base: "/customers/agreements",
  listPermission: p.subscriptionsSearchSpecialAgreements,
  readPermission: p.subscriptionsReadSpecialAgreement,
  list: (q) => api.allSpecialAgreements(q),
  detail: async () => undefined,
  listOnly: true,
  searchable: true,
  filters: [
    {
      key: "status",
      label: "Status",
      values: [
        "SCHEDULED",
        "AWAITING_SETTLEMENT",
        "ACTIVE",
        "COMPLETED",
        "CANCELLED",
        "NEEDS_ATTENTION",
      ],
    },
  ],
  columns: [
    {
      key: "planName",
      label: "Agreement",
      link: (r) => "/customers/" + r.accountId + "/agreements/" + r.id,
    },
    {
      key: "accountName",
      label: "Customer",
      link: (r) => "/customers/" + r.accountId + "?view=agreements",
    },
    { key: "status", label: "Status", format: "status" },
    { key: "startsAt", label: "Starts", format: "date" },
    { key: "endsAt", label: "Ends", format: "date" },
    { key: "agreedTermAmount", label: "Amount", format: "money" },
  ],
};
export const billingResources = [invoices, agreements];
