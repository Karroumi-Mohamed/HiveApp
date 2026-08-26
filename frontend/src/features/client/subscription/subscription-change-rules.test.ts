import { describe, expect, test } from "bun:test";
import { ApiError } from "@/api/http";
import { subscriptionChangeFailureMessage } from "./subscription-change-rules";

describe("subscription price failure feedback", () => {
  test("turns a stale exact-price conflict into a recoverable instruction", () => {
    const error = new ApiError(409, "STALE_RESOURCE_VERSION", "Ce tarif a changé.");
    expect(subscriptionChangeFailureMessage(error)).toContain("prévisualisez à nouveau");
  });

  test("keeps a backend validation message visible", () => {
    const error = new ApiError(400, "INVALID_REQUEST", "Cette devise n’est plus proposée.");
    expect(subscriptionChangeFailureMessage(error)).toBe("Cette devise n’est plus proposée.");
  });
});
