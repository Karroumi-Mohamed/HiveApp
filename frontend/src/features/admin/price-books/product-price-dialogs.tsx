import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { ProductPrice, ProductPriceAction, ProductPriceBlocker } from "@/api/contracts";
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
import {
  adminCommercialKeys,
  commercialQueryEnabled,
  invalidateAdminCommercial,
} from "@/features/commercial/commercial-query";
import { commercialAmount } from "@/lib/exact-decimal";
import {
  canUseProductPriceAction,
  instantFromLocalValue,
  localDateTimeValue,
  type ProductPriceDraftErrors,
  type ProductPriceDraftFields,
  productPriceActivationReady,
  productPriceActivationReviewReady,
  productPriceBlocker,
  validateProductPriceDraft,
} from "./product-price-rules";
import { ProductPriceTermsForm } from "./product-price-terms-form";

function mutationMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.code === "STALE_ACTIVATION_PREVIEW") {
      return "La vérification n’est plus actuelle. Relisez le résultat puis recommencez.";
    }
    if (error.code === "STALE_RESOURCE_VERSION") return "Le tarif a changé. Les données ont été rechargées.";
    if (error.code === "PRICE_ENTRY_OVERLAP") return "Un tarif actif couvre déjà cette période.";
    return error.message;
  }
  return "L’opération n’a pas pu être exécutée.";
}

function PriceDialog({
  open,
  onOpenChange,
  trigger,
  children,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  trigger: ReactNode;
  children: ReactNode;
}) {
  return (
    <Dialog onOpenChange={onOpenChange} open={open}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>{children}</DialogContent>
    </Dialog>
  );
}

export function EditProductPriceDialog({ price, trigger }: { price: ProductPrice; trigger: ReactNode }) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [fields, setFields] = useState<ProductPriceDraftFields>({
    amount: String(price.amount),
    currencyCode: price.currencyCode,
    billingCycle: price.billingCycle,
    effectiveFrom: localDateTimeValue(price.effectiveFrom),
    effectiveUntil: "",
  });
  const [errors, setErrors] = useState<ProductPriceDraftErrors>({});
  useEffect(() => {
    if (!open) return;
    setFields({
      amount: String(price.amount),
      currencyCode: price.currencyCode,
      billingCycle: price.billingCycle,
      effectiveFrom: localDateTimeValue(price.effectiveFrom),
      effectiveUntil: "",
    });
    setErrors({});
  }, [open, price]);

  const update = useMutation({
    mutationFn: () =>
      adminApi.updateProductPrice(price.id, {
        amount: commercialAmount(fields.amount),
        currencyCode: fields.currencyCode.trim().toUpperCase(),
        billingCycle: fields.billingCycle,
        effectiveFrom: instantFromLocalValue(fields.effectiveFrom),
        effectiveUntil: null,
        version: price.version,
      }),
    onSuccess: async () => {
      await invalidateAdminCommercial(queryClient, adminCommercialKeys.priceBooks.all());
      setOpen(false);
      toast.success("Brouillon mis à jour");
    },
    onError: async (error) => {
      await queryClient.invalidateQueries({ queryKey: adminCommercialKeys.priceBooks.detail(price.id) });
      toast.error(mutationMessage(error));
    },
  });

  const submit = () => {
    const next = validateProductPriceDraft(fields);
    setErrors(next);
    if (Object.keys(next).length === 0) update.mutate();
  };

  return (
    <PriceDialog onOpenChange={setOpen} open={open} trigger={trigger}>
      <DialogHeader>
        <DialogTitle>Modifier le brouillon</DialogTitle>
        <DialogDescription>
          Seuls les brouillons sont modifiables. Une mise en vente fige ces conditions.
        </DialogDescription>
      </DialogHeader>
      <ProductPriceTermsForm errors={errors} fields={fields} onChange={setFields} />
      <DialogFooter>
        <Button onClick={() => setOpen(false)} variant="outline">
          Annuler
        </Button>
        <Button disabled={update.isPending} onClick={submit}>
          {update.isPending ? "Enregistrement…" : "Enregistrer"}
        </Button>
      </DialogFooter>
    </PriceDialog>
  );
}

type ReasonAction = "PAUSE" | "REVISE" | "ARCHIVE";

const reasonActionCopy: Record<
  ReasonAction,
  { title: string; description: string; confirm: string; destructive?: boolean }
> = {
  PAUSE: {
    title: "Suspendre ce tarif ?",
    description:
      "Il ne sera plus proposé aux nouvelles ventes. Les abonnements existants gardent leur tarif enregistré.",
    confirm: "Suspendre la vente",
  },
  REVISE: {
    title: "Préparer le tarif suivant ?",
    description:
      "Un brouillon copiera ce tarif pour préparer son remplacement. Le tarif en vente et les abonnements existants ne changent pas à cette étape.",
    confirm: "Créer le brouillon",
  },
  ARCHIVE: {
    title: "Archiver définitivement ce tarif ?",
    description:
      "L’archivage est terminal. Le tarif restera dans l’historique, mais ne pourra plus être modifié ni remis en vente.",
    confirm: "Archiver",
    destructive: true,
  },
};

export function ProductPriceReasonDialog({
  price,
  action,
  trigger,
}: {
  price: ProductPrice;
  action: ReasonAction;
  trigger: ReactNode;
}) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const copy = reasonActionCopy[action];
  const mutation = useMutation({
    mutationFn: () => {
      if (action === "PAUSE") return adminApi.pauseProductPrice(price.id, price.version, reason);
      if (action === "REVISE") return adminApi.reviseProductPrice(price.id, price.version, reason);
      return adminApi.archiveProductPrice(price.id, price.version, reason);
    },
    onSuccess: async (result) => {
      await invalidateAdminCommercial(queryClient, adminCommercialKeys.priceBooks.all());
      setOpen(false);
      setReason("");
      toast.success(action === "REVISE" ? "Nouveau tarif préparé en brouillon" : "Statut du tarif mis à jour");
      if (action === "REVISE") navigate(`/admin/price-books/${result.id}`);
    },
    onError: async (error) => {
      await queryClient.invalidateQueries({ queryKey: adminCommercialKeys.priceBooks.detail(price.id) });
      toast.error(mutationMessage(error));
    },
  });

  return (
    <PriceDialog onOpenChange={setOpen} open={open} trigger={trigger}>
      <DialogHeader>
        <DialogTitle>{copy.title}</DialogTitle>
        <DialogDescription>{copy.description}</DialogDescription>
      </DialogHeader>
      <div className="space-y-2">
        <Label htmlFor={`price-reason-${action}`}>Motif de l’opération</Label>
        <Textarea
          id={`price-reason-${action}`}
          maxLength={500}
          onChange={(event) => setReason(event.target.value)}
          placeholder="Décision, contexte ou référence interne…"
          rows={4}
          value={reason}
        />
        <p className="text-end text-xs text-muted-foreground tabular-nums">{reason.trim().length}/500</p>
      </div>
      <DialogFooter>
        <Button onClick={() => setOpen(false)} variant="outline">
          Annuler
        </Button>
        <Button
          disabled={!reason.trim() || mutation.isPending}
          onClick={() => mutation.mutate()}
          variant={copy.destructive ? "destructive" : "default"}
        >
          {mutation.isPending ? "Traitement…" : copy.confirm}
        </Button>
      </DialogFooter>
    </PriceDialog>
  );
}

export function ProductPriceActivationDialog({ price, trigger }: { price: ProductPrice; trigger: ReactNode }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [evidenceClock, setEvidenceClock] = useState(() => Date.now());
  const activationPreviewKey = adminCommercialKeys.priceBooks.activationPreview(price.id, price.version);
  const changeOpen = (next: boolean) => {
    setOpen(next);
    if (next) {
      setEvidenceClock(Date.now());
      return;
    }
    setReason("");
    queryClient.removeQueries({ queryKey: adminCommercialKeys.priceBooks.activationPreviews(price.id) });
  };
  const preview = useQuery({
    queryKey: activationPreviewKey,
    queryFn: () => adminApi.previewProductPriceActivation(price.id),
    enabled: commercialQueryEnabled(session.can, adminPermissions.priceBooksPreviewActivation, open),
    staleTime: 0,
  });
  const previewExpiresAt = preview.data?.expiresAt;
  const action: ProductPriceAction = price.status === "INACTIVE" ? "REACTIVATE" : "ACTIVATE";
  const previewReady = productPriceActivationReviewReady(
    price,
    { data: preview.data, isFetching: preview.isFetching, isError: preview.isError },
    evidenceClock,
  );
  useEffect(() => {
    if (!open || !previewExpiresAt) return;
    const expiresAt = Date.parse(previewExpiresAt);
    if (!Number.isFinite(expiresAt)) return;
    const timer = window.setTimeout(() => setEvidenceClock(Date.now()), Math.max(0, expiresAt - Date.now() + 1));
    return () => window.clearTimeout(timer);
  }, [open, previewExpiresAt]);
  const activate = useMutation({
    mutationFn: () => {
      if (
        !preview.data ||
        !productPriceActivationReviewReady(
          price,
          { data: preview.data, isFetching: preview.isFetching, isError: preview.isError },
          Date.now(),
        )
      ) {
        throw new Error("Activation preview is not current.");
      }
      const request = {
        version: preview.data.expectedVersion,
        reason: reason.trim(),
        activationPreviewToken: preview.data.previewToken,
      };
      return action === "REACTIVATE"
        ? adminApi.reactivateProductPrice(price.id, request)
        : adminApi.activateProductPrice(price.id, request);
    },
    onSuccess: async () => {
      changeOpen(false);
      await invalidateAdminCommercial(queryClient, adminCommercialKeys.priceBooks.all());
      toast.success("Tarif mis en vente");
    },
    onError: async (error) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: adminCommercialKeys.priceBooks.detail(price.id), exact: true }),
        preview.refetch(),
      ]);
      setEvidenceClock(Date.now());
      toast.error(mutationMessage(error));
    },
  });
  const canActivate = canUseProductPriceAction(price, action, session.can);
  const retainedPreviewIsStale = Boolean(
    preview.data &&
      !preview.isFetching &&
      !preview.isError &&
      !productPriceActivationReady(price, preview.data, evidenceClock),
  );

  return (
    <PriceDialog onOpenChange={changeOpen} open={open} trigger={trigger}>
      <DialogHeader>
        <DialogTitle>
          {action === "ACTIVATE" ? "Mettre ce tarif en vente ?" : "Remettre ce tarif en vente ?"}
        </DialogTitle>
        <DialogDescription>
          Cette opération ne modifie jamais les conditions déjà enregistrées dans les abonnements existants.
        </DialogDescription>
      </DialogHeader>
      <div aria-live="polite">
        {preview.isFetching ? (
          <p className="py-4 text-sm text-muted-foreground" role="status">
            Vérification serveur…
          </p>
        ) : preview.isError ? (
          <div className="rounded-lg border border-destructive/30 p-4 text-sm" role="alert">
            <p>La prévisualisation n’a pas pu être chargée.</p>
            <Button className="mt-3" onClick={() => void preview.refetch()} size="sm" variant="outline">
              Réessayer
            </Button>
          </div>
        ) : retainedPreviewIsStale ? (
          <div className="rounded-lg border border-warning/30 bg-warning/5 p-4 text-sm" role="status">
            <p>La vérification a expiré ou le tarif affiché a changé.</p>
            <Button className="mt-3" onClick={() => void preview.refetch()} size="sm" variant="outline">
              Recalculer
            </Button>
          </div>
        ) : preview.data?.blockers.length ? (
          <div className="rounded-lg border border-warning/30 bg-warning/5 p-4">
            <p className="text-sm font-medium">Mise en vente bloquée</p>
            <ul className="mt-2 list-disc space-y-1 ps-5 text-sm text-muted-foreground">
              {preview.data.blockers.map((blocker: ProductPriceBlocker) => (
                <li key={blocker}>{productPriceBlocker[blocker]}</li>
              ))}
            </ul>
          </div>
        ) : preview.data?.activatable ? (
          <p className="rounded-lg border border-success/30 bg-success/5 p-4 text-sm" role="status">
            Aucun chevauchement : ce tarif peut être mis en vente.
          </p>
        ) : null}
      </div>
      <div className="space-y-2">
        <Label htmlFor="price-activation-reason">Motif de l’opération</Label>
        <Textarea
          id="price-activation-reason"
          maxLength={500}
          name="activation-reason"
          onChange={(event) => setReason(event.target.value)}
          placeholder="Décision, contexte ou référence interne…"
          rows={4}
          autoComplete="off"
          value={reason}
        />
      </div>
      <DialogFooter>
        <Button onClick={() => changeOpen(false)} variant="outline">
          Annuler
        </Button>
        <Button
          disabled={!previewReady || !canActivate || !reason.trim() || activate.isPending}
          onClick={() => activate.mutate()}
        >
          {activate.isPending
            ? action === "REACTIVATE"
              ? "Remise en vente…"
              : "Mise en vente…"
            : action === "REACTIVATE"
              ? "Remettre en vente"
              : "Mettre en vente"}
        </Button>
      </DialogFooter>
    </PriceDialog>
  );
}

export function DeleteProductPriceDialog({ price, trigger }: { price: ProductPrice; trigger: ReactNode }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [confirmation, setConfirmation] = useState("");
  const remove = useMutation({
    mutationFn: () => adminApi.deleteProductPrice(price.id, price.version),
    onSuccess: async () => {
      await invalidateAdminCommercial(queryClient, adminCommercialKeys.priceBooks.all());
      toast.success("Brouillon supprimé");
      navigate("/admin/price-books");
    },
    onError: async (error) => {
      await queryClient.invalidateQueries({ queryKey: adminCommercialKeys.priceBooks.detail(price.id) });
      toast.error(mutationMessage(error));
    },
  });
  return (
    <PriceDialog onOpenChange={setOpen} open={open} trigger={trigger}>
      <DialogHeader>
        <DialogTitle>Supprimer ce brouillon ?</DialogTitle>
        <DialogDescription>
          Seul ce tarif non publié sera supprimé. Le produit reste intact. Saisissez son nom pour confirmer.
        </DialogDescription>
      </DialogHeader>
      <div className="space-y-2">
        <p className="select-all rounded-md border bg-muted/40 px-3 py-2 text-sm font-medium">{price.productName}</p>
        <Label htmlFor="delete-product-price-confirmation">Nom du produit</Label>
        <Input
          autoComplete="off"
          id="delete-product-price-confirmation"
          onChange={(event) => setConfirmation(event.target.value)}
          value={confirmation}
        />
      </div>
      <DialogFooter>
        <Button onClick={() => setOpen(false)} variant="outline">
          Annuler
        </Button>
        <Button
          disabled={confirmation !== price.productName || remove.isPending}
          onClick={() => remove.mutate()}
          variant="destructive"
        >
          Supprimer le brouillon
        </Button>
      </DialogFooter>
    </PriceDialog>
  );
}
