import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { adminApi } from "@/api/admin-api";
import type { Plan } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { StatusBadge } from "@/components/patterns/status-badge";
import {
  cycleText,
  featureModePresentation,
  money,
  planTone,
  statusText,
} from "@/features/admin/plans/plan-presentation";
import {
  buildPlanSchemaModel,
  SCHEMA,
  shouldShowNoCommercialExtensions,
} from "@/features/admin/plans/plan-schema-model";
import { adminCommercialKeys, commercialQueryEnabled } from "@/features/commercial/commercial-query";

/**
 * The plan as a wiring diagram: the plan node feeds its features; each feature shows what it
 * grants; dashed nodes are the commercial extensions — quota packages and add-ons — attached to
 * this plan, including packages sold through an add-on. Isolating a feature keeps its whole
 * chain lit (packages, carrying add-ons) and expands its permissions into a connected node.
 */

const extensionStatusText: Record<string, string> = {
  DRAFT: "Brouillon",
  ACTIVE: "Actif",
  INACTIVE: "Inactif",
  ARCHIVED: "Archivé",
};

function edgePath(x1: number, y1: number, x2: number, y2: number) {
  const bend = (x2 - x1) / 2;
  return `M ${x1} ${y1} C ${x1 + bend} ${y1}, ${x2 - bend} ${y2}, ${x2} ${y2}`;
}

export function PlanSchema({ plan }: { plan: Plan }) {
  const session = useAdminSession();
  // Focus is a toggle on real buttons (click, Enter, Space), so isolation is keyboard- and
  // touch-reachable, never hover-only.
  const [focus, setFocus] = useState<string | null>(null);
  const features = useQuery({
    queryKey: adminCommercialKeys.plans.features(plan.id),
    queryFn: () => adminApi.planFeatures(plan.id),
    enabled: commercialQueryEnabled(session.can, adminPermissions.plansListFeatures),
  });
  const canReadCatalog = session.can(adminPermissions.registryFeatureCatalog);
  const catalog = useQuery({
    queryKey: adminCommercialKeys.registry.featureCatalog("PLAN_ASSIGNABLE"),
    queryFn: () => adminApi.featureCatalog("PLAN_ASSIGNABLE"),
    enabled: commercialQueryEnabled(session.can, adminPermissions.registryFeatureCatalog),
  });
  const canSeePackages = session.can(adminPermissions.quotaPackagesList);
  const packages = useQuery({
    queryKey: adminCommercialKeys.quotaPackages.list(),
    queryFn: adminApi.quotaPackages,
    enabled: commercialQueryEnabled(session.can, adminPermissions.quotaPackagesList),
  });
  const canSeeAddOns = session.can(adminPermissions.addOnsList);
  const addOns = useQuery({
    queryKey: adminCommercialKeys.addOns.list(),
    queryFn: adminApi.addOns,
    enabled: commercialQueryEnabled(session.can, adminPermissions.addOnsList),
  });

  const model = useMemo(
    () =>
      buildPlanSchemaModel({
        plan: { code: plan.code, currencyCode: plan.currencyCode, billingCycle: plan.billingCycle },
        features: features.data ?? [],
        catalogFeatures: (catalog.data ?? []).flatMap((module) => module.features),
        catalogComplete: canReadCatalog && catalog.isSuccess,
        packages: packages.data ?? [],
        addOns: addOns.data ?? [],
      }),
    [
      plan.code,
      plan.currencyCode,
      plan.billingCycle,
      features.data,
      catalog.data,
      canReadCatalog,
      catalog.isSuccess,
      packages.data,
      addOns.data,
    ],
  );

  const extensionsLoading = (canSeePackages && packages.isLoading) || (canSeeAddOns && addOns.isLoading);
  if (features.isLoading || (canReadCatalog && catalog.isLoading) || extensionsLoading) {
    return <LoadingState rows={4} />;
  }
  if (features.isError) return <ErrorState retry={() => void features.refetch()} />;
  if (!model.rows.length)
    return (
      <p className="border-y py-10 text-center text-sm text-muted-foreground">
        Aucune fonctionnalité à représenter — composez d’abord le forfait.
      </p>
    );

  const planCy = model.height / 2;
  const litFeature = (featureCode: string) => focus === null || focus === featureCode;
  const litRelated = (related: string[]) => focus === null || related.includes(focus);
  const width = SCHEMA.permissionsX + SCHEMA.permissionsW + 4;
  const focusedRow = model.rows.find((row) => row.feature.featureCode === focus);
  const focusedPermissions = model.permissionsNodes.find((node) => node.featureCode === focus);
  const hiddenParts = [
    canReadCatalog ? null : "permissions et unités (catalogue non lisible)",
    canSeePackages ? null : "paquets de quotas",
    canSeeAddOns ? null : "add-ons",
  ].filter((part): part is string => part !== null);
  // A failed extension query must read as a failure, never as "this plan has no extensions".
  const failedParts = [
    packages.isError ? "paquets de quotas" : null,
    addOns.isError ? "add-ons" : null,
    catalog.isError ? "catalogue des fonctionnalités" : null,
  ].filter((part): part is string => part !== null);

  return (
    <section className="rounded-xl border bg-card p-5">
      <div className="mb-4">
        <h2 className="text-sm font-semibold">Schéma du forfait</h2>
        <p className="mt-1 text-xs text-muted-foreground">
          Ce que le forfait accorde, et les extensions commerciales qui s’y attachent. Cliquez une fonctionnalité pour
          isoler sa chaîne et déplier ses permissions
          {hiddenParts.length ? ` — masqué : ${hiddenParts.join(", ")}` : ""}.
        </p>
        {failedParts.length ? (
          <p className="mt-2 text-xs text-warning">
            Chargement échoué : {failedParts.join(", ")} — le schéma est incomplet.{" "}
            <button
              className="underline underline-offset-2"
              onClick={() => {
                if (packages.isError) void packages.refetch();
                if (addOns.isError) void addOns.refetch();
                if (catalog.isError) void catalog.refetch();
              }}
              type="button"
            >
              Réessayer
            </button>
          </p>
        ) : null}
      </div>
      {/* A schematic reads as a diagram, not prose: the canvas stays LTR even in RTL. */}
      <div className="overflow-x-auto" dir="ltr">
        <div className="relative" style={{ height: model.height, minWidth: width }}>
          <svg aria-hidden="true" className="absolute inset-0 size-full text-muted-foreground/25">
            {model.rows.map(({ feature, y, height }) => (
              <path
                className={litFeature(feature.featureCode) ? "" : "opacity-25"}
                d={edgePath(SCHEMA.planX + SCHEMA.planW, planCy, SCHEMA.featureX, y + height / 2)}
                fill="none"
                key={feature.id}
                stroke="currentColor"
                strokeWidth={focus === feature.featureCode ? 2 : 1.25}
              />
            ))}
            {model.packageNodes.map((node) => {
              const start =
                node.via.type === "feature"
                  ? model.rows.find((entry) => entry.feature.featureCode === node.via.code)
                  : model.addOnNodes.find((entry) => entry.addOn.code === node.via.code);
              if (!start) return null;
              return (
                <path
                  className={litRelated(node.relatedFeatureCodes) ? "" : "opacity-25"}
                  d={edgePath(
                    (node.via.type === "feature" ? SCHEMA.featureX + SCHEMA.featureW : SCHEMA.extensionX) +
                      (node.via.type === "feature" ? 0 : 12),
                    start.y + start.height / 2,
                    SCHEMA.extensionX,
                    node.y + node.height / 2,
                  )}
                  fill="none"
                  key={node.pkg.id}
                  stroke="currentColor"
                  strokeDasharray="4 3"
                  strokeWidth={focus !== null && node.relatedFeatureCodes.includes(focus) ? 2 : 1.25}
                />
              );
            })}
            {model.addOnNodes.map((node) => (
              <path
                className={litRelated(node.featureCodes) ? "" : "opacity-25"}
                d={edgePath(SCHEMA.planX + SCHEMA.planW, planCy, SCHEMA.extensionX, node.y + node.height / 2)}
                fill="none"
                key={node.addOn.id}
                stroke="currentColor"
                strokeDasharray="2 4"
                strokeWidth={1.25}
              />
            ))}
            {focusedRow && focusedPermissions ? (
              <path
                d={edgePath(
                  SCHEMA.featureX + SCHEMA.featureW,
                  focusedRow.y + focusedRow.height / 2,
                  SCHEMA.permissionsX,
                  focusedPermissions.y + focusedPermissions.height / 2,
                )}
                fill="none"
                stroke="currentColor"
                strokeWidth={2}
              />
            ) : null}
          </svg>

          <article
            className="absolute rounded-lg border bg-background p-4 shadow-sm"
            style={{ insetInlineStart: SCHEMA.planX, top: planCy - 52, width: SCHEMA.planW }}
          >
            <div className="flex items-start justify-between gap-2">
              <h3 className="min-w-0 truncate text-sm font-semibold">{plan.name}</h3>
              <StatusBadge tone={planTone[plan.status]}>{statusText[plan.status]}</StatusBadge>
            </div>
            <p className="mt-2 text-lg font-semibold tabular-nums">
              {money(plan.price, plan.currencyCode)}
              <span className="ms-1 text-xs font-normal text-muted-foreground">/ {cycleText[plan.billingCycle]}</span>
            </p>
          </article>

          {model.rows.map(({ feature, definition, quotaLines, permissionPreview, permissionOverflow, y, height }) => (
            <button
              aria-pressed={focus === feature.featureCode}
              className={`absolute rounded-lg border bg-background p-3 text-start shadow-sm transition-opacity focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${
                litFeature(feature.featureCode) ? "" : "opacity-40"
              }`}
              key={feature.id}
              onClick={() => setFocus((current) => (current === feature.featureCode ? null : feature.featureCode))}
              style={{ insetInlineStart: SCHEMA.featureX, top: y, width: SCHEMA.featureW, minHeight: height }}
              type="button"
            >
              <span className="flex items-start justify-between gap-2">
                <span className="min-w-0 truncate text-sm font-medium" title={feature.featureCode}>
                  {definition?.displayName ?? feature.featureCode}
                </span>
                <span className="shrink-0 text-[10px] text-muted-foreground">
                  {featureModePresentation[feature.mode]?.label ?? feature.mode}
                </span>
              </span>
              {permissionPreview.length ? (
                <span className="mt-1.5 block truncate text-xs text-muted-foreground">
                  {permissionPreview.join(" · ")}
                  {permissionOverflow > 0 ? ` · +${permissionOverflow} autres` : ""}
                </span>
              ) : (
                <span className="mt-1.5 block text-xs text-muted-foreground">
                  {canReadCatalog ? "Aucune permission" : "Permissions non lisibles"}
                </span>
              )}
              {quotaLines.map((line) => (
                <span
                  className={`block text-xs ${
                    line.startsWith("À définir")
                      ? "text-warning"
                      : line.startsWith("Illimité") || line.startsWith("Sans")
                        ? "text-muted-foreground"
                        : "tabular-nums"
                  }`}
                  key={line}
                >
                  {line}
                </span>
              ))}
            </button>
          ))}

          {model.packageNodes.map((node) => (
            <article
              className={`absolute rounded-lg border border-dashed bg-background p-3 transition-opacity ${
                litRelated(node.relatedFeatureCodes) ? "" : "opacity-40"
              } ${node.pkg.status !== "ACTIVE" ? "opacity-55" : ""}`}
              key={`${node.pkg.id}-${node.via.type}-${node.via.code}`}
              style={{
                insetInlineStart: SCHEMA.extensionX,
                top: node.y,
                width: SCHEMA.extensionW,
                minHeight: node.height,
              }}
            >
              <div className="flex items-start justify-between gap-2">
                <h3 className="min-w-0 truncate text-sm font-medium">{node.pkg.name}</h3>
                <span className="shrink-0 text-[10px] text-muted-foreground">
                  {extensionStatusText[node.pkg.status] ?? node.pkg.status}
                </span>
              </div>
              <p className="mt-1 text-xs tabular-nums">
                +{node.pkg.capacityPerUnit} {node.resourceLabel}
                {node.via.type === "addOn" ? <span className="text-muted-foreground"> · via add-on</span> : null}
              </p>
              <p className="text-xs text-muted-foreground">
                {money(node.pkg.price, node.pkg.currencyCode)} / {cycleText[node.pkg.billingCycle]}
              </p>
            </article>
          ))}

          {model.addOnNodes.map((node) => (
            <article
              className={`absolute rounded-lg border border-dashed bg-background p-3 transition-opacity ${
                litRelated(node.featureCodes) ? "" : "opacity-40"
              } ${node.availability !== "AVAILABLE" ? "opacity-55" : ""}`}
              key={node.addOn.id}
              style={{
                insetInlineStart: SCHEMA.extensionX,
                top: node.y,
                width: SCHEMA.extensionW,
                minHeight: node.height,
              }}
            >
              <div className="flex items-start justify-between gap-2">
                <h3 className="min-w-0 truncate text-sm font-medium">{node.addOn.name}</h3>
                <span className="shrink-0 text-[10px] text-muted-foreground">
                  Add-on · {extensionStatusText[node.addOn.status] ?? node.addOn.status}
                  {node.availability === "UNAVAILABLE"
                    ? " · Indisponible"
                    : node.availability === "UNKNOWN"
                      ? " · Non vérifié"
                      : ""}
                </span>
              </div>
              <p className="mt-1 truncate text-xs text-muted-foreground" title={node.featureNames.join(", ")}>
                {node.featureNames.slice(0, 3).join(" · ") || "Sans fonctionnalité"}
                {node.featureNames.length > 3 ? ` · +${node.featureNames.length - 3}` : ""}
              </p>
              {node.notes.map((note) => (
                <p className="truncate text-xs text-muted-foreground" key={note} title={note}>
                  {note}
                </p>
              ))}
              <p className="text-xs text-muted-foreground">
                {money(node.addOn.price, node.addOn.currencyCode)} / {cycleText[node.addOn.billingCycle]}
              </p>
            </article>
          ))}

          {focusedPermissions ? (
            <article
              className="absolute rounded-lg border bg-background p-3 shadow-sm"
              style={{
                insetInlineStart: SCHEMA.permissionsX,
                top: focusedPermissions.y,
                width: SCHEMA.permissionsW,
                minHeight: focusedPermissions.height,
              }}
            >
              <h3 className="text-sm font-medium">Permissions accordées</h3>
              {focusedPermissions.labels.length ? (
                focusedPermissions.labels.map((label) => (
                  <p className="text-xs text-muted-foreground" key={label}>
                    {label}
                  </p>
                ))
              ) : (
                <p className="text-xs text-muted-foreground">
                  {canReadCatalog ? "Aucune permission" : "Catalogue non lisible"}
                </p>
              )}
              {focusedPermissions.overflow > 0 ? (
                <p className="text-xs text-muted-foreground">+{focusedPermissions.overflow} autres</p>
              ) : null}
            </article>
          ) : null}
        </div>
      </div>
      {shouldShowNoCommercialExtensions({
        packageNodeCount: model.packageNodes.length,
        addOnNodeCount: model.addOnNodes.length,
        canSeePackages,
        canSeeAddOns,
        extensionsLoading,
        hasFailures: failedParts.length > 0,
      }) ? (
        <p className="mt-4 border-t pt-3 text-xs text-muted-foreground">
          Aucune extension commerciale attachée. Les paquets de quotas (Commercial → Quotas) se relient par
          fonctionnalité, ressource et forfaits autorisés ; les add-ons par forfaits autorisés, à devise et cycle
          compatibles.
        </p>
      ) : null}
    </section>
  );
}
