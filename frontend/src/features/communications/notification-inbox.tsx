import {
  ArrowLeftIcon,
  ArrowRightIcon,
  ArrowsClockwiseIcon,
  BellIcon,
  CheckCircleIcon,
  GearIcon,
  ListChecksIcon,
  TagIcon,
  WarningIcon,
} from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { communicationApi } from "@/api/communication-api";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { communicationDate, useCommunicationCopy } from "./communication-copy";
import { notificationAction, notificationTopics } from "./communication-rules";
import { InternalNotificationComposer } from "./internal-notification-composer";
import { InternalNotificationHistory } from "./internal-notification-history";
import { NotificationSettings } from "./notification-settings";

const icons = { NOTICE: BellIcon, WARNING: WarningIcon, ACTION: ListChecksIcon, OFFER: TagIcon };
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

export function NotificationInbox({ context, marketing }: { context: NotificationContext; marketing?: ReactNode }) {
  const c = useCommunicationCopy(),
    cache = useQueryClient();
  const [params, setParams] = useSearchParams();
  const [settings, setSettings] = useState(false),
    [compose, setCompose] = useState(false),
    [sent, setSent] = useState(false);
  const selected = params.get("item");
  const kind = kinds.find((k) => k === params.get("kind"));
  const topic = notificationTopics.find((t) => t === params.get("topic"));
  const page = Math.min(10000, Math.max(0, Math.floor(Number(params.get("page")) || 0)));
  const archived = params.get("archived") === "true",
    unread = params.get("unread") === "true";
  const key = [context.platform ? "admin" : "client", "notifications", context.identity];
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
    enabled: context.allowed && !sent,
    refetchInterval: 30_000,
    refetchIntervalInBackground: false,
    queryFn: () => communicationApi.inbox(kind, archived, unread, page, topic, context.platform, context.companyId),
  });
  const detail = useQuery({
    queryKey: [...key, "item", selected],
    enabled: context.allowed && !sent && !!selected,
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
  if (context.sent && (sent || !context.allowed))
    return (
      <div className="space-y-5">
        <PageHeader
          title={c("sent")}
          actions={
            context.allowed && (
              <Button variant="ghost" onClick={() => setSent(false)}>
                {c("inbox")}
              </Button>
            )
          }
        />
        <InternalNotificationHistory identity={context.identity} />
      </div>
    );
  if (!context.allowed) return <PermissionState />;
  const item = detail.isError ? undefined : detail.data,
    action = item ? notificationAction(item, context.platform) : null;
  return (
    <div className="space-y-5">
      <PageHeader
        title={c(context.platform ? "operatorInbox" : "notifications")}
        actions={
          <>
            {context.delivery && (
              <Button asChild variant="ghost">
                <Link to="/admin/notifications/delivery">{c("deliveryQueue")}</Link>
              </Button>
            )}
            <Button variant="ghost" aria-expanded={settings} onClick={() => setSettings((v) => !v)}>
              <GearIcon />
              {c("personalSettings")}
            </Button>
            {context.send && <Button onClick={() => setCompose(true)}>{c("notifyMembers")}</Button>}
            {context.sent && (
              <Button variant="ghost" onClick={() => setSent(true)}>
                {c("sent")}
              </Button>
            )}
          </>
        }
      />
      {settings && (
        <div className="grid gap-4 xl:grid-cols-2">
          <NotificationSettings context={context} />
          {marketing}
        </div>
      )}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b pb-3">
        <nav aria-label={c("type")} className="flex flex-wrap gap-1">
          {(["ALL", ...kinds] as const).map((value) => {
            const Icon = value === "ALL" ? BellIcon : icons[value];
            return (
              <Button
                key={value}
                variant={(kind ?? "ALL") === value ? "secondary" : "ghost"}
                aria-pressed={(kind ?? "ALL") === value}
                onClick={() => change({ kind: value === "ALL" ? null : value, page: null, item: null })}
              >
                <Icon />
                {c(value === "ALL" ? "all" : value)}
              </Button>
            );
          })}
        </nav>
        <Select
          value={topic ?? "ALL"}
          onValueChange={(v) => change({ topic: v === "ALL" ? null : v, page: null, item: null })}
        >
          <SelectTrigger aria-label={c("topic")} className="w-full sm:w-56">
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
      </div>
      <div className="flex flex-wrap gap-5 text-sm">
        {(["unread", "archives"] as const).map((filter) => (
          <label key={filter} htmlFor={`filter-${filter}`} className="flex min-h-11 items-center gap-2">
            <Checkbox
              id={`filter-${filter}`}
              checked={filter === "unread" ? unread : archived}
              onCheckedChange={(v) =>
                change({
                  [filter === "archives" ? "archived" : "unread"]: v === true ? "true" : null,
                  page: null,
                  item: null,
                })
              }
            />
            {c(filter)}
          </label>
        ))}
        <Button
          variant="ghost"
          className="ms-auto"
          disabled={query.isFetching}
          onClick={() => {
            void cache.invalidateQueries({ queryKey: [key[0], "notifications"] });
          }}
        >
          <ArrowsClockwiseIcon />
          {c("refresh")}
        </Button>
      </div>
      <div className={selected ? "grid gap-5 lg:grid-cols-[minmax(260px,1fr)_minmax(0,2fr)]" : ""}>
        <section
          aria-label={c("notifications")}
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
                const Icon = icons[row.kind] ?? BellIcon;
                return (
                  <li key={row.id}>
                    <button
                      type="button"
                      aria-current={selected === row.id ? "true" : undefined}
                      className={`flex min-h-20 w-full items-start gap-4 p-5 text-start transition-colors hover:bg-muted/60 focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring ${selected === row.id ? "bg-muted/60" : ""}`}
                      onClick={() => change({ item: row.id })}
                    >
                      <Icon
                        className={`mt-1 size-5 shrink-0 ${row.kind === "WARNING" ? "text-warning" : "text-primary"}`}
                      />
                      <span className="min-w-0 flex-1">
                        <span className="flex flex-wrap items-baseline justify-between gap-2">
                          <span dir="auto" className={row.read ? "font-medium" : "font-semibold"}>
                            {["PLAN_CONTENT", "REPRICING"].includes(row.source) ? c(row.source) : row.messageTitle}
                          </span>
                          <span className="text-xs text-muted-foreground">{communicationDate(row.availableAt)}</span>
                        </span>
                        <span className="mt-1 flex flex-wrap gap-x-2 text-sm text-muted-foreground">
                          <span>{c(row.topic)}</span>
                          <span>· {c(row.kind)}</span>
                          {!row.read && <span className="font-medium text-primary">· {c("unread")}</span>}
                          {row.priority === "HIGH" && !row.resolved && (
                            <span className="font-semibold text-warning">· {c("highPriority")}</span>
                          )}
                          {row.resolved && <span>· {c("resolved")}</span>}
                        </span>
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
          {query.data && (
            <PaginationBar {...query.data} onPageChange={(p) => change({ page: String(p), item: null })} />
          )}
        </section>
        {selected && (
          <section className="min-w-0 self-start rounded-xl border bg-card p-5 sm:p-6">
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
      {compose && context.send && <InternalNotificationComposer onClose={() => setCompose(false)} />}
    </div>
  );
}
