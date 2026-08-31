import type { PageResponse, UUID } from "@/api/contracts";
import { apiRequest, jsonBody } from "@/api/http";
import type {
  OfferClient,
  OfferClientAcceptance,
  OfferClientRedemption,
  OfferCodeResolution,
  OfferEligibilityPreview,
} from "@/api/offer-contracts";

type RequestOptions = { signal?: AbortSignal };

function context() {
  return {
    companyId: window.localStorage.getItem("hiveapp-selected-company"),
    isB2B: window.localStorage.getItem("hiveapp-b2b-mode") === "true",
  };
}

function client<T>(path: string, options: Parameters<typeof apiRequest<T>>[1] = {}) {
  return apiRequest<T>(`/api/v1/subscriptions/offers${path}`, {
    audience: "client",
    context: context(),
    ...options,
  });
}

export const clientOfferApi = {
  catalogue: (
    query: { page?: number; size?: number; sort?: string; direction?: "asc" | "desc" },
    options: RequestOptions = {},
  ) => client<PageResponse<OfferClient>>("", { query, signal: options.signal }),
  detail: (id: UUID, options: RequestOptions = {}) => client<OfferClient>(`/${id}`, options),
  resolveCode: (code: string, options: RequestOptions = {}) =>
    client<OfferCodeResolution>("/code-resolution", {
      method: "POST",
      body: jsonBody({ code }),
      signal: options.signal,
    }),
  preview: (id: UUID, discoveryToken?: string | null, options: RequestOptions = {}) =>
    client<OfferEligibilityPreview>(`/${id}/preview`, {
      method: "POST",
      body: jsonBody({ discoveryToken: discoveryToken || null }),
      signal: options.signal,
    }),
  accept: (id: UUID, previewToken: string, idempotencyKey: string) =>
    client<OfferClientAcceptance>(`/${id}/accept`, {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: jsonBody({ previewToken }),
    }),
  history: (
    query: { page?: number; size?: number; sort?: string; direction?: "asc" | "desc" },
    options: RequestOptions = {},
  ) => client<PageResponse<OfferClientRedemption>>("/redemptions", { query, signal: options.signal }),
  redemption: (id: UUID, options: RequestOptions = {}) => client<OfferClientRedemption>(`/redemptions/${id}`, options),
};
