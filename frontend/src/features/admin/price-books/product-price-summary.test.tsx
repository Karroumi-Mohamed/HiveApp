import { afterEach, beforeEach, expect, test } from "bun:test";
import { Window } from "happy-dom";

const browser = new Window({ url: "http://localhost:3000/admin/plans/plan-1" });
for (const key of [
  "window",
  "document",
  "navigator",
  "sessionStorage",
  "HTMLElement",
  "Element",
  "Node",
  "MutationObserver",
  "getComputedStyle",
] as const) {
  Object.defineProperty(globalThis, key, { configurable: true, value: key === "window" ? browser : browser[key] });
}
const { render, cleanup } = await import("@testing-library/react");
const { QueryClient, QueryClientProvider } = await import("@tanstack/react-query");
const { MemoryRouter } = await import("react-router");
const { AdminSessionProvider } = await import("@/auth/session-provider");
const { clearSession, writeSession } = await import("@/auth/session-store");
const { adminPermissions } = await import("@/auth/permissions");
const { ProductPriceSummary } = await import("./product-price-summary");
const originalFetch = globalThis.fetch;
let requests: string[] = [];
const response = (content: unknown[], total = content.length) =>
  new Response(
    JSON.stringify({ content, totalElements: total, totalPages: 1, page: 0, size: 100, first: true, last: true }),
  );
function mount(allowed = true) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } });
  client.setQueryData(["admin", "me", "test-token"], {
    id: "admin",
    email: "admin@hiveapp.test",
    emailVerified: true,
    isSuperAdmin: false,
    isActive: true,
    permissions: allowed ? [adminPermissions.priceBooksList] : [],
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <AdminSessionProvider>
          <ProductPriceSummary ownerType="PLAN" ownerId="plan-1" />
        </AdminSessionProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
beforeEach(() => {
  requests = [];
  clearSession("admin");
  writeSession("admin", {
    accessToken: "test-token",
    refreshToken: "refresh",
    expiresAt: Date.now() + 3_600_000,
    passwordChangeRequired: false,
  });
});
afterEach(() => {
  cleanup();
  clearSession("admin");
  globalThis.fetch = originalFetch;
});
test("shows independent cycles and currencies from the authoritative bounded query", async () => {
  globalThis.fetch = (async (input) => {
    requests.push(String(input));
    return response([
      { id: "m", amount: "100", currencyCode: "MAD", billingCycle: "MONTHLY" },
      { id: "y", amount: "1000", currencyCode: "MAD", billingCycle: "YEARLY" },
      { id: "e", amount: "90", currencyCode: "EUR", billingCycle: "MONTHLY" },
    ]);
  }) as typeof fetch;
  const view = mount();
  expect(await view.findByText("Annuel")).toBeTruthy();
  expect(view.getAllByText("Mensuel")).toHaveLength(2);
  expect(view.getByText(/90,00\s*€/)).toBeTruthy();
  expect(view.container.textContent).not.toMatch(/MAD\s+MAD/);
  expect(requests).toHaveLength(1);
  expect(requests[0]).toContain("currentOnly=true");
  expect(requests[0]).toContain("ownerIds=plan-1");
  expect(view.getByRole("link").getAttribute("href")).toContain("ownerId=plan-1");
});
test("does not fetch or expose tariffs without list permission", () => {
  globalThis.fetch = (async (input) => {
    requests.push(String(input));
    return response([]);
  }) as typeof fetch;
  const view = mount(false);
  expect(view.getByText(/non accessibles/)).toBeTruthy();
  expect(requests).toHaveLength(0);
  expect(view.queryByRole("link")).toBeNull();
});
test("distinguishes an empty catalogue from a failed query", async () => {
  globalThis.fetch = (async (_input) => response([])) as typeof fetch;
  const view = mount();
  expect(await view.findByText("Aucun tarif applicable actuellement.")).toBeTruthy();
});
test("shows retry on failure, never a legacy-price fallback", async () => {
  globalThis.fetch = (async (_input) => new Response("{}", { status: 500 })) as typeof fetch;
  const view = mount();
  expect(await view.findByRole("alert")).toBeTruthy();
  expect(view.getByRole("button", { name: "Réessayer" })).toBeTruthy();
  expect(view.queryByText("Aucun tarif applicable actuellement.")).toBeNull();
});
