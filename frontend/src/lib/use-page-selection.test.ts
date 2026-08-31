import { describe, expect, test } from "bun:test";
import type { RowSelectionState } from "@tanstack/react-table";
import { pruneSelection, reconcilePageSelection, selectionContextSignature } from "./use-page-selection";

const visible = (...ids: string[]) => new Set(ids);

describe("selection pruning", () => {
  test("keeps selections whose rows are still on screen", () => {
    const selection: RowSelectionState = { a: true, b: true };
    expect(pruneSelection(selection, visible("a", "b"))).toBe(selection);
  });

  test("drops a selection whose row has disappeared", () => {
    // The regression: the id stayed in state, so the row silently returned already selected when
    // a later refetch brought it back.
    expect(pruneSelection({ a: true, b: true }, visible("a"))).toEqual({ a: true });
  });

  test("a returning row is not selected again", () => {
    const afterDisappearing = pruneSelection({ a: true }, visible());
    expect(pruneSelection(afterDisappearing, visible("a"))).toEqual({});
  });

  test("an empty page clears everything", () => {
    expect(pruneSelection({ a: true, b: true }, visible())).toEqual({});
  });

  test("returns the same object when nothing changed, so no needless state write happens", () => {
    const selection: RowSelectionState = {};
    expect(pruneSelection(selection, visible("a"))).toBe(selection);
  });
});

describe("selection query context", () => {
  const selection: RowSelectionState = { a: true };
  const base = selectionContextSignature(["", "all", 0, []]);

  test("changing page clears selection even when the same row id is visible", () => {
    const next = selectionContextSignature(["", "all", 1, []]);
    expect(reconcilePageSelection(selection, base, next, visible("a"))).toEqual({});
  });

  test("changing a filter clears selection", () => {
    const next = selectionContextSignature(["", "active", 0, []]);
    expect(reconcilePageSelection(selection, base, next, visible("a"))).toEqual({});
  });

  test("changing sort order clears selection", () => {
    const next = selectionContextSignature(["", "all", 0, [{ id: "email", desc: true }]]);
    expect(reconcilePageSelection(selection, base, next, visible("a"))).toEqual({});
  });

  test("the same context still prunes a row that disappeared", () => {
    expect(reconcilePageSelection(selection, base, base, visible())).toEqual({});
  });
});
