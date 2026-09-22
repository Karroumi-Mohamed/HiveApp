import { ArrowLeftIcon, GitBranchIcon } from "@phosphor-icons/react";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams, useSearchParams } from "react-router";
import { adminApi } from "@/api/admin-api";
import type { PlanFeature, PlanOperationalItem } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable } from "@/components/patterns/data-table";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { ProductPriceOptions } from "@/features/admin/price-books/product-price-summary";
import { adminCommercialKeys } from "@/features/commercial/commercial-query";
import { PlanStatusTag, SelectPlanPublicVersion } from "./plan-version-controls";

const column = createDataColumns<PlanOperationalItem>();

export function PlanVersionsPage() {
  const { planId = "" } = useParams();
  const { t } = useTranslation();
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get("page")) || 0);
  const [selection, setSelection] = useState<string[]>([]);
  const query = useQuery({
    queryKey: [...adminCommercialKeys.plans.all(), planId, "versions", page],
    queryFn: () => adminApi.planVersions(planId, { page, size: 20 }),
    enabled: session.can(adminPermissions.plansListVersions) && !!planId,
  });
  if (query.isLoading) return <LoadingState />;
  if (query.isError || !query.data) return <ErrorState retry={() => void query.refetch()} />;
  const data = query.data;
  const canOpen = session.can(adminPermissions.plansReadDetail);
  const columns = column.columns([
    ...(session.can(adminPermissions.plansCompareVersions)
      ? [
          column.display({
            id: "select",
            header: "",
            cell: ({ row }) => (
              <div className="flex min-h-11 items-center">
                <Checkbox
                  aria-label={t("planVersions.selectVersion", { number: row.original.revisionNumber })}
                  checked={selection.includes(row.original.id)}
                  disabled={selection.length === 2 && !selection.includes(row.original.id)}
                  onCheckedChange={(checked) =>
                    setSelection((previous) =>
                      checked ? [...previous, row.original.id] : previous.filter((id) => id !== row.original.id),
                    )
                  }
                />
              </div>
            ),
          }),
        ]
      : []),
    column.accessor("revisionNumber", {
      header: t("planVersions.versions"),
      cell: ({ row }) => (
        <div className="space-y-1">
          <span className="font-semibold">{t("planVersions.version", { number: row.original.revisionNumber })}</span>
          <p className="text-xs text-muted-foreground">{row.original.name}</p>
        </div>
      ),
    }),
    column.accessor("status", {
      header: t("planVersions.status"),
      cell: ({ getValue }) => <PlanStatusTag status={getValue()} />,
    }),
    column.display({
      id: "use",
      header: t("planVersions.role"),
      cell: ({ row }) =>
        row.original.id === data.publicPlanId ? (
          <span className="font-medium text-primary">{t("planVersions.publicShort")}</span>
        ) : row.original.status === "DRAFT" ? (
          t("planVersions.DRAFT")
        ) : (
          t("planVersions.retained")
        ),
    }),
    ...(session.can(adminPermissions.plansListSubscribers)
      ? [
          column.accessor("currentSubscriberCount", {
            header: t("planVersions.subscribers"),
            cell: ({ getValue }) => <span className="tabular-nums">{getValue()}</span>,
          }),
        ]
      : []),
    column.display({
      id: "actions",
      header: t("planVersions.actions"),
      cell: ({ row }) => (
        <div className="flex flex-wrap items-center gap-2">
          {canOpen && (
            <Button asChild variant="ghost" size="sm">
              <Link to={`/admin/plans/${row.original.id}`}>
                {row.original.status === "DRAFT" ? t("planVersions.continueDraft") : t("planVersions.open")}
              </Link>
            </Button>
          )}
          {session.can(adminPermissions.plansSelectPublicVersion) &&
            row.original.id !== data.publicPlanId &&
            row.original.status === "ACTIVE" &&
            row.original.salesVisibility === "PUBLIC" && (
              <SelectPlanPublicVersion plan={row.original} catalogRevision={data.catalogRevision} />
            )}
        </div>
      ),
    }),
  ]);
  return (
    <div className="space-y-5">
      <Button asChild variant="ghost" size="sm">
        <Link to={canOpen ? `/admin/plans/${planId}` : "/admin/plans"}>
          <ArrowLeftIcon className="rtl:rotate-180" />
          {t(canOpen ? "planVersions.backToPlan" : "planVersions.plans")}
        </Link>
      </Button>
      <PageHeader
        title={t("planVersions.versions")}
        description={t("planVersions.versionHint")}
        actions={
          canOpen &&
          (data.draftPlanId ? (
            <Button asChild>
              <Link to={`/admin/plans/${data.draftPlanId}`}>{t("planVersions.continueDraft")}</Link>
            </Button>
          ) : (
            session.can(adminPermissions.plansRevise) && (
              <Button asChild>
                <Link to={`/admin/plans/${planId}?createVersion=1`}>
                  <GitBranchIcon />
                  {t("planVersions.createVersion")}
                </Link>
              </Button>
            )
          ))
        }
      />
      <section className="overflow-hidden rounded-xl border bg-card">
        {session.can(adminPermissions.plansCompareVersions) && (
          <div className="flex flex-wrap items-center justify-between gap-3 border-b p-4">
            <p className="text-sm text-muted-foreground">{t("planVersions.compareHint")}</p>
            {selection.length === 2 ? (
              <Button asChild size="sm">
                <Link to={`/admin/plans/${planId}/versions/compare?source=${selection[0]}&target=${selection[1]}`}>
                  {t("planVersions.compare")}
                </Link>
              </Button>
            ) : (
              <Button size="sm" disabled>
                {t("planVersions.compare")}
              </Button>
            )}
          </div>
        )}
        <DataTable columns={columns} data={data.versions.content} getRowId={(row) => row.id} />
        <PaginationBar
          page={page}
          totalPages={data.versions.totalPages}
          totalElements={data.versions.totalElements}
          onPageChange={(next) => setParams({ page: String(next) })}
        />
      </section>
    </div>
  );
}

function FeatureTerms({ feature }: { feature: PlanFeature | null }) {
  const { t } = useTranslation();
  if (!feature) return <span className="text-muted-foreground">{t("planVersions.absent")}</span>;
  return (
    <div className="space-y-2">
      <p>{t(`planVersions.${feature.mode}`)}</p>
      <ul className="space-y-1 text-xs text-muted-foreground">
        {feature.quotaConfigs.map((quota) => (
          <li key={quota.resource}>
            {quota.resource} · {quota.mode === "UNLIMITED" ? t("planVersions.unlimited") : quota.limit}
          </li>
        ))}
      </ul>
    </div>
  );
}

export function PlanVersionComparisonPage() {
  const { planId = "" } = useParams();
  const [params] = useSearchParams();
  const source = params.get("source") ?? "";
  const target = params.get("target") ?? "";
  const { t } = useTranslation();
  const session = useAdminSession();
  const [showUnchanged, setShowUnchanged] = useState(false);
  const query = useQuery({
    queryKey: [...adminCommercialKeys.plans.all(), "compare", source, target],
    queryFn: () => adminApi.comparePlanVersions(source, target),
    enabled: !!source && !!target && session.can(adminPermissions.plansCompareVersions),
  });
  const registry = useQuery({
    queryKey: adminCommercialKeys.registry.featureCatalog("PLAN_ASSIGNABLE"),
    queryFn: () => adminApi.featureCatalog("PLAN_ASSIGNABLE"),
    enabled: session.can(adminPermissions.registryFeatureCatalog),
  });
  if (query.isLoading) return <LoadingState />;
  if (query.isError || !query.data)
    return <ErrorState retry={source && target ? () => void query.refetch() : undefined} />;
  const data = query.data;
  const rows = data.features.filter((feature) => showUnchanged || feature.changed);
  const names = new Map(
    registry.data?.flatMap((module) => module.features.map((feature) => [feature.code, feature.displayName] as const)),
  );
  const compareColumn = createDataColumns<(typeof data.features)[number]>();
  const columns = compareColumn.columns([
    compareColumn.accessor("featureCode", {
      header: t("planVersions.feature"),
      cell: ({ getValue }) => <span className="font-medium">{names.get(getValue()) ?? getValue()}</span>,
    }),
    compareColumn.display({
      id: "before",
      header: t("planVersions.version", { number: data.source.productVersionNumber }),
      cell: ({ row }) => <FeatureTerms feature={row.original.before} />,
    }),
    compareColumn.display({
      id: "after",
      header: t("planVersions.version", { number: data.target.productVersionNumber }),
      cell: ({ row }) => <FeatureTerms feature={row.original.after} />,
    }),
  ]);
  return (
    <div className="space-y-5">
      <Button asChild size="sm" variant="ghost">
        <Link to={`/admin/plans/${planId}/versions`}>
          <ArrowLeftIcon className="rtl:rotate-180" />
          {t("planVersions.versions")}
        </Link>
      </Button>
      <PageHeader
        title={t("planVersions.compareTitle")}
        description={`${data.source.name} · ${t("planVersions.comparing", { source: data.source.productVersionNumber, target: data.target.productVersionNumber })}`}
      />
      <Label className="flex min-h-11 items-center gap-2">
        <Checkbox checked={showUnchanged} onCheckedChange={(value) => setShowUnchanged(value === true)} />
        {t("planVersions.showUnchanged")}
      </Label>
      {registry.isError && <ErrorState retry={() => void registry.refetch()} />}
      <section className="overflow-hidden rounded-xl border bg-card">
        <DataTable
          columns={columns}
          data={rows}
          getRowId={(row) => row.featureCode}
          emptyState={<EmptyState title={t("planVersions.noChanges")} />}
        />
      </section>
      {(data.extensionPolicyChanged || data.visibilityChanged) && (
        <section className="space-y-3 rounded-xl border bg-card p-5">
          {data.extensionPolicyChanged && (
            <p>
              {t("planVersions.extensionChanged")}{" "}
              <span className="text-sm text-muted-foreground">
                {data.source.extensionPolicy} → {data.target.extensionPolicy}
              </span>
            </p>
          )}
          {data.visibilityChanged && (
            <p>
              {t("planVersions.visibilityChanged")}{" "}
              <span className="text-sm text-muted-foreground">
                {data.source.salesVisibility} → {data.target.salesVisibility}
              </span>
            </p>
          )}
        </section>
      )}
      {data.pricesVisible && (
        <section className="grid gap-5 rounded-xl border bg-card p-5 sm:grid-cols-2">
          {[
            { version: data.source, prices: data.sourcePrices },
            { version: data.target, prices: data.targetPrices },
          ].map(({ version, prices }) => (
            <div key={version.id}>
              <h2 className="mb-3 text-sm font-semibold">
                {t("planVersions.prices")} · {t("planVersions.version", { number: version.productVersionNumber })}
              </h2>
              {prices.length ? (
                <ProductPriceOptions prices={prices} />
              ) : (
                <p className="text-sm text-muted-foreground">{t("planVersions.noPrice")}</p>
              )}
            </div>
          ))}
        </section>
      )}
    </div>
  );
}
