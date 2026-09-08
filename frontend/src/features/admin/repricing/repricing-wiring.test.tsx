import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { Window } from "happy-dom";

const browser = new Window({ url: "http://localhost:3000/admin/repricing/new" });
for (const key of [
  "window",
  "document",
  "navigator",
  "sessionStorage",
  "HTMLElement",
  "HTMLInputElement",
  "HTMLFormElement",
  "HTMLTextAreaElement",
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
const { cleanup, render, waitFor, fireEvent } = await import("@testing-library/react");
const userEvent = (await import("@testing-library/user-event")).default;
const { QueryClient, QueryClientProvider } = await import("@tanstack/react-query");
const { createMemoryRouter, RouterProvider } = await import("react-router");
const { adminPermissions } = await import("@/auth/permissions");
const { AdminSessionProvider } = await import("@/auth/session-provider");
const { clearSession, writeSession } = await import("@/auth/session-store");
const { TooltipProvider } = await import("@/components/ui/tooltip");
const { AdminRepricingCreatePage } = await import("./admin-repricing-create-page");
const { AdminRepricingDetailPage } = await import("./admin-repricing-pages");

const oldPrice = {
  id: "old",
  productType: "PLAN",
  productId: "pro",
  productName: "Pro",
  amount: "100",
  currencyCode: "MAD",
  billingCycle: "MONTHLY",
  status: "INACTIVE",
  revisionNumber: 1,
};
const newPrice = { ...oldPrice, id: "new", amount: "200", status: "ACTIVE", revisionNumber: 2 };
const summary = {
  id: "job",
  status: "PREVIEWED",
  productName: "Pro",
  currencyCode: "MAD",
  billingCycle: "MONTHLY",
  sourcePrice: "100",
  targetPrice: "200",
  targetCount: 1,
  readyCount: 1,
  pendingCount: 0,
  appliedCount: 0,
  conflictCount: 0,
  cancelledCount: 0,
  awaitingPaymentCount: 0,
  createdAt: "2026-09-08T10:00:00Z",
};
const item = {
  id: "item",
  status: "READY",
  blocker: null,
  quantity: 1,
  oldUnitPrice: "100",
  newUnitPrice: "200",
  oldTotal: "120",
  newTotal: "220",
  currencyCode: "MAD",
  billingCycle: "MONTHLY",
  effectiveAt: "2026-10-08T10:00:00Z",
  operationId: null,
  delivery: "NOT_REQUESTED",
  emailAttempts: 0,
};
const required = [
  adminPermissions.repricingPreview,
  adminPermissions.repricingResults,
  adminPermissions.repricingConfirm,
  adminPermissions.priceBooksRead,
  adminPermissions.priceBooksList,
];
const requests: { path: string; body: Record<string, unknown> | null }[] = [];
const clients: InstanceType<typeof QueryClient>[] = [];
let reviewFails = false;
let expiresIn = 60_000;
function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}
function page(content: unknown[]) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, first: true, last: true };
}
function mount(permissions = required, path = "/admin/repricing/new?from=old") {
  const profile = { id: "admin", email: "admin@test", isActive: true, isSuperAdmin: false, permissions };
  globalThis.fetch = (async (input, init) => {
    const url = new URL(String(input), "http://localhost:8080");
    if (url.pathname === "/api/admin/me") return response(profile);
    requests.push({ path: url.pathname, body: init?.body ? JSON.parse(String(init.body)) : null });
    if (url.pathname === "/api/admin/product-prices/old") return response(oldPrice);
    if (url.pathname === "/api/admin/product-prices")
      return response(page(url.searchParams.has("ownerId") ? [newPrice] : [oldPrice]));
    if (url.pathname.endsWith("/preview"))
      return reviewFails
        ? response({ message: "Review failed", code: "TEST_ERROR" }, 500)
        : response({
            summary,
            sample: [item],
            previewToken: "signed-review",
            expiresAt: new Date(Date.now() + expiresIn).toISOString(),
          });
    if (url.pathname.endsWith("/results")) return response(page([item]));
    if (url.pathname.endsWith("/identities"))
      return response([{ itemId: "item", accountId: "account", accountName: "Acme" }]);
    if (url.pathname.endsWith("/confirm"))
      return response({
        summary: { ...summary, status: "CONFIRMED" },
        request: {},
        confirmedAt: new Date().toISOString(),
      });
    throw new Error(`Unexpected request ${url.pathname}`);
  }) as typeof fetch;
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity }, mutations: { retry: false } },
  });
  clients.push(client);
  client.setQueryData(["admin", "me", "admin-token"], profile);
  const router = createMemoryRouter(
    [
      { path: "/admin/repricing/new", element: <AdminRepricingCreatePage /> },
      { path: "/admin/repricing/job", element: <h1>Changement confirmé</h1> },
      { path: "/admin/repricing/:repricingId/results-only", element: <AdminRepricingDetailPage /> },
    ],
    { initialEntries: [path] },
  );
  return render(
    <QueryClientProvider client={client}>
      <AdminSessionProvider>
        <TooltipProvider>
          <RouterProvider router={router} />
        </TooltipProvider>
      </AdminSessionProvider>
    </QueryClientProvider>,
  );
}
beforeEach(() => {
  cleanup();
  requests.length = 0;
  reviewFails = false;
  expiresIn = 60_000;
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
  for (const client of clients.splice(0)) client.clear();
  globalThis.fetch = originalFetch;
});
async function reachReview(view: ReturnType<typeof mount>) {
  const user = userEvent.setup({ document: browser.document as unknown as Document });
  await user.click(await view.findByRole("radio", { name: /200.*tarif R2/ }));
  await user.click(view.getByRole("button", { name: "Continuer" }));
  await user.click(view.getByRole("button", { name: "Continuer" }));
  await user.type(view.getByLabelText("Motif interne"), "Révision annuelle");
  await user.click(view.getByRole("button", { name: "Continuer" }));
  return user;
}
describe("mounted repricing flow", () => {
  test("no confirmation without review; sends only reviewed tariff identities then navigates", async () => {
    const view = mount();
    const user = await reachReview(view);
    expect((view.getByRole("button", { name: "Confirmer et notifier" }) as HTMLButtonElement).disabled).toBeTrue();
    fireEvent.submit(view.getByRole("button", { name: "Confirmer et notifier" }).closest("form") as HTMLFormElement);
    expect(requests.some((request) => request.path.endsWith("/confirm"))).toBeFalse();
    await user.click(view.getByRole("button", { name: "Calculer les changements" }));
    await waitFor(() =>
      expect((view.getByRole("button", { name: "Confirmer et notifier" }) as HTMLButtonElement).disabled).toBeFalse(),
    );
    expect(requests.some((request) => request.path.endsWith("/identities"))).toBeFalse();
    expect(requests.find((request) => request.path.endsWith("/preview"))?.body?.sourcePriceId).toBe("old");
    expect(requests.find((request) => request.path.endsWith("/preview"))?.body?.targetPriceId).toBe("new");
    await user.click(view.getByRole("button", { name: "Confirmer et notifier" }));
    expect(await view.findByRole("heading", { name: "Changement confirmé" })).toBeTruthy();
    expect(requests.find((request) => request.path.endsWith("/confirm"))?.body).toEqual({
      previewToken: "signed-review",
    });
  });
  test("failed preview never unlocks confirmation", async () => {
    reviewFails = true;
    const view = mount();
    const user = await reachReview(view);
    await user.click(view.getByRole("button", { name: "Calculer les changements" }));
    expect(await view.findByRole("alert")).toBeTruthy();
    expect((view.getByRole("button", { name: "Confirmer et notifier" }) as HTMLButtonElement).disabled).toBeTrue();
  });
  test("an expiring preview actively disables the button without another click", async () => {
    expiresIn = 150;
    const view = mount();
    const user = await reachReview(view);
    await user.click(view.getByRole("button", { name: "Calculer les changements" }));
    await waitFor(() => expect(view.getByText(/Révision expirée ou aucun compte admissible/)).toBeTruthy());
    expect((view.getByRole("button", { name: "Confirmer et notifier" }) as HTMLButtonElement).disabled).toBeTrue();
  });
  test("results-only authority renders results without querying detail or identities", async () => {
    const view = mount([adminPermissions.repricingResults], "/admin/repricing/job/results-only");
    expect(await view.findByRole("heading", { name: "Résultats par compte" })).toBeTruthy();
    await waitFor(() => expect(view.getByText("Identité protégée")).toBeTruthy());
    expect(requests.map((request) => request.path)).toEqual(["/api/admin/subscription-repricing/job/results"]);
  });
});
