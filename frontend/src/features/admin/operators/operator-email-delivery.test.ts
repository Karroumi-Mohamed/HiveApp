import { describe, expect, test } from "bun:test";
import type { EmailDeliverySummary } from "@/api/contracts";
import { emailDeliveryFeedback } from "./operator-email-delivery";

const delivery = (status: EmailDeliverySummary["status"]): EmailDeliverySummary => ({
  deliveryId: "delivery-id",
  purpose: "ACTIVATION",
  status,
  attemptedAt: null,
  deliveredAt: null,
  failureCode: null,
  totalAttempts: 1,
  failedAttempts: status === "FAILED" ? 1 : 0,
  retryable: status === "FAILED",
});

describe("operator credential email feedback", () => {
  test("does not call a suppressed development email sent", () => {
    expect(emailDeliveryFeedback(delivery("SUPPRESSED")).tone).toBe("warning");
  });

  test("distinguishes accepted, failed and still-pending delivery", () => {
    expect(emailDeliveryFeedback(delivery("SENT")).tone).toBe("success");
    expect(emailDeliveryFeedback(delivery("FAILED")).tone).toBe("error");
    expect(emailDeliveryFeedback(delivery("PENDING")).tone).toBe("info");
    expect(emailDeliveryFeedback(null).tone).toBe("info");
  });
});
