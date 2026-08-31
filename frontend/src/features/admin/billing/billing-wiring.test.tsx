import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { Window } from "happy-dom";

const browser = new Window({ url: "http://localhost:3000/admin/billing" });
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
const { cleanup, render } = await import("@testing-library/react");
const { QueryClient, QueryClientProvider } = await import("@tanstack/react-query");
const { createMemoryRouter, RouterProvider } = await import("react-router");
const { adminPermissions } = await import("@/auth/permissions");
const { AdminSessionProvider } = await import("@/auth/session-provider");
const { clearSession, writeSession } = await import("@/auth/session-store");
const { AdminBillingPage } = await import("./admin-billing-page");
const { AdminInvoiceDetailPage } = await import("./admin-invoice-detail-page");

function response(body: unknown) {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}

function page(content: unknown[]) {
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

function renderRoute(path: string, element: React.ReactNode, permissions: string[]) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  queryClient.setQueryData(["admin", "me", "admin-token"], {
    id: "admin-1",
    email: "admin@hiveapp.test",
    emailVerified: true,
    isSuperAdmin: false,
    isActive: true,
    permissions,
  });
  const router = createMemoryRouter([{ path, element }], { initialEntries: [path.replace(":invoiceId", "invoice-1")] });
  return render(
    <QueryClientProvider client={queryClient}>
      <AdminSessionProvider>
        <RouterProvider router={router} />
      </AdminSessionProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() =>
  writeSession("admin", {
    accessToken: "admin-token",
    refreshToken: "refresh-token",
    expiresAt: Date.now() + 60_000,
    passwordChangeRequired: false,
  }),
);
afterEach(() => {
  cleanup();
  clearSession("admin");
  globalThis.fetch = originalFetch;
});

describe("billing least-privilege wiring", () => {
  test("invoice-list access does not request or label Account identity", async () => {
    let accountRequests = 0;
    globalThis.fetch = (async (input: Parameters<typeof fetch>[0]) => {
      const url = String(input);
      if (url.includes("account-identity")) accountRequests += 1;
      if (url.includes("/billing/invoices")) return response(page([]));
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;
    const view = renderRoute("/admin/billing", <AdminBillingPage />, [adminPermissions.billingListInvoices]);
    expect(await view.findByPlaceholderText("Numéro de facture…")).toBeTruthy();
    expect(await view.findByText("Aucune facture")).toBeTruthy();
    expect(view.queryByText("Compte")).toBeNull();
    expect(accountRequests).toBe(0);
  });

  test("reconciliation-only access starts on its authorized surface", async () => {
    let invoiceRequests = 0;
    let reconciliationRequests = 0;
    globalThis.fetch = (async (input) => {
      const url = String(input);
      if (url.includes("/billing/invoices")) invoiceRequests += 1;
      if (url.includes("/billing/reconciliation")) {
        reconciliationRequests += 1;
        return response(page([]));
      }
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;
    const view = renderRoute("/admin/billing", <AdminBillingPage />, [adminPermissions.billingListReconciliation]);
    expect(await view.findByText("Aucune commande")).toBeTruthy();
    expect(reconciliationRequests).toBe(1);
    expect(invoiceRequests).toBe(0);
  });

  test("payment evidence remains readable without broad Invoice detail", async () => {
    let invoiceRequests = 0;
    let paymentRequests = 0;
    globalThis.fetch = (async (input) => {
      const url = String(input);
      if (url.endsWith("/billing/invoices/invoice-1")) invoiceRequests += 1;
      if (url.includes("/billing/invoices/invoice-1/payments")) {
        paymentRequests += 1;
        return response([
          {
            id: "payment-1",
            kind: "PROVIDER",
            status: "FAILED",
            amount: "19.9900",
            currencyCode: "MAD",
            trustedForSettlement: false,
            externalReference: null,
            failureReason: "Refusé",
            operatorUserId: null,
            operatorReason: null,
            retryOfPaymentId: null,
            recoveryReference: null,
            completedAt: null,
            createdAt: "2026-08-31T10:00:00Z",
          },
        ]);
      }
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;
    const view = renderRoute("/admin/billing/invoices/:invoiceId", <AdminInvoiceDetailPage />, [
      adminPermissions.billingReadPayments,
    ]);
    expect(await view.findByText("Refusé")).toBeTruthy();
    expect(paymentRequests).toBe(1);
    expect(invoiceRequests).toBe(0);
  });

  test("Account identity remains readable without broad Invoice detail", async () => {
    let invoiceRequests = 0;
    globalThis.fetch = (async (input) => {
      const url = String(input);
      if (url.endsWith("/billing/invoices/invoice-1")) invoiceRequests += 1;
      if (url.includes("/billing/invoices/invoice-1/account-identity")) {
        return response({ id: "account-1", name: "Atlas SARL" });
      }
      throw new Error(`Unexpected request: ${url}`);
    }) as typeof fetch;
    const view = renderRoute("/admin/billing/invoices/:invoiceId", <AdminInvoiceDetailPage />, [
      adminPermissions.billingReadAccountIdentity,
    ]);
    expect(await view.findByText("Atlas SARL")).toBeTruthy();
    expect(invoiceRequests).toBe(0);
  });

  test("manual settlement remains operable without broad Invoice detail", async () => {
    globalThis.fetch = (async (input: Parameters<typeof fetch>[0]) => {
      throw new Error(`Unexpected request: ${String(input)}`);
    }) as unknown as typeof fetch;
    const view = renderRoute("/admin/billing/invoices/:invoiceId", <AdminInvoiceDetailPage />, [
      adminPermissions.billingManualSettlement,
    ]);
    expect(await view.findByRole("button", { name: "Règlement manuel" })).toBeTruthy();
  });
});
