import { ArrowClockwiseIcon, ArrowRightIcon } from "@phosphor-icons/react";
import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { adminApi } from "@/api/admin-api";
import type {
  BillingCycle,
  CommercialAnalyticsInterval,
  CommercialAttentionRow,
  CommercialAttentionType,
  CommercialFinancialPoint,
} from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable } from "@/components/patterns/data-table";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { RowAction } from "@/components/patterns/row-action";
import { SectionTabs } from "@/components/patterns/section-tabs";
import { StatusText } from "@/components/patterns/status-text";
import { TableActionsCell, tableActionsColumnMeta } from "@/components/patterns/table-actions-cell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { formatExactMoney, sumExactDecimals } from "@/lib/exact-decimal";
import {
  attentionTypeLabel,
  billingCycleShortLabel,
  bucketLabel,
  dateTime,
  lifecycleActionLabel,
  offerOutcomeLabel,
  productTypeLabel,
  subscriptionStatusLabel,
} from "./analytics-presentation";
import {
  type AnalyticsFilters,
  type AnalyticsRangePreset,
  adminAnalyticsKeys,
  analyticsQuery,
  analyticsSearchChanges,
  timeSeriesQuery,
  toDateTimeLocal,
} from "./analytics-query";
import { AnalyticsSeriesChart } from "./analytics-series-chart";

type AnalyticsView = "summary" | "financial" | "subscriptions" | "offers" | "attention";

const validRanges = new Set<AnalyticsRangePreset>(["30", "90", "365", "custom"]);
const validIntervals = new Set<CommercialAnalyticsInterval>(["DAY", "WEEK", "MONTH"]);
const validCycles = new Set<BillingCycle>(["MONTHLY", "YEARLY", "FOREVER"]);
const validAttentionTypes = new Set<CommercialAttentionType>([
  "PAST_DUE",
  "SUSPENDED",
  "OPEN_INVOICE",
  "CHANGE_NEEDS_ATTENTION",
]);

function updateSearch(
  params: URLSearchParams,
  setParams: ReturnType<typeof useSearchParams>[1],
  updates: Record<string, string | null>,
) {
  const next = new URLSearchParams(params);
  for (const [key, value] of Object.entries(updates)) {
    if (value) next.set(key, value);
    else next.delete(key);
  }
  setParams(next, { replace: true });
}

function RangeBar({
  filters,
  onChange,
  showMoneyDimensions,
}: {
  filters: AnalyticsFilters;
  onChange: (updates: Partial<AnalyticsFilters>) => void;
  showMoneyDimensions: boolean;
}) {
  const browserZone = Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
  const zones = Array.from(new Set([browserZone, "UTC", "Africa/Casablanca", "Europe/Paris", "America/New_York"]));
  return (
    <section aria-label="Période et dimensions" className="border-y py-4">
      <div className="flex flex-wrap items-end gap-3">
        <div className="space-y-1 text-xs font-medium">
          <span className="text-muted-foreground">Période</span>
          <Select onValueChange={(value) => onChange({ range: value as AnalyticsRangePreset })} value={filters.range}>
            <SelectTrigger aria-label="Période" className="w-44" size="sm">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="30">30 derniers jours</SelectItem>
              <SelectItem value="90">90 derniers jours</SelectItem>
              <SelectItem value="365">12 derniers mois</SelectItem>
              <SelectItem value="custom">Personnalisée</SelectItem>
            </SelectContent>
          </Select>
        </div>
        {filters.range === "custom" ? (
          <>
            <label className="space-y-1 text-xs font-medium" htmlFor="analytics-from">
              <span className="text-muted-foreground">Du</span>
              <Input
                className="h-8 w-52"
                id="analytics-from"
                onChange={(event) => onChange({ from: event.target.value })}
                type="datetime-local"
                value={filters.from ?? ""}
              />
            </label>
            <label className="space-y-1 text-xs font-medium" htmlFor="analytics-until">
              <span className="text-muted-foreground">Au</span>
              <Input
                className="h-8 w-52"
                id="analytics-until"
                onChange={(event) => onChange({ until: event.target.value })}
                type="datetime-local"
                value={filters.until ?? ""}
              />
            </label>
          </>
        ) : null}
        <div className="space-y-1 text-xs font-medium">
          <span className="text-muted-foreground">Regroupement</span>
          <Select
            onValueChange={(value) => onChange({ interval: value as CommercialAnalyticsInterval })}
            value={filters.interval}
          >
            <SelectTrigger aria-label="Regroupement" className="w-32" size="sm">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="DAY">Jour</SelectItem>
              <SelectItem value="WEEK">Semaine</SelectItem>
              <SelectItem value="MONTH">Mois</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1 text-xs font-medium">
          <span className="text-muted-foreground">Fuseau</span>
          <Select onValueChange={(timezone) => onChange({ timezone })} value={filters.timezone}>
            <SelectTrigger aria-label="Fuseau" className="w-48" size="sm">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {zones.map((zone) => (
                <SelectItem key={zone} value={zone}>
                  {zone}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        {showMoneyDimensions ? (
          <>
            <label className="space-y-1 text-xs font-medium" htmlFor="analytics-currency">
              <span className="text-muted-foreground">Devise</span>
              <Input
                className="h-8 w-24 uppercase"
                id="analytics-currency"
                maxLength={3}
                onChange={(event) => onChange({ currencyCode: event.target.value })}
                placeholder="Toutes"
                value={filters.currencyCode ?? ""}
              />
            </label>
            <div className="space-y-1 text-xs font-medium">
              <span className="text-muted-foreground">Cycle</span>
              <Select
                onValueChange={(value) =>
                  onChange({ billingCycle: value === "all" ? undefined : (value as BillingCycle) })
                }
                value={filters.billingCycle ?? "all"}
              >
                <SelectTrigger aria-label="Cycle" className="w-36" size="sm">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">Tous</SelectItem>
                  <SelectItem value="MONTHLY">Mensuel</SelectItem>
                  <SelectItem value="YEARLY">Annuel</SelectItem>
                  <SelectItem value="FOREVER">Permanent</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </>
        ) : null}
      </div>
    </section>
  );
}

function MetricStrip({ items }: { items: { label: string; value: string | number; tone?: "warning" | "danger" }[] }) {
  return (
    <dl className="grid border-y sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
      {items.map((item) => (
        <div className="border-b px-4 py-4 last:border-b-0 sm:border-e lg:border-b-0" key={item.label}>
          <dt className="text-xs text-muted-foreground">{item.label}</dt>
          <dd
            className={`mt-1 text-xl font-semibold tabular-nums ${item.tone === "danger" ? "text-destructive" : item.tone === "warning" ? "text-warning" : ""}`}
          >
            {item.value}
          </dd>
        </div>
      ))}
    </dl>
  );
}

function SummaryView({ query }: { query: ReturnType<typeof analyticsQuery> }) {
  const overview = useQuery({
    queryKey: adminAnalyticsKeys.overview(query),
    queryFn: () => adminApi.commercialAnalyticsOverview(query),
  });
  if (overview.isLoading) return <LoadingState rows={6} />;
  if (overview.isError || !overview.data) return <ErrorState retry={() => void overview.refetch()} />;
  const data = overview.data;
  const current = data.currentSubscriptions;
  const pending = data.finality.pendingPayments + data.finality.pendingRefunds + data.finality.pendingProviderCommands;
  return (
    <div className="space-y-8">
      <section className="space-y-3">
        <h2 className="text-sm font-semibold">Abonnements maintenant</h2>
        <MetricStrip
          items={Object.entries(subscriptionStatusLabel).map(([status, label]) => ({
            label,
            value: current[status as keyof typeof current] ?? 0,
            tone: status === "SUSPENDED" ? "danger" : status === "PAST_DUE" ? "warning" : undefined,
          }))}
        />
      </section>
      <section className="overflow-hidden rounded-xl border bg-card">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b px-4 py-3">
          <h2 className="text-sm font-semibold">Flux financiers prouvés</h2>
          <time className="text-xs text-muted-foreground" dateTime={data.metadata.completeThrough}>
            Actualisé {dateTime(data.metadata.completeThrough)}
          </time>
        </div>
        {data.financialTotals.length ? (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Dimension</TableHead>
                <TableHead>Facturé</TableHead>
                <TableHead>Encaissé</TableHead>
                <TableHead>Avoirs</TableHead>
                <TableHead>Remboursé</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.financialTotals.map((row) => (
                <TableRow key={`${row.dimension.currencyCode}-${row.dimension.billingCycle}`}>
                  <TableCell className="font-medium">
                    {row.dimension.currencyCode} · {billingCycleShortLabel[row.dimension.billingCycle]}
                  </TableCell>
                  <TableCell className="tabular-nums">
                    {formatExactMoney(row.invoiced, row.dimension.currencyCode)}
                  </TableCell>
                  <TableCell className="tabular-nums">
                    {formatExactMoney(row.collected, row.dimension.currencyCode)}
                  </TableCell>
                  <TableCell className="tabular-nums">
                    {formatExactMoney(row.credited, row.dimension.currencyCode)}
                  </TableCell>
                  <TableCell className="tabular-nums">
                    {formatExactMoney(row.refunded, row.dimension.currencyCode)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        ) : (
          <EmptyState title="Aucun flux financier sur cette période" />
        )}
      </section>
      <section className="grid gap-6 lg:grid-cols-2">
        <div className="border-y py-4">
          <h2 className="text-sm font-semibold">Valeur récurrente configurée</h2>
          <div className="mt-3 space-y-2">
            {data.configuredRecurringValues.length ? (
              data.configuredRecurringValues.map((row) => (
                <div
                  className="flex items-baseline justify-between gap-4 text-sm"
                  key={`${row.dimension.currencyCode}-${row.dimension.billingCycle}`}
                >
                  <span className="text-muted-foreground">
                    {row.subscriptions} abonnements · {billingCycleShortLabel[row.dimension.billingCycle]}
                  </span>
                  <span className="font-semibold tabular-nums">
                    {formatExactMoney(row.amount, row.dimension.currencyCode)}
                  </span>
                </div>
              ))
            ) : (
              <p className="text-sm text-muted-foreground">Aucune valeur configurée.</p>
            )}
          </div>
        </div>
        <div className="border-y py-4">
          <h2 className="text-sm font-semibold">Finalité opérationnelle</h2>
          <dl className="mt-3 grid grid-cols-2 gap-3 text-sm">
            <div>
              <dt className="text-muted-foreground">Paiements en attente</dt>
              <dd className="font-semibold tabular-nums">{data.finality.pendingPayments}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Remboursements</dt>
              <dd className="font-semibold tabular-nums">{data.finality.pendingRefunds}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Commandes fournisseur</dt>
              <dd className="font-semibold tabular-nums">{data.finality.pendingProviderCommands}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Délai de grâce ≤ 7 jours</dt>
              <dd className="font-semibold tabular-nums">{data.graceDeadlinesWithinSevenDays}</dd>
            </div>
          </dl>
          {pending ? (
            <p className="mt-3 text-xs text-warning">{pending} élément(s) ne sont pas encore définitifs.</p>
          ) : null}
        </div>
      </section>
    </div>
  );
}

function FinancialView({ query }: { query: ReturnType<typeof analyticsQuery> }) {
  const result = useQuery({
    queryKey: adminAnalyticsKeys.financial(query),
    queryFn: () => adminApi.commercialFinancialSeries(query),
  });
  const [selected, setSelected] = useState("");
  if (result.isLoading) return <LoadingState rows={7} />;
  if (result.isError || !result.data) return <ErrorState retry={() => void result.refetch()} />;
  if (!result.data.dimensions.length) return <EmptyState title="Aucun flux financier sur cette période" />;
  const keyed = result.data.dimensions.map((item) => ({
    ...item,
    key: `${item.dimension.currencyCode}-${item.dimension.billingCycle}`,
  }));
  const active = keyed.find((item) => item.key === selected) ?? keyed.at(0);
  if (!active) return <EmptyState title="Aucun flux financier sur cette période" />;
  const labels = active.points.map((point) =>
    bucketLabel(point.bucketStart, result.data.metadata.interval, result.data.metadata.timezone),
  );
  const totals = {
    invoiced: sumExactDecimals(active.points.map((point) => point.invoiced)),
    collected: sumExactDecimals(active.points.map((point) => point.collected)),
    credited: sumExactDecimals(active.points.map((point) => point.credited)),
    refunded: sumExactDecimals(active.points.map((point) => point.refunded)),
  };
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-sm font-semibold">Chronologie financière</h2>
        {keyed.length > 1 ? (
          <Select onValueChange={setSelected} value={active.key}>
            <SelectTrigger className="w-52" size="sm">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {keyed.map((item) => (
                <SelectItem key={item.key} value={item.key}>
                  {item.dimension.currencyCode} · {billingCycleShortLabel[item.dimension.billingCycle]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        ) : (
          <span className="text-xs text-muted-foreground">
            {active.dimension.currencyCode} · {billingCycleShortLabel[active.dimension.billingCycle]}
          </span>
        )}
      </div>
      <MetricStrip
        items={[
          { label: "Facturé", value: formatExactMoney(totals.invoiced, active.dimension.currencyCode) },
          { label: "Encaissé", value: formatExactMoney(totals.collected, active.dimension.currencyCode) },
          { label: "Avoirs", value: formatExactMoney(totals.credited, active.dimension.currencyCode) },
          { label: "Remboursé", value: formatExactMoney(totals.refunded, active.dimension.currencyCode) },
        ]}
      />
      <section className="rounded-xl border bg-card p-5">
        <AnalyticsSeriesChart
          labels={labels}
          provisionalIndex={active.points.findIndex((point) => point.provisional)}
          series={[
            {
              key: "invoiced",
              label: "Facturé",
              values: active.points.map((point) => point.invoiced),
              className: "text-primary",
            },
            {
              key: "collected",
              label: "Encaissé",
              values: active.points.map((point) => point.collected),
              className: "text-success",
            },
            {
              key: "credited",
              label: "Avoirs",
              values: active.points.map((point) => point.credited),
              className: "text-warning",
            },
            {
              key: "refunded",
              label: "Remboursé",
              values: active.points.map((point) => point.refunded),
              className: "text-destructive",
            },
          ]}
          title="Montants par période"
        />
      </section>
      <FinancialPointTable
        currency={active.dimension.currencyCode}
        interval={result.data.metadata.interval}
        points={active.points}
        timezone={result.data.metadata.timezone}
      />
    </div>
  );
}

function FinancialPointTable({
  points,
  currency,
  interval,
  timezone,
}: {
  points: CommercialFinancialPoint[];
  currency: string;
  interval: CommercialAnalyticsInterval;
  timezone: string;
}) {
  return (
    <section className="max-h-[28rem] overflow-auto rounded-xl border bg-card">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Période</TableHead>
            <TableHead>Facturé</TableHead>
            <TableHead>Encaissé</TableHead>
            <TableHead>Avoirs</TableHead>
            <TableHead>Remboursé</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {points.map((point) => (
            <TableRow className="[content-visibility:auto]" key={point.bucketStart}>
              <TableCell>
                <span>{bucketLabel(point.bucketStart, interval, timezone)}</span>
                {point.provisional ? (
                  <StatusText className="ms-2" tone="warning">
                    Provisoire
                  </StatusText>
                ) : null}
              </TableCell>
              <TableCell className="tabular-nums">{formatExactMoney(point.invoiced, currency)}</TableCell>
              <TableCell className="tabular-nums">{formatExactMoney(point.collected, currency)}</TableCell>
              <TableCell className="tabular-nums">{formatExactMoney(point.credited, currency)}</TableCell>
              <TableCell className="tabular-nums">{formatExactMoney(point.refunded, currency)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </section>
  );
}

function SubscriptionView({ query }: { query: ReturnType<typeof analyticsQuery> }) {
  const historicalQuery = timeSeriesQuery(query);
  const result = useQuery({
    queryKey: adminAnalyticsKeys.subscriptions(historicalQuery),
    queryFn: () => adminApi.commercialSubscriptionSeries(historicalQuery),
  });
  const holdings = useQuery({ queryKey: adminAnalyticsKeys.holdings(), queryFn: adminApi.commercialProductHoldings });
  if (result.isLoading || holdings.isLoading) return <LoadingState rows={7} />;
  if (result.isError || !result.data) return <ErrorState retry={() => void result.refetch()} />;
  const hasMovements = result.data.points.some(
    (point) =>
      point.productsAdded > 0 ||
      point.productsRemoved > 0 ||
      Object.values(point.lifecycleActions).some((count) => count > 0),
  );
  const labels = result.data.points.map((point) =>
    bucketLabel(point.bucketStart, result.data.metadata.interval, result.data.metadata.timezone),
  );
  return (
    <div className="space-y-6">
      {hasMovements ? (
        <section className="rounded-xl border bg-card p-5">
          <AnalyticsSeriesChart
            labels={labels}
            provisionalIndex={result.data.points.findIndex((point) => point.provisional)}
            series={[
              {
                key: "added",
                label: "Produits ajoutés",
                values: result.data.points.map((point) => point.productsAdded),
                className: "text-primary",
              },
              {
                key: "removed",
                label: "Produits retirés",
                values: result.data.points.map((point) => point.productsRemoved),
                className: "text-destructive",
              },
            ]}
            title="Mouvements de produits"
          />
        </section>
      ) : (
        <section className="rounded-xl border bg-card">
          <EmptyState title="Aucun mouvement d’abonnement sur cette période" />
        </section>
      )}
      <section className={hasMovements ? "grid gap-6 xl:grid-cols-2" : undefined}>
        {hasMovements ? (
          <div className="overflow-hidden rounded-xl border bg-card">
            <div className="border-b px-4 py-3">
              <h2 className="text-sm font-semibold">Mouvements sur la période</h2>
            </div>
            {result.data.productMovements.length ? (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Produit</TableHead>
                    <TableHead>Ajouts</TableHead>
                    <TableHead>Retraits</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {result.data.productMovements.map((row) => (
                    <TableRow key={`${row.productType}-${row.productCode}`}>
                      <TableCell>
                        <span className="block font-medium">{row.productCode}</span>
                        <span className="text-xs text-muted-foreground">{productTypeLabel[row.productType]}</span>
                      </TableCell>
                      <TableCell className="tabular-nums">{row.additions}</TableCell>
                      <TableCell className="tabular-nums">{row.removals}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            ) : (
              <EmptyState title="Aucun produit ajouté ou retiré" />
            )}
          </div>
        ) : null}
        <div className="overflow-hidden rounded-xl border bg-card">
          <div className="border-b px-4 py-3">
            <h2 className="text-sm font-semibold">Détention actuelle</h2>
          </div>
          {holdings.isError ? (
            <ErrorState retry={() => void holdings.refetch()} />
          ) : holdings.data?.length ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Produit</TableHead>
                  <TableHead>Abonnements</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {holdings.data.map((row) => (
                  <TableRow key={`${row.productType}-${row.productCode}`}>
                    <TableCell>
                      <span className="block font-medium">{row.productCode}</span>
                      <span className="text-xs text-muted-foreground">{productTypeLabel[row.productType]}</span>
                    </TableCell>
                    <TableCell className="tabular-nums">{row.subscriptions}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : (
            <EmptyState title="Aucun produit détenu" />
          )}
        </div>
      </section>
      {hasMovements ? (
        <section className="max-h-[28rem] overflow-auto rounded-xl border bg-card">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Période</TableHead>
                <TableHead>Cycle de vie</TableHead>
                <TableHead>Ajouts</TableHead>
                <TableHead>Retraits</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {result.data.points.map((point) => (
                <TableRow className="[content-visibility:auto]" key={point.bucketStart}>
                  <TableCell>
                    {bucketLabel(point.bucketStart, result.data.metadata.interval, result.data.metadata.timezone)}
                    {point.provisional ? (
                      <StatusText className="ms-2" tone="warning">
                        Provisoire
                      </StatusText>
                    ) : null}
                  </TableCell>
                  <TableCell>
                    {Object.entries(point.lifecycleActions)
                      .map(([action, count]) => `${lifecycleActionLabel[action] ?? action} × ${count}`)
                      .join(" · ") || "—"}
                  </TableCell>
                  <TableCell className="tabular-nums">{point.productsAdded}</TableCell>
                  <TableCell className="tabular-nums">{point.productsRemoved}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </section>
      ) : null}
    </div>
  );
}

function OfferView({ query }: { query: ReturnType<typeof analyticsQuery> }) {
  const historicalQuery = timeSeriesQuery(query);
  const result = useQuery({
    queryKey: adminAnalyticsKeys.offers(historicalQuery),
    queryFn: () => adminApi.commercialOfferSeries(historicalQuery),
  });
  if (result.isLoading) return <LoadingState rows={7} />;
  if (result.isError || !result.data) return <ErrorState retry={() => void result.refetch()} />;
  const hasOutcomes = result.data.points.some(
    (point) => point.reserved > 0 || point.applied > 0 || point.cancelled > 0 || point.failed > 0,
  );
  if (!hasOutcomes) return <EmptyState title="Aucun résultat d’offre sur cette période" />;
  const labels = result.data.points.map((point) =>
    bucketLabel(point.bucketStart, result.data.metadata.interval, result.data.metadata.timezone),
  );
  return (
    <div className="space-y-6">
      <section className="rounded-xl border bg-card p-5">
        <AnalyticsSeriesChart
          labels={labels}
          provisionalIndex={result.data.points.findIndex((point) => point.provisional)}
          series={[
            {
              key: "reserved",
              label: offerOutcomeLabel.RESERVED,
              values: result.data.points.map((point) => point.reserved),
              className: "text-primary",
            },
            {
              key: "applied",
              label: offerOutcomeLabel.APPLIED,
              values: result.data.points.map((point) => point.applied),
              className: "text-success",
            },
            {
              key: "cancelled",
              label: offerOutcomeLabel.CANCELLED,
              values: result.data.points.map((point) => point.cancelled),
              className: "text-warning",
            },
            {
              key: "failed",
              label: offerOutcomeLabel.FAILED,
              values: result.data.points.map((point) => point.failed),
              className: "text-destructive",
            },
          ]}
          title="Résultats des offres"
        />
      </section>
      <section className="max-h-[28rem] overflow-auto rounded-xl border bg-card">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Période</TableHead>
              <TableHead>Réservées</TableHead>
              <TableHead>Appliquées</TableHead>
              <TableHead>Annulées</TableHead>
              <TableHead>Échouées</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {result.data.points.map((point) => (
              <TableRow className="[content-visibility:auto]" key={point.bucketStart}>
                <TableCell>
                  {bucketLabel(point.bucketStart, result.data.metadata.interval, result.data.metadata.timezone)}
                  {point.provisional ? (
                    <StatusText className="ms-2" tone="warning">
                      Provisoire
                    </StatusText>
                  ) : null}
                </TableCell>
                <TableCell className="tabular-nums">{point.reserved}</TableCell>
                <TableCell className="tabular-nums">{point.applied}</TableCell>
                <TableCell className="tabular-nums">{point.cancelled}</TableCell>
                <TableCell className="tabular-nums">{point.failed}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </section>
    </div>
  );
}

function AttentionView({
  params,
  setParams,
}: {
  params: URLSearchParams;
  setParams: ReturnType<typeof useSearchParams>[1];
}) {
  const requestedType = params.get("type") as CommercialAttentionType | null;
  const type: CommercialAttentionType | "all" =
    requestedType && validAttentionTypes.has(requestedType) ? requestedType : "all";
  const page = Math.max(0, Number(params.get("page") ?? 0) || 0);
  const request = { type: type === "all" ? undefined : type, page, size: 20 };
  const result = useQuery({
    queryKey: adminAnalyticsKeys.attention(request),
    queryFn: () => adminApi.commercialAttention(request),
    placeholderData: keepPreviousData,
  });
  const column = useMemo(() => createDataColumns<CommercialAttentionRow>(), []);
  const columns = useMemo(
    () =>
      column.columns([
        column.accessor("accountName", {
          meta: { headerClassName: "min-w-48" },
          header: "Compte",
          cell: ({ row }) => <span className="font-medium">{row.original.accountName}</span>,
        }),
        column.accessor("type", {
          meta: { headerClassName: "w-44" },
          header: "Attention",
          cell: ({ row }) => (
            <StatusText
              tone={
                row.original.type === "SUSPENDED" || row.original.type === "CHANGE_NEEDS_ATTENTION"
                  ? "danger"
                  : "warning"
              }
            >
              {attentionTypeLabel[row.original.type]}
            </StatusText>
          ),
        }),
        column.accessor("occurredAt", {
          meta: { headerClassName: "w-44" },
          header: "Depuis",
          cell: ({ row }) => dateTime(row.original.occurredAt),
        }),
        column.accessor("dueAt", {
          meta: { headerClassName: "w-44" },
          header: "Échéance",
          cell: ({ row }) => dateTime(row.original.dueAt),
        }),
        column.display({
          id: "detail",
          meta: { headerClassName: "min-w-48" },
          header: "Détail",
          cell: ({ row }) => (
            <span>
              {row.original.amount && row.original.currencyCode
                ? formatExactMoney(row.original.amount, row.original.currencyCode)
                : (row.original.reason ?? row.original.status)}
            </span>
          ),
        }),
        column.display({
          id: "actions",
          meta: tableActionsColumnMeta(1),
          header: "Actions",
          cell: ({ row }) => (
            <TableActionsCell label={`Actions pour ${row.original.accountName}`}>
              <RowAction
                icon={<ArrowRightIcon className="rtl:rotate-180" />}
                label="Ouvrir"
                to={row.original.destination}
              />
            </TableActionsCell>
          ),
        }),
      ]),
    [column],
  );
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <div className="border-b p-4">
        <Select
          onValueChange={(value) =>
            updateSearch(params, setParams, { type: value === "all" ? null : value, page: null })
          }
          value={type}
        >
          <SelectTrigger aria-label="Type d’attention" className="w-56" size="sm">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">Toutes les alertes</SelectItem>
            {Object.entries(attentionTypeLabel).map(([value, label]) => (
              <SelectItem key={value} value={value}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      {result.isLoading ? (
        <div className="p-5">
          <LoadingState rows={6} />
        </div>
      ) : result.isError ? (
        <ErrorState retry={() => void result.refetch()} />
      ) : (
        <>
          <div className="hidden md:block">
            <DataTable
              columns={columns}
              data={result.data?.content ?? []}
              emptyState={<EmptyState title="Aucune opération à traiter" />}
              getRowId={(row) => `${row.type}-${row.recordId}`}
            />
          </div>
          <div className="divide-y md:hidden">
            {result.data?.content.length ? (
              result.data.content.map((row) => (
                <Link
                  className="block space-y-2 p-4 hover:bg-muted/50"
                  key={`${row.type}-${row.recordId}`}
                  to={row.destination}
                >
                  <div className="flex items-start justify-between gap-3">
                    <span className="font-medium">{row.accountName}</span>
                    <StatusText
                      tone={row.type === "SUSPENDED" || row.type === "CHANGE_NEEDS_ATTENTION" ? "danger" : "warning"}
                    >
                      {attentionTypeLabel[row.type]}
                    </StatusText>
                  </div>
                  <p className="text-sm text-muted-foreground">
                    {row.amount && row.currencyCode
                      ? formatExactMoney(row.amount, row.currencyCode)
                      : (row.reason ?? row.status)}
                  </p>
                  <p className="text-xs text-muted-foreground">Depuis {dateTime(row.occurredAt)}</p>
                </Link>
              ))
            ) : (
              <EmptyState title="Aucune opération à traiter" />
            )}
          </div>
          <PaginationBar
            onPageChange={(nextPage) => updateSearch(params, setParams, { page: nextPage ? String(nextPage) : null })}
            page={result.data?.page ?? 0}
            totalElements={result.data?.totalElements ?? 0}
            totalPages={result.data?.totalPages ?? 0}
          />
        </>
      )}
    </section>
  );
}

export function AdminAnalyticsPage() {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [params, setParams] = useSearchParams();
  const [anchor, setAnchor] = useState(() => new Date());
  const tabs = [
    ...(session.can(adminPermissions.analyticsReadSummary) ? [{ label: "Synthèse", value: "summary" }] : []),
    ...(session.can(adminPermissions.analyticsReadFinancialSeries) ? [{ label: "Finances", value: "financial" }] : []),
    ...(session.can(adminPermissions.analyticsReadSubscriptionSeries)
      ? [{ label: "Abonnements", value: "subscriptions" }]
      : []),
    ...(session.can(adminPermissions.analyticsReadOfferSeries) ? [{ label: "Offres", value: "offers" }] : []),
    ...(session.can(adminPermissions.analyticsReadOperations) ? [{ label: "À traiter", value: "attention" }] : []),
  ];
  const requested = params.get("view") as AnalyticsView | null;
  const view = (tabs.some((tab) => tab.value === requested) ? requested : tabs[0]?.value) as AnalyticsView;
  const rangeValue = params.get("range") as AnalyticsRangePreset | null;
  const intervalValue = params.get("interval") as CommercialAnalyticsInterval | null;
  const cycleValue = params.get("cycle") as BillingCycle | null;
  const filters: AnalyticsFilters = {
    range: rangeValue && validRanges.has(rangeValue) ? rangeValue : "30",
    from: params.get("from") ?? toDateTimeLocal(new Date(anchor.getTime() - 30 * 86_400_000)),
    until: params.get("until") ?? toDateTimeLocal(anchor),
    timezone: params.get("timezone") ?? (Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC"),
    interval: intervalValue && validIntervals.has(intervalValue) ? intervalValue : "DAY",
    currencyCode: params.get("currency") ?? undefined,
    billingCycle: cycleValue && validCycles.has(cycleValue) ? cycleValue : undefined,
  };
  const query = analyticsQuery(filters, anchor);
  const onFiltersChange = (updates: Partial<AnalyticsFilters>) =>
    updateSearch(params, setParams, analyticsSearchChanges(updates));
  return (
    <div className="space-y-7">
      <PageHeader
        actions={
          <Button
            onClick={() => {
              setAnchor(new Date());
              void queryClient.invalidateQueries({ queryKey: adminAnalyticsKeys.all });
            }}
            size="sm"
            variant="ghost"
          >
            <ArrowClockwiseIcon />
            Actualiser
          </Button>
        }
        title="Statistiques"
      />
      <SectionTabs
        items={tabs}
        onValueChange={(nextView) => updateSearch(params, setParams, { view: nextView, page: null })}
        value={view}
      />
      {view !== "attention" ? (
        <RangeBar
          filters={filters}
          onChange={onFiltersChange}
          showMoneyDimensions={view === "summary" || view === "financial"}
        />
      ) : null}
      {view === "summary" ? (
        <SummaryView query={query} />
      ) : view === "financial" ? (
        <FinancialView query={query} />
      ) : view === "subscriptions" ? (
        <SubscriptionView query={query} />
      ) : view === "offers" ? (
        <OfferView query={query} />
      ) : (
        <AttentionView params={params} setParams={setParams} />
      )}
    </div>
  );
}
