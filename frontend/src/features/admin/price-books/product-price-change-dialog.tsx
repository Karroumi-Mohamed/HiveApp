import { useMutation, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { ProductPrice, ProductPriceChangePreview, ProductPriceChangeRequest } from "@/api/contracts";
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
import { invalidateAdminCommercial } from "@/features/commercial/commercial-query";
import { commercialAmount, formatExactMoney, isCommercialAmount } from "@/lib/exact-decimal";
import {
  canUseProductPriceAction,
  instantFromLocalValue,
  localDateTimeValue,
  productPriceCycle,
} from "./product-price-rules";

export const priceChangeBlocker: Record<string, string> = {
  CURRENT_NOT_APPLICABLE: "Ce tarif n’est plus le tarif actuel. Ouvrez celui qui est en vigueur.",
  CURRENT_VERSION_CHANGED: "Le tarif a changé depuis votre ouverture. Rechargez les données.",
  OWNER_NOT_ACTIVE: "Le produit archivé ne peut plus recevoir de changement tarifaire.",
  SCHEDULE_ALREADY_EXISTS: "Un changement est déjà programmé. Modifiez-le ou annulez-le d’abord.",
  SCHEDULE_CHANGED: "Le changement programmé a changé ou a déjà pris effet. Rechargez les données.",
  CHANGE_DATE_PASSED: "Choisissez une date future ou l’application immédiate.",
  OTHER_TARIFF_OVERLAP: "Un autre tarif couvre cette période. Aucun changement n’a été enregistré.",
  PUBLISHED_OFFERS_DEPEND_ON_PRICE:
    "Des offres publiées utilisent ce tarif au-delà du changement. Terminez ou révisez ces offres avant de continuer. Elles ne seront pas modifiées automatiquement.",
};
export const priceChangeAction = {
  CHANGE: "CHANGE_PRICE",
  RESCHEDULE: "RESCHEDULE_CHANGE",
  CANCEL: "CANCEL_CHANGE",
} as const;
export const priceChangeLabel = {
  CHANGE: "Changer le tarif",
  RESCHEDULE: "Modifier le changement",
  CANCEL: "Annuler le changement",
} as const;

/** Preserve decimal text while accepting the French decimal separator; never round money. */
export function tariffChangeAmount(value: string, currency: string): string | null {
  const amount = commercialAmount(value.replace(",", "."));
  if (!isCommercialAmount(amount)) return null;
  const digits = new Intl.NumberFormat("fr-MA", { style: "currency", currency }).resolvedOptions()
    .maximumFractionDigits;
  return /[1-9]/.test((amount.split(".")[1] ?? "").slice(digits)) ? null : amount;
}

export function priceChangeReviewReady(
  price: Pick<ProductPrice, "id" | "version">,
  review: ProductPriceChangePreview | null,
  now = Date.now(),
) {
  return Boolean(
    review?.allowed &&
      review.previewToken &&
      review.currentPrice.id === price.id &&
      review.currentPrice.version === price.version &&
      Date.parse(review.expiresAt) > now,
  );
}

export function ProductPriceChangeDialog({
  price,
  scheduled,
  operation,
  trigger,
}: {
  price: ProductPrice;
  scheduled?: ProductPrice;
  operation: ProductPriceChangeRequest["operation"];
  trigger: ReactNode;
}) {
  const session = useAdminSession();
  const navigate = useNavigate();
  const cache = useQueryClient();
  const [open, setOpen] = useState(false);
  const [amount, setAmount] = useState("");
  const [timing, setTiming] = useState<"NOW" | "SCHEDULED">("NOW");
  const [date, setDate] = useState("");
  const [reason, setReason] = useState("");
  const [review, setReview] = useState<ProductPriceChangePreview | null>(null);
  const reviewRef = useRef<HTMLElement>(null);
  useEffect(() => {
    if (review) reviewRef.current?.scrollIntoView({ block: "nearest" });
  }, [review]);
  const [key, setKey] = useState("");
  const [error, setError] = useState("");
  const [uncertain, setUncertain] = useState(false);
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (!open) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [open]);
  const canApply =
    session.can(adminPermissions.priceBooksPreviewChange) &&
    canUseProductPriceAction(price, priceChangeAction[operation], session.can);
  const cancel = operation === "CANCEL";
  const preview = useMutation({
    mutationFn: (input: ProductPriceChangeRequest) => adminApi.previewProductPriceChange(price.id, input),
    onSuccess: (value) => {
      setReview(value);
      setKey(crypto.randomUUID());
      setError("");
      setNow(Date.now());
    },
    onError: () => {
      setReview(null);
      setError("La vérification a échoué. Réessayez sans modifier le tarif actuel.");
    },
  });
  const apply = useMutation({
    mutationFn: () => {
      if (!review || !canApply || (!uncertain && !priceChangeReviewReady(price, review)))
        throw new Error("Review required");
      return adminApi.confirmProductPriceChange(price.id, {
        change: review.change,
        previewToken: review.previewToken,
        idempotencyKey: key,
      });
    },
    onSuccess: async (result) => {
      await invalidateAdminCommercial(cache);
      toast.success(
        cancel
          ? "Changement annulé. Le tarif actuel continue sans interruption."
          : timing === "NOW"
            ? "Nouveau tarif appliqué aux prochaines souscriptions."
            : "Changement programmé sans interruption de tarif.",
      );
      setOpen(false);
      if (!cancel && review?.change.timing === "NOW" && result.successorPrice) {
        navigate(`/admin/price-books/${result.successorPrice.id}`);
      }
    },
    onError: async (failure) => {
      if (failure instanceof ApiError && failure.status < 500) {
        setReview(null);
        setUncertain(false);
        setError(
          failure.status === 409
            ? "Les données ont changé. Vérifiez à nouveau avant de confirmer."
            : "Cette opération n’est pas autorisée ou ses données sont invalides.",
        );
        await invalidateAdminCommercial(cache);
      } else {
        setUncertain(true);
        setError("Résultat non confirmé. Réessayez la même demande : elle ne sera exécutée qu’une seule fois.");
      }
    },
  });
  const busy = preview.isPending || apply.isPending;
  const change = (action: () => void) => {
    action();
    setReview(null);
    setError("");
  };
  const verify = () => {
    const normalizedAmount = cancel ? null : tariffChangeAmount(amount, price.currencyCode);
    if (
      !reason.trim() ||
      (!cancel && normalizedAmount === null) ||
      (!cancel && timing === "SCHEDULED" && !Number.isFinite(Date.parse(date)))
    ) {
      setError(
        `Indiquez un motif et un montant valide en ${price.currencyCode}, sans précision supplémentaire, ainsi que la date si nécessaire.`,
      );
      return;
    }
    preview.mutate({
      operation,
      currentVersion: price.version,
      timing: cancel ? null : timing,
      amount: normalizedAmount,
      effectiveFrom: !cancel && timing === "SCHEDULED" ? instantFromLocalValue(date) : null,
      reason: reason.trim(),
    });
  };
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (busy) return;
        if (next) {
          setAmount(scheduled?.amount ?? price.amount);
          setTiming(operation === "RESCHEDULE" ? "SCHEDULED" : "NOW");
          setDate(localDateTimeValue(scheduled?.effectiveFrom ?? price.effectiveUntil));
          setReason("");
          setReview(null);
          setError("");
          setUncertain(false);
        }
        setOpen(next);
      }}
    >
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent className="flex max-h-[calc(100dvh-2rem)] flex-col overflow-hidden">
        <DialogHeader className="shrink-0">
          <DialogTitle>{priceChangeLabel[operation]}</DialogTitle>
          <DialogDescription>
            {price.productName} · {productPriceCycle[price.billingCycle]} · {price.currencyCode}. Les abonnements
            existants conservent leurs conditions.
          </DialogDescription>
        </DialogHeader>
        <div className="min-h-0 space-y-4 overflow-y-auto">
          <div className="rounded-lg border bg-muted/40 p-4 text-sm">
            Tarif actuel : <strong>{formatExactMoney(price.amount, price.currencyCode)}</strong>
            {scheduled && (
              <p className="mt-2">
                Programmé : {formatExactMoney(scheduled.amount, scheduled.currencyCode)} à partir du{" "}
                {new Date(scheduled.effectiveFrom).toLocaleString("fr-MA")}
              </p>
            )}
            {cancel && (
              <p className="mt-2">
                L’annulation conserve le tarif actuel sans date de fin. Aucun abonnement n’est modifié.
              </p>
            )}
          </div>
          <fieldset disabled={busy || uncertain} className="space-y-4">
            <legend className="sr-only">Paramètres du changement</legend>
            {!cancel && (
              <>
                <div className="space-y-2">
                  <Label htmlFor="change-amount">Nouveau montant ({price.currencyCode})</Label>
                  <Input
                    id="change-amount"
                    inputMode="decimal"
                    value={amount}
                    onChange={(event) => change(() => setAmount(event.target.value))}
                  />
                </div>
                <fieldset className="flex flex-wrap gap-4">
                  <legend className="mb-2 text-sm font-medium">Quand appliquer ce tarif ?</legend>
                  {(["NOW", "SCHEDULED"] as const).map((value) => (
                    <label key={value} className="flex items-center gap-2 text-sm">
                      <input
                        type="radio"
                        name="change-timing"
                        checked={timing === value}
                        onChange={() => change(() => setTiming(value))}
                      />
                      {value === "NOW" ? "Maintenant" : "À une date"}
                    </label>
                  ))}
                </fieldset>
                {timing === "SCHEDULED" && (
                  <div className="space-y-2">
                    <Label htmlFor="change-date">Date et heure du changement</Label>
                    <Input
                      id="change-date"
                      type="datetime-local"
                      value={date}
                      onChange={(event) => change(() => setDate(event.target.value))}
                    />
                    <p className="text-xs text-muted-foreground">
                      Heure locale de votre navigateur. Le tarif actuel continue jusqu’à cet instant précis.
                    </p>
                  </div>
                )}
              </>
            )}
            <div className="space-y-2">
              <Label htmlFor="change-reason">Motif</Label>
              <Textarea
                id="change-reason"
                maxLength={500}
                value={reason}
                onChange={(event) => change(() => setReason(event.target.value))}
              />
            </div>
          </fieldset>
          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}
          {review && (
            <section
              ref={reviewRef}
              aria-live="polite"
              className="space-y-2 rounded-lg border bg-muted/40 p-4 text-sm"
              aria-label="Vérification du changement"
            >
              {review.allowed ? (
                <>
                  <p className="font-medium">
                    {cancel
                      ? "Le tarif actuel restera en vigueur."
                      : review.change.timing === "NOW"
                        ? "Passage au nouveau tarif lors de la confirmation."
                        : `Passage au nouveau tarif le ${new Date(review.cutoff ?? "").toLocaleString("fr-MA")}.`}
                  </p>
                  <dl className="space-y-1 tabular-nums">
                    <div className="flex justify-between gap-3">
                      <dt>{cancel ? "Tarif conservé" : "Tarif actuel"}</dt>
                      <dd className="font-medium">
                        {formatExactMoney(review.currentPrice.amount, price.currencyCode)}
                      </dd>
                    </div>
                    {!cancel && review.change.amount !== null && (
                      <div className="flex justify-between gap-3">
                        <dt>Nouveau tarif</dt>
                        <dd className="font-medium">{formatExactMoney(review.change.amount, price.currencyCode)}</dd>
                      </div>
                    )}
                  </dl>
                  <p>
                    {cancel
                      ? "Le changement programmé est annulé. Le tarif actuel continue sans date de fin."
                      : "Les deux tarifs se succèdent sans chevauchement ni interruption."}{" "}
                    Aucune offre ni condition d’abonné ne sera modifiée.
                  </p>
                  {!priceChangeReviewReady(price, review, now) && !uncertain && (
                    <p role="alert">Cette vérification a expiré ou les données ont changé. Vérifiez à nouveau.</p>
                  )}
                </>
              ) : (
                <>
                  <p className="font-medium">Changement indisponible</p>
                  <ul className="list-disc space-y-1 ps-4">
                    {review.blockers.map((blocker) => (
                      <li key={blocker}>
                        {priceChangeBlocker[blocker] ?? "La configuration doit être vérifiée avant ce changement."}
                      </li>
                    ))}
                  </ul>
                  {review.blockingOfferCount > 0 && (
                    <p>{review.blockingOfferCount} offre(s) publiée(s) concernée(s).</p>
                  )}
                </>
              )}
            </section>
          )}
        </div>
        <DialogFooter className="shrink-0">
          <Button variant="ghost" disabled={busy} onClick={() => setOpen(false)}>
            Fermer
          </Button>
          <Button variant="outline" disabled={busy || !canApply || uncertain} onClick={verify}>
            {preview.isPending ? "Vérification…" : "Vérifier"}
          </Button>
          <Button
            disabled={busy || !canApply || (!uncertain && !priceChangeReviewReady(price, review, now))}
            onClick={() => apply.mutate()}
          >
            {apply.isPending ? "Confirmation…" : uncertain ? "Réessayer la même demande" : "Confirmer"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
