import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { SubscriptionLifecycleAction, SubscriptionLifecyclePreview } from "@/api/contracts";
import { ApiError } from "@/api/http";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { adminCommercialKeys, invalidateAdminSubscriptionEntitlement } from "@/features/commercial/commercial-query";
import { subscriptionStatusPresentation } from "@/features/commercial/subscription-presentation";
import {
  lifecyclePreviewIsReady,
  lifecycleStatusTransition,
  subscriptionLifecycleActionOrder,
  subscriptionLifecyclePresentation,
} from "./subscription-lifecycle-rules";
import { normalizedOperatorReason, operatorReasonError } from "./subscription-operation-rules";

const dateTime = (value: string | null) =>
  value ? new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "—";

function localDateTimeToInstant(value: string) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

function lifecycleError(error: unknown) {
  if (error instanceof ApiError && error.code === "STALE_RESOURCE_VERSION") {
    return "L’abonnement a changé. Une nouvelle vérification est nécessaire.";
  }
  if (error instanceof ApiError && error.code === "INVALID_STATE") {
    return "Cette opération n’est plus disponible dans l’état actuel.";
  }
  return "L’opération n’a pas pu être enregistrée.";
}

export function SubscriptionLifecyclePanel({ accountId }: { accountId: string }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [action, setAction] = useState<SubscriptionLifecycleAction | null>(null);
  const [preview, setPreview] = useState<SubscriptionLifecyclePreview | null>(null);
  const [reason, setReason] = useState("");
  const [reasonTouched, setReasonTouched] = useState(false);
  const [graceLocal, setGraceLocal] = useState("");
  const [historyPage, setHistoryPage] = useState(0);
  const canReadActions = session.can(adminPermissions.subscriptionsReadLifecycleActions);
  const canReadHistory = session.can(adminPermissions.subscriptionsReadLifecycleHistory);
  const canPreview = session.can(adminPermissions.subscriptionsPreviewLifecycle);
  const actions = useQuery({
    queryKey: adminCommercialKeys.subscriptions.lifecycleActions(accountId),
    queryFn: () => adminApi.subscriptionLifecycleActions(accountId),
    enabled: canReadActions,
    retry: false,
  });
  const history = useQuery({
    queryKey: adminCommercialKeys.subscriptions.lifecycleHistory(accountId, historyPage),
    queryFn: () =>
      adminApi.subscriptionLifecycleHistory(accountId, {
        page: historyPage,
        size: 10,
        sort: "createdAt",
        direction: "desc",
      }),
    enabled: canReadHistory,
    retry: false,
  });
  const availableActions = useMemo(
    () =>
      subscriptionLifecycleActionOrder.filter(
        (candidate) =>
          actions.data?.availableActions.includes(candidate) &&
          session.can(subscriptionLifecyclePresentation[candidate].permission),
      ),
    [actions.data, session],
  );
  const graceEndsAt = action === "EXTEND_GRACE" ? localDateTimeToInstant(graceLocal) : null;
  const reasonProblem = operatorReasonError(reason);
  const previewReady = action ? lifecyclePreviewIsReady(preview, action, graceEndsAt) : false;

  const previewMutation = useMutation({
    mutationFn: ({ candidate, nextGrace }: { candidate: SubscriptionLifecycleAction; nextGrace: string | null }) =>
      adminApi.previewSubscriptionLifecycle(accountId, {
        action: candidate,
        graceEndsAt: nextGrace,
      }),
    onSuccess: setPreview,
    onError: () => setPreview(null),
  });
  const applyMutation = useMutation({
    mutationFn: () => {
      if (!action || !previewReady || !preview) throw new Error("A current lifecycle review is required");
      return adminApi.applySubscriptionLifecycle(accountId, action, {
        previewToken: preview.previewToken,
        reason: normalizedOperatorReason(reason),
        graceEndsAt,
      });
    },
    onSuccess: async () => {
      await invalidateAdminSubscriptionEntitlement(queryClient);
      toast.success("Cycle de vie de l’abonnement mis à jour");
      closeDialog();
    },
    onError: (error) => {
      if (error instanceof ApiError && error.code === "STALE_RESOURCE_VERSION") {
        setPreview(null);
        void invalidateAdminSubscriptionEntitlement(queryClient);
      }
      toast.error(lifecycleError(error));
    },
  });

  function requestPreview(candidate: SubscriptionLifecycleAction, nextGrace: string | null) {
    setPreview(null);
    previewMutation.mutate({ candidate, nextGrace });
  }

  function openDialog(candidate: SubscriptionLifecycleAction) {
    setAction(candidate);
    setPreview(null);
    setReason("");
    setReasonTouched(false);
    setGraceLocal("");
    if (candidate !== "EXTEND_GRACE") requestPreview(candidate, null);
  }

  function closeDialog() {
    setAction(null);
    setPreview(null);
    setReason("");
    setReasonTouched(false);
    setGraceLocal("");
    previewMutation.reset();
    applyMutation.reset();
  }

  if (!canReadActions && !canReadHistory) return null;
  const presentation = action ? subscriptionLifecyclePresentation[action] : null;
  const transition = preview ? lifecycleStatusTransition(preview.beforeStatus, preview.afterStatus) : null;

  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <div className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
        <h2 className="text-sm font-semibold">Cycle de vie</h2>
        {actions.isLoading ? (
          <span className="text-xs text-muted-foreground">Chargement des actions…</span>
        ) : actions.isError ? (
          <Button onClick={() => void actions.refetch()} size="sm" variant="ghost">
            Réessayer les actions
          </Button>
        ) : availableActions.length ? (
          <div className="flex flex-wrap gap-2">
            {availableActions.map((candidate) => {
              const item = subscriptionLifecyclePresentation[candidate];
              return (
                <Button
                  disabled={!canPreview}
                  key={candidate}
                  onClick={() => openDialog(candidate)}
                  size="sm"
                  title={canPreview ? undefined : "La vérification du cycle de vie n’est pas autorisée."}
                  variant={item.destructive ? "destructive" : "outline"}
                >
                  {item.label}
                </Button>
              );
            })}
          </div>
        ) : null}
      </div>

      {canReadHistory ? (
        <div className="border-t">
          {history.isLoading ? (
            <div className="p-5">
              <LoadingState rows={3} />
            </div>
          ) : history.isError ? (
            <ErrorState retry={() => void history.refetch()} title="Impossible de charger l’historique" />
          ) : !history.data?.content.length ? (
            <p className="px-5 py-4 text-sm text-muted-foreground">Aucune intervention enregistrée.</p>
          ) : (
            <>
              <ol className="divide-y">
                {history.data.content.map((event) => (
                  <li className="grid gap-1 px-5 py-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:gap-5" key={event.id}>
                    <div>
                      <p className="text-sm font-medium">{subscriptionLifecyclePresentation[event.action].label}</p>
                      <p className="mt-1 text-sm text-muted-foreground">{event.reason}</p>
                    </div>
                    <p className="text-xs text-muted-foreground sm:text-end">
                      {event.actorEmail}
                      <br />
                      {dateTime(event.createdAt)}
                    </p>
                  </li>
                ))}
              </ol>
              <PaginationBar
                onPageChange={setHistoryPage}
                page={history.data.page}
                totalElements={history.data.totalElements}
                totalPages={history.data.totalPages}
              />
            </>
          )}
        </div>
      ) : null}

      <Dialog onOpenChange={(open) => !open && closeDialog()} open={Boolean(action)}>
        <DialogContent>
          {presentation && action ? (
            <>
              <DialogHeader>
                <DialogTitle>{presentation.dialogTitle}</DialogTitle>
                <DialogDescription>{presentation.description}</DialogDescription>
              </DialogHeader>

              {action === "EXTEND_GRACE" ? (
                <div className="space-y-2">
                  <Label htmlFor="subscription-grace-deadline">Nouvelle échéance</Label>
                  <div className="flex flex-col gap-2 sm:flex-row">
                    <Input
                      id="subscription-grace-deadline"
                      onChange={(event) => {
                        setGraceLocal(event.target.value);
                        setPreview(null);
                      }}
                      type="datetime-local"
                      value={graceLocal}
                    />
                    <Button
                      disabled={!graceEndsAt || previewMutation.isPending}
                      onClick={() => requestPreview(action, graceEndsAt)}
                      type="button"
                      variant="outline"
                    >
                      {previewMutation.isPending ? "Vérification…" : "Vérifier"}
                    </Button>
                  </div>
                </div>
              ) : null}

              {previewMutation.isPending ? <LoadingState rows={2} /> : null}
              {previewMutation.isError ? (
                <div className="border-s-2 border-destructive ps-3 text-sm text-destructive" role="alert">
                  Impossible de vérifier l’opération.
                  <Button
                    className="ms-1 h-auto px-1 py-0"
                    onClick={() => requestPreview(action, graceEndsAt)}
                    type="button"
                    variant="link"
                  >
                    Réessayer
                  </Button>
                </div>
              ) : null}

              {preview ? (
                <div className="divide-y rounded-lg border text-sm">
                  {transition ? (
                    <div className="flex justify-between gap-4 px-4 py-3">
                      <span className="text-muted-foreground">État</span>
                      <span className="font-medium text-end">
                        {subscriptionStatusPresentation[transition.before].label} →{" "}
                        {subscriptionStatusPresentation[transition.after].label}
                      </span>
                    </div>
                  ) : null}
                  <div className="flex justify-between gap-4 px-4 py-3">
                    <span className="text-muted-foreground">Prise d’effet</span>
                    <span className="font-medium text-end">{dateTime(preview.effectiveAt)}</span>
                  </div>
                  {action === "EXTEND_GRACE" ? (
                    <div className="flex justify-between gap-4 px-4 py-3">
                      <span className="text-muted-foreground">Nouveau délai</span>
                      <span className="font-medium text-end">{dateTime(preview.nextGraceEndsAt)}</span>
                    </div>
                  ) : null}
                </div>
              ) : null}
              {preview?.blockers.length ? (
                <ul className="border-s-2 border-destructive ps-4 text-sm text-destructive" role="alert">
                  {preview.blockers.map((blocker) => (
                    <li key={blocker}>{blocker}</li>
                  ))}
                </ul>
              ) : null}

              <div className="space-y-2">
                <Label htmlFor="subscription-lifecycle-reason">Justification</Label>
                <Textarea
                  aria-describedby={reasonTouched && reasonProblem ? "subscription-lifecycle-reason-error" : undefined}
                  id="subscription-lifecycle-reason"
                  onBlur={() => setReasonTouched(true)}
                  onChange={(event) => setReason(event.target.value)}
                  placeholder="Pourquoi cette intervention est-elle nécessaire ?"
                  value={reason}
                />
                {reasonTouched && reasonProblem ? (
                  <p className="text-xs text-destructive" id="subscription-lifecycle-reason-error" role="alert">
                    {reasonProblem}
                  </p>
                ) : null}
              </div>

              <DialogFooter>
                <Button onClick={closeDialog} type="button" variant="outline">
                  Annuler
                </Button>
                <Button
                  disabled={!previewReady || Boolean(reasonProblem) || applyMutation.isPending}
                  onClick={() => {
                    setReasonTouched(true);
                    if (previewReady && !reasonProblem) applyMutation.mutate();
                  }}
                  type="button"
                  variant={presentation.destructive ? "destructive" : "default"}
                >
                  {applyMutation.isPending ? "Enregistrement…" : presentation.label}
                </Button>
              </DialogFooter>
            </>
          ) : null}
        </DialogContent>
      </Dialog>
    </section>
  );
}
