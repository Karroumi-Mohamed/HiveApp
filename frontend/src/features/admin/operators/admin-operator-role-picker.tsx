import { MagnifyingGlassIcon } from "@phosphor-icons/react";
import { useInfiniteQuery } from "@tanstack/react-query";
import { useDeferredValue, useEffect, useMemo, useState } from "react";
import { adminApi } from "@/api/admin-api";
import type { AdminRoleSummary } from "@/api/contracts";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { roleSelectionChanges } from "@/features/admin/operators/admin-operator-rules";

type RoleOption = Pick<AdminRoleSummary, "id" | "name" | "description" | "status" | "isActive">;

export function OperatorRolePicker({
  assignedRoles,
  canAssign,
  canRemove,
  onSave,
  children,
}: {
  assignedRoles: AdminRoleSummary[];
  canAssign: boolean;
  canRemove: boolean;
  onSave: (roleIds: string[]) => Promise<void>;
  children: React.ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const deferredSearch = useDeferredValue(search.trim());

  const roles = useInfiniteQuery({
    queryKey: ["admin", "roles", "operator-picker", deferredSearch],
    queryFn: ({ pageParam }) =>
      adminApi.roles({
        active: true,
        search: deferredSearch || undefined,
        page: pageParam,
        size: 50,
        sort: "name",
        direction: "asc",
      }),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.page + 1),
    enabled: open && canAssign,
  });

  useEffect(() => {
    if (!open) return;
    setSelected(new Set(assignedRoles.map((role) => role.id)));
    setSearch("");
    setError(null);
  }, [assignedRoles, open]);

  const options = useMemo(() => {
    const byId = new Map<string, RoleOption>();
    for (const role of assignedRoles) byId.set(role.id, role);
    for (const page of roles.data?.pages ?? []) {
      for (const role of page.content) {
        if (role.availableActions.includes("ASSIGN_TO_OPERATOR")) byId.set(role.id, role);
      }
    }
    const query = search.trim().toLocaleLowerCase("fr");
    return [...byId.values()]
      .filter((role) => !query || `${role.name} ${role.description ?? ""}`.toLocaleLowerCase("fr").includes(query))
      .toSorted((a, b) => a.name.localeCompare(b.name, "fr"));
  }, [assignedRoles, roles.data?.pages, search]);

  const currentIds = assignedRoles.map((role) => role.id);
  const changes = roleSelectionChanges(currentIds, [...selected]);
  const hasChanges = changes.added.length > 0 || changes.removed.length > 0;

  const submit = async () => {
    if (!hasChanges) return;
    setSaving(true);
    setError(null);
    try {
      await onSave([...selected]);
      setOpen(false);
    } catch {
      setError("Aucun changement n’a été appliqué. Actualisez la liste puis réessayez.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Gérer les rôles</DialogTitle>
          <DialogDescription>Sélectionnez l’ensemble des rôles que cet opérateur doit conserver.</DialogDescription>
        </DialogHeader>
        <div className="relative">
          <MagnifyingGlassIcon className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <Input
            aria-label="Rechercher un rôle"
            autoComplete="off"
            className="ps-9"
            name="role-search"
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Rechercher un rôle…"
            spellCheck={false}
            value={search}
          />
        </div>
        <div className="max-h-[min(55vh,28rem)] overflow-y-auto border-y">
          {roles.isLoading && options.length === 0 ? (
            <p className="py-8 text-center text-sm text-muted-foreground">Chargement…</p>
          ) : roles.isError && options.length === 0 ? (
            <div className="flex flex-col items-center gap-3 py-8 text-sm text-muted-foreground">
              <p>Impossible de charger les rôles.</p>
              <Button onClick={() => void roles.refetch()} size="sm" variant="outline">
                Réessayer
              </Button>
            </div>
          ) : options.length ? (
            options.map((role) => {
              const wasAssigned = currentIds.includes(role.id);
              const disabled = wasAssigned ? !canRemove : !canAssign || !role.isActive;
              const id = `operator-role-${role.id}`;
              return (
                <label
                  className="flex min-h-14 cursor-pointer items-start gap-3 border-b py-3 last:border-b-0 has-[:disabled]:cursor-not-allowed has-[:disabled]:opacity-55"
                  htmlFor={id}
                  key={role.id}
                >
                  <Checkbox
                    checked={selected.has(role.id)}
                    disabled={disabled}
                    id={id}
                    onCheckedChange={(checked) =>
                      setSelected((current) => {
                        const next = new Set(current);
                        if (checked) next.add(role.id);
                        else next.delete(role.id);
                        return next;
                      })
                    }
                  />
                  <span className="min-w-0 flex-1">
                    <span className="flex flex-wrap items-center gap-2 text-sm font-medium">
                      {role.name}
                      {!role.isActive ? <span className="text-xs text-muted-foreground">Inactif</span> : null}
                    </span>
                    {role.description ? (
                      <span className="mt-0.5 block text-xs leading-5 text-muted-foreground">{role.description}</span>
                    ) : null}
                  </span>
                </label>
              );
            })
          ) : (
            <p className="py-8 text-center text-sm text-muted-foreground">Aucun rôle correspondant.</p>
          )}
          {roles.hasNextPage ? (
            <div className="border-t py-3 text-center">
              <Button
                disabled={roles.isFetchingNextPage}
                onClick={() => void roles.fetchNextPage()}
                size="sm"
                variant="ghost"
              >
                {roles.isFetchingNextPage ? "Chargement…" : "Charger plus de rôles"}
              </Button>
            </div>
          ) : null}
        </div>
        <div className="min-h-5 text-xs text-muted-foreground">
          {hasChanges
            ? `${changes.added.length} à attribuer · ${changes.removed.length} à retirer`
            : "Aucun changement"}
        </div>
        {error ? <p className="text-sm text-destructive">{error}</p> : null}
        <DialogFooter>
          <Button onClick={() => setOpen(false)} type="button" variant="outline">
            Annuler
          </Button>
          <Button disabled={!hasChanges || saving} onClick={() => void submit()}>
            {saving ? "Enregistrement…" : "Enregistrer les rôles"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
