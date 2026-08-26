import { ArrowLeftIcon, CheckCircleIcon, PlusIcon, TrashIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useBlocker, useNavigate, useSearchParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { AssignPlanFeatureInput, BillingCycle } from "@/api/contracts";
import { ApiError } from "@/api/http";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { QuotaEditor } from "@/components/patterns/quota-editor";
import { StatusBadge } from "@/components/patterns/status-badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { prefillFromSource, requiredCreationPermission } from "@/features/admin/plans/plan-create-rules";
import { cycleText, featureModePresentation, money, selectableCycles } from "@/features/admin/plans/plan-presentation";
import {
  adminCommercialKeys,
  commercialQueryEnabled,
  invalidateAdminCommercial,
} from "@/features/commercial/commercial-query";

type StagedFeature = AssignPlanFeatureInput;

const STEPS = ["Point de départ", "Identité", "Tarification", "Composition & quotas", "Révision"] as const;

/**
 * The guided creation of PLAN-FLOW-004, in the decided order: nothing is persisted until the
 * review step confirms — the draft is then created and, for a blank start, the staged
 * composition is applied to it. Activation stays a separate lifecycle action with backend
 * validation, as the flow's final step requires.
 */
export function AdminPlanCreatePage() {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [step, setStep] = useState(0);

  const plans = useQuery({
    queryKey: adminCommercialKeys.plans.list(),
    queryFn: adminApi.plans,
    enabled: commercialQueryEnabled(session.can, adminPermissions.plansList),
  });
  const [sourceId, setSourceId] = useState(params.get("from") ?? "");
  const source = (plans.data ?? []).find((plan) => plan.id === sourceId);

  const [fields, setFields] = useState({
    name: "",
    description: "",
    price: "0",
    currencyCode: "MAD",
    billingCycle: "MONTHLY" as BillingCycle,
  });
  const setField = (patch: Partial<typeof fields>) => setFields((current) => ({ ...current, ...patch }));

  // A deep link (?from=…) resolves only once the plans have loaded; prefill then, and only while
  // the form is still untouched so a slow response cannot overwrite what the operator typed.
  useEffect(() => {
    if (!source || fields.name) return;
    setFields(prefillFromSource(source));
  }, [source, fields.name]);

  const canReadCatalog = session.can(adminPermissions.registryFeatureCatalog);
  const catalog = useQuery({
    queryKey: adminCommercialKeys.registry.featureCatalog("PLAN_ASSIGNABLE"),
    queryFn: () => adminApi.featureCatalog("PLAN_ASSIGNABLE"),
    enabled: commercialQueryEnabled(session.can, adminPermissions.registryFeatureCatalog),
  });
  const canPreviewSource = session.can(adminPermissions.plansListFeatures);
  const sourceFeatures = useQuery({
    queryKey: adminCommercialKeys.plans.features(sourceId),
    queryFn: () => adminApi.planFeatures(sourceId),
    enabled: commercialQueryEnabled(session.can, adminPermissions.plansListFeatures, Boolean(sourceId)),
  });

  const canStageComposition = !source && canReadCatalog && session.can(adminPermissions.plansAssignFeature);
  const [staged, setStaged] = useState<StagedFeature[]>([]);
  const catalogFeatures = useMemo(
    () => (catalog.data ?? []).flatMap((module) => module.features).filter((feature) => feature.planAssignable),
    [catalog.data],
  );
  const addable = catalogFeatures.filter((feature) => !staged.some((entry) => entry.featureCode === feature.code));
  const definitionOf = (featureCode: string) => catalogFeatures.find((feature) => feature.code === featureCode);

  const completedRef = useRef(false);
  const save = useMutation({
    mutationFn: () => {
      const input = {
        name: fields.name,
        description: fields.description,
        price: Number(fields.price),
        currencyCode: fields.currencyCode.toUpperCase(),
        billingCycle: fields.billingCycle,
      };
      // One transactional command: the backend creates the plan and its whole staged
      // composition together, so no partial draft can differ from what was reviewed.
      return source ? adminApi.duplicatePlan(source.id, input) : adminApi.createPlan({ ...input, features: staged });
    },
    onSuccess: (plan) => {
      void invalidateAdminCommercial(queryClient, adminCommercialKeys.plans.all());
      completedRef.current = true;
      toast.success("Brouillon créé", { description: "Activez-le depuis le cycle de vie une fois vérifié." });
      navigate(`/admin/plans/${plan.id}/features`);
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : "Création impossible"),
  });

  // An abandoned wizard is real loss: nothing is saved before the final step, so leaving —
  // in-app or by closing the tab — asks first while anything has been entered.
  const dirty =
    !completedRef.current &&
    (Boolean(sourceId) || staged.length > 0 || fields.name !== "" || fields.description !== "");
  const dirtyRef = useRef(dirty);
  dirtyRef.current = dirty;
  const blocker = useBlocker(useCallback(() => dirtyRef.current && !completedRef.current, []));
  useEffect(() => {
    if (blocker.state !== "blocked") return;
    if (window.confirm("Quitter sans créer le forfait ? Les choix saisis seront perdus.")) blocker.proceed();
    else blocker.reset();
  }, [blocker]);
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  if (!session.can(requiredCreationPermission(Boolean(source)))) {
    return (
      <p className="py-8 text-sm text-muted-foreground">
        {source ? "Vous n’êtes pas autorisé à dupliquer un forfait." : "Vous n’êtes pas autorisé à créer un forfait."}
      </p>
    );
  }

  const identityReady = fields.name.trim().length > 0;
  const pricingReady =
    fields.price.trim() !== "" &&
    Number.isFinite(Number(fields.price)) &&
    Number(fields.price) >= 0 &&
    /^[A-Z]{3}$/.test(fields.currencyCode);
  const stepReady = step === 1 ? identityReady : step === 2 ? pricingReady : true;

  return (
    <div className="space-y-7">
      <div>
        <Button asChild variant="ghost">
          <Link to="/admin/plans">
            <ArrowLeftIcon />
            Forfaits
          </Link>
        </Button>
      </div>
      <PageHeader
        description="Rien n’est enregistré avant la révision finale ; le forfait démarre en brouillon."
        title="Créer un forfait"
      />

      <ol className="flex flex-wrap gap-x-5 gap-y-2 border-b pb-4 text-sm" aria-label="Étapes de création">
        {STEPS.map((label, index) => (
          <li className="flex items-center gap-2" key={label}>
            <span
              className={`grid size-6 place-items-center rounded-full border text-xs tabular-nums ${
                index === step
                  ? "border-primary bg-primary text-primary-foreground"
                  : index < step
                    ? "border-primary/50 text-primary"
                    : "text-muted-foreground"
              }`}
            >
              {index + 1}
            </span>
            <span className={index === step ? "font-medium" : "text-muted-foreground"}>{label}</span>
          </li>
        ))}
      </ol>

      {step === 0 ? (
        <section className="max-w-4xl space-y-4">
          <p className="text-sm text-muted-foreground">
            Partir d’un forfait existant copie sa composition de fonctionnalités et ses quotas de base — pas ses
            attaches d’add-ons ni de paquets de quotas, ni ses abonnés. La duplication demande l’autorisation
            correspondante.
          </p>
          <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
            <button
              aria-pressed={sourceId === ""}
              className={`rounded-lg border p-4 text-start transition-colors hover:border-ring/50 ${
                sourceId === "" ? "border-primary ring-1 ring-primary" : ""
              }`}
              onClick={() => setSourceId("")}
              type="button"
            >
              <span className="block text-sm font-medium">Vierge</span>
              <span className="mt-0.5 block text-xs text-muted-foreground">Composition vide, à construire.</span>
            </button>
            {(plans.data ?? []).map((plan) => (
              <button
                aria-pressed={sourceId === plan.id}
                className={`rounded-lg border p-4 text-start transition-colors hover:border-ring/50 disabled:cursor-not-allowed disabled:opacity-45 ${
                  sourceId === plan.id ? "border-primary ring-1 ring-primary" : ""
                }`}
                disabled={!session.can(adminPermissions.plansDuplicate)}
                key={plan.id}
                onClick={() => {
                  setSourceId(plan.id);
                  setFields(prefillFromSource(plan));
                }}
                title={
                  session.can(adminPermissions.plansDuplicate)
                    ? undefined
                    : "Vous n’êtes pas autorisé à dupliquer un forfait"
                }
                type="button"
              >
                <span className="block truncate text-sm font-medium">{plan.name}</span>
                <span className="mt-0.5 block text-xs text-muted-foreground">
                  {money(plan.price, plan.currencyCode)} / {cycleText[plan.billingCycle]}
                </span>
              </button>
            ))}
          </div>
        </section>
      ) : null}

      {step === 1 ? (
        <section className="grid max-w-4xl gap-5 sm:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="new-plan-name">Nom</Label>
            <Input
              id="new-plan-name"
              onChange={(event) => setField({ name: event.target.value })}
              required
              value={fields.name}
            />
            <p className="text-xs leading-4 text-muted-foreground">Nom commercial, tel qu’affiché aux clients.</p>
          </div>
          <div className="space-y-2 sm:col-span-2">
            <Label htmlFor="new-plan-description">Description</Label>
            <Textarea
              id="new-plan-description"
              onChange={(event) => setField({ description: event.target.value })}
              value={fields.description}
            />
            <p className="text-xs leading-4 text-muted-foreground">
              Facultative — résumé montré dans le catalogue d’abonnement.
            </p>
          </div>
        </section>
      ) : null}

      {step === 2 ? (
        <section className="grid max-w-4xl gap-5 sm:grid-cols-3">
          <div className="space-y-2">
            <Label htmlFor="new-plan-price">Prix</Label>
            <Input
              id="new-plan-price"
              min="0"
              onChange={(event) => setField({ price: event.target.value })}
              step="0.01"
              type="number"
              value={fields.price}
            />
            <p className="text-xs leading-4 text-muted-foreground">Montant facturé à chaque cycle.</p>
          </div>
          <div className="space-y-2">
            <Label htmlFor="new-plan-currency">Devise</Label>
            <Input
              dir="ltr"
              id="new-plan-currency"
              maxLength={3}
              onChange={(event) => setField({ currencyCode: event.target.value.toUpperCase() })}
              value={fields.currencyCode}
            />
            <p className="text-xs leading-4 text-muted-foreground">
              Code ISO à 3 lettres. Les paquets de quotas attachés devront utiliser la même devise.
            </p>
          </div>
          <div className="space-y-2">
            <Label>Cycle</Label>
            <Select
              onValueChange={(value) => setField({ billingCycle: value as BillingCycle })}
              value={fields.billingCycle}
            >
              <SelectTrigger aria-label="Cycle de facturation">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {selectableCycles.map((cycle) => (
                  <SelectItem key={cycle} value={cycle}>
                    {cycle === "MONTHLY" ? "Mensuel" : "Annuel"}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <p className="text-xs leading-4 text-muted-foreground">
              Les licences perpétuelles sont différées par la politique tarifaire.
            </p>
          </div>
        </section>
      ) : null}

      {step === 3 ? (
        <section className="max-w-4xl space-y-4">
          {source ? (
            <>
              <p className="text-sm text-muted-foreground">
                La composition ci-dessous sera copiée de{" "}
                <span className="font-medium text-foreground">{source.name}</span>. Elle restera modifiable sur le
                brouillon.
              </p>
              {canPreviewSource ? (
                <ul className="divide-y border-y text-sm">
                  {(sourceFeatures.data ?? []).map((feature) => (
                    <li className="flex items-center justify-between gap-3 py-2.5" key={feature.id}>
                      <span>{definitionOf(feature.featureCode)?.displayName ?? feature.featureCode}</span>
                      <span className="text-xs text-muted-foreground">
                        {featureModePresentation[feature.mode]?.label ?? feature.mode}
                      </span>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-xs text-muted-foreground">
                  Aperçu indisponible avec vos permissions ; la copie s’effectue quand même.
                </p>
              )}
            </>
          ) : canStageComposition ? (
            <>
              <div className="flex items-center justify-between gap-3">
                <p className="text-sm text-muted-foreground">
                  Composez le futur brouillon. Chaque ressource devra recevoir une décision explicite — une limite ou un
                  illimité assumé — avant que le forfait puisse être activé.
                </p>
                <Select
                  onValueChange={(code) =>
                    setStaged((current) => [...current, { featureCode: code, mode: "INCLUDED", quotaConfigs: [] }])
                  }
                  value=""
                >
                  <SelectTrigger aria-label="Ajouter une fonctionnalité" className="w-64">
                    <SelectValue placeholder="Ajouter une fonctionnalité…" />
                  </SelectTrigger>
                  <SelectContent>
                    {addable.map((feature) => (
                      <SelectItem key={feature.id} value={feature.code}>
                        {feature.displayName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              {staged.length === 0 ? (
                <p className="border-y py-8 text-center text-sm text-muted-foreground">
                  Aucune fonctionnalité pour l’instant — un forfait vide ne pourra pas être activé.
                </p>
              ) : (
                <ul className="divide-y border-y">
                  {staged.map((entry, index) => {
                    const definition = definitionOf(entry.featureCode);
                    return (
                      <li className="space-y-3 py-4" key={entry.featureCode}>
                        <div className="flex items-center justify-between gap-3">
                          <div className="min-w-0">
                            <p className="text-sm font-medium">{definition?.displayName ?? entry.featureCode}</p>
                            {definition?.description ? (
                              <p className="mt-0.5 text-xs text-muted-foreground">{definition.description}</p>
                            ) : null}
                          </div>
                          <div className="flex items-center gap-2">
                            <Select
                              onValueChange={(mode) =>
                                setStaged((current) =>
                                  current.map((item, at) =>
                                    at === index
                                      ? {
                                          ...item,
                                          mode: mode as AssignPlanFeatureInput["mode"],
                                          quotaConfigs: mode === "INCLUDED" ? item.quotaConfigs : [],
                                        }
                                      : item,
                                  ),
                                )
                              }
                              value={entry.mode}
                            >
                              <SelectTrigger aria-label="Mode" className="w-44">
                                <SelectValue />
                              </SelectTrigger>
                              <SelectContent>
                                <SelectItem value="INCLUDED">Incluse</SelectItem>
                                <SelectItem value="OPTIONAL_ADD_ON">Add-on optionnel</SelectItem>
                                <SelectItem value="BLOCKED_FOR_PLAN">Bloquée</SelectItem>
                              </SelectContent>
                            </Select>
                            <Button
                              aria-label={`Retirer ${definition?.displayName ?? entry.featureCode}`}
                              onClick={() => setStaged((current) => current.filter((_, at) => at !== index))}
                              size="icon-sm"
                              type="button"
                              variant="ghost"
                            >
                              <TrashIcon />
                            </Button>
                          </div>
                        </div>
                        {entry.mode === "INCLUDED" ? (
                          <QuotaEditor
                            onChange={(quotaConfigs) =>
                              setStaged((current) =>
                                current.map((item, at) => (at === index ? { ...item, quotaConfigs } : item)),
                              )
                            }
                            slots={definition?.quotaSchema ?? []}
                            value={entry.quotaConfigs}
                          />
                        ) : null}
                      </li>
                    );
                  })}
                </ul>
              )}
            </>
          ) : (
            <p className="text-sm text-muted-foreground">
              {canReadCatalog
                ? "Vos permissions ne couvrent pas la composition — le brouillon se créera vide et un opérateur autorisé le composera."
                : "Le catalogue des fonctionnalités n’est pas lisible avec vos permissions — le brouillon se créera vide."}
            </p>
          )}
        </section>
      ) : null}

      {step === 4 ? (
        <section className="max-w-4xl space-y-6">
          <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_18rem]">
            <dl className="divide-y border-y text-sm">
              <div className="grid gap-1 py-3 sm:grid-cols-[10rem_1fr]">
                <dt className="text-muted-foreground">Identité</dt>
                <dd className="font-medium">{fields.name}</dd>
              </div>
              <div className="grid gap-1 py-3 sm:grid-cols-[10rem_1fr]">
                <dt className="text-muted-foreground">Ce que le client paie</dt>
                <dd className="font-medium tabular-nums">
                  {money(
                    Number(fields.price) || 0,
                    /^[A-Z]{3}$/.test(fields.currencyCode) ? fields.currencyCode : "MAD",
                  )}{" "}
                  / {cycleText[fields.billingCycle]}
                </dd>
              </div>
              <div className="grid gap-1 py-3 sm:grid-cols-[10rem_1fr]">
                <dt className="text-muted-foreground">Ce que le client reçoit</dt>
                <dd>
                  {source ? (
                    `La composition et les quotas de base de ${source.name} — sans ses attaches d'add-ons ni de paquets de quotas.`
                  ) : staged.length ? (
                    <ul className="space-y-1">
                      {staged.map((entry) => (
                        <li key={entry.featureCode}>
                          {definitionOf(entry.featureCode)?.displayName ?? entry.featureCode}
                          <span className="text-xs text-muted-foreground">
                            {" "}
                            · {featureModePresentation[entry.mode]?.label ?? entry.mode}
                            {entry.quotaConfigs.length
                              ? ` · ${entry.quotaConfigs
                                  .map((config) =>
                                    config.mode === "UNLIMITED" || config.limit === null
                                      ? `illimité ${config.resource}`
                                      : `${config.limit} ${config.resource}`,
                                  )
                                  .join(", ")}`
                              : ""}
                          </span>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    "Rien pour l’instant — la composition se fera sur le brouillon."
                  )}
                </dd>
              </div>
            </dl>
            <aside className="rounded-xl border bg-background/40 p-5 lg:self-start">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <h3 className="truncate font-semibold">{fields.name || "Nom du forfait"}</h3>
                </div>
                <StatusBadge tone="info">Brouillon</StatusBadge>
              </div>
              <p className="mt-3 line-clamp-2 min-h-8 text-xs leading-4 text-muted-foreground">
                {fields.description || "Aucune description."}
              </p>
              <p className="mt-4 text-2xl font-semibold tabular-nums">
                {money(Number(fields.price) || 0, /^[A-Z]{3}$/.test(fields.currencyCode) ? fields.currencyCode : "MAD")}
                <span className="ms-1.5 text-sm font-normal text-muted-foreground">
                  / {cycleText[fields.billingCycle]}
                </span>
              </p>
            </aside>
          </div>
          <p className="flex items-center gap-2 text-xs text-muted-foreground">
            <CheckCircleIcon className="size-4" />
            L’activation restera une étape distincte : le backend valide la composition avant qu’un brouillon devienne
            souscriptible.
          </p>
        </section>
      ) : null}

      <div className="flex items-center justify-between border-t pt-5">
        <Button disabled={step === 0} onClick={() => setStep((current) => current - 1)} type="button" variant="outline">
          Retour
        </Button>
        {step < STEPS.length - 1 ? (
          <Button disabled={!stepReady} onClick={() => setStep((current) => current + 1)} type="button">
            Continuer
          </Button>
        ) : (
          <Button
            disabled={save.isPending || !identityReady || !pricingReady}
            onClick={() => save.mutate()}
            type="button"
          >
            <PlusIcon />
            {save.isPending ? "Création…" : "Créer le brouillon"}
          </Button>
        )}
      </div>
    </div>
  );
}
