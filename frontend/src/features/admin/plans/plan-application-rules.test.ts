import { describe, expect, test } from "bun:test";
import type { ContentDetail, ContentRequest, EffectiveQuotaLimit } from "@/api/plan-application-api";
import { canConfirmContent, changedContentLimits, contentPolling, validContentRequest } from "./plan-application-rules";

const request: ContentRequest = {
  sourcePlanId: "v1",
  scope: "FAMILY",
  audience: "ALL",
  accountIds: [],
  excludedAccountIds: [],
  statuses: ["ACTIVE"],
  search: null,
  currency: null,
  billingCycle: null,
  application: { timing: "NOW", notBefore: null, reason: "Reviewed" },
  notificationPolicy: "IN_APP",
};
const detail: ContentDetail = {
  summary: {
    id: "job",
    familyId: "family",
    targetPlanId: "v2",
    status: "PREVIEWED",
    counts: { READY: 2 },
    createdAt: "",
    evaluatedAt: "",
    executeAt: null,
    completedAt: null,
    requestedByUserId: "actor",
    reason: "Reason",
    version: 0,
  },
  definition: { targetPlanId: "v2", request },
  conflicts: [],
  reviewInvalidated: false,
  previewToken: "token",
  expiresAt: "2030-01-01T00:00:00Z",
};

describe("content version review", () => {
  test("confirmation requires an unexpired actor-bound review with ready Accounts", () => {
    const now = Date.parse("2026-01-01T00:00:00Z");
    expect(canConfirmContent(detail, false, now)).toBeTrue();
    expect(canConfirmContent({ ...detail, previewToken: null }, true, now)).toBeFalse();
    expect(canConfirmContent({ ...detail, reviewInvalidated: true }, true, now)).toBeFalse();
    expect(canConfirmContent(detail, false, Date.parse(detail.expiresAt as string))).toBeFalse();
    expect(
      canConfirmContent({ ...detail, summary: { ...detail.summary, counts: { UNCHANGED: 2 } } }, true, now),
    ).toBeFalse();
  });
  test("partial application always needs explicit acknowledgement", () => {
    const mixed = { ...detail, summary: { ...detail.summary, counts: { READY: 1, CONFLICT: 1 } } };
    expect(canConfirmContent(mixed, false, 0)).toBeFalse();
    expect(canConfirmContent(mixed, true, 0)).toBeTrue();
  });
  test("selection, currency and reason must be valid before freeze", () => {
    expect(validContentRequest(request)).toBeTrue();
    expect(validContentRequest({ ...request, audience: "SELECTED" })).toBeFalse();
    expect(validContentRequest({ ...request, currency: "MA" })).toBeFalse();
    expect(validContentRequest({ ...request, application: { ...request.application, reason: " " } })).toBeFalse();
  });
  test("future dates are explicit and trial subscriptions cannot wait for paid renewal", () => {
    expect(
      validContentRequest({ ...request, application: { ...request.application, timing: "AT_DATE", notBefore: null } }),
    ).toBeFalse();
    expect(
      validContentRequest(
        { ...request, application: { ...request.application, timing: "AT_DATE", notBefore: "2030-01-01T00:00:00Z" } },
        0,
      ),
    ).toBeTrue();
    expect(
      validContentRequest({
        ...request,
        statuses: ["TRIALING"],
        application: { ...request.application, timing: "AT_RENEWAL" },
      }),
    ).toBeFalse();
  });
  test("only active background work is polled", () => {
    expect(contentPolling("ASSESSING")).toBe(5000);
    expect(contentPolling("RUNNING")).toBe(5000);
    expect(contentPolling("PREVIEWED")).toBeFalse();
    expect(contentPolling("COMPLETED")).toBeFalse();
  });
  test("quota comparison distinguishes removal, unlimited, zero and unchanged limits", () => {
    const quota: EffectiveQuotaLimit = {
      featureCode: "staff",
      resource: "members",
      mode: "FINITE",
      includedLimit: 5,
      purchasedCapacity: 0,
      effectiveLimit: 5,
    };
    expect(changedContentLimits([quota], [quota])).toHaveLength(0);
    expect(changedContentLimits([quota], [])).toHaveLength(1);
    expect(changedContentLimits([quota], [{ ...quota, effectiveLimit: 0 }])[0]?.after?.effectiveLimit).toBe(0);
    expect(changedContentLimits([quota], [{ ...quota, mode: "UNLIMITED", effectiveLimit: null }])[0]?.after?.mode).toBe(
      "UNLIMITED",
    );
  });
});
