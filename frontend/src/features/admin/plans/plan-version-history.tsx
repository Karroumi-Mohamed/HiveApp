import { useQuery } from "@tanstack/react-query";
import { useId, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { type PlanHistoryEvent, planApplicationApi } from "@/api/plan-application-api";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable } from "@/components/patterns/data-table";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

export function PlanVersionHistory({ planId }: { planId: string }) {
  const session = useAdminSession();
  const { t, i18n } = useTranslation();
  const id = useId();
  const [page, setPage] = useState(0);
  const [kind, setKind] = useState("ALL");
  const [from, setFrom] = useState("");
  const [until, setUntil] = useState("");
  const [actor, setActor] = useState<{ id: string; label: string } | null>(null);
  const date = (value: string) => (value ? new Date(`${value}T00:00:00`).toISOString() : undefined);
  const query = useQuery({
    queryKey: ["admin", "plans", planId, "version-history", page, kind, from, until, actor?.id],
    queryFn: () =>
      planApplicationApi.history(planId, {
        page,
        kind: kind === "ALL" ? undefined : kind,
        from: date(from),
        until: date(until),
        actorId: actor?.id,
      }),
    enabled: session.can(adminPermissions.plansReadVersionHistory) && (!from || !until || from < until),
  });
  if (!session.can(adminPermissions.plansReadVersionHistory)) return null;
  const column = createDataColumns<PlanHistoryEvent>();
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <div className="space-y-4 border-b p-5">
        <h2 className="font-semibold">{t("planApplication.versionHistory")}</h2>
        <div className="flex flex-wrap items-end gap-3">
          <div className="space-y-1">
            <Label htmlFor={`${id}-kind`}>{t("planApplication.eventKind")}</Label>
            <Select
              value={kind}
              onValueChange={(value) => {
                setKind(value);
                setPage(0);
              }}
            >
              <SelectTrigger id={`${id}-kind`} className="w-52">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {["ALL", "VERSION", "APPLICATION"].map((value) => (
                  <SelectItem key={value} value={value}>
                    {t(`planApplication.event${value}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1">
            <Label htmlFor={`${id}-from`}>{t("planApplication.fromDate")}</Label>
            <Input
              id={`${id}-from`}
              type="date"
              value={from}
              onChange={(e) => {
                setFrom(e.target.value);
                setPage(0);
              }}
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor={`${id}-until`}>{t("planApplication.untilDate")}</Label>
            <Input
              id={`${id}-until`}
              type="date"
              value={until}
              onChange={(e) => {
                setUntil(e.target.value);
                setPage(0);
              }}
            />
          </div>
          {actor && (
            <Button
              variant="outline"
              onClick={() => {
                setActor(null);
                setPage(0);
              }}
            >
              {actor.label} · {t("planApplication.clearFilter")}
            </Button>
          )}
        </div>
        {from && until && from >= until && (
          <p role="alert" className="text-sm text-destructive">
            {t("planApplication.dateRangeError")}
          </p>
        )}
      </div>
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState retry={() => void query.refetch()} />
      ) : (
        <DataTable
          data={query.data?.content ?? []}
          getRowId={(row) => row.id}
          emptyState={<EmptyState title={t("planApplication.noHistory")} />}
          columns={column.columns([
            column.accessor("occurredAt", {
              header: t("planApplication.eventDate"),
              cell: ({ getValue }) =>
                new Intl.DateTimeFormat(i18n.language === "ar" ? "ar-MA" : "fr-MA", {
                  dateStyle: "medium",
                  timeStyle: "short",
                }).format(new Date(getValue())),
            }),
            column.accessor("action", {
              header: t("planApplication.eventAction"),
              cell: ({ getValue }) =>
                t(`planApplication.actions.${getValue().split(".").at(-1)}`, { defaultValue: getValue() }),
            }),
            column.display({
              id: "actor",
              header: t("planApplication.eventActor"),
              cell: ({ row }) =>
                row.original.actorUserId ? (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => {
                      setActor({
                        id: row.original.actorUserId as string,
                        label: row.original.actorLabel || t("planApplication.adminActor"),
                      });
                      setPage(0);
                    }}
                  >
                    {row.original.actorLabel || t("planApplication.adminActor")}
                  </Button>
                ) : (
                  t("planApplication.systemActor")
                ),
            }),
            column.accessor("outcome", {
              header: t("planApplication.status"),
              cell: ({ getValue }) => (
                <span className={getValue() === "SUCCEEDED" ? "text-success" : "text-destructive"}>
                  {t(`planApplication.${getValue() === "SUCCEEDED" ? "success" : "FAILED"}`)}
                </span>
              ),
            }),
            column.display({
              id: "open",
              header: "",
              cell: ({ row }) =>
                (row.original.kind === "APPLICATION"
                  ? session.can(adminPermissions.plansReadApplication)
                  : session.can(adminPermissions.plansReadDetail)) && (
                  <Button asChild variant="ghost" size="sm">
                    <Link
                      to={
                        row.original.kind === "APPLICATION"
                          ? `/admin/plans/${planId}/applications/${row.original.resourceId}`
                          : `/admin/plans/${row.original.resourceId}`
                      }
                    >
                      {row.original.productVersionNumber
                        ? `V${row.original.productVersionNumber}`
                        : t("planVersions.open")}
                    </Link>
                  </Button>
                ),
            }),
          ])}
        />
      )}
      {query.data && <PaginationBar {...query.data} onPageChange={setPage} />}
    </section>
  );
}
