import type { CommunicationDraft } from "@/api/communication-api";
export function communicationDraftValid(draft: CommunicationDraft, now = Date.now()) {
  const start = draft.availableAt ? new Date(draft.availableAt).getTime() : now;
  const end = draft.expiresAt ? new Date(draft.expiresAt).getTime() : null;
  return (
    !!draft.messageTitle.trim() &&
    draft.messageTitle.length <= 160 &&
    !!draft.messageBody.trim() &&
    draft.messageBody.length <= 10000 &&
    draft.accountIds.length > 0 &&
    draft.accountIds.length <= 500 &&
    new Set(draft.accountIds).size === draft.accountIds.length &&
    !(draft.kind === "WARNING" && draft.purpose === "MARKETING") &&
    (!draft.replies || draft.kind === "MESSAGE") &&
    Number.isFinite(start) &&
    (end === null || (Number.isFinite(end) && end > start && end > now))
  );
}
export function localDateTime(value: string | null) {
  if (!value) return "";
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return "";
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}
