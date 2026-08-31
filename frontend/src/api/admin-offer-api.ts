import type { PageResponse, UUID } from "@/api/contracts";
import { apiRequest, jsonBody } from "@/api/http";
import type {
  OfferAccountAssessment,
  OfferAccountChoice,
  OfferAdminAcceptance,
  OfferComparison,
  OfferCreateInput,
  OfferDefinitionPreview,
  OfferDiscovery,
  OfferEditableDefinition,
  OfferHistoryEntry,
  OfferMutation,
  OfferOperationState,
  OfferOwner,
  OfferOwnerChoice,
  OfferOwnerMutation,
  OfferPricedChoice,
  OfferProductOwnerType,
  OfferPublicationPreview,
  OfferQuotaResourceChoice,
  OfferRedemption,
  OfferRedemptionIdentity,
  OfferRedemptionStatus,
  OfferRevision,
  OfferStats,
  OfferStatus,
  OfferSummary,
  OfferSurface,
  OfferUpdateInput,
} from "@/api/offer-contracts";

type RequestOptions = { signal?: AbortSignal };

function admin<T>(path: string, options: Parameters<typeof apiRequest<T>>[1] = {}) {
  return apiRequest<T>(`/api/admin/offers${path}`, { audience: "admin", ...options });
}

export const adminOfferApi = {
  list: (
    query: {
      search?: string;
      status?: OfferStatus;
      campaignId?: UUID;
      discovery?: OfferDiscovery;
      acceptance?: "CLIENT_OR_OPERATOR" | "OPERATOR_ONLY";
      includeArchived?: boolean;
      page?: number;
      size?: number;
      sort?: string;
      direction?: "asc" | "desc";
    },
    options: RequestOptions = {},
  ) => admin<PageResponse<OfferSummary>>("", { query, signal: options.signal }),
  detail: (id: UUID, options: RequestOptions = {}) =>
    admin<import("@/api/offer-contracts").OfferDetail>(`/${id}`, options),
  operations: (id: UUID, options: RequestOptions = {}) => admin<OfferOperationState>(`/${id}/operations`, options),
  editableDefinition: (id: UUID, options: RequestOptions = {}) =>
    admin<OfferEditableDefinition>(`/${id}/editable-definition`, options),
  previewCreateDefinition: (input: OfferCreateInput, options: RequestOptions = {}) =>
    admin<OfferDefinitionPreview>("/definition-preview", {
      method: "POST",
      body: jsonBody(input),
      signal: options.signal,
    }),
  create: (input: OfferCreateInput) => admin<OfferMutation>("", { method: "POST", body: jsonBody(input) }),
  previewUpdateDefinition: (id: UUID, input: OfferUpdateInput, options: RequestOptions = {}) =>
    admin<OfferDefinitionPreview>(`/${id}/definition-preview`, {
      method: "POST",
      body: jsonBody(input),
      signal: options.signal,
    }),
  update: (id: UUID, input: OfferUpdateInput) =>
    admin<OfferMutation>(`/${id}`, { method: "PUT", body: jsonBody(input) }),
  duplicate: (id: UUID, input: { version: number; name: string; reason: string }) =>
    admin<OfferMutation>(`/${id}/duplicate`, { method: "POST", body: jsonBody(input) }),
  revise: (id: UUID, input: { version: number; reason: string }) =>
    admin<OfferMutation>(`/${id}/revisions`, { method: "POST", body: jsonBody(input) }),
  revisions: (id: UUID, page = 0, size = 20, options: RequestOptions = {}) =>
    admin<PageResponse<OfferRevision>>(`/${id}/revisions`, { query: { page, size }, signal: options.signal }),
  compare: (id: UUID, comparedId: UUID, options: RequestOptions = {}) =>
    admin<OfferComparison>(`/${id}/compare/${comparedId}`, options),
  publicationPreview: (id: UUID, options: RequestOptions = {}) =>
    admin<OfferPublicationPreview>(`/${id}/publication-preview`, options),
  publish: (id: UUID, input: { version: number; reason: string; previewToken: string }) =>
    admin<OfferMutation>(`/${id}/publish`, { method: "POST", body: jsonBody(input) }),
  retire: (id: UUID, input: { version: number; reason: string }) =>
    admin<OfferMutation>(`/${id}/retire`, { method: "POST", body: jsonBody(input) }),
  restore: (id: UUID, input: { version: number; reason: string; previewToken: string }) =>
    admin<OfferMutation>(`/${id}/restore`, { method: "POST", body: jsonBody(input) }),
  archive: (id: UUID, input: { version: number; reason: string }) =>
    admin<OfferMutation>(`/${id}/archive`, { method: "POST", body: jsonBody(input) }),
  deleteDraft: (id: UUID, input: { version: number; reason: string }) =>
    admin<void>(`/${id}`, { method: "DELETE", body: jsonBody(input) }),
  owner: (id: UUID, options: RequestOptions = {}) => admin<OfferOwner>(`/${id}/owner`, options),
  reassignOwner: (id: UUID, input: { lineageVersion: number; ownerAdminUserId: UUID; reason: string }) =>
    admin<OfferOwnerMutation>(`/${id}/owner`, { method: "PUT", body: jsonBody(input) }),
  stats: (id: UUID, options: RequestOptions = {}) => admin<OfferStats>(`/${id}/stats`, options),
  history: (id: UUID, page = 0, size = 20, options: RequestOptions = {}) =>
    admin<PageResponse<OfferHistoryEntry>>(`/${id}/history`, { query: { page, size }, signal: options.signal }),
  redemptions: (
    id: UUID,
    query: {
      status?: OfferRedemptionStatus;
      surface?: OfferSurface;
      page?: number;
      size?: number;
      sort?: string;
      direction?: "asc" | "desc";
    },
    options: RequestOptions = {},
  ) => admin<PageResponse<OfferRedemption>>(`/${id}/redemptions`, { query, signal: options.signal }),
  redemption: (id: UUID, redemptionId: UUID, options: RequestOptions = {}) =>
    admin<OfferRedemption>(`/${id}/redemptions/${redemptionId}`, options),
  resolveRedemptionIdentities: (id: UUID, redemptionIds: UUID[]) =>
    admin<OfferRedemptionIdentity[]>(`/${id}/redemption-identity-resolution`, {
      method: "POST",
      body: jsonBody({ redemptionIds }),
    }),
  accountChoices: (
    query: { search?: string; active?: boolean; page?: number; size?: number },
    options: RequestOptions = {},
  ) => admin<PageResponse<OfferAccountChoice>>("/account-choices", { query, signal: options.signal }),
  resolveAccountChoices: (ids: UUID[]) =>
    admin<OfferAccountChoice[]>("/account-choice-resolution", { method: "POST", body: jsonBody(ids) }),
  productChoices: (
    query: { ownerType: OfferProductOwnerType; search?: string; page?: number; size?: number },
    options: RequestOptions = {},
  ) => admin<PageResponse<OfferPricedChoice>>("/product-price-choices", { query, signal: options.signal }),
  resolveProductChoices: (priceIds: UUID[]) =>
    admin<OfferPricedChoice[]>("/product-price-choice-resolution", { method: "POST", body: jsonBody(priceIds) }),
  quotaResourceChoices: (selectedPriceIds: UUID[], search?: string, options: RequestOptions = {}) =>
    admin<OfferQuotaResourceChoice[]>("/quota-resource-choices", {
      query: { selectedPriceIds, search },
      signal: options.signal,
    }),
  ownerChoices: (query: { search?: string; page?: number; size?: number }, options: RequestOptions = {}) =>
    admin<PageResponse<OfferOwnerChoice>>("/owner-choices", { query, signal: options.signal }),
  resolveOwnerChoices: (ids: UUID[]) =>
    admin<OfferOwnerChoice[]>("/owner-choice-resolution", { method: "POST", body: jsonBody(ids) }),
  campaignChoices: (query: { search?: string; page?: number; size?: number }, options: RequestOptions = {}) =>
    admin<PageResponse<import("@/api/offer-contracts").OfferCampaignChoice>>("/campaign-choices", {
      query,
      signal: options.signal,
    }),
  resolveCampaignChoices: (ids: UUID[]) =>
    admin<import("@/api/offer-contracts").OfferCampaignChoice[]>("/campaign-choice-resolution", {
      method: "POST",
      body: jsonBody(ids),
    }),
  previewForAccount: (id: UUID, accountId: UUID, options: RequestOptions = {}) =>
    admin<OfferAccountAssessment>(`/${id}/accounts/${accountId}/preview`, { method: "POST", signal: options.signal }),
  applyForAccount: (
    id: UUID,
    accountId: UUID,
    idempotencyKey: string,
    input: { previewToken: string; reason: string },
  ) =>
    admin<OfferAdminAcceptance>(`/${id}/accounts/${accountId}/apply`, {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: jsonBody(input),
    }),
};
