import { useTranslation } from "react-i18next";
import type { EffectiveQuotaLimit } from "@/api/plan-application-api";
import { createDataColumns, DataTable } from "@/components/patterns/data-table";
import { changedContentLimits } from "./plan-application-rules";

export function ContentStatus({ status, label }: { status: string; label?: string }) {
  const { t } = useTranslation();
  const color =
    status === "APPLIED" || status === "COMPLETED"
      ? "text-success"
      : ["CONFLICT", "FAILED", "COMPLETED_WITH_ERRORS"].includes(status)
        ? "text-warning"
        : "text-muted-foreground";
  return <span className={`text-sm font-medium ${color}`}>{label ?? t(`planApplication.${status}`)}</span>;
}

export function PlanContentImpact({
  before,
  after,
  removed,
  added = [],
}: {
  before: EffectiveQuotaLimit[];
  after: EffectiveQuotaLimit[];
  removed: string[];
  added?: string[];
}) {
  const { t } = useTranslation();
  const rows = changedContentLimits(before, after);
  const columns = createDataColumns<(typeof rows)[number]>();
  const limit = (value?: EffectiveQuotaLimit) =>
    !value
      ? t("planApplication.absent")
      : value.mode === "UNLIMITED"
        ? t("planApplication.unlimited")
        : value.effectiveLimit;
  return (
    <div className="space-y-3">
      {rows.length > 0 ? (
        <DataTable
          getRowId={(row) => row.id}
          data={rows}
          columns={columns.columns([
            columns.accessor("resource", {
              header: t("planVersions.feature"),
              cell: ({ row }) => (
                <div>
                  {t(`planApplication.resource_${row.original.resource}`, { defaultValue: row.original.resource })}
                  <p className="text-xs text-muted-foreground">
                    {t(`planApplication.${row.original.featureCode.replaceAll(".", "_")}`, {
                      defaultValue: row.original.featureCode,
                    })}
                  </p>
                </div>
              ),
            }),
            columns.display({
              id: "before",
              header: t("planApplication.before"),
              cell: ({ row }) => limit(row.original.before),
            }),
            columns.display({
              id: "after",
              header: t("planApplication.after"),
              cell: ({ row }) => <span className="font-semibold">{limit(row.original.after)}</span>,
            }),
          ])}
        />
      ) : (
        <p className="text-sm text-muted-foreground">{t("planApplication.noLimitChange")}</p>
      )}
      {removed.length > 0 && (
        <div className="rounded-lg border border-warning/30 bg-warning-subtle p-3">
          <h4 className="text-sm font-medium">{t("planApplication.removed")}</h4>
          <ul className="mt-2 list-inside list-disc text-sm">
            {removed.map((code) => (
              <li key={code}>{t(`planApplication.${code.replaceAll(".", "_")}`, { defaultValue: code })}</li>
            ))}
          </ul>
        </div>
      )}
      {added.length > 0 && (
        <div className="rounded-lg border border-success/30 bg-success-subtle p-3">
          <h4 className="text-sm font-medium">{t("planApplication.added")}</h4>
          <ul className="mt-2 list-inside list-disc text-sm">
            {added.map((code) => (
              <li key={code}>{t(`planApplication.${code.replaceAll(".", "_")}`, { defaultValue: code })}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
