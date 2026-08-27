import type { SubscriptionStatus } from "@/api/contracts";

export type PlanSubscriberStatus = "all" | SubscriptionStatus;

export type PlanSubscriberListState = {
  search: string;
  status: PlanSubscriberStatus;
  page: number;
};

const statuses = new Set<SubscriptionStatus>(["ACTIVE", "TRIALING", "PAST_DUE", "SUSPENDED", "CANCELLED", "EXPIRED"]);

export function readPlanSubscriberListState(params: URLSearchParams): PlanSubscriberListState {
  const requestedPage = Number(params.get("subscriberPage"));
  const requestedStatus = params.get("subscriberStatus");
  return {
    search: params.get("subscriberQuery") ?? "",
    status: statuses.has(requestedStatus as SubscriptionStatus) ? (requestedStatus as SubscriptionStatus) : "all",
    page: Number.isInteger(requestedPage) && requestedPage >= 0 ? requestedPage : 0,
  };
}

export function writePlanSubscriberListState(
  current: URLSearchParams,
  state: PlanSubscriberListState,
): URLSearchParams {
  const next = new URLSearchParams(current);
  const values: Record<string, string | null> = {
    subscriberQuery: state.search || null,
    subscriberStatus: state.status === "all" ? null : state.status,
    subscriberPage: state.page ? String(state.page) : null,
  };
  for (const [key, value] of Object.entries(values)) {
    if (value === null) next.delete(key);
    else next.set(key, value);
  }
  return next;
}
