import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useSearchParams } from "react-router";
import type { SubscriptionStatus } from "@/api/contracts";
import { type FamilySubscriber, planApplicationApi, type SubscriberView } from "@/api/plan-application-api";
import { adminPermissions, adminSubscriptionDetailSurfacePermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable } from "@/components/patterns/data-table";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, PermissionState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { formatExactMoney } from "@/lib/exact-decimal";
import { useDebouncedValue } from "@/lib/use-debounced-value";

const views = {
  ALL: "allVersions",
  CURRENT_VERSION: "currentVersion",
  OTHER_VERSIONS: "otherVersions",
  PENDING: "pending",
  NEEDS_REVIEW: "blocked",
} as const;
const column = createDataColumns<FamilySubscriber>();

/** One table for the subscriber workspace and the Plan-scoped audience picker. */
export function PlanSubscriberTable({
  planId,
  selection,
}: {
  planId: string;
  selection?: {
    view: "ALL" | "CURRENT_VERSION";
    ids: string[];
    statuses: SubscriptionStatus[];
    onChange: (ids: string[]) => void;
  };
}) {
  const { t, i18n } = useTranslation();
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const [pickerSearch, setPickerSearch] = useState("");
  const [pickerPage, setPickerPage] = useState(0);
  const viewParam = params.get("view");
  const view: SubscriberView =
    selection?.view ?? (viewParam && viewParam in views ? (viewParam as SubscriberView) : "ALL");
  const search = selection ? pickerSearch : (params.get("search") ?? "");
  const page = selection ? pickerPage : Math.max(0, Number(params.get("page")) || 0);
  const deferred = useDebouncedValue(search);
  const update = (key: string, value: string) =>
    setParams(
      (previous) => {
        const next = new URLSearchParams(previous);
        next.set(key, value);
        if (key !== "page") next.delete("page");
        return next;
      },
      { replace: true },
    );
  const query = useQuery({
    queryKey: ["admin", "plans", planId, "family-subscribers", view, deferred, page],
    queryFn: () => planApplicationApi.subscribers(planId, { view, search: deferred || undefined, page }),
    enabled: session.can(adminPermissions.plansListFamilySubscribers),
  });
  if (!session.can(adminPermissions.plansListFamilySubscribers)) return <PermissionState />;
  const canOpen = adminSubscriptionDetailSurfacePermissions.some(session.can);
  const canApply = [
    adminPermissions.plansCreateApplication,
    adminPermissions.plansPreviewApplication,
    adminPermissions.plansReadApplication,
    adminPermissions.plansListVersions,
    adminPermissions.plansReadDetail,
  ].every(session.can);
  const toggle = (id: string, checked: boolean) =>
    selection?.onChange(checked ? [...new Set([...selection.ids, id])] : selection.ids.filter((value) => value !== id));
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <div className="flex flex-wrap items-center gap-3 border-b p-4">
        {!selection && (
          <Select value={view} onValueChange={(value) => update("view", value)}>
            <SelectTrigger className="w-60" aria-label={t("planApplication.source")}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Object.entries(views).map(([key, label]) => (
                <SelectItem key={key} value={key}>
                  {t(`planApplication.${label}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
        <Input
          className="max-w-sm"
          aria-label={t("planApplication.search")}
          placeholder={t("planApplication.search")}
          value={search}
          maxLength={200}
          onChange={(event) => {
            if (selection) {
              setPickerSearch(event.target.value);
              setPickerPage(0);
            } else update("search", event.target.value);
          }}
        />
        {selection && (
          <span className="text-xs text-muted-foreground">
            {t("planApplication.selected", { count: selection.ids.length })}
          </span>
        )}
      </div>
      {selection && selection.ids.length > 0 && (
        <div className="border-b p-3">
          <Button size="sm" variant="ghost" onClick={() => selection.onChange([])}>
            {t("planApplication.clearSelection")}
          </Button>
          <p className="text-xs text-muted-foreground">{t("planApplication.selectionRetained")}</p>
        </div>
      )}
      {query.isError ? (
        <ErrorState retry={() => void query.refetch()} />
      ) : (
        <DataTable
          isLoading={query.isLoading}
          data={query.data?.content ?? []}
          getRowId={(row) => row.accountId}
          emptyState={<EmptyState title={t("planApplication.noResults")} />}
          columns={column.columns([
            ...(selection
              ? [
                  column.display({
                    id: "select",
                    header: "",
                    cell: ({ row }) => (
                      <Checkbox
                        aria-label={`${t("planApplication.selectAccount")} ${row.original.accountName}`}
                        checked={selection.ids.includes(row.original.accountId)}
                        disabled={
                          !selection.statuses.includes(row.original.status) ||
                          (!selection.ids.includes(row.original.accountId) && selection.ids.length >= 10000)
                        }
                        onCheckedChange={(value) => toggle(row.original.accountId, value === true)}
                      />
                    ),
                  }),
                ]
              : []),
            column.accessor("accountName", {
              header: t("planApplication.account"),
              cell: ({ getValue }) => <span className="font-medium">{getValue()}</span>,
            }),
            column.accessor("productVersionNumber", {
              header: t("planApplication.versions"),
              cell: ({ row }) =>
                session.can(adminPermissions.plansReadDetail) ? (
                  <Link className="font-medium text-primary hover:underline" to={`/admin/plans/${row.original.planId}`}>
                    V{row.original.productVersionNumber}
                  </Link>
                ) : (
                  `V${row.original.productVersionNumber}`
                ),
            }),
            column.accessor("status", {
              header: t("planApplication.status"),
              cell: ({ getValue }) => t(`planApplication.${getValue()}`),
            }),
            column.display({
              id: "price",
              header: t("planApplication.price"),
              cell: ({ row }) => formatExactMoney(row.original.retainedTotal, row.original.currency),
            }),
            column.accessor("periodEnd", {
              header: t("planApplication.periodEnd"),
              cell: ({ getValue }) =>
                new Intl.DateTimeFormat(i18n.language === "ar" ? "ar-MA" : "fr-MA", { dateStyle: "medium" }).format(
                  new Date(getValue()),
                ),
            }),
            ...(!selection
              ? [
                  column.display({
                    id: "actions",
                    header: t("planVersions.actions"),
                    cell: ({ row }) => (
                      <div className="flex flex-wrap gap-2">
                        {canOpen && (
                          <Button asChild size="sm" variant="ghost">
                            <Link to={`/admin/subscriptions/${row.original.accountId}`}>{t("planVersions.open")}</Link>
                          </Button>
                        )}
                        {canApply && (
                          <Button asChild size="sm" variant="ghost">
                            <Link to={`/admin/plans/${planId}/apply?account=${row.original.accountId}`}>
                              {t("planApplication.changeVersion")}
                            </Link>
                          </Button>
                        )}
                      </div>
                    ),
                  }),
                ]
              : []),
          ])}
        />
      )}
      {query.data && (
        <PaginationBar
          {...query.data}
          onPageChange={(next) => (selection ? setPickerPage(next) : update("page", String(next)))}
        />
      )}
    </section>
  );
}
