import { ArrowLeftIcon, ArrowRightIcon, PlusIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router";
import { type RepricingSummary, repricingApi } from "@/api/repricing-api";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable } from "@/components/patterns/data-table";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { RowAction } from "@/components/patterns/row-action";
import { TableActionsCell, tableActionsColumnMeta } from "@/components/patterns/table-actions-cell";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { formatExactMoney } from "@/lib/exact-decimal";
import { type RepricingAction, RepricingResults } from "./repricing-results";
import { repricingCycle, repricingDate } from "./repricing-rules";

const column = createDataColumns<RepricingSummary>();
export function AdminRepricingListPage() {
  const session = useAdminSession();
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: ["admin", "repricing", "list", page],
    queryFn: () => repricingApi.list(page),
    enabled: session.can(adminPermissions.repricingList),
  });
  const canOpen = session.can(adminPermissions.repricingRead) || session.can(adminPermissions.repricingResults);
  const columns = column.columns([
    column.accessor("productName", {
      header: "Produit",
      cell: ({ row }) => <span className="font-medium">{row.original.productName}</span>,
    }),
    column.display({
      id: "tariff",
      header: "Ancien → nouveau tarif",
      cell: ({ row: { original: item } }) => (
        <span className="tabular-nums">
          {formatExactMoney(item.sourcePrice, item.currencyCode)} →{" "}
          {formatExactMoney(item.targetPrice, item.currencyCode)} / {repricingCycle(item.billingCycle)}
        </span>
      ),
    }),
    column.display({
      id: "progress",
      header: "Abonnés",
      cell: ({ row: { original: item } }) => (
        <span>
          {item.status === "PREVIEWED"
            ? "Révision non confirmée"
            : item.status === "CANCELLED"
              ? "Annulé"
              : `${item.appliedCount} appliqué(s) · ${item.pendingCount} planifié(s)`}
          <span className="block text-xs text-muted-foreground">
            {item.targetCount} compte(s) · {item.conflictCount} à revoir · {item.awaitingPaymentCount} renouvellement(s)
            en cours
          </span>
        </span>
      ),
    }),
    column.accessor("createdAt", { header: "Créé le", cell: ({ row }) => repricingDate(row.original.createdAt) }),
    column.display({
      id: "actions",
      header: "Actions",
      meta: tableActionsColumnMeta(1),
      cell: ({ row }) => (
        <TableActionsCell label="Actions du changement de tarif">
          <RowAction
            label="Ouvrir le changement"
            icon={<ArrowRightIcon className="rtl:rotate-180" />}
            disabled={!canOpen}
            to={`/admin/repricing/${row.original.id}`}
          />
        </TableActionsCell>
      ),
    }),
  ]);
  if (!session.can(adminPermissions.repricingList)) return <PermissionState />;
  return (
    <div className="space-y-6">
      <Button asChild variant="ghost" size="sm">
        <Link to="/admin/price-books">
          <ArrowLeftIcon className="rtl:rotate-180" />
          Grille tarifaire
        </Link>
      </Button>
      <PageHeader
        title="Tarifs des abonnés"
        actions={
          session.can(adminPermissions.repricingPreview) ? (
            <Button asChild>
              <Link to="/admin/repricing/new">
                <PlusIcon />
                Planifier un changement
              </Link>
            </Button>
          ) : undefined
        }
      />
      <section className="overflow-hidden rounded-xl border bg-card">
        {query.isLoading ? (
          <LoadingState />
        ) : query.isError ? (
          <ErrorState retry={() => void query.refetch()} />
        ) : query.data ? (
          <>
            <DataTable
              columns={columns}
              data={query.data.content}
              getRowId={(item) => item.id}
              emptyState={<EmptyState title="Aucun changement de tarif" />}
            />
            <PaginationBar {...query.data} onPageChange={setPage} />
          </>
        ) : null}
      </section>
    </div>
  );
}

export function AdminRepricingDetailPage() {
  const { repricingId = "" } = useParams();
  const session = useAdminSession();
  const client = useQueryClient();
  const [action, setAction] = useState<RepricingAction | null>(null);
  const [reason, setReason] = useState("");
  const canRead = session.can(adminPermissions.repricingRead);
  const query = useQuery({
    queryKey: ["admin", "repricing", repricingId],
    queryFn: () => repricingApi.detail(repricingId),
    enabled: canRead,
    refetchInterval: 30_000,
  });
  const mutation = useMutation({
    mutationFn: async () => {
      if (!action || !reason.trim()) throw new Error("Indiquez le motif.");
      if (action.kind === "cancel") return repricingApi.cancel(repricingId, reason.trim(), action.itemId);
      if (action.kind === "email") return repricingApi.retryNotices(repricingId, reason.trim());
      if (!action.itemId) throw new Error("Résultat manquant.");
      return repricingApi.retry(repricingId, action.itemId, reason.trim());
    },
    onSuccess: () => {
      setAction(null);
      setReason("");
      void client.invalidateQueries({ queryKey: ["admin", "repricing"] });
    },
  });
  const openAction = (value: RepricingAction) => {
    setAction(value);
    setReason("");
    mutation.reset();
  };
  const summary = query.data?.summary;
  if (!canRead && !session.can(adminPermissions.repricingResults)) return <PermissionState />;
  return (
    <div className="space-y-6">
      <Button asChild variant="ghost" size="sm">
        <Link to={session.can(adminPermissions.repricingList) ? "/admin/repricing" : "/admin"}>
          <ArrowLeftIcon className="rtl:rotate-180" />
          Tarifs des abonnés
        </Link>
      </Button>
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState retry={() => void query.refetch()} />
      ) : (
        <PageHeader
          title={summary?.productName ?? "Changement de tarif"}
          description={
            summary
              ? `${formatExactMoney(summary.sourcePrice, summary.currencyCode)} → ${formatExactMoney(summary.targetPrice, summary.currencyCode)} / ${repricingCycle(summary.billingCycle)}`
              : undefined
          }
          actions={
            <>
              {summary?.status === "CONFIRMED" && session.can(adminPermissions.repricingEmail) ? (
                <Button variant="ghost" onClick={() => openAction({ kind: "email" })}>
                  Réessayer les emails échoués
                </Button>
              ) : null}
              {summary &&
              summary.pendingCount + summary.readyCount > 0 &&
              summary.status !== "CANCELLED" &&
              session.can(adminPermissions.repricingCancel) ? (
                <Button variant="outline" onClick={() => openAction({ kind: "cancel" })}>
                  Annuler les changements restants
                </Button>
              ) : null}
            </>
          }
        />
      )}
      {summary ? (
        <p className="border-y py-4 text-sm">
          {summary.targetCount} compte(s) · {summary.pendingCount} planifié(s) · {summary.awaitingPaymentCount}{" "}
          renouvellement(s) en cours · {summary.appliedCount} appliqué(s) · {summary.conflictCount} à revoir ·{" "}
          {summary.cancelledCount} annulé(s)
        </p>
      ) : null}
      {summary?.status === "PREVIEWED" ? (
        <p className="text-sm text-warning">
          Révision non confirmée. Aucun changement ni notification envoyé.{" "}
          <Link className="underline" to={`/admin/repricing/new?from=${query.data?.request.sourcePriceId}`}>
            Recommencer une révision
          </Link>
        </p>
      ) : null}
      {summary?.status === "CANCELLED" ? (
        <p className="text-sm">
          Les changements restants sont annulés. Les renouvellements déjà traités ne sont pas annulés rétroactivement.
        </p>
      ) : null}
      {query.data ? <p className="text-sm text-muted-foreground">Motif : {query.data.request.reason}</p> : null}
      <RepricingResults id={repricingId} onAction={openAction} />
      <Dialog
        open={action !== null}
        onOpenChange={(value) => {
          if (!value && !mutation.isPending) setAction(null);
        }}
      >
        <DialogContent>
          <form
            onSubmit={(event) => {
              event.preventDefault();
              if (reason.trim() && !mutation.isPending) mutation.mutate();
            }}
          >
            <DialogHeader>
              <DialogTitle>
                {action?.kind === "cancel"
                  ? "Annuler ce changement ?"
                  : action?.kind === "email"
                    ? "Réessayer les emails échoués ?"
                    : "Réessayer ce changement ?"}
              </DialogTitle>
              <DialogDescription>
                {action?.kind === "cancel"
                  ? "Seuls les changements encore planifiés seront annulés. Les renouvellements déjà lancés restent dans le suivi de facturation."
                  : action?.kind === "email"
                    ? "Aucun changement sur le tarif ou les paiements. Seuls les envois échoués encore pertinents sont relancés."
                    : "Les conditions originales seront recontrôlées. Un changement d’abonnement nécessite une nouvelle révision."}
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-2 py-5">
              <Label htmlFor="repricing-action-reason">Motif</Label>
              <Textarea
                id="repricing-action-reason"
                maxLength={2000}
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                required
              />
              {mutation.isError ? (
                <p role="alert" className="text-sm text-destructive">
                  {mutation.error.message}
                </p>
              ) : null}
            </div>
            <DialogFooter>
              <Button type="button" variant="ghost" disabled={mutation.isPending} onClick={() => setAction(null)}>
                Fermer
              </Button>
              <Button type="submit" disabled={!reason.trim() || mutation.isPending}>
                {mutation.isPending ? "Traitement…" : "Confirmer"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
