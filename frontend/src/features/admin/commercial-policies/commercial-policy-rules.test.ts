import { describe, expect, test } from "bun:test";
import type { CommercialPolicyActivationPreview, CommercialPolicyDetail } from "@/api/contracts";
import {
  emptyCommercialPolicyDraft,
  newDraftEffect,
  reviewedActivationReady,
  toCommercialPolicyWriteInput,
  validateCommercialPolicyDraft,
  validateDraftEffect,
} from "./commercial-policy-rules";

describe("commercial policy typed editor", () => {
  test("prunes fields that do not belong to the selected effect contract", () => {
    const draft = emptyCommercialPolicyDraft();
    draft.name = "Remise contrat";
    draft.reason = "Contrat C-42";
    draft.accountId = "account-1";
    draft.effects = [
      {
        ...newDraftEffect("FIXED_DISCOUNT"),
        amount: "12.5000",
        currencyCode: "mad",
        productType: "PLAN",
        productRevisionId: "must-be-pruned",
        percentage: "50",
      },
    ];
    expect(toCommercialPolicyWriteInput(draft).effects[0]).toEqual({
      type: "FIXED_DISCOUNT",
      productType: null,
      productRevisionId: null,
      featureCode: null,
      quotaResource: null,
      quantityDelta: null,
      amount: "12.5000",
      currencyCode: "MAD",
      billingCycle: null,
      percentage: null,
      maximumAmount: null,
      maximumCurrencyCode: null,
    });
  });

  test("free product grants require an explicit end date", () => {
    const effect = { ...newDraftEffect("GRANT_ADD_ON"), productRevisionId: "add-on-1" };
    expect(validateDraftEffect(effect, false)).toContain("date de fin");
    expect(validateDraftEffect(effect, true)).toBeNull();
  });

  test("a percentage discount requires a bounded monetary maximum", () => {
    const effect = { ...newDraftEffect("PERCENTAGE_DISCOUNT"), percentage: "20" };
    expect(validateDraftEffect(effect, true)).not.toBeNull();
    effect.maximumAmount = "100";
    expect(validateDraftEffect(effect, true)).toBeNull();
    effect.percentage = "1.00000";
    expect(validateDraftEffect(effect, true)).not.toBeNull();
  });

  test("target validation is kind-specific and segment drafts remain explicit", () => {
    const draft = emptyCommercialPolicyDraft();
    draft.name = "Segment à préparer";
    draft.reason = "Campagne";
    draft.targetKind = "SEGMENT";
    draft.effects = [{ ...newDraftEffect("FIXED_DISCOUNT"), amount: "10" }];
    expect(validateCommercialPolicyDraft(draft).target).toContain("segment");
    draft.segmentReference = "LOYAL_CUSTOMERS";
    expect(validateCommercialPolicyDraft(draft).target).toBeUndefined();
  });

  test("one policy cannot silently mix monetary currencies", () => {
    const draft = emptyCommercialPolicyDraft();
    draft.name = "Remise encadrée";
    draft.reason = "Contrat négocié";
    draft.accountId = "account-1";
    draft.effects = [
      { ...newDraftEffect("FIXED_DISCOUNT"), amount: "10", currencyCode: "MAD" },
      {
        ...newDraftEffect("PERCENTAGE_DISCOUNT"),
        percentage: "5",
        maximumAmount: "100",
        maximumCurrencyCode: "EUR",
      },
    ];
    expect(validateCommercialPolicyDraft(draft).effects).toContain("même devise");
  });
});

describe("commercial policy signed activation", () => {
  const policy = {
    summary: { id: "policy-1", version: 4 },
  } as CommercialPolicyDetail;
  const preview = {
    policyId: "policy-1",
    expectedVersion: 4,
    activatable: true,
    blockers: [],
    expiresAt: new Date(Date.now() + 60_000).toISOString(),
  } as unknown as CommercialPolicyActivationPreview;

  test("accepts only current unexpired evidence for the exact version", () => {
    expect(reviewedActivationReady(policy, preview)).toBeTrue();
    expect(reviewedActivationReady(policy, { ...preview, expectedVersion: 3 })).toBeFalse();
    expect(reviewedActivationReady(policy, { ...preview, policyId: "other" })).toBeFalse();
    expect(reviewedActivationReady(policy, { ...preview, expiresAt: new Date(0).toISOString() })).toBeFalse();
    expect(reviewedActivationReady(policy, { ...preview, blockers: ["INVALID_EFFECT"] })).toBeFalse();
  });
});
