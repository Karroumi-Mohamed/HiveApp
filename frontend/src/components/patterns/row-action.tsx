import type { ReactNode } from "react";
import { Link } from "react-router";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";

/**
 * An icon button in a table row, with the tooltip carrying its meaning.
 *
 * <p>Unavailable actions use `aria-disabled` rather than `disabled`. A truly disabled button is
 * removed from the tab order and receives no pointer events, so its tooltip can never appear —
 * losing the explanation exactly when the reader most needs it ("why can't I click this?").
 * Keeping it focusable preserves that explanation for keyboard and screen-reader users, and the
 * click handler is detached so it still does nothing.
 */
export function RowAction({
  label,
  disabledLabel,
  icon,
  to,
  onClick,
  disabled = false,
  tone = "default",
}: {
  label: string;
  /** Why the action is unavailable. Shown instead of `label` when disabled. */
  disabledLabel?: string;
  icon: ReactNode;
  to?: string;
  onClick?: () => void;
  disabled?: boolean;
  tone?: "default" | "danger";
}) {
  const control = to ? (
    <Button aria-label={label} asChild size="icon-sm" variant="ghost">
      <Link to={to}>{icon}</Link>
    </Button>
  ) : (
    <Button
      aria-disabled={disabled || undefined}
      aria-label={label}
      className={cn(
        tone === "danger" && !disabled && "text-destructive hover:text-destructive",
        disabled && "cursor-not-allowed opacity-45 hover:bg-transparent",
      )}
      onClick={disabled ? undefined : onClick}
      size="icon-sm"
      variant="ghost"
    >
      {icon}
    </Button>
  );

  return (
    <Tooltip>
      <TooltipTrigger asChild>{control}</TooltipTrigger>
      <TooltipContent>{disabled ? (disabledLabel ?? label) : label}</TooltipContent>
    </Tooltip>
  );
}
