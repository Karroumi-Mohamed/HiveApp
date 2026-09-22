import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { Window } from "happy-dom";
import type { ReactNode } from "react";
import type { AddOn, Plan, PlanActivationPreview, PlanDeletionPreview } from "@/api/contracts";

const browser = new Window({ url: "http://localhost:3000/admin/plans/plan-1" });
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
const { MemoryRouter } = await import("react-router");
const { adminPermissions } = await import("@/auth/permissions");
const { AdminSessionProvider } = await import("@/auth/session-provider");
const { clearSession, writeSession } = await import("@/auth/session-store");
const { Button } = await import("@/components/ui/button");
const { AddOnFeatureDialog } = await import("./admin-add-on-page");
const { QuotaForm } = await import("./admin-quota-package-page");
const { CommercialLifecycleDialog } = await import("./commercial-lifecycle-dialog");
const { DeletePlanDialog } = await import("@/features/admin/plans/admin-plans-page");

const plan: Plan = {
  id: "plan-1",
  code: "PRO",
  name: "Pro",
  description: null,
  price: "100.0000",
  currencyCode: "MAD",
  billingCycle: "MONTHLY",
  status: "DRAFT",
  lineageId: "lineage-1",
  revisionNumber: 1,
  sourcePlanId: null,
  creationReason: "CREATED",
  extensionPolicy: "OPEN_COMPATIBLE",
  salesVisibility: "PUBLIC",
  version: 4,
};

const addOn: AddOn = {
  id: "add-on-1",
  code: "CUSTOM_ROLES",
  name: "Custom Roles",
  description: null,
  price: "10.0000",
  currencyCode: "MAD",
  billingCycle: "MONTHLY",
  status: "DRAFT",
  definitionVersion: 1,
  lineageId: "add-on-lineage-1",
  revisionNumber: 1,
  sourceAddOnId: null,
  creationReason: "CREATED",
  allowedPlanCodes: [],
  blockedPlanCodes: [],
  dependencyCodes: [],
  exclusionCodes: [],
  features: [],
  salesVisibility: "PUBLIC",
  version: 1,
};

function activationPreview(token: string, expiresAt: string): PlanActivationPreview {
  return {
    planId: plan.id,
    expectedVersion: plan.version,
    catalogRevision: 7,
    registryVersion: "registry-v1",
    evaluatedAt: new Date().toISOString(),
    expiresAt,
    previewToken: token,
    activatable: true,
    blockers: [],
    includedFeatureCount: 2,
    optionalAddOnFeatureCount: 1,
    blockedFeatureCount: 0,
    reviewedPrices: [],
  };
}

function deletionPreview(token: string): PlanDeletionPreview {
  return {
    planId: plan.id,
    planName: plan.name,
    expectedVersion: plan.version,
    catalogRevision: 7,
    registryVersion: "registry-v1",
    evaluatedAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 60_000).toISOString(),
    previewToken: token,
    deletable: true,
    ownedFeatureCount: 0,
    subscriptionHistoryCount: 0,
    changeOperationReferenceCount: 0,
    addOnReferenceCount: 0,
    quotaPackageReferenceCount: 0,
    lineageReferenceCount: 0,
    blockers: [],
  };
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function renderAdmin(element: ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Number.POSITIVE_INFINITY }, mutations: { retry: false } },
  });
  queryClient.setQueryData(["admin", "me", "admin-token"], {
    id: "admin-1",
    email: "admin@hiveapp.test",
    emailVerified: true,
    isSuperAdmin: false,
    isActive: true,
    permissions: [
      adminPermissions.plansDelete,
      adminPermissions.plansPreviewDelete,
      adminPermissions.plansPreviewActivation,
      adminPermissions.plansTransition,
      adminPermissions.plansChoose,
      adminPermissions.addOnsChoose,
      adminPermissions.addOnsAssignFeature,
      adminPermissions.quotaPackagesCreate,
      adminPermissions.registryFeatureCatalog,
    ],
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <AdminSessionProvider>
        <MemoryRouter>{element}</MemoryRouter>
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

describe("commercial dialog open paths", () => {
  test("an expired activation review stays disabled and exposes a fresh review path", async () => {
    let previewCalls = 0;
    globalThis.fetch = (async (input) => {
      const url = new URL(String(input));
      if (url.pathname !== "/api/admin/plans/plan-1/activation-preview") {
        throw new Error(`Unexpected request: ${url}`);
      }
      previewCalls += 1;
      return jsonResponse(
        activationPreview(
          previewCalls === 1 ? "expired.token" : "fresh.token",
          new Date(Date.now() + (previewCalls === 1 ? -1_000 : 60_000)).toISOString(),
        ),
      );
    }) as typeof fetch;

    const view = renderAdmin(
      <CommercialLifecycleDialog
        action="ACTIVATE"
        kind="plan"
        onChanged={() => undefined}
        product={plan}
        trigger={<Button>Activer</Button>}
      />,
    );
    const user = userEvent.setup({ document: view.container.ownerDocument });
    await user.click(view.getByRole("button", { name: "Activer" }));
    expect(await view.findByRole("heading", { name: "Mettre cette version en vente ?" })).toBeTruthy();
    await view.findByText("La vérification a expiré ou ne correspond plus à cette version.");

    await user.type(view.getByLabelText("Motif"), "Validation commerciale");
    expect(view.getByRole("button", { name: "Activer" }).hasAttribute("disabled")).toBeTrue();
    await user.click(view.getByRole("button", { name: "Recalculer l’activation" }));

    await view.findByText("Prête à être activée");
    expect(previewCalls).toBe(2);
    expect(view.getByRole("button", { name: "Activer" }).hasAttribute("disabled")).toBeFalse();
  });

  test("a failed deletion review renders retry and never submits fallback evidence", async () => {
    const token = "delete.signed.token";
    let previewCalls = 0;
    const deletionBodies: Array<Record<string, unknown>> = [];
    globalThis.fetch = (async (input, init) => {
      const url = new URL(String(input));
      if (url.pathname === "/api/admin/plans/plan-1/deletion-preview") {
        previewCalls += 1;
        return previewCalls === 1
          ? jsonResponse({ code: "HTTP_ERROR", message: "Preview unavailable" }, 500)
          : jsonResponse(deletionPreview(token));
      }
      if (url.pathname === "/api/admin/plans/plan-1" && init?.method === "DELETE") {
        deletionBodies.push(JSON.parse(String(init.body)) as Record<string, unknown>);
        return new Response(null, { status: 204 });
      }
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;

    const view = renderAdmin(<DeletePlanDialog plan={plan} />);
    const user = userEvent.setup({ document: view.container.ownerDocument });
    await user.click(view.getByRole("button", { name: "Supprimer" }));
    expect(await view.findByRole("heading", { name: "Supprimer Pro" })).toBeTruthy();
    expect(await view.findByText("Vérification de suppression indisponible")).toBeTruthy();
    expect(deletionBodies).toHaveLength(0);

    await user.click(view.getByRole("button", { name: "Réessayer" }));
    await view.findByText("Historique");
    await user.type(view.getByLabelText("Saisissez Pro"), "Pro");
    await user.click(view.getByRole("button", { name: "Confirmer la suppression" }));

    await waitFor(() => expect(deletionBodies).toHaveLength(1));
    expect(deletionBodies[0]).toEqual({
      confirmationName: "Pro",
      expectedVersion: plan.version,
      previewToken: token,
    });
    expect(browser.location.href).not.toContain(token);
  });

  test("an add-on feature editor reports a failed catalogue instead of presenting an empty chooser", async () => {
    let catalogCalls = 0;
    globalThis.fetch = (async (input) => {
      const url = new URL(String(input));
      if (url.pathname !== "/api/admin/registry/feature-catalog") throw new Error(`Unexpected request: ${url}`);
      catalogCalls += 1;
      return catalogCalls === 1
        ? jsonResponse({ code: "HTTP_ERROR", message: "Catalogue unavailable" }, 500)
        : jsonResponse([]);
    }) as typeof fetch;

    const view = renderAdmin(<AddOnFeatureDialog addOn={addOn} />);
    const user = userEvent.setup({ document: view.container.ownerDocument });
    await user.click(view.getByRole("button", { name: "Ajouter" }));

    expect(await view.findByText("Les choix n’ont pas pu être chargés.")).toBeTruthy();
    expect(view.getByRole("button", { name: "Enregistrer" }).hasAttribute("disabled")).toBeTrue();
    await user.click(view.getByRole("button", { name: "Réessayer" }));
    await waitFor(() => expect(catalogCalls).toBe(2));
    expect(view.queryByText("Les choix n’ont pas pu être chargés.")).toBeNull();
  });

  test("a quota form reports a failed catalogue and cannot submit without a resolved resource", async () => {
    let catalogCalls = 0;
    const emptyPage = {
      content: [],
      page: 0,
      size: 50,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    };
    globalThis.fetch = (async (input) => {
      const url = new URL(String(input));
      if (url.pathname === "/api/admin/plans/chooser" || url.pathname === "/api/admin/add-ons/chooser") {
        return jsonResponse(emptyPage);
      }
      if (url.pathname === "/api/admin/registry/feature-catalog") {
        catalogCalls += 1;
        return jsonResponse({ code: "HTTP_ERROR", message: "Catalogue unavailable" }, 500);
      }
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;

    const view = renderAdmin(<QuotaForm trigger={<Button>Créer un pack</Button>} />);
    const user = userEvent.setup({ document: view.container.ownerDocument });
    await user.click(view.getByRole("button", { name: "Créer un pack" }));

    expect(await view.findByText("Les choix n’ont pas pu être chargés.")).toBeTruthy();
    expect(catalogCalls).toBe(1);
    expect(view.getByRole("button", { name: "Enregistrer" }).hasAttribute("disabled")).toBeTrue();
  });
});
