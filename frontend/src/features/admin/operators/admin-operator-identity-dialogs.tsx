import { useEffect, useState } from "react";
import type { AdminUser } from "@/api/contracts";
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
import { operatorEmailChanged } from "@/features/admin/operators/admin-operator-rules";

export function OperatorNameDialog({
  operator,
  onSave,
  children,
}: {
  operator: AdminUser;
  onSave: (input: { firstName: string; lastName: string }) => Promise<void>;
  children: React.ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const [firstName, setFirstName] = useState(operator.firstName);
  const [lastName, setLastName] = useState(operator.lastName);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setFirstName(operator.firstName);
    setLastName(operator.lastName);
    setError(null);
  }, [open, operator.firstName, operator.lastName]);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await onSave({ firstName: firstName.trim(), lastName: lastName.trim() });
      setOpen(false);
    } catch (reason) {
      setError(reason instanceof ApiError ? reason.message : "Modification impossible");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Modifier le nom</DialogTitle>
          <DialogDescription>Corrigez l’identité affichée de cet opérateur.</DialogDescription>
        </DialogHeader>
        <form className="space-y-5" onSubmit={(event) => void submit(event)}>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="operator-first-name">Prénom</Label>
              <Input
                autoFocus
                autoComplete="given-name"
                id="operator-first-name"
                onChange={(event) => setFirstName(event.target.value)}
                value={firstName}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="operator-last-name">Nom</Label>
              <Input
                autoComplete="family-name"
                id="operator-last-name"
                onChange={(event) => setLastName(event.target.value)}
                value={lastName}
              />
            </div>
          </div>
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
          <DialogFooter>
            <Button onClick={() => setOpen(false)} type="button" variant="outline">
              Annuler
            </Button>
            <Button disabled={!firstName.trim() || !lastName.trim() || saving} type="submit">
              {saving ? "Enregistrement…" : "Enregistrer le nom"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export function OperatorEmailDialog({
  operator,
  onSave,
  children,
}: {
  operator: AdminUser;
  onSave: (email: string) => Promise<void>;
  children: React.ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const [email, setEmail] = useState(operator.email);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const changed = operatorEmailChanged(operator.email, email);

  useEffect(() => {
    if (!open) return;
    setEmail(operator.email);
    setError(null);
  }, [open, operator.email]);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!changed) return;
    setSaving(true);
    setError(null);
    try {
      await onSave(email.trim());
      setOpen(false);
    } catch (reason) {
      setError(
        reason instanceof ApiError && reason.code === "RESOURCE_ALREADY_EXISTS"
          ? "Cette adresse est déjà utilisée."
          : reason instanceof ApiError
            ? reason.message
            : "Modification impossible",
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Changer l’adresse email</DialogTitle>
          <DialogDescription>La nouvelle adresse devient immédiatement l’identifiant de connexion.</DialogDescription>
        </DialogHeader>
        <form className="space-y-5" onSubmit={(event) => void submit(event)}>
          <div className="space-y-2">
            <Label htmlFor="operator-email">Nouvelle adresse email</Label>
            <Input
              autoFocus
              autoComplete="email"
              id="operator-email"
              onChange={(event) => setEmail(event.target.value)}
              type="email"
              value={email}
            />
            {error ? <p className="text-sm text-destructive">{error}</p> : null}
          </div>
          {changed ? (
            <div className="border-y border-warning/30 bg-warning-subtle/40 py-3 text-sm leading-6">
              L’adresse deviendra non vérifiée. Une demande de vérification sera créée pour la nouvelle adresse et la
              récupération du mot de passe restera indisponible jusqu’à sa validation.
            </div>
          ) : null}
          <DialogFooter>
            <Button onClick={() => setOpen(false)} type="button" variant="outline">
              Annuler
            </Button>
            <Button disabled={!email.trim() || !changed || saving} type="submit">
              {saving ? "Modification…" : "Changer l’adresse"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
