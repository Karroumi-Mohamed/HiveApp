import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { Window } from "happy-dom";
import { ApiError, apiRequest, configureHttpAuth } from "./http";

const originalFetch = globalThis.fetch;

describe("HTTP boundary", () => {
  beforeEach(() => {
    const browser = new Window({ url: "http://localhost:3000" });
    Object.defineProperty(globalThis, "window", { configurable: true, value: browser });
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    configureHttpAuth(
      () => null,
      () => undefined,
    );
  });

  test("client context and token are applied once at the transport boundary", async () => {
    let capturedUrl = "";
    let capturedHeaders = new Headers();
    configureHttpAuth(
      (audience) => `${audience}-token`,
      () => undefined,
    );
    globalThis.fetch = (async (input, init) => {
      capturedUrl = String(input);
      capturedHeaders = new Headers(init?.headers);
      return new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }) as typeof fetch;

    await apiRequest("/api/v1/example", {
      audience: "client",
      context: { companyId: "company-7", isB2B: true },
      query: { page: 2, ignored: undefined },
    });

    expect(capturedUrl).toBe("http://localhost:3000/api/v1/example?page=2");
    expect(capturedHeaders.get("Authorization")).toBe("Bearer client-token");
    expect(capturedHeaders.get("X-Company-ID")).toBe("company-7");
    expect(capturedHeaders.get("X-Is-B2B")).toBe("true");
  });

  test("an unauthorized response clears only the affected audience", async () => {
    const cleared: string[] = [];
    configureHttpAuth(
      () => "token",
      (audience) => cleared.push(audience),
    );
    globalThis.fetch = (async () =>
      new Response(JSON.stringify({ code: "AUTHENTICATION_REQUIRED", message: "Session expirée" }), {
        status: 401,
        statusText: "Unauthorized",
        headers: { "Content-Type": "application/json" },
      })) as unknown as typeof fetch;

    const request = apiRequest("/api/admin/me", { audience: "admin" });

    await expect(request).rejects.toBeInstanceOf(ApiError);
    expect(cleared).toEqual(["admin"]);
  });
});
