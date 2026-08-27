import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { Window } from "happy-dom";

const browser = new Window({ url: "http://localhost:3000/admin/commercial-policies" });
for (const key of [
  "window",
  "document",
  "navigator",
  "sessionStorage",
  "HTMLElement",
  "HTMLInputElement",
  "HTMLFormElement",
  "HTMLButtonElement",
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
const { AdminCommercialPolicyDetailPage } = await import("./admin-commercial-policy-detail-page");

const policyId = "5ce8c602-2da0-4a24-a07c-df10d04a5c50";
const activationId = "f627cf50-b295-4ee3-a52a-eb04cde766d3";

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

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

describe("commercial policy detail permission wiring", () => {
  test("an activation-audience reader can use a known id without leaking other policy surfaces", async () => {
    const requests: string[] = [];
    globalThis.fetch = (async (input) => {
      const url = String(input);
      if (url.endsWith("/api/admin/me")) {
        return response({
          id: "admin-1",
          email: "admin@hiveapp.test",
          emailVerified: true,
          isSuperAdmin: false,
          isActive: true,
          permissions: [adminPermissions.commercialPoliciesReadActivationAccounts],
        });
      }
      requests.push(url);
      if (url.includes(`/api/admin/commercial-policies/${policyId}/activations/${activationId}/accounts`)) {
        return response({
          policyId,
          activationId,
          activationNumber: 3,
          immutableAccountCount: 1,
          accounts: {
            content: [{ id: "f0531700-650b-4036-a26d-3e449c1c2070", name: "Acme", slug: "acme", active: true }],
            page: 0,
            size: 20,
            totalElements: 1,
            totalPages: 1,
            first: true,
            last: true,
          },
        });
      }
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    queryClient.setQueryData(["admin", "me", "admin-token"], {
      id: "admin-1",
      email: "admin@hiveapp.test",
      emailVerified: true,
      isSuperAdmin: false,
      isActive: true,
      permissions: [adminPermissions.commercialPoliciesReadActivationAccounts],
    });
    const router = createMemoryRouter(
      [
        {
          path: "/admin/commercial-policies/:policyId/:tab",
          element: <AdminCommercialPolicyDetailPage />,
        },
      ],
      {
        initialEntries: [`/admin/commercial-policies/${policyId}/activations?activation=../../owner`],
      },
    );
    const view = render(
      <QueryClientProvider client={queryClient}>
        <AdminSessionProvider>
          <RouterProvider router={router} />
        </AdminSessionProvider>
      </QueryClientProvider>,
    );
    const user = userEvent.setup({ document: view.container.ownerDocument });

    await waitFor(() => expect(requests).toEqual([]));
    const input = await view.findByLabelText("Activation à examiner");
    await user.type(input, activationId);
    await user.click(view.getByRole("button", { name: "Examiner" }));

    expect(await view.findByText("Audience figée · activation #3")).toBeTruthy();
    expect(view.getByText("Acme")).toBeTruthy();
    expect(requests).toHaveLength(1);
    expect(requests[0]).toContain(`/activations/${activationId}/accounts`);
    expect(requests[0]).not.toMatch(/\/commercial-policies\?.*|\/commercial-policies\/[^/]+$/);
  });
});
