import type { CommunicationDraft, CommunicationItem } from "@/api/communication-api";
import { manualNotificationValid } from "./manual-notification-rules";
export function communicationDraftValid(draft: CommunicationDraft, now = Date.now()) {
  const start = draft.availableAt ? new Date(draft.availableAt).getTime() : now;
  const end = draft.expiresAt ? new Date(draft.expiresAt).getTime() : null;
  return (
    manualNotificationValid(draft) &&
    draft.accountIds.length > 0 &&
    draft.accountIds.length <= (draft.kind === "OFFER" ? 100 : 500) &&
    new Set(draft.accountIds).size === draft.accountIds.length &&
    !(draft.kind === "WARNING" && draft.purpose === "MARKETING") &&
    ["NOTICE", "WARNING", "OFFER"].includes(draft.kind) &&
    (draft.kind !== "OFFER" || (!!draft.offerId && draft.purpose === "MARKETING")) &&
    Number.isFinite(start) &&
    (end === null || (Number.isFinite(end) && end > start && end > now))
  );
}
export function notificationAction(item: CommunicationItem, platform = false) {
  if (item.resolved || ["CANCELLED", "WITHDRAWN", "APPLIED", "UNAVAILABLE", "RESOLVED"].includes(item.sourceState))
    return null;
  const path = item.actionPath;
  if (!path?.startsWith(platform ? "/admin/" : "/app/") || path.includes("\\") || path.includes("//")) return null;
  const label =
    item.topic === "BILLING"
      ? "billingAction"
      : item.topic === "COLLABORATION"
        ? "collaborationAction"
        : item.kind === "OFFER"
          ? "offerAction"
          : item.eventType?.startsWith("account.member_")
            ? "memberAction"
            : ["PLAN_CONTENT", "REPRICING"].includes(item.source)
              ? "subscriptionAction"
              : "source";
  return { path, label };
}
export const notificationTopics = [
  "GENERAL",
  "ACCOUNT",
  "BILLING",
  "COLLABORATION",
  "COMMERCIAL",
  "OPERATIONS",
  "TASKS",
] as const;
export function localDateTime(value: string | null) {
  if (!value) return "";
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return "";
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}
