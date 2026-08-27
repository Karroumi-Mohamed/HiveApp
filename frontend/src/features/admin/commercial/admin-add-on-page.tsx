import { ArrowLeftIcon, PencilSimpleIcon, PlusIcon, TrashIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type FormEvent, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { AddOn, BillingCycle, CommercialProductAction, ExactDecimal, QuotaLimit } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { QuotaEditor } from "@/components/patterns/quota-editor";
import { EmptyState, ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
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
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import {
  CommercialAvailabilityHistory,
  CommercialAvailabilityPanel,
} from "@/features/admin/commercial/commercial-detail-panels";
import { CommercialLifecycleDialog } from "@/features/admin/commercial/commercial-lifecycle-dialog";
import { ProductPricePanel } from "@/features/admin/price-books/product-price-panel";
import {
  adminCommercialKeys,
  commercialQueryEnabled,
  invalidateAdminCommercial,
} from "@/features/commercial/commercial-query";
import { commercialAmount, formatExactMoney, isCommercialAmount } from "@/lib/exact-decimal";
import { addOnLifecycleActions, addOnStatusLabel } from "./add-on-lifecycle";
import {
  commercialChoiceDescription,
  isCommercialChoiceDisabled,
  mergeCommercialChoices,
} from "./commercial-choice-rules";

const price = (value: ExactDecimal, currency: string) => formatExactMoney(value, currency);

import {
  ChoiceList,
  ChoiceLoadState,
  ConfirmActionDialog,
  DeleteDraftDialog,
  Field,
  Pair,
  ReferenceSet,
} from "./commercial-form-primitives";

function AddOnRevisionAction({
  item,
  revise,
  revisionPending,
}: {
  item: AddOn;
  revise: () => Promise<unknown>;
  revisionPending: boolean;
}) {
  const session = useAdminSession();
  const actions = addOnLifecycleActions(item.status);
  return actions.includes("REVISE") && session.can(adminPermissions.addOnsRevise) ? (
    <ConfirmActionDialog
      confirmLabel={`Créer R${item.revisionNumber + 1}`}
      description="Le prix, les compatibilités, les dépendances, les fonctionnalités et leurs quotas seront copiés dans un nouveau brouillon. La révision actuellement en vente reste inchangée jusqu’à la publication du brouillon. Les packages de capacité attachés à cette révision restent à vérifier séparément."
      onConfirm={revise}
      pending={revisionPending}
      title="Créer une nouvelle révision ?"
      trigger={<Button variant="outline">Créer une révision</Button>}
    />
  ) : null;
}

export function AddOnForm({ item, trigger }: { item?: AddOn; trigger: React.ReactNode }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [planSearch, setPlanSearch] = useState("");
  const [addOnSearch, setAddOnSearch] = useState("");
  const planOptions = useQuery({
    queryKey: ["admin", "commercial", "plan-choices", planSearch],
    queryFn: () => adminApi.planChoices({ search: planSearch || undefined, size: 50 }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.plansChoose, open),
  });
  const addOnOptions = useQuery({
    queryKey: ["admin", "commercial", "add-on-choices", addOnSearch],
    queryFn: () => adminApi.addOnChoices({ search: addOnSearch || undefined, size: 50 }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.addOnsChoose, open),
  });
  const [name, setName] = useState(item?.name ?? "");
  const [description, setDescription] = useState(item?.description ?? "");
  const [amount, setAmount] = useState(String(item?.price ?? 0));
  const [currency, setCurrency] = useState(item?.currencyCode ?? "MAD");
  const [cycle, setCycle] = useState<BillingCycle>(item?.billingCycle ?? "MONTHLY");
  const [allowed, setAllowed] = useState<string[]>(item?.allowedPlanCodes ?? []);
  const [blocked, setBlocked] = useState<string[]>(item?.blockedPlanCodes ?? []);
  const [dependencies, setDependencies] = useState<string[]>(item?.dependencyCodes ?? []);
  const [exclusions, setExclusions] = useState<string[]>(item?.exclusionCodes ?? []);
  const canChoosePlans = session.can(adminPermissions.plansChoose);
  const canResolvePlanCodes = session.can(adminPermissions.plansResolveChoiceCodes);
  const canChooseAddOns = session.can(adminPermissions.addOnsChoose);
  const canResolveAddOnCodes = session.can(adminPermissions.addOnsResolveChoiceCodes);
  const selectedPlans = useQuery({
    queryKey: ["admin", "commercial", "plan-choices", "selected", ...allowed, ...blocked],
    queryFn: () => adminApi.selectedPlanCodeChoices([...new Set([...allowed, ...blocked])]),
    enabled: open && session.can(adminPermissions.plansResolveChoiceCodes) && Boolean(allowed.length || blocked.length),
  });
  const selectedAddOns = useQuery({
    queryKey: ["admin", "commercial", "add-on-choices", "selected", ...dependencies, ...exclusions],
    queryFn: () => adminApi.selectedAddOnCodeChoices([...new Set([...dependencies, ...exclusions])]),
    enabled:
      open &&
      session.can(adminPermissions.addOnsResolveChoiceCodes) &&
      Boolean(dependencies.length || exclusions.length),
  });
  const planChoices = mergeCommercialChoices(planOptions.data?.content ?? [], selectedPlans.data ?? []);
  const addOnChoices = mergeCommercialChoices(addOnOptions.data?.content ?? [], selectedAddOns.data ?? []);
  const overlappingPlan = allowed.find((code) => blocked.includes(code));
  const overlappingRelation = dependencies.find((code) => exclusions.includes(code));
  const formBlocker = overlappingPlan
    ? "Un forfait ne peut pas être autorisé et bloqué en même temps."
    : overlappingRelation
      ? "Un add-on ne peut pas être requis et incompatible en même temps."
      : null;
  const input = {
    name,
    description,
    price: commercialAmount(amount),
    currencyCode: currency,
    billingCycle: cycle,
    allowedPlanCodes: allowed,
    blockedPlanCodes: blocked,
    dependencyCodes: dependencies,
    exclusionCodes: exclusions,
    salesVisibility: item?.salesVisibility ?? ("PUBLIC" as const),
    expectedVersion: item?.version,
  };
  const save = useMutation({
    mutationFn: () => (item ? adminApi.updateAddOn(item.id, input) : adminApi.createAddOn(input)),
    onSuccess: (saved) => {
      void invalidateAdminCommercial(queryClient, adminCommercialKeys.addOns.all());
      toast.success(item ? "Add-on mis à jour" : "Add-on créé");
      setOpen(false);
      navigate(`/admin/add-ons/${saved.id}`);
    },
  });
  if (!session.can(item ? adminPermissions.addOnsUpdate : adminPermissions.addOnsCreate)) return null;
  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{item ? "Modifier l’add-on" : "Créer un add-on"}</DialogTitle>
          <DialogDescription>
            Définissez le prix, les forfaits compatibles et les relations avec les autres add-ons.
          </DialogDescription>
        </DialogHeader>
        <form
          className="grid gap-4 sm:grid-cols-2"
          onSubmit={(event: FormEvent) => {
            event.preventDefault();
            if (formBlocker || !isCommercialAmount(amount)) return;
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
          <Field label="Prix">
            <Input inputMode="decimal" onChange={(event) => setAmount(event.target.value)} value={amount} />
          </Field>
          <Field label="Devise">
            <Input
              maxLength={3}
              onChange={(event) => {
                setCurrency(event.target.value.toUpperCase());
              }}
              required
              value={currency}
            />
          </Field>
          <Field label="Cycle">
            <Select
              onValueChange={(value) => {
                setCycle(value as BillingCycle);
              }}
              value={cycle}
            >
              <SelectTrigger aria-label="Cycle de facturation">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="MONTHLY">Mensuel</SelectItem>
                <SelectItem value="YEARLY">Annuel</SelectItem>
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
                  : (allowed.length || blocked.length) && !canResolvePlanCodes
                    ? "Votre rôle ne permet pas d’afficher les forfaits déjà enregistrés."
                    : null
              }
            />
          </div>
          <ChoiceList
            label="Forfaits autorisés"
            onChange={setAllowed}
            options={planChoices.map((plan) => {
              return {
                label: plan.name,
                value: plan.code,
                description: commercialChoiceDescription(plan),
                disabled: isCommercialChoiceDisabled(plan, allowed.includes(plan.code), blocked.includes(plan.code)),
              };
            })}
            selected={allowed}
          />
          <ChoiceList
            label="Forfaits bloqués"
            onChange={setBlocked}
            options={planChoices.map((plan) => ({
              label: plan.name,
              value: plan.code,
              description: commercialChoiceDescription(plan),
              disabled: isCommercialChoiceDisabled(plan, blocked.includes(plan.code), allowed.includes(plan.code)),
            }))}
            selected={blocked}
          />
          <div className="sm:col-span-2">
            <Field label="Rechercher un add-on lié">
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
                  : (dependencies.length || exclusions.length) && !canResolveAddOnCodes
                    ? "Votre rôle ne permet pas d’afficher les relations déjà enregistrées."
                    : null
              }
            />
          </div>
          <ChoiceList
            label="Dépendances"
            onChange={setDependencies}
            options={addOnChoices
              .filter((candidate) => candidate.id !== item?.id)
              .map((candidate) => ({
                label: candidate.name,
                value: candidate.code,
                description: commercialChoiceDescription(candidate),
                disabled: isCommercialChoiceDisabled(
                  candidate,
                  dependencies.includes(candidate.code),
                  exclusions.includes(candidate.code),
                ),
              }))}
            selected={dependencies}
          />
          <ChoiceList
            label="Incompatible avec"
            onChange={setExclusions}
            options={addOnChoices
              .filter((candidate) => candidate.id !== item?.id)
              .map((candidate) => ({
                label: candidate.name,
                value: candidate.code,
                description: commercialChoiceDescription(candidate),
                disabled: isCommercialChoiceDisabled(
                  candidate,
                  exclusions.includes(candidate.code),
                  dependencies.includes(candidate.code),
                ),
              }))}
            selected={exclusions}
          />
          {formBlocker ? (
            <p aria-live="polite" className="text-sm text-destructive sm:col-span-2" role="alert">
              {formBlocker}
            </p>
          ) : null}
          <div className="flex justify-end gap-2 sm:col-span-2">
            <Button onClick={() => setOpen(false)} type="button" variant="outline">
              Annuler
            </Button>
            <Button disabled={save.isPending || Boolean(formBlocker) || !isCommercialAmount(amount)} type="submit">
              Enregistrer
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function AddOnFeatureDialog({ item, addOn }: { item?: AddOn["features"][number]; addOn: AddOn }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [featureCode, setFeatureCode] = useState(item?.featureCode ?? "");
  const [quotas, setQuotas] = useState<QuotaLimit[]>(item?.quotaConfigs ?? []);
  const catalog = useQuery({
    queryKey: adminCommercialKeys.registry.featureCatalog("PLAN_ASSIGNABLE"),
    queryFn: () => adminApi.featureCatalog("PLAN_ASSIGNABLE"),
    enabled: commercialQueryEnabled(session.can, adminPermissions.registryFeatureCatalog, open),
  });
  const available = (catalog.data ?? [])
    .flatMap((module) => module.features)
    .filter(
      (feature) =>
        feature.planAssignable &&
        (feature.code === item?.featureCode ||
          !addOn.features.some((assigned) => assigned.featureCode === feature.code)),
    );
  const definition = available.find((feature) => feature.code === featureCode);
  const save = useMutation({
    mutationFn: () => {
      const input = { featureCode, quotaConfigs: quotas };
      return item
        ? adminApi.updateAddOnFeature(addOn.id, item.id, addOn.version, input)
        : adminApi.assignAddOnFeature(addOn.id, addOn.version, input);
    },
    onSuccess: () => {
      void invalidateAdminCommercial(queryClient, adminCommercialKeys.addOns.all());
      toast.success(item ? "Configuration mise à jour" : "Fonctionnalité ajoutée");
      setOpen(false);
    },
  });
  if (!session.can(item ? adminPermissions.addOnsUpdateFeature : adminPermissions.addOnsAssignFeature)) return null;
  return (
    <Dialog onOpenChange={setOpen} open={open}>
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
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{item ? "Configurer la fonctionnalité" : "Ajouter une fonctionnalité"}</DialogTitle>
          <DialogDescription>
            Les quotas s’ajoutent à ceux du forfait lorsque cet add-on est sélectionné.
          </DialogDescription>
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
          <QuotaEditor onChange={setQuotas} slots={definition?.quotaSchema ?? []} value={quotas} />
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

export function AdminAddOnsPage() {
  const { addOnId, tab = "overview" } = useParams();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const session = useAdminSession();
  const canListAddOns = session.can(adminPermissions.addOnsList);
  const canReadRegistryCatalog = session.can(adminPermissions.registryFeatureCatalog);
  const detail = useQuery({
    queryKey: adminCommercialKeys.addOns.detail(addOnId ?? ""),
    queryFn: () => adminApi.addOn(addOnId ?? ""),
    enabled: commercialQueryEnabled(session.can, adminPermissions.addOnsReadDetail, Boolean(addOnId)),
  });
  const operations = useQuery({
    queryKey: ["admin", "commercial", "add-on", addOnId, "operations"],
    queryFn: () => adminApi.addOnOperations(addOnId ?? ""),
    enabled: commercialQueryEnabled(session.can, adminPermissions.addOnsReadOperations, Boolean(addOnId)),
  });
  const referencedPlans = useQuery({
    queryKey: [
      "admin",
      "commercial",
      "add-on",
      addOnId,
      "referenced-plans",
      detail.data?.allowedPlanCodes,
      detail.data?.blockedPlanCodes,
    ],
    queryFn: () =>
      adminApi.selectedPlanCodeChoices([
        ...(detail.data?.allowedPlanCodes ?? []),
        ...(detail.data?.blockedPlanCodes ?? []),
      ]),
    enabled: Boolean(
      session.can(adminPermissions.plansResolveChoiceCodes) &&
        addOnId &&
        detail.data &&
        (detail.data.allowedPlanCodes.length || detail.data.blockedPlanCodes.length),
    ),
  });
  const referencedAddOns = useQuery({
    queryKey: [
      "admin",
      "commercial",
      "add-on",
      addOnId,
      "relations",
      detail.data?.dependencyCodes,
      detail.data?.exclusionCodes,
    ],
    queryFn: () =>
      adminApi.selectedAddOnCodeChoices([
        ...(detail.data?.dependencyCodes ?? []),
        ...(detail.data?.exclusionCodes ?? []),
      ]),
    enabled: Boolean(
      session.can(adminPermissions.addOnsResolveChoiceCodes) &&
        addOnId &&
        detail.data &&
        (detail.data.dependencyCodes.length || detail.data.exclusionCodes.length),
    ),
  });
  const registry = useQuery({
    queryKey: adminCommercialKeys.registry.featureCatalog("PLAN_ASSIGNABLE"),
    queryFn: () => adminApi.featureCatalog("PLAN_ASSIGNABLE"),
    enabled: commercialQueryEnabled(session.can, adminPermissions.registryFeatureCatalog, Boolean(addOnId)),
  });
  const selected = detail.data;
  const inaccessibleTab =
    (tab === "prices" && !session.can(adminPermissions.priceBooksList)) ||
    (tab === "availability" && !session.can(adminPermissions.commercialPreviewAddOnVisibility)) ||
    (tab === "history" && !session.can(adminPermissions.commercialReadHistory));
  const hasAction = (action: CommercialProductAction) => operations.data?.availableActions.includes(action) ?? false;
  const readableRegistry = canReadRegistryCatalog ? (registry.data ?? []) : [];
  const planNames = new Map((referencedPlans.data ?? []).map((plan) => [plan.code, plan.name]));
  const addOnNames = new Map((referencedAddOns.data ?? []).map((item) => [item.code, item.name]));
  const planName = (code: string) =>
    session.can(adminPermissions.plansResolveChoiceCodes) ? (planNames.get(code) ?? "Forfait introuvable") : "Masqué";
  const addOnName = (code: string) =>
    session.can(adminPermissions.addOnsResolveChoiceCodes) ? (addOnNames.get(code) ?? "Add-on introuvable") : "Masqué";
  const featureNames = new Map(
    readableRegistry.flatMap((module) => module.features).map((feature) => [feature.code, feature.displayName]),
  );
  const resourceUnits = new Map(
    readableRegistry.flatMap((module) =>
      module.features.flatMap((feature) =>
        feature.quotaSchema.map((slot) => [`${feature.code}:${slot.resource}`, slot.unit] as const),
      ),
    ),
  );
  const revise = useMutation({
    mutationFn: (id: string) => adminApi.reviseAddOn(id, selected?.version),
    onSuccess: (revision) => {
      void invalidateAdminCommercial(queryClient, adminCommercialKeys.addOns.all());
      toast.success(`Révision R${revision.revisionNumber} créée`);
      navigate(`/admin/add-ons/${revision.id}`);
    },
  });
  const removeFeature = useMutation({
    mutationFn: ({ id, featureId }: { id: string; featureId: string }) =>
      adminApi.removeAddOnFeature(id, featureId, selected?.version ?? 0),
    onSuccess: () => {
      void invalidateAdminCommercial(queryClient, adminCommercialKeys.addOns.all());
      toast.success("Fonctionnalité retirée");
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => adminApi.deleteAddOn(id, selected?.version ?? 0),
    onSuccess: () => {
      void invalidateAdminCommercial(queryClient, adminCommercialKeys.addOns.all());
      toast.success("Brouillon supprimé");
      navigate("/admin/add-ons");
    },
  });
  if (addOnId && detail.isLoading) return <LoadingState />;
  if (addOnId && (detail.isError || (!selected && !detail.isLoading))) {
    return <ErrorState retry={() => void detail.refetch()} title="Add-on introuvable" />;
  }
  if (selected)
    return (
      <div className="space-y-7">
        <Button asChild size="sm" variant="ghost">
          <Link to={canListAddOns ? "/admin/add-ons" : "/admin"}>
            <ArrowLeftIcon className="rtl:rotate-180" />
            {canListAddOns ? "Add-ons" : "Administration"}
          </Link>
        </Button>
        <PageHeader
          actions={
            <div className="flex flex-wrap justify-end gap-2">
              {hasAction("EDIT_DRAFT") && session.can(adminPermissions.addOnsUpdate) ? (
                <AddOnForm item={selected} trigger={<Button variant="outline">Modifier</Button>} />
              ) : null}
              {hasAction("DELETE_DRAFT") && session.can(adminPermissions.addOnsDelete) ? (
                <DeleteDraftDialog
                  label="cet add-on"
                  name={selected.name}
                  onDelete={() => remove.mutate(selected.id)}
                  pending={remove.isPending}
                />
              ) : null}
              {hasAction("REVISE") ? (
                <AddOnRevisionAction
                  item={selected}
                  revise={() => revise.mutateAsync(selected.id)}
                  revisionPending={revise.isPending}
                />
              ) : null}
              {hasAction("ACTIVATE") &&
              session.can(adminPermissions.addOnsTransition) &&
              session.can(adminPermissions.addOnsPreviewActivation) ? (
                <CommercialLifecycleDialog
                  action="ACTIVATE"
                  kind="add-on"
                  onChanged={() => void detail.refetch()}
                  product={selected}
                  trigger={<Button>Activer</Button>}
                />
              ) : null}
              {hasAction("DEACTIVATE") && session.can(adminPermissions.addOnsTransition) ? (
                <CommercialLifecycleDialog
                  action="DEACTIVATE"
                  kind="add-on"
                  onChanged={() => void detail.refetch()}
                  product={selected}
                  trigger={<Button variant="outline">Suspendre</Button>}
                />
              ) : null}
              {hasAction("ARCHIVE") && session.can(adminPermissions.addOnsTransition) ? (
                <CommercialLifecycleDialog
                  action="ARCHIVE"
                  kind="add-on"
                  onChanged={() => void detail.refetch()}
                  product={selected}
                  trigger={<Button variant="outline">Archiver</Button>}
                />
              ) : null}
            </div>
          }
          description={
            <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
              <StatusBadge
                tone={selected.status === "ACTIVE" ? "success" : selected.status === "DRAFT" ? "info" : "warning"}
              >
                {addOnStatusLabel(selected.status)}
              </StatusBadge>
            </div>
          }
          title={selected.name}
        />
        <SectionTabs
          ariaLabel="Sections de l’add-on"
          tabs={[
            { label: "Synthèse", to: `/admin/add-ons/${selected.id}`, end: true },
            { label: "Composition", to: `/admin/add-ons/${selected.id}/composition`, count: selected.features.length },
            ...(session.can(adminPermissions.priceBooksList)
              ? [{ label: "Tarifs", to: `/admin/add-ons/${selected.id}/prices` }]
              : []),
            ...(session.can(adminPermissions.commercialPreviewAddOnVisibility)
              ? [{ label: "Disponibilité", to: `/admin/add-ons/${selected.id}/availability` }]
              : []),
            ...(session.can(adminPermissions.commercialReadHistory)
              ? [{ label: "Historique", to: `/admin/add-ons/${selected.id}/history` }]
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
              <h2 className="text-sm font-semibold">Configuration</h2>
              <dl className="mt-5 space-y-4 text-sm">
                <Pair
                  label="Prix"
                  value={`${price(selected.price, selected.currencyCode)} / ${selected.billingCycle}`}
                />
                <Pair label="Révision" value={`R${selected.revisionNumber}`} />
                <Pair label="Fonctionnalités" value={selected.features.length} />
              </dl>
            </section>
            <section className="rounded-xl border bg-card p-5">
              <h2 className="text-sm font-semibold">Compatibilité</h2>
              <ReferenceSet label="Forfaits autorisés" values={selected.allowedPlanCodes.map(planName)} />
              <ReferenceSet label="Forfaits bloqués" values={selected.blockedPlanCodes.map(planName)} />
              <ReferenceSet label="Dépendances" values={selected.dependencyCodes.map(addOnName)} />
              <ReferenceSet label="Exclusions" values={selected.exclusionCodes.map(addOnName)} />
            </section>
          </div>
        ) : null}
        {tab === "prices" && session.can(adminPermissions.priceBooksList) ? (
          <ProductPricePanel ownerId={selected.id} ownerType="ADD_ON" />
        ) : null}
        {tab === "availability" && session.can(adminPermissions.commercialPreviewAddOnVisibility) ? (
          <CommercialAvailabilityPanel kind="add-on" product={selected} />
        ) : null}
        {tab === "history" && session.can(adminPermissions.commercialReadHistory) ? (
          <CommercialAvailabilityHistory productId={selected.id} />
        ) : null}
        {tab === "composition" ? (
          <section className="overflow-hidden rounded-xl border bg-card">
            <div className="flex items-center justify-between border-b p-4">
              <div>
                <h2 className="text-sm font-semibold">Fonctionnalités</h2>
                <p className="mt-1 text-xs text-muted-foreground">{selected.features.length} configurées</p>
              </div>
              {hasAction("MANAGE_COMPOSITION") ? <AddOnFeatureDialog addOn={selected} /> : null}
            </div>
            {selected.features.length ? (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Fonctionnalité</TableHead>
                    <TableHead>Quotas</TableHead>
                    <TableHead className="w-24" />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {selected.features.map((feature) => (
                    <TableRow key={feature.id}>
                      <TableCell>
                        <span className="text-sm">
                          {featureNames.get(feature.featureCode) ?? "Fonctionnalité indisponible"}
                        </span>
                      </TableCell>
                      <TableCell>
                        {feature.quotaConfigs.length
                          ? feature.quotaConfigs
                              .map(
                                (quota) =>
                                  `${resourceUnits.get(`${feature.featureCode}:${quota.resource}`) ?? "capacité"}: ${quota.mode === "UNLIMITED" ? "∞" : quota.limit}`,
                              )
                              .join(" · ")
                          : "—"}
                      </TableCell>
                      <TableCell>
                        {hasAction("MANAGE_COMPOSITION") ? (
                          <div className="flex justify-end">
                            <AddOnFeatureDialog addOn={selected} item={feature} />
                            {session.can(adminPermissions.addOnsRemoveFeature) ? (
                              <Button
                                aria-label="Retirer"
                                onClick={() => removeFeature.mutate({ id: selected.id, featureId: feature.id })}
                                size="icon-sm"
                                variant="ghost"
                              >
                                <TrashIcon />
                              </Button>
                            ) : null}
                          </div>
                        ) : null}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            ) : (
              <EmptyState title="Aucune fonctionnalité" />
            )}
          </section>
        ) : null}
      </div>
    );
  return <ErrorState title="Add-on introuvable" />;
}
