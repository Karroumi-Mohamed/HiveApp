import { adminPermissions } from "@/auth/permissions";

export type CampaignPermissionChecker = (permission: string) => boolean;

/** Chooses the most informative Campaign surface the current operator may actually read. */
export function campaignReadableDestination(campaignId: string, can: CampaignPermissionChecker) {
  if (can(adminPermissions.campaignsRead)) return `/admin/campaigns/${campaignId}`;
  if (can(adminPermissions.campaignsReadOperations)) return `/admin/campaigns/${campaignId}/operations`;
  if (can(adminPermissions.campaignsReadAudience) || can(adminPermissions.campaignsReadAudienceIdentities))
    return `/admin/campaigns/${campaignId}/audience`;
  if (can(adminPermissions.campaignsRevisions) || can(adminPermissions.campaignsCompare))
    return `/admin/campaigns/${campaignId}/revisions`;
  if (can(adminPermissions.campaignsHistory)) return `/admin/campaigns/${campaignId}/history`;
  if (can(adminPermissions.campaignsOwner)) return `/admin/campaigns/${campaignId}/owner`;
  return undefined;
}

export function campaignCollectionDestination(can: CampaignPermissionChecker) {
  return can(adminPermissions.campaignsList) ? "/admin/campaigns" : "/admin";
}

/** Mutation acknowledgements contain no definition, so navigation must use a readable follow-up surface. */
export function campaignMutationDestination(campaignId: string, can: CampaignPermissionChecker) {
  return campaignReadableDestination(campaignId, can) ?? campaignCollectionDestination(can);
}
