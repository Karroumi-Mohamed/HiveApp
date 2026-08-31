import type { ApiErrorBody } from "@/api/contracts";

export type ApiAudience = "admin" | "client" | "public";
export type ClientContextHeaders = { companyId?: string | null; isB2B?: boolean };

type TokenReader = (audience: Exclude<ApiAudience, "public">) => string | null;
type UnauthorizedHandler = (audience: Exclude<ApiAudience, "public">) => void;

let readToken: TokenReader = () => null;
let handleUnauthorized: UnauthorizedHandler = () => undefined;

export function configureHttpAuth(tokenReader: TokenReader, unauthorizedHandler: UnauthorizedHandler) {
  readToken = tokenReader;
  handleUnauthorized = unauthorizedHandler;
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

const configuredApiOrigin =
  typeof process !== "undefined" ? process.env.BUN_PUBLIC_API_URL?.trim().replace(/\/$/, "") : undefined;
const apiOrigin =
  configuredApiOrigin ??
  (typeof window !== "undefined" && window.location.port === "3000" ? "http://localhost:8080" : "");

export async function apiRequest<T>(
  path: string,
  options: RequestInit & {
    audience?: ApiAudience;
    context?: ClientContextHeaders;
    query?: Record<string, unknown>;
  } = {},
): Promise<T> {
  const { audience = "public", context, query, ...requestInit } = options;
  const url = new URL(`${apiOrigin}${path}`, window.location.origin);
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined && value !== null && value !== "") url.searchParams.set(key, String(value));
  }

  const headers = new Headers(requestInit.headers);
  if (requestInit.body && !(requestInit.body instanceof FormData)) headers.set("Content-Type", "application/json");
  if (audience !== "public") {
    const token = readToken(audience);
    if (token) headers.set("Authorization", `Bearer ${token}`);
  }
  if (audience === "client") {
    if (context?.companyId) headers.set("X-Company-ID", context.companyId);
    if (context?.isB2B) headers.set("X-Is-B2B", "true");
  }

  const response = await fetch(url, { ...requestInit, headers });
  if (response.status === 401 && audience !== "public") handleUnauthorized(audience);
  if (!response.ok) {
    let body: ApiErrorBody | undefined;
    try {
      body = (await response.json()) as ApiErrorBody;
    } catch {
      body = undefined;
    }
    throw new ApiError(response.status, body?.code ?? "HTTP_ERROR", body?.message ?? response.statusText, body);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export function jsonBody(value: unknown): string {
  return JSON.stringify(value);
}
