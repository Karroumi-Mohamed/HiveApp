import type { ComponentProps } from "react";
import { cn } from "@/lib/utils";

const referenceTagClasses =
  "inline-flex max-w-full items-center rounded-sm bg-muted/60 px-1.5 py-0.5 text-xs font-medium text-foreground";

/**
 * Names another system object inline without implying a lifecycle or health status.
 * Deliberately rectangular, neutral, and dot-free so it cannot be confused with StatusBadge.
 */
export function ReferenceTag({ className, children, ...props }: ComponentProps<"span">) {
  return (
    <span className={cn(referenceTagClasses, className)} {...props}>
      {children}
    </span>
  );
}

/** Interactive counterpart used when a reference reveals related detail in place. */
export function ReferenceTagButton({ className, children, type = "button", ...props }: ComponentProps<"button">) {
  const expanded = props["aria-expanded"] === true || props["aria-expanded"] === "true";

  return (
    <button
      className={cn(
        "inline-flex max-w-full cursor-pointer items-center gap-1.5 rounded-sm border border-border bg-muted/45 px-2 py-1 text-sm font-medium text-foreground shadow-xs transition-colors",
        "hover:border-primary/35 hover:bg-accent hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
        "data-[state=open]:border-primary/30 data-[state=open]:bg-primary/5 data-[state=open]:text-primary",
        "dark:border-muted-foreground/45 dark:bg-muted/75 dark:shadow-none dark:hover:border-primary/55 dark:hover:bg-primary/10",
        "dark:data-[state=open]:border-primary/55 dark:data-[state=open]:bg-primary/15",
        className,
      )}
      data-state={expanded ? "open" : "closed"}
      type={type}
      {...props}
    >
      {children}
    </button>
  );
}
