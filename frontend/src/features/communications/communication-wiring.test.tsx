import { afterEach, beforeEach, expect, test } from "bun:test";
import { Window } from "happy-dom";
import type { CommunicationItem } from "@/api/communication-api";

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
const {
  clientPermissions: p,
  adminPermissions: a,
  clientCommunicationSurfacePermissions,
} = await import("@/auth/permissions");
const { ClientReadPermissionGate } = await import("@/components/patterns/permission-gate");
const { ClientCommunicationsPage, OperatorNotificationsPage } = await import("./client-communications-page");
const { CommunicationAdminHub } = await import("./admin-communication-pages");
const { CommunicationComposer } = await import("./communication-composer");
const { NotificationDeliveryPage } = await import("./notification-delivery-page");
const { InternalCommunicationsPage } = await import("./internal-communications-page");
const { ClientSettingsPage } = await import("./personal-settings-page");
const { ClientNotificationShortcut } = await import("./notification-shortcut");
const { i18n } = await import("@/app/i18n");
const response = (value: unknown) =>
  new Response(JSON.stringify(value), { headers: { "Content-Type": "application/json" } });
const page = (content: unknown[]) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 });
const warning: CommunicationItem = {
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
  topic: "ACCOUNT",
  eventType: null,
  resourceId: null,
  audience: "ACCOUNT",
  resolved: false,
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
    if (u.pathname.endsWith("/settings")) return response([]);
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
test("inbox refresh discovers asynchronously delivered notifications without a page reload", async () => {
  let delivered = false;
  globalThis.fetch = (async (_input, _init) => response(page(delivered ? [warning] : []))) as typeof fetch;
  const view = mount(true, [p.communicationsRead], "/app/communications", <ClientCommunicationsPage />);
  await waitFor(() => expect(view.getByRole("heading", { name: "Aucune notification" })).toBeTruthy());
  delivered = true;
  await userEvent
    .setup({ document: browser.document as unknown as Document })
    .click(view.getByRole("button", { name: "Actualiser" }));
  await waitFor(() => expect(view.getByRole("button", { name: /Capacity warning/ })).toBeTruthy());
});
test("automatic email recovery uses the email endpoint and requires a reviewed reason", async () => {
  let retried = false;
  let submitted: unknown;
  globalThis.fetch = (async (input, init) => {
    const u = new URL(String(input));
    calls.push(`${init?.method ?? "GET"} ${u.pathname}`);
    if (init?.method === "POST") {
      submitted = JSON.parse(String(init.body));
      retried = true;
      return new Response(null, { status: 204 });
    }
    return response(
      page(
        u.pathname.endsWith("/emails") && !retried
          ? [
              {
                id: "email-entry",
                type: "billing.payment_failed",
                state: "FAILED",
                attempts: 3,
                nextAttemptAt: null,
                version: 4,
                canRetry: true,
                failureCode: "TRANSPORT_FAILED",
              },
            ]
          : [],
      ),
    );
  }) as typeof fetch;
  const view = mount(
    false,
    [a.notificationsDelivery, a.notificationsRetry],
    "/admin/notifications/delivery",
    <NotificationDeliveryPage />,
  );
  const user = userEvent.setup({ document: browser.document as unknown as Document });
  await user.click(view.getByRole("button", { name: "Emails automatiques" }));
  await waitFor(() => expect(view.getByText("Paiement non abouti")).toBeTruthy());
  expect(view.getByText("Échec du service email")).toBeTruthy();
  await user.click(view.getByRole("button", { name: "Relancer" }));
  expect((view.getByRole("button", { name: "Confirmer" }) as HTMLButtonElement).disabled).toBe(true);
  await user.type(view.getByLabelText("Motif"), "Transport rétabli");
  await user.click(view.getByRole("button", { name: "Confirmer" }));
  await waitFor(() => expect(calls).toContain("POST /api/admin/notifications/delivery/emails/email-entry/retry"));
  expect(submitted).toEqual({ version: 4, reason: "Transport rétabli" });
  expect(calls.some((c) => c.includes("/api/v1/"))).toBe(false);
});
test("refresh updates selected detail and removes obsolete acknowledge actions", async () => {
  let current = warning;
  globalThis.fetch = (async (input) => {
    const u = new URL(String(input));
    return response(u.pathname.endsWith("/entry-1") ? current : page([current]));
  }) as typeof fetch;
  const view = mount(
    true,
    [p.communicationsRead, p.communicationsAcknowledge],
    "/app/communications?item=entry-1",
    <ClientCommunicationsPage />,
  );
  await waitFor(() => expect(view.getByRole("button", { name: "J’ai vu" })).toBeTruthy());
  current = { ...warning, sourceState: "RESOLVED", resolved: true, canAcknowledge: false };
  await userEvent
    .setup({ document: browser.document as unknown as Document })
    .click(view.getByRole("button", { name: "Actualiser" }));
  await waitFor(() => expect(view.queryByRole("button", { name: "J’ai vu" })).toBeNull());
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
test("sent history is separately authorized and never loads another sender's recipients", async () => {
  globalThis.fetch = (async (input) => {
    calls.push(String(input));
    return response(
      page([
        {
          commandId: "send-1",
          messageTitle: "Team update",
          messageBody: "Original content",
          recipients: 2,
          pending: 1,
          delivered: 1,
          failed: 0,
          createdAt: "2026-09-22T10:00:00Z",
        },
      ]),
    );
  }) as typeof fetch;
  const view = mount(
    true,
    [p.notificationsSent],
    "/app/announcements",
    <ClientReadPermissionGate anyOf={clientCommunicationSurfacePermissions}>
      <InternalCommunicationsPage />
    </ClientReadPermissionGate>,
  );
  await waitFor(() => expect(view.getAllByText("Team update").length).toBeGreaterThan(0));
  expect(calls.every((url) => new URL(url).pathname.endsWith("/communications/internal"))).toBe(true);
  expect(view.queryByRole("button", { name: "Informer des membres" })).toBeNull();
});
test("notification preferences save an explicit email language and requests carry UI language", async () => {
  let language = "fr";
  globalThis.fetch = (async (input, init) => {
    expect(new Headers(init?.headers).get("Accept-Language")).toBe("fr");
    const path = new URL(String(input)).pathname;
    if (path.endsWith("/language")) {
      if (init?.method === "PUT") language = JSON.parse(String(init.body)).language;
      return response({ language });
    }
    if (path.endsWith("/settings")) return response([]);
    if (path.endsWith("/preferences")) return response({ marketingInApp: false, marketingEmail: false });
    return response(page([]));
  }) as typeof fetch;
  const view = mount(
    true,
    [p.communicationsRead, p.notificationsPreferences],
    "/app/settings?section=notifications",
    <ClientSettingsPage />,
  );
  const user = userEvent.setup({ document: browser.document as unknown as Document });
  const picker = await view.findByRole("combobox", { name: "Langue des emails reçus" });
  await waitFor(() => expect((picker as HTMLButtonElement).disabled).toBe(false));
  await user.click(picker);
  await user.click(view.getByRole("option", { name: "العربية" }));
  await waitFor(() => expect(language).toBe("ar"));
});
test("information stays one-way and never loads a conversation", async () => {
  clientFetch({ ...warning, kind: "NOTICE", canAcknowledge: false });
  const view = mount(true, [p.communicationsRead], "/app/communications?item=entry-1", <ClientCommunicationsPage />);
  await waitFor(() => expect(view.getByRole("heading", { name: "Capacity warning" })).toBeTruthy());
  expect(view.queryByLabelText("Votre réponse")).toBeNull();
  expect(calls.some((c) => c.includes("/replies"))).toBe(false);
});
test("operator inbox uses the operator API and never requests account preferences", async () => {
  clientFetch({ ...warning, audience: "PLATFORM" });
  const view = mount(false, [a.notificationsRead], "/admin/notifications?item=entry-1", <OperatorNotificationsPage />);
  await waitFor(() => expect(view.getByRole("heading", { name: "Capacity warning" })).toBeTruthy());
  expect(calls).toContain("GET /api/admin/notifications/entry-1");
  expect(calls.some((c) => c.includes("/api/v1/") || c.includes("/preferences"))).toBe(false);
  expect(view.queryByRole("button", { name: "Informer des membres" })).toBeNull();
});
test("source action opens the business flow without performing it or offering a reply", async () => {
  clientFetch({
    ...warning,
    kind: "ACTION",
    topic: "COLLABORATION",
    actionPath: "/app/collaborations/collaboration-1",
    canAcknowledge: false,
  });
  const view = mount(true, [p.communicationsRead], "/app/communications?item=entry-1", <ClientCommunicationsPage />);
  await waitFor(() => expect(view.getByRole("link", { name: "Examiner la collaboration" })).toBeTruthy());
  expect(view.getByRole("link", { name: "Examiner la collaboration" }).getAttribute("href")).toBe(
    "/app/collaborations/collaboration-1",
  );
  expect(calls.some((c) => c.startsWith("POST"))).toBe(false);
});
test("account-internal send stages a named audience, then sends a stable command only after review", async () => {
  const sent: unknown[] = [];
  globalThis.fetch = (async (input, init) => {
    const path = new URL(String(input)).pathname;
    calls.push(`${init?.method ?? "GET"} ${path}`);
    if (path.endsWith("/recipients")) return response(page([{ id: "member-1", name: "Sara" }]));
    if (path.endsWith("/internal")) {
      sent.push(JSON.parse(String(init?.body)));
      return response({ commandId: "accepted", recipients: 1 });
    }
    return response(page([]));
  }) as typeof fetch;
  const view = mount(
    true,
    [p.notificationsSend, p.notificationsChoose],
    "/app/announcements",
    <InternalCommunicationsPage />,
  );
  const user = userEvent.setup({ document: browser.document as unknown as Document });
  await user.click(view.getByRole("button", { name: "Informer des membres" }));
  await user.type(view.getByLabelText("Titre"), "Réunion demain");
  await user.type(view.getByLabelText("Message", { exact: true }), "Consultez votre planning.");
  await waitFor(() => expect(view.getByRole("checkbox", { name: "Sélectionner Sara" })).toBeTruthy());
  await user.click(view.getByRole("checkbox", { name: "Sélectionner Sara" }));
  await user.click(view.getByRole("button", { name: "Vérifier l’envoi" }));
  expect(sent).toEqual([]);
  await user.click(view.getByRole("button", { name: "Envoyer l’information" }));
  await waitFor(() => expect(sent).toHaveLength(1));
  expect(sent[0]).toMatchObject({ messageTitle: "Réunion demain", memberIds: ["member-1"] });
  expect(sent[0]).toHaveProperty("commandId");
  await waitFor(() =>
    expect(view.getByRole("heading", { name: "Information enregistrée pour les destinataires choisis." })).toBeTruthy(),
  );
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

test("receiving has no sending, preference or delivery controls, even for a fully authorized actor", async () => {
  clientFetch();
  const view = mount(true, Object.values(p), "/app/communications", <ClientCommunicationsPage />);
  await view.findByText("Capacity warning");
  expect(view.queryByRole("button", { name: "Informer des membres" })).toBeNull();
  expect(view.queryByRole("combobox")).toBeNull();
  expect(view.queryByRole("table")).toBeNull();
  expect(calls).toEqual(["GET /api/v1/communications"]);
});

test("bell opens a lazy receiving panel without navigating or marking messages read", async () => {
  globalThis.fetch = (async (input) => {
    const path = new URL(String(input)).pathname;
    calls.push(path);
    if (path.endsWith("/summary")) return response({ unread: 1 });
    return response(page([warning]));
  }) as typeof fetch;
  const view = mount(true, [p.communicationsRead], "/app", <ClientNotificationShortcut />);
  await waitFor(() => expect(calls).toEqual(["/api/v1/communications/summary"]));
  expect(view.queryByRole("dialog")).toBeNull();
  const user = userEvent.setup({ document: browser.document as unknown as Document });
  await user.click(view.getByRole("button", { name: /Notifications/ }));
  await view.findByRole("dialog");
  await view.findByText("Capacity warning");
  expect(view.getByRole("link", { name: "Voir toutes les notifications" }).getAttribute("href")).toBe(
    "/app/communications",
  );
  expect(view.getByRole("link", { name: "Mes préférences" }).getAttribute("href")).toBe(
    "/app/settings?section=notifications",
  );
  expect(calls).toEqual(["/api/v1/communications/summary", "/api/v1/communications"]);
  await user.keyboard("{Escape}");
  await waitFor(() => expect(view.queryByRole("dialog")).toBeNull());
});

test("a delivery-only operator can reach diagnostics without fetching their inbox or customer publications", async () => {
  globalThis.fetch = (async (input) => {
    calls.push(new URL(String(input)).pathname);
    return response(page([]));
  }) as typeof fetch;
  mount(false, [a.notificationsDelivery], "/admin/communications", <CommunicationAdminHub />);
  await waitFor(() => expect(calls).toEqual(["/api/admin/notifications/delivery"]));
});

test("personal notification settings do not fetch account marketing consent or receiving history", async () => {
  globalThis.fetch = (async (input) => {
    const path = new URL(String(input)).pathname;
    calls.push(path);
    return response(path.endsWith("/language") ? { language: "fr" } : []);
  }) as typeof fetch;
  const view = mount(true, [p.communicationsRead], "/app/settings?section=notifications", <ClientSettingsPage />);
  const language = await view.findByRole("combobox", { name: "Langue des emails reçus" });
  expect((language as HTMLButtonElement).disabled).toBe(true);
  await waitFor(() =>
    expect(calls.sort()).toEqual(["/api/v1/communications/language", "/api/v1/communications/settings"]),
  );
  expect(view.queryByText("Préférences du compte")).toBeNull();
});

test("manual translation requires complete content and is reviewed and sent in the same command", async () => {
  let sent: Record<string, unknown> | undefined;
  globalThis.fetch = (async (input, init) => {
    const path = new URL(String(input)).pathname;
    if (path.endsWith("/recipients")) return response(page([{ id: "member-1", name: "Sara" }]));
    sent = JSON.parse(String(init?.body));
    return response({ commandId: "accepted", recipients: 1 });
  }) as typeof fetch;
  const view = mount(
    true,
    [p.notificationsSend, p.notificationsChoose],
    "/app/announcements",
    <InternalCommunicationsPage />,
  );
  const user = userEvent.setup({ document: browser.document as unknown as Document });
  await user.click(view.getByRole("button", { name: "Informer des membres" }));
  await user.type(view.getByLabelText("Titre", { exact: true }), "Bonjour");
  await user.type(view.getByLabelText("Message", { exact: true }), "Message original");
  await user.click(await view.findByRole("checkbox", { name: "Sélectionner Sara" }));
  await user.click(view.getByRole("button", { name: /Ajouter une autre langue/ }));
  expect((view.getByRole("button", { name: "Vérifier l’envoi" }) as HTMLButtonElement).disabled).toBe(true);
  await user.type(view.getByLabelText("Titre · العربية"), "مرحبا");
  await user.type(view.getByLabelText("Message · العربية"), "نص عربي");
  await user.click(view.getByRole("button", { name: "Vérifier l’envoi" }));
  expect(view.getByRole("heading", { name: "مرحبا" })).toBeTruthy();
  expect(view.getByRole("heading", { name: "Bonjour" })).toBeTruthy();
  expect(sent).toBeUndefined();
  await user.click(view.getByRole("button", { name: "Envoyer l’information" }));
  await waitFor(() =>
    expect(sent).toMatchObject({
      originalLanguage: "fr",
      messageTitle: "Bonjour",
      translations: { ar: { messageTitle: "مرحبا", messageBody: "نص عربي" } },
      memberIds: ["member-1"],
    }),
  );
});

test("a sending permission without recipient selection never exposes the composer or makes requests", async () => {
  clientFetch();
  const view = mount(true, [p.notificationsSend], "/app/announcements", <InternalCommunicationsPage />);
  expect(view.getByText("Accès indisponible")).toBeTruthy();
  expect(calls).toEqual([]);
});
