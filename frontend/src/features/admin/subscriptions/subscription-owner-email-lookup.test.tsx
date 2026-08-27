import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { Window } from "happy-dom";
import type { ReactNode } from "react";
import type { AdminSubscription } from "@/api/contracts";

const browser = new Window({ url: "http://localhost:3000/admin/subscriptions" });
for (const key of [
  "window",
  "document",
  "navigator",
  "sessionStorage",
  "HTMLElement",
  "HTMLInputElement",
  "HTMLButtonElement",
  "HTMLFormElement",
  "HTMLAnchorElement",
  "Element",
  "Node",
  "NodeFilter",
  "Event",
  "CustomEvent",
  "MouseEvent",
  "PointerEvent",
  "KeyboardEvent",
  "MutationObserver",
  "ResizeObserver",
  "getComputedStyle",
] as const) {
  const value = key === "window" ? browser : browser[key];
  Object.defineProperty(globalThis, key, { configurable: true, value });
}
Object.defineProperty(globalThis, "requestAnimationFrame", {
  configurable: true,
  value: (callback: FrameRequestCallback) => setTimeout(() => callback(Date.now()), 0),
});
Object.defineProperty(globalThis, "cancelAnimationFrame", {
  configurable: true,
  value: (id: number) => clearTimeout(id),
});

const originalFetch = globalThis.fetch;
const { cleanup, render, waitFor } = await import("@testing-library/react");
const userEvent = (await import("@testing-library/user-event")).default;
const { QueryClient, QueryClientProvider } = await import("@tanstack/react-query");
const { MemoryRouter, useLocation } = await import("react-router");
const { adminApi } = await import("@/api/admin-api");
const { adminPermissions } = await import("@/auth/permissions");
const { AdminSessionProvider } = await import("@/auth/session-provider");
const { clearSession, writeSession } = await import("@/auth/session-store");
const { OverridesEditor } = await import("./admin-subscriptions-page");
const { SubscriptionOwnerEmailLookup } = await import("./subscription-owner-email-lookup");

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function pageResponse(content: unknown[]) {
  return {
    content,
    page: 0,
    size: 10,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    first: true,
    last: true,
  };
}

function LocationProbe() {
  const location = useLocation();
  return <output aria-label="Route courante">{location.pathname}</output>;
}

function renderAdmin(element: ReactNode, permissions: string[]) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Number.POSITIVE_INFINITY }, mutations: { retry: false } },
  });
  queryClient.setQueryData(["admin", "me", "admin-token"], {
    id: "admin-1",
    email: "admin@hiveapp.test",
    emailVerified: true,
    isSuperAdmin: false,
    isActive: true,
    permissions,
  });
  const view = render(
    <QueryClientProvider client={queryClient}>
      <AdminSessionProvider>
        <MemoryRouter initialEntries={["/admin/subscriptions"]}>
          {element}
          <LocationProbe />
        </MemoryRouter>
      </AdminSessionProvider>
    </QueryClientProvider>,
  );
  return { queryClient, view };
}

function renderLookup(permissions: string[]) {
  return renderAdmin(<SubscriptionOwnerEmailLookup />, permissions);
}

const subscription: AdminSubscription = {
  id: "subscription-1",
  accountId: "account-1",
  accountName: "Acme",
  planCode: "PRO",
  planName: "Pro",
  status: "ACTIVE",
  currentPrice: "100.0000",
  currentPriceCurrencyCode: "MAD",
  currentPeriodStart: "2026-08-01T00:00:00Z",
  currentPeriodEnd: "2026-09-01T00:00:00Z",
  cancelAtPeriodEnd: false,
  customOverrides: { schemaVersion: 1, addOnCodes: [], quotaPackages: [] },
  entitlementSnapshot: null,
};

beforeEach(() => {
  cleanup();
  clearSession("admin");
  writeSession("admin", {
    accessToken: "admin-token",
    refreshToken: null,
    expiresAt: Date.now() + 600_000,
    passwordChangeRequired: false,
  });
});

afterEach(() => {
  cleanup();
  clearSession("admin");
  globalThis.fetch = originalFetch;
});

describe("subscription owner-email lookup", () => {
  test("does not mount the identity-bearing query without its narrow permission", () => {
    let requests = 0;
    globalThis.fetch = (async (_input: RequestInfo | URL) => {
      requests += 1;
      return jsonResponse(pageResponse([]));
    }) as typeof fetch;

    const { queryClient, view } = renderLookup([adminPermissions.subscriptionsSearch]);

    expect(view.queryByRole("button", { name: "Trouver par e-mail" })).toBeNull();
    expect(requests).toBe(0);
    expect(
      queryClient.getQueryCache().findAll({
        queryKey: ["admin", "commercial", "subscriptions", "account-owner-lookup"],
      }),
    ).toHaveLength(0);
  });

  test("ordinary Account search sends no owner-identity parameter", async () => {
    let requestedUrl = new URL("http://localhost/not-requested");
    globalThis.fetch = (async (input) => {
      requestedUrl = new URL(String(input));
      return jsonResponse(pageResponse([]));
    }) as typeof fetch;

    await adminApi.accounts({ query: "Acme", page: 0, size: 20, sort: "name", direction: "asc" });

    expect(requestedUrl?.pathname).toBe("/api/admin/subscriptions/accounts/search");
    expect(requestedUrl?.searchParams.get("query")).toBe("Acme");
    expect(requestedUrl?.searchParams.has("ownerEmail")).toBeFalse();
    expect(requestedUrl?.searchParams.get("sort")).toBe("name");
  });

  test("plan subscriber identity lookup keeps the email in the POST body", async () => {
    let request = { url: new URL("http://localhost/not-requested"), init: undefined as RequestInit | undefined };
    globalThis.fetch = (async (input, init) => {
      request = { url: new URL(String(input)), init };
      return jsonResponse(pageResponse([]));
    }) as typeof fetch;

    await adminApi.planSubscribersByOwnerEmail("plan-1", {
      ownerEmail: "owner@example.com",
      page: 1,
      size: 10,
    });

    expect(request.url.pathname).toBe("/api/admin/plans/plan-1/subscribers/by-owner-email");
    expect(request.url.searchParams.has("ownerEmail")).toBeFalse();
    expect(request.url.searchParams.get("page")).toBe("1");
    expect(request.init?.method).toBe("POST");
    expect(JSON.parse(String(request.init?.body))).toEqual({ ownerEmail: "owner@example.com" });
  });

  test("submits the exact protected lookup and opens the returned Account", async () => {
    const requests: Array<{ url: URL; init?: RequestInit }> = [];
    globalThis.fetch = (async (input, init) => {
      const url = new URL(String(input));
      requests.push({ url, init });
      if (url.pathname === "/api/admin/subscriptions/accounts/by-owner-email") {
        return jsonResponse(
          pageResponse([
            {
              ownerEmail: "owner@example.com",
              account: {
                id: "account-1",
                name: "Acme",
                slug: "acme",
                active: true,
                createdAt: "2026-08-27T10:00:00Z",
                latestSubscription: null,
              },
            },
          ]),
        );
      }
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;

    const { view } = renderLookup([
      adminPermissions.subscriptionsLookupAccountOwnerEmail,
      adminPermissions.subscriptionsRead,
    ]);
    const user = userEvent.setup({ document: view.container.ownerDocument });
    await user.click(view.getByRole("button", { name: "Trouver par e-mail" }));
    await user.type(view.getByLabelText("E-mail exact du propriétaire"), "  Owner@Example.COM  ");
    await user.click(view.getByRole("button", { name: "Rechercher" }));

    await view.findByText("Acme");
    const lookupRequest = requests.find(({ url }) => url.pathname.endsWith("/accounts/by-owner-email"));
    expect(lookupRequest?.url.searchParams.has("ownerEmail")).toBeFalse();
    expect(lookupRequest?.url.searchParams.get("sort")).toBe("name");
    expect(lookupRequest?.init?.method).toBe("POST");
    expect(JSON.parse(String(lookupRequest?.init?.body))).toEqual({ ownerEmail: "owner@example.com" });
    expect(browser.location.href).not.toContain("owner@example.com");

    await user.click(view.getByRole("link", { name: "Ouvrir" }));
    await waitFor(() =>
      expect(view.getByLabelText("Route courante").textContent).toBe("/admin/subscriptions/account-1"),
    );
  });
});

describe("subscription override dialog", () => {
  test("renders one trigger child and opens without violating the Radix asChild contract", async () => {
    globalThis.fetch = (async (input: RequestInfo | URL) => {
      throw new Error(`Unexpected request: ${String(input)}`);
    }) as unknown as typeof fetch;
    const { view } = renderAdmin(<OverridesEditor subscription={subscription} />, [
      adminPermissions.subscriptionsOverrides,
    ]);
    const trigger = view.getByRole("button", { name: "Gérer les exceptions" });
    expect(view.getAllByRole("button", { name: "Gérer les exceptions" })).toHaveLength(1);

    const user = userEvent.setup({ document: view.container.ownerDocument });
    await user.click(trigger);
    expect(await view.findByRole("heading", { name: "Exceptions de l’abonnement" })).toBeTruthy();
  });
});
