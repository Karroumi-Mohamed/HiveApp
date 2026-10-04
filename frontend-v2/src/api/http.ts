import type { ApiErrorBody } from "./contracts";

export type ApiAudience = "admin" | "client" | "public";
export type ClientContextHeaders = {
  companyId?: string | null;
  isB2B?: boolean;
};
let readToken: (audience: "admin" | "client") => string | null = () => null;
let handleUnauthorized: (audience: "admin" | "client") => void = () =>
  undefined;
let refreshAuth: (() => Promise<boolean>) | undefined;
export function configureHttpAuth(
  reader: typeof readToken,
  handler: typeof handleUnauthorized,
  refresh?: () => Promise<boolean>,
) {
  readToken = reader;
  handleUnauthorized = handler;
  refreshAuth = refresh;
}
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly body?: ApiErrorBody,
  ) {
    super(message);
    this.name = "ApiError";
  }
}
export async function apiRequest<T>(
  path: string,
  options: RequestInit & {
    audience?: ApiAudience;
    context?: ClientContextHeaders;
    query?: Record<string, unknown>;
  } = {},
): Promise<T> {
  const { audience = "public", context, query, ...init } = options;
  const origin = import.meta.env.VITE_API_URL?.trim().replace(/\/$/, "") || "";
  const url = new URL(`${origin}${path}`, window.location.origin);
  for (const [key, value] of Object.entries(query || {})) {
    if (value !== undefined && value !== null && value !== "")
      url.searchParams.set(key, String(value));
  }
  const headers = new Headers(init.headers);
  headers.set("Accept-Language", "en");
  if (init.body && !(init.body instanceof FormData))
    headers.set("Content-Type", "application/json");
  if (audience !== "public" && readToken(audience))
    headers.set("Authorization", `Bearer ${readToken(audience)}`);
  if (audience === "client" && context?.companyId)
    headers.set("X-Company-ID", context.companyId);
  if (audience === "client" && context?.isB2B) headers.set("X-Is-B2B", "true");
  async function send() {
    try {
      return await fetch(url, {
        ...init,
        headers,
        signal: init.signal ?? AbortSignal.timeout(30000),
      });
    } catch (error) {
      throw new ApiError(
        0,
        "CONNECTION_ERROR",
        error instanceof DOMException && error.name === "TimeoutError"
          ? "The backend took too long to respond. Try again."
          : "Could not reach the Hive backend. Check the connection and try again.",
      );
    }
  }
  let response = await send();
  if (
    response.status === 401 &&
    audience === "admin" &&
    refreshAuth &&
    (await refreshAuth())
  ) {
    headers.set("Authorization", `Bearer ${readToken("admin")}`);
    response = await send();
  }
  if (!response.ok) {
    if (response.status === 401 && audience !== "public")
      handleUnauthorized(audience);
    const body = (await response.json().catch(() => undefined)) as
      ApiErrorBody | undefined;
    throw new ApiError(
      response.status,
      body?.code || "HTTP_ERROR",
      body?.message ||
        (response.status >= 500
          ? "The backend is unavailable or could not complete this request. Check the connection and try again."
          : `The backend could not accept this request (HTTP ${response.status}).`),
      body,
    );
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
export const jsonBody = (value: unknown) => JSON.stringify(value);
