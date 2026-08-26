import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useState } from "react";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { ProductPrice } from "@/api/contracts";
import { ApiError } from "@/api/http";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
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
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  adminCommercialKeys,
  commercialQueryEnabled,
  invalidateAdminCommercial,
} from "@/features/commercial/commercial-query";
import { productPriceReplacementBlocker } from "./product-price-rules";

function money(price: ProductPrice) {
  return new Intl.NumberFormat("fr-MA", { style: "currency", currency: price.currencyCode }).format(price.amount);
}

function dateTime(value: string) {
  return new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function replacementError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.code === "STALE_RESOURCE_VERSION") return "Un tarif a changé. Les deux révisions ont été rechargées.";
    return error.message;
  }
  return "Le remplacement n’a pas pu être programmé.";
}

export function ProductPriceReplacementDialog({ successor, trigger }: { successor: ProductPrice; trigger: ReactNode }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const currentId = successor.sourcePriceId ?? "";
  const current = useQuery({
    queryKey: adminCommercialKeys.priceBooks.detail(currentId),
    queryFn: () => adminApi.productPrice(currentId),
    enabled: commercialQueryEnabled(session.can, adminPermissions.priceBooksRead, open && Boolean(currentId)),
  });
  const preview = useQuery({
    queryKey: adminCommercialKeys.priceBooks.replacementPreview(
      successor.id,
      current.data?.version ?? -1,
      successor.version,
    ),
    queryFn: () =>
      adminApi.previewProductPriceReplacement(successor.id, {
        currentPriceId: current.data?.id ?? "",
        currentVersion: current.data?.version ?? -1,
        successorVersion: successor.version,
      }),
    enabled: commercialQueryEnabled(
      session.can,
      adminPermissions.priceBooksPreviewReplacement,
      open && Boolean(current.data),
    ),
  });
  const schedule = useMutation({
    mutationFn: () => {
      if (!current.data) throw new Error("Tarif actuel indisponible");
      return adminApi.scheduleProductPriceReplacement(successor.id, {
        currentPriceId: current.data.id,
        currentVersion: current.data.version,
        successorVersion: successor.version,
        reason,
      });
    },
    onSuccess: async (result) => {
      await invalidateAdminCommercial(queryClient, adminCommercialKeys.priceBooks.all());
      setOpen(false);
      setReason("");
      toast.success(result.existingResult ? "Ce remplacement était déjà programmé" : "Remplacement programmé");
    },
    onError: async (error) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: adminCommercialKeys.priceBooks.detail(successor.id) }),
        queryClient.invalidateQueries({ queryKey: adminCommercialKeys.priceBooks.detail(currentId) }),
      ]);
      toast.error(replacementError(error));
    },
  });
  const canSchedule = session.can(adminPermissions.priceBooksScheduleReplacement);

  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Programmer le nouveau tarif</DialogTitle>
          <DialogDescription>
            Le tarif actuel s’arrêtera à la date de début de cette révision, sans modifier les abonnements existants.
          </DialogDescription>
        </DialogHeader>
        {current.isLoading ? (
          <LoadingState rows={3} />
        ) : current.isError || !current.data ? (
          <ErrorState retry={() => void current.refetch()} title="Tarif actuel indisponible" />
        ) : !session.can(adminPermissions.priceBooksPreviewReplacement) ? (
          <PermissionState description="Votre rôle ne permet pas de vérifier un remplacement tarifaire." />
        ) : preview.isLoading ? (
          <LoadingState rows={3} />
        ) : preview.isError ? (
          <ErrorState retry={() => void preview.refetch()} title="Prévisualisation indisponible" />
        ) : preview.data ? (
          <div className="space-y-5">
            <dl className="grid gap-px overflow-hidden rounded-lg border bg-border sm:grid-cols-3">
              <div className="bg-card p-4">
                <dt className="text-xs text-muted-foreground">Tarif actuel</dt>
                <dd className="mt-1 font-semibold tabular-nums">{money(current.data)}</dd>
              </div>
              <div className="bg-card p-4">
                <dt className="text-xs text-muted-foreground">Nouveau tarif</dt>
                <dd className="mt-1 font-semibold tabular-nums">{money(successor)}</dd>
              </div>
              <div className="bg-card p-4">
                <dt className="text-xs text-muted-foreground">Basculement</dt>
                <dd className="mt-1 text-sm font-medium">{dateTime(preview.data.cutoff)}</dd>
              </div>
            </dl>
            {preview.data.blockers.length ? (
              <div className="rounded-lg border border-warning/30 bg-warning/5 p-4">
                <p className="text-sm font-medium">Le remplacement est bloqué</p>
                <ul className="mt-2 list-disc space-y-1 ps-5 text-sm text-muted-foreground">
                  {preview.data.blockers.map((blocker) => (
                    <li key={blocker}>{productPriceReplacementBlocker[blocker]}</li>
                  ))}
                </ul>
              </div>
            ) : (
              <p className="rounded-lg border border-success/30 bg-success/5 p-4 text-sm">
                Les deux périodes se suivront sans chevauchement.
              </p>
            )}
            <div className="space-y-2">
              <Label htmlFor="price-replacement-reason">Motif de l’opération</Label>
              <Textarea
                id="price-replacement-reason"
                maxLength={500}
                onChange={(event) => setReason(event.target.value)}
                placeholder="Décision, référence interne ou contexte…"
                rows={4}
                value={reason}
              />
              <p className="text-end text-xs text-muted-foreground tabular-nums">{reason.trim().length}/500</p>
            </div>
            {!canSchedule ? (
              <p className="text-xs text-muted-foreground">
                Votre rôle permet la vérification, mais pas la programmation.
              </p>
            ) : null}
          </div>
        ) : null}
        <DialogFooter>
          <Button onClick={() => setOpen(false)} variant="outline">
            Annuler
          </Button>
          <Button
            disabled={!preview.data?.schedulable || !canSchedule || !reason.trim() || schedule.isPending}
            onClick={() => schedule.mutate()}
          >
            {schedule.isPending ? "Programmation…" : "Programmer le remplacement"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
