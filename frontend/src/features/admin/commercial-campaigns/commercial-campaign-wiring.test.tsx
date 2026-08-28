import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { Window } from "happy-dom";

const browser = new Window({ url: "http://localhost:3000/admin/campaigns" });
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
const { cleanup, render, waitFor, within } = await import("@testing-library/react");
const userEvent = (await import("@testing-library/user-event")).default;
const { QueryClient, QueryClientProvider } = await import("@tanstack/react-query");
const { createMemoryRouter, RouterProvider } = await import("react-router");
const { adminPermissions } = await import("@/auth/permissions");
const { AdminSessionProvider } = await import("@/auth/session-provider");
const { clearSession, writeSession } = await import("@/auth/session-store");
const { AdminCommercialCampaignDetailPage } = await import("./admin-commercial-campaign-detail-page");
const { AdminCommercialCampaignEditPage } = await import("./commercial-campaign-editor");

const campaignId = "5ce8c602-2da0-4a24-a07c-df10d04a5c50";
const accountId = "f0531700-650b-4036-a26d-3e449c1c2070";
const segmentId = "922b3f0b-c4e8-4b70-b465-69dc8e08c3f8";
const activationId = "f627cf50-b295-4ee3-a52a-eb04cde766d3";
const comparedId = "cb0f6fd1-6ff1-4efa-bbd0-bc911ff6f99e";

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

function detail() {
  return {
    summary: {
      id: campaignId,
      code: "CAMPAIGN-R1",
      name: "Renouvellements",
      status: "DRAFT",
      audienceMode: "SEGMENT",
      source: "RETENTION",
      startsAt: "2026-08-29T10:00:00Z",
      endsAt: "2026-08-30T10:00:00Z",
      configuredAccountCount: 1,
      frozenAccountCount: null,
      lineageId: "7972a3d0-2c0e-4f89-8a03-2c0ea7c47d75",
      revisionNumber: 1,
      creationReason: "CREATED",
      version: 4,
      createdAt: "2026-08-28T00:00:00Z",
      updatedAt: "2026-08-28T00:00:00Z",
      availableActions: ["UPDATE", "SCHEDULE"],
      blockedActions: {},
      ownerIdentityRestricted: true,
      audienceIdentityRestricted: true,
    },
    description: null,
    reason: "Rétention",
    audience: { mode: "SEGMENT", explicitAccountIds: [], segmentId, segmentActivationId: activationId },
    sourceCampaignId: null,
    scheduledAt: null,
    activatedAt: null,
    pausedAt: null,
    resumedAt: null,
    endedAt: null,
    archivedAt: null,
  };
}

function renderDetail(entry: string, permissions: string[]) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  queryClient.setQueryData(["admin", "me", "admin-token"], session(permissions));
  const router = createMemoryRouter(
    [{ path: "/admin/campaigns/:campaignId/:tab?", element: <AdminCommercialCampaignDetailPage /> }],
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

function renderEditor(permissions: string[]) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  queryClient.setQueryData(["admin", "me", "admin-token"], session(permissions));
  const router = createMemoryRouter(
    [
      { path: "/admin/campaigns/:campaignId/edit", element: <AdminCommercialCampaignEditPage /> },
      { path: "/admin/campaigns/:campaignId", element: <p>Campagne</p> },
    ],
    { initialEntries: [`/admin/campaigns/${campaignId}/edit`] },
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

describe("commercial campaign least-privilege wiring", () => {
  test("fetches a signed schedule review only on demand and submits that exact evidence", async () => {
    const previewRequests: string[] = [];
    const scheduleBodies: Array<Record<string, unknown>> = [];
    globalThis.fetch = (async (input, init) => {
      const url = String(input);
      if (url.endsWith(`/api/admin/campaigns/${campaignId}`)) return response(detail());
      if (url.endsWith(`/api/admin/campaigns/${campaignId}/schedule-preview`)) {
        previewRequests.push(url);
        return response({
          campaignId,
          campaignVersion: 4,
          mode: "SEGMENT",
          registryVersion: "registry-8",
          evaluatedAt: new Date().toISOString(),
          expiresAt: new Date(Date.now() + 60_000).toISOString(),
          previewToken: "signed-campaign-preview",
          targetedAccountCount: 24,
          publicAudience: false,
          schedulable: true,
          blockers: [],
          sample: [{ accountId }],
          segmentId,
          segmentActivationId: activationId,
          fingerprint: "opaque",
        });
      }
      if (url.endsWith(`/api/admin/campaigns/${campaignId}/schedule`) && init?.method === "POST") {
        scheduleBodies.push(JSON.parse(String(init.body)) as Record<string, unknown>);
        return response({ ...detail(), summary: { ...detail().summary, status: "SCHEDULED", version: 5 } });
      }
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;
    const view = renderDetail(`/admin/campaigns/${campaignId}`, [
      adminPermissions.campaignsRead,
      adminPermissions.campaignsPreviewSchedule,
      adminPermissions.campaignsSchedule,
    ]);
    const user = userEvent.setup({ document: view.container.ownerDocument });
    const trigger = await view.findByRole("button", { name: "Planifier" });
    expect(previewRequests).toHaveLength(0);
    await user.click(trigger);
    const dialog = await view.findByRole("dialog");
    expect(await within(dialog).findByText("Audience prête")).toBeTruthy();
    await user.type(within(dialog).getByLabelText("Motif de planification"), "Lancement contrôlé");
    await user.click(within(dialog).getByRole("button", { name: "Planifier" }));
    await waitFor(() => expect(scheduleBodies).toHaveLength(1));
    expect(scheduleBodies[0]).toMatchObject({
      version: 4,
      previewToken: "signed-campaign-preview",
      reason: "Lancement contrôlé",
    });
  });

  test("loads opaque frozen evidence first and fetches identities only after explicit reveal", async () => {
    const requests: string[] = [];
    globalThis.fetch = (async (input) => {
      const url = String(input);
      requests.push(url);
      if (url.endsWith(`/api/admin/campaigns/${campaignId}`)) return response(detail());
      if (url.includes(`/api/admin/campaigns/${campaignId}/audience-identities`)) {
        return response({
          campaignId,
          snapshotId: "snapshot-1",
          immutableAccountCount: 1,
          accounts: {
            content: [
              { accountId, accountName: "Acme", accountSlug: "acme", ownerEmail: "owner@acme.test", active: true },
            ],
            page: 0,
            size: 20,
            totalElements: 1,
            totalPages: 1,
            first: true,
            last: true,
          },
        });
      }
      if (url.includes(`/api/admin/campaigns/${campaignId}/audience`)) {
        return response({
          campaignId,
          snapshotId: "snapshot-1",
          mode: "SEGMENT",
          publicAudience: false,
          immutableAccountCount: 1,
          segmentId,
          segmentActivationId: activationId,
          reviewedByActorUserId: "admin-1",
          evaluatedAt: "2026-08-28T10:00:00Z",
          evidenceExpiresAt: "2026-08-28T10:05:00Z",
          campaignVersion: 4,
          catalogRevision: 8,
          registryVersion: "registry-8",
          audienceFingerprint: "opaque",
          reason: "Rétention",
          startsAt: "2026-08-29T10:00:00Z",
          endsAt: "2026-08-30T10:00:00Z",
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
    const view = renderDetail(`/admin/campaigns/${campaignId}/audience`, [
      adminPermissions.campaignsRead,
      adminPermissions.campaignsReadAudience,
      adminPermissions.campaignsReadAudienceIdentities,
    ]);
    expect(await view.findByText(accountId)).toBeTruthy();
    expect(requests.some((url) => url.includes("audience-identities"))).toBeFalse();
    await userEvent
      .setup({ document: view.container.ownerDocument })
      .click(view.getByRole("button", { name: "Révéler les identités" }));
    expect(await view.findByText("Acme")).toBeTruthy();
    expect(requests.filter((url) => url.includes("audience-identities"))).toHaveLength(1);
  });

  test("an identity-only operator confirms reveal without inheriting opaque Campaign reads", async () => {
    const requests: string[] = [];
    globalThis.fetch = (async (input) => {
      const url = String(input);
      requests.push(url);
      if (url.includes(`/api/admin/campaigns/${campaignId}/audience-identities`)) {
        return response({
          campaignId,
          snapshotId: "snapshot-1",
          immutableAccountCount: 1,
          accounts: {
            content: [{ accountId, accountName: "Acme", accountSlug: "acme", ownerEmail: null, active: true }],
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
    const view = renderDetail(`/admin/campaigns/${campaignId}/audience`, [
      adminPermissions.campaignsReadAudienceIdentities,
    ]);
    expect(await view.findByText("Identités protégées")).toBeTruthy();
    expect(requests.some((url) => url.includes("/audience"))).toBeFalse();
    await userEvent
      .setup({ document: view.container.ownerDocument })
      .click(view.getByRole("button", { name: "Révéler les identités" }));
    expect(await view.findByText("Acme")).toBeTruthy();
    expect(requests.some((url) => url.includes("audience-identities"))).toBeTrue();
    expect(requests.some((url) => url.endsWith(`/campaigns/${campaignId}`))).toBeFalse();
  });

  test("a history-only operator deep-links without fetching Campaign detail", async () => {
    const requests: string[] = [];
    globalThis.fetch = (async (input) => {
      const url = String(input);
      requests.push(url);
      if (url.includes(`/api/admin/campaigns/${campaignId}/history`))
        return response({
          content: [
            {
              id: "event-1",
              action: "SCHEDULE",
              outcome: "SUCCEEDED",
              actorUserId: "admin-1",
              actorEmail: "admin@hiveapp.test",
              reason: "Lancement",
              occurredAt: "2026-08-28T10:00:00Z",
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        });
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;
    const view = renderDetail(`/admin/campaigns/${campaignId}/history`, [adminPermissions.campaignsHistory]);
    expect(await view.findByText("Campagne planifiée")).toBeTruthy();
    expect(requests.some((url) => url.includes(`/campaigns/${campaignId}/history`))).toBeTrue();
    expect(requests.some((url) => url.endsWith(`/campaigns/${campaignId}`))).toBeFalse();
  });

  test("an owner-only operator reassigns from the narrow owner version without reading Campaign detail", async () => {
    const requests: string[] = [];
    const mutationBodies: Array<Record<string, unknown>> = [];
    let version = 11;
    let ownerAdminUserId = "owner-1";
    globalThis.fetch = (async (input, init) => {
      const url = String(input);
      requests.push(url);
      if (url.includes("/api/admin/campaigns/owner-choices/selected")) {
        return response([
          {
            adminUserId: "owner-2",
            email: "next-owner@hiveapp.test",
            username: "next-owner",
            displayName: "Nouvelle responsable",
          },
        ]);
      }
      if (url.includes("/api/admin/campaigns/owner-choices")) {
        return response({
          content: [
            {
              adminUserId: "owner-2",
              email: "next-owner@hiveapp.test",
              username: "next-owner",
              displayName: "Nouvelle responsable",
            },
          ],
          page: 0,
          size: 15,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        });
      }
      if (url.endsWith(`/api/admin/campaigns/${campaignId}/owner`) && init?.method === "PUT") {
        const body = JSON.parse(String(init.body)) as Record<string, unknown>;
        mutationBodies.push(body);
        ownerAdminUserId = String(body.ownerAdminUserId);
        version += 1;
        return response({ campaignId, status: "DRAFT", version });
      }
      if (url.endsWith(`/api/admin/campaigns/${campaignId}/owner`)) {
        return response({
          campaignId,
          adminUserId: ownerAdminUserId,
          userId: "user-1",
          email: ownerAdminUserId === "owner-1" ? "owner@hiveapp.test" : "next-owner@hiveapp.test",
          username: ownerAdminUserId,
          displayName: ownerAdminUserId === "owner-1" ? "Responsable actuelle" : "Nouvelle responsable",
          active: true,
          status: "DRAFT",
          version,
        });
      }
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;

    const view = renderDetail(`/admin/campaigns/${campaignId}/owner`, [
      adminPermissions.campaignsOwner,
      adminPermissions.campaignsReassignOwner,
      adminPermissions.campaignsChooseOwners,
      adminPermissions.campaignsResolveOwnerChoices,
    ]);
    const user = userEvent.setup({ document: view.container.ownerDocument });
    expect(await view.findByText("Responsable actuelle")).toBeTruthy();
    await user.click(view.getByRole("button", { name: "Réassigner" }));
    const dialog = await view.findByRole("dialog");
    await user.click(await within(dialog).findByRole("radio", { name: /Nouvelle responsable/ }));
    await user.type(within(dialog).getByLabelText("Motif obligatoire"), "Rotation opérationnelle");
    await user.click(within(dialog).getByRole("button", { name: "Réassigner" }));
    await waitFor(() => expect(mutationBodies).toHaveLength(1));
    expect(mutationBodies[0]).toMatchObject({
      version: 11,
      ownerAdminUserId: "owner-2",
      reason: "Rotation opérationnelle",
    });
    expect(requests.some((url) => url.endsWith(`/api/admin/campaigns/${campaignId}`))).toBeFalse();
  });

  test("a compare-only operator uses a known revision without listing the lineage or reading detail", async () => {
    const requests: string[] = [];
    globalThis.fetch = (async (input) => {
      const url = String(input);
      requests.push(url);
      if (url.includes(`/api/admin/campaigns/${campaignId}/compare/${comparedId}`)) {
        const source = detail();
        return response({
          sourceCampaignId: campaignId,
          comparedCampaignId: comparedId,
          sameLineage: true,
          directSuccessor: true,
          changedFields: ["name"],
          source,
          compared: {
            ...source,
            summary: { ...source.summary, id: comparedId, name: "Renouvellements v2", revisionNumber: 2 },
          },
        });
      }
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;
    const view = renderDetail(`/admin/campaigns/${campaignId}/revisions?against=${comparedId}`, [
      adminPermissions.campaignsCompare,
    ]);
    expect(await view.findByText("Différences")).toBeTruthy();
    expect(view.getByText("Nom")).toBeTruthy();
    expect(requests.filter((url) => url.includes("/compare/"))).toHaveLength(1);
    expect(requests.some((url) => url.includes(`/campaigns/${campaignId}/revisions`))).toBeFalse();
    expect(requests.some((url) => url.endsWith(`/campaigns/${campaignId}`))).toBeFalse();
  });

  test("hydrates a retained Segment by its exact activation instead of searching the latest", async () => {
    const requests: string[] = [];
    globalThis.fetch = (async (input) => {
      const url = String(input);
      requests.push(url);
      if (url.endsWith(`/api/admin/campaigns/${campaignId}`)) return response(detail());
      if (url.includes("/api/admin/campaigns/segment-choices/selected"))
        return response({
          id: segmentId,
          code: "RENEWAL",
          name: "Renouvellements annuels",
          revisionNumber: 2,
          kind: "TYPED_CRITERIA",
          activationId,
          activationNumber: 7,
          immutableAccountCount: 24,
          state: "AVAILABLE",
        });
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;
    const view = renderEditor([
      adminPermissions.campaignsRead,
      adminPermissions.campaignsUpdate,
      adminPermissions.campaignsResolveSegmentChoices,
    ]);
    const user = userEvent.setup({ document: view.container.ownerDocument });
    await view.findByText("Modifier le brouillon");
    await user.click(view.getByRole("tab", { name: "Audience" }));
    expect(await view.findByText(/Renouvellements annuels/)).toBeTruthy();
    await waitFor(() =>
      expect(
        requests.some((url) => url.includes(`segmentId=${segmentId}`) && url.includes(`activationId=${activationId}`)),
      ).toBeTrue(),
    );
    expect(requests.some((url) => /segment-choices\?(?!.*selected)/.test(url))).toBeFalse();
  });
});
