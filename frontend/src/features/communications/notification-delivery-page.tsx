import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router";
import { communicationApi, type NotificationEmail, type NotificationEvent } from "@/api/communication-api";
import { adminPermissions as p } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable } from "@/components/patterns/data-table";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { ErrorState, PermissionState } from "@/components/patterns/remote-state";
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { communicationDate, useCommunicationCopy } from "./communication-copy";

type DeliveryRow = NotificationEvent | NotificationEmail;
const columns = createDataColumns<DeliveryRow>();
export function NotificationDeliveryPage({ embedded = false }: { embedded?: boolean }) {
  const c = useCommunicationCopy();
  const [email, setEmail] = useState(false);
  return (
    <div className="space-y-5">
      {!embedded && (
        <Link to="/admin/communications?channel=notifications" className="text-sm text-primary">
          ← {c("title")}
        </Link>
      )}
      <PageHeader title={c("deliveryQueue")} />
      <fieldset className="flex gap-2">
        <legend className="sr-only">{c("deliveryChannel")}</legend>
        <Button variant={email ? "ghost" : "secondary"} aria-pressed={!email} onClick={() => setEmail(false)}>
          {c("inbox")}
        </Button>
        <Button variant={email ? "secondary" : "ghost"} aria-pressed={email} onClick={() => setEmail(true)}>
          {c("automaticEmail")}
        </Button>
      </fieldset>
      <NotificationDeliveryTable key={String(email)} email={email} />
    </div>
  );
}
function NotificationDeliveryTable({ email }: { email: boolean }) {
  const c = useCommunicationCopy(),
    session = useAdminSession(),
    cache = useQueryClient();
  const [state, setState] = useState<DeliveryRow["state"] | "ALL">("FAILED"),
    [page, setPage] = useState(0),
    [selected, setSelected] = useState<DeliveryRow | null>(null),
    [reason, setReason] = useState("");
  const allowed = session.can(p.notificationsDelivery);
  const query = useQuery({
    queryKey: ["admin", "notification-delivery", email, state, page],
    queryFn: async () =>
      email
        ? communicationApi.notificationEmails(state === "ALL" ? undefined : (state as NotificationEmail["state"]), page)
        : communicationApi.events(state === "ALL" ? undefined : (state as NotificationEvent["state"]), page),
    enabled: allowed,
    refetchInterval: 15000,
    refetchIntervalInBackground: false,
  });
  const retry = useMutation({
    mutationFn: () => {
      if (!selected) throw new Error(c("error"));
      return email
        ? communicationApi.retryNotificationEmail(selected.id, selected.version, reason)
        : communicationApi.retryEvent(selected.id, selected.version, reason);
    },
    onSuccess: () => {
      setSelected(null);
      setReason("");
      void cache.invalidateQueries({ queryKey: ["admin", "notification-delivery"] });
    },
  });
  if (!allowed) return <PermissionState />;
  const table = columns.columns([
    columns.accessor("type", { header: c("event"), cell: ({ getValue }) => c(getValue()) }),
    columns.accessor("state", {
      header: c("status"),
      cell: ({ row }) => (
        <div>
          {c(row.original.state)}
          {row.original.failureCode && (
            <p className="mt-1 text-xs text-muted-foreground">{c(row.original.failureCode)}</p>
          )}
        </div>
      ),
    }),
    columns.accessor("attempts", { header: c("attempts") }),
    columns.accessor("nextAttemptAt", {
      header: c("nextAttempt"),
      cell: ({ row }) => (row.original.state === "PENDING" ? communicationDate(row.original.nextAttemptAt) : "—"),
    }),
    columns.display({
      id: "context",
      header: c("details"),
      cell: ({ row }) => (
        <details>
          <summary className="cursor-pointer text-primary">{c("details")}</summary>
          <dl className="mt-2 space-y-2 text-xs">
            <dt>{c("correlation")}</dt>
            <dd className="break-all font-mono">
              {"eventId" in row.original ? row.original.eventId : row.original.id}
            </dd>
            <dt>{c("createdAt")}</dt>
            <dd>{communicationDate(row.original.createdAt ?? null)}</dd>
            <dt>{c("updatedAt")}</dt>
            <dd>{communicationDate(row.original.updatedAt ?? null)}</dd>
          </dl>
          {row.original.sourcePath?.startsWith("/admin/") && (
            <Link className="mt-3 block text-sm text-primary underline" to={row.original.sourcePath}>
              {c("source")}
            </Link>
          )}
        </details>
      ),
    }),
    columns.display({
      id: "actions",
      header: c("actions"),
      cell: ({ row }) =>
        ("canRetry" in row.original ? row.original.canRetry : row.original.state === "FAILED") &&
        session.can(p.notificationsRetry) ? (
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              retry.reset();
              setReason("");
              setSelected(row.original);
            }}
          >
            {c("retryEvent")}
          </Button>
        ) : null,
    }),
  ]);
  return (
    <div className="space-y-5">
      <Select
        value={state}
        onValueChange={(v) => {
          setState(v as typeof state);
          setPage(0);
        }}
      >
        <SelectTrigger aria-label={c("status")} className="w-64">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {(email
            ? ["ALL", "FAILED", "SUPPRESSED", "PENDING", "SENDING", "SENT", "CANCELLED"]
            : ["ALL", "FAILED", "PENDING", "DELIVERED"]
          ).map((v) => (
            <SelectItem key={v} value={v}>
              {c(v === "ALL" ? "all" : v)}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {query.isError ? (
        <ErrorState retry={() => void query.refetch()} />
      ) : (
        <div className="overflow-hidden rounded-xl border bg-card">
          <DataTable
            columns={table}
            data={query.data?.content ?? []}
            getRowId={(r) => r.id}
            isLoading={query.isLoading}
          />
          {query.data && <PaginationBar {...query.data} page={page} onPageChange={setPage} />}
        </div>
      )}
      <Dialog
        open={!!selected}
        onOpenChange={(v) => {
          if (!v && !retry.isPending) setSelected(null);
        }}
      >
        <DialogContent closeLabel={c("dismiss")}>
          <DialogHeader>
            <DialogTitle>{c("retryEvent")}</DialogTitle>
            <DialogDescription>{c(email ? "retryAutomaticEmailHint" : "retryEventHint")}</DialogDescription>
          </DialogHeader>
          <Label htmlFor="retry-reason">{c("reason")}</Label>
          <Textarea id="retry-reason" maxLength={2000} value={reason} onChange={(e) => setReason(e.target.value)} />
          {retry.isError && (
            <p role="alert" className="text-sm text-destructive">
              {retry.error.message}
            </p>
          )}
          <DialogFooter>
            <Button variant="ghost" disabled={retry.isPending} onClick={() => setSelected(null)}>
              {c("dismiss")}
            </Button>
            <Button disabled={!reason.trim() || retry.isPending} onClick={() => retry.mutate()}>
              {c("confirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
