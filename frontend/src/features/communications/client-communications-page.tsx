import { ArrowLeftIcon, BellIcon, ChatCircleTextIcon, WarningIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useSearchParams } from "react-router";
import { type CommunicationKind, type CommunicationPreference, communicationApi } from "@/api/communication-api";
import { clientPermissions } from "@/auth/permissions";
import { useClientSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { communicationDate, useCommunicationCopy } from "./communication-copy";
import { CommunicationThread } from "./communication-thread";

const icons = { NOTICE: BellIcon, WARNING: WarningIcon, MESSAGE: ChatCircleTextIcon };
function Preferences() {
  const c = useCommunicationCopy(),
    session = useClientSession(),
    cache = useQueryClient();
  const key = ["client", "communications", session.account?.id, "preferences"];
  const query = useQuery({ queryKey: key, queryFn: communicationApi.preferences });
  const [next, setNext] = useState<CommunicationPreference | null>(null);
  const canChange = session.permissions?.isOwner && session.can(clientPermissions.communicationsPreferences);
  const save = useMutation({
    mutationFn: () => {
      const value = next ?? query.data;
      if (!value) throw new Error(c("error"));
      return communicationApi.updatePreferences(value);
    },
    onSuccess: () => {
      setNext(null);
      void cache.invalidateQueries({ queryKey: ["client", "communications"] });
    },
  });
  if (query.isError) return <ErrorState retry={() => void query.refetch()} />;
  if (query.isLoading) return <LoadingState rows={2} />;
  const value = next ?? query.data;
  return (
    <details className="rounded-xl border bg-card p-4">
      <summary className="cursor-pointer font-medium">{c("prefs")}</summary>
      <p className="my-3 text-sm text-muted-foreground">{c("prefHint")}</p>
      {value && (
        <div className="space-y-3">
          {(["marketingInApp", "marketingEmail"] as const).map((field) => (
            <label htmlFor={field} className="flex min-h-11 items-center gap-3 text-sm" key={field}>
              <Checkbox
                id={field}
                checked={value[field]}
                disabled={!canChange || save.isPending}
                onCheckedChange={(checked) => setNext({ ...value, [field]: checked === true })}
              />
              {c(field)}
            </label>
          ))}
          {canChange && (
            <Button variant="outline" onClick={() => save.mutate()} disabled={!next || save.isPending}>
              {c("savePreferences")}
            </Button>
          )}
        </div>
      )}
      <p className="mt-3 text-xs text-muted-foreground">{c("ownerOnly")}</p>
      {save.isError && (
        <p role="alert" className="text-destructive">
          {c("error")}
        </p>
      )}
      {save.isSuccess && !next && (
        <p role="status" className="text-sm text-success">
          {c("saved")}
        </p>
      )}
    </details>
  );
}
export function ClientCommunicationsPage() {
  const c = useCommunicationCopy(),
    session = useClientSession(),
    cache = useQueryClient();
  const [params, setParams] = useSearchParams();
  const selected = params.get("item");
  const rawKind = params.get("kind");
  const kind = (["NOTICE", "WARNING", "MESSAGE"].includes(rawKind ?? "") ? rawKind : null) as CommunicationKind | null;
  const page = Math.max(0, Number(params.get("page")) || 0),
    archived = params.get("archived") === "true",
    unread = params.get("unread") === "true";
  const allowed = session.can(clientPermissions.communicationsRead) && !session.isB2B;
  const change = (values: Record<string, string | null>) =>
    setParams((previous) => {
      const p = new URLSearchParams(previous);
      for (const [k, v] of Object.entries(values)) {
        if (v === null) p.delete(k);
        else p.set(k, v);
      }
      return p;
    });
  const query = useQuery({
    queryKey: ["client", "communications", session.account?.id, kind, archived, unread, page],
    queryFn: () => communicationApi.inbox(kind ?? undefined, archived, unread, page),
    enabled: allowed,
  });
  const detail = useQuery({
    queryKey: ["client", "communications", session.account?.id, "item", selected],
    queryFn: () => communicationApi.item(selected ?? ""),
    enabled: allowed && !!selected,
  });
  const act = useMutation({
    mutationFn: ({ action, restore = false }: { action: "read" | "acknowledge" | "archive"; restore?: boolean }) =>
      communicationApi.interact(selected ?? "", action, !restore),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: ["client", "communications"] });
    },
  });
  if (!allowed) return <PermissionState />;
  const item = detail.data;
  return (
    <div className="space-y-5">
      <PageHeader title={c("title")} />
      <nav aria-label={c("type")} className="flex flex-wrap gap-2 border-b pb-3">
        {(["ALL", "NOTICE", "WARNING", "MESSAGE"] as const).map((value) => {
          const Icon = value === "ALL" ? null : icons[value];
          return (
            <Button
              key={value}
              variant={(kind ?? "ALL") === value ? "secondary" : "ghost"}
              aria-pressed={(kind ?? "ALL") === value}
              onClick={() => change({ kind: value === "ALL" ? null : value, page: null, item: null })}
            >
              {Icon && <Icon />}
              {c(value === "ALL" ? "all" : value)}
            </Button>
          );
        })}
      </nav>
      <div className="flex flex-wrap gap-5 text-sm">
        {(["unread", "archives"] as const).map((key) => (
          <label htmlFor={`filter-${key}`} key={key} className="flex min-h-11 items-center gap-2">
            <Checkbox
              id={`filter-${key}`}
              checked={key === "unread" ? unread : archived}
              onCheckedChange={(value) =>
                change({ [key === "archives" ? "archived" : "unread"]: value === true ? "true" : null, page: null })
              }
            />
            {c(key)}
          </label>
        ))}
      </div>
      <div className={selected ? "grid gap-5 lg:grid-cols-[minmax(260px,1fr)_minmax(0,2fr)]" : ""}>
        <section
          className={`self-start overflow-hidden rounded-xl border bg-card ${selected ? "hidden lg:block" : ""}`}
        >
          {query.isLoading ? (
            <LoadingState />
          ) : query.isError ? (
            <ErrorState retry={() => void query.refetch()} />
          ) : !query.data?.content.length ? (
            <EmptyState title={c("empty")} />
          ) : (
            <ul className="divide-y">
              {query.data.content.map((row) => {
                const Icon = icons[row.kind];
                return (
                  <li key={row.id}>
                    <button
                      type="button"
                      className={`flex w-full items-start gap-4 p-5 text-start transition-colors hover:bg-muted/60 focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring ${selected === row.id ? "bg-muted/60" : ""}`}
                      onClick={() => change({ item: row.id })}
                    >
                      <Icon
                        className={`mt-1 size-5 shrink-0 ${row.kind === "WARNING" ? "text-warning" : "text-primary"}`}
                      />
                      <span className="min-w-0 flex-1">
                        <span className="flex flex-wrap items-baseline justify-between gap-2">
                          <span className={row.read ? "font-medium" : "font-semibold"}>
                            {row.source === "ADMIN" ? row.messageTitle : c(row.source)}
                          </span>
                          <span className="text-xs text-muted-foreground">{communicationDate(row.availableAt)}</span>
                        </span>
                        <span className="mt-1 block text-sm text-muted-foreground">
                          {c(row.kind)}
                          {row.read ? ` · ${c("readState")}` : ` · ${c("unread")}`}
                        </span>
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
          {query.data && <PaginationBar {...query.data} onPageChange={(p) => change({ page: String(p) })} />}
        </section>
        {selected && (
          <section className="min-w-0 rounded-xl border bg-card p-5 sm:p-6">
            <Button variant="ghost" size="sm" onClick={() => change({ item: null })}>
              <ArrowLeftIcon className="rtl:rotate-180" />
              {c("back")}
            </Button>
            {detail.isLoading ? (
              <LoadingState />
            ) : detail.isError ? (
              <ErrorState retry={() => void detail.refetch()} />
            ) : (
              item && (
                <div className="mt-4 space-y-5">
                  <div>
                    <p className="mb-2 text-xs font-medium text-muted-foreground">
                      {c(item.kind)} · {c(item.sourceState)}
                    </p>
                    <h2 dir="auto" className="text-xl font-semibold">
                      {item.source === "ADMIN" ? item.messageTitle : c(item.source)}
                    </h2>
                    <p className="mt-2 text-sm text-muted-foreground">
                      {c(
                        item.kind === "NOTICE" ? "noticeHint" : item.kind === "WARNING" ? "warningHint" : "messageHint",
                      )}
                    </p>
                  </div>
                  <p dir="auto" className="max-w-prose whitespace-pre-wrap break-words leading-relaxed">
                    {item.source === "ADMIN"
                      ? item.messageBody
                      : item.source === "PLAN_CONTENT"
                        ? `${item.messageTitle} · ${item.messageBody}`
                        : c("sourceHint")}
                  </p>
                  {item.acknowledged && <p className="border-s-2 border-warning ps-3 text-sm">{c("acknowledged")}</p>}
                  <div className="flex flex-wrap gap-2">
                    {!item.read && session.can(clientPermissions.communicationsMarkRead) && (
                      <Button variant="outline" disabled={act.isPending} onClick={() => act.mutate({ action: "read" })}>
                        {c("read")}
                      </Button>
                    )}
                    {item.canAcknowledge &&
                      !item.acknowledged &&
                      session.can(clientPermissions.communicationsAcknowledge) && (
                        <Button disabled={act.isPending} onClick={() => act.mutate({ action: "acknowledge" })}>
                          {c("seen")}
                        </Button>
                      )}
                    {item.canArchive && session.can(clientPermissions.communicationsArchive) && (
                      <Button
                        variant="ghost"
                        disabled={act.isPending}
                        onClick={() => act.mutate({ action: "archive", restore: item.archived })}
                      >
                        {c(item.archived ? "restore" : "archive")}
                      </Button>
                    )}
                    {item.actionPath && (
                      <Button asChild variant="outline">
                        <Link to={item.actionPath}>{c("source")}</Link>
                      </Button>
                    )}
                  </div>
                  {act.isError && (
                    <p role="alert" className="text-sm text-destructive">
                      {c("error")}
                    </p>
                  )}
                  {item.kind === "MESSAGE" && (
                    <>
                      <p className="text-sm text-muted-foreground">{item.closed ? c("closed") : ""}</p>
                      <CommunicationThread
                        key={item.id}
                        id={item.id}
                        canReply={item.canReply && session.can(clientPermissions.communicationsReply)}
                      />
                    </>
                  )}
                </div>
              )
            )}
          </section>
        )}
      </div>
      <Preferences />
    </div>
  );
}
