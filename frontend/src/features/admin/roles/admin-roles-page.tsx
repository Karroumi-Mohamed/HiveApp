import { ArrowRightIcon, CopyIcon, MagnifyingGlassIcon, PlusIcon } from "@phosphor-icons/react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import type { SortingState } from "@tanstack/react-table";
import { useState } from "react";
import { adminApi } from "@/api/admin-api";
import type { AdminRole, AdminRoleStatus } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable, SortHeader } from "@/components/patterns/data-table";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { RowAction } from "@/components/patterns/row-action";
import { StatusBadge, type StatusTone } from "@/components/patterns/status-badge";
import { TableActionsCell, tableActionsColumnMeta } from "@/components/patterns/table-actions-cell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { AdminRoleCreateDialog } from "@/features/admin/roles/admin-role-create-dialog";
import { AdminRoleDuplicateDialog } from "@/features/admin/roles/admin-role-dialogs";
import { useDebouncedValue } from "@/lib/use-debounced-value";

const PAGE_SIZE = 20;
const column = createDataColumns<AdminRole>();

const statusPresentation: Record<AdminRoleStatus, { label: string; tone: StatusTone }> = {
  ACTIVE: { label: "Actif", tone: "success" },
  INACTIVE: { label: "Inactif", tone: "warning" },
  ARCHIVED: { label: "Archivé", tone: "neutral" },
};

function formatDate(value: string) {
  return new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium" }).format(new Date(value));
}

/**
 * Same contract as the operators table: every action is always rendered as an icon with a
 * tooltip, and an unavailable one is disabled with the reason — never hidden. Navigation lives
 * in the end-of-row arrow, not in a data cell.
 */
function RowActions({ role }: { role: AdminRole }) {
  const canDuplicate = role.availableActions.includes("DUPLICATE");
  const canOpen = role.availableActions.includes("READ_DETAIL");
  const [duplicateOpen, setDuplicateOpen] = useState(false);

  return (
    <TableActionsCell label={`Actions pour ${role.name}`}>
      <RowAction
        disabled={!canDuplicate}
        disabledLabel={"Vous n’êtes pas autorisé à dupliquer ce rôle"}
        icon={<CopyIcon />}
        label="Dupliquer le rôle"
        onClick={() => setDuplicateOpen(true)}
      />
      <RowAction
        disabled={!canOpen}
        disabledLabel={"Vous n’êtes pas autorisé à consulter ce rôle"}
        icon={<ArrowRightIcon />}
        label="Ouvrir la fiche"
        to={canOpen ? `/admin/roles/${role.id}` : undefined}
      />
      <AdminRoleDuplicateDialog onOpenChange={setDuplicateOpen} open={duplicateOpen} role={role} />
    </TableActionsCell>
  );
}

const roleColumns = column.columns([
  column.accessor("name", {
    meta: { headerClassName: "min-w-[260px]" },
    header: ({ column: current }) => <SortHeader column={current}>Rôle</SortHeader>,
    // Plain text like the operator identity cell: navigation belongs to the end-of-row arrow,
    // not to a link buried in a data cell.
    cell: ({ row }) => (
      <span className="block min-w-0">
        <span className="block truncate font-medium">{row.original.name}</span>
        {row.original.description ? (
          <span className="block max-w-xl truncate text-xs text-muted-foreground">{row.original.description}</span>
        ) : null}
      </span>
    ),
  }),
  column.accessor("status", {
    header: ({ column: current }) => <SortHeader column={current}>Statut</SortHeader>,
    cell: ({ row }) => {
      const presentation = statusPresentation[row.original.status];
      return <StatusBadge tone={presentation.tone}>{presentation.label}</StatusBadge>;
    },
  }),
  column.display({
    id: "permissions",
    header: "Permissions",
    cell: ({ row }) => <span className="tabular-nums">{row.original.permissions.length}</span>,
  }),
  column.accessor("assignedOperatorCount", {
    header: ({ column: current }) => <SortHeader column={current}>Opérateurs</SortHeader>,
    cell: ({ row }) => <span className="tabular-nums">{row.original.assignedOperatorCount}</span>,
  }),
  column.accessor("updatedAt", {
    header: ({ column: current }) => <SortHeader column={current}>Modifié</SortHeader>,
    cell: ({ row }) => <span className="text-sm text-muted-foreground">{formatDate(row.original.updatedAt)}</span>,
  }),
  column.display({
    id: "actions",
    meta: tableActionsColumnMeta(2),
    header: "Actions",
    cell: ({ row }) => <RowActions role={row.original} />,
  }),
]);

function MobileRoleList({ roles }: { roles: AdminRole[] }) {
  if (roles.length === 0) {
    return (
      <div className="py-8 md:hidden">
        <EmptyState description="Modifiez les filtres ou créez un rôle." title="Aucun rôle" />
      </div>
    );
  }
  return (
    <div className="divide-y md:hidden">
      {roles.map((role) => {
        const presentation = statusPresentation[role.status];
        return (
          <article className="space-y-3 p-4" key={role.id}>
            <div className="flex items-start gap-3">
              <div className="min-w-0 flex-1">
                <span className="font-medium">{role.name}</span>
                {role.description ? (
                  <p className="mt-0.5 line-clamp-2 text-xs text-muted-foreground">{role.description}</p>
                ) : null}
              </div>
              <StatusBadge tone={presentation.tone}>{presentation.label}</StatusBadge>
              <RowActions role={role} />
            </div>
            <dl className="grid grid-cols-3 gap-3 text-xs">
              <div>
                <dt className="text-muted-foreground">Permissions</dt>
                <dd className="mt-0.5 font-medium tabular-nums">{role.permissions.length}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Opérateurs</dt>
                <dd className="mt-0.5 font-medium tabular-nums">{role.assignedOperatorCount}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Modifié</dt>
                <dd className="mt-0.5 font-medium">{formatDate(role.updatedAt)}</dd>
              </div>
            </dl>
          </article>
        );
      })}
    </div>
  );
}

export function AdminRolesPage() {
  const session = useAdminSession();
  const [search, setSearch] = useState("");
  const debouncedSearch = useDebouncedValue(search);
  const [status, setStatus] = useState<AdminRoleStatus | "ALL">("ALL");
  const [page, setPage] = useState(0);
  const [sorting, setSorting] = useState<SortingState>([]);

  const roles = useQuery({
    queryKey: ["admin", "roles", debouncedSearch, status, page, sorting],
    queryFn: () =>
      adminApi.roles({
        search: debouncedSearch || undefined,
        status: status === "ALL" ? undefined : status,
        page,
        size: PAGE_SIZE,
        sort: sorting[0]?.id,
        direction: sorting[0] ? (sorting[0].desc ? "desc" : "asc") : undefined,
      }),
    placeholderData: keepPreviousData,
  });

  return (
    <div className="space-y-6">
      <PageHeader
        actions={
          session.can(adminPermissions.rolesCreate) || session.can(adminPermissions.rolesCreateFromPreset) ? (
            <AdminRoleCreateDialog
              trigger={
                <Button>
                  <PlusIcon /> Créer un rôle
                </Button>
              }
            />
          ) : undefined
        }
        title="Rôles"
      />

      <section className="overflow-hidden rounded-lg border bg-card">
        <div className="flex flex-col gap-3 border-b p-4 sm:flex-row">
          <div className="relative flex-1 sm:max-w-sm">
            <MagnifyingGlassIcon className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              aria-label="Rechercher les rôles"
              className="ps-9"
              onChange={(event) => {
                setSearch(event.target.value);
                setPage(0);
              }}
              placeholder="Nom ou description…"
              value={search}
            />
          </div>
          <Select
            onValueChange={(value: AdminRoleStatus | "ALL") => {
              setStatus(value);
              setPage(0);
            }}
            value={status}
          >
            <SelectTrigger aria-label="Statut du rôle" className="w-full sm:w-44">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">Tous les statuts</SelectItem>
              <SelectItem value="ACTIVE">Actifs</SelectItem>
              <SelectItem value="INACTIVE">Inactifs</SelectItem>
              <SelectItem value="ARCHIVED">Archivés</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {roles.isLoading ? (
          <div className="p-5">
            <LoadingState />
          </div>
        ) : roles.isError ? (
          <ErrorState retry={() => void roles.refetch()} />
        ) : (
          <>
            <MobileRoleList roles={roles.data?.content ?? []} />
            <div className="hidden md:block">
              <DataTable
                columns={roleColumns}
                data={roles.data?.content ?? []}
                emptyState={<EmptyState description="Modifiez les filtres ou créez un rôle." title="Aucun rôle" />}
                getRowId={(role) => role.id}
                isLoading={roles.isLoading}
                onSortingChange={setSorting}
                sorting={sorting}
              />
            </div>
          </>
        )}

        {roles.data ? (
          <PaginationBar
            onPageChange={setPage}
            page={roles.data.page}
            totalElements={roles.data.totalElements}
            totalPages={roles.data.totalPages}
          />
        ) : null}
      </section>
    </div>
  );
}
