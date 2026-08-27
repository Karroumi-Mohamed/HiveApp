import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { Window } from "happy-dom";
import type { ReactNode } from "react";
import type { AdminSubscription, ClientPlanCatalog, SubscriptionChangePreview } from "@/api/contracts";

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
const { MemoryRouter, useLocation } = await import("react-router");
const { adminApi } = await import("@/api/admin-api");
const { adminPermissions } = await import("@/auth/permissions");
const { AdminSessionProvider } = await import("@/auth/session-provider");
const { clearSession, writeSession } = await import("@/auth/session-store");
const { OverridesEditor, SubscriptionDetail } = await import("./admin-subscriptions-page");
const { AdminSubscriptionChangeWorkbench } = await import("./admin-subscription-change-workbench");
const { RetainedSubscriptionQuantityControl } = await import("@/features/commercial/subscription-quantity-control");
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

const changeCatalog: ClientPlanCatalog = {
  currentSubscription: {
    id: "subscription-1",
    planCode: "PRO",
    status: "ACTIVE",
    currentPrice: "100.0000",
    currentPriceCurrencyCode: "MAD",
    planPriceEntryId: "price-1",
    billingCycle: "MONTHLY",
    currentPeriodStart: "2026-08-01T00:00:00Z",
    currentPeriodEnd: "2026-09-01T00:00:00Z",
    cancelAtPeriodEnd: false,
    addOnCodes: [],
    quotaPackages: [],
    retainedAddOns: [],
    retainedQuotaPackages: [],
  },
  plans: [
    {
      code: "PRO",
      name: "Pro",
      description: null,
      basePrice: "100.0000",
      currencyCode: "MAD",
      billingCycle: "MONTHLY",
      current: true,
      selectable: true,
      features: [],
      addOns: [],
      quotaPackages: [],
      prices: [
        {
          priceEntryId: "price-1",
          amount: "100.0000",
          currencyCode: "MAD",
          billingCycle: "MONTHLY",
          effectiveFrom: "2026-08-01T00:00:00Z",
          effectiveUntil: null,
        },
      ],
    },
  ],
};

const changeCatalogWithAddOn: ClientPlanCatalog = {
  ...changeCatalog,
  plans: changeCatalog.plans.map((plan) => ({
    ...plan,
    addOns: [
      {
        code: "AUDIT",
        name: "Audit avancé",
        description: null,
        price: "10.0000",
        currencyCode: "MAD",
        billingCycle: "MONTHLY",
        definitionVersion: 1,
        dependencyCodes: [],
        exclusionCodes: [],
        features: [],
        prices: [
          {
            priceEntryId: "addon-price-1",
            amount: "10.0000",
            currencyCode: "MAD",
            billingCycle: "MONTHLY",
            effectiveFrom: "2026-08-01T00:00:00Z",
            effectiveUntil: null,
          },
        ],
      },
    ],
  })),
};

const preview = (token: string): SubscriptionChangePreview => ({
  subscriptionId: "subscription-1",
  expectedSubscriptionVersion: 4,
  catalogRevision: 9,
  registryVersion: "registry-v1",
  evaluatedAt: new Date().toISOString(),
  expiresAt: new Date(Date.now() + 60_000).toISOString(),
  previewToken: token,
  currentPlanCode: "PRO",
  targetPlanCode: "PRO",
  currentPrice: "100.0000",
  previewPrice: "100.0000",
  currencyCode: "MAD",
  immediateAllowed: true,
  effectiveFeatureCodes: [],
  effectiveQuotaLimits: [],
  addOnCodes: [],
  quotaPackages: [],
  conflicts: [],
});

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

  test("subscription history uses the bounded PageResponse query contract", async () => {
    let requestedUrl = new URL("http://localhost/not-requested");
    globalThis.fetch = (async (input) => {
      requestedUrl = new URL(String(input));
      return jsonResponse(pageResponse([]));
    }) as typeof fetch;

    const result = await adminApi.subscriptionChanges("account-1", {
      page: 2,
      size: 10,
      sort: "status",
      direction: "asc",
    });

    expect(requestedUrl.pathname).toBe("/api/admin/subscriptions/account/account-1/changes");
    expect(Object.fromEntries(requestedUrl.searchParams)).toEqual({
      page: "2",
      size: "10",
      sort: "status",
      direction: "asc",
    });
    expect(result.content).toEqual([]);
    expect(result.page).toBe(0);
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

describe("independent operator subscription surfaces", () => {
  test("loads the change workbench without making the broader subscription query", async () => {
    const requests: string[] = [];
    globalThis.fetch = (async (input) => {
      const url = new URL(String(input));
      requests.push(url.pathname);
      if (url.pathname.endsWith("/change-catalog")) return jsonResponse(changeCatalog);
      throw new Error(`Unexpected request: ${url.pathname}`);
    }) as typeof fetch;

    const { view } = renderAdmin(<SubscriptionDetail accountId="account-1" />, [
      adminPermissions.subscriptionsChooseChangeOptions,
    ]);

    expect(await view.findByRole("heading", { name: "Préparer un changement" })).toBeTruthy();
    expect(requests).toEqual(["/api/admin/subscriptions/account/account-1/change-catalog"]);
  });

  test("loads bounded history without requiring current-subscription access", async () => {
    const requests: string[] = [];
    globalThis.fetch = (async (input) => {
      const url = new URL(String(input));
      requests.push(url.pathname);
      if (url.pathname.endsWith("/changes")) return jsonResponse(pageResponse([]));
      throw new Error(`Unexpected request: ${url.pathname}`);
    }) as typeof fetch;

    const { view } = renderAdmin(<SubscriptionDetail accountId="account-1" />, [
      adminPermissions.subscriptionsReadChanges,
    ]);

    expect(await view.findByRole("heading", { name: "Opérations de changement" })).toBeTruthy();
    expect(await view.findByText("Aucune opération")).toBeTruthy();
    expect(requests).toEqual(["/api/admin/subscriptions/account/account-1/changes"]);
  });

  test("confirms a paid checkout from history without loading the broader subscription", async () => {
    const requests: Array<{ path: string; method: string; body: unknown }> = [];
    const checkout = {
      id: "checkout-1",
      status: "PENDING_CONFIRMATION" as const,
      amount: "125.0000",
      currencyCode: "MAD",
      gatewayAttemptStatus: "PENDING" as const,
      gatewayReference: "gateway-attempt-1",
      gatewayFailureReason: null,
      confirmationSource: null,
      confirmationReference: null,
      confirmedAt: null,
    };
    const operation = {
      id: "operation-1",
      createdAt: "2026-08-27T10:00:00Z",
      updatedAt: "2026-08-27T10:00:00Z",
      timing: "IMMEDIATE" as const,
      status: "AWAITING_CONFIRMATION" as const,
      effectiveAt: null,
      sourcePlanCode: "PRO",
      targetPlanCode: "BUSINESS",
      attentionReason: null,
      checkout,
      requestOrigin: "PLATFORM_ADMIN" as const,
      requestedByUserId: "admin-1",
      requestReason: "Contrat validé",
      cancellationOrigin: null,
      cancelledByUserId: null,
      cancellationReason: null,
      cancelledAt: null,
    };
    globalThis.fetch = (async (input, init) => {
      const url = new URL(String(input));
      const method = init?.method ?? "GET";
      const body = init?.body ? JSON.parse(String(init.body)) : null;
      requests.push({ path: url.pathname, method, body });
      if (url.pathname.endsWith("/changes")) return jsonResponse(pageResponse([operation]));
      if (url.pathname.endsWith("/checkouts/checkout-1/confirm-manual")) {
        return jsonResponse({
          ...checkout,
          status: "CONFIRMED",
          confirmationSource: "MANUAL_OPERATOR",
          confirmationReference: "receipt-42",
          confirmedAt: "2026-08-27T10:10:00Z",
        });
      }
      throw new Error(`Unexpected request: ${url.pathname}`);
    }) as typeof fetch;

    const { view } = renderAdmin(<SubscriptionDetail accountId="account-1" />, [
      adminPermissions.subscriptionsReadChanges,
      adminPermissions.subscriptionsConfirmCheckout,
    ]);
    const user = userEvent.setup({ document: view.container.ownerDocument });
    const [confirmTrigger] = await view.findAllByRole("button", { name: "Confirmer" });
    if (!confirmTrigger) throw new Error("Expected a checkout confirmation trigger");
    await user.click(confirmTrigger);

    expect((await view.findAllByText(/125,00 MAD/)).length).toBeGreaterThan(0);
    await user.type(view.getByLabelText("Référence"), " receipt-42 ");
    await user.type(view.getByLabelText("Justification"), " Paiement vérifié ");
    await user.click(view.getByRole("button", { name: "Confirmer le paiement" }));

    await waitFor(() =>
      expect(requests.some(({ path }) => path.endsWith("/checkouts/checkout-1/confirm-manual"))).toBeTrue(),
    );
    const confirmation = requests.find(({ path }) => path.endsWith("/checkouts/checkout-1/confirm-manual"));
    expect(confirmation).toEqual({
      path: "/api/admin/subscriptions/checkouts/checkout-1/confirm-manual",
      method: "POST",
      body: { reference: "receipt-42", reason: "Paiement vérifié" },
    });
    expect(requests.some(({ path }) => path === "/api/admin/subscriptions/account/account-1")).toBeFalse();
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

describe("retained subscription quantity", () => {
  test("offers keep-or-remove instead of invalid intermediate quantities", async () => {
    const values: number[] = [];
    const view = render(
      <RetainedSubscriptionQuantityControl
        label="Pack historique"
        maximum={5}
        onChange={(value) => values.push(value)}
        quantityEditable={false}
        removable={true}
        retainedQuantity={5}
        value={5}
      />,
    );
    const user = userEvent.setup({ document: view.container.ownerDocument });

    await user.click(view.getByRole("button", { name: "Retirer" }));

    expect(values).toEqual([0]);
    expect(view.queryByRole("button", { name: /Réduire|Augmenter/ })).toBeNull();
  });
});

describe("operator subscription change workbench", () => {
  test("does not request signed evidence for an unchanged exact selection", () => {
    globalThis.fetch = (async (input: RequestInfo | URL) => {
      throw new Error(`Unexpected request: ${String(input)}`);
    }) as unknown as typeof fetch;
    const { view } = renderAdmin(<AdminSubscriptionChangeWorkbench accountId="account-1" catalog={changeCatalog} />, [
      adminPermissions.subscriptionsPreviewChange,
    ]);

    expect(view.getByText("Aucun changement sélectionné.")).toBeTruthy();
    expect(view.getByRole("button", { name: "Prévisualiser" }).hasAttribute("disabled")).toBeTrue();
  });

  test("requires a reason and replaces rejected signed evidence before retrying", async () => {
    let previewCalls = 0;
    const applyBodies: Array<Record<string, unknown>> = [];
    globalThis.fetch = (async (input, init) => {
      const url = new URL(String(input));
      if (url.pathname.endsWith("/changes/preview")) {
        previewCalls += 1;
        return jsonResponse(preview(previewCalls === 1 ? "first.review.token" : "fresh.review.token"));
      }
      if (url.pathname.endsWith("/changes/apply")) {
        const body = JSON.parse(String(init?.body)) as Record<string, unknown>;
        applyBodies.push(body);
        if (applyBodies.length === 1) {
          return jsonResponse({ code: "STALE_RESOURCE_VERSION", message: "Rejected old evidence" }, 409);
        }
        return jsonResponse({
          subscription,
          preview: preview("fresh.review.token"),
          operation: {
            id: "operation-1",
            createdAt: "2026-08-27T10:00:00Z",
            updatedAt: "2026-08-27T10:00:00Z",
            timing: "IMMEDIATE",
            status: "APPLIED",
            effectiveAt: "2026-08-27T10:00:00Z",
            sourcePlanCode: "PRO",
            targetPlanCode: "PRO",
            attentionReason: null,
            checkout: null,
          },
        });
      }
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;

    const { view } = renderAdmin(
      <AdminSubscriptionChangeWorkbench accountId="account-1" catalog={changeCatalogWithAddOn} />,
      [adminPermissions.subscriptionsPreviewChange, adminPermissions.subscriptionsApplyChange],
    );
    const user = userEvent.setup({ document: view.container.ownerDocument });
    await user.click(view.getByRole("checkbox", { name: /Audit avancé/ }));
    await user.click(view.getByRole("button", { name: "Prévisualiser" }));
    await view.findByRole("heading", { name: "Vérifier le changement" });

    await user.click(view.getByRole("button", { name: "Appliquer le changement" }));
    expect(await view.findByText("Saisissez la justification de cette opération.")).toBeTruthy();
    expect(applyBodies).toHaveLength(0);

    await user.type(view.getByLabelText("Justification de l’opération"), "  Contrat client approuvé  ");
    await user.click(view.getByRole("button", { name: "Appliquer le changement" }));
    await waitFor(() => expect(previewCalls).toBe(2));
    await waitFor(() => expect(view.getByRole("button", { name: "Appliquer le changement" })).toBeTruthy());
    await user.click(view.getByRole("button", { name: "Appliquer le changement" }));
    await waitFor(() => expect(applyBodies).toHaveLength(2));

    expect(applyBodies.map((body) => body.previewToken)).toEqual(["first.review.token", "fresh.review.token"]);
    expect(applyBodies.every((body) => body.reason === "Contrat client approuvé")).toBeTrue();
  });
});
