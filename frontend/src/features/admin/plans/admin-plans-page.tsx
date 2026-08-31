import {
  ArrowLeftIcon,
  ArrowRightIcon,
  CopyIcon,
  GitBranchIcon,
  MagnifyingGlassIcon,
  PencilSimpleIcon,
  PlusIcon,
  TrashIcon,
} from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type FormEvent, useEffect, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type {
  BillingCycle,
  CommercialProductAction,
  ExtensionCompatibility,
  Plan,
  PlanDeletionPreview,
  PlanFeature,
  PlanFeatureMode,
  QuotaLimit,
  RegistryFeature,
} from "@/api/contracts";
import { ApiError } from "@/api/http";
import { adminPermissions, adminSubscriptionDetailSurfacePermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable, DataTableExpander } from "@/components/patterns/data-table";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { QuotaEditor } from "@/components/patterns/quota-editor";
import { ReferenceTagButton } from "@/components/patterns/reference-tag";
import { EmptyState, ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { RowAction } from "@/components/patterns/row-action";
import { SectionTabs } from "@/components/patterns/section-tabs";
import { StatusBadge } from "@/components/patterns/status-badge";
import { StatusText } from "@/components/patterns/status-text";
import { TableActionsCell } from "@/components/patterns/table-actions-cell";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import {
  CommercialAvailabilityHistory,
  CommercialAvailabilityPanel,
  PlanCompatibilityPanel,
} from "@/features/admin/commercial/commercial-detail-panels";
import { ChoiceLoadState } from "@/features/admin/commercial/commercial-form-primitives";
import { CommercialLifecycleDialog } from "@/features/admin/commercial/commercial-lifecycle-dialog";
import { PlanSchema } from "@/features/admin/plans/admin-plan-schema";
import {
  addOnAvailabilityLabel,
  cycleText,
  featureModePresentation,
  money,
  planTone,
  selectableCycles,
  statusText,
} from "@/features/admin/plans/plan-presentation";
import { quotaLinesOf } from "@/features/admin/plans/plan-schema-model";
import {
  readPlanSubscriberListState,
  writePlanSubscriberListState,
} from "@/features/admin/plans/plan-subscriber-list-state";
import { ProductPricePanel } from "@/features/admin/price-books/product-price-panel";
import {
  adminCommercialKeys,
  commercialQueryEnabled,
  invalidateAdminCommercial,
} from "@/features/commercial/commercial-query";
import { subscriptionStatusPresentation } from "@/features/commercial/subscription-presentation";
import { commercialAmount, isCommercialAmount } from "@/lib/exact-decimal";
import { useDebouncedValue } from "@/lib/use-debounced-value";

type PlanFeatureCommercialRow = {
  feature: PlanFeature;
  definition: RegistryFeature | undefined;
  addOns: ExtensionCompatibility[];
  capacityPacks: ExtensionCompatibility[];
};

const planFeatureColumn = createDataColumns<PlanFeatureCommercialRow>();

function PlanFormDialog({
  source,
  mode = "create",
  trigger,
  open: controlledOpen,
  onOpenChange,
}: {
  source?: Plan;
  mode?: "create" | "edit" | "duplicate" | "revise";
  /** Omit to control the dialog from outside through `open`/`onOpenChange`. */
  trigger?: React.ReactNode;
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
}) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [uncontrolledOpen, setUncontrolledOpen] = useState(false);
  const open = trigger ? uncontrolledOpen : (controlledOpen ?? false);
  const setOpen = (next: boolean) => {
    if (trigger) setUncontrolledOpen(next);
    else onOpenChange?.(next);
  };
  const [name, setName] = useState(source?.name ?? "");
  const [description, setDescription] = useState(source?.description ?? "");
  const [price, setPrice] = useState(String(source?.price ?? 0));
  const [currencyCode, setCurrency] = useState(source?.currencyCode ?? "MAD");
  const [billingCycle, setCycle] = useState<BillingCycle>(source?.billingCycle ?? "MONTHLY");
  const save = useMutation({
    mutationFn: () => {
      const input = {
        name,
        description,
        price: commercialAmount(price),
        currencyCode: currencyCode.toUpperCase(),
        billingCycle,
      };
      if (mode === "edit" && source)
        return adminApi.updatePlan(source.id, { ...input, expectedVersion: source.version });
      if (mode === "duplicate" && source) return adminApi.duplicatePlan(source.id, source.version, input);
      if (mode === "revise" && source) return adminApi.revisePlan(source.id, source.version, input);
      return adminApi.createPlan({ ...input, features: [] });
    },
    onSuccess: (plan) => {
      void invalidateAdminCommercial(queryClient, adminCommercialKeys.plans.all());
      toast.success(mode === "edit" ? "Forfait mis à jour" : "Brouillon créé");
      setOpen(false);
      navigate(`/admin/plans/${plan.id}`);
    },
  });
  const requiredPermission =
    mode === "edit"
      ? adminPermissions.plansUpdate
      : mode === "duplicate"
        ? adminPermissions.plansDuplicate
        : mode === "revise"
          ? adminPermissions.plansRevise
          : adminPermissions.plansCreate;
  if (!session.can(requiredPermission)) return null;
  return (
    <Dialog onOpenChange={setOpen} open={open}>
      {trigger ? <DialogTrigger asChild>{trigger}</DialogTrigger> : null}
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>
            {mode === "edit"
              ? "Modifier le forfait"
              : mode === "revise"
                ? "Créer une révision"
                : mode === "duplicate"
                  ? "Dupliquer le forfait"
                  : "Créer un forfait"}
          </DialogTitle>
          <DialogDescription>
            {mode === "edit"
              ? "Modifie ce forfait sans créer de révision : la lignée et les abonnés ne changent pas."
              : mode === "revise"
                ? `Nouvelle révision R${(source?.revisionNumber ?? 0) + 1} dans la même lignée : les abonnés actuels restent sur leur révision. Après activation, la nouvelle pourra être choisie explicitement pour de prochaines souscriptions. Elle démarre en brouillon.`
                : mode === "duplicate"
                  ? "Copie indépendante dans une nouvelle lignée, sans lien avec les abonnés du forfait source. Elle démarre en brouillon."
                  : "Un nouveau forfait démarre en brouillon : configurez ses fonctionnalités et quotas avant de l’activer."}
          </DialogDescription>
        </DialogHeader>
        <form
          className="grid gap-5 sm:grid-cols-2"
          onSubmit={(event: FormEvent) => {
            event.preventDefault();
            if (!isCommercialAmount(price)) return;
            save.mutate();
          }}
        >
          <div className="space-y-2">
            <Label htmlFor="plan-name">Nom</Label>
            <Input id="plan-name" onChange={(event) => setName(event.target.value)} required value={name} />
            <p className="text-xs leading-4 text-muted-foreground">Nom commercial, tel qu’affiché aux clients.</p>
          </div>
          <div className="space-y-2 sm:col-span-2">
            <Label htmlFor="plan-description">Description</Label>
            <Textarea
              id="plan-description"
              onChange={(event) => setDescription(event.target.value)}
              value={description}
            />
            <p className="text-xs leading-4 text-muted-foreground">
              Facultative — résumé montré dans le catalogue d’abonnement.
            </p>
          </div>
          <div className="space-y-2">
            <Label htmlFor="plan-price">Prix</Label>
            <Input
              id="plan-price"
              inputMode="decimal"
              onChange={(event) => setPrice(event.target.value)}
              value={price}
            />
            <p className="text-xs leading-4 text-muted-foreground">Montant facturé à chaque cycle.</p>
          </div>
          <div className="space-y-2">
            <Label htmlFor="plan-currency">Devise</Label>
            <Input
              id="plan-currency"
              maxLength={3}
              onChange={(event) => setCurrency(event.target.value.toUpperCase())}
              value={currencyCode}
            />
            <p className="text-xs leading-4 text-muted-foreground">Code ISO à 3 lettres (MAD, EUR…).</p>
          </div>
          <div className="space-y-2">
            <Label>Cycle</Label>
            <Select onValueChange={(value) => setCycle(value as BillingCycle)} value={billingCycle}>
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
          <div className="flex items-end justify-end gap-2 sm:col-span-2">
            <Button onClick={() => setOpen(false)} type="button" variant="outline">
              Annuler
            </Button>
            <Button disabled={save.isPending || !isCommercialAmount(price)} type="submit">
              {save.isPending ? "Enregistrement…" : "Enregistrer"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function PlanFeatureDialog({
  plan,
  item,
  available,
  open: controlledOpen,
  onOpenChange,
  withTrigger = true,
}: {
  plan: Plan;
  item?: PlanFeature;
  available: RegistryFeature[];
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  /** False when the dialog is driven from a row action instead of its own button. */
  withTrigger?: boolean;
}) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [uncontrolledOpen, setUncontrolledOpen] = useState(false);
  const open = withTrigger ? uncontrolledOpen : (controlledOpen ?? false);
  const setOpen = (next: boolean) => {
    if (withTrigger) setUncontrolledOpen(next);
    else onOpenChange?.(next);
  };
  const [featureCode, setFeatureCode] = useState(item?.featureCode ?? "");
  const [mode, setMode] = useState<PlanFeatureMode>(item?.mode ?? "INCLUDED");
  const [quotas, setQuotas] = useState<QuotaLimit[]>(item?.quotaConfigs ?? []);
  const definition = available.find((feature) => feature.code === featureCode);
  const save = useMutation({
    mutationFn: () => {
      const input = { featureCode, mode, quotaConfigs: mode === "INCLUDED" ? quotas : [] };
      return item
        ? adminApi.updatePlanFeature(plan.id, item.id, plan.version, input)
        : adminApi.assignPlanFeature(plan.id, plan.version, input);
    },
    onSuccess: () => {
      void invalidateAdminCommercial(queryClient, adminCommercialKeys.plans.all());
      toast.success(item ? "Configuration mise à jour" : "Fonctionnalité ajoutée");
      setOpen(false);
    },
  });
  if (!session.can(item ? adminPermissions.plansUpdateFeature : adminPermissions.plansAssignFeature)) return null;
  return (
    <Dialog onOpenChange={setOpen} open={open}>
      {withTrigger ? (
        <DialogTrigger asChild>
          {item ? (
            <Button aria-label="Modifier la configuration" size="icon-sm" variant="ghost">
              <PencilSimpleIcon />
            </Button>
          ) : (
            <Button size="sm">
              <PlusIcon />
              Ajouter
            </Button>
          )}
        </DialogTrigger>
      ) : null}
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{item ? "Configurer la fonctionnalité" : "Ajouter une fonctionnalité"}</DialogTitle>
          <DialogDescription>Définissez ce que le client reçoit avec ce forfait.</DialogDescription>
        </DialogHeader>
        <div className="space-y-5">
          <div className="space-y-2">
            <Label>Fonctionnalité</Label>
            <Select disabled={Boolean(item)} onValueChange={setFeatureCode} value={featureCode}>
              <SelectTrigger aria-label="Fonctionnalité">
                <SelectValue placeholder="Sélectionner" />
              </SelectTrigger>
              <SelectContent>
                {available.map((feature) => (
                  <SelectItem key={feature.id} value={feature.code}>
                    {feature.displayName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label>Disponibilité dans le forfait</Label>
            <Select onValueChange={(value) => setMode(value as PlanFeatureMode)} value={mode}>
              <SelectTrigger aria-label="Mode de la fonctionnalité">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="INCLUDED">Incluse</SelectItem>
                <SelectItem value="OPTIONAL_ADD_ON">Disponible en add-on</SelectItem>
                <SelectItem value="BLOCKED_FOR_PLAN">Indisponible</SelectItem>
              </SelectContent>
            </Select>
            <p className="text-xs leading-4 text-muted-foreground">
              {mode === "INCLUDED"
                ? "Comprise dans le forfait, avec ses limites incluses."
                : mode === "OPTIONAL_ADD_ON"
                  ? "Le client peut l’obtenir en achetant un add-on compatible."
                  : "Le client ne peut pas l’obtenir avec ce forfait."}
            </p>
          </div>
          <div className="space-y-2">
            <Label>Limites incluses</Label>
            <QuotaEditor
              disabled={mode !== "INCLUDED"}
              onChange={setQuotas}
              slots={definition?.quotaSchema ?? []}
              value={quotas}
            />
            {mode === "INCLUDED" ? (
              <p className="text-xs leading-4 text-muted-foreground">
                Chaque ressource exige une décision explicite — limite chiffrée ou illimité assumé — avant l’activation
                du forfait. Les packs de capacité achetés s’ajoutent à ces limites.
              </p>
            ) : null}
            {mode !== "INCLUDED" ? (
              <p className="text-xs text-muted-foreground">
                Les limites incluses s’appliquent uniquement aux fonctionnalités comprises dans le forfait.
              </p>
            ) : null}
          </div>
          <div className="flex justify-end">
            <Button disabled={!featureCode || !definition || save.isPending} onClick={() => save.mutate()}>
              Enregistrer
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function PlanFeatures({ plan }: { plan: Plan }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const canInspectExtensions = session.can(adminPermissions.commercialInspectCompatibility);
  const canReadCatalog = session.can(adminPermissions.registryFeatureCatalog);
  const canAssignFeature = session.can(adminPermissions.plansAssignFeature);
  const features = useQuery({
    queryKey: adminCommercialKeys.plans.features(plan.id),
    queryFn: () => adminApi.planFeatures(plan.id),
    enabled: commercialQueryEnabled(session.can, adminPermissions.plansListFeatures),
  });
  const catalog = useQuery({
    queryKey: adminCommercialKeys.registry.featureCatalog("PLAN_ASSIGNABLE"),
    queryFn: () => adminApi.featureCatalog("PLAN_ASSIGNABLE"),
    // Without registry access the rows fall back to raw codes instead of provoking 403s.
    enabled: commercialQueryEnabled(session.can, adminPermissions.registryFeatureCatalog),
  });
  const extensions = useQuery({
    queryKey: ["admin", "commercial", "plan", plan.id, "extension-compatibility", "composition"],
    queryFn: () => adminApi.inspectPlanCompatibility(plan.id, { page: 0, size: 100 }),
    enabled: canInspectExtensions,
  });
  const refresh = () => {
    void invalidateAdminCommercial(queryClient, adminCommercialKeys.plans.all());
  };
  const remove = useMutation({
    mutationFn: (id: string) => adminApi.removePlanFeature(plan.id, id, plan.version),
    onSuccess: () => {
      refresh();
      toast.success("Fonctionnalité retirée");
    },
  });
  if (features.isLoading) return <LoadingState />;
  if (features.isError) return <ErrorState retry={() => void features.refetch()} />;
  const catalogFeatures = (canReadCatalog ? (catalog.data ?? []) : []).flatMap((module) => module.features);
  // The display join uses the whole catalogue: an already-assigned feature must keep its name
  // even if it is no longer offered for new assignment.
  const byCode = new Map(catalogFeatures.map((feature) => [feature.code, feature]));
  const available = catalogFeatures.filter((feature) => feature.planAssignable);
  const addable = available.filter(
    (feature) => !features.data?.some((assigned) => assigned.featureCode === feature.code),
  );
  const frozen = plan.status !== "DRAFT";
  const allPlanFeatures = features.data ?? [];
  const rows: PlanFeatureCommercialRow[] = allPlanFeatures.map((feature) => {
    const featureAddOns = (extensions.data?.content ?? []).filter(
      (item) => item.productType === "ADD_ON" && item.featureCodes.includes(feature.featureCode),
    );
    const capacityPacks = (extensions.data?.content ?? []).filter(
      (item) => item.productType === "QUOTA_PACKAGE" && item.quotaFeatureCode === feature.featureCode,
    );
    return {
      feature,
      definition: byCode.get(feature.featureCode),
      addOns: featureAddOns,
      capacityPacks,
    };
  });
  const columns = planFeatureColumn.columns([
    planFeatureColumn.display({
      id: "details",
      header: "",
      meta: { headerClassName: "w-12", cellClassName: "w-12 ps-3 pe-0" },
      cell: ({ row }) => (
        <DataTableExpander
          collapseLabel={`Masquer les offres associées à ${row.original.definition?.displayName ?? row.original.feature.featureCode}`}
          expandLabel={`Afficher les offres associées à ${row.original.definition?.displayName ?? row.original.feature.featureCode}`}
          row={row}
        />
      ),
    }),
    planFeatureColumn.display({
      id: "feature",
      header: "Fonctionnalité",
      meta: { cellClassName: "max-w-md whitespace-normal" },
      cell: ({ row }) => (
        <>
          <span className="block font-medium">
            {row.original.definition?.displayName ?? row.original.feature.featureCode}
          </span>
          {row.original.definition?.description ? (
            <span className="mt-0.5 block text-xs leading-4 text-muted-foreground">
              {row.original.definition.description}
            </span>
          ) : null}
        </>
      ),
    }),
    planFeatureColumn.display({
      id: "availability",
      header: "Disponibilité",
      meta: { cellClassName: "max-w-64 whitespace-normal" },
      cell: ({ row }) => (
        <PlanFeatureAvailability
          addOnState={
            !canInspectExtensions ? "HIDDEN" : extensions.isPending ? "LOADING" : extensions.isError ? "ERROR" : "READY"
          }
          expanded={row.getIsExpanded()}
          onToggleDetails={row.getToggleExpandedHandler()}
          row={row.original}
        />
      ),
    }),
    planFeatureColumn.display({
      id: "includedLimits",
      header: "Limites incluses",
      meta: { cellClassName: "whitespace-normal" },
      cell: ({ row }) =>
        row.original.feature.mode === "INCLUDED" ? (
          quotaLinesOf(row.original.feature, row.original.definition).map((line) => (
            <span
              className={
                line.startsWith("Illimité") || line.startsWith("Aucune")
                  ? "block text-sm text-muted-foreground"
                  : "block text-sm tabular-nums"
              }
              key={line}
            >
              {line}
            </span>
          ))
        ) : (
          <span className="text-sm text-muted-foreground">—</span>
        ),
    }),
    planFeatureColumn.display({
      id: "capacityPacks",
      header: "Packs de capacité",
      meta: { cellClassName: "min-w-56 whitespace-normal" },
      cell: ({ row }) => (
        <PlanCapacityPackSummary
          loadState={
            !canInspectExtensions ? "HIDDEN" : extensions.isPending ? "LOADING" : extensions.isError ? "ERROR" : "READY"
          }
          row={row.original}
        />
      ),
    }),
    planFeatureColumn.display({
      id: "actions",
      header: "Actions",
      meta: { headerClassName: "w-24", cellClassName: "w-24" },
      cell: ({ row }) => (
        <PlanFeatureActions
          available={available}
          catalogBlockedBy={
            !canReadCatalog
              ? "Votre rôle ne permet pas de consulter le catalogue des fonctionnalités"
              : catalog.isPending
                ? "Catalogue des fonctionnalités en cours de chargement…"
                : catalog.isError
                  ? "Le catalogue des fonctionnalités n’a pas pu être chargé"
                  : null
          }
          feature={row.original.feature}
          frozen={frozen}
          onRemove={() => remove.mutate(row.original.feature.id)}
          plan={plan}
          removing={remove.isPending}
        />
      ),
    }),
  ]);
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <div className="flex items-center justify-between gap-4 border-b p-4">
        <div>
          <h2 className="text-sm font-semibold">Contenu du forfait</h2>
          <p className="mt-1 text-xs text-muted-foreground">
            {frozen
              ? "La composition d’un forfait publié est figée — créez une révision pour la faire évoluer."
              : `${features.data?.length ?? 0} fonctionnalités configurées`}
          </p>
          {extensions.data && extensions.data.totalElements > extensions.data.content.length ? (
            <p className="mt-1 text-xs text-muted-foreground">
              Les offres associées sont limitées aux {extensions.data.content.length} premiers résultats ici ; consultez
              Compatibilité pour la liste complète.
            </p>
          ) : null}
          {!frozen && canAssignFeature ? (
            <ChoiceLoadState
              error={catalog.isError}
              loading={canReadCatalog && catalog.isPending}
              onRetry={() => void catalog.refetch()}
              unavailable={
                canReadCatalog ? null : "Votre rôle ne permet pas de consulter le catalogue des fonctionnalités."
              }
            />
          ) : null}
        </div>
        {frozen || !canAssignFeature ? null : canReadCatalog && catalog.isSuccess ? (
          <PlanFeatureDialog available={addable} plan={plan} />
        ) : (
          <Button disabled size="sm">
            <PlusIcon />
            Ajouter
          </Button>
        )}
      </div>
      {features.data?.length ? (
        <DataTable
          columns={columns}
          data={rows}
          getRowCanExpand={(row) => row.addOns.length > 0 || row.capacityPacks.length > 0}
          getRowId={(row) => row.feature.id}
          renderExpandedRow={(row) => <PlanFeatureOffers row={row} />}
          rowHeightPx={76}
        />
      ) : (
        <EmptyState description="Ajoutez les capacités comprises dans ce forfait." title="Aucune fonctionnalité" />
      )}
    </section>
  );
}

type ExtensionLoadState = "READY" | "LOADING" | "ERROR" | "HIDDEN";

function PlanFeatureAvailability({
  row,
  addOnState,
  expanded,
  onToggleDetails,
}: {
  row: PlanFeatureCommercialRow;
  addOnState: ExtensionLoadState;
  expanded: boolean;
  onToggleDetails: () => void;
}) {
  const modeLabel = featureModePresentation[row.feature.mode]?.label ?? row.feature.mode;
  if (row.feature.mode === "INCLUDED") {
    return (
      <StatusBadge dot={false} tone="success">
        {modeLabel}
      </StatusBadge>
    );
  }
  if (row.feature.mode !== "OPTIONAL_ADD_ON") {
    return (
      <span
        className={row.feature.mode === "BLOCKED_FOR_PLAN" ? "text-sm text-muted-foreground" : "text-sm font-medium"}
      >
        {modeLabel}
      </span>
    );
  }

  if (addOnState === "LOADING") return <span className="text-sm text-muted-foreground">Vérification…</span>;
  if (addOnState === "ERROR") return <span className="text-sm text-destructive">Vérification impossible</span>;
  if (addOnState === "HIDDEN") return <span className="text-sm text-muted-foreground">{modeLabel}</span>;

  const names = row.addOns.map((addOn) => addOn.name);
  const label = addOnAvailabilityLabel(names);
  if (!names.length) return <span className="text-sm font-medium text-warning">{label}</span>;

  return (
    <span className="inline-flex max-w-full items-center gap-1.5 text-sm">
      <span className="text-muted-foreground">Via</span>
      <ReferenceTagButton
        aria-expanded={expanded}
        aria-label={`${expanded ? "Masquer" : "Afficher"} les add-ons associés à ${row.definition?.displayName ?? "la fonctionnalité indisponible"}`}
        onClick={onToggleDetails}
        title={names.join(", ")}
      >
        <span className="truncate">{label.replace(/^Via /, "")}</span>
      </ReferenceTagButton>
    </span>
  );
}

function PlanCapacityPackSummary({ row, loadState }: { row: PlanFeatureCommercialRow; loadState: ExtensionLoadState }) {
  if (row.feature.mode === "BLOCKED_FOR_PLAN") return <span className="text-sm text-muted-foreground">—</span>;
  if (loadState === "LOADING") return <span className="text-sm text-muted-foreground">Chargement…</span>;
  if (loadState === "ERROR") return <span className="text-sm text-destructive">Chargement impossible</span>;
  if (loadState === "HIDDEN") return <span className="text-sm text-muted-foreground">Non accessible</span>;
  if (!row.capacityPacks.length) return <span className="text-sm text-muted-foreground">—</span>;

  return (
    <ul className="space-y-1" title={row.capacityPacks.map((pkg) => pkg.name).join(", ")}>
      {row.capacityPacks.slice(0, 2).map((pkg) => (
        <li className="truncate text-sm" key={pkg.productId}>
          {pkg.name}
        </li>
      ))}
      {row.capacityPacks.length > 2 ? (
        <li className="text-xs text-muted-foreground">+{row.capacityPacks.length - 2} autres</li>
      ) : null}
    </ul>
  );
}

function PlanFeatureOffers({ row }: { row: PlanFeatureCommercialRow }) {
  const unitOf = (resource: string) =>
    row.definition?.quotaSchema.find((slot) => slot.resource === resource)?.unit ?? "capacité";
  return (
    <div className="grid px-14 py-5 md:grid-cols-2">
      <section className="min-w-0 md:pe-8">
        <div className="flex items-center justify-between gap-4 border-b pb-2">
          <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Add-ons</h3>
          <span className="text-xs tabular-nums text-muted-foreground">{row.addOns.length}</span>
        </div>
        {row.addOns.length ? (
          <ul className="divide-y">
            {row.addOns.map((addOn) => (
              <li
                className="grid gap-x-4 gap-y-2 py-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center"
                key={addOn.productId}
              >
                <div className="min-w-0">
                  <Link
                    className="font-medium underline-offset-4 hover:underline"
                    to={`/admin/add-ons/${addOn.productId}`}
                  >
                    {addOn.name}
                  </Link>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    {addOn.applicablePriceCount > 0
                      ? `${addOn.applicablePriceCount} tarif${addOn.applicablePriceCount > 1 ? "s" : ""} applicable${addOn.applicablePriceCount > 1 ? "s" : ""}`
                      : "Aucun tarif applicable"}
                    {addOn.issues.length
                      ? ` · ${addOn.issues.length} point${addOn.issues.length > 1 ? "s" : ""} à vérifier`
                      : ""}
                  </p>
                </div>
                <StatusText
                  className="sm:justify-self-end"
                  tone={addOn.operatorSelectable ? "success" : addOn.issues.length ? "warning" : "neutral"}
                >
                  {addOn.operatorSelectable ? "Sélectionnable" : "Non sélectionnable"}
                </StatusText>
              </li>
            ))}
          </ul>
        ) : (
          <p className="py-4 text-sm text-muted-foreground">Aucun add-on associé</p>
        )}
      </section>
      <section className="min-w-0 border-t pt-5 md:border-t-0 md:border-s md:ps-8 md:pt-0">
        <div className="flex items-center justify-between gap-4 border-b pb-2">
          <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Packs de capacité</h3>
          <span className="text-xs tabular-nums text-muted-foreground">{row.capacityPacks.length}</span>
        </div>
        {row.capacityPacks.length ? (
          <ul className="divide-y">
            {row.capacityPacks.map((pkg) => (
              <li
                className="grid gap-x-4 gap-y-2 py-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center"
                key={pkg.productId}
              >
                <div className="min-w-0">
                  <Link
                    className="font-medium underline-offset-4 hover:underline"
                    to={`/admin/quota-packages/${pkg.productId}`}
                  >
                    {pkg.name}
                  </Link>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    +{pkg.capacityPerUnit ?? "—"} {unitOf(pkg.quotaResource ?? "capacité")} ·{" "}
                    {pkg.applicablePriceCount > 0
                      ? `${pkg.applicablePriceCount} tarif${pkg.applicablePriceCount > 1 ? "s" : ""} applicable${pkg.applicablePriceCount > 1 ? "s" : ""}`
                      : "Aucun tarif applicable"}
                  </p>
                </div>
                <StatusText
                  className="sm:justify-self-end"
                  tone={pkg.operatorSelectable ? "success" : pkg.issues.length ? "warning" : "neutral"}
                >
                  {pkg.operatorSelectable ? "Sélectionnable" : "Non sélectionnable"}
                </StatusText>
              </li>
            ))}
          </ul>
        ) : (
          <p className="py-4 text-sm text-muted-foreground">Aucun pack de capacité</p>
        )}
      </section>
    </div>
  );
}

function PlanFeatureActions({
  feature,
  plan,
  available,
  catalogBlockedBy,
  frozen,
  removing,
  onRemove,
}: {
  feature: PlanFeature;
  plan: Plan;
  available: RegistryFeature[];
  catalogBlockedBy: string | null;
  frozen: boolean;
  removing: boolean;
  onRemove: () => void;
}) {
  const session = useAdminSession();
  const [editOpen, setEditOpen] = useState(false);
  const editBlockedBy = frozen
    ? "La composition ne se modifie qu’à l’état brouillon — créez une révision"
    : (catalogBlockedBy ??
      (!session.can(adminPermissions.plansUpdateFeature)
        ? "Vous n’êtes pas autorisé à configurer les fonctionnalités"
        : null));
  const removeBlockedBy = frozen
    ? "La composition ne se modifie qu’à l’état brouillon — créez une révision"
    : !session.can(adminPermissions.plansRemoveFeature)
      ? "Vous n’êtes pas autorisé à retirer une fonctionnalité"
      : removing
        ? "Retrait en cours…"
        : null;
  return (
    <>
      <span className="flex items-center gap-0.5">
        <RowAction
          disabled={editBlockedBy !== null}
          disabledLabel={editBlockedBy ?? undefined}
          icon={<PencilSimpleIcon />}
          label="Configurer la fonctionnalité"
          onClick={() => setEditOpen(true)}
        />
        <RowAction
          disabled={removeBlockedBy !== null}
          disabledLabel={removeBlockedBy ?? undefined}
          icon={<TrashIcon />}
          label="Retirer du forfait"
          onClick={onRemove}
          tone="danger"
        />
      </span>
      {editOpen ? (
        <PlanFeatureDialog
          available={available}
          item={feature}
          onOpenChange={(next) => !next && setEditOpen(false)}
          open
          plan={plan}
          withTrigger={false}
        />
      ) : null}
    </>
  );
}

function PlanSubscribers({ plan }: { plan: Plan }) {
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const state = readPlanSubscriberListState(params);
  const deferred = useDebouncedValue(state.search);
  const setState = (next: typeof state) => setParams(writePlanSubscriberListState(params, next), { replace: true });
  const canOpenSubscriber = adminSubscriptionDetailSurfacePermissions.some(session.can);
  const subscribers = useQuery({
    queryKey: adminCommercialKeys.plans.subscribers(plan.id, {
      search: deferred,
      status: state.status,
      page: state.page,
    }),
    queryFn: () =>
      adminApi.planSubscribers(plan.id, {
        search: deferred || undefined,
        status: state.status === "all" ? undefined : state.status,
        page: state.page,
        size: 20,
      }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.plansListSubscribers),
  });
  useEffect(() => {
    if (!subscribers.data) return;
    const boundedPage = subscribers.data.totalPages === 0 ? 0 : Math.min(state.page, subscribers.data.totalPages - 1);
    if (boundedPage !== state.page) {
      setParams(writePlanSubscriberListState(params, { ...state, page: boundedPage }), { replace: true });
    }
  }, [params, setParams, state, subscribers.data]);
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <div className="flex flex-col gap-3 border-b p-4 sm:flex-row">
        <div className="relative flex-1 sm:max-w-sm">
          <MagnifyingGlassIcon
            aria-hidden="true"
            className="absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
          />
          <Input
            aria-label="Rechercher des abonnés"
            className="ps-9"
            onChange={(event) => {
              setState({ ...state, search: event.target.value, page: 0 });
            }}
            placeholder="Nom du compte…"
            value={state.search}
          />
        </div>
        <Select
          onValueChange={(value) => {
            setState({ ...state, status: value as typeof state.status, page: 0 });
          }}
          value={state.status}
        >
          <SelectTrigger aria-label="Statut de l’abonnement" className="w-full sm:w-48">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">Tous les statuts</SelectItem>
            {Object.entries(subscriptionStatusPresentation).map(([value, presentation]) => (
              <SelectItem key={value} value={value}>
                {presentation.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      {subscribers.isLoading ? (
        <div className="p-5">
          <LoadingState />
        </div>
      ) : subscribers.isError ? (
        <ErrorState retry={() => void subscribers.refetch()} />
      ) : !subscribers.data?.content.length ? (
        <EmptyState title="Aucun abonné" />
      ) : (
        <>
          <div className="divide-y md:hidden">
            {subscribers.data.content.map((subscriber) => {
              const presentation = subscriptionStatusPresentation[subscriber.status];
              return (
                <article className="space-y-3 p-4" key={subscriber.subscriptionId}>
                  <div className="flex items-start justify-between gap-3">
                    <p className="min-w-0 truncate font-medium">{subscriber.accountName}</p>
                    <StatusBadge tone={presentation.tone}>{presentation.label}</StatusBadge>
                  </div>
                  <dl className="grid grid-cols-2 gap-3 text-xs">
                    <div>
                      <dt className="text-muted-foreground">Prix configuré</dt>
                      <dd className="mt-0.5 font-medium">
                        {money(subscriber.configuredRecurringPrice, subscriber.configuredRecurringPriceCurrencyCode)}
                      </dd>
                    </div>
                    <div>
                      <dt className="text-muted-foreground">Fin de période</dt>
                      <dd className="mt-0.5 font-medium">
                        {subscriber.currentPeriodEnd
                          ? new Intl.DateTimeFormat("fr-MA").format(new Date(subscriber.currentPeriodEnd))
                          : "—"}
                      </dd>
                    </div>
                  </dl>
                  <div className="flex justify-end">
                    <TableActionsCell label={`Actions pour ${subscriber.accountName}`}>
                      <RowAction
                        disabled={!canOpenSubscriber}
                        disabledLabel="Votre rôle ne permet pas d’ouvrir cet abonnement"
                        icon={<ArrowRightIcon className="rtl:rotate-180" />}
                        label="Ouvrir l’abonnement"
                        to={canOpenSubscriber ? `/admin/subscriptions/${subscriber.accountId}` : undefined}
                      />
                    </TableActionsCell>
                  </div>
                </article>
              );
            })}
          </div>
          <div className="hidden overflow-x-auto md:block">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Compte</TableHead>
                  <TableHead>Statut</TableHead>
                  <TableHead>Prix configuré</TableHead>
                  <TableHead>Fin de période</TableHead>
                  <TableHead>Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {subscribers.data.content.map((subscriber) => {
                  const presentation = subscriptionStatusPresentation[subscriber.status];
                  return (
                    <TableRow key={subscriber.subscriptionId}>
                      <TableCell>
                        <p className="font-medium">{subscriber.accountName}</p>
                      </TableCell>
                      <TableCell>
                        <StatusBadge tone={presentation.tone}>{presentation.label}</StatusBadge>
                      </TableCell>
                      <TableCell>
                        {money(subscriber.configuredRecurringPrice, subscriber.configuredRecurringPriceCurrencyCode)}
                      </TableCell>
                      <TableCell>
                        {subscriber.currentPeriodEnd
                          ? new Intl.DateTimeFormat("fr-MA").format(new Date(subscriber.currentPeriodEnd))
                          : "—"}
                      </TableCell>
                      <TableCell>
                        <TableActionsCell label={`Actions pour ${subscriber.accountName}`}>
                          <RowAction
                            disabled={!canOpenSubscriber}
                            disabledLabel="Votre rôle ne permet pas d’ouvrir cet abonnement"
                            icon={<ArrowRightIcon className="rtl:rotate-180" />}
                            label="Ouvrir l’abonnement"
                            to={canOpenSubscriber ? `/admin/subscriptions/${subscriber.accountId}` : undefined}
                          />
                        </TableActionsCell>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>
          <PaginationBar
            onPageChange={(page) => setState({ ...state, page })}
            page={subscribers.data.page}
            totalElements={subscribers.data.totalElements}
            totalPages={subscribers.data.totalPages}
          />
        </>
      )}
    </section>
  );
}

function planDeletionEvidenceIsCurrent(plan: Plan, preview: PlanDeletionPreview | undefined, now: number) {
  if (!preview) return false;
  const expiresAt = Date.parse(preview.expiresAt);
  return Boolean(
    preview.previewToken &&
      preview.planId === plan.id &&
      preview.expectedVersion === plan.version &&
      Number.isFinite(expiresAt) &&
      expiresAt > now,
  );
}

export function DeletePlanDialog({ plan }: { plan: Plan }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [confirmation, setConfirmation] = useState("");
  const [evidenceClock, setEvidenceClock] = useState(() => Date.now());
  const previewKey = adminCommercialKeys.plans.deletePreview(plan.id);
  const preview = useQuery({
    queryKey: previewKey,
    queryFn: () => adminApi.previewPlanDeletion(plan.id),
    enabled: commercialQueryEnabled(session.can, adminPermissions.plansPreviewDelete, open),
    staleTime: 0,
  });
  const evidenceCurrent =
    !preview.isFetching && !preview.isError && planDeletionEvidenceIsCurrent(plan, preview.data, evidenceClock);
  useEffect(() => {
    if (!open || !preview.data?.expiresAt) return;
    const expiresAt = Date.parse(preview.data.expiresAt);
    if (!Number.isFinite(expiresAt)) return;
    const timer = window.setTimeout(() => setEvidenceClock(Date.now()), Math.max(0, expiresAt - Date.now() + 1));
    return () => window.clearTimeout(timer);
  }, [open, preview.data?.expiresAt]);
  const refreshPreview = () => {
    setEvidenceClock(Date.now());
    void preview.refetch();
  };
  const remove = useMutation({
    mutationFn: () => {
      if (!preview.data || !planDeletionEvidenceIsCurrent(plan, preview.data, Date.now())) {
        throw new Error("La vérification de suppression n’est plus actuelle.");
      }
      return adminApi.deletePlan(plan.id, {
        confirmationName: confirmation,
        expectedVersion: preview.data.expectedVersion,
        previewToken: preview.data.previewToken,
      });
    },
    onSuccess: () => {
      void invalidateAdminCommercial(queryClient, adminCommercialKeys.plans.all());
      toast.success("Forfait supprimé");
      navigate("/admin/plans");
    },
    onError: (error) => {
      const rejectedEvidence =
        error.message === "La vérification de suppression n’est plus actuelle." ||
        (error instanceof ApiError &&
          ["STALE_IMPACT_PREVIEW", "STALE_RESOURCE_VERSION", "STALE_ACTIVATION_PREVIEW"].includes(error.code));
      if (rejectedEvidence) {
        setEvidenceClock(Date.now());
        void preview.refetch();
        toast.warning("Le forfait a changé ou la vérification a expiré. La suppression est recalculée.");
        return;
      }
      toast.error(error.message);
    },
  });
  if (!session.can(adminPermissions.plansDelete)) return null;
  return (
    <Dialog
      onOpenChange={(next) => {
        setOpen(next);
        setConfirmation("");
        if (next) setEvidenceClock(Date.now());
        else queryClient.removeQueries({ queryKey: previewKey, exact: true });
      }}
      open={open}
    >
      <DialogTrigger asChild>
        <Button variant="destructive">
          <TrashIcon />
          Supprimer
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Supprimer {plan.name}</DialogTitle>
          <DialogDescription>Seul un brouillon inutilisé et sans dépendance peut être supprimé.</DialogDescription>
        </DialogHeader>
        {preview.isFetching ? (
          <LoadingState rows={3} />
        ) : preview.isError ? (
          <ErrorState retry={refreshPreview} title="Vérification de suppression indisponible" />
        ) : preview.data && !evidenceCurrent ? (
          <div className="space-y-3 rounded-lg border border-warning/30 bg-warning/5 p-4 text-sm" role="status">
            <p>La vérification a expiré ou le forfait affiché a changé.</p>
            <Button onClick={refreshPreview} variant="outline">
              Recalculer la suppression
            </Button>
          </div>
        ) : preview.data ? (
          <div className="space-y-4">
            {preview.data.blockers.length ? (
              <ul className="list-disc space-y-1 ps-5 text-sm text-destructive">
                {preview.data.blockers.map((blocker) => (
                  <li key={blocker}>{blocker}</li>
                ))}
              </ul>
            ) : null}
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div>
                <span className="text-muted-foreground">Historique</span>
                <strong className="ms-2">{preview.data.subscriptionHistoryCount}</strong>
              </div>
              <div>
                <span className="text-muted-foreground">Références</span>
                <strong className="ms-2">
                  {preview.data.changeOperationReferenceCount +
                    preview.data.addOnReferenceCount +
                    preview.data.quotaPackageReferenceCount +
                    preview.data.lineageReferenceCount}
                </strong>
              </div>
            </div>
            {preview.data.deletable ? (
              <div className="space-y-2">
                <Label htmlFor="confirm-plan">Saisissez {preview.data.planName}</Label>
                <Input
                  id="confirm-plan"
                  onChange={(event) => setConfirmation(event.target.value)}
                  value={confirmation}
                />
              </div>
            ) : null}
            <div className="flex justify-end">
              <Button
                disabled={
                  !evidenceCurrent ||
                  !preview.data.deletable ||
                  confirmation !== preview.data.planName ||
                  remove.isPending
                }
                onClick={() => remove.mutate()}
                variant="destructive"
              >
                Confirmer la suppression
              </Button>
            </div>
          </div>
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function PlanDetailPage({ id, tab = "overview" }: { id: string; tab?: string }) {
  const session = useAdminSession();
  const plan = useQuery({
    queryKey: adminCommercialKeys.plans.detail(id),
    queryFn: () => adminApi.plan(id),
    enabled: commercialQueryEnabled(session.can, adminPermissions.plansReadDetail),
  });
  const operations = useQuery({
    queryKey: ["admin", "commercial", "plan", id, "operations"],
    queryFn: () => adminApi.planOperations(id),
    enabled: commercialQueryEnabled(session.can, adminPermissions.plansReadOperations),
  });
  if (plan.isLoading) return <LoadingState />;
  if (plan.isError || !plan.data) return <ErrorState retry={() => void plan.refetch()} />;
  const data = plan.data;
  const hasAction = (action: CommercialProductAction) => operations.data?.availableActions.includes(action) ?? false;
  return (
    <div className="space-y-5">
      <div className="space-y-3">
        <Button asChild className="-ms-2 text-muted-foreground" size="sm" variant="ghost">
          <Link to="/admin/plans">
            <ArrowLeftIcon className="rtl:rotate-180" />
            Forfaits
          </Link>
        </Button>
        <PageHeader
          actions={
            <>
              {hasAction("EDIT_DRAFT") ? (
                <PlanFormDialog
                  mode="edit"
                  source={data}
                  trigger={
                    <Button size="sm" variant="ghost">
                      <PencilSimpleIcon />
                      Modifier
                    </Button>
                  }
                />
              ) : null}
              {session.can(adminPermissions.plansDuplicate) ? (
                <Button asChild size="sm" variant="ghost">
                  <Link to={`/admin/plans/new?from=${data.id}`}>
                    <CopyIcon />
                    Dupliquer
                  </Link>
                </Button>
              ) : null}
              {hasAction("REVISE") ? (
                <PlanFormDialog
                  mode="revise"
                  source={data}
                  trigger={
                    <Button size="sm">
                      <GitBranchIcon />
                      Réviser
                    </Button>
                  }
                />
              ) : null}
            </>
          }
          description={
            <span className="flex items-center gap-2 text-xs">
              <span>Révision {data.revisionNumber}</span>
            </span>
          }
          title={
            <span className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
              <span>{data.name}</span>
              <StatusText className="text-sm tracking-normal" tone={planTone[data.status]}>
                {statusText[data.status]}
              </StatusText>
            </span>
          }
        />
      </div>
      <SectionTabs
        ariaLabel="Sections du forfait"
        tabs={[
          { label: "Synthèse", to: `/admin/plans/${id}`, end: true },
          ...(session.can(adminPermissions.plansListFeatures)
            ? [{ label: "Fonctionnalités", to: `/admin/plans/${id}/features`, count: data.featureCount }]
            : []),
          ...(session.can(adminPermissions.plansListFeatures)
            ? [{ label: "Schéma", to: `/admin/plans/${id}/schema` }]
            : []),
          ...(session.can(adminPermissions.plansListSubscribers)
            ? [{ label: "Abonnés", to: `/admin/plans/${id}/subscribers`, count: data.currentSubscriberCount }]
            : []),
          ...(session.can(adminPermissions.priceBooksList)
            ? [{ label: "Tarifs", to: `/admin/plans/${id}/prices` }]
            : []),
          ...(session.can(adminPermissions.commercialInspectCompatibility)
            ? [{ label: "Compatibilité", to: `/admin/plans/${id}/compatibility` }]
            : []),
          ...(session.can(adminPermissions.commercialPreviewPlanPolicy)
            ? [{ label: "Disponibilité", to: `/admin/plans/${id}/availability` }]
            : []),
          ...(session.can(adminPermissions.commercialReadHistory)
            ? [{ label: "Historique", to: `/admin/plans/${id}/history` }]
            : []),
          ...(session.can(adminPermissions.plansTransition)
            ? [{ label: "Cycle de vie", to: `/admin/plans/${id}/lifecycle` }]
            : []),
        ]}
      />
      {operations.isError ? (
        <p className="text-sm text-warning">
          Les actions autorisées n’ont pas pu être vérifiées.{" "}
          <button className="underline underline-offset-2" onClick={() => void operations.refetch()} type="button">
            Réessayer
          </button>
        </p>
      ) : null}
      {tab === "features" && session.can(adminPermissions.plansListFeatures) ? (
        <PlanFeatures plan={data} />
      ) : tab === "schema" && session.can(adminPermissions.plansListFeatures) ? (
        <PlanSchema plan={data} />
      ) : tab === "subscribers" && session.can(adminPermissions.plansListSubscribers) ? (
        <PlanSubscribers plan={data} />
      ) : tab === "prices" && session.can(adminPermissions.priceBooksList) ? (
        <ProductPricePanel ownerId={data.id} ownerType="PLAN" />
      ) : tab === "compatibility" && session.can(adminPermissions.commercialInspectCompatibility) ? (
        <PlanCompatibilityPanel planId={data.id} />
      ) : tab === "availability" && session.can(adminPermissions.commercialPreviewPlanPolicy) ? (
        <CommercialAvailabilityPanel kind="plan" product={data} />
      ) : tab === "history" && session.can(adminPermissions.commercialReadHistory) ? (
        <CommercialAvailabilityHistory productId={data.id} />
      ) : tab === "lifecycle" && session.can(adminPermissions.plansTransition) ? (
        <div className="space-y-6">
          {session.can(adminPermissions.plansTransition) ? (
            <section className="rounded-xl border bg-card p-5">
              <h2 className="text-sm font-semibold">Changer le statut</h2>
              <div className="mt-4 flex flex-wrap gap-2">
                {hasAction("ACTIVATE") && session.can(adminPermissions.plansPreviewActivation) ? (
                  <CommercialLifecycleDialog
                    action="ACTIVATE"
                    kind="plan"
                    onChanged={() => void plan.refetch()}
                    product={data}
                    trigger={<Button>Activer</Button>}
                  />
                ) : hasAction("DEACTIVATE") ? (
                  <CommercialLifecycleDialog
                    action="DEACTIVATE"
                    kind="plan"
                    onChanged={() => void plan.refetch()}
                    product={data}
                    trigger={<Button variant="outline">Suspendre</Button>}
                  />
                ) : null}
                {hasAction("ARCHIVE") ? (
                  <CommercialLifecycleDialog
                    action="ARCHIVE"
                    kind="plan"
                    onChanged={() => void plan.refetch()}
                    product={data}
                    trigger={<Button variant="destructive">Archiver</Button>}
                  />
                ) : null}
              </div>
            </section>
          ) : null}
          {hasAction("DELETE_DRAFT") &&
          session.can(adminPermissions.plansDelete) &&
          session.can(adminPermissions.plansPreviewDelete) ? (
            <section className="rounded-xl border border-destructive/30 p-5">
              <h2 className="text-sm font-semibold text-destructive">Zone de suppression</h2>
              <p className="mt-1 text-xs text-muted-foreground">
                Une prévisualisation serveur vérifie toutes les références avant suppression.
              </p>
              <div className="mt-4">
                <DeletePlanDialog plan={data} />
              </div>
            </section>
          ) : null}
        </div>
      ) : tab ? (
        <PermissionState />
      ) : (
        <div className="grid gap-5 lg:grid-cols-[1.1fr_0.9fr]">
          <section className="rounded-xl border bg-card p-5">
            <h2 className="text-sm font-semibold">Configuration commerciale</h2>
            <dl className="mt-5 grid gap-5 sm:grid-cols-2">
              <div>
                <dt className="text-xs text-muted-foreground">Prix</dt>
                <dd className="mt-1 text-xl font-semibold">
                  {money(data.price, data.currencyCode)}{" "}
                  <span className="text-sm font-normal text-muted-foreground">/ {cycleText[data.billingCycle]}</span>
                </dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">Révision</dt>
                <dd className="mt-1 font-medium">
                  R{data.revisionNumber}
                  <span className="ms-2 text-xs font-normal text-muted-foreground">
                    {data.creationReason === "DUPLICATED"
                      ? "· créé par duplication"
                      : data.creationReason === "REVISED"
                        ? "· révision d’un forfait antérieur"
                        : "· création directe"}
                  </span>
                </dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">Fonctionnalités</dt>
                <dd className="mt-1 font-medium">{data.featureCount}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">Avec quota de base</dt>
                <dd className="mt-1 font-medium">
                  {data.quotaConfiguredFeatureCount}
                  <span className="ms-2 text-xs font-normal text-muted-foreground">· les autres sont illimitées</span>
                </dd>
              </div>
            </dl>
            <p className="mt-5 border-t pt-4 text-xs leading-5 text-muted-foreground">
              « Modifier » change ce forfait en place. « Réviser » crée R{data.revisionNumber + 1} dans la même lignée
              sans toucher aux abonnés actuels ; après activation, cette révision pourra être choisie explicitement pour
              de prochaines souscriptions. « Dupliquer » démarre une lignée indépendante.
            </p>
            {data.description ? (
              <p className="mt-5 border-t pt-4 text-sm text-muted-foreground">{data.description}</p>
            ) : null}
          </section>
          <section className="rounded-xl border bg-card p-5">
            <h2 className="text-sm font-semibold">Utilisation actuelle</h2>
            <dl className="mt-5 space-y-4">
              <div className="flex justify-between">
                <dt className="text-sm text-muted-foreground">Abonnés actifs</dt>
                <dd className="font-semibold tabular-nums">{data.activeSubscriberCount}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-sm text-muted-foreground">En essai</dt>
                <dd className="font-semibold tabular-nums">{data.trialingSubscriberCount}</dd>
              </div>
              <div className="flex justify-between border-t pt-4">
                <dt className="text-sm text-muted-foreground">Revenu configuré</dt>
                <dd className="font-semibold">
                  {money(data.configuredRecurringPriceTotal, data.configuredRecurringPriceCurrencyCode)}
                </dd>
              </div>
            </dl>
            {data.warnings.length ? (
              <ul className="mt-5 list-disc space-y-1 border-t pt-4 ps-5 text-xs text-warning">
                {data.warnings.map((warning) => (
                  <li key={warning}>{warning}</li>
                ))}
              </ul>
            ) : null}
          </section>
        </div>
      )}
    </div>
  );
}

export function AdminPlansPage() {
  const { planId, tab } = useParams();
  if (!planId) return <ErrorState />;
  return <PlanDetailPage id={planId} tab={tab} />;
}
