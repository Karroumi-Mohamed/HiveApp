import { describe, expect, test } from "bun:test";
import type { CommunicationDraft } from "@/api/communication-api";
import { communicationDraftValid, localDateTime } from "./communication-rules";

const draft: CommunicationDraft = {
  kind: "NOTICE",
  purpose: "SERVICE",
  messageTitle: "Update",
  messageBody: "Details",
  accountIds: ["account-1"],
  email: false,
  replies: false,
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
  test("warnings cannot be marketing and only messages can allow replies", () => {
    expect(communicationDraftValid({ ...draft, kind: "WARNING", purpose: "MARKETING" })).toBe(false);
    expect(communicationDraftValid({ ...draft, replies: true })).toBe(false);
    expect(communicationDraftValid({ ...draft, kind: "MESSAGE", replies: true })).toBe(true);
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
