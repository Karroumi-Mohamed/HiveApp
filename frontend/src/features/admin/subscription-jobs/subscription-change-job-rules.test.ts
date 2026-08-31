import { describe, expect, test } from "bun:test";
import {
  changeJobCanBeCancelled,
  changeJobCanBeRetried,
  changeJobProcessedCount,
  subscriptionChangeJobReasonError,
} from "./subscription-change-job-rules";

describe("subscription change job rules", () => {
  test("only unstarted jobs can be cancelled", () => {
    expect(changeJobCanBeCancelled({ status: "PREVIEWED" })).toBe(true);
    expect(changeJobCanBeCancelled({ status: "SCHEDULED" })).toBe(true);
    expect(changeJobCanBeCancelled({ status: "RUNNING" })).toBe(false);
    expect(changeJobCanBeCancelled({ status: "COMPLETED" })).toBe(false);
  });

  test("retry requires a completed error and at least one retryable result", () => {
    expect(changeJobCanBeRetried({ status: "COMPLETED_WITH_ERRORS", failedCount: 1, conflictCount: 0 })).toBe(true);
    expect(changeJobCanBeRetried({ status: "COMPLETED_WITH_ERRORS", failedCount: 0, conflictCount: 0 })).toBe(false);
    expect(changeJobCanBeRetried({ status: "RUNNING", failedCount: 1, conflictCount: 0 })).toBe(false);
  });

  test("processed count excludes ready items", () => {
    expect(
      changeJobProcessedCount({
        appliedCount: 2,
        pendingCount: 3,
        awaitingPaymentCount: 1,
        conflictCount: 1,
        failedCount: 2,
        cancelledCount: 1,
      }),
    ).toBe(10);
  });

  test("requires a bounded operator reason", () => {
    expect(subscriptionChangeJobReasonError("   ")).toBeTruthy();
    expect(subscriptionChangeJobReasonError("Contrat annuel signé")).toBeNull();
    expect(subscriptionChangeJobReasonError("x".repeat(2001))).toBeTruthy();
  });
});
