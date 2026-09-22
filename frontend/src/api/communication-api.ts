import type { PageResponse } from "./contracts";
import { apiRequest, jsonBody } from "./http";
export type CommunicationKind = "NOTICE" | "WARNING" | "ACTION" | "OFFER";
export type NotificationTopic =
  | "GENERAL"
  | "ACCOUNT"
  | "BILLING"
  | "COLLABORATION"
  | "COMMERCIAL"
  | "OPERATIONS"
  | "TASKS";
export type NotificationSetting = { topic: NotificationTopic; inAppEnabled: boolean; emailEnabled: boolean };
export type InternalNotice = { commandId: string; messageTitle: string; messageBody: string; memberIds: string[] };
export type SentNotice = {
  commandId: string;
  messageTitle: string;
  messageBody: string;
  createdAt: string;
  recipients: number;
  delivered: number;
  failed: number;
  pending: number;
};
export type NotificationEvent = {
  id: string;
  type: string;
  state: "PENDING" | "DELIVERED" | "FAILED";
  attempts: number;
  nextAttemptAt: string;
  failureCode: string | null;
  version: number;
  createdAt?: string;
  updatedAt?: string;
  sourcePath?: string | null;
  canRetry?: boolean;
};
export type NotificationEmail = Omit<NotificationEvent, "state" | "nextAttemptAt"> & {
  state: "PENDING" | "SENDING" | "SENT" | "SUPPRESSED" | "FAILED" | "CANCELLED";
  nextAttemptAt: string | null;
  canRetry: boolean;
  eventId?: string;
};
export type CommunicationPurpose = "SERVICE" | "MARKETING";
export type CommunicationDraft = {
  kind: CommunicationKind;
  purpose: CommunicationPurpose;
  messageTitle: string;
  messageBody: string;
  accountIds: string[];
  email: boolean;
  offerId: string | null;
  availableAt: string | null;
  expiresAt: string | null;
};
export type CommunicationPublication = CommunicationDraft & {
  availableAt: string;
  id: string;
  version: number;
  state: "DRAFT" | "PUBLISHED" | "CANCELLED";
  createdAt: string;
};
export type CommunicationItem = {
  id: string;
  kind: CommunicationKind;
  purpose: CommunicationPurpose;
  messageTitle: string;
  messageBody: string;
  source: string;
  sourceState: string;
  actionPath: string | null;
  availableAt: string;
  expiresAt: string | null;
  read: boolean;
  acknowledged: boolean;
  archived: boolean;
  canAcknowledge: boolean;
  canArchive: boolean;
  topic: NotificationTopic;
  eventType: string | null;
  resourceId: string | null;
  audience: "ACCOUNT" | "MEMBER" | "PLATFORM" | "OPERATOR";
  resolved: boolean;
  senderName?: string | null;
  priority?: "NORMAL" | "HIGH";
};
export type CommunicationRecipient = {
  id: string;
  accountId: string;
  accountName: string;
  visible: boolean;
  emailDelivery: string;
  emailAttempts: number;
  readers: number;
  acknowledgements: number;
};
export type CommunicationPreference = { marketingInApp: boolean; marketingEmail: boolean };
const admin = <T>(path: string, options: Parameters<typeof apiRequest>[1] = {}) =>
  apiRequest<T>(`/api/admin/customer-communications${path}`, { ...options, audience: "admin" });
const client = <T>(path: string, options: Parameters<typeof apiRequest>[1] = {}) =>
  apiRequest<T>(`/api/v1/communications${path}`, { ...options, audience: "client" });
const body = (value: unknown, method = "POST") => ({ method, body: jsonBody(value) });
const operator = <T>(path: string, options: Parameters<typeof apiRequest>[1] = {}) =>
  apiRequest<T>(`/api/admin/notifications${path}`, { ...options, audience: "admin" });
const inboxApi = (platform: boolean) => (platform ? operator : client);
export const communicationApi = {
  list: (page = 0) => admin<PageResponse<CommunicationPublication>>("", { query: { page, size: 20 } }),
  detail: (id: string) => admin<CommunicationPublication>(`/${id}`),
  selectedRecipients: (id: string) => admin<{ id: string; name: string }[]>(`/${id}/selected-recipients`),
  choices: (search: string, page = 0) =>
    admin<PageResponse<{ id: string; name: string }>>("/recipients", { query: { search, page, size: 20 } }),
  create: (draft: CommunicationDraft) => admin<CommunicationPublication>("", body(draft)),
  edit: (id: string, version: number, draft: CommunicationDraft) =>
    admin<CommunicationPublication>(`/${id}`, body({ version, draft }, "PUT")),
  publish: (id: string, version: number, reason: string) =>
    admin<CommunicationPublication>(`/${id}/publish`, body({ version, reason })),
  cancel: (id: string, version: number, reason: string) =>
    admin<CommunicationPublication>(`/${id}/cancel`, body({ version, reason })),
  results: (id: string, page = 0) =>
    admin<PageResponse<CommunicationRecipient>>(`/${id}/results`, { query: { page, size: 20 } }),
  retry: (id: string) => admin<void>(`/entries/${id}/retry-email`, { method: "POST" }),
  inbox: (
    kind: CommunicationKind | undefined,
    archived: boolean,
    unread: boolean,
    page = 0,
    topic?: NotificationTopic,
    platform = false,
    companyId?: string | null,
  ) =>
    inboxApi(platform)<PageResponse<CommunicationItem>>("", {
      query: { kind, topic, archived, unread, page, size: 20 },
      context: { companyId },
    }),
  item: (id: string, platform = false, companyId?: string | null) =>
    inboxApi(platform)<CommunicationItem>(`/${id}`, { context: { companyId } }),
  summary: (platform = false, companyId?: string | null) =>
    inboxApi(platform)<{ unread: number }>("/summary", { context: { companyId } }),
  interact: (
    id: string,
    action: "read" | "acknowledge" | "archive",
    archived = true,
    platform = false,
    companyId?: string | null,
  ) =>
    inboxApi(platform)<void>(`/${id}/${action}`, {
      method: "POST",
      context: { companyId },
      query: action === "archive" ? { archived } : undefined,
    }),
  settings: (platform = false) => inboxApi(platform)<NotificationSetting[]>(platform ? "/preferences" : "/settings"),
  language: (platform = false) => inboxApi(platform)<{ language: "fr" | "ar" }>("/language"),
  setLanguage: (language: "fr" | "ar", platform = false) =>
    inboxApi(platform)<{ language: "fr" | "ar" }>("/language", body({ language }, "PUT")),
  setting: (setting: NotificationSetting, platform = false) =>
    inboxApi(platform)<NotificationSetting>(platform ? "/preferences" : "/settings", body(setting, "PUT")),
  members: (search: string, page = 0) =>
    client<PageResponse<{ id: string; name: string }>>("/recipients", { query: { search, page, size: 20 } }),
  sendInternal: (notice: InternalNotice) =>
    client<{ commandId: string; recipients: number }>("/internal", body(notice)),
  sent: (page = 0) => client<PageResponse<SentNotice>>("/internal", { query: { page, size: 20 } }),
  events: (state: NotificationEvent["state"] | undefined, page = 0) =>
    operator<PageResponse<NotificationEvent>>("/delivery", { query: { state, page, size: 20 } }),
  retryEvent: (id: string, version: number, reason: string) =>
    operator<void>(`/delivery/${id}/retry`, body({ version, reason })),
  notificationEmails: (state: NotificationEmail["state"] | undefined, page = 0) =>
    operator<PageResponse<NotificationEmail>>("/delivery/emails", { query: { state, page, size: 20 } }),
  retryNotificationEmail: (id: string, version: number, reason: string) =>
    operator<void>(`/delivery/emails/${id}/retry`, body({ version, reason })),
  preferences: () => client<CommunicationPreference>("/preferences"),
  updatePreferences: (value: CommunicationPreference) =>
    client<CommunicationPreference>("/preferences", body(value, "PUT")),
};
