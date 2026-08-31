import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { Window } from "happy-dom";

const browser = new Window({ url: "http://localhost:3000/admin/activities" });
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
const { cleanup, render, within } = await import("@testing-library/react");
const userEvent = (await import("@testing-library/user-event")).default;
const { QueryClient, QueryClientProvider } = await import("@tanstack/react-query");
const { createMemoryRouter, RouterProvider } = await import("react-router");
const { adminPermissions } = await import("@/auth/permissions");
const { AdminSessionProvider } = await import("@/auth/session-provider");
const { clearSession, writeSession } = await import("@/auth/session-store");
const { TooltipProvider } = await import("@/components/ui/tooltip");
const { AdminActivitiesPage } = await import("./admin-activities-page");
const { AdminCommunicationsPage } = await import("./admin-communications-page");
const { AdminObservabilityPage } = await import("./admin-observability-page");

const activity = {
  id: "a0000000-0000-0000-0000-000000000001",
  occurredAt: "2026-08-31T12:00:00Z",
  actorSurface: "PLATFORM_ADMIN",
  actorUserId: "b0000000-0000-0000-0000-000000000001",
  actorIdentity: null,
  clientAccountId: null,
  targetAccountId: null,
  accountIdentity: null,
  targetCompanyId: null,
  collaborationId: null,
  action: "platform.plans.update",
  resourceType: "PLAN_ADMIN",
  resourceId: "FLEX",
  outcome: "SUCCEEDED",
  requestMethod: "PATCH",
  requestPath: "/api/admin/plans/1",
  requestId: "request-1",
  payloadAvailable: true,
};

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function renderPage(path: string, element: React.ReactNode, permissions: string[]) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  queryClient.setQueryData(["admin", "me", "admin-token"], {
    id: "admin-1",
    email: "admin@hiveapp.test",
    emailVerified: true,
    isSuperAdmin: false,
    isActive: true,
    permissions,
  });
  const router = createMemoryRouter([{ path, element }], { initialEntries: [path] });
  return render(
    <QueryClientProvider client={queryClient}>
      <AdminSessionProvider>
        <TooltipProvider>
          <RouterProvider router={router} />
        </TooltipProvider>
      </AdminSessionProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() =>
  writeSession("admin", {
    accessToken: "admin-token",
    refreshToken: "refresh-token",
    expiresAt: Date.now() + 5 * 60_000,
    passwordChangeRequired: false,
  }),
);
afterEach(() => {
  cleanup();
  clearSession("admin");
  globalThis.fetch = originalFetch;
});

describe("operations least-privilege wiring", () => {
  test("activity read access never fetches payload evidence", async () => {
    const requests: string[] = [];
    globalThis.fetch = (async (input) => {
      const url = String(input);
      requests.push(url);
      if (url.endsWith(`/activities/${activity.id}`)) return response(activity);
      if (url.includes("/activities?"))
        return response({
          content: [activity],
          page: 0,
          size: 25,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        });
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;
    const view = renderPage("/admin/activities", <AdminActivitiesPage />, [adminPermissions.activitiesRead]);
    const [examineActivity] = await view.findAllByRole("button", { name: "Examiner" });
    if (!examineActivity) throw new Error("Activity action is missing");
    await userEvent.click(examineActivity);
    expect(await view.findByText("Corrélation")).toBeTruthy();
    expect(requests.some((url) => url.includes("/payload"))).toBe(false);
  });

  test("communication read access never fetches recipient or failure evidence", async () => {
    const requests: string[] = [];
    const delivery = {
      id: "c0000000-0000-0000-0000-000000000001",
      accountId: null,
      recipientUserId: "d0000000-0000-0000-0000-000000000001",
      recipientEmail: null,
      purpose: "ACTIVATION",
      status: "FAILED",
      createdAt: "2026-08-31T12:00:00Z",
      attemptedAt: "2026-08-31T12:00:01Z",
      deliveredAt: null,
      failureCode: null,
      recipientIdentityVisible: false,
      failureEvidenceVisible: false,
    };
    globalThis.fetch = (async (input) => {
      const url = String(input);
      requests.push(url);
      if (url.endsWith("/communications/summary"))
        return response({ total: 1, byStatus: { FAILED: 1 }, byPurpose: { ACTIVATION: 1 } });
      if (url.endsWith(`/communications/${delivery.id}`)) return response(delivery);
      if (url.includes("/communications?"))
        return response({
          content: [delivery],
          page: 0,
          size: 25,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        });
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;
    const view = renderPage("/admin/communications", <AdminCommunicationsPage />, [
      adminPermissions.communicationsRead,
    ]);
    const [examineCommunication] = await view.findAllByRole("button", { name: "Examiner" });
    if (!examineCommunication) throw new Error("Communication action is missing");
    await userEvent.click(examineCommunication);
    const dialog = await view.findByRole("dialog");
    expect(within(dialog).getByText("Identité protégée")).toBeTruthy();
    expect(requests.some((url) => url.includes("/recipient") || url.includes("failure-evidence"))).toBe(false);
  });

  test("health-only observability access requests no backlog or log configuration", async () => {
    const requests: string[] = [];
    globalThis.fetch = (async (input) => {
      const url = String(input);
      requests.push(url);
      if (url.endsWith("/observability/health"))
        return response({
          generatedAt: "2026-08-31T12:00:00Z",
          components: [{ key: "database", label: "Database", state: "UP", guidance: null }],
        });
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;
    const view = renderPage("/admin/observability", <AdminObservabilityPage />, [
      adminPermissions.observabilityReadHealth,
    ]);
    expect(await view.findAllByText("Opérationnel")).toHaveLength(2);
    expect(requests.some((url) => url.includes("backlogs") || url.includes("log-access"))).toBe(false);
  });
});
