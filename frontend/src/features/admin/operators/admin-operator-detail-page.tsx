import { ArrowLeftIcon, PencilSimpleIcon, XIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import { ApiError } from "@/api/http";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { StatusBadge } from "@/components/patterns/status-badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

/** Groups `platform.admin_users.create` under "admin_users" so a long list stays readable. */
function groupByFeature(codes: string[]): [string, string[]][] {
  const groups = new Map<string, string[]>();
  for (const code of codes) {
    const parts = code.split(".");
    const feature = (parts.length >= 3 ? parts[1] : undefined) ?? "autres";
    const action = parts.length >= 3 ? parts.slice(2).join(".") : code;
    groups.set(feature, [...(groups.get(feature) ?? []), action]);
  }
  return [...groups.entries()].sort(([a], [b]) => a.localeCompare(b));
}

function Section({
  title,
  description,
  action,
  children,
}: {
  title: string;
  description?: string;
  action?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <section className="rounded-xl border bg-card p-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h2 className="text-sm font-semibold">{title}</h2>
          {description ? <p className="mt-1 text-xs text-muted-foreground">{description}</p> : null}
        </div>
        {action ? <div className="shrink-0">{action}</div> : null}
      </div>
      <div className="mt-4">{children}</div>
    </section>
  );
}

export function AdminOperatorDetailPage() {
  const { operatorId = "" } = useParams();
  const session = useAdminSession();
  const queryClient = useQueryClient();
  // Held in state rather than read from the mutation, so it survives the refetch that follows.
  // It is never re-fetchable — the backend returns it once and stores only a hash.
  const [temporaryPassword, setTemporaryPassword] = useState<string | null>(null);
  const [editingName, setEditingName] = useState(false);
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");

  const operator = useQuery({
    queryKey: ["admin", "users", operatorId],
    queryFn: () => adminApi.user(operatorId),
    enabled: Boolean(operatorId),
  });

  const canReadPermissions = session.can(adminPermissions.usersReadPermissions);
  const permissions = useQuery({
    queryKey: ["admin", "users", operatorId, "permissions"],
    queryFn: () => adminApi.operatorPermissions(operatorId),
    enabled: Boolean(operatorId) && canReadPermissions,
  });

  const roles = useQuery({
    queryKey: ["admin", "roles", "assignable"],
    queryFn: () => adminApi.roles({ active: true, page: 0, size: 100 }),
    enabled: session.can(adminPermissions.rolesRead) && session.can(adminPermissions.usersAssignRole),
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
    void queryClient.invalidateQueries({ queryKey: ["admin", "overview"] });
  };

  const assign = useMutation({
    mutationFn: (roleId: string) => adminApi.assignUserRole(operatorId, roleId),
    onSuccess: () => {
      refresh();
      toast.success("Rôle attribué");
    },
  });
  const remove = useMutation({
    mutationFn: (roleId: string) => adminApi.removeUserRole(operatorId, roleId),
    onSuccess: () => {
      refresh();
      toast.success("Rôle retiré");
    },
  });
  const toggle = useMutation({
    mutationFn: () => adminApi.toggleUser(operatorId),
    onSuccess: () => {
      refresh();
      toast.success("Statut mis à jour");
    },
  });
  const resend = useMutation({
    mutationFn: () => adminApi.resendOperatorActivation(operatorId),
    onSuccess: () => {
      refresh();
      toast.success("Lien d’activation renvoyé");
    },
  });
  const temporary = useMutation({
    mutationFn: () => adminApi.generateOperatorTemporaryAccess(operatorId),
    onSuccess: (access) => {
      refresh();
      setTemporaryPassword(access.temporaryPassword);
      toast.success("Mot de passe temporaire généré");
    },
  });

  const rename = useMutation({
    mutationFn: (input: { firstName: string; lastName: string }) => adminApi.renameOperator(operatorId, input),
    onSuccess: () => {
      refresh();
      void queryClient.invalidateQueries({ queryKey: ["admin", "users", operatorId] });
      setEditingName(false);
      toast.success("Nom mis à jour");
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : "Modification impossible"),
  });

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

  // AdminMe.id and AdminUserResponseDto.id are both the AdminUser id, so this compares like for
  // like. The backend refuses self-deactivation; the button should say so beforehand.
  const isSelf = session.me?.id === data.id;
  const hasActivated = data.credentialState === "ACTIVE";
  const canResend = session.can(adminPermissions.usersResendActivation);
  const canIssueTemporary = session.can(adminPermissions.usersTemporaryAccess);
  const assignableRoles = (roles.data?.content ?? []).filter(
    (role) =>
      role.availableActions.includes("ASSIGN_TO_OPERATOR") && !data.roles.some((assigned) => assigned.id === role.id),
  );

  return (
    <div className="space-y-7">
      <div>{backLink}</div>

      {editingName ? (
        <Section
          description="Corriger l’orthographe d’un nom. Cette action ne concerne qu’une personne, elle n’a pas de forme groupée."
          title="Nom de l’opérateur"
        >
          <form
            className="flex flex-wrap items-end gap-3"
            onSubmit={(event) => {
              event.preventDefault();
              rename.mutate({ firstName: firstName.trim(), lastName: lastName.trim() });
            }}
          >
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
            <Button disabled={!firstName.trim() || !lastName.trim() || rename.isPending} type="submit">
              {rename.isPending ? "Enregistrement…" : "Enregistrer"}
            </Button>
            <Button onClick={() => setEditingName(false)} type="button" variant="ghost">
              Annuler
            </Button>
          </form>
        </Section>
      ) : null}

      <PageHeader
        actions={
          !editingName && session.can(adminPermissions.usersRename) ? (
            <Button
              onClick={() => {
                setFirstName(data.firstName ?? "");
                setLastName(data.lastName ?? "");
                setEditingName(true);
              }}
              variant="outline"
            >
              <PencilSimpleIcon />
              Modifier le nom
            </Button>
          ) : undefined
        }
        description={
          <span className="flex flex-wrap items-center gap-2">
            <span className="text-sm text-muted-foreground">{data.email}</span>
            <StatusBadge tone={data.isActive ? "success" : "danger"}>{data.isActive ? "Actif" : "Inactif"}</StatusBadge>
            {data.isSuperAdmin ? <StatusBadge tone="info">SuperAdmin</StatusBadge> : null}
            <StatusBadge tone={hasActivated ? "neutral" : "warning"}>
              {hasActivated ? "Accès activé" : "En attente d’activation"}
            </StatusBadge>
          </span>
        }
        title={[data.firstName, data.lastName].filter(Boolean).join(" ") || data.email}
      />

      <div className="grid gap-5 lg:grid-cols-2">
        <Section
          action={
            assignableRoles.length ? (
              <Select onValueChange={(value) => assign.mutate(value)}>
                <SelectTrigger aria-label="Attribuer un rôle" className="w-48">
                  <SelectValue placeholder="Attribuer un rôle" />
                </SelectTrigger>
                <SelectContent>
                  {assignableRoles.map((role) => (
                    <SelectItem key={role.id} value={role.id}>
                      {role.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            ) : undefined
          }
          description="Les rôles inactifs ne peuvent pas être attribués et ne confèrent aucun droit."
          title="Rôles"
        >
          <div className="divide-y rounded-lg border">
            {data.roles.length ? (
              data.roles.map((role) => (
                <div className="flex items-center justify-between gap-3 p-3" key={role.id}>
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{role.name}</p>
                    <p className="text-xs text-muted-foreground">{role.isActive ? "Rôle actif" : "Rôle inactif"}</p>
                  </div>
                  {session.can(adminPermissions.usersRemoveRole) ? (
                    <Button
                      aria-label={`Retirer ${role.name}`}
                      onClick={() => remove.mutate(role.id)}
                      size="icon-sm"
                      variant="ghost"
                    >
                      <XIcon />
                    </Button>
                  ) : null}
                </div>
              ))
            ) : (
              <p className="p-4 text-sm text-muted-foreground">Aucun rôle attribué.</p>
            )}
          </div>
        </Section>

        <Section
          description={
            data.isSuperAdmin
              ? "Un SuperAdmin détient toute autorité de la plateforme, indépendamment des rôles."
              : "Résolu depuis les rôles actifs de l’opérateur."
          }
          title="Permissions effectives"
        >
          {!canReadPermissions ? (
            <p className="text-sm text-muted-foreground">Vous n’êtes pas autorisé à consulter ce détail.</p>
          ) : permissions.isLoading ? (
            <LoadingState rows={3} />
          ) : permissions.isError ? (
            <ErrorState retry={() => void permissions.refetch()} />
          ) : !permissions.data?.length ? (
            <p className="rounded-lg border p-4 text-sm text-muted-foreground">
              Aucune permission. Cet opérateur peut se connecter mais n’a accès à rien.
            </p>
          ) : (
            <div className="space-y-4">
              <p className="text-xs text-muted-foreground">{permissions.data.length} permissions</p>
              {groupByFeature(permissions.data).map(([feature, actions]) => (
                <div key={feature}>
                  <p className="text-xs font-semibold">{feature}</p>
                  <div className="mt-1.5 flex flex-wrap gap-1.5">
                    {actions.map((action) => (
                      <span
                        className="rounded-md border bg-muted/50 px-2 py-0.5 font-mono text-xs"
                        key={`${feature}.${action}`}
                      >
                        {action}
                      </span>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </Section>
      </div>

      {canResend || canIssueTemporary ? (
        <Section
          description={
            hasActivated
              ? "Cet opérateur a défini son propre mot de passe."
              : "Le lien envoyé par email expire après 24 heures."
          }
          title="Identifiants"
        >
          <div className="flex flex-wrap gap-2">
            {canResend ? (
              <Button disabled={hasActivated || resend.isPending} onClick={() => resend.mutate()} variant="outline">
                {resend.isPending ? "Envoi…" : "Renvoyer le lien d’activation"}
              </Button>
            ) : null}
            {canIssueTemporary ? (
              <Button disabled={temporary.isPending} onClick={() => temporary.mutate()} variant="ghost">
                {temporary.isPending ? "Génération…" : "Générer un mot de passe temporaire"}
              </Button>
            ) : null}
          </div>
          {temporaryPassword ? (
            <div className="mt-4 rounded-lg border border-warning/30 bg-warning-subtle p-3">
              <p className="text-xs font-medium">
                Transmettez ce mot de passe directement à l’opérateur. Il ne sera plus jamais affiché.
              </p>
              <code className="mt-2 block font-mono text-sm break-all">{temporaryPassword}</code>
            </div>
          ) : null}
        </Section>
      ) : null}

      {!data.isSuperAdmin && session.can(adminPermissions.usersMutate) ? (
        <Section
          description={
            isSelf
              ? "Vous ne pouvez pas désactiver votre propre accès."
              : "La désactivation empêche immédiatement toute nouvelle session administrateur."
          }
          title="Statut d’accès"
        >
          <Button
            disabled={toggle.isPending || (isSelf && data.isActive)}
            onClick={() => toggle.mutate()}
            variant={data.isActive ? "destructive" : "outline"}
          >
            {data.isActive ? "Désactiver l’accès" : "Réactiver l’accès"}
          </Button>
        </Section>
      ) : null}
    </div>
  );
}
