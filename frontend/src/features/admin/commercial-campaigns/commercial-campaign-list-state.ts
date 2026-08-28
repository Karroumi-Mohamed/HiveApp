import type { SortingState } from "@tanstack/react-table";
import type {
  CommercialCampaignAudienceMode,
  CommercialCampaignSource,
  CommercialCampaignStatus,
} from "@/api/contracts";

export type CommercialCampaignListState = {
  search: string;
  status: CommercialCampaignStatus | "ALL";
  audienceMode: CommercialCampaignAudienceMode | "ALL";
  source: CommercialCampaignSource | "ALL";
  includeArchived: boolean;
  page: number;
  size: 10 | 20 | 50;
  sort: string;
  direction: "asc" | "desc";
};

const statuses = new Set(["DRAFT", "SCHEDULED", "ACTIVE", "PAUSED", "ENDED", "ARCHIVED"]);
const audiences = new Set(["PUBLIC", "EXPLICIT_ACCOUNTS", "SEGMENT"]);
const sources = new Set(["MARKETING", "SALES", "RETENTION", "SUPPORT", "MANUAL"]);
export const campaignSortableFields = new Set([
  "createdAt",
  "updatedAt",
  "code",
  "name",
  "status",
  "source",
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

export function readCampaignListState(params: URLSearchParams): CommercialCampaignListState {
  const requestedSize = Number(params.get("size"));
  const requestedSort = params.get("sort") ?? "createdAt";
  return {
    search: params.get("q")?.trim().slice(0, 180) ?? "",
    status: member(params.get("status"), statuses),
    audienceMode: member(params.get("audience"), audiences),
    source: member(params.get("source"), sources),
    includeArchived: params.get("archived") === "true",
    page: page(params.get("page")),
    size: ([10, 20, 50].includes(requestedSize) ? requestedSize : 20) as 10 | 20 | 50,
    sort: campaignSortableFields.has(requestedSort) ? requestedSort : "createdAt",
    direction: params.get("direction") === "asc" ? "asc" : "desc",
  };
}

export function writeCampaignListState(current: URLSearchParams, state: CommercialCampaignListState) {
  const next = new URLSearchParams(current);
  const values: Record<string, string | null> = {
    q: state.search || null,
    status: state.status === "ALL" ? null : state.status,
    audience: state.audienceMode === "ALL" ? null : state.audienceMode,
    source: state.source === "ALL" ? null : state.source,
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

export function campaignSorting(state: CommercialCampaignListState): SortingState {
  return [{ id: state.sort, desc: state.direction === "desc" }];
}

export function campaignListStateFromSorting(state: CommercialCampaignListState, sorting: SortingState) {
  const first = sorting[0];
  return first && campaignSortableFields.has(first.id)
    ? { ...state, page: 0, sort: first.id, direction: first.desc ? ("desc" as const) : ("asc" as const) }
    : state;
}
