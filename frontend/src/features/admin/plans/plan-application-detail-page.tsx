import { ArrowLeftIcon, CheckCircleIcon, ClockIcon, WarningCircleIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useId, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router";
import {
  type ContentDetail,
  type ContentItemStatus,
  type ContentResult,
  type ContentSummary,
  planApplicationApi,
} from "@/api/plan-application-api";
import { adminPermissions, adminSubscriptionDetailSurfacePermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable, DataTableExpander } from "@/components/patterns/data-table";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { formatExactMoney } from "@/lib/exact-decimal";
import {
  canConfirmContent,
  contentApplicationKey,
  contentConflictCategory,
  contentPolling,
} from "./plan-application-rules";
import { ContentStatus, PlanContentImpact } from "./plan-content-impact";

function useApplicationDate() {
  const { i18n } = useTranslation();
  return (value: string | null) =>
    value
      ? new Intl.DateTimeFormat(i18n.language === "ar" ? "ar-MA" : "fr-MA", {
          dateStyle: "medium",
          timeStyle: "short",
        }).format(new Date(value))
      : "—";
}

export function PlanApplications({ planId }: { planId: string }) {
  const session = useAdminSession();
  const { t } = useTranslation();
  const date = useApplicationDate();
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: [...contentApplicationKey, "list", planId, page],
    queryFn: () => planApplicationApi.list(planId, page),
    enabled: session.can(adminPermissions.plansListApplications),
  });
  if (!session.can(adminPermissions.plansListApplications)) return null;
  if (query.isLoading) return <LoadingState />;
  if (query.isError) return <ErrorState retry={() => void query.refetch()} />;
  const column = createDataColumns<ContentSummary>();
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <h2 className="border-b p-5 font-semibold">{t("planApplication.history")}</h2>
      <DataTable
        data={query.data?.content ?? []}
        getRowId={(row) => row.id}
        emptyState={<EmptyState title={t("planApplication.noApplications")} />}
        columns={column.columns([
          column.accessor("createdAt", {
            header: t("planApplication.planned"),
            cell: ({ getValue }) => date(getValue()),
          }),
          column.accessor("status", {
            header: t("planApplication.status"),
            cell: ({ getValue }) => <ContentStatus status={getValue()} />,
          }),
          column.display({
            id: "counts",
            header: t("planApplication.total"),
            cell: ({ row }) => (
              <span className="tabular-nums">
                {row.original.counts.APPLIED ?? 0} /{" "}
                {Object.values(row.original.counts).reduce((sum, value) => sum + value, 0)}
              </span>
            ),
          }),
          column.accessor("reason", {
            header: t("planApplication.reason"),
            cell: ({ getValue }) => <p className="max-w-sm truncate">{getValue()}</p>,
          }),
          column.display({
            id: "open",
            header: "",
            cell: ({ row }) =>
              session.can(adminPermissions.plansReadApplication) && (
                <Button asChild size="sm" variant="ghost">
                  <Link to={`/admin/plans/${planId}/applications/${row.original.id}`}>{t("planVersions.open")}</Link>
                </Button>
              ),
          }),
        ])}
      />
      {query.data && <PaginationBar {...query.data} onPageChange={setPage} />}
    </section>
  );
}

function ContentResults({
  detail,
  onRetryNotice,
  reason,
  onReasonChange,
}: {
  detail: ContentDetail;
  onRetryNotice: (id: string) => void;
  reason: string | null;
  onReasonChange: (value: string | null) => void;
}) {
  const { t } = useTranslation();
  const session = useAdminSession();
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<ContentItemStatus | "ALL">("ALL");
  const activeStatus = reason ? "CONFLICT" : status;
  useEffect(() => {
    if (reason !== undefined) setPage(0);
  }, [reason]);
  const jobId = detail.summary.id;
  const allowed = session.can(adminPermissions.plansApplicationResults);
  const query = useQuery({
    queryKey: [
      ...contentApplicationKey,
      jobId,
      "results",
      page,
      activeStatus,
      reason,
      detail.summary.version,
      detail.summary.status,
    ],
    queryFn: () =>
      planApplicationApi.results(jobId, page, activeStatus === "ALL" ? undefined : activeStatus, reason ?? undefined),
    enabled: allowed,
    refetchInterval: contentPolling(detail.summary.status),
  });
  const ids = query.data?.content.map((row) => row.id) ?? [];
  const identities = useQuery({
    queryKey: [...contentApplicationKey, jobId, "identities", ids],
    queryFn: () => planApplicationApi.identities(jobId, ids),
    enabled: allowed && session.can(adminPermissions.plansApplicationIdentities) && ids.length > 0,
  });
  if (!allowed) return null;
  const names = new Map(identities.data?.map((identity) => [identity.itemId, identity]));
  const column = createDataColumns<ContentResult>();
  const canOpen = adminSubscriptionDetailSurfacePermissions.some(session.can);
  const columns = column.columns([
    column.display({
      id: "expand",
      header: "",
      cell: ({ row }) => (
        <DataTableExpander
          row={row}
          expandLabel={t("planApplication.detail")}
          collapseLabel={t("planApplication.hide")}
        />
      ),
    }),
    column.display({
      id: "account",
      header: t("planApplication.account"),
      cell: ({ row }) => names.get(row.original.id)?.accountName ?? t("planApplication.privateAccount"),
    }),
    column.display({
      id: "versions",
      header: t("planApplication.versions"),
      cell: ({ row }) =>
        row.original.impact ? (
          <span dir="ltr">
            V{row.original.impact.sourceVersion} → V{row.original.impact.targetVersion}
          </span>
        ) : (
          "—"
        ),
    }),
    column.accessor("status", {
      header: t("planApplication.status"),
      cell: ({ getValue }) => <ContentStatus status={getValue()} />,
    }),
    column.display({
      id: "price",
      header: t("planApplication.price"),
      cell: ({ row }) =>
        row.original.impact ? formatExactMoney(row.original.impact.retainedTotal, row.original.impact.currency) : "—",
    }),
    column.display({
      id: "notice",
      header: t("planApplication.notifications"),
      cell: ({ row }) => {
        const notice = row.original.notice;
        if (!notice) return "—";
        const key =
          notice.emailDelivery === "FAILED"
            ? "emailFailed"
            : notice.emailDelivery === "CANCELLED"
              ? "emailCancelled"
              : notice.emailDelivery;
        return (
          <div className="space-y-1">
            <p className="text-xs">{t(`planApplication.${key}`)}</p>
            {session.can(adminPermissions.plansRetryNotices) &&
              session.can(adminPermissions.plansApplyVersion) &&
              session.can(adminPermissions.plansPreviewApplication) &&
              detail.summary.status !== "CANCELLED" &&
              (["FAILED", "SUPPRESSED"].includes(notice.emailDelivery) ||
                row.original.outcomeCode === "NOTICE_RECIPIENT_CHANGED") && (
                <Button size="sm" variant="ghost" onClick={() => onRetryNotice(notice.id)}>
                  {t("planApplication.retryNotice")}
                </Button>
              )}
          </div>
        );
      },
    }),
  ]);
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b p-4">
        <h2 className="font-semibold">{t("planApplication.results")}</h2>
        <Select
          value={activeStatus}
          onValueChange={(value) => {
            setStatus(value as typeof status);
            onReasonChange(null);
            setPage(0);
          }}
        >
          <SelectTrigger className="w-56" aria-label={t("planApplication.status")}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">{t("planApplication.any")}</SelectItem>
            {[
              ...new Set([...Object.keys(detail.summary.counts), ...(activeStatus === "ALL" ? [] : [activeStatus])]),
            ].map((value) => (
              <SelectItem value={value} key={value}>
                {t(`planApplication.${value}`)} ({detail.summary.counts[value as ContentItemStatus] ?? 0})
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      {identities.isError && (
        <div className="p-3">
          <ErrorState retry={() => void identities.refetch()} />
        </div>
      )}
      {query.isError ? (
        <ErrorState retry={() => void query.refetch()} />
      ) : (
        <DataTable
          columns={columns}
          data={query.data?.content ?? []}
          getRowId={(row) => row.id}
          isLoading={query.isLoading}
          emptyState={<EmptyState title={t("planApplication.noResults")} />}
          getRowCanExpand={(row) => !!row.impact || !!row.outcomeCode || row.executionConflicts.length > 0}
          renderExpandedRow={(row) => (
            <div className="space-y-4 bg-muted/60 p-5">
              {row.impact && (
                <PlanContentImpact
                  before={row.impact.beforeLimits}
                  after={row.impact.afterLimits}
                  removed={row.impact.removedFeatures}
                  added={row.impact.addedFeatures}
                />
              )}
              {(row.status === "CONFLICT" || row.status === "FAILED") && (
                <div className="space-y-2 border-s-2 border-warning ps-4">
                  <p className="text-sm">
                    {t(
                      row.status === "FAILED"
                        ? "planApplication.retry"
                        : ["NOTICE_REQUIRED_FAILED", "NOTICE_RECIPIENT_CHANGED"].includes(row.outcomeCode ?? "")
                          ? "planApplication.noticeRetryHint"
                          : "planApplication.blockedHint",
                    )}
                  </p>
                  <ul className="space-y-1 text-sm text-muted-foreground">
                    {[
                      ...new Map(
                        [...(row.impact?.conflicts ?? []), ...row.executionConflicts].map((conflict) => [
                          JSON.stringify(conflict),
                          conflict,
                        ]),
                      ).entries(),
                    ].map(([key, conflict]) => (
                      <li key={key}>{conflict.message}</li>
                    ))}
                  </ul>
                  {row.outcomeCode && <p className="text-xs text-muted-foreground">{row.outcomeCode}</p>}
                </div>
              )}
              {names.get(row.id) && canOpen && (
                <Button asChild size="sm" variant="outline">
                  <Link to={`/admin/subscriptions/${names.get(row.id)?.accountId}`}>
                    {t("planApplication.openAccount")}
                  </Link>
                </Button>
              )}
            </div>
          )}
        />
      )}
      {query.data && <PaginationBar {...query.data} onPageChange={setPage} />}
    </section>
  );
}

export function PlanApplicationDetailPage() {
  const { planId = "", applicationId = "" } = useParams();
  const { t } = useTranslation();
  const session = useAdminSession();
  const date = useApplicationDate();
  const queryClient = useQueryClient();
  const id = useId();
  const [partial, setPartial] = useState(false);
  const [conflictReason, setConflictReason] = useState<string | null>(null);
  const [now, setNow] = useState(Date.now());
  const [action, setAction] = useState<"cancel" | "retry" | "confirm" | { notice: string } | null>(null);
  const [actionTitle, setActionTitle] = useState("confirm");
  const [reason, setReason] = useState("");
  const query = useQuery({
    queryKey: [...contentApplicationKey, applicationId],
    queryFn: () => planApplicationApi.detail(applicationId),
    enabled: session.can(adminPermissions.plansReadApplication),
    refetchInterval: (query) => contentPolling(query.state.data?.summary.status),
  });
  useEffect(() => {
    const expiry = query.data?.expiresAt;
    if (!expiry) return;
    const timer = window.setTimeout(() => setNow(Date.now()), Math.max(0, Date.parse(expiry) - Date.now() + 25));
    return () => window.clearTimeout(timer);
  }, [query.data?.expiresAt]);
  const mutation = useMutation({
    mutationFn: async () => {
      if (action === "confirm" && query.data && canConfirmContent(query.data, partial))
        return planApplicationApi.confirm(applicationId, query.data.previewToken as string, partial);
      if (action === "cancel") return planApplicationApi.cancel(applicationId, reason.trim());
      if (action === "retry") return planApplicationApi.retry(applicationId, reason.trim());
      if (action && typeof action === "object")
        return planApplicationApi.retryNotices(applicationId, [action.notice], reason.trim());
      throw new Error("Review is not confirmable");
    },
    onSuccess: () => {
      setAction(null);
      setReason("");
      setConflictReason(null);
      void queryClient.invalidateQueries({ queryKey: contentApplicationKey });
      void queryClient.invalidateQueries({ queryKey: ["admin", "plans"] });
    },
    onError: () => {
      void query.refetch();
    },
  });
  if (!session.can(adminPermissions.plansReadApplication)) return <PermissionState />;
  if (query.isLoading) return <LoadingState />;
  if (query.isError || !query.data) return <ErrorState retry={() => void query.refetch()} />;
  const detail = query.data;
  const { summary, definition } = detail;
  const hasApply = [
    adminPermissions.plansConfirmApplication,
    adminPermissions.plansApplyVersion,
    adminPermissions.plansPreviewApplication,
  ].every(session.can);
  const hasCreate =
    [adminPermissions.plansCreateApplication, adminPermissions.plansPreviewApplication].every(session.can) &&
    [adminPermissions.plansReadApplication, adminPermissions.plansListVersions, adminPermissions.plansReadDetail].every(
      session.can,
    );
  const finished = ["COMPLETED", "COMPLETED_WITH_ERRORS", "CANCELLED"].includes(summary.status);
  const startAction = (value: typeof action) => {
    setAction(value);
    setActionTitle(typeof value === "object" && value ? "retryNotice" : (value ?? "confirm"));
    setReason("");
    mutation.reset();
  };
  return (
    <div className="space-y-5">
      <Button asChild variant="ghost" size="sm">
        <Link to={`/admin/plans/${planId}/history`}>
          <ArrowLeftIcon className="rtl:rotate-180" />
          {t("planApplication.history")}
        </Link>
      </Button>
      <PageHeader
        title={t("planApplication.title")}
        description={t("planApplication.preserved")}
        actions={<ContentStatus status={summary.status} />}
      />
      <section
        className="grid grid-cols-2 gap-px overflow-hidden rounded-xl border bg-border sm:grid-cols-4"
        aria-live="polite"
      >
        {["total", "READY", "APPLIED", "CONFLICT"].map((key) => (
          <div className="bg-card p-5" key={key}>
            <p className="text-xs text-muted-foreground">{t(`planApplication.${key}`)}</p>
            <p className="mt-1 text-2xl font-semibold tabular-nums">
              {key === "total"
                ? Object.values(summary.counts).reduce((sum, value) => sum + value, 0)
                : (summary.counts[key as ContentItemStatus] ?? 0)}
            </p>
          </div>
        ))}
      </section>
      {((summary.counts.WAITING ?? 0) > 0 || (summary.counts.FAILED ?? 0) > 0) && (
        <div className="flex flex-wrap gap-5 rounded-lg border bg-card p-4 text-sm" aria-live="polite">
          {(summary.counts.WAITING ?? 0) > 0 && (
            <span>
              {t("planApplication.WAITING")} · <strong>{summary.counts.WAITING}</strong>
            </span>
          )}
          {(summary.counts.FAILED ?? 0) > 0 && (
            <span className="text-warning">
              {t("planApplication.FAILED")} · <strong>{summary.counts.FAILED}</strong>
            </span>
          )}
        </div>
      )}
      <section className="space-y-4 rounded-xl border bg-card p-5">
        <div className="flex flex-wrap justify-between gap-4">
          <div className="flex items-center gap-2 text-sm">
            <ClockIcon className="size-5" />
            {t(`planApplication.${definition.request.application.timing}`)}
            {definition.request.application.notBefore && ` · ${date(definition.request.application.notBefore)}`}
          </div>
          <span className="text-sm text-muted-foreground">
            {t(`planApplication.${definition.request.notificationPolicy}`)}
          </span>
        </div>
        <p className="text-sm text-muted-foreground">{summary.reason}</p>
        {summary.status === "PREVIEWED" && (
          <>
            {detail.reviewInvalidated || (detail.expiresAt && Date.parse(detail.expiresAt) <= now) ? (
              <p role="alert" className="text-sm text-warning">
                {t("planApplication.stale")}
              </p>
            ) : null}
            {(summary.counts.CONFLICT ?? 0) > 0 && (
              <Label className="flex min-h-11 items-center gap-3">
                <Checkbox checked={partial} onCheckedChange={(value) => setPartial(value === true)} />
                {t("planApplication.partial")}
              </Label>
            )}
          </>
        )}
        <div className="flex flex-wrap gap-2">
          {summary.status === "PREVIEWED" && hasApply && (
            <Button disabled={!canConfirmContent(detail, partial, now)} onClick={() => startAction("confirm")}>
              <CheckCircleIcon />
              {t("planApplication.confirm")}
            </Button>
          )}
          {hasCreate && (summary.status === "PREVIEWED" || finished) && (
            <Button asChild variant="outline">
              <Link to={`/admin/plans/${definition.targetPlanId}/apply`}>{t("planApplication.restart")}</Link>
            </Button>
          )}
          {!finished && session.can(adminPermissions.plansCancelApplication) && (
            <Button variant="ghost" onClick={() => startAction("cancel")}>
              {t("planApplication.cancel")}
            </Button>
          )}
          {summary.status === "COMPLETED_WITH_ERRORS" &&
            (summary.counts.FAILED ?? 0) > 0 &&
            [
              adminPermissions.plansRetryApplication,
              adminPermissions.plansApplyVersion,
              adminPermissions.plansPreviewApplication,
            ].every(session.can) && (
              <Button variant="outline" onClick={() => startAction("retry")}>
                {t("planApplication.retry")}
              </Button>
            )}
          <Button variant="ghost" onClick={() => void query.refetch()}>
            {t("planApplication.refresh")}
          </Button>
        </div>
      </section>
      {detail.conflicts.length > 0 && (
        <section className="rounded-xl border border-warning/30 bg-warning-subtle p-5">
          <h2 className="flex items-center gap-2 font-semibold">
            <WarningCircleIcon />
            {t("planApplication.resolve")}
          </h2>
          <p className="my-2 text-sm">{t("planApplication.conflictGroupsHint")}</p>
          <ul className="space-y-2">
            {detail.conflicts.map((group) => (
              <li key={group.primaryReason} className="flex justify-between gap-3 text-sm">
                <button
                  type="button"
                  disabled={!session.can(adminPermissions.plansApplicationResults)}
                  aria-pressed={conflictReason === group.primaryReason}
                  onClick={() =>
                    setConflictReason((previous) => (previous === group.primaryReason ? null : group.primaryReason))
                  }
                  className="text-start font-medium hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  {t(`planApplication.${contentConflictCategory(group.primaryReason, group.resolutionKind)}`)}
                  <span className="mt-1 block text-xs text-muted-foreground">{group.primaryReason}</span>
                </button>
                <strong className="tabular-nums">{group.accounts}</strong>
              </li>
            ))}
          </ul>
        </section>
      )}
      <ContentResults
        detail={detail}
        reason={conflictReason}
        onReasonChange={setConflictReason}
        onRetryNotice={(notice) => startAction({ notice })}
      />
      <Dialog
        open={action !== null}
        onOpenChange={(open) => {
          if (!open && !mutation.isPending) setAction(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t(`planApplication.${actionTitle}`)}</DialogTitle>
            <DialogDescription>
              {t(action === "cancel" ? "planApplication.cancelHint" : "planApplication.preserved")}
            </DialogDescription>
          </DialogHeader>
          {action !== null && action !== "confirm" && (
            <div className="space-y-2">
              <Label htmlFor={`${id}-reason`}>{t("planApplication.reason")}</Label>
              <Textarea
                id={`${id}-reason`}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                maxLength={2000}
              />
            </div>
          )}
          {mutation.isError && (
            <p role="alert" className="text-sm text-destructive">
              {t("planApplication.error")}
            </p>
          )}
          <DialogFooter>
            <Button variant="ghost" disabled={mutation.isPending} onClick={() => setAction(null)}>
              {t("planApplication.close")}
            </Button>
            <Button
              disabled={
                mutation.isPending || (action === "confirm" ? !canConfirmContent(detail, partial, now) : !reason.trim())
              }
              onClick={() => mutation.mutate()}
            >
              {t(action === "confirm" ? "planApplication.confirm" : `planApplication.${actionTitle}`)}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
