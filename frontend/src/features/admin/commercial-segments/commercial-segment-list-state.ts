import type { SortingState } from "@tanstack/react-table";
import type { CommercialSegmentKind, CommercialSegmentSource, CommercialSegmentStatus } from "@/api/contracts";

export type CommercialSegmentListState = {
  search: string;
  status: CommercialSegmentStatus | "ALL";
  kind: CommercialSegmentKind | "ALL";
  source: CommercialSegmentSource | "ALL";
  includeArchived: boolean;
  page: number;
  size: 10 | 20 | 50;
  sort: string;
  direction: "asc" | "desc";
};

const statuses = new Set(["DRAFT", "ACTIVE", "ARCHIVED"]);
const kinds = new Set(["EXPLICIT_ACCOUNTS", "TYPED_CRITERIA"]);
const sources = new Set(["MANUAL", "IMPORTED", "SUPPORT"]);
export const commercialSegmentSortableFields = new Set([
  "createdAt",
  "updatedAt",
  "code",
  "name",
  "status",
  "kind",
  "source",
  "revisionNumber",
]);

function member<T extends string>(value: string | null, allowed: Set<string>): T | "ALL" {
  return value && allowed.has(value) ? (value as T) : "ALL";
}

function page(value: string | null) {
  if (!value || !/^\d+$/.test(value)) return 0;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed <= 10_000 ? parsed : 0;
}

export function readCommercialSegmentListState(params: URLSearchParams): CommercialSegmentListState {
  const requestedSize = Number(params.get("size"));
  const requestedSort = params.get("sort") ?? "updatedAt";
  return {
    search: params.get("q")?.trim().slice(0, 180) ?? "",
    status: member(params.get("status"), statuses),
    kind: member(params.get("kind"), kinds),
    source: member(params.get("source"), sources),
    includeArchived: params.get("archived") === "true",
    page: page(params.get("page")),
    size: ([10, 20, 50].includes(requestedSize) ? requestedSize : 20) as 10 | 20 | 50,
    sort: commercialSegmentSortableFields.has(requestedSort) ? requestedSort : "updatedAt",
    direction: params.get("direction") === "asc" ? "asc" : "desc",
  };
}

export function writeCommercialSegmentListState(current: URLSearchParams, state: CommercialSegmentListState) {
  const next = new URLSearchParams(current);
  const values: Record<string, string | null> = {
    q: state.search || null,
    status: state.status === "ALL" ? null : state.status,
    kind: state.kind === "ALL" ? null : state.kind,
    source: state.source === "ALL" ? null : state.source,
    archived: state.includeArchived ? "true" : null,
    page: state.page ? String(state.page) : null,
    size: state.size === 20 ? null : String(state.size),
    sort: state.sort === "updatedAt" ? null : state.sort,
    direction: state.direction === "desc" ? null : state.direction,
  };
  for (const [key, value] of Object.entries(values)) {
    if (value === null) next.delete(key);
    else next.set(key, value);
  }
  return next;
}

export function commercialSegmentSorting(state: CommercialSegmentListState): SortingState {
  return [{ id: state.sort, desc: state.direction === "desc" }];
}

export function segmentListStateFromSorting(state: CommercialSegmentListState, sorting: SortingState) {
  const first = sorting[0];
  return first && commercialSegmentSortableFields.has(first.id)
    ? { ...state, page: 0, sort: first.id, direction: first.desc ? ("desc" as const) : ("asc" as const) }
    : state;
}
