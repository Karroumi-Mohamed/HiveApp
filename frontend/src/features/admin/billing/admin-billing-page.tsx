import { ArrowClockwiseIcon, ArrowRightIcon, MagnifyingGlassIcon } from "@phosphor-icons/react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { SortingState } from "@tanstack/react-table";
import { useMemo, useState } from "react";
import { useSearchParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type {
  BillingInvoiceRow,
  BillingInvoiceStatus,
  BillingOutboxOperation,
  BillingOutboxRow,
  BillingOutboxStatus,
  BillingProviderEventRow,
  BillingProviderEventStatus,
} from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable, SortHeader } from "@/components/patterns/data-table";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { RowAction } from "@/components/patterns/row-action";
import { SectionTabs } from "@/components/patterns/section-tabs";
import { StatusBadge } from "@/components/patterns/status-badge";
import { TableActionsCell, tableActionsColumnMeta } from "@/components/patterns/table-actions-cell";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { formatExactMoney } from "@/lib/exact-decimal";
import {
  billingCycleLabel,
  billingErrorMessage,
  billingOperationLabel,
  invoiceStatusPresentation,
  outboxStatusPresentation,
  providerEventStatusPresentation,
  providerPaymentStatusPresentation,
} from "./billing-presentation";
import { adminBillingKeys } from "./billing-query";

const dateTime = (value: string | null) =>
  value ? new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "—";

function InvoiceList() {
  const session = useAdminSession();
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<BillingInvoiceStatus | "all">("all");
  const [page, setPage] = useState(0);
  const [sorting, setSorting] = useState<SortingState>([{ id: "issuedAt", desc: true }]);
  const request = {
    search: search.trim() || undefined,
    status: status === "all" ? undefined : status,
    page,
    size: 20,
    sort: (sorting[0]?.id ?? "issuedAt") as "issuedAt" | "invoiceNumber" | "status" | "amount",
    direction: sorting[0]?.desc === false ? ("asc" as const) : ("desc" as const),
  };
  const invoices = useQuery({
    queryKey: adminBillingKeys.invoices(request),
    queryFn: () => adminApi.billingInvoices(request),
    enabled: session.can(adminPermissions.billingListInvoices),
    placeholderData: keepPreviousData,
  });
  const canReadAccount = session.can(adminPermissions.billingReadAccountIdentity);
  const canOpen = session.can(adminPermissions.billingReadInvoice) || session.can(adminPermissions.billingReadPayments);
  const column = useMemo(() => createDataColumns<BillingInvoiceRow>(), []);
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
        ...(canReadAccount
          ? [
              column.display({
                id: "account",
                meta: { headerClassName: "min-w-48" },
                header: "Compte",
                cell: ({ row }) => row.original.account?.name ?? <span className="text-muted-foreground">—</span>,
              }),
            ]
          : []),
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
          cell: ({ row }) => <time dateTime={row.original.issuedAt}>{dateTime(row.original.issuedAt)}</time>,
        }),
        column.display({
          id: "actions",
          meta: tableActionsColumnMeta(1),
          header: "Actions",
          cell: ({ row }) => (
            <TableActionsCell label={`Actions pour ${row.original.invoiceNumber}`}>
              <RowAction
                disabled={!canOpen}
                disabledLabel="Détail de facture non autorisé"
                icon={<ArrowRightIcon className="rtl:rotate-180" />}
                label="Ouvrir"
                to={canOpen ? `/admin/billing/invoices/${row.original.id}` : undefined}
              />
            </TableActionsCell>
          ),
        }),
      ]),
    [canOpen, canReadAccount, column],
  );

  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <div className="flex flex-col gap-3 border-b p-4 sm:flex-row">
        <label className="relative flex-1 sm:max-w-sm" htmlFor="billing-invoice-search">
          <MagnifyingGlassIcon className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <span className="sr-only">Rechercher une facture</span>
          <Input
            className="ps-9"
            id="billing-invoice-search"
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(0);
            }}
            placeholder={canReadAccount ? "Numéro ou compte…" : "Numéro de facture…"}
            value={search}
          />
        </label>
        <Select
          onValueChange={(value) => {
            setStatus(value as typeof status);
            setPage(0);
          }}
          value={status}
        >
          <SelectTrigger aria-label="État de facture" className="w-full sm:w-52">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">Tous les états</SelectItem>
            {Object.entries(invoiceStatusPresentation).map(([value, presentation]) => (
              <SelectItem key={value} value={value}>
                {presentation.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
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

function ReconciliationList() {
  const session = useAdminSession();
  const [status, setStatus] = useState<BillingOutboxStatus | "all">("all");
  const [operation, setOperation] = useState<BillingOutboxOperation | "all">("all");
  const [page, setPage] = useState(0);
  const [sorting, setSorting] = useState<SortingState>([{ id: "createdAt", desc: true }]);
  const request = {
    status: status === "all" ? undefined : status,
    operation: operation === "all" ? undefined : operation,
    page,
    size: 20,
    sort: (sorting[0]?.id ?? "createdAt") as "createdAt" | "nextAttemptAt" | "status" | "operation" | "attemptCount",
    direction: sorting[0]?.desc === false ? ("asc" as const) : ("desc" as const),
  };
  const commands = useQuery({
    queryKey: adminBillingKeys.reconciliation(request),
    queryFn: () => adminApi.billingReconciliation(request),
    enabled: session.can(adminPermissions.billingListReconciliation),
    placeholderData: keepPreviousData,
  });
  const column = useMemo(() => createDataColumns<BillingOutboxRow>(), []);
  const columns = useMemo(
    () =>
      column.columns([
        column.accessor("operation", {
          meta: { headerClassName: "min-w-44" },
          header: ({ column: item }) => <SortHeader column={item}>Opération</SortHeader>,
          cell: ({ row }) => (
            <span>
              <span className="block font-medium">{billingOperationLabel[row.original.operation]}</span>
              <span className="mt-0.5 block font-mono text-xs text-muted-foreground">{row.original.aggregateId}</span>
            </span>
          ),
        }),
        column.accessor("status", {
          meta: { headerClassName: "w-44", cellClassName: "w-44" },
          header: ({ column: item }) => <SortHeader column={item}>État</SortHeader>,
          cell: ({ row }) => {
            const presentation = outboxStatusPresentation[row.original.status];
            return (
              <StatusBadge dot={false} tone={presentation.tone}>
                {presentation.label}
              </StatusBadge>
            );
          },
        }),
        column.accessor("attemptCount", {
          meta: { headerClassName: "w-32", cellClassName: "w-32" },
          header: ({ column: item }) => <SortHeader column={item}>Tentatives</SortHeader>,
          cell: ({ row }) => <span className="tabular-nums">{row.original.attemptCount}</span>,
        }),
        column.accessor("nextAttemptAt", {
          meta: { headerClassName: "w-48", cellClassName: "w-48" },
          header: ({ column: item }) => <SortHeader column={item}>Prochaine tentative</SortHeader>,
          cell: ({ row }) => dateTime(row.original.nextAttemptAt),
        }),
        column.accessor("createdAt", {
          meta: { headerClassName: "w-48", cellClassName: "w-48" },
          header: ({ column: item }) => <SortHeader column={item}>Créée le</SortHeader>,
          cell: ({ row }) => dateTime(row.original.createdAt),
        }),
        column.display({
          id: "detail",
          header: "Dernier résultat",
          cell: ({ row }) =>
            row.original.lastError ? (
              <span className="line-clamp-2 max-w-80 text-sm text-destructive">{row.original.lastError}</span>
            ) : (
              <span className="text-muted-foreground">—</span>
            ),
        }),
      ]),
    [column],
  );
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <div className="flex flex-col gap-3 border-b p-4 sm:flex-row">
        <Select
          onValueChange={(value) => {
            setStatus(value as typeof status);
            setPage(0);
          }}
          value={status}
        >
          <SelectTrigger aria-label="État de commande" className="w-full sm:w-52">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">Tous les états</SelectItem>
            {Object.entries(outboxStatusPresentation).map(([value, presentation]) => (
              <SelectItem key={value} value={value}>
                {presentation.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select
          onValueChange={(value) => {
            setOperation(value as typeof operation);
            setPage(0);
          }}
          value={operation}
        >
          <SelectTrigger aria-label="Type d’opération" className="w-full sm:w-52">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">Toutes les opérations</SelectItem>
            {Object.entries(billingOperationLabel).map(([value, label]) => (
              <SelectItem key={value} value={value}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      {commands.isLoading ? (
        <div className="p-5">
          <LoadingState rows={6} />
        </div>
      ) : commands.isError ? (
        <ErrorState retry={() => void commands.refetch()} />
      ) : (
        <>
          <DataTable
            columns={columns}
            data={commands.data?.content ?? []}
            emptyState={<EmptyState title="Aucune commande" />}
            getRowId={(item) => item.id}
            onSortingChange={(next) => {
              setSorting(next);
              setPage(0);
            }}
            sorting={sorting}
          />
          <PaginationBar
            onPageChange={setPage}
            page={commands.data?.page ?? 0}
            totalElements={commands.data?.totalElements ?? 0}
            totalPages={commands.data?.totalPages ?? 0}
          />
        </>
      )}
    </section>
  );
}

function ProviderEventList() {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<BillingProviderEventStatus | "all">("all");
  const [page, setPage] = useState(0);
  const [sorting, setSorting] = useState<SortingState>([{ id: "receivedAt", desc: true }]);
  const request = {
    status: status === "all" ? undefined : status,
    page,
    size: 20,
    sort: (sorting[0]?.id ?? "receivedAt") as "receivedAt" | "occurredAt" | "status" | "operation" | "provider",
    direction: sorting[0]?.desc === false ? ("asc" as const) : ("desc" as const),
  };
  const events = useQuery({
    queryKey: adminBillingKeys.providerEvents(request),
    queryFn: () => adminApi.billingProviderEvents(request),
    enabled: session.can(adminPermissions.billingListProviderEvents),
    placeholderData: keepPreviousData,
  });
  const reprocess = useMutation({
    mutationFn: adminApi.reprocessBillingProviderEvent,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminBillingKeys.all });
      toast.success("Événement rapproché à nouveau");
    },
    onError: (error) => toast.error(billingErrorMessage(error)),
  });
  const reprocessEvent = reprocess.mutate;
  const canReprocess = session.can(adminPermissions.billingReconcileProviderEvent);
  const column = useMemo(() => createDataColumns<BillingProviderEventRow>(), []);
  const columns = useMemo(
    () =>
      column.columns([
        column.accessor("provider", {
          meta: { headerClassName: "min-w-48" },
          header: ({ column: item }) => <SortHeader column={item}>Fournisseur</SortHeader>,
          cell: ({ row }) => (
            <span>
              <span className="block font-medium">{row.original.provider}</span>
              <span className="mt-0.5 block max-w-64 truncate font-mono text-xs text-muted-foreground">
                {row.original.eventId}
              </span>
            </span>
          ),
        }),
        column.accessor("operation", {
          header: ({ column: item }) => <SortHeader column={item}>Opération</SortHeader>,
          cell: ({ row }) => billingOperationLabel[row.original.operation],
        }),
        column.display({
          id: "amount",
          header: "Montant",
          cell: ({ row }) => (
            <span className="font-medium tabular-nums">
              {formatExactMoney(row.original.amount, row.original.currencyCode)}
            </span>
          ),
        }),
        column.accessor("providerStatus", {
          meta: { headerClassName: "w-40", cellClassName: "w-40" },
          header: "Résultat fournisseur",
          cell: ({ row }) => {
            const presentation = providerPaymentStatusPresentation[row.original.providerStatus];
            return (
              <StatusBadge dot={false} tone={presentation.tone}>
                {presentation.label}
              </StatusBadge>
            );
          },
        }),
        column.accessor("processingStatus", {
          meta: { headerClassName: "w-44", cellClassName: "w-44" },
          header: ({ column: item }) => <SortHeader column={item}>Rapprochement</SortHeader>,
          cell: ({ row }) => {
            const presentation = providerEventStatusPresentation[row.original.processingStatus];
            return (
              <StatusBadge dot={false} tone={presentation.tone}>
                {presentation.label}
              </StatusBadge>
            );
          },
        }),
        column.accessor("occurredAt", {
          meta: { headerClassName: "w-48", cellClassName: "w-48" },
          header: ({ column: item }) => <SortHeader column={item}>Survenu le</SortHeader>,
          cell: ({ row }) => dateTime(row.original.occurredAt),
        }),
        column.display({
          id: "attention",
          header: "Attention",
          cell: ({ row }) =>
            row.original.attentionReason ? (
              <span className="line-clamp-2 max-w-72 text-sm text-warning">{row.original.attentionReason}</span>
            ) : (
              <span className="text-muted-foreground">—</span>
            ),
        }),
        column.display({
          id: "actions",
          meta: tableActionsColumnMeta(1),
          header: "Actions",
          cell: ({ row }) => (
            <TableActionsCell label={`Actions pour ${row.original.eventId}`}>
              <RowAction
                disabled={!canReprocess || row.original.processingStatus === "APPLIED" || reprocess.isPending}
                disabledLabel={
                  row.original.processingStatus === "APPLIED"
                    ? "Événement déjà rapproché"
                    : "Rapprochement non autorisé"
                }
                icon={<ArrowClockwiseIcon />}
                label="Rapprocher à nouveau"
                onClick={() => reprocessEvent(row.original.id)}
              />
            </TableActionsCell>
          ),
        }),
      ]),
    [canReprocess, column, reprocess.isPending, reprocessEvent],
  );
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <div className="flex border-b p-4">
        <Select
          onValueChange={(value) => {
            setStatus(value as typeof status);
            setPage(0);
          }}
          value={status}
        >
          <SelectTrigger aria-label="État de rapprochement" className="w-full sm:w-56">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">Tous les états</SelectItem>
            {Object.entries(providerEventStatusPresentation).map(([value, presentation]) => (
              <SelectItem key={value} value={value}>
                {presentation.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      {events.isLoading ? (
        <div className="p-5">
          <LoadingState rows={6} />
        </div>
      ) : events.isError ? (
        <ErrorState retry={() => void events.refetch()} />
      ) : (
        <>
          <DataTable
            columns={columns}
            data={events.data?.content ?? []}
            emptyState={<EmptyState title="Aucun événement fournisseur" />}
            getRowId={(item) => item.id}
            onSortingChange={(next) => {
              setSorting(next);
              setPage(0);
            }}
            sorting={sorting}
          />
          <PaginationBar
            onPageChange={setPage}
            page={events.data?.page ?? 0}
            totalElements={events.data?.totalElements ?? 0}
            totalPages={events.data?.totalPages ?? 0}
          />
        </>
      )}
    </section>
  );
}

export function AdminBillingPage() {
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const tabs = [
    ...(session.can(adminPermissions.billingListInvoices) ? [{ label: "Factures", value: "invoices" }] : []),
    ...(session.can(adminPermissions.billingListReconciliation)
      ? [{ label: "Commandes fournisseur", value: "reconciliation" }]
      : []),
    ...(session.can(adminPermissions.billingListProviderEvents)
      ? [{ label: "Événements fournisseur", value: "events" }]
      : []),
  ];
  const requested = params.get("view");
  const view = requested && tabs.some((item) => item.value === requested) ? requested : (tabs[0]?.value ?? "invoices");
  return (
    <div className="space-y-7">
      <PageHeader title="Facturation" />
      <SectionTabs
        items={tabs}
        onValueChange={(value) => {
          const next = new URLSearchParams(params);
          next.set("view", value);
          setParams(next, { replace: true });
        }}
        value={view}
      />
      {view === "invoices" ? (
        <InvoiceList />
      ) : view === "reconciliation" ? (
        <ReconciliationList />
      ) : (
        <ProviderEventList />
      )}
    </div>
  );
}
