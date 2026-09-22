import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { planApplicationApi } from "@/api/plan-application-api";
import { clientPermissions } from "@/auth/permissions";
import { useClientSession } from "@/auth/session-provider";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { ContentStatus, PlanContentImpact } from "@/features/admin/plans/plan-content-impact";

export function ClientContentNotices() {
  const { t, i18n } = useTranslation();
  const session = useClientSession();
  const cache = useQueryClient();
  const [page, setPage] = useState(0);
  const key = ["client", "content-notices", session.selectedCompanyId, session.isB2B];
  const allowed = session.can(clientPermissions.subscriptionReadContentNotices);
  const query = useQuery({
    queryKey: [...key, page],
    queryFn: () => planApplicationApi.notices(page),
    enabled: allowed,
  });
  const mark = useMutation({
    mutationFn: planApplicationApi.markRead,
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: key });
    },
  });
  const date = (value: string) =>
    new Intl.DateTimeFormat(i18n.language === "ar" ? "ar-MA" : "fr-MA", {
      dateStyle: "medium",
      timeStyle: "short",
    }).format(new Date(value));
  if (!allowed) return <PermissionState />;
  if (query.isLoading) return <LoadingState />;
  if (query.isError) return <ErrorState retry={() => void query.refetch()} />;
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <div className="border-b p-5">
        <h2 className="font-semibold">{t("planApplication.noticesTitle")}</h2>
        <p className="mt-1 text-sm text-muted-foreground">{t("planApplication.noticeHint")}</p>
      </div>
      {mark.isError && (
        <p role="alert" className="p-4 text-sm text-destructive">
          {t("planApplication.error")}
        </p>
      )}
      <ul className="divide-y">
        {query.data?.content.map((notice) => (
          <li key={notice.id} className="space-y-4 p-5">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h3 className="font-semibold">
                {notice.planName}{" "}
                <span className="ms-2 text-sm font-normal text-muted-foreground" dir="ltr">
                  V{notice.sourceVersion} → V{notice.targetVersion}
                </span>
              </h3>
              <ContentStatus
                status={notice.state}
                label={notice.state === "CONFLICT" ? t("planApplication.clientConflict") : undefined}
              />
            </div>
            <p className="text-sm">{t("planApplication.retainedClient")}</p>
            <p className="text-sm text-muted-foreground">
              {notice.effectiveAt
                ? `${t("planApplication.effective")} · ${date(notice.effectiveAt)}`
                : `${t(`planApplication.${notice.timing}`)} · ${date(notice.plannedAt)}`}
            </p>
            <PlanContentImpact
              before={notice.beforeLimits}
              after={notice.afterLimits}
              removed={notice.removedFeatures}
              added={notice.addedFeatures}
            />
            <div className="flex justify-end">
              {!notice.read && session.can(clientPermissions.subscriptionMarkContentNoticeRead) ? (
                <Button variant="ghost" size="sm" disabled={mark.isPending} onClick={() => mark.mutate(notice.id)}>
                  {t("planApplication.markRead")}
                </Button>
              ) : (
                <span className="text-xs text-muted-foreground">
                  {t(`planApplication.${notice.read ? "read" : "unread"}`)}
                </span>
              )}
            </div>
          </li>
        ))}
      </ul>
      {!query.data?.content.length && <EmptyState title={t("planApplication.noNotices")} />}
      {query.data && <PaginationBar {...query.data} onPageChange={setPage} />}
    </section>
  );
}
