import type { SortingState } from "@tanstack/react-table";
import type { OfferAcceptance, OfferDiscovery, OfferStatus } from "@/api/offer-contracts";

export type OfferListState = {
  search: string;
  status: OfferStatus | "ALL";
  discovery: OfferDiscovery | "ALL";
  acceptance: OfferAcceptance | "ALL";
  includeArchived: boolean;
  page: number;
  size: 10 | 20 | 50;
  sort: string;
  direction: "asc" | "desc";
};

const statuses = new Set(["DRAFT", "PUBLISHED", "RETIRED", "ARCHIVED"]);
const discoveries = new Set(["CATALOG", "CODE_ONLY"]);
const acceptances = new Set(["CLIENT_OR_OPERATOR", "OPERATOR_ONLY"]);
export const offerSortableFields = new Set([
  "createdAt",
  "updatedAt",
  "code",
  "name",
  "status",
  "startsAt",
  "endsAt",
  "revisionNumber",
]);

function member<T extends string>(value: string | null, values: Set<string>): T | "ALL" {
  return value && values.has(value) ? (value as T) : "ALL";
}

function page(value: string | null) {
  if (!value || !/^\d+$/.test(value)) return 0;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed <= 10_000 ? parsed : 0;
}

export function readOfferListState(params: URLSearchParams): OfferListState {
  const requestedSize = Number(params.get("size"));
  const requestedSort = params.get("sort") ?? "createdAt";
  return {
    search: params.get("q")?.trim().slice(0, 180) ?? "",
    status: member(params.get("status"), statuses),
    discovery: member(params.get("discovery"), discoveries),
    acceptance: member(params.get("acceptance"), acceptances),
    includeArchived: params.get("archived") === "true",
    page: page(params.get("page")),
    size: ([10, 20, 50].includes(requestedSize) ? requestedSize : 20) as 10 | 20 | 50,
    sort: offerSortableFields.has(requestedSort) ? requestedSort : "createdAt",
    direction: params.get("direction") === "asc" ? "asc" : "desc",
  };
}

export function writeOfferListState(current: URLSearchParams, state: OfferListState) {
  const next = new URLSearchParams(current);
  const values: Record<string, string | null> = {
    q: state.search || null,
    status: state.status === "ALL" ? null : state.status,
    discovery: state.discovery === "ALL" ? null : state.discovery,
    acceptance: state.acceptance === "ALL" ? null : state.acceptance,
    archived: state.includeArchived ? "true" : null,
    page: state.page ? String(state.page) : null,
    size: state.size === 20 ? null : String(state.size),
    sort: state.sort === "createdAt" ? null : state.sort,
    direction: state.direction === "desc" ? null : state.direction,
  };
  for (const [key, value] of Object.entries(values)) {
    if (value === null) next.delete(key);
    else next.set(key, value);
  }
  return next;
}

export function offerSorting(state: OfferListState): SortingState {
  return [{ id: state.sort, desc: state.direction === "desc" }];
}

export function offerListStateFromSorting(state: OfferListState, sorting: SortingState): OfferListState {
  const first = sorting[0];
  return first && offerSortableFields.has(first.id)
    ? { ...state, page: 0, sort: first.id, direction: first.desc ? "desc" : "asc" }
    : state;
}
