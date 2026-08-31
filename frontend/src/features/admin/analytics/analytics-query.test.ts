import { describe, expect, test } from "bun:test";
import { analyticsQuery, analyticsSearchChanges, timeSeriesQuery } from "./analytics-query";

const anchor = new Date("2026-08-31T12:00:00.000Z");

describe("analytics query rules", () => {
  test("anchors a preset range and normalizes commercial dimensions", () => {
    expect(
      analyticsQuery(
        {
          range: "30",
          timezone: "Africa/Casablanca",
          interval: "DAY",
          currencyCode: " mad ",
          billingCycle: "MONTHLY",
        },
        anchor,
      ),
    ).toEqual({
      from: "2026-08-01T12:00:00.000Z",
      until: "2026-08-31T12:00:00.000Z",
      timezone: "Africa/Casablanca",
      interval: "DAY",
      currencyCode: "MAD",
      billingCycle: "MONTHLY",
    });
  });

  test("ignores malformed custom dates instead of crashing render", () => {
    expect(
      analyticsQuery(
        {
          range: "custom",
          from: "not-a-date",
          until: "2026-08-31T12:00",
          timezone: "UTC",
          interval: "WEEK",
        },
        anchor,
      ),
    ).toEqual({
      until: "2026-08-31T12:00:00.000Z",
      timezone: "UTC",
      interval: "WEEK",
      currencyCode: undefined,
      billingCycle: undefined,
    });
  });

  test("a single filter edit never clears unrelated URL state", () => {
    expect(analyticsSearchChanges({ interval: "MONTH" })).toEqual({ interval: "MONTH" });
    expect(analyticsSearchChanges({ billingCycle: undefined })).toEqual({ cycle: null });
  });

  test("historical series never receive money-only dimensions", () => {
    expect(
      timeSeriesQuery({
        from: "2026-08-01T00:00:00Z",
        until: "2026-09-01T00:00:00Z",
        timezone: "UTC",
        interval: "DAY",
        currencyCode: "MAD",
        billingCycle: "MONTHLY",
      }),
    ).toEqual({
      from: "2026-08-01T00:00:00Z",
      until: "2026-09-01T00:00:00Z",
      timezone: "UTC",
      interval: "DAY",
    });
  });
});
