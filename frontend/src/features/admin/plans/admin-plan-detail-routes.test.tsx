import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { Window } from "happy-dom";

const browser = new Window({ url: "http://localhost:3000/admin/plans" });
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
  Object.defineProperty(globalThis, key, { configurable: true, value: key === "window" ? browser : browser[key] });
}

const originalFetch = globalThis.fetch;
const { cleanup, render, waitFor } = await import("@testing-library/react");
const userEvent = (await import("@testing-library/user-event")).default;
const { QueryClient, QueryClientProvider } = await import("@tanstack/react-query");
const { createMemoryRouter, RouterProvider } = await import("react-router");
const { adminPermissions } = await import("@/auth/permissions");
const { AdminSessionProvider } = await import("@/auth/session-provider");
const { clearSession, writeSession } = await import("@/auth/session-store");
const { TooltipProvider } = await import("@/components/ui/tooltip");
const { adminPlanDetailRoutes } = await import("./admin-plan-detail-routes");
const { i18n } = await import("@/app/i18n");
const { PlanFamilyCatalogue } = await import("./plan-family-catalogue");

const planId = "7f31acf0-5101-4341-ac59-7ac88d29ce45";
const base = `/admin/plans/${planId}`;
const plan = {
  id: planId,
  code: "ENTERPRISE",
  name: "Enterprise",
  description: null,
  price: "100",
  currencyCode: "MAD",
  billingCycle: "MONTHLY",
  status: "ACTIVE",
  lineageId: planId,
  revisionNumber: 1,
  sourcePlanId: null,
  creationReason: "CREATED",
  extensionPolicy: "OPEN_COMPATIBLE",
  salesVisibility: "PUBLIC",
  version: 0,
  featureCount: 0,
  quotaConfiguredFeatureCount: 0,
  activeSubscriberCount: 0,
  trialingSubscriberCount: 0,
  currentSubscriberCount: 0,
  historicalSubscriberCount: 0,
  affectedSubscriptionCount: 0,
  configuredRecurringPriceTotal: "0",
  configuredRecurringPriceCurrencyCode: "MAD",
  warnings: [],
};
const queries: InstanceType<typeof QueryClient>[] = [];
const requests: string[] = [];
const writes: { path: string; body: Record<string, unknown> }[] = [];
let applicationConfirmed = false;
let extraComparisonFamilies = false;
const secondId = "d777b6cb-a4b2-4ffe-99a8-cd7e1fb21652";
const firstVersion = {
  ...plan,
  applicablePriceCount: 1,
  draftPriceCount: 0,
  publishedPriceCount: 1,
  availableActions: [],
  blockers: [],
  includedFeatureCount: 0,
};
const secondVersion = { ...firstVersion, id: secondId, revisionNumber: 2 };
const versionRef = { ...plan, productVersionNumber: 1, rowVersion: 0 };

function response(body: unknown) {
  return new Response(JSON.stringify(body), { headers: { "Content-Type": "application/json" } });
}

function renderPage(path: string, permissions: string[], isSuperAdmin = false) {
  const profile = { id: "admin-1", email: "admin@hiveapp.test", isActive: true, isSuperAdmin, permissions };
  globalThis.fetch = (async (input, options) => {
    const url = new URL(String(input), "http://localhost:8080");
    if (url.pathname === "/api/admin/me") return response(profile);
    requests.push(url.pathname);
    if (options?.method === "POST") writes.push({ path: url.pathname, body: JSON.parse(String(options.body)) });
    if (url.pathname.startsWith("/api/admin/plan-version-applications")) {
      if (url.pathname.endsWith("/confirm")) applicationConfirmed = true;
      if (url.pathname.endsWith("/results"))
        return response({
          content: [
            {
              id: "result-1",
              status: applicationConfirmed ? "APPLIED" : "READY",
              frozenSubscriptionId: "sub-1",
              impact: null,
              executionConflicts: [],
              operationId: null,
              outcomeCode: null,
              attempts: 0,
              nextAttemptAt: null,
              completedAt: null,
              notice: null,
            },
          ],
          page: 0,
          size: 20,
          totalPages: 1,
          totalElements: 1,
        });
      return response({
        summary: {
          id: "job-1",
          familyId: planId,
          targetPlanId: secondId,
          status: applicationConfirmed ? "COMPLETED_WITH_ERRORS" : "PREVIEWED",
          counts: applicationConfirmed ? { APPLIED: 1, CONFLICT: 1 } : { READY: 1, CONFLICT: 1 },
          createdAt: "2026-01-01T00:00:00Z",
          reason: "Reviewed",
          requestedByUserId: "admin-1",
          version: 0,
        },
        definition: {
          targetPlanId: secondId,
          request: {
            sourcePlanId: planId,
            scope: "FAMILY",
            audience: "ALL",
            accountIds: [],
            excludedAccountIds: [],
            statuses: ["ACTIVE"],
            application: { timing: "NOW", notBefore: null, reason: "Reviewed" },
            notificationPolicy: "IN_APP",
          },
        },
        conflicts: [{ primaryReason: "OTHER_CHANGE_PENDING", accounts: 1 }],
        reviewInvalidated: false,
        previewToken: "review-token",
        expiresAt: new Date(Date.now() + 60_000).toISOString(),
      });
    }
    if (url.pathname === "/api/admin/plans/comparison")
      return response({
        catalogRevision: 7,
        asOf: "2026-09-23T00:00:00Z",
        pricesVisible: false,
        plans: [versionRef, { ...versionRef, id: secondId, name: "Flex" }].map((item, index) => ({
          plan: item,
          currentPrices: [],
          scheduledPrices: [],
          features: [
            {
              id: `${index}-staff`,
              featureCode: "platform.staff",
              mode: "INCLUDED",
              quotaConfigs: [{ resource: "members", mode: "FINITE", limit: index ? 5 : 3 }],
            },
            { id: `${index}-workspace`, featureCode: "platform.workspace", mode: "INCLUDED", quotaConfigs: [] },
          ],
        })),
      });
    if (url.pathname.endsWith("/extensions/compatibility"))
      return response({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
    if (url.pathname === `/api/admin/plans/${planId}`) return response(plan);
    if (url.pathname === `/api/admin/plans/${secondId}`) return response(secondVersion);
    if (url.pathname.endsWith("/versions"))
      return response({
        lineageId: planId,
        publicPlanId: planId,
        draftPlanId: null,
        catalogRevision: 7,
        versions: { content: [firstVersion, secondVersion], totalPages: 1, totalElements: 2 },
      });
    if (url.pathname.includes("/compare/"))
      return response({
        lineageId: planId,
        source: versionRef,
        target: { ...versionRef, id: secondId, productVersionNumber: 2 },
        features: [
          {
            featureCode: "client.staff",
            changed: true,
            before: {
              id: "a",
              featureCode: "client.staff",
              mode: "INCLUDED",
              quotaConfigs: [{ resource: "members", mode: "FINITE", limit: 5 }],
            },
            after: {
              id: "b",
              featureCode: "client.staff",
              mode: "INCLUDED",
              quotaConfigs: [{ resource: "members", mode: "FINITE", limit: 10 }],
            },
          },
        ],
        pricesVisible: false,
        sourcePrices: [],
        targetPrices: [],
      });
    if (url.pathname === "/api/admin/plans/families")
      return response({
        content: [
          {
            lineageId: planId,
            publicVersion: versionRef,
            draft: { ...versionRef, id: secondId, status: "DRAFT", productVersionNumber: 2 },
            versionCount: 2,
            currentSubscriberCount: null,
            pricesVisible: false,
            currentPrices: [],
          },
          ...(extraComparisonFamilies
            ? [1, 2, 3].map((number) => ({
                lineageId: `family-${number}`,
                publicVersion: {
                  ...versionRef,
                  id: `7f31acf0-5101-4341-ac59-7ac88d29ce4${number}`,
                  name: `Plan ${number}`,
                },
                draft: null,
                versionCount: 1,
                currentSubscriberCount: null,
                pricesVisible: false,
                currentPrices: [],
              }))
            : []),
        ],
        totalPages: 1,
        totalElements: 1,
      });
    if (url.pathname.includes("/feature-catalog")) return response([]);
    if (url.pathname.endsWith("/operations")) return response({ availableActions: [] });
    if (url.pathname.endsWith("/family-subscribers"))
      return response({
        content: [
          {
            subscriptionId: "sub-1",
            accountId: "account-1",
            accountName: "Atlas",
            planId: secondId,
            productVersionNumber: 2,
            status: "ACTIVE",
            retainedTotal: "100",
            currency: "MAD",
            billingCycle: "MONTHLY",
            periodEnd: "2027-01-01T00:00:00Z",
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      });
    if (url.pathname.endsWith("/version-history"))
      return response({
        content: [
          {
            id: "event-1",
            occurredAt: "2026-01-01T00:00:00Z",
            kind: "VERSION",
            action: "platform.plans.select_public_version",
            outcome: "SUCCEEDED",
            actorUserId: null,
            actorLabel: null,
            resourceId: secondId,
            productVersionNumber: 2,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      });
    if (url.pathname.endsWith("/features")) return response([]);
    if (url.pathname.endsWith("/subscribers") || url.pathname === "/api/admin/product-prices") {
      return response({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true });
    }
    throw new Error(`Unexpected request: ${url.pathname}`);
  }) as typeof fetch;
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } });
  queries.push(queryClient);
  queryClient.setQueryData(["admin", "me", "admin-token"], profile);
  const router = createMemoryRouter(
    [{ path: "/admin", children: [{ path: "plans", element: <PlanFamilyCatalogue /> }, ...adminPlanDetailRoutes] }],
    { initialEntries: [path] },
  );
  const view = render(
    <QueryClientProvider client={queryClient}>
      <AdminSessionProvider>
        <TooltipProvider>
          <RouterProvider router={router} />
        </TooltipProvider>
      </AdminSessionProvider>
    </QueryClientProvider>,
  );
  return { ...view, router };
}

beforeEach(async () => {
  await i18n.changeLanguage("fr");
  cleanup();
  requests.length = 0;
  writes.length = 0;
  applicationConfirmed = false;
  extraComparisonFamilies = false;
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
  for (const client of queries.splice(0)) client.clear();
  globalThis.fetch = originalFetch;
});

describe("plan detail route rendering and authorization", () => {
  test("catalogue selection enables at two, caps at three, and opens exact version IDs", async () => {
    extraComparisonFamilies = true;
    const view = renderPage("/admin/plans", [adminPermissions.plansListFamilies, adminPermissions.plansCompare]);
    const user = userEvent.setup({ document: browser.document as unknown as Document });
    await waitFor(() => expect(view.getByRole("heading", { name: "Enterprise" })).toBeTruthy());
    await user.click(view.getByRole("button", { name: "Comparer les forfaits" }));
    await user.click(view.getByRole("checkbox", { name: "Sélectionner Enterprise" }));
    expect(view.getByRole("button", { name: "Choisir 2 ou 3 forfaits" }).hasAttribute("disabled")).toBe(true);
    await user.click(view.getByRole("checkbox", { name: "Sélectionner Plan 1" }));
    expect(view.getByRole("link", { name: "Comparer" })).toBeTruthy();
    await user.click(view.getByRole("checkbox", { name: "Sélectionner Plan 2" }));
    expect(view.getByRole("checkbox", { name: "Sélectionner Plan 3" }).hasAttribute("disabled")).toBe(true);
    await user.click(view.getByRole("link", { name: "Comparer" }));
    await waitFor(() => expect(view.router.state.location.pathname).toBe("/admin/plans/compare"));
    expect(new URLSearchParams(view.router.state.location.search).get("ids")?.split(",")).toHaveLength(3);
  });
  test("cross-plan comparison renders differences and expands details without secondary reads", async () => {
    const view = renderPage(`/admin/plans/compare?ids=${planId},${secondId}`, [adminPermissions.plansCompare]);
    await waitFor(() => expect(view.getByText("platform.staff")).toBeTruthy());
    expect(view.getByText("platform.workspace")).toBeTruthy();
    const user = userEvent.setup({ document: browser.document as unknown as Document });
    await user.click(view.getByRole("checkbox", { name: "Différences uniquement" }));
    expect(view.queryByText("platform.workspace")).toBeNull();
    await user.click(view.getByRole("button", { name: "Détails de platform.staff" }));
    expect(view.getByText("Permissions de la fonctionnalité")).toBeTruthy();
    expect(view.getAllByText("Compatibilité non autorisée")).toHaveLength(2);
    expect(requests).toEqual(["/api/admin/plans/comparison"]);
    expect(writes).toEqual([]);
    await user.click(view.getByRole("button", { name: "Actualiser" }));
    await waitFor(() => expect(requests.filter((path) => path === "/api/admin/plans/comparison")).toHaveLength(2));
    expect(view.getByRole("button", { name: "Réduire platform.staff" }).getAttribute("aria-expanded")).toBe("true");
  });

  test("compatibility details are lazy, feature-filtered and read-only", async () => {
    const view = renderPage(`/admin/plans/compare?ids=${planId},${secondId}`, [
      adminPermissions.plansCompare,
      adminPermissions.commercialInspectCompatibility,
    ]);
    await waitFor(() => expect(view.getByText("platform.staff")).toBeTruthy());
    expect(requests).toEqual(["/api/admin/plans/comparison"]);
    const user = userEvent.setup({ document: browser.document as unknown as Document });
    await user.click(view.getByRole("button", { name: "Détails de platform.staff" }));
    await waitFor(() => expect(view.getAllByText("Aucun résultat")).toHaveLength(2));
    expect(requests.filter((path) => path.endsWith("/extensions/compatibility"))).toHaveLength(2);
    expect(writes).toEqual([]);
  });

  test("comparison rejects malformed URLs and the independent route guard makes no requests", async () => {
    const view = renderPage("/admin/plans/compare?ids=invalid", [adminPermissions.plansCompare]);
    await waitFor(() => expect(view.getByText("Sélectionnez deux ou trois versions distinctes.")).toBeTruthy());
    expect(requests).toEqual([]);
    cleanup();
    const denied = renderPage(`/admin/plans/compare?ids=${planId},${secondId}`, [
      adminPermissions.plansCompareVersions,
    ]);
    await waitFor(() => expect(denied.getByText("Accès indisponible")).toBeTruthy());
    expect(requests).toEqual([]);
  });

  test("result rows refetch and the conflict filter clears after a successful job action", async () => {
    const view = renderPage(`${base}/applications/job-1`, [
      adminPermissions.plansReadApplication,
      adminPermissions.plansApplicationResults,
      adminPermissions.plansConfirmApplication,
      adminPermissions.plansApplyVersion,
      adminPermissions.plansPreviewApplication,
    ]);
    const user = userEvent.setup({ document: browser.document as unknown as Document });
    await waitFor(() => expect(requests.filter((path) => path.endsWith("/results")).length).toBe(1));
    const conflictFilter = view.container.querySelector('button[aria-pressed="false"]');
    expect(conflictFilter).toBeTruthy();
    await user.click(conflictFilter as HTMLButtonElement);
    expect(conflictFilter?.getAttribute("aria-pressed")).toBe("true");
    await user.click(view.getByRole("checkbox"));
    await user.click(view.getByRole("button", { name: "Confirmer l’application" }));
    const dialog = view.getByRole("dialog");
    const buttons = dialog.querySelectorAll("button");
    const confirm = Array.from(buttons).find((button) => button.textContent?.includes("Confirmer l’application"));
    expect(confirm).toBeTruthy();
    await user.click(confirm as HTMLButtonElement);
    await waitFor(() => expect(requests.filter((path) => path.endsWith("/results")).length).toBeGreaterThan(1));
    await waitFor(() => expect(view.getAllByText("Appliqué").length).toBeGreaterThan(1));
    expect(view.container.querySelector('button[aria-pressed="true"]')).toBeNull();
  });

  test("family subscribers show retained terms and a version without the old subscriber permission", async () => {
    const view = renderPage(`${base}/subscribers`, [
      adminPermissions.plansReadDetail,
      adminPermissions.plansListFamilySubscribers,
    ]);
    await waitFor(() => expect(view.getByText("Atlas")).toBeTruthy());
    expect(view.getByText("V2")).toBeTruthy();
    expect(view.getByRole("combobox")).toBeTruthy();
    expect(requests.some((path) => path.endsWith("/family-subscribers"))).toBe(true);
    expect(requests.some((path) => path.endsWith("/subscribers"))).toBe(false);
    expect(view.queryByText("Accès indisponible")).toBeNull();
  });

  test("version history works independently and renders system actor with a readable action", async () => {
    const view = renderPage(`${base}/history`, [
      adminPermissions.plansReadDetail,
      adminPermissions.plansReadVersionHistory,
    ]);
    await waitFor(() => expect(view.getByText("Version proposée au catalogue")).toBeTruthy());
    expect(view.getByText("Système")).toBeTruthy();
    expect(requests.some((path) => path.startsWith("/api/admin/plan-version-applications"))).toBe(false);
    expect(view.queryByText("Accès indisponible")).toBeNull();
  });

  test("Arabic read-only application uses localized steps and exposes no mutations", async () => {
    await i18n.changeLanguage("ar");
    const view = renderPage(`${base}/applications/job-1`, [adminPermissions.plansReadApplication]);
    await waitFor(() => expect(view.getByText("تطبيق إصدار على المشتركين")).toBeTruthy());
    expect(view.queryByText("تأكيد التطبيق")).toBeNull();
    expect(writes.length).toBe(0);
  });

  test("content flow freezes only on review, acknowledges partial results and confirms explicitly", async () => {
    const view = renderPage(`/admin/plans/${secondId}/apply`, [
      adminPermissions.plansReadDetail,
      adminPermissions.plansListVersions,
      adminPermissions.plansCreateApplication,
      adminPermissions.plansPreviewApplication,
      adminPermissions.plansReadApplication,
      adminPermissions.plansApplyVersion,
      adminPermissions.plansConfirmApplication,
    ]);
    const user = userEvent.setup({ document: view.container.ownerDocument });
    const next = await view.findByRole("button", { name: "Continuer" });
    await waitFor(() => expect(next.hasAttribute("disabled")).toBeFalse());
    expect(writes).toHaveLength(0);
    await user.click(next);
    await user.type(view.getByLabelText("Motif du changement"), "Reviewed removal with retained billing");
    await user.click(view.getByRole("button", { name: "Analyser les impacts" }));
    await waitFor(() => expect(view.router.state.location.pathname).toContain("/applications/job-1"));
    expect(writes[0]?.body).toMatchObject({
      scope: "FAMILY",
      audience: "ALL",
      notificationPolicy: "IN_APP",
      application: { timing: "NOW", reason: "Reviewed removal with retained billing" },
    });
    const confirm = await view.findByRole("button", { name: "Confirmer l’application" });
    expect(confirm.hasAttribute("disabled")).toBeTrue();
    await user.click(view.getByRole("checkbox", { name: /Appliquer uniquement/ }));
    await user.click(confirm);
    const dialog = view.getByRole("dialog");
    await user.click(
      Array.from(dialog.querySelectorAll("button")).find(
        (button) => button.textContent === "Confirmer l’application",
      ) as HTMLButtonElement,
    );
    await waitFor(() => expect(writes).toHaveLength(2));
    expect(writes[1]?.body).toEqual({ previewToken: "review-token", applyReadyOnly: true });
    expect(requests.some((path) => path.endsWith("/results") || path.endsWith("/identities"))).toBeFalse();
  });

  test("read-only application access does not request results or identities or offer confirmation", async () => {
    const view = renderPage(`${base}/applications/job-1`, [adminPermissions.plansReadApplication]);
    expect(await view.findByRole("heading", { name: "Appliquer une version aux abonnés" })).toBeTruthy();
    expect(view.queryByRole("button", { name: "Confirmer l’application" })).toBeNull();
    expect(view.queryByText("Accès indisponible")).toBeNull();
    expect(requests).toEqual(["/api/admin/plan-version-applications/job-1"]);
  });

  test("application creation route needs preview permission as well as creation", async () => {
    const view = renderPage(`${base}/apply`, [adminPermissions.plansCreateApplication]);
    expect(await view.findByText("Accès indisponible")).toBeTruthy();
    expect(requests).toHaveLength(0);
  });

  test("SuperAdmin opening the base URL sees the overview, not a false permission denial", async () => {
    const view = renderPage(base, [adminPermissions.plansReadDetail], true);
    expect(await view.findByRole("heading", { name: "Contenu du forfait" })).toBeTruthy();
    expect(view.queryByText("Accès indisponible")).toBeNull();
  });

  test("an ordinary detail reader can also open the overview without broader permissions", async () => {
    const view = renderPage(base, [adminPermissions.plansReadDetail]);
    expect(await view.findByText("Le contenu n’est pas accessible avec votre rôle.")).toBeTruthy();
    expect(requests).toEqual([`/api/admin/plans/${planId}`]);
  });

  test.each([
    ["features", adminPermissions.plansListFeatures, "Contenu du forfait", "/features"],
    ["schema", adminPermissions.plansListFeatures, "Aucune fonctionnalité à représenter", "/features"],
    ["subscribers", adminPermissions.plansListSubscribers, "Aucun abonné", "/subscribers"],
  ])("the static %s URL renders its own panel", async (tab, permission, text, endpoint) => {
    const view = renderPage(`${base}/${tab}`, [adminPermissions.plansReadDetail, permission]);
    expect(await view.findByText(new RegExp(text))).toBeTruthy();
    expect(view.queryByText("Accès indisponible")).toBeNull();
    expect(requests).toContain(`/api/admin/plans/${planId}${endpoint}`);
  });

  test("the content view switches between the table and schema without extra main tabs", async () => {
    const view = renderPage(base, [adminPermissions.plansReadDetail, adminPermissions.plansListFeatures]);
    const user = userEvent.setup({ document: view.container.ownerDocument });
    await view.findByRole("heading", { name: "Contenu du forfait" });
    await user.click(view.getByRole("link", { name: "Schéma" }));
    expect(await view.findByText(/Aucune fonctionnalité à représenter/)).toBeTruthy();
    await user.click(view.getByRole("link", { name: "Tableau" }));
    expect(await view.findByRole("heading", { name: "Contenu du forfait" })).toBeTruthy();
    expect(view.router.state.location.pathname).toBe(base);
  });

  test("the dynamic prices URL still opens its authorized panel", async () => {
    const view = renderPage(`${base}/prices`, [adminPermissions.plansReadDetail, adminPermissions.priceBooksList]);
    expect(await view.findByRole("heading", { name: "Aucun tarif" })).toBeTruthy();
    expect(requests).toContain("/api/admin/product-prices");
    expect(view.queryByText("Accès indisponible")).toBeNull();
  });

  test.each(["features", "schema", "subscribers", "prices"])(
    "an unauthorized %s deep link stays denied without fetching its data",
    async (tab) => {
      const view = renderPage(`${base}/${tab}`, [adminPermissions.plansReadDetail]);
      expect(await view.findByText("Accès indisponible")).toBeTruthy();
      expect(requests.every((path) => path === `/api/admin/plans/${planId}`)).toBe(true);
    },
  );

  test("without plan-detail access the route never fetches the plan", async () => {
    const view = renderPage(base, []);
    expect(await view.findByText("Accès indisponible")).toBeTruthy();
    expect(requests).toHaveLength(0);
  });

  test("an unknown tab does not silently expose overview content", async () => {
    const view = renderPage(`${base}/unknown`, [adminPermissions.plansReadDetail]);
    await waitFor(() => expect(view.queryByText("Accès indisponible")).not.toBeNull());
    expect(view.queryByRole("heading", { name: "Configuration commerciale" })).toBeNull();
  });

  test("a versions-only reader can compare neither versions nor mutate the public choice", async () => {
    const view = renderPage(`${base}/versions`, [adminPermissions.plansListVersions]);
    expect(await view.findByText("Version 2")).toBeTruthy();
    expect(view.queryByRole("button", { name: "Proposer cette version" })).toBeNull();
    expect(view.queryByRole("checkbox")).toBeNull();
    expect(requests).toEqual([`/api/admin/plans/${planId}/versions`]);
  });

  test("comparison uses the independent read permission and degrades to feature codes", async () => {
    const view = renderPage(`${base}/versions/compare?source=${planId}&target=${secondId}`, [
      adminPermissions.plansCompareVersions,
    ]);
    expect(await view.findByText("client.staff")).toBeTruthy();
    expect(view.getByText("members · 5")).toBeTruthy();
    expect(view.getByText("members · 10")).toBeTruthy();
    expect(requests).toEqual([`/api/admin/plans/${planId}/compare/${secondId}`]);
  });

  test("selecting two versions enables comparison and prevents selecting too many", async () => {
    const view = renderPage(`${base}/versions`, [
      adminPermissions.plansListVersions,
      adminPermissions.plansCompareVersions,
    ]);
    const user = userEvent.setup({ document: view.container.ownerDocument });
    await user.click(await view.findByRole("checkbox", { name: "Sélectionner la version 1" }));
    await user.click(view.getByRole("checkbox", { name: "Sélectionner la version 2" }));
    expect(view.getByRole("link", { name: "Comparer" }).getAttribute("href")).toContain(
      `source=${planId}&target=${secondId}`,
    );
  });

  test("family catalogue renders one card and keeps inaccessible counts and prices hidden", async () => {
    const view = renderPage("/admin/plans", [
      adminPermissions.plansListFamilies,
      adminPermissions.plansListVersions,
      adminPermissions.plansReadDetail,
    ]);
    expect(await view.findByRole("article", { name: "Enterprise" })).toBeTruthy();
    expect(view.getAllByRole("article")).toHaveLength(1);
    expect(view.getByRole("link", { name: "Continuer le brouillon" }).getAttribute("href")).toBe(
      `/admin/plans/${secondId}`,
    );
    expect(view.queryByText("0 abonnés")).toBeNull();
    expect(requests).toEqual(["/api/admin/plans/families"]);
  });

  test.each(["versions", "versions/compare"])("unauthorized %s route does not fetch data", async (path) => {
    const view = renderPage(`${base}/${path}`, [adminPermissions.plansReadDetail]);
    expect(await view.findByText("Accès indisponible")).toBeTruthy();
    expect(requests).toHaveLength(0);
  });
});
