import type {
  ExactDecimal,
  PageResponse,
  SubscriptionStatus,
} from "@/api/contracts";
import { apiRequest, jsonBody } from "@/api/http";

export type RepricingState =
  | "READY"
  | "PENDING"
  | "AWAITING_PAYMENT"
  | "APPLIED"
  | "CONFLICT"
  | "CANCELLED";
export type NoticeDelivery =
  | "NOT_REQUESTED"
  | "PENDING"
  | "SENDING"
  | "SENT"
  | "SUPPRESSED"
  | "FAILED"
  | "CANCELLED";
export type RepricingRequest = {
  sourcePriceId: string;
  targetPriceId: string;
  audience: "SELECTED" | "TARIFF_HOLDERS" | "FILTERED" | "SEGMENT";
  accountIds: string[];
  excludedAccountIds: string[];
  planId: string | null;
  subscriptionStatus: SubscriptionStatus | null;
  segmentId: string | null;
  notBefore: string | null;
  email: boolean;
  reason: string;
};
export type RepricingSummary = {
  id: string;
  status: "PREVIEWED" | "CONFIRMED" | "CANCELLED";
  productName: string;
  currencyCode: string;
  billingCycle: string;
  sourcePrice: ExactDecimal;
  targetPrice: ExactDecimal;
  targetCount: number;
  readyCount: number;
  pendingCount: number;
  appliedCount: number;
  conflictCount: number;
  cancelledCount: number;
  awaitingPaymentCount: number;
  createdAt: string;
};
export type RepricingItem = {
  id: string;
  status: RepricingState;
  blocker: string | null;
  quantity: number;
  oldUnitPrice: ExactDecimal;
  newUnitPrice: ExactDecimal;
  oldTotal: ExactDecimal | null;
  newTotal: ExactDecimal | null;
  currencyCode: string;
  billingCycle: string;
  effectiveAt: string | null;
  operationId: string | null;
  delivery: NoticeDelivery;
  emailAttempts: number;
};
export type RepricingDetail = {
  summary: RepricingSummary;
  request: RepricingRequest;
  confirmedAt: string | null;
};
export type RepricingPreview = {
  summary: RepricingSummary;
  previewToken: string;
  expiresAt: string;
  sample: RepricingItem[];
};
export type RepricingIdentity = {
  itemId: string;
  accountId: string;
  accountName: string;
};
export type PriceNotice = {
  id: string;
  productName: string;
  change: RepricingItem;
  read: boolean;
  createdAt: string;
};
const admin = <T>(
  path: string,
  options: Parameters<typeof apiRequest>[1] = {},
) =>
  apiRequest<T>(`/api/admin/subscription-repricing${path}`, {
    ...options,
    audience: "admin",
  });
const post = <T>(path: string, body: unknown) =>
  admin<T>(path, { method: "POST", body: jsonBody(body) });
export const repricingApi = {
  preview: (request: RepricingRequest) =>
    post<RepricingPreview>("/preview", request),
  confirm: (id: string, previewToken: string) =>
    post<RepricingDetail>(`/${id}/confirm`, { previewToken }),
  list: (page = 0) =>
    admin<PageResponse<RepricingSummary>>("", { query: { page, size: 20 } }),
  detail: (id: string) => admin<RepricingDetail>(`/${id}`),
  results: (id: string, page = 0, status?: RepricingState) =>
    admin<PageResponse<RepricingItem>>(`/${id}/results`, {
      query: { page, size: 20, status },
    }),
  identities: (id: string, itemIds: string[]) =>
    post<RepricingIdentity[]>(`/${id}/identities`, { itemIds }),
  cancel: (id: string, reason: string, itemId?: string) =>
    post<RepricingDetail>(
      `/${id}${itemId ? `/results/${itemId}` : ""}/cancel`,
      { reason },
    ),
  retry: (id: string, itemId: string, reason: string) =>
    post<RepricingDetail>(`/${id}/results/${itemId}/retry`, { reason }),
  retryNotices: (id: string, reason: string) =>
    post<RepricingDetail>(`/${id}/retry-notices`, { reason }),
  notices: (page = 0) =>
    apiRequest<PageResponse<PriceNotice>>("/api/v1/subscriptions/notices", {
      audience: "client",
      query: { page, size: 20 },
    }),
  markRead: (id: string) =>
    apiRequest<void>(`/api/v1/subscriptions/notices/${id}/read`, {
      audience: "client",
      method: "POST",
    }),
};
