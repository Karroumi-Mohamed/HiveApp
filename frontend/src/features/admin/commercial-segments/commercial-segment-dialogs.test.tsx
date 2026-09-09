import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { Window } from "happy-dom";

const browser = new Window({ url: "http://localhost:3000/admin/segments/segment-1" });
for (const key of [
  "window",
  "document",
  "navigator",
  "sessionStorage",
  "HTMLElement",
  "HTMLInputElement",
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
const { MemoryRouter } = await import("react-router");
const { adminPermissions } = await import("@/auth/permissions");
const { AdminSessionProvider } = await import("@/auth/session-provider");
const { clearSession, writeSession } = await import("@/auth/session-store");
const { CommercialSegmentActivationDialog } = await import("./admin-commercial-segment-detail-page");

import type { CommercialSegmentDetail, CommercialSegmentPreview } from "@/api/contracts";

const now = "2026-08-27T00:00:00Z";
const segment: CommercialSegmentDetail = {
  summary: {
    id: "segment-1",
    code: "SEGMENT-1",
    name: "Renouvellements prioritaires",
    status: "DRAFT",
    kind: "EXPLICIT_ACCOUNTS",
    configuredAccountCount: 1,
    latestActivationAccountCount: null,
    lineageId: "lineage-1",
    revisionNumber: 1,
    creationReason: "CREATED",
    version: 4,
    createdAt: now,
    updatedAt: now,
    availableActions: ["ACTIVATE"],
    blockedActions: {},
    ownerIdentityRestricted: true,
    audienceIdentityRestricted: true,
  },
  description: null,
  reason: "Renouvellement",
  definition: { explicitAccountIds: ["account-1"], criteria: null },
  sourceSegmentId: null,
};

function preview(): CommercialSegmentPreview {
  return {
    segmentId: segment.summary.id,
    criteriaVersion: segment.summary.version,
    evaluatedAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 60_000).toISOString(),
    previewToken: "signed.segment.preview",
    totalAccounts: 1,
    activationAccountLimit: 10_000,
    activatable: true,
    blockers: [],
    sample: [{ accountId: "account-1" }],
  };
}

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function renderDialog() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  queryClient.setQueryData(["admin", "me", "admin-token"], {
    id: "admin-1",
    email: "admin@hiveapp.test",
    emailVerified: true,
    isSuperAdmin: false,
    isActive: true,
    permissions: [adminPermissions.segmentsPreview, adminPermissions.segmentsActivate],
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <AdminSessionProvider>
        <MemoryRouter>
          <CommercialSegmentActivationDialog segment={segment} />
        </MemoryRouter>
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

describe("signed commercial segment activation", () => {
  test("does not mint replacement evidence after a failed request closes", async () => {
    let previewCalls = 0;
    let activationCalls = 0;
    let finishActivation: ((response: Response) => void) | undefined;
    globalThis.fetch = (async (input) => {
      const url = String(input);
      if (url.endsWith("/api/admin/segments/segment-1/preview")) {
        previewCalls += 1;
        return response(preview());
      }
      if (url.endsWith("/api/admin/segments/segment-1/activate")) {
        activationCalls += 1;
        return new Promise<Response>((resolve) => {
          finishActivation = resolve;
        });
      }
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;

    const view = renderDialog();
    const user = userEvent.setup({ document: view.container.ownerDocument });
    await user.click(view.getByRole("button", { name: "Vérifier et activer" }));
    await view.findByText("1");
    await user.type(view.getByLabelText("Motif obligatoire"), "Campagne approuvée");
    await user.click(view.getByRole("button", { name: "Activer" }));
    await waitFor(() => expect(activationCalls).toBe(1));
    await user.click(view.getByRole("button", { name: "Annuler" }));
    finishActivation?.(response({ code: "STALE_ACTIVATION_PREVIEW", message: "Stale" }, 409));

    await waitFor(() => expect(view.queryByRole("dialog")).toBeNull());
    await new Promise((resolve) => setTimeout(resolve, 10));
    expect(previewCalls).toBe(1);
  });
});
