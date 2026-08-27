import type { SortingState } from "@tanstack/react-table";
import type { SubscriptionStatus } from "@/api/contracts";

export type SubscriptionAccountStatusFilter = "all" | "active" | "inactive";
export type SubscriptionFilter = "all" | "any" | "none" | SubscriptionStatus;

export type SubscriptionAccountListState = {
  search: string;
  accountStatus: SubscriptionAccountStatusFilter;
  subscription: SubscriptionFilter;
  page: number;
  size: number;
  sort: "name" | "slug" | "active" | "createdAt";
  direction: "asc" | "desc";
};

const accountStatuses = new Set<SubscriptionAccountStatusFilter>(["all", "active", "inactive"]);
const subscriptionFilters = new Set<SubscriptionFilter>([
  "all",
  "any",
  "none",
  "ACTIVE",
  "TRIALING",
  "PAST_DUE",
  "SUSPENDED",
  "CANCELLED",
  "EXPIRED",
]);
const sorts = new Set<SubscriptionAccountListState["sort"]>(["name", "slug", "active", "createdAt"]);

const nonNegativeInteger = (value: string | null) => {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : 0;
};

export function readSubscriptionAccountListState(params: URLSearchParams): SubscriptionAccountListState {
  const requestedSize = Number(params.get("size"));
  const accountStatus = params.get("accountStatus") as SubscriptionAccountStatusFilter | null;
  const subscription = params.get("subscription") as SubscriptionFilter | null;
  const sort = params.get("sort") as SubscriptionAccountListState["sort"] | null;
  return {
    search: params.get("q")?.trim() ?? "",
    accountStatus: accountStatus && accountStatuses.has(accountStatus) ? accountStatus : "all",
    subscription: subscription && subscriptionFilters.has(subscription) ? subscription : "all",
    page: nonNegativeInteger(params.get("page")),
    size: [10, 20, 50].includes(requestedSize) ? requestedSize : 20,
    sort: sort && sorts.has(sort) ? sort : "name",
    direction: params.get("direction") === "desc" ? "desc" : "asc",
  };
}

export function writeSubscriptionAccountListState(current: URLSearchParams, next: SubscriptionAccountListState) {
  const params = new URLSearchParams(current);
  const values: Record<string, string | null> = {
    q: next.search || null,
    accountStatus: next.accountStatus === "all" ? null : next.accountStatus,
    subscription: next.subscription === "all" ? null : next.subscription,
    page: next.page ? String(next.page) : null,
    size: next.size === 20 ? null : String(next.size),
    sort: next.sort === "name" ? null : next.sort,
    direction: next.direction === "asc" ? null : next.direction,
  };
  for (const [key, value] of Object.entries(values)) {
    if (value === null) params.delete(key);
    else params.set(key, value);
  }
  return params;
}

export function subscriptionAccountQuery(state: SubscriptionAccountListState) {
  const exactStatus =
    subscriptionFilters.has(state.subscription) && !["all", "any", "none"].includes(state.subscription)
      ? (state.subscription as SubscriptionStatus)
      : undefined;
  return {
    query: state.search || undefined,
    accountActive: state.accountStatus === "all" ? undefined : state.accountStatus === "active",
    subscriptionStatus: exactStatus,
    hasSubscription: state.subscription === "any" ? true : state.subscription === "none" ? false : undefined,
    page: state.page,
    size: state.size,
    sort: state.sort,
    direction: state.direction,
  } as const;
}

export function subscriptionAccountSorting(state: SubscriptionAccountListState): SortingState {
  return [{ id: state.sort, desc: state.direction === "desc" }];
}

export function subscriptionAccountStateFromSorting(
  state: SubscriptionAccountListState,
  sorting: SortingState,
): SubscriptionAccountListState {
  const first = sorting[0];
  return first && sorts.has(first.id as SubscriptionAccountListState["sort"])
    ? {
        ...state,
        page: 0,
        sort: first.id as SubscriptionAccountListState["sort"],
        direction: first.desc ? "desc" : "asc",
      }
    : state;
}
