import { ArrowLeftIcon, CheckCircleIcon, WarningCircleIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { SubscriptionChangeInput, SubscriptionChangeJobPreview } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { StatusBadge } from "@/components/patterns/status-badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { AdminSubscriptionChangeWorkbench } from "@/features/admin/subscriptions/admin-subscription-change-workbench";
import { adminCommercialKeys, commercialQueryEnabled } from "@/features/commercial/commercial-query";
import { changeJobResultStatusPresentation, subscriptionChangeJobReasonError } from "./subscription-change-job-rules";
import { SubscriptionJobAccountPicker } from "./subscription-job-account-picker";

function localDateTimeToInstant(value: string) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

function PreviewPanel({
  preview,
  confirming,
  onConfirm,
}: {
  preview: SubscriptionChangeJobPreview;
  confirming: boolean;
  onConfirm: () => void;
}) {
  const [clock, setClock] = useState(() => Date.now());
  useEffect(() => {
    const delay = Date.parse(preview.expiresAt) - Date.now();
    if (delay <= 0) return;
    const timer = window.setTimeout(() => setClock(Date.now()), delay + 25);
    return () => window.clearTimeout(timer);
  }, [preview.expiresAt]);
  const expired = Date.parse(preview.expiresAt) <= clock;
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <div className="flex flex-col gap-3 border-b p-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-sm font-semibold">Revue de la population</h2>
          <p className="mt-1 text-xs text-muted-foreground">
            {preview.readyCount} prêt(s) sur {preview.targetCount} · {preview.conflictCount} conflit(s)
          </p>
        </div>
        <Button disabled={confirming || expired} onClick={onConfirm}>
          {confirming ? "Confirmation…" : "Confirmer et lancer"}
        </Button>
      </div>
      {expired ? (
        <p className="border-b border-warning/30 bg-warning-subtle px-4 py-3 text-sm text-warning" role="alert">
          Cette revue a expiré. Recalculez-la avant de confirmer.
        </p>
      ) : null}
      <div className="grid divide-y sm:grid-cols-3 sm:divide-x sm:divide-y-0 sm:divide-x-reverse rtl:sm:divide-x-reverse">
        <div className="p-4">
          <p className="text-xs text-muted-foreground">Population figée</p>
          <p className="mt-1 text-xl font-semibold tabular-nums">{preview.targetCount}</p>
        </div>
        <div className="p-4">
          <p className="text-xs text-muted-foreground">Prêts</p>
          <p className="mt-1 text-xl font-semibold tabular-nums text-success">{preview.readyCount}</p>
        </div>
        <div className="p-4">
          <p className="text-xs text-muted-foreground">Conflits avant exécution</p>
          <p className="mt-1 text-xl font-semibold tabular-nums text-destructive">{preview.conflictCount}</p>
        </div>
      </div>
      {preview.sample.length ? (
        <div className="border-t p-4">
          <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Échantillon de contrôle
          </h3>
          <ul className="mt-3 divide-y border-y">
            {preview.sample.map((item) => {
              const presentation = changeJobResultStatusPresentation[item.status];
              return (
                <li className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:justify-between" key={item.id}>
                  <span className="min-w-0 text-sm">
                    <span className="font-medium">
                      {item.assessment.currentPlanCode ?? "Sans abonnement"} → {item.assessment.targetPlanCode}
                    </span>
                    <span className="mt-0.5 block text-xs text-muted-foreground">
                      {item.assessment.conflicts.length
                        ? `${item.assessment.conflicts.length} conflit(s) détecté(s)`
                        : "Évaluation valide"}
                    </span>
                  </span>
                  <StatusBadge dot={false} tone={presentation.tone}>
                    {presentation.label}
                  </StatusBadge>
                </li>
              );
            })}
          </ul>
        </div>
      ) : null}
    </section>
  );
}

export function AdminSubscriptionChangeJobCreatePage() {
  const session = useAdminSession();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [accountIds, setAccountIds] = useState<string[]>([]);
  const [reason, setReason] = useState("");
  const [executeAt, setExecuteAt] = useState("");
  const [preview, setPreview] = useState<SubscriptionChangeJobPreview | null>(null);
  const referenceAccountId = accountIds[0] ?? null;
  const reasonProblem = subscriptionChangeJobReasonError(reason);
  const clearPreview = useCallback(() => setPreview(null), []);
  const catalog = useQuery({
    queryKey: referenceAccountId
      ? adminCommercialKeys.subscriptions.changeCatalog(referenceAccountId)
      : [...adminCommercialKeys.subscriptions.all(), "job-reference-catalog"],
    queryFn: () => adminApi.subscriptionChangeCatalog(referenceAccountId as string),
    enabled: commercialQueryEnabled(
      session.can,
      adminPermissions.subscriptionsChooseChangeOptions,
      Boolean(referenceAccountId),
    ),
    retry: false,
  });

  const previewMutation = useMutation({
    mutationFn: (selection: SubscriptionChangeInput) =>
      adminApi.previewSubscriptionChangeJob({
        accountIds,
        selection,
        reason: reason.trim(),
        executeAt: localDateTimeToInstant(executeAt),
      }),
    onSuccess: setPreview,
    onError: () => toast.error("La population n’a pas pu être évaluée."),
  });
  const confirmMutation = useMutation({
    mutationFn: () => {
      if (!preview) throw new Error("A current preview is required");
      return adminApi.confirmSubscriptionChangeJob(preview.jobId, preview.previewToken);
    },
    onSuccess: (job) => {
      void queryClient.invalidateQueries({ queryKey: adminCommercialKeys.subscriptions.all() });
      toast.success(job.summary.status === "SCHEDULED" ? "Traitement planifié" : "Traitement mis en file");
      navigate(`/admin/subscription-jobs/${job.summary.id}`);
    },
    onError: () => {
      setPreview(null);
      toast.error("La revue n’est plus valide. Recalculez la population.");
    },
  });

  const changeAccounts = (ids: string[]) => {
    setAccountIds(ids);
    setPreview(null);
  };

  return (
    <div className="space-y-7">
      <Button asChild size="sm" variant="ghost">
        <Link to="/admin/subscription-jobs">
          <ArrowLeftIcon className="rtl:rotate-180" />
          Traitements
        </Link>
      </Button>
      <PageHeader title="Nouveau changement en lot" />

      <section className="rounded-xl border bg-card p-4">
        <h2 className="text-sm font-semibold">1. Comptes concernés</h2>
        <div className="mt-4">
          <SubscriptionJobAccountPicker onChange={changeAccounts} selectedIds={accountIds} />
        </div>
      </section>

      {referenceAccountId ? (
        catalog.isLoading ? (
          <section className="rounded-xl border bg-card p-5">
            <LoadingState rows={5} />
          </section>
        ) : catalog.isError ? (
          <ErrorState retry={() => void catalog.refetch()} title="Impossible de charger le catalogue de référence" />
        ) : catalog.data ? (
          <>
            <section className="rounded-xl border bg-card p-4">
              <h2 className="text-sm font-semibold">2. Justification et exécution</h2>
              <div className="mt-4 grid gap-4 lg:grid-cols-[1fr_320px]">
                <div className="space-y-2">
                  <Label htmlFor="subscription-job-reason">Justification</Label>
                  <Textarea
                    id="subscription-job-reason"
                    maxLength={2000}
                    onChange={(event) => {
                      setReason(event.target.value);
                      setPreview(null);
                    }}
                    placeholder="Contrat, migration ou décision commerciale…"
                    value={reason}
                  />
                  {reason && reasonProblem ? <p className="text-xs text-destructive">{reasonProblem}</p> : null}
                </div>
                <div className="space-y-2">
                  <Label htmlFor="subscription-job-execute-at">Exécuter à</Label>
                  <Input
                    id="subscription-job-execute-at"
                    onChange={(event) => {
                      setExecuteAt(event.target.value);
                      setPreview(null);
                    }}
                    type="datetime-local"
                    value={executeAt}
                  />
                  <p className="text-xs text-muted-foreground">Laissez vide pour lancer après confirmation.</p>
                </div>
              </div>
            </section>
            <div>
              <p className="mb-3 text-sm font-semibold">3. Changement commercial</p>
              <AdminSubscriptionChangeWorkbench
                accountId={referenceAccountId}
                catalog={catalog.data}
                key={referenceAccountId}
                onReviewSelection={(selection) => previewMutation.mutate(selection)}
                onSelectionChange={clearPreview}
                populationMode
                reviewDisabled={Boolean(reasonProblem)}
                reviewDisabledReason={reasonProblem ?? undefined}
                reviewLabel="Évaluer la population"
                reviewPending={previewMutation.isPending}
                reviewPermission={adminPermissions.subscriptionsPreviewChangeJob}
              />
            </div>
          </>
        ) : null
      ) : (
        <div className="flex items-center gap-2 border-s-2 border-info ps-4 text-sm text-muted-foreground">
          <CheckCircleIcon className="text-info" />
          Sélectionnez un premier compte pour charger les options commerciales.
        </div>
      )}

      {preview ? (
        <PreviewPanel
          confirming={confirmMutation.isPending}
          onConfirm={() => confirmMutation.mutate()}
          preview={preview}
        />
      ) : previewMutation.isError ? (
        <div className="flex items-center gap-2 border-s-2 border-destructive ps-4 text-sm text-destructive">
          <WarningCircleIcon />
          Corrigez les données signalées puis relancez l’évaluation.
        </div>
      ) : null}
    </div>
  );
}
