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
import { type FormEvent, useDeferredValue, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type {
  AddOn,
  BillingCycle,
  Plan,
  PlanFeature,
  PlanStatus,
  QuotaLimit,
  QuotaPackage,
  RegistryFeature,
} from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable, DataTableExpander } from "@/components/patterns/data-table";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { QuotaEditor } from "@/components/patterns/quota-editor";
import { ReferenceTagButton } from "@/components/patterns/reference-tag";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { RowAction } from "@/components/patterns/row-action";
import { SectionTabs } from "@/components/patterns/section-tabs";
import { StatusBadge } from "@/components/patterns/status-badge";
import { StatusText } from "@/components/patterns/status-text";
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

type PlanFeatureCommercialRow = {
  feature: PlanFeature;
  definition: RegistryFeature | undefined;
  addOns: AddOn[];
  capacityPacks: QuotaPackage[];
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
        price: Number(price),
        currencyCode: currencyCode.toUpperCase(),
        billingCycle,
      };
      if (mode === "edit" && source) return adminApi.updatePlan(source.id, input);
      if (mode === "duplicate" && source) return adminApi.duplicatePlan(source.id, input);
      if (mode === "revise" && source) return adminApi.revisePlan(source.id, input);
      return adminApi.createPlan(input);
    },
    onSuccess: (plan) => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "plans"] });
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
              min="0"
              onChange={(event) => setPrice(event.target.value)}
              step="0.01"
              type="number"
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
            <Button disabled={save.isPending} type="submit">
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
  const [mode, setMode] = useState(item?.mode ?? "INCLUDED");
  const [quotas, setQuotas] = useState<QuotaLimit[]>(item?.quotaConfigs ?? []);
  const definition = available.find((feature) => feature.code === featureCode);
  const save = useMutation({
    mutationFn: () => {
      const input = { featureCode, mode, quotaConfigs: mode === "INCLUDED" ? quotas : [] };
      return item ? adminApi.updatePlanFeature(plan.id, item.id, input) : adminApi.assignPlanFeature(plan.id, input);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "plans", plan.id] });
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
            <Select onValueChange={setMode} value={mode}>
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
            <Button disabled={!featureCode || save.isPending} onClick={() => save.mutate()}>
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
  const canSeeAddOns = session.can(adminPermissions.addOnsList);
  const canSeeCapacityPacks = session.can(adminPermissions.quotaPackagesList);
  const features = useQuery({
    queryKey: ["admin", "plans", plan.id, "features"],
    queryFn: () => adminApi.planFeatures(plan.id),
  });
  const catalog = useQuery({
    queryKey: ["admin", "registry", "plan-features"],
    queryFn: adminApi.registryInventory,
    // Without registry access the rows fall back to raw codes instead of provoking 403s.
    enabled: session.can(adminPermissions.registryRead),
  });
  const addOns = useQuery({
    queryKey: ["admin", "add-ons"],
    queryFn: adminApi.addOns,
    enabled: canSeeAddOns,
  });
  const quotaPackages = useQuery({
    queryKey: ["admin", "quota-packages"],
    queryFn: adminApi.quotaPackages,
    enabled: canSeeCapacityPacks,
  });
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ["admin", "plans", plan.id] });
  };
  const remove = useMutation({
    mutationFn: (id: string) => adminApi.removePlanFeature(plan.id, id),
    onSuccess: () => {
      refresh();
      toast.success("Fonctionnalité retirée");
    },
  });
  if (features.isLoading) return <LoadingState />;
  if (features.isError) return <ErrorState retry={() => void features.refetch()} />;
  const catalogFeatures = (catalog.data ?? []).flatMap((module) => module.features);
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
    const featureAddOns = (addOns.data ?? []).filter(
      (addOn) =>
        addOn.features.some((item) => item.featureCode === feature.featureCode) &&
        !addOn.blockedPlanCodes.includes(plan.code) &&
        (addOn.allowedPlanCodes.length === 0 || addOn.allowedPlanCodes.includes(plan.code)) &&
        addOn.currencyCode === plan.currencyCode &&
        addOn.billingCycle === plan.billingCycle,
    );
    const addOnCodes = new Set(featureAddOns.map((addOn) => addOn.code));
    const capacityPacks = (quotaPackages.data ?? []).filter(
      (pkg) =>
        pkg.featureCode === feature.featureCode &&
        (pkg.allowedPlanCodes.includes(plan.code) || pkg.allowedAddOnCodes.some((code) => addOnCodes.has(code))),
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
          <span className="block font-medium" title={row.original.feature.featureCode}>
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
          addOnState={!canSeeAddOns ? "HIDDEN" : addOns.isPending ? "LOADING" : addOns.isError ? "ERROR" : "READY"}
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
            !canSeeCapacityPacks
              ? "HIDDEN"
              : quotaPackages.isPending
                ? "LOADING"
                : quotaPackages.isError
                  ? "ERROR"
                  : "READY"
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
        </div>
        {frozen ? null : <PlanFeatureDialog available={addable} plan={plan} />}
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
        aria-label={`${expanded ? "Masquer" : "Afficher"} les add-ons associés à ${row.definition?.displayName ?? row.feature.featureCode}`}
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
        <li className="truncate text-sm" key={pkg.id}>
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
    row.definition?.quotaSchema.find((slot) => slot.resource === resource)?.unit ?? resource;
  return (
    <div className="grid px-14 py-5 md:grid-cols-2">
      <section className="min-w-0 md:pe-8">
        <div className="flex items-center justify-between gap-4 border-b pb-2">
          <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Add-ons</h3>
          <span className="text-xs tabular-nums text-muted-foreground">{row.addOns.length}</span>
        </div>
        {row.addOns.length ? (
          <ul className="divide-y">
            {row.addOns.map((addOn) => {
              const addOnFeature = addOn.features.find((feature) => feature.featureCode === row.feature.featureCode);
              return (
                <li
                  className="grid gap-x-4 gap-y-2 py-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center"
                  key={addOn.id}
                >
                  <div className="min-w-0">
                    <Link className="font-medium underline-offset-4 hover:underline" to={`/admin/add-ons/${addOn.id}`}>
                      {addOn.name}
                    </Link>
                    <p className="mt-0.5 text-xs text-muted-foreground">
                      {money(addOn.price, addOn.currencyCode)} / {cycleText[addOn.billingCycle]}
                      {addOnFeature?.quotaConfigs.length
                        ? ` · ${addOnFeature.quotaConfigs
                            .map((quota) =>
                              quota.mode === "UNLIMITED" || quota.limit === null
                                ? `Illimité — ${unitOf(quota.resource)}`
                                : `${quota.limit} ${unitOf(quota.resource)}`,
                            )
                            .join(" · ")}`
                        : ""}
                    </p>
                  </div>
                  <StatusText className="sm:justify-self-end" tone={planTone[addOn.status]}>
                    {statusText[addOn.status]}
                  </StatusText>
                </li>
              );
            })}
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
              <li className="grid gap-x-4 gap-y-2 py-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center" key={pkg.id}>
                <div className="min-w-0">
                  <Link
                    className="font-medium underline-offset-4 hover:underline"
                    to={`/admin/quota-packages/${pkg.id}`}
                  >
                    {pkg.name}
                  </Link>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    +{pkg.capacityPerUnit} {unitOf(pkg.resource)} · {money(pkg.price, pkg.currencyCode)} /{" "}
                    {cycleText[pkg.billingCycle]}
                  </p>
                </div>
                <StatusText className="sm:justify-self-end" tone={planTone[pkg.status]}>
                  {statusText[pkg.status]}
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
  frozen,
  removing,
  onRemove,
}: {
  feature: PlanFeature;
  plan: Plan;
  available: RegistryFeature[];
  frozen: boolean;
  removing: boolean;
  onRemove: () => void;
}) {
  const session = useAdminSession();
  const [editOpen, setEditOpen] = useState(false);
  const editBlockedBy = frozen
    ? "La composition ne se modifie qu’à l’état brouillon — créez une révision"
    : !session.can(adminPermissions.plansUpdateFeature)
      ? "Vous n’êtes pas autorisé à configurer les fonctionnalités"
      : null;
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
  const [search, setSearch] = useState("");
  const deferred = useDeferredValue(search);
  const [status, setStatus] = useState("all");
  const [page, setPage] = useState(0);
  const subscribers = useQuery({
    queryKey: ["admin", "plans", plan.id, "subscribers", deferred, status, page],
    queryFn: () =>
      adminApi.planSubscribers(plan.id, {
        search: deferred || undefined,
        status: status === "all" ? undefined : status,
        page,
        size: 20,
      }),
  });
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <div className="flex flex-col gap-3 border-b p-4 sm:flex-row">
        <div className="relative flex-1 sm:max-w-sm">
          <MagnifyingGlassIcon className="absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="ps-9"
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(0);
            }}
            placeholder="Nom du compte…"
            value={search}
          />
        </div>
        <Select
          onValueChange={(value) => {
            setStatus(value);
            setPage(0);
          }}
          value={status}
        >
          <SelectTrigger aria-label="Statut de l’abonnement" className="w-full sm:w-48">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">Tous les statuts</SelectItem>
            {["ACTIVE", "TRIALING", "PAST_DUE", "SUSPENDED", "CANCELLED", "EXPIRED"].map((value) => (
              <SelectItem key={value} value={value}>
                {value}
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
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Compte</TableHead>
                <TableHead>Statut</TableHead>
                <TableHead>Prix configuré</TableHead>
                <TableHead>Fin de période</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {subscribers.data.content.map((subscriber) => (
                <TableRow key={subscriber.subscriptionId}>
                  <TableCell>
                    <p className="font-medium">{subscriber.accountName}</p>
                    <code className="text-xs text-muted-foreground">{subscriber.accountId}</code>
                  </TableCell>
                  <TableCell>
                    <StatusBadge
                      tone={
                        subscriber.status === "ACTIVE"
                          ? "success"
                          : subscriber.status === "PAST_DUE" || subscriber.status === "SUSPENDED"
                            ? "danger"
                            : "warning"
                      }
                    >
                      {subscriber.status}
                    </StatusBadge>
                  </TableCell>
                  <TableCell>
                    {money(subscriber.configuredRecurringPrice, subscriber.configuredRecurringPriceCurrencyCode)}
                  </TableCell>
                  <TableCell>
                    {subscriber.currentPeriodEnd
                      ? new Intl.DateTimeFormat("fr-MA").format(new Date(subscriber.currentPeriodEnd))
                      : "—"}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <PaginationBar
            onPageChange={setPage}
            page={subscribers.data.page}
            totalElements={subscribers.data.totalElements}
            totalPages={subscribers.data.totalPages}
          />
        </>
      )}
    </section>
  );
}

function DeletePlanDialog({ plan }: { plan: Plan }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [confirmation, setConfirmation] = useState("");
  const preview = useQuery({
    queryKey: ["admin", "plans", plan.id, "delete-preview"],
    queryFn: () => adminApi.previewPlanDeletion(plan.id),
    enabled: open,
  });
  const remove = useMutation({
    mutationFn: () =>
      adminApi.deletePlan(plan.id, {
        confirmationName: confirmation,
        expectedVersion: preview.data?.expectedVersion ?? 0,
        previewToken: preview.data?.previewToken ?? "",
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "plans"] });
      toast.success("Forfait supprimé");
      navigate("/admin/plans");
    },
  });
  if (!session.can(adminPermissions.plansDelete)) return null;
  return (
    <Dialog onOpenChange={setOpen} open={open}>
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
        {preview.isLoading ? (
          <LoadingState rows={3} />
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
                disabled={!preview.data.deletable || confirmation !== preview.data.planName || remove.isPending}
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
  const queryClient = useQueryClient();
  const plan = useQuery({ queryKey: ["admin", "plans", id], queryFn: () => adminApi.plan(id) });
  const transition = useMutation({
    mutationFn: (status: PlanStatus) => adminApi.transitionPlan(id, status),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "plans"] });
      toast.success("Cycle de vie mis à jour");
    },
  });
  if (plan.isLoading) return <LoadingState />;
  if (plan.isError || !plan.data) return <ErrorState retry={() => void plan.refetch()} />;
  const data = plan.data;
  const canRevise = data.status !== "DRAFT" && data.status !== "ARCHIVED";
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
              {session.can(adminPermissions.plansDuplicate) ? (
                <Button asChild size="sm" variant="ghost">
                  <Link to={`/admin/plans/new?from=${data.id}`}>
                    <CopyIcon />
                    Dupliquer
                  </Link>
                </Button>
              ) : null}
              {canRevise ? (
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
          { label: "Cycle de vie", to: `/admin/plans/${id}/lifecycle` },
        ]}
      />
      {tab === "features" && session.can(adminPermissions.plansListFeatures) ? (
        <PlanFeatures plan={data} />
      ) : tab === "schema" && session.can(adminPermissions.plansListFeatures) ? (
        <PlanSchema plan={data} />
      ) : tab === "subscribers" && session.can(adminPermissions.plansListSubscribers) ? (
        <PlanSubscribers plan={data} />
      ) : tab === "lifecycle" ? (
        <div className="space-y-6">
          {session.can(adminPermissions.plansTransition) ? (
            <section className="rounded-xl border bg-card p-5">
              <h2 className="text-sm font-semibold">Changer le statut</h2>
              <div className="mt-4 flex flex-wrap gap-2">
                {(["DRAFT", "ACTIVE", "INACTIVE", "ARCHIVED"] as PlanStatus[])
                  .filter((value) => value !== data.status)
                  .map((value) => (
                    <Button
                      disabled={transition.isPending}
                      key={value}
                      onClick={() => transition.mutate(value)}
                      variant={value === "ARCHIVED" ? "destructive" : "outline"}
                    >
                      {statusText[value]}
                    </Button>
                  ))}
              </div>
            </section>
          ) : null}
          {data.status === "DRAFT" &&
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
  const session = useAdminSession();
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("all");
  const plans = useQuery({ queryKey: ["admin", "plans"], queryFn: adminApi.plans });
  const filtered = useMemo(
    () =>
      (plans.data ?? []).filter(
        (plan) =>
          (status === "all" || plan.status === status) && plan.name.toLowerCase().includes(search.toLowerCase()),
      ),
    [plans.data, search, status],
  );
  if (planId) return <PlanDetailPage id={planId} tab={tab} />;
  return (
    <div className="space-y-7">
      <PageHeader
        actions={
          session.can(adminPermissions.plansCreate) ? (
            <Button asChild>
              <Link to="/admin/plans/new">
                <PlusIcon />
                Créer un forfait
              </Link>
            </Button>
          ) : undefined
        }
        title="Forfaits"
      />
      <section className="overflow-hidden rounded-xl border bg-card">
        <div className="flex flex-col gap-3 border-b p-4 sm:flex-row">
          <div className="relative flex-1 sm:max-w-sm">
            <MagnifyingGlassIcon className="absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="ps-9"
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Rechercher par nom…"
              value={search}
            />
          </div>
          <Select onValueChange={setStatus} value={status}>
            <SelectTrigger aria-label="Statut du forfait" className="w-full sm:w-44">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Tous les statuts</SelectItem>
              {Object.entries(statusText).map(([value, label]) => (
                <SelectItem key={value} value={value}>
                  {label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        {plans.isLoading ? (
          <div className="p-5">
            <LoadingState />
          </div>
        ) : plans.isError ? (
          <ErrorState retry={() => void plans.refetch()} />
        ) : !filtered.length ? (
          <EmptyState title="Aucun forfait" />
        ) : (
          <div className="grid gap-4 p-4 sm:grid-cols-2 xl:grid-cols-3">
            {filtered.map((plan) => (
              <PlanCard key={plan.id} plan={plan} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

/**
 * A catalogue entry, not a data row: a handful of plans is easier to compare as priced cards
 * than as a table. Actions follow the table contract anyway — always rendered, disabled with
 * the reason, navigation on the end arrow.
 */
function PlanCard({ plan }: { plan: Plan }) {
  const session = useAdminSession();
  const [dialog, setDialog] = useState<"duplicate" | "revise" | null>(null);
  const duplicateBlockedBy = session.can(adminPermissions.plansDuplicate)
    ? null
    : "Vous n’êtes pas autorisé à dupliquer un forfait";
  const reviseBlockedBy = !session.can(adminPermissions.plansRevise)
    ? "Vous n’êtes pas autorisé à créer une révision"
    : plan.status === "DRAFT"
      ? "Modifiez directement ce brouillon ; il ne peut pas être révisé"
      : plan.status === "ARCHIVED"
        ? "Un forfait archivé est terminal et ne peut pas être révisé"
        : null;
  const openBlockedBy = session.can(adminPermissions.plansReadDetail)
    ? null
    : "Vous n’êtes pas autorisé à consulter ce forfait";
  return (
    <article className="flex flex-col rounded-xl border bg-background/40 p-5 transition-colors hover:border-ring/40">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="truncate font-semibold">{plan.name}</h3>
        </div>
        <StatusBadge tone={planTone[plan.status]}>{statusText[plan.status]}</StatusBadge>
      </div>
      <p className="mt-3 line-clamp-2 min-h-8 text-xs leading-4 text-muted-foreground">
        {plan.description || "Aucune description."}
      </p>
      <p className="mt-4 text-2xl font-semibold tabular-nums">
        {money(plan.price, plan.currencyCode)}
        <span className="ms-1.5 text-sm font-normal text-muted-foreground">/ {cycleText[plan.billingCycle]}</span>
      </p>
      <div className="mt-5 flex items-center justify-between border-t pt-3">
        <span className="text-xs tabular-nums text-muted-foreground">Révision R{plan.revisionNumber}</span>
        <span className="flex items-center gap-0.5">
          <RowAction
            disabled={duplicateBlockedBy !== null}
            disabledLabel={duplicateBlockedBy ?? undefined}
            icon={<CopyIcon />}
            label="Dupliquer le forfait"
            to={duplicateBlockedBy === null ? `/admin/plans/new?from=${plan.id}` : undefined}
          />
          <RowAction
            disabled={reviseBlockedBy !== null}
            disabledLabel={reviseBlockedBy ?? undefined}
            icon={<GitBranchIcon />}
            label="Créer une révision"
            onClick={() => setDialog("revise")}
          />
          <RowAction
            disabled={openBlockedBy !== null}
            disabledLabel={openBlockedBy ?? undefined}
            icon={<ArrowRightIcon />}
            label="Ouvrir le forfait"
            to={openBlockedBy === null ? `/admin/plans/${plan.id}` : undefined}
          />
        </span>
      </div>
      {/* Mounted only while open, so each opening starts from the plan's current values. */}
      {dialog ? (
        <PlanFormDialog mode={dialog} onOpenChange={(next) => !next && setDialog(null)} open source={plan} />
      ) : null}
    </article>
  );
}
