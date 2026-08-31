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
const { AdminCommercialPolicyEditPage } = await import("./commercial-policy-editor");

import type { CommercialPolicyDetail } from "@/api/contracts";

const policyId = "5ce8c602-2da0-4a24-a07c-df10d04a5c50";
const now = "2026-08-01T00:00:00Z";
const originalPolicy: CommercialPolicyDetail = {
  summary: {
    id: policyId,
    code: "POLICY-1",
    name: "Version initiale",
    status: "DRAFT",
    targetKind: "SEGMENT",
    targetLabel: "Segment futur",
    configuredTargetCount: 0,
    effectCount: 1,
    latestAffectedAccountCount: null,
    source: "CONTRACT",
    priority: 20,
    effectiveFrom: now,
    effectiveUntil: "2026-09-01T00:00:00Z",
    lineageId: "8bc34c5c-737f-4d50-9434-d54dca8abf22",
    revisionNumber: 1,
    creationReason: "CREATED",
    version: 4,
    createdAt: now,
    updatedAt: now,
    availableActions: ["EDIT_DRAFT"],
    blockers: ["SEGMENT_RESOLUTION_UNAVAILABLE"],
    ownerIdentityRestricted: true,
    executionSupported: true,
    executionBlockers: ["SCHEDULED_EXECUTION_NOT_AVAILABLE"],
  },
  description: null,
  reason: "Contrat signé",
  approvalReference: null,
  contractReference: null,
  target: {
    kind: "SEGMENT",
    accountId: null,
    accountName: null,
    accountIds: [],
    planRevisionId: null,
    planCode: null,
    planName: null,
    segmentReference: "LOYAL_CUSTOMERS",
  },
  effects: [
    {
      id: "f627cf50-b295-4ee3-a52a-eb04cde766d3",
      order: 0,
      type: "FIXED_DISCOUNT",
      productType: null,
      productRevisionId: null,
      productCode: null,
      featureCode: null,
      quotaResource: null,
      quantityDelta: null,
      amount: "10.00",
      currencyCode: "MAD",
      billingCycle: null,
      percentage: null,
      maximumAmount: null,
      maximumCurrencyCode: null,
      precedenceClass: 200,
      canOverridePlatformHardLimits: false,
    },
  ],
  sourcePolicyId: null,
};

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

describe("commercial policy editor conflict wiring", () => {
  test("uses the policy-scoped executable Segment chooser instead of the general Segment catalogue", async () => {
    const requests: string[] = [];
    const choice = {
      id: "50db9575-bf1c-4a30-8b82-e82d9c14d836",
      code: "LOYAL_CUSTOMERS",
      name: "Clients fidèles",
      revisionNumber: 2,
      kind: "TYPED_CRITERIA",
      immutableAccountCount: 42,
    };
    globalThis.fetch = (async (input, init) => {
      const url = String(input);
      requests.push(url);
      if (url.endsWith(`/api/admin/commercial-policies/${policyId}`) && (!init?.method || init.method === "GET")) {
        return response(originalPolicy);
      }
      if (url.includes("/api/admin/commercial-policies/segment-choices/selected")) {
        return response([choice]);
      }
      if (url.includes("/api/admin/commercial-policies/segment-choices")) {
        return response({
          content: [choice],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
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
      permissions: [
        adminPermissions.commercialPoliciesRead,
        adminPermissions.commercialPoliciesUpdateDraft,
        adminPermissions.commercialPoliciesChooseSegments,
        adminPermissions.commercialPoliciesResolveSegmentChoices,
      ],
    });
    const router = createMemoryRouter(
      [{ path: "/admin/commercial-policies/:policyId/edit", element: <AdminCommercialPolicyEditPage /> }],
      { initialEntries: [`/admin/commercial-policies/${policyId}/edit`] },
    );
    const view = render(
      <QueryClientProvider client={queryClient}>
        <AdminSessionProvider>
          <RouterProvider router={router} />
        </AdminSessionProvider>
      </QueryClientProvider>,
    );

    expect(await view.findByLabelText("Segment actif avec audience figée")).toBeTruthy();
    await waitFor(() => {
      expect(requests.some((request) => request.includes("/commercial-policies/segment-choices?"))).toBeTrue();
      expect(requests.some((request) => request.includes("/commercial-policies/segment-choices/selected?"))).toBeTrue();
    });
    expect(requests.some((request) => request.includes("/api/admin/segments"))).toBeFalse();
  });

  test("requires an explicit reload and never retries stale edits with a refreshed version", async () => {
    let detailReads = 0;
    const updateBodies: Array<Record<string, unknown>> = [];
    const remotePolicy = {
      ...originalPolicy,
      summary: { ...originalPolicy.summary, name: "Version distante", version: 5 },
    };
    globalThis.fetch = (async (input, init) => {
      const url = String(input);
      if (url.endsWith(`/api/admin/commercial-policies/${policyId}`) && (!init?.method || init.method === "GET")) {
        detailReads += 1;
        return response(detailReads === 1 ? originalPolicy : remotePolicy);
      }
      if (url.endsWith(`/api/admin/commercial-policies/${policyId}`) && init?.method === "PUT") {
        const body = JSON.parse(String(init.body)) as Record<string, unknown>;
        updateBodies.push(body);
        if (updateBodies.length === 1) {
          return response({ code: "STALE_RESOURCE_VERSION", message: "Changed concurrently" }, 409);
        }
        return response({
          ...remotePolicy,
          summary: { ...remotePolicy.summary, name: body.name as string, version: 6 },
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
      permissions: [adminPermissions.commercialPoliciesRead, adminPermissions.commercialPoliciesUpdateDraft],
    });
    const router = createMemoryRouter(
      [
        {
          path: "/admin/commercial-policies/:policyId/edit",
          element: <AdminCommercialPolicyEditPage />,
        },
        { path: "/admin/commercial-policies/:policyId", element: <p>Fiche enregistrée</p> },
      ],
      { initialEntries: [`/admin/commercial-policies/${policyId}/edit`] },
    );
    const view = render(
      <QueryClientProvider client={queryClient}>
        <AdminSessionProvider>
          <RouterProvider router={router} />
        </AdminSessionProvider>
      </QueryClientProvider>,
    );
    const user = userEvent.setup({ document: view.container.ownerDocument });

    await user.click(await view.findByRole("tab", { name: "Cadre" }));
    const name = view.getByLabelText("Nom de la politique") as HTMLInputElement;
    await user.clear(name);
    await user.type(name, "Modification locale périmée");
    await user.click(view.getByRole("tab", { name: "Révision" }));
    await user.click(view.getByRole("button", { name: "Enregistrer le brouillon" }));

    expect(await view.findByText("Le brouillon a changé ailleurs")).toBeTruthy();
    await waitFor(() => expect(detailReads).toBe(2));
    expect(updateBodies[0]?.version).toBe(4);
    expect(updateBodies[0]?.name).toBe("Modification locale périmée");
    expect(view.getByRole("button", { name: "Enregistrer le brouillon" }).hasAttribute("disabled")).toBeTrue();

    await user.click(view.getByRole("button", { name: "Recharger et abandonner mes modifications" }));
    await waitFor(() => expect(detailReads).toBe(3));
    await user.click(view.getByRole("tab", { name: "Cadre" }));
    const reloadedName = view.getByLabelText("Nom de la politique") as HTMLInputElement;
    expect(reloadedName.value).toBe("Version distante");
    await user.clear(reloadedName);
    await user.type(reloadedName, "Version relue");
    await user.click(view.getByRole("tab", { name: "Révision" }));
    await user.click(view.getByRole("button", { name: "Enregistrer le brouillon" }));

    await waitFor(() => expect(updateBodies).toHaveLength(2));
    expect(updateBodies[1]?.version).toBe(5);
    expect(updateBodies[1]?.name).toBe("Version relue");
    expect(await view.findByText("Fiche enregistrée")).toBeTruthy();
  });

  test("does not resolve retained account identities without account-choice visibility", async () => {
    const requests: string[] = [];
    const accountPolicy: CommercialPolicyDetail = {
      ...originalPolicy,
      summary: { ...originalPolicy.summary, targetKind: "ACCOUNT", configuredTargetCount: 1 },
      target: {
        kind: "ACCOUNT",
        accountId: "f0531700-650b-4036-a26d-3e449c1c2070",
        accountName: null,
        accountIds: [],
        planRevisionId: null,
        planCode: null,
        planName: null,
        segmentReference: null,
      },
    };
    globalThis.fetch = (async (input, init) => {
      const url = String(input);
      requests.push(url);
      if (url.endsWith(`/api/admin/commercial-policies/${policyId}`) && (!init?.method || init.method === "GET")) {
        return response(accountPolicy);
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
      permissions: [adminPermissions.commercialPoliciesRead, adminPermissions.commercialPoliciesUpdateDraft],
    });
    const router = createMemoryRouter(
      [{ path: "/admin/commercial-policies/:policyId/edit", element: <AdminCommercialPolicyEditPage /> }],
      { initialEntries: [`/admin/commercial-policies/${policyId}/edit`] },
    );
    const view = render(
      <QueryClientProvider client={queryClient}>
        <AdminSessionProvider>
          <RouterProvider router={router} />
        </AdminSessionProvider>
      </QueryClientProvider>,
    );

    expect(await view.findByText(/Sélection conservée sans lecture d’identité/)).toBeTruthy();
    await waitFor(() => expect(requests.length).toBeGreaterThan(0));
    expect(requests.some((request) => request.includes("/commercial-policies/account-choices"))).toBeFalse();
    expect(requests.some((request) => request.includes("/plans/choices"))).toBeFalse();
  });
});
