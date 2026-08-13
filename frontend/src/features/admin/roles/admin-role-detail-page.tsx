import { ArrowLeftIcon, CaretRightIcon, MagnifyingGlassIcon, UsersIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { AdminRole, RegistryModule } from "@/api/contracts";
import { ApiError } from "@/api/http";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { StatusBadge } from "@/components/patterns/status-badge";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { RoleFormDialog } from "@/features/admin/roles/admin-roles-page";
import { useDebouncedValue } from "@/lib/use-debounced-value";

function Section({
  title,
  description,
  action,
  children,
}: {
  title: string;
  description?: React.ReactNode;
  action?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <section className="rounded-xl border bg-card p-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h2 className="text-sm font-semibold">{title}</h2>
          {description ? <div className="mt-1 text-xs text-muted-foreground">{description}</div> : null}
        </div>
        {action ? <div className="shrink-0">{action}</div> : null}
      </div>
      <div className="mt-4">{children}</div>
    </section>
  );
}

function initials(value: string) {
  return (value.split("@")[0] ?? value).slice(0, 2).toUpperCase();
}

/**
 * Turns a permission's action into something readable — `bulk_set_active` into "Bulk set active".
 *
 * <p>The registry's `name` is not usable here: it is set to the full permission code for all 70
 * permissions, so rendering it printed the code twice around the one line that actually explained
 * anything. The action alone is the part that varies within a feature, which is exactly what
 * distinguishes one row from its neighbours.
 */
function actionLabel(action: string) {
  const spaced = action.replace(/_/g, " ");
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

/**
 * The whole grantable catalogue for one feature, with every permission shown whether granted or
 * not.
 *
 * <p>Listing only what is already granted answers "what does this role have" but never "what
 * could it have" — so a reader cannot tell an intentionally withheld permission from one nobody
 * knew existed. Showing the full set makes the unchecked boxes as informative as the checked
 * ones.
 */
function FeatureGroup({
  feature,
  grantedIds,
  onToggle,
  busy,
  canGrant,
  canRevoke,
}: {
  feature: RegistryModule["features"][number];
  grantedIds: Set<string>;
  onToggle: (permissionId: string, granted: boolean) => void;
  busy: boolean;
  canGrant: boolean;
  canRevoke: boolean;
}) {
  const grantedHere = feature.permissions.filter((permission) => grantedIds.has(permission.id)).length;
  const [open, setOpen] = useState(grantedHere > 0);

  return (
    <div className="rounded-lg border">
      <button
        aria-expanded={open}
        className="flex w-full items-center gap-3 p-3 text-start hover:bg-muted/50"
        onClick={() => setOpen((value) => !value)}
        type="button"
      >
        <CaretRightIcon className={`size-4 shrink-0 transition-transform ${open ? "rotate-90" : ""}`} />
        <span className="min-w-0 flex-1">
          <span className="block truncate text-sm font-medium">{feature.displayName}</span>
          {feature.description ? (
            <span className="block truncate text-xs text-muted-foreground">{feature.description}</span>
          ) : null}
        </span>
        <StatusBadge dot={false} tone={grantedHere > 0 ? "info" : "neutral"}>
          {grantedHere}/{feature.permissions.length}
        </StatusBadge>
      </button>
      {open ? (
        <div className="divide-y border-t">
          {feature.permissions
            .toSorted((a, b) => a.code.localeCompare(b.code))
            .map((permission) => {
              const granted = grantedIds.has(permission.id);
              const blocked = granted ? !canRevoke : !canGrant;
              return (
                <label
                  className={`flex items-start gap-3 p-3 ${blocked ? "opacity-60" : "cursor-pointer hover:bg-muted/40"}`}
                  htmlFor={`permission-${permission.id}`}
                  key={permission.id}
                  // Kept reachable for anyone who needs to map a row back to code, without
                  // spending a line of the layout on it.
                  title={permission.code}
                >
                  <Checkbox
                    aria-label={`${granted ? "Retirer" : "Accorder"} ${actionLabel(permission.action)}`}
                    checked={granted}
                    className="mt-0.5"
                    disabled={busy || blocked}
                    id={`permission-${permission.id}`}
                    onCheckedChange={() => onToggle(permission.id, granted)}
                  />
                  <span className="min-w-0 flex-1">
                    <span className="block text-sm font-medium">{actionLabel(permission.action)}</span>
                    {permission.description ? (
                      <span className="block text-xs text-muted-foreground">{permission.description}</span>
                    ) : null}
                  </span>
                </label>
              );
            })}
        </div>
      ) : null}
    </div>
  );
}

function RoleHolders({ roleId, count }: { roleId: string; count: number }) {
  const session = useAdminSession();
  const canRead = session.can(adminPermissions.rolesReadHolders);
  const holders = useQuery({
    queryKey: ["admin", "roles", roleId, "operators"],
    queryFn: () => adminApi.roleHolders(roleId),
    enabled: canRead,
  });

  return (
    <Section
      description="Modifier les permissions ci-dessus change immédiatement ce que ces personnes peuvent faire."
      title={`Détenteurs (${count})`}
    >
      {!canRead ? (
        <p className="text-sm text-muted-foreground">Vous n’êtes pas autorisé à consulter cette liste.</p>
      ) : holders.isLoading ? (
        <LoadingState rows={2} />
      ) : holders.isError ? (
        <ErrorState retry={() => void holders.refetch()} />
      ) : !holders.data?.length ? (
        <div className="grid place-items-center gap-2 rounded-lg border p-6 text-center">
          <UsersIcon className="size-6 text-muted-foreground" />
          <p className="text-sm font-medium">Personne ne détient ce rôle</p>
          <p className="text-xs text-muted-foreground">Les modifications n’affectent encore aucun accès.</p>
        </div>
      ) : (
        <div className="divide-y rounded-lg border">
          {holders.data.map((holder) => (
            <Link
              className="flex items-center gap-3 p-3 hover:bg-muted/40"
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
      )}
    </Section>
  );
}

export function AdminRoleDetailPage() {
  const { roleId = "" } = useParams();
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const debouncedSearch = useDebouncedValue(search);

  const role = useQuery({
    queryKey: ["admin", "roles", roleId],
    queryFn: () => adminApi.role(roleId),
    enabled: Boolean(roleId),
  });
  const catalog = useQuery({
    queryKey: ["admin", "permission-catalog"],
    queryFn: adminApi.permissionCatalog,
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ["admin", "roles"] });
    void queryClient.invalidateQueries({ queryKey: ["admin", "overview"] });
  };
  const onError = (error: unknown) => toast.error(error instanceof ApiError ? error.message : "Action impossible");

  const toggleRole = useMutation({
    mutationFn: () => adminApi.toggleRole(roleId),
    onSuccess: () => {
      refresh();
      toast.success("Statut mis à jour");
    },
    onError,
  });
  const togglePermission = useMutation({
    mutationFn: ({ permissionId, granted }: { permissionId: string; granted: boolean }) =>
      granted
        ? adminApi.revokeRolePermission(roleId, permissionId)
        : adminApi.grantRolePermission(roleId, permissionId),
    onSuccess: (_result, variables) => {
      refresh();
      toast.success(variables.granted ? "Permission retirée" : "Permission accordée");
    },
    onError,
  });

  // Filtering narrows what is shown; it never changes what is granted. A feature is kept when it
  // matches by name, or when any of its permissions does.
  const filteredModules = useMemo(() => {
    const term = debouncedSearch.trim().toLowerCase();
    const modules = catalog.data ?? [];
    if (!term) return modules;
    return modules
      .map((module) => ({
        ...module,
        features: module.features
          .map((feature) => ({
            ...feature,
            permissions: feature.permissions.filter((permission) =>
              `${permission.code} ${permission.name} ${permission.description}`.toLowerCase().includes(term),
            ),
          }))
          .filter((feature) => feature.permissions.length > 0 || feature.displayName.toLowerCase().includes(term)),
      }))
      .filter((module) => module.features.length > 0);
  }, [catalog.data, debouncedSearch]);

  const backLink = (
    <Button asChild variant="ghost">
      <Link to="/admin/roles">
        <ArrowLeftIcon />
        Rôles
      </Link>
    </Button>
  );

  if (role.isLoading) {
    return (
      <div className="space-y-7">
        {backLink}
        <LoadingState />
      </div>
    );
  }

  const data: AdminRole | undefined = role.data;
  if (role.isError || !data) {
    return (
      <div className="space-y-7">
        {backLink}
        <ErrorState retry={() => void role.refetch()} />
      </div>
    );
  }

  const grantedIds = new Set(data.permissions.map((permission) => permission.id));
  const totalGrantable = (catalog.data ?? []).reduce(
    (sum, module) => sum + module.features.reduce((count, feature) => count + feature.permissions.length, 0),
    0,
  );
  const canGrant = session.can(adminPermissions.rolesGrant);
  const canRevoke = session.can(adminPermissions.rolesRevoke);

  return (
    <div className="space-y-7">
      <div>{backLink}</div>

      <PageHeader
        actions={
          <>
            {session.can(adminPermissions.rolesUpdate) ? (
              <RoleFormDialog role={data} trigger={<Button variant="outline">Modifier</Button>} />
            ) : null}
            {session.can(adminPermissions.rolesMutate) ? (
              <Button
                disabled={toggleRole.isPending}
                onClick={() => toggleRole.mutate()}
                variant={data.isActive ? "destructive" : "outline"}
              >
                {data.isActive ? "Désactiver le rôle" : "Activer le rôle"}
              </Button>
            ) : null}
          </>
        }
        description={
          <span className="flex flex-wrap items-center gap-2">
            <StatusBadge tone={data.isActive ? "success" : "danger"}>{data.isActive ? "Actif" : "Inactif"}</StatusBadge>
            <StatusBadge tone={data.assignedOperatorCount > 0 ? "info" : "neutral"}>
              {data.assignedOperatorCount} détenteur{data.assignedOperatorCount === 1 ? "" : "s"}
            </StatusBadge>
            {data.description ? <span className="text-sm text-muted-foreground">{data.description}</span> : null}
          </span>
        }
        title={data.name}
      />

      {!data.isActive ? (
        <p className="rounded-lg border border-warning/30 bg-warning-subtle p-3 text-sm">
          Ce rôle est inactif : il ne peut pas être attribué et n’accorde aucune permission à ceux qui le détiennent
          déjà.
        </p>
      ) : null}

      <Section
        action={
          <div className="relative w-full sm:w-72">
            <MagnifyingGlassIcon className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              aria-label="Filtrer les permissions"
              className="ps-9"
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Filtrer les permissions…"
              value={search}
            />
          </div>
        }
        description={
          <>
            <span className="block">
              {grantedIds.size} accordée{grantedIds.size === 1 ? "" : "s"} sur {totalGrantable} disponibles. Cochez pour
              accorder, décochez pour retirer — chaque modification prend effet immédiatement.
            </span>
            {!canGrant && !canRevoke ? (
              <span className="mt-1 block">Vous consultez ce catalogue en lecture seule.</span>
            ) : null}
          </>
        }
        title="Permissions"
      >
        {catalog.isLoading ? (
          <LoadingState rows={4} />
        ) : catalog.isError ? (
          <ErrorState retry={() => void catalog.refetch()} />
        ) : !filteredModules.length ? (
          <p className="rounded-lg border p-6 text-center text-sm text-muted-foreground">
            Aucune permission ne correspond à ce filtre.
          </p>
        ) : (
          <div className="space-y-6">
            {filteredModules.map((module) => (
              <div key={module.code}>
                <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{module.code}</p>
                <div className="mt-2 space-y-2">
                  {module.features.map((feature) => (
                    <FeatureGroup
                      busy={togglePermission.isPending}
                      canGrant={canGrant}
                      canRevoke={canRevoke}
                      feature={feature}
                      grantedIds={grantedIds}
                      key={feature.id}
                      onToggle={(permissionId, granted) => togglePermission.mutate({ permissionId, granted })}
                    />
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </Section>

      <RoleHolders count={data.assignedOperatorCount} roleId={roleId} />
    </div>
  );
}
