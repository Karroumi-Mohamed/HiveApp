import { afterEach, beforeEach, describe, expect, spyOn, test } from "bun:test";
import { Window } from "happy-dom";

const browser = new Window({ url: "http://localhost:3000/admin/commercial-policies/policy-1" });
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
const { toast } = await import("sonner");
const { adminPermissions } = await import("@/auth/permissions");
const { AdminSessionProvider } = await import("@/auth/session-provider");
const { clearSession, writeSession } = await import("@/auth/session-store");
const { Button } = await import("@/components/ui/button");
const { CommercialPolicyActivationDialog } = await import("./commercial-policy-dialogs");

import type { CommercialPolicyActivationPreview, CommercialPolicyDetail } from "@/api/contracts";

const now = "2026-08-01T00:00:00Z";
const policy: CommercialPolicyDetail = {
  summary: {
    id: "policy-1",
    code: "POLICY-1",
    name: "Conditions négociées",
    status: "DRAFT",
    targetKind: "ACCOUNT",
    targetLabel: "Acme",
    configuredTargetCount: 1,
    effectCount: 1,
    latestAffectedAccountCount: null,
    source: "CONTRACT",
    priority: 20,
    effectiveFrom: now,
    effectiveUntil: null,
    lineageId: "lineage-1",
    revisionNumber: 1,
    creationReason: "CREATED",
    version: 4,
    createdAt: now,
    updatedAt: now,
    availableActions: ["PREVIEW_ACTIVATION", "ACTIVATE"],
    blockers: [],
    ownerIdentityRestricted: true,
    executionSupported: true,
    executionBlockers: ["SCHEDULED_EXECUTION_NOT_AVAILABLE"],
  },
  description: null,
  reason: "Contrat signé",
  approvalReference: null,
  contractReference: null,
  target: {
    kind: "ACCOUNT",
    accountId: "account-1",
    accountName: "Acme",
    accountIds: [],
    planRevisionId: null,
    planCode: null,
    planName: null,
    segmentReference: null,
  },
  effects: [],
  sourcePolicyId: null,
};

function activationPreview(token: string): CommercialPolicyActivationPreview {
  return {
    policyId: policy.summary.id,
    expectedVersion: policy.summary.version,
    catalogRevision: 8,
    registryVersion: "registry-v2",
    evaluatedAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 60_000).toISOString(),
    previewToken: token,
    activatable: true,
    blockers: [],
    affectedAccountCount: 1,
    sampleAccounts: [{ id: "account-1", name: "Acme", slug: "acme", active: true }],
    policyRevisionToEnd: null,
    executionSupported: true,
    executionBlockers: ["SCHEDULED_EXECUTION_NOT_AVAILABLE"],
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
    permissions: [adminPermissions.commercialPoliciesPreviewActivation, adminPermissions.commercialPoliciesActivate],
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <AdminSessionProvider>
        <MemoryRouter>
          <CommercialPolicyActivationDialog policy={policy} trigger={<Button>Vérifier</Button>} />
        </MemoryRouter>
      </AdminSessionProvider>
    </QueryClientProvider>,
  );
}

let toastError: ReturnType<typeof spyOn>;

beforeEach(() => {
  cleanup();
  clearSession("admin");
  writeSession("admin", {
    accessToken: "admin-token",
    refreshToken: null,
    expiresAt: Date.now() + 600_000,
    passwordChangeRequired: false,
  });
  toastError = spyOn(toast, "error").mockImplementation(() => "toast-id");
});

afterEach(() => {
  cleanup();
  clearSession("admin");
  toastError.mockRestore();
  globalThis.fetch = originalFetch;
});

describe("signed commercial-policy activation", () => {
  test("shows the execution boundary and replaces rejected evidence before a retry", async () => {
    let previewCalls = 0;
    const bodies: Array<Record<string, unknown>> = [];
    globalThis.fetch = (async (input, init) => {
      const url = String(input);
      if (url.endsWith("/api/admin/commercial-policies/policy-1/activation-preview")) {
        previewCalls += 1;
        return response(activationPreview(previewCalls === 1 ? "old.signed.token" : "fresh.signed.token"));
      }
      if (url.endsWith("/api/admin/commercial-policies/policy-1/activate")) {
        bodies.push(JSON.parse(String(init?.body)) as Record<string, unknown>);
        return bodies.length === 1
          ? response({ code: "STALE_ACTIVATION_PREVIEW", message: "Stale" }, 409)
          : response({ ...policy, summary: { ...policy.summary, status: "ACTIVE", version: 5 } });
      }
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;

    const view = renderDialog();
    const user = userEvent.setup({ document: view.container.ownerDocument });
    await user.click(view.getByRole("button", { name: "Vérifier" }));
    expect(await view.findByText("L’exécution planifiée des effets n’est pas encore disponible.")).toBeTruthy();
    expect(view.getByText(/La politique est évaluée pendant une opération d’abonnement/)).toBeTruthy();

    const confirm = view.getByRole("button", { name: "Activer la définition" });
    expect(confirm.hasAttribute("disabled")).toBeTrue();
    await user.type(view.getByLabelText("Motif de l’activation"), "  Contrat approuvé  ");
    await user.click(confirm);

    await waitFor(() => expect(previewCalls).toBe(2));
    await waitFor(() => expect(toastError).toHaveBeenCalled());
    expect(bodies[0]).toEqual({
      version: 4,
      reason: "Contrat approuvé",
      activationPreviewToken: "old.signed.token",
    });

    await waitFor(() => expect(confirm.hasAttribute("disabled")).toBeFalse());
    await user.click(confirm);
    await waitFor(() => expect(bodies).toHaveLength(2));
    expect(bodies[1]?.activationPreviewToken).toBe("fresh.signed.token");
  });

  test("discards signed evidence when the dialog closes", async () => {
    let previewCalls = 0;
    const bodies: Array<Record<string, unknown>> = [];
    globalThis.fetch = (async (input, init) => {
      const url = String(input);
      if (url.endsWith("/api/admin/commercial-policies/policy-1/activation-preview")) {
        previewCalls += 1;
        return response(activationPreview(`signed.token.${previewCalls}`));
      }
      if (url.endsWith("/api/admin/commercial-policies/policy-1/activate")) {
        bodies.push(JSON.parse(String(init?.body)) as Record<string, unknown>);
        return response({ ...policy, summary: { ...policy.summary, status: "ACTIVE", version: 5 } });
      }
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;

    const view = renderDialog();
    const user = userEvent.setup({ document: view.container.ownerDocument });
    await user.click(view.getByRole("button", { name: "Vérifier" }));
    await waitFor(() => expect(previewCalls).toBe(1));
    await user.click(view.getByRole("button", { name: "Annuler" }));
    await user.click(view.getByRole("button", { name: "Vérifier" }));
    await waitFor(() => expect(previewCalls).toBe(2));
    await user.type(view.getByLabelText("Motif de l’activation"), "Nouvelle vérification");
    await user.click(view.getByRole("button", { name: "Activer la définition" }));
    await waitFor(() => expect(bodies).toHaveLength(1));
    expect(bodies[0]?.activationPreviewToken).toBe("signed.token.2");
  });

  test("does not mint replacement evidence after the dialog closes", async () => {
    let previewCalls = 0;
    let finishActivation: ((response: Response) => void) | undefined;
    globalThis.fetch = (async (input) => {
      const url = String(input);
      if (url.endsWith("/api/admin/commercial-policies/policy-1/activation-preview")) {
        previewCalls += 1;
        return response(activationPreview("one.use.token"));
      }
      if (url.endsWith("/api/admin/commercial-policies/policy-1/activate")) {
        return await new Promise<Response>((resolve) => {
          finishActivation = resolve;
        });
      }
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;

    const view = renderDialog();
    const user = userEvent.setup({ document: view.container.ownerDocument });
    await user.click(view.getByRole("button", { name: "Vérifier" }));
    await view.findByText("Preuve actuelle");
    await user.type(view.getByLabelText("Motif de l’activation"), "Approbation");
    await user.click(view.getByRole("button", { name: "Activer la définition" }));
    await waitFor(() => expect(finishActivation).toBeDefined());
    await user.click(view.getByRole("button", { name: "Annuler" }));
    finishActivation?.(response({ code: "STALE_ACTIVATION_PREVIEW", message: "Stale" }, 409));
    await waitFor(() => expect(toastError).toHaveBeenCalled());
    expect(previewCalls).toBe(1);
  });
});
