import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { Window } from "happy-dom";

const browser = new Window({ url: "http://localhost:3000/app/subscription" });
for (const key of [
  "window",
  "document",
  "navigator",
  "localStorage",
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
const { cleanup, render, waitFor } = await import("@testing-library/react");
const userEvent = (await import("@testing-library/user-event")).default;
const { QueryClient, QueryClientProvider } = await import("@tanstack/react-query");
const { MemoryRouter } = await import("react-router");
const { clientPermissions } = await import("@/auth/permissions");
const { ClientSessionProvider } = await import("@/auth/session-provider");
const { clearSession, writeSession } = await import("@/auth/session-store");
const { ClientSubscriptionPage } = await import("./client-subscription-page");

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function pageResponse(content: unknown[]) {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    first: true,
    last: true,
  };
}

function renderClient(entry: string, permissions: string[]) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Number.POSITIVE_INFINITY }, mutations: { retry: false } },
  });
  queryClient.setQueryData(["client", "account", "client-token"], {
    id: "account-1",
    ownerId: "member-1",
    name: "Acme",
    slug: "acme",
    isActive: true,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  });
  queryClient.setQueryData(["client", "companies", "client-token"], []);
  queryClient.setQueryData(["client", "permissions", null, false, "client-token"], {
    memberId: "member-1",
    accountId: "account-1",
    companyId: null,
    isOwner: false,
    permissions,
  });
  const view = render(
    <QueryClientProvider client={queryClient}>
      <ClientSessionProvider>
        <MemoryRouter initialEntries={[entry]}>
          <ClientSubscriptionPage />
        </MemoryRouter>
      </ClientSessionProvider>
    </QueryClientProvider>,
  );
  return { queryClient, view };
}

const price = {
  priceEntryId: "price-1",
  amount: "100.0000",
  currencyCode: "MAD",
  billingCycle: "MONTHLY",
  effectiveFrom: "2026-01-01T00:00:00Z",
  effectiveUntil: null,
} as const;

const addOn = (code: string, name: string, dependencyCodes: string[] = []) => ({
  code,
  name,
  description: null,
  price: "10.0000",
  currencyCode: "MAD",
  billingCycle: "MONTHLY" as const,
  definitionVersion: 1,
  dependencyCodes,
  exclusionCodes: [],
  features: [],
  prices: [{ ...price, priceEntryId: `${code}-price`, amount: "10.0000" }],
});

const catalog = {
  currentSubscription: {
    id: "subscription-1",
    planCode: "PRO",
    status: "ACTIVE" as const,
    currentPrice: "110.0000",
    currentPriceCurrencyCode: "MAD",
    planPriceEntryId: "price-1",
    billingCycle: "MONTHLY" as const,
    currentPeriodStart: "2026-08-01T00:00:00Z",
    currentPeriodEnd: "2026-09-01T00:00:00Z",
    cancelAtPeriodEnd: false,
    addOnCodes: ["AUDIT"],
    quotaPackages: [],
    retainedAddOns: [
      {
        code: "AUDIT",
        name: "Audit avancé",
        definitionVersion: 1,
        unitPrice: "10.0000",
        currencyCode: "MAD",
        billingCycle: "MONTHLY" as const,
        featureCodes: [],
        priceEntryId: "AUDIT-price",
        state: "SELECTABLE" as const,
        removable: true,
        selectableForNewSale: true,
      },
    ],
    retainedQuotaPackages: [],
  },
  plans: [
    {
      code: "PRO",
      name: "Pro",
      description: null,
      basePrice: "100.0000",
      currencyCode: "MAD",
      billingCycle: "MONTHLY" as const,
      current: true,
      selectable: true,
      features: [],
      addOns: [
        addOn("AUDIT", "Audit avancé"),
        addOn("CORE", "Module socle"),
        addOn("ADVANCED", "Module avancé", ["CORE"]),
      ],
      quotaPackages: [],
      prices: [price],
    },
  ],
};

beforeEach(() => {
  cleanup();
  clearSession("client");
  browser.localStorage.clear();
  writeSession("client", {
    accessToken: "client-token",
    refreshToken: null,
    expiresAt: Date.now() + 600_000,
    passwordChangeRequired: false,
  });
});

afterEach(() => {
  cleanup();
  clearSession("client");
  globalThis.fetch = originalFetch;
});

describe("client subscription operations", () => {
  test("confirms cancellation before sending the destructive request", async () => {
    let deleteCalls = 0;
    const pending = {
      id: "operation-1",
      createdAt: "2026-08-27T10:00:00Z",
      updatedAt: "2026-08-27T10:00:00Z",
      timing: "AT_RENEWAL",
      status: "PENDING",
      effectiveAt: "2026-09-01T00:00:00Z",
      sourcePlanCode: "PRO",
      targetPlanCode: "BUSINESS",
      attentionReason: null,
      checkout: null,
    };
    globalThis.fetch = (async (input, init) => {
      const url = new URL(String(input));
      if (url.pathname === "/api/v1/subscriptions/changes/operation-1") {
        deleteCalls += 1;
        expect(init?.method).toBe("DELETE");
        return jsonResponse({ ...pending, status: "CANCELLED" });
      }
      if (url.pathname === "/api/v1/subscriptions/changes") return jsonResponse(pageResponse([pending]));
      throw new Error(`Unexpected request: ${url.pathname}`);
    }) as typeof fetch;

    const { view } = renderClient("/app/subscription?tab=changes", [
      clientPermissions.subscriptionReadChanges,
      clientPermissions.subscriptionCancel,
    ]);
    const user = userEvent.setup({ document: view.container.ownerDocument });
    expect((await view.findAllByText("BUSINESS")).length).toBeGreaterThan(0);
    const [cancelTrigger] = view.getAllByRole("button", { name: "Annuler" });
    if (!cancelTrigger) throw new Error("Expected a cancellation trigger");
    await user.click(cancelTrigger);

    expect(deleteCalls).toBe(0);
    expect(await view.findByRole("heading", { name: "Annuler ce changement ?" })).toBeTruthy();
    await user.click(view.getByRole("button", { name: "Confirmer l’annulation" }));
    await waitFor(() => expect(deleteCalls).toBe(1));
  });

  test("does not duplicate a selectable held Add-on and wires recursive dependencies", async () => {
    globalThis.fetch = (async (input) => {
      const url = new URL(String(input));
      if (url.pathname === "/api/v1/subscriptions/catalog") return jsonResponse(catalog);
      throw new Error(`Unexpected request: ${url.pathname}`);
    }) as typeof fetch;

    const { view } = renderClient("/app/subscription?tab=catalog", [
      clientPermissions.subscriptionCatalog,
      clientPermissions.subscriptionPreview,
    ]);
    const user = userEvent.setup({ document: view.container.ownerDocument });
    await view.findByText("Audit avancé");

    expect(view.getAllByText("Audit avancé")).toHaveLength(1);
    expect(view.queryByText("Add-ons conservés")).toBeNull();
    expect(view.getByText("Aucun changement sélectionné.")).toBeTruthy();
    expect(view.getByRole("button", { name: "Prévisualiser" }).hasAttribute("disabled")).toBeTrue();

    await user.click(view.getByRole("checkbox", { name: /Module avancé/ }));
    expect(view.getByRole("checkbox", { name: /Module socle/ }).getAttribute("aria-checked")).toBe("true");
    expect(view.getByRole("button", { name: "Prévisualiser" }).hasAttribute("disabled")).toBeFalse();
  });
});
