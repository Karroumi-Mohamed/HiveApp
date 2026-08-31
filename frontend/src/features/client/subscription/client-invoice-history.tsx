import { ArrowLeftIcon, ArrowRightIcon, FileTextIcon } from "@phosphor-icons/react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import type { SortingState } from "@tanstack/react-table";
import { useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { clientApi } from "@/api/client-api";
import type { BillingInvoiceRow } from "@/api/contracts";
import { clientPermissions } from "@/auth/permissions";
import { useClientSession } from "@/auth/session-provider";
import { createDataColumns, DataTable, SortHeader } from "@/components/patterns/data-table";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { RowAction } from "@/components/patterns/row-action";
import { StatusBadge } from "@/components/patterns/status-badge";
import { TableActionsCell, tableActionsColumnMeta } from "@/components/patterns/table-actions-cell";
import { Button } from "@/components/ui/button";
import {
  billingCycleLabel,
  invoiceStatusPresentation,
  paymentStatusPresentation,
} from "@/features/admin/billing/billing-presentation";
import { clientBillingKeys } from "@/features/admin/billing/billing-query";
import { formatExactMoney } from "@/lib/exact-decimal";

const dateTime = (value: string | null) =>
  value ? new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "—";

export function ClientInvoiceHistory() {
  const session = useClientSession();
  const [params, setParams] = useSearchParams();
  const selectedId = params.get("invoice");
  const [page, setPage] = useState(0);
  const [sorting, setSorting] = useState<SortingState>([{ id: "issuedAt", desc: true }]);
  const context = { companyId: session.selectedCompanyId, isB2B: session.isB2B };
  const request = {
    page,
    size: 20,
    sort: (sorting[0]?.id ?? "issuedAt") as "issuedAt" | "invoiceNumber" | "status" | "amount",
    direction: sorting[0]?.desc === false ? ("asc" as const) : ("desc" as const),
  };
  const invoices = useQuery({
    queryKey: clientBillingKeys.invoices(context, request),
    queryFn: () => clientApi.subscriptionInvoices(request),
    enabled: session.can(clientPermissions.subscriptionListInvoices) && !selectedId,
    placeholderData: keepPreviousData,
  });
  const detail = useQuery({
    queryKey: clientBillingKeys.invoice(context, selectedId ?? ""),
    queryFn: () => clientApi.subscriptionInvoice(selectedId ?? ""),
    enabled: Boolean(selectedId) && session.can(clientPermissions.subscriptionReadInvoice),
    retry: false,
  });
  const column = useMemo(() => createDataColumns<BillingInvoiceRow>(), []);
  const canOpen = session.can(clientPermissions.subscriptionReadInvoice);
  const columns = useMemo(
    () =>
      column.columns([
        column.accessor("invoiceNumber", {
          meta: { headerClassName: "min-w-44" },
          header: ({ column: item }) => <SortHeader column={item}>Facture</SortHeader>,
          cell: ({ row }) => (
            <span>
              <span className="block font-medium">{row.original.invoiceNumber}</span>
              <span className="mt-0.5 block text-xs text-muted-foreground">
                {billingCycleLabel[row.original.billingCycle]}
              </span>
            </span>
          ),
        }),
        column.accessor("totalAmount", {
          meta: { headerClassName: "w-44", cellClassName: "w-44" },
          header: ({ column: item }) => <SortHeader column={item}>Montant</SortHeader>,
          cell: ({ row }) => (
            <span className="font-medium tabular-nums">
              {formatExactMoney(row.original.totalAmount, row.original.currencyCode)}
            </span>
          ),
        }),
        column.accessor("status", {
          meta: { headerClassName: "w-44", cellClassName: "w-44" },
          header: ({ column: item }) => <SortHeader column={item}>État</SortHeader>,
          cell: ({ row }) => {
            const presentation = invoiceStatusPresentation[row.original.status];
            return (
              <StatusBadge dot={false} tone={presentation.tone}>
                {presentation.label}
              </StatusBadge>
            );
          },
        }),
        column.accessor("issuedAt", {
          meta: { headerClassName: "w-48", cellClassName: "w-48" },
          header: ({ column: item }) => <SortHeader column={item}>Émise le</SortHeader>,
          cell: ({ row }) => dateTime(row.original.issuedAt),
        }),
        column.display({
          id: "actions",
          meta: tableActionsColumnMeta(1),
          header: "Actions",
          cell: ({ row }) => (
            <TableActionsCell label={`Actions pour ${row.original.invoiceNumber}`}>
              <RowAction
                disabled={!canOpen}
                disabledLabel="Détail non autorisé"
                icon={<ArrowRightIcon className="rtl:rotate-180" />}
                label="Ouvrir"
                onClick={() => {
                  const next = new URLSearchParams(params);
                  next.set("invoice", row.original.id);
                  setParams(next);
                }}
              />
            </TableActionsCell>
          ),
        }),
      ]),
    [canOpen, column, params, setParams],
  );

  if (selectedId) {
    if (!canOpen) return <EmptyState title="Détail indisponible" />;
    if (detail.isLoading) return <LoadingState rows={6} />;
    if (detail.isError)
      return <ErrorState retry={() => void detail.refetch()} title="Impossible de charger la facture" />;
    if (!detail.data) return <EmptyState title="Facture introuvable" />;
    const { invoice, lines, payments } = detail.data;
    const presentation = invoiceStatusPresentation[invoice.status];
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between gap-3">
          <Button
            onClick={() => {
              const next = new URLSearchParams(params);
              next.delete("invoice");
              setParams(next);
            }}
            size="sm"
            variant="ghost"
          >
            <ArrowLeftIcon className="rtl:rotate-180" />
            Toutes les factures
          </Button>
          {session.can(clientPermissions.subscriptionReadInvoiceDocument) ? (
            <Button asChild size="sm" variant="ghost">
              <Link to={`/app/subscription/invoices/${selectedId}/document`}>
                <FileTextIcon />
                Document
              </Link>
            </Button>
          ) : null}
        </div>
        <section className="overflow-hidden rounded-xl border bg-card">
          <div className="flex flex-col gap-3 border-b p-4 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <h2 className="text-xl font-semibold">{invoice.invoiceNumber}</h2>
              <p className="mt-1 text-2xl font-semibold tabular-nums">
                {formatExactMoney(invoice.totalAmount, invoice.currencyCode)}
              </p>
            </div>
            <StatusBadge dot={false} tone={presentation.tone}>
              {presentation.label}
            </StatusBadge>
          </div>
          <dl className="grid divide-y sm:grid-cols-3 sm:divide-x sm:divide-y-0">
            <div className="p-4">
              <dt className="text-xs text-muted-foreground">Cycle</dt>
              <dd className="mt-1 font-medium">{billingCycleLabel[invoice.billingCycle]}</dd>
            </div>
            <div className="p-4">
              <dt className="text-xs text-muted-foreground">Période</dt>
              <dd className="mt-1 text-sm">
                {dateTime(invoice.periodStart)} – {dateTime(invoice.periodEnd)}
              </dd>
            </div>
            <div className="p-4">
              <dt className="text-xs text-muted-foreground">Règlement</dt>
              <dd className="mt-1 text-sm">{dateTime(invoice.settledAt)}</dd>
            </div>
          </dl>
        </section>
        <section className="overflow-hidden rounded-xl border bg-card">
          <h2 className="border-b px-4 py-3 font-semibold">Détail</h2>
          <div className="divide-y">
            {lines.map((line) => (
              <div className="grid gap-2 p-4 sm:grid-cols-[1fr_auto_auto] sm:items-center sm:gap-8" key={line.id}>
                <div>
                  <p className="font-medium">{line.sourceName}</p>
                  <p className="mt-0.5 text-xs text-muted-foreground">Quantité {line.quantity}</p>
                </div>
                <p className="text-sm text-muted-foreground">
                  {formatExactMoney(line.unitAmount, line.currencyCode)} / unité
                </p>
                <p className="font-semibold tabular-nums">{formatExactMoney(line.lineAmount, line.currencyCode)}</p>
              </div>
            ))}
          </div>
        </section>
        {payments.length ? (
          <section className="overflow-hidden rounded-xl border bg-card">
            <h2 className="border-b px-4 py-3 font-semibold">Paiements</h2>
            <div className="divide-y">
              {payments.map((payment) => {
                const state = paymentStatusPresentation[payment.status];
                return (
                  <div
                    className="flex items-center justify-between gap-4 p-4"
                    key={`${payment.kind}-${payment.status}-${payment.amount}-${payment.currencyCode}-${payment.completedAt ?? "pending"}`}
                  >
                    <div>
                      <p className="font-medium">
                        {payment.kind === "PROVIDER" ? "Paiement en ligne" : "Règlement manuel"}
                      </p>
                      <p className="mt-1 text-xs text-muted-foreground">{dateTime(payment.completedAt)}</p>
                    </div>
                    <div className="text-end">
                      <p className="font-semibold tabular-nums">
                        {formatExactMoney(payment.amount, payment.currencyCode)}
                      </p>
                      <StatusBadge className="mt-1" dot={false} tone={state.tone}>
                        {state.label}
                      </StatusBadge>
                    </div>
                  </div>
                );
              })}
            </div>
          </section>
        ) : null}
      </div>
    );
  }

  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      {invoices.isLoading ? (
        <div className="p-5">
          <LoadingState rows={6} />
        </div>
      ) : invoices.isError ? (
        <ErrorState retry={() => void invoices.refetch()} />
      ) : (
        <>
          <DataTable
            columns={columns}
            data={invoices.data?.content ?? []}
            emptyState={<EmptyState title="Aucune facture" />}
            getRowId={(invoice) => invoice.id}
            onSortingChange={(next) => {
              setSorting(next);
              setPage(0);
            }}
            sorting={sorting}
          />
          <PaginationBar
            onPageChange={setPage}
            page={invoices.data?.page ?? 0}
            totalElements={invoices.data?.totalElements ?? 0}
            totalPages={invoices.data?.totalPages ?? 0}
          />
        </>
      )}
    </section>
  );
}
