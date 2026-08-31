import { ArrowRightIcon, PlusIcon } from "@phosphor-icons/react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import type { SortingState } from "@tanstack/react-table";
import { useMemo, useState } from "react";
import { Link } from "react-router";
import { adminApi } from "@/api/admin-api";
import type { SubscriptionChangeJobStatus, SubscriptionChangeJobSummary } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable, SortHeader } from "@/components/patterns/data-table";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { RowAction } from "@/components/patterns/row-action";
import { StatusBadge } from "@/components/patterns/status-badge";
import { TableActionsCell, tableActionsColumnMeta } from "@/components/patterns/table-actions-cell";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { adminCommercialKeys, commercialQueryEnabled } from "@/features/commercial/commercial-query";
import { changeJobProcessedCount, changeJobStatusPresentation } from "./subscription-change-job-rules";

const dateTime = (value: string | null) =>
  value ? new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "—";

export function AdminSubscriptionChangeJobsPage() {
  const session = useAdminSession();
  const [status, setStatus] = useState<SubscriptionChangeJobStatus | "all">("all");
  const [page, setPage] = useState(0);
  const [sorting, setSorting] = useState<SortingState>([{ id: "createdAt", desc: true }]);
  const request = {
    status: status === "all" ? undefined : status,
    page,
    size: 20,
    sort: (sorting[0]?.id ?? "createdAt") as "createdAt" | "executeAt" | "status" | "completedAt",
    direction: sorting[0]?.desc === false ? ("asc" as const) : ("desc" as const),
  };
  const jobs = useQuery({
    queryKey: adminCommercialKeys.subscriptions.jobs(request),
    queryFn: () => adminApi.subscriptionChangeJobs(request),
    enabled: commercialQueryEnabled(session.can, adminPermissions.subscriptionsListChangeJobs),
    placeholderData: keepPreviousData,
  });
  const canOpen =
    session.can(adminPermissions.subscriptionsReadChangeJob) ||
    session.can(adminPermissions.subscriptionsReadChangeJobResults);
  const column = useMemo(() => createDataColumns<SubscriptionChangeJobSummary>(), []);
  const columns = useMemo(
    () =>
      column.columns([
        column.accessor("createdAt", {
          meta: { headerClassName: "min-w-48" },
          header: ({ column: item }) => <SortHeader column={item}>Créé le</SortHeader>,
          cell: ({ row }) => (
            <span>
              <time className="block font-medium" dateTime={row.original.createdAt}>
                {dateTime(row.original.createdAt)}
              </time>
              <span className="mt-0.5 block max-w-80 truncate text-xs text-muted-foreground">
                {row.original.reason}
              </span>
            </span>
          ),
        }),
        column.accessor("status", {
          meta: { headerClassName: "w-48", cellClassName: "w-48" },
          header: ({ column: item }) => <SortHeader column={item}>État</SortHeader>,
          cell: ({ row }) => {
            const presentation = changeJobStatusPresentation[row.original.status];
            return (
              <StatusBadge dot={false} tone={presentation.tone}>
                {presentation.label}
              </StatusBadge>
            );
          },
        }),
        column.display({
          id: "progress",
          header: "Progression",
          cell: ({ row }) => {
            const processed = changeJobProcessedCount(row.original);
            return (
              <span>
                <span className="block font-medium tabular-nums">
                  {processed} / {row.original.targetCount}
                </span>
                <span className="mt-0.5 block text-xs text-muted-foreground">
                  {row.original.failedCount + row.original.conflictCount
                    ? `${row.original.failedCount} échec(s) · ${row.original.conflictCount} conflit(s)`
                    : `${row.original.appliedCount + row.original.pendingCount} appliqué(s) ou planifié(s)`}
                </span>
              </span>
            );
          },
        }),
        column.accessor("executeAt", {
          meta: { headerClassName: "w-48", cellClassName: "w-48" },
          header: ({ column: item }) => <SortHeader column={item}>Exécution</SortHeader>,
          cell: ({ row }) => (
            <time dateTime={row.original.executeAt ?? undefined}>{dateTime(row.original.executeAt)}</time>
          ),
        }),
        column.display({
          id: "actions",
          meta: tableActionsColumnMeta(1),
          header: "Actions",
          cell: ({ row }) => (
            <TableActionsCell label="Actions du traitement">
              <RowAction
                disabled={!canOpen}
                disabledLabel="Consultation du traitement non autorisée"
                icon={<ArrowRightIcon className="rtl:rotate-180" />}
                label="Ouvrir"
                to={canOpen ? `/admin/subscription-jobs/${row.original.id}` : undefined}
              />
            </TableActionsCell>
          ),
        }),
      ]),
    [canOpen, column],
  );

  return (
    <div className="space-y-7">
      <PageHeader
        actions={
          session.can(adminPermissions.subscriptionsPreviewChangeJob) ? (
            <Button asChild>
              <Link to="/admin/subscription-jobs/new">
                <PlusIcon />
                Nouveau traitement
              </Link>
            </Button>
          ) : undefined
        }
        title="Changements en lot"
      />
      <section className="overflow-hidden rounded-xl border bg-card">
        <div className="flex border-b p-4">
          <Select
            onValueChange={(value) => {
              setStatus(value as typeof status);
              setPage(0);
            }}
            value={status}
          >
            <SelectTrigger aria-label="État du traitement" className="w-full sm:w-60">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Tous les états</SelectItem>
              {Object.entries(changeJobStatusPresentation).map(([value, presentation]) => (
                <SelectItem key={value} value={value}>
                  {presentation.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        {jobs.isLoading ? (
          <div className="p-5">
            <LoadingState rows={5} />
          </div>
        ) : jobs.isError ? (
          <ErrorState retry={() => void jobs.refetch()} />
        ) : (
          <>
            <DataTable
              columns={columns}
              data={jobs.data?.content ?? []}
              emptyState={<EmptyState title="Aucun traitement" />}
              getRowId={(job) => job.id}
              onSortingChange={(next) => {
                setSorting(next);
                setPage(0);
              }}
              sorting={sorting}
            />
            <PaginationBar
              onPageChange={setPage}
              page={jobs.data?.page ?? 0}
              totalElements={jobs.data?.totalElements ?? 0}
              totalPages={jobs.data?.totalPages ?? 0}
            />
          </>
        )}
      </section>
    </div>
  );
}
