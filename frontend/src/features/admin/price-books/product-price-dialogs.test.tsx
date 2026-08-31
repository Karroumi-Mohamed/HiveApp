import { afterEach, beforeEach, describe, expect, spyOn, test } from "bun:test";
import { Window } from "happy-dom";

const browser = new Window({ url: "http://localhost:3000/admin/price-books/price-1" });
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
const { ProductPriceActivationDialog } = await import("./product-price-dialogs");
const { ProductPriceActionButton } = await import("./product-price-action-button");

import type { ProductPrice, ProductPriceActivationPreview } from "@/api/contracts";

const price: ProductPrice = {
  id: "price-1",
  productType: "PLAN",
  productId: "plan-1",
  productCode: "PRO",
  productName: "Pro",
  amount: "100.0000",
  currencyCode: "MAD",
  billingCycle: "MONTHLY",
  status: "INACTIVE",
  effectiveFrom: "2026-08-01T00:00:00Z",
  effectiveUntil: null,
  lineageId: "lineage-1",
  revisionNumber: 1,
  sourcePriceId: null,
  compatibilityDefault: false,
  version: 4,
  createdAt: "2026-08-01T00:00:00Z",
  updatedAt: "2026-08-01T00:00:00Z",
  availableActions: ["PREVIEW_ACTIVATION", "REACTIVATE"],
  blockers: [],
};

function activationPreview(token: string): ProductPriceActivationPreview {
  return {
    priceEntryId: price.id,
    expectedVersion: price.version,
    catalogRevision: 7,
    registryVersion: "registry-v1",
    evaluatedAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 60_000).toISOString(),
    previewToken: token,
    activatable: true,
    blockers: [],
  };
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function renderDialog(trigger = <Button>Ouvrir la vérification</Button>) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Number.POSITIVE_INFINITY }, mutations: { retry: false } },
  });
  queryClient.setQueryData(["admin", "me", "admin-token"], {
    id: "admin-1",
    email: "admin@hiveapp.test",
    emailVerified: true,
    isSuperAdmin: false,
    isActive: true,
    permissions: [adminPermissions.priceBooksPreviewActivation, adminPermissions.priceBooksReactivate],
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <AdminSessionProvider>
        <MemoryRouter>
          <ProductPriceActivationDialog price={price} trigger={trigger} />
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

describe("signed product-price reactivation", () => {
  test("the permission-aware action forwards dialog trigger events", async () => {
    globalThis.fetch = (async (input) => {
      const url = String(input);
      if (url.endsWith("/api/admin/product-prices/price-1/activation-preview")) {
        return jsonResponse(activationPreview("forwarded.trigger.token"));
      }
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;

    const view = renderDialog(
      <ProductPriceActionButton action="PREVIEW_ACTIVATION" price={price}>
        Vérifier et mettre en vente
      </ProductPriceActionButton>,
    );
    const user = userEvent.setup({ document: view.container.ownerDocument });
    await user.click(view.getByRole("button", { name: "Vérifier et mettre en vente" }));

    expect(await view.findByRole("heading", { name: "Remettre ce tarif en vente ?" })).toBeTruthy();
  });

  test("cannot submit before review and recovers from rejected evidence with a fresh token", async () => {
    const firstToken = "tampered.secret.token";
    const freshToken = "fresh.signed.token";
    let releaseFirstPreview: ((response: Response) => void) | undefined;
    const firstPreview = new Promise<Response>((resolve) => {
      releaseFirstPreview = resolve;
    });
    let previewCalls = 0;
    const activationBodies: Array<Record<string, unknown>> = [];
    const requestedUrls: string[] = [];
    globalThis.fetch = (async (input, init) => {
      const url = String(input);
      requestedUrls.push(url);
      if (url.endsWith("/api/admin/product-prices/price-1/activation-preview")) {
        previewCalls += 1;
        return previewCalls === 1 ? firstPreview : jsonResponse(activationPreview(freshToken));
      }
      if (url.endsWith("/api/admin/product-prices/price-1/reactivate")) {
        activationBodies.push(JSON.parse(String(init?.body)) as Record<string, unknown>);
        if (activationBodies.length === 1) {
          return jsonResponse(
            {
              code: "STALE_ACTIVATION_PREVIEW",
              message: `Rejected ${firstToken}`,
            },
            409,
          );
        }
        return jsonResponse({ ...price, status: "ACTIVE", version: 5 });
      }
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;

    const view = renderDialog();
    const user = userEvent.setup({ document: view.container.ownerDocument });
    await user.click(view.getByRole("button", { name: "Ouvrir la vérification" }));
    const confirm = view.getByRole("button", { name: "Remettre en vente" });
    expect(confirm.hasAttribute("disabled")).toBeTrue();

    releaseFirstPreview?.(jsonResponse(activationPreview(firstToken)));
    await view.findByText("Aucun chevauchement : ce tarif peut être mis en vente.");
    await user.type(view.getByLabelText("Motif de l’opération"), "  Reprise validée  ");
    expect(confirm.hasAttribute("disabled")).toBeFalse();

    await user.click(confirm);
    await waitFor(() => expect(previewCalls).toBe(2));
    await waitFor(() => expect(toastError).toHaveBeenCalled());
    expect(activationBodies[0]).toEqual({
      version: 4,
      reason: "Reprise validée",
      activationPreviewToken: firstToken,
    });
    expect(String(toastError.mock.calls.at(-1)?.[0])).not.toContain(firstToken);

    await waitFor(() => expect(confirm.hasAttribute("disabled")).toBeFalse());
    await user.click(confirm);
    await waitFor(() => expect(activationBodies).toHaveLength(2));
    expect(activationBodies[1]).toEqual({
      version: 4,
      reason: "Reprise validée",
      activationPreviewToken: freshToken,
    });
    expect(requestedUrls.every((url) => !url.includes(firstToken) && !url.includes(freshToken))).toBeTrue();
    expect(browser.location.href).not.toContain("token");
  });

  test("a failed evidence refresh cannot re-enable the retained rejected token", async () => {
    const rejectedToken = "rejected.signed.token";
    let previewCalls = 0;
    let activationCalls = 0;
    globalThis.fetch = (async (input) => {
      const url = String(input);
      if (url.endsWith("/api/admin/product-prices/price-1/activation-preview")) {
        previewCalls += 1;
        return previewCalls === 1
          ? jsonResponse(activationPreview(rejectedToken))
          : jsonResponse({ code: "HTTP_ERROR", message: "Preview unavailable" }, 500);
      }
      if (url.endsWith("/api/admin/product-prices/price-1/reactivate")) {
        activationCalls += 1;
        return jsonResponse({ code: "STALE_ACTIVATION_PREVIEW", message: "Stale" }, 409);
      }
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;

    const view = renderDialog();
    const user = userEvent.setup({ document: view.container.ownerDocument });
    await user.click(view.getByRole("button", { name: "Ouvrir la vérification" }));
    await view.findByText("Aucun chevauchement : ce tarif peut être mis en vente.");
    await user.type(view.getByLabelText("Motif de l’opération"), "Reprise validée");
    const confirm = view.getByRole("button", { name: "Remettre en vente" });
    await user.click(confirm);

    await view.findByText("La prévisualisation n’a pas pu être chargée.");
    expect(confirm.hasAttribute("disabled")).toBeTrue();
    await user.click(confirm);
    expect(activationCalls).toBe(1);
  });

  test("closing the dialog discards reviewed evidence instead of reusing its cache", async () => {
    let previewCalls = 0;
    let releaseSecondPreview: ((response: Response) => void) | undefined;
    const secondPreview = new Promise<Response>((resolve) => {
      releaseSecondPreview = resolve;
    });
    globalThis.fetch = (async (input) => {
      const url = String(input);
      if (!url.endsWith("/api/admin/product-prices/price-1/activation-preview")) {
        throw new Error(`Unexpected request: ${url}`);
      }
      previewCalls += 1;
      return previewCalls === 1 ? jsonResponse(activationPreview("first.token")) : secondPreview;
    }) as typeof fetch;

    const view = renderDialog();
    const user = userEvent.setup({ document: view.container.ownerDocument });
    await user.click(view.getByRole("button", { name: "Ouvrir la vérification" }));
    await view.findByText("Aucun chevauchement : ce tarif peut être mis en vente.");
    await user.click(view.getByRole("button", { name: "Annuler" }));
    await user.click(view.getByRole("button", { name: "Ouvrir la vérification" }));

    await waitFor(() => expect(previewCalls).toBe(2));
    expect(view.getByRole("button", { name: "Remettre en vente" }).hasAttribute("disabled")).toBeTrue();
    releaseSecondPreview?.(jsonResponse(activationPreview("second.token")));
    await view.findByText("Aucun chevauchement : ce tarif peut être mis en vente.");
  });
});
