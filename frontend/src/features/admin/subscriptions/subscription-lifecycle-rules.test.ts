import { describe, expect, test } from "bun:test";
import type { SubscriptionLifecyclePreview } from "@/api/contracts";
import { lifecyclePreviewIsReady, lifecycleStatusTransition } from "./subscription-lifecycle-rules";

const preview: SubscriptionLifecyclePreview = {
  subscriptionId: "subscription-1",
  expectedVersion: 4,
  action: "EXTEND_GRACE",
  beforeStatus: "PAST_DUE",
  afterStatus: "PAST_DUE",
  effectiveAt: "2026-08-31T12:00:00Z",
  previousGraceEndsAt: "2026-09-01T12:00:00Z",
  nextGraceEndsAt: "2026-09-05T12:00:00Z",
  blockers: [],
  evaluatedAt: "2026-08-31T12:00:00Z",
  expiresAt: "2026-08-31T12:05:00Z",
  previewToken: "signed-review",
};

describe("subscription lifecycle review", () => {
  test("requires live evidence for the exact action and grace deadline", () => {
    const now = new Date("2026-08-31T12:01:00Z");
    expect(lifecyclePreviewIsReady(preview, "EXTEND_GRACE", preview.nextGraceEndsAt, now)).toBeTrue();
    expect(lifecyclePreviewIsReady(preview, "EXTEND_GRACE", "2026-09-06T12:00:00Z", now)).toBeFalse();
    expect(lifecyclePreviewIsReady(preview, "SUSPEND", preview.nextGraceEndsAt, now)).toBeFalse();
    expect(
      lifecyclePreviewIsReady(
        { ...preview, expiresAt: "2026-08-31T12:00:30Z" },
        "EXTEND_GRACE",
        preview.nextGraceEndsAt,
        now,
      ),
    ).toBeFalse();
    expect(
      lifecyclePreviewIsReady({ ...preview, blockers: ["blocked"] }, "EXTEND_GRACE", preview.nextGraceEndsAt, now),
    ).toBeFalse();
  });

  test("does not invent a transition for renewal flags", () => {
    expect(lifecycleStatusTransition("ACTIVE", "ACTIVE")).toBeNull();
    expect(lifecycleStatusTransition("ACTIVE", "SUSPENDED")).toEqual({ before: "ACTIVE", after: "SUSPENDED" });
  });
});
