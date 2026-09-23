import { ArrowLeftIcon, ArrowRightIcon, ArrowsClockwiseIcon, BellIcon, CheckCircleIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useSearchParams } from "react-router";
import { communicationApi } from "@/api/communication-api";
import { PageHeader } from "@/components/patterns/page-header";
import { EmptyState, ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { communicationDate, useCommunicationCopy } from "./communication-copy";
import { notificationAction, notificationTopics } from "./communication-rules";
import { NotificationFeed } from "./notification-feed";

const kinds = ["NOTICE", "WARNING", "ACTION", "OFFER"] as const;
export type NotificationContext = {
  platform: boolean;
  companyId?: string | null;
  identity: string;
  allowed: boolean;
  read: boolean;
  acknowledge: boolean;
  archive: boolean;
  preferences: boolean;
  send: boolean;
  sent?: boolean;
  delivery: boolean;
};

export function NotificationInbox({ context, panel = false }: { context: NotificationContext; panel?: boolean }) {
  const { i18n } = useTranslation();
  const c = useCommunicationCopy(),
    cache = useQueryClient();
  const [routeParams, setRouteParams] = useSearchParams();
  const [panelParams, setPanelParams] = useState(new URLSearchParams());
  const [filters, setFilters] = useState(false);
  const params = panel ? panelParams : routeParams;
  const setParams = (update: (previous: URLSearchParams) => URLSearchParams) => {
    if (panel) setPanelParams(update);
    else setRouteParams(update);
  };
  const selected = params.get("item");
  const kind = kinds.find((k) => k === params.get("kind"));
  const topic = notificationTopics.find((t) => t === params.get("topic"));
  const page = Math.min(10000, Math.max(0, Math.floor(Number(params.get("page")) || 0)));
  const archived = params.get("archived") === "true",
    unread = params.get("unread") === "true";
  const key = [context.platform ? "admin" : "client", "notifications", context.identity, i18n.language];
  const change = (values: Record<string, string | null>) =>
    setParams((previous) => {
      const next = new URLSearchParams(previous);
      for (const [k, v] of Object.entries(values)) {
        if (v === null) next.delete(k);
        else next.set(k, v);
      }
      return next;
    });
  const query = useQuery({
    queryKey: [...key, kind, topic, archived, unread, page],
    enabled: context.allowed,
    refetchInterval: 30_000,
    refetchIntervalInBackground: false,
    queryFn: () => communicationApi.inbox(kind, archived, unread, page, topic, context.platform, context.companyId),
  });
  const detail = useQuery({
    queryKey: [...key, "item", selected],
    enabled: context.allowed && !!selected,
    refetchInterval: 30_000,
    refetchIntervalInBackground: false,
    queryFn: () => communicationApi.item(selected ?? "", context.platform, context.companyId),
  });
  const act = useMutation({
    mutationFn: ({ action, restore = false }: { action: "read" | "acknowledge" | "archive"; restore?: boolean }) =>
      communicationApi.interact(selected ?? "", action, !restore, context.platform, context.companyId),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: [key[0], "notifications"] });
    },
  });
  if (!context.allowed) return <PermissionState />;
  const item = detail.isError ? undefined : detail.data,
    action = item ? notificationAction(item, context.platform) : null;
  return (
    <div className={panel ? "space-y-5" : "mx-auto max-w-6xl space-y-5"}>
      {!panel && <PageHeader title={c(context.platform ? "operatorInbox" : "notifications")} />}
      {(!panel || !selected) && (
        <div className="flex flex-wrap items-center gap-2 border-b pb-3">
          <nav aria-label={c("notifications")} className="flex gap-1">
            <Button
              variant={!unread ? "secondary" : "ghost"}
              aria-pressed={!unread}
              onClick={() => change({ unread: null, page: null, item: null })}
            >
              {c("all")}
            </Button>
            <Button
              variant={unread ? "secondary" : "ghost"}
              aria-pressed={unread}
              onClick={() => change({ unread: "true", page: null, item: null })}
            >
              {c("unread")}
            </Button>
          </nav>
          {!panel && (
            <Button variant="ghost" aria-expanded={filters} onClick={() => setFilters((v) => !v)}>
              {c("filters")}
              {kind || topic || archived ? " ·" : ""}
            </Button>
          )}
          <Button
            variant="ghost"
            size="icon"
            className="ms-auto"
            aria-label={c("refresh")}
            disabled={query.isFetching}
            onClick={() => void cache.invalidateQueries({ queryKey: [key[0], "notifications"] })}
          >
            <ArrowsClockwiseIcon />
          </Button>
        </div>
      )}
      {!panel && filters && (
        <div className="flex flex-wrap gap-4 rounded-lg border bg-muted/30 p-4">
          <Select
            value={kind ?? "ALL"}
            onValueChange={(v) => change({ kind: v === "ALL" ? null : v, page: null, item: null })}
          >
            <SelectTrigger aria-label={c("type")} className="w-full sm:w-48">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">{c("all")}</SelectItem>
              {kinds.map((k) => (
                <SelectItem key={k} value={k}>
                  {c(k)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select
            value={topic ?? "ALL"}
            onValueChange={(v) => change({ topic: v === "ALL" ? null : v, page: null, item: null })}
          >
            <SelectTrigger aria-label={c("topic")} className="w-full sm:w-48">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">
                {c("topic")} · {c("all")}
              </SelectItem>
              {notificationTopics.map((t) => (
                <SelectItem key={t} value={t}>
                  {c(t)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <label htmlFor="notification-archives" className="flex min-h-11 items-center gap-2 text-sm">
            <Checkbox
              id="notification-archives"
              checked={archived}
              onCheckedChange={(v) => change({ archived: v === true ? "true" : null, page: null, item: null })}
            />
            {c("archives")}
          </label>
        </div>
      )}
      <div className={selected && !panel ? "grid gap-5 lg:grid-cols-[minmax(260px,1fr)_minmax(0,2fr)]" : ""}>
        <section
          aria-label={c("notifications")}
          className={`self-start overflow-hidden ${panel ? "" : "rounded-xl border bg-card"} ${selected ? (panel ? "hidden" : "hidden lg:block") : ""}`}
        >
          {query.isLoading ? (
            <LoadingState />
          ) : query.isError ? (
            <ErrorState retry={() => void query.refetch()} />
          ) : !query.data?.content.length ? (
            <EmptyState
              title={c(unread ? "noUnreadNotifications" : "noNotifications")}
              icon={<BellIcon className="size-8" />}
            />
          ) : (
            <NotificationFeed items={query.data.content} selected={selected} onSelect={(id) => change({ item: id })} />
          )}
          {query.data && query.data.totalPages > 1 && (
            <div className="flex justify-between gap-2 border-t p-3">
              <Button
                variant="ghost"
                size="sm"
                disabled={page === 0}
                onClick={() => change({ page: String(page - 1), item: null })}
              >
                {c("newer")}
              </Button>
              <Button
                variant="ghost"
                size="sm"
                disabled={page + 1 >= query.data.totalPages}
                onClick={() => change({ page: String(page + 1), item: null })}
              >
                {c("older")}
              </Button>
            </div>
          )}
        </section>
        {selected && (
          <section className={panel ? "min-w-0" : "min-w-0 self-start rounded-xl border bg-card p-5 sm:p-6"}>
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
                <article className="mt-5 space-y-5">
                  <header>
                    <div className="mb-2 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
                      <span>
                        {c(item.kind)} · {c(item.topic)}
                      </span>
                      {["MEMBER", "OPERATOR"].includes(item.audience) && <span>· {c("personal")}</span>}
                      {item.priority === "HIGH" && !item.resolved && (
                        <span className="font-semibold text-warning">· {c("highPriority")}</span>
                      )}
                    </div>
                    <h2 dir="auto" className="text-xl font-semibold">
                      {["PLAN_CONTENT", "REPRICING"].includes(item.source) ? c(item.source) : item.messageTitle}
                    </h2>
                    <p className="mt-2 text-xs text-muted-foreground">{communicationDate(item.availableAt)}</p>
                    {item.senderName && (
                      <p className="mt-2 text-sm text-muted-foreground">
                        {c("sender")} : <span dir="auto">{item.senderName}</span>
                      </p>
                    )}
                  </header>
                  <p dir="auto" className="max-w-prose whitespace-pre-wrap break-words leading-relaxed">
                    {item.source === "REPRICING"
                      ? c("sourceHint")
                      : item.source === "PLAN_CONTENT"
                        ? `${item.messageTitle} · ${item.messageBody}`
                        : item.messageBody}
                  </p>
                  {item.resolved ? (
                    <p className="flex items-center gap-2 text-sm text-success">
                      <CheckCircleIcon />
                      {c(
                        item.sourceState === "UNAVAILABLE"
                          ? "UNAVAILABLE"
                          : ["CANCELLED", "WITHDRAWN"].includes(item.sourceState)
                            ? "WITHDRAWN"
                            : "resolved",
                      )}
                    </p>
                  ) : (
                    item.kind !== "NOTICE" && (
                      <p className="border-s-2 border-border ps-3 text-sm text-muted-foreground">
                        {c(
                          item.kind === "WARNING" ? "warningHint" : item.kind === "ACTION" ? "actionHint" : "offerHint",
                        )}
                      </p>
                    )
                  )}
                  {item.acknowledged && !item.resolved && <p className="text-sm text-warning">{c("acknowledged")}</p>}
                  <div className="flex flex-wrap gap-2 border-t pt-4">
                    {action && (
                      <Button asChild>
                        <Link to={action.path}>
                          {c(action.label)}
                          <ArrowRightIcon className="rtl:rotate-180" />
                        </Link>
                      </Button>
                    )}
                    {item.canAcknowledge && !item.acknowledged && context.acknowledge && (
                      <Button
                        variant="outline"
                        disabled={act.isPending}
                        onClick={() => act.mutate({ action: "acknowledge" })}
                      >
                        {c("seen")}
                      </Button>
                    )}
                    {!item.read && context.read && (
                      <Button variant="outline" disabled={act.isPending} onClick={() => act.mutate({ action: "read" })}>
                        {c("read")}
                      </Button>
                    )}
                    {item.canArchive && context.archive && (
                      <Button
                        variant="ghost"
                        disabled={act.isPending}
                        onClick={() => act.mutate({ action: "archive", restore: item.archived })}
                      >
                        {c(item.archived ? "restore" : "archive")}
                      </Button>
                    )}
                  </div>
                  {act.isError && (
                    <p role="alert" className="text-sm text-destructive">
                      {act.error.message || c("error")}
                    </p>
                  )}
                </article>
              )
            )}
          </section>
        )}
      </div>
    </div>
  );
}
