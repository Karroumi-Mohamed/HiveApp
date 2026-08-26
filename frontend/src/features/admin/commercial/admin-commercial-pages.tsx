import { ArrowLeftIcon, MagnifyingGlassIcon, PencilSimpleIcon, PlusIcon, TrashIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { cloneElement, type FormEvent, isValidElement, useEffect, useId, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { AddOn, BillingCycle, Plan, QuotaLimit, QuotaPackage } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { QuotaEditor } from "@/components/patterns/quota-editor";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { StatusBadge } from "@/components/patterns/status-badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
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
import { ProductPricePanel } from "@/features/admin/price-books/product-price-panel";
import {
  adminCommercialKeys,
  commercialQueryEnabled,
  invalidateAdminCommercial,
} from "@/features/commercial/commercial-query";
import {
  type AddOnPlanCompatibilityIssue,
  addOnLifecycleActions,
  addOnPlanCompatibilityIssue,
  addOnStatusLabel,
} from "./add-on-lifecycle";

const price = (value: number, currency: string) =>
  new Intl.NumberFormat("fr-MA", { style: "currency", currency }).format(value);

const cycleLabel = (cycle: BillingCycle) =>
  cycle === "MONTHLY" ? "mensuel" : cycle === "YEARLY" ? "annuel" : "permanent";

function planCompatibilityMessage(issue: AddOnPlanCompatibilityIssue, planName: string): string {
  if (issue === "PLAN_NOT_ACTIVE") return `${planName} n’est pas en vente.`;
  if (issue === "CURRENCY_MISMATCH") return `${planName} utilise une autre devise.`;
  return `${planName} utilise un autre cycle de facturation.`;
}

function publicationPlanBlocker(addOn: AddOn, plans: Plan[]): string | undefined {
  const candidates = addOn.allowedPlanCodes.length
    ? addOn.allowedPlanCodes.map((code) => plans.find((plan) => plan.code === code))
    : plans.filter((plan) => !addOn.blockedPlanCodes.includes(plan.code));
  const missingCode = addOn.allowedPlanCodes.find((code) => !plans.some((plan) => plan.code === code));
  if (missingCode) return `Le forfait ${missingCode} n’existe plus.`;
  const issue = candidates
    .filter((plan): plan is Plan => Boolean(plan))
    .map((plan) => ({ plan, issue: addOnPlanCompatibilityIssue(addOn.currencyCode, addOn.billingCycle, plan) }))
    .find((entry) => entry.issue);
  if (addOn.allowedPlanCodes.length && issue?.issue) {
    if (issue.issue === "CURRENCY_MISMATCH") {
      return `${issue.plan.name} utilise ${issue.plan.currencyCode}, mais cet add-on utilise ${addOn.currencyCode}.`;
    }
    if (issue.issue === "CYCLE_MISMATCH") {
      return `${issue.plan.name} est facturé en cycle ${cycleLabel(issue.plan.billingCycle)}, mais cet add-on est ${cycleLabel(addOn.billingCycle)}.`;
    }
    return `${issue.plan.name} n’est pas en vente.`;
  }
  const hasCompatiblePlan = candidates
    .filter((plan): plan is Plan => Boolean(plan))
    .some((plan) => !addOnPlanCompatibilityIssue(addOn.currencyCode, addOn.billingCycle, plan));
  if (!hasCompatiblePlan) {
    return `Aucun forfait en vente ne correspond à ${addOn.currencyCode} · ${cycleLabel(addOn.billingCycle)}.`;
  }
  return undefined;
}

function ConfirmActionDialog({
  trigger,
  title,
  description,
  confirmLabel,
  onConfirm,
  pending,
  destructive = false,
}: {
  trigger: React.ReactNode;
  title: string;
  description: string;
  confirmLabel: string;
  onConfirm: () => Promise<unknown>;
  pending: boolean;
  destructive?: boolean;
}) {
  const [open, setOpen] = useState(false);
  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <div className="flex justify-end gap-2">
          <Button onClick={() => setOpen(false)} type="button" variant="outline">
            Annuler
          </Button>
          <Button
            disabled={pending}
            onClick={async () => {
              try {
                await onConfirm();
                setOpen(false);
              } catch {
                // The shared mutation handler displays the backend's exact blocker.
              }
            }}
            type="button"
            variant={destructive ? "destructive" : "default"}
          >
            {confirmLabel}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function SimpleLifecycleActions({
  status,
  transition,
  pending,
}: {
  status: string;
  transition: (status: string) => Promise<unknown>;
  pending: boolean;
}) {
  if (status === "ARCHIVED") return <p className="text-sm text-muted-foreground">État définitif.</p>;
  if (status === "DRAFT")
    return (
      <ConfirmActionDialog
        confirmLabel="Publier"
        description="Après publication, ce package ne pourra plus être modifié directement."
        onConfirm={() => transition("ACTIVE")}
        pending={pending}
        title="Publier ce package ?"
        trigger={<Button size="sm">Publier</Button>}
      />
    );
  return (
    <div className="flex flex-wrap gap-2">
      <Button disabled={pending} onClick={() => void transition(status === "ACTIVE" ? "INACTIVE" : "ACTIVE")} size="sm">
        {status === "ACTIVE" ? "Suspendre la vente" : "Remettre en vente"}
      </Button>
      <ConfirmActionDialog
        confirmLabel="Archiver définitivement"
        description="Cette action est irréversible. Le package ne pourra plus être remis en vente."
        destructive
        onConfirm={() => transition("ARCHIVED")}
        pending={pending}
        title="Archiver ce package ?"
        trigger={
          <Button size="sm" variant="outline">
            Archiver
          </Button>
        }
      />
    </div>
  );
}

function AddOnLifecycleActions({
  item,
  activeRevision,
  transition,
  revise,
  transitionPending,
  revisionPending,
  publicationBlocker,
}: {
  item: AddOn;
  activeRevision?: AddOn;
  transition: (status: AddOn["status"]) => Promise<unknown>;
  revise: () => Promise<unknown>;
  transitionPending: boolean;
  revisionPending: boolean;
  publicationBlocker?: string;
}) {
  const session = useAdminSession();
  const actions = addOnLifecycleActions(item.status);
  const replacing = activeRevision
    ? ` Cette publication suspendra automatiquement la révision R${activeRevision.revisionNumber} pour les nouvelles ventes.`
    : "";
  return (
    <>
      {actions.includes("REVISE") && session.can(adminPermissions.addOnsRevise) ? (
        <ConfirmActionDialog
          confirmLabel={`Créer R${item.revisionNumber + 1}`}
          description="Le prix, les compatibilités, les dépendances, les fonctionnalités et leurs quotas seront copiés dans un nouveau brouillon. La révision actuellement en vente reste inchangée jusqu’à la publication du brouillon. Les packages de capacité attachés à cette révision restent à vérifier séparément."
          onConfirm={revise}
          pending={revisionPending}
          title="Créer une nouvelle révision ?"
          trigger={<Button variant="outline">Créer une révision</Button>}
        />
      ) : null}
      {actions.includes("PAUSE") && session.can(adminPermissions.addOnsTransition) ? (
        <ConfirmActionDialog
          confirmLabel="Suspendre les ventes"
          description="L’add-on disparaîtra des nouvelles offres. Les abonnements existants conserveront leur version achetée."
          onConfirm={() => transition("INACTIVE")}
          pending={transitionPending}
          title="Suspendre les nouvelles ventes ?"
          trigger={<Button variant="outline">Suspendre la vente</Button>}
        />
      ) : null}
      {(actions.includes("PUBLISH") || actions.includes("RESUME")) && session.can(adminPermissions.addOnsTransition) ? (
        <ConfirmActionDialog
          confirmLabel={actions.includes("PUBLISH") ? "Publier" : "Remettre en vente"}
          description={`L’add-on sera proposé aux nouveaux clients compatibles.${replacing} Les abonnements existants ne changent pas.`}
          onConfirm={() => transition("ACTIVE")}
          pending={transitionPending}
          title={actions.includes("PUBLISH") ? "Publier cet add-on ?" : "Remettre cet add-on en vente ?"}
          trigger={
            <Button disabled={Boolean(publicationBlocker)} title={publicationBlocker}>
              {actions.includes("PUBLISH") ? "Publier" : "Remettre en vente"}
            </Button>
          }
        />
      ) : null}
      {actions.includes("ARCHIVE") && session.can(adminPermissions.addOnsTransition) ? (
        <ConfirmActionDialog
          confirmLabel="Archiver définitivement"
          description="Cette action est irréversible : cette révision ne pourra plus être modifiée, publiée ou remise en vente. Les abonnements existants conservent leurs conditions enregistrées."
          destructive
          onConfirm={() => transition("ARCHIVED")}
          pending={transitionPending}
          title="Archiver définitivement cet add-on ?"
          trigger={<Button variant="outline">Archiver</Button>}
        />
      ) : null}
    </>
  );
}

function DeleteDraftDialog({
  name,
  label,
  onDelete,
  pending,
}: {
  name: string;
  label: string;
  onDelete: () => void;
  pending: boolean;
}) {
  const [confirmation, setConfirmation] = useState("");
  return (
    <Dialog>
      <DialogTrigger asChild>
        <Button variant="destructive">
          <TrashIcon />
          Supprimer
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Supprimer {label}</DialogTitle>
          <DialogDescription>
            Seuls les brouillons inutilisés peuvent être supprimés. Saisissez le nom exact pour confirmer.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <Input
            aria-label={`Saisissez ${name} pour confirmer`}
            onChange={(event) => setConfirmation(event.target.value)}
            value={confirmation}
          />
          <div className="flex justify-end">
            <Button disabled={confirmation !== name || pending} onClick={onDelete} variant="destructive">
              Supprimer définitivement
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function AddOnForm({ item, trigger }: { item?: AddOn; trigger: React.ReactNode }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const planOptions = useQuery({
    queryKey: adminCommercialKeys.plans.list(),
    queryFn: adminApi.plans,
    enabled: commercialQueryEnabled(session.can, adminPermissions.plansList, open),
  });
  const addOnOptions = useQuery({
    queryKey: adminCommercialKeys.addOns.list(),
    queryFn: adminApi.addOns,
    enabled: commercialQueryEnabled(session.can, adminPermissions.addOnsList, open),
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
  const [termsTouched, setTermsTouched] = useState(Boolean(item));
  useEffect(() => {
    if (item || !open || termsTouched) return;
    const firstActivePlan = planOptions.data?.find((plan) => plan.status === "ACTIVE");
    if (!firstActivePlan) return;
    setCurrency(firstActivePlan.currencyCode);
    setCycle(firstActivePlan.billingCycle);
  }, [item, open, planOptions.data, termsTouched]);
  const planCompatibility = new Map(
    (planOptions.data ?? []).map((plan) => [plan.code, addOnPlanCompatibilityIssue(currency, cycle, plan)]),
  );
  const incompatibleAllowedPlan = allowed
    .map((code) => (planOptions.data ?? []).find((plan) => plan.code === code))
    .find((plan) => plan && addOnPlanCompatibilityIssue(currency, cycle, plan));
  const overlappingPlan = allowed.find((code) => blocked.includes(code));
  const overlappingRelation = dependencies.find((code) => exclusions.includes(code));
  const formBlocker = incompatibleAllowedPlan
    ? planCompatibilityMessage(
        addOnPlanCompatibilityIssue(currency, cycle, incompatibleAllowedPlan) as AddOnPlanCompatibilityIssue,
        incompatibleAllowedPlan.name,
      )
    : overlappingPlan
      ? "Un forfait ne peut pas être autorisé et bloqué en même temps."
      : overlappingRelation
        ? "Un add-on ne peut pas être requis et incompatible en même temps."
        : null;
  const input = {
    name,
    description,
    price: Number(amount),
    currencyCode: currency,
    billingCycle: cycle,
    allowedPlanCodes: allowed,
    blockedPlanCodes: blocked,
    dependencyCodes: dependencies,
    exclusionCodes: exclusions,
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
            if (formBlocker) return;
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
            <Input
              min="0"
              onChange={(event) => setAmount(event.target.value)}
              step="0.01"
              type="number"
              value={amount}
            />
          </Field>
          <Field label="Devise">
            <Input
              maxLength={3}
              onChange={(event) => {
                setTermsTouched(true);
                setCurrency(event.target.value.toUpperCase());
              }}
              required
              value={currency}
            />
          </Field>
          <Field label="Cycle">
            <Select
              onValueChange={(value) => {
                setTermsTouched(true);
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
          <ChoiceList
            label="Forfaits autorisés"
            onChange={setAllowed}
            options={(planOptions.data ?? []).map((plan) => {
              const issue = planCompatibility.get(plan.code);
              return {
                label: plan.name,
                value: plan.code,
                description: `${plan.currencyCode} · ${cycleLabel(plan.billingCycle)}${issue ? ` · ${planCompatibilityMessage(issue, plan.name)}` : ""}`,
                disabled: (Boolean(issue) || blocked.includes(plan.code)) && !allowed.includes(plan.code),
              };
            })}
            selected={allowed}
          />
          <ChoiceList
            label="Forfaits bloqués"
            onChange={setBlocked}
            options={(planOptions.data ?? []).map((plan) => ({
              label: plan.name,
              value: plan.code,
              description: `${plan.currencyCode} · ${cycleLabel(plan.billingCycle)}`,
              disabled: allowed.includes(plan.code) && !blocked.includes(plan.code),
            }))}
            selected={blocked}
          />
          <ChoiceList
            label="Dépendances"
            onChange={setDependencies}
            options={(addOnOptions.data ?? [])
              .filter((candidate) => candidate.id !== item?.id)
              .map((candidate) => ({
                label: candidate.name,
                value: candidate.code,
                disabled: exclusions.includes(candidate.code) && !dependencies.includes(candidate.code),
              }))}
            selected={dependencies}
          />
          <ChoiceList
            label="Incompatible avec"
            onChange={setExclusions}
            options={(addOnOptions.data ?? [])
              .filter((candidate) => candidate.id !== item?.id)
              .map((candidate) => ({
                label: candidate.name,
                value: candidate.code,
                disabled: dependencies.includes(candidate.code) && !exclusions.includes(candidate.code),
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
            <Button disabled={save.isPending || Boolean(formBlocker)} type="submit">
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
        ? adminApi.updateAddOnFeature(addOn.id, item.id, input)
        : adminApi.assignAddOnFeature(addOn.id, input);
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

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  const id = useId();
  const control = isValidElement(children)
    ? cloneElement(children as React.ReactElement<{ id?: string }>, { id })
    : children;
  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      {control}
    </div>
  );
}

function ChoiceList({
  label,
  options,
  selected,
  onChange,
}: {
  label: string;
  options: Array<{ label: string; value: string; description?: string; disabled?: boolean }>;
  selected: string[];
  onChange: (values: string[]) => void;
}) {
  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium">{label}</legend>
      <div className="max-h-36 divide-y overflow-y-auto rounded-md border">
        {options.length ? (
          options.map((option) => {
            const id = `${label}-${option.value}`.replaceAll(" ", "-");
            return (
              <label
                className={`flex items-center gap-3 px-3 py-2.5 text-sm ${option.disabled ? "cursor-not-allowed text-muted-foreground" : "cursor-pointer"}`}
                htmlFor={id}
                key={option.value}
              >
                <Checkbox
                  checked={selected.includes(option.value)}
                  disabled={option.disabled}
                  id={id}
                  onCheckedChange={(checked) =>
                    onChange(
                      checked
                        ? [...new Set([...selected, option.value])]
                        : selected.filter((value) => value !== option.value),
                    )
                  }
                />
                <span className="min-w-0">
                  <span className="block">{option.label}</span>
                  {option.description ? (
                    <span className="block text-xs text-muted-foreground">{option.description}</span>
                  ) : null}
                </span>
              </label>
            );
          })
        ) : (
          <p className="px-3 py-2.5 text-sm text-muted-foreground">Aucun choix disponible</p>
        )}
      </div>
    </fieldset>
  );
}

export function AdminAddOnsPage() {
  const { addOnId } = useParams();
  const [search, setSearch] = useState("");
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const session = useAdminSession();
  const canListAddOns = session.can(adminPermissions.addOnsList);
  const canListPlans = session.can(adminPermissions.plansList);
  const canReadRegistryCatalog = session.can(adminPermissions.registryFeatureCatalog);
  const items = useQuery({
    queryKey: adminCommercialKeys.addOns.list(),
    queryFn: adminApi.addOns,
    enabled: commercialQueryEnabled(session.can, adminPermissions.addOnsList, !addOnId),
  });
  const detail = useQuery({
    queryKey: adminCommercialKeys.addOns.detail(addOnId ?? ""),
    queryFn: () => adminApi.addOn(addOnId ?? ""),
    enabled: commercialQueryEnabled(session.can, adminPermissions.addOnsReadDetail, Boolean(addOnId)),
  });
  const plans = useQuery({
    queryKey: adminCommercialKeys.plans.list(),
    queryFn: adminApi.plans,
    enabled: commercialQueryEnabled(session.can, adminPermissions.plansList, Boolean(addOnId)),
  });
  const registry = useQuery({
    queryKey: adminCommercialKeys.registry.featureCatalog("PLAN_ASSIGNABLE"),
    queryFn: () => adminApi.featureCatalog("PLAN_ASSIGNABLE"),
    enabled: commercialQueryEnabled(session.can, adminPermissions.registryFeatureCatalog, Boolean(addOnId)),
  });
  const selected = detail.data;
  const readableItems = canListAddOns ? (items.data ?? []) : [];
  const readablePlans = canListPlans ? (plans.data ?? []) : [];
  const readableRegistry = canReadRegistryCatalog ? (registry.data ?? []) : [];
  const planBlocker =
    selected && canListPlans && plans.isSuccess && (selected.status === "DRAFT" || selected.status === "INACTIVE")
      ? publicationPlanBlocker(selected, readablePlans)
      : undefined;
  const activeRevision = selected
    ? readableItems.find(
        (item) => item.lineageId === selected.lineageId && item.id !== selected.id && item.status === "ACTIVE",
      )
    : undefined;
  const filtered = useMemo(
    () => readableItems.filter((item) => item.name.toLowerCase().includes(search.toLowerCase())),
    [readableItems, search],
  );
  const planNames = new Map(readablePlans.map((plan) => [plan.code, plan.name]));
  const addOnNames = new Map(readableItems.map((item) => [item.code, item.name]));
  const featureNames = new Map(
    readableRegistry.flatMap((module) => module.features).map((feature) => [feature.code, feature.displayName]),
  );
  const transition = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => adminApi.transitionAddOn(id, status),
    onSuccess: (_, variables) => {
      void invalidateAdminCommercial(queryClient, adminCommercialKeys.addOns.all());
      toast.success(
        variables.status === "ACTIVE"
          ? selected?.status === "DRAFT"
            ? "Add-on publié"
            : "Add-on remis en vente"
          : variables.status === "INACTIVE"
            ? "Vente suspendue"
            : "Add-on archivé",
      );
    },
  });
  const revise = useMutation({
    mutationFn: (id: string) => adminApi.reviseAddOn(id),
    onSuccess: (revision) => {
      void invalidateAdminCommercial(queryClient, adminCommercialKeys.addOns.all());
      toast.success(`Révision R${revision.revisionNumber} créée`);
      navigate(`/admin/add-ons/${revision.id}`);
    },
  });
  const removeFeature = useMutation({
    mutationFn: ({ id, featureId }: { id: string; featureId: string }) => adminApi.removeAddOnFeature(id, featureId),
    onSuccess: () => {
      void invalidateAdminCommercial(queryClient, adminCommercialKeys.addOns.all());
      toast.success("Fonctionnalité retirée");
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => adminApi.deleteAddOn(id),
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
              {selected.status === "DRAFT" && session.can(adminPermissions.addOnsUpdate) ? (
                <AddOnForm item={selected} trigger={<Button variant="outline">Modifier</Button>} />
              ) : null}
              {selected.status === "DRAFT" && session.can(adminPermissions.addOnsDelete) ? (
                <DeleteDraftDialog
                  label="cet add-on"
                  name={selected.name}
                  onDelete={() => remove.mutate(selected.id)}
                  pending={remove.isPending}
                />
              ) : null}
              <AddOnLifecycleActions
                activeRevision={activeRevision}
                item={selected}
                revise={() => revise.mutateAsync(selected.id)}
                revisionPending={revise.isPending}
                publicationBlocker={planBlocker}
                transition={(status) => transition.mutateAsync({ id: selected.id, status })}
                transitionPending={transition.isPending}
              />
            </div>
          }
          description={
            <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
              <StatusBadge
                tone={selected.status === "ACTIVE" ? "success" : selected.status === "DRAFT" ? "info" : "warning"}
              >
                {addOnStatusLabel(selected.status)}
              </StatusBadge>
              {planBlocker ? (
                <span aria-live="polite" className="text-sm text-destructive" role="alert">
                  {planBlocker} Modifiez la compatibilité avant de publier.
                </span>
              ) : null}
            </div>
          }
          title={selected.name}
        />
        <div className="grid gap-5 lg:grid-cols-2">
          <section className="rounded-xl border bg-card p-5">
            <h2 className="text-sm font-semibold">Configuration</h2>
            <dl className="mt-5 space-y-4 text-sm">
              <Pair label="Prix" value={`${price(selected.price, selected.currencyCode)} / ${selected.billingCycle}`} />
              <Pair label="Révision" value={`R${selected.revisionNumber}`} />
              <Pair label="Fonctionnalités" value={selected.features.length} />
            </dl>
          </section>
          <section className="rounded-xl border bg-card p-5">
            <h2 className="text-sm font-semibold">Compatibilité</h2>
            <ReferenceSet
              label="Forfaits autorisés"
              values={selected.allowedPlanCodes.map((code) => planNames.get(code) ?? "Forfait indisponible")}
            />
            <ReferenceSet
              label="Forfaits bloqués"
              values={selected.blockedPlanCodes.map((code) => planNames.get(code) ?? "Forfait indisponible")}
            />
            <ReferenceSet
              label="Dépendances"
              values={selected.dependencyCodes.map((code) => addOnNames.get(code) ?? code)}
            />
            <ReferenceSet
              label="Exclusions"
              values={selected.exclusionCodes.map((code) => addOnNames.get(code) ?? code)}
            />
          </section>
        </div>
        <ProductPricePanel ownerId={selected.id} ownerType="ADD_ON" />
        <section className="overflow-hidden rounded-xl border bg-card">
          <div className="flex items-center justify-between border-b p-4">
            <div>
              <h2 className="text-sm font-semibold">Fonctionnalités</h2>
              <p className="mt-1 text-xs text-muted-foreground">{selected.features.length} configurées</p>
            </div>
            {selected.status === "DRAFT" ? <AddOnFeatureDialog addOn={selected} /> : null}
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
                            .map((quota) => `${quota.resource}: ${quota.mode === "UNLIMITED" ? "∞" : quota.limit}`)
                            .join(" · ")
                        : "—"}
                    </TableCell>
                    <TableCell>
                      {selected.status === "DRAFT" ? (
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
      </div>
    );
  return (
    <div className="space-y-7">
      <PageHeader
        actions={
          session.can(adminPermissions.addOnsCreate) ? (
            <AddOnForm
              trigger={
                <Button>
                  <PlusIcon />
                  Créer un add-on
                </Button>
              }
            />
          ) : undefined
        }
        title="Add-ons"
      />
      <section className="overflow-hidden rounded-xl border bg-card">
        <div className="relative border-b p-4 sm:max-w-md">
          <MagnifyingGlassIcon
            aria-hidden="true"
            className="absolute start-7 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
          />
          <Input
            aria-label="Rechercher des add-ons"
            className="ps-9"
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Rechercher par nom…"
            value={search}
          />
        </div>
        {items.isLoading ? (
          <div className="p-5">
            <LoadingState />
          </div>
        ) : items.isError ? (
          <ErrorState retry={() => void items.refetch()} />
        ) : !filtered.length ? (
          <EmptyState title="Aucun add-on" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Add-on</TableHead>
                <TableHead>Prix</TableHead>
                <TableHead>Compatibilité</TableHead>
                <TableHead>Statut</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((item) => (
                <TableRow key={item.id}>
                  <TableCell>
                    <Link className="block" to={`/admin/add-ons/${item.id}`}>
                      <span className="font-medium">{item.name}</span>
                      <span className="ms-2 text-xs tabular-nums text-muted-foreground">R{item.revisionNumber}</span>
                    </Link>
                  </TableCell>
                  <TableCell>{price(item.price, item.currencyCode)}</TableCell>
                  <TableCell>
                    {item.allowedPlanCodes.length ? `${item.allowedPlanCodes.length} forfaits` : "Tous"}
                  </TableCell>
                  <TableCell>
                    <StatusBadge
                      tone={item.status === "ACTIVE" ? "success" : item.status === "DRAFT" ? "info" : "warning"}
                    >
                      {addOnStatusLabel(item.status)}
                    </StatusBadge>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </section>
    </div>
  );
}

function QuotaForm({ item, trigger }: { item?: QuotaPackage; trigger: React.ReactNode }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const planOptions = useQuery({
    queryKey: adminCommercialKeys.plans.list(),
    queryFn: adminApi.plans,
    enabled: commercialQueryEnabled(session.can, adminPermissions.plansList, open),
  });
  const addOnOptions = useQuery({
    queryKey: adminCommercialKeys.addOns.list(),
    queryFn: adminApi.addOns,
    enabled: commercialQueryEnabled(session.can, adminPermissions.addOnsList, open),
  });
  const registry = useQuery({
    queryKey: adminCommercialKeys.registry.featureCatalog("PLAN_ASSIGNABLE"),
    queryFn: () => adminApi.featureCatalog("PLAN_ASSIGNABLE"),
    enabled: commercialQueryEnabled(session.can, adminPermissions.registryFeatureCatalog, open),
  });
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
  const featureOptions = (registry.data ?? [])
    .flatMap((module) => module.features)
    .filter((feature) => feature.quotaSchema.length > 0);
  const selectedFeature = featureOptions.find((feature) => feature.code === featureCode);
  const input = {
    name,
    description,
    featureCode,
    resource,
    capacityPerUnit: Number(capacity),
    price: Number(amount),
    currencyCode: currency,
    billingCycle: cycle,
    repeatable,
    maximumQuantity: Number(maximum),
    allowedPlanCodes: plans,
    allowedAddOnCodes: addons,
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
            <Select disabled={!selectedFeature} onValueChange={setResource} value={resource}>
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
          <Field label="Capacité par unité">
            <Input min="1" onChange={(event) => setCapacity(event.target.value)} type="number" value={capacity} />
          </Field>
          <Field label="Quantité maximale">
            <Input min="1" onChange={(event) => setMaximum(event.target.value)} type="number" value={maximum} />
          </Field>
          <Field label="Prix">
            <Input
              min="0"
              onChange={(event) => setAmount(event.target.value)}
              step="0.01"
              type="number"
              value={amount}
            />
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
                <SelectItem value="FOREVER">Permanent</SelectItem>
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
          <ChoiceList
            label="Forfaits autorisés"
            onChange={setPlans}
            options={(planOptions.data ?? []).map((plan) => ({ label: plan.name, value: plan.code }))}
            selected={plans}
          />
          <ChoiceList
            label="Add-ons autorisés"
            onChange={setAddons}
            options={(addOnOptions.data ?? []).map((addOn) => ({ label: addOn.name, value: addOn.code }))}
            selected={addons}
          />
          <div className="flex justify-end gap-2 sm:col-span-2">
            <Button onClick={() => setOpen(false)} type="button" variant="outline">
              Annuler
            </Button>
            <Button disabled={save.isPending} type="submit">
              Enregistrer
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export function AdminQuotaPackagesPage() {
  const { packageId } = useParams();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const session = useAdminSession();
  const canListQuotaPackages = session.can(adminPermissions.quotaPackagesList);
  const canReadRegistryCatalog = session.can(adminPermissions.registryFeatureCatalog);
  const [search, setSearch] = useState("");
  const items = useQuery({
    queryKey: adminCommercialKeys.quotaPackages.list(),
    queryFn: adminApi.quotaPackages,
    enabled: commercialQueryEnabled(session.can, adminPermissions.quotaPackagesList, !packageId),
  });
  const detail = useQuery({
    queryKey: adminCommercialKeys.quotaPackages.detail(packageId ?? ""),
    queryFn: () => adminApi.quotaPackage(packageId ?? ""),
    enabled: commercialQueryEnabled(session.can, adminPermissions.quotaPackagesReadDetail, Boolean(packageId)),
  });
  const registry = useQuery({
    queryKey: adminCommercialKeys.registry.featureCatalog("PLAN_ASSIGNABLE"),
    queryFn: () => adminApi.featureCatalog("PLAN_ASSIGNABLE"),
    enabled: commercialQueryEnabled(session.can, adminPermissions.registryFeatureCatalog),
  });
  const selected = detail.data;
  const registryFeatures = (canReadRegistryCatalog ? (registry.data ?? []) : []).flatMap((module) => module.features);
  const featureNames = new Map(registryFeatures.map((feature) => [feature.code, feature.displayName]));
  const resourceUnits = new Map(
    registryFeatures.flatMap((feature) =>
      feature.quotaSchema.map((slot) => [`${feature.code}:${slot.resource}`, slot.unit] as const),
    ),
  );
  const filtered = useMemo(
    () =>
      (canListQuotaPackages ? (items.data ?? []) : []).filter((item) =>
        item.name.toLowerCase().includes(search.toLowerCase()),
      ),
    [canListQuotaPackages, items.data, search],
  );
  const transition = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => adminApi.transitionQuotaPackage(id, status),
    onSuccess: () => {
      void invalidateAdminCommercial(queryClient, adminCommercialKeys.quotaPackages.all());
      toast.success("Statut mis à jour");
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => adminApi.deleteQuotaPackage(id),
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
              {selected.status === "DRAFT" && session.can(adminPermissions.quotaPackagesUpdate) ? (
                <QuotaForm item={selected} trigger={<Button variant="outline">Modifier</Button>} />
              ) : null}
              {selected.status === "DRAFT" && session.can(adminPermissions.quotaPackagesDelete) ? (
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
        <div className="grid gap-5 lg:grid-cols-2">
          <section className="rounded-xl border bg-card p-5">
            <h2 className="text-sm font-semibold">Capacité</h2>
            <dl className="mt-5 space-y-4 text-sm">
              <Pair label="Fonctionnalité" value={featureNames.get(selected.featureCode) ?? "Indisponible"} />
              <Pair
                label="Ressource"
                value={resourceUnits.get(`${selected.featureCode}:${selected.resource}`) ?? "Indisponible"}
              />
              <Pair label="Par unité" value={selected.capacityPerUnit} />
              <Pair label="Maximum" value={selected.maximumQuantity} />
            </dl>
          </section>
          <section className="rounded-xl border bg-card p-5">
            <h2 className="text-sm font-semibold">Commercial</h2>
            <dl className="mt-5 space-y-4 text-sm">
              <Pair label="Prix" value={price(selected.price, selected.currencyCode)} />
              <Pair label="Cycle" value={selected.billingCycle} />
              <Pair label="Répétable" value={selected.repeatable ? "Oui" : "Non"} />
              <Pair label="Version" value={selected.definitionVersion} />
            </dl>
          </section>
        </div>
        <ProductPricePanel ownerId={selected.id} ownerType="QUOTA_PACKAGE" />
        {session.can(adminPermissions.quotaPackagesTransition) ? (
          <section className="rounded-xl border bg-card p-5">
            <h2 className="text-sm font-semibold">Cycle de vie</h2>
            <div className="mt-4">
              <SimpleLifecycleActions
                pending={transition.isPending}
                status={selected.status}
                transition={(status) => transition.mutateAsync({ id: selected.id, status })}
              />
            </div>
          </section>
        ) : null}
      </div>
    );
  return (
    <div className="space-y-7">
      <PageHeader
        actions={
          session.can(adminPermissions.quotaPackagesCreate) ? (
            <QuotaForm
              trigger={
                <Button>
                  <PlusIcon />
                  Créer un package
                </Button>
              }
            />
          ) : undefined
        }
        title="Packages de quota"
      />
      <section className="overflow-hidden rounded-xl border bg-card">
        <div className="relative border-b p-4 sm:max-w-md">
          <MagnifyingGlassIcon
            aria-hidden="true"
            className="absolute start-7 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
          />
          <Input
            aria-label="Rechercher des packages de quota"
            className="ps-9"
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Rechercher par nom…"
            value={search}
          />
        </div>
        {items.isLoading ? (
          <div className="p-5">
            <LoadingState />
          </div>
        ) : items.isError ? (
          <ErrorState retry={() => void items.refetch()} />
        ) : !filtered.length ? (
          <EmptyState title="Aucun package" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Package</TableHead>
                <TableHead>Ressource</TableHead>
                <TableHead>Capacité</TableHead>
                <TableHead>Prix</TableHead>
                <TableHead>Statut</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((item) => (
                <TableRow key={item.id}>
                  <TableCell>
                    <Link className="block" to={`/admin/quota-packages/${item.id}`}>
                      <span className="font-medium">{item.name}</span>
                    </Link>
                  </TableCell>
                  <TableCell>{resourceUnits.get(`${item.featureCode}:${item.resource}`) ?? "Indisponible"}</TableCell>
                  <TableCell className="tabular-nums">{item.capacityPerUnit}</TableCell>
                  <TableCell>{price(item.price, item.currencyCode)}</TableCell>
                  <TableCell>
                    <StatusBadge
                      tone={item.status === "ACTIVE" ? "success" : item.status === "DRAFT" ? "info" : "warning"}
                    >
                      {addOnStatusLabel(item.status)}
                    </StatusBadge>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </section>
    </div>
  );
}

function Pair({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="text-end font-medium">{value}</dd>
    </div>
  );
}
function ReferenceSet({ label, values }: { label: string; values: string[] }) {
  return (
    <div className="mt-4">
      <p className="text-xs text-muted-foreground">{label}</p>
      <div className="mt-2 flex flex-wrap gap-1.5">
        {values.length ? (
          values.map((value) => (
            <span className="rounded bg-muted px-2 py-1 text-xs" key={value}>
              {value}
            </span>
          ))
        ) : (
          <span className="text-sm">Aucune restriction</span>
        )}
      </div>
    </div>
  );
}
