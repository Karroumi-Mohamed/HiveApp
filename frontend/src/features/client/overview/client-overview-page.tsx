import { BuildingsIcon, HandshakeIcon, ShieldCheckIcon, UsersThreeIcon } from "@phosphor-icons/react";
import { useQueries } from "@tanstack/react-query";
import { Link } from "react-router";
import { clientApi } from "@/api/client-api";
import { clientPermissions } from "@/auth/permissions";
import { useClientSession } from "@/auth/session-provider";
import { OperationalCard } from "@/components/patterns/operational-card";
import { PageHeader } from "@/components/patterns/page-header";
import { ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { StatusBadge } from "@/components/patterns/status-badge";
import { Button } from "@/components/ui/button";

export function ClientOverviewPage() {
  const session = useClientSession();
  const canMembers = session.can(clientPermissions.membersRead);
  const canRoles = session.can(clientPermissions.rolesRead);
  const canCollaborations =
    session.can(clientPermissions.collaborationsRead) || session.can(clientPermissions.incomingCollaborationsRead);
  const [members, roles, collaborations, subscription] = useQueries({
    queries: [
      { queryKey: ["client", "members"], queryFn: clientApi.members, enabled: canMembers },
      { queryKey: ["client", "roles"], queryFn: clientApi.roles, enabled: canRoles },
      { queryKey: ["client", "collaborations"], queryFn: clientApi.collaborations, enabled: canCollaborations },
      {
        queryKey: ["client", "subscription"],
        queryFn: clientApi.subscription,
        enabled: session.can(clientPermissions.subscriptionRead),
      },
    ],
  });
  const visibleQueries = [members, roles, collaborations, subscription].filter((query) => query.fetchStatus !== "idle");
  if (visibleQueries.some((query) => query.isLoading)) return <LoadingState />;
  if (visibleQueries.some((query) => query.isError)) return <ErrorState />;
  const pendingCollaborations = collaborations.data?.filter((item) => item.status === "PENDING").length ?? 0;
  return (
    <div className="space-y-7">
      <PageHeader title="Vue d’ensemble" />
      <div className="grid gap-px overflow-hidden rounded-xl border bg-border sm:grid-cols-2 xl:grid-cols-4">
        <OperationalCard
          breakdown={[
            { label: "Actives", value: session.companies.filter((company) => company.isActive).length },
            { label: "Inactives", value: session.companies.filter((company) => !company.isActive).length },
          ]}
          icon={BuildingsIcon}
          label="au total"
          title="Entreprises"
          to="/app/companies"
          value={session.companies.length}
        />
        {members.data ? (
          <OperationalCard
            breakdown={[
              { label: "Actifs", value: members.data.filter((member) => member.isActive).length },
              {
                label: "Accès bloqués",
                value: members.data.filter((member) => member.initialAccessLocked).length,
                tone: "warning",
              },
            ]}
            icon={UsersThreeIcon}
            label="au total"
            title="Membres"
            to="/app/members"
            value={members.data.length}
          />
        ) : null}
        {roles.data ? (
          <OperationalCard
            breakdown={[
              { label: "Actifs", value: roles.data.filter((role) => role.status === "ACTIVE").length },
              { label: "Inactifs", value: roles.data.filter((role) => role.status === "INACTIVE").length },
            ]}
            icon={ShieldCheckIcon}
            label="configurés"
            title="Rôles"
            to="/app/roles"
            value={roles.data.length}
          />
        ) : null}
        {collaborations.data ? (
          <OperationalCard
            breakdown={[
              {
                label: "En attente",
                value: pendingCollaborations,
                tone: pendingCollaborations ? "warning" : "default",
              },
              { label: "Suspendues", value: collaborations.data.filter((item) => item.status === "SUSPENDED").length },
            ]}
            icon={HandshakeIcon}
            label="actives"
            title="Collaborations"
            to="/app/collaborations"
            tone={pendingCollaborations ? "warning" : "default"}
            value={collaborations.data.filter((item) => item.status === "ACTIVE").length}
          />
        ) : null}
      </div>
      <div className="grid gap-6 lg:grid-cols-[1.25fr_0.75fr]">
        <section className="rounded-xl border bg-card p-5">
          <div className="flex items-center justify-between gap-4">
            <h2 className="text-sm font-semibold">Contexte de travail</h2>
            <StatusBadge tone={session.account?.isActive ? "success" : "danger"}>
              {session.account?.isActive ? "Actif" : "Suspendu"}
            </StatusBadge>
          </div>
          <dl className="mt-5 grid gap-5 sm:grid-cols-2">
            <div>
              <dt className="text-xs text-muted-foreground">Compte</dt>
              <dd className="mt-1 font-medium">{session.account?.name}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Entreprise sélectionnée</dt>
              <dd className="mt-1 font-medium">
                {session.companies.find((company) => company.id === session.selectedCompanyId)?.name ??
                  "Tout le compte"}
              </dd>
            </div>
          </dl>
        </section>
        {subscription.data ? (
          <section className="rounded-xl border bg-card p-5">
            <div className="flex items-start justify-between gap-4">
              <div>
                <h2 className="text-sm font-semibold">Abonnement</h2>
                <p className="mt-2 text-lg font-semibold">{subscription.data.plan.name}</p>
              </div>
              <StatusBadge>{subscription.data.status}</StatusBadge>
            </div>
            <Button asChild className="mt-5" size="sm" variant="outline">
              <Link to="/app/subscription">Gérer</Link>
            </Button>
          </section>
        ) : null}
      </div>
    </div>
  );
}
