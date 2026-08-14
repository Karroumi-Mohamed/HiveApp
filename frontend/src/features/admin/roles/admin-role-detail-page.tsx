import {
  ArchiveIcon,
  ArrowLeftIcon,
  CheckCircleIcon,
  CopyIcon,
  FloppyDiskIcon,
  PencilSimpleIcon,
  TrashIcon,
  UserIcon,
} from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type {
  AdminPermission,
  AdminRoleHistoryEntry,
  AdminRoleImpact,
  AdminRoleStatus,
  RoleHolder,
} from "@/api/contracts";
import { ApiError } from "@/api/http";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { StatusBadge, type StatusTone } from "@/components/patterns/status-badge";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { AdminPermissionEditor } from "@/features/admin/roles/admin-permission-editor";
import { AdminRoleDuplicateDialog, AdminRoleMetadataDialog } from "@/features/admin/roles/admin-role-dialogs";
import { permissionSetsDiffer } from "@/features/admin/roles/admin-role-rules";

const statusPresentation: Record<AdminRoleStatus, { label: string; tone: StatusTone }> = {
  ACTIVE: { label: "Actif", tone: "success" },
  INACTIVE: { label: "Inactif", tone: "warning" },
  ARCHIVED: { label: "Archivé", tone: "neutral" },
};

function formatDate(value: string) {
  return new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function initials(value: string) {
  return (value.split("@")[0] ?? value).slice(0, 2).toUpperCase();
}

const roleActionLabels: Record<string, string> = {
  "platform.roles.create": "Rôle créé",
  "platform.roles.create_from_preset": "Rôle créé depuis un préréglage",
  "platform.roles.duplicate": "Rôle dupliqué",
  "platform.roles.update": "Informations modifiées",
  "platform.roles.replace_permissions": "Permissions modifiées",
  "platform.roles.transition_status": "Statut modifié",
  "platform.roles.assign_operator": "Opérateur assigné",
  "platform.roles.remove_operator": "Opérateur retiré",
  "platform.roles.grant_permission": "Permission ajoutée",
  "platform.roles.revoke_permission": "Permission retirée",
  "platform.roles.toggle_active": "Activation modifiée",
  "platform.roles.delete": "Rôle supprimé",
};

function readableAction(value: string) {
  if (roleActionLabels[value]) return roleActionLabels[value];
  const spaced = value.replaceAll("_", " ").replaceAll(".", " · ");
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

function RoleImpactDialog({
  impact,
  title,
  confirmLabel,
  busy,
  onClose,
  onConfirm,
}: {
  impact: AdminRoleImpact | null;
  title: string;
  confirmLabel: string;
  busy: boolean;
  onClose: () => void;
  onConfirm: () => void;
}) {
  return (
    <Dialog onOpenChange={(open) => !open && onClose()} open={impact !== null}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>Vérifiez l’impact calculé avec l’état actuel du rôle.</DialogDescription>
        </DialogHeader>
        {impact ? (
          <dl className="divide-y border-y text-sm">
            <div className="flex justify-between gap-4 py-3">
              <dt className="text-muted-foreground">Opérateurs affectés</dt>
              <dd className="font-medium tabular-nums">{impact.assignmentCount}</dd>
            </div>
            {impact.permissionsAdded.length > 0 ? (
              <div className="flex justify-between gap-4 py-3">
                <dt className="text-muted-foreground">Permissions ajoutées</dt>
                <dd className="font-medium tabular-nums">{impact.permissionsAdded.length}</dd>
              </div>
            ) : null}
            {impact.permissionsRemoved.length > 0 ? (
              <div className="flex justify-between gap-4 py-3">
                <dt className="text-muted-foreground">Permissions retirées</dt>
                <dd className="font-medium tabular-nums">{impact.permissionsRemoved.length}</dd>
              </div>
            ) : null}
            {impact.operatorsLosingLastPermissionSource > 0 ? (
              <div className="flex justify-between gap-4 py-3">
                <dt className="text-muted-foreground">Perdent leur dernière source d’accès</dt>
                <dd className="font-medium tabular-nums">{impact.operatorsLosingLastPermissionSource}</dd>
              </div>
            ) : null}
            {impact.currentStatus !== impact.proposedStatus ? (
              <div className="flex justify-between gap-4 py-3">
                <dt className="text-muted-foreground">Nouveau statut</dt>
                <dd className="font-medium">{statusPresentation[impact.proposedStatus].label}</dd>
              </div>
            ) : null}
          </dl>
        ) : null}
        {impact?.actorMayLoseAccess ? (
          <p className="border-s-2 border-warning ps-3 text-sm">
            Votre propre accès peut être réduit par cette modification.
          </p>
        ) : null}
        <DialogFooter>
          <Button disabled={busy} onClick={onClose} type="button" variant="outline">
            Annuler
          </Button>
          <Button disabled={busy} onClick={onConfirm} type="button">
            {busy ? "Application…" : confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function RoleOperators({ roleId }: { roleId: string }) {
  const session = useAdminSession();
  const canRead = session.can(adminPermissions.rolesReadHolders);
  const holders = useQuery({
    queryKey: ["admin", "roles", roleId, "operators"],
    queryFn: () => adminApi.roleHolders(roleId),
    enabled: canRead,
  });

  if (!canRead) return <p className="py-8 text-sm text-muted-foreground">Accès non autorisé.</p>;
  if (holders.isLoading) return <LoadingState rows={3} />;
  if (holders.isError) return <ErrorState retry={() => void holders.refetch()} />;
  if (!holders.data?.length)
    return <p className="border-y py-10 text-center text-sm text-muted-foreground">Aucun opérateur.</p>;

  return (
    <div className="divide-y border-y">
      {holders.data.map((holder: RoleHolder) => (
        <Link
          className="flex min-h-16 items-center gap-3 py-3 hover:bg-muted/40"
          key={holder.adminUserId}
          to={`/admin/operators/${holder.adminUserId}`}
        >
          <Avatar className="size-8">
            <AvatarFallback>{initials(holder.email)}</AvatarFallback>
          </Avatar>
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm font-medium">
              {[holder.firstName, holder.lastName].filter(Boolean).join(" ") || holder.email}
            </span>
            <span className="block truncate text-xs text-muted-foreground">{holder.email}</span>
          </span>
          {holder.isSuperAdmin ? <StatusBadge tone="info">SuperAdmin</StatusBadge> : null}
          <StatusBadge tone={holder.isActive ? "success" : "danger"}>
            {holder.isActive ? "Actif" : "Inactif"}
          </StatusBadge>
        </Link>
      ))}
    </div>
  );
}

function RoleHistory({ roleId }: { roleId: string }) {
  const session = useAdminSession();
  const canRead = session.can(adminPermissions.rolesReadHistory);
  const history = useQuery({
    queryKey: ["admin", "roles", roleId, "history"],
    queryFn: () => adminApi.roleHistory(roleId),
    enabled: canRead,
  });

  if (!canRead) return <p className="py-8 text-sm text-muted-foreground">Accès non autorisé.</p>;
  if (history.isLoading) return <LoadingState rows={4} />;
  if (history.isError) return <ErrorState retry={() => void history.refetch()} />;
  if (!history.data?.length)
    return <p className="border-y py-10 text-center text-sm text-muted-foreground">Aucun événement enregistré.</p>;

  return (
    <ol className="divide-y border-y">
      {history.data.map((entry: AdminRoleHistoryEntry) => (
        <li className="grid gap-1 py-3 text-sm sm:grid-cols-[1fr_auto]" key={entry.id}>
          <span className="font-medium">
            {readableAction(entry.action)}
            {entry.subject ? <span className="font-normal text-muted-foreground"> · {entry.subject}</span> : null}
          </span>
          <time className="text-muted-foreground" dateTime={entry.occurredAt}>
            {formatDate(entry.occurredAt)}
          </time>
          <span className="text-xs text-muted-foreground">
            {entry.outcome === "SUCCEEDED" ? "Réussi" : "Échec"} ·{" "}
            {entry.actorEmail ? `par ${entry.actorEmail}` : "Système"}
          </span>
        </li>
      ))}
    </ol>
  );
}

function DefinitionRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="grid gap-1 py-3 sm:grid-cols-[180px_1fr] sm:gap-6">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="text-sm font-medium">{value}</dd>
    </div>
  );
}

export function AdminRoleDetailPage() {
  const { roleId = "" } = useParams();
  const navigate = useNavigate();
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [selectedIds, setSelectedIds] = useState<Set<string>>(() => new Set());
  const [permissionImpact, setPermissionImpact] = useState<AdminRoleImpact | null>(null);
  const [statusImpact, setStatusImpact] = useState<AdminRoleImpact | null>(null);
  const [pendingStatus, setPendingStatus] = useState<AdminRoleStatus | null>(null);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleteConfirmation, setDeleteConfirmation] = useState("");

  const role = useQuery({
    queryKey: ["admin", "roles", roleId],
    queryFn: () => adminApi.role(roleId),
    enabled: Boolean(roleId),
  });
  const grantablePermissions = useQuery({
    queryKey: ["admin", "roles", "grantable-permissions"],
    queryFn: adminApi.grantableRolePermissions,
    enabled: session.can(adminPermissions.rolesListGrantable),
  });

  useEffect(() => {
    if (role.data) setSelectedIds(new Set(role.data.permissions.map((permission) => permission.id)));
  }, [role.data]);

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ["admin", "roles"] });
    void queryClient.invalidateQueries({ queryKey: ["admin", "overview"] });
  };
  const reportError = (error: unknown) => {
    const message = error instanceof ApiError ? error.message : "Action impossible.";
    toast.error(message);
    if (error instanceof ApiError && error.code === "STALE_IMPACT_PREVIEW") refresh();
  };

  const previewPermissions = useMutation({
    mutationFn: (permissionIds: string[]) => adminApi.previewRoleImpact(roleId, { permissionIds }),
    onSuccess: setPermissionImpact,
    onError: reportError,
  });
  const replacePermissions = useMutation({
    mutationFn: (impact: AdminRoleImpact) =>
      adminApi.replaceRolePermissions(roleId, {
        permissionIds: [...selectedIds],
        expectedVersion: impact.version,
        confirmedAssignmentCount: impact.assignmentCount,
      }),
    onSuccess: () => {
      setPermissionImpact(null);
      refresh();
      toast.success("Permissions mises à jour");
    },
    onError: (error) => {
      reportError(error);
      if (error instanceof ApiError && error.code === "STALE_IMPACT_PREVIEW") {
        setPermissionImpact(null);
      }
    },
  });
  const previewStatus = useMutation({
    mutationFn: (status: AdminRoleStatus) => adminApi.previewRoleImpact(roleId, { status }),
    onSuccess: (impact, status) => {
      setPendingStatus(status);
      setStatusImpact(impact);
    },
    onError: reportError,
  });
  const transitionStatus = useMutation({
    mutationFn: ({ impact, status }: { impact: AdminRoleImpact; status: AdminRoleStatus }) =>
      adminApi.transitionRoleStatus(roleId, {
        status,
        expectedVersion: impact.version,
        confirmedAssignmentCount: impact.assignmentCount,
      }),
    onSuccess: () => {
      setStatusImpact(null);
      setPendingStatus(null);
      refresh();
      toast.success("Statut mis à jour");
    },
    onError: (error) => {
      reportError(error);
      if (error instanceof ApiError && error.code === "STALE_IMPACT_PREVIEW") {
        setStatusImpact(null);
        setPendingStatus(null);
      }
    },
  });
  const deleteRole = useMutation({
    mutationFn: () => adminApi.deleteRole(roleId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "roles"] });
      toast.success("Rôle supprimé");
      navigate("/admin/roles");
    },
    onError: reportError,
  });

  const data = role.data;
  const currentIds = useMemo(() => new Set(data?.permissions.map((permission) => permission.id) ?? []), [data]);
  const editableIds = useMemo(
    () => new Set(grantablePermissions.data?.map((permission) => permission.id) ?? []),
    [grantablePermissions.data],
  );
  const visiblePermissions = useMemo(() => {
    const byId = new Map<string, AdminPermission>();
    for (const permission of [...(grantablePermissions.data ?? []), ...(data?.permissions ?? [])]) {
      byId.set(permission.id, permission);
    }
    return [...byId.values()];
  }, [data?.permissions, grantablePermissions.data]);
  const permissionsChanged = permissionSetsDiffer(currentIds, selectedIds);
  const hasLockedCurrentPermission = [...currentIds].some((permissionId) => !editableIds.has(permissionId));
  const canEditPermissions =
    data?.availableActions.includes("EDIT_PERMISSIONS") === true &&
    grantablePermissions.isSuccess &&
    !hasLockedCurrentPermission &&
    data?.status !== "ARCHIVED";

  const backLink = (
    <Button asChild variant="ghost">
      <Link to="/admin/roles">
        <ArrowLeftIcon /> Rôles
      </Link>
    </Button>
  );

  if (role.isLoading) {
    return (
      <div className="space-y-6">
        {backLink}
        <LoadingState />
      </div>
    );
  }
  if (role.isError || !data) {
    return (
      <div className="space-y-6">
        {backLink}
        <ErrorState retry={() => void role.refetch()} />
      </div>
    );
  }

  const status = statusPresentation[data.status];
  const canTransition = data.availableActions.includes("TRANSITION_STATUS");

  return (
    <div className="space-y-6">
      <div>{backLink}</div>
      <PageHeader
        actions={
          <>
            {data.availableActions.includes("EDIT_METADATA") ? (
              <AdminRoleMetadataDialog
                role={data}
                trigger={
                  <Button variant="outline">
                    <PencilSimpleIcon /> Modifier
                  </Button>
                }
              />
            ) : null}
            {data.availableActions.includes("DUPLICATE") ? (
              <AdminRoleDuplicateDialog
                role={data}
                trigger={
                  <Button variant="outline">
                    <CopyIcon /> Dupliquer
                  </Button>
                }
              />
            ) : null}
          </>
        }
        description={
          <span className="flex flex-wrap items-center gap-2">
            <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
            {data.description ? <span>{data.description}</span> : null}
          </span>
        }
        title={data.name}
      />

      {data.status === "INACTIVE" ? (
        <p className="border-s-2 border-warning ps-3 text-sm text-muted-foreground">
          Ce rôle n’accorde aucun accès tant qu’il reste inactif.
        </p>
      ) : null}

      <Tabs defaultValue="overview">
        <TabsList className="w-full justify-start border-b" variant="line">
          <TabsTrigger value="overview">Aperçu</TabsTrigger>
          <TabsTrigger value="permissions">Permissions</TabsTrigger>
          <TabsTrigger value="operators">Opérateurs ({data.assignedOperatorCount})</TabsTrigger>
          <TabsTrigger value="history">Historique</TabsTrigger>
        </TabsList>

        <TabsContent className="pt-5" value="overview">
          <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_320px]">
            <section>
              <h2 className="font-semibold">Informations</h2>
              <dl className="mt-3 divide-y border-y">
                <DefinitionRow label="Statut" value={<StatusBadge tone={status.tone}>{status.label}</StatusBadge>} />
                <DefinitionRow label="Permissions" value={data.permissions.length} />
                <DefinitionRow label="Opérateurs" value={data.assignedOperatorCount} />
                <DefinitionRow label="Créé" value={formatDate(data.createdAt)} />
                <DefinitionRow label="Dernière modification" value={formatDate(data.updatedAt)} />
              </dl>
            </section>

            <section>
              <h2 className="font-semibold">Cycle de vie</h2>
              <div className="mt-3 grid gap-2">
                {data.status !== "ACTIVE" && data.status !== "ARCHIVED" ? (
                  <Button
                    disabled={!canTransition || previewStatus.isPending}
                    onClick={() => previewStatus.mutate("ACTIVE")}
                  >
                    <CheckCircleIcon /> Activer
                  </Button>
                ) : null}
                {data.status === "ACTIVE" ? (
                  <Button
                    disabled={!canTransition || previewStatus.isPending}
                    onClick={() => previewStatus.mutate("INACTIVE")}
                    variant="outline"
                  >
                    Désactiver
                  </Button>
                ) : null}
                {data.status !== "ARCHIVED" ? (
                  <Button
                    disabled={!canTransition || previewStatus.isPending}
                    onClick={() => previewStatus.mutate("ARCHIVED")}
                    variant="outline"
                  >
                    <ArchiveIcon /> Archiver
                  </Button>
                ) : (
                  <Button
                    disabled={!canTransition || previewStatus.isPending}
                    onClick={() => previewStatus.mutate("INACTIVE")}
                    variant="outline"
                  >
                    Restaurer comme inactif
                  </Button>
                )}
                {data.availableActions.includes("DELETE") ? (
                  <Button className="text-destructive" onClick={() => setDeleteOpen(true)} variant="ghost">
                    <TrashIcon /> Supprimer définitivement
                  </Button>
                ) : null}
              </div>
            </section>
          </div>
        </TabsContent>

        <TabsContent className="pt-5" value="permissions">
          <div className="sticky top-16 z-20 -mx-1 mb-5 flex flex-col gap-3 border-b bg-background/95 px-1 py-3 backdrop-blur sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 className="font-semibold">Permissions du rôle</h2>
              <p className="mt-1 text-sm text-muted-foreground">{selectedIds.size} sélectionnée(s)</p>
            </div>
            {canEditPermissions ? (
              <div className="flex gap-2">
                <Button
                  disabled={!permissionsChanged}
                  onClick={() => setSelectedIds(new Set(currentIds))}
                  variant="outline"
                >
                  Annuler les changements
                </Button>
                <Button
                  disabled={!permissionsChanged || previewPermissions.isPending}
                  onClick={() => previewPermissions.mutate([...selectedIds])}
                >
                  <FloppyDiskIcon /> Vérifier et enregistrer
                </Button>
              </div>
            ) : null}
          </div>
          {grantablePermissions.isLoading ? (
            <LoadingState rows={5} />
          ) : grantablePermissions.isError && data.permissions.length === 0 ? (
            <ErrorState retry={() => void grantablePermissions.refetch()} />
          ) : (
            <>
              {hasLockedCurrentPermission ? (
                <p className="mb-4 border-s-2 border-warning ps-3 text-sm text-muted-foreground">
                  Ce rôle contient une permission hors de votre plafond de délégation. Il reste consultable mais ne peut
                  pas être modifié par votre compte.
                </p>
              ) : null}
              <AdminPermissionEditor
                disabled={!canEditPermissions}
                editableIds={editableIds}
                onChange={setSelectedIds}
                permissions={visiblePermissions}
                selectedIds={selectedIds}
              />
            </>
          )}
        </TabsContent>

        <TabsContent className="pt-5" value="operators">
          <div className="mb-4 flex items-center gap-2">
            <UserIcon className="text-muted-foreground" />
            <h2 className="font-semibold">Opérateurs assignés</h2>
          </div>
          <RoleOperators roleId={roleId} />
        </TabsContent>

        <TabsContent className="pt-5" value="history">
          <h2 className="mb-4 font-semibold">Historique du rôle</h2>
          <RoleHistory roleId={roleId} />
        </TabsContent>
      </Tabs>

      <RoleImpactDialog
        busy={replacePermissions.isPending}
        confirmLabel="Enregistrer les permissions"
        impact={permissionImpact}
        onClose={() => setPermissionImpact(null)}
        onConfirm={() => permissionImpact && replacePermissions.mutate(permissionImpact)}
        title="Confirmer les permissions"
      />
      <RoleImpactDialog
        busy={transitionStatus.isPending}
        confirmLabel="Confirmer le statut"
        impact={statusImpact}
        onClose={() => {
          setStatusImpact(null);
          setPendingStatus(null);
        }}
        onConfirm={() =>
          statusImpact && pendingStatus && transitionStatus.mutate({ impact: statusImpact, status: pendingStatus })
        }
        title="Confirmer le changement de statut"
      />

      <Dialog
        onOpenChange={(open) => {
          setDeleteOpen(open);
          if (!open) setDeleteConfirmation("");
        }}
        open={deleteOpen}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Supprimer définitivement le rôle</DialogTitle>
            <DialogDescription>
              Cette action est réservée aux rôles inactifs qui n’ont jamais été assignés. Pour confirmer, saisissez
              exactement :
            </DialogDescription>
          </DialogHeader>
          <p className="select-all break-all font-mono font-semibold text-foreground">{data.name}</p>
          <div className="space-y-2">
            <Label htmlFor="delete-role-confirmation">Saisissez le nom du rôle</Label>
            <Input
              autoComplete="off"
              autoFocus
              id="delete-role-confirmation"
              onChange={(event) => setDeleteConfirmation(event.target.value)}
              spellCheck={false}
              value={deleteConfirmation}
            />
          </div>
          <DialogFooter>
            <Button disabled={deleteRole.isPending} onClick={() => setDeleteOpen(false)} variant="outline">
              Annuler
            </Button>
            <Button
              disabled={deleteRole.isPending || deleteConfirmation !== data.name}
              onClick={() => deleteRole.mutate()}
              variant="destructive"
            >
              <TrashIcon /> {deleteRole.isPending ? "Suppression…" : "Supprimer"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
