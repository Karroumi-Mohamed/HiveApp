import { CaretDownIcon, CaretRightIcon, MagnifyingGlassIcon } from "@phosphor-icons/react";
import { useDeferredValue, useMemo, useState } from "react";
import type { AdminPermission } from "@/api/contracts";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { cn } from "@/lib/utils";

const ACTION_LABELS: Record<string, string> = {
  archive: "Archiver",
  assign_role: "Attribuer un rôle",
  bulk_assign_role: "Attribuer un rôle en masse",
  bulk_resend_activation: "Renvoyer les activations en masse",
  bulk_set_active: "Modifier les statuts en masse",
  change_email: "Changer l’adresse email",
  confirm_checkout: "Confirmer un paiement",
  create: "Créer",
  create_from_preset: "Créer depuis un modèle",
  create_trial: "Créer un essai",
  delete: "Supprimer",
  duplicate: "Dupliquer",
  generate_temporary_access: "Générer un accès temporaire",
  grant_permission: "Accorder une permission",
  list: "Lister",
  list_features: "Lister les fonctionnalités",
  list_grantable_permissions: "Lister les permissions attribuables",
  list_presets: "Lister les modèles",
  list_subscribers: "Lister les abonnés",
  lookup_subscriber_owner_email: "Consulter l’email du propriétaire",
  overview: "Consulter la synthèse",
  preview_impact: "Prévisualiser l’impact",
  read: "Consulter",
  read_changes: "Consulter les changements",
  read_detail: "Consulter le détail",
  read_history: "Consulter l’historique",
  read_holders: "Consulter les opérateurs attribués",
  read_permissions: "Consulter les permissions",
  remove_role: "Retirer un rôle",
  rename: "Modifier le nom",
  replace_permissions: "Remplacer les permissions",
  resend_activation: "Renvoyer l’activation",
  revoke_permission: "Retirer une permission",
  search_accounts: "Rechercher des comptes",
  send_email_verification: "Envoyer la vérification email",
  toggle_active: "Modifier le statut d’accès",
  transition_status: "Changer le statut",
  update: "Modifier",
  update_overrides: "Modifier les dérogations",
};

const RESOURCE_LABELS: Record<string, string> = {
  admin_roles: "Rôles administrateur",
  admin_users: "Opérateurs",
  add_ons: "Modules complémentaires",
  plans: "Forfaits",
  quota_packages: "Packs de quotas",
  registry: "Registre des fonctionnalités",
  subscriptions: "Abonnements",
};

/** Shared with every screen that displays catalogue permissions, so labels cannot drift. */
export function permissionActionLabel(action: string) {
  const translated = ACTION_LABELS[action.toLowerCase()];
  if (translated) return translated;
  const spaced = action.replace(/_/g, " ");
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

export function permissionResourceLabel(resource: string) {
  const translated = RESOURCE_LABELS[resource.toLowerCase()];
  if (translated) return translated;
  const spaced = resource.replace(/_/g, " ");
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

export function AdminPermissionEditor({
  permissions,
  selectedIds,
  editableIds,
  disabled = false,
  className,
  listClassName,
  onChange,
}: {
  permissions: AdminPermission[];
  selectedIds: ReadonlySet<string>;
  editableIds?: ReadonlySet<string>;
  disabled?: boolean;
  className?: string;
  listClassName?: string;
  onChange: (next: Set<string>) => void;
}) {
  const [search, setSearch] = useState("");
  const deferredSearch = useDeferredValue(search.trim().toLowerCase());
  const [resourceFilter, setResourceFilter] = useState("ALL");
  const [collapsed, setCollapsed] = useState<Set<string>>(() => new Set());

  const resources = useMemo(
    () => [...new Set(permissions.map((permission) => permission.resource))].toSorted(),
    [permissions],
  );

  const groups = useMemo(() => {
    const byResource = new Map<string, AdminPermission[]>();
    for (const permission of permissions) {
      if (resourceFilter !== "ALL" && permission.resource !== resourceFilter) continue;
      const haystack = `${permission.code} ${permission.description ?? ""} ${permission.action}`.toLowerCase();
      if (deferredSearch && !haystack.includes(deferredSearch)) continue;
      const group = byResource.get(permission.resource) ?? [];
      group.push(permission);
      byResource.set(permission.resource, group);
    }
    return [...byResource.entries()]
      .map(([resource, entries]) => [resource, entries.toSorted((a, b) => a.code.localeCompare(b.code))] as const)
      .toSorted(([first], [second]) => first.localeCompare(second));
  }, [permissions, deferredSearch, resourceFilter]);

  const togglePermission = (permission: AdminPermission) => {
    if (disabled || (editableIds && !editableIds.has(permission.id))) return;
    const next = new Set(selectedIds);
    if (next.has(permission.id)) next.delete(permission.id);
    else next.add(permission.id);
    onChange(next);
  };

  const toggleGroup = (entries: AdminPermission[]) => {
    const editable = entries.filter((permission) => !editableIds || editableIds.has(permission.id));
    if (disabled || editable.length === 0) return;
    const next = new Set(selectedIds);
    const everySelected = editable.every((permission) => next.has(permission.id));
    for (const permission of editable) {
      if (everySelected) next.delete(permission.id);
      else next.add(permission.id);
    }
    onChange(next);
  };

  return (
    <div className={cn("space-y-4", className)}>
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative min-w-[min(100%,18rem)] flex-1">
          <MagnifyingGlassIcon className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            aria-label="Filtrer les permissions"
            className="ps-9"
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Rechercher une permission…"
            value={search}
          />
        </div>
        <Select onValueChange={setResourceFilter} value={resourceFilter}>
          <SelectTrigger aria-label="Domaine de permission" className="w-full sm:w-60">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">Tous les domaines</SelectItem>
            {resources.map((resource) => (
              <SelectItem key={resource} value={resource}>
                {permissionResourceLabel(resource)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <span className="shrink-0 whitespace-nowrap text-sm tabular-nums text-muted-foreground sm:ms-auto">
          {selectedIds.size} sélectionnée(s)
        </span>
      </div>

      {groups.length === 0 ? (
        <p className="border-y py-8 text-center text-sm text-muted-foreground">Aucune permission correspondante.</p>
      ) : (
        <div className={cn("divide-y border-y", listClassName)}>
          {groups.map(([resource, entries]) => {
            const isCollapsed = collapsed.has(resource);
            const selectedCount = entries.filter((permission) => selectedIds.has(permission.id)).length;
            return (
              <section key={resource}>
                <div className="flex min-h-12 items-center gap-2 py-2">
                  <button
                    aria-expanded={!isCollapsed}
                    className="flex min-h-11 min-w-0 flex-1 items-center gap-2 rounded-md px-2 text-start hover:bg-muted/50"
                    onClick={() =>
                      setCollapsed((current) => {
                        const next = new Set(current);
                        if (next.has(resource)) next.delete(resource);
                        else next.add(resource);
                        return next;
                      })
                    }
                    type="button"
                  >
                    {isCollapsed ? <CaretRightIcon /> : <CaretDownIcon />}
                    <span className="truncate font-medium">{permissionResourceLabel(resource)}</span>
                    <span className="text-xs tabular-nums text-muted-foreground">
                      {selectedCount}/{entries.length}
                    </span>
                  </button>
                  <button
                    className="min-h-11 rounded-md px-3 text-xs font-medium hover:bg-muted/50 disabled:cursor-not-allowed disabled:opacity-45"
                    disabled={disabled || entries.every((permission) => editableIds && !editableIds.has(permission.id))}
                    onClick={() => toggleGroup(entries)}
                    type="button"
                  >
                    {entries.every((permission) => selectedIds.has(permission.id))
                      ? "Tout retirer"
                      : "Tout sélectionner"}
                  </button>
                </div>
                {!isCollapsed ? (
                  <div className="divide-y border-t ps-6">
                    {entries.map((permission) => {
                      const editable = !disabled && (!editableIds || editableIds.has(permission.id));
                      return (
                        <label
                          className={`flex min-h-14 items-start gap-3 py-3 pe-2 ${editable ? "cursor-pointer" : "opacity-55"}`}
                          htmlFor={`admin-permission-${permission.id}`}
                          key={permission.id}
                          title={permission.code}
                        >
                          <Checkbox
                            checked={selectedIds.has(permission.id)}
                            className="mt-0.5"
                            disabled={!editable}
                            id={`admin-permission-${permission.id}`}
                            onCheckedChange={() => togglePermission(permission)}
                          />
                          <span className="min-w-0">
                            <span className="block text-sm font-medium">
                              {permissionActionLabel(permission.action)}
                            </span>
                            {permission.description ? (
                              <span className="block text-xs text-muted-foreground">{permission.description}</span>
                            ) : null}
                          </span>
                        </label>
                      );
                    })}
                  </div>
                ) : null}
              </section>
            );
          })}
        </div>
      )}
    </div>
  );
}
