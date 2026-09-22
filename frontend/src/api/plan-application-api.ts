import type {
  BillingCycle,
  ExactDecimal,
  PageResponse,
  SubscriptionChangePreview,
  SubscriptionStatus,
} from "@/api/contracts";
import { apiRequest, jsonBody } from "@/api/http";
import type { NoticeDelivery } from "@/api/repricing-api";

export type ContentTiming = "NOW" | "AT_RENEWAL" | "AT_DATE";
export type EffectiveQuotaLimit = SubscriptionChangePreview["effectiveQuotaLimits"][number];
export type SubscriptionChangeConflict = SubscriptionChangePreview["conflicts"][number];
export type ContentNoticePolicy = "IN_APP" | "EMAIL" | "EMAIL_REQUIRED";
export type ContentItemStatus =
  | "ASSESSING"
  | "READY"
  | "WAITING"
  | "APPLIED"
  | "UNCHANGED"
  | "EXCLUDED"
  | "CONFLICT"
  | "FAILED"
  | "CANCELLED";
export type ContentJobStatus =
  | "ASSESSING"
  | "PREVIEWED"
  | "QUEUED"
  | "SCHEDULED"
  | "RUNNING"
  | "COMPLETED"
  | "COMPLETED_WITH_ERRORS"
  | "CANCELLED";
export type ContentRequest = {
  sourcePlanId: string;
  scope: "VERSION" | "FAMILY";
  audience: "SELECTED" | "FILTERED" | "ALL";
  accountIds: string[];
  excludedAccountIds: string[];
  statuses: SubscriptionStatus[];
  search: string | null;
  currency: string | null;
  billingCycle: BillingCycle | null;
  application: { timing: ContentTiming; notBefore: string | null; reason: string };
  notificationPolicy: ContentNoticePolicy;
};
export type ContentSummary = {
  id: string;
  familyId: string;
  targetPlanId: string;
  status: ContentJobStatus;
  counts: Partial<Record<ContentItemStatus, number>>;
  createdAt: string;
  evaluatedAt: string;
  executeAt: string | null;
  completedAt: string | null;
  requestedByUserId: string;
  reason: string;
  version: number;
};
export type ContentDetail = {
  summary: ContentSummary;
  definition: { targetPlanId: string; request: ContentRequest };
  conflicts: { primaryReason: string; accounts: number; resolutionKind?: string }[];
  reviewInvalidated: boolean;
  previewToken: string | null;
  expiresAt: string | null;
};
export type ContentImpact = {
  sourceVersion: number;
  targetVersion: number;
  retainedTotal: ExactDecimal;
  currency: string;
  conflicts: SubscriptionChangeConflict[];
  beforeLimits: EffectiveQuotaLimit[];
  afterLimits: EffectiveQuotaLimit[];
  removedFeatures: string[];
  addedFeatures?: string[];
};
export type ContentNoticeState = "SCHEDULED" | "APPLIED" | "CONFLICT" | "CANCELLED";
export type ContentResult = {
  id: string;
  status: ContentItemStatus;
  frozenSubscriptionId: string;
  impact: ContentImpact | null;
  executionConflicts: SubscriptionChangeConflict[];
  operationId: string | null;
  outcomeCode: string | null;
  attempts: number;
  nextAttemptAt: string | null;
  completedAt: string | null;
  notice: {
    id: string;
    state: ContentNoticeState;
    emailDelivery: NoticeDelivery;
    attempts: number;
    createdAt: string;
    required: boolean;
  } | null;
};
export type ContentIdentity = { itemId: string; accountId: string; accountName: string };
export type ContentNotice = {
  id: string;
  planName: string;
  sourceVersion: number;
  targetVersion: number;
  state: ContentNoticeState;
  timing: ContentTiming;
  plannedAt: string;
  effectiveAt: string | null;
  beforeLimits: EffectiveQuotaLimit[];
  afterLimits: EffectiveQuotaLimit[];
  removedFeatures: string[];
  addedFeatures?: string[];
  financialTermsRetained: boolean;
  read: boolean;
  createdAt: string;
};
export type SubscriberView = "ALL" | "CURRENT_VERSION" | "OTHER_VERSIONS" | "PENDING" | "NEEDS_REVIEW";
export type FamilySubscriber = {
  subscriptionId: string;
  accountId: string;
  accountName: string;
  planId: string;
  productVersionNumber: number;
  status: SubscriptionStatus;
  retainedTotal: ExactDecimal;
  currency: string;
  billingCycle: BillingCycle;
  periodEnd: string;
};
export type PlanHistoryEvent = {
  id: string;
  occurredAt: string;
  kind: "VERSION" | "APPLICATION";
  action: string;
  outcome: "SUCCEEDED" | "FAILED";
  actorUserId: string | null;
  actorLabel: string | null;
  resourceId: string;
  productVersionNumber: number | null;
};
const admin = <T>(path: string, options: Parameters<typeof apiRequest>[1] = {}) =>
  apiRequest<T>(`/api/admin/plan-version-applications${path}`, { ...options, audience: "admin" });
const post = <T>(path: string, body: unknown) => admin<T>(path, { method: "POST", body: jsonBody(body) });
export const planApplicationApi = {
  history: (planId: string, query: { page: number; kind?: string; from?: string; until?: string; actorId?: string }) =>
    apiRequest<PageResponse<PlanHistoryEvent>>(`/api/admin/plans/${planId}/version-history`, {
      audience: "admin",
      query: { size: 20, ...query },
    }),
  subscribers: (
    planId: string,
    query: {
      view?: SubscriberView;
      search?: string;
      status?: SubscriptionStatus;
      currency?: string;
      cycle?: BillingCycle;
      page?: number;
      size?: number;
    },
  ) =>
    apiRequest<PageResponse<FamilySubscriber>>(`/api/admin/plans/${planId}/family-subscribers`, {
      audience: "admin",
      query: { size: 20, ...query },
    }),
  create: (targetPlanId: string, request: ContentRequest) =>
    admin<ContentDetail>("", { method: "POST", query: { targetPlanId }, body: jsonBody(request) }),
  list: (planId: string, page = 0) => admin<PageResponse<ContentSummary>>("", { query: { planId, page, size: 20 } }),
  detail: (id: string) => admin<ContentDetail>(`/${id}`),
  results: (id: string, page = 0, status?: ContentItemStatus, reason?: string) =>
    admin<PageResponse<ContentResult>>(`/${id}/results`, { query: { page, size: 20, status, reason } }),
  identities: (id: string, resultIds: string[]) => post<ContentIdentity[]>(`/${id}/identities`, { resultIds }),
  confirm: (id: string, previewToken: string, applyReadyOnly: boolean) =>
    post<ContentDetail>(`/${id}/confirm`, { previewToken, applyReadyOnly }),
  cancel: (id: string, reason: string) => post<ContentDetail>(`/${id}/cancel`, { reason }),
  retry: (id: string, reason: string) => post<ContentDetail>(`/${id}/retry`, { reason }),
  retryNotices: (id: string, noticeIds: string[], reason: string) =>
    post<ContentDetail>(`/${id}/notices/retry`, { noticeIds, reason }),
  notices: (page = 0) =>
    apiRequest<PageResponse<ContentNotice>>("/api/v1/subscriptions/content-notices", {
      audience: "client",
      query: { page, size: 20 },
    }),
  markRead: (id: string) =>
    apiRequest<void>(`/api/v1/subscriptions/content-notices/${id}/read`, { audience: "client", method: "POST" }),
};
