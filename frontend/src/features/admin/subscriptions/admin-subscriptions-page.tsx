import { ArrowLeftIcon, ArrowRightIcon, MagnifyingGlassIcon } from "@phosphor-icons/react";
import { keepPreviousData, useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import type { SortingState } from "@tanstack/react-table";
import { type FormEvent, useDeferredValue, useEffect, useMemo, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type {
  AdminSubscription,
  AdminSubscriptionChangeOperation,
  AssignablePlanPrice,
  SubscriptionAccountListItem,
  SubscriptionChangeOperation,
} from "@/api/contracts";
import { ApiError } from "@/api/http";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable, SortHeader } from "@/components/patterns/data-table";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { RowAction } from "@/components/patterns/row-action";
import { StatusBadge } from "@/components/patterns/status-badge";
import { TableActionsCell, tableActionsColumnMeta } from "@/components/patterns/table-actions-cell";
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
import { Textarea } from "@/components/ui/textarea";
import { capacityUnitLabel } from "@/features/admin/commercial/commercial-presentation";
import {
  adminCommercialKeys,
  commercialQueryEnabled,
  invalidateAdminCommercial,
  invalidateAdminSubscriptionEntitlement,
} from "@/features/commercial/commercial-query";
import { SubscriptionChangeList } from "@/features/commercial/subscription-change-list";
import {
  adminSubscriptionOperationUrlKeys,
  readSubscriptionOperationListState,
  subscriptionOperationQuery,
  subscriptionOperationSorting,
  subscriptionOperationStateFromSorting,
  writeSubscriptionOperationListState,
} from "@/features/commercial/subscription-operation-list-state";
import { subscriptionStatusPresentation } from "@/features/commercial/subscription-presentation";
import { formatExactMoney } from "@/lib/exact-decimal";
import { useDebouncedValue } from "@/lib/use-debounced-value";
import { AdminSubscriptionChangeWorkbench } from "./admin-subscription-change-workbench";
import { AssignablePlanPricePicker } from "./assignable-plan-price-picker";
import {
  readSubscriptionAccountListState,
  subscriptionAccountQuery,
  subscriptionAccountSorting,
  subscriptionAccountStateFromSorting,
  writeSubscriptionAccountListState,
} from "./subscription-account-list-state";
import {
  normalizedOperatorReason,
  operatorReasonError,
  subscriptionOperationCanBeCancelled,
  subscriptionOperationOriginLabel,
} from "./subscription-operation-rules";
import { SubscriptionOwnerEmailLookup } from "./subscription-owner-email-lookup";

const money = formatExactMoney;
const date = (value: string | null) =>
  value ? new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium" }).format(new Date(value)) : "—";
const dateTime = (value: string | null) =>
  value ? new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "—";

function CreateSubscription({ accountId }: { accountId: string }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const canCreate = session.can(adminPermissions.subscriptionsCreate);
  const canCreateTrial = session.can(adminPermissions.subscriptionsCreateTrial);
  const canListPrices = session.can(adminPermissions.subscriptionsListAssignablePrices);
  const [selectedPrice, setSelectedPrice] = useState<AssignablePlanPrice | null>(null);
  const [trialDays, setTrialDays] = useState("14");
  const trialDaysNumber = Number(trialDays);
  const validTrialDays = Number.isInteger(trialDaysNumber) && trialDaysNumber >= 1 && trialDaysNumber <= 365;
  const [mode, setMode] = useState<"subscription" | "trial">(canCreate ? "subscription" : "trial");
  const create = useMutation({
    mutationFn: () => {
      if (!selectedPrice) throw new Error("Aucun tarif sélectionné");
      const selection = {
        priceEntryId: selectedPrice.priceEntryId,
        currencyCode: selectedPrice.currencyCode,
        billingCycle: selectedPrice.billingCycle,
      };
      return mode === "trial"
        ? adminApi.createTrial(accountId, selectedPrice.planCode, trialDaysNumber, selection)
        : adminApi.createSubscription(accountId, selectedPrice.planCode, selection);
    },
    onSuccess: () => {
      void invalidateAdminSubscriptionEntitlement(queryClient);
      toast.success(mode === "trial" ? "Essai démarré" : "Abonnement créé");
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : "Création impossible"),
  });
  if (!canCreate && !canCreateTrial) {
    return (
      <EmptyState
        description="Aucun abonnement utilisable n’est actuellement rattaché à ce compte."
        title="Aucun abonnement"
      />
    );
  }
  return (
    <section className="max-w-4xl rounded-xl border bg-card p-5">
      <h2 className="text-base font-semibold">Aucun abonnement utilisable</h2>
      <p className="mt-1 text-sm text-muted-foreground">Affectez un forfait ou démarrez une période d’essai.</p>
      <div className="mt-5 grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label>Type</Label>
          <Select onValueChange={(value) => setMode(value as "subscription" | "trial")} value={mode}>
            <SelectTrigger aria-label="Type d’abonnement">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {canCreate ? <SelectItem value="subscription">Abonnement</SelectItem> : null}
              {canCreateTrial ? <SelectItem value="trial">Essai</SelectItem> : null}
            </SelectContent>
          </Select>
        </div>
        {mode === "trial" ? (
          <div className="space-y-2">
            <Label htmlFor="trial-days">Durée en jours</Label>
            <Input
              aria-describedby={!validTrialDays ? "trial-days-error" : undefined}
              aria-invalid={!validTrialDays}
              id="trial-days"
              max="365"
              min="1"
              onChange={(event) => setTrialDays(event.target.value)}
              type="number"
              value={trialDays}
            />
            {!validTrialDays ? (
              <p className="text-xs text-destructive" id="trial-days-error" role="alert">
                Choisissez une durée entre 1 et 365 jours.
              </p>
            ) : null}
          </div>
        ) : null}
      </div>
      <div className="mt-5">
        <AssignablePlanPricePicker
          enabled={canCreate || canCreateTrial}
          onChange={setSelectedPrice}
          value={selectedPrice}
        />
      </div>
      <div className="mt-5 flex justify-end">
        <Button
          disabled={
            !selectedPrice ||
            !canListPrices ||
            create.isPending ||
            (mode === "trial" ? !canCreateTrial || !validTrialDays : !canCreate)
          }
          onClick={() => create.mutate()}
        >
          {create.isPending ? "Création…" : mode === "trial" ? "Démarrer l’essai" : "Créer l’abonnement"}
        </Button>
      </div>
    </section>
  );
}

export function OverridesEditor({ subscription }: { subscription: AdminSubscription }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [addOnSearch, setAddOnSearch] = useState("");
  const [quotaSearch, setQuotaSearch] = useState("");
  const debouncedAddOnSearch = useDebouncedValue(addOnSearch);
  const debouncedQuotaSearch = useDebouncedValue(quotaSearch);
  const [selectedAddOns, setSelectedAddOns] = useState<string[]>(subscription.customOverrides.addOnCodes);
  const canListAddOns = session.can(adminPermissions.subscriptionsChooseAddOnOverrides);
  const canListQuotaPackages = session.can(adminPermissions.subscriptionsChooseQuotaOverrides);
  const [quantities, setQuantities] = useState<Record<string, number>>(
    Object.fromEntries(subscription.customOverrides.quotaPackages.map((item) => [item.packageCode, item.quantity])),
  );
  const [addOns, quotaPackages] = useQueries({
    queries: [
      {
        queryKey: [
          "admin",
          "subscriptions",
          subscription.accountId,
          "override-add-ons",
          selectedAddOns,
          debouncedAddOnSearch,
        ],
        queryFn: () =>
          adminApi.subscriptionAddOnOverrideChoices(subscription.accountId, {
            selectedAddOnCodes: selectedAddOns,
            useCurrentAddOnSelections: false,
            search: debouncedAddOnSearch || undefined,
            size: 50,
          }),
        enabled: commercialQueryEnabled(
          session.can,
          adminPermissions.subscriptionsChooseAddOnOverrides,
          open && session.can(adminPermissions.subscriptionsOverrides),
        ),
      },
      {
        queryKey: [
          "admin",
          "subscriptions",
          subscription.accountId,
          "override-quotas",
          selectedAddOns,
          debouncedQuotaSearch,
        ],
        queryFn: () =>
          adminApi.subscriptionQuotaOverrideChoices(subscription.accountId, {
            selectedAddOnCodes: selectedAddOns,
            useCurrentAddOnSelections: false,
            search: debouncedQuotaSearch || undefined,
            size: 50,
          }),
        enabled: commercialQueryEnabled(
          session.can,
          adminPermissions.subscriptionsChooseQuotaOverrides,
          open && session.can(adminPermissions.subscriptionsOverrides),
        ),
      },
    ],
  });
  const addOnChoices = [
    ...new Map(
      [...(addOns.data?.content ?? []), ...(addOns.data?.retainedSelections ?? [])].map((item) => [
        item.productId,
        item,
      ]),
    ).values(),
  ];
  const quotaChoices = [
    ...new Map(
      [...(quotaPackages.data?.content ?? []), ...(quotaPackages.data?.retainedSelections ?? [])].map((item) => [
        item.productId,
        item,
      ]),
    ).values(),
  ];
  const invalidQuantityCodes = new Set(
    quotaChoices
      .filter((item) => {
        const quantity = quantities[item.code] ?? 0;
        return !Number.isInteger(quantity) || quantity < 0 || quantity > item.maximumQuantity;
      })
      .map((item) => item.code),
  );
  const catalogReady =
    canListAddOns &&
    canListQuotaPackages &&
    !addOns.isLoading &&
    !quotaPackages.isLoading &&
    !addOns.isError &&
    !quotaPackages.isError;
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
      void invalidateAdminCommercial(queryClient, adminCommercialKeys.subscriptions.all());
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
            <Input
              aria-label="Rechercher un add-on"
              className="mt-3"
              onChange={(event) => setAddOnSearch(event.target.value)}
              placeholder="Nom de l’add-on…"
              value={addOnSearch}
            />
            <div className="mt-3 divide-y rounded-lg border">
              {!canListAddOns ? (
                <p className="p-3 text-sm text-muted-foreground">Catalogue non accessible pour cet accès.</p>
              ) : addOns.isLoading ? (
                <div className="p-3">
                  <LoadingState rows={2} />
                </div>
              ) : addOns.isError ? (
                <ErrorState retry={() => void addOns.refetch()} title="Impossible de charger les add-ons" />
              ) : addOnChoices.length ? (
                addOnChoices.map((item) => (
                  <div className="flex items-start gap-3 p-3" key={item.productId}>
                    <Checkbox
                      checked={selectedAddOns.includes(item.code)}
                      disabled={item.retained && !item.removable}
                      id={`addon-${item.productId}`}
                      onCheckedChange={(checked) =>
                        setSelectedAddOns((current) =>
                          checked
                            ? [...new Set([...current, item.code])]
                            : current.filter((code) => code !== item.code),
                        )
                      }
                    />
                    <Label className="font-normal" htmlFor={`addon-${item.productId}`}>
                      <span className="block text-sm font-medium">{item.name}</span>
                      <span className="block text-xs text-muted-foreground">
                        {money(item.unitPrice, item.currencyCode)} ·{" "}
                        {item.billingCycle === "MONTHLY"
                          ? "mensuel"
                          : item.billingCycle === "YEARLY"
                            ? "annuel"
                            : "permanent"}
                        {item.retained ? " · conservé" : ""}
                      </span>
                    </Label>
                  </div>
                ))
              ) : (
                <p className="p-3 text-sm text-muted-foreground">Aucun add-on actif.</p>
              )}
            </div>
            {addOns.data?.hasMoreCandidates ? (
              <p className="mt-2 text-xs text-muted-foreground">Affinez la recherche pour voir les autres add-ons.</p>
            ) : null}
          </section>
          <section>
            <h3 className="text-sm font-semibold">Packs de capacité</h3>
            <Input
              aria-label="Rechercher un pack de capacité"
              className="mt-3"
              onChange={(event) => setQuotaSearch(event.target.value)}
              placeholder="Nom du pack…"
              value={quotaSearch}
            />
            <div className="mt-3 divide-y rounded-lg border">
              {!canListQuotaPackages ? (
                <p className="p-3 text-sm text-muted-foreground">Catalogue non accessible pour cet accès.</p>
              ) : quotaPackages.isLoading ? (
                <div className="p-3">
                  <LoadingState rows={2} />
                </div>
              ) : quotaPackages.isError ? (
                <ErrorState
                  retry={() => void quotaPackages.refetch()}
                  title="Impossible de charger les packs de capacité"
                />
              ) : quotaChoices.length ? (
                quotaChoices.map((item) => (
                  <div className="grid grid-cols-[1fr_6rem] items-center gap-3 p-3" key={item.productId}>
                    <div>
                      <p className="text-sm font-medium">{item.name}</p>
                      <p className="text-xs text-muted-foreground">
                        +{item.capacityPerUnit} {capacityUnitLabel(item.resource)} par unité ·{" "}
                        {money(item.unitPrice, item.currencyCode)} ·{" "}
                        {item.billingCycle === "MONTHLY"
                          ? "mensuel"
                          : item.billingCycle === "YEARLY"
                            ? "annuel"
                            : "permanent"}
                        {item.retained ? " · conservé" : ""}
                      </p>
                    </div>
                    <Input
                      aria-label={`Quantité ${item.name}`}
                      aria-describedby={
                        invalidQuantityCodes.has(item.code) ? `quota-${item.productId}-error` : undefined
                      }
                      aria-invalid={invalidQuantityCodes.has(item.code)}
                      disabled={item.retained && !item.quantityEditable && !item.removable}
                      max={item.maximumQuantity}
                      min="0"
                      onChange={(event) =>
                        setQuantities((current) => ({ ...current, [item.code]: Number(event.target.value) }))
                      }
                      type="number"
                      step="1"
                      value={quantities[item.code] ?? 0}
                    />
                    {invalidQuantityCodes.has(item.code) ? (
                      <p
                        className="col-span-2 text-xs text-destructive"
                        id={`quota-${item.productId}-error`}
                        role="alert"
                      >
                        Saisissez un nombre entier entre 0 et {item.maximumQuantity}.
                      </p>
                    ) : null}
                  </div>
                ))
              ) : (
                <p className="p-3 text-sm text-muted-foreground">Aucun pack actif.</p>
              )}
            </div>
            {quotaPackages.data?.hasMoreCandidates ? (
              <p className="mt-2 text-xs text-muted-foreground">Affinez la recherche pour voir les autres packs.</p>
            ) : null}
          </section>
          <div className="flex justify-end">
            <Button
              disabled={save.isPending || !catalogReady || invalidQuantityCodes.size > 0}
              onClick={() => save.mutate()}
            >
              Enregistrer
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function CheckoutDialog({ checkoutId }: { checkoutId: string }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reference, setReference] = useState("");
  const [reason, setReason] = useState("");
  const confirm = useMutation({
    mutationFn: () => adminApi.confirmCheckout(checkoutId, { reference: reference.trim(), reason: reason.trim() }),
    onSuccess: () => {
      void invalidateAdminSubscriptionEntitlement(queryClient);
      toast.success("Paiement confirmé manuellement");
      setOpen(false);
    },
    onError: (error) => {
      if (error instanceof ApiError && error.code === "INVALID_STATE") {
        toast.error("Ce paiement n’attend plus de confirmation. L’abonnement a été rechargé.");
        void invalidateAdminSubscriptionEntitlement(queryClient);
        return;
      }
      toast.error("La confirmation manuelle n’a pas pu être enregistrée.");
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
              maxLength={255}
              onChange={(event) => setReference(event.target.value)}
              required
              value={reference}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="checkout-reason">Justification</Label>
            <Textarea
              id="checkout-reason"
              maxLength={2000}
              onChange={(event) => setReason(event.target.value)}
              required
              value={reason}
            />
          </div>
          <div className="flex justify-end">
            <Button disabled={confirm.isPending || !reference.trim() || !reason.trim()} type="submit">
              Confirmer le paiement
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function OperationProvenance({ operation }: { operation: AdminSubscriptionChangeOperation }) {
  return (
    <dl className="grid gap-x-8 gap-y-4 text-sm sm:grid-cols-2 lg:grid-cols-3">
      <Info label="Origine de la demande" value={subscriptionOperationOriginLabel[operation.requestOrigin]} />
      <Info label="Identifiant du demandeur" value={operation.requestedByUserId ?? "—"} />
      <Info label="Justification" value={operation.requestReason ?? "—"} />
      {operation.cancellationOrigin ? (
        <>
          <Info
            label="Origine de l’annulation"
            value={subscriptionOperationOriginLabel[operation.cancellationOrigin]}
          />
          <Info label="Identifiant de l’auteur" value={operation.cancelledByUserId ?? "—"} />
          <Info label="Annulée le" value={dateTime(operation.cancelledAt)} />
          <div className="sm:col-span-2 lg:col-span-3">
            <Info label="Motif de l’annulation" value={operation.cancellationReason ?? "—"} />
          </div>
        </>
      ) : null}
    </dl>
  );
}

function CancelChangeDialog({ accountId, operation }: { accountId: string; operation: SubscriptionChangeOperation }) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [touched, setTouched] = useState(false);
  const reasonProblem = operatorReasonError(reason);
  const cancel = useMutation({
    mutationFn: () => adminApi.cancelSubscriptionChange(accountId, operation.id, normalizedOperatorReason(reason)),
    onSuccess: () => {
      void invalidateAdminSubscriptionEntitlement(queryClient);
      toast.success("Changement annulé");
      setOpen(false);
    },
    onError: (error) => {
      if (error instanceof ApiError && error.code === "INVALID_STATE") {
        void queryClient.invalidateQueries({ queryKey: adminCommercialKeys.subscriptions.changes(accountId) });
        toast.error("Ce changement n’est plus annulable. L’historique a été rechargé.");
        return;
      }
      toast.error("L’annulation n’a pas pu être enregistrée.");
    },
  });
  return (
    <Dialog
      onOpenChange={(nextOpen) => {
        setOpen(nextOpen);
        if (!nextOpen) {
          setReason("");
          setTouched(false);
        }
      }}
      open={open}
    >
      <DialogTrigger asChild>
        <Button size="sm" variant="ghost">
          Annuler
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Annuler ce changement ?</DialogTitle>
          <DialogDescription>
            {operation.sourcePlanCode} → {operation.targetPlanCode} ·{" "}
            {operation.timing === "AT_RENEWAL" ? "au renouvellement" : "immédiat"}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor={`cancel-change-reason-${operation.id}`}>Motif de l’annulation</Label>
            <Textarea
              aria-describedby={touched && reasonProblem ? `cancel-change-error-${operation.id}` : undefined}
              aria-invalid={touched && Boolean(reasonProblem)}
              id={`cancel-change-reason-${operation.id}`}
              maxLength={2000}
              onBlur={() => setTouched(true)}
              onChange={(event) => setReason(event.target.value)}
              value={reason}
            />
            {touched && reasonProblem ? (
              <p className="text-xs text-destructive" id={`cancel-change-error-${operation.id}`} role="alert">
                {reasonProblem}
              </p>
            ) : null}
          </div>
          <div className="flex justify-end gap-2">
            <Button onClick={() => setOpen(false)} variant="outline">
              Retour
            </Button>
            <Button
              disabled={cancel.isPending}
              onClick={() => {
                setTouched(true);
                if (!reasonProblem) cancel.mutate();
              }}
              variant="destructive"
            >
              {cancel.isPending ? "Annulation…" : "Confirmer l’annulation"}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function SubscriptionDetail({ accountId }: { accountId: string }) {
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const canReadChanges = session.can(adminPermissions.subscriptionsReadChanges);
  const canChooseChange = session.can(adminPermissions.subscriptionsChooseChangeOptions);
  const operationState = readSubscriptionOperationListState(params, adminSubscriptionOperationUrlKeys);
  const operationRequest = subscriptionOperationQuery(operationState);
  const subscription = useQuery({
    queryKey: adminCommercialKeys.subscriptions.detail(accountId),
    queryFn: () => adminApi.subscription(accountId),
    enabled: commercialQueryEnabled(session.can, adminPermissions.subscriptionsRead),
    retry: false,
  });
  const changeCatalog = useQuery({
    queryKey: adminCommercialKeys.subscriptions.changeCatalog(accountId),
    queryFn: () => adminApi.subscriptionChangeCatalog(accountId),
    enabled: commercialQueryEnabled(session.can, adminPermissions.subscriptionsChooseChangeOptions),
    retry: false,
  });
  const changes = useQuery({
    queryKey: adminCommercialKeys.subscriptions.changes(accountId, operationRequest),
    queryFn: () => adminApi.subscriptionChanges(accountId, operationRequest),
    enabled: commercialQueryEnabled(session.can, adminPermissions.subscriptionsReadChanges),
    placeholderData: keepPreviousData,
    retry: false,
  });
  useEffect(() => {
    if (!changes.data || changes.isPlaceholderData) return;
    const boundedPage = changes.data.totalPages === 0 ? 0 : Math.min(operationState.page, changes.data.totalPages - 1);
    if (boundedPage === operationState.page) return;
    setParams(
      writeSubscriptionOperationListState(
        params,
        { ...operationState, page: boundedPage },
        adminSubscriptionOperationUrlKeys,
      ),
      { replace: true },
    );
  }, [changes.data, changes.isPlaceholderData, operationState, params, setParams]);
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
            <StatusBadge tone={subscriptionStatusPresentation[data.status].tone}>
              {subscriptionStatusPresentation[data.status].label}
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
              label="Packs de capacité"
              value={data.customOverrides.quotaPackages.reduce((total, item) => total + item.quantity, 0)}
            />
            <Info label="Version du snapshot" value={data.entitlementSnapshot?.planDefinitionVersion ?? "—"} />
          </dl>
        </section>
      </div>
      {canChooseChange ? (
        changeCatalog.isLoading ? (
          <section className="rounded-xl border bg-card p-5">
            <LoadingState rows={4} />
          </section>
        ) : changeCatalog.isError ? (
          <ErrorState
            retry={() => void changeCatalog.refetch()}
            title="Impossible de charger les options de changement"
          />
        ) : changeCatalog.data ? (
          <AdminSubscriptionChangeWorkbench accountId={accountId} catalog={changeCatalog.data} />
        ) : null
      ) : null}
      {canReadChanges ? (
        <section className="overflow-hidden rounded-xl border bg-card">
          <div className="border-b p-4">
            <h2 className="text-sm font-semibold">Opérations de changement</h2>
          </div>
          {changes.isLoading ? (
            <div className="p-5">
              <LoadingState rows={3} />
            </div>
          ) : changes.isError ? (
            <ErrorState retry={() => void changes.refetch()} title="Impossible de charger les changements" />
          ) : !changes.data?.content.length ? (
            <EmptyState title="Aucune opération" />
          ) : (
            <>
              <SubscriptionChangeList
                onSortingChange={(sorting) =>
                  setParams(
                    writeSubscriptionOperationListState(
                      params,
                      subscriptionOperationStateFromSorting(operationState, sorting),
                      adminSubscriptionOperationUrlKeys,
                    ),
                    { replace: true },
                  )
                }
                operations={changes.data.content}
                renderAction={(operation) => (
                  <div className="flex items-center gap-1">
                    {operation.checkout?.status === "PENDING_CONFIRMATION" ? (
                      <CheckoutDialog checkoutId={operation.checkout.id} />
                    ) : null}
                    {subscriptionOperationCanBeCancelled(operation) &&
                    session.can(adminPermissions.subscriptionsCancelChange) ? (
                      <CancelChangeDialog accountId={accountId} operation={operation} />
                    ) : null}
                  </div>
                )}
                renderDetails={(operation) => (
                  <OperationProvenance operation={operation as AdminSubscriptionChangeOperation} />
                )}
                sorting={subscriptionOperationSorting(operationState)}
              />
              <PaginationBar
                onPageChange={(page) =>
                  setParams(
                    writeSubscriptionOperationListState(
                      params,
                      { ...operationState, page },
                      adminSubscriptionOperationUrlKeys,
                    ),
                    { replace: true },
                  )
                }
                page={changes.data.page}
                totalElements={changes.data.totalElements}
                totalPages={changes.data.totalPages}
              />
            </>
          )}
        </section>
      ) : null}
    </div>
  );
}

export function AdminSubscriptionsPage() {
  const { accountId } = useParams();
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const state = readSubscriptionAccountListState(params);
  const deferredSearch = useDeferredValue(state.search);
  const request = useMemo(
    () => subscriptionAccountQuery({ ...state, search: deferredSearch }),
    [deferredSearch, state],
  );
  const accounts = useQuery({
    queryKey: adminCommercialKeys.subscriptions.accounts(request),
    queryFn: () => adminApi.accounts(request),
    enabled: commercialQueryEnabled(session.can, adminPermissions.subscriptionsSearch, !accountId),
    placeholderData: keepPreviousData,
  });
  useEffect(() => {
    if (!accounts.data || accounts.isPlaceholderData) return;
    const boundedPage = accounts.data.totalPages === 0 ? 0 : Math.min(state.page, accounts.data.totalPages - 1);
    if (boundedPage !== state.page) {
      setParams(
        writeSubscriptionAccountListState(params, {
          ...state,
          page: boundedPage,
        }),
        { replace: true },
      );
    }
  }, [accounts.data, accounts.isPlaceholderData, params, setParams, state]);
  const canOpen = session.can(adminPermissions.subscriptionsRead);
  const column = useMemo(() => createDataColumns<SubscriptionAccountListItem>(), []);
  const columns = useMemo(
    () =>
      column.columns([
        column.accessor("name", {
          meta: { headerClassName: "min-w-48" },
          header: ({ column: item }) => <SortHeader column={item}>Compte</SortHeader>,
          cell: ({ row }) => (
            <span>
              <span className="block font-medium">{row.original.name}</span>
              <span className="mt-0.5 block text-xs text-muted-foreground">{row.original.slug}</span>
            </span>
          ),
        }),
        column.display({
          id: "plan",
          header: "Dernier forfait",
          cell: ({ row }) =>
            row.original.latestSubscription ? (
              <span>
                <span className="block font-medium">{row.original.latestSubscription.planName}</span>
                <span className="mt-0.5 block text-xs text-muted-foreground">
                  R{row.original.latestSubscription.planRevisionNumber} ·{" "}
                  {row.original.latestSubscription.billingCycle === "MONTHLY" ? "mensuel" : "annuel"}
                </span>
              </span>
            ) : (
              <span className="text-sm text-muted-foreground">Aucun abonnement</span>
            ),
        }),
        column.display({
          id: "subscription",
          header: "Abonnement",
          cell: ({ row }) => {
            const current = row.original.latestSubscription;
            if (!current) return <span className="text-sm text-muted-foreground">—</span>;
            const presentation = subscriptionStatusPresentation[current.status];
            return (
              <span>
                <StatusBadge tone={presentation.tone}>{presentation.label}</StatusBadge>
                <span className="mt-1 block text-xs text-muted-foreground">
                  {current.cancelAtPeriodEnd ? "Fin programmée" : `Échéance ${date(current.currentPeriodEnd)}`}
                </span>
              </span>
            );
          },
        }),
        column.accessor("createdAt", {
          meta: { headerClassName: "w-32", cellClassName: "w-32" },
          header: ({ column: item }) => <SortHeader column={item}>Créé le</SortHeader>,
          cell: ({ row }) => <time dateTime={row.original.createdAt}>{date(row.original.createdAt)}</time>,
        }),
        column.accessor("active", {
          meta: { headerClassName: "w-28", cellClassName: "w-28" },
          header: ({ column: item }) => <SortHeader column={item}>État du compte</SortHeader>,
          cell: ({ row }) => (
            <StatusBadge tone={row.original.active ? "success" : "danger"}>
              {row.original.active ? "Actif" : "Inactif"}
            </StatusBadge>
          ),
        }),
        column.display({
          id: "actions",
          meta: tableActionsColumnMeta(1),
          header: "Actions",
          cell: ({ row }) => (
            <TableActionsCell label={`Actions pour ${row.original.name}`}>
              <RowAction
                disabled={!canOpen}
                disabledLabel="Consultation de l’abonnement non autorisée"
                icon={<ArrowRightIcon className="rtl:rotate-180" />}
                label="Ouvrir"
                to={canOpen ? `/admin/subscriptions/${row.original.id}` : undefined}
              />
            </TableActionsCell>
          ),
        }),
      ]),
    [canOpen, column],
  );
  const sorting = subscriptionAccountSorting(state);
  const setState = (next: typeof state) =>
    setParams(writeSubscriptionAccountListState(params, next), { replace: true });
  if (accountId)
    return (
      <div className="space-y-7">
        <Button asChild size="sm" variant="ghost">
          <Link to="/admin/subscriptions">
            <ArrowLeftIcon className="rtl:rotate-180" />
            Abonnements
          </Link>
        </Button>
        <PageHeader title="Abonnement du compte" />
        <SubscriptionDetail accountId={accountId} />
      </div>
    );
  return (
    <div className="space-y-7">
      <PageHeader
        actions={
          session.can(adminPermissions.subscriptionsLookupAccountOwnerEmail) ? (
            <SubscriptionOwnerEmailLookup />
          ) : undefined
        }
        title="Abonnements"
      />
      <section className="overflow-hidden rounded-xl border bg-card">
        <div className="grid gap-3 border-b p-4 md:grid-cols-[minmax(240px,1fr)_180px_210px]">
          <div className="relative">
            <MagnifyingGlassIcon
              aria-hidden="true"
              className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
            />
            <Input
              aria-label="Rechercher des comptes"
              className="ps-9"
              onChange={(event) => setState({ ...state, search: event.target.value, page: 0 })}
              placeholder="Nom, slug ou identifiant du compte…"
              value={state.search}
            />
          </div>
          <Select
            onValueChange={(accountStatus) =>
              setState({ ...state, accountStatus: accountStatus as typeof state.accountStatus, page: 0 })
            }
            value={state.accountStatus}
          >
            <SelectTrigger aria-label="État du compte">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Tous les comptes</SelectItem>
              <SelectItem value="active">Comptes actifs</SelectItem>
              <SelectItem value="inactive">Comptes inactifs</SelectItem>
            </SelectContent>
          </Select>
          <Select
            onValueChange={(subscription) =>
              setState({ ...state, subscription: subscription as typeof state.subscription, page: 0 })
            }
            value={state.subscription}
          >
            <SelectTrigger aria-label="État de l’abonnement">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Tous les abonnements</SelectItem>
              <SelectItem value="any">Avec historique</SelectItem>
              <SelectItem value="none">Sans abonnement</SelectItem>
              <SelectItem value="ACTIVE">Actifs</SelectItem>
              <SelectItem value="TRIALING">En essai</SelectItem>
              <SelectItem value="PAST_DUE">Impayés</SelectItem>
              <SelectItem value="SUSPENDED">Suspendus</SelectItem>
              <SelectItem value="CANCELLED">Annulés</SelectItem>
              <SelectItem value="EXPIRED">Expirés</SelectItem>
            </SelectContent>
          </Select>
        </div>
        {accounts.isLoading ? (
          <div className="p-5">
            <LoadingState />
          </div>
        ) : accounts.isError ? (
          <ErrorState retry={() => void accounts.refetch()} />
        ) : (
          <>
            <div className="hidden md:block">
              <DataTable
                columns={columns}
                data={accounts.data?.content ?? []}
                emptyState={<EmptyState description="Modifiez les filtres ou la recherche." title="Aucun compte" />}
                getRowId={(row) => row.id}
                onSortingChange={(next: SortingState) => setState(subscriptionAccountStateFromSorting(state, next))}
                sorting={sorting}
              />
            </div>
            <div className="divide-y md:hidden">
              {!accounts.data?.content.length ? (
                <EmptyState description="Modifiez les filtres ou la recherche." title="Aucun compte" />
              ) : null}
              {(accounts.data?.content ?? []).map((account) => (
                <article className="flex items-center justify-between gap-4 p-4" key={account.id}>
                  <div className="min-w-0">
                    <p className="truncate font-medium">{account.name}</p>
                    <p className="truncate text-xs text-muted-foreground">{account.slug}</p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {account.latestSubscription
                        ? `${account.latestSubscription.planName} · ${subscriptionStatusPresentation[account.latestSubscription.status].label}`
                        : "Aucun abonnement"}
                    </p>
                  </div>
                  {canOpen ? (
                    <Button asChild size="icon-sm" variant="ghost">
                      <Link aria-label={`Ouvrir ${account.name}`} to={`/admin/subscriptions/${account.id}`}>
                        <ArrowRightIcon className="rtl:rotate-180" />
                      </Link>
                    </Button>
                  ) : (
                    <Button
                      aria-label={`Ouverture de ${account.name} non autorisée`}
                      disabled
                      size="icon-sm"
                      variant="ghost"
                    >
                      <ArrowRightIcon className="rtl:rotate-180" />
                    </Button>
                  )}
                </article>
              ))}
            </div>
            <PaginationBar
              onPageChange={(page) => setState({ ...state, page })}
              page={accounts.data?.page ?? 0}
              totalElements={accounts.data?.totalElements ?? 0}
              totalPages={accounts.data?.totalPages ?? 0}
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
