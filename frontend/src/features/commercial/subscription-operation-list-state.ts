import type { SortingState } from "@tanstack/react-table";

export type SubscriptionOperationSort = "createdAt" | "effectiveAt" | "status" | "timing";

export type SubscriptionOperationListState = {
  page: number;
  size: number;
  sort: SubscriptionOperationSort;
  direction: "asc" | "desc";
};

type SubscriptionOperationUrlKeys = {
  page: string;
  size: string;
  sort: string;
  direction: string;
};

export const adminSubscriptionOperationUrlKeys: SubscriptionOperationUrlKeys = {
  page: "operationPage",
  size: "operationSize",
  sort: "operationSort",
  direction: "operationDirection",
};

export const clientSubscriptionOperationUrlKeys: SubscriptionOperationUrlKeys = {
  page: "changePage",
  size: "changeSize",
  sort: "changeSort",
  direction: "changeDirection",
};

const sorts = new Set<SubscriptionOperationSort>(["createdAt", "effectiveAt", "status", "timing"]);

export function readSubscriptionOperationListState(
  params: URLSearchParams,
  keys: SubscriptionOperationUrlKeys,
): SubscriptionOperationListState {
  const page = Number(params.get(keys.page));
  const size = Number(params.get(keys.size));
  const sort = params.get(keys.sort) as SubscriptionOperationSort | null;
  return {
    page: Number.isInteger(page) && page >= 0 ? page : 0,
    size: [10, 20, 50].includes(size) ? size : 20,
    sort: sort && sorts.has(sort) ? sort : "createdAt",
    direction: params.get(keys.direction) === "asc" ? "asc" : "desc",
  };
}

export function writeSubscriptionOperationListState(
  current: URLSearchParams,
  state: SubscriptionOperationListState,
  keys: SubscriptionOperationUrlKeys,
): URLSearchParams {
  const next = new URLSearchParams(current);
  const values: Record<string, string | null> = {
    [keys.page]: state.page ? String(state.page) : null,
    [keys.size]: state.size === 20 ? null : String(state.size),
    [keys.sort]: state.sort === "createdAt" ? null : state.sort,
    [keys.direction]: state.direction === "desc" ? null : state.direction,
  };
  for (const [key, value] of Object.entries(values)) {
    if (value === null) next.delete(key);
    else next.set(key, value);
  }
  return next;
}

export function subscriptionOperationQuery(state: SubscriptionOperationListState) {
  return {
    page: state.page,
    size: state.size,
    sort: state.sort,
    direction: state.direction,
  } as const;
}

export function subscriptionOperationSorting(state: SubscriptionOperationListState): SortingState {
  return [{ id: state.sort, desc: state.direction === "desc" }];
}

export function subscriptionOperationStateFromSorting(
  state: SubscriptionOperationListState,
  sorting: SortingState,
): SubscriptionOperationListState {
  const first = sorting[0];
  if (!first || !sorts.has(first.id as SubscriptionOperationSort)) return state;
  return {
    ...state,
    page: 0,
    sort: first.id as SubscriptionOperationSort,
    direction: first.desc ? "desc" : "asc",
  };
}
