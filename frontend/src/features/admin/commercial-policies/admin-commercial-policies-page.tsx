import { ArrowRightIcon, MagnifyingGlassIcon, PlusIcon, WarningCircleIcon } from "@phosphor-icons/react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import type { SortingState } from "@tanstack/react-table";
import { useCallback, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { adminApi } from "@/api/admin-api";
import type {
  CommercialPolicySource,
  CommercialPolicyStatus,
  CommercialPolicySummary,
  CommercialPolicyTargetKind,
} from "@/api/contracts";
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
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { adminCommercialKeys, commercialQueryEnabled } from "@/features/commercial/commercial-query";
import { useDebouncedValue } from "@/lib/use-debounced-value";
import {
  commercialPolicySorting,
  effectiveAtInstant,
  policyListStateFromSorting,
  readCommercialPolicyListState,
  writeCommercialPolicyListState,
} from "./commercial-policy-list-state";
import { policySource, policyStatus, policyTarget } from "./commercial-policy-rules";

const column = createDataColumns<CommercialPolicySummary>();

function date(value: string | null) {
  return value ? new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium" }).format(new Date(value)) : "sans fin";
}

function targetLabel(policy: CommercialPolicySummary) {
  if (policy.targetKind === "ACCOUNT_SET") return `${policy.configuredTargetCount} compte(s)`;
  if (policy.targetKind === "SEGMENT") return policy.targetLabel || "Segment non configuré";
  return policy.targetLabel || policyTarget[policy.targetKind];
}

function PolicyRowActions({ policy }: { policy: CommercialPolicySummary }) {
  const session = useAdminSession();
  const destination = session.can(adminPermissions.commercialPoliciesRead)
    ? `/admin/commercial-policies/${policy.id}`
    : session.can(adminPermissions.commercialPoliciesPreviewAudience)
      ? `/admin/commercial-policies/${policy.id}/audience`
      : session.can(adminPermissions.commercialPoliciesReadRevisions) ||
          session.can(adminPermissions.commercialPoliciesCompare)
        ? `/admin/commercial-policies/${policy.id}/revisions`
        : session.can(adminPermissions.commercialPoliciesReadActivations) ||
            session.can(adminPermissions.commercialPoliciesReadActivationAccounts)
          ? `/admin/commercial-policies/${policy.id}/activations`
          : session.can(adminPermissions.commercialPoliciesReadHistory)
            ? `/admin/commercial-policies/${policy.id}/history`
            : session.can(adminPermissions.commercialPoliciesReadOwner)
              ? `/admin/commercial-policies/${policy.id}/owner`
              : undefined;
  return (
    <TableActionsCell label={`Actions pour ${policy.name}`}>
      <RowAction
        disabled={!destination}
        disabledLabel="Votre rôle ne permet pas d’ouvrir une surface de cette définition"
        icon={<ArrowRightIcon className="rtl:rotate-180" />}
        label="Ouvrir la politique"
        to={destination}
      />
    </TableActionsCell>
  );
}

const columns = column.columns([
  column.accessor("name", {
    meta: { headerClassName: "min-w-[250px]" },
    header: ({ column: current }) => <SortHeader column={current}>Politique</SortHeader>,
    cell: ({ row }) => (
      <span className="block min-w-0">
        <span className="block truncate font-medium">{row.original.name}</span>
        <span className="block truncate text-xs text-muted-foreground">
          {row.original.code} · R{row.original.revisionNumber}
        </span>
      </span>
    ),
  }),
  column.accessor("targetKind", {
    header: ({ column: current }) => <SortHeader column={current}>Audience</SortHeader>,
    cell: ({ row }) => (
      <span className="block min-w-40">
        <span className="block text-sm">{targetLabel(row.original)}</span>
        <span className="block text-xs text-muted-foreground">{policyTarget[row.original.targetKind]}</span>
      </span>
    ),
  }),
  column.accessor("effectCount", {
    header: "Effets",
    cell: ({ row }) => (
      <span className="tabular-nums">
        {row.original.effectCount}
        {row.original.executionSupported ? null : (
          <span className="mt-0.5 block text-xs text-warning">Exécution différée</span>
        )}
      </span>
    ),
  }),
  column.accessor("effectiveFrom", {
    header: ({ column: current }) => <SortHeader column={current}>Période</SortHeader>,
    cell: ({ row }) => (
      <span className="text-sm">
        <span className="block">{date(row.original.effectiveFrom)}</span>
        <span className="block text-xs text-muted-foreground">jusqu’au {date(row.original.effectiveUntil)}</span>
      </span>
    ),
  }),
  column.accessor("priority", {
    header: ({ column: current }) => <SortHeader column={current}>Origine</SortHeader>,
    cell: ({ row }) => (
      <span className="text-sm">
        <span className="block">{policySource[row.original.source]}</span>
        <span className="block text-xs text-muted-foreground tabular-nums">Priorité {row.original.priority}</span>
      </span>
    ),
  }),
  column.accessor("status", {
    header: ({ column: current }) => <SortHeader column={current}>Statut</SortHeader>,
    cell: ({ row }) => {
      const status = policyStatus[row.original.status];
      return (
        <span className="flex items-center gap-2">
          <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
          {row.original.blockers.length ? (
            <WarningCircleIcon
              aria-label={`${row.original.blockers.length} blocage(s)`}
              className="size-4 text-warning"
            />
          ) : null}
        </span>
      );
    },
  }),
  column.display({
    id: "actions",
    meta: tableActionsColumnMeta(1),
    header: "Actions",
    cell: ({ row }) => <PolicyRowActions policy={row.original} />,
  }),
]);

function MobilePolicies({ policies }: { policies: CommercialPolicySummary[] }) {
  if (!policies.length)
    return (
      <div className="md:hidden">
        <EmptyState title="Aucune politique" />
      </div>
    );
  return (
    <div className="divide-y md:hidden">
      {policies.map((policy) => {
        const status = policyStatus[policy.status];
        return (
          <article className="space-y-3 p-4" key={policy.id}>
            <div className="flex items-start gap-3">
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium">{policy.name}</p>
                <p className="mt-0.5 truncate text-xs text-muted-foreground">{targetLabel(policy)}</p>
              </div>
              <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
              <PolicyRowActions policy={policy} />
            </div>
            <dl className="grid grid-cols-2 gap-3 text-xs">
              <div>
                <dt className="text-muted-foreground">Effets</dt>
                <dd className="mt-0.5 font-medium tabular-nums">{policy.effectCount}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Origine</dt>
                <dd className="mt-0.5 font-medium">{policySource[policy.source]}</dd>
              </div>
            </dl>
          </article>
        );
      })}
    </div>
  );
}

export function AdminCommercialPoliciesPage() {
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const state = readCommercialPolicyListState(params);
  const [search, setSearch] = useState(state.search);
  const debouncedSearch = useDebouncedValue(search);
  useEffect(() => setSearch(state.search), [state.search]);
  useEffect(() => {
    if (debouncedSearch === state.search) return;
    setParams(writeCommercialPolicyListState(params, { ...state, search: debouncedSearch, page: 0 }), {
      replace: true,
    });
  }, [debouncedSearch, params, setParams, state]);
  const update = useCallback(
    (changes: Partial<typeof state>) =>
      setParams(
        writeCommercialPolicyListState(params, {
          ...state,
          ...changes,
          page: "page" in changes ? (changes.page ?? 0) : 0,
        }),
        { replace: true },
      ),
    [params, setParams, state],
  );
  const filters = { ...state, search: debouncedSearch };
  const policies = useQuery({
    queryKey: adminCommercialKeys.policies.list(filters),
    queryFn: () =>
      adminApi.commercialPolicies({
        search: debouncedSearch || undefined,
        status: state.status === "ALL" ? undefined : (state.status as CommercialPolicyStatus),
        targetKind: state.targetKind === "ALL" ? undefined : (state.targetKind as CommercialPolicyTargetKind),
        source: state.source === "ALL" ? undefined : (state.source as CommercialPolicySource),
        effectiveAt: effectiveAtInstant(state.effectiveAt),
        includeArchived: state.includeArchived,
        page: state.page,
        size: state.size,
        sort: state.sort,
        direction: state.direction,
      }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.commercialPoliciesList),
    placeholderData: keepPreviousData,
  });
  useEffect(() => {
    if (!policies.data || policies.isPlaceholderData) return;
    const bounded = policies.data.totalPages === 0 ? 0 : Math.min(state.page, policies.data.totalPages - 1);
    if (bounded !== state.page) update({ page: bounded });
  }, [policies.data, policies.isPlaceholderData, state.page, update]);
  const sorting: SortingState = commercialPolicySorting(state);
  return (
    <div className="space-y-7">
      <PageHeader
        actions={
          session.can(adminPermissions.commercialPoliciesCreate) ? (
            <Button asChild>
              <Link to="/admin/commercial-policies/new">
                <PlusIcon />
                Créer une politique
              </Link>
            </Button>
          ) : undefined
        }
        title="Politiques commerciales"
      />
      <section className="overflow-hidden rounded-xl border bg-card">
        <div className="grid gap-3 border-b p-4 sm:grid-cols-2 xl:grid-cols-[minmax(220px,1fr)_repeat(4,175px)]">
          <div className="relative">
            <MagnifyingGlassIcon className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              aria-label="Rechercher les politiques"
              className="ps-9"
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Nom ou code…"
              value={search}
            />
          </div>
          <Select onValueChange={(value) => update({ status: value as typeof state.status })} value={state.status}>
            <SelectTrigger className="w-full" aria-label="Statut">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">Tous les statuts</SelectItem>
              {Object.entries(policyStatus).map(([value, status]) => (
                <SelectItem key={value} value={value}>
                  {status.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select
            onValueChange={(value) => update({ targetKind: value as typeof state.targetKind })}
            value={state.targetKind}
          >
            <SelectTrigger className="w-full" aria-label="Type de cible">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">Toutes les audiences</SelectItem>
              {Object.entries(policyTarget).map(([value, label]) => (
                <SelectItem key={value} value={value}>
                  {label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select onValueChange={(value) => update({ source: value as typeof state.source })} value={state.source}>
            <SelectTrigger className="w-full" aria-label="Origine">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">Toutes les origines</SelectItem>
              {Object.entries(policySource).map(([value, label]) => (
                <SelectItem key={value} value={value}>
                  {label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Input
            aria-label="Effective à la date"
            onChange={(event) => update({ effectiveAt: event.target.value })}
            type="date"
            value={state.effectiveAt}
          />
        </div>
        <div className="flex min-h-12 items-center justify-between gap-4 border-b px-4 py-2">
          <div className="flex items-center gap-2">
            <Checkbox
              checked={state.includeArchived}
              id="commercial-policy-archives"
              onCheckedChange={(checked) => update({ includeArchived: checked === true })}
            />
            <Label className="cursor-pointer font-normal" htmlFor="commercial-policy-archives">
              Inclure les archives
            </Label>
          </div>
          <div className="flex items-center gap-3">
            <Select
              onValueChange={(value) => update({ size: Number(value) as typeof state.size })}
              value={String(state.size)}
            >
              <SelectTrigger className="h-8 w-[118px]" aria-label="Résultats par page">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="10">10 par page</SelectItem>
                <SelectItem value="20">20 par page</SelectItem>
                <SelectItem value="50">50 par page</SelectItem>
              </SelectContent>
            </Select>
            <span className="text-xs text-muted-foreground">{policies.data?.totalElements ?? 0} résultat(s)</span>
          </div>
        </div>
        {policies.isLoading ? (
          <div className="p-4">
            <LoadingState />
          </div>
        ) : policies.isError ? (
          <ErrorState retry={() => void policies.refetch()} />
        ) : policies.data ? (
          <>
            <div className="hidden md:block">
              <DataTable
                columns={columns}
                data={policies.data.content}
                emptyState={
                  <EmptyState description="Modifiez les filtres ou créez un brouillon." title="Aucune politique" />
                }
                getRowId={(policy) => policy.id}
                onSortingChange={(next) => update(policyListStateFromSorting(state, next) as Partial<typeof state>)}
                sorting={sorting}
              />
            </div>
            <MobilePolicies policies={policies.data.content} />
            <PaginationBar
              onPageChange={(page) => update({ page })}
              page={policies.data.page}
              totalElements={policies.data.totalElements}
              totalPages={policies.data.totalPages}
            />
          </>
        ) : null}
      </section>
    </div>
  );
}
