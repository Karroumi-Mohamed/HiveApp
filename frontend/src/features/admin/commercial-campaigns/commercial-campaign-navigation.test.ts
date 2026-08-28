import { describe, expect, test } from "bun:test";
import { adminPermissions } from "@/auth/permissions";
import {
  campaignCollectionDestination,
  campaignMutationDestination,
  campaignReadableDestination,
} from "./commercial-campaign-navigation";

function can(...permissions: string[]) {
  return (permission: string) => permissions.includes(permission);
}

describe("commercial campaign navigation", () => {
  test("never sends a mutation-only operator to a read-gated Campaign detail", () => {
    expect(campaignMutationDestination("campaign-1", can(adminPermissions.campaignsCreate))).toBe("/admin");
    expect(
      campaignMutationDestination("campaign-1", can(adminPermissions.campaignsCreate, adminPermissions.campaignsList)),
    ).toBe("/admin/campaigns");
  });

  test("uses the most informative independently readable surface", () => {
    expect(campaignReadableDestination("campaign-1", can(adminPermissions.campaignsHistory))).toBe(
      "/admin/campaigns/campaign-1/history",
    );
    expect(
      campaignReadableDestination(
        "campaign-1",
        can(adminPermissions.campaignsReadOperations, adminPermissions.campaignsHistory),
      ),
    ).toBe("/admin/campaigns/campaign-1/operations");
    expect(
      campaignReadableDestination(
        "campaign-1",
        can(adminPermissions.campaignsRead, adminPermissions.campaignsReadOperations),
      ),
    ).toBe("/admin/campaigns/campaign-1");
  });

  test("falls back to the collection only when it is readable", () => {
    expect(campaignCollectionDestination(can(adminPermissions.campaignsList))).toBe("/admin/campaigns");
    expect(campaignCollectionDestination(can())).toBe("/admin");
  });
});
