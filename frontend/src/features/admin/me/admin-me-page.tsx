import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { PermissionInventory } from "@/components/patterns/permission-inventory";
import { StatusBadge } from "@/components/patterns/status-badge";

export function AdminMePage() {
  const { me } = useAdminSession();
  if (!me) return null;
  return (
    <div className="space-y-7">
      <PageHeader title="Mon accès" />
      <section className="max-w-3xl divide-y rounded-xl border bg-card">
        <div className="flex flex-col gap-3 p-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="font-medium">{me.email}</p>
            <p className="mt-1 text-xs text-muted-foreground">Identifiant opérateur</p>
          </div>
          <StatusBadge tone={me.isActive ? "success" : "danger"}>{me.isActive ? "Actif" : "Inactif"}</StatusBadge>
        </div>
        <div className="grid gap-5 p-5 sm:grid-cols-2">
          <div>
            <p className="text-xs text-muted-foreground">Niveau</p>
            <p className="mt-1 font-medium">{me.isSuperAdmin ? "SuperAdmin" : "Opérateur"}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Permissions effectives</p>
            <p className="mt-1 font-medium tabular-nums">{me.permissions.length}</p>
          </div>
        </div>
        <div className="p-5">
          <PermissionInventory permissions={me.permissions} />
        </div>
      </section>
    </div>
  );
}
