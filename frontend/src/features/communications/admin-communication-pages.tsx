import { PlusIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams, useSearchParams } from "react-router";
import { type CommunicationPublication, type CommunicationRecipient, communicationApi } from "@/api/communication-api";
import { adminPermissions as p } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable } from "@/components/patterns/data-table";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
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
import { Textarea } from "@/components/ui/textarea";
import { AdminCommunicationsPage } from "@/features/admin/operations/admin-communications-page";
import { communicationDate, useCommunicationCopy } from "./communication-copy";
import { CommunicationThread } from "./communication-thread";

const publicationColumns = createDataColumns<CommunicationPublication>();
const recipientColumns = createDataColumns<CommunicationRecipient>();

export function CommunicationAdminHub() {
  const c = useCommunicationCopy(),
    session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const canClients = session.can(p.customerCommunicationsRead),
    canSecurity = session.can(p.communicationsRead);
  const security = canSecurity && (!canClients || params.get("channel") === "security");
  return (
    <div className="space-y-5">
      <nav aria-label={c("title")} className="flex flex-wrap gap-2 border-b pb-3">
        {canClients && (
          <Button variant={!security ? "secondary" : "ghost"} aria-pressed={!security} onClick={() => setParams({})}>
            {c("clients")}
          </Button>
        )}
        {canSecurity && (
          <Button
            variant={security ? "secondary" : "ghost"}
            aria-pressed={security}
            onClick={() => setParams({ channel: "security" })}
          >
            {c("security")}
          </Button>
        )}
      </nav>
      {security ? <AdminCommunicationsPage /> : canClients ? <PublicationList /> : <PermissionState />}
    </div>
  );
}

function PublicationList() {
  const c = useCommunicationCopy(),
    session = useAdminSession();
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: ["admin", "communications", "publications", page],
    queryFn: () => communicationApi.list(page),
  });
  const columns = publicationColumns.columns([
    publicationColumns.accessor("messageTitle", {
      header: c("subject"),
      cell: ({ row }) => (
        <Link
          className="font-medium text-primary underline-offset-4 hover:underline"
          to={`/admin/communications/${row.original.id}`}
        >
          {row.original.messageTitle}
        </Link>
      ),
    }),
    publicationColumns.accessor("kind", { header: c("type"), cell: ({ getValue }) => c(getValue()) }),
    publicationColumns.accessor("purpose", { header: c("purpose"), cell: ({ getValue }) => c(getValue()) }),
    publicationColumns.accessor("state", {
      header: c("status"),
      cell: ({ row }) =>
        c(
          row.original.state === "PUBLISHED" && new Date(row.original.availableAt).getTime() > Date.now()
            ? "SCHEDULED"
            : row.original.state,
        ),
    }),
    publicationColumns.accessor("accountIds", {
      header: c("recipients"),
      cell: ({ getValue }) => <span className="tabular-nums">{getValue().length}</span>,
    }),
    publicationColumns.accessor("availableAt", {
      header: c("availableShort"),
      cell: ({ getValue }) => communicationDate(getValue()),
    }),
  ]);
  return (
    <div className="space-y-5">
      <PageHeader
        title={c("title")}
        actions={
          session.can(p.customerCommunicationsCreate) && session.can(p.customerCommunicationsChoose) ? (
            <Button asChild>
              <Link to="/admin/communications/new">
                <PlusIcon />
                {c("create")}
              </Link>
            </Button>
          ) : undefined
        }
      />
      {query.isError ? (
        <ErrorState retry={() => void query.refetch()} />
      ) : (
        <div className="overflow-hidden rounded-xl border bg-card">
          <DataTable
            columns={columns}
            data={query.data?.content ?? []}
            getRowId={(r) => r.id}
            isLoading={query.isLoading}
            emptyState={<EmptyState title={c("empty")} />}
          />
          {query.data && <PaginationBar {...query.data} page={page} onPageChange={setPage} />}
        </div>
      )}
    </div>
  );
}

export function CommunicationAdminDetail() {
  const { communicationId = "" } = useParams();
  const c = useCommunicationCopy(),
    session = useAdminSession(),
    cache = useQueryClient();
  const [action, setAction] = useState<"publish" | "cancel" | null>(null),
    [reason, setReason] = useState("");
  const query = useQuery({
    queryKey: ["admin", "communications", communicationId],
    queryFn: () => communicationApi.detail(communicationId),
    enabled: session.can(p.customerCommunicationsRead),
  });
  const command = useMutation({
    mutationFn: () => {
      if (!action || !query.data) throw new Error(c("error"));
      return communicationApi[action](communicationId, query.data.version, reason.trim());
    },
    onSuccess: () => {
      setAction(null);
      setReason("");
      void cache.invalidateQueries({ queryKey: ["admin", "communications"] });
    },
  });
  if (!session.can(p.customerCommunicationsRead)) return <PermissionState />;
  if (query.isLoading) return <LoadingState />;
  if (query.isError || !query.data) return <ErrorState retry={() => void query.refetch()} />;
  const item = query.data;
  const canPublish =
    session.can(p.customerCommunicationsPublish) &&
    (item.purpose !== "MARKETING" || session.can(p.customerCommunicationsMarketing));
  return (
    <div className="space-y-6">
      <Link to="/admin/communications" className="text-sm text-muted-foreground hover:text-foreground">
        ← {c("title")}
      </Link>
      <PageHeader
        title={item.messageTitle}
        description={`${c(item.kind)} · ${c(item.purpose)} · ${c(item.state === "PUBLISHED" && new Date(item.availableAt).getTime() > Date.now() ? "SCHEDULED" : item.state)}`}
        actions={
          <>
            {item.state === "DRAFT" &&
              session.can(p.customerCommunicationsEdit) &&
              session.can(p.customerCommunicationsChoose) && (
                <Button variant="outline" asChild>
                  <Link to={`/admin/communications/${item.id}/edit`}>{c("edit")}</Link>
                </Button>
              )}
            {item.state === "DRAFT" && canPublish && (
              <Button
                onClick={() => {
                  command.reset();
                  setAction("publish");
                }}
              >
                {c("publish")}
              </Button>
            )}
            {item.state !== "CANCELLED" && session.can(p.customerCommunicationsCancel) && (
              <Button
                variant="ghost"
                onClick={() => {
                  command.reset();
                  setAction("cancel");
                }}
              >
                {c("cancel")}
              </Button>
            )}
          </>
        }
      />
      <div className="grid gap-5 lg:grid-cols-[minmax(0,2fr)_minmax(250px,1fr)]">
        <article className="rounded-xl border bg-card p-6">
          <p className="max-w-prose whitespace-pre-wrap break-words leading-relaxed">{item.messageBody}</p>
        </article>
        <aside className="rounded-xl border bg-card p-5">
          <dl className="space-y-4 text-sm">
            {[
              [c("recipients"), item.accountIds.length],
              [c("availableShort"), communicationDate(item.availableAt)],
              [c("expiry"), communicationDate(item.expiresAt)],
              [c("delivery"), c(item.email ? "inAppEmail" : "NOT_REQUESTED")],
              [c("replies"), c(item.replies ? "enabled" : "disabled")],
            ].map(([label, value]) => (
              <div key={label}>
                <dt className="text-muted-foreground">{label}</dt>
                <dd className="mt-1 font-medium">{value}</dd>
              </div>
            ))}
          </dl>
          {session.can(p.customerCommunicationsChoose) && <SelectedRecipients id={item.id} />}
        </aside>
      </div>
      {item.purpose === "MARKETING" && <p className="text-sm text-muted-foreground">{c("marketingHint")}</p>}
      {item.state !== "DRAFT" && session.can(p.customerCommunicationsResults) && (
        <PublicationResults key={item.id} publication={item} />
      )}
      <Dialog
        open={action !== null}
        onOpenChange={(open) => {
          if (!open && !command.isPending) {
            setAction(null);
            setReason("");
          }
        }}
      >
        <DialogContent closeLabel={c("dismiss")}>
          <DialogHeader>
            <DialogTitle>{c(action ?? "publish")}</DialogTitle>
            <DialogDescription>{c(action === "cancel" ? "cancelHint" : "immutable")}</DialogDescription>
          </DialogHeader>
          <Label htmlFor="communication-reason">{c("reason")}</Label>
          <Textarea
            id="communication-reason"
            value={reason}
            maxLength={2000}
            onChange={(e) => setReason(e.target.value)}
          />
          {command.isError && (
            <p role="alert" className="text-sm text-destructive">
              {command.error.message || c("error")}
            </p>
          )}
          <DialogFooter>
            <Button variant="outline" disabled={command.isPending} onClick={() => setAction(null)}>
              {c("dismiss")}
            </Button>
            <Button disabled={!reason.trim() || command.isPending} onClick={() => command.mutate()}>
              {c("confirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function SelectedRecipients({ id }: { id: string }) {
  const c = useCommunicationCopy();
  const [open, setOpen] = useState(false);
  const query = useQuery({
    queryKey: ["admin", "communications", id, "selected-recipients"],
    queryFn: () => communicationApi.selectedRecipients(id),
    enabled: open,
  });
  return (
    <details className="mt-5 border-t pt-4" onToggle={(e) => setOpen(e.currentTarget.open)}>
      <summary className="cursor-pointer text-sm font-medium text-primary">{c("recipients")}</summary>
      {query.isLoading ? (
        <LoadingState rows={2} />
      ) : query.isError ? (
        <ErrorState retry={() => void query.refetch()} />
      ) : (
        <ul className="mt-3 max-h-60 space-y-2 overflow-y-auto text-sm">
          {query.data?.map((a) => (
            <li key={a.id}>{a.name}</li>
          ))}
        </ul>
      )}
    </details>
  );
}

function PublicationResults({ publication }: { publication: CommunicationPublication }) {
  const currentlyAvailable =
    publication.state === "PUBLISHED" &&
    new Date(publication.availableAt).getTime() <= Date.now() &&
    (!publication.expiresAt || new Date(publication.expiresAt).getTime() > Date.now());
  const c = useCommunicationCopy(),
    session = useAdminSession(),
    cache = useQueryClient();
  const [page, setPage] = useState(0),
    [selected, setSelected] = useState<CommunicationRecipient | null>(null);
  const query = useQuery({
    queryKey: ["admin", "communications", publication.id, "results", page],
    queryFn: () => communicationApi.results(publication.id, page),
  });
  const retry = useMutation({
    mutationFn: communicationApi.retry,
    onSuccess: () => void cache.invalidateQueries({ queryKey: ["admin", "communications"] }),
  });
  const close = useMutation({
    mutationFn: ({ id, closed }: { id: string; closed: boolean }) => communicationApi.close(id, closed),
    onSuccess: (_, v) => {
      setSelected((s) => (s?.id === v.id ? { ...s, closed: v.closed } : s));
      void cache.invalidateQueries({ queryKey: ["admin", "communications"] });
    },
  });
  const canRetry =
    session.can(p.customerCommunicationsRetry) &&
    (publication.purpose !== "MARKETING" || session.can(p.customerCommunicationsMarketing));
  const columns = recipientColumns.columns([
    recipientColumns.accessor("accountName", {
      header: c("account"),
      cell: ({ getValue }) => <span className="font-medium">{getValue()}</span>,
    }),
    recipientColumns.accessor("visible", {
      header: c("inbox"),
      cell: ({ getValue }) => c(getValue() ? "visible" : "notVisible"),
    }),
    recipientColumns.accessor("emailDelivery", { header: c("delivery"), cell: ({ getValue }) => c(getValue()) }),
    recipientColumns.accessor("readers", { header: c("readers") }),
    ...(publication.kind === "WARNING"
      ? [recipientColumns.accessor("acknowledgements", { header: c("acknowledgements") })]
      : []),
    ...(publication.kind === "MESSAGE" ? [recipientColumns.accessor("replies", { header: c("replies") })] : []),
    recipientColumns.display({
      id: "actions",
      header: c("actions"),
      cell: ({ row }) => (
        <div className="flex flex-wrap gap-2">
          {publication.kind === "MESSAGE" && session.can(p.customerCommunicationsReadReplies) && (
            <Button size="sm" variant="outline" onClick={() => setSelected(row.original)}>
              {c("open")}
            </Button>
          )}
          {currentlyAvailable && canRetry && ["FAILED", "SUPPRESSED"].includes(row.original.emailDelivery) && (
            <Button size="sm" variant="ghost" disabled={retry.isPending} onClick={() => retry.mutate(row.original.id)}>
              {c("retry")}
            </Button>
          )}
        </div>
      ),
    }),
  ]);
  return (
    <section className="space-y-4">
      <h2 className="text-lg font-semibold">{c("results")}</h2>
      {query.isError ? (
        <ErrorState retry={() => void query.refetch()} />
      ) : (
        <div className="overflow-hidden rounded-xl border bg-card">
          <DataTable
            columns={columns}
            data={query.data?.content ?? []}
            getRowId={(r) => r.id}
            isLoading={query.isLoading}
          />
          {query.data && <PaginationBar {...query.data} page={page} onPageChange={setPage} />}
        </div>
      )}
      {retry.isError && (
        <p role="alert" className="text-sm text-destructive">
          {retry.error.message}
        </p>
      )}
      <Dialog
        open={!!selected}
        onOpenChange={(open) => {
          if (!open) setSelected(null);
        }}
      >
        <DialogContent closeLabel={c("dismiss")} className="max-h-[85dvh] overflow-y-auto sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>{selected?.accountName}</DialogTitle>
            <DialogDescription>{c("messageHint")}</DialogDescription>
          </DialogHeader>
          {selected && (
            <>
              <div className="flex items-center justify-between gap-3">
                <p className="text-sm text-muted-foreground">{c(selected.closed ? "closed" : "privateThread")}</p>
                {publication.replies && currentlyAvailable && session.can(p.customerCommunicationsClose) && (
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={close.isPending}
                    onClick={() => close.mutate({ id: selected.id, closed: !selected.closed })}
                  >
                    {c(selected.closed ? "reopen" : "closeThread")}
                  </Button>
                )}
              </div>
              {close.isError && (
                <p role="alert" className="text-sm text-destructive">
                  {close.error.message}
                </p>
              )}
              <CommunicationThread
                key={selected.id}
                id={selected.id}
                admin
                canReply={
                  publication.replies &&
                  !selected.closed &&
                  currentlyAvailable &&
                  selected.visible &&
                  session.can(p.customerCommunicationsReply)
                }
              />
            </>
          )}
        </DialogContent>
      </Dialog>
    </section>
  );
}
