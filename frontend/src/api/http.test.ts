import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { runInNewContext } from "node:vm";
import { Window } from "happy-dom";
import { ApiError, apiRequest, configureHttpAuth } from "./http";

const originalFetch = globalThis.fetch;

describe("HTTP boundary", () => {
  test("a browser build uses the configured API origin without a process global", async () => {
    const build = await Bun.build({
      entrypoints: [`${import.meta.dir}/http.ts`],
      target: "browser",
      format: "cjs",
      define: { "process.env.BUN_PUBLIC_API_URL": JSON.stringify("http://localhost:8081/") },
    });
    expect(build.success).toBeTrue();
    const output = build.outputs[0];
    if (!output) throw new Error("HTTP browser bundle was not generated");
    let requested = "";
    const module = { exports: {} as { apiRequest: typeof apiRequest } };
    runInNewContext(await output.text(), {
      module,
      exports: module.exports,
      window: { location: { origin: "http://localhost:5173", port: "5173" } },
      URL,
      Headers,
      FormData,
      fetch: async (url: URL) => {
        requested = String(url);
        return new Response("{}", { status: 200 });
      },
    });
    await module.exports.apiRequest("/api/admin/me");
    expect(requested).toBe("http://localhost:8081/api/admin/me");
  });

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
