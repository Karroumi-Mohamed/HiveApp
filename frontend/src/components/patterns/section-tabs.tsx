import { NavLink } from "react-router";
import { cn } from "@/lib/utils";

export type SectionTab = { label: string; to: string; end?: boolean; count?: number };
export type LocalSectionTab = { label: string; value: string; count?: number };

export function SectionTabs(
  props:
    | { tabs: SectionTab[]; ariaLabel: string }
    | { items: LocalSectionTab[]; value: string; onValueChange: (value: string) => void; ariaLabel?: string },
) {
  if ("items" in props) {
    return (
      <div
        aria-label={props.ariaLabel ?? "Sections"}
        className="scrollbar-hidden overflow-x-auto border-b"
        role="tablist"
      >
        <div className="flex min-w-max gap-6">
          {props.items.map((item) => (
            <button
              aria-selected={props.value === item.value}
              className={cn(
                "flex min-h-11 items-center gap-2 border-b-2 border-transparent text-sm font-medium text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                props.value === item.value && "border-primary text-foreground",
              )}
              key={item.value}
              onClick={() => props.onValueChange(item.value)}
              role="tab"
              type="button"
            >
              {item.label}
              {item.count !== undefined ? (
                <span className="rounded-full bg-muted px-1.5 py-0.5 text-[11px] tabular-nums">{item.count}</span>
              ) : null}
            </button>
          ))}
        </div>
      </div>
    );
  }
  const { tabs, ariaLabel } = props;
  return (
    <nav aria-label={ariaLabel} className="scrollbar-hidden overflow-x-auto border-b">
      <div className="flex min-w-max gap-6">
        {tabs.map((tab) => (
          <NavLink
            className={({ isActive }) =>
              cn(
                "relative flex min-h-11 items-center gap-2 border-b-2 border-transparent text-sm font-medium text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                isActive && "border-primary text-foreground",
              )
            }
            end={tab.end}
            key={tab.to}
            to={tab.to}
          >
            {tab.label}
            {tab.count !== undefined ? (
              <span className="rounded-full bg-muted px-1.5 py-0.5 text-[11px] tabular-nums">{tab.count}</span>
            ) : null}
          </NavLink>
        ))}
      </div>
    </nav>
  );
}
