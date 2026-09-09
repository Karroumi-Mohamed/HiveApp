import { afterEach, beforeEach, expect, test } from "bun:test";
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
const { cleanup, fireEvent, render, waitFor } = await import("@testing-library/react");
const userEvent = (await import("@testing-library/user-event")).default;
const { QueryClient, QueryClientProvider } = await import("@tanstack/react-query");
const { MemoryRouter } = await import("react-router");
const { adminPermissions } = await import("@/auth/permissions");
const { AdminSessionProvider } = await import("@/auth/session-provider");
const { clearSession, writeSession } = await import("@/auth/session-store");
const { Button } = await import("@/components/ui/button");
const { ProductPriceChangeDialog, priceChangeReviewReady, tariffChangeAmount } = await import(
  "./product-price-change-dialog"
);
const { ProductPriceContinuityPanel } = await import("./product-price-continuity-panel");
const { productPriceDisplayStatus } = await import("./product-price-rules");

import type { ProductPrice, ProductPriceChangePreview, ProductPriceChangeRequest } from "@/api/contracts";

const price: ProductPrice = {
  id: "price-1",
  productType: "PLAN",
  productId: "plan-1",
  productCode: "PRO",
  productName: "Pro",
  amount: "100.00",
  currencyCode: "MAD",
  billingCycle: "MONTHLY",
  status: "ACTIVE",
  effectiveFrom: "2026-01-01T00:00:00Z",
  effectiveUntil: null,
  lineageId: "lineage-1",
  revisionNumber: 1,
  sourcePriceId: null,
  compatibilityDefault: false,
  version: 4,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
  availableActions: ["CHANGE_PRICE", "RESCHEDULE_CHANGE", "CANCEL_CHANGE"],
  blockers: [],
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
function review(change: ProductPriceChangeRequest, allowed = true): ProductPriceChangePreview {
  return {
    change,
    currentPrice: price,
    scheduledPrice: null,
    evaluatedAt: new Date().toISOString(),
    cutoff: change.effectiveFrom ?? new Date().toISOString(),
    expiresAt: new Date(Date.now() + 60_000).toISOString(),
    previewToken: "signed-review",
    allowed,
    blockingOfferCount: allowed ? 0 : 1,
    blockers: allowed ? [] : ["PUBLISHED_OFFERS_DEPEND_ON_PRICE"],
  };
}
function mount(operation: ProductPriceChangeRequest["operation"] = "CHANGE", allowed = true, panel = false) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity }, mutations: { retry: false } },
  });
  client.setQueryData(["admin", "me", "test-token"], {
    id: "admin",
    email: "admin@hiveapp.test",
    emailVerified: true,
    isSuperAdmin: false,
    isActive: true,
    permissions: allowed
      ? [
          adminPermissions.priceBooksList,
          adminPermissions.priceBooksRead,
          adminPermissions.priceBooksPreviewChange,
          adminPermissions.priceBooksChange,
          adminPermissions.priceBooksRescheduleChange,
          adminPermissions.priceBooksCancelChange,
        ]
      : [],
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <AdminSessionProvider>
          {panel ? (
            <ProductPriceContinuityPanel
              price={{ ...price, effectiveUntil: new Date(Date.now() + 3600_000).toISOString() }}
            />
          ) : (
            <ProductPriceChangeDialog price={price} operation={operation} trigger={<Button>Ouvrir</Button>} />
          )}
        </AdminSessionProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
beforeEach(() => {
  cleanup();
  clearSession("admin");
  writeSession("admin", {
    accessToken: "test-token",
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

test("requires a fresh review and invalidates it when fields change", async () => {
  let calls = 0;
  globalThis.fetch = (async (_input, init) => {
    calls++;
    return json(review(JSON.parse(String(init?.body))));
  }) as typeof fetch;
  const view = mount();
  const user = userEvent.setup({ document: view.container.ownerDocument });
  await user.click(view.getByRole("button", { name: "Ouvrir" }));
  expect((view.getByRole("button", { name: "Confirmer" }) as HTMLButtonElement).disabled).toBe(true);
  await user.type(view.getByLabelText("Motif"), "Commercial adjustment");
  await user.click(view.getByRole("button", { name: "Vérifier" }));
  await waitFor(() =>
    expect((view.getByRole("button", { name: "Confirmer" }) as HTMLButtonElement).disabled).toBe(false),
  );
  await user.type(view.getByLabelText("Nouveau montant (MAD)"), "1");
  expect((view.getByRole("button", { name: "Confirmer" }) as HTMLButtonElement).disabled).toBe(true);
  expect(calls).toBe(1);
});

test("network uncertainty retries the identical reviewed intent and idempotency key", async () => {
  const bodies: unknown[] = [];
  globalThis.fetch = (async (input, init) => {
    const body = JSON.parse(String(init?.body));
    if (String(input).endsWith("change-preview")) return json(review(body));
    bodies.push(body);
    if (bodies.length === 1) throw new TypeError("Network disconnected");
    return json({
      previousPrice: price,
      successorPrice: { ...price, id: "next" },
      cutoff: new Date().toISOString(),
      existingResult: true,
    });
  }) as typeof fetch;
  const view = mount();
  const user = userEvent.setup({ document: view.container.ownerDocument });
  await user.click(view.getByRole("button", { name: "Ouvrir" }));
  await user.type(view.getByLabelText("Motif"), "Price adjustment");
  await user.click(view.getByRole("button", { name: "Vérifier" }));
  await waitFor(() =>
    expect((view.getByRole("button", { name: "Confirmer" }) as HTMLButtonElement).disabled).toBe(false),
  );
  await user.click(view.getByRole("button", { name: "Confirmer" }));
  await user.click(await view.findByRole("button", { name: "Réessayer la même demande" }));
  await waitFor(() => expect(bodies).toHaveLength(2));
  expect(bodies[1]).toEqual(bodies[0]);
});

test("published offer blockers are visible and prevent confirmation", async () => {
  globalThis.fetch = (async (_input, init) => json(review(JSON.parse(String(init?.body)), false))) as typeof fetch;
  const view = mount();
  const user = userEvent.setup({ document: view.container.ownerDocument });
  await user.click(view.getByRole("button", { name: "Ouvrir" }));
  await user.type(view.getByLabelText("Motif"), "New price");
  await user.click(view.getByRole("button", { name: "Vérifier" }));
  expect(await view.findByText("Changement indisponible")).toBeTruthy();
  expect(view.getByText("1 offre(s) publiée(s) concernée(s).")).toBeTruthy();
  expect((view.getByRole("button", { name: "Confirmer" }) as HTMLButtonElement).disabled).toBe(true);
});

test("cancellation sends no new pricing terms", async () => {
  let intent: ProductPriceChangeRequest | undefined;
  globalThis.fetch = (async (_input, init) => {
    intent = JSON.parse(String(init?.body));
    if (!intent) throw new Error("Expected cancellation intent");
    return json(review(intent));
  }) as typeof fetch;
  const view = mount("CANCEL");
  const user = userEvent.setup({ document: view.container.ownerDocument });
  await user.click(view.getByRole("button", { name: "Ouvrir" }));
  expect(view.queryByLabelText("Nouveau montant (MAD)")).toBeNull();
  await user.type(view.getByLabelText("Motif"), "Cancel scheduled change");
  await user.click(view.getByRole("button", { name: "Vérifier" }));
  await waitFor(() => expect(intent?.operation).toBe("CANCEL"));
  expect(intent?.amount).toBeNull();
  expect(intent?.timing).toBeNull();
  expect(intent?.effectiveFrom).toBeNull();
  expect(await view.findByText("Tarif conservé")).toBeTruthy();
  expect(view.getByText(/Le changement programmé est annulé/)).toBeTruthy();
  expect(view.queryByText(/Les deux tarifs se succèdent/)).toBeNull();
});

test("scheduled review preserves the chosen local date and repeats the exact new amount", async () => {
  let intent: ProductPriceChangeRequest | undefined;
  globalThis.fetch = (async (_input, init) => {
    intent = JSON.parse(String(init?.body));
    if (!intent) throw new Error("Expected schedule intent");
    return json(review(intent));
  }) as typeof fetch;
  const view = mount();
  const user = userEvent.setup({ document: view.container.ownerDocument });
  await user.click(view.getByRole("button", { name: "Ouvrir" }));
  await user.clear(view.getByLabelText("Nouveau montant (MAD)"));
  await user.type(view.getByLabelText("Nouveau montant (MAD)"), "125,50");
  await user.click(view.getByRole("radio", { name: "À une date" }));
  const date = "2099-09-14T12:00";
  fireEvent.change(view.getByLabelText("Date et heure du changement"), { target: { value: date } });
  await user.type(view.getByLabelText("Motif"), "Future price");
  await user.click(view.getByRole("button", { name: "Vérifier" }));
  expect(await view.findByText("Nouveau tarif")).toBeTruthy();
  expect(intent?.amount).toBe("125.50");
  expect(intent?.timing).toBe("SCHEDULED");
  expect(intent?.effectiveFrom).toBe(new Date(date).toISOString());
  expect(view.getByRole("region", { name: "Vérification du changement" }).textContent).toContain("125,50");
});

test("missing permissions cannot preview or confirm even when a dialog is opened", async () => {
  let calls = 0;
  globalThis.fetch = (async (_input) => {
    calls++;
    return json({ message: "Unexpected request" }, 500);
  }) as typeof fetch;
  const view = mount("CHANGE", false);
  const user = userEvent.setup({ document: view.container.ownerDocument });
  await user.click(view.getByRole("button", { name: "Ouvrir" }));
  expect((view.getByRole("button", { name: "Vérifier" }) as HTMLButtonElement).disabled).toBe(true);
  expect((view.getByRole("button", { name: "Confirmer" }) as HTMLButtonElement).disabled).toBe(true);
  expect(calls).toBe(0);
});

test("scheduled change actions remain disabled when successor loading fails", async () => {
  globalThis.fetch = (async (_input) => json({ message: "Failure" }, 500)) as typeof fetch;
  const view = mount("CHANGE", true, true);
  expect(await view.findByRole("alert")).toBeTruthy();
  expect((view.getByRole("button", { name: "Modifier le changement" }) as HTMLButtonElement).disabled).toBe(true);
  expect((view.getByRole("button", { name: "Annuler le changement" }) as HTMLButtonElement).disabled).toBe(true);
});

test("published future and elapsed prices are not labelled current", () => {
  const now = Date.now();
  expect(productPriceDisplayStatus({ ...price, effectiveFrom: new Date(now + 1000).toISOString() }, now).label).toBe(
    "Programmé",
  );
  expect(productPriceDisplayStatus({ ...price, effectiveUntil: new Date(now).toISOString() }, now).label).toBe(
    "Ancien tarif",
  );
  expect(productPriceDisplayStatus(price, now).label).toBe("Tarif actuel");
  const r = review({
    operation: "CHANGE",
    currentVersion: 4,
    timing: "NOW",
    amount: "200",
    effectiveFrom: null,
    reason: "Test",
  });
  expect(priceChangeReviewReady(price, r)).toBe(true);
  expect(priceChangeReviewReady({ ...price, version: 5 }, r)).toBe(false);
  expect(priceChangeReviewReady(price, { ...r, expiresAt: new Date(now).toISOString() }, now)).toBe(false);
});

test("tariff input accepts French decimals but never silently rounds minor units", () => {
  expect(tariffChangeAmount("100,12", "MAD")).toBe("100.12");
  expect(tariffChangeAmount("100.1200", "MAD")).toBe("100.1200");
  expect(tariffChangeAmount("100.123", "MAD")).toBeNull();
  expect(tariffChangeAmount("100.5", "JPY")).toBeNull();
  expect(tariffChangeAmount("1,2,3", "MAD")).toBeNull();
  expect(tariffChangeAmount("0", "MAD")).toBe("0");
});
