import { ArrowLeftIcon, FilePlusIcon, PlusIcon, ShieldCheckIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type FormEvent, type ReactNode, useState } from "react";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { AdminRolePreset } from "@/api/contracts";
import { ApiError } from "@/api/http";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { Button } from "@/components/ui/button";
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
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { AdminPermissionEditor } from "@/features/admin/roles/admin-permission-editor";
import { draftFromPreset } from "@/features/admin/roles/admin-role-rules";

type Source = { kind: "blank" } | { kind: "preset"; preset: AdminRolePreset };

export function AdminRoleCreateDialog({ trigger }: { trigger: ReactNode }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [source, setSource] = useState<Source | null>(null);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [selectedIds, setSelectedIds] = useState<Set<string>>(() => new Set());
  const canCreateBlank = session.can(adminPermissions.rolesCreate);
  const canCreateFromPreset =
    session.can(adminPermissions.rolesCreateFromPreset) && session.can(adminPermissions.rolesListPresets);

  const presets = useQuery({
    queryKey: ["admin", "role-presets"],
    queryFn: adminApi.rolePresets,
    enabled: open && canCreateFromPreset,
  });
  const permissions = useQuery({
    queryKey: ["admin", "roles", "grantable-permissions"],
    queryFn: adminApi.grantableRolePermissions,
    enabled: open && session.can(adminPermissions.rolesListGrantable),
  });

  const save = useMutation({
    mutationFn: () => {
      const input = { name, description: description || undefined, permissionIds: [...selectedIds] };
      return source?.kind === "preset"
        ? adminApi.createRoleFromPreset({ ...input, presetCode: source.preset.code })
        : adminApi.createRole(input);
    },
    onSuccess: (role) => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "roles"] });
      toast.success("Rôle créé", { description: `${role.name} est inactif jusqu’à son activation.` });
      close();
    },
  });

  const close = () => {
    setOpen(false);
    setSource(null);
    setName("");
    setDescription("");
    setSelectedIds(new Set());
    save.reset();
  };

  const selectSource = (next: Source) => {
    setSource(next);
    if (next.kind === "blank") {
      setName("");
      setDescription("");
      setSelectedIds(new Set());
      return;
    }
    const draft = draftFromPreset(next.preset);
    setName(draft.name);
    setDescription(draft.description);
    setSelectedIds(draft.permissionIds);
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (name.trim()) save.mutate();
  };

  if (!canCreateBlank && !canCreateFromPreset) return null;
  const nameConflict = save.error instanceof ApiError && save.error.code === "ROLE_NAME_CONFLICT";

  return (
    <Dialog onOpenChange={(next) => (next ? setOpen(true) : close())} open={open}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent className="max-h-[calc(100dvh-2rem)] overflow-hidden p-0 sm:max-w-4xl">
        {!source ? (
          <div className="overflow-y-auto p-6">
            <DialogHeader>
              <DialogTitle>Créer un rôle</DialogTitle>
              <DialogDescription>Commencez vide ou utilisez un préréglage HiveApp.</DialogDescription>
            </DialogHeader>
            <div className="divide-y border-y">
              {canCreateBlank ? (
                <button
                  className="flex min-h-16 w-full items-center gap-3 px-2 text-start hover:bg-muted/50"
                  onClick={() => selectSource({ kind: "blank" })}
                  type="button"
                >
                  <FilePlusIcon className="size-5 text-muted-foreground" />
                  <span>
                    <span className="block font-medium">Rôle vide</span>
                    <span className="block text-sm text-muted-foreground">Choisir les permissions manuellement</span>
                  </span>
                </button>
              ) : null}
              {presets.data?.map((preset) => (
                <button
                  className="flex min-h-16 w-full items-center gap-3 px-2 text-start hover:bg-muted/50"
                  key={preset.code}
                  onClick={() => selectSource({ kind: "preset", preset })}
                  type="button"
                >
                  <ShieldCheckIcon className="size-5 text-muted-foreground" />
                  <span className="min-w-0 flex-1">
                    <span className="block font-medium">{preset.name}</span>
                    <span className="block truncate text-sm text-muted-foreground">{preset.description}</span>
                  </span>
                  <span className="text-sm tabular-nums text-muted-foreground">
                    {preset.permissions.length} permissions
                  </span>
                </button>
              ))}
            </div>
          </div>
        ) : (
          <form
            className="grid h-[min(52rem,calc(100dvh-2rem))] min-h-0 grid-rows-[auto_auto_minmax(0,1fr)_auto]"
            onSubmit={submit}
          >
            <DialogHeader className="px-6 pt-6 pb-5">
              <button
                className="mb-1 inline-flex min-h-11 w-fit items-center gap-2 rounded-md pe-3 text-sm text-muted-foreground hover:text-foreground"
                onClick={() => setSource(null)}
                type="button"
              >
                <ArrowLeftIcon /> Changer de point de départ
              </button>
              <DialogTitle>Nouveau rôle</DialogTitle>
              <DialogDescription className="sr-only">
                Définissez le nom, la description et les permissions du nouveau rôle.
              </DialogDescription>
            </DialogHeader>
            <div className="grid gap-4 px-6 pb-5 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="new-admin-role-name">Nom</Label>
                <Input
                  aria-invalid={nameConflict}
                  id="new-admin-role-name"
                  maxLength={100}
                  onChange={(event) => {
                    setName(event.target.value);
                    if (nameConflict) save.reset();
                  }}
                  required
                  value={name}
                />
                {nameConflict ? <p className="text-sm text-destructive">Ce nom est déjà utilisé.</p> : null}
              </div>
              <div className="space-y-2">
                <Label htmlFor="new-admin-role-description">Description</Label>
                <Textarea
                  id="new-admin-role-description"
                  maxLength={500}
                  onChange={(event) => setDescription(event.target.value)}
                  value={description}
                />
              </div>
            </div>
            <div className="min-h-0 overflow-hidden border-t px-6 pt-4">
              <AdminPermissionEditor
                className="flex h-full min-h-0 flex-col gap-4 space-y-0"
                disabled={permissions.isLoading || permissions.isError}
                listClassName="min-h-0 flex-1 overflow-y-auto overscroll-contain pe-1"
                onChange={setSelectedIds}
                permissions={permissions.data ?? (source.kind === "preset" ? source.preset.permissions : [])}
                selectedIds={selectedIds}
              />
            </div>
            <div className="bg-background">
              {save.isError && !nameConflict ? (
                <p className="border-t px-6 pt-3 text-sm text-destructive">
                  {save.error instanceof ApiError ? save.error.message : "Création impossible."}
                </p>
              ) : null}
              <DialogFooter className="border-t px-6 py-4">
                <Button onClick={close} type="button" variant="outline">
                  Annuler
                </Button>
                <Button disabled={save.isPending || !name.trim()} type="submit">
                  <PlusIcon /> {save.isPending ? "Création…" : "Créer le rôle"}
                </Button>
              </DialogFooter>
            </div>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
