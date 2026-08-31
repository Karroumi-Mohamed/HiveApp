import { PencilSimpleIcon } from "@phosphor-icons/react";
import type { QueryKey } from "@tanstack/react-query";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import type { AccountBillingProfile, AccountBillingProfileInput } from "@/api/contracts";
import { ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";

type Props = {
  canRead: boolean;
  canUpdate: boolean;
  load: () => Promise<AccountBillingProfile>;
  save: (input: AccountBillingProfileInput) => Promise<AccountBillingProfile>;
  queryKey: QueryKey;
};

const blank: AccountBillingProfileInput = {
  legalName: "",
  billingEmail: "",
  taxId: "",
  address: "",
  countryCode: "",
};

export function BillingProfilePanel({ canRead, canUpdate, load, save, queryKey }: Props) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<AccountBillingProfileInput>(blank);
  const profile = useQuery({ queryKey, queryFn: load, enabled: canRead, retry: false });
  useEffect(() => {
    if (!profile.data) return;
    setForm({
      legalName: profile.data.legalName,
      billingEmail: profile.data.billingEmail ?? "",
      taxId: profile.data.taxId ?? "",
      address: profile.data.address ?? "",
      countryCode: profile.data.countryCode ?? "",
    });
  }, [profile.data]);
  const update = useMutation({
    mutationFn: save,
    onSuccess: (next) => {
      queryClient.setQueryData(queryKey, next);
      setOpen(false);
      toast.success("Coordonnées de facturation enregistrées");
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : "Enregistrement impossible"),
  });
  if (!canRead) return null;
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <div className="flex items-center justify-between gap-4 border-b px-5 py-4">
        <div className="flex items-center gap-3">
          <h2 className="font-semibold">Coordonnées de facturation</h2>
          {profile.data && !profile.data.explicitlyConfigured ? (
            <span className="text-xs font-medium text-warning">À compléter</span>
          ) : null}
        </div>
        {canUpdate ? (
          <Button onClick={() => setOpen(true)} size="sm" variant="ghost">
            <PencilSimpleIcon /> Modifier
          </Button>
        ) : null}
      </div>
      {profile.isLoading ? (
        <div className="p-5">
          <LoadingState rows={3} />
        </div>
      ) : profile.isError ? (
        <ErrorState retry={() => void profile.refetch()} />
      ) : profile.data ? (
        <dl className="grid gap-px bg-border sm:grid-cols-2 lg:grid-cols-3">
          {[
            ["Raison sociale", profile.data.legalName],
            ["E-mail de facturation", profile.data.billingEmail],
            ["Identifiant fiscal", profile.data.taxId],
            ["Pays", profile.data.countryCode],
            ["Adresse", profile.data.address],
          ].map(([label, value]) => (
            <div className="bg-card px-5 py-4" key={label}>
              <dt className="text-xs text-muted-foreground">{label}</dt>
              <dd className="mt-1 text-sm font-medium whitespace-pre-line">{value || "—"}</dd>
            </div>
          ))}
        </dl>
      ) : null}
      <Dialog onOpenChange={(value) => !update.isPending && setOpen(value)} open={open}>
        <DialogContent className="sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>Coordonnées de facturation</DialogTitle>
            <DialogDescription>Ces données seront figées sur les prochains documents émis.</DialogDescription>
          </DialogHeader>
          <form
            className="grid gap-5 sm:grid-cols-2"
            onSubmit={(event) => {
              event.preventDefault();
              if (!form.legalName.trim()) return;
              update.mutate({
                ...form,
                legalName: form.legalName.trim(),
                billingEmail: form.billingEmail?.trim() || null,
                taxId: form.taxId?.trim() || null,
                address: form.address?.trim() || null,
                countryCode: form.countryCode?.trim().toUpperCase() || null,
              });
            }}
          >
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="billing-legal-name">Raison sociale</Label>
              <Input
                id="billing-legal-name"
                onChange={(e) => setForm({ ...form, legalName: e.target.value })}
                required
                value={form.legalName}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="billing-email">E-mail de facturation</Label>
              <Input
                id="billing-email"
                onChange={(e) => setForm({ ...form, billingEmail: e.target.value })}
                type="email"
                value={form.billingEmail ?? ""}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="billing-tax-id">Identifiant fiscal</Label>
              <Input
                id="billing-tax-id"
                onChange={(e) => setForm({ ...form, taxId: e.target.value })}
                value={form.taxId ?? ""}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="billing-country">Pays (ISO)</Label>
              <Input
                id="billing-country"
                maxLength={2}
                onChange={(e) => setForm({ ...form, countryCode: e.target.value.toUpperCase() })}
                value={form.countryCode ?? ""}
              />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="billing-address">Adresse</Label>
              <Textarea
                id="billing-address"
                onChange={(e) => setForm({ ...form, address: e.target.value })}
                value={form.address ?? ""}
              />
            </div>
            <div className="flex justify-end gap-2 sm:col-span-2">
              <Button disabled={update.isPending} onClick={() => setOpen(false)} type="button" variant="outline">
                Annuler
              </Button>
              <Button disabled={update.isPending || !form.legalName.trim()} type="submit">
                {update.isPending ? "Enregistrement…" : "Enregistrer"}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </section>
  );
}
