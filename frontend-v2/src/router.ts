import { createRouter, createWebHistory } from "vue-router";
import { session } from "./data/session";
import { resources } from "./resources";
const hub = (path: string, title: string, keys: string[]) => ({
  path,
  component: () => import("./views/HubView.vue"),
  meta: { title, resources: keys },
});
export const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: "/", redirect: "/overview" },
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
      component: () => import("./views/CustomersView.vue"),
      meta: { title: "Customers" },
    },
    {
      path: "/customers/:id",
      component: () => import("./views/CustomerView.vue"),
      meta: { title: "Customer" },
    },
    hub("/catalog", "Catalog", ["plans", "addons", "capacity", "prices"]),
    hub("/commercial", "Commercial", [
      "campaigns",
      "offers",
      "segments",
      "policies",
    ]),
    {
      path: "/billing",
      component: () => import("./views/BillingView.vue"),
      meta: { title: "Billing" },
    },
    {
      path: "/billing/:id",
      component: () => import("./views/InvoiceView.vue"),
      meta: { title: "Invoice" },
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
    hub("/settings", "Settings", [
      "operators",
      "roles",
      "features",
      "preferences",
    ]),
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
      .filter((r) => r.key !== "invoices")
      .flatMap((r) => [
        {
          path: r.base,
          redirect: () => ({
            path: "/" + r.base.split("/")[1],
            query: { view: r.key },
          }),
        },
        {
          path: r.base + "/:id",
          component: () => import("./views/ResourceView.vue"),
          meta: { title: r.singular, resource: r.key },
        },
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
router.beforeEach((to) => {
  if (!session.token && !to.meta.public)
    return { path: "/login", query: { returnTo: to.fullPath } };
  if (session.initialAccessToken && !session.token && to.path === "/login")
    return "/auth/initial-password";
});
router.afterEach(
  (to) => (document.title = String(to.meta.title || "Workspace") + " · Hive"),
);
