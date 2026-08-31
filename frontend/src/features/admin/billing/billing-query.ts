export const adminBillingKeys = {
  all: ["admin", "billing"] as const,
  invoices: (query: object) => ["admin", "billing", "invoices", query] as const,
  invoice: (id: string) => ["admin", "billing", "invoice", id] as const,
  account: (id: string) => ["admin", "billing", "invoice", id, "account"] as const,
  payments: (id: string) => ["admin", "billing", "invoice", id, "payments"] as const,
  reconciliation: (query: object) => ["admin", "billing", "reconciliation", query] as const,
  providerEvents: (query: object) => ["admin", "billing", "provider-events", query] as const,
  refundPreview: (id: string) => ["admin", "billing", "payment", id, "refund-preview"] as const,
  retryPreview: (id: string) => ["admin", "billing", "invoice", id, "retry-preview"] as const,
};

export const clientBillingKeys = {
  invoices: (context: object, query: object) => ["client", "billing", context, "invoices", query] as const,
  invoice: (context: object, id: string) => ["client", "billing", context, "invoice", id] as const,
};
