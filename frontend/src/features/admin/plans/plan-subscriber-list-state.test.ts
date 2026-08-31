import { describe, expect, test } from "bun:test";
import { readPlanSubscriberListState, writePlanSubscriberListState } from "./plan-subscriber-list-state";

describe("plan subscriber list state", () => {
  test("rejects unsupported statuses and invalid pages", () => {
    expect(readPlanSubscriberListState(new URLSearchParams("subscriberStatus=DELETED&subscriberPage=-3"))).toEqual({
      search: "",
      status: "all",
      page: 0,
    });
  });

  test("round-trips filters while preserving the detail tab", () => {
    const params = writePlanSubscriberListState(new URLSearchParams("tab=subscribers"), {
      search: "Acme",
      status: "PAST_DUE",
      page: 2,
    });
    expect(params.get("tab")).toBe("subscribers");
    expect(readPlanSubscriberListState(params)).toEqual({ search: "Acme", status: "PAST_DUE", page: 2 });
  });

  test("removes default values from the URL", () => {
    const params = writePlanSubscriberListState(
      new URLSearchParams("subscriberQuery=old&subscriberStatus=ACTIVE&subscriberPage=3"),
      { search: "", status: "all", page: 0 },
    );
    expect(params.toString()).toBe("");
  });
});
