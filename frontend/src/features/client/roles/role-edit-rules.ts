import type { RoleImpact } from "@/api/contracts";

export type RoleImpactState = {
  data: RoleImpact | undefined;
  isFetching: boolean;
  isError: boolean;
};

/** Editing confirms a specific impact snapshot; retained, refreshing, or failed data is stale. */
export function isRolePreviewReady(isEdit: boolean, impact: RoleImpactState): boolean {
  return !isEdit || (impact.data !== undefined && !impact.isFetching && !impact.isError);
}
