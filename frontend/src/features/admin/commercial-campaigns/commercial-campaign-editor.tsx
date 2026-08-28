import { ArrowLeftIcon, ArrowRightIcon, WarningCircleIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useRef, useState } from "react";
import { Link, Navigate, useBlocker, useNavigate, useParams, useSearchParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { CommercialCampaignDetail, CommercialCampaignSegmentChoiceState } from "@/api/contracts";
import { ApiError } from "@/api/http";
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
import {
  adminCommercialKeys,
  commercialQueryEnabled,
  invalidateCommercialCampaignTargeting,
} from "@/features/commercial/commercial-query";
import { CommercialCampaignAudienceEditor } from "./commercial-campaign-audience-editor";
import {
  adjacentCampaignEditorSteps,
  type CampaignEditorStep,
  campaignEditorSteps,
  campaignEditorSuccessDestination,
  shouldBlockCampaignEditorNavigation,
} from "./commercial-campaign-editor-state";
import {
  type CommercialCampaignDraft,
  campaignAudienceMode,
  campaignMutationMessage,
  campaignSource,
  draftFromCommercialCampaign,
  emptyCommercialCampaignDraft,
  hasCampaignDraftErrors,
  toCommercialCampaignWriteInput,
  validateCommercialCampaignDraft,
  validCampaignId,
} from "./commercial-campaign-rules";

const validSteps = new Set(campaignEditorSteps.map((step) => step.value));

function FieldError({ children }: { children?: string }) {
  return children ? (
    <p className="text-xs text-destructive" role="alert">
      {children}
    </p>
  ) : null;
}

function DefinitionStep({
  draft,
  errors,
  onChange,
}: {
  draft: CommercialCampaignDraft;
  errors: ReturnType<typeof validateCommercialCampaignDraft>;
  onChange: (next: CommercialCampaignDraft) => void;
}) {
  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <div className="space-y-2">
        <Label htmlFor="campaign-name">Nom</Label>
        <Input
          id="campaign-name"
          maxLength={180}
          onChange={(event) => onChange({ ...draft, name: event.target.value })}
          value={draft.name}
        />
        <FieldError>{errors.name}</FieldError>
      </div>
      <div className="space-y-2">
        <Label htmlFor="campaign-source">Origine</Label>
        <Select
          onValueChange={(value) => onChange({ ...draft, source: value as CommercialCampaignDraft["source"] })}
          value={draft.source}
        >
          <SelectTrigger id="campaign-source">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {Object.entries(campaignSource).map(([value, label]) => (
              <SelectItem key={value} value={value}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-2 lg:col-span-2">
        <Label htmlFor="campaign-description">Description</Label>
        <Textarea
          id="campaign-description"
          maxLength={1000}
          onChange={(event) => onChange({ ...draft, description: event.target.value })}
          rows={4}
          value={draft.description}
        />
      </div>
      <div className="space-y-2 lg:col-span-2">
        <Label htmlFor="campaign-reason">Motif opérationnel</Label>
        <Textarea
          id="campaign-reason"
          maxLength={500}
          onChange={(event) => onChange({ ...draft, reason: event.target.value })}
          rows={3}
          value={draft.reason}
        />
        <FieldError>{errors.reason}</FieldError>
      </div>
    </div>
  );
}

function AudienceStep({
  draft,
  errors,
  onChange,
  onSegmentStateChange,
}: {
  draft: CommercialCampaignDraft;
  errors: ReturnType<typeof validateCommercialCampaignDraft>;
  onChange: (next: CommercialCampaignDraft) => void;
  onSegmentStateChange: (state: CommercialCampaignSegmentChoiceState | undefined) => void;
}) {
  return (
    <div className="space-y-6">
      <fieldset className="grid gap-2 sm:grid-cols-3">
        <legend className="mb-3 text-sm font-medium">Portée</legend>
        {Object.entries(campaignAudienceMode).map(([value, label]) => {
          const id = `campaign-audience-${value}`;
          return (
            <Label
              className="flex cursor-pointer items-center gap-3 rounded-lg border px-4 py-3 font-normal has-[:checked]:border-primary has-[:checked]:bg-primary/5"
              htmlFor={id}
              key={value}
            >
              <input
                checked={draft.audienceMode === value}
                className="accent-primary"
                id={id}
                name="campaign-audience"
                onChange={() => onChange({ ...draft, audienceMode: value as CommercialCampaignDraft["audienceMode"] })}
                type="radio"
              />
              {label}
            </Label>
          );
        })}
      </fieldset>
      <CommercialCampaignAudienceEditor draft={draft} onChange={onChange} onSegmentStateChange={onSegmentStateChange} />
      <FieldError>{errors.audience}</FieldError>
    </div>
  );
}

function CalendarStep({
  draft,
  errors,
  onChange,
}: {
  draft: CommercialCampaignDraft;
  errors: ReturnType<typeof validateCommercialCampaignDraft>;
  onChange: (next: CommercialCampaignDraft) => void;
}) {
  return (
    <div className="grid gap-6 sm:grid-cols-2">
      <div className="space-y-2">
        <Label htmlFor="campaign-start">Début</Label>
        <Input
          dir="ltr"
          id="campaign-start"
          onChange={(event) => onChange({ ...draft, startsAt: event.target.value })}
          type="datetime-local"
          value={draft.startsAt}
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor="campaign-end">Fin</Label>
        <Input
          dir="ltr"
          id="campaign-end"
          onChange={(event) => onChange({ ...draft, endsAt: event.target.value })}
          type="datetime-local"
          value={draft.endsAt}
        />
      </div>
      <div className="sm:col-span-2">
        <FieldError>{errors.window}</FieldError>
      </div>
      <p className="border-y py-4 text-sm text-muted-foreground sm:col-span-2">
        L’enregistrement crée un brouillon. La planification reste une opération séparée avec vérification signée de
        l’audience.
      </p>
    </div>
  );
}

function ReviewStep({
  draft,
  errors,
}: {
  draft: CommercialCampaignDraft;
  errors: ReturnType<typeof validateCommercialCampaignDraft>;
}) {
  return (
    <div className="space-y-5">
      {hasCampaignDraftErrors(errors) ? (
        <Alert variant="destructive">
          <WarningCircleIcon />
          <AlertTitle>Le brouillon est incomplet</AlertTitle>
          <AlertDescription>Corrigez les champs signalés avant l’enregistrement.</AlertDescription>
        </Alert>
      ) : null}
      <dl className="grid gap-x-8 gap-y-5 border-y py-5 sm:grid-cols-2">
        <div>
          <dt className="text-xs text-muted-foreground">Nom</dt>
          <dd className="mt-1 font-medium">{draft.name || "—"}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Origine</dt>
          <dd className="mt-1 font-medium">{campaignSource[draft.source]}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Audience</dt>
          <dd className="mt-1 font-medium">{campaignAudienceMode[draft.audienceMode]}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Taille configurée</dt>
          <dd className="mt-1 font-medium tabular-nums">
            {draft.audienceMode === "EXPLICIT_ACCOUNTS"
              ? `${draft.explicitAccountIds.length} compte(s)`
              : draft.audienceMode === "PUBLIC"
                ? "Dynamique"
                : "Audience du segment"}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Début</dt>
          <dd className="mt-1 font-medium" dir="ltr">
            {draft.startsAt || "—"}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Fin</dt>
          <dd className="mt-1 font-medium" dir="ltr">
            {draft.endsAt || "—"}
          </dd>
        </div>
        <div className="sm:col-span-2">
          <dt className="text-xs text-muted-foreground">Motif</dt>
          <dd className="mt-1 whitespace-pre-wrap text-sm">{draft.reason || "—"}</dd>
        </div>
      </dl>
    </div>
  );
}

function CampaignEditor({ existing }: { existing?: CommercialCampaignDetail }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const requestedStep = params.get("step");
  const step = (
    requestedStep && validSteps.has(requestedStep as CampaignEditorStep) ? requestedStep : "definition"
  ) as CampaignEditorStep;
  const setStep = (value: CampaignEditorStep) => {
    const next = new URLSearchParams(params);
    if (value === "definition") next.delete("step");
    else next.set("step", value);
    setParams(next, { replace: true });
  };
  const initial = useRef(existing ? draftFromCommercialCampaign(existing) : emptyCommercialCampaignDraft());
  const initialSerialized = useRef(JSON.stringify(initial.current));
  const [draft, setDraft] = useState(initial.current);
  const [segmentState, setSegmentState] = useState<CommercialCampaignSegmentChoiceState | undefined>();
  const onSegmentStateChange = useCallback(
    (state: CommercialCampaignSegmentChoiceState | undefined) => setSegmentState(state),
    [],
  );
  const [submitted, setSubmitted] = useState(false);
  const [versionConflict, setVersionConflict] = useState(false);
  const completed = useRef(false);
  const editorFocus = useRef<HTMLElement>(null);
  const errors = validateCommercialCampaignDraft(draft, segmentState);
  const mutation = useMutation({
    mutationFn: () =>
      existing
        ? adminApi.updateCommercialCampaign(existing.summary.id, {
            ...toCommercialCampaignWriteInput(draft),
            version: existing.summary.version,
          })
        : adminApi.createCommercialCampaign(toCommercialCampaignWriteInput(draft)),
    onSuccess: async (campaign) => {
      completed.current = true;
      await invalidateCommercialCampaignTargeting(
        queryClient,
        adminCommercialKeys.campaigns.detail(campaign.summary.id),
      );
      toast.success(existing ? "Brouillon enregistré" : "Campagne créée");
      navigate(
        campaignEditorSuccessDestination(
          campaign.summary.id,
          Boolean(existing),
          session.can(adminPermissions.campaignsRead),
          session.can(adminPermissions.campaignsList),
        ),
        { replace: true },
      );
    },
    onError: (error) => {
      if (error instanceof ApiError && error.code === "STALE_RESOURCE_VERSION") setVersionConflict(true);
      toast.error(campaignMutationMessage(error));
    },
  });
  const reload = useMutation({
    mutationFn: async () => {
      if (!existing) return null;
      await queryClient.invalidateQueries({ queryKey: adminCommercialKeys.campaigns.detail(existing.summary.id) });
      return adminApi.commercialCampaign(existing.summary.id);
    },
    onSuccess: (fresh) => {
      if (!fresh) return;
      const next = draftFromCommercialCampaign(fresh);
      initial.current = next;
      initialSerialized.current = JSON.stringify(next);
      setDraft(next);
      setVersionConflict(false);
      setSubmitted(false);
      queryClient.setQueryData(adminCommercialKeys.campaigns.detail(fresh.summary.id), fresh);
      toast.success("Nouvelle version chargée");
    },
  });
  const dirty = !completed.current && JSON.stringify(draft) !== initialSerialized.current;
  const dirtyRef = useRef(dirty);
  dirtyRef.current = dirty;
  const blocker = useBlocker(
    useCallback(
      ({ currentLocation, nextLocation }) =>
        shouldBlockCampaignEditorNavigation(
          dirtyRef.current,
          completed.current,
          currentLocation.pathname,
          nextLocation.pathname,
        ),
      [],
    ),
  );
  useEffect(() => {
    if (blocker.state !== "blocked") return;
    if (window.confirm("Quitter sans enregistrer cette campagne ? Les changements saisis seront perdus."))
      blocker.proceed();
    else blocker.reset();
  }, [blocker]);
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);
  const { previous, next } = adjacentCampaignEditorSteps(step);
  const submit = () => {
    setSubmitted(true);
    if (!hasCampaignDraftErrors(errors)) mutation.mutate();
  };
  return (
    <section aria-label="Éditeur de campagne" className="space-y-6" ref={editorFocus} tabIndex={-1}>
      <Button asChild className="-ms-2" size="sm" variant="ghost">
        <Link to={existing ? `/admin/campaigns/${existing.summary.id}` : "/admin/campaigns"}>
          <ArrowLeftIcon className="rtl:rotate-180" />
          {existing ? "Campagne" : "Campagnes"}
        </Link>
      </Button>
      <PageHeader title={existing ? "Modifier le brouillon" : "Nouvelle campagne"} />
      <SectionTabs
        ariaLabel="Étapes de définition"
        items={[...campaignEditorSteps]}
        onValueChange={(value) => setStep(value as CampaignEditorStep)}
        value={step}
      />
      <div className="min-h-[420px] py-2">
        {step === "definition" ? (
          <DefinitionStep draft={draft} errors={submitted ? errors : {}} onChange={setDraft} />
        ) : step === "audience" ? (
          <AudienceStep
            draft={draft}
            errors={submitted ? errors : {}}
            onChange={setDraft}
            onSegmentStateChange={onSegmentStateChange}
          />
        ) : step === "calendar" ? (
          <CalendarStep draft={draft} errors={submitted ? errors : {}} onChange={setDraft} />
        ) : (
          <ReviewStep draft={draft} errors={errors} />
        )}
      </div>
      {versionConflict ? (
        <Alert variant="destructive">
          <WarningCircleIcon />
          <AlertTitle>Le brouillon a changé ailleurs</AlertTitle>
          <AlertDescription>
            Vos saisies ne seront pas appliquées sans relecture.
            <Button
              className="mt-3"
              disabled={reload.isPending}
              onClick={() => reload.mutate()}
              size="sm"
              type="button"
              variant="outline"
            >
              {reload.isPending ? "Rechargement…" : "Recharger et abandonner mes modifications"}
            </Button>
          </AlertDescription>
        </Alert>
      ) : null}
      <footer className="flex flex-col-reverse justify-between gap-3 border-t pt-5 sm:flex-row">
        <Button disabled={!previous} onClick={() => previous && setStep(previous)} type="button" variant="ghost">
          <ArrowLeftIcon className="rtl:rotate-180" />
          Précédent
        </Button>
        {step === "review" ? (
          <Button disabled={mutation.isPending || versionConflict} onClick={submit}>
            {mutation.isPending ? "Enregistrement…" : existing ? "Enregistrer le brouillon" : "Créer le brouillon"}
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

export function AdminCommercialCampaignCreatePage() {
  const session = useAdminSession();
  return session.can(adminPermissions.campaignsCreate) ? <CampaignEditor /> : <PermissionState />;
}

export function AdminCommercialCampaignEditPage() {
  const session = useAdminSession();
  const { campaignId } = useParams();
  const id = validCampaignId(campaignId) ? campaignId : "";
  const campaign = useQuery({
    queryKey: adminCommercialKeys.campaigns.detail(id),
    queryFn: () => adminApi.commercialCampaign(id),
    enabled: commercialQueryEnabled(session.can, adminPermissions.campaignsRead, Boolean(id)),
  });
  if (!session.can(adminPermissions.campaignsUpdate)) return <PermissionState />;
  if (!id)
    return (
      <ErrorState
        description="L’identifiant présent dans l’adresse est invalide."
        title="Adresse de campagne invalide"
      />
    );
  if (!session.can(adminPermissions.campaignsRead)) return <Navigate replace to={`/admin/campaigns/${id}`} />;
  if (campaign.isLoading) return <LoadingState />;
  if (campaign.isError || !campaign.data)
    return <ErrorState retry={() => void campaign.refetch()} title="Campagne introuvable" />;
  if (!campaign.data.summary.availableActions.includes("UPDATE"))
    return <Navigate replace to={`/admin/campaigns/${id}`} />;
  return <CampaignEditor existing={campaign.data} />;
}
