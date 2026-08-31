import { CreditCardIcon, CubeIcon, ShieldCheckIcon, WarningCircleIcon } from "@phosphor-icons/react";
import { useQuery } from "@tanstack/react-query";
import { adminApi } from "@/api/admin-api";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { OperationalCard } from "@/components/patterns/operational-card";
import { PageHeader } from "@/components/patterns/page-header";
import { ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { StatusBadge } from "@/components/patterns/status-badge";
import { Button } from "@/components/ui/button";
import { adminCommercialKeys, commercialQueryEnabled } from "@/features/commercial/commercial-query";

export function AdminOverviewPage() {
  const session = useAdminSession();
  const accessEnabled = session.can(adminPermissions.accessOverview);
  const commercialEnabled = commercialQueryEnabled(session.can, adminPermissions.plansOverview);
  const syncEnabled = session.can(adminPermissions.registrySync);
  const access = useQuery({
    queryKey: ["admin", "overview", "access"],
    queryFn: adminApi.accessOverview,
    enabled: accessEnabled,
  });
  const commercial = useQuery({
    queryKey: adminCommercialKeys.overview(),
    queryFn: adminApi.commercialOverview,
    enabled: commercialEnabled,
  });
  const sync = useQuery({
    queryKey: ["admin", "overview", "registry"],
    queryFn: adminApi.latestRegistrySync,
    enabled: syncEnabled,
    retry: false,
  });
  const syncIsOnlySurface = !accessEnabled && !commercialEnabled && syncEnabled;
  if (!accessEnabled && !commercialEnabled && !syncEnabled) return <PermissionState />;
  if (
    (accessEnabled && access.isLoading) ||
    (commercialEnabled && commercial.isLoading) ||
    (syncIsOnlySurface && sync.isLoading)
  )
    return <LoadingState />;
  if (
    (accessEnabled && access.isError) ||
    (commercialEnabled && commercial.isError) ||
    (syncIsOnlySurface && sync.isError)
  )
    return (
      <ErrorState
        retry={() => {
          const retries: Promise<unknown>[] = [];
          if (accessEnabled && access.isError) retries.push(access.refetch());
          if (commercialEnabled && commercial.isError) retries.push(commercial.refetch());
          if (syncIsOnlySurface && sync.isError) retries.push(sync.refetch());
          void Promise.all(retries);
        }}
      />
    );
  const attention =
    commercialEnabled && commercial.data
      ? commercial.data.pastDueSubscriptions +
        commercial.data.suspendedSubscriptions +
        commercial.data.pendingCheckouts +
        commercial.data.changesNeedingAttention
      : 0;
  return (
    <div className="space-y-7">
      <PageHeader title="Vue d’ensemble" />
      <section aria-label="Situation opérationnelle" className="grid gap-4 md:grid-cols-2 2xl:grid-cols-4">
        {accessEnabled && access.data ? (
          <OperationalCard
            breakdown={[
              { label: "Total", value: access.data.totalOperators },
              {
                label: "Inactifs",
                value: access.data.inactiveOperators,
                tone: access.data.inactiveOperators ? "warning" : "default",
              },
              { label: "SuperAdmins", value: access.data.superAdmins },
              { label: "Rôles actifs", value: access.data.activeRoles },
            ]}
            icon={ShieldCheckIcon}
            label="opérateurs actifs"
            title="Accès plateforme"
            to="/admin/operators"
            value={access.data.activeOperators}
          />
        ) : null}
        {commercialEnabled && commercial.data ? (
          <OperationalCard
            breakdown={[
              { label: "Essais", value: commercial.data.trialingSubscriptions },
              {
                label: "Impayés",
                value: commercial.data.pastDueSubscriptions,
                tone: commercial.data.pastDueSubscriptions ? "warning" : "default",
              },
              {
                label: "Suspendus",
                value: commercial.data.suspendedSubscriptions,
                tone: commercial.data.suspendedSubscriptions ? "danger" : "default",
              },
              { label: "Total actuel", value: commercial.data.currentSubscriptions },
            ]}
            icon={CreditCardIcon}
            label="abonnements actifs"
            title="Abonnements"
            to={session.can(adminPermissions.subscriptionsSearch) ? "/admin/subscriptions" : undefined}
            value={commercial.data.activeSubscriptions}
          />
        ) : null}
        {commercialEnabled && commercial.data ? (
          <OperationalCard
            breakdown={[
              { label: "Brouillons", value: commercial.data.draftPlans },
              { label: "Inactifs", value: commercial.data.inactivePlans },
              { label: "Archivés", value: commercial.data.archivedPlans },
              { label: "Total", value: commercial.data.totalPlans },
            ]}
            icon={CubeIcon}
            label="forfaits actifs"
            title="Catalogue commercial"
            to={session.can(adminPermissions.plansList) ? "/admin/plans" : undefined}
            value={commercial.data.activePlans}
          />
        ) : null}
        {commercialEnabled && commercial.data ? (
          <OperationalCard
            breakdown={[
              {
                label: "Confirmations",
                value: commercial.data.pendingCheckouts,
                tone: commercial.data.pendingCheckouts ? "warning" : "default",
              },
              { label: "Planifiés", value: commercial.data.scheduledChanges },
              {
                label: "À reprendre",
                value: commercial.data.changesNeedingAttention,
                tone: commercial.data.changesNeedingAttention ? "danger" : "default",
              },
              {
                label: "Abonnements",
                value: commercial.data.pastDueSubscriptions + commercial.data.suspendedSubscriptions,
              },
            ]}
            icon={WarningCircleIcon}
            label="actions à traiter"
            title="File d’attention"
            tone={attention ? "warning" : "default"}
            to={session.can(adminPermissions.subscriptionsSearch) ? "/admin/subscriptions" : undefined}
            value={attention}
          />
        ) : null}
      </section>
      {syncEnabled ? (
        <section className="border-y py-5">
          <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
            <div>
              <h2 className="text-sm font-semibold">Synchronisation du registre</h2>
              {sync.isError ? (
                <div className="mt-2 flex items-center gap-2 text-xs text-destructive">
                  <span>Synchronisation indisponible.</span>
                  <Button onClick={() => void sync.refetch()} size="sm" variant="outline">
                    Réessayer
                  </Button>
                </div>
              ) : sync.isLoading ? (
                <p className="mt-1 text-xs text-muted-foreground" role="status">
                  Chargement de la synchronisation…
                </p>
              ) : sync.data ? (
                <p className="mt-1 text-xs text-muted-foreground">
                  Build {sync.data.buildVersion} · {sync.data.discoveredFeatures} fonctionnalités ·{" "}
                  {sync.data.discoveredPermissions} permissions
                </p>
              ) : (
                <p className="mt-1 text-xs text-muted-foreground">Aucun résultat disponible.</p>
              )}
            </div>
            {sync.data ? (
              <StatusBadge tone={sync.data.status === "SUCCEEDED" ? "success" : "danger"}>
                {sync.data.status === "SUCCEEDED" ? "Synchronisé" : "Échec"}
              </StatusBadge>
            ) : null}
          </div>
        </section>
      ) : null}
    </div>
  );
}
