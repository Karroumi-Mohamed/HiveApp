import type { ComponentProps } from "react";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

export type StatusTone = "neutral" | "success" | "warning" | "danger" | "info";

const toneClasses: Record<StatusTone, string> = {
  neutral: "border-border bg-muted text-muted-foreground",
  success: "border-success/20 bg-success-subtle text-success",
  warning: "border-warning/25 bg-warning-subtle text-warning",
  danger: "border-destructive/20 bg-destructive/10 text-destructive",
  info: "border-info/20 bg-info-subtle text-info",
};

type StatusBadgeProps = ComponentProps<typeof Badge> & {
  tone?: StatusTone;
  dot?: boolean;
};

export function StatusBadge({ tone = "neutral", dot = true, className, children, ...props }: StatusBadgeProps) {
  return (
    <Badge
      variant="outline"
      className={cn("gap-1.5 border px-2.5 py-1 font-medium", toneClasses[tone], className)}
      {...props}
    >
      {dot ? <span aria-hidden="true" className="size-1.5 rounded-full bg-current" /> : null}
      {children}
    </Badge>
  );
}
