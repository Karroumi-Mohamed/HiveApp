import { describe, expect, test } from "bun:test";
import {
  subscriptionChangeRecordedMessage,
  subscriptionChangeStatusPresentation,
  subscriptionStatusPresentation,
} from "./subscription-presentation";

describe("subscription presentation", () => {
  test("presents every subscription state in French", () => {
    expect(Object.keys(subscriptionStatusPresentation).sort()).toEqual([
      "ACTIVE",
      "CANCELLED",
      "EXPIRED",
      "PAST_DUE",
      "SUSPENDED",
      "TRIALING",
    ]);
    expect(subscriptionStatusPresentation.PAST_DUE).toEqual({ label: "Impayé", tone: "danger" });
  });

  test("distinguishes scheduled and payment-waiting operations", () => {
    expect(subscriptionChangeStatusPresentation.PENDING.label).toBe("Planifié");
    expect(subscriptionChangeStatusPresentation.AWAITING_CONFIRMATION.label).toBe("Paiement à confirmer");
    expect(subscriptionChangeStatusPresentation.NEEDS_ATTENTION.tone).toBe("danger");
    expect(subscriptionChangeRecordedMessage({ status: "AWAITING_CONFIRMATION" })).toContain("paiement");
    expect(subscriptionChangeRecordedMessage({ status: "PENDING" })).toBe("Changement planifié");
  });
});
