import { describe, expect, test } from "bun:test";
import type { CommercialSegmentDetail } from "@/api/contracts";
import { ApiError } from "@/api/http";
import {
  draftFromCommercialSegment,
  emptyCommercialSegmentDraft,
  reviewedSegmentActivationReady,
  segmentCurrentSubscriptionStatuses,
  segmentHistoryAction,
  segmentMutationMessage,
  toCommercialSegmentWriteInput,
  validateCommercialSegmentDraft,
} from "./commercial-segment-rules";

describe("commercial segment definition rules", () => {
  test("requires an audience for either definition kind", () => {
    const explicit = emptyCommercialSegmentDraft();
    explicit.name = "Comptes prioritaires";
    explicit.reason = "Suivi contractuel";
    expect(validateCommercialSegmentDraft(explicit).audience).toContain("compte");

    const criteria = { ...explicit, kind: "TYPED_CRITERIA" as const };
    expect(validateCommercialSegmentDraft(criteria).audience).toContain("critère");
  });

  test("normalizes sets while keeping OR groups and cross-group AND inputs explicit", () => {
    const draft = emptyCommercialSegmentDraft();
    Object.assign(draft, {
      name: "  Abonnements à risque  ",
      reason: "  Prévenir une rupture  ",
      kind: "TYPED_CRITERIA",
      subscriptionStatuses: ["PAST_DUE", "SUSPENDED", "PAST_DUE"],
      currencyCodes: [" mad ", "MAD", "eur"],
      productHoldings: [
        { type: "ADD_ON", code: "CUSTOM_ROLES" },
        { type: "ADD_ON", code: "CUSTOM_ROLES" },
        { type: "PLAN", code: " FLEX " },
      ],
    });

    expect(toCommercialSegmentWriteInput(draft).definition.criteria).toEqual({
      currentPlanRevisionIds: [],
      subscriptionStatuses: ["PAST_DUE", "SUSPENDED"],
      currencyCodes: ["MAD", "EUR"],
      billingCycles: [],
      accountCreatedFrom: null,
      accountCreatedUntil: null,
      productHoldings: [
        { type: "ADD_ON", code: "CUSTOM_ROLES" },
        { type: "PLAN", code: "FLEX" },
      ],
    });
  });

  test("rejects invalid currencies, empty products and reversed account windows", () => {
    const draft = emptyCommercialSegmentDraft();
    Object.assign(draft, {
      name: "Audience",
      reason: "Analyse",
      kind: "TYPED_CRITERIA",
      currencyCodes: ["MA"],
      productHoldings: [{ type: "PLAN", code: " " }],
      accountCreatedFrom: "2026-08-27T10:00",
      accountCreatedUntil: "2026-08-27T09:00",
    });
    expect(validateCommercialSegmentDraft(draft)).toMatchObject({
      currencies: expect.any(String),
      products: expect.any(String),
      createdWindow: expect.any(String),
    });
  });

  test("offers only backend-supported current subscription statuses", () => {
    expect(segmentCurrentSubscriptionStatuses).toEqual(["TRIALING", "ACTIVE", "PAST_DUE", "SUSPENDED"]);
    const draft = emptyCommercialSegmentDraft();
    Object.assign(draft, {
      name: "Audience",
      reason: "Analyse",
      kind: "TYPED_CRITERIA",
      subscriptionStatuses: ["CANCELLED"],
    });
    expect(validateCommercialSegmentDraft(draft).statuses).toContain("annulé");
  });

  test("mirrors the backend date bounds and maximum account-age window", () => {
    const draft = emptyCommercialSegmentDraft();
    Object.assign(draft, {
      name: "Audience",
      reason: "Analyse",
      kind: "TYPED_CRITERIA",
      accountCreatedFrom: "1999-12-31T23:59:59Z",
    });
    const now = Date.parse("2026-08-27T12:00:00Z");
    expect(validateCommercialSegmentDraft(draft, now).createdWindow).toContain("2000");

    draft.accountCreatedFrom = "2000-01-01T00:00:00Z";
    draft.accountCreatedUntil = "2021-01-02T00:00:00Z";
    expect(validateCommercialSegmentDraft(draft, now).createdWindow).toContain("20 ans");

    draft.accountCreatedFrom = "";
    draft.accountCreatedUntil = "2026-08-29T00:00:00Z";
    expect(validateCommercialSegmentDraft(draft, now).createdWindow).toContain("demain");
  });

  test("ignores hidden typed criteria after switching to an explicit-account audience", () => {
    const draft = emptyCommercialSegmentDraft();
    Object.assign(draft, {
      name: "Audience explicite",
      reason: "Analyse",
      kind: "EXPLICIT_ACCOUNTS",
      explicitAccountIds: ["account-1"],
      accountCreatedFrom: "1999-12-31T23:59:59Z",
      subscriptionStatuses: ["CANCELLED"],
    });

    expect(validateCommercialSegmentDraft(draft, Date.parse("2026-08-27T12:00:00Z"))).toEqual({});
  });

  test("round-trips a persisted typed definition without inventing explicit accounts", () => {
    const segment = {
      summary: { name: "Renouvellements", kind: "TYPED_CRITERIA", source: "MANUAL" },
      description: "Audience à contacter",
      reason: "Renouvellement annuel",
      definition: {
        explicitAccountIds: [],
        criteria: {
          currentPlanRevisionIds: ["revision-1"],
          subscriptionStatuses: ["ACTIVE"],
          currencyCodes: ["MAD"],
          billingCycles: ["YEARLY"],
          accountCreatedFrom: "2026-01-01T00:00:00Z",
          accountCreatedUntil: null,
          productHoldings: [{ type: "PLAN", code: "FLEX" }],
        },
      },
    } as unknown as CommercialSegmentDetail;

    const input = toCommercialSegmentWriteInput(draftFromCommercialSegment(segment));
    expect(input.definition.explicitAccountIds).toEqual([]);
    expect(input.definition.criteria?.currentPlanRevisionIds).toEqual(["revision-1"]);
    expect(input.definition.criteria?.billingCycles).toEqual(["YEARLY"]);
    expect(input.definition.criteria?.productHoldings).toEqual([{ type: "PLAN", code: "FLEX" }]);
  });
});

describe("commercial segment activation evidence", () => {
  const segment = {
    summary: { id: "segment-1", version: 4 },
  } as unknown as CommercialSegmentDetail;
  const preview = {
    segmentId: "segment-1",
    criteriaVersion: 4,
    expiresAt: "2026-08-27T12:01:00Z",
    previewToken: "signed.segment.preview",
    activatable: true,
    blockers: [],
  };

  test("accepts only unexpired evidence for the exact segment revision", () => {
    const now = Date.parse("2026-08-27T12:00:00Z");
    expect(reviewedSegmentActivationReady(segment, preview, now)).toBeTrue();
    expect(reviewedSegmentActivationReady(segment, { ...preview, segmentId: "other" }, now)).toBeFalse();
    expect(reviewedSegmentActivationReady(segment, { ...preview, criteriaVersion: 5 }, now)).toBeFalse();
    expect(reviewedSegmentActivationReady(segment, { ...preview, activatable: false }, now)).toBeFalse();
    expect(reviewedSegmentActivationReady(segment, { ...preview, previewToken: "" }, now)).toBeFalse();
    expect(reviewedSegmentActivationReady(segment, { ...preview, blockers: ["EMPTY_AUDIENCE"] }, now)).toBeFalse();
    expect(reviewedSegmentActivationReady(segment, { ...preview, expiresAt: "2026-08-27T12:00:00Z" }, now)).toBeFalse();
  });
});

describe("commercial segment mutation errors", () => {
  test("branches on stable error codes and does not expose backend messages", () => {
    expect(segmentMutationMessage(new ApiError(409, "STALE_ACTIVATION_PREVIEW", "provider detail"))).toContain(
      "expiré",
    );
    expect(segmentMutationMessage(new ApiError(409, "STALE_RESOURCE_VERSION", "provider detail"))).toContain(
      "Rechargez",
    );
    expect(segmentMutationMessage(new ApiError(500, "HTTP_ERROR", "private stack"))).toBe(
      "L’opération n’a pas pu être exécutée.",
    );
  });
});

describe("commercial segment history presentation", () => {
  test("localizes permission-path and method-style audit actions", () => {
    expect(segmentHistoryAction("platform.account_segments.update_draft")).toBe("Brouillon modifié");
    expect(segmentHistoryAction("reassign-owner")).toBe("Responsable réassigné");
  });
});
