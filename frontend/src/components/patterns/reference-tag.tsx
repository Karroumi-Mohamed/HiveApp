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
  return (
    <button
      className={cn(
        referenceTagClasses,
        "cursor-pointer hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
        className,
      )}
      type={type}
      {...props}
    >
      {children}
    </button>
  );
}
