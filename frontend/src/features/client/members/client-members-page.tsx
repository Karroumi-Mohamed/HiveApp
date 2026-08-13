import {
  ArrowLeftIcon,
  CopyIcon,
  KeyIcon,
  MagnifyingGlassIcon,
  PlusIcon,
  ShieldCheckIcon,
  UserMinusIcon,
} from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type FormEvent, useDeferredValue, useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { toast } from "sonner";
import { clientApi } from "@/api/client-api";
import type { Member, MemberAccess, MemberCreation, MemberRoleAssignment } from "@/api/contracts";
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
import {
  isAssignmentHeld,
  isRoleAssignableTo,
  reconcileRoleSelection,
} from "@/features/client/members/role-assignment-rules";

function AccessMaterial({ access, onClose }: { access: MemberCreation | MemberAccess; onClose?: () => void }) {
  const password = access.temporaryPassword;
  return (
    <div className="space-y-5">
      <div>
        <StatusBadge tone={password ? "warning" : "success"}>{access.credentialState}</StatusBadge>
        <p className="mt-3 text-sm text-muted-foreground">
          {password
            ? "Copiez ce mot de passe maintenant. Il ne sera plus affiché après fermeture."
            : "Les instructions ont été envoyées par email lorsque la livraison est disponible."}
        </p>
      </div>
      {password ? (
        <div className="flex items-center gap-2 rounded-lg border bg-muted/40 p-3">
          <code className="min-w-0 flex-1 break-all font-mono text-sm">{password}</code>
          <Button
            aria-label="Copier"
            onClick={() => void navigator.clipboard.writeText(password).then(() => toast.success("Mot de passe copié"))}
            size="icon-sm"
            variant="outline"
          >
            <CopyIcon />
          </Button>
        </div>
      ) : null}
      {onClose ? (
        <div className="flex justify-end">
          <Button onClick={onClose}>Terminer</Button>
        </div>
      ) : null}
    </div>
  );
}

function CreateMemberDialog() {
  const session = useClientSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [result, setResult] = useState<MemberCreation | null>(null);
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [employeeNumber, setEmployeeNumber] = useState("");
  const create = useMutation({
    mutationFn: () =>
      clientApi.createMember({
        username,
        email: email || null,
        firstName,
        lastName,
        displayName: displayName || null,
        phone: null,
        employeeNumber: employeeNumber || null,
        initialRoles: [],
      }),
    onSuccess: (data) => {
      setResult(data);
      void queryClient.invalidateQueries({ queryKey: ["client", "members"] });
      toast.success("Membre créé");
    },
  });
  const close = () => {
    setOpen(false);
    setResult(null);
    setUsername("");
    setEmail("");
    setFirstName("");
    setLastName("");
    setDisplayName("");
    setEmployeeNumber("");
  };
  if (!session.can(clientPermissions.membersCreate)) return null;
  return (
    <Dialog onOpenChange={(value) => (value ? setOpen(true) : close())} open={open}>
      <DialogTrigger asChild>
        <Button>
          <PlusIcon />
          Créer un membre
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{result ? "Accès initial" : "Nouveau membre"}</DialogTitle>
          <DialogDescription>
            {result
              ? "Le membre devra choisir son mot de passe définitif à la première connexion."
              : "L’email est facultatif. Un identifiant unique est toujours requis."}
          </DialogDescription>
        </DialogHeader>
        {result ? (
          <AccessMaterial access={result} onClose={close} />
        ) : (
          <form
            className="space-y-4"
            onSubmit={(event: FormEvent) => {
              event.preventDefault();
              create.mutate();
            }}
          >
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="member-first">Prénom</Label>
                <Input
                  id="member-first"
                  onChange={(event) => setFirstName(event.target.value)}
                  required
                  value={firstName}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="member-last">Nom</Label>
                <Input
                  id="member-last"
                  onChange={(event) => setLastName(event.target.value)}
                  required
                  value={lastName}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="member-user">Identifiant</Label>
                <Input
                  id="member-user"
                  onChange={(event) => setUsername(event.target.value)}
                  pattern="[A-Za-z0-9._-]+"
                  required
                  value={username}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="member-email">Email</Label>
                <Input
                  id="member-email"
                  onChange={(event) => setEmail(event.target.value)}
                  type="email"
                  value={email}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="member-display">Nom affiché</Label>
                <Input
                  id="member-display"
                  onChange={(event) => setDisplayName(event.target.value)}
                  value={displayName}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="member-number">Numéro employé</Label>
                <Input
                  id="member-number"
                  onChange={(event) => setEmployeeNumber(event.target.value)}
                  value={employeeNumber}
                />
              </div>
            </div>
            <div className="flex justify-end">
              <Button disabled={create.isPending} type="submit">
                {create.isPending ? "Création…" : "Créer"}
              </Button>
            </div>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}

/**
 * A role may be held more than once by the same member, at different scopes: the backend's
 * identity for an assignment is role + scope + company, not the role alone. Excluding a role
 * outright once it appears anywhere made a legitimate second assignment — the same role at a
 * company, having already been granted account-wide — impossible to express.
 */
function RoleAssignment({ memberId, assignments }: { memberId: string; assignments: MemberRoleAssignment[] }) {
  const session = useClientSession();
  const queryClient = useQueryClient();
  const roles = useQuery({
    queryKey: ["client", "roles"],
    queryFn: clientApi.roles,
    enabled: session.can(clientPermissions.rolesRead),
  });
  const [roleId, setRoleId] = useState("");
  const [scope, setScope] = useState("ACCOUNT");
  const [companyId, setCompanyId] = useState("");
  const assign = useMutation({
    mutationFn: () =>
      clientApi.assignMemberRole(memberId, { roleId, scope, companyId: scope === "COMPANY" ? companyId : null }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["client", "members", memberId] });
      setRoleId("");
      toast.success("Rôle attribué");
    },
  });
  // Only the exact tuple already held is excluded, and it is recomputed whenever the scope or
  // company changes — switching from Compte to an Entreprise re-offers a role that is only
  // assigned account-wide.
  const target = { scope, companyId: companyId || null };
  const availableRoles = (roles.data ?? []).filter(
    (role) =>
      role.status === "ACTIVE" && isRoleAssignableTo(role, target) && !isAssignmentHeld(assignments, role.id, target),
  );
  // Changing the scope or company can invalidate a role already picked — it may be bound to a
  // different company, or the new tuple may already be held. The choice is dropped rather than
  // silently submitted against a target it does not fit.
  const reconciledRoleId = reconcileRoleSelection(
    roleId,
    availableRoles.map((role) => role.id),
  );
  const selectionIsValid = Boolean(roleId) && reconciledRoleId === roleId;

  // Roles and assignments can also change after a refetch, not only through these form controls.
  // Keep the stored choice aligned so returning to an earlier scope cannot resurrect an invalid
  // selection that merely looked empty.
  useEffect(() => {
    if (roleId !== reconciledRoleId) setRoleId(reconciledRoleId);
  }, [reconciledRoleId, roleId]);

  if (!session.can(clientPermissions.membersAssignRole) || !session.can(clientPermissions.rolesRead)) return null;
  return (
    <form
      className="grid gap-3 rounded-lg border p-4 md:grid-cols-[1fr_10rem_1fr_auto]"
      onSubmit={(event) => {
        event.preventDefault();
        if (!selectionIsValid || (scope === "COMPANY" && !companyId)) return;
        assign.mutate();
      }}
    >
      <Select onValueChange={setRoleId} value={reconciledRoleId}>
        <SelectTrigger aria-label="Rôle">
          <SelectValue placeholder="Rôle actif" />
        </SelectTrigger>
        <SelectContent>
          {availableRoles.map((role) => (
            <SelectItem key={role.id} value={role.id}>
              {role.name}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <Select onValueChange={setScope} value={scope}>
        <SelectTrigger aria-label="Portée">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="ACCOUNT">Compte</SelectItem>
          <SelectItem value="COMPANY">Entreprise</SelectItem>
        </SelectContent>
      </Select>
      {scope === "COMPANY" ? (
        <Select onValueChange={setCompanyId} value={companyId}>
          <SelectTrigger aria-label="Entreprise">
            <SelectValue placeholder="Entreprise" />
          </SelectTrigger>
          <SelectContent>
            {session.companies
              .filter((company) => company.isActive)
              .map((company) => (
                <SelectItem key={company.id} value={company.id}>
                  {company.name}
                </SelectItem>
              ))}
          </SelectContent>
        </Select>
      ) : (
        <div />
      )}
      <Button
        disabled={!roleId || !selectionIsValid || (scope === "COMPANY" && !companyId) || assign.isPending}
        type="submit"
      >
        Attribuer
      </Button>
    </form>
  );
}

function AuthorizationPanel({ memberId }: { memberId: string }) {
  const queryClient = useQueryClient();
  const session = useClientSession();
  const authorization = useQuery({
    queryKey: ["client", "members", memberId, "authorization"],
    queryFn: () => clientApi.memberAuthorization(memberId),
  });
  const catalog = useQuery({
    queryKey: ["client", "roles", "catalog"],
    queryFn: () => clientApi.roleCatalog(),
    enabled:
      session.can(clientPermissions.membersGrantPermission) && session.can(clientPermissions.rolesPermissionCatalog),
  });
  const [permission, setPermission] = useState("");
  const [decision, setDecision] = useState("DENY");
  const [scope, setScope] = useState("ACCOUNT");
  const [companyId, setCompanyId] = useState("");
  const [reason, setReason] = useState("");
  const removeRole = useMutation({
    mutationFn: ({
      roleId,
      roleScope,
      roleCompanyId,
    }: {
      roleId: string;
      roleScope: string;
      roleCompanyId: string | null;
    }) => clientApi.removeMemberRole(memberId, roleId, roleScope, roleCompanyId),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["client", "members", memberId] }),
  });
  const grant = useMutation({
    mutationFn: () =>
      clientApi.grantMemberOverride(memberId, {
        permissionCode: permission,
        scope,
        companyId: scope === "COMPANY" ? companyId : null,
        decision,
        reason,
        expiresAt: null,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["client", "members", memberId] });
      setPermission("");
      setReason("");
      toast.success("Exception ajoutée");
    },
  });
  const revoke = useMutation({
    mutationFn: ({
      code,
      itemScope,
      itemCompanyId,
    }: {
      code: string;
      itemScope: string;
      itemCompanyId: string | null;
    }) => clientApi.revokeMemberOverride(memberId, code, itemScope, itemCompanyId),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["client", "members", memberId] }),
  });
  if (authorization.isLoading) return <LoadingState />;
  if (!authorization.data || authorization.isError) return <ErrorState retry={() => void authorization.refetch()} />;
  const choices =
    catalog.data?.availableChoices.flatMap((module) => module.features.flatMap((feature) => feature.permissions)) ?? [];
  return (
    <div className="space-y-7">
      <section className="space-y-4">
        <div>
          <h2 className="text-sm font-semibold">Rôles attribués</h2>
          <p className="mt-1 text-xs text-muted-foreground">La portée indique où le rôle produit ses effets.</p>
        </div>
        <RoleAssignment assignments={authorization.data.roles} memberId={memberId} />
        {!authorization.data.roles.length ? (
          <EmptyState title="Aucun rôle" />
        ) : (
          <div className="divide-y rounded-xl border bg-card">
            {authorization.data.roles.map((role) => (
              <div className="flex items-center justify-between gap-4 p-4" key={role.assignmentId}>
                <div>
                  <p className="text-sm font-medium">{role.roleName}</p>
                  <p className="text-xs text-muted-foreground">
                    {role.scope === "ACCOUNT" ? "Tout le compte" : role.companyName}
                  </p>
                </div>
                {session.can(clientPermissions.membersRemoveRole) ? (
                  <Button
                    aria-label="Retirer le rôle"
                    onClick={() =>
                      removeRole.mutate({ roleId: role.roleId, roleScope: role.scope, roleCompanyId: role.companyId })
                    }
                    size="icon-sm"
                    variant="ghost"
                  >
                    <UserMinusIcon />
                  </Button>
                ) : null}
              </div>
            ))}
          </div>
        )}
      </section>
      <section className="space-y-4">
        <div>
          <h2 className="text-sm font-semibold">Exceptions directes</h2>
          <p className="mt-1 text-xs text-muted-foreground">
            Un refus direct prévaut sur les rôles. Utilisez une justification auditable.
          </p>
        </div>
        {session.can(clientPermissions.membersGrantPermission) &&
        session.can(clientPermissions.rolesPermissionCatalog) ? (
          <form
            className="grid gap-3 rounded-xl border p-4 lg:grid-cols-[1.4fr_8rem_8rem_1fr]"
            onSubmit={(event) => {
              event.preventDefault();
              grant.mutate();
            }}
          >
            <Select onValueChange={setPermission} value={permission}>
              <SelectTrigger aria-label="Permission">
                <SelectValue placeholder="Permission" />
              </SelectTrigger>
              <SelectContent>
                {choices.map((item) => (
                  <SelectItem key={item.code} value={item.code}>
                    {item.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select onValueChange={setDecision} value={decision}>
              <SelectTrigger aria-label="Décision">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="DENY">Refuser</SelectItem>
                <SelectItem value="GRANT">Accorder</SelectItem>
              </SelectContent>
            </Select>
            <Select onValueChange={setScope} value={scope}>
              <SelectTrigger aria-label="Portée">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ACCOUNT">Compte</SelectItem>
                <SelectItem value="COMPANY">Entreprise</SelectItem>
              </SelectContent>
            </Select>
            {scope === "COMPANY" ? (
              <Select onValueChange={setCompanyId} value={companyId}>
                <SelectTrigger aria-label="Entreprise">
                  <SelectValue placeholder="Entreprise" />
                </SelectTrigger>
                <SelectContent>
                  {session.companies.map((company) => (
                    <SelectItem key={company.id} value={company.id}>
                      {company.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            ) : (
              <Input
                aria-label="Justification"
                onChange={(event) => setReason(event.target.value)}
                placeholder="Justification"
                value={reason}
              />
            )}{" "}
            {scope === "COMPANY" ? (
              <Input
                aria-label="Justification"
                className="lg:col-span-3"
                onChange={(event) => setReason(event.target.value)}
                placeholder="Justification"
                value={reason}
              />
            ) : null}
            <Button
              className="lg:col-start-4"
              disabled={!permission || !reason.trim() || grant.isPending}
              type="submit"
            >
              <ShieldCheckIcon />
              Ajouter
            </Button>
          </form>
        ) : null}
        {authorization.data.overrides.length ? (
          <div className="divide-y rounded-xl border bg-card">
            {authorization.data.overrides.map((override) => (
              <div className="flex items-center justify-between gap-4 p-4" key={override.id}>
                <div>
                  <div className="flex items-center gap-2">
                    <code className="text-xs">{override.permissionCode}</code>
                    <StatusBadge tone={override.decision === "DENY" ? "danger" : "success"}>
                      {override.decision === "DENY" ? "Refus" : "Accord"}
                    </StatusBadge>
                  </div>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {override.reason} ·{" "}
                    {override.scope === "ACCOUNT"
                      ? "Compte"
                      : session.companies.find((company) => company.id === override.companyId)?.name}
                  </p>
                </div>
                {session.can(clientPermissions.membersRevokePermission) ? (
                  <Button
                    aria-label="Supprimer l’exception"
                    onClick={() =>
                      revoke.mutate({
                        code: override.permissionCode,
                        itemScope: override.scope,
                        itemCompanyId: override.companyId,
                      })
                    }
                    size="icon-sm"
                    variant="ghost"
                  >
                    <UserMinusIcon />
                  </Button>
                ) : null}
              </div>
            ))}
          </div>
        ) : (
          <EmptyState title="Aucune exception" />
        )}
      </section>
    </div>
  );
}

function AccessPanel({ member }: { member: Member }) {
  const session = useClientSession();
  const queryClient = useQueryClient();
  const access = useQuery({
    queryKey: ["client", "members", member.id, "access"],
    queryFn: () => clientApi.memberAccess(member.id),
  });
  const [material, setMaterial] = useState<MemberAccess | null>(null);
  const action = useMutation({
    mutationFn: (type: "regenerate" | "reset" | "unlock") =>
      type === "regenerate"
        ? clientApi.regenerateMemberAccess(member.id)
        : type === "reset"
          ? clientApi.resetMemberAccess(member.id)
          : clientApi.unlockMemberAccess(member.id).then(() => null),
    onSuccess: (data) => {
      if (data) setMaterial(data);
      void queryClient.invalidateQueries({ queryKey: ["client", "members", member.id, "access"] });
      toast.success("Accès mis à jour");
    },
  });
  if (access.isLoading) return <LoadingState />;
  return (
    <div className="max-w-3xl space-y-5">
      <section className="rounded-xl border bg-card p-5">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-sm font-semibold">État des identifiants</h2>
            <p className="mt-2 text-sm text-muted-foreground">{access.data?.method ?? "—"}</p>
          </div>
          <StatusBadge tone={member.initialAccessLocked ? "danger" : "neutral"}>
            {access.data?.credentialState ?? member.credentialState}
          </StatusBadge>
        </div>
        <div className="mt-5 flex flex-wrap gap-2">
          {session.can(
            member.credentialState === "ACTIVE"
              ? clientPermissions.membersResetAccess
              : clientPermissions.membersRegenerateAccess,
          ) ? (
            <Button
              onClick={() => action.mutate(member.credentialState === "ACTIVE" ? "reset" : "regenerate")}
              variant="outline"
            >
              <KeyIcon />
              {member.credentialState === "ACTIVE" ? "Réinitialiser l’accès" : "Régénérer l’accès"}
            </Button>
          ) : null}
          {member.initialAccessLocked && session.can(clientPermissions.membersUnlockAccess) ? (
            <Button onClick={() => action.mutate("unlock")} variant="outline">
              Déverrouiller
            </Button>
          ) : null}
        </div>
      </section>
      {material ? (
        <section className="rounded-xl border border-warning/30 bg-warning/5 p-5">
          <AccessMaterial access={material} onClose={() => setMaterial(null)} />
        </section>
      ) : null}
    </div>
  );
}

function MemberLifecycleDialog({ member }: { member: Member }) {
  const session = useClientSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const deactivate = member.isActive;
  const mutation = useMutation({
    mutationFn: async () => {
      if (deactivate) await clientApi.deactivateMember(member.id, reason);
      else await clientApi.reactivateMember(member.id, reason);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["client", "members"] });
      toast.success(deactivate ? "Membre désactivé" : "Membre réactivé");
      setOpen(false);
      setReason("");
    },
  });
  if (!session.can(deactivate ? clientPermissions.membersDeactivate : clientPermissions.membersReactivate)) return null;
  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>
        <Button variant={deactivate ? "destructive" : "default"}>{deactivate ? "Désactiver" : "Réactiver"}</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {deactivate ? "Désactiver" : "Réactiver"} {member.displayName}
          </DialogTitle>
          <DialogDescription>
            {deactivate
              ? "L’accès est coupé immédiatement; les rôles, groupes et références restent conservés."
              : "Le quota actuel est vérifié avant de restaurer l’accès. Les anciennes sessions ne sont pas restaurées."}
          </DialogDescription>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            mutation.mutate();
          }}
        >
          <div className="space-y-2">
            <Label htmlFor="member-lifecycle-reason">Motif</Label>
            <Textarea
              id="member-lifecycle-reason"
              maxLength={500}
              onChange={(event) => setReason(event.target.value)}
              required
              value={reason}
            />
          </div>
          <div className="flex justify-end">
            <Button
              disabled={!reason.trim() || mutation.isPending}
              type="submit"
              variant={deactivate ? "destructive" : "default"}
            >
              Confirmer
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function MemberDetail({ memberId }: { memberId: string }) {
  const session = useClientSession();
  const [tab, setTab] = useState("profile");
  const queryClient = useQueryClient();
  const members = useQuery({ queryKey: ["client", "members"], queryFn: clientApi.members });
  const member = members.data?.find((item) => item.id === memberId);
  const [displayName, setDisplayName] = useState("");
  const update = useMutation({
    mutationFn: () => clientApi.updateMember(memberId, displayName),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["client", "members"] });
      toast.success("Membre mis à jour");
    },
  });
  if (members.isLoading) return <LoadingState />;
  if (!member) return <ErrorState title="Membre introuvable" />;
  return (
    <div className="space-y-7">
      <Button asChild size="sm" variant="ghost">
        <Link to="/app/members">
          <ArrowLeftIcon className="rtl:rotate-180" />
          Membres
        </Link>
      </Button>
      <PageHeader
        actions={!member.isOwner ? <MemberLifecycleDialog member={member} /> : undefined}
        title={member.displayName}
      />
      <SectionTabs
        items={[
          { label: "Profil", value: "profile" },
          ...(session.can(clientPermissions.membersReadAuthorization)
            ? [{ label: "Autorisations", value: "authorization" }]
            : []),
          ...(session.can(clientPermissions.membersReadAccess) ? [{ label: "Accès", value: "access" }] : []),
        ]}
        onValueChange={setTab}
        value={tab}
      />
      {tab === "authorization" && session.can(clientPermissions.membersReadAuthorization) ? (
        <AuthorizationPanel memberId={memberId} />
      ) : tab === "access" && session.can(clientPermissions.membersReadAccess) ? (
        <AccessPanel member={member} />
      ) : (
        <section className="max-w-3xl rounded-xl border bg-card p-5">
          <dl className="grid gap-5 sm:grid-cols-2">
            <div>
              <dt className="text-xs text-muted-foreground">Identifiant</dt>
              <dd className="mt-1 font-medium">{member.username}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Email</dt>
              <dd className="mt-1 font-medium">{member.email ?? "—"}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Numéro employé</dt>
              <dd className="mt-1 font-medium">{member.employeeNumber ?? "—"}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Identifiants</dt>
              <dd className="mt-1">
                <StatusBadge>{member.credentialState}</StatusBadge>
              </dd>
            </div>
          </dl>
          {session.can(clientPermissions.membersUpdate) ? (
            <form
              className="mt-6 flex gap-3 border-t pt-5"
              onSubmit={(event) => {
                event.preventDefault();
                update.mutate();
              }}
            >
              <div className="flex-1 space-y-2">
                <Label htmlFor="member-display-edit">Nom affiché</Label>
                <Input
                  id="member-display-edit"
                  onChange={(event) => setDisplayName(event.target.value)}
                  placeholder={member.displayName}
                  value={displayName}
                />
              </div>
              <Button className="self-end" disabled={!displayName.trim()} type="submit">
                Enregistrer
              </Button>
            </form>
          ) : null}
        </section>
      )}
    </div>
  );
}

export function ClientMembersPage() {
  const { memberId } = useParams();
  const [search, setSearch] = useState("");
  const deferred = useDeferredValue(search);
  const members = useQuery({ queryKey: ["client", "members"], queryFn: clientApi.members });
  const filtered = useMemo(
    () =>
      members.data?.filter((member) =>
        `${member.displayName} ${member.username} ${member.email ?? ""} ${member.employeeNumber ?? ""}`
          .toLowerCase()
          .includes(deferred.toLowerCase()),
      ) ?? [],
    [members.data, deferred],
  );
  if (memberId) return <MemberDetail memberId={memberId} />;
  return (
    <div className="space-y-7">
      <PageHeader actions={<CreateMemberDialog />} title="Membres" />
      <section className="overflow-hidden rounded-xl border bg-card">
        <div className="relative border-b p-4">
          <MagnifyingGlassIcon className="absolute start-7 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="max-w-md ps-9"
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Nom, identifiant, email ou numéro…"
            value={search}
          />
        </div>
        {members.isLoading ? (
          <div className="p-5">
            <LoadingState />
          </div>
        ) : members.isError ? (
          <ErrorState retry={() => void members.refetch()} />
        ) : !filtered.length ? (
          <EmptyState title="Aucun membre" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Membre</TableHead>
                <TableHead>Identifiant</TableHead>
                <TableHead>Numéro employé</TableHead>
                <TableHead>Accès</TableHead>
                <TableHead>Statut</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((member) => (
                <TableRow key={member.id}>
                  <TableCell>
                    <Link className="font-medium hover:underline" to={`/app/members/${member.id}`}>
                      {member.displayName}
                    </Link>
                    {member.email ? <p className="text-xs text-muted-foreground">{member.email}</p> : null}
                  </TableCell>
                  <TableCell>
                    <code className="text-xs">{member.username}</code>
                  </TableCell>
                  <TableCell>{member.employeeNumber ?? "—"}</TableCell>
                  <TableCell>{member.credentialState}</TableCell>
                  <TableCell>
                    <StatusBadge tone={member.isActive ? "success" : "danger"}>
                      {member.isOwner ? "Propriétaire" : member.isActive ? "Actif" : "Inactif"}
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
