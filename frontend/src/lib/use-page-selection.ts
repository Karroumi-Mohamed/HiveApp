import type { RowSelectionState } from "@tanstack/react-table";
import { useMemo, useState } from "react";

/**
 * Drops selected ids that are no longer on screen, returning the original object when nothing
 * changed so the caller can compare by identity and avoid a pointless state write.
 *
 * <p>Filtering only the derived list is not enough: the state would still hold the vanished id,
 * and a row that came back on a later refetch would silently arrive already selected.
 */
export function pruneSelection(selection: RowSelectionState, visibleIds: Set<string>): RowSelectionState {
  // RowSelectionState is Record<string, true>: an id being present is what "selected" means, so
  // pruning is purely a question of whether the row is still on screen.
  const kept = Object.keys(selection).filter((id) => visibleIds.has(id));
  if (kept.length === Object.keys(selection).length) return selection;
  return Object.fromEntries(kept.map((id) => [id, true])) as RowSelectionState;
}

export function selectionContextSignature(contextKey: unknown): string {
  return JSON.stringify(contextKey ?? null);
}

/** Selection never survives a page, filter, search, or sort context change. */
export function reconcilePageSelection(
  selection: RowSelectionState,
  previousContext: string,
  currentContext: string,
  visibleIds: Set<string>,
): RowSelectionState {
  if (previousContext !== currentContext) return {};
  return pruneSelection(selection, visibleIds);
}

/**
 * Row selection scoped to the rows currently on screen.
 *
 * <p>Selection is deliberately *not* carried across pages, searches, filters or sort changes. A
 * selection that outlives its query context is invisible: the operator sees "3 selected" while
 * looking at a different page, and a bulk action then reaches rows they cannot see and did not
 * mean to include.
 *
 * @param contextKey everything that determines which rows are on screen
 */

export function usePageSelection<T>(rows: T[], getRowId: (row: T) => string, contextKey: unknown) {
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({});
  const contextSignature = selectionContextSignature(contextKey);

  // Adjusted during render rather than in an effect, so there is no frame in which the new page
  // is on screen while the previous page's selection is still live.
  const [seenContext, setSeenContext] = useState(contextSignature);
  if (seenContext !== contextSignature) {
    setSeenContext(contextSignature);
  }

  const visibleIds = useMemo(() => new Set(rows.map(getRowId)), [rows, getRowId]);

  const reconciledSelection = useMemo(
    () => reconcilePageSelection(rowSelection, seenContext, contextSignature, visibleIds),
    [contextSignature, rowSelection, seenContext, visibleIds],
  );

  if (reconciledSelection !== rowSelection) setRowSelection(reconciledSelection);

  const selectedIds = useMemo(() => Object.keys(reconciledSelection), [reconciledSelection]);

  return {
    rowSelection: reconciledSelection,
    setRowSelection,
    selectedIds,
    clearSelection: () => setRowSelection({}),
  };
}
