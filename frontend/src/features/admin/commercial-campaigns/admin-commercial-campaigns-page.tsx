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
  CommercialCampaignAudienceMode,
  CommercialCampaignSource,
  CommercialCampaignStatus,
  CommercialCampaignSummary,
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
  campaignListStateFromSorting,
  campaignSorting,
  readCampaignListState,
  writeCampaignListState,
} from "./commercial-campaign-list-state";
import { campaignAudienceMode, campaignSource, campaignStatus } from "./commercial-campaign-rules";

const column = createDataColumns<CommercialCampaignSummary>();
const shortDate = new Intl.DateTimeFormat("fr", { day: "2-digit", month: "short", year: "numeric" });

function destinationForCampaign(campaign: CommercialCampaignSummary, can: (permission: string) => boolean) {
  if (can(adminPermissions.campaignsRead)) return `/admin/campaigns/${campaign.id}`;
  if (can(adminPermissions.campaignsReadAudience) || can(adminPermissions.campaignsReadAudienceIdentities))
    return `/admin/campaigns/${campaign.id}/audience`;
  if (can(adminPermissions.campaignsRevisions) || can(adminPermissions.campaignsCompare))
    return `/admin/campaigns/${campaign.id}/revisions`;
  if (can(adminPermissions.campaignsHistory)) return `/admin/campaigns/${campaign.id}/history`;
  if (can(adminPermissions.campaignsOwner)) return `/admin/campaigns/${campaign.id}/owner`;
  return undefined;
}

function CampaignRowActions({ campaign }: { campaign: CommercialCampaignSummary }) {
  const session = useAdminSession();
  const destination = destinationForCampaign(campaign, session.can);
  return (
    <TableActionsCell label={`Actions pour ${campaign.name}`}>
      <RowAction
        disabled={!destination}
        disabledLabel="Votre rôle ne permet pas d’ouvrir une surface de cette campagne"
        icon={<ArrowRightIcon className="rtl:rotate-180" />}
        label="Ouvrir la campagne"
        to={destination}
      />
    </TableActionsCell>
  );
}

const columns = column.columns([
  column.accessor("name", {
    meta: { headerClassName: "min-w-[240px]" },
    header: ({ column: current }) => <SortHeader column={current}>Campagne</SortHeader>,
    cell: ({ row }) => (
      <span className="block min-w-0">
        <span className="block truncate font-medium">{row.original.name}</span>
        <span className="block truncate text-xs text-muted-foreground">
          {row.original.code} · R{row.original.revisionNumber}
        </span>
      </span>
    ),
  }),
  column.accessor("startsAt", {
    header: ({ column: current }) => <SortHeader column={current}>Période</SortHeader>,
    cell: ({ row }) => (
      <span className="block min-w-36 text-sm">
        <span className="block">{shortDate.format(new Date(row.original.startsAt))}</span>
        <span className="block text-xs text-muted-foreground">
          au {shortDate.format(new Date(row.original.endsAt))}
        </span>
      </span>
    ),
  }),
  column.accessor("audienceMode", {
    header: "Audience",
    cell: ({ row }) => {
      const count = row.original.frozenAccountCount ?? row.original.configuredAccountCount;
      return (
        <span className="block min-w-40">
          <span className="block text-sm">{campaignAudienceMode[row.original.audienceMode]}</span>
          <span className="block text-xs text-muted-foreground tabular-nums">
            {row.original.audienceMode === "PUBLIC" ? "Audience dynamique" : count == null ? "—" : `${count} compte(s)`}
          </span>
        </span>
      );
    },
  }),
  column.accessor("source", {
    header: ({ column: current }) => <SortHeader column={current}>Origine</SortHeader>,
    cell: ({ row }) => campaignSource[row.original.source],
  }),
  column.accessor("status", {
    header: ({ column: current }) => <SortHeader column={current}>Statut</SortHeader>,
    cell: ({ row }) => {
      const status = campaignStatus[row.original.status];
      const blockers = Object.values(row.original.blockedActions).flat().length;
      return (
        <span className="flex items-center gap-2">
          <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
          {blockers ? (
            <WarningCircleIcon aria-label={`${blockers} blocage(s)`} className="size-4 text-warning" />
          ) : null}
        </span>
      );
    },
  }),
  column.display({
    id: "actions",
    meta: tableActionsColumnMeta(1),
    header: "Actions",
    cell: ({ row }) => <CampaignRowActions campaign={row.original} />,
  }),
]);

function MobileCampaigns({ campaigns }: { campaigns: CommercialCampaignSummary[] }) {
  if (!campaigns.length)
    return (
      <div className="md:hidden">
        <EmptyState title="Aucune campagne" />
      </div>
    );
  return (
    <div className="divide-y md:hidden">
      {campaigns.map((campaign) => {
        const status = campaignStatus[campaign.status];
        return (
          <article className="space-y-3 p-4" key={campaign.id}>
            <div className="flex items-start gap-3">
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium">{campaign.name}</p>
                <p className="mt-0.5 truncate text-xs text-muted-foreground">
                  {campaign.code} · R{campaign.revisionNumber}
                </p>
              </div>
              <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
              <CampaignRowActions campaign={campaign} />
            </div>
            <dl className="grid grid-cols-2 gap-3 text-xs">
              <div>
                <dt className="text-muted-foreground">Audience</dt>
                <dd className="mt-0.5 font-medium">{campaignAudienceMode[campaign.audienceMode]}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Début</dt>
                <dd className="mt-0.5 font-medium">{shortDate.format(new Date(campaign.startsAt))}</dd>
              </div>
            </dl>
          </article>
        );
      })}
    </div>
  );
}

export function AdminCommercialCampaignsPage() {
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const state = readCampaignListState(params);
  const [search, setSearch] = useState(state.search);
  const debouncedSearch = useDebouncedValue(search);
  useEffect(() => setSearch(state.search), [state.search]);
  useEffect(() => {
    if (debouncedSearch === state.search) return;
    setParams(writeCampaignListState(params, { ...state, search: debouncedSearch, page: 0 }), { replace: true });
  }, [debouncedSearch, params, setParams, state]);
  const update = useCallback(
    (changes: Partial<typeof state>) =>
      setParams(
        writeCampaignListState(params, {
          ...state,
          ...changes,
          page: "page" in changes ? (changes.page ?? 0) : 0,
        }),
        { replace: true },
      ),
    [params, setParams, state],
  );
  const filters = { ...state, search: debouncedSearch };
  const campaigns = useQuery({
    queryKey: adminCommercialKeys.campaigns.list(filters),
    queryFn: () =>
      adminApi.commercialCampaigns({
        search: debouncedSearch || undefined,
        status: state.status === "ALL" ? undefined : (state.status as CommercialCampaignStatus),
        audienceMode: state.audienceMode === "ALL" ? undefined : (state.audienceMode as CommercialCampaignAudienceMode),
        source: state.source === "ALL" ? undefined : (state.source as CommercialCampaignSource),
        includeArchived: state.includeArchived,
        page: state.page,
        size: state.size,
        sort: state.sort,
        direction: state.direction,
      }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.campaignsList),
    placeholderData: keepPreviousData,
  });
  useEffect(() => {
    if (!campaigns.data || campaigns.isPlaceholderData) return;
    const bounded = campaigns.data.totalPages === 0 ? 0 : Math.min(state.page, campaigns.data.totalPages - 1);
    if (bounded !== state.page) update({ page: bounded });
  }, [campaigns.data, campaigns.isPlaceholderData, state.page, update]);
  const sorting: SortingState = campaignSorting(state);
  return (
    <div className="space-y-7">
      <PageHeader
        actions={
          session.can(adminPermissions.campaignsCreate) ? (
            <Button asChild>
              <Link to="/admin/campaigns/new">
                <PlusIcon />
                Créer une campagne
              </Link>
            </Button>
          ) : undefined
        }
        title="Campagnes"
      />
      {!session.can(adminPermissions.campaignsList) ? (
        <PermissionState />
      ) : (
        <section aria-busy={campaigns.isFetching} className="overflow-hidden rounded-xl border bg-card">
          <div className="grid gap-3 border-b p-4 sm:grid-cols-2 xl:grid-cols-[minmax(240px,1fr)_repeat(3,180px)]">
            <div className="relative">
              <MagnifyingGlassIcon className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                aria-label="Rechercher les campagnes"
                className="ps-9"
                maxLength={180}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Nom ou code…"
                value={search}
              />
            </div>
            <Select onValueChange={(value) => update({ status: value as typeof state.status })} value={state.status}>
              <SelectTrigger aria-label="Statut">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">Tous les statuts</SelectItem>
                {Object.entries(campaignStatus).map(([value, item]) => (
                  <SelectItem key={value} value={value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select
              onValueChange={(value) => update({ audienceMode: value as typeof state.audienceMode })}
              value={state.audienceMode}
            >
              <SelectTrigger aria-label="Audience">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">Toutes les audiences</SelectItem>
                {Object.entries(campaignAudienceMode).map(([value, label]) => (
                  <SelectItem key={value} value={value}>
                    {label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select onValueChange={(value) => update({ source: value as typeof state.source })} value={state.source}>
              <SelectTrigger aria-label="Origine">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">Toutes les origines</SelectItem>
                {Object.entries(campaignSource).map(([value, label]) => (
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
                id="campaign-archives"
                onCheckedChange={(checked) => update({ includeArchived: checked === true })}
              />
              <Label className="cursor-pointer font-normal" htmlFor="campaign-archives">
                Inclure les archives
              </Label>
            </div>
            <div className="flex items-center gap-3">
              <Button
                aria-label="Actualiser les campagnes"
                disabled={campaigns.isFetching}
                onClick={() => void campaigns.refetch()}
                size="icon-sm"
                title="Actualiser les campagnes"
                variant="ghost"
              >
                <ArrowClockwiseIcon className={campaigns.isFetching ? "animate-spin" : undefined} />
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
              <span className="text-xs text-muted-foreground">{campaigns.data?.totalElements ?? 0} résultat(s)</span>
            </div>
          </div>
          {campaigns.isLoading ? (
            <div className="p-4">
              <LoadingState />
            </div>
          ) : campaigns.isError ? (
            <ErrorState retry={() => void campaigns.refetch()} />
          ) : campaigns.data ? (
            <>
              <div className="hidden md:block">
                <DataTable
                  columns={columns}
                  data={campaigns.data.content}
                  emptyState={
                    <EmptyState description="Modifiez les filtres ou créez un brouillon." title="Aucune campagne" />
                  }
                  getRowId={(campaign) => campaign.id}
                  onSortingChange={(next) => update(campaignListStateFromSorting(state, next) as Partial<typeof state>)}
                  sorting={sorting}
                />
              </div>
              <MobileCampaigns campaigns={campaigns.data.content} />
              <PaginationBar
                onPageChange={(page) => update({ page })}
                page={campaigns.data.page}
                totalElements={campaigns.data.totalElements}
                totalPages={campaigns.data.totalPages}
              />
            </>
          ) : null}
        </section>
      )}
    </div>
  );
}
