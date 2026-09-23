import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { communicationApi, type SentNotice } from "@/api/communication-api";
import { createDataColumns, DataTable } from "@/components/patterns/data-table";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { ErrorState } from "@/components/patterns/remote-state";
import { communicationDate, useCommunicationCopy } from "./communication-copy";

import { ManualNotificationPreview } from "./manual-notification-editor";

const columns = createDataColumns<SentNotice>();
export function InternalNotificationHistory({ identity }: { identity: string }) {
  const c = useCommunicationCopy();
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: ["client", "notifications", identity, "sent", page],
    queryFn: () => communicationApi.sent(page),
    refetchInterval: 15000,
    refetchIntervalInBackground: false,
  });
  const table = columns.columns([
    columns.accessor("messageTitle", {
      header: c("subject"),
      cell: ({ row }) => (
        <details>
          <summary className="cursor-pointer font-medium text-primary" dir="auto">
            {row.original.messageTitle}
          </summary>
          <div className="mt-3">
            <ManualNotificationPreview value={row.original} />
          </div>
        </details>
      ),
    }),
    columns.accessor("createdAt", { header: c("sentAt"), cell: ({ getValue }) => communicationDate(getValue()) }),
    columns.accessor("recipients", { header: c("selectedMembers") }),
    columns.accessor("delivered", { header: c("DELIVERED") }),
    columns.accessor("pending", { header: c("PENDING") }),
    columns.accessor("failed", { header: c("FAILED") }),
  ]);
  return (
    <section className="space-y-4">
      <p className="max-w-prose text-sm text-muted-foreground">{c("sentHint")}</p>
      {query.isError ? (
        <ErrorState retry={() => void query.refetch()} />
      ) : (
        <div className="overflow-hidden rounded-xl border bg-card">
          <DataTable
            columns={table}
            data={query.data?.content ?? []}
            getRowId={(r) => r.commandId}
            isLoading={query.isLoading}
          />
          {query.data && <PaginationBar {...query.data} page={page} onPageChange={setPage} />}
        </div>
      )}
    </section>
  );
}
