import {
  ArrowLeftIcon,
  ArrowRightIcon,
  EnvelopeSimpleIcon,
  KeyIcon,
  MagnifyingGlassIcon,
  PencilSimpleIcon,
  XIcon,
} from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { AdminPermission, AdminUser } from "@/api/contracts";
import { ApiError } from "@/api/http";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { RowAction } from "@/components/patterns/row-action";
import { SectionTabs } from "@/components/patterns/section-tabs";
import { StatusBadge } from "@/components/patterns/status-badge";
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
import { OperatorEmailDialog, OperatorNameDialog } from "@/features/admin/operators/admin-operator-identity-dialogs";
import { OperatorRolePicker } from "@/features/admin/operators/admin-operator-role-picker";
import { announceEmailDelivery } from "@/features/admin/operators/operator-email-delivery";
import { permissionActionLabel, permissionResourceLabel } from "@/features/admin/roles/admin-permission-editor";

type OperatorTab = "identity" | "roles" | "permissions" | "access";

const credentialLabels: Record<string, string> = {
  ACTIVE: "Mot de passe défini",
  EMAIL_ACTIVATION_PENDING: "Activation par email en attente",
  EMAIL_RESET_PENDING: "Réinitialisation en attente",
  TEMPORARY_PASSWORD: "Mot de passe temporaire émis",
  INITIAL_PASSWORD_CHANGE: "Changement du mot de passe requis",
};

function displayName(operator: AdminUser) {
  return [operator.firstName, operator.lastName].filter(Boolean).join(" ") || operator.email;
}

function DetailRow({ label, value, action }: { label: string; value: React.ReactNode; action?: React.ReactNode }) {
  return (
    <div className="grid min-h-16 gap-2 py-3 sm:grid-cols-[10rem_minmax(0,1fr)_auto] sm:items-center sm:gap-5">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="min-w-0 text-sm font-medium">{value}</dd>
      {action ? <dd className="sm:justify-self-end">{action}</dd> : null}
    </div>
  );
}

function SectionHeading({ title, action }: { title: string; action?: React.ReactNode }) {
  return (
    <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
      <h2 className="text-base font-semibold">{title}</h2>
      {action}
    </div>
  );
}

/** Grouped by catalogue resource, exactly as the role permission editor groups them. */
function groupedPermissions(permissions: AdminPermission[]) {
  const groups = new Map<string, AdminPermission[]>();
  for (const permission of permissions) {
    groups.set(permission.resource, [...(groups.get(permission.resource) ?? []), permission]);
  }
  return [...groups.entries()].toSorted(([a], [b]) => a.localeCompare(b, "fr"));
}

export function AdminOperatorDetailPage() {
  const { operatorId = "" } = useParams();
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<OperatorTab>("identity");
  const [permissionSearch, setPermissionSearch] = useState("");
  const [temporaryPassword, setTemporaryPassword] = useState<string | null>(null);
  const [roleToRemove, setRoleToRemove] = useState<AdminUser["roles"][number] | null>(null);

  const operator = useQuery({
    queryKey: ["admin", "users", operatorId],
    queryFn: () => adminApi.user(operatorId),
    enabled: Boolean(operatorId),
  });

  const canReadPermissions = session.can(adminPermissions.usersReadPermissions);
  const permissions = useQuery({
    queryKey: ["admin", "users", operatorId, "permissions"],
    queryFn: () => adminApi.operatorPermissions(operatorId),
    enabled: Boolean(operatorId) && tab === "permissions" && canReadPermissions,
  });

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["admin", "users"] }),
      queryClient.invalidateQueries({ queryKey: ["admin", "overview"] }),
    ]);
  };

  const onMutationError = (reason: unknown) =>
    toast.error(reason instanceof ApiError ? reason.message : "Action impossible");

  const toggle = useMutation({
    mutationFn: () => adminApi.toggleUser(operatorId),
    onSuccess: async () => {
      await refresh();
      toast.success("Statut mis à jour");
    },
    onError: onMutationError,
  });
  const resendActivation = useMutation({
    mutationFn: () => adminApi.resendOperatorActivation(operatorId),
    onSuccess: async (access) => {
      await refresh();
      announceEmailDelivery(access.emailDelivery);
    },
    onError: onMutationError,
  });
  const removeRole = useMutation({
    mutationFn: (roleId: string) => adminApi.removeUserRole(operatorId, roleId),
    onSuccess: async () => {
      await refresh();
      setRoleToRemove(null);
      toast.success("Rôle retiré");
    },
    onError: onMutationError,
  });
  const temporary = useMutation({
    mutationFn: () => adminApi.generateOperatorTemporaryAccess(operatorId),
    onSuccess: async (access) => {
      await refresh();
      setTemporaryPassword(access.temporaryPassword);
      toast.success("Mot de passe temporaire généré");
    },
    onError: onMutationError,
  });

  const filteredPermissionGroups = useMemo(() => {
    const query = permissionSearch.trim().toLocaleLowerCase("fr");
    const visible = (permissions.data ?? []).filter(
      (permission) =>
        !query ||
        `${permissionActionLabel(permission.action)} ${permission.name} ${permission.description} ${permission.code}`
          .toLocaleLowerCase("fr")
          .includes(query),
    );
    return groupedPermissions(visible);
  }, [permissionSearch, permissions.data]);

  const backLink = (
    <Button asChild variant="ghost">
      <Link to="/admin/operators">
        <ArrowLeftIcon />
        Opérateurs
      </Link>
    </Button>
  );

  if (operator.isLoading) {
    return (
      <div className="space-y-7">
        {backLink}
        <LoadingState />
      </div>
    );
  }

  const data = operator.data;
  if (operator.isError || !data) {
    return (
      <div className="space-y-7">
        {backLink}
        <ErrorState retry={() => void operator.refetch()} />
      </div>
    );
  }

  const isSelf = session.me?.id === data.id;
  const activationPending = data.credentialState === "EMAIL_ACTIVATION_PENDING";
  const canChangeName = session.can(adminPermissions.usersRename);
  const canChangeEmail = session.can(adminPermissions.usersChangeEmail);
  const selfAssignmentProtected = isSelf && !session.me?.isSuperAdmin;
  const canAssignRoles =
    session.can(adminPermissions.usersAssignRole) &&
    session.can(adminPermissions.rolesRead) &&
    !selfAssignmentProtected;
  const canRemoveRoles = session.can(adminPermissions.usersRemoveRole) && !selfAssignmentProtected;

  const resendActivationBlockedBy = !session.can(adminPermissions.usersResendActivation)
    ? "Vous n’êtes pas autorisé à renvoyer l’activation."
    : !activationPending
      ? "Cet opérateur a déjà activé son accès."
      : resendActivation.isPending
        ? "Envoi en cours…"
        : null;
  const temporaryBlockedBy = !session.can(adminPermissions.usersTemporaryAccess)
    ? "Vous n’êtes pas autorisé à générer un accès temporaire."
    : temporary.isPending
      ? "Génération en cours…"
      : null;
  const toggleBlockedBy = !session.can(adminPermissions.usersMutate)
    ? "Vous n’êtes pas autorisé à modifier ce statut."
    : data.isSuperAdmin
      ? "Le statut d’un SuperAdmin ne se modifie pas ici."
      : isSelf && data.isActive
        ? "Vous ne pouvez pas désactiver votre propre accès."
        : toggle.isPending
          ? "Mise à jour en cours…"
          : null;
  const saveName = async (input: { firstName: string; lastName: string }) => {
    await adminApi.renameOperator(operatorId, input);
    await refresh();
    toast.success("Nom mis à jour");
  };

  const saveEmail = async (email: string) => {
    await adminApi.changeOperatorEmail(operatorId, email);
    await refresh();
    if (isSelf) await queryClient.invalidateQueries({ queryKey: ["admin", "me"] });
    toast.success("Adresse modifiée", {
      description: "La nouvelle adresse doit maintenant être vérifiée.",
    });
  };

  const saveRoles = async (selectedIds: string[]) => {
    try {
      await adminApi.replaceUserRoles(operatorId, selectedIds);
    } finally {
      await refresh();
    }
    toast.success("Rôles mis à jour");
  };

  return (
    <div className="space-y-7">
      <div>{backLink}</div>

      <PageHeader
        description={
          <span className="flex flex-wrap items-center gap-2">
            <span className="text-sm text-muted-foreground" dir="ltr">
              {data.email}
            </span>
            <StatusBadge tone={data.isActive ? "success" : "danger"}>
              {data.isActive ? "Accès actif" : "Accès désactivé"}
            </StatusBadge>
            {data.isSuperAdmin ? <StatusBadge tone="info">SuperAdmin</StatusBadge> : null}
          </span>
        }
        title={displayName(data)}
      />

      <SectionTabs
        ariaLabel="Gestion de l’opérateur"
        items={[
          { value: "identity", label: "Identité" },
          { value: "roles", label: "Rôles", count: data.roles.length },
          { value: "permissions", label: "Permissions" },
          { value: "access", label: "Accès" },
        ]}
        onValueChange={(value) => setTab(value as OperatorTab)}
        value={tab}
      />

      {tab === "identity" ? (
        <div className="max-w-4xl">
          <SectionHeading title="Identité de l’opérateur" />
          <dl className="divide-y border-y">
            <DetailRow
              action={
                canChangeName ? (
                  <OperatorNameDialog onSave={saveName} operator={data}>
                    <Button size="sm" variant="ghost">
                      <PencilSimpleIcon /> Modifier
                    </Button>
                  </OperatorNameDialog>
                ) : undefined
              }
              label="Nom complet"
              value={displayName(data)}
            />
            <DetailRow
              action={
                canChangeEmail ? (
                  <OperatorEmailDialog onSave={saveEmail} operator={data}>
                    <Button size="sm" variant="ghost">
                      <PencilSimpleIcon /> Changer
                    </Button>
                  </OperatorEmailDialog>
                ) : undefined
              }
              label="Email de connexion"
              value={
                <span className="flex flex-wrap items-center gap-2">
                  <span className="break-all" dir="ltr">
                    {data.email}
                  </span>
                  <StatusBadge tone={data.emailVerified ? "success" : "warning"}>
                    {data.emailVerified ? "Vérifié" : "Non vérifié"}
                  </StatusBadge>
                </span>
              }
            />
            <DetailRow label="Niveau" value={data.isSuperAdmin ? "SuperAdmin" : "Opérateur"} />
          </dl>
        </div>
      ) : null}

      {tab === "roles" ? (
        <div className="max-w-5xl">
          <SectionHeading
            action={
              canAssignRoles || canRemoveRoles ? (
                <OperatorRolePicker
                  assignedRoles={data.roles}
                  canAssign={canAssignRoles}
                  canRemove={canRemoveRoles}
                  onSave={saveRoles}
                >
                  <Button>Gérer les rôles</Button>
                </OperatorRolePicker>
              ) : undefined
            }
            title="Rôles attribués"
          />
          {data.roles.length ? (
            <div className="divide-y border-y">
              {data.roles.map((role) => {
                const removeBlockedBy = selfAssignmentProtected
                  ? "Un opérateur non-SuperAdmin ne peut pas modifier ses propres rôles"
                  : !session.can(adminPermissions.usersRemoveRole)
                    ? "Vous n’êtes pas autorisé à retirer un rôle"
                    : removeRole.isPending
                      ? "Retrait en cours…"
                      : null;
                const canOpenRole = session.can(adminPermissions.rolesReadDetail);
                return (
                  <div className="flex min-h-16 items-center justify-between gap-4 py-3" key={role.id}>
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium">{role.name}</p>
                      {role.description ? (
                        <p className="mt-0.5 line-clamp-2 text-xs text-muted-foreground">{role.description}</p>
                      ) : null}
                    </div>
                    <div className="flex shrink-0 items-center gap-3">
                      <StatusBadge tone={role.isActive ? "success" : "neutral"}>
                        {role.isActive ? "Actif" : "Inactif"}
                      </StatusBadge>
                      <span className="flex items-center gap-0.5">
                        <RowAction
                          disabled={removeBlockedBy !== null}
                          disabledLabel={removeBlockedBy ?? undefined}
                          icon={<XIcon />}
                          label="Retirer le rôle"
                          onClick={() => setRoleToRemove(role)}
                          tone="danger"
                        />
                        <RowAction
                          disabled={!canOpenRole}
                          disabledLabel="Vous n’êtes pas autorisé à consulter ce rôle"
                          icon={<ArrowRightIcon />}
                          label="Ouvrir la fiche du rôle"
                          to={canOpenRole ? `/admin/roles/${role.id}` : undefined}
                        />
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <EmptyState description="Attribuez un rôle pour ouvrir des capacités administratives." title="Aucun rôle" />
          )}
        </div>
      ) : null}

      {tab === "permissions" ? (
        <div className="max-w-5xl">
          <SectionHeading title="Permissions effectives" />
          {!canReadPermissions ? (
            <p className="text-sm text-muted-foreground">Vous n’êtes pas autorisé à consulter ce détail.</p>
          ) : permissions.isLoading ? (
            <LoadingState rows={5} />
          ) : permissions.isError ? (
            <ErrorState retry={() => void permissions.refetch()} />
          ) : !permissions.data?.length ? (
            <EmptyState
              description="Cet opérateur peut se connecter mais n’a accès à aucune opération."
              title="Aucune permission"
            />
          ) : (
            <>
              <div className="relative mb-5 max-w-md">
                <MagnifyingGlassIcon className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
                <Input
                  aria-label="Rechercher une permission"
                  autoComplete="off"
                  className="ps-9"
                  name="permission-search"
                  onChange={(event) => setPermissionSearch(event.target.value)}
                  placeholder={`Rechercher parmi ${permissions.data.length} permissions…`}
                  spellCheck={false}
                  value={permissionSearch}
                />
              </div>
              {filteredPermissionGroups.length ? (
                <div className="divide-y border-y">
                  {filteredPermissionGroups.map(([resource, entries]) => (
                    <div className="grid gap-3 py-4 sm:grid-cols-[11rem_minmax(0,1fr)]" key={resource}>
                      <h3 className="text-sm font-semibold">{permissionResourceLabel(resource)}</h3>
                      <div className="grid gap-x-8 gap-y-3 lg:grid-cols-2">
                        {entries.map((permission) => (
                          <div className="min-w-0" key={permission.id} title={permission.code}>
                            <p className="text-sm font-medium">{permissionActionLabel(permission.action)}</p>
                            {permission.description ? (
                              <p className="mt-0.5 text-xs leading-5 text-muted-foreground">{permission.description}</p>
                            ) : null}
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="border-y py-8 text-center text-sm text-muted-foreground">Aucun résultat.</p>
              )}
            </>
          )}
        </div>
      ) : null}

      {tab === "access" ? (
        <div className="max-w-4xl space-y-10">
          <section>
            <SectionHeading title="État des identifiants" />
            <dl className="divide-y border-y">
              <DetailRow label="Accès plateforme" value={data.isActive ? "Autorisé" : "Désactivé"} />
              <DetailRow label="Identifiants" value={credentialLabels[data.credentialState] ?? data.credentialState} />
              <DetailRow
                label="Récupération autonome"
                value={data.emailVerified ? "Disponible par email" : "Bloquée jusqu’à la vérification de l’email"}
              />
            </dl>
          </section>

          <section className="border-t pt-6">
            <SectionHeading title="Actions sur les identifiants" />
            {/* Every action stays on screen; when one does not apply it is disabled and the row
                text says why, so nobody has to guess whether it is missing or forbidden. */}
            <dl className="divide-y border-y">
              <DetailRow
                action={
                  <Button
                    disabled={resendActivationBlockedBy !== null}
                    onClick={() => resendActivation.mutate()}
                    size="sm"
                    variant="outline"
                  >
                    <EnvelopeSimpleIcon />
                    {resendActivation.isPending ? "Envoi…" : "Renvoyer l’activation"}
                  </Button>
                }
                label="Lien d’activation"
                value={
                  <span className="font-normal text-muted-foreground">
                    {resendActivationBlockedBy ?? "Renvoie un nouveau lien ; le précédent cesse de fonctionner."}
                  </span>
                }
              />
              <DetailRow
                action={
                  <Button
                    disabled={temporaryBlockedBy !== null}
                    onClick={() => temporary.mutate()}
                    size="sm"
                    variant="outline"
                  >
                    <KeyIcon />
                    {temporary.isPending ? "Génération…" : "Générer un accès temporaire"}
                  </Button>
                }
                label="Accès temporaire"
                value={
                  <span className="font-normal text-muted-foreground">
                    {temporaryBlockedBy ?? "Émet un mot de passe à transmettre directement, affiché une seule fois."}
                  </span>
                }
              />
            </dl>
            {temporaryPassword ? (
              <div className="mt-5 border-y border-warning/30 bg-warning-subtle/40 py-4">
                <p className="text-xs font-medium">
                  Transmettez ce mot de passe directement. Il ne sera plus jamais affiché.
                </p>
                <code className="mt-2 block select-all break-all font-mono text-sm">{temporaryPassword}</code>
              </div>
            ) : null}
          </section>

          <section className="border-t pt-6">
            <SectionHeading title="Statut d’accès" />
            <p className="mb-4 text-sm text-muted-foreground">
              {toggleBlockedBy ?? "La désactivation empêche immédiatement toute nouvelle session administrateur."}
            </p>
            <Button
              disabled={toggleBlockedBy !== null}
              onClick={() => toggle.mutate()}
              variant={data.isActive ? "destructive" : "outline"}
            >
              {data.isActive ? "Désactiver l’accès" : "Réactiver l’accès"}
            </Button>
          </section>
        </div>
      ) : null}

      <Dialog onOpenChange={(open) => !open && setRoleToRemove(null)} open={roleToRemove !== null}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Retirer le rôle</DialogTitle>
            <DialogDescription>
              Retirer « {roleToRemove?.name} » de {displayName(data)} peut supprimer immédiatement certaines capacités.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button onClick={() => setRoleToRemove(null)} type="button" variant="outline">
              Annuler
            </Button>
            <Button
              disabled={!roleToRemove || removeRole.isPending}
              onClick={() => roleToRemove && removeRole.mutate(roleToRemove.id)}
              type="button"
              variant="destructive"
            >
              {removeRole.isPending ? "Retrait…" : "Retirer le rôle"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
