import { ArrowLeftIcon, CopyIcon, MagnifyingGlassIcon, PlusIcon, TrashIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type FormEvent, useDeferredValue, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import { clientApi } from "@/api/client-api";
import type { Role, RoleImpact } from "@/api/contracts";
import { ApiError } from "@/api/http";
import { clientPermissions } from "@/auth/permissions";
import { useClientSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { SectionTabs } from "@/components/patterns/section-tabs";
import { StatusBadge } from "@/components/patterns/status-badge";
import { Button } from "@/components/ui/button";
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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { isRolePreviewReady } from "@/features/client/roles/role-edit-rules";

function RoleForm({ role, duplicate, onDone }: { role?: Role; duplicate?: boolean; onDone: (role: Role) => void }) {
  const session = useClientSession();
  const queryClient = useQueryClient();
  const [name, setName] = useState(duplicate ? `${role?.name ?? ""} — copie` : (role?.name ?? ""));
  const [description, setDescription] = useState(role?.description ?? "");
  const [boundary, setBoundary] = useState(role?.templateBoundary ?? "ACCOUNT");
  const [companyId, setCompanyId] = useState(role?.boundaryCompanyId ?? "");
  // Editing an existing role is the only branch the backend gates on an impact preview. Creating
  // and duplicating change nothing that is already assigned, so neither the preview nor its
  // permission is required for them.
  const isEdit = Boolean(role) && !duplicate;
  const impactQueryKey = ["client", "roles", role?.id, "impact", "UPDATE"] as const;
  const impact = useQuery({
    queryKey: impactQueryKey,
    queryFn: () => clientApi.roleImpact(role?.id ?? "", "UPDATE"),
    enabled: isEdit && Boolean(role?.id) && session.can(clientPermissions.rolesImpact),
  });

  const save = useMutation({
    mutationFn: () => {
      if (duplicate && role) return clientApi.duplicateRole(role.id, { name, description: description || null });
      if (role) {
        // No preview, no edit. Falling back to the role's own version with a null count sent a
        // confirmation of numbers nobody had seen — which the backend rejects for an assigned
        // role, and which would silently confirm a stale picture if it ever stopped rejecting it.
        const preview = impact.data;
        if (!isRolePreviewReady(isEdit, impact) || !preview) {
          throw new Error("Impact preview is required before confirming this change");
        }
        return clientApi.updateRole(role.id, {
          name,
          description: description || null,
          expectedVersion: preview.version,
          confirmedAssignmentCount: preview.assignmentCount,
        });
      }
      return clientApi.createRole({
        templateBoundary: boundary,
        boundaryCompanyId: boundary === "COMPANY" ? companyId : null,
        name,
        description: description || null,
      });
    },
    onSuccess: onDone,
    onError: (error) => {
      // The preview went stale between rendering and confirming — someone assigned or unassigned
      // the role meanwhile. Refetch so the reader confirms against what is true now rather than
      // retrying blindly against numbers the server has already rejected.
      if (error instanceof ApiError && error.code === "OPERATION_BLOCKED") {
        void queryClient.resetQueries({ queryKey: impactQueryKey, exact: true });
      }
    },
  });
  // Retained data is deliberately not confirmable while it is refreshing or after a failed
  // refresh: it describes the state that the server already rejected as stale.
  const previewReady = isRolePreviewReady(isEdit, impact);

  return (
    <form
      className="space-y-4"
      onSubmit={(event: FormEvent) => {
        event.preventDefault();
        // Guarded here as well as on the button: Enter submits a form whatever the button says.
        if (!previewReady) return;
        save.mutate();
      }}
    >
      <div className="space-y-2">
        <Label htmlFor="role-name">Nom</Label>
        <Input id="role-name" onChange={(event) => setName(event.target.value)} required value={name} />
      </div>
      {isEdit && previewReady && impact.data && impact.data.assignmentCount > 0 ? (
        <div className="rounded-lg border border-warning/30 bg-warning-subtle p-3 text-sm">
          <p className="font-medium">
            Ce rôle est attribué {impact.data.assignmentCount} fois et concerne {impact.data.affectedMemberCount}{" "}
            membre(s), dont {impact.data.activeMemberCount} actif(s).
          </p>
          {impact.data.scopes.length ? (
            <p className="mt-1 text-xs text-muted-foreground">
              Portées : {impact.data.scopes.map((entry) => `${entry.scope} (${entry.assignmentCount})`).join(", ")}
            </p>
          ) : null}
          <p className="mt-2 text-xs text-muted-foreground">
            Enregistrer confirme cette situation. Si elle change entre-temps, la confirmation est refusée et cet aperçu
            est actualisé.
          </p>
        </div>
      ) : null}
      {isEdit && !previewReady ? (
        <div className="rounded-lg border border-destructive/30 bg-destructive/5 p-3">
          <p className="text-sm">
            {impact.isFetching
              ? "Calcul de l’impact de cette modification…"
              : impact.isError
                ? "L’impact de cette modification n’a pas pu être calculé. Sans cet aperçu, la modification ne peut pas être confirmée."
                : "Cette modification exige un aperçu de son impact avant confirmation."}
          </p>
          {!impact.isFetching ? (
            <Button
              className="mt-2"
              onClick={() => void queryClient.resetQueries({ queryKey: impactQueryKey, exact: true })}
              size="sm"
              type="button"
              variant="outline"
            >
              Réessayer le calcul
            </Button>
          ) : null}
        </div>
      ) : null}
      {!role ? (
        <>
          <div className="space-y-2">
            <Label>Portée maximale du modèle</Label>
            <Select onValueChange={(value) => setBoundary(value as "ACCOUNT" | "COMPANY")} value={boundary}>
              <SelectTrigger aria-label="Portée maximale du modèle">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ACCOUNT">Compte</SelectItem>
                <SelectItem value="COMPANY">Entreprise</SelectItem>
              </SelectContent>
            </Select>
          </div>
          {boundary === "COMPANY" ? (
            <div className="space-y-2">
              <Label>Entreprise</Label>
              <Select onValueChange={setCompanyId} value={companyId}>
                <SelectTrigger aria-label="Entreprise">
                  <SelectValue placeholder="Sélectionner" />
                </SelectTrigger>
                <SelectContent>
                  {session.companies.map((company) => (
                    <SelectItem key={company.id} value={company.id}>
                      {company.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          ) : null}
        </>
      ) : null}
      <div className="space-y-2">
        <Label htmlFor="role-description">Description</Label>
        <Textarea id="role-description" onChange={(event) => setDescription(event.target.value)} value={description} />
      </div>
      <div className="flex justify-end">
        <Button
          disabled={!name.trim() || (boundary === "COMPANY" && !companyId) || save.isPending || !previewReady}
          type="submit"
        >
          Enregistrer
        </Button>
      </div>
    </form>
  );
}

function RoleDialog({ role, duplicate }: { role?: Role; duplicate?: boolean }) {
  const session = useClientSession();
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const requiredPermission = duplicate
    ? clientPermissions.rolesDuplicate
    : role
      ? clientPermissions.rolesUpdate
      : clientPermissions.rolesCreate;
  // Only editing an existing role needs the preview, so only editing needs permission to read
  // one. Requiring it for creation and duplication hid two actions that change nothing already
  // assigned.
  const needsImpactPreview = Boolean(role) && !duplicate;
  if (!session.can(requiredPermission)) return null;
  if (needsImpactPreview && !session.can(clientPermissions.rolesImpact)) return null;
  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>
        <Button variant={role ? "outline" : "default"}>
          {duplicate ? (
            <>
              <CopyIcon />
              Dupliquer
            </>
          ) : role ? (
            "Modifier"
          ) : (
            <>
              <PlusIcon />
              Créer un rôle
            </>
          )}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{duplicate ? "Dupliquer le rôle" : role ? "Modifier le rôle" : "Nouveau rôle"}</DialogTitle>
          <DialogDescription>
            Un rôle définit ce qui peut être accordé. Sa portée maximale est fixe après création.
          </DialogDescription>
        </DialogHeader>
        <RoleForm
          duplicate={duplicate}
          onDone={(saved) => {
            void queryClient.invalidateQueries({ queryKey: ["client", "roles"] });
            setOpen(false);
            toast.success("Rôle enregistré");
            navigate(`/app/roles/${saved.id}`);
          }}
          role={role}
        />
      </DialogContent>
    </Dialog>
  );
}

function ImpactDialog({ role, action }: { role: Role; action: "activate" | "deactivate" | "archive" | "delete" }) {
  const session = useClientSession();
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const type = action.toUpperCase();
  const impact = useQuery({
    queryKey: ["client", "roles", role.id, "impact", type],
    queryFn: () => clientApi.roleImpact(role.id, type),
    enabled: open,
  });
  const mutate = useMutation({
    mutationFn: async () => {
      if (action === "delete") {
        await clientApi.deleteRole(role.id);
        return;
      }
      await clientApi.transitionRole(
        role.id,
        action,
        impact.data
          ? { expectedVersion: impact.data.version, confirmedAssignmentCount: impact.data.assignmentCount }
          : undefined,
      );
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["client", "roles"] });
      setOpen(false);
      toast.success("Cycle de vie mis à jour");
      if (action === "delete") navigate("/app/roles");
    },
  });
  const requiredPermission = {
    activate: clientPermissions.rolesActivate,
    deactivate: clientPermissions.rolesDeactivate,
    archive: clientPermissions.rolesArchive,
    delete: clientPermissions.rolesDelete,
  }[action];
  if (!session.can(requiredPermission)) return null;
  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>
        <Button variant={action === "delete" ? "destructive" : "outline"}>
          {action === "activate"
            ? "Activer"
            : action === "deactivate"
              ? "Désactiver"
              : action === "archive"
                ? "Archiver"
                : "Supprimer"}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Confirmer l’action</DialogTitle>
          <DialogDescription>La prévisualisation ci-dessous est recalculée au moment de l’action.</DialogDescription>
        </DialogHeader>
        {impact.isLoading ? (
          <LoadingState rows={3} />
        ) : impact.data ? (
          <ImpactSummary impact={impact.data} />
        ) : (
          <ErrorState retry={() => void impact.refetch()} />
        )}
        <div className="flex justify-end">
          <Button
            disabled={!impact.data || mutate.isPending}
            onClick={() => mutate.mutate()}
            variant={action === "delete" ? "destructive" : "default"}
          >
            Confirmer
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function ImpactSummary({ impact }: { impact: RoleImpact }) {
  return (
    <dl className="grid gap-4 rounded-lg border p-4 sm:grid-cols-3">
      <div>
        <dt className="text-xs text-muted-foreground">Attributions</dt>
        <dd className="mt-1 text-xl font-semibold tabular-nums">{impact.assignmentCount}</dd>
      </div>
      <div>
        <dt className="text-xs text-muted-foreground">Membres affectés</dt>
        <dd className="mt-1 text-xl font-semibold tabular-nums">{impact.affectedMemberCount}</dd>
      </div>
      <div>
        <dt className="text-xs text-muted-foreground">Membres actifs</dt>
        <dd className="mt-1 text-xl font-semibold tabular-nums">{impact.activeMemberCount}</dd>
      </div>
    </dl>
  );
}

function PermissionEditor({ role }: { role: Role }) {
  const session = useClientSession();
  const queryClient = useQueryClient();
  const catalog = useQuery({
    queryKey: ["client", "roles", role.id, "catalog"],
    queryFn: () => clientApi.roleCatalog(role.id),
    enabled: session.can(clientPermissions.rolesPermissionCatalog),
  });
  const [search, setSearch] = useState("");
  const choices = useMemo(
    () =>
      catalog.data?.availableChoices.flatMap((module) =>
        module.features.flatMap((feature) =>
          feature.permissions.map((permission) => ({
            ...permission,
            feature: feature.displayName,
            module: module.code,
          })),
        ),
      ) ?? [],
    [catalog.data],
  );
  const filtered = choices.filter((item) =>
    `${item.name} ${item.code} ${item.feature}`.toLowerCase().includes(search.toLowerCase()),
  );
  const add = useMutation({
    mutationFn: async (code: string) => {
      const impact = await clientApi.roleImpact(role.id, "ADD_PERMISSION", code);
      return clientApi.addRolePermission(
        role.id,
        code,
        catalog.data?.registryVersion ?? "",
        impact.version,
        impact.assignmentCount,
      );
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["client", "roles", role.id] });
      toast.success("Permission ajoutée");
    },
  });
  const remove = useMutation({
    mutationFn: async (code: string) => {
      const impact = await clientApi.roleImpact(role.id, "REMOVE_PERMISSION", code);
      return clientApi.removeRolePermission(role.id, code, impact.version, impact.assignmentCount);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["client", "roles", role.id] });
      toast.success("Permission retirée");
    },
  });
  if (catalog.isLoading) return <LoadingState />;
  if (catalog.isError) return <ErrorState retry={() => void catalog.refetch()} />;
  return (
    <div className="grid gap-6 lg:grid-cols-[0.85fr_1.15fr]">
      <section className="overflow-hidden rounded-xl border bg-card">
        <div className="border-b p-4">
          <h2 className="text-sm font-semibold">Permissions accordées</h2>
        </div>
        {!role.permissionCodes.length ? (
          <EmptyState title="Aucune permission" />
        ) : (
          <div className="divide-y">
            {role.permissionCodes.toSorted().map((code) => (
              <div className="flex items-center justify-between gap-3 p-3" key={code}>
                <code className="break-all text-xs">{code}</code>
                {session.can(clientPermissions.rolesRevoke) ? (
                  <Button aria-label="Retirer" onClick={() => remove.mutate(code)} size="icon-sm" variant="ghost">
                    <TrashIcon />
                  </Button>
                ) : null}
              </div>
            ))}
          </div>
        )}
      </section>
      {session.can(clientPermissions.rolesGrant) && session.can(clientPermissions.rolesPermissionCatalog) ? (
        <section className="overflow-hidden rounded-xl border bg-card">
          <div className="relative border-b p-4">
            <MagnifyingGlassIcon className="absolute start-7 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="ps-9"
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Rechercher une permission…"
              value={search}
            />
          </div>
          <div className="max-h-[34rem] divide-y overflow-y-auto">
            {filtered
              .filter((item) => !role.permissionCodes.includes(item.code))
              .map((item) => (
                <button
                  className="flex w-full items-start justify-between gap-4 p-4 text-start hover:bg-muted/50"
                  key={item.code}
                  onClick={() => add.mutate(item.code)}
                  type="button"
                >
                  <span>
                    <span className="block text-sm font-medium">{item.name}</span>
                    <code className="mt-1 block text-xs text-muted-foreground">{item.code}</code>
                  </span>
                  <PlusIcon className="mt-1 size-4 shrink-0" />
                </button>
              ))}
          </div>
        </section>
      ) : null}
    </div>
  );
}

function RoleDetail({ id }: { id: string }) {
  const [tab, setTab] = useState("summary");
  const role = useQuery({ queryKey: ["client", "roles", id], queryFn: () => clientApi.role(id) });
  if (role.isLoading) return <LoadingState />;
  if (!role.data) return <ErrorState retry={() => void role.refetch()} />;
  const data = role.data;
  return (
    <div className="space-y-7">
      <Button asChild size="sm" variant="ghost">
        <Link to="/app/roles">
          <ArrowLeftIcon className="rtl:rotate-180" />
          Rôles
        </Link>
      </Button>
      <PageHeader
        actions={
          <>
            <RoleDialog role={data} />
            <RoleDialog duplicate role={data} />
            {data.status === "INACTIVE" ? (
              <ImpactDialog action="activate" role={data} />
            ) : data.status === "ACTIVE" ? (
              <ImpactDialog action="deactivate" role={data} />
            ) : null}
          </>
        }
        title={data.name}
      />
      <SectionTabs
        items={[
          { label: "Résumé", value: "summary" },
          { label: `Permissions (${data.permissionCodes.length})`, value: "permissions" },
          { label: "Cycle de vie", value: "lifecycle" },
        ]}
        onValueChange={setTab}
        value={tab}
      />
      {tab === "permissions" ? (
        <PermissionEditor role={data} />
      ) : tab === "lifecycle" ? (
        <section className="max-w-3xl rounded-xl border bg-card p-5">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-sm font-semibold">État du rôle</h2>
              <p className="mt-1 text-xs text-muted-foreground">
                Les changements prévisualisent le nombre de membres affectés.
              </p>
            </div>
            <StatusBadge>{data.status}</StatusBadge>
          </div>
          <div className="mt-5 flex flex-wrap gap-2">
            {data.status !== "ARCHIVED" ? <ImpactDialog action="archive" role={data} /> : null}
            {!data.everAssigned && !data.isSystemRole ? <ImpactDialog action="delete" role={data} /> : null}
          </div>
        </section>
      ) : (
        <section className="max-w-3xl rounded-xl border bg-card p-5">
          <dl className="grid gap-5 sm:grid-cols-2">
            <div>
              <dt className="text-xs text-muted-foreground">Portée</dt>
              <dd className="mt-1 font-medium">{data.templateBoundary === "ACCOUNT" ? "Compte" : "Entreprise"}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Statut</dt>
              <dd className="mt-1">
                <StatusBadge>{data.status}</StatusBadge>
              </dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Révision</dt>
              <dd className="mt-1 font-medium tabular-nums">{data.definitionRevision}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Déjà attribué</dt>
              <dd className="mt-1 font-medium">{data.everAssigned ? "Oui" : "Non"}</dd>
            </div>
          </dl>
          {data.description ? (
            <p className="mt-6 border-t pt-5 text-sm text-muted-foreground">{data.description}</p>
          ) : null}
        </section>
      )}
    </div>
  );
}

export function ClientRolesPage() {
  const { roleId } = useParams();
  const [search, setSearch] = useState("");
  const deferred = useDeferredValue(search);
  const roles = useQuery({ queryKey: ["client", "roles"], queryFn: clientApi.roles });
  const filtered =
    roles.data?.filter((role) =>
      `${role.name} ${role.description ?? ""}`.toLowerCase().includes(deferred.toLowerCase()),
    ) ?? [];
  if (roleId) return <RoleDetail id={roleId} />;
  return (
    <div className="space-y-7">
      <PageHeader actions={<RoleDialog />} title="Rôles" />
      <section className="overflow-hidden rounded-xl border bg-card">
        <div className="relative border-b p-4">
          <MagnifyingGlassIcon className="absolute start-7 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="max-w-md ps-9"
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Rechercher un rôle…"
            value={search}
          />
        </div>
        {roles.isLoading ? (
          <div className="p-5">
            <LoadingState />
          </div>
        ) : roles.isError ? (
          <ErrorState retry={() => void roles.refetch()} />
        ) : !filtered.length ? (
          <EmptyState title="Aucun rôle" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Rôle</TableHead>
                <TableHead>Portée</TableHead>
                <TableHead>Permissions</TableHead>
                <TableHead>Statut</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((role) => (
                <TableRow key={role.id}>
                  <TableCell>
                    <Link className="font-medium hover:underline" to={`/app/roles/${role.id}`}>
                      {role.name}
                    </Link>
                    {role.description ? (
                      <p className="max-w-md truncate text-xs text-muted-foreground">{role.description}</p>
                    ) : null}
                  </TableCell>
                  <TableCell>{role.templateBoundary === "ACCOUNT" ? "Compte" : "Entreprise"}</TableCell>
                  <TableCell className="tabular-nums">{role.permissionCodes.length}</TableCell>
                  <TableCell>
                    <StatusBadge
                      tone={role.status === "ACTIVE" ? "success" : role.status === "ARCHIVED" ? "neutral" : "warning"}
                    >
                      {role.status}
                    </StatusBadge>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </section>
    </div>
  );
}
