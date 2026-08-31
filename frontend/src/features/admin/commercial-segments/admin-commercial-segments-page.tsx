import {
  ArrowClockwiseIcon,
  ArrowRightIcon,
  MagnifyingGlassIcon,
  PlusIcon,
  WarningCircleIcon,
} from "@phosphor-icons/react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import type { SortingState } from "@tanstack/react-table";
import { useCallback, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { adminApi } from "@/api/admin-api";
import type {
  CommercialSegmentKind,
  CommercialSegmentSource,
  CommercialSegmentStatus,
  CommercialSegmentSummary,
} from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable, SortHeader } from "@/components/patterns/data-table";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { RowAction } from "@/components/patterns/row-action";
import { StatusBadge } from "@/components/patterns/status-badge";
import { TableActionsCell, tableActionsColumnMeta } from "@/components/patterns/table-actions-cell";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { adminCommercialKeys, commercialQueryEnabled } from "@/features/commercial/commercial-query";
import { useDebouncedValue } from "@/lib/use-debounced-value";
import {
  commercialSegmentSorting,
  readCommercialSegmentListState,
  segmentListStateFromSorting,
  writeCommercialSegmentListState,
} from "./commercial-segment-list-state";
import { segmentKind, segmentSource, segmentStatus } from "./commercial-segment-rules";

const column = createDataColumns<CommercialSegmentSummary>();

function SegmentRowActions({ segment }: { segment: CommercialSegmentSummary }) {
  const session = useAdminSession();
  const destination = session.can(adminPermissions.segmentsRead)
    ? `/admin/segments/${segment.id}`
    : session.can(adminPermissions.segmentsCount) ||
        session.can(adminPermissions.segmentsPreview) ||
        session.can(adminPermissions.segmentsReadSampleIdentities)
      ? `/admin/segments/${segment.id}/audience`
      : session.can(adminPermissions.segmentsReadRevisions) || session.can(adminPermissions.segmentsCompare)
        ? `/admin/segments/${segment.id}/revisions`
        : session.can(adminPermissions.segmentsReadActivations) ||
            session.can(adminPermissions.segmentsReadActivationAudience) ||
            session.can(adminPermissions.segmentsReadActivationIdentities)
          ? `/admin/segments/${segment.id}/activations`
          : session.can(adminPermissions.segmentsReadHistory)
            ? `/admin/segments/${segment.id}/history`
            : session.can(adminPermissions.segmentsReadOwner)
              ? `/admin/segments/${segment.id}/owner`
              : undefined;
  return (
    <TableActionsCell label={`Actions pour ${segment.name}`}>
      <RowAction
        disabled={!destination}
        disabledLabel="Votre rôle ne permet pas d’ouvrir une surface de ce segment"
        icon={<ArrowRightIcon className="rtl:rotate-180" />}
        label="Ouvrir le segment"
        to={destination}
      />
    </TableActionsCell>
  );
}

const columns = column.columns([
  column.accessor("name", {
    meta: { headerClassName: "min-w-[240px]" },
    header: ({ column: current }) => <SortHeader column={current}>Segment</SortHeader>,
    cell: ({ row }) => (
      <span className="block min-w-0">
        <span className="block truncate font-medium">{row.original.name}</span>
        <span className="block truncate text-xs text-muted-foreground">
          {row.original.code} · R{row.original.revisionNumber}
        </span>
      </span>
    ),
  }),
  column.accessor("kind", {
    header: ({ column: current }) => <SortHeader column={current}>Définition</SortHeader>,
    cell: ({ row }) => (
      <span className="block min-w-40">
        <span className="block text-sm">{segmentKind[row.original.kind]}</span>
        <span className="block text-xs text-muted-foreground">{segmentSource[row.original.source]}</span>
      </span>
    ),
  }),
  column.accessor("configuredAccountCount", {
    header: "Comptes configurés",
    cell: ({ row }) => (
      <span className="tabular-nums">
        {row.original.kind === "EXPLICIT_ACCOUNTS" ? row.original.configuredAccountCount : "Dynamique"}
      </span>
    ),
  }),
  column.accessor("latestActivationAccountCount", {
    header: "Dernière activation",
    cell: ({ row }) => (
      <span className="tabular-nums">
        {row.original.latestActivationAccountCount == null
          ? "—"
          : `${row.original.latestActivationAccountCount} compte(s)`}
      </span>
    ),
  }),
  column.accessor("status", {
    header: ({ column: current }) => <SortHeader column={current}>Statut</SortHeader>,
    cell: ({ row }) => {
      const status = segmentStatus[row.original.status];
      const blockerCount = Object.values(row.original.blockedActions).flat().length;
      return (
        <span className="flex items-center gap-2">
          <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
          {blockerCount ? (
            <WarningCircleIcon aria-label={`${blockerCount} blocage(s)`} className="size-4 text-warning" />
          ) : null}
        </span>
      );
    },
  }),
  column.display({
    id: "actions",
    meta: tableActionsColumnMeta(1),
    header: "Actions",
    cell: ({ row }) => <SegmentRowActions segment={row.original} />,
  }),
]);

function MobileSegments({ segments }: { segments: CommercialSegmentSummary[] }) {
  if (!segments.length)
    return (
      <div className="md:hidden">
        <EmptyState title="Aucun segment" />
      </div>
    );
  return (
    <div className="divide-y md:hidden">
      {segments.map((segment) => {
        const status = segmentStatus[segment.status];
        return (
          <article className="space-y-3 p-4" key={segment.id}>
            <div className="flex items-start gap-3">
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium">{segment.name}</p>
                <p className="mt-0.5 truncate text-xs text-muted-foreground">{segmentKind[segment.kind]}</p>
              </div>
              <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
              <SegmentRowActions segment={segment} />
            </div>
            <dl className="grid grid-cols-2 gap-3 text-xs">
              <div>
                <dt className="text-muted-foreground">Origine</dt>
                <dd className="mt-0.5 font-medium">{segmentSource[segment.source]}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Dernière audience</dt>
                <dd className="mt-0.5 font-medium tabular-nums">{segment.latestActivationAccountCount ?? "—"}</dd>
              </div>
            </dl>
          </article>
        );
      })}
    </div>
  );
}

export function AdminCommercialSegmentsPage() {
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const state = readCommercialSegmentListState(params);
  const [search, setSearch] = useState(state.search);
  const debouncedSearch = useDebouncedValue(search);
  useEffect(() => setSearch(state.search), [state.search]);
  useEffect(() => {
    if (debouncedSearch === state.search) return;
    setParams(writeCommercialSegmentListState(params, { ...state, search: debouncedSearch, page: 0 }), {
      replace: true,
    });
  }, [debouncedSearch, params, setParams, state]);
  const update = useCallback(
    (changes: Partial<typeof state>) =>
      setParams(
        writeCommercialSegmentListState(params, {
          ...state,
          ...changes,
          page: "page" in changes ? (changes.page ?? 0) : 0,
        }),
        { replace: true },
      ),
    [params, setParams, state],
  );
  const filters = { ...state, search: debouncedSearch };
  const segments = useQuery({
    queryKey: adminCommercialKeys.segments.list(filters),
    queryFn: () =>
      adminApi.commercialSegments({
        search: debouncedSearch || undefined,
        status: state.status === "ALL" ? undefined : (state.status as CommercialSegmentStatus),
        kind: state.kind === "ALL" ? undefined : (state.kind as CommercialSegmentKind),
        source: state.source === "ALL" ? undefined : (state.source as CommercialSegmentSource),
        includeArchived: state.includeArchived,
        page: state.page,
        size: state.size,
        sort: state.sort,
        direction: state.direction,
      }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.segmentsList),
    placeholderData: keepPreviousData,
  });
  useEffect(() => {
    if (!segments.data || segments.isPlaceholderData) return;
    const bounded = segments.data.totalPages === 0 ? 0 : Math.min(state.page, segments.data.totalPages - 1);
    if (bounded !== state.page) update({ page: bounded });
  }, [segments.data, segments.isPlaceholderData, state.page, update]);
  const sorting: SortingState = commercialSegmentSorting(state);
  return (
    <div className="space-y-7">
      <PageHeader
        actions={
          session.can(adminPermissions.segmentsCreate) ? (
            <Button asChild>
              <Link to="/admin/segments/new">
                <PlusIcon />
                Créer un segment
              </Link>
            </Button>
          ) : undefined
        }
        title="Segments de comptes"
      />
      {!session.can(adminPermissions.segmentsList) ? (
        <PermissionState />
      ) : (
        <section aria-busy={segments.isFetching} className="overflow-hidden rounded-xl border bg-card">
          <div className="grid gap-3 border-b p-4 sm:grid-cols-2 xl:grid-cols-[minmax(240px,1fr)_repeat(3,180px)]">
            <div className="relative">
              <MagnifyingGlassIcon className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                aria-label="Rechercher les segments"
                className="ps-9"
                maxLength={180}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Nom ou code…"
                value={search}
              />
            </div>
            <Select onValueChange={(value) => update({ status: value as typeof state.status })} value={state.status}>
              <SelectTrigger aria-label="Statut" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">Tous les statuts</SelectItem>
                {Object.entries(segmentStatus).map(([value, item]) => (
                  <SelectItem key={value} value={value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select onValueChange={(value) => update({ kind: value as typeof state.kind })} value={state.kind}>
              <SelectTrigger aria-label="Type de définition" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">Toutes les définitions</SelectItem>
                {Object.entries(segmentKind).map(([value, label]) => (
                  <SelectItem key={value} value={value}>
                    {label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select onValueChange={(value) => update({ source: value as typeof state.source })} value={state.source}>
              <SelectTrigger aria-label="Origine" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">Toutes les origines</SelectItem>
                {Object.entries(segmentSource).map(([value, label]) => (
                  <SelectItem key={value} value={value}>
                    {label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="flex min-h-12 flex-wrap items-center justify-between gap-4 border-b px-4 py-2">
            <div className="flex items-center gap-2">
              <Checkbox
                checked={state.includeArchived}
                id="segment-archives"
                onCheckedChange={(checked) => update({ includeArchived: checked === true })}
              />
              <Label className="cursor-pointer font-normal" htmlFor="segment-archives">
                Inclure les archives
              </Label>
            </div>
            <div className="flex items-center gap-3">
              <Button
                aria-label="Actualiser les segments"
                disabled={segments.isFetching}
                onClick={() => void segments.refetch()}
                size="icon-sm"
                title="Actualiser les segments"
                variant="ghost"
              >
                <ArrowClockwiseIcon className={segments.isFetching ? "animate-spin" : undefined} />
              </Button>
              <Select
                onValueChange={(value) => update({ size: Number(value) as typeof state.size })}
                value={String(state.size)}
              >
                <SelectTrigger aria-label="Résultats par page" className="h-8 w-[118px]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="10">10 par page</SelectItem>
                  <SelectItem value="20">20 par page</SelectItem>
                  <SelectItem value="50">50 par page</SelectItem>
                </SelectContent>
              </Select>
              <span className="text-xs text-muted-foreground">{segments.data?.totalElements ?? 0} résultat(s)</span>
            </div>
          </div>
          {segments.isLoading ? (
            <div className="p-4">
              <LoadingState />
            </div>
          ) : segments.isError ? (
            <ErrorState retry={() => void segments.refetch()} />
          ) : segments.data ? (
            <>
              <div className="hidden md:block">
                <DataTable
                  columns={columns}
                  data={segments.data.content}
                  emptyState={
                    <EmptyState description="Modifiez les filtres ou créez un brouillon." title="Aucun segment" />
                  }
                  getRowId={(segment) => segment.id}
                  onSortingChange={(next) => update(segmentListStateFromSorting(state, next) as Partial<typeof state>)}
                  sorting={sorting}
                />
              </div>
              <MobileSegments segments={segments.data.content} />
              <PaginationBar
                onPageChange={(page) => update({ page })}
                page={segments.data.page}
                totalElements={segments.data.totalElements}
                totalPages={segments.data.totalPages}
              />
            </>
          ) : null}
        </section>
      )}
    </div>
  );
}
