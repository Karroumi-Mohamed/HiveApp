import { afterEach, beforeEach, expect, test } from "bun:test";
import { Window } from "happy-dom";

const browser = new Window({ url: "http://localhost:3000/app/communications" });
for (const key of [
  "window",
  "document",
  "navigator",
  "localStorage",
  "sessionStorage",
  "HTMLElement",
  "HTMLInputElement",
  "HTMLButtonElement",
  "HTMLTextAreaElement",
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
  Object.defineProperty(globalThis, key, { configurable: true, value: key === "window" ? browser : browser[key] });
}
Object.defineProperty(globalThis, "requestAnimationFrame", {
  configurable: true,
  value: (fn: FrameRequestCallback) => setTimeout(() => fn(Date.now()), 0),
});
Object.defineProperty(globalThis, "cancelAnimationFrame", {
  configurable: true,
  value: (id: number) => clearTimeout(id),
});
const originalFetch = globalThis.fetch;
const { render, cleanup, waitFor } = await import("@testing-library/react");
const userEvent = (await import("@testing-library/user-event")).default;
const { QueryClient, QueryClientProvider } = await import("@tanstack/react-query");
const { createMemoryRouter, RouterProvider } = await import("react-router");
const { ClientSessionProvider, AdminSessionProvider } = await import("@/auth/session-provider");
const { writeSession, clearSession } = await import("@/auth/session-store");
const { clientPermissions: p, adminPermissions: a } = await import("@/auth/permissions");
const { ClientCommunicationsPage } = await import("./client-communications-page");
const { CommunicationAdminHub } = await import("./admin-communication-pages");
const { CommunicationComposer } = await import("./communication-composer");
const { i18n } = await import("@/app/i18n");
const response = (value: unknown) =>
  new Response(JSON.stringify(value), { headers: { "Content-Type": "application/json" } });
const page = (content: unknown[]) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 });
const warning = {
  id: "entry-1",
  kind: "WARNING",
  purpose: "SERVICE",
  messageTitle: "Capacity warning",
  messageBody: "Review your capacity.",
  source: "ADMIN",
  sourceState: "PUBLISHED",
  actionPath: null,
  availableAt: "2026-09-22T10:00:00Z",
  expiresAt: null,
  read: false,
  acknowledged: false,
  archived: false,
  canAcknowledge: true,
  canArchive: false,
  canReply: false,
  closed: false,
};
let calls: string[] = [];
beforeEach(async () => {
  calls = [];
  await i18n.changeLanguage("fr");
  for (const surface of ["admin", "client"] as const)
    writeSession(surface, {
      accessToken: `${surface}-token`,
      refreshToken: "refresh-token",
      expiresAt: Date.now() + 300000,
      passwordChangeRequired: false,
    });
});
afterEach(() => {
  cleanup();
  clearSession("admin");
  clearSession("client");
  globalThis.fetch = originalFetch;
  browser.localStorage.clear();
});
function mount(client: boolean, permissions: string[], path: string, element: React.ReactNode) {
  const cache = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Number.POSITIVE_INFINITY }, mutations: { retry: false } },
  });
  cache.setQueryData(["client", "account", "client-token"], { id: "account-1", name: "Acme", ownerId: "user-1" });
  cache.setQueryData(["client", "companies", "client-token"], []);
  cache.setQueryData(["client", "permissions", null, false, "client-token"], {
    memberId: "member-1",
    accountId: "account-1",
    isOwner: false,
    permissions,
  });
  cache.setQueryData(["admin", "me", "admin-token"], {
    id: "admin-1",
    email: "admin@example.com",
    isSuperAdmin: false,
    isActive: true,
    permissions,
  });
  const Provider = client ? ClientSessionProvider : AdminSessionProvider;
  const router = createMemoryRouter([{ path: "*", element }], { initialEntries: [path] });
  return render(
    <QueryClientProvider client={cache}>
      <Provider>
        <RouterProvider router={router} />
      </Provider>
    </QueryClientProvider>,
  );
}
function clientFetch(item = warning) {
  globalThis.fetch = (async (input, init) => {
    const u = new URL(String(input));
    calls.push(`${init?.method ?? "GET"} ${u.pathname}`);
    if (init?.method === "POST") return new Response(null, { status: 204 });
    if (u.pathname.endsWith("/preferences")) return response({ marketingInApp: false, marketingEmail: false });
    if (u.pathname.endsWith("/entry-1")) return response(item);
    if (u.pathname.endsWith("/replies")) return response(page([]));
    return response(page([item]));
  }) as typeof fetch;
}
test("read-only viewer never receives acknowledge or archive actions", async () => {
  clientFetch();
  const view = mount(true, [p.communicationsRead], "/app/communications?item=entry-1", <ClientCommunicationsPage />);
  await waitFor(() => expect(view.getByRole("heading", { name: "Capacity warning" })).toBeTruthy());
  expect(view.queryByRole("button", { name: "J’ai vu" })).toBeNull();
  expect(view.queryByRole("button", { name: "Marquer comme lu" })).toBeNull();
  expect(calls.some((c) => c.startsWith("POST"))).toBe(false);
});
test("reading and acknowledging are separate actions and active warnings cannot be archived", async () => {
  clientFetch();
  const view = mount(
    true,
    [p.communicationsRead, p.communicationsMarkRead, p.communicationsAcknowledge, p.communicationsArchive],
    "/app/communications?item=entry-1",
    <ClientCommunicationsPage />,
  );
  await waitFor(() => expect(view.getByRole("button", { name: "J’ai vu" })).toBeTruthy());
  await userEvent
    .setup({ document: browser.document as unknown as Document })
    .click(view.getByRole("button", { name: "J’ai vu" }));
  await waitFor(() => expect(calls).toContain("POST /api/v1/communications/entry-1/acknowledge"));
  expect(calls).not.toContain("POST /api/v1/communications/entry-1/read");
  expect(view.queryByRole("button", { name: "Archiver" })).toBeNull();
});
test("without inbox permission the page performs no communication request", async () => {
  clientFetch();
  const view = mount(true, [], "/app/communications", <ClientCommunicationsPage />);
  await waitFor(() => expect(view.getByText("Accès indisponible")).toBeTruthy());
  expect(calls).toEqual([]);
});
test("client messages show reply input only with reply permission", async () => {
  clientFetch({ ...warning, kind: "MESSAGE", canAcknowledge: false, canReply: true });
  const view = mount(
    true,
    [p.communicationsRead, p.communicationsReply],
    "/app/communications?item=entry-1",
    <ClientCommunicationsPage />,
  );
  await waitFor(() => expect(view.getByLabelText("Votre réponse")).toBeTruthy());
  const user = userEvent.setup({ document: browser.document as unknown as Document });
  await user.type(view.getByLabelText("Votre réponse"), "My private reply");
  await user.click(view.getByRole("button", { name: "Envoyer la réponse" }));
  await waitFor(() => expect(calls).toContain("POST /api/v1/communications/entry-1/replies"));
});
test("customer communications reader does not load credential emails", async () => {
  globalThis.fetch = (async (input) => {
    calls.push(new URL(String(input)).pathname);
    return response(page([]));
  }) as typeof fetch;
  const view = mount(false, [a.customerCommunicationsRead], "/admin/communications", <CommunicationAdminHub />);
  await waitFor(() => expect(view.getByText("Aucune communication")).toBeTruthy());
  expect(view.queryByRole("button", { name: "Emails de sécurité" })).toBeNull();
  expect(calls).toEqual(["/api/admin/customer-communications"]);
});
test("composer stages content and recipients without saving before review", async () => {
  globalThis.fetch = (async (input) => {
    calls.push(new URL(String(input)).pathname);
    return response(page([{ id: "account-1", name: "Acme" }]));
  }) as typeof fetch;
  const view = mount(
    false,
    [a.customerCommunicationsRead, a.customerCommunicationsCreate, a.customerCommunicationsChoose],
    "/admin/communications/new",
    <CommunicationComposer />,
  );
  const user = userEvent.setup({ document: browser.document as unknown as Document });
  await user.type(view.getByLabelText("Titre"), "Service information");
  await user.type(view.getByLabelText("Message", { exact: true }), "Useful details");
  await user.click(view.getByRole("button", { name: "Continuer" }));
  await waitFor(() => expect(view.getByRole("checkbox", { name: "Sélectionner Acme" })).toBeTruthy());
  await user.click(view.getByRole("checkbox", { name: "Sélectionner Acme" }));
  await user.click(view.getByRole("button", { name: "Continuer" }));
  expect(view.getByRole("button", { name: "Enregistrer le brouillon" })).toBeTruthy();
  expect(calls).toEqual(["/api/admin/customer-communications/recipients"]);
});
