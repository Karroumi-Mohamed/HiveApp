import { ArrowLeftIcon, ArrowRightIcon, ArrowsClockwiseIcon, MagnifyingGlassIcon } from "@phosphor-icons/react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { SortingState } from "@tanstack/react-table";
import { type FormEvent, useDeferredValue, useEffect, useMemo, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type {
  AdminSubscriptionChangeOperation,
  SubscriptionAccountListItem,
  SubscriptionCheckout,
} from "@/api/contracts";
import { ApiError } from "@/api/http";
import { adminPermissions, adminSubscriptionDetailSurfacePermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable, SortHeader } from "@/components/patterns/data-table";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { RowAction } from "@/components/patterns/row-action";
import { StatusBadge } from "@/components/patterns/status-badge";
import { TableActionsCell, tableActionsColumnMeta } from "@/components/patterns/table-actions-cell";
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
import { Textarea } from "@/components/ui/textarea";
import { AdminCommercialPolicyTerms } from "@/features/commercial/commercial-policy-terms";
import {
  adminCommercialKeys,
  commercialQueryEnabled,
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
import { AdminSubscriptionChangeWorkbench } from "./admin-subscription-change-workbench";
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

function CheckoutDialog({ checkout }: { checkout: SubscriptionCheckout }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reference, setReference] = useState("");
  const [reason, setReason] = useState("");
  const confirm = useMutation({
    mutationFn: () => adminApi.confirmCheckout(checkout.id, { reference: reference.trim(), reason: reason.trim() }),
    onSuccess: () => {
      void invalidateAdminSubscriptionEntitlement(queryClient);
      toast.success("Paiement confirmé manuellement");
      setReference("");
      setReason("");
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
    <Dialog
      onOpenChange={(nextOpen) => {
        setOpen(nextOpen);
        if (!nextOpen && !confirm.isPending) {
          setReference("");
          setReason("");
        }
      }}
      open={open}
    >
      <DialogTrigger asChild>
        <Button size="sm" variant="outline">
          Confirmer
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Confirmation manuelle</DialogTitle>
          <DialogDescription>
            {formatExactMoney(checkout.amount, checkout.currencyCode)} · cette action exige une référence externe et une
            justification auditable.
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
              name="checkout-reference"
              onChange={(event) => setReference(event.target.value)}
              required
              spellCheck={false}
              autoComplete="off"
              value={reference}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="checkout-reason">Justification</Label>
            <Textarea
              id="checkout-reason"
              maxLength={2000}
              name="checkout-reason"
              onChange={(event) => setReason(event.target.value)}
              required
              autoComplete="off"
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

const checkoutStatusLabel: Record<SubscriptionCheckout["status"], string> = {
  PENDING_CONFIRMATION: "Confirmation en attente",
  CONFIRMED: "Confirmé",
  FAILED: "Échoué",
  CANCELLED: "Annulé",
};

function OperationDetails({ operation }: { operation: AdminSubscriptionChangeOperation }) {
  const checkout = operation.checkout;
  return (
    <div className="space-y-5">
      <OperationProvenance operation={operation} />
      {operation.commercialPolicyEvaluation ? (
        <section className="border-t pt-4">
          <AdminCommercialPolicyTerms
            evaluation={operation.commercialPolicyEvaluation}
            title="Conditions acceptées avec ce changement"
          />
        </section>
      ) : null}
      {checkout ? (
        <section className="border-t pt-4">
          <h3 className="text-sm font-semibold">Paiement</h3>
          <dl className="mt-3 grid gap-x-8 gap-y-4 text-sm sm:grid-cols-2 lg:grid-cols-3">
            <Info label="Montant" value={formatExactMoney(checkout.amount, checkout.currencyCode)} />
            <Info label="Statut" value={checkoutStatusLabel[checkout.status]} />
            <Info label="État de la tentative" value={checkout.gatewayAttemptStatus ?? "—"} />
            <Info label="Référence du prestataire" value={checkout.gatewayReference ?? "—"} />
            <Info label="Référence de confirmation" value={checkout.confirmationReference ?? "—"} />
            <Info label="Confirmé le" value={dateTime(checkout.confirmedAt)} />
            {checkout.gatewayFailureReason ? (
              <div className="sm:col-span-2 lg:col-span-3">
                <Info label="Échec du prestataire" value={checkout.gatewayFailureReason} />
              </div>
            ) : null}
          </dl>
        </section>
      ) : null}
    </div>
  );
}

function CancelChangeDialog({
  accountId,
  operation,
}: {
  accountId: string;
  operation: AdminSubscriptionChangeOperation;
}) {
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
      setReason("");
      setTouched(false);
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

export function SubscriptionDetail({ accountId }: { accountId: string }) {
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const canReadSubscription = session.can(adminPermissions.subscriptionsRead);
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
  const data = subscription.data;
  return (
    <div className="space-y-6">
      {canReadSubscription ? (
        subscription.isLoading ? (
          <LoadingState />
        ) : subscription.isError ? (
          subscription.error instanceof ApiError && subscription.error.status === 404 ? (
            <EmptyState
              description="L’abonnement initial est provisionné automatiquement lors de la création du compte."
              title="Aucun abonnement"
            />
          ) : (
            <ErrorState retry={() => void subscription.refetch()} />
          )
        ) : data ? (
          <div className="grid gap-5 lg:grid-cols-[1fr_0.85fr]">
            {data.status === "PAST_DUE" || data.status === "SUSPENDED" ? (
              <section
                className={`border-s-2 ps-4 lg:col-span-2 ${
                  data.status === "SUSPENDED" ? "border-destructive" : "border-warning"
                }`}
                role="status"
              >
                <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <h2 className="font-semibold">
                      {data.status === "SUSPENDED" ? "Abonnement suspendu" : "Recouvrement en cours"}
                    </h2>
                    <p className="mt-1 text-sm text-muted-foreground">
                      {data.status === "SUSPENDED"
                        ? `Suspendu depuis le ${dateTime(data.suspendedAt)}. Les données sont conservées sans accès opérationnel.`
                        : `Échu le ${dateTime(data.pastDueAt)} · accès maintenu jusqu’au ${dateTime(data.graceEndsAt)}.`}
                    </p>
                  </div>
                  {session.can(adminPermissions.billingListInvoices) ? (
                    <Button asChild className="self-start" size="sm" variant="outline">
                      <Link to={`/admin/billing?view=invoices&accountId=${data.accountId}`}>Voir les factures</Link>
                    </Button>
                  ) : null}
                </div>
              </section>
            ) : null}
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
              {data.entitlementSnapshot?.commercialPolicyEvaluation ? (
                <div className="mt-6 border-t pt-5">
                  <AdminCommercialPolicyTerms
                    evaluation={data.entitlementSnapshot.commercialPolicyEvaluation}
                    title="Conditions commerciales détenues"
                  />
                </div>
              ) : null}
            </section>
            <section className="rounded-xl border bg-card p-5">
              <div>
                <h2 className="text-sm font-semibold">Composition détenue</h2>
                <p className="mt-1 text-xs text-muted-foreground">
                  Add-ons et capacités enregistrés dans cet abonnement.
                </p>
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
        ) : null
      ) : null}
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
                      <CheckoutDialog checkout={operation.checkout} />
                    ) : null}
                    {subscriptionOperationCanBeCancelled(operation) &&
                    session.can(adminPermissions.subscriptionsCancelChange) ? (
                      <CancelChangeDialog accountId={accountId} operation={operation} />
                    ) : null}
                  </div>
                )}
                renderDetails={(operation) => <OperationDetails operation={operation} />}
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
  const canOpen = adminSubscriptionDetailSurfacePermissions.some(session.can);
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
          <>
            {session.can(adminPermissions.subscriptionsListChangeJobs) ? (
              <Button asChild variant="outline">
                <Link to="/admin/subscription-jobs">
                  <ArrowsClockwiseIcon />
                  Changements en lot
                </Link>
              </Button>
            ) : null}
            {session.can(adminPermissions.subscriptionsLookupAccountOwnerEmail) ? (
              <SubscriptionOwnerEmailLookup />
            ) : null}
          </>
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
    <div className="min-w-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-1 whitespace-pre-wrap break-words font-medium">{value}</dd>
    </div>
  );
}
