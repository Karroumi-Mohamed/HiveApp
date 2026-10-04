export function destination(path: string | undefined | null) {
  if (!path) return "/operations";
  if (path === "/admin" || path === "/admin/") return "/overview";
  if (/^\/admin\/subscription-(?:change-)?jobs\/new(?:\?|$)/.test(path))
    return "/changes/new";
  if (/^\/admin\/notifications\/delivery(?:\?|$)/.test(path))
    return "/operations?view=notification-events";
  if (/^\/admin\/analytics(?:\?|$)/.test(path)) return "/overview?view=reports";
  if (/^\/admin\/observability(?:\?|$)/.test(path))
    return "/settings?view=health";
  if (/^\/admin\/billing\/invoices\/([^/?]+)\/document/.test(path))
    return path.replace(
      /^\/admin\/billing\/invoices\/([^/?]+)\/document.*/,
      "/customers/invoices/$1?view=document",
    );
  if (/^\/admin\/billing\/provider-events(?:\?|$)/.test(path))
    return "/billing?view=provider-events";
  if (/^\/admin\/billing\/reconciliation(?:\?|$)/.test(path))
    return "/billing?view=provider-commands";
  if (/^\/admin\/subscriptions\/agreements(?:\?|$)/.test(path))
    return "/customers?view=agreements";
  path = path.replace(
    /^\/admin\/subscriptions\/account\//,
    "/admin/subscriptions/",
  );
  const replacements: [RegExp, string][] = [
    [
      /^\/admin\/subscriptions\/([^/?]+)\/agreements\/([^/?]+)/,
      "/customers/$1/agreements/$2",
    ],
    [/^\/admin\/subscriptions\/([^/?]+)/, "/customers/$1"],
    [/^\/admin\/subscription-(?:change-)?jobs/, "/operations"],
    [/^\/admin\/billing\/invoices/, "/customers/invoices"],
    [/^\/admin\/billing/, "/billing"],
    [/^\/admin\/plans/, "/catalog/plans"],
    [/^\/admin\/add-ons/, "/catalog/addons"],
    [/^\/admin\/quota-packages/, "/catalog/capacity"],
    [/^\/admin\/price-books/, "/catalog/prices"],
    [/^\/admin\/campaigns/, "/commercial/campaigns"],
    [/^\/admin\/segments/, "/commercial/segments"],
    [/^\/admin\/commercial-policies/, "/commercial/policies"],
    [/^\/admin\/offers/, "/commercial/offers"],
    [/^\/admin\/(?:subscription-)?repricing/, "/operations/repricing"],
    [/^\/admin\/customer-communications/, "/customers/communications"],
    [/^\/admin\/communications/, "/operations/delivery"],
    [/^\/admin\/activities/, "/operations/activity"],
    [/^\/admin\/notifications/, "/inbox"],
    [/^\/admin\/plan-version-applications/, "/operations/rollouts"],
    [/^\/admin\/operators/, "/settings/operators"],
    [/^\/admin\/roles/, "/settings/roles"],
    [/^\/admin\/features/, "/settings/features"],
  ];
  for (const [pattern, to] of replacements)
    if (pattern.test(path)) {
      const mapped = path.replace(pattern, to);
      const detail =
        /^(\/(?:catalog|commercial|settings|operations|customers)\/[^/]+\/[^/?]+)\/([^/?]+)(.*)$/.exec(
          mapped,
        );
      if (detail)
        return (
          detail[1] +
          (detail[2] === "edit"
            ? "?edit=1"
            : "?view=" + encodeURIComponent(detail[2]!))
        );
      return mapped.replace(/\/edit(?:\?.*)?$/, "?edit=1");
    }
  return path.startsWith("/admin")
    ? "/operations"
    : path.startsWith("/") && !path.startsWith("//")
      ? path
      : "/operations";
}
