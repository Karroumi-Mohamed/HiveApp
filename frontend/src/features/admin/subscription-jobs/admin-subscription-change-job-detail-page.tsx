import { ArrowClockwiseIcon, ArrowLeftIcon, EyeIcon, ProhibitIcon } from "@phosphor-icons/react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { SortingState } from "@tanstack/react-table";
import { useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type {
  SubscriptionChangeJobItemStatus,
  SubscriptionChangeJobResult,
  SubscriptionChangeJobSummary,
} from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable, DataTableExpander, SortHeader } from "@/components/patterns/data-table";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { StatusBadge } from "@/components/patterns/status-badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { adminCommercialKeys, commercialQueryEnabled } from "@/features/commercial/commercial-query";
import { formatExactMoney } from "@/lib/exact-decimal";
import {
  changeJobCanBeCancelled,
  changeJobCanBeRetried,
  changeJobProcessedCount,
  changeJobResultStatusPresentation,
  changeJobStatusPresentation,
  subscriptionChangeJobReasonError,
} from "./subscription-change-job-rules";

const dateTime = (value: string | null) =>
  value ? new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "—";

function JobActionDialog({
  action,
  job,
  open,
  onOpenChange,
}: {
  action: "cancel" | "retry";
  job: SubscriptionChangeJobSummary;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const queryClient = useQueryClient();
  const [reason, setReason] = useState("");
  const problem = subscriptionChangeJobReasonError(reason);
  const mutation = useMutation({
    mutationFn: () =>
      action === "cancel"
        ? adminApi.cancelSubscriptionChangeJob(job.id, reason.trim())
        : adminApi.retrySubscriptionChangeJob(job.id, reason.trim()),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminCommercialKeys.subscriptions.all() });
      toast.success(action === "cancel" ? "Traitement annulé" : "Nouvelle tentative mise en file");
      setReason("");
      onOpenChange(false);
    },
    onError: () =>
      toast.error(action === "cancel" ? "Le traitement ne peut plus être annulé." : "La relance a échoué."),
  });
  const cancel = action === "cancel";
  return (
    <Dialog onOpenChange={onOpenChange} open={open}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{cancel ? "Annuler ce traitement ?" : "Relancer les résultats en erreur ?"}</DialogTitle>
          <DialogDescription>
            {cancel
              ? "Seuls les comptes qui n’ont pas commencé seront annulés."
              : "Seuls les échecs et conflits seront réévalués. Les réussites restent inchangées."}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-2">
          <Label htmlFor={`job-${action}-reason`}>Justification</Label>
          <Textarea
            id={`job-${action}-reason`}
            maxLength={2000}
            onChange={(event) => setReason(event.target.value)}
            value={reason}
          />
          {reason && problem ? <p className="text-xs text-destructive">{problem}</p> : null}
        </div>
        <div className="flex justify-end gap-2">
          <Button onClick={() => onOpenChange(false)} variant="outline">
            Retour
          </Button>
          <Button
            disabled={mutation.isPending || Boolean(problem)}
            onClick={() => mutation.mutate()}
            variant={cancel ? "destructive" : "default"}
          >
            {mutation.isPending ? "Enregistrement…" : cancel ? "Annuler le traitement" : "Relancer"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function Summary({ job }: { job: SubscriptionChangeJobSummary }) {
  const presentation = changeJobStatusPresentation[job.status];
  const processed = changeJobProcessedCount(job);
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <div className="flex flex-col gap-3 border-b p-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <p className="text-xs text-muted-foreground">Justification</p>
          <p className="mt-1 whitespace-pre-wrap break-words text-sm font-medium">{job.reason}</p>
        </div>
        <StatusBadge dot={false} tone={presentation.tone}>
          {presentation.label}
        </StatusBadge>
      </div>
      <dl className="grid divide-y sm:grid-cols-4 sm:divide-x sm:divide-y-0 sm:divide-x-reverse rtl:sm:divide-x-reverse">
        <div className="p-4">
          <dt className="text-xs text-muted-foreground">Progression</dt>
          <dd className="mt-1 font-semibold tabular-nums">
            {processed} / {job.targetCount}
          </dd>
        </div>
        <div className="p-4">
          <dt className="text-xs text-muted-foreground">Réussis ou planifiés</dt>
          <dd className="mt-1 font-semibold tabular-nums text-success">{job.appliedCount + job.pendingCount}</dd>
        </div>
        <div className="p-4">
          <dt className="text-xs text-muted-foreground">Paiement requis</dt>
          <dd className="mt-1 font-semibold tabular-nums text-warning">{job.awaitingPaymentCount}</dd>
        </div>
        <div className="p-4">
          <dt className="text-xs text-muted-foreground">Erreurs ou conflits</dt>
          <dd className="mt-1 font-semibold tabular-nums text-destructive">{job.failedCount + job.conflictCount}</dd>
        </div>
      </dl>
      <dl className="grid gap-4 border-t p-4 text-sm sm:grid-cols-3">
        <div>
          <dt className="text-xs text-muted-foreground">Créé le</dt>
          <dd className="mt-1">{dateTime(job.createdAt)}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Exécution prévue</dt>
          <dd className="mt-1">{dateTime(job.executeAt)}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Tentatives de relance</dt>
          <dd className="mt-1 tabular-nums">{job.retryCount}</dd>
        </div>
      </dl>
    </section>
  );
}

export function AdminSubscriptionChangeJobDetailPage() {
  const { jobId = "" } = useParams();
  const session = useAdminSession();
  const [status, setStatus] = useState<SubscriptionChangeJobItemStatus | "all">("all");
  const [page, setPage] = useState(0);
  const [sorting, setSorting] = useState<SortingState>([]);
  const [revealIdentities, setRevealIdentities] = useState(false);
  const [dialog, setDialog] = useState<"cancel" | "retry" | null>(null);
  const resultRequest = {
    status: status === "all" ? undefined : status,
    page,
    size: 20,
    sort: (sorting[0]?.id ?? "createdAt") as "createdAt" | "status" | "lastAttemptAt" | "completedAt",
    direction: sorting[0]?.desc ? ("desc" as const) : ("asc" as const),
  };
  const job = useQuery({
    queryKey: adminCommercialKeys.subscriptions.job(jobId),
    queryFn: () => adminApi.subscriptionChangeJob(jobId),
    enabled: commercialQueryEnabled(session.can, adminPermissions.subscriptionsReadChangeJob, Boolean(jobId)),
    retry: false,
  });
  const results = useQuery({
    queryKey: adminCommercialKeys.subscriptions.jobResults(jobId, resultRequest),
    queryFn: () => adminApi.subscriptionChangeJobResults(jobId, resultRequest),
    enabled: commercialQueryEnabled(session.can, adminPermissions.subscriptionsReadChangeJobResults, Boolean(jobId)),
    placeholderData: keepPreviousData,
    retry: false,
  });
  const resultIds = useMemo(() => (results.data?.content ?? []).map((result) => result.id), [results.data?.content]);
  const identities = useQuery({
    queryKey: [...adminCommercialKeys.subscriptions.jobResults(jobId, resultRequest), "identities", resultIds],
    queryFn: () => adminApi.resolveSubscriptionChangeJobIdentities(jobId, resultIds),
    enabled: commercialQueryEnabled(
      session.can,
      adminPermissions.subscriptionsReadChangeJobResultIdentities,
      revealIdentities && resultIds.length > 0,
    ),
    retry: false,
  });
  const identitiesByItem = useMemo(
    () => new Map((revealIdentities ? (identities.data ?? []) : []).map((item) => [item.itemId, item])),
    [identities.data, revealIdentities],
  );
  const column = useMemo(() => createDataColumns<SubscriptionChangeJobResult>(), []);
  const columns = useMemo(
    () =>
      column.columns([
        column.display({
          id: "expand",
          meta: { headerClassName: "w-12", cellClassName: "w-12" },
          header: "",
          cell: ({ row }) => (
            <DataTableExpander collapseLabel="Masquer les détails" expandLabel="Afficher les détails" row={row} />
          ),
        }),
        column.display({
          id: "account",
          meta: { headerClassName: "min-w-44" },
          header: "Compte",
          cell: ({ row }) => {
            const identity = identitiesByItem.get(row.original.id);
            return identity ? (
              <span>
                <span className="block font-medium">{identity.accountName}</span>
              </span>
            ) : (
              <span className="text-sm text-muted-foreground">Compte masqué</span>
            );
          },
        }),
        column.display({
          id: "change",
          header: "Changement",
          cell: ({ row }) => (
            <span className="font-medium">
              {row.original.assessment.currentPlanCode ?? "—"} → {row.original.assessment.targetPlanCode}
            </span>
          ),
        }),
        column.accessor("status", {
          meta: { headerClassName: "w-44", cellClassName: "w-44" },
          header: ({ column: item }) => <SortHeader column={item}>Résultat</SortHeader>,
          cell: ({ row }) => {
            const presentation = changeJobResultStatusPresentation[row.original.status];
            return (
              <StatusBadge dot={false} tone={presentation.tone}>
                {presentation.label}
              </StatusBadge>
            );
          },
        }),
        column.accessor("lastAttemptAt", {
          meta: { headerClassName: "w-48", cellClassName: "w-48" },
          header: ({ column: item }) => <SortHeader column={item}>Dernière tentative</SortHeader>,
          cell: ({ row }) => (
            <span>
              <span className="block">{dateTime(row.original.lastAttemptAt)}</span>
              <span className="mt-0.5 block text-xs text-muted-foreground">{row.original.attempts} tentative(s)</span>
            </span>
          ),
        }),
      ]),
    [column, identitiesByItem],
  );
  const summary = job.data?.summary;
  const canReveal = session.can(adminPermissions.subscriptionsReadChangeJobResultIdentities);

  return (
    <div className="space-y-7">
      <Button asChild size="sm" variant="ghost">
        <Link to="/admin/subscription-jobs">
          <ArrowLeftIcon className="rtl:rotate-180" />
          Traitements
        </Link>
      </Button>
      <PageHeader
        actions={
          summary ? (
            <>
              {session.can(adminPermissions.subscriptionsRetryChangeJob) ? (
                <Button disabled={!changeJobCanBeRetried(summary)} onClick={() => setDialog("retry")} variant="outline">
                  <ArrowClockwiseIcon />
                  Relancer
                </Button>
              ) : null}
              {session.can(adminPermissions.subscriptionsCancelChangeJob) ? (
                <Button
                  disabled={!changeJobCanBeCancelled(summary)}
                  onClick={() => setDialog("cancel")}
                  variant="destructive"
                >
                  <ProhibitIcon />
                  Annuler
                </Button>
              ) : null}
            </>
          ) : undefined
        }
        title="Traitement d’abonnements"
      />
      {job.isLoading ? (
        <LoadingState rows={4} />
      ) : job.isError ? (
        <ErrorState retry={() => void job.refetch()} />
      ) : summary ? (
        <Summary job={summary} />
      ) : null}

      {session.can(adminPermissions.subscriptionsReadChangeJobResults) ? (
        <section className="overflow-hidden rounded-xl border bg-card">
          <div className="flex flex-col gap-3 border-b p-4 sm:flex-row sm:items-center sm:justify-between">
            <Select
              onValueChange={(value) => {
                setStatus(value as typeof status);
                setPage(0);
              }}
              value={status}
            >
              <SelectTrigger aria-label="Résultat" className="w-full sm:w-56">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Tous les résultats</SelectItem>
                {Object.entries(changeJobResultStatusPresentation).map(([value, presentation]) => (
                  <SelectItem key={value} value={value}>
                    {presentation.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {canReveal ? (
              <Button onClick={() => setRevealIdentities((value) => !value)} variant="outline">
                <EyeIcon />
                {revealIdentities ? "Masquer les comptes" : "Afficher les comptes"}
              </Button>
            ) : null}
          </div>
          {results.isLoading || (revealIdentities && identities.isLoading) ? (
            <div className="p-5">
              <LoadingState rows={5} />
            </div>
          ) : results.isError || (revealIdentities && identities.isError) ? (
            <ErrorState
              retry={() => {
                void results.refetch();
                if (revealIdentities) void identities.refetch();
              }}
            />
          ) : (
            <>
              <DataTable
                columns={columns}
                data={results.data?.content ?? []}
                emptyState={<EmptyState title="Aucun résultat" />}
                getRowId={(result) => result.id}
                onSortingChange={(next) => {
                  setSorting(next);
                  setPage(0);
                }}
                renderExpandedRow={(result) => (
                  <div className="grid gap-4 border-s-2 border-border bg-background/50 p-4 text-sm sm:grid-cols-3">
                    <div>
                      <p className="text-xs text-muted-foreground">Prix évalué</p>
                      <p className="mt-1 font-medium">
                        {result.assessment.targetPrice && result.assessment.currencyCode
                          ? formatExactMoney(result.assessment.targetPrice, result.assessment.currencyCode)
                          : "—"}
                      </p>
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">Code de résultat</p>
                      <p className="mt-1 break-all font-mono text-xs">{result.outcomeCode ?? "—"}</p>
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">Conflits</p>
                      <p className="mt-1 font-medium">{result.assessment.conflicts.length}</p>
                    </div>
                  </div>
                )}
                sorting={sorting}
              />
              <PaginationBar
                onPageChange={setPage}
                page={results.data?.page ?? 0}
                totalElements={results.data?.totalElements ?? 0}
                totalPages={results.data?.totalPages ?? 0}
              />
            </>
          )}
        </section>
      ) : null}
      {summary && dialog ? (
        <JobActionDialog action={dialog} job={summary} onOpenChange={(open) => !open && setDialog(null)} open />
      ) : null}
    </div>
  );
}
