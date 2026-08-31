import { ArrowLeftIcon, ArrowRightIcon, CheckCircleIcon, WarningCircleIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useBlocker, useNavigate, useParams, useSearchParams } from "react-router";
import { toast } from "sonner";
import { adminOfferApi } from "@/api/admin-offer-api";
import { ApiError } from "@/api/http";
import type { OfferEditableDefinition, OfferPricedChoice } from "@/api/offer-contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { SectionTabs } from "@/components/patterns/section-tabs";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { adminCommercialKeys, commercialQueryEnabled } from "@/features/commercial/commercial-query";
import {
  emptyOfferDraft,
  hasOfferDraftErrors,
  type OfferDraft,
  type OfferEditorStep,
  offerDraftFingerprint,
  offerDraftFromDefinition,
  offerEditorSteps,
  toLocalDateTime,
  toOfferCreateInput,
  toOfferUpdateInput,
  validateOfferDraft,
} from "./offer-editor-rules";
import { OfferProductSelector } from "./offer-product-selector";
import { offerAcceptance, offerDiscovery } from "./offer-rules";

const validSteps = new Set(offerEditorSteps.map((step) => step.value));
const reviewDateTime = new Intl.DateTimeFormat("fr-FR", { dateStyle: "medium", timeStyle: "short" });

function FieldError({ children }: { children?: string }) {
  return children ? (
    <p className="text-xs text-destructive" role="alert">
      {children}
    </p>
  ) : null;
}

function ScopeStep({
  draft,
  errors,
  existing,
  onChange,
}: {
  draft: OfferDraft;
  errors: ReturnType<typeof validateOfferDraft>;
  existing?: OfferEditableDefinition;
  onChange: (draft: OfferDraft) => void;
}) {
  const session = useAdminSession();
  const campaigns = useQuery({
    queryKey: adminCommercialKeys.offers.campaignChoices({ page: 0, size: 100 }),
    queryFn: ({ signal }) => adminOfferApi.campaignChoices({ page: 0, size: 100 }, { signal }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.offersChooseCampaigns, !existing),
  });
  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <div className="space-y-2">
        <Label htmlFor="offer-name">Nom</Label>
        <Input
          id="offer-name"
          maxLength={180}
          onChange={(event) => onChange({ ...draft, name: event.target.value })}
          value={draft.name}
        />
        <FieldError>{errors.name}</FieldError>
      </div>
      <div className="space-y-2">
        <Label htmlFor="offer-campaign">Campagne</Label>
        {existing ? (
          <Input
            disabled
            id="offer-campaign"
            value={`${existing.campaign.name} · R${existing.campaign.revisionNumber}`}
          />
        ) : (
          <Select
            onValueChange={(campaignId) => {
              const campaign = campaigns.data?.content.find((item) => item.id === campaignId);
              onChange({
                ...draft,
                campaignId,
                startsAt: campaign ? toLocalDateTime(campaign.startsAt) : draft.startsAt,
                endsAt: campaign ? toLocalDateTime(campaign.endsAt) : draft.endsAt,
              });
            }}
            value={draft.campaignId || undefined}
          >
            <SelectTrigger id="offer-campaign">
              <SelectValue placeholder={campaigns.isLoading ? "Chargement…" : "Sélectionner une campagne"} />
            </SelectTrigger>
            <SelectContent>
              {campaigns.data?.content.map((campaign) => (
                <SelectItem key={campaign.id} value={campaign.id}>
                  {campaign.name} · R{campaign.revisionNumber}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
        <FieldError>{errors.campaign}</FieldError>
      </div>
      <div className="space-y-2 lg:col-span-2">
        <Label htmlFor="offer-description">Description client</Label>
        <Textarea
          id="offer-description"
          maxLength={1000}
          onChange={(event) => onChange({ ...draft, description: event.target.value })}
          rows={4}
          value={draft.description}
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor="offer-start">Début</Label>
        <Input
          dir="ltr"
          id="offer-start"
          onChange={(event) => onChange({ ...draft, startsAt: event.target.value })}
          type="datetime-local"
          value={draft.startsAt}
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor="offer-end">Fin</Label>
        <Input
          dir="ltr"
          id="offer-end"
          onChange={(event) => onChange({ ...draft, endsAt: event.target.value })}
          type="datetime-local"
          value={draft.endsAt}
        />
      </div>
      <div className="lg:col-span-2">
        <FieldError>{errors.window}</FieldError>
      </div>
    </div>
  );
}

function EffectStep({
  draft,
  errors,
  onChange,
}: {
  draft: OfferDraft;
  errors: ReturnType<typeof validateOfferDraft>;
  onChange: (draft: OfferDraft) => void;
}) {
  return (
    <div className="space-y-7">
      <fieldset className="grid gap-2 sm:grid-cols-3">
        <legend className="mb-3 text-sm font-medium">Réduction</legend>
        {(["NONE", "FIXED", "PERCENTAGE_WITH_CAP"] as const).map((value) => {
          const id = `offer-discount-${value}`;
          const label = value === "NONE" ? "Aucune" : value === "FIXED" ? "Montant fixe" : "Pourcentage plafonné";
          return (
            <Label
              className="flex cursor-pointer items-center gap-3 rounded-lg border px-4 py-3 font-normal has-[:checked]:border-primary has-[:checked]:bg-primary/5"
              htmlFor={id}
              key={value}
            >
              <input
                checked={draft.discountType === value}
                className="accent-primary"
                id={id}
                name="offer-discount"
                onChange={() => onChange({ ...draft, discountType: value })}
                type="radio"
              />
              {label}
            </Label>
          );
        })}
      </fieldset>
      {draft.discountType === "FIXED" ? (
        <div className="max-w-sm space-y-2">
          <Label htmlFor="offer-fixed-discount">Montant de réduction</Label>
          <Input
            dir="ltr"
            id="offer-fixed-discount"
            min="0.01"
            onChange={(event) => onChange({ ...draft, discountAmount: event.target.value })}
            step="0.01"
            type="number"
            value={draft.discountAmount}
          />
        </div>
      ) : null}
      {draft.discountType === "PERCENTAGE_WITH_CAP" ? (
        <div className="grid gap-6 sm:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="offer-percentage">Pourcentage</Label>
            <Input
              dir="ltr"
              id="offer-percentage"
              max="100"
              min="0.0001"
              onChange={(event) => onChange({ ...draft, percentage: event.target.value })}
              step="0.01"
              type="number"
              value={draft.percentage}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="offer-cap">Plafond monétaire</Label>
            <Input
              dir="ltr"
              id="offer-cap"
              min="0.01"
              onChange={(event) => onChange({ ...draft, percentageCap: event.target.value })}
              step="0.01"
              type="number"
              value={draft.percentageCap}
            />
          </div>
        </div>
      ) : null}
      <FieldError>{errors.discount}</FieldError>
    </div>
  );
}

function LimitsStep({
  draft,
  errors,
  existing,
  onChange,
}: {
  draft: OfferDraft;
  errors: ReturnType<typeof validateOfferDraft>;
  existing?: OfferEditableDefinition;
  onChange: (draft: OfferDraft) => void;
}) {
  const lineageLocked = existing && !existing.lineageTermsEditable;
  return (
    <div className="space-y-8">
      {lineageLocked ? (
        <Alert>
          <AlertTitle>Conditions de lignée figées</AlertTitle>
          <AlertDescription>
            Cette révision conserve le mode de découverte, l’acceptation et les capacités de la lignée publiée.
          </AlertDescription>
        </Alert>
      ) : null}
      <div className="grid gap-6 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="offer-discovery">Découverte</Label>
          <Select
            disabled={Boolean(lineageLocked)}
            onValueChange={(value) => onChange({ ...draft, discovery: value as OfferDraft["discovery"] })}
            value={draft.discovery}
          >
            <SelectTrigger id="offer-discovery">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Object.entries(offerDiscovery).map(([value, label]) => (
                <SelectItem key={value} value={value}>
                  {label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="offer-acceptance">Acceptation</Label>
          <Select
            disabled={Boolean(lineageLocked)}
            onValueChange={(value) => onChange({ ...draft, acceptance: value as OfferDraft["acceptance"] })}
            value={draft.acceptance}
          >
            <SelectTrigger id="offer-acceptance">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Object.entries(offerAcceptance).map(([value, label]) => (
                <SelectItem key={value} value={value}>
                  {label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="offer-global-limit">Capacité globale</Label>
          <Input
            disabled={Boolean(lineageLocked)}
            id="offer-global-limit"
            min={1}
            onChange={(event) => onChange({ ...draft, globalLimit: event.target.value })}
            placeholder="Sans limite"
            type="number"
            value={draft.globalLimit}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="offer-account-limit">Acceptations par compte</Label>
          <Input
            disabled={Boolean(lineageLocked)}
            id="offer-account-limit"
            min={1}
            onChange={(event) => onChange({ ...draft, perAccountLimit: event.target.value })}
            placeholder="Sans limite"
            type="number"
            value={draft.perAccountLimit}
          />
        </div>
      </div>
      {draft.discovery === "CODE_ONLY" ? (
        <div className="max-w-md space-y-3">
          <Label htmlFor="offer-code">Code privé</Label>
          {existing?.customerCodeConfigured ? (
            <Select
              disabled={Boolean(lineageLocked)}
              onValueChange={(value) =>
                onChange({ ...draft, customerCodeMode: value as OfferDraft["customerCodeMode"] })
              }
              value={draft.customerCodeMode}
            >
              <SelectTrigger aria-label="Modification du code privé">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="KEEP">Conserver le code actuel</SelectItem>
                <SelectItem value="REPLACE">Remplacer le code</SelectItem>
                <SelectItem value="REMOVE">Supprimer le code</SelectItem>
              </SelectContent>
            </Select>
          ) : null}
          {(!existing?.customerCodeConfigured || draft.customerCodeMode === "REPLACE") && !lineageLocked ? (
            <Input
              autoComplete="off"
              id="offer-code"
              maxLength={64}
              onChange={(event) => onChange({ ...draft, customerCode: event.target.value })}
              value={draft.customerCode}
            />
          ) : null}
          <FieldError>{errors.code}</FieldError>
        </div>
      ) : null}
      <FieldError>{errors.limits}</FieldError>
    </div>
  );
}

function ReviewStep({
  draft,
  errors,
  preview,
  previewFresh,
  onPreview,
  previewing,
}: {
  draft: OfferDraft;
  errors: ReturnType<typeof validateOfferDraft>;
  preview: import("@/api/offer-contracts").OfferDefinitionPreview | null;
  previewFresh: boolean;
  onPreview: () => void;
  previewing: boolean;
}) {
  const selectedCount = 1 + draft.addOns.length + draft.quotaPackages.length;
  return (
    <div className="space-y-6">
      {hasOfferDraftErrors(errors) ? (
        <Alert variant="destructive">
          <WarningCircleIcon />
          <AlertTitle>Le brouillon est incomplet</AlertTitle>
          <AlertDescription>Corrigez les champs signalés avant la vérification serveur.</AlertDescription>
        </Alert>
      ) : null}
      <dl className="grid gap-x-8 gap-y-5 border-y py-5 sm:grid-cols-2">
        <div>
          <dt className="text-xs text-muted-foreground">Offre</dt>
          <dd className="mt-1 font-medium">{draft.name || "—"}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Période</dt>
          <dd className="mt-1 font-medium">
            {reviewDateTime.format(new Date(draft.startsAt))} — {reviewDateTime.format(new Date(draft.endsAt))}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Sélection</dt>
          <dd className="mt-1 font-medium">
            {selectedCount} produit(s) · {draft.timing === "IMMEDIATE" ? "immédiat" : "au renouvellement"}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Accès</dt>
          <dd className="mt-1 font-medium">
            {offerDiscovery[draft.discovery]} · {offerAcceptance[draft.acceptance]}
          </dd>
        </div>
      </dl>
      <Button disabled={hasOfferDraftErrors(errors) || previewing} onClick={onPreview} type="button" variant="outline">
        {previewing ? "Vérification…" : "Vérifier la définition"}
      </Button>
      {preview && previewFresh ? (
        preview.valid ? (
          <Alert>
            <CheckCircleIcon />
            <AlertTitle>Définition valide</AlertTitle>
            <AlertDescription>La campagne, les prix, la compatibilité et les quotas ont été vérifiés.</AlertDescription>
          </Alert>
        ) : (
          <Alert variant="destructive">
            <WarningCircleIcon />
            <AlertTitle>Définition refusée</AlertTitle>
            <AlertDescription>
              <ul className="list-disc space-y-1 ps-5">
                {preview.issues.map((issue) => (
                  <li key={`${issue.fieldPath}:${issue.code}`}>{issue.message}</li>
                ))}
              </ul>
            </AlertDescription>
          </Alert>
        )
      ) : null}
      {preview && !previewFresh ? (
        <p className="text-sm text-warning">La définition a changé depuis la dernière vérification.</p>
      ) : null}
    </div>
  );
}

function mergeChoices(current: OfferPricedChoice[] | undefined, resolved: OfferPricedChoice[] | undefined) {
  const byId = new Map<string, OfferPricedChoice>();
  for (const choice of [...(resolved ?? []), ...(current ?? [])]) byId.set(choice.priceId, choice);
  return [...byId.values()];
}

function OfferEditor({ existing }: { existing?: OfferEditableDefinition }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const requestedStep = params.get("step");
  const step = (
    requestedStep && validSteps.has(requestedStep as OfferEditorStep) ? requestedStep : "scope"
  ) as OfferEditorStep;
  const setStep = (value: OfferEditorStep) => {
    const next = new URLSearchParams(params);
    if (value === "scope") next.delete("step");
    else next.set("step", value);
    setParams(next, { replace: true });
  };
  const initial = useRef(existing ? offerDraftFromDefinition(existing) : emptyOfferDraft());
  const initialFingerprint = useRef(offerDraftFingerprint(initial.current));
  const [draft, setDraft] = useState(initial.current);
  const [submitted, setSubmitted] = useState(false);
  const [previewState, setPreviewState] = useState<{
    fingerprint: string;
    data: import("@/api/offer-contracts").OfferDefinitionPreview;
  } | null>(null);
  const completed = useRef(false);
  const errors = validateOfferDraft(draft, existing?.customerCodeConfigured);
  const fingerprint = offerDraftFingerprint(draft);
  const selectedPriceIds = useMemo(
    () =>
      [
        draft.plan.priceId,
        ...draft.addOns.map((item) => item.priceId),
        ...draft.quotaPackages.map((item) => item.priceId),
      ].filter(Boolean),
    [draft.plan.priceId, draft.addOns, draft.quotaPackages],
  );
  const plans = useQuery({
    queryKey: adminCommercialKeys.offers.productChoices("PLAN", { page: 0, size: 100 }),
    queryFn: ({ signal }) => adminOfferApi.productChoices({ ownerType: "PLAN", page: 0, size: 100 }, { signal }),
    enabled: session.can(adminPermissions.offersChooseProducts),
  });
  const addOns = useQuery({
    queryKey: adminCommercialKeys.offers.productChoices("ADD_ON", { page: 0, size: 100 }),
    queryFn: ({ signal }) => adminOfferApi.productChoices({ ownerType: "ADD_ON", page: 0, size: 100 }, { signal }),
    enabled: session.can(adminPermissions.offersChooseProducts),
  });
  const packages = useQuery({
    queryKey: adminCommercialKeys.offers.productChoices("QUOTA_PACKAGE", { page: 0, size: 100 }),
    queryFn: ({ signal }) =>
      adminOfferApi.productChoices({ ownerType: "QUOTA_PACKAGE", page: 0, size: 100 }, { signal }),
    enabled: session.can(adminPermissions.offersChooseProducts),
  });
  const resolved = useQuery({
    queryKey: [
      ...adminCommercialKeys.offers.editableDefinition(existing?.id ?? "new"),
      "resolved-products",
      selectedPriceIds,
    ],
    queryFn: () => adminOfferApi.resolveProductChoices(selectedPriceIds),
    enabled: Boolean(selectedPriceIds.length && session.can(adminPermissions.offersResolveProductChoices)),
  });
  const quotaResources = useQuery({
    queryKey: [
      ...adminCommercialKeys.offers.editableDefinition(existing?.id ?? "new"),
      "quota-resources",
      selectedPriceIds,
    ],
    queryFn: ({ signal }) => adminOfferApi.quotaResourceChoices(selectedPriceIds, undefined, { signal }),
    enabled: Boolean(selectedPriceIds.length && session.can(adminPermissions.offersChooseQuotaResources)),
  });
  const preview = useMutation({
    mutationFn: () =>
      existing
        ? adminOfferApi.previewUpdateDefinition(existing.id, toOfferUpdateInput(draft, existing))
        : adminOfferApi.previewCreateDefinition(toOfferCreateInput(draft)),
    onSuccess: (data) => setPreviewState({ fingerprint, data }),
    onError: () => toast.error("La définition n’a pas pu être vérifiée."),
  });
  const save = useMutation({
    mutationFn: () =>
      existing
        ? adminOfferApi.update(existing.id, toOfferUpdateInput(draft, existing))
        : adminOfferApi.create(toOfferCreateInput(draft)),
    onSuccess: async (result) => {
      completed.current = true;
      await queryClient.invalidateQueries({ queryKey: adminCommercialKeys.offers.all() });
      toast.success(existing ? "Brouillon enregistré" : "Offre créée");
      navigate(`/admin/offers/${result.offerId}`, { replace: true });
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : "L’offre n’a pas pu être enregistrée."),
  });
  const dirty = !completed.current && fingerprint !== initialFingerprint.current;
  const dirtyRef = useRef(dirty);
  dirtyRef.current = dirty;
  const blocker = useBlocker(useCallback(() => dirtyRef.current && !completed.current, []));
  useEffect(() => {
    if (blocker.state !== "blocked") return;
    if (window.confirm("Quitter sans enregistrer cette offre ?")) blocker.proceed();
    else blocker.reset();
  }, [blocker]);
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);
  const previewFresh = previewState?.fingerprint === fingerprint;
  const canSave = Boolean(previewFresh && previewState?.data.valid && !hasOfferDraftErrors(errors));
  const currentIndex = offerEditorSteps.findIndex((item) => item.value === step);
  const previous = currentIndex > 0 ? (offerEditorSteps[currentIndex - 1]?.value ?? null) : null;
  const next = currentIndex < offerEditorSteps.length - 1 ? (offerEditorSteps[currentIndex + 1]?.value ?? null) : null;
  const choicesLoading = plans.isLoading || addOns.isLoading || packages.isLoading;
  const choicesError = plans.isError || addOns.isError || packages.isError;
  const plansData = mergeChoices(plans.data?.content, resolved.data).filter((choice) => choice.ownerType === "PLAN");
  const addOnsData = mergeChoices(addOns.data?.content, resolved.data).filter(
    (choice) => choice.ownerType === "ADD_ON",
  );
  const packagesData = mergeChoices(packages.data?.content, resolved.data).filter(
    (choice) => choice.ownerType === "QUOTA_PACKAGE",
  );

  return (
    <section aria-label="Éditeur d’offre" className="space-y-6">
      <Button asChild className="-ms-2" size="sm" variant="ghost">
        <Link to={existing ? `/admin/offers/${existing.id}` : "/admin/offers"}>
          <ArrowLeftIcon className="rtl:rotate-180" />
          {existing ? "Offre" : "Offres"}
        </Link>
      </Button>
      <PageHeader title={existing ? "Modifier le brouillon" : "Nouvelle offre"} />
      <SectionTabs
        ariaLabel="Étapes de définition"
        items={offerEditorSteps}
        onValueChange={(value) => setStep(value as OfferEditorStep)}
        value={step}
      />
      <div className="min-h-[440px] py-2">
        {step === "scope" ? (
          <ScopeStep draft={draft} errors={submitted ? errors : {}} existing={existing} onChange={setDraft} />
        ) : step === "selection" ? (
          choicesLoading ? (
            <LoadingState />
          ) : choicesError ? (
            <ErrorState
              description="Les produits et prix n’ont pas pu être chargés."
              retry={() => void Promise.all([plans.refetch(), addOns.refetch(), packages.refetch()])}
            />
          ) : (
            <>
              <OfferProductSelector
                addOns={addOnsData}
                draft={draft}
                onChange={setDraft}
                packages={packagesData}
                plans={plansData}
                quotaResources={quotaResources.data ?? []}
              />
              <FieldError>{submitted ? errors.selection : undefined}</FieldError>
            </>
          )
        ) : step === "effect" ? (
          <EffectStep draft={draft} errors={submitted ? errors : {}} onChange={setDraft} />
        ) : step === "limits" ? (
          <LimitsStep draft={draft} errors={submitted ? errors : {}} existing={existing} onChange={setDraft} />
        ) : (
          <ReviewStep
            draft={draft}
            errors={errors}
            onPreview={() => {
              setSubmitted(true);
              if (!hasOfferDraftErrors(errors)) preview.mutate();
            }}
            preview={previewState?.data ?? null}
            previewFresh={previewFresh}
            previewing={preview.isPending}
          />
        )}
      </div>
      <footer className="flex flex-col-reverse justify-between gap-3 border-t pt-5 sm:flex-row">
        <Button disabled={!previous} onClick={() => previous && setStep(previous)} type="button" variant="ghost">
          <ArrowLeftIcon className="rtl:rotate-180" />
          Précédent
        </Button>
        {step === "review" ? (
          <Button disabled={!canSave || save.isPending} onClick={() => save.mutate()}>
            {save.isPending ? "Enregistrement…" : existing ? "Enregistrer le brouillon" : "Créer le brouillon"}
          </Button>
        ) : (
          <Button disabled={!next} onClick={() => next && setStep(next)} type="button">
            Continuer
            <ArrowRightIcon className="rtl:rotate-180" />
          </Button>
        )}
      </footer>
    </section>
  );
}

export function AdminOfferCreatePage() {
  const session = useAdminSession();
  return session.can(adminPermissions.offersCreate) && session.can(adminPermissions.offersPreviewCreateDefinition) ? (
    <OfferEditor />
  ) : (
    <PermissionState />
  );
}

export function AdminOfferEditPage() {
  const session = useAdminSession();
  const { offerId } = useParams();
  const id = offerId && /^[0-9a-f-]{36}$/i.test(offerId) ? offerId : "";
  const definition = useQuery({
    queryKey: adminCommercialKeys.offers.editableDefinition(id),
    queryFn: ({ signal }) => adminOfferApi.editableDefinition(id, { signal }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.offersReadEditableDefinition, Boolean(id)),
  });
  if (!id)
    return (
      <ErrorState description="L’identifiant présent dans l’adresse est invalide." title="Adresse d’offre invalide" />
    );
  if (
    !session.can(adminPermissions.offersUpdate) ||
    !session.can(adminPermissions.offersReadEditableDefinition) ||
    !session.can(adminPermissions.offersPreviewUpdateDefinition)
  )
    return <PermissionState />;
  if (definition.isLoading) return <LoadingState />;
  if (definition.isError || !definition.data)
    return <ErrorState retry={() => void definition.refetch()} title="Offre introuvable" />;
  return <OfferEditor existing={definition.data} />;
}
