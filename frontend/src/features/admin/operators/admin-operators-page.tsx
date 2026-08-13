import {
  ArrowRightIcon,
  CheckCircleIcon,
  MagnifyingGlassIcon,
  PaperPlaneTiltIcon,
  PlusIcon,
  ProhibitIcon,
} from "@phosphor-icons/react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { RowSelectionState, SortingState } from "@tanstack/react-table";
import { useState } from "react";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { AdminUser, AdminUserCreation, BulkOperationResult } from "@/api/contracts";
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
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
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
import { useDebouncedValue } from "@/lib/use-debounced-value";

/**
 * The backend distinguishes these; the previous message blamed authorization for every
 * failure, including an email that was simply already taken.
 */
function createOperatorError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === "INVALID_STATE" || error.code === "RESOURCE_ALREADY_EXISTS") {
      return "Cet email est déjà utilisé sur la plateforme.";
    }
    if (error.code === "INVALID_PERMISSION_GRANT") {
      return "Seul un SuperAdmin peut créer un autre SuperAdmin.";
    }
    if (error.code === "VALIDATION_FAILED" || error.code === "INVALID_REQUEST") {
      return "Vérifiez les informations saisies.";
    }
    return error.message;
  }
  return "Création impossible. Réessayez.";
}

function initials(email: string) {
  return (email.split("@")[0] ?? email).slice(0, 2).toUpperCase();
}

/**
 * Per-row actions as icons with tooltips. The email is plain text rather than a link:
 * navigation buried in a data cell is not discoverable, and every row action belongs in one
 * predictable place at the end of the row.
 */
function RowActions({ operator }: { operator: AdminUser }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
    void queryClient.invalidateQueries({ queryKey: ["admin", "overview"] });
  };
  const onError = (error: unknown) => toast.error(error instanceof ApiError ? error.message : "Action impossible");
  const toggle = useMutation({
    mutationFn: () => adminApi.toggleUser(operator.id),
    onSuccess: () => {
      refresh();
      toast.success("Statut mis à jour");
    },
    onError,
  });
  const resend = useMutation({
    mutationFn: () => adminApi.resendOperatorActivation(operator.id),
    onSuccess: () => {
      refresh();
      toast.success("Lien d\u2019activation renvoyé");
    },
    onError,
  });

  const isSelf = session.me?.id === operator.id;
  const pending = operator.credentialState !== "ACTIVE";

  // Every action is always rendered; when it does not apply it is disabled and its tooltip says
  // why. Hiding it instead would both shift the remaining icons sideways from row to row and
  // leave the reader guessing whether the action is missing, forbidden, or simply not needed.
  const resendBlockedBy = !session.can(adminPermissions.usersResendActivation)
    ? "Vous n\u2019\u00eates pas autoris\u00e9 \u00e0 renvoyer l\u2019activation"
    : !pending
      ? "Cet op\u00e9rateur a d\u00e9j\u00e0 activ\u00e9 son acc\u00e8s"
      : resend.isPending
        ? "Envoi en cours\u2026"
        : null;

  const toggleBlockedBy = !session.can(adminPermissions.usersMutate)
    ? "Vous n\u2019\u00eates pas autoris\u00e9 \u00e0 modifier ce statut"
    : operator.isSuperAdmin
      ? "Le statut d\u2019un SuperAdmin ne se modifie pas ici"
      : isSelf && operator.isActive
        ? "Vous ne pouvez pas d\u00e9sactiver votre propre acc\u00e8s"
        : toggle.isPending
          ? "Action en cours\u2026"
          : null;

  return (
    <span className="flex items-center gap-0.5">
      <RowAction
        disabled={resendBlockedBy !== null}
        disabledLabel={resendBlockedBy ?? undefined}
        icon={<PaperPlaneTiltIcon />}
        label={"Renvoyer le lien d\u2019activation"}
        onClick={() => resend.mutate()}
      />
      <RowAction
        disabled={toggleBlockedBy !== null}
        disabledLabel={toggleBlockedBy ?? undefined}
        icon={operator.isActive ? <ProhibitIcon /> : <CheckCircleIcon />}
        label={operator.isActive ? "D\u00e9sactiver l\u2019acc\u00e8s" : "R\u00e9activer l\u2019acc\u00e8s"}
        onClick={() => toggle.mutate()}
        tone={operator.isActive ? "danger" : "default"}
      />
      <RowAction icon={<ArrowRightIcon />} label="Ouvrir la fiche" to={`/admin/operators/${operator.id}`} />
    </span>
  );
}

function CreateOperatorDialog() {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [superAdmin, setSuperAdmin] = useState(false);
  const [issued, setIssued] = useState<AdminUserCreation | null>(null);

  // Only a SuperAdmin may create another. The backend rejects it either way; showing the
  // control to everyone else just manufactures failures.
  const canGrantSuperAdmin = Boolean(session.me?.isSuperAdmin);

  const reset = () => {
    setFirstName("");
    setLastName("");
    setEmail("");
    setSuperAdmin(false);
    setIssued(null);
  };

  const create = useMutation({
    mutationFn: () =>
      adminApi.createUser({
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        email: email.trim(),
        isSuperAdmin: superAdmin,
      }),
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
      void queryClient.invalidateQueries({ queryKey: ["admin", "overview"] });
      // The dialog stays open to confirm what was sent and where to go if it never arrives.
      setIssued(result);
    },
  });

  if (!session.can(adminPermissions.usersCreate)) return null;

  const complete = firstName.trim() && lastName.trim() && email.trim();

  return (
    <Dialog
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) reset();
      }}
      open={open}
    >
      <DialogTrigger asChild>
        <Button>
          <PlusIcon />
          Créer un opérateur
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{issued ? "Opérateur créé" : "Créer un opérateur"}</DialogTitle>
          <DialogDescription>
            {issued
              ? "Un lien d’activation vient d’être envoyé par email."
              : "Créez l’identité de l’opérateur et son accès à l’administration."}
          </DialogDescription>
        </DialogHeader>
        {issued ? (
          <div className="space-y-4">
            <div className="rounded-lg border p-3">
              <p className="text-sm font-medium">{issued.operator.email}</p>
              <p className="mt-2 text-xs text-muted-foreground">
                L’opérateur définit son mot de passe depuis ce lien, valable 24 heures. Aucun mot de passe n’a été créé
                ici. Si l’email n’arrive pas, sa fiche permet de le renvoyer ou de générer un accès temporaire.
              </p>
            </div>
            <div className="flex justify-end">
              <Button
                onClick={() => {
                  setOpen(false);
                  reset();
                }}
              >
                Terminer
              </Button>
            </div>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="operator-first-name">Prénom</Label>
                <Input
                  autoFocus
                  id="operator-first-name"
                  onChange={(event) => setFirstName(event.target.value)}
                  value={firstName}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="operator-last-name">Nom</Label>
                <Input id="operator-last-name" onChange={(event) => setLastName(event.target.value)} value={lastName} />
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="operator-email">Email professionnel</Label>
              <Input
                id="operator-email"
                onChange={(event) => setEmail(event.target.value)}
                type="email"
                value={email}
              />
              <p className="text-xs text-muted-foreground">
                Sert d’identifiant de connexion. Il doit être inutilisé sur la plateforme.
              </p>
            </div>
            {canGrantSuperAdmin ? (
              <div className="flex items-start gap-3 rounded-lg border p-3">
                <Checkbox
                  checked={superAdmin}
                  id="operator-super-admin"
                  onCheckedChange={(value) => setSuperAdmin(value === true)}
                />
                <Label className="font-normal" htmlFor="operator-super-admin">
                  <span className="block text-sm font-medium">SuperAdmin</span>
                  <span className="block text-xs text-muted-foreground">
                    Accès total et protection renforcée. À utiliser exceptionnellement.
                  </span>
                </Label>
              </div>
            ) : null}
            {create.isError ? <p className="text-sm text-destructive">{createOperatorError(create.error)}</p> : null}
            <div className="flex justify-end gap-2">
              <Button onClick={() => setOpen(false)} variant="outline">
                Annuler
              </Button>
              <Button disabled={!complete || create.isPending} onClick={() => create.mutate()}>
                {create.isPending ? "Création…" : "Créer l’accès"}
              </Button>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}

const PAGE_SIZE = 20;

const column = createDataColumns<AdminUser>();

/**
 * Each column owns its header, its cell, and its own width. Keeping width with the definition is
 * what stops one column silently absorbing every spare pixel and crushing the rest.
 */
const operatorColumns = column.columns([
  column.display({
    id: "select",
    meta: { headerClassName: "w-10", cellClassName: "w-10" },
    header: ({ table }) => (
      <Checkbox
        aria-label="Sélectionner les opérateurs de cette page"
        checked={table.getIsAllRowsSelected() || (table.getIsSomeRowsSelected() && "indeterminate")}
        onCheckedChange={() => table.toggleAllRowsSelected()}
      />
    ),
    cell: ({ row }) => (
      <Checkbox
        aria-label={`Sélectionner ${row.original.email}`}
        checked={row.getIsSelected()}
        onCheckedChange={() => row.toggleSelected()}
      />
    ),
  }),
  column.accessor("email", {
    meta: { headerClassName: "min-w-[220px]" },
    header: ({ column: col }) => <SortHeader column={col}>Opérateur</SortHeader>,
    cell: ({ row }) => (
      <span className="flex items-center gap-3">
        <Avatar className="size-9">
          <AvatarFallback>{initials(row.original.email)}</AvatarFallback>
        </Avatar>
        <span className="min-w-0">
          <span className="block truncate font-medium">
            {[row.original.firstName, row.original.lastName].filter(Boolean).join(" ") || row.original.email}
          </span>
          <span className="block truncate text-xs text-muted-foreground">{row.original.email}</span>
        </span>
      </span>
    ),
  }),
  column.display({
    id: "roles",
    header: "Rôles",
    cell: ({ row }) =>
      row.original.roles.length ? (
        <span className="flex flex-wrap gap-1">
          {row.original.roles.map((role) => (
            <span className="rounded-md border bg-muted/50 px-2 py-0.5 text-xs" key={role.id}>
              {role.name}
            </span>
          ))}
        </span>
      ) : (
        <span className="text-muted-foreground">—</span>
      ),
  }),
  column.accessor("isSuperAdmin", {
    id: "superAdmin",
    header: ({ column: col }) => <SortHeader column={col}>Niveau</SortHeader>,
    cell: ({ row }) => (row.original.isSuperAdmin ? "SuperAdmin" : "Opérateur"),
  }),
  column.accessor("credentialState", {
    id: "activation",
    header: "Activation",
    cell: ({ row }) =>
      row.original.credentialState === "ACTIVE" ? (
        <span className="text-sm text-muted-foreground">Terminée</span>
      ) : (
        <StatusBadge tone="warning">En attente</StatusBadge>
      ),
  }),
  column.accessor("isActive", {
    id: "active",
    header: ({ column: col }) => <SortHeader column={col}>Statut</SortHeader>,
    cell: ({ row }) => (
      <StatusBadge tone={row.original.isActive ? "success" : "danger"}>
        {row.original.isActive ? "Actif" : "Désactivé"}
      </StatusBadge>
    ),
  }),
  column.display({
    id: "actions",
    meta: { headerClassName: "w-32", cellClassName: "w-32" },
    header: "Actions",
    cell: ({ row }) => <RowActions operator={row.original} />,
  }),
]);

export function AdminOperatorsPage() {
  const session = useAdminSession();
  const [search, setSearch] = useState("");
  const debouncedSearch = useDebouncedValue(search);
  const [active, setActive] = useState("all");
  const [page, setPage] = useState(0);
  const [sorting, setSorting] = useState<SortingState>([]);
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({});
  const queryClient = useQueryClient();
  const users = useQuery({
    queryKey: ["admin", "users", debouncedSearch, active, page, sorting],
    queryFn: () =>
      adminApi.users({
        search: debouncedSearch || undefined,
        active: active === "all" ? undefined : active === "active",
        page,
        size: PAGE_SIZE,
        sort: sorting[0]?.id,
        direction: sorting[0] ? (sorting[0].desc ? "desc" : "asc") : undefined,
      }),
    placeholderData: keepPreviousData,
  });
  const rows = users.data?.content ?? [];
  const selectedIds = Object.keys(rowSelection).filter((id) => rowSelection[id]);
  const clearSelection = () => setRowSelection({});

  // Every bulk call reports per-item outcomes, so a partial result is announced honestly rather
  // than shown as a flat success.
  const announce = (label: string) => (result: BulkOperationResult) => {
    void queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
    void queryClient.invalidateQueries({ queryKey: ["admin", "overview"] });
    clearSelection();
    if (result.failures.length === 0) {
      toast.success(`${label} : ${result.succeeded} opérateur(s)`);
      return;
    }
    toast.warning(`${label} : ${result.succeeded} réussi(s), ${result.failures.length} refusé(s)`, {
      description: result.failures[0]?.message,
    });
  };

  const bulkActive = useMutation({
    mutationFn: (next: boolean) => adminApi.bulkSetOperatorsActive(selectedIds, next),
    onSuccess: announce("Statut mis à jour"),
  });
  const bulkResend = useMutation({
    mutationFn: () => adminApi.bulkResendOperatorActivation(selectedIds),
    onSuccess: announce("Liens renvoyés"),
  });
  const bulkAssignRole = useMutation({
    mutationFn: (roleId: string) => adminApi.bulkAssignOperatorRole(selectedIds, roleId),
    onSuccess: announce("Rôle attribué"),
  });

  const assignableRoles = useQuery({
    queryKey: ["admin", "roles", "assignable"],
    queryFn: () => adminApi.roles({ active: true, page: 0, size: 100 }),
    enabled: selectedIds.length > 0 && session.can(adminPermissions.rolesRead),
  });

  const busy = bulkActive.isPending || bulkResend.isPending || bulkAssignRole.isPending;

  return (
    <div className="space-y-7">
      <PageHeader
        actions={session.can(adminPermissions.usersCreate) ? <CreateOperatorDialog /> : undefined}
        title="Opérateurs"
      />
      <section className="overflow-hidden rounded-xl border bg-card">
        <div className="flex flex-col gap-3 border-b p-4 sm:flex-row sm:items-center">
          <div className="relative flex-1 sm:max-w-sm">
            <MagnifyingGlassIcon className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              aria-label="Rechercher les opérateurs"
              className="ps-9"
              onChange={(event) => {
                setSearch(event.target.value);
                setPage(0);
              }}
              placeholder="Email ou identifiant…"
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
            <SelectTrigger aria-label="Statut de l’opérateur" className="w-full sm:w-44">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Tous les statuts</SelectItem>
              <SelectItem value="active">Actifs</SelectItem>
              <SelectItem value="inactive">Inactifs</SelectItem>
            </SelectContent>
          </Select>
        </div>
        {users.isLoading ? (
          <div className="p-5">
            <LoadingState />
          </div>
        ) : users.isError ? (
          <ErrorState retry={() => void users.refetch()} />
        ) : (
          <DataTable
            emptyState={
              <EmptyState
                description="Modifiez les filtres ou créez un premier accès opérateur."
                title="Aucun opérateur"
              />
            }
            columns={operatorColumns}
            data={rows}
            getRowId={(operator) => operator.id}
            isLoading={users.isLoading}
            onRowSelectionChange={setRowSelection}
            onSortingChange={setSorting}
            rowSelection={rowSelection}
            sorting={sorting}
          />
        )}
        {users.data ? (
          <PaginationBar
            onPageChange={setPage}
            page={users.data.page}
            totalElements={users.data.totalElements}
            totalPages={users.data.totalPages}
          />
        ) : null}
      </section>

      <BulkActionBar
        count={selectedIds.length}
        noun="opérateur sélectionné"
        nounPlural="opérateurs sélectionnés"
        onClear={clearSelection}
      >
        {session.can(adminPermissions.usersBulkAssignRole) ? (
          <Select disabled={busy} onValueChange={(value) => bulkAssignRole.mutate(value)} value="">
            <SelectTrigger aria-label="Attribuer un rôle à la sélection" className="h-8 w-48">
              <SelectValue placeholder="Attribuer un rôle" />
            </SelectTrigger>
            <SelectContent>
              {(assignableRoles.data?.content ?? [])
                .filter((role) => role.isActive)
                .map((role) => (
                  <SelectItem key={role.id} value={role.id}>
                    {role.name}
                  </SelectItem>
                ))}
            </SelectContent>
          </Select>
        ) : null}
        <RowAction
          disabled={busy || !session.can(adminPermissions.usersBulkResendActivation)}
          disabledLabel="Vous n’êtes pas autorisé à renvoyer l’activation"
          icon={<PaperPlaneTiltIcon />}
          label="Renvoyer le lien d’activation"
          onClick={() => bulkResend.mutate()}
        />
        <RowAction
          disabled={busy || !session.can(adminPermissions.usersBulkSetActive)}
          disabledLabel="Vous n’êtes pas autorisé à modifier ces statuts"
          icon={<CheckCircleIcon />}
          label="Activer"
          onClick={() => bulkActive.mutate(true)}
        />
        <RowAction
          disabled={busy || !session.can(adminPermissions.usersBulkSetActive)}
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
