import { ArrowLeftIcon, MagnifyingGlassIcon, PencilSimpleIcon, PlusIcon, TrashIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { cloneElement, type FormEvent, isValidElement, useId, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { AddOn, BillingCycle, QuotaLimit, QuotaPackage } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { QuotaEditor } from "@/components/patterns/quota-editor";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
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

const splitCodes = (value: string) =>
  value
    .split(",")
    .map((item) => item.trim().toUpperCase())
    .filter(Boolean);
const joinCodes = (value: string[]) => value.join(", ");
const price = (value: number, currency: string) =>
  new Intl.NumberFormat("fr-MA", { style: "currency", currency }).format(value);

function Lifecycle({
  status,
  mutate,
  pending,
}: {
  status: string;
  mutate: (status: string) => void;
  pending: boolean;
}) {
  return (
    <div className="flex flex-wrap gap-2">
      {["DRAFT", "ACTIVE", "INACTIVE", "ARCHIVED"]
        .filter((value) => value !== status)
        .map((value) => (
          <Button
            disabled={pending}
            key={value}
            onClick={() => mutate(value)}
            size="sm"
            variant={value === "ARCHIVED" ? "destructive" : "outline"}
          >
            {value}
          </Button>
        ))}
    </div>
  );
}

function DeleteDraftDialog({
  code,
  label,
  onDelete,
  pending,
}: {
  code: string;
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
            Seuls les brouillons inutilisés peuvent être supprimés. Saisissez le code pour confirmer.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <Input
            aria-label="Code de confirmation"
            onChange={(event) => setConfirmation(event.target.value)}
            value={confirmation}
          />
          <div className="flex justify-end">
            <Button disabled={confirmation !== code || pending} onClick={onDelete} variant="destructive">
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
  const [code, setCode] = useState(item?.code ?? "");
  const [name, setName] = useState(item?.name ?? "");
  const [description, setDescription] = useState(item?.description ?? "");
  const [amount, setAmount] = useState(String(item?.price ?? 0));
  const [currency, setCurrency] = useState(item?.currencyCode ?? "MAD");
  const [cycle, setCycle] = useState<BillingCycle>(item?.billingCycle ?? "MONTHLY");
  const [allowed, setAllowed] = useState(joinCodes(item?.allowedPlanCodes ?? []));
  const [blocked, setBlocked] = useState(joinCodes(item?.blockedPlanCodes ?? []));
  const [dependencies, setDependencies] = useState(joinCodes(item?.dependencyCodes ?? []));
  const [exclusions, setExclusions] = useState(joinCodes(item?.exclusionCodes ?? []));
  const input = {
    ...(item ? {} : { code }),
    name,
    description,
    price: Number(amount),
    currencyCode: currency,
    billingCycle: cycle,
    allowedPlanCodes: splitCodes(allowed),
    blockedPlanCodes: splitCodes(blocked),
    dependencyCodes: splitCodes(dependencies),
    exclusionCodes: splitCodes(exclusions),
  };
  const save = useMutation({
    mutationFn: () => (item ? adminApi.updateAddOn(item.id, input) : adminApi.createAddOn(input)),
    onSuccess: (saved) => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "add-ons"] });
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
            La compatibilité commerciale est définie par codes de forfait, dépendances et exclusions.
          </DialogDescription>
        </DialogHeader>
        <form
          className="grid gap-4 sm:grid-cols-2"
          onSubmit={(event: FormEvent) => {
            event.preventDefault();
            save.mutate();
          }}
        >
          {!item ? (
            <Field label="Code">
              <Input onChange={(event) => setCode(event.target.value.toUpperCase())} required value={code} />
            </Field>
          ) : null}
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
          <Field label="Forfaits autorisés">
            <Input onChange={(event) => setAllowed(event.target.value)} placeholder="PRO, BUSINESS" value={allowed} />
          </Field>
          <Field label="Forfaits bloqués">
            <Input onChange={(event) => setBlocked(event.target.value)} value={blocked} />
          </Field>
          <Field label="Dépendances">
            <Input onChange={(event) => setDependencies(event.target.value)} value={dependencies} />
          </Field>
          <Field label="Exclusions">
            <Input onChange={(event) => setExclusions(event.target.value)} value={exclusions} />
          </Field>
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

function AddOnFeatureDialog({ item, addOn }: { item?: AddOn["features"][number]; addOn: AddOn }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [featureCode, setFeatureCode] = useState(item?.featureCode ?? "");
  const [quotas, setQuotas] = useState<QuotaLimit[]>(item?.quotaConfigs ?? []);
  const catalog = useQuery({ queryKey: ["admin", "registry", "add-on-features"], queryFn: adminApi.registryInventory });
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
      void queryClient.invalidateQueries({ queryKey: ["admin", "add-ons"] });
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

export function AdminAddOnsPage() {
  const { addOnId } = useParams();
  const [search, setSearch] = useState("");
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const session = useAdminSession();
  const items = useQuery({ queryKey: ["admin", "add-ons"], queryFn: adminApi.addOns });
  const selected = items.data?.find((item) => item.id === addOnId);
  const filtered = useMemo(
    () => (items.data ?? []).filter((item) => `${item.code} ${item.name}`.toLowerCase().includes(search.toLowerCase())),
    [items.data, search],
  );
  const transition = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => adminApi.transitionAddOn(id, status),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "add-ons"] });
      toast.success("Statut mis à jour");
    },
  });
  const removeFeature = useMutation({
    mutationFn: ({ id, featureId }: { id: string; featureId: string }) => adminApi.removeAddOnFeature(id, featureId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "add-ons"] });
      toast.success("Fonctionnalité retirée");
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => adminApi.deleteAddOn(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "add-ons"] });
      toast.success("Brouillon supprimé");
      navigate("/admin/add-ons");
    },
  });
  if (addOnId && items.isLoading) return <LoadingState />;
  if (addOnId && !selected && !items.isLoading) return <ErrorState title="Add-on introuvable" />;
  if (selected)
    return (
      <div className="space-y-7">
        <Button asChild size="sm" variant="ghost">
          <Link to="/admin/add-ons">
            <ArrowLeftIcon className="rtl:rotate-180" />
            Add-ons
          </Link>
        </Button>
        <PageHeader
          actions={
            <div className="flex gap-2">
              {selected.status === "DRAFT" && session.can(adminPermissions.addOnsDelete) ? (
                <AddOnForm item={selected} trigger={<Button variant="outline">Modifier</Button>} />
              ) : null}
              {selected.status === "DRAFT" ? (
                <DeleteDraftDialog
                  code={selected.code}
                  label="cet add-on"
                  onDelete={() => remove.mutate(selected.id)}
                  pending={remove.isPending}
                />
              ) : null}
            </div>
          }
          description={
            <span className="flex gap-2">
              <code>{selected.code}</code>
              <StatusBadge
                tone={selected.status === "ACTIVE" ? "success" : selected.status === "DRAFT" ? "info" : "warning"}
              >
                {selected.status}
              </StatusBadge>
            </span>
          }
          title={selected.name}
        />
        <div className="grid gap-5 lg:grid-cols-2">
          <section className="rounded-xl border bg-card p-5">
            <h2 className="text-sm font-semibold">Configuration</h2>
            <dl className="mt-5 space-y-4 text-sm">
              <Pair label="Prix" value={`${price(selected.price, selected.currencyCode)} / ${selected.billingCycle}`} />
              <Pair label="Version" value={selected.definitionVersion} />
              <Pair label="Fonctionnalités" value={selected.features.length} />
            </dl>
          </section>
          <section className="rounded-xl border bg-card p-5">
            <h2 className="text-sm font-semibold">Compatibilité</h2>
            <CodeSet label="Forfaits autorisés" values={selected.allowedPlanCodes} />
            <CodeSet label="Forfaits bloqués" values={selected.blockedPlanCodes} />
            <CodeSet label="Dépendances" values={selected.dependencyCodes} />
            <CodeSet label="Exclusions" values={selected.exclusionCodes} />
          </section>
        </div>
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
                      <code className="text-xs">{feature.featureCode}</code>
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
        {session.can(adminPermissions.addOnsTransition) ? (
          <section className="rounded-xl border bg-card p-5">
            <h2 className="text-sm font-semibold">Cycle de vie</h2>
            <div className="mt-4">
              <Lifecycle
                mutate={(status) => transition.mutate({ id: selected.id, status })}
                pending={transition.isPending}
                status={selected.status}
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
          <MagnifyingGlassIcon className="absolute start-7 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="ps-9"
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Code ou nom…"
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
                      <code className="mt-1 block text-xs text-muted-foreground">{item.code}</code>
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
                      {item.status}
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
  const [code, setCode] = useState(item?.code ?? "");
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
  const [plans, setPlans] = useState(joinCodes(item?.allowedPlanCodes ?? []));
  const [addons, setAddons] = useState(joinCodes(item?.allowedAddOnCodes ?? []));
  const input = {
    ...(item ? {} : { code }),
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
    allowedPlanCodes: splitCodes(plans),
    allowedAddOnCodes: splitCodes(addons),
  };
  const save = useMutation({
    mutationFn: () => (item ? adminApi.updateQuotaPackage(item.id, input) : adminApi.createQuotaPackage(input)),
    onSuccess: (saved) => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "quota-packages"] });
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
          {!item ? (
            <Field label="Code">
              <Input onChange={(event) => setCode(event.target.value.toUpperCase())} required value={code} />
            </Field>
          ) : null}
          <Field label="Nom">
            <Input onChange={(event) => setName(event.target.value)} required value={name} />
          </Field>
          <div className="sm:col-span-2">
            <Field label="Description">
              <Textarea onChange={(event) => setDescription(event.target.value)} value={description} />
            </Field>
          </div>
          <Field label="Code fonctionnalité">
            <Input onChange={(event) => setFeatureCode(event.target.value)} required value={featureCode} />
          </Field>
          <Field label="Ressource">
            <Input onChange={(event) => setResource(event.target.value)} required value={resource} />
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
          <Field label="Forfaits autorisés">
            <Input onChange={(event) => setPlans(event.target.value)} value={plans} />
          </Field>
          <Field label="Add-ons autorisés">
            <Input onChange={(event) => setAddons(event.target.value)} value={addons} />
          </Field>
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
  const [search, setSearch] = useState("");
  const items = useQuery({ queryKey: ["admin", "quota-packages"], queryFn: adminApi.quotaPackages });
  const selected = items.data?.find((item) => item.id === packageId);
  const filtered = useMemo(
    () =>
      (items.data ?? []).filter((item) =>
        `${item.code} ${item.name} ${item.featureCode}`.toLowerCase().includes(search.toLowerCase()),
      ),
    [items.data, search],
  );
  const transition = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => adminApi.transitionQuotaPackage(id, status),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "quota-packages"] });
      toast.success("Statut mis à jour");
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => adminApi.deleteQuotaPackage(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "quota-packages"] });
      toast.success("Brouillon supprimé");
      navigate("/admin/quota-packages");
    },
  });
  if (packageId && items.isLoading) return <LoadingState />;
  if (packageId && !selected && !items.isLoading) return <ErrorState title="Package introuvable" />;
  if (selected)
    return (
      <div className="space-y-7">
        <Button asChild size="sm" variant="ghost">
          <Link to="/admin/quota-packages">
            <ArrowLeftIcon className="rtl:rotate-180" />
            Packages de quota
          </Link>
        </Button>
        <PageHeader
          actions={
            <div className="flex gap-2">
              {selected.status === "DRAFT" && session.can(adminPermissions.quotaPackagesDelete) ? (
                <QuotaForm item={selected} trigger={<Button variant="outline">Modifier</Button>} />
              ) : null}
              {selected.status === "DRAFT" ? (
                <DeleteDraftDialog
                  code={selected.code}
                  label="ce package"
                  onDelete={() => remove.mutate(selected.id)}
                  pending={remove.isPending}
                />
              ) : null}
            </div>
          }
          description={
            <span className="flex gap-2">
              <code>{selected.code}</code>
              <StatusBadge
                tone={selected.status === "ACTIVE" ? "success" : selected.status === "DRAFT" ? "info" : "warning"}
              >
                {selected.status}
              </StatusBadge>
            </span>
          }
          title={selected.name}
        />
        <div className="grid gap-5 lg:grid-cols-2">
          <section className="rounded-xl border bg-card p-5">
            <h2 className="text-sm font-semibold">Capacité</h2>
            <dl className="mt-5 space-y-4 text-sm">
              <Pair label="Fonctionnalité" value={selected.featureCode} />
              <Pair label="Ressource" value={selected.resource} />
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
        {session.can(adminPermissions.quotaPackagesTransition) ? (
          <section className="rounded-xl border bg-card p-5">
            <h2 className="text-sm font-semibold">Cycle de vie</h2>
            <div className="mt-4">
              <Lifecycle
                mutate={(status) => transition.mutate({ id: selected.id, status })}
                pending={transition.isPending}
                status={selected.status}
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
          <MagnifyingGlassIcon className="absolute start-7 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="ps-9"
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Code, nom ou fonctionnalité…"
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
                      <code className="mt-1 block text-xs text-muted-foreground">{item.code}</code>
                    </Link>
                  </TableCell>
                  <TableCell>
                    <code className="text-xs">{item.resource}</code>
                  </TableCell>
                  <TableCell className="tabular-nums">{item.capacityPerUnit}</TableCell>
                  <TableCell>{price(item.price, item.currencyCode)}</TableCell>
                  <TableCell>
                    <StatusBadge
                      tone={item.status === "ACTIVE" ? "success" : item.status === "DRAFT" ? "info" : "warning"}
                    >
                      {item.status}
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
function CodeSet({ label, values }: { label: string; values: string[] }) {
  return (
    <div className="mt-4">
      <p className="text-xs text-muted-foreground">{label}</p>
      <div className="mt-2 flex flex-wrap gap-1.5">
        {values.length ? (
          values.map((value) => (
            <code className="rounded bg-muted px-2 py-1 text-xs" key={value}>
              {value}
            </code>
          ))
        ) : (
          <span className="text-sm">Aucune restriction</span>
        )}
      </div>
    </div>
  );
}
