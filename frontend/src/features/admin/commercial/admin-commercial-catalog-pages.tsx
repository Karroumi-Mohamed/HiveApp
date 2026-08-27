import { ArrowRightIcon, GitBranchIcon, MagnifyingGlassIcon, PlusIcon } from "@phosphor-icons/react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import type { SortingState } from "@tanstack/react-table";
import { useDeferredValue, useEffect, useMemo } from "react";
import { Link, useSearchParams } from "react-router";
import { adminApi } from "@/api/admin-api";
import type {
  AddOnOperationalItem,
  PageResponse,
  PlanOperationalItem,
  QuotaPackageOperationalItem,
} from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable, SortHeader } from "@/components/patterns/data-table";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { RowAction } from "@/components/patterns/row-action";
import { TableActionsCell, tableActionsColumnMeta } from "@/components/patterns/table-actions-cell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { adminCommercialKeys } from "@/features/commercial/commercial-query";
import { AddOnForm, QuotaForm } from "./admin-commercial-pages";
import {
  listStateFromSorting,
  readCommercialListState,
  sortingFromListState,
  writeCommercialListState,
} from "./commercial-list-state";
import {
  availabilityLabel,
  BlockerSummary,
  capacityUnitLabel,
  extensionPolicyLabel,
  LifecycleText,
} from "./commercial-presentation";

type Kind = "plan" | "add-on" | "quota";
type Row = PlanOperationalItem | AddOnOperationalItem | QuotaPackageOperationalItem;

const configuration = {
  plan: {
    title: "Forfaits",
    singular: "forfait",
    path: "/admin/plans",
    permission: adminPermissions.plansList,
    createPermission: adminPermissions.plansCreate,
    defaultSort: "updatedAt",
  },
  "add-on": {
    title: "Add-ons",
    singular: "add-on",
    path: "/admin/add-ons",
    permission: adminPermissions.addOnsList,
    createPermission: adminPermissions.addOnsCreate,
    defaultSort: "name",
  },
  quota: {
    title: "Packs de capacité",
    singular: "pack",
    path: "/admin/quota-packages",
    permission: adminPermissions.quotaPackagesList,
    createPermission: adminPermissions.quotaPackagesCreate,
    defaultSort: "name",
  },
} as const;

function isPlan(row: Row): row is PlanOperationalItem {
  return "extensionPolicy" in row;
}
function isAddOn(row: Row): row is AddOnOperationalItem {
  return "featureCount" in row && !isPlan(row);
}
function productFacts(row: Row) {
  if (isPlan(row))
    return `${row.includedFeatureCount}/${row.featureCount} fonctionnalités · ${row.currentSubscriberCount} abonnements`;
  if (isAddOn(row)) return `${row.featureCount} fonctionnalités · ${row.targetPlanCount || "Tous les"} forfaits ciblés`;
  return `Capacité · ${capacityUnitLabel(row.resource)}`;
}

function targetFacts(row: Row) {
  if (isPlan(row)) return extensionPolicyLabel[row.extensionPolicy];
  if (isAddOn(row))
    return row.targetingMode === "TARGETED"
      ? `${row.targetPlanCount} forfaits ciblés`
      : "Tous les forfaits compatibles";
  return row.targetingMode === "TARGETED"
    ? `${row.targetPlanCount} forfaits · ${row.targetAddOnCount} add-ons`
    : "Toutes les offres compatibles";
}

function MobileCommercialRows({ rows, path }: { rows: Row[]; path: string }) {
  return (
    <div className="divide-y md:hidden">
      {rows.map((row) => (
        <article className="space-y-3 px-4 py-4" key={row.id}>
          <div className="flex items-start justify-between gap-4">
            <div className="min-w-0">
              <p className="truncate font-medium">{row.name}</p>
              <p className="mt-1 text-xs text-muted-foreground">
                R{row.revisionNumber} · {productFacts(row)}
              </p>
            </div>
            <LifecycleText status={row.status} />
          </div>
          <div className="flex items-end justify-between gap-4">
            <div className="space-y-1">
              <p className="text-xs text-muted-foreground">
                {availabilityLabel[row.salesVisibility]} · {targetFacts(row)}
              </p>
              <BlockerSummary blockers={row.blockers} />
            </div>
            <Button asChild size="icon-sm" variant="ghost">
              <Link aria-label={`Ouvrir ${row.name}`} to={`${path}/${row.id}`}>
                <ArrowRightIcon className="rtl:rotate-180" />
              </Link>
            </Button>
          </div>
        </article>
      ))}
    </div>
  );
}

function CommercialCatalogPage({ kind }: { kind: Kind }) {
  const config = configuration[kind];
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const state = readCommercialListState(params, config.defaultSort);
  const deferredSearch = useDeferredValue(state.search);
  const filters = useMemo(
    () => ({
      search: deferredSearch || undefined,
      status: state.status === "all" ? undefined : state.status,
      salesVisibility: state.visibility === "all" ? undefined : (state.visibility as "PUBLIC" | "DIRECT_ONLY"),
      page: state.page,
      size: state.size,
      sort: state.sort,
      direction: state.direction,
    }),
    [deferredSearch, state.direction, state.page, state.size, state.sort, state.status, state.visibility],
  );
  const query = useQuery<PageResponse<Row>>({
    queryKey:
      kind === "plan"
        ? adminCommercialKeys.plans.list(filters)
        : kind === "add-on"
          ? adminCommercialKeys.addOns.list(filters)
          : adminCommercialKeys.quotaPackages.list(filters),
    queryFn: () =>
      (kind === "plan"
        ? adminApi.operationalPlans(filters)
        : kind === "add-on"
          ? adminApi.operationalAddOns(filters)
          : adminApi.operationalQuotaPackages(filters)) as Promise<PageResponse<Row>>,
    enabled: session.can(config.permission),
    placeholderData: keepPreviousData,
  });
  const data = query.data;
  useEffect(() => {
    if (data && data.totalPages > 0 && state.page >= data.totalPages) {
      setParams(writeCommercialListState(params, { ...state, page: Math.max(data.totalPages - 1, 0) }), {
        replace: true,
      });
    }
  }, [data, params, setParams, state]);

  const setState = (next: typeof state) => setParams(writeCommercialListState(params, next), { replace: true });
  const column = useMemo(() => createDataColumns<Row>(), []);
  const columns = useMemo(
    () =>
      column.columns([
        column.accessor("name", {
          meta: { headerClassName: "min-w-[220px]" },
          header: ({ column: item }) => <SortHeader column={item}>Produit</SortHeader>,
          cell: ({ row }) => (
            <span>
              <span className="block font-medium">{row.original.name}</span>
              <span className="mt-0.5 block text-xs text-muted-foreground">Révision {row.original.revisionNumber}</span>
            </span>
          ),
        }),
        column.accessor("status", {
          meta: { headerClassName: "w-28", cellClassName: "w-28" },
          header: ({ column: item }) => <SortHeader column={item}>Cycle de vie</SortHeader>,
          cell: ({ row }) => <LifecycleText status={row.original.status} />,
        }),
        column.accessor("salesVisibility", {
          meta: { headerClassName: "min-w-[160px]" },
          header: ({ column: item }) => <SortHeader column={item}>Vente</SortHeader>,
          cell: ({ row }) => <span className="text-sm">{availabilityLabel[row.original.salesVisibility]}</span>,
        }),
        column.display({
          id: "composition",
          header: "Configuration",
          cell: ({ row }) => <span className="text-sm text-muted-foreground">{productFacts(row.original)}</span>,
        }),
        column.display({
          id: "target",
          header: "Portée",
          cell: ({ row }) => <span className="text-sm text-muted-foreground">{targetFacts(row.original)}</span>,
        }),
        column.display({
          id: "readiness",
          header: "État commercial",
          cell: ({ row }) => <BlockerSummary blockers={row.original.blockers} />,
        }),
        column.display({
          id: "actions",
          meta: tableActionsColumnMeta(2),
          header: "Actions",
          cell: ({ row }) => (
            <TableActionsCell label={`Actions pour ${row.original.name}`}>
              <RowAction
                disabled={!row.original.availableActions.includes("REVISE")}
                disabledLabel="La révision n’est pas disponible dans cet état"
                icon={<GitBranchIcon />}
                label="Ouvrir pour réviser"
                to={row.original.availableActions.includes("REVISE") ? `${config.path}/${row.original.id}` : undefined}
              />
              <RowAction
                icon={<ArrowRightIcon className="rtl:rotate-180" />}
                label="Ouvrir"
                to={`${config.path}/${row.original.id}`}
              />
            </TableActionsCell>
          ),
        }),
      ]),
    [column, config.path],
  );
  const sorting = sortingFromListState(state);

  if (!session.can(config.permission)) return <PermissionState />;
  const createAction = session.can(config.createPermission) ? (
    kind === "plan" ? (
      <Button asChild>
        <Link to="/admin/plans/new">
          <PlusIcon />
          Créer un forfait
        </Link>
      </Button>
    ) : kind === "add-on" ? (
      <AddOnForm
        trigger={
          <Button>
            <PlusIcon />
            Créer un add-on
          </Button>
        }
      />
    ) : (
      <QuotaForm
        trigger={
          <Button>
            <PlusIcon />
            Créer un pack
          </Button>
        }
      />
    )
  ) : undefined;
  return (
    <div className="space-y-7">
      <PageHeader actions={createAction} title={config.title} />
      <section className="overflow-hidden rounded-xl border bg-card">
        <div className="grid gap-3 border-b p-4 md:grid-cols-[minmax(220px,1fr)_180px_190px]">
          <div className="relative">
            <MagnifyingGlassIcon
              aria-hidden="true"
              className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
            />
            <Input
              aria-label={`Rechercher un ${config.singular}`}
              className="ps-9"
              onChange={(event) => setState({ ...state, search: event.target.value, page: 0 })}
              placeholder="Nom du produit…"
              value={state.search}
            />
          </div>
          <Select onValueChange={(status) => setState({ ...state, status, page: 0 })} value={state.status}>
            <SelectTrigger aria-label="Cycle de vie">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Tous les états</SelectItem>
              <SelectItem value="DRAFT">Brouillons</SelectItem>
              <SelectItem value="ACTIVE">Actifs</SelectItem>
              <SelectItem value="INACTIVE">Suspendus</SelectItem>
              <SelectItem value="ARCHIVED">Archivés</SelectItem>
            </SelectContent>
          </Select>
          <Select onValueChange={(visibility) => setState({ ...state, visibility, page: 0 })} value={state.visibility}>
            <SelectTrigger aria-label="Visibilité commerciale">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Toutes les disponibilités</SelectItem>
              <SelectItem value="PUBLIC">Catalogue public</SelectItem>
              <SelectItem value="DIRECT_ONLY">Attribution directe</SelectItem>
            </SelectContent>
          </Select>
        </div>
        {query.isLoading ? (
          <div className="p-5">
            <LoadingState />
          </div>
        ) : query.isError ? (
          <ErrorState retry={() => void query.refetch()} />
        ) : (
          <>
            <div className="hidden md:block">
              <DataTable
                columns={columns}
                data={data?.content ?? []}
                emptyState={
                  <EmptyState
                    title={`Aucun ${config.singular}`}
                    description="Modifiez les filtres ou créez une nouvelle révision."
                  />
                }
                getRowId={(row) => row.id}
                onSortingChange={(next: SortingState) => setState(listStateFromSorting(state, next))}
                sorting={sorting}
              />
            </div>
            <MobileCommercialRows path={config.path} rows={data?.content ?? []} />
            <PaginationBar
              onPageChange={(page) => setState({ ...state, page })}
              page={data?.page ?? 0}
              totalElements={data?.totalElements ?? 0}
              totalPages={data?.totalPages ?? 0}
            />
          </>
        )}
      </section>
    </div>
  );
}

export function AdminOperationalPlansPage() {
  return <CommercialCatalogPage kind="plan" />;
}
export function AdminOperationalAddOnsPage() {
  return <CommercialCatalogPage kind="add-on" />;
}
export function AdminOperationalQuotaPackagesPage() {
  return <CommercialCatalogPage kind="quota" />;
}
