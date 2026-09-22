import { describe, expect, test } from "bun:test";
import type { CommunicationDraft, CommunicationItem } from "@/api/communication-api";
import { communicationDraftValid, localDateTime, notificationAction } from "./communication-rules";

const draft: CommunicationDraft = {
  kind: "NOTICE",
  purpose: "SERVICE",
  messageTitle: "Update",
  messageBody: "Details",
  accountIds: ["account-1"],
  email: false,
  offerId: null,
  availableAt: null,
  expiresAt: null,
};
describe("communication review", () => {
  test("requires readable content and a bounded unique audience", () => {
    expect(communicationDraftValid(draft)).toBe(true);
    for (const patch of [
      { messageTitle: " " },
      { messageBody: " " },
      { accountIds: [] },
      { accountIds: ["a", "a"] },
      { accountIds: Array.from({ length: 501 }, (_, i) => String(i)) },
    ])
      expect(communicationDraftValid({ ...draft, ...patch })).toBe(false);
  });
  test("warnings cannot be marketing and actionable notifications cannot be fabricated manually", () => {
    expect(communicationDraftValid({ ...draft, kind: "WARNING", purpose: "MARKETING" })).toBe(false);
    expect(communicationDraftValid({ ...draft, kind: "ACTION" })).toBe(false);
    expect(communicationDraftValid({ ...draft, kind: "OFFER", purpose: "MARKETING" })).toBe(false);
    expect(communicationDraftValid({ ...draft, kind: "OFFER", purpose: "MARKETING", offerId: "offer-1" })).toBe(true);
    expect(
      communicationDraftValid({
        ...draft,
        kind: "OFFER",
        purpose: "MARKETING",
        offerId: "offer-1",
        accountIds: Array.from({ length: 101 }, (_, i) => String(i)),
      }),
    ).toBe(false);
  });
  test("source actions stay in the correct application and use contextual labels", () => {
    const item = {
      actionPath: "/app/offers/123",
      kind: "OFFER",
      topic: "COMMERCIAL",
      source: "ADMIN",
    } as CommunicationItem;
    expect(notificationAction(item)).toEqual({ path: "/app/offers/123", label: "offerAction" });
    expect(notificationAction(item, true)).toBeNull();
    for (const sourceState of ["CANCELLED", "WITHDRAWN", "RESOLVED", "UNAVAILABLE"])
      expect(notificationAction({ ...item, sourceState })).toBeNull();
    for (const path of ["https://evil.invalid", "//evil.invalid", "/app/\\evil", "/admin/invoices/1"])
      expect(notificationAction({ ...item, actionPath: path })).toBeNull();
  });
  test("expiry must follow both availability and now", () => {
    const now = Date.parse("2026-09-22T00:00:00Z");
    expect(
      communicationDraftValid(
        { ...draft, availableAt: "2026-09-23T00:00:00Z", expiresAt: "2026-09-22T23:00:00Z" },
        now,
      ),
    ).toBe(false);
    expect(communicationDraftValid({ ...draft, expiresAt: "2026-09-21T00:00:00Z" }, now)).toBe(false);
    expect(communicationDraftValid({ ...draft, expiresAt: "2026-09-24T00:00:00Z" }, now)).toBe(true);
  });
  test("local datetime formatting retains the local instant", () => {
    const value = "2026-09-22T08:30:00Z";
    expect(new Date(localDateTime(value)).getTime()).toBe(Date.parse(value));
    expect(localDateTime(null)).toBe("");
    expect(localDateTime("invalid")).toBe("");
  });
});
