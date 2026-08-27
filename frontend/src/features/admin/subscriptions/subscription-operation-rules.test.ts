import { describe, expect, test } from "bun:test";
import { ApiError } from "@/api/http";
import {
  adminChangeNeedsFreshReview,
  normalizedOperatorReason,
  operatorReasonError,
  operatorSubscriptionMutationFailureMessage,
  subscriptionOperationCanBeCancelled,
} from "./subscription-operation-rules";

describe("operator subscription operations", () => {
  test("requires an auditable bounded reason and sends its normalized form", () => {
    expect(operatorReasonError("   ")).toBe("Saisissez la justification de cette opération.");
    expect(operatorReasonError("x".repeat(2001))).toBe("La justification ne peut pas dépasser 2 000 caractères.");
    expect(operatorReasonError("  Contrat approuvé  ")).toBeNull();
    expect(normalizedOperatorReason("  Contrat approuvé  ")).toBe("Contrat approuvé");
  });

  test("offers cancellation only while an operation is outstanding", () => {
    expect(subscriptionOperationCanBeCancelled({ status: "PENDING" })).toBeTrue();
    expect(subscriptionOperationCanBeCancelled({ status: "AWAITING_CONFIRMATION" })).toBeTrue();
    expect(subscriptionOperationCanBeCancelled({ status: "APPLIED" })).toBeFalse();
    expect(subscriptionOperationCanBeCancelled({ status: "CANCELLED" })).toBeFalse();
  });

  test("invalidates signed review evidence by stable code, never message text", () => {
    expect(adminChangeNeedsFreshReview(new ApiError(409, "STALE_RESOURCE_VERSION", "anything"))).toBeTrue();
    expect(adminChangeNeedsFreshReview(new ApiError(409, "INVALID_STATE", "STALE_RESOURCE_VERSION"))).toBeFalse();
  });

  test("maps operational failures by stable code without showing backend prose", () => {
    expect(
      operatorSubscriptionMutationFailureMessage(
        new ApiError(400, "VALIDATION_FAILED", "internal validation details"),
        "Échec",
      ),
    ).toContain("Corrigez");
    expect(operatorSubscriptionMutationFailureMessage(new ApiError(500, "HTTP_ERROR", "stack"), "Échec")).toBe("Échec");
  });
});
