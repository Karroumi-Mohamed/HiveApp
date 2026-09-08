import { ArrowClockwiseIcon, ArrowRightIcon, XIcon } from "@phosphor-icons/react";
import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { type RepricingItem, type RepricingState, repricingApi } from "@/api/repricing-api";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable } from "@/components/patterns/data-table";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { RowAction } from "@/components/patterns/row-action";
import { TableActionsCell, tableActionsColumnMeta } from "@/components/patterns/table-actions-cell";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { formatExactMoney } from "@/lib/exact-decimal";
import { noticeDeliveries, repricingBlocker, repricingDate, repricingStates } from "./repricing-rules";

export type RepricingAction = { kind: "cancel" | "retry" | "email"; itemId?: string };
export function RepricingResults({
  id,
  review = false,
  onAction,
}: {
  id: string;
  review?: boolean;
  onAction?: (action: RepricingAction) => void;
}) {
  const session = useAdminSession();
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<RepricingState | "all">("all");
  const canRead = session.can(adminPermissions.repricingResults);
  const results = useQuery({
    queryKey: ["admin", "repricing", id, "results", page, status],
    queryFn: () => repricingApi.results(id, page, status === "all" ? undefined : status),
    enabled: canRead,
    refetchInterval: review ? false : 30_000,
  });
  const itemIds = results.data?.content.map((item) => item.id) ?? [];
  const identities = useQuery({
    queryKey: ["admin", "repricing", id, "identities", itemIds],
    queryFn: () => repricingApi.identities(id, itemIds),
    enabled: session.can(adminPermissions.repricingIdentities) && itemIds.length > 0 && canRead,
  });
  const names = new Map(identities.data?.map((item) => [item.itemId, item]));
  const column = useMemo(() => createDataColumns<RepricingItem>(), []);
  const columns = column.columns([
    column.display({
      id: "account",
      header: "Compte",
      cell: ({ row }) => (
        <span className="font-medium">
          {names.get(row.original.id)?.accountName ?? (identities.isLoading ? "Chargement…" : "Identité protégée")}
        </span>
      ),
    }),
    column.display({
      id: "price",
      header: "Tarif unitaire",
      cell: ({ row: { original: item } }) => (
        <span className="whitespace-nowrap tabular-nums">
          {formatExactMoney(item.oldUnitPrice, item.currencyCode)} →{" "}
          {formatExactMoney(item.newUnitPrice, item.currencyCode)}
          <span className="block text-xs text-muted-foreground">Quantité : {item.quantity}</span>
        </span>
      ),
    }),
    column.display({
      id: "total",
      header: "Total récurrent",
      cell: ({ row: { original: item } }) => (
        <span className="whitespace-nowrap tabular-nums">
          {item.oldTotal === null ? "—" : formatExactMoney(item.oldTotal, item.currencyCode)} →{" "}
          {item.newTotal === null ? "—" : formatExactMoney(item.newTotal, item.currencyCode)}
        </span>
      ),
    }),
    column.display({
      id: "date",
      header: "Renouvellement",
      cell: ({ row }) => repricingDate(row.original.effectiveAt),
    }),
    column.display({
      id: "state",
      header: "État",
      cell: ({ row: { original: item } }) => (
        <span className={item.status === "CONFLICT" ? "text-warning" : item.status === "APPLIED" ? "text-success" : ""}>
          {repricingStates[item.status]}
          {item.blocker ? <span className="mt-1 block max-w-64 text-xs">{repricingBlocker(item.blocker)}</span> : null}
          {!review && item.delivery !== "NOT_REQUESTED" ? (
            <span className="mt-1 block text-xs text-muted-foreground">Email : {noticeDeliveries[item.delivery]}</span>
          ) : null}
        </span>
      ),
    }),
    ...(!review
      ? [
          column.display({
            id: "actions",
            header: "Actions",
            meta: tableActionsColumnMeta(3),
            cell: ({ row: { original: item } }) => {
              const identity = names.get(item.id);
              return (
                <TableActionsCell label="Actions du changement">
                  <RowAction
                    label="Annuler pour ce compte"
                    icon={<XIcon />}
                    disabled={item.status !== "PENDING" || !session.can(adminPermissions.repricingCancel)}
                    disabledLabel="Seuls les changements planifiés peuvent être annulés avec l’autorisation requise"
                    onClick={() => onAction?.({ kind: "cancel", itemId: item.id })}
                  />
                  <RowAction
                    label="Réessayer l’exécution"
                    icon={<ArrowClockwiseIcon />}
                    disabled={
                      item.status !== "CONFLICT" ||
                      item.blocker !== "EXECUTION_FAILED" ||
                      !session.can(adminPermissions.repricingRetry)
                    }
                    disabledLabel="Réservé aux échecs techniques ; les conditions modifiées demandent une nouvelle révision"
                    onClick={() => onAction?.({ kind: "retry", itemId: item.id })}
                  />
                  <RowAction
                    label="Ouvrir l’abonnement"
                    icon={<ArrowRightIcon className="rtl:rotate-180" />}
                    disabled={!identity || !session.can(adminPermissions.subscriptionsRead)}
                    to={identity ? `/admin/subscriptions/${identity.accountId}` : undefined}
                  />
                </TableActionsCell>
              );
            },
          }),
        ]
      : []),
  ]);
  if (!canRead) return <PermissionState />;
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b p-4">
        <h2 className="font-semibold">{review ? "Abonnés concernés" : "Résultats par compte"}</h2>
        <Select
          value={status}
          onValueChange={(value) => {
            setStatus(value as typeof status);
            setPage(0);
          }}
        >
          <SelectTrigger aria-label="Filtrer les résultats" className="w-60">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">Tous les états</SelectItem>
            {Object.entries(repricingStates).map(([value, label]) => (
              <SelectItem key={value} value={value}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      {identities.isError ? (
        <ErrorState title="Noms des comptes indisponibles" retry={() => void identities.refetch()} />
      ) : null}
      {results.isLoading ? (
        <LoadingState />
      ) : results.isError ? (
        <ErrorState retry={() => void results.refetch()} />
      ) : results.data ? (
        <>
          <DataTable
            columns={columns}
            data={results.data.content}
            getRowId={(item) => item.id}
            emptyState={<EmptyState title="Aucun résultat" />}
          />
          <PaginationBar {...results.data} onPageChange={setPage} />
        </>
      ) : null}
    </section>
  );
}
