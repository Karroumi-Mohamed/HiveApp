import { createRouter, createWebHistory } from "vue-router";
import { session } from "./data/session";
import { firstWorkspace } from "./lib/navigation";
import { resources } from "./resources";
const hub = (path: string, title: string, keys: string[]) => ({
  path,
  component: () => import("./views/HubView.vue"),
  meta: { title, resources: keys },
});
const scrollPositions = new Map<string, { top: number }>();
export const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: (to, _from, saved) =>
    saved || scrollPositions.get(to.fullPath) || { top: 0 },
  routes: [
    { path: "/", redirect: () => firstWorkspace() },
    {
      path: "/overview",
      component: () => import("./views/OverviewView.vue"),
      meta: { title: "Platform Overview" },
    },
    {
      path: "/customers/:accountId/agreements/:id",
      component: () => import("./views/AgreementView.vue"),
      meta: { title: "Agreement" },
    },
    {
      path: "/customers",
      component: () => import("./views/CustomersWorkspaceView.vue"),
      meta: { title: "Customers" },
    },
    {
      path: "/customers/:id",
      component: () => import("./views/CustomerView.vue"),
      meta: { title: "Customer" },
    },
    hub("/catalog", "Catalog", ["plans", "addons", "capacity", "prices"]),
    hub("/commercial", "Commercial", [
      "offers",
      "campaigns",
      "segments",
      "policies",
    ]),
    {
      path: "/billing",
      redirect: (to) => ({
        path: "/customers",
        query: {
          ...to.query,
          view: to.query.view === "agreements" ? "agreements" : "invoices",
        },
      }),
    },
    {
      path: "/billing/:id",
      redirect: (to) => ({
        path: "/customers/invoices/" + to.params.id,
        query: to.query,
      }),
    },
    {
      path: "/customers/invoices/:id",
      component: () => import("./views/InvoiceView.vue"),
      meta: { title: "Invoice" },
    },
    hub("/inbox", "Inbox", ["inbox"]),
    {
      path: "/operations/messages/:id",
      redirect: (to) => ({
        path: "/customers/communications/" + to.params.id,
        query: to.query,
      }),
    },
    {
      path: "/operations/messages",
      redirect: (to) => ({
        path: "/customers",
        query: { ...to.query, view: "messages" },
      }),
    },
    {
      path: "/operations/inbox/:id",
      redirect: (to) => ({ path: "/inbox/" + to.params.id, query: to.query }),
    },
    {
      path: "/settings/preferences",
      redirect: "/settings/profile?view=notifications",
    },
    {
      path: "/settings/preferences/:id",
      redirect: "/settings/profile?view=notifications",
    },
    {
      path: "/operations",
      component: () => import("./views/OperationsView.vue"),
      meta: { title: "Operations" },
    },
    {
      path: "/settings/profile",
      component: () => import("./views/ProfileView.vue"),
      meta: { title: "Profile" },
    },
    hub("/settings", "Settings", ["operators", "roles", "features"]),
    {
      path: "/changes/new",
      component: () => import("./views/ChangeView.vue"),
      meta: { title: "Change subscriptions" },
    },
    ...["repricing", "rollouts"].map((kind) => ({
      path: "/operations/" + kind + "/new",
      component: () => import("./views/ExecutionCreateView.vue"),
      meta: {
        title:
          kind === "repricing"
            ? "Reprice subscriptions"
            : "Apply plan revision",
        kind,
      },
    })),
    ...Object.values(resources)
      .filter((r) => r.key !== "invoices" && r.key !== "preferences")
      .flatMap((r) => [
        ...(r.key === "inbox"
          ? []
          : [
              {
                path: r.base,
                redirect: (to: any) => ({
                  path: "/" + r.base.split("/")[1],
                  query: ["jobs", "repricing", "rollouts"].includes(r.key)
                    ? { ...to.query, view: "executions", kind: r.key }
                    : { ...to.query, view: r.key },
                }),
              },
            ]),
        ...(!r.listOnly
          ? [
              {
                path: r.base + "/:id",
                component: () => import("./views/ResourceView.vue"),
                meta: { title: r.singular, resource: r.key },
              },
            ]
          : []),
      ]),
    {
      path: "/operations/:id",
      component: () => import("./views/OperationView.vue"),
      meta: { title: "Subscription change" },
    },
    {
      path: "/login",
      component: () => import("./views/LoginView.vue"),
      meta: { title: "Sign in", public: true },
    },
    ...[
      {
        path: "/auth/activation",
        auth: "activation",
        alias: "/admin/activation/complete",
      },
      {
        path: "/auth/password-reset",
        auth: "reset",
        alias: "/admin/password-reset/complete",
      },
      {
        path: "/auth/email-verification",
        auth: "verification",
        alias: "/admin/email-verification/complete",
      },
      { path: "/auth/initial-password", auth: "initial" },
    ].map((x) => ({
      ...x,
      component: () => import("./views/AuthView.vue"),
      meta: { title: "Account access", auth: x.auth, public: true },
    })),
    {
      path: "/:pathMatch(.*)*",
      component: () => import("./views/NotFoundView.vue"),
      meta: { title: "Page not found" },
    },
  ],
});
router.beforeEach((to, from) => {
  scrollPositions.set(from.fullPath, { top: window.scrollY });
  if (to.path === "/operations") {
    const view = String(to.query.view || "");
    if (view === "inbox")
      return { path: "/inbox", query: { ...to.query, view: undefined } };
    if (view === "messages")
      return { path: "/customers", query: { ...to.query, view: "messages" } };
    if (view === "health")
      return { path: "/settings", query: { ...to.query, view: "health" } };
    if (["jobs", "repricing", "rollouts"].includes(view))
      return {
        path: to.path,
        query: { ...to.query, view: "executions", kind: view },
      };
    if (view === "attention")
      return { path: to.path, query: { ...to.query, view: "queue" } };
    if (["notification-events", "notification-emails"].includes(view))
      return {
        path: to.path,
        query: { ...to.query, view: "delivery", delivery: view },
      };
  }
  if (to.path === "/settings" && to.query.view === "preferences")
    return "/settings/profile?view=notifications";
  if (!session.token && !to.meta.public)
    return { path: "/login", query: { returnTo: to.fullPath } };
  if (session.initialAccessToken && !session.token && to.path === "/login")
    return "/auth/initial-password";
});
router.afterEach(
  (to) => (document.title = String(to.meta.title || "Workspace") + " · Hive"),
);
