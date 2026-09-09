import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { Window } from "happy-dom";

const browser = new Window({ url: "http://localhost:3000/admin/segments" });
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
const { AdminCommercialSegmentDetailPage } = await import("./admin-commercial-segment-detail-page");
const { AdminCommercialSegmentEditPage } = await import("./commercial-segment-editor");

const segmentId = "5ce8c602-2da0-4a24-a07c-df10d04a5c50";
const activationId = "f627cf50-b295-4ee3-a52a-eb04cde766d3";
const accountId = "f0531700-650b-4036-a26d-3e449c1c2070";
const comparedId = "7972a3d0-2c0e-4f89-8a03-2c0ea7c47d75";

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function session(permissions: string[]) {
  return {
    id: "admin-1",
    email: "admin@hiveapp.test",
    emailVerified: true,
    isSuperAdmin: false,
    isActive: true,
    permissions,
  };
}

function renderRoute(entry: string, permissions: string[]) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  queryClient.setQueryData(["admin", "me", "admin-token"], session(permissions));
  const router = createMemoryRouter(
    [
      {
        path: "/admin/segments/:segmentId/:tab",
        element: <AdminCommercialSegmentDetailPage />,
      },
    ],
    { initialEntries: [entry] },
  );
  return render(
    <QueryClientProvider client={queryClient}>
      <AdminSessionProvider>
        <RouterProvider router={router} />
      </AdminSessionProvider>
    </QueryClientProvider>,
  );
}

function renderActivation(permissions: string[]) {
  return renderRoute(`/admin/segments/${segmentId}/activations?activation=${activationId}`, permissions);
}

function segmentDetail(name: string, version: number) {
  return {
    summary: {
      id: segmentId,
      code: "SEGMENT-R1",
      name,
      status: "DRAFT",
      kind: "EXPLICIT_ACCOUNTS",
      configuredAccountCount: 1,
      latestActivationAccountCount: null,
      lineageId: "922b3f0b-c4e8-4b70-b465-69dc8e08c3f8",
      revisionNumber: 1,
      creationReason: "CREATED",
      version,
      createdAt: "2026-08-27T00:00:00Z",
      updatedAt: "2026-08-27T00:00:00Z",
      availableActions: ["EDIT_DRAFT"],
      blockedActions: {},
      ownerIdentityRestricted: true,
      audienceIdentityRestricted: true,
    },
    description: null,
    reason: "Renouvellement",
    definition: { explicitAccountIds: [accountId], criteria: null },
    sourceSegmentId: null,
  };
}

function renderEditor(permissions: string[]) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  queryClient.setQueryData(["admin", "me", "admin-token"], session(permissions));
  const router = createMemoryRouter(
    [
      { path: "/admin/segments/:segmentId/edit", element: <AdminCommercialSegmentEditPage /> },
      { path: "/admin/segments/:segmentId", element: <p>Fiche enregistrée</p> },
    ],
    { initialEntries: [`/admin/segments/${segmentId}/edit`] },
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

describe("commercial segment activation permission wiring", () => {
  test("an identity-only reader deep-links to known evidence without broader segment reads", async () => {
    const requests: string[] = [];
    globalThis.fetch = (async (input) => {
      const url = String(input);
      if (url.endsWith("/api/admin/me")) {
        return response(session([adminPermissions.segmentsReadActivationIdentities]));
      }
      requests.push(url);
      if (url.includes(`/api/admin/segments/${segmentId}/activations/${activationId}/identities`)) {
        return response({
          segmentId,
          activationId,
          activationNumber: 3,
          immutableAccountCount: 1,
          accounts: {
            content: [{ accountId, accountName: null, accountSlug: null, ownerEmail: null, active: false }],
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

    const view = renderActivation([adminPermissions.segmentsReadActivationIdentities]);

    expect(await view.findByText("Audience figée · activation 3")).toBeTruthy();
    expect(view.getAllByText(accountId).length).toBeGreaterThan(0);
    expect(view.getByText(/Email indisponible/)).toBeTruthy();
    expect(requests).toHaveLength(1);
    expect(requests[0]).toContain(`/activations/${activationId}/identities`);
    expect(requests[0]).not.toMatch(/\/segments\?.*|\/segments\/[^/]+$/);
  });

  test("a stale edit requires an explicit reload before the new version can be submitted", async () => {
    let detailReads = 0;
    const updateBodies: Array<Record<string, unknown>> = [];
    const original = segmentDetail("Version initiale", 4);
    const remote = segmentDetail("Version distante", 5);
    globalThis.fetch = (async (input, init) => {
      const url = String(input);
      if (url.endsWith(`/api/admin/segments/${segmentId}`) && (!init?.method || init.method === "GET")) {
        detailReads += 1;
        return response(detailReads === 1 ? original : remote);
      }
      if (url.endsWith(`/api/admin/segments/${segmentId}`) && init?.method === "PUT") {
        const body = JSON.parse(String(init.body)) as Record<string, unknown>;
        updateBodies.push(body);
        return updateBodies.length === 1
          ? response({ code: "STALE_RESOURCE_VERSION", message: "Changed concurrently" }, 409)
          : response({ ...remote, summary: { ...remote.summary, name: body.name, version: 6 } });
      }
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;

    const view = renderEditor([adminPermissions.segmentsRead, adminPermissions.segmentsUpdateDraft]);
    const user = userEvent.setup({ document: view.container.ownerDocument });
    const name = (await view.findByLabelText("Nom")) as HTMLInputElement;
    await user.clear(name);
    await user.type(name, "Modification locale périmée");
    await user.click(view.getByRole("tab", { name: "Révision" }));
    await user.click(view.getByRole("button", { name: "Enregistrer le brouillon" }));

    expect(await view.findByText("Le brouillon a changé ailleurs")).toBeTruthy();
    await waitFor(() => expect(detailReads).toBe(2));
    expect(updateBodies[0]?.version).toBe(4);
    expect(updateBodies[0]).not.toHaveProperty("source");
    expect(view.queryByLabelText("Origine")).toBeNull();
    expect(view.queryByText("Origine")).toBeNull();
    expect(view.getByRole("button", { name: "Enregistrer le brouillon" }).hasAttribute("disabled")).toBeTrue();

    await user.click(view.getByRole("button", { name: "Recharger et abandonner mes modifications" }));
    await waitFor(() => expect(detailReads).toBe(3));
    const reloadedName = view.getByLabelText("Nom") as HTMLInputElement;
    expect(reloadedName.value).toBe("Version distante");
    await user.clear(reloadedName);
    await user.type(reloadedName, "Version relue");
    await user.click(view.getByRole("tab", { name: "Révision" }));
    await user.click(view.getByRole("button", { name: "Enregistrer le brouillon" }));

    await waitFor(() => expect(updateBodies).toHaveLength(2));
    expect(updateBodies[1]?.version).toBe(5);
    expect(await view.findByText("Fiche enregistrée")).toBeTruthy();
  });

  test("an opaque-only reader never requests the identity surface", async () => {
    const requests: string[] = [];
    globalThis.fetch = (async (input) => {
      const url = String(input);
      if (url.endsWith("/api/admin/me")) {
        return response(session([adminPermissions.segmentsReadActivationAudience]));
      }
      requests.push(url);
      if (url.includes(`/api/admin/segments/${segmentId}/activations/${activationId}/accounts`)) {
        return response({
          segmentId,
          activationId,
          activationNumber: 4,
          immutableAccountCount: 1,
          accounts: {
            content: [{ accountId }],
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

    const view = renderActivation([adminPermissions.segmentsReadActivationAudience]);

    expect(await view.findByText("Audience figée · activation 4")).toBeTruthy();
    expect(view.getByText(accountId)).toBeTruthy();
    expect(requests).toHaveLength(1);
    expect(requests[0]).toContain(`/activations/${activationId}/accounts`);
    expect(requests[0]).not.toContain("/identities");
  });

  test("a compare-only reader uses a known revision id without loading the lineage", async () => {
    const requests: string[] = [];
    globalThis.fetch = (async (input) => {
      const url = String(input);
      if (url.endsWith("/api/admin/me")) return response(session([adminPermissions.segmentsCompare]));
      requests.push(url);
      if (url.endsWith(`/api/admin/segments/${segmentId}/compare/${comparedId}`)) {
        const summary = {
          id: segmentId,
          code: "SEGMENT-R1",
          name: "Comptes prioritaires",
          kind: "EXPLICIT_ACCOUNTS",
          revisionNumber: 1,
        };
        return response({
          sourceSegmentId: segmentId,
          comparedSegmentId: comparedId,
          sameLineage: true,
          directSuccessor: true,
          changedFields: [],
          source: { summary, definition: { explicitAccountIds: [accountId], criteria: null } },
          compared: {
            summary: { ...summary, id: comparedId, code: "SEGMENT-R2", revisionNumber: 2 },
            definition: { explicitAccountIds: [accountId], criteria: null },
          },
        });
      }
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;

    const view = renderRoute(`/admin/segments/${segmentId}/revisions?against=${comparedId}`, [
      adminPermissions.segmentsCompare,
    ]);

    expect(await view.findByText("Aucune différence de définition.")).toBeTruthy();
    expect(requests).toEqual([expect.stringContaining(`/api/admin/segments/${segmentId}/compare/${comparedId}`)]);
    expect(requests[0]).not.toMatch(/\/revisions(?:\?|$)/);
  });
});
