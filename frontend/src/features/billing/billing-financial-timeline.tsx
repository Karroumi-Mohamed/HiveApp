import { ArrowRightIcon } from "@phosphor-icons/react";
import type { QueryKey } from "@tanstack/react-query";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router";
import type { BillingFinancialTimelineEntry, BillingTimelineEntryType, PageResponse } from "@/api/contracts";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { billingTimelineStateLabel, billingTimelineTypeLabel } from "@/features/admin/billing/billing-presentation";
import { formatExactMoney } from "@/lib/exact-decimal";

type Query = { type?: BillingTimelineEntryType; currencyCode?: string; page: number; size: number };
type Props = {
  enabled: boolean;
  invoiceHref?: (invoiceId: string) => string;
  load: (query: Query) => Promise<PageResponse<BillingFinancialTimelineEntry>>;
  queryKey: (query: Query) => QueryKey;
};

const occurred = (value: string) =>
  new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));

export function BillingFinancialTimeline({ enabled, invoiceHref, load, queryKey }: Props) {
  const [page, setPage] = useState(0);
  const [type, setType] = useState<BillingTimelineEntryType | "ALL">("ALL");
  const [currency, setCurrency] = useState("");
  const query = { type: type === "ALL" ? undefined : type, currencyCode: currency || undefined, page, size: 20 };
  const timeline = useQuery({
    queryKey: queryKey(query),
    queryFn: () => load(query),
    enabled,
    placeholderData: keepPreviousData,
    retry: false,
  });
  if (!enabled) return null;
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <div className="flex flex-col gap-3 border-b px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
        <h2 className="font-semibold">Historique financier</h2>
        <div className="flex gap-2">
          <Select
            onValueChange={(value) => {
              setType(value as typeof type);
              setPage(0);
            }}
            value={type}
          >
            <SelectTrigger aria-label="Type d’opération" className="w-40">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">Toutes</SelectItem>
              <SelectItem value="INVOICE">Documents</SelectItem>
              <SelectItem value="PAYMENT">Paiements</SelectItem>
              <SelectItem value="CREDIT">Avoirs</SelectItem>
              <SelectItem value="REFUND">Remboursements</SelectItem>
            </SelectContent>
          </Select>
          <Input
            aria-label="Devise"
            className="w-24 uppercase"
            maxLength={3}
            onChange={(e) => {
              setCurrency(e.target.value.trim().toUpperCase());
              setPage(0);
            }}
            placeholder="Devise"
            value={currency}
          />
        </div>
      </div>
      {timeline.isLoading ? (
        <div className="p-5">
          <LoadingState rows={5} />
        </div>
      ) : timeline.isError ? (
        <ErrorState retry={() => void timeline.refetch()} />
      ) : timeline.data?.content.length ? (
        <div className="divide-y">
          {timeline.data.content.map((entry) => (
            <div
              className="grid gap-2 px-5 py-4 sm:grid-cols-[minmax(0,1fr)_auto_auto] sm:items-center sm:gap-8"
              key={`${entry.type}-${entry.recordId}`}
            >
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                  <p className="font-medium">{billingTimelineTypeLabel[entry.type]}</p>
                  <span
                    className={entry.status === "FAILED" ? "text-sm text-destructive" : "text-sm text-muted-foreground"}
                  >
                    {billingTimelineStateLabel(entry.type, entry.status)}
                  </span>
                </div>
                <p className="mt-1 text-xs text-muted-foreground">
                  {entry.invoiceNumber} · {occurred(entry.occurredAt)}
                </p>
              </div>
              <p className="font-semibold tabular-nums">{formatExactMoney(entry.amount, entry.currencyCode)}</p>
              {invoiceHref ? (
                <Button asChild size="icon-sm" variant="ghost">
                  <Link aria-label={`Ouvrir ${entry.invoiceNumber}`} to={invoiceHref(entry.invoiceId)}>
                    <ArrowRightIcon className="rtl:rotate-180" />
                  </Link>
                </Button>
              ) : null}
            </div>
          ))}
        </div>
      ) : (
        <EmptyState title="Aucune opération financière" />
      )}
      {timeline.data ? (
        <PaginationBar
          onPageChange={setPage}
          page={timeline.data.page}
          totalElements={timeline.data.totalElements}
          totalPages={timeline.data.totalPages}
        />
      ) : null}
    </section>
  );
}
