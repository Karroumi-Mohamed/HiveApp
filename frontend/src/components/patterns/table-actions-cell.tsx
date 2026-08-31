import type { ReactNode } from "react";

const columnWidthByCapacity = {
  1: "w-16",
  2: "w-24",
  3: "w-32",
} as const;

/**
 * Keeps operational-table action columns aligned as their action count changes.
 * The widths intentionally include breathing room around the icon controls.
 */
export function tableActionsColumnMeta(capacity: keyof typeof columnWidthByCapacity) {
  const width = columnWidthByCapacity[capacity];
  return { headerClassName: width, cellClassName: width };
}

/** A predictable end-of-row location for icon actions on desktop and mobile lists. */
export function TableActionsCell({ children, label }: { children: ReactNode; label: string }) {
  return (
    <fieldset aria-label={label} className="m-0 flex min-w-max items-center gap-0.5 border-0 p-0">
      {children}
    </fieldset>
  );
}
