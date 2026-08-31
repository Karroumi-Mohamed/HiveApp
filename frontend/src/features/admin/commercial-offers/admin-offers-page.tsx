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
import { adminOfferApi } from "@/api/admin-offer-api";
import type { OfferAcceptance, OfferDiscovery, OfferStatus, OfferSummary } from "@/api/offer-contracts";
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
import { offerListStateFromSorting, offerSorting, readOfferListState, writeOfferListState } from "./offer-list-state";
import { offerAcceptance, offerDiscovery, offerPeriodLabel, offerStatus } from "./offer-rules";

const column = createDataColumns<OfferSummary>();
const shortDate = new Intl.DateTimeFormat("fr", { day: "2-digit", month: "short", year: "numeric" });

function readableDestination(offerId: string, can: (permission: string) => boolean) {
  if (can(adminPermissions.offersRead)) return `/admin/offers/${offerId}`;
  if (can(adminPermissions.offersReadOperations)) return `/admin/offers/${offerId}/operations`;
  if (can(adminPermissions.offersReadStats)) return `/admin/offers/${offerId}/results`;
  if (can(adminPermissions.offersReadRedemptions)) return `/admin/offers/${offerId}/redemptions`;
  if (can(adminPermissions.offersHistory)) return `/admin/offers/${offerId}/history`;
  return null;
}

function OfferRowActions({ offer }: { offer: OfferSummary }) {
  const session = useAdminSession();
  const destination = readableDestination(offer.id, session.can);
  return (
    <TableActionsCell label={`Actions pour ${offer.name}`}>
      <RowAction
        disabled={!destination}
        disabledLabel="Votre rôle ne permet pas d’ouvrir une surface de cette offre"
        icon={<ArrowRightIcon className="rtl:rotate-180" />}
        label="Ouvrir l’offre"
        to={destination ?? undefined}
      />
    </TableActionsCell>
  );
}

const columns = column.columns([
  column.accessor("name", {
    meta: { headerClassName: "w-[260px] max-w-[260px]", cellClassName: "w-[260px] max-w-[260px]" },
    header: ({ column: current }) => <SortHeader column={current}>Offre</SortHeader>,
    cell: ({ row }) => (
      <span className="block min-w-0">
        <span className="block truncate font-medium">{row.original.name}</span>
        <span className="block truncate text-xs text-muted-foreground" dir="ltr">
          {row.original.businessCode} · R{row.original.revisionNumber}
        </span>
      </span>
    ),
  }),
  column.accessor("startsAt", {
    meta: { headerClassName: "w-36", cellClassName: "w-36" },
    header: ({ column: current }) => <SortHeader column={current}>Période</SortHeader>,
    cell: ({ row }) => (
      <span className="block text-sm">
        <span className="block">{offerPeriodLabel(row.original.startsAt, row.original.endsAt)}</span>
        <span className="block text-xs text-muted-foreground">
          {shortDate.format(new Date(row.original.startsAt))} — {shortDate.format(new Date(row.original.endsAt))}
        </span>
      </span>
    ),
  }),
  column.accessor("discovery", {
    meta: { headerClassName: "w-32", cellClassName: "w-32" },
    header: "Accès",
    cell: ({ row }) => (
      <span className="block text-sm">
        <span className="block">{offerDiscovery[row.original.discovery]}</span>
        <span className="block text-xs text-muted-foreground">{offerAcceptance[row.original.acceptance]}</span>
      </span>
    ),
  }),
  column.accessor("campaign", {
    meta: { headerClassName: "w-44", cellClassName: "w-44" },
    enableSorting: false,
    header: "Campagne",
    cell: ({ row }) => (
      <span className="block min-w-0">
        <span className="block truncate text-sm">{row.original.campaign.name}</span>
        <span className="block truncate text-xs text-muted-foreground">R{row.original.campaign.revisionNumber}</span>
      </span>
    ),
  }),
  column.accessor("status", {
    meta: { headerClassName: "w-32", cellClassName: "w-32" },
    header: ({ column: current }) => <SortHeader column={current}>Statut</SortHeader>,
    cell: ({ row }) => {
      const status = offerStatus[row.original.status];
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
    cell: ({ row }) => <OfferRowActions offer={row.original} />,
  }),
]);

function MobileOffers({ offers }: { offers: OfferSummary[] }) {
  if (!offers.length)
    return (
      <div className="md:hidden">
        <EmptyState title="Aucune offre" />
      </div>
    );
  return (
    <div className="divide-y md:hidden">
      {offers.map((offer) => {
        const status = offerStatus[offer.status];
        return (
          <article className="space-y-3 p-4" key={offer.id}>
            <div className="flex items-start gap-3">
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium">{offer.name}</p>
                <p className="mt-0.5 truncate text-xs text-muted-foreground" dir="ltr">
                  {offer.businessCode} · R{offer.revisionNumber}
                </p>
              </div>
              <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
              <OfferRowActions offer={offer} />
            </div>
            <dl className="grid grid-cols-2 gap-3 text-xs">
              <div>
                <dt className="text-muted-foreground">Accès</dt>
                <dd className="mt-0.5 font-medium">{offerDiscovery[offer.discovery]}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Période</dt>
                <dd className="mt-0.5 font-medium">{offerPeriodLabel(offer.startsAt, offer.endsAt)}</dd>
              </div>
            </dl>
          </article>
        );
      })}
    </div>
  );
}

export function AdminOffersPage() {
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const state = readOfferListState(params);
  const [search, setSearch] = useState(state.search);
  const debouncedSearch = useDebouncedValue(search);
  useEffect(() => setSearch(state.search), [state.search]);
  useEffect(() => {
    if (debouncedSearch === state.search) return;
    setParams(writeOfferListState(params, { ...state, search: debouncedSearch, page: 0 }), { replace: true });
  }, [debouncedSearch, params, setParams, state]);
  const update = useCallback(
    (changes: Partial<typeof state>) =>
      setParams(
        writeOfferListState(params, { ...state, ...changes, page: "page" in changes ? (changes.page ?? 0) : 0 }),
        { replace: true },
      ),
    [params, setParams, state],
  );
  const filters = { ...state, search: debouncedSearch };
  const offers = useQuery({
    queryKey: adminCommercialKeys.offers.list(filters),
    queryFn: ({ signal }) =>
      adminOfferApi.list(
        {
          search: debouncedSearch || undefined,
          status: state.status === "ALL" ? undefined : (state.status as OfferStatus),
          discovery: state.discovery === "ALL" ? undefined : (state.discovery as OfferDiscovery),
          acceptance: state.acceptance === "ALL" ? undefined : (state.acceptance as OfferAcceptance),
          includeArchived: state.includeArchived,
          page: state.page,
          size: state.size,
          sort: state.sort,
          direction: state.direction,
        },
        { signal },
      ),
    enabled: commercialQueryEnabled(session.can, adminPermissions.offersList),
    placeholderData: keepPreviousData,
  });
  useEffect(() => {
    if (!offers.data || offers.isPlaceholderData) return;
    const bounded = offers.data.totalPages === 0 ? 0 : Math.min(state.page, offers.data.totalPages - 1);
    if (bounded !== state.page) update({ page: bounded });
  }, [offers.data, offers.isPlaceholderData, state.page, update]);
  const sorting: SortingState = offerSorting(state);

  return (
    <div className="space-y-7">
      <PageHeader
        actions={
          session.can(adminPermissions.offersCreate) ? (
            <Button asChild>
              <Link to="/admin/offers/new">
                <PlusIcon />
                Créer une offre
              </Link>
            </Button>
          ) : undefined
        }
        title="Offres"
      />
      {!session.can(adminPermissions.offersList) ? (
        <PermissionState />
      ) : (
        <section aria-busy={offers.isFetching} className="overflow-hidden rounded-xl border bg-card">
          <div className="grid gap-3 border-b p-4 sm:grid-cols-2 xl:grid-cols-[minmax(240px,1fr)_repeat(3,180px)]">
            <div className="relative">
              <MagnifyingGlassIcon className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                aria-label="Rechercher les offres"
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
                {Object.entries(offerStatus).map(([value, item]) => (
                  <SelectItem key={value} value={value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select
              onValueChange={(value) => update({ discovery: value as typeof state.discovery })}
              value={state.discovery}
            >
              <SelectTrigger aria-label="Mode de découverte">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">Tous les accès</SelectItem>
                {Object.entries(offerDiscovery).map(([value, label]) => (
                  <SelectItem key={value} value={value}>
                    {label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select
              onValueChange={(value) => update({ acceptance: value as typeof state.acceptance })}
              value={state.acceptance}
            >
              <SelectTrigger aria-label="Mode d’acceptation">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">Toutes les acceptations</SelectItem>
                {Object.entries(offerAcceptance).map(([value, label]) => (
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
                id="offer-archives"
                onCheckedChange={(checked) => update({ includeArchived: checked === true })}
              />
              <Label className="cursor-pointer font-normal" htmlFor="offer-archives">
                Inclure les archives
              </Label>
            </div>
            <div className="flex items-center gap-3">
              <Button
                aria-label="Actualiser les offres"
                disabled={offers.isFetching}
                onClick={() => void offers.refetch()}
                size="icon-sm"
                title="Actualiser les offres"
                variant="ghost"
              >
                <ArrowClockwiseIcon className={offers.isFetching ? "animate-spin" : undefined} />
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
              <span className="text-xs text-muted-foreground">{offers.data?.totalElements ?? 0} résultat(s)</span>
            </div>
          </div>
          {offers.isLoading ? (
            <div className="p-4">
              <LoadingState />
            </div>
          ) : offers.isError ? (
            <ErrorState retry={() => void offers.refetch()} />
          ) : offers.data ? (
            <>
              <div className="hidden md:block">
                <DataTable
                  columns={columns}
                  data={offers.data.content}
                  emptyState={
                    <EmptyState description="Modifiez les filtres ou créez un brouillon." title="Aucune offre" />
                  }
                  getRowId={(offer) => offer.id}
                  onSortingChange={(next) => update(offerListStateFromSorting(state, next) as Partial<typeof state>)}
                  sorting={sorting}
                />
              </div>
              <MobileOffers offers={offers.data.content} />
              <PaginationBar
                onPageChange={(page) => update({ page })}
                page={offers.data.page}
                totalElements={offers.data.totalElements}
                totalPages={offers.data.totalPages}
              />
            </>
          ) : null}
        </section>
      )}
    </div>
  );
}
