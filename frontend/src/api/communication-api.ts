import type { PageResponse } from "./contracts";
import { apiRequest, jsonBody } from "./http";
export type CommunicationKind = "NOTICE" | "WARNING" | "MESSAGE";
export type CommunicationPurpose = "SERVICE" | "MARKETING";
export type CommunicationDraft = {
  kind: CommunicationKind;
  purpose: CommunicationPurpose;
  messageTitle: string;
  messageBody: string;
  accountIds: string[];
  email: boolean;
  replies: boolean;
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
  canReply: boolean;
  closed: boolean;
};
export type CommunicationReply = {
  id: string;
  commandId: string;
  fromAdmin: boolean;
  replyBody: string;
  createdAt: string;
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
  replies: number;
  closed: boolean;
};
export type CommunicationPreference = { marketingInApp: boolean; marketingEmail: boolean };
const admin = <T>(path: string, options: Parameters<typeof apiRequest>[1] = {}) =>
  apiRequest<T>(`/api/admin/customer-communications${path}`, { ...options, audience: "admin" });
const client = <T>(path: string, options: Parameters<typeof apiRequest>[1] = {}) =>
  apiRequest<T>(`/api/v1/communications${path}`, { ...options, audience: "client" });
const body = (value: unknown, method = "POST") => ({ method, body: jsonBody(value) });
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
  close: (id: string, closed: boolean) => admin<void>(`/entries/${id}/close`, { method: "POST", query: { closed } }),
  inbox: (kind: CommunicationKind | undefined, archived: boolean, unread: boolean, page = 0) =>
    client<PageResponse<CommunicationItem>>("", { query: { kind, archived, unread, page, size: 20 } }),
  item: (id: string) => client<CommunicationItem>(`/${id}`),
  interact: (id: string, action: "read" | "acknowledge" | "archive", archived = true) =>
    client<void>(`/${id}/${action}`, { method: "POST", query: action === "archive" ? { archived } : undefined }),
  thread: (id: string, isAdmin: boolean, page = 0) =>
    (isAdmin ? admin : client)<PageResponse<CommunicationReply>>(`${isAdmin ? "/entries" : ""}/${id}/replies`, {
      query: { page, size: 20 },
    }),
  reply: (id: string, isAdmin: boolean, commandId: string, replyBody: string) =>
    (isAdmin ? admin : client)<CommunicationReply>(
      `${isAdmin ? "/entries" : ""}/${id}/replies`,
      body({ commandId, replyBody }),
    ),
  preferences: () => client<CommunicationPreference>("/preferences"),
  updatePreferences: (value: CommunicationPreference) =>
    client<CommunicationPreference>("/preferences", body(value, "PUT")),
};
