import { describe, expect, test } from "bun:test";
import {
  billingCycleLabel,
  billingLineTypeLabel,
  billingOperationLabel,
  invoiceStatusPresentation,
  outboxStatusPresentation,
  providerEventStatusPresentation,
  providerPaymentStatusPresentation,
} from "./billing-presentation";

describe("billing presentation", () => {
  test("covers every backend invoice and reconciliation state", () => {
    expect(Object.keys(invoiceStatusPresentation).sort()).toEqual(["CANCELLED", "OPEN", "SETTLED", "SETTLED_ZERO"]);
    expect(Object.keys(outboxStatusPresentation).sort()).toEqual([
      "CANCELLED",
      "FAILED",
      "PENDING",
      "PROCESSED",
      "PROCESSING",
    ]);
    expect(Object.keys(providerEventStatusPresentation).sort()).toEqual([
      "APPLIED",
      "MISMATCHED",
      "RECEIVED",
      "UNMATCHED",
    ]);
  });

  test("uses operator vocabulary instead of transport enum names", () => {
    expect(billingOperationLabel.CHARGE).toBe("Encaissement");
    expect(billingCycleLabel.YEARLY).toBe("Annuel");
    expect(billingLineTypeLabel.QUOTA_PACKAGE).toBe("Pack de capacité");
    expect(providerPaymentStatusPresentation.SUCCESS.label).toBe("Confirmé");
  });
});
