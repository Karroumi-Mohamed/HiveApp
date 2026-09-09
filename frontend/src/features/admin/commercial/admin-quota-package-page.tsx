import { ArrowLeftIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type FormEvent, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { BillingCycle, CommercialProductAction, QuotaPackage } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { SectionTabs } from "@/components/patterns/section-tabs";
import { StatusBadge } from "@/components/patterns/status-badge";
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import {
  CommercialAvailabilityHistory,
  CommercialAvailabilityPanel,
} from "@/features/admin/commercial/commercial-detail-panels";
import { CommercialLifecycleDialog } from "@/features/admin/commercial/commercial-lifecycle-dialog";
import { ProductPricePanel } from "@/features/admin/price-books/product-price-panel";
import { ProductPriceSummary } from "@/features/admin/price-books/product-price-summary";
import {
  adminCommercialKeys,
  commercialQueryEnabled,
  invalidateAdminCommercial,
} from "@/features/commercial/commercial-query";
import { commercialAmount, isCommercialAmount } from "@/lib/exact-decimal";
import { addOnStatusLabel } from "./add-on-lifecycle";
import {
  commercialChoiceDescription,
  isCommercialChoiceDisabled,
  mergeCommercialChoices,
} from "./commercial-choice-rules";
import { ChoiceList, ChoiceLoadState, DeleteDraftDialog, Field, Pair } from "./commercial-form-primitives";
import { QuotaHistoryPanel, QuotaRevisionPanel } from "./quota-revision-panels";

export function QuotaForm({ item, trigger }: { item?: QuotaPackage; trigger: React.ReactNode }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [planSearch, setPlanSearch] = useState("");
  const [addOnSearch, setAddOnSearch] = useState("");
  const planOptions = useQuery({
    queryKey: ["admin", "commercial", "quota-plan-choices", planSearch],
    queryFn: () => adminApi.planChoices({ search: planSearch || undefined, size: 50 }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.plansChoose, open),
  });
  const addOnOptions = useQuery({
    queryKey: ["admin", "commercial", "quota-add-on-choices", addOnSearch],
    queryFn: () => adminApi.addOnChoices({ search: addOnSearch || undefined, size: 50 }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.addOnsChoose, open),
  });
  const registry = useQuery({
    queryKey: adminCommercialKeys.registry.featureCatalog("PLAN_ASSIGNABLE"),
    queryFn: () => adminApi.featureCatalog("PLAN_ASSIGNABLE"),
    enabled: commercialQueryEnabled(session.can, adminPermissions.registryFeatureCatalog, open),
  });
  const canReadRegistry = session.can(adminPermissions.registryFeatureCatalog);
  const registryReady = canReadRegistry && registry.isSuccess;
  const [name, setName] = useState(item?.name ?? "");
  const [description, setDescription] = useState(item?.description ?? "");
  const [featureCode, setFeatureCode] = useState(item?.featureCode ?? "");
  const [resource, setResource] = useState(item?.resource ?? "");
  const [capacity, setCapacity] = useState(String(item?.capacityPerUnit ?? 1));
  const [amount, setAmount] = useState(String(item?.price ?? 0));
  const [currency, setCurrency] = useState(item?.currencyCode ?? "MAD");
  const [cycle, setCycle] = useState<BillingCycle>(item?.billingCycle ?? "MONTHLY");
  const [repeatable, setRepeatable] = useState(item?.repeatable ?? false);
  const [maximum, setMaximum] = useState(String(item?.maximumQuantity ?? 1));
  const [plans, setPlans] = useState<string[]>(item?.allowedPlanCodes ?? []);
  const [addons, setAddons] = useState<string[]>(item?.allowedAddOnCodes ?? []);
  const canChoosePlans = session.can(adminPermissions.plansChoose);
  const canResolvePlanCodes = session.can(adminPermissions.plansResolveChoiceCodes);
  const canChooseAddOns = session.can(adminPermissions.addOnsChoose);
  const canResolveAddOnCodes = session.can(adminPermissions.addOnsResolveChoiceCodes);
  const selectedPlans = useQuery({
    queryKey: ["admin", "commercial", "quota-plan-choices", "selected", ...plans],
    queryFn: () => adminApi.selectedPlanCodeChoices(plans),
    enabled: open && session.can(adminPermissions.plansResolveChoiceCodes) && Boolean(plans.length),
  });
  const selectedAddOns = useQuery({
    queryKey: ["admin", "commercial", "quota-add-on-choices", "selected", ...addons],
    queryFn: () => adminApi.selectedAddOnCodeChoices(addons),
    enabled: open && session.can(adminPermissions.addOnsResolveChoiceCodes) && Boolean(addons.length),
  });
  const planChoices = mergeCommercialChoices(planOptions.data?.content ?? [], selectedPlans.data ?? []);
  const addOnChoices = mergeCommercialChoices(addOnOptions.data?.content ?? [], selectedAddOns.data ?? []);
  const featureOptions = (registry.data ?? [])
    .flatMap((module) => module.features)
    .filter((feature) => feature.quotaSchema.length > 0);
  const selectedFeature = featureOptions.find((feature) => feature.code === featureCode);
  const quotaTargetReady = Boolean(
    registryReady && selectedFeature?.quotaSchema.some((slot) => slot.resource === resource),
  );
  const numericLimitsValid = Number(capacity) >= 1 && Number(maximum) >= 1;
  const input = {
    name,
    description,
    featureCode,
    resource,
    capacityPerUnit: Number(capacity),
    price: commercialAmount(amount),
    currencyCode: currency,
    billingCycle: cycle,
    repeatable,
    maximumQuantity: Number(maximum),
    allowedPlanCodes: plans,
    allowedAddOnCodes: addons,
    salesVisibility: item?.salesVisibility ?? ("PUBLIC" as const),
    expectedVersion: item?.version,
  };
  const save = useMutation({
    mutationFn: () => (item ? adminApi.updateQuotaPackage(item.id, input) : adminApi.createQuotaPackage(input)),
    onSuccess: (saved) => {
      void invalidateAdminCommercial(queryClient, adminCommercialKeys.quotaPackages.all());
      toast.success(item ? "Package mis à jour" : "Package créé");
      setOpen(false);
      navigate(`/admin/quota-packages/${saved.id}`);
    },
  });
  if (!session.can(item ? adminPermissions.quotaPackagesUpdate : adminPermissions.quotaPackagesCreate)) return null;
  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{item ? "Modifier le package" : "Créer un package de quota"}</DialogTitle>
          <DialogDescription>
            Une unité de package ajoute une capacité définie sur une ressource mesurable.
          </DialogDescription>
        </DialogHeader>
        <form
          className="grid gap-4 sm:grid-cols-2"
          onSubmit={(event: FormEvent) => {
            event.preventDefault();
            if (!quotaTargetReady || !numericLimitsValid || !isCommercialAmount(amount)) return;
            save.mutate();
          }}
        >
          <Field label="Nom">
            <Input onChange={(event) => setName(event.target.value)} required value={name} />
          </Field>
          <div className="sm:col-span-2">
            <Field label="Description">
              <Textarea onChange={(event) => setDescription(event.target.value)} value={description} />
            </Field>
          </div>
          <Field label="Fonctionnalité">
            <Select
              disabled={!registryReady}
              onValueChange={(value) => {
                setFeatureCode(value);
                setResource("");
              }}
              value={featureCode}
            >
              <SelectTrigger aria-label="Fonctionnalité">
                <SelectValue placeholder="Sélectionner" />
              </SelectTrigger>
              <SelectContent>
                {featureOptions.map((feature) => (
                  <SelectItem key={feature.id} value={feature.code}>
                    {feature.displayName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
          <Field label="Ressource">
            <Select disabled={!registryReady || !selectedFeature} onValueChange={setResource} value={resource}>
              <SelectTrigger aria-label="Ressource mesurée">
                <SelectValue placeholder="Sélectionner" />
              </SelectTrigger>
              <SelectContent>
                {(selectedFeature?.quotaSchema ?? []).map((slot) => (
                  <SelectItem key={slot.resource} value={slot.resource}>
                    {slot.unit}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
          <div className="sm:col-span-2">
            <ChoiceLoadState
              error={registry.isError}
              loading={canReadRegistry && registry.isPending}
              onRetry={() => void registry.refetch()}
              unavailable={
                canReadRegistry ? null : "Votre rôle ne permet pas de consulter le catalogue des fonctionnalités."
              }
            />
          </div>
          <Field label="Capacité par unité">
            <Input min="1" onChange={(event) => setCapacity(event.target.value)} type="number" value={capacity} />
          </Field>
          <Field label="Quantité maximale">
            <Input min="1" onChange={(event) => setMaximum(event.target.value)} type="number" value={maximum} />
          </Field>
          <Field label="Prix">
            <Input inputMode="decimal" onChange={(event) => setAmount(event.target.value)} value={amount} />
          </Field>
          <Field label="Devise">
            <Input maxLength={3} onChange={(event) => setCurrency(event.target.value.toUpperCase())} value={currency} />
          </Field>
          <Field label="Cycle">
            <Select onValueChange={(value) => setCycle(value as BillingCycle)} value={cycle}>
              <SelectTrigger aria-label="Cycle de facturation">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="MONTHLY">Mensuel</SelectItem>
                <SelectItem value="YEARLY">Annuel</SelectItem>
              </SelectContent>
            </Select>
          </Field>
          <Field label="Répétition">
            <Select onValueChange={(value) => setRepeatable(value === "yes")} value={repeatable ? "yes" : "no"}>
              <SelectTrigger aria-label="Répétition du package">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="yes">Plusieurs unités</SelectItem>
                <SelectItem value="no">Une seule unité</SelectItem>
              </SelectContent>
            </Select>
          </Field>
          <div className="sm:col-span-2">
            <Field label="Rechercher un forfait">
              <Input
                onChange={(event) => setPlanSearch(event.target.value)}
                placeholder="Nom du forfait…"
                value={planSearch}
              />
            </Field>
            {planOptions.data && !planOptions.data.last ? (
              <p className="mt-1 text-xs text-muted-foreground">Affinez la recherche pour voir les autres forfaits.</p>
            ) : null}
            <ChoiceLoadState
              error={planOptions.isError || selectedPlans.isError}
              loading={planOptions.isLoading || selectedPlans.isLoading}
              onRetry={() => {
                void planOptions.refetch();
                void selectedPlans.refetch();
              }}
              unavailable={
                !canChoosePlans
                  ? "Votre rôle ne permet pas de rechercher des forfaits."
                  : plans.length && !canResolvePlanCodes
                    ? "Votre rôle ne permet pas d’afficher les forfaits déjà enregistrés."
                    : null
              }
            />
          </div>
          <ChoiceList
            label="Forfaits autorisés"
            onChange={setPlans}
            options={planChoices.map((plan) => ({
              label: plan.name,
              value: plan.code,
              description: commercialChoiceDescription(plan),
              disabled: isCommercialChoiceDisabled(plan, plans.includes(plan.code)),
            }))}
            selected={plans}
          />
          <div>
            <Field label="Rechercher un add-on">
              <Input
                onChange={(event) => setAddOnSearch(event.target.value)}
                placeholder="Nom de l’add-on…"
                value={addOnSearch}
              />
            </Field>
            {addOnOptions.data && !addOnOptions.data.last ? (
              <p className="mt-1 text-xs text-muted-foreground">Affinez la recherche pour voir les autres add-ons.</p>
            ) : null}
            <ChoiceLoadState
              error={addOnOptions.isError || selectedAddOns.isError}
              loading={addOnOptions.isLoading || selectedAddOns.isLoading}
              onRetry={() => {
                void addOnOptions.refetch();
                void selectedAddOns.refetch();
              }}
              unavailable={
                !canChooseAddOns
                  ? "Votre rôle ne permet pas de rechercher des add-ons."
                  : addons.length && !canResolveAddOnCodes
                    ? "Votre rôle ne permet pas d’afficher les add-ons déjà enregistrés."
                    : null
              }
            />
          </div>
          <ChoiceList
            label="Add-ons autorisés"
            onChange={setAddons}
            options={addOnChoices.map((addOn) => ({
              label: addOn.name,
              value: addOn.code,
              description: commercialChoiceDescription(addOn),
              disabled: isCommercialChoiceDisabled(addOn, addons.includes(addOn.code)),
            }))}
            selected={addons}
          />
          <div className="flex justify-end gap-2 sm:col-span-2">
            <Button onClick={() => setOpen(false)} type="button" variant="outline">
              Annuler
            </Button>
            <Button
              disabled={save.isPending || !quotaTargetReady || !numericLimitsValid || !isCommercialAmount(amount)}
              type="submit"
            >
              Enregistrer
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export function AdminQuotaPackagesPage() {
  const { packageId, tab = "overview" } = useParams();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const session = useAdminSession();
  const canListQuotaPackages = session.can(adminPermissions.quotaPackagesList);
  const canReadRegistryCatalog = session.can(adminPermissions.registryFeatureCatalog);
  const detail = useQuery({
    queryKey: adminCommercialKeys.quotaPackages.detail(packageId ?? ""),
    queryFn: () => adminApi.quotaPackage(packageId ?? ""),
    enabled: commercialQueryEnabled(session.can, adminPermissions.quotaPackagesReadDetail, Boolean(packageId)),
  });
  const operations = useQuery({
    queryKey: adminCommercialKeys.quotaPackages.operations(packageId ?? ""),
    queryFn: () => adminApi.quotaPackageOperations(packageId ?? ""),
    enabled: commercialQueryEnabled(session.can, adminPermissions.quotaPackagesReadOperations, Boolean(packageId)),
  });
  const registry = useQuery({
    queryKey: adminCommercialKeys.registry.featureCatalog("PLAN_ASSIGNABLE"),
    queryFn: () => adminApi.featureCatalog("PLAN_ASSIGNABLE"),
    enabled: commercialQueryEnabled(session.can, adminPermissions.registryFeatureCatalog),
  });
  const selected = detail.data;
  const inaccessibleTab =
    (tab === "prices" && !session.can(adminPermissions.priceBooksList)) ||
    (tab === "availability" && !session.can(adminPermissions.commercialPreviewQuotaVisibility)) ||
    (tab === "revisions" &&
      !session.can(adminPermissions.quotaPackagesCompare) &&
      !session.can(adminPermissions.quotaPackagesRevise)) ||
    (tab === "history" &&
      !session.can(adminPermissions.quotaPackagesHistory) &&
      !session.can(adminPermissions.commercialReadHistory)) ||
    (tab === "lifecycle" && !session.can(adminPermissions.quotaPackagesTransition));
  const hasAction = (action: CommercialProductAction) => operations.data?.availableActions.includes(action) ?? false;
  const registryFeatures = (canReadRegistryCatalog ? (registry.data ?? []) : []).flatMap((module) => module.features);
  const featureNames = new Map(registryFeatures.map((feature) => [feature.code, feature.displayName]));
  const resourceUnits = new Map(
    registryFeatures.flatMap((feature) =>
      feature.quotaSchema.map((slot) => [`${feature.code}:${slot.resource}`, slot.unit] as const),
    ),
  );
  const remove = useMutation({
    mutationFn: (id: string) => adminApi.deleteQuotaPackage(id, selected?.version ?? 0),
    onSuccess: () => {
      void invalidateAdminCommercial(queryClient, adminCommercialKeys.quotaPackages.all());
      toast.success("Brouillon supprimé");
      navigate("/admin/quota-packages");
    },
  });
  if (packageId && detail.isLoading) return <LoadingState />;
  if (packageId && (detail.isError || (!selected && !detail.isLoading))) {
    return <ErrorState retry={() => void detail.refetch()} title="Package introuvable" />;
  }
  if (selected)
    return (
      <div className="space-y-7">
        <Button asChild size="sm" variant="ghost">
          <Link to={canListQuotaPackages ? "/admin/quota-packages" : "/admin"}>
            <ArrowLeftIcon className="rtl:rotate-180" />
            {canListQuotaPackages ? "Packages de quota" : "Administration"}
          </Link>
        </Button>
        <PageHeader
          actions={
            <div className="flex flex-wrap justify-end gap-2">
              {hasAction("EDIT_DRAFT") && session.can(adminPermissions.quotaPackagesUpdate) ? (
                <QuotaForm item={selected} trigger={<Button variant="outline">Modifier</Button>} />
              ) : null}
              {hasAction("DELETE_DRAFT") && session.can(adminPermissions.quotaPackagesDelete) ? (
                <DeleteDraftDialog
                  label="ce package"
                  name={selected.name}
                  onDelete={() => remove.mutate(selected.id)}
                  pending={remove.isPending}
                />
              ) : null}
            </div>
          }
          description={
            <StatusBadge
              tone={selected.status === "ACTIVE" ? "success" : selected.status === "DRAFT" ? "info" : "warning"}
            >
              {addOnStatusLabel(selected.status)}
            </StatusBadge>
          }
          title={selected.name}
        />
        <SectionTabs
          ariaLabel="Sections du pack"
          tabs={[
            { label: "Synthèse", to: `/admin/quota-packages/${selected.id}`, end: true },
            ...(session.can(adminPermissions.priceBooksList)
              ? [{ label: "Tarifs", to: `/admin/quota-packages/${selected.id}/prices` }]
              : []),
            ...(session.can(adminPermissions.commercialPreviewQuotaVisibility)
              ? [{ label: "Disponibilité", to: `/admin/quota-packages/${selected.id}/availability` }]
              : []),
            ...(session.can(adminPermissions.quotaPackagesCompare) || session.can(adminPermissions.quotaPackagesRevise)
              ? [{ label: "Révisions", to: `/admin/quota-packages/${selected.id}/revisions` }]
              : []),
            ...(session.can(adminPermissions.quotaPackagesHistory) ||
            session.can(adminPermissions.commercialReadHistory)
              ? [{ label: "Historique", to: `/admin/quota-packages/${selected.id}/history` }]
              : []),
            ...(session.can(adminPermissions.quotaPackagesTransition)
              ? [{ label: "Cycle de vie", to: `/admin/quota-packages/${selected.id}/lifecycle` }]
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
        {inaccessibleTab ? <PermissionState /> : null}
        {tab === "overview" ? (
          <div className="grid gap-5 lg:grid-cols-2">
            <section className="rounded-xl border bg-card p-5">
              <h2 className="text-sm font-semibold">Capacité</h2>
              <dl className="mt-5 space-y-4 text-sm">
                <Pair
                  label="Fonctionnalité"
                  value={
                    canReadRegistryCatalog
                      ? (featureNames.get(selected.featureCode) ?? "Fonctionnalité introuvable")
                      : "Masquée"
                  }
                />
                <Pair
                  label="Ressource"
                  value={
                    canReadRegistryCatalog
                      ? (resourceUnits.get(`${selected.featureCode}:${selected.resource}`) ?? "Ressource introuvable")
                      : "Masquée"
                  }
                />
                <Pair label="Par unité" value={selected.capacityPerUnit} />
                <Pair label="Maximum" value={selected.maximumQuantity} />
              </dl>
            </section>
            <section className="rounded-xl border bg-card p-5">
              <h2 className="text-sm font-semibold">Commercial</h2>
              <dl className="mt-5 space-y-4 text-sm">
                <div>
                  <dt className="mb-3 text-muted-foreground">Tarifs par unité</dt>
                  <dd>
                    <ProductPriceSummary ownerType="QUOTA_PACKAGE" ownerId={selected.id} />
                  </dd>
                </div>
                <Pair label="Répétable" value={selected.repeatable ? "Oui" : "Non"} />
                <Pair label="Version" value={selected.definitionVersion} />
              </dl>
            </section>
          </div>
        ) : null}
        {tab === "prices" && session.can(adminPermissions.priceBooksList) ? (
          <ProductPricePanel ownerId={selected.id} ownerType="QUOTA_PACKAGE" />
        ) : null}
        {tab === "availability" && session.can(adminPermissions.commercialPreviewQuotaVisibility) ? (
          <CommercialAvailabilityPanel kind="quota" product={selected} />
        ) : null}
        {tab === "revisions" &&
        (session.can(adminPermissions.quotaPackagesCompare) || session.can(adminPermissions.quotaPackagesRevise)) ? (
          <QuotaRevisionPanel key={selected.id} product={selected} />
        ) : null}
        {tab === "history" ? (
          <div className="space-y-6">
            {session.can(adminPermissions.quotaPackagesHistory) ? (
              <QuotaHistoryPanel key={selected.id} productId={selected.id} />
            ) : null}
            {session.can(adminPermissions.commercialReadHistory) ? (
              <CommercialAvailabilityHistory productId={selected.id} />
            ) : null}
          </div>
        ) : null}
        {tab === "lifecycle" &&
        session.can(adminPermissions.quotaPackagesTransition) &&
        selected.status !== "ARCHIVED" ? (
          <section className="rounded-xl border bg-card p-5">
            <h2 className="text-sm font-semibold">Cycle de vie</h2>
            <div className="mt-4 flex flex-wrap gap-2">
              {hasAction("ACTIVATE") && session.can(adminPermissions.quotaPackagesPreviewActivation) ? (
                <CommercialLifecycleDialog
                  action="ACTIVATE"
                  kind="quota"
                  onChanged={() => void detail.refetch()}
                  product={selected}
                  trigger={<Button>Activer</Button>}
                />
              ) : hasAction("DEACTIVATE") ? (
                <CommercialLifecycleDialog
                  action="DEACTIVATE"
                  kind="quota"
                  onChanged={() => void detail.refetch()}
                  product={selected}
                  trigger={<Button variant="outline">Suspendre</Button>}
                />
              ) : null}
              {hasAction("ARCHIVE") ? (
                <CommercialLifecycleDialog
                  action="ARCHIVE"
                  kind="quota"
                  onChanged={() => void detail.refetch()}
                  product={selected}
                  trigger={<Button variant="destructive">Archiver</Button>}
                />
              ) : null}
            </div>
          </section>
        ) : null}
      </div>
    );
  return <ErrorState title="Pack introuvable" />;
}
