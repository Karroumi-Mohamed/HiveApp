import {
  ArrowRightIcon,
  CheckCircleIcon,
  MagnifyingGlassIcon,
  PlusIcon,
  ProhibitIcon,
  ShieldCheckIcon,
} from "@phosphor-icons/react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { SortingState } from "@tanstack/react-table";
import { type FormEvent, useState } from "react";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { AdminRole, BulkOperationResult } from "@/api/contracts";
import { ApiError } from "@/api/http";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { BulkActionBar } from "@/components/patterns/bulk-action-bar";
import { createDataColumns, DataTable, SortHeader } from "@/components/patterns/data-table";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { RowAction } from "@/components/patterns/row-action";
import { StatusBadge } from "@/components/patterns/status-badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useDebouncedValue } from "@/lib/use-debounced-value";
import { usePageSelection } from "@/lib/use-page-selection";

export function RoleFormDialog({ trigger, role }: { trigger: React.ReactNode; role?: AdminRole }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState(role?.name ?? "");
  const [description, setDescription] = useState(role?.description ?? "");
  const save = useMutation({
    mutationFn: () =>
      role ? adminApi.updateRole(role.id, { name, description }) : adminApi.createRole({ name, description }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "roles"] });
      toast.success(role ? "Rôle mis à jour" : "Rôle créé");
      setOpen(false);
    },
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate();
  };
  if (!session.can(role ? adminPermissions.rolesUpdate : adminPermissions.rolesCreate)) return null;
  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{role ? "Modifier le rôle" : "Créer un rôle"}</DialogTitle>
          <DialogDescription>Le rôle regroupe des permissions déclarées par la plateforme.</DialogDescription>
        </DialogHeader>
        <form className="space-y-5" onSubmit={submit}>
          <div className="space-y-2">
            <Label htmlFor="role-name">Nom</Label>
            <Input id="role-name" onChange={(event) => setName(event.target.value)} required value={name} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="role-description">Description</Label>
            <Textarea
              id="role-description"
              onChange={(event) => setDescription(event.target.value)}
              value={description}
            />
          </div>
          <div className="flex justify-end gap-2">
            <Button onClick={() => setOpen(false)} type="button" variant="outline">
              Annuler
            </Button>
            <Button disabled={save.isPending} type="submit">
              {save.isPending ? "Enregistrement…" : "Enregistrer"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

const PAGE_SIZE = 20;
const roleId = (role: AdminRole) => role.id;
const column = createDataColumns<AdminRole>();

/** Each column owns its header, cell, and width. */
const roleColumns = column.columns([
  column.display({
    id: "select",
    meta: { headerClassName: "w-10", cellClassName: "w-10" },
    header: ({ table }) => (
      <Checkbox
        aria-label="Sélectionner les rôles de cette page"
        checked={table.getIsAllRowsSelected() || (table.getIsSomeRowsSelected() && "indeterminate")}
        onCheckedChange={() => table.toggleAllRowsSelected()}
      />
    ),
    cell: ({ row }) => (
      <Checkbox
        aria-label={`Sélectionner ${row.original.name}`}
        checked={row.getIsSelected()}
        onCheckedChange={() => row.toggleSelected()}
      />
    ),
  }),
  column.accessor("name", {
    meta: { headerClassName: "min-w-[240px]" },
    header: ({ column: col }) => <SortHeader column={col}>Rôle</SortHeader>,
    cell: ({ row }) => (
      <span className="flex items-center gap-3">
        <ShieldCheckIcon className="size-5 shrink-0 text-muted-foreground" />
        <span className="min-w-0">
          <span className="block truncate font-medium">{row.original.name}</span>
          {row.original.description ? (
            <span className="block max-w-md truncate text-xs text-muted-foreground">{row.original.description}</span>
          ) : null}
        </span>
      </span>
    ),
  }),
  column.display({
    id: "permissions",
    header: "Permissions",
    cell: ({ row }) => <span className="tabular-nums">{row.original.permissions.length}</span>,
  }),
  column.display({
    id: "holders",
    header: "Opérateurs",
    // What a deactivation would affect. Previously invisible, so the consequence of switching a
    // role off could not be judged from this screen at all.
    cell: ({ row }) => (
      <span className="tabular-nums">
        {row.original.assignedOperatorCount > 0 ? (
          row.original.assignedOperatorCount
        ) : (
          <span className="text-muted-foreground">—</span>
        )}
      </span>
    ),
  }),
  column.accessor("isActive", {
    id: "active",
    header: ({ column: col }) => <SortHeader column={col}>Statut</SortHeader>,
    cell: ({ row }) => (
      <StatusBadge tone={row.original.isActive ? "success" : "danger"}>
        {row.original.isActive ? "Actif" : "Inactif"}
      </StatusBadge>
    ),
  }),
  column.display({
    id: "actions",
    meta: { headerClassName: "w-32", cellClassName: "w-32" },
    header: "Actions",
    cell: ({ row }) => <RowActions role={row.original} />,
  }),
]);

/**
 * Per-row actions. Always rendered: an unavailable action is disabled and its tooltip says why,
 * so a missing permission reads as "not yours" rather than as an absent feature.
 */
function RowActions({ role }: { role: AdminRole }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const toggle = useMutation({
    mutationFn: () => adminApi.toggleRole(role.id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "roles"] });
      void queryClient.invalidateQueries({ queryKey: ["admin", "overview"] });
      toast.success("Statut mis à jour");
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : "Action impossible"),
  });

  const toggleBlockedBy = !session.can(adminPermissions.rolesMutate)
    ? "Vous n’êtes pas autorisé à modifier ce statut"
    : toggle.isPending
      ? "Action en cours…"
      : null;

  return (
    <span className="flex items-center gap-0.5">
      <RowAction
        disabled={toggleBlockedBy !== null}
        disabledLabel={toggleBlockedBy ?? undefined}
        icon={role.isActive ? <ProhibitIcon /> : <CheckCircleIcon />}
        label={role.isActive ? "Désactiver le rôle" : "Activer le rôle"}
        onClick={() => toggle.mutate()}
        tone={role.isActive ? "danger" : "default"}
      />
      <RowAction icon={<ArrowRightIcon />} label="Ouvrir la fiche" to={`/admin/roles/${role.id}`} />
    </span>
  );
}

export function AdminRolesPage() {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const debouncedSearch = useDebouncedValue(search);
  const [active, setActive] = useState("all");
  const [page, setPage] = useState(0);
  const [sorting, setSorting] = useState<SortingState>([]);

  const roles = useQuery({
    queryKey: ["admin", "roles", debouncedSearch, active, page, sorting],
    queryFn: () =>
      adminApi.roles({
        search: debouncedSearch || undefined,
        active: active === "all" ? undefined : active === "active",
        page,
        size: PAGE_SIZE,
        sort: sorting[0]?.id,
        direction: sorting[0] ? (sorting[0].desc ? "desc" : "asc") : undefined,
      }),
    placeholderData: keepPreviousData,
  });

  const rows = roles.data?.content ?? [];
  const { rowSelection, setRowSelection, selectedIds, clearSelection } = usePageSelection(rows, roleId, [
    debouncedSearch,
    active,
    page,
    sorting,
  ]);

  const bulkActive = useMutation({
    mutationFn: (next: boolean) => adminApi.bulkSetRolesActive(selectedIds, next),
    onSuccess: (result: BulkOperationResult) => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "roles"] });
      void queryClient.invalidateQueries({ queryKey: ["admin", "overview"] });
      clearSelection();
      if (result.failures.length === 0) {
        toast.success(`Statut mis à jour : ${result.succeeded} rôle(s)`);
        return;
      }
      toast.warning(`Statut mis à jour : ${result.succeeded} réussi(s), ${result.failures.length} refusé(s)`, {
        description: result.failures[0]?.message,
      });
    },
  });

  return (
    <div className="space-y-7">
      <PageHeader
        actions={
          session.can(adminPermissions.rolesCreate) ? (
            <RoleFormDialog
              trigger={
                <Button>
                  <PlusIcon />
                  Créer un rôle
                </Button>
              }
            />
          ) : undefined
        }
        title="Rôles administrateur"
      />
      <section className="overflow-hidden rounded-xl border bg-card">
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
            onValueChange={(value) => {
              setActive(value);
              setPage(0);
            }}
            value={active}
          >
            <SelectTrigger aria-label="Statut du rôle" className="w-full sm:w-44">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Tous les statuts</SelectItem>
              <SelectItem value="active">Actifs</SelectItem>
              <SelectItem value="inactive">Inactifs</SelectItem>
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
          <DataTable
            columns={roleColumns}
            data={rows}
            emptyState={<EmptyState description="Modifiez les filtres ou créez un premier rôle." title="Aucun rôle" />}
            getRowId={roleId}
            isLoading={roles.isLoading}
            onRowSelectionChange={setRowSelection}
            onSortingChange={setSorting}
            rowSelection={rowSelection}
            sorting={sorting}
          />
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

      <BulkActionBar
        count={selectedIds.length}
        noun="rôle sélectionné"
        nounPlural="rôles sélectionnés"
        onClear={clearSelection}
      >
        <RowAction
          disabled={bulkActive.isPending || !session.can(adminPermissions.rolesBulkSetActive)}
          disabledLabel="Vous n’êtes pas autorisé à modifier ces statuts"
          icon={<CheckCircleIcon />}
          label="Activer"
          onClick={() => bulkActive.mutate(true)}
        />
        <RowAction
          disabled={bulkActive.isPending || !session.can(adminPermissions.rolesBulkSetActive)}
          disabledLabel="Vous n’êtes pas autorisé à modifier ces statuts"
          icon={<ProhibitIcon />}
          label="Désactiver"
          onClick={() => bulkActive.mutate(false)}
          tone="danger"
        />
      </BulkActionBar>
    </div>
  );
}
