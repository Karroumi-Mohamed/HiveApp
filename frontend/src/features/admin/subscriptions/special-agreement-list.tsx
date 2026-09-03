import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useDeferredValue, useMemo, useState } from "react";
import { Link } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { SpecialAgreementDetail, SpecialAgreementSummary } from "@/api/contracts";
import { ApiError } from "@/api/http";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable, DataTableExpander } from "@/components/patterns/data-table";
import { PaginationBar } from "@/components/patterns/pagination-bar";
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
import { Textarea } from "@/components/ui/textarea";
import { capacityUnitLabel } from "@/features/admin/commercial/commercial-presentation";
import { adminCommercialKeys, invalidateAdminSubscriptionEntitlement } from "@/features/commercial/commercial-query";
import {
  specialAgreementDateTime,
  specialAgreementEnd,
  specialAgreementPricing,
  specialAgreementStatus,
} from "@/features/commercial/special-agreement-presentation";
import { formatExactMoney } from "@/lib/exact-decimal";

function AgreementAction({
  accountId,
  agreement,
  action,
}: {
  accountId: string;
  agreement: SpecialAgreementDetail;
  action: "settle" | "cancel" | "retry" | "resolve";
}) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [reference, setReference] = useState("");
  const mutation = useMutation({
    mutationFn: async () => {
      if (action === "settle" && agreement.checkout) {
        return adminApi.confirmCheckout(agreement.checkout.id, { reference: reference.trim(), reason: reason.trim() });
      }
      if (action === "cancel") return adminApi.cancelSpecialAgreement(accountId, agreement.summary.id, reason.trim());
      if (action === "retry") return adminApi.retrySpecialAgreement(accountId, agreement.summary.id, reason.trim());
      if (action === "resolve")
        return adminApi.resolveSpecialAgreementManualReview(accountId, agreement.summary.id, reason.trim());
      throw new Error("No agreement action is available");
    },
    onSuccess: () => {
      setReason("");
      setReference("");
      setOpen(false);
      void queryClient.invalidateQueries({ queryKey: adminCommercialKeys.subscriptions.agreements(accountId) });
      void queryClient.invalidateQueries({
        queryKey: adminCommercialKeys.subscriptions.agreement(accountId, agreement.summary.id),
      });
      void invalidateAdminSubscriptionEntitlement(queryClient);
      toast.success(
        action === "settle"
          ? "Règlement enregistré"
          : action === "cancel"
            ? "Accord annulé"
            : action === "resolve"
              ? "Révision manuelle clôturée"
              : "Traitement relancé",
      );
    },
    onError: (error) =>
      toast.error(error instanceof ApiError ? error.message : "L’action n’a pas pu être enregistrée."),
  });
  const title =
    action === "settle"
      ? "Enregistrer le règlement"
      : action === "cancel"
        ? "Annuler cet accord"
        : action === "resolve"
          ? "Clore la révision manuelle"
          : "Relancer le traitement";
  const buttonLabel =
    action === "settle"
      ? "Marquer payé"
      : action === "cancel"
        ? "Annuler l’accord"
        : action === "resolve"
          ? "Clore après décision"
          : "Réessayer";
  return (
    <Dialog
      onOpenChange={(nextOpen) => {
        setOpen(nextOpen);
        if (!nextOpen && !mutation.isPending) {
          setReason("");
          setReference("");
        }
      }}
      open={open}
    >
      <DialogTrigger asChild>
        <Button size="sm" variant={action === "cancel" ? "destructive" : "outline"}>
          {buttonLabel}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>
            {action === "settle"
              ? `Confirmez la réception de ${formatExactMoney(agreement.summary.agreedTermAmount, agreement.summary.currencyCode)} avec sa référence externe.`
              : action === "resolve"
                ? "Cette action ne réussit que si une nouvelle décision d’abonnement a déjà été appliquée au compte."
                : "Cette opération sera conservée dans l’historique d’audit."}
          </DialogDescription>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            mutation.mutate();
          }}
        >
          {action === "settle" ? (
            <div className="space-y-2">
              <Label htmlFor={`agreement-reference-${agreement.summary.id}`}>Référence du règlement</Label>
              <Input
                id={`agreement-reference-${agreement.summary.id}`}
                maxLength={255}
                onChange={(event) => setReference(event.target.value)}
                value={reference}
              />
            </div>
          ) : null}
          <div className="space-y-2">
            <Label htmlFor={`agreement-reason-${agreement.summary.id}-${action}`}>Justification</Label>
            <Textarea
              id={`agreement-reason-${agreement.summary.id}-${action}`}
              maxLength={2000}
              onChange={(event) => setReason(event.target.value)}
              rows={3}
              value={reason}
            />
          </div>
          <div className="flex justify-end">
            <Button
              disabled={mutation.isPending || reason.trim().length < 3 || (action === "settle" && !reference.trim())}
              type="submit"
              variant={action === "cancel" ? "destructive" : "default"}
            >
              {mutation.isPending ? "Traitement…" : buttonLabel}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function AgreementDetail({ accountId, agreementId }: { accountId: string; agreementId: string }) {
  const session = useAdminSession();
  const agreement = useQuery({
    queryKey: adminCommercialKeys.subscriptions.agreement(accountId, agreementId),
    queryFn: () => adminApi.specialAgreement(accountId, agreementId),
    retry: false,
  });
  if (agreement.isLoading)
    return (
      <div className="p-5">
        <LoadingState rows={2} />
      </div>
    );
  if (agreement.isError || !agreement.data) return <ErrorState retry={() => void agreement.refetch()} />;
  const item = agreement.data;
  return (
    <div className="border-s-2 border-primary/40 px-5 py-4">
      <dl className="grid gap-x-8 gap-y-4 text-sm sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <dt className="text-xs text-muted-foreground">Add-ons</dt>
          <dd className="mt-1 font-medium">{item.addOns.map((value) => value.name).join(", ") || "—"}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Packs de capacité</dt>
          <dd className="mt-1 font-medium">
            {item.quotaPackages.map((value) => `${value.name} × ${value.quantity}`).join(", ") || "—"}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Valeur catalogue</dt>
          <dd className="mt-1 font-medium">
            {item.catalogueTermAmount
              ? formatExactMoney(item.catalogueTermAmount, item.summary.currencyCode)
              : "Sur mesure"}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Après la période</dt>
          <dd className="mt-1 font-medium">{specialAgreementEnd[item.summary.endInstruction]}</dd>
        </div>
        <div className="sm:col-span-2 lg:col-span-4">
          <dt className="text-xs text-muted-foreground">Motif</dt>
          <dd className="mt-1 font-medium">{item.reason}</dd>
        </div>
        {item.quotaBonuses.length ? (
          <div className="sm:col-span-2 lg:col-span-4">
            <dt className="text-xs text-muted-foreground">Capacité privée accordée</dt>
            <dd className="mt-1 flex flex-wrap gap-x-5 gap-y-1 font-medium">
              {item.quotaBonuses.map((value) => (
                <span key={`${value.featureCode}:${value.resource}`}>
                  +{value.quantity} {capacityUnitLabel(value.resource)}
                </span>
              ))}
            </dd>
          </div>
        ) : null}
      </dl>
      {item.attentionReason ? (
        <p className="mt-4 border-s-2 border-warning ps-3 text-sm">{item.attentionReason}</p>
      ) : null}
      <div className="mt-5 flex flex-wrap justify-end gap-2 border-t pt-5">
        {item.summary.availableActions.settleManually && session.can(adminPermissions.subscriptionsConfirmCheckout) ? (
          <AgreementAction accountId={accountId} action="settle" agreement={item} />
        ) : null}
        {item.summary.availableActions.cancel && session.can(adminPermissions.subscriptionsCancelSpecialAgreement) ? (
          <AgreementAction accountId={accountId} action="cancel" agreement={item} />
        ) : null}
        {(item.summary.availableActions.retryStart || item.summary.availableActions.retryEnd) &&
        session.can(adminPermissions.subscriptionsRetrySpecialAgreement) ? (
          <AgreementAction accountId={accountId} action="retry" agreement={item} />
        ) : null}
        {item.summary.availableActions.resolveManualReview &&
        session.can(adminPermissions.subscriptionsResolveSpecialAgreementManualReview) ? (
          <AgreementAction accountId={accountId} action="resolve" agreement={item} />
        ) : null}
      </div>
    </div>
  );
}

export function SpecialAgreementList({ accountId }: { accountId?: string }) {
  const session = useAdminSession();
  const canReadDetail = session.can(adminPermissions.subscriptionsReadSpecialAgreement);
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState<SpecialAgreementSummary["status"] | "ALL">("ALL");
  const [search, setSearch] = useState("");
  const deferredSearch = useDeferredValue(search.trim());
  const request = {
    ...(accountId ? {} : { search: deferredSearch || undefined }),
    status: filter === "ALL" ? undefined : filter,
    page,
    size: 10,
  };
  const agreements = useQuery({
    queryKey: accountId
      ? adminCommercialKeys.subscriptions.agreements(accountId, request)
      : adminCommercialKeys.subscriptions.allAgreements(request),
    queryFn: () =>
      accountId ? adminApi.specialAgreements(accountId, request) : adminApi.allSpecialAgreements(request),
    retry: false,
  });
  const column = useMemo(() => createDataColumns<SpecialAgreementSummary>(), []);
  const columns = useMemo(
    () =>
      column.columns([
        ...(canReadDetail
          ? [
              column.display({
                id: "expand",
                meta: { headerClassName: "w-12", cellClassName: "w-12" },
                header: "",
                cell: ({ row }) => (
                  <DataTableExpander collapseLabel="Masquer l’accord" expandLabel="Afficher l’accord" row={row} />
                ),
              }),
            ]
          : []),
        ...(accountId
          ? []
          : [
              column.display({
                id: "account",
                header: "Compte",
                cell: ({ row }) => (
                  <Button asChild className="h-auto justify-start p-0 font-medium" variant="link">
                    <Link to={`/admin/subscriptions/${row.original.accountId}`}>{row.original.accountName}</Link>
                  </Button>
                ),
              }),
            ]),
        column.display({
          id: "plan",
          header: "Conditions",
          cell: ({ row }) => (
            <span>
              <strong className="block">{row.original.planName}</strong>
              <span className="text-xs text-muted-foreground">{specialAgreementPricing[row.original.pricingMode]}</span>
            </span>
          ),
        }),
        column.display({
          id: "period",
          header: "Période",
          cell: ({ row }) => (
            <span className="text-sm">
              <span className="block">{specialAgreementDateTime(row.original.startsAt)}</span>
              <span className="text-xs text-muted-foreground">au {specialAgreementDateTime(row.original.endsAt)}</span>
            </span>
          ),
        }),
        column.display({
          id: "amount",
          header: "Montant",
          cell: ({ row }) => formatExactMoney(row.original.agreedTermAmount, row.original.currencyCode),
        }),
        column.display({
          id: "status",
          header: "État",
          cell: ({ row }) => (
            <StatusBadge dot={false} tone={specialAgreementStatus[row.original.status].tone}>
              {specialAgreementStatus[row.original.status].label}
            </StatusBadge>
          ),
        }),
      ]),
    [accountId, canReadDetail, column],
  );
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <div className="grid gap-3 border-b p-4 sm:grid-cols-[minmax(0,1fr)_220px] sm:items-center">
        {accountId ? (
          <h2 className="text-sm font-semibold">Accords spéciaux</h2>
        ) : (
          <Input
            aria-label="Rechercher des accords spéciaux"
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(0);
            }}
            placeholder="Compte ou forfait…"
            value={search}
          />
        )}
        <Select
          onValueChange={(value) => {
            setFilter(value as SpecialAgreementSummary["status"] | "ALL");
            setPage(0);
          }}
          value={filter}
        >
          <SelectTrigger aria-label="État de l’accord">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">Tous les états</SelectItem>
            {(Object.keys(specialAgreementStatus) as SpecialAgreementSummary["status"][]).map((value) => (
              <SelectItem key={value} value={value}>
                {specialAgreementStatus[value].label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      {agreements.isLoading ? (
        <div className="p-5">
          <LoadingState rows={3} />
        </div>
      ) : agreements.isError ? (
        <ErrorState retry={() => void agreements.refetch()} />
      ) : (
        <>
          <div className="hidden md:block">
            <DataTable
              columns={columns}
              data={agreements.data?.content ?? []}
              emptyState={<EmptyState title="Aucun accord spécial" />}
              getRowId={(row) => row.id}
              renderExpandedRow={
                canReadDetail ? (row) => <AgreementDetail accountId={row.accountId} agreementId={row.id} /> : undefined
              }
            />
          </div>
          <div className="divide-y md:hidden">
            {(agreements.data?.content ?? []).map((item) => (
              <div key={item.id}>
                <div className="flex items-center justify-between gap-3 p-4">
                  <div>
                    {!accountId ? (
                      <span className="mb-1 block text-xs text-muted-foreground">{item.accountName}</span>
                    ) : null}
                    <strong>{item.planName}</strong>
                    <p className="text-xs text-muted-foreground">
                      {specialAgreementDateTime(item.startsAt)} → {specialAgreementDateTime(item.endsAt)}
                    </p>
                  </div>
                  <StatusBadge dot={false} tone={specialAgreementStatus[item.status].tone}>
                    {specialAgreementStatus[item.status].label}
                  </StatusBadge>
                </div>
                {canReadDetail ? (
                  <details className="group border-t">
                    <summary className="cursor-pointer list-none px-4 py-3 text-sm font-medium">
                      Afficher les détails
                    </summary>
                    <AgreementDetail accountId={item.accountId} agreementId={item.id} />
                  </details>
                ) : null}
              </div>
            ))}
            {!agreements.data?.content.length ? <EmptyState title="Aucun accord spécial" /> : null}
          </div>
          <PaginationBar
            onPageChange={setPage}
            page={agreements.data?.page ?? 0}
            totalElements={agreements.data?.totalElements ?? 0}
            totalPages={agreements.data?.totalPages ?? 0}
          />
        </>
      )}
    </section>
  );
}
