import type { ComponentProps } from "react";
import type { StatusTone } from "@/components/patterns/status-badge";
import { cn } from "@/lib/utils";

const toneClasses: Record<StatusTone, string> = {
  neutral: "text-muted-foreground",
  success: "text-success",
  warning: "text-warning",
  danger: "text-destructive",
  info: "text-info",
};

/** A lifecycle state expressed as semantic text, without badge chrome or a decorative dot. */
export function StatusText({
  tone = "neutral",
  className,
  children,
  ...props
}: ComponentProps<"span"> & {
  tone?: StatusTone;
}) {
  return (
    <span className={cn("text-xs font-medium", toneClasses[tone], className)} {...props}>
      {children}
    </span>
  );
}
