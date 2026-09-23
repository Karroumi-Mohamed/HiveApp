import { ArrowLeftIcon, ArrowsClockwiseIcon } from "@phosphor-icons/react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useSearchParams } from "react-router";
import { adminApi } from "@/api/admin-api";
import type { ComparedPlan, PlanVersion, PlanVersionPrice, RegistryFeature } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable, DataTableExpander } from "@/components/patterns/data-table";
import { PageHeader } from "@/components/patterns/page-header";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { StatusBadge } from "@/components/patterns/status-badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { adminCommercialKeys } from "@/features/commercial/commercial-query";
import { sumExactDecimals } from "@/lib/exact-decimal";
import { ComparisonExtensions } from "./plan-comparison-extensions";
import { capacityOf, comparisonFeatures, comparisonSelection } from "./plan-comparison-model";
import { ComparisonPriceList } from "./plan-comparison-prices";
import { PlanStatusTag, PlanVersionTag } from "./plan-version-controls";

type Row = { id: string; label: ReactNode; values: ReactNode[]; different: boolean; detail?: ReactNode };
const column = createDataColumns<Row>();

function ComparisonGrid({ plans, rows, label }: { plans: PlanVersion[]; rows: Row[]; label: string }) {
  const { t } = useTranslation();
  const session = useAdminSession();
  const columns = column.columns([
    column.display({
      id: "label",
      header: label,
      meta: {
        headerClassName: "w-44 min-w-44 sticky start-0 !z-20 bg-card",
        cellClassName: "min-w-44 sticky start-0 z-10 bg-card whitespace-normal font-medium",
      },
      cell: ({ row }) => (
        <div className="flex items-center gap-1">
          <DataTableExpander
            row={row}
            expandLabel={t("planComparison.expand", {
              name: typeof row.original.label === "string" ? row.original.label : "",
            })}
            collapseLabel={t("planComparison.collapse", {
              name: typeof row.original.label === "string" ? row.original.label : "",
            })}
          />
          {row.original.label}
        </div>
      ),
    }),
    ...plans.map((plan, index) =>
      column.display({
        id: plan.id,
        header: () => (
          <div className="space-y-2 py-4">
            <div className="text-lg font-semibold">{plan.name}</div>
            <PlanVersionTag
              id={plan.id}
              number={plan.productVersionNumber}
              clickable={session.can(adminPermissions.plansListVersions)}
            />
          </div>
        ),
        meta: {
          headerClassName: "min-w-56 whitespace-normal border-s",
          cellClassName: "min-w-56 max-w-sm whitespace-normal border-s py-4 align-top",
        },
        cell: ({ row }) => row.original.values[index],
      }),
    ),
  ]);
  return (
    <section
      aria-label={label}
      className={`overflow-hidden rounded-xl border bg-card [&_table]:table-fixed ${plans.length === 3 ? "[&_table]:min-w-[848px]" : "[&_table]:min-w-[624px]"} [&_[data-slot=table-container]]:max-h-[72vh] [&_th]:sticky [&_th]:top-0 [&_th]:z-10 [&_th]:bg-card [&_th]:shadow-[0_1px_0_var(--border)]`}
    >
      <DataTable
        columns={columns}
        data={rows}
        getRowId={(row) => row.id}
        getRowCanExpand={(row) => Boolean(row.detail)}
        autoResetExpanded={false}
        renderExpandedRow={(row) => <div className="bg-muted/60 p-5">{row.detail}</div>}
        emptyState={<EmptyState title={t("planComparison.noDifference")} />}
      />
    </section>
  );
}

function samePrices(prices: PlanVersionPrice[][]) {
  const signatures = prices.map((list) =>
    JSON.stringify(
      list
        .map((price) => [
          sumExactDecimals([price.amount]),
          price.currencyCode,
          price.billingCycle,
          price.effectiveFrom,
          price.effectiveUntil,
        ])
        .sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b))),
    ),
  );
  return new Set(signatures).size === 1;
}

function ComparisonBody({
  plans,
  pricesVisible,
  catalog,
  onlyDifferences,
}: {
  plans: ComparedPlan[];
  pricesVisible: boolean;
  catalog: RegistryFeature[] | undefined;
  onlyDifferences: boolean;
}) {
  const { t } = useTranslation();
  const session = useAdminSession();
  const [extensionsOpen, setExtensionsOpen] = useState(false);
  const rows: Row[] = [];
  const field = (key: "status" | "salesVisibility" | "extensionPolicy", label: string) =>
    rows.push({
      id: key,
      label,
      different: new Set(plans.map(({ plan }) => plan[key])).size > 1,
      values: plans.map(({ plan }) =>
        key === "status" ? <PlanStatusTag key={plan.id} status={plan.status} /> : t(`planComparison.${plan[key]}`),
      ),
    });
  field("status", t("planComparison.status"));
  field("salesVisibility", t("planComparison.visibility"));
  field("extensionPolicy", t("planComparison.policy"));
  if (pricesVisible) {
    for (const key of ["currentPrices", "scheduledPrices"] as const) {
      const tuples = [
        ...new Set(plans.flatMap((plan) => plan[key].map((price) => `${price.currencyCode}:${price.billingCycle}`))),
      ].sort();
      if (!tuples.length)
        rows.push({
          id: key,
          label: t(key === "currentPrices" ? "planComparison.prices" : "planComparison.scheduled"),
          different: false,
          values: plans.map(({ plan }) => (
            <span key={plan.id} className="text-muted-foreground">
              {t(key === "currentPrices" ? "planComparison.noPrice" : "planComparison.noScheduled")}
            </span>
          )),
        });
      for (const tuple of tuples) {
        const [currency, cycle] = tuple.split(":");
        const prices = plans.map((plan) =>
          plan[key].filter((price) => `${price.currencyCode}:${price.billingCycle}` === tuple),
        );
        rows.push({
          id: `${key}:${tuple}`,
          label: (
            <div className="space-y-1">
              <p>{t(key === "currentPrices" ? "planComparison.prices" : "planComparison.scheduled")}</p>
              <p className="text-xs font-normal text-muted-foreground">
                {currency} · {t(`planComparison.${cycle}`)}
              </p>
            </div>
          ),
          different: !samePrices(prices),
          values: plans.map(({ plan }, index) =>
            prices[index]?.length ? (
              <ComparisonPriceList key={plan.id} prices={prices[index] ?? []} />
            ) : (
              <span key={plan.id} className="text-muted-foreground">
                —
              </span>
            ),
          ),
        });
      }
    }
  }
  const features: Row[] = comparisonFeatures(plans, catalog ?? []).map((row) => ({
    id: row.code,
    label: row.definition?.displayName ?? row.code,
    different: row.changed,
    values: plans.map(({ plan }, index) => {
      const feature = row.features[index];
      return (
        <div key={plan.id} className="space-y-3">
          {feature?.mode === "INCLUDED" ? (
            <StatusBadge tone="success" dot={false}>
              {t("planComparison.INCLUDED")}
            </StatusBadge>
          ) : (
            <span
              className={feature?.mode === "OPTIONAL_ADD_ON" ? "font-medium text-primary" : "text-muted-foreground"}
            >
              {t(`planComparison.${feature?.mode ?? "ABSENT"}`)}
            </span>
          )}
          {feature?.mode === "INCLUDED" && (
            <ul className="space-y-1 text-sm">
              {row.resources.length ? (
                row.resources.map((resource) => {
                  const capacity = capacityOf(feature, resource);
                  const unit = row.definition?.quotaSchema.find((slot) => slot.resource === resource)?.unit || resource;
                  return (
                    <li key={resource}>
                      <span className={capacity.kind === "UNDEFINED" ? "text-warning" : "font-medium tabular-nums"}>
                        {capacity.kind === "FINITE" ? capacity.limit : t(`planComparison.${capacity.kind}`)}
                      </span>{" "}
                      <span className="text-muted-foreground">{unit}</span>
                    </li>
                  );
                })
              ) : (
                <li className="text-muted-foreground">
                  {t(row.definition ? "planComparison.noQuota" : "planComparison.unknownQuota")}
                </li>
              )}
            </ul>
          )}
        </div>
      );
    }),
    detail: (
      <div className="space-y-5">
        <div>
          <h3 className="font-semibold">{t("planComparison.permissions")}</h3>
          <p className="mt-1 text-xs text-muted-foreground">{t("planComparison.permissionsHint")}</p>
          {!row.definition ? (
            <p className="mt-3 text-sm">{t("planComparison.registryHidden")}</p>
          ) : (
            <ul className="mt-3 grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
              {row.definition.permissions.map((permission) => (
                <li key={permission.code} className="text-sm">
                  {permission.description || permission.action.replaceAll("_", " ")}
                </li>
              ))}
              {!row.definition.permissions.length && <li>{t("planComparison.noPermissions")}</li>}
            </ul>
          )}
        </div>
        <div className={`grid gap-5 ${plans.length === 2 ? "lg:grid-cols-2" : "lg:grid-cols-3"}`}>
          {plans.map(({ plan }) => (
            <div className="min-w-0 rounded-lg border bg-card p-4" key={plan.id}>
              <h3 className="mb-3 font-semibold">{plan.name}</h3>
              <ComparisonExtensions planId={plan.id} featureCode={row.code} catalog={catalog ?? []} />
            </div>
          ))}
        </div>
      </div>
    ),
  }));
  return (
    <>
      <div className="space-y-3">
        <h2 className="text-lg font-semibold">{t("planComparison.sales")}</h2>
        <p className="text-sm text-muted-foreground">{t("planComparison.priceContext")}</p>
        {!pricesVisible && <p className="text-sm text-warning">{t("planComparison.priceHidden")}</p>}
        <ComparisonGrid
          plans={plans.map((item) => item.plan)}
          rows={rows.filter((row) => !onlyDifferences || row.different)}
          label={t("planComparison.sales")}
        />
      </div>
      <div className="space-y-3">
        <h2 className="text-lg font-semibold">{t("planComparison.content")}</h2>
        <ComparisonGrid
          plans={plans.map((item) => item.plan)}
          rows={features.filter((row) => !onlyDifferences || row.different)}
          label={t("planComparison.feature")}
        />
      </div>
      <details
        className="rounded-xl border bg-card"
        open={extensionsOpen}
        onToggle={(event) => setExtensionsOpen(event.currentTarget.open)}
      >
        <summary className="min-h-11 cursor-pointer p-5 text-lg font-semibold focus-visible:ring-2 focus-visible:ring-ring">
          {t("planComparison.extensions")}
        </summary>
        {extensionsOpen && (
          <div className="space-y-4 px-5 pb-5">
            <p className="text-sm text-muted-foreground">{t("planComparison.extensionsHint")}</p>
            <div className={`grid gap-5 ${plans.length === 2 ? "lg:grid-cols-2" : "lg:grid-cols-3"}`}>
              {plans.map(({ plan }) => (
                <section aria-label={plan.name} className="min-w-0 rounded-lg border p-4" key={plan.id}>
                  <h3 className="mb-3 text-lg font-semibold">{plan.name}</h3>
                  <ComparisonExtensions planId={plan.id} catalog={catalog ?? []} />
                </section>
              ))}
            </div>
          </div>
        )}
      </details>
      {session.can(adminPermissions.plansReadDetail) && (
        <div className="flex flex-wrap gap-3">
          {plans.map(({ plan }) => (
            <Button key={plan.id} asChild variant="outline">
              <Link to={`/admin/plans/${plan.id}`}>
                {t("planComparison.view")} {plan.name}
              </Link>
            </Button>
          ))}
        </div>
      )}
    </>
  );
}

export function PlanComparisonPage() {
  const [params] = useSearchParams();
  const ids = comparisonSelection(params.get("ids"));
  const { t, i18n } = useTranslation();
  const session = useAdminSession();
  const client = useQueryClient();
  const [differences, setDifferences] = useState(false);
  const query = useQuery({
    queryKey: [...adminCommercialKeys.plans.all(), "catalog-comparison", ids],
    queryFn: () => adminApi.comparePlans(ids ?? []),
    enabled: Boolean(ids) && session.can(adminPermissions.plansCompare),
    refetchInterval: 30_000,
  });
  const registry = useQuery({
    queryKey: adminCommercialKeys.registry.featureCatalog("PLAN_ASSIGNABLE"),
    queryFn: () => adminApi.featureCatalog("PLAN_ASSIGNABLE"),
    enabled:
      Boolean(ids) &&
      session.can(adminPermissions.plansCompare) &&
      session.can(adminPermissions.registryFeatureCatalog),
  });
  return (
    <div className="space-y-6">
      {session.can(adminPermissions.plansListFamilies) && (
        <Button asChild variant="ghost" size="sm">
          <Link to="/admin/plans?compare=1">
            <ArrowLeftIcon className="rtl:rotate-180" />
            {t("planComparison.change")}
          </Link>
        </Button>
      )}
      <PageHeader
        title={t("planComparison.title")}
        description={t("planComparison.hint")}
        actions={
          ids && (
            <Button
              variant="outline"
              onClick={() => {
                void query.refetch();
                if (session.can(adminPermissions.registryFeatureCatalog)) void registry.refetch();
                void client.invalidateQueries({ queryKey: ["admin", "commercial", "comparison-extensions"] });
              }}
            >
              <ArrowsClockwiseIcon />
              {t("planComparison.refresh")}
            </Button>
          )
        }
      />
      {!ids ? (
        <EmptyState title={t("planComparison.invalid")} />
      ) : query.isLoading ? (
        <LoadingState rows={5} />
      ) : query.isError || !query.data ? (
        <ErrorState retry={() => void query.refetch()} />
      ) : (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <Label className="flex min-h-11 items-center gap-3">
              <Checkbox checked={differences} onCheckedChange={(checked) => setDifferences(checked === true)} />
              {t("planComparison.differences")}
            </Label>
            <p className="text-xs text-muted-foreground">
              {t("planComparison.asOf", { date: new Date(query.data.asOf).toLocaleString(i18n.language) })}
            </p>
          </div>
          {differences && <p className="text-xs text-muted-foreground">{t("planComparison.differencesHint")}</p>}
          {!session.can(adminPermissions.registryFeatureCatalog) ? (
            <p className="text-sm text-muted-foreground">{t("planComparison.registryHidden")}</p>
          ) : registry.isError ? (
            <div role="alert" className="text-sm text-warning">
              {t("planComparison.registryError")}{" "}
              <Button size="sm" variant="ghost" onClick={() => void registry.refetch()}>
                {t("planComparison.retry")}
              </Button>
            </div>
          ) : registry.isLoading ? (
            <p role="status">{t("planComparison.loading")}</p>
          ) : null}
          <ComparisonBody
            key={ids.join(",")}
            plans={query.data.plans}
            pricesVisible={query.data.pricesVisible && session.can(adminPermissions.priceBooksList)}
            catalog={
              session.can(adminPermissions.registryFeatureCatalog) && !registry.isError
                ? registry.data?.flatMap((module) => module.features)
                : undefined
            }
            onlyDifferences={differences}
          />
        </>
      )}
    </div>
  );
}
