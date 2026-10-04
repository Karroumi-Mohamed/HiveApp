import { adminPermissions as p } from "@/auth/permissions";
import { can, session } from "@/data/session";

export type NavigationEntry = {
  path: string;
  title: string;
  icon:
    | "overview"
    | "customers"
    | "catalog"
    | "commercial"
    | "billing"
    | "operations"
    | "settings"
    | "mail"
    | "activity"
    | "bell";
  permissions: string[];
};
export const workspaceNavigation: NavigationEntry[] = [
  {
    path: "/overview",
    title: "Overview",
    icon: "overview",
    permissions: [
      p.plansOverview,
      p.analyticsReadSummary,
      p.analyticsReadSubscriptionSeries,
      p.analyticsReadOfferSeries,
    ],
  },
  {
    path: "/customers",
    title: "Customers",
    icon: "customers",
    permissions: [
      p.subscriptionsSearch,
      p.subscriptionsChooseAccounts,
      p.subscriptionsChooseChangeOptions,
      p.subscriptionsPreviewChange,
      p.subscriptionsApplyChange,
      p.subscriptionsSearchSpecialAgreements,
      p.customerCommunicationsRead,
      p.customerCommunicationsCreate,
      p.billingListInvoices,
    ],
  },
  {
    path: "/catalog",
    title: "Catalog",
    icon: "catalog",
    permissions: [
      p.plansList,
      p.plansListFamilies,
      p.addOnsList,
      p.quotaPackagesList,
      p.priceBooksList,
      p.plansCreate,
      p.addOnsCreate,
      p.quotaPackagesCreate,
      p.priceBooksCreate,
    ],
  },
  {
    path: "/commercial",
    title: "Commercial",
    icon: "commercial",
    permissions: [
      p.campaignsList,
      p.segmentsList,
      p.commercialPoliciesList,
      p.offersList,
      p.campaignsCreate,
      p.segmentsCreate,
      p.commercialPoliciesCreate,
      p.offersCreate,
    ],
  },
  {
    path: "/operations",
    title: "Operations",
    icon: "operations",
    permissions: [
      p.subscriptionsListChangeJobs,
      p.subscriptionsPreviewChangeJob,
      p.repricingList,
      p.repricingPreview,
      p.plansListApplications,
      p.plansCreateApplication,
      p.analyticsReadOperations,
      p.activitiesRead,
      p.communicationsRead,
      p.notificationsDelivery,
    ],
  },
];
export const settingsPermissions = [
  p.usersCreate,
  p.rolesCreate,
  p.rolesCreateFromPreset,
  p.usersRead,
  p.rolesRead,
  p.registryFeatureCatalog,
  p.observabilityReadHealth,
  p.observabilityReadBacklogs,
  p.observabilityReadLogAccess,
  p.registrySync,
];
export const taskNavigation: NavigationEntry[] = [
  ...workspaceNavigation,
  {
    path: "/changes/new",
    title: "Create subscription change",
    icon: "operations",
    permissions: [p.subscriptionsPreviewChangeJob],
  },
  {
    path: "/operations/repricing/new",
    title: "Reprice subscriptions",
    icon: "operations",
    permissions: [p.repricingPreview],
  },
  {
    path: "/operations/rollouts/new",
    title: "Apply plan revision",
    icon: "operations",
    permissions: [p.plansCreateApplication],
  },
  {
    path: "/settings/operators/new",
    title: "Create operator",
    icon: "customers",
    permissions: [p.usersCreate],
  },
  {
    path: "/settings/roles/new",
    title: "Create access role",
    icon: "settings",
    permissions: [p.rolesCreate, p.rolesCreateFromPreset],
  },
  {
    path: "/customers?view=agreements",
    title: "Customer agreements",
    icon: "customers",
    permissions: [p.subscriptionsSearchSpecialAgreements],
  },
  {
    path: "/customers?view=messages",
    title: "Customer communications",
    icon: "mail",
    permissions: [p.customerCommunicationsRead, p.customerCommunicationsCreate],
  },
  {
    path: "/customers?view=invoices",
    title: "Invoices",
    icon: "billing",
    permissions: [p.billingListInvoices],
  },
  {
    path: "/catalog?view=plans",
    title: "Plan families and revisions",
    icon: "catalog",
    permissions: [p.plansListFamilies, p.plansList, p.plansCreate],
  },
  {
    path: "/catalog?view=prices",
    title: "Prices",
    icon: "billing",
    permissions: [p.priceBooksList, p.priceBooksCreate],
  },
  {
    path: "/commercial?view=offers",
    title: "Offers",
    icon: "commercial",
    permissions: [p.offersList, p.offersCreate],
  },
  {
    path: "/commercial?view=campaigns",
    title: "Campaigns",
    icon: "commercial",
    permissions: [p.campaignsList, p.campaignsCreate],
  },
  {
    path: "/commercial?view=segments",
    title: "Audiences",
    icon: "customers",
    permissions: [p.segmentsList, p.segmentsCreate],
  },
  {
    path: "/commercial?view=policies",
    title: "Commercial rules",
    icon: "settings",
    permissions: [p.commercialPoliciesList, p.commercialPoliciesCreate],
  },
  {
    path: "/operations?view=executions&kind=jobs",
    title: "Subscription change executions",
    icon: "operations",
    permissions: [p.subscriptionsListChangeJobs],
  },
  {
    path: "/operations?view=executions&kind=repricing",
    title: "Repricing executions",
    icon: "operations",
    permissions: [p.repricingList],
  },
  {
    path: "/operations?view=executions&kind=rollouts",
    title: "Plan revision applications",
    icon: "operations",
    permissions: [p.plansListApplications],
  },
  {
    path: "/operations?view=delivery",
    title: "Delivery",
    icon: "mail",
    permissions: [p.communicationsRead, p.notificationsDelivery],
  },
  {
    path: "/operations?view=activity",
    title: "Audit activity",
    icon: "activity",
    permissions: [p.activitiesRead],
  },
  {
    path: "/inbox",
    title: "Personal inbox",
    icon: "bell",
    permissions: [p.notificationsRead],
  },
  {
    path: "/settings",
    title: "Platform settings",
    icon: "settings",
    permissions: settingsPermissions,
  },
  {
    path: "/settings?view=health",
    title: "System status",
    icon: "settings",
    permissions: [
      p.observabilityReadHealth,
      p.observabilityReadBacklogs,
      p.observabilityReadLogAccess,
      p.registrySync,
    ],
  },
];

export function safeReturnTo(value: unknown): string | undefined {
  if (
    typeof value !== "string" ||
    value.length > 8192 ||
    !value.startsWith("/") ||
    value.startsWith("//") ||
    /[\\\u0000-\u001f]/.test(value)
  )
    return undefined;
  const url = new URL(value, window.location.origin);
  if (
    url.origin !== window.location.origin ||
    !/^\/(overview|customers|catalog|commercial|operations|settings|inbox|changes|billing)(\/|$)/.test(
      url.pathname,
    )
  )
    return undefined;
  return url.pathname + url.search + url.hash;
}

export function withReturnTo(path: string, origin: string): string {
  const target = safeReturnTo(path);
  if (!target) return "/overview";
  const url = new URL(target, window.location.origin);
  const returnTo = safeReturnTo(origin);
  if (returnTo && target !== returnTo)
    url.searchParams.set("returnTo", returnTo);
  return url.pathname + url.search + url.hash;
}

export function contextualPath(
  path: string,
  route: { fullPath: string; query: Record<string, unknown> },
): string {
  return withReturnTo(
    path,
    safeReturnTo(route.query.returnTo) || route.fullPath,
  );
}

export function workspacePath(path: string, returnTo?: unknown): string {
  const source = safeReturnTo(returnTo);
  const ownedPath =
    source && (/\/new$/.test(path) || path.startsWith("/changes"))
      ? source
      : path;
  if (ownedPath.startsWith("/billing")) return "/customers";
  if (ownedPath.startsWith("/changes")) return "/customers";
  return (
    workspaceNavigation.find((entry) => ownedPath.startsWith(entry.path))
      ?.path || (ownedPath.startsWith("/inbox") ? "/inbox" : "/settings")
  );
}

export function firstWorkspace(): string {
  if (
    !can(...workspaceNavigation.flatMap((entry) => entry.permissions)) &&
    !can(...settingsPermissions) &&
    !can(p.notificationsRead) &&
    !session.me
  )
    return "/overview";
  return (
    workspaceNavigation.find((entry) => can(...entry.permissions))?.path ||
    (can(...settingsPermissions)
      ? "/settings"
      : can(p.notificationsRead)
        ? "/inbox"
        : "/settings/profile")
  );
}
