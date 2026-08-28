import { describe, expect, test } from "bun:test";
import type {
  CommercialCampaignDetail,
  CommercialCampaignOperationState,
  CommercialCampaignSchedulePreview,
} from "@/api/contracts";
import { ApiError } from "@/api/http";
import {
  campaignFrozenAudienceLabel,
  campaignHistoryAction,
  campaignMutationMessage,
  draftFromCommercialCampaign,
  emptyCommercialCampaignDraft,
  reviewedCampaignScheduleReady,
  toCommercialCampaignWriteInput,
  validateCommercialCampaignDraft,
} from "./commercial-campaign-rules";

describe("commercial campaign draft rules", () => {
  test("requires a valid window, reason and concrete non-public audience", () => {
    const draft = emptyCommercialCampaignDraft(Date.parse("2026-08-28T10:00:00Z"));
    draft.name = "Campagne";
    draft.reason = "";
    draft.startsAt = "2026-08-29T12:00";
    draft.endsAt = "2026-08-29T11:00";
    draft.audienceMode = "EXPLICIT_ACCOUNTS";
    expect(validateCommercialCampaignDraft(draft)).toMatchObject({
      reason: expect.any(String),
      window: expect.any(String),
      audience: expect.any(String),
    });
  });

  test("keeps the exact retained Segment activation and rejects a superseded one", () => {
    const draft = emptyCommercialCampaignDraft();
    Object.assign(draft, {
      name: "Renouvellement",
      reason: "Suivi",
      audienceMode: "SEGMENT",
      segmentId: "segment-1",
      segmentActivationId: "activation-7",
    });
    expect(validateCommercialCampaignDraft(draft, "ACTIVATION_SUPERSEDED").audience).toContain("remplacée");
    expect(toCommercialCampaignWriteInput(draft).audience).toMatchObject({
      segmentId: "segment-1",
      segmentActivationId: "activation-7",
    });
  });

  test("round-trips a persisted audience without leaking hidden modes into the request", () => {
    const campaign = {
      summary: {
        name: "Comptes prioritaires",
        source: "RETENTION",
        startsAt: "2026-08-29T10:00:00Z",
        endsAt: "2026-08-30T10:00:00Z",
      },
      description: null,
      reason: "Rétention",
      audience: {
        mode: "EXPLICIT_ACCOUNTS",
        explicitAccountIds: ["account-2", "account-1", "account-1"],
        segmentId: null,
        segmentActivationId: null,
      },
    } as unknown as CommercialCampaignDetail;
    expect(toCommercialCampaignWriteInput(draftFromCommercialCampaign(campaign)).audience).toEqual({
      mode: "EXPLICIT_ACCOUNTS",
      explicitAccountIds: ["account-2", "account-1"],
      segmentId: null,
      segmentActivationId: null,
    });
  });
});

describe("commercial campaign schedule evidence", () => {
  const campaign = { id: "campaign-1", version: 4 } as CommercialCampaignOperationState;
  const preview = {
    campaignId: "campaign-1",
    campaignVersion: 4,
    previewToken: "signed.campaign.preview",
    expiresAt: "2026-08-28T12:01:00Z",
    schedulable: true,
    blockers: [],
  } as unknown as CommercialCampaignSchedulePreview;

  test("accepts only unexpired evidence for the exact Campaign revision", () => {
    const now = Date.parse("2026-08-28T12:00:00Z");
    expect(reviewedCampaignScheduleReady(campaign, preview, now)).toBeTrue();
    expect(reviewedCampaignScheduleReady(campaign, { ...preview, campaignId: "other" }, now)).toBeFalse();
    expect(reviewedCampaignScheduleReady(campaign, { ...preview, campaignVersion: 5 }, now)).toBeFalse();
    expect(reviewedCampaignScheduleReady(campaign, { ...preview, schedulable: false }, now)).toBeFalse();
    expect(reviewedCampaignScheduleReady(campaign, { ...preview, blockers: ["EMPTY_AUDIENCE"] }, now)).toBeFalse();
    expect(reviewedCampaignScheduleReady(campaign, { ...preview, expiresAt: "2026-08-28T12:00:00Z" }, now)).toBeFalse();
  });
});

describe("commercial campaign presentation", () => {
  test("does not present a public audience as an empty frozen audience", () => {
    expect(campaignFrozenAudienceLabel("PUBLIC", 0)).toBe("Publique et dynamique");
    expect(campaignFrozenAudienceLabel("EXPLICIT_ACCOUNTS", null)).toBe("—");
    expect(campaignFrozenAudienceLabel("SEGMENT", 2)).toBe("2 comptes");
  });

  test("uses stable error codes and localizes audit actions", () => {
    expect(campaignMutationMessage(new ApiError(409, "STALE_SCHEDULE_PREVIEW", "provider detail"))).toContain("expiré");
    expect(campaignMutationMessage(new ApiError(409, "STALE_RESOURCE_VERSION", "provider detail"))).toContain(
      "Rechargez",
    );
    expect(campaignMutationMessage(new ApiError(500, "HTTP_ERROR", "private stack"))).not.toContain("private");
    expect(campaignHistoryAction("platform.campaigns.process_due_start")).toContain("démarrée");
  });
});
