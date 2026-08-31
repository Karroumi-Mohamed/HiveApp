import { CheckCircleIcon, WarningCircleIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useRef, useState } from "react";
import { toast } from "sonner";
import { adminOfferApi } from "@/api/admin-offer-api";
import { ApiError } from "@/api/http";
import type { OfferAction, OfferOperationState } from "@/api/offer-contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
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
import { adminCommercialKeys, commercialQueryEnabled } from "@/features/commercial/commercial-query";
import { useDebouncedValue } from "@/lib/use-debounced-value";
import { newIdempotencyKey, offerBlocker, offerEligibilityBlocker } from "./offer-rules";

const mutationLabels: Partial<Record<OfferAction, { title: string; confirm: string; destructive?: boolean }>> = {
  RETIRE: { title: "Retirer l’offre", confirm: "Retirer", destructive: true },
  ARCHIVE: { title: "Archiver l’offre", confirm: "Archiver", destructive: true },
  DELETE_DRAFT: { title: "Supprimer le brouillon", confirm: "Supprimer", destructive: true },
  REVISE: { title: "Créer une révision", confirm: "Créer la révision" },
};

async function invalidateOffer(queryClient: ReturnType<typeof useQueryClient>, offerId: string) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: adminCommercialKeys.offers.detail(offerId) }),
    queryClient.invalidateQueries({ queryKey: adminCommercialKeys.offers.operations(offerId) }),
    queryClient.invalidateQueries({ queryKey: adminCommercialKeys.offers.all() }),
  ]);
}

export function OfferReasonDialog({
  action,
  offer,
  trigger,
}: {
  action: "RETIRE" | "ARCHIVE" | "DELETE_DRAFT" | "REVISE";
  offer: OfferOperationState;
  trigger: ReactNode;
}) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const config = mutationLabels[action] as NonNullable<(typeof mutationLabels)[typeof action]>;
  const mutation = useMutation({
    mutationFn: () => {
      const input = { version: offer.version, reason: reason.trim() };
      if (action === "RETIRE") return adminOfferApi.retire(offer.id, input);
      if (action === "ARCHIVE") return adminOfferApi.archive(offer.id, input);
      if (action === "REVISE") return adminOfferApi.revise(offer.id, input);
      return adminOfferApi
        .deleteDraft(offer.id, input)
        .then(() => ({ offerId: offer.id, status: "ARCHIVED" as const, version: offer.version }));
    },
    onSuccess: async (result) => {
      setOpen(false);
      await invalidateOffer(queryClient, offer.id);
      toast.success(action === "REVISE" ? "Révision créée" : "Opération terminée");
      if (action === "REVISE") window.location.assign(`/admin/offers/${result.offerId}`);
      if (action === "DELETE_DRAFT") window.location.assign("/admin/offers");
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : "L’opération a échoué."),
  });
  return (
    <Dialog
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) setReason("");
      }}
      open={open}
    >
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{config.title}</DialogTitle>
          <DialogDescription>Cette action et son motif seront conservés dans l’historique.</DialogDescription>
        </DialogHeader>
        <div className="space-y-2">
          <Label htmlFor={`offer-reason-${action}`}>Motif</Label>
          <Textarea
            id={`offer-reason-${action}`}
            maxLength={500}
            onChange={(event) => setReason(event.target.value)}
            rows={3}
            value={reason}
          />
        </div>
        <DialogFooter>
          <Button onClick={() => setOpen(false)} variant="ghost">
            Annuler
          </Button>
          <Button
            disabled={!reason.trim() || mutation.isPending}
            onClick={() => mutation.mutate()}
            variant={config.destructive ? "destructive" : "default"}
          >
            {mutation.isPending ? "Traitement…" : config.confirm}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function OfferDuplicateDialog({ offer, trigger }: { offer: OfferOperationState; trigger: ReactNode }) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState(`${offer.name} — copie`);
  const [reason, setReason] = useState("");
  const mutation = useMutation({
    mutationFn: () =>
      adminOfferApi.duplicate(offer.id, { version: offer.version, name: name.trim(), reason: reason.trim() }),
    onSuccess: async (result) => {
      await queryClient.invalidateQueries({ queryKey: adminCommercialKeys.offers.all() });
      toast.success("Offre dupliquée");
      window.location.assign(`/admin/offers/${result.offerId}/edit`);
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : "La duplication a échoué."),
  });
  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Dupliquer l’offre</DialogTitle>
          <DialogDescription>
            La copie devient un brouillon indépendant. Les acceptations ne sont jamais copiées.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="offer-copy-name">Nom de la copie</Label>
            <Input
              id="offer-copy-name"
              maxLength={180}
              onChange={(event) => setName(event.target.value)}
              value={name}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="offer-copy-reason">Motif</Label>
            <Textarea
              id="offer-copy-reason"
              maxLength={500}
              onChange={(event) => setReason(event.target.value)}
              rows={3}
              value={reason}
            />
          </div>
        </div>
        <DialogFooter>
          <Button onClick={() => setOpen(false)} variant="ghost">
            Annuler
          </Button>
          <Button disabled={!name.trim() || !reason.trim() || mutation.isPending} onClick={() => mutation.mutate()}>
            {mutation.isPending ? "Duplication…" : "Dupliquer"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function OfferPublicationDialog({
  mode,
  offer,
  trigger,
}: {
  mode: "PUBLISH" | "RESTORE";
  offer: OfferOperationState;
  trigger: ReactNode;
}) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const preview = useMutation({ mutationFn: () => adminOfferApi.publicationPreview(offer.id) });
  const mutation = useMutation({
    mutationFn: () => {
      if (!preview.data?.ready || preview.data.mode !== mode) throw new Error("Preview required");
      const input = {
        version: preview.data.expectedVersion,
        reason: reason.trim(),
        previewToken: preview.data.previewToken,
      };
      return mode === "PUBLISH" ? adminOfferApi.publish(offer.id, input) : adminOfferApi.restore(offer.id, input);
    },
    onSuccess: async () => {
      setOpen(false);
      await invalidateOffer(queryClient, offer.id);
      toast.success(mode === "PUBLISH" ? "Offre publiée" : "Offre restaurée");
    },
    onError: async (error) => {
      preview.reset();
      preview.mutate();
      await invalidateOffer(queryClient, offer.id);
      toast.error(error instanceof ApiError ? error.message : "L’opération a échoué.");
    },
  });
  const title = mode === "PUBLISH" ? "Publier l’offre" : "Restaurer l’offre";
  return (
    <Dialog
      onOpenChange={(next) => {
        setOpen(next);
        if (next) preview.mutate();
        else {
          preview.reset();
          setReason("");
        }
      }}
      open={open}
    >
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>
            La définition et les conditions commerciales sont vérifiées par le serveur avant l’action.
          </DialogDescription>
        </DialogHeader>
        {preview.isPending ? (
          <p className="text-sm text-muted-foreground">Vérification…</p>
        ) : preview.isError ? (
          <Alert variant="destructive">
            <WarningCircleIcon />
            <AlertTitle>Vérification impossible</AlertTitle>
            <AlertDescription>
              <Button className="mt-2" onClick={() => preview.mutate()} size="sm" variant="outline">
                Réessayer
              </Button>
            </AlertDescription>
          </Alert>
        ) : preview.data?.ready ? (
          <Alert>
            <CheckCircleIcon />
            <AlertTitle>Prête</AlertTitle>
            <AlertDescription>La vérification expire automatiquement; confirmez avec un motif.</AlertDescription>
          </Alert>
        ) : preview.data ? (
          <Alert variant="destructive">
            <WarningCircleIcon />
            <AlertTitle>Publication bloquée</AlertTitle>
            <AlertDescription>
              <ul className="list-disc ps-5">
                {preview.data.blockers.map((item) => (
                  <li key={item}>{offerBlocker[item]}</li>
                ))}
                {preview.data.definitionIssues.map((issue) => (
                  <li key={`${issue.fieldPath}:${issue.code}`}>{issue.message}</li>
                ))}
              </ul>
            </AlertDescription>
          </Alert>
        ) : null}
        <div className="space-y-2">
          <Label htmlFor={`offer-publication-reason-${mode}`}>Motif</Label>
          <Textarea
            id={`offer-publication-reason-${mode}`}
            maxLength={500}
            onChange={(event) => setReason(event.target.value)}
            rows={3}
            value={reason}
          />
        </div>
        <DialogFooter>
          <Button onClick={() => setOpen(false)} variant="ghost">
            Annuler
          </Button>
          <Button
            disabled={!preview.data?.ready || preview.data.mode !== mode || !reason.trim() || mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending ? "Traitement…" : mode === "PUBLISH" ? "Publier" : "Restaurer"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function OfferApplyAccountDialog({ offer, trigger }: { offer: OfferOperationState; trigger: ReactNode }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const debounced = useDebouncedValue(search);
  const [accountId, setAccountId] = useState("");
  const [reason, setReason] = useState("");
  const key = useRef(newIdempotencyKey());
  const choices = useQuery({
    queryKey: adminCommercialKeys.offers.accountChoices({ search: debounced, page: 0 }),
    queryFn: ({ signal }) =>
      adminOfferApi.accountChoices({ search: debounced || undefined, active: true, page: 0, size: 20 }, { signal }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.offersChooseAccounts, open),
  });
  const preview = useMutation({ mutationFn: () => adminOfferApi.previewForAccount(offer.id, accountId) });
  const apply = useMutation({
    mutationFn: () => {
      if (!preview.data?.eligible || !preview.data.preview) throw new Error("Preview required");
      return adminOfferApi.applyForAccount(offer.id, accountId, key.current, {
        previewToken: preview.data.preview.previewToken,
        reason: reason.trim(),
      });
    },
    onSuccess: async (result) => {
      setOpen(false);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: adminCommercialKeys.offers.stats(offer.id) }),
        queryClient.invalidateQueries({ queryKey: adminCommercialKeys.offers.redemptions(offer.id, {}) }),
      ]);
      toast.success(result.nextAction === "TRACK_OPERATION" ? "Offre acceptée; opération créée" : "Offre acceptée");
    },
    onError: (error) => {
      if (error instanceof ApiError) key.current = newIdempotencyKey();
      toast.error(
        error instanceof ApiError ? error.message : "Résultat réseau incertain. Réessayez avec la même demande.",
      );
    },
  });
  const reset = () => {
    setSearch("");
    setAccountId("");
    setReason("");
    preview.reset();
    key.current = newIdempotencyKey();
  };
  return (
    <Dialog
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) reset();
      }}
      open={open}
    >
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Appliquer à un compte</DialogTitle>
          <DialogDescription>
            Sélectionnez un compte, vérifiez le résultat exact, puis enregistrez le motif.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="offer-account-search">Compte</Label>
            <Input
              id="offer-account-search"
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Rechercher par nom ou identifiant…"
              value={search}
            />
            <div className="max-h-40 overflow-y-auto border-y">
              {choices.data?.content.map((account) => (
                <label className="flex cursor-pointer items-center gap-3 border-b py-2 last:border-0" key={account.id}>
                  <input
                    checked={accountId === account.id}
                    className="accent-primary"
                    name="offer-account"
                    onChange={() => {
                      setAccountId(account.id);
                      preview.reset();
                      key.current = newIdempotencyKey();
                    }}
                    type="radio"
                  />
                  <span>
                    <span className="block text-sm font-medium">{account.name}</span>
                    <span className="block text-xs text-muted-foreground">{account.slug}</span>
                  </span>
                </label>
              ))}
              {choices.data && !choices.data.totalElements ? (
                <p className="py-4 text-sm text-muted-foreground">Aucun compte actif.</p>
              ) : null}
            </div>
          </div>
          <Button
            disabled={!accountId || preview.isPending}
            onClick={() => preview.mutate()}
            type="button"
            variant="outline"
          >
            {preview.isPending ? "Vérification…" : "Vérifier ce compte"}
          </Button>
          {preview.data ? (
            preview.data.eligible && preview.data.preview ? (
              <Alert>
                <CheckCircleIcon />
                <AlertTitle>Compte éligible</AlertTitle>
                <AlertDescription>
                  Prix final: {preview.data.preview.finalPrice} {preview.data.preview.currencyCode}.{" "}
                  {preview.data.preview.change.checkoutRequired
                    ? "Un paiement serait requis."
                    : "Aucun paiement supplémentaire."}
                </AlertDescription>
              </Alert>
            ) : (
              <Alert variant="destructive">
                <WarningCircleIcon />
                <AlertTitle>Offre non applicable</AlertTitle>
                <AlertDescription>
                  <ul className="list-disc ps-5">
                    {preview.data.blockers.map((blocker) => (
                      <li key={blocker}>{offerEligibilityBlocker[blocker]}</li>
                    ))}
                  </ul>
                </AlertDescription>
              </Alert>
            )
          ) : null}
          <div className="space-y-2">
            <Label htmlFor="offer-account-reason">Motif</Label>
            <Textarea
              id="offer-account-reason"
              maxLength={500}
              onChange={(event) => setReason(event.target.value)}
              rows={3}
              value={reason}
            />
          </div>
        </div>
        <DialogFooter>
          <Button onClick={() => setOpen(false)} variant="ghost">
            Annuler
          </Button>
          <Button
            disabled={!preview.data?.eligible || !preview.data.preview || !reason.trim() || apply.isPending}
            onClick={() => apply.mutate()}
          >
            {apply.isPending ? "Application…" : "Appliquer l’offre"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
