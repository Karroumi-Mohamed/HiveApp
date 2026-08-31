import { TrashIcon } from "@phosphor-icons/react";
import { cloneElement, isValidElement, useId, useState } from "react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
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

export function ConfirmActionDialog({
  trigger,
  title,
  description,
  confirmLabel,
  onConfirm,
  pending,
  destructive = false,
}: {
  trigger: React.ReactNode;
  title: string;
  description: string;
  confirmLabel: string;
  onConfirm: () => Promise<unknown>;
  pending: boolean;
  destructive?: boolean;
}) {
  const [open, setOpen] = useState(false);
  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <div className="flex justify-end gap-2">
          <Button onClick={() => setOpen(false)} type="button" variant="outline">
            Annuler
          </Button>
          <Button
            disabled={pending}
            onClick={async () => {
              try {
                await onConfirm();
                setOpen(false);
              } catch {
                // The shared mutation handler displays the backend's exact blocker.
              }
            }}
            type="button"
            variant={destructive ? "destructive" : "default"}
          >
            {confirmLabel}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

export function DeleteDraftDialog({
  name,
  label,
  onDelete,
  pending,
}: {
  name: string;
  label: string;
  onDelete: () => void;
  pending: boolean;
}) {
  const [confirmation, setConfirmation] = useState("");
  return (
    <Dialog>
      <DialogTrigger asChild>
        <Button variant="destructive">
          <TrashIcon />
          Supprimer
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Supprimer {label}</DialogTitle>
          <DialogDescription>
            Seuls les brouillons inutilisés peuvent être supprimés. Saisissez le nom exact pour confirmer.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <Input
            aria-label={`Saisissez ${name} pour confirmer`}
            onChange={(event) => setConfirmation(event.target.value)}
            value={confirmation}
          />
          <div className="flex justify-end">
            <Button disabled={confirmation !== name || pending} onClick={onDelete} variant="destructive">
              Supprimer définitivement
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

export function Field({ label, children }: { label: string; children: React.ReactNode }) {
  const id = useId();
  const control = isValidElement(children)
    ? cloneElement(children as React.ReactElement<{ id?: string }>, { id })
    : children;
  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      {control}
    </div>
  );
}

export function ChoiceList({
  label,
  options,
  selected,
  onChange,
}: {
  label: string;
  options: Array<{ label: string; value: string; description?: string; disabled?: boolean }>;
  selected: string[];
  onChange: (values: string[]) => void;
}) {
  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium">{label}</legend>
      <div className="max-h-36 divide-y overflow-y-auto rounded-md border">
        {options.length ? (
          options.map((option) => {
            const id = `${label}-${option.value}`.replaceAll(" ", "-");
            return (
              <label
                className={`flex items-center gap-3 px-3 py-2.5 text-sm ${option.disabled ? "cursor-not-allowed text-muted-foreground" : "cursor-pointer"}`}
                htmlFor={id}
                key={option.value}
              >
                <Checkbox
                  checked={selected.includes(option.value)}
                  disabled={option.disabled}
                  id={id}
                  onCheckedChange={(checked) =>
                    onChange(
                      checked
                        ? [...new Set([...selected, option.value])]
                        : selected.filter((value) => value !== option.value),
                    )
                  }
                />
                <span className="min-w-0">
                  <span className="block">{option.label}</span>
                  {option.description ? (
                    <span className="block text-xs text-muted-foreground">{option.description}</span>
                  ) : null}
                </span>
              </label>
            );
          })
        ) : (
          <p className="px-3 py-2.5 text-sm text-muted-foreground">Aucun choix disponible</p>
        )}
      </div>
    </fieldset>
  );
}

export function ChoiceLoadState({
  loading,
  error,
  onRetry,
  unavailable,
}: {
  loading: boolean;
  error: boolean;
  onRetry: () => void;
  unavailable?: string | null;
}) {
  if (unavailable) return <p className="text-xs text-warning">{unavailable}</p>;
  if (loading) return <p className="text-xs text-muted-foreground">Chargement des choix…</p>;
  if (!error) return null;
  return (
    <p className="text-xs text-destructive" role="alert">
      Les choix n’ont pas pu être chargés.{" "}
      <button className="underline underline-offset-2" onClick={onRetry} type="button">
        Réessayer
      </button>
    </p>
  );
}

export function Pair({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="text-end font-medium">{value}</dd>
    </div>
  );
}
export function ReferenceSet({ label, values }: { label: string; values: string[] }) {
  return (
    <div className="mt-4">
      <p className="text-xs text-muted-foreground">{label}</p>
      <div className="mt-2 flex flex-wrap gap-1.5">
        {values.length ? (
          values.map((value) => (
            <span className="rounded bg-muted px-2 py-1 text-xs" key={value}>
              {value}
            </span>
          ))
        ) : (
          <span className="text-sm">Aucune restriction</span>
        )}
      </div>
    </div>
  );
}
