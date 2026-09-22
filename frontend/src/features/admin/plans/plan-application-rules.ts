import type { ContentDetail, ContentJobStatus, ContentRequest, EffectiveQuotaLimit } from "@/api/plan-application-api";

export const contentPolling = (status?: ContentJobStatus) =>
  status && ["ASSESSING", "QUEUED", "SCHEDULED", "RUNNING"].includes(status) ? 5000 : false;

export function canConfirmContent(detail: ContentDetail, partial: boolean, now = Date.now()) {
  return (
    detail.summary.status === "PREVIEWED" &&
    !detail.reviewInvalidated &&
    !!detail.previewToken &&
    !!detail.expiresAt &&
    Date.parse(detail.expiresAt) > now &&
    (detail.summary.counts.READY ?? 0) > 0 &&
    (!(detail.summary.counts.CONFLICT ?? 0) || partial)
  );
}

export function validContentRequest(request: ContentRequest, now = Date.now()) {
  if (!request.sourcePlanId || !request.application.reason.trim() || request.application.reason.length > 2000)
    return false;
  if (request.currency !== null && !/^[A-Z]{3}$/.test(request.currency)) return false;
  if (request.audience === "SELECTED" && !request.accountIds.length) return false;
  if (request.application.timing === "AT_DATE") {
    if (!request.application.notBefore || !(Date.parse(request.application.notBefore) > now)) return false;
  } else if (request.application.notBefore !== null) return false;
  if (request.application.timing === "AT_RENEWAL" && request.statuses.includes("TRIALING")) return false;
  return request.statuses.length > 0;
}

export function changedContentLimits(before: EffectiveQuotaLimit[], after: EffectiveQuotaLimit[]) {
  const key = (value: EffectiveQuotaLimit) => `${value.featureCode}\u0000${value.resource}`;
  const left = new Map(before.map((value) => [key(value), value]));
  const right = new Map(after.map((value) => [key(value), value]));
  return [...new Set([...left.keys(), ...right.keys()])].sort().flatMap((id) => {
    const old = left.get(id),
      next = right.get(id);
    if (old && next && old.mode === next.mode && old.effectiveLimit === next.effectiveLimit) return [];
    const resource = old ?? next;
    return resource
      ? [{ id, featureCode: resource.featureCode, resource: resource.resource, before: old, after: next }]
      : [];
  });
}

export const contentApplicationKey = ["admin", "plan-applications"] as const;

export function contentConflictCategory(code: string, kind?: string) {
  const kinds: Record<string, string> = {
    REVIEW_NOTICE: "noticeConflict",
    REVIEW_CAPACITY: "capacityConflict",
    REVIEW_PURCHASES: "purchaseConflict",
    REVIEW_COMMERCIAL_TERMS: "commercialConflict",
    REVIEW_SUBSCRIPTION: "subscriptionConflict",
    REVIEW_VERSION: "reviewConflict",
  };
  if (kind && kinds[kind]) return kinds[kind];
  if (/NOTICE/.test(code)) return "noticeConflict";
  if (/QUOTA|CAPACITY|USAGE|LIMIT/.test(code)) return "capacityConflict";
  if (/ADD_ON|PACK|PURCHASE|DEPENDENC|DUPLICATE_PAID/.test(code)) return "purchaseConflict";
  if (/POLICY|BONUS|OFFER|AGREEMENT/.test(code)) return "commercialConflict";
  if (/PENDING|PERIOD|TRIAL|ENTITLED|ACCOUNT/.test(code)) return "subscriptionConflict";
  return "reviewConflict";
}
