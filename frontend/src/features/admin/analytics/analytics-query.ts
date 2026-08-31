import type { BillingCycle, CommercialAnalyticsInterval, CommercialAnalyticsQuery } from "@/api/contracts";

export type AnalyticsRangePreset = "30" | "90" | "365" | "custom";

export type AnalyticsFilters = {
  range: AnalyticsRangePreset;
  from?: string;
  until?: string;
  timezone: string;
  interval: CommercialAnalyticsInterval;
  currencyCode?: string;
  billingCycle?: BillingCycle;
};

export function analyticsSearchChanges(updates: Partial<AnalyticsFilters>): Record<string, string | null> {
  const changes: Record<string, string | null> = {};
  if ("range" in updates) changes.range = updates.range ?? null;
  if ("from" in updates) changes.from = updates.from ?? null;
  if ("until" in updates) changes.until = updates.until ?? null;
  if ("timezone" in updates) changes.timezone = updates.timezone ?? null;
  if ("interval" in updates) changes.interval = updates.interval ?? null;
  if ("currencyCode" in updates) changes.currency = updates.currencyCode ?? null;
  if ("billingCycle" in updates) changes.cycle = updates.billingCycle ?? null;
  return changes;
}

export const adminAnalyticsKeys = {
  all: ["admin", "analytics"] as const,
  overview: (query: CommercialAnalyticsQuery) => ["admin", "analytics", "overview", query] as const,
  financial: (query: CommercialAnalyticsQuery) => ["admin", "analytics", "financial", query] as const,
  subscriptions: (query: CommercialAnalyticsQuery) => ["admin", "analytics", "subscriptions", query] as const,
  holdings: () => ["admin", "analytics", "holdings"] as const,
  offers: (query: CommercialAnalyticsQuery) => ["admin", "analytics", "offers", query] as const,
  attention: (query: object) => ["admin", "analytics", "attention", query] as const,
};

export function analyticsQuery(filters: AnalyticsFilters, anchor: Date): CommercialAnalyticsQuery {
  const query: CommercialAnalyticsQuery = {
    timezone: filters.timezone,
    interval: filters.interval,
    currencyCode: filters.currencyCode?.trim().toUpperCase() || undefined,
    billingCycle: filters.billingCycle,
  };
  if (filters.range === "custom") {
    const from = validInstant(filters.from);
    const until = validInstant(filters.until);
    if (from) query.from = from;
    if (until) query.until = until;
    return query;
  }
  const days = Number(filters.range);
  query.until = anchor.toISOString();
  query.from = new Date(anchor.getTime() - days * 86_400_000).toISOString();
  return query;
}

function validInstant(value?: string): string | undefined {
  if (!value) return undefined;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

export function timeSeriesQuery(query: CommercialAnalyticsQuery): CommercialAnalyticsQuery {
  return {
    from: query.from,
    until: query.until,
    timezone: query.timezone,
    interval: query.interval,
  };
}

export function toDateTimeLocal(value: Date): string {
  const shifted = new Date(value.getTime() - value.getTimezoneOffset() * 60_000);
  return shifted.toISOString().slice(0, 16);
}
