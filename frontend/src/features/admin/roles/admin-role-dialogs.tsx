import { CopyIcon } from "@phosphor-icons/react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { type FormEvent, type ReactNode, useState } from "react";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { AdminRole } from "@/api/contracts";
import { ApiError } from "@/api/http";
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

function RoleFields({
  prefix,
  name,
  description,
  nameConflict,
  onNameChange,
  onDescriptionChange,
}: {
  prefix: string;
  name: string;
  description: string;
  nameConflict: boolean;
  onNameChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
}) {
  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <Label htmlFor={`${prefix}-name`}>Nom</Label>
        <Input
          aria-invalid={nameConflict}
          id={`${prefix}-name`}
          maxLength={100}
          onChange={(event) => onNameChange(event.target.value)}
          required
          value={name}
        />
        {nameConflict ? <p className="text-sm text-destructive">Ce nom est déjà utilisé.</p> : null}
      </div>
      <div className="space-y-2">
        <Label htmlFor={`${prefix}-description`}>Description</Label>
        <Textarea
          id={`${prefix}-description`}
          maxLength={500}
          onChange={(event) => onDescriptionChange(event.target.value)}
          value={description}
        />
      </div>
    </div>
  );
}

export function AdminRoleMetadataDialog({ role, trigger }: { role: AdminRole; trigger: ReactNode }) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState(role.name);
  const [description, setDescription] = useState(role.description ?? "");
  const save = useMutation({
    mutationFn: () =>
      adminApi.updateRole(role.id, { name, description: description || undefined, expectedVersion: role.version }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "roles"] });
      toast.success("Rôle mis à jour");
      setOpen(false);
    },
  });
  const nameConflict = save.error instanceof ApiError && save.error.code === "ROLE_NAME_CONFLICT";
  return (
    <Dialog
      onOpenChange={(next) => {
        setOpen(next);
        if (next) {
          setName(role.name);
          setDescription(role.description ?? "");
          save.reset();
        }
      }}
      open={open}
    >
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Modifier le rôle</DialogTitle>
          <DialogDescription className="sr-only">Modifiez le nom et la description du rôle.</DialogDescription>
        </DialogHeader>
        <form
          className="space-y-5"
          onSubmit={(event: FormEvent) => {
            event.preventDefault();
            save.mutate();
          }}
        >
          <RoleFields
            description={description}
            name={name}
            nameConflict={nameConflict}
            onDescriptionChange={setDescription}
            onNameChange={setName}
            prefix="edit-admin-role"
          />
          {save.isError && !nameConflict ? (
            <p className="text-sm text-destructive">
              {save.error instanceof ApiError ? save.error.message : "Modification impossible."}
            </p>
          ) : null}
          <DialogFooter>
            <Button onClick={() => setOpen(false)} type="button" variant="outline">
              Annuler
            </Button>
            <Button disabled={save.isPending || !name.trim()} type="submit">
              {save.isPending ? "Enregistrement…" : "Enregistrer"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export function AdminRoleDuplicateDialog({
  role,
  trigger,
  open: controlledOpen,
  onOpenChange,
}: {
  role: AdminRole;
  trigger?: ReactNode;
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
}) {
  const queryClient = useQueryClient();
  const [internalOpen, setInternalOpen] = useState(false);
  const open = controlledOpen ?? internalOpen;
  const setOpen = onOpenChange ?? setInternalOpen;
  const [name, setName] = useState(`${role.name} — copie`);
  const [description, setDescription] = useState(role.description ?? "");
  const duplicate = useMutation({
    mutationFn: () => adminApi.duplicateRole(role.id, { name, description: description || undefined }),
    onSuccess: (created) => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "roles"] });
      toast.success("Rôle dupliqué", { description: `${created.name} est inactif.` });
      setOpen(false);
    },
  });
  const nameConflict = duplicate.error instanceof ApiError && duplicate.error.code === "ROLE_NAME_CONFLICT";
  return (
    <Dialog
      onOpenChange={(next) => {
        setOpen(next);
        if (next) {
          setName(`${role.name} — copie`);
          setDescription(role.description ?? "");
          duplicate.reset();
        }
      }}
      open={open}
    >
      {trigger ? <DialogTrigger asChild>{trigger}</DialogTrigger> : null}
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Dupliquer {role.name}</DialogTitle>
          <DialogDescription>Les permissions sont copiées, pas les opérateurs ni le statut.</DialogDescription>
        </DialogHeader>
        <form
          className="space-y-5"
          onSubmit={(event: FormEvent) => {
            event.preventDefault();
            duplicate.mutate();
          }}
        >
          <RoleFields
            description={description}
            name={name}
            nameConflict={nameConflict}
            onDescriptionChange={setDescription}
            onNameChange={setName}
            prefix="duplicate-admin-role"
          />
          {duplicate.isError && !nameConflict ? (
            <p className="text-sm text-destructive">
              {duplicate.error instanceof ApiError ? duplicate.error.message : "Duplication impossible."}
            </p>
          ) : null}
          <DialogFooter>
            <Button onClick={() => setOpen(false)} type="button" variant="outline">
              Annuler
            </Button>
            <Button disabled={duplicate.isPending || !name.trim()} type="submit">
              <CopyIcon /> {duplicate.isPending ? "Duplication…" : "Dupliquer"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
