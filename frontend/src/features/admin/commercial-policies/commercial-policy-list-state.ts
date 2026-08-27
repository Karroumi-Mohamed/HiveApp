import type { SortingState } from "@tanstack/react-table";
import type { CommercialPolicySource, CommercialPolicyStatus, CommercialPolicyTargetKind } from "@/api/contracts";

export type CommercialPolicyListState = {
  search: string;
  status: CommercialPolicyStatus | "ALL";
  targetKind: CommercialPolicyTargetKind | "ALL";
  source: CommercialPolicySource | "ALL";
  effectiveAt: string;
  includeArchived: boolean;
  page: number;
  size: 10 | 20 | 50;
  sort: string;
  direction: "asc" | "desc";
};

const statuses = new Set(["DRAFT", "ACTIVE", "PAUSED", "ENDED", "ARCHIVED"]);
const targets = new Set(["ACCOUNT", "ACCOUNT_SET", "SEGMENT", "PLAN_REVISION_SUBSCRIBERS"]);
const sources = new Set(["CONTRACT", "SALES", "MARKETING", "RETENTION", "SUPPORT", "COMPLIANCE", "OTHER"]);
export const commercialPolicySortableFields = new Set([
  "createdAt",
  "updatedAt",
  "code",
  "name",
  "status",
  "targetKind",
  "source",
  "priority",
  "effectiveFrom",
  "effectiveUntil",
  "revisionNumber",
]);

function boundedPage(value: string | null) {
  if (!value || !/^\d+$/.test(value)) return 0;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed <= 10_000 ? parsed : 0;
}

function member<T extends string>(value: string | null, allowed: Set<string>): T | "ALL" {
  return value && allowed.has(value) ? (value as T) : "ALL";
}

function boundedDate(value: string | null) {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return "";
  const [year, month, day] = value.split("-").map(Number);
  const parsed = new Date(year ?? 0, (month ?? 1) - 1, day ?? 1);
  return parsed.getFullYear() === year && parsed.getMonth() === (month ?? 1) - 1 && parsed.getDate() === day
    ? value
    : "";
}

export function readCommercialPolicyListState(params: URLSearchParams): CommercialPolicyListState {
  const requestedSize = Number(params.get("size"));
  const requestedSort = params.get("sort") ?? "updatedAt";
  return {
    search: params.get("q")?.trim().slice(0, 180) ?? "",
    status: member(params.get("status"), statuses),
    targetKind: member(params.get("target"), targets),
    source: member(params.get("source"), sources),
    effectiveAt: boundedDate(params.get("effectiveAt")),
    includeArchived: params.get("archived") === "true",
    page: boundedPage(params.get("page")),
    size: ([10, 20, 50].includes(requestedSize) ? requestedSize : 20) as 10 | 20 | 50,
    sort: commercialPolicySortableFields.has(requestedSort) ? requestedSort : "updatedAt",
    direction: params.get("direction") === "asc" ? "asc" : "desc",
  };
}

export function writeCommercialPolicyListState(current: URLSearchParams, state: CommercialPolicyListState) {
  const next = new URLSearchParams(current);
  const values: Record<string, string | null> = {
    q: state.search || null,
    status: state.status === "ALL" ? null : state.status,
    target: state.targetKind === "ALL" ? null : state.targetKind,
    source: state.source === "ALL" ? null : state.source,
    effectiveAt: state.effectiveAt || null,
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

export function commercialPolicySorting(state: CommercialPolicyListState): SortingState {
  return [{ id: state.sort, desc: state.direction === "desc" }];
}

export function policyListStateFromSorting(state: CommercialPolicyListState, sorting: SortingState) {
  const first = sorting[0];
  return first
    ? { ...state, page: 0, sort: first.id, direction: first.desc ? ("desc" as const) : ("asc" as const) }
    : state;
}

export function effectiveAtInstant(localDate: string) {
  if (!localDate) return undefined;
  const instant = new Date(`${localDate}T12:00:00`);
  return Number.isNaN(instant.getTime()) ? undefined : instant.toISOString();
}
