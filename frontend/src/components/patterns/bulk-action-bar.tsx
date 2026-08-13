import { XIcon } from "@phosphor-icons/react";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";

/**
 * Floating bar for acting on a selection.
 *
 * <p>Rendered only when something is selected, so it never occupies space it has no use for, and
 * fixed to the bottom of the viewport rather than to the end of the table — a selection is made
 * by scrolling through rows, and an action bar that scrolls away with them is unreachable
 * exactly when it is needed.
 *
 * <p>It carries only operations that make sense applied to many rows at once. Anything that
 * identifies a single person — a name, an email — belongs on the row itself.
 */
export function BulkActionBar({
  count,
  onClear,
  children,
  noun = "sélectionné",
  nounPlural = "sélectionnés",
}: {
  count: number;
  onClear: () => void;
  children: ReactNode;
  noun?: string;
  nounPlural?: string;
}) {
  if (count === 0) return null;
  return (
    <section
      aria-label="Actions groupées"
      className="pointer-events-none fixed inset-x-0 bottom-0 z-40 flex justify-center p-4"
    >
      <div className="pointer-events-auto flex flex-wrap items-center gap-4 rounded-xl border bg-popover px-4 py-2.5 shadow-lg">
        <span className="text-sm font-medium">
          {count} {count > 1 ? nounPlural : noun}
        </span>
        <div className="flex flex-wrap items-center gap-2">{children}</div>
        <Button aria-label="Annuler la sélection" onClick={onClear} size="icon-sm" variant="ghost">
          <XIcon />
        </Button>
      </div>
    </section>
  );
}
