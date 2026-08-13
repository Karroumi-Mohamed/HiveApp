import {
  ArrowLeftIcon,
  CopyIcon,
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
import type { BillingCycle, Plan, PlanFeature, PlanStatus, QuotaLimit, RegistryFeature } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { QuotaEditor } from "@/components/patterns/quota-editor";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { SectionTabs } from "@/components/patterns/section-tabs";
import { StatusBadge, type StatusTone } from "@/components/patterns/status-badge";
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

const planTone: Record<PlanStatus, StatusTone> = {
  DRAFT: "info",
  ACTIVE: "success",
  INACTIVE: "warning",
  ARCHIVED: "neutral",
};
const statusText: Record<PlanStatus, string> = {
  DRAFT: "Brouillon",
  ACTIVE: "Actif",
  INACTIVE: "Inactif",
  ARCHIVED: "Archivé",
};
const cycleText: Record<BillingCycle, string> = { MONTHLY: "mois", YEARLY: "an", FOREVER: "à vie" };
const money = (value: number, currency: string) =>
  new Intl.NumberFormat("fr-MA", { style: "currency", currency }).format(value);

function PlanFormDialog({
  source,
  mode = "create",
  trigger,
}: {
  source?: Plan;
  mode?: "create" | "edit" | "duplicate" | "revise";
  trigger: React.ReactNode;
}) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [code, setCode] = useState(
    mode === "edit"
      ? (source?.code ?? "")
      : source
        ? `${source.code}_${mode === "revise" ? `R${source.revisionNumber + 1}` : "COPY"}`
        : "",
  );
  const [name, setName] = useState(source?.name ?? "");
  const [description, setDescription] = useState(source?.description ?? "");
  const [price, setPrice] = useState(String(source?.price ?? 0));
  const [currencyCode, setCurrency] = useState(source?.currencyCode ?? "MAD");
  const [billingCycle, setCycle] = useState<BillingCycle>(source?.billingCycle ?? "MONTHLY");
  const save = useMutation({
    mutationFn: () => {
      const input = {
        ...(mode === "edit" ? {} : { code }),
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
      <DialogTrigger asChild>{trigger}</DialogTrigger>
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
          <DialogDescription>Les nouveaux forfaits et branches commencent en brouillon.</DialogDescription>
        </DialogHeader>
        <form
          className="grid gap-5 sm:grid-cols-2"
          onSubmit={(event: FormEvent) => {
            event.preventDefault();
            save.mutate();
          }}
        >
          {mode !== "edit" ? (
            <div className="space-y-2">
              <Label htmlFor="plan-code">Code</Label>
              <Input
                id="plan-code"
                onChange={(event) => setCode(event.target.value.toUpperCase())}
                required
                value={code}
              />
            </div>
          ) : null}
          <div className="space-y-2">
            <Label htmlFor="plan-name">Nom</Label>
            <Input id="plan-name" onChange={(event) => setName(event.target.value)} required value={name} />
          </div>
          <div className="space-y-2 sm:col-span-2">
            <Label htmlFor="plan-description">Description</Label>
            <Textarea
              id="plan-description"
              onChange={(event) => setDescription(event.target.value)}
              value={description}
            />
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
          </div>
          <div className="space-y-2">
            <Label htmlFor="plan-currency">Devise</Label>
            <Input
              id="plan-currency"
              maxLength={3}
              onChange={(event) => setCurrency(event.target.value.toUpperCase())}
              value={currencyCode}
            />
          </div>
          <div className="space-y-2">
            <Label>Cycle</Label>
            <Select onValueChange={(value) => setCycle(value as BillingCycle)} value={billingCycle}>
              <SelectTrigger aria-label="Cycle de facturation">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="MONTHLY">Mensuel</SelectItem>
                <SelectItem value="YEARLY">Annuel</SelectItem>
                <SelectItem value="FOREVER">Permanent</SelectItem>
              </SelectContent>
            </Select>
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
}: {
  plan: Plan;
  item?: PlanFeature;
  available: RegistryFeature[];
}) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
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
          <DialogDescription>Le mode et les quotas deviennent le contrat commercial du forfait.</DialogDescription>
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
            <Label>Mode</Label>
            <Select onValueChange={setMode} value={mode}>
              <SelectTrigger aria-label="Mode de la fonctionnalité">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="INCLUDED">Incluse</SelectItem>
                <SelectItem value="OPTIONAL_ADD_ON">Add-on optionnel</SelectItem>
                <SelectItem value="BLOCKED_FOR_PLAN">Bloquée</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label>Quotas de base</Label>
            <QuotaEditor
              disabled={mode !== "INCLUDED"}
              onChange={setQuotas}
              slots={definition?.quotaSchema ?? []}
              value={quotas}
            />
            {mode !== "INCLUDED" ? (
              <p className="text-xs text-muted-foreground">
                Seules les fonctionnalités incluses définissent des quotas de base.
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
  const features = useQuery({
    queryKey: ["admin", "plans", plan.id, "features"],
    queryFn: () => adminApi.planFeatures(plan.id),
  });
  const catalog = useQuery({ queryKey: ["admin", "registry", "plan-features"], queryFn: adminApi.registryInventory });
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
  const available = (catalog.data ?? [])
    .flatMap((module) => module.features)
    .filter((feature) => feature.planAssignable);
  const addable = available.filter(
    (feature) => !features.data?.some((assigned) => assigned.featureCode === feature.code),
  );
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <div className="flex items-center justify-between border-b p-4">
        <div>
          <h2 className="text-sm font-semibold">Composition</h2>
          <p className="mt-1 text-xs text-muted-foreground">{features.data?.length ?? 0} fonctionnalités configurées</p>
        </div>
        {plan.status === "DRAFT" ? <PlanFeatureDialog available={addable} plan={plan} /> : null}
      </div>
      {features.data?.length ? (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Fonctionnalité</TableHead>
              <TableHead>Mode</TableHead>
              <TableHead>Quotas</TableHead>
              <TableHead className="w-24" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {features.data.map((feature: PlanFeature) => (
              <TableRow key={feature.id}>
                <TableCell>
                  <code className="text-xs">{feature.featureCode}</code>
                </TableCell>
                <TableCell>{feature.mode}</TableCell>
                <TableCell>{feature.quotaConfigs.length || "—"}</TableCell>
                <TableCell>
                  {plan.status === "DRAFT" ? (
                    <div className="flex justify-end">
                      <PlanFeatureDialog available={available} item={feature} plan={plan} />
                      {session.can(adminPermissions.plansRemoveFeature) ? (
                        <Button
                          aria-label="Retirer"
                          onClick={() => remove.mutate(feature.id)}
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
        <EmptyState description="Ajoutez les capacités comprises dans ce forfait." title="Aucune fonctionnalité" />
      )}
    </section>
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
        confirmationCode: confirmation,
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
                <Label htmlFor="confirm-plan">Saisissez {preview.data.planCode}</Label>
                <Input
                  id="confirm-plan"
                  onChange={(event) => setConfirmation(event.target.value)}
                  value={confirmation}
                />
              </div>
            ) : null}
            <div className="flex justify-end">
              <Button
                disabled={!preview.data.deletable || confirmation !== preview.data.planCode || remove.isPending}
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
  return (
    <div className="space-y-7">
      <div>
        <Button asChild size="sm" variant="ghost">
          <Link to="/admin/plans">
            <ArrowLeftIcon className="rtl:rotate-180" />
            Forfaits
          </Link>
        </Button>
        <PageHeader
          actions={
            <>
              <PlanFormDialog mode="edit" source={data} trigger={<Button variant="outline">Modifier</Button>} />
              {data.status !== "DRAFT" && data.status !== "ARCHIVED" ? (
                <PlanFormDialog mode="revise" source={data} trigger={<Button variant="outline">Réviser</Button>} />
              ) : null}
              <PlanFormDialog
                mode="duplicate"
                source={data}
                trigger={
                  <Button variant="outline">
                    <CopyIcon />
                    Dupliquer
                  </Button>
                }
              />
            </>
          }
          description={
            <span className="flex items-center gap-2">
              <code>{data.code}</code>
              <StatusBadge tone={planTone[data.status]}>{statusText[data.status]}</StatusBadge>
            </span>
          }
          title={data.name}
        />
      </div>
      <SectionTabs
        ariaLabel="Sections du forfait"
        tabs={[
          { label: "Synthèse", to: `/admin/plans/${id}`, end: true },
          ...(session.can(adminPermissions.plansListFeatures)
            ? [{ label: "Fonctionnalités", to: `/admin/plans/${id}/features`, count: data.featureCount }]
            : []),
          ...(session.can(adminPermissions.plansListSubscribers)
            ? [{ label: "Abonnés", to: `/admin/plans/${id}/subscribers`, count: data.currentSubscriberCount }]
            : []),
          { label: "Cycle de vie", to: `/admin/plans/${id}/lifecycle` },
        ]}
      />
      {tab === "features" && session.can(adminPermissions.plansListFeatures) ? (
        <PlanFeatures plan={data} />
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
                <dd className="mt-1 font-medium">R{data.revisionNumber}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">Fonctionnalités</dt>
                <dd className="mt-1 font-medium">{data.featureCount}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">Quotas configurés</dt>
                <dd className="mt-1 font-medium">{data.quotaConfiguredFeatureCount}</dd>
              </div>
            </dl>
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
          (status === "all" || plan.status === status) &&
          `${plan.code} ${plan.name}`.toLowerCase().includes(search.toLowerCase()),
      ),
    [plans.data, search, status],
  );
  if (planId) return <PlanDetailPage id={planId} tab={tab} />;
  return (
    <div className="space-y-7">
      <PageHeader
        actions={
          session.can(adminPermissions.plansCreate) ? (
            <PlanFormDialog
              trigger={
                <Button>
                  <PlusIcon />
                  Créer un forfait
                </Button>
              }
            />
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
              placeholder="Code ou nom…"
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
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Forfait</TableHead>
                <TableHead>Prix</TableHead>
                <TableHead>Révision</TableHead>
                <TableHead>Statut</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((plan) => (
                <TableRow key={plan.id}>
                  <TableCell>
                    <Link
                      className="block focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                      to={`/admin/plans/${plan.id}`}
                    >
                      <span className="font-medium">{plan.name}</span>
                      <code className="mt-1 block text-xs text-muted-foreground">{plan.code}</code>
                    </Link>
                  </TableCell>
                  <TableCell>
                    {money(plan.price, plan.currencyCode)} / {cycleText[plan.billingCycle]}
                  </TableCell>
                  <TableCell>R{plan.revisionNumber}</TableCell>
                  <TableCell>
                    <StatusBadge tone={planTone[plan.status]}>{statusText[plan.status]}</StatusBadge>
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
