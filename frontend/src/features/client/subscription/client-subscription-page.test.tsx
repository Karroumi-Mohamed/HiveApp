import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { Window } from "happy-dom";
import type {
  ClientCommercialPolicyDecision,
  ClientCommercialPolicyEvaluation,
  ClientSubscriptionChangePreview,
  SubscriptionChangeOperation,
} from "@/api/contracts";

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
const { ClientCommercialPolicyTerms } = await import("@/features/commercial/commercial-policy-terms");

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
  selectable: true,
  commercialPolicyDecisions: [],
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
  commercialPolicyDecisions: [],
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
      commercialPolicyDecisions: [],
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

const policyGrant: ClientCommercialPolicyDecision = {
  effectType: "GRANT_ADD_ON",
  productType: "ADD_ON",
  productCode: "GIFT",
  featureCode: null,
  quotaResource: null,
  quantityDelta: null,
  outcome: "AVAILABLE",
  evaluatedAmount: "0.0000",
  evaluatedCurrencyCode: "MAD",
  explanation: "internal explanation that must remain hidden",
};

const policyQuotaGrant: ClientCommercialPolicyDecision = {
  ...policyGrant,
  effectType: "GRANT_QUOTA_PACKAGE",
  productType: "QUOTA_PACKAGE",
  productCode: "GIFT_CAPACITY",
};

const policyCatalog = {
  ...catalog,
  commercialPolicyDecisions: [policyGrant, policyQuotaGrant],
  plans: catalog.plans.map((plan) => ({
    ...plan,
    commercialPolicyDecisions: [policyGrant, policyQuotaGrant],
    addOns: [
      ...plan.addOns,
      {
        ...addOn("GIFT", "Assistance incluse"),
        price: "0.0000",
        prices: [{ ...price, priceEntryId: "GIFT-price", amount: "0.0000" }],
        commercialPolicyDecisions: [policyGrant],
      },
    ],
    quotaPackages: [
      {
        code: "GIFT_CAPACITY",
        name: "Capacité incluse",
        description: null,
        definitionVersion: 1,
        featureCode: "STAFF",
        resource: "members",
        capacityPerUnit: 5,
        price: "5.0000",
        currencyCode: "MAD",
        billingCycle: "MONTHLY" as const,
        repeatable: false,
        maximumQuantity: 1,
        allowedPlanCodes: ["PRO"],
        allowedAddOnCodes: [],
        prices: [{ ...price, priceEntryId: "GIFT_CAPACITY-price", amount: "5.0000" }],
        directlyAvailable: false,
        requiresAddOnCodes: ["GIFT"],
        selectable: true,
        commercialPolicyDecisions: [policyQuotaGrant],
      },
    ],
  })),
};

function clientPolicyEvaluation(
  conflicts: ClientCommercialPolicyEvaluation["conflicts"] = [],
): ClientCommercialPolicyEvaluation {
  return {
    evaluatedAt: "2026-08-27T10:00:00Z",
    catalogueRecurringPrice: "150.0000",
    fixedRecurringPrice: "120.0000",
    discountAmount: "20.0000",
    finalRecurringPrice: "100.0000",
    currencyCode: "MAD",
    decisions: [{ ...policyGrant, outcome: "APPLIED" }],
    conflicts,
  };
}

function clientPolicyPreview(
  commercialPolicyEvaluation: ClientCommercialPolicyEvaluation,
): ClientSubscriptionChangePreview {
  return {
    subscriptionId: "subscription-1",
    expectedSubscriptionVersion: 4,
    catalogRevision: 9,
    registryVersion: "registry-v1",
    evaluatedAt: "2026-08-27T10:00:00Z",
    expiresAt: new Date(Date.now() + 60_000).toISOString(),
    previewToken: "safe.preview.token",
    currentPlanCode: "PRO",
    targetPlanCode: "PRO",
    currentPrice: "110.0000",
    previewPrice: "100.0000",
    currencyCode: "MAD",
    immediateAllowed: true,
    effectiveFeatureCodes: [],
    effectiveQuotaLimits: [],
    addOnCodes: ["AUDIT", "GIFT"],
    quotaPackages: [],
    conflicts: [],
    commercialPolicyEvaluation,
  };
}

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
  test("orders exact commercial pricing while keeping the rendered client terms privacy-safe", () => {
    const evaluation = {
      ...clientPolicyEvaluation(),
      decisions: [
        ...clientPolicyEvaluation().decisions,
        {
          ...policyGrant,
          effectType: "BLOCK_FEATURE" as const,
          productType: null,
          productCode: null,
          featureCode: "REJECTED_SECRET_FEATURE",
          outcome: "REJECTED_LOWER_PRECEDENCE" as const,
        },
      ],
    };
    const view = render(<ClientCommercialPolicyTerms evaluation={evaluation} />);
    const text = (view.container.textContent ?? "").replaceAll("\u00a0", " ");

    expect(text.indexOf("Sous-total catalogue")).toBeLessThan(text.indexOf("Tarif fixe retenu"));
    expect(text.indexOf("Tarif fixe retenu")).toBeLessThan(text.indexOf("Remise non cumulable"));
    expect(text.indexOf("Remise non cumulable")).toBeLessThan(text.indexOf("Prix récurrent final"));
    expect(text).toContain("100,00 MAD");
    expect(text).toContain("Add-on GIFT inclus sans coût récurrent");
    expect(text).not.toContain("Fonctionnalité REJECTED_SECRET_FEATURE indisponible pour ce compte");
    expect(text).not.toContain("REJECTED_SECRET_FEATURE");
    expect(text).not.toContain("policy-secret-id");
    expect(text).not.toContain("internal explanation");
  });

  test("names an applied fixed recurring price even when it equals the catalogue subtotal", () => {
    const evaluation: ClientCommercialPolicyEvaluation = {
      ...clientPolicyEvaluation(),
      fixedRecurringPrice: "150.0000",
      discountAmount: "0.0000",
      finalRecurringPrice: "150.0000",
      decisions: [
        {
          ...policyGrant,
          effectType: "FIXED_SUBSCRIPTION_PRICE",
          productType: null,
          productCode: null,
          outcome: "APPLIED",
          evaluatedAmount: "150.0000",
        },
      ],
    };

    const view = render(<ClientCommercialPolicyTerms evaluation={evaluation} />);
    expect(view.getByText("Tarif fixe retenu")).toBeTruthy();
    expect(view.getAllByText("150,00 MAD").length).toBeGreaterThanOrEqual(3);
  });

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
      attentionCode: null,
      checkout: null,
      commercialPolicyEvaluation: null,
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

  test("restores the exact held price after switching away from the current plan", async () => {
    const yearlyPrice = {
      ...price,
      priceEntryId: "price-yearly",
      amount: "1000.0000",
      billingCycle: "YEARLY" as const,
    };
    const otherPrice = { ...price, priceEntryId: "basic-monthly", amount: "60.0000" };
    const pricedCatalog = {
      ...catalog,
      currentSubscription: {
        ...catalog.currentSubscription,
        currentPrice: "1010.0000",
        planPriceEntryId: yearlyPrice.priceEntryId,
        billingCycle: "YEARLY" as const,
      },
      plans: [
        { ...catalog.plans[0], prices: [price, yearlyPrice] },
        {
          ...catalog.plans[0],
          code: "BASIC",
          name: "Basic",
          current: false,
          addOns: [],
          prices: [otherPrice],
        },
      ],
    };
    globalThis.fetch = (async (input) => {
      const url = new URL(String(input));
      if (url.pathname === "/api/v1/subscriptions/catalog") return jsonResponse(pricedCatalog);
      throw new Error(`Unexpected request: ${url.pathname}`);
    }) as typeof fetch;

    const { view } = renderClient("/app/subscription?tab=catalog", [
      clientPermissions.subscriptionCatalog,
      clientPermissions.subscriptionPreview,
    ]);
    const user = userEvent.setup({ document: view.container.ownerDocument });
    const pro = await view.findByRole("button", { name: /Pro/ });
    const basic = view.getByRole("button", { name: /Basic/ });

    expect(pro.getAttribute("aria-pressed")).toBe("true");
    expect(view.getByRole("combobox", { name: "Tarif du forfait" }).textContent).toContain("Annuel");
    await user.click(basic);
    expect(basic.getAttribute("aria-pressed")).toBe("true");
    await user.click(pro);

    expect(pro.getAttribute("aria-pressed")).toBe("true");
    expect(view.getByRole("combobox", { name: "Tarif du forfait" }).textContent).toContain("Annuel");
    expect(view.getByText("Aucun changement sélectionné.")).toBeTruthy();
    expect(view.getByRole("button", { name: "Prévisualiser" }).hasAttribute("disabled")).toBeTrue();
    expect(view.container.textContent).not.toContain("À partir de");
  });

  test("does not present an unresolved policy grant as included or send it as a paid choice", async () => {
    const conditionalGrant = { ...policyGrant, productCode: "GIFT_WITH_DEP" };
    const conditionalCatalog = {
      ...catalog,
      commercialPolicyDecisions: [conditionalGrant],
      plans: catalog.plans.map((plan) => ({
        ...plan,
        commercialPolicyDecisions: [conditionalGrant],
        addOns: [
          ...plan.addOns,
          {
            ...addOn("GIFT_WITH_DEP", "Assistance conditionnelle", ["CORE"]),
            prices: [{ ...price, priceEntryId: "gift-with-dep-price", amount: "0.0000" }],
            commercialPolicyDecisions: [conditionalGrant],
          },
        ],
      })),
    };
    let previewSelection: { addOnCodes?: string[] } | null = null;
    globalThis.fetch = (async (input, init) => {
      const url = new URL(String(input));
      if (url.pathname === "/api/v1/subscriptions/catalog") return jsonResponse(conditionalCatalog);
      if (url.pathname === "/api/v1/subscriptions/preview") {
        previewSelection = JSON.parse(String(init?.body));
        return jsonResponse(clientPolicyPreview(clientPolicyEvaluation()));
      }
      throw new Error(`Unexpected request: ${url.pathname}`);
    }) as typeof fetch;

    const { view } = renderClient("/app/subscription?tab=catalog", [
      clientPermissions.subscriptionCatalog,
      clientPermissions.subscriptionPreview,
    ]);
    const user = userEvent.setup({ document: view.container.ownerDocument });
    const conditional = await view.findByRole("checkbox", { name: /Assistance conditionnelle/ });

    expect(conditional.getAttribute("aria-checked")).toBe("false");
    expect(conditional.hasAttribute("disabled")).toBeTrue();
    expect(view.getByText(/Inclus dès que vous sélectionnez Module socle/)).toBeTruthy();

    await user.click(view.getByRole("checkbox", { name: /^Module socle/ }));
    await waitFor(() => expect(conditional.getAttribute("aria-checked")).toBe("true"));
    expect(view.getAllByText("Inclus par condition commerciale").length).toBeGreaterThan(0);

    await user.click(view.getByRole("button", { name: "Prévisualiser" }));
    await waitFor(() => expect(previewSelection).not.toBeNull());
    const submitted = previewSelection as { addOnCodes?: string[] } | null;
    expect(submitted?.addOnCodes).toContain("CORE");
    expect(submitted?.addOnCodes).not.toContain("GIFT_WITH_DEP");
  });

  test("locks a zero-price policy grant while still allowing an exact selection to be reviewed", async () => {
    globalThis.fetch = (async (input) => {
      const url = new URL(String(input));
      if (url.pathname === "/api/v1/subscriptions/catalog") return jsonResponse(policyCatalog);
      throw new Error(`Unexpected request: ${url.pathname}`);
    }) as typeof fetch;

    const { view } = renderClient("/app/subscription?tab=catalog", [
      clientPermissions.subscriptionCatalog,
      clientPermissions.subscriptionPreview,
    ]);

    await view.findByText("Assistance incluse");
    const granted = view.getByRole("checkbox", { name: /Assistance incluse/ });
    expect(granted.getAttribute("aria-checked")).toBe("true");
    expect(granted.hasAttribute("disabled")).toBeTrue();
    expect(view.getAllByText("Inclus par condition commerciale")).toHaveLength(2);
    expect(view.getByText("Capacité incluse")).toBeTruthy();
    expect(view.getByText("1 incluse")).toBeTruthy();
    expect(view.queryByRole("button", { name: "Réduire Capacité incluse" })).toBeNull();
    expect(view.queryByRole("button", { name: "Augmenter Capacité incluse" })).toBeNull();
    expect(view.getByRole("button", { name: "Prévisualiser" }).hasAttribute("disabled")).toBeFalse();
  });

  test("keeps accepted zero-price snapshot terms visible after the granting policy pauses", async () => {
    const heldCatalog = {
      ...catalog,
      currentSubscription: {
        ...catalog.currentSubscription,
        quotaPackages: [{ packageCode: "HELD_CAPACITY", quantity: 1 }],
        retainedAddOns: catalog.currentSubscription.retainedAddOns.map((item) => ({
          ...item,
          unitPrice: "0.0000",
        })),
        retainedQuotaPackages: [
          {
            code: "HELD_CAPACITY",
            name: "Capacité contractuelle",
            definitionVersion: 1,
            featureCode: "STAFF",
            resource: "members",
            capacityPerUnit: 5,
            quantity: 1,
            unitPrice: "0.0000",
            currencyCode: "MAD",
            billingCycle: "MONTHLY" as const,
            priceEntryId: "HELD_CAPACITY-price",
            state: "SELECTABLE" as const,
            removable: false,
            quantityEditable: false,
            maximumSelectableQuantity: 1,
          },
        ],
      },
      plans: catalog.plans.map((plan) => ({
        ...plan,
        quotaPackages: [
          {
            code: "HELD_CAPACITY",
            name: "Capacité contractuelle",
            description: null,
            definitionVersion: 2,
            featureCode: "STAFF",
            resource: "members",
            capacityPerUnit: 5,
            price: "5.0000",
            currencyCode: "MAD",
            billingCycle: "MONTHLY" as const,
            repeatable: true,
            maximumQuantity: 10,
            allowedPlanCodes: ["PRO"],
            allowedAddOnCodes: [],
            prices: [{ ...price, priceEntryId: "HELD_CAPACITY-price", amount: "5.0000" }],
            directlyAvailable: true,
            requiresAddOnCodes: [],
            selectable: true,
            commercialPolicyDecisions: [],
          },
        ],
      })),
    };
    globalThis.fetch = (async (input) => {
      const url = new URL(String(input));
      if (url.pathname === "/api/v1/subscriptions/catalog") return jsonResponse(heldCatalog);
      throw new Error(`Unexpected request: ${url.pathname}`);
    }) as typeof fetch;

    const { view } = renderClient("/app/subscription?tab=catalog", [clientPermissions.subscriptionCatalog]);

    await view.findByText("Audit avancé");
    const text = (view.container.textContent ?? "").replaceAll("\u00a0", " ");
    expect(text).toContain("0,00 MAD · conditions détenues");
    expect(text).toContain("0,00 MAD par unité · conditions détenues");
    expect(view.getByText("× 1")).toBeTruthy();
    expect(view.queryByText("Inclus par condition commerciale")).toBeNull();
  });

  test("shows only safe policy terms and blocks confirmation when the reviewed selection conflicts", async () => {
    const evaluation = clientPolicyEvaluation([
      {
        code: "POLICY_FEATURE_BLOCKED",
        productCode: null,
        featureCode: "PAYROLL",
        quotaResource: null,
        message: "server wording and policy-secret-id must not be rendered",
      },
    ]);
    globalThis.fetch = (async (input) => {
      const url = new URL(String(input));
      if (url.pathname === "/api/v1/subscriptions/catalog") return jsonResponse(policyCatalog);
      if (url.pathname === "/api/v1/subscriptions/preview")
        return jsonResponse({ ...clientPolicyPreview(evaluation), immediateAllowed: false });
      throw new Error(`Unexpected request: ${url.pathname}`);
    }) as typeof fetch;

    const { view } = renderClient("/app/subscription?tab=catalog", [
      clientPermissions.subscriptionCatalog,
      clientPermissions.subscriptionPreview,
      clientPermissions.subscriptionApply,
    ]);
    const user = userEvent.setup({ document: view.container.ownerDocument });
    await view.findByText("Assistance incluse");
    await user.click(view.getByRole("button", { name: "Prévisualiser" }));

    expect(await view.findByRole("heading", { name: "Vérifier le changement" })).toBeTruthy();
    expect(view.getByText("Prix récurrent final")).toBeTruthy();
    expect(view.getByText("La fonctionnalité PAYROLL n’est pas disponible pour ce compte.")).toBeTruthy();
    expect(view.getByText("Aucune capacité mesurée.")).toBeTruthy();
    expect(view.getByText(/Ce changement ne peut pas être appliqué maintenant/)).toBeTruthy();
    expect(view.getByRole("button", { name: "Confirmer le changement" }).hasAttribute("disabled")).toBeTrue();
    expect(view.container.textContent).not.toContain("server wording");
    expect(view.container.textContent).not.toContain("policy-secret-id");
  });

  test("renders the immutable privacy-safe terms stored with change history", async () => {
    const operation: SubscriptionChangeOperation = {
      id: "operation-policy",
      createdAt: "2026-08-27T10:00:00Z",
      updatedAt: "2026-08-27T10:00:00Z",
      timing: "IMMEDIATE",
      status: "APPLIED",
      effectiveAt: "2026-08-27T10:00:00Z",
      sourcePlanCode: "PRO",
      targetPlanCode: "PRO",
      attentionCode: "OPERATOR_ASSISTANCE_REQUIRED",
      checkout: null,
      commercialPolicyEvaluation: clientPolicyEvaluation(),
    };
    globalThis.fetch = (async (input) => {
      const url = new URL(String(input));
      if (url.pathname === "/api/v1/subscriptions/changes") return jsonResponse(pageResponse([operation]));
      throw new Error(`Unexpected request: ${url.pathname}`);
    }) as typeof fetch;

    const { view } = renderClient("/app/subscription?tab=changes", [clientPermissions.subscriptionReadChanges]);
    const user = userEvent.setup({ document: view.container.ownerDocument });
    const disclosure = await view.findByRole("button", { name: "Afficher la traçabilité de PRO vers PRO" });
    await user.click(disclosure);

    expect((await view.findAllByText("Conditions acceptées avec ce changement")).length).toBeGreaterThan(0);
    expect(view.getAllByText("Prix récurrent final").length).toBeGreaterThan(0);
    expect(
      view.getAllByText("Ce changement nécessite l’aide d’un opérateur. Contactez le support.").length,
    ).toBeGreaterThan(0);
    expect(view.container.textContent).not.toContain("policy-secret-id");
    expect(view.container.textContent).not.toContain("internal explanation");
  });

  test("opens the billing surface with timeline permission alone", async () => {
    const requests: string[] = [];
    globalThis.fetch = (async (input) => {
      const url = new URL(String(input));
      requests.push(url.pathname);
      if (url.pathname === "/api/v1/subscriptions/financial-timeline") return jsonResponse(pageResponse([]));
      throw new Error(`Unexpected request: ${url.pathname}`);
    }) as typeof fetch;

    const { view } = renderClient("/app/subscription?tab=invoices", [
      clientPermissions.subscriptionReadFinancialTimeline,
    ]);

    expect(await view.findByText("Aucune opération financière")).toBeTruthy();
    expect(view.queryByText("Aucune facture")).toBeNull();
    expect(requests).toEqual(["/api/v1/subscriptions/financial-timeline"]);
  });

  test("opens the billing surface with profile permission alone", async () => {
    const requests: string[] = [];
    globalThis.fetch = (async (input) => {
      const url = new URL(String(input));
      requests.push(url.pathname);
      if (url.pathname === "/api/v1/subscriptions/billing-profile") {
        return jsonResponse({
          accountId: "account-1",
          legalName: "Acme SARL",
          billingEmail: "billing@acme.test",
          taxId: null,
          address: null,
          countryCode: "MA",
          explicitlyConfigured: true,
        });
      }
      throw new Error(`Unexpected request: ${url.pathname}`);
    }) as typeof fetch;

    const { view } = renderClient("/app/subscription?tab=invoices", [clientPermissions.subscriptionReadBillingProfile]);

    expect(await view.findByText("Acme SARL")).toBeTruthy();
    expect(view.queryByText("Historique financier")).toBeNull();
    expect(requests).toEqual(["/api/v1/subscriptions/billing-profile"]);
  });

  test("shows an honest empty state when agreements are the only readable current surface", async () => {
    globalThis.fetch = (async (input) => {
      const url = new URL(String(input));
      if (url.pathname === "/api/v1/subscriptions/agreements") return jsonResponse(pageResponse([]));
      throw new Error(`Unexpected request: ${url.pathname}`);
    }) as typeof fetch;

    const { view } = renderClient("/app/subscription?tab=current", [
      clientPermissions.subscriptionReadSpecialAgreements,
    ]);

    expect(await view.findByText("Aucun accord spécial")).toBeTruthy();
  });
});
