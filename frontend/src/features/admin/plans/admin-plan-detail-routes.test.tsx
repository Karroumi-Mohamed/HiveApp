import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { Window } from "happy-dom";

const browser = new Window({ url: "http://localhost:3000/admin/plans" });
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
  Object.defineProperty(globalThis, key, { configurable: true, value: key === "window" ? browser : browser[key] });
}

const originalFetch = globalThis.fetch;
const { cleanup, render, waitFor } = await import("@testing-library/react");
const userEvent = (await import("@testing-library/user-event")).default;
const { QueryClient, QueryClientProvider } = await import("@tanstack/react-query");
const { createMemoryRouter, RouterProvider } = await import("react-router");
const { adminPermissions } = await import("@/auth/permissions");
const { AdminSessionProvider } = await import("@/auth/session-provider");
const { clearSession, writeSession } = await import("@/auth/session-store");
const { TooltipProvider } = await import("@/components/ui/tooltip");
const { adminPlanDetailRoutes } = await import("./admin-plan-detail-routes");

const planId = "7f31acf0-5101-4341-ac59-7ac88d29ce45";
const base = `/admin/plans/${planId}`;
const plan = {
  id: planId,
  code: "ENTERPRISE",
  name: "Enterprise",
  description: null,
  price: "100",
  currencyCode: "MAD",
  billingCycle: "MONTHLY",
  status: "ACTIVE",
  lineageId: planId,
  revisionNumber: 1,
  sourcePlanId: null,
  creationReason: "CREATED",
  extensionPolicy: "OPEN_COMPATIBLE",
  salesVisibility: "PUBLIC",
  version: 0,
  featureCount: 0,
  quotaConfiguredFeatureCount: 0,
  activeSubscriberCount: 0,
  trialingSubscriberCount: 0,
  currentSubscriberCount: 0,
  historicalSubscriberCount: 0,
  affectedSubscriptionCount: 0,
  configuredRecurringPriceTotal: "0",
  configuredRecurringPriceCurrencyCode: "MAD",
  warnings: [],
};
const queries: InstanceType<typeof QueryClient>[] = [];
const requests: string[] = [];

function response(body: unknown) {
  return new Response(JSON.stringify(body), { headers: { "Content-Type": "application/json" } });
}

function renderPage(path: string, permissions: string[], isSuperAdmin = false) {
  const profile = { id: "admin-1", email: "admin@hiveapp.test", isActive: true, isSuperAdmin, permissions };
  globalThis.fetch = (async (input) => {
    const url = new URL(String(input), "http://localhost:8080");
    if (url.pathname === "/api/admin/me") return response(profile);
    requests.push(url.pathname);
    if (url.pathname === `/api/admin/plans/${planId}`) return response(plan);
    if (url.pathname.endsWith("/operations")) return response({ availableActions: [] });
    if (url.pathname.endsWith("/features")) return response([]);
    if (url.pathname.endsWith("/subscribers") || url.pathname === "/api/admin/product-prices") {
      return response({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true });
    }
    throw new Error(`Unexpected request: ${url.pathname}`);
  }) as typeof fetch;
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } });
  queries.push(queryClient);
  queryClient.setQueryData(["admin", "me", "admin-token"], profile);
  const router = createMemoryRouter([{ path: "/admin", children: adminPlanDetailRoutes }], { initialEntries: [path] });
  const view = render(
    <QueryClientProvider client={queryClient}>
      <AdminSessionProvider>
        <TooltipProvider>
          <RouterProvider router={router} />
        </TooltipProvider>
      </AdminSessionProvider>
    </QueryClientProvider>,
  );
  return { ...view, router };
}

beforeEach(() => {
  cleanup();
  requests.length = 0;
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
  for (const client of queries.splice(0)) client.clear();
  globalThis.fetch = originalFetch;
});

describe("plan detail route rendering and authorization", () => {
  test("SuperAdmin opening the base URL sees the overview, not a false permission denial", async () => {
    const view = renderPage(base, [adminPermissions.plansReadDetail], true);
    expect(await view.findByRole("heading", { name: "Configuration commerciale" })).toBeTruthy();
    expect(view.queryByText("Accès indisponible")).toBeNull();
  });

  test("an ordinary detail reader can also open the overview without broader permissions", async () => {
    const view = renderPage(base, [adminPermissions.plansReadDetail]);
    expect(await view.findByRole("heading", { name: "Utilisation actuelle" })).toBeTruthy();
    expect(requests).toEqual([`/api/admin/plans/${planId}`]);
  });

  test.each([
    ["features", adminPermissions.plansListFeatures, "Contenu du forfait", "/features"],
    ["schema", adminPermissions.plansListFeatures, "Aucune fonctionnalité à représenter", "/features"],
    ["subscribers", adminPermissions.plansListSubscribers, "Aucun abonné", "/subscribers"],
  ])("the static %s URL renders its own panel", async (tab, permission, text, endpoint) => {
    const view = renderPage(`${base}/${tab}`, [adminPermissions.plansReadDetail, permission]);
    expect(await view.findByText(new RegExp(text))).toBeTruthy();
    expect(view.queryByText("Accès indisponible")).toBeNull();
    expect(requests).toContain(`/api/admin/plans/${planId}${endpoint}`);
  });

  test("tab clicks switch content and returning to Synthèse restores the overview", async () => {
    const view = renderPage(base, [adminPermissions.plansReadDetail, adminPermissions.plansListFeatures]);
    const user = userEvent.setup({ document: view.container.ownerDocument });
    await view.findByRole("heading", { name: "Configuration commerciale" });
    await user.click(view.getByRole("link", { name: /Fonctionnalités/ }));
    expect(await view.findByRole("heading", { name: "Contenu du forfait" })).toBeTruthy();
    await user.click(view.getByRole("link", { name: "Synthèse" }));
    expect(await view.findByRole("heading", { name: "Configuration commerciale" })).toBeTruthy();
    expect(view.router.state.location.pathname).toBe(base);
  });

  test("the dynamic prices URL still opens its authorized panel", async () => {
    const view = renderPage(`${base}/prices`, [adminPermissions.plansReadDetail, adminPermissions.priceBooksList]);
    expect(await view.findByRole("heading", { name: "Aucun tarif" })).toBeTruthy();
    expect(requests).toContain("/api/admin/product-prices");
    expect(view.queryByText("Accès indisponible")).toBeNull();
  });

  test.each(["features", "schema", "subscribers", "prices"])(
    "an unauthorized %s deep link stays denied without fetching its data",
    async (tab) => {
      const view = renderPage(`${base}/${tab}`, [adminPermissions.plansReadDetail]);
      expect(await view.findByText("Accès indisponible")).toBeTruthy();
      expect(requests.every((path) => path === `/api/admin/plans/${planId}`)).toBe(true);
    },
  );

  test("without plan-detail access the route never fetches the plan", async () => {
    const view = renderPage(base, []);
    expect(await view.findByText("Accès indisponible")).toBeTruthy();
    expect(requests).toHaveLength(0);
  });

  test("an unknown tab does not silently expose overview content", async () => {
    const view = renderPage(`${base}/unknown`, [adminPermissions.plansReadDetail]);
    await waitFor(() => expect(view.queryByText("Accès indisponible")).not.toBeNull());
    expect(view.queryByRole("heading", { name: "Configuration commerciale" })).toBeNull();
  });
});
