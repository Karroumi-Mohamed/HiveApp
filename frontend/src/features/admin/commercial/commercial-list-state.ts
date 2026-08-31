import type { SortingState } from "@tanstack/react-table";

export type CommercialListState = {
  search: string;
  status: string;
  visibility: string;
  page: number;
  size: number;
  sort: string;
  direction: "asc" | "desc";
};

const positivePage = (value: string | null) => {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : 0;
};

export function readCommercialListState(params: URLSearchParams, defaultSort = "updatedAt"): CommercialListState {
  const requestedSize = Number(params.get("size"));
  return {
    search: params.get("q")?.trim() ?? "",
    status: params.get("status") ?? "all",
    visibility: params.get("visibility") ?? "all",
    page: positivePage(params.get("page")),
    size: [10, 20, 50].includes(requestedSize) ? requestedSize : 20,
    sort: params.get("sort") ?? defaultSort,
    direction: params.get("direction") === "asc" ? "asc" : "desc",
  };
}

export function writeCommercialListState(current: URLSearchParams, next: CommercialListState) {
  const params = new URLSearchParams(current);
  const values: Record<string, string | null> = {
    q: next.search || null,
    status: next.status === "all" ? null : next.status,
    visibility: next.visibility === "all" ? null : next.visibility,
    page: next.page ? String(next.page) : null,
    size: next.size === 20 ? null : String(next.size),
    sort: next.sort,
    direction: next.direction,
  };
  for (const [key, value] of Object.entries(values)) {
    if (value === null) params.delete(key);
    else params.set(key, value);
  }
  return params;
}

export function sortingFromListState(state: CommercialListState): SortingState {
  return [{ id: state.sort, desc: state.direction === "desc" }];
}

export function listStateFromSorting(state: CommercialListState, sorting: SortingState): CommercialListState {
  const first = sorting[0];
  return first ? { ...state, page: 0, sort: first.id, direction: first.desc ? "desc" : "asc" } : state;
}
