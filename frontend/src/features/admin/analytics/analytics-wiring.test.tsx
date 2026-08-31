import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { Window } from "happy-dom";

const browser = new Window({ url: "http://localhost:3000/admin/analytics" });
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
  "DocumentFragment",
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
const { cleanup, render } = await import("@testing-library/react");
const { QueryClient, QueryClientProvider } = await import("@tanstack/react-query");
const { createMemoryRouter, RouterProvider } = await import("react-router");
const { adminPermissions } = await import("@/auth/permissions");
const { AdminSessionProvider } = await import("@/auth/session-provider");
const { clearSession, writeSession } = await import("@/auth/session-store");
const { AdminAnalyticsPage } = await import("./admin-analytics-page");

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const metadata = {
  generatedAt: "2026-08-31T12:00:00Z",
  completeThrough: "2026-08-31T11:59:59Z",
  from: "2026-08-01T12:00:00Z",
  until: "2026-08-31T12:00:00Z",
  timezone: "UTC",
  interval: "DAY",
  currentBucketProvisional: true,
};

function renderPage(entry: string, permissions: string[]) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  queryClient.setQueryData(["admin", "me", "admin-token"], {
    id: "admin-1",
    email: "admin@hiveapp.test",
    emailVerified: true,
    isSuperAdmin: false,
    isActive: true,
    permissions,
  });
  const router = createMemoryRouter([{ path: "/admin/analytics", element: <AdminAnalyticsPage /> }], {
    initialEntries: [entry],
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <AdminSessionProvider>
        <RouterProvider router={router} />
      </AdminSessionProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() =>
  writeSession("admin", {
    accessToken: "admin-token",
    refreshToken: "refresh-token",
    expiresAt: Date.now() + 60_000,
    passwordChangeRequired: false,
  }),
);
afterEach(() => {
  cleanup();
  clearSession("admin");
  globalThis.fetch = originalFetch;
});

describe("commercial analytics least-privilege wiring", () => {
  test("summary-only access requests no detailed or identity-bearing surface", async () => {
    const requests: string[] = [];
    globalThis.fetch = (async (input) => {
      const url = String(input);
      requests.push(url);
      if (url.includes("/analytics/overview")) {
        return response({
          metadata,
          availability: {
            financialSeries: false,
            subscriptionSeries: false,
            offerSeries: false,
            operations: false,
          },
          financialTotals: [],
          configuredRecurringValues: [],
          currentSubscriptions: { ACTIVE: 3 },
          operationsNeedingAttention: 0,
          graceDeadlinesWithinSevenDays: 0,
          offerOutcomes: {},
          finality: { pendingPayments: 0, pendingRefunds: 0, pendingProviderCommands: 0 },
        });
      }
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;

    const view = renderPage("/admin/analytics", [adminPermissions.analyticsReadSummary]);
    expect(await view.findByText("Flux financiers prouvés")).toBeTruthy();
    expect(await view.findByText("3")).toBeTruthy();
    expect(requests.filter((url) => url.includes("/analytics/overview"))).toHaveLength(1);
    expect(requests.some((url) => url.includes("financial-series") || url.includes("attention"))).toBe(false);
  });

  test("subscription access loads series and current holdings, but no financial data", async () => {
    const requests: string[] = [];
    globalThis.fetch = (async (input) => {
      const url = String(input);
      requests.push(url);
      if (url.includes("/analytics/subscription-series")) {
        return response({ metadata, points: [], productMovements: [] });
      }
      if (url.includes("/analytics/product-holdings")) return response([]);
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;

    const view = renderPage("/admin/analytics?view=subscriptions", [adminPermissions.analyticsReadSubscriptionSeries]);
    expect(await view.findByText("Détention actuelle")).toBeTruthy();
    expect(await view.findByText("Aucun mouvement d’abonnement sur cette période")).toBeTruthy();
    expect(view.queryByText("Devise")).toBeNull();
    expect(view.queryByText("Cycle")).toBeNull();
    expect(requests.some((url) => url.includes("subscription-series"))).toBe(true);
    expect(requests.some((url) => url.includes("product-holdings"))).toBe(true);
    expect(requests.some((url) => url.includes("financial-series") || url.includes("overview"))).toBe(false);
  });

  test("a failed request remains visibly retryable instead of becoming a zero", async () => {
    globalThis.fetch = (async (input) => {
      const url = String(input);
      if (url.includes("/analytics/overview")) return response({ message: "failure" }, 500);
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;

    const view = renderPage("/admin/analytics", [adminPermissions.analyticsReadSummary]);
    expect(await view.findByText("Impossible de charger les données")).toBeTruthy();
    expect(view.getByRole("button", { name: "Réessayer" })).toBeTruthy();
  });
});
