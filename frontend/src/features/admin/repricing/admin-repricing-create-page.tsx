import { ArrowLeftIcon } from "@phosphor-icons/react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useBlocker, useNavigate, useSearchParams } from "react-router";
import { adminApi } from "@/api/admin-api";
import type { ProductPrice, SubscriptionStatus } from "@/api/contracts";
import { type RepricingPreview, type RepricingRequest, repricingApi } from "@/api/repricing-api";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { SubscriptionJobAccountPicker } from "@/features/admin/subscription-jobs/subscription-job-account-picker";
import { formatExactMoney } from "@/lib/exact-decimal";
import { RepricingChoiceList } from "./repricing-choice-list";
import { RepricingResults } from "./repricing-results";
import { compatibleTariff, currentRepricingPreview, repricingCycle, repricingDate } from "./repricing-rules";

const steps = ["Tarif", "Abonnés", "Date et notification", "Révision"];
const audiences = {
  TARIFF_HOLDERS: "Tous les abonnés à cet ancien tarif",
  SELECTED: "Choisir des comptes",
  FILTERED: "Filtrer les abonnés",
  SEGMENT: "Utiliser un segment",
} as const;
const priceLabel = (price: ProductPrice) =>
  `${price.productName} · ${formatExactMoney(price.amount, price.currencyCode)} / ${repricingCycle(price.billingCycle)} · tarif R${price.revisionNumber}`;

export function AdminRepricingCreatePage() {
  const session = useAdminSession();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const from = params.get("from") ?? "";
  const [source, setSource] = useState<ProductPrice | null>(null);
  const [target, setTarget] = useState<ProductPrice | null>(null);
  const [step, setStep] = useState(0);
  const [audience, setAudience] = useState<RepricingRequest["audience"]>("TARIFF_HOLDERS");
  const [accountIds, setAccountIds] = useState<string[]>([]);
  const [excludedAccountIds, setExcludedAccountIds] = useState<string[]>([]);
  const [showExclusions, setShowExclusions] = useState(false);
  const [plan, setPlan] = useState<{ id: string; name: string } | null>(null);
  const [segment, setSegment] = useState<{ id: string; name: string } | null>(null);
  const [subscriptionStatus, setSubscriptionStatus] = useState<SubscriptionStatus>("ACTIVE");
  const [timing, setTiming] = useState<"next" | "date">("next");
  const [date, setDate] = useState("");
  const [email, setEmail] = useState(false);
  const [reason, setReason] = useState("");
  const [dirty, setDirty] = useState(false);
  const completed = useRef(false);
  const [preview, setPreview] = useState<RepricingPreview | null>(null);
  const [reviewedKey, setReviewedKey] = useState("");
  const [, tick] = useState(0);
  const prefill = useQuery({
    queryKey: ["admin", "repricing", "source", from],
    queryFn: () => adminApi.productPrice(from),
    enabled: Boolean(from) && session.can(adminPermissions.priceBooksRead),
  });
  useEffect(() => {
    if (prefill.data && !dirty) setSource(prefill.data);
  }, [prefill.data, dirty]);
  useEffect(() => {
    if (!preview) return;
    const timer = window.setTimeout(
      () => tick((value) => value + 1),
      Math.max(0, Date.parse(preview.expiresAt) - Date.now()) + 10,
    );
    return () => window.clearTimeout(timer);
  }, [preview]);
  const blocker = useBlocker(useCallback(() => dirty && !completed.current, [dirty]));
  useEffect(() => {
    if (blocker.state !== "blocked") return;
    if (window.confirm("Quitter sans confirmer ce changement de tarif ?")) blocker.proceed();
    else blocker.reset();
  }, [blocker]);
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => {
      if (!completed.current) event.preventDefault();
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);
  const changed = () => {
    setDirty(true);
    setPreview(null);
  };
  const parsedDate = new Date(date);
  const validDate = timing === "next" || (Number.isFinite(parsedDate.getTime()) && parsedDate.getTime() > Date.now());
  const request: RepricingRequest = {
    sourcePriceId: source?.id ?? "",
    targetPriceId: target?.id ?? "",
    audience,
    accountIds: audience === "SELECTED" ? accountIds : [],
    excludedAccountIds,
    planId: audience === "FILTERED" ? (plan?.id ?? null) : null,
    subscriptionStatus: audience === "FILTERED" ? subscriptionStatus : null,
    segmentId: audience === "SEGMENT" ? (segment?.id ?? null) : null,
    notBefore: timing === "date" && validDate ? parsedDate.toISOString() : null,
    email,
    reason: reason.trim(),
  };
  const requestKey = JSON.stringify(request);
  const pairReady = Boolean(source && target && compatibleTariff(source, target));
  const audienceReady =
    audience === "SELECTED"
      ? accountIds.some((id) => !excludedAccountIds.includes(id))
      : audience !== "SEGMENT" || Boolean(segment);
  const inputsReady =
    pairReady &&
    audienceReady &&
    validDate &&
    Boolean(reason.trim()) &&
    (!email || session.can(adminPermissions.repricingEmail));
  const ready = inputsReady && currentRepricingPreview(preview, reviewedKey, requestKey);
  const review = useMutation({
    mutationFn: (value: RepricingRequest) => repricingApi.preview(value),
    onSuccess: (data, value) => {
      setPreview(data);
      setReviewedKey(JSON.stringify(value));
    },
    onError: () => setPreview(null),
  });
  const confirm = useMutation({
    mutationFn: async () => {
      if (
        !inputsReady ||
        !currentRepricingPreview(preview, reviewedKey, requestKey) ||
        !preview ||
        !session.can(adminPermissions.repricingConfirm)
      )
        throw new Error("Recalculez la révision avant de confirmer.");
      return repricingApi.confirm(preview.summary.id, preview.previewToken);
    },
    onSuccess: (data) => {
      completed.current = true;
      navigate(`/admin/repricing/${data.summary.id}`);
    },
    onError: () => setPreview(null),
  });
  if (!session.can(adminPermissions.repricingPreview)) return <PermissionState />;
  return (
    <div className="space-y-6">
      <Button asChild variant="ghost" size="sm">
        <Link to="/admin/price-books">
          <ArrowLeftIcon className="rtl:rotate-180" />
          Grille tarifaire
        </Link>
      </Button>
      <PageHeader title="Modifier le tarif des abonnés" />
      <ol aria-label="Étapes" className="flex flex-wrap gap-x-6 gap-y-2 border-b pb-4 text-sm">
        {steps.map((label, index) => (
          <li
            key={label}
            aria-current={index === step ? "step" : undefined}
            className={index === step ? "font-semibold text-primary" : "text-muted-foreground"}
          >
            {index + 1}. {label}
          </li>
        ))}
      </ol>
      <form
        className="space-y-6"
        onSubmit={(event) => {
          event.preventDefault();
          if (step === 3 && ready) confirm.mutate();
        }}
      >
        {step === 0 ? (
          <div className="grid gap-8 lg:grid-cols-2">
            <section className="space-y-4">
              <h2 className="font-semibold">Ancien tarif</h2>
              {prefill.isLoading ? (
                <LoadingState rows={2} />
              ) : prefill.isError ? (
                <ErrorState retry={() => void prefill.refetch()} />
              ) : null}
              {source ? <p className="border-s-2 border-primary ps-3 text-sm">{priceLabel(source)}</p> : null}
              <RepricingChoiceList
                title="Tarifs existants"
                cacheKey={["admin", "repricing", "source-choices"]}
                allowed={session.can(adminPermissions.priceBooksList)}
                load={(search, page) => adminApi.productPrices({ search, page, size: 10 })}
                label={priceLabel}
                selectedId={source?.id}
                onSelect={(value) => {
                  changed();
                  setSource(value);
                  setTarget(null);
                }}
              />
            </section>
            <section className="space-y-4">
              <h2 className="font-semibold">Nouveau tarif</h2>
              {target ? <p className="border-s-2 border-primary ps-3 text-sm">{priceLabel(target)}</p> : null}
              {source ? (
                <RepricingChoiceList
                  key={source.id}
                  title="Tarifs publiés compatibles"
                  cacheKey={["admin", "repricing", "target-choices", source.id]}
                  allowed={session.can(adminPermissions.priceBooksList)}
                  load={(search, page) =>
                    adminApi.productPrices({
                      search,
                      page,
                      size: 10,
                      ownerId: source.productId,
                      ownerType: source.productType,
                      currencyCode: source.currencyCode,
                      billingCycle: source.billingCycle,
                      status: "ACTIVE",
                    })
                  }
                  label={priceLabel}
                  eligible={(value) => compatibleTariff(source, value)}
                  selectedId={target?.id}
                  onSelect={(value) => {
                    changed();
                    setTarget(value);
                  }}
                />
              ) : (
                <p className="text-sm text-muted-foreground">Choisissez d’abord l’ancien tarif.</p>
              )}
              <p className="text-sm text-muted-foreground">
                Même produit, devise et cycle. Les fonctionnalités et quantités ne changent pas.
              </p>
            </section>
          </div>
        ) : null}
        {step === 1 ? (
          <div className="max-w-4xl space-y-6">
            <fieldset className="grid gap-3 sm:grid-cols-2" aria-label="Abonnés concernés">
              {Object.entries(audiences).map(([value, label]) => (
                <label
                  key={value}
                  className="flex min-h-12 cursor-pointer items-center gap-3 rounded-md border px-4 py-3 text-sm"
                >
                  <input
                    className="size-4 accent-primary"
                    name="audience"
                    type="radio"
                    checked={audience === value}
                    onChange={() => {
                      changed();
                      setAudience(value as typeof audience);
                    }}
                  />
                  {label}
                </label>
              ))}
            </fieldset>
            {audience === "SELECTED" ? (
              <SubscriptionJobAccountPicker
                selectedIds={accountIds}
                onChange={(ids) => {
                  changed();
                  setAccountIds(ids);
                }}
              />
            ) : null}
            {audience === "FILTERED" ? (
              <div className="space-y-4">
                <Label>Statut d’abonnement</Label>
                <Select
                  value={subscriptionStatus}
                  onValueChange={(value) => {
                    changed();
                    setSubscriptionStatus(value as SubscriptionStatus);
                  }}
                >
                  <SelectTrigger aria-label="Statut d’abonnement" className="w-64">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ACTIVE">Actif</SelectItem>
                    <SelectItem value="TRIALING">En essai</SelectItem>
                    <SelectItem value="PAST_DUE">En retard de paiement</SelectItem>
                    <SelectItem value="SUSPENDED">Suspendu</SelectItem>
                  </SelectContent>
                </Select>
                {plan ? (
                  <p className="text-sm">
                    Forfait : {plan.name}{" "}
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() => {
                        changed();
                        setPlan(null);
                      }}
                    >
                      Tous les forfaits
                    </Button>
                  </p>
                ) : null}
                <RepricingChoiceList
                  title="Forfait (facultatif)"
                  cacheKey={["admin", "repricing", "plan-choices"]}
                  allowed={session.can(adminPermissions.plansChoose)}
                  load={(search, page) => adminApi.planChoices({ search, page, size: 10 })}
                  label={(item) => item.name}
                  selectedId={plan?.id}
                  onSelect={(item) => {
                    changed();
                    setPlan(item);
                  }}
                />
              </div>
            ) : null}
            {audience === "SEGMENT" ? (
              <>
                <RepricingChoiceList
                  title="Segments actifs"
                  cacheKey={["admin", "repricing", "segment-choices"]}
                  allowed={session.can(adminPermissions.segmentsList)}
                  load={(search, page) => adminApi.commercialSegments({ search, page, size: 10, status: "ACTIVE" })}
                  label={(item) => item.name}
                  selectedId={segment?.id}
                  onSelect={(item) => {
                    changed();
                    setSegment(item);
                  }}
                />
                {segment ? <p className="text-sm">Segment sélectionné : {segment.name}</p> : null}
              </>
            ) : null}
            <Button
              type="button"
              variant="ghost"
              onClick={() => setShowExclusions((value) => !value)}
              aria-expanded={showExclusions}
            >
              {showExclusions ? "Masquer" : "Choisir"} les exclusions ({excludedAccountIds.length})
            </Button>
            {showExclusions ? (
              <SubscriptionJobAccountPicker
                selectedIds={excludedAccountIds}
                onChange={(ids) => {
                  changed();
                  setExcludedAccountIds(ids);
                }}
              />
            ) : null}
            <p className="text-sm text-muted-foreground">
              Les accords particuliers et remises restent protégés. Le calcul indiquera les comptes exclus et leur
              motif.
            </p>
          </div>
        ) : null}
        {step === 2 ? (
          <div className="max-w-2xl space-y-6">
            <fieldset className="space-y-3" aria-label="Date d’application">
              <label className="flex items-center gap-3">
                <input
                  type="radio"
                  name="timing"
                  checked={timing === "next"}
                  onChange={() => {
                    changed();
                    setTiming("next");
                  }}
                />
                Au prochain renouvellement de chaque compte
              </label>
              <label className="flex items-center gap-3">
                <input
                  type="radio"
                  name="timing"
                  checked={timing === "date"}
                  onChange={() => {
                    changed();
                    setTiming("date");
                  }}
                />
                Au premier renouvellement à partir d’une date
              </label>
            </fieldset>
            {timing === "date" ? (
              <div className="space-y-2">
                <Label htmlFor="repricing-date">Date et heure locales</Label>
                <Input
                  id="repricing-date"
                  type="datetime-local"
                  value={date}
                  onChange={(event) => {
                    changed();
                    setDate(event.target.value);
                  }}
                />
                {date && !validDate ? (
                  <p role="alert" className="text-sm text-destructive">
                    Choisissez une date future.
                  </p>
                ) : null}
              </div>
            ) : null}
            <p className="text-sm text-muted-foreground">
              Aucun changement sur une période déjà payée. Chaque compte conserve sa date de renouvellement.
            </p>
            <div className="space-y-3">
              <p className="text-sm">Une notification privée sera ajoutée dans l’abonnement du compte.</p>
              <label htmlFor="repricing-email" className="flex items-center gap-3 text-sm">
                <Checkbox
                  id="repricing-email"
                  checked={email}
                  disabled={!session.can(adminPermissions.repricingEmail)}
                  onCheckedChange={(value) => {
                    changed();
                    setEmail(value === true);
                  }}
                />
                Envoyer aussi un email au propriétaire
              </label>
              {!session.can(adminPermissions.repricingEmail) ? (
                <p className="text-xs text-muted-foreground">L’envoi d’emails demande une autorisation séparée.</p>
              ) : null}
            </div>
            <div className="space-y-2">
              <Label htmlFor="repricing-reason">Motif interne</Label>
              <Textarea
                id="repricing-reason"
                maxLength={2000}
                value={reason}
                onChange={(event) => {
                  changed();
                  setReason(event.target.value);
                }}
              />
            </div>
          </div>
        ) : null}
        {step === 3 ? (
          <div className="space-y-5">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <h2 className="font-semibold">{source?.productName}</h2>
                <p className="mt-1 tabular-nums">
                  {source && formatExactMoney(source.amount, source.currencyCode)} →{" "}
                  {target && formatExactMoney(target.amount, target.currencyCode)} /{" "}
                  {source && repricingCycle(source.billingCycle)}
                </p>
                <p className="mt-1 text-sm text-muted-foreground">
                  {audiences[audience]} ·{" "}
                  {timing === "next" ? "Prochain renouvellement" : `À partir du ${repricingDate(request.notBefore)}`}
                </p>
              </div>
              <Button
                type="button"
                variant="outline"
                disabled={!inputsReady || review.isPending || confirm.isPending}
                onClick={() => review.mutate(request)}
              >
                {review.isPending ? "Calcul en cours…" : preview ? "Recalculer" : "Calculer les changements"}
              </Button>
            </div>
            {review.isError ? (
              <p role="alert" className="text-sm text-destructive">
                {review.error.message}
              </p>
            ) : null}
            {preview && reviewedKey === requestKey ? (
              <>
                <p className="text-sm">
                  <strong>{preview.summary.readyCount}</strong> compte(s) prêts ·{" "}
                  <strong>{preview.summary.conflictCount}</strong> à revoir. Révision valable jusqu’au{" "}
                  {repricingDate(preview.expiresAt)}.
                </p>
                <RepricingResults key={preview.summary.id} id={preview.summary.id} review />
                {!ready ? (
                  <p role="alert" className="text-sm text-warning">
                    Révision expirée ou aucun compte admissible. Recalculez avant de confirmer.
                  </p>
                ) : null}
              </>
            ) : null}
            <p className="text-sm text-muted-foreground">
              La confirmation planifie le changement. Elle ne facture pas maintenant et ne marque aucun paiement comme
              reçu.
            </p>
          </div>
        ) : null}
        {confirm.isError ? (
          <p role="alert" className="text-sm text-destructive">
            {confirm.error.message} Recalculez la révision.
          </p>
        ) : null}
        <footer className="flex justify-between border-t pt-4">
          <Button
            type="button"
            variant="ghost"
            disabled={step === 0 || confirm.isPending}
            onClick={() => setStep((value) => value - 1)}
          >
            Précédent
          </Button>
          {step < 3 ? (
            <Button
              type="button"
              disabled={step === 0 ? !pairReady : step === 1 ? !audienceReady : !inputsReady}
              onClick={() => setStep((value) => value + 1)}
            >
              Continuer
            </Button>
          ) : (
            <Button
              type="submit"
              disabled={!ready || confirm.isPending || !session.can(adminPermissions.repricingConfirm)}
            >
              {confirm.isPending ? "Confirmation…" : "Confirmer et notifier"}
            </Button>
          )}
        </footer>
      </form>
    </div>
  );
}
