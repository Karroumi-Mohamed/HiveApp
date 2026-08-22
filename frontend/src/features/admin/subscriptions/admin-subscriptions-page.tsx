import { ArrowLeftIcon, MagnifyingGlassIcon } from "@phosphor-icons/react";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { type FormEvent, useDeferredValue, useEffect, useState } from "react";
import { Link, useParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { AdminSubscription, SubscriptionChangeOperation } from "@/api/contracts";
import { ApiError } from "@/api/http";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
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

const money = (value: number, currency: string) =>
  new Intl.NumberFormat("fr-MA", { style: "currency", currency }).format(value);
const date = (value: string | null) =>
  value ? new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium" }).format(new Date(value)) : "—";

function CreateSubscription({ accountId }: { accountId: string }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const plans = useQuery({ queryKey: ["admin", "plans"], queryFn: adminApi.plans });
  const [planCode, setPlanCode] = useState("");
  const [trialDays, setTrialDays] = useState("14");
  const [mode, setMode] = useState("subscription");
  const create = useMutation({
    mutationFn: () =>
      mode === "trial"
        ? adminApi.createTrial(accountId, planCode, Number(trialDays))
        : adminApi.createSubscription(accountId, planCode),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "subscription", accountId] });
      toast.success(mode === "trial" ? "Essai démarré" : "Abonnement créé");
    },
  });
  const activePlans = plans.data?.filter((plan) => plan.status === "ACTIVE") ?? [];
  return (
    <section className="max-w-2xl rounded-xl border bg-card p-5">
      <h2 className="text-base font-semibold">Aucun abonnement utilisable</h2>
      <p className="mt-1 text-sm text-muted-foreground">Affectez un forfait ou démarrez une période d’essai.</p>
      <div className="mt-5 grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label>Type</Label>
          <Select onValueChange={setMode} value={mode}>
            <SelectTrigger aria-label="Type d’abonnement">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="subscription">Abonnement</SelectItem>
              <SelectItem value="trial">Essai</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2">
          <Label>Forfait</Label>
          <Select onValueChange={setPlanCode} value={planCode}>
            <SelectTrigger aria-label="Forfait">
              <SelectValue placeholder="Sélectionner" />
            </SelectTrigger>
            <SelectContent>
              {activePlans.map((plan) => (
                <SelectItem key={plan.id} value={plan.code}>
                  {plan.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        {mode === "trial" ? (
          <div className="space-y-2">
            <Label htmlFor="trial-days">Durée en jours</Label>
            <Input
              id="trial-days"
              min="1"
              onChange={(event) => setTrialDays(event.target.value)}
              type="number"
              value={trialDays}
            />
          </div>
        ) : null}
        <div className="flex items-end">
          <Button
            disabled={
              !planCode ||
              create.isPending ||
              !session.can(
                mode === "trial" ? adminPermissions.subscriptionsCreateTrial : adminPermissions.subscriptionsCreate,
              )
            }
            onClick={() => create.mutate()}
          >
            {create.isPending ? "Création…" : mode === "trial" ? "Démarrer l’essai" : "Créer l’abonnement"}
          </Button>
        </div>
      </div>
    </section>
  );
}

function OverridesEditor({ subscription }: { subscription: AdminSubscription }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [selectedAddOns, setSelectedAddOns] = useState<string[]>(subscription.customOverrides.addOnCodes);
  const [quantities, setQuantities] = useState<Record<string, number>>(
    Object.fromEntries(subscription.customOverrides.quotaPackages.map((item) => [item.packageCode, item.quantity])),
  );
  const [addOns, quotaPackages] = useQueries({
    queries: [
      { queryKey: ["admin", "add-ons"], queryFn: adminApi.addOns },
      { queryKey: ["admin", "quota-packages"], queryFn: adminApi.quotaPackages },
    ],
  });
  useEffect(() => {
    if (open) {
      setSelectedAddOns(subscription.customOverrides.addOnCodes);
      setQuantities(
        Object.fromEntries(subscription.customOverrides.quotaPackages.map((item) => [item.packageCode, item.quantity])),
      );
    }
  }, [open, subscription.customOverrides]);
  const save = useMutation({
    mutationFn: () =>
      adminApi.updateSubscriptionOverrides(subscription.accountId, {
        addOnCodes: selectedAddOns,
        quotaPackages: Object.entries(quantities)
          .filter(([, quantity]) => quantity > 0)
          .map(([packageCode, quantity]) => ({ packageCode, quantity })),
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "subscription", subscription.accountId] });
      toast.success("Exceptions mises à jour");
      setOpen(false);
    },
  });
  if (!session.can(adminPermissions.subscriptionsOverrides)) return null;
  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>
        <Button variant="outline">Gérer les exceptions</Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Exceptions de l’abonnement</DialogTitle>
          <DialogDescription>
            Ajoutez uniquement les produits autorisés et compatibles avec le forfait actuel.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-6">
          <section>
            <h3 className="text-sm font-semibold">Add-ons</h3>
            <div className="mt-3 divide-y rounded-lg border">
              {addOns.data
                ?.filter((item) => item.status === "ACTIVE")
                .map((item) => (
                  <div className="flex items-start gap-3 p-3" key={item.id}>
                    <Checkbox
                      checked={selectedAddOns.includes(item.code)}
                      id={`addon-${item.id}`}
                      onCheckedChange={(checked) =>
                        setSelectedAddOns((current) =>
                          checked
                            ? [...new Set([...current, item.code])]
                            : current.filter((code) => code !== item.code),
                        )
                      }
                    />
                    <Label className="font-normal" htmlFor={`addon-${item.id}`}>
                      <span className="block text-sm font-medium">{item.name}</span>
                      <span className="block text-xs text-muted-foreground">
                        {money(item.price, item.currencyCode)}
                      </span>
                    </Label>
                  </div>
                ))}
            </div>
          </section>
          <section>
            <h3 className="text-sm font-semibold">Packages de quota</h3>
            <div className="mt-3 divide-y rounded-lg border">
              {quotaPackages.data
                ?.filter((item) => item.status === "ACTIVE")
                .map((item) => (
                  <div className="grid grid-cols-[1fr_6rem] items-center gap-3 p-3" key={item.id}>
                    <div>
                      <p className="text-sm font-medium">{item.name}</p>
                      <p className="text-xs text-muted-foreground">
                        +{item.capacityPerUnit} {item.resource} par unité
                      </p>
                    </div>
                    <Input
                      aria-label={`Quantité ${item.name}`}
                      max={item.maximumQuantity}
                      min="0"
                      onChange={(event) =>
                        setQuantities((current) => ({ ...current, [item.code]: Number(event.target.value) }))
                      }
                      type="number"
                      value={quantities[item.code] ?? 0}
                    />
                  </div>
                ))}
            </div>
          </section>
          <div className="flex justify-end">
            <Button disabled={save.isPending} onClick={() => save.mutate()}>
              Enregistrer
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function CheckoutDialog({ checkoutId, accountId }: { checkoutId: string; accountId: string }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reference, setReference] = useState("");
  const [reason, setReason] = useState("");
  const confirm = useMutation({
    mutationFn: () => adminApi.confirmCheckout(checkoutId, { reference, reason }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "subscription-changes", accountId] });
      void queryClient.invalidateQueries({ queryKey: ["admin", "subscription", accountId] });
      toast.success("Paiement confirmé manuellement");
      setOpen(false);
    },
  });
  if (!session.can(adminPermissions.subscriptionsConfirmCheckout)) return null;
  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>
        <Button size="sm" variant="outline">
          Confirmer
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Confirmation manuelle</DialogTitle>
          <DialogDescription>
            Cette action exige une référence externe et une justification auditable.
          </DialogDescription>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={(event: FormEvent) => {
            event.preventDefault();
            confirm.mutate();
          }}
        >
          <div className="space-y-2">
            <Label htmlFor="checkout-reference">Référence</Label>
            <Input
              id="checkout-reference"
              onChange={(event) => setReference(event.target.value)}
              required
              value={reference}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="checkout-reason">Justification</Label>
            <Textarea
              id="checkout-reason"
              onChange={(event) => setReason(event.target.value)}
              required
              value={reason}
            />
          </div>
          <div className="flex justify-end">
            <Button disabled={confirm.isPending} type="submit">
              Confirmer le paiement
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function SubscriptionDetail({ accountId }: { accountId: string }) {
  const subscription = useQuery({
    queryKey: ["admin", "subscription", accountId],
    queryFn: () => adminApi.subscription(accountId),
    retry: false,
  });
  const changes = useQuery({
    queryKey: ["admin", "subscription-changes", accountId],
    queryFn: () => adminApi.subscriptionChanges(accountId),
    retry: false,
  });
  if (subscription.isLoading) return <LoadingState />;
  if (subscription.isError) {
    if (subscription.error instanceof ApiError && subscription.error.status === 404)
      return <CreateSubscription accountId={accountId} />;
    return <ErrorState retry={() => void subscription.refetch()} />;
  }
  if (!subscription.data) return null;
  const data = subscription.data;
  return (
    <div className="space-y-6">
      <div className="grid gap-5 lg:grid-cols-[1fr_0.85fr]">
        <section className="rounded-xl border bg-card p-5">
          <div className="flex items-start justify-between">
            <div>
              <h2 className="text-lg font-semibold">{data.planName}</h2>
            </div>
            <StatusBadge
              tone={
                data.status === "ACTIVE"
                  ? "success"
                  : data.status === "PAST_DUE" || data.status === "SUSPENDED"
                    ? "danger"
                    : "warning"
              }
            >
              {data.status}
            </StatusBadge>
          </div>
          <dl className="mt-6 grid gap-5 sm:grid-cols-2">
            <Info label="Prix actuel" value={money(data.currentPrice, data.currentPriceCurrencyCode)} />
            <Info label="Début de période" value={date(data.currentPeriodStart)} />
            <Info label="Fin de période" value={date(data.currentPeriodEnd)} />
            <Info label="Résiliation planifiée" value={data.cancelAtPeriodEnd ? "Oui" : "Non"} />
          </dl>
        </section>
        <section className="rounded-xl border bg-card p-5">
          <div className="flex items-start justify-between gap-3">
            <div>
              <h2 className="text-sm font-semibold">Exceptions</h2>
              <p className="mt-1 text-xs text-muted-foreground">Produits ajoutés au modèle de base.</p>
            </div>
            <OverridesEditor subscription={data} />
          </div>
          <dl className="mt-5 space-y-4">
            <Info label="Add-ons" value={data.customOverrides.addOnCodes.length} />
            <Info
              label="Packages de quota"
              value={data.customOverrides.quotaPackages.reduce((total, item) => total + item.quantity, 0)}
            />
            <Info label="Version du snapshot" value={data.entitlementSnapshot?.planDefinitionVersion ?? "—"} />
          </dl>
        </section>
      </div>
      <section className="overflow-hidden rounded-xl border bg-card">
        <div className="border-b p-4">
          <h2 className="text-sm font-semibold">Opérations de changement</h2>
        </div>
        {changes.isLoading ? (
          <div className="p-5">
            <LoadingState rows={3} />
          </div>
        ) : !changes.data?.length ? (
          <EmptyState title="Aucune opération" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Changement</TableHead>
                <TableHead>Timing</TableHead>
                <TableHead>Statut</TableHead>
                <TableHead>Effet</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {changes.data.map((operation: SubscriptionChangeOperation) => (
                <TableRow key={operation.id}>
                  <TableCell>
                    <code className="text-xs">{operation.sourcePlanCode}</code> →{" "}
                    <code className="text-xs">{operation.targetPlanCode}</code>
                  </TableCell>
                  <TableCell>{operation.timing === "IMMEDIATE" ? "Immédiat" : "Au renouvellement"}</TableCell>
                  <TableCell>
                    <StatusBadge
                      tone={
                        operation.status === "APPLIED"
                          ? "success"
                          : operation.status === "NEEDS_ATTENTION"
                            ? "danger"
                            : "warning"
                      }
                    >
                      {operation.status}
                    </StatusBadge>
                  </TableCell>
                  <TableCell>{date(operation.effectiveAt)}</TableCell>
                  <TableCell>
                    {operation.checkout?.status === "PENDING_CONFIRMATION" ? (
                      <CheckoutDialog accountId={accountId} checkoutId={operation.checkout.id} />
                    ) : null}
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

export function AdminSubscriptionsPage() {
  const { accountId } = useParams();
  const [search, setSearch] = useState("");
  const deferred = useDeferredValue(search);
  const [page, setPage] = useState(0);
  const accounts = useQuery({
    queryKey: ["admin", "subscription-accounts", deferred, page],
    queryFn: () => adminApi.accounts({ query: deferred || undefined, page, size: 20 }),
  });
  if (accountId)
    return (
      <div className="space-y-7">
        <Button asChild size="sm" variant="ghost">
          <Link to="/admin/subscriptions">
            <ArrowLeftIcon className="rtl:rotate-180" />
            Abonnements
          </Link>
        </Button>
        <PageHeader
          title={accounts.data?.content.find((account) => account.id === accountId)?.name ?? "Abonnement du compte"}
        />
        <SubscriptionDetail accountId={accountId} />
      </div>
    );
  return (
    <div className="space-y-7">
      <PageHeader title="Abonnements" />
      <section className="overflow-hidden rounded-xl border bg-card">
        <div className="relative border-b p-4">
          <MagnifyingGlassIcon className="absolute start-7 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            autoFocus
            className="max-w-md ps-9"
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(0);
            }}
            placeholder="Compte, slug ou email du propriétaire…"
            value={search}
          />
        </div>
        {accounts.isLoading ? (
          <div className="p-5">
            <LoadingState />
          </div>
        ) : accounts.isError ? (
          <ErrorState retry={() => void accounts.refetch()} />
        ) : !accounts.data?.content.length ? (
          <EmptyState title="Aucun compte" />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Compte</TableHead>
                  <TableHead>Propriétaire</TableHead>
                  <TableHead>Slug</TableHead>
                  <TableHead>Statut</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {accounts.data.content.map((account) => (
                  <TableRow key={account.id}>
                    <TableCell>
                      <Link className="font-medium" to={`/admin/subscriptions/${account.id}`}>
                        {account.name}
                      </Link>
                    </TableCell>
                    <TableCell>{account.ownerEmail}</TableCell>
                    <TableCell>
                      <code className="text-xs">{account.slug}</code>
                    </TableCell>
                    <TableCell>
                      <StatusBadge tone={account.active ? "success" : "danger"}>
                        {account.active ? "Actif" : "Inactif"}
                      </StatusBadge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <PaginationBar
              onPageChange={setPage}
              page={accounts.data.page}
              totalElements={accounts.data.totalElements}
              totalPages={accounts.data.totalPages}
            />
          </>
        )}
      </section>
    </div>
  );
}

function Info({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-1 font-medium">{value}</dd>
    </div>
  );
}
