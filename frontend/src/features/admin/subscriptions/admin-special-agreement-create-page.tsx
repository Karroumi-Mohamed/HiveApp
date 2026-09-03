import { ArrowLeftIcon, ArrowRightIcon, WarningCircleIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, Navigate, useBlocker, useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type {
  SpecialAgreementDefinition,
  SpecialAgreementEndInstruction,
  SpecialAgreementPreview,
  SpecialAgreementPricingMode,
  SpecialAgreementSettlementMode,
  SubscriptionChangeInput,
} from "@/api/contracts";
import { ApiError } from "@/api/http";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { SectionTabs } from "@/components/patterns/section-tabs";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { capacityUnitLabel } from "@/features/admin/commercial/commercial-presentation";
import { adminCommercialKeys, invalidateAdminSubscriptionEntitlement } from "@/features/commercial/commercial-query";
import { formatExactMoney } from "@/lib/exact-decimal";
import { AdminSubscriptionChangeWorkbench } from "./admin-subscription-change-workbench";
import {
  type AgreementTermMode,
  agreementDefinitionProblem,
  agreementEnd,
  buildAgreementDefinition,
  toLocalDateTime,
} from "./special-agreement-rules";

type Step = "content" | "period" | "price" | "end" | "review";
const steps: Array<{ value: Step; label: string }> = [
  { value: "content", label: "Contenu" },
  { value: "period", label: "Période" },
  { value: "price", label: "Prix et règlement" },
  { value: "end", label: "Après la période" },
  { value: "review", label: "Révision" },
];

const pricingLabels: Record<SpecialAgreementPricingMode, { title: string; detail: string }> = {
  CATALOGUE_TOTAL: { title: "Total catalogue", detail: "Calculé sur les cycles complets de la période." },
  CUSTOM_TOTAL: { title: "Montant négocié", detail: "Un prix exact pour toute la période." },
  COMPLIMENTARY: { title: "Offert", detail: "Facture à zéro, sans faux paiement." },
};
const settlementLabels: Record<Exclude<SpecialAgreementSettlementMode, "NONE">, string> = {
  PROVIDER: "Paiement en ligne",
  MANUAL: "Paiement reçu manuellement",
};
const endLabels: Record<SpecialAgreementEndInstruction, { title: string; detail: string }> = {
  CONTINUE_REVIEWED_TERMS: {
    title: "Continuer",
    detail: "Poursuivre avec le prix récurrent défini ici.",
  },
  RESTORE_PREVIOUS_TERMS: {
    title: "Restaurer les conditions précédentes",
    detail: "Revenir à l’abonnement détenu avant cet accord.",
  },
  END_ACCESS: { title: "Mettre fin à l’accès", detail: "Arrêter l’abonnement sans supprimer les données." },
  MANUAL_REVIEW: { title: "Demander une décision", detail: "Suspendre l’accès et créer un point d’attention." },
};

function localNow(): string {
  return toLocalDateTime(new Date(Date.now() + 10 * 60_000).toISOString());
}

function Choice<T extends string>({
  checked,
  detail,
  name,
  onChange,
  title,
  value,
}: {
  checked: boolean;
  detail: string;
  name: string;
  onChange: (value: T) => void;
  title: string;
  value: T;
}) {
  const id = `${name}-${value}`.toLowerCase();
  return (
    <Label className="flex cursor-pointer items-start gap-3 border-b py-4 font-normal last:border-b-0" htmlFor={id}>
      <input
        checked={checked}
        className="mt-1 accent-primary"
        id={id}
        name={name}
        onChange={() => onChange(value)}
        type="radio"
      />
      <span>
        <strong className="block text-sm">{title}</strong>
        <span className="mt-0.5 block text-xs text-muted-foreground">{detail}</span>
      </span>
    </Label>
  );
}

function money(value: string | null, currency: string) {
  return value === null ? "—" : formatExactMoney(value, currency);
}

function Review({ preview, planName }: { preview: SpecialAgreementPreview; planName: string }) {
  return (
    <div className="space-y-6">
      <dl className="grid gap-x-8 gap-y-5 border-y py-5 sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <dt className="text-xs text-muted-foreground">Forfait</dt>
          <dd className="mt-1 font-semibold">{planName}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Durée facturée</dt>
          <dd className="mt-1 font-semibold">
            {preview.completeBillingCycles
              ? `${preview.completeBillingCycles} cycle${preview.completeBillingCycles > 1 ? "s" : ""} complet${preview.completeBillingCycles > 1 ? "s" : ""}`
              : "Période sur mesure"}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Valeur catalogue</dt>
          <dd className="mt-1 font-semibold">{money(preview.catalogueTermAmount, preview.currencyCode)}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Montant convenu</dt>
          <dd className="mt-1 font-semibold">{money(preview.agreedTermAmount, preview.currencyCode)}</dd>
        </div>
      </dl>
      <div className="grid gap-5 lg:grid-cols-2">
        <section>
          <h2 className="text-sm font-semibold">Contenu</h2>
          <p className="mt-2 text-sm text-muted-foreground">
            {preview.termEntitlements.addOnCodes.length} add-on(s) ·{" "}
            {preview.termEntitlements.quotaPackages.reduce((total, item) => total + item.quantity, 0)} pack(s) de
            capacité
          </p>
          <div className="mt-3 divide-y border-y text-sm">
            {preview.termEntitlements.effectiveQuotaLimits.map((quota) => (
              <div className="flex justify-between gap-4 py-2.5" key={`${quota.featureCode}-${quota.resource}`}>
                <span>{capacityUnitLabel(quota.resource)}</span>
                <strong>{quota.effectiveLimit ?? "Illimité"}</strong>
              </div>
            ))}
          </div>
        </section>
        <section>
          <h2 className="text-sm font-semibold">Exécution</h2>
          <dl className="mt-3 space-y-3 text-sm">
            <div className="flex justify-between gap-4 border-b pb-3">
              <dt className="text-muted-foreground">Début</dt>
              <dd>{new Date(preview.startsAt).toLocaleString("fr-MA")}</dd>
            </div>
            <div className="flex justify-between gap-4 border-b pb-3">
              <dt className="text-muted-foreground">Fin</dt>
              <dd>{new Date(preview.endsAt).toLocaleString("fr-MA")}</dd>
            </div>
            <div className="flex justify-between gap-4 border-b pb-3">
              <dt className="text-muted-foreground">Après</dt>
              <dd>{endLabels[preview.endInstruction].title}</dd>
            </div>
          </dl>
        </section>
      </div>
      {preview.conflicts.length ? (
        <Alert variant="destructive">
          <WarningCircleIcon />
          <AlertTitle>Conflits à résoudre</AlertTitle>
          <AlertDescription>{preview.conflicts.map((item) => item.message).join(" ")}</AlertDescription>
        </Alert>
      ) : null}
    </div>
  );
}

export function AdminSpecialAgreementCreatePage() {
  const { accountId } = useParams();
  const session = useAdminSession();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [step, setStep] = useState<Step>("content");
  const [selection, setSelection] = useState<SubscriptionChangeInput | null>(null);
  const [bonuses, setBonuses] = useState<Record<string, number>>({});
  const [termMode, setTermMode] = useState<AgreementTermMode>("ONE_MONTH");
  const [months, setMonths] = useState(2);
  const [startsAt, setStartsAt] = useState(localNow);
  const [exactEnd, setExactEnd] = useState(() => {
    const start = localNow();
    const end = agreementEnd(start, "ONE_MONTH", 1, "");
    return end ? toLocalDateTime(end) : "";
  });
  const [pricingMode, setPricingMode] = useState<SpecialAgreementPricingMode>("CATALOGUE_TOTAL");
  const [customTotal, setCustomTotal] = useState("");
  const [settlementMode, setSettlementMode] = useState<Exclude<SpecialAgreementSettlementMode, "NONE">>("PROVIDER");
  const [manualReference, setManualReference] = useState("");
  const [endInstruction, setEndInstruction] = useState<SpecialAgreementEndInstruction>("RESTORE_PREVIOUS_TERMS");
  const [followOnPricingMode, setFollowOnPricingMode] = useState<SpecialAgreementPricingMode>("CATALOGUE_TOTAL");
  const [followOnAmount, setFollowOnAmount] = useState("");
  const [reason, setReason] = useState("");
  const [preview, setPreview] = useState<SpecialAgreementPreview | null>(null);
  const [previewKey, setPreviewKey] = useState("");
  const completed = useRef(false);
  const catalog = useQuery({
    queryKey: adminCommercialKeys.subscriptions.changeCatalog(accountId ?? ""),
    queryFn: () => adminApi.subscriptionChangeCatalog(accountId ?? ""),
    enabled: Boolean(accountId) && session.can(adminPermissions.subscriptionsChooseChangeOptions),
    retry: false,
  });
  const subscription = useQuery({
    queryKey: adminCommercialKeys.subscriptions.detail(accountId ?? ""),
    queryFn: () => adminApi.subscription(accountId ?? ""),
    enabled: Boolean(accountId) && session.can(adminPermissions.subscriptionsRead),
    retry: false,
  });
  const selectedPlan = catalog.data?.plans.find((item) => item.code === selection?.targetPlanCode) ?? null;
  const quotaChoices = useMemo(() => {
    if (!selectedPlan || !selection) return [];
    const features = [
      ...selectedPlan.features,
      ...selectedPlan.addOns
        .filter((item) => selection.addOnCodes.includes(item.code))
        .flatMap((item) => item.features),
    ];
    return [
      ...new Map(
        features.flatMap((feature) =>
          feature.quotas.map(
            (quota) =>
              [
                `${quota.featureCode}:${quota.slot.resource}`,
                { featureCode: quota.featureCode, featureName: feature.displayName, ...quota.slot },
              ] as const,
          ),
        ),
      ).values(),
    ];
  }, [selectedPlan, selection]);
  const endsAt = agreementEnd(startsAt, termMode, months, exactEnd);
  const definition = useMemo<SpecialAgreementDefinition | null>(() => {
    if (!selection || !endsAt) return null;
    return buildAgreementDefinition({
      selection,
      quotaBonuses: quotaChoices.map((quota) => ({
        featureCode: quota.featureCode,
        resource: quota.resource,
        quantity: bonuses[`${quota.featureCode}:${quota.resource}`] ?? 0,
      })),
      startsAt: new Date(startsAt).toISOString(),
      endsAt,
      pricingMode,
      customTotal,
      currencyCode: selection.planPriceSelection.currencyCode,
      settlementMode,
      endInstruction,
      followOnPricingMode,
      followOnCustomAmount: followOnAmount,
    });
  }, [
    bonuses,
    customTotal,
    endInstruction,
    endsAt,
    followOnAmount,
    followOnPricingMode,
    pricingMode,
    quotaChoices,
    selection,
    settlementMode,
    startsAt,
  ]);
  const definitionKey = definition ? JSON.stringify(definition) : "";
  const previewReady = Boolean(preview && previewKey === definitionKey && Date.parse(preview.expiresAt) > Date.now());
  const dirty = Boolean(selection);
  const blocker = useBlocker(useCallback(() => dirty && !completed.current, [dirty]));
  useEffect(() => {
    if (blocker.state !== "blocked") return;
    if (window.confirm("Quitter sans créer cet accord ? Les conditions saisies seront perdues.")) blocker.proceed();
    else blocker.reset();
  }, [blocker]);
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);
  const previewMutation = useMutation({
    mutationFn: (value: SpecialAgreementDefinition) => adminApi.previewSpecialAgreement(accountId ?? "", value),
    onSuccess: (data, value) => {
      setPreview(data);
      setPreviewKey(JSON.stringify(value));
    },
    onError: (error) => {
      setPreview(null);
      toast.error(error instanceof ApiError ? error.message : "La révision n’a pas pu être calculée.");
    },
  });
  const createMutation = useMutation({
    mutationFn: async () => {
      if (!definition || !previewReady || !preview) throw new Error("Une révision à jour est requise.");
      const created = await adminApi.createSpecialAgreement(accountId ?? "", {
        definition,
        previewToken: preview.previewToken,
        reason: reason.trim(),
      });
      if (
        definition.settlementMode === "MANUAL" &&
        created.checkout &&
        session.can(adminPermissions.subscriptionsConfirmCheckout) &&
        manualReference.trim()
      ) {
        try {
          await adminApi.confirmCheckout(created.checkout.id, {
            reference: manualReference.trim(),
            reason: reason.trim(),
          });
        } catch (error) {
          return { settlementError: error };
        }
      }
      return { settlementError: null };
    },
    onSuccess: ({ settlementError }) => {
      completed.current = true;
      void invalidateAdminSubscriptionEntitlement(queryClient);
      if (settlementError) {
        toast.warning("Accord créé, mais le règlement reste à confirmer depuis sa fiche.");
      } else {
        toast.success("Accord spécial créé");
      }
      navigate(`/admin/subscriptions/${accountId}`);
    },
    onError: (error) => {
      if (error instanceof ApiError && error.code === "STALE_SPECIAL_AGREEMENT_PREVIEW") {
        setPreview(null);
        setPreviewKey("");
        toast.error("Les données ont changé. Recalculez la révision.");
        return;
      }
      toast.error(error instanceof ApiError ? error.message : "L’accord n’a pas pu être créé.");
    },
  });
  if (!accountId) return <Navigate replace to="/admin/subscriptions" />;
  if (
    !session.can(adminPermissions.subscriptionsChooseChangeOptions) ||
    !session.can(adminPermissions.subscriptionsPreviewSpecialAgreement) ||
    !session.can(adminPermissions.subscriptionsCreateSpecialAgreement)
  ) {
    return <Navigate replace to={`/admin/subscriptions/${accountId}`} />;
  }
  if (catalog.isLoading) return <LoadingState rows={5} />;
  if (catalog.isError || !catalog.data) return <ErrorState retry={() => void catalog.refetch()} />;
  const index = steps.findIndex((item) => item.value === step);
  const previous = steps.at(index - 1)?.value;
  const next = steps.at(index + 1)?.value;
  const definitionProblem = definition
    ? agreementDefinitionProblem(definition)
    : "Sélectionnez le contenu de l’accord.";
  const manualConfirmationExpected =
    pricingMode !== "COMPLIMENTARY" &&
    settlementMode === "MANUAL" &&
    session.can(adminPermissions.subscriptionsConfirmCheckout);
  const canContinue =
    !definitionProblem && (step !== "price" || !manualConfirmationExpected || !!manualReference.trim());
  return (
    <section className="space-y-6">
      <Button asChild className="-ms-2" size="sm" variant="ghost">
        <Link to={`/admin/subscriptions/${accountId}`}>
          <ArrowLeftIcon className="rtl:rotate-180" />
          Abonnement
        </Link>
      </Button>
      <PageHeader description={subscription.data?.accountName} title="Créer un accord spécial" />
      <SectionTabs
        ariaLabel="Étapes de l’accord"
        items={steps}
        onValueChange={(value) => setStep(value as Step)}
        value={step}
      />
      <div className="min-h-[430px] py-2">
        {step === "content" ? (
          <div className="space-y-6">
            <AdminSubscriptionChangeWorkbench
              accountId={accountId}
              catalog={catalog.data}
              onReviewSelection={(value) => {
                setSelection(value);
                setStep("period");
              }}
              onSelectionChange={setSelection}
              populationMode
              reviewLabel="Continuer"
              reviewPermission={adminPermissions.subscriptionsPreviewSpecialAgreement}
              showTiming={false}
              supplement={
                quotaChoices.length ? (
                  <section className="border-t p-4">
                    <h2 className="text-sm font-semibold">Capacité privée</h2>
                    <div className="mt-3 grid gap-x-8 gap-y-3 sm:grid-cols-2">
                      {quotaChoices.map((quota) => {
                        const key = `${quota.featureCode}:${quota.resource}`;
                        return (
                          <div className="grid grid-cols-[1fr_7rem] items-center gap-4" key={key}>
                            <Label htmlFor={`bonus-${key}`}>
                              {quota.featureName} · {capacityUnitLabel(quota.resource)}
                            </Label>
                            <Input
                              id={`bonus-${key}`}
                              min={0}
                              onChange={(event) =>
                                setBonuses((current) => ({ ...current, [key]: Number(event.target.value) || 0 }))
                              }
                              type="number"
                              value={bonuses[key] ?? 0}
                            />
                          </div>
                        );
                      })}
                    </div>
                  </section>
                ) : null
              }
            />
          </div>
        ) : step === "period" ? (
          <div className="space-y-6">
            <div className="grid gap-5 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="agreement-start">Début</Label>
                <Input
                  id="agreement-start"
                  onChange={(event) => setStartsAt(event.target.value)}
                  type="datetime-local"
                  value={startsAt}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="agreement-term-mode">Durée</Label>
                <Select onValueChange={(value) => setTermMode(value as AgreementTermMode)} value={termMode}>
                  <SelectTrigger id="agreement-term-mode">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ONE_MONTH">Un mois</SelectItem>
                    <SelectItem value="MONTHS">Nombre de mois</SelectItem>
                    <SelectItem value="EXACT">Dates exactes</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              {termMode === "MONTHS" ? (
                <div className="space-y-2">
                  <Label htmlFor="agreement-months">Nombre de mois</Label>
                  <Input
                    id="agreement-months"
                    max={120}
                    min={1}
                    onChange={(event) => setMonths(Number(event.target.value) || 1)}
                    type="number"
                    value={months}
                  />
                </div>
              ) : null}
              {termMode === "EXACT" ? (
                <div className="space-y-2">
                  <Label htmlFor="agreement-end">Fin</Label>
                  <Input
                    id="agreement-end"
                    onChange={(event) => setExactEnd(event.target.value)}
                    type="datetime-local"
                    value={exactEnd}
                  />
                </div>
              ) : null}
            </div>
            {endsAt ? (
              <p className="border-s-2 border-primary ps-3 text-sm">
                Fin prévue · {new Date(endsAt).toLocaleString("fr-MA")}
              </p>
            ) : null}
          </div>
        ) : step === "price" ? (
          <div className="grid gap-8 lg:grid-cols-2">
            <fieldset>
              <legend className="text-sm font-semibold">Prix de la période</legend>
              <div className="mt-2 border-y">
                {(Object.keys(pricingLabels) as SpecialAgreementPricingMode[]).map((value) => (
                  <Choice
                    checked={pricingMode === value}
                    detail={pricingLabels[value].detail}
                    key={value}
                    name="agreement-price"
                    onChange={setPricingMode}
                    title={pricingLabels[value].title}
                    value={value}
                  />
                ))}
              </div>
              {pricingMode === "CUSTOM_TOTAL" ? (
                <div className="mt-4 space-y-2">
                  <Label htmlFor="agreement-custom-total">
                    Montant total · {selection?.planPriceSelection.currencyCode}
                  </Label>
                  <Input
                    id="agreement-custom-total"
                    min="0"
                    onChange={(event) => setCustomTotal(event.target.value)}
                    step="0.01"
                    type="number"
                    value={customTotal}
                  />
                </div>
              ) : null}
            </fieldset>
            <fieldset disabled={pricingMode === "COMPLIMENTARY"}>
              <legend className="text-sm font-semibold">Règlement</legend>
              {pricingMode === "COMPLIMENTARY" ? (
                <p className="mt-4 border-y py-4 text-sm text-muted-foreground">Aucun paiement requis.</p>
              ) : (
                <div className="mt-2 border-y">
                  {(Object.keys(settlementLabels) as Array<Exclude<SpecialAgreementSettlementMode, "NONE">>).map(
                    (value) => (
                      <Choice
                        checked={settlementMode === value}
                        detail={
                          value === "MANUAL"
                            ? "Une référence et une justification seront exigées."
                            : "L’accès démarre après confirmation du prestataire."
                        }
                        key={value}
                        name="agreement-settlement"
                        onChange={setSettlementMode}
                        title={settlementLabels[value]}
                        value={value}
                      />
                    ),
                  )}
                </div>
              )}
              {pricingMode !== "COMPLIMENTARY" && settlementMode === "MANUAL" ? (
                session.can(adminPermissions.subscriptionsConfirmCheckout) ? (
                  <div className="mt-4 space-y-2">
                    <Label htmlFor="agreement-manual-reference">Référence du règlement reçu</Label>
                    <Input
                      id="agreement-manual-reference"
                      maxLength={255}
                      onChange={(event) => setManualReference(event.target.value)}
                      value={manualReference}
                    />
                    <p className="text-xs text-muted-foreground">
                      Le règlement sera enregistré dans le registre financier après la création de l’accord.
                    </p>
                  </div>
                ) : (
                  <p className="mt-4 border-s-2 border-warning ps-3 text-sm">
                    L’accord restera en attente jusqu’à la confirmation d’un opérateur autorisé.
                  </p>
                )
              ) : null}
            </fieldset>
          </div>
        ) : step === "end" ? (
          <div className="grid gap-8 lg:grid-cols-[1fr_0.8fr]">
            <fieldset>
              <legend className="text-sm font-semibold">À la date de fin</legend>
              <div className="mt-2 border-y">
                {(Object.keys(endLabels) as SpecialAgreementEndInstruction[]).map((value) => (
                  <Choice
                    checked={endInstruction === value}
                    detail={endLabels[value].detail}
                    key={value}
                    name="agreement-end-instruction"
                    onChange={setEndInstruction}
                    title={endLabels[value].title}
                    value={value}
                  />
                ))}
              </div>
            </fieldset>
            {endInstruction === "CONTINUE_REVIEWED_TERMS" ? (
              <section className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="agreement-follow-on-mode">Prix récurrent</Label>
                  <Select
                    onValueChange={(value) => setFollowOnPricingMode(value as SpecialAgreementPricingMode)}
                    value={followOnPricingMode}
                  >
                    <SelectTrigger id="agreement-follow-on-mode">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="CATALOGUE_TOTAL">Prix catalogue</SelectItem>
                      <SelectItem value="CUSTOM_TOTAL">Montant personnalisé</SelectItem>
                      <SelectItem value="COMPLIMENTARY">Gratuit</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                {followOnPricingMode === "CUSTOM_TOTAL" ? (
                  <div className="space-y-2">
                    <Label htmlFor="agreement-follow-on-amount">Montant par cycle</Label>
                    <Input
                      id="agreement-follow-on-amount"
                      min="0"
                      onChange={(event) => setFollowOnAmount(event.target.value)}
                      step="0.01"
                      type="number"
                      value={followOnAmount}
                    />
                  </div>
                ) : null}
              </section>
            ) : null}
          </div>
        ) : (
          <div className="space-y-6">
            {!previewReady ? (
              <div className="flex min-h-48 flex-col items-center justify-center gap-3 border-y text-center">
                <p className="text-sm text-muted-foreground">Calculez les conditions exactes avant de confirmer.</p>
                <Button
                  disabled={!definition || Boolean(definitionProblem) || previewMutation.isPending}
                  onClick={() => definition && previewMutation.mutate(definition)}
                >
                  {previewMutation.isPending ? "Calcul…" : "Calculer la révision"}
                </Button>
              </div>
            ) : preview ? (
              <Review planName={selectedPlan?.name ?? selection?.targetPlanCode ?? "—"} preview={preview} />
            ) : null}
            {previewReady ? (
              <div className="space-y-2 border-t pt-5">
                <Label htmlFor="agreement-reason">Justification de l’accord</Label>
                <Textarea
                  id="agreement-reason"
                  maxLength={2000}
                  onChange={(event) => setReason(event.target.value)}
                  rows={3}
                  value={reason}
                />
              </div>
            ) : null}
          </div>
        )}
      </div>
      {step !== "content" && step !== "review" && definitionProblem ? (
        <p className="border-s-2 border-destructive ps-3 text-sm text-destructive" role="alert">
          {definitionProblem}
        </p>
      ) : null}
      {step === "price" && manualConfirmationExpected && !manualReference.trim() ? (
        <p className="border-s-2 border-destructive ps-3 text-sm text-destructive" role="alert">
          Saisissez la référence du règlement reçu.
        </p>
      ) : null}
      {step !== "content" ? (
        <footer className="flex flex-col-reverse justify-between gap-3 border-t pt-5 sm:flex-row">
          <Button disabled={!previous} onClick={() => previous && setStep(previous)} type="button" variant="ghost">
            <ArrowLeftIcon className="rtl:rotate-180" /> Précédent
          </Button>
          {step === "review" ? (
            <Button
              disabled={
                !previewReady ||
                !preview?.confirmable ||
                reason.trim().length < 3 ||
                createMutation.isPending ||
                !session.can(adminPermissions.subscriptionsCreateSpecialAgreement)
              }
              onClick={() => createMutation.mutate()}
            >
              {createMutation.isPending ? "Création…" : "Créer l’accord"}
            </Button>
          ) : (
            <Button disabled={!next || !canContinue} onClick={() => next && setStep(next)} type="button">
              Continuer <ArrowRightIcon className="rtl:rotate-180" />
            </Button>
          )}
        </footer>
      ) : null}
    </section>
  );
}
