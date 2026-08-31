import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { Window } from "happy-dom";

const browser = new Window({ url: "http://localhost:3000/admin/subscription-jobs/job-1" });
for (const key of [
  "window",
  "document",
  "navigator",
  "sessionStorage",
  "HTMLElement",
  "HTMLButtonElement",
  "HTMLFormElement",
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
const { cleanup, render, waitFor } = await import("@testing-library/react");
const userEvent = (await import("@testing-library/user-event")).default;
const { QueryClient, QueryClientProvider } = await import("@tanstack/react-query");
const { createMemoryRouter, RouterProvider } = await import("react-router");
const { adminPermissions } = await import("@/auth/permissions");
const { AdminSessionProvider } = await import("@/auth/session-provider");
const { clearSession, writeSession } = await import("@/auth/session-store");
const { AdminSubscriptionChangeJobDetailPage } = await import("./admin-subscription-change-job-detail-page");

const jobId = "873cd580-f082-4fa3-a126-1105fb654d51";
const itemId = "358a4ee2-c9f5-4293-8c98-d7b83a3134e9";

function response(body: unknown) {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}

function resultPage() {
  return {
    content: [
      {
        id: itemId,
        status: "APPLIED",
        assessment: {
          subscriptionId: "25a4e198-7f85-4da6-a6fa-9a3f39fd69ea",
          expectedSubscriptionVersion: 2,
          currentPlanCode: "STARTER",
          targetPlanCode: "BUSINESS",
          currentPrice: "9.9900",
          targetPrice: "19.9900",
          currencyCode: "USD",
          timing: "IMMEDIATE",
          effectiveAt: "2026-08-31T10:00:00Z",
          conflicts: [],
        },
        subscriptionOperationId: "bdedb602-ddb1-47cf-be65-d6a9ef59b66b",
        outcomeCode: "APPLIED",
        attempts: 1,
        lastAttemptAt: "2026-08-31T10:00:00Z",
        completedAt: "2026-08-31T10:00:00Z",
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
  };
}

function renderPage(permissions: string[]) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  queryClient.setQueryData(["admin", "me", "admin-token"], {
    id: "admin-1",
    email: "admin@hiveapp.test",
    emailVerified: true,
    isSuperAdmin: false,
    isActive: true,
    permissions,
  });
  const router = createMemoryRouter(
    [{ path: "/admin/subscription-jobs/:jobId", element: <AdminSubscriptionChangeJobDetailPage /> }],
    { initialEntries: [`/admin/subscription-jobs/${jobId}`] },
  );
  return render(
    <QueryClientProvider client={queryClient}>
      <AdminSessionProvider>
        <RouterProvider router={router} />
      </AdminSessionProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  writeSession("admin", {
    accessToken: "admin-token",
    refreshToken: "refresh-token",
    expiresAt: Date.now() + 60_000,
    passwordChangeRequired: false,
  });
});

afterEach(() => {
  cleanup();
  clearSession("admin");
  globalThis.fetch = originalFetch;
});

describe("subscription change job identity boundary", () => {
  test("result access alone never requests or renders Account identities", async () => {
    let identityRequests = 0;
    globalThis.fetch = (async (input) => {
      const url = String(input);
      if (url.includes("/results/identities")) {
        identityRequests += 1;
        return response([]);
      }
      if (url.includes(`/subscription-change-jobs/${jobId}/results`)) return response(resultPage());
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;

    const view = renderPage([adminPermissions.subscriptionsReadChangeJobResults]);
    expect(await view.findByText("Compte masqué")).toBeTruthy();
    expect(view.queryByRole("button", { name: "Afficher les comptes" })).toBeNull();
    expect(identityRequests).toBe(0);
  });

  test("the independent identity permission still requires an explicit reveal", async () => {
    let identityRequests = 0;
    globalThis.fetch = (async (input) => {
      const url = String(input);
      if (url.includes("/results/identities")) {
        identityRequests += 1;
        return response([{ itemId, accountId: "account-1", accountName: "Atelier Atlas" }]);
      }
      if (url.includes(`/subscription-change-jobs/${jobId}/results`)) return response(resultPage());
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;

    const view = renderPage([
      adminPermissions.subscriptionsReadChangeJobResults,
      adminPermissions.subscriptionsReadChangeJobResultIdentities,
    ]);
    await view.findByText("Compte masqué");
    expect(identityRequests).toBe(0);
    await userEvent.click(view.getByRole("button", { name: "Afficher les comptes" }));
    await waitFor(() => expect(identityRequests).toBe(1));
    expect(await view.findByText("Atelier Atlas")).toBeTruthy();
  });
});
