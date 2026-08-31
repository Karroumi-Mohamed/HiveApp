import { ArrowUpRightIcon, type Icon } from "@phosphor-icons/react";
import { Link } from "react-router";
import { cn } from "@/lib/utils";

export type OperationalBreakdown = { label: string; value: number | string; tone?: "default" | "warning" | "danger" };

export function OperationalCard({
  title,
  value,
  label,
  icon: IconComponent,
  breakdown,
  to,
  tone = "default",
}: {
  title: string;
  value: number | string;
  label: string;
  icon: Icon;
  breakdown: OperationalBreakdown[];
  to?: string;
  tone?: "default" | "warning" | "danger";
}) {
  const body = (
    <div className="group h-full rounded-xl border bg-card p-5 transition-colors hover:border-foreground/20">
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-2.5">
          <IconComponent
            aria-hidden="true"
            className={cn(
              "size-5 text-muted-foreground",
              tone === "warning" && "text-warning",
              tone === "danger" && "text-destructive",
            )}
          />
          <h2 className="text-sm font-semibold">{title}</h2>
        </div>
        {to ? (
          <ArrowUpRightIcon
            aria-hidden="true"
            className="size-4 text-muted-foreground transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5 rtl:group-hover:-translate-x-0.5"
          />
        ) : null}
      </div>
      <div className="mt-5 flex items-end gap-2">
        <span className="text-3xl font-semibold tracking-[-0.04em] tabular-nums">{value}</span>
        <span className="pb-1 text-sm text-muted-foreground">{label}</span>
      </div>
      <dl className="mt-5 grid grid-cols-2 gap-x-5 gap-y-2 border-t pt-4 text-xs">
        {breakdown.map((item) => (
          <div className="flex min-w-0 items-center justify-between gap-2" key={item.label}>
            <dt className="truncate text-muted-foreground">{item.label}</dt>
            <dd
              className={cn(
                "font-semibold tabular-nums",
                item.tone === "warning" && "text-warning",
                item.tone === "danger" && "text-destructive",
              )}
            >
              {item.value}
            </dd>
          </div>
        ))}
      </dl>
    </div>
  );
  return to ? (
    <Link
      className="block h-full rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      to={to}
    >
      {body}
    </Link>
  ) : (
    body
  );
}
