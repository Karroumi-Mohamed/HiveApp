import { BellIcon, ListChecksIcon, TagIcon, WarningIcon } from "@phosphor-icons/react";
import type { CommunicationItem } from "@/api/communication-api";
import { communicationDate, useCommunicationCopy } from "./communication-copy";

const icons = { NOTICE: BellIcon, WARNING: WarningIcon, ACTION: ListChecksIcon, OFFER: TagIcon };
export function NotificationFeed({
  items,
  selected,
  onSelect,
}: {
  items: CommunicationItem[];
  selected?: string | null;
  onSelect: (id: string) => void;
}) {
  const c = useCommunicationCopy();
  const today = new Date().toDateString();
  const groups = [
    { key: "today", items: items.filter((n) => new Date(n.availableAt).toDateString() === today) },
    { key: "earlier", items: items.filter((n) => new Date(n.availableAt).toDateString() !== today) },
  ];
  return (
    <div>
      {groups
        .filter((g) => g.items.length)
        .map((group) => (
          <section key={group.key}>
            <h2 className="px-4 pb-2 pt-5 text-xs font-semibold text-muted-foreground">{c(group.key)}</h2>
            <ul>
              {group.items.map((item) => {
                const Icon = icons[item.kind] ?? BellIcon;
                return (
                  <li key={item.id}>
                    <button
                      type="button"
                      aria-current={selected === item.id ? "true" : undefined}
                      className={`flex w-full items-start gap-3 border-s-2 px-4 py-4 text-start transition-colors hover:bg-muted/60 focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring ${selected === item.id ? "bg-muted/60" : ""} ${item.read ? "border-transparent" : "border-primary bg-primary/[0.035]"}`}
                      onClick={() => onSelect(item.id)}
                    >
                      <Icon
                        aria-hidden="true"
                        className={`mt-0.5 size-5 shrink-0 ${item.kind === "WARNING" && !item.resolved ? "text-warning" : "text-muted-foreground"}`}
                      />
                      <span className="min-w-0 flex-1">
                        <span
                          dir="auto"
                          className={`block break-words text-sm ${item.read ? "font-medium" : "font-semibold"}`}
                        >
                          {["PLAN_CONTENT", "REPRICING"].includes(item.source) ? c(item.source) : item.messageTitle}
                        </span>
                        <span
                          dir="auto"
                          className="mt-1 line-clamp-2 block break-words text-sm leading-relaxed text-muted-foreground"
                        >
                          {item.messageBody}
                        </span>
                        <span className="mt-2 flex flex-wrap gap-x-2 gap-y-1 text-xs text-muted-foreground">
                          <time dateTime={item.availableAt}>{communicationDate(item.availableAt)}</time>
                          {!item.read && <span className="sr-only">{c("unread")}</span>}
                          {item.priority === "HIGH" && !item.resolved && (
                            <span className="font-medium text-warning">{c("highPriority")}</span>
                          )}
                          {item.resolved && (
                            <span>{c(item.sourceState === "UNAVAILABLE" ? "UNAVAILABLE" : "resolved")}</span>
                          )}
                        </span>
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          </section>
        ))}
    </div>
  );
}
