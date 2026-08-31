import { describe, expect, test } from "bun:test";
import { operatorEmailChanged, roleSelectionChanges } from "./admin-operator-rules";

describe("operator email editing", () => {
  test("case and surrounding whitespace do not manufacture an email change", () => {
    expect(operatorEmailChanged("operator@hiveapp.test", "  OPERATOR@HIVEAPP.TEST ")).toBeFalse();
  });

  test("a genuinely different address requires verification", () => {
    expect(operatorEmailChanged("old@hiveapp.test", "new@hiveapp.test")).toBeTrue();
  });
});

describe("operator role editing", () => {
  test("computes additions and removals as unordered sets", () => {
    expect(roleSelectionChanges(["a", "b"], ["b", "c"])).toEqual({ added: ["c"], removed: ["a"] });
  });

  test("reordering roles is not a change", () => {
    expect(roleSelectionChanges(["a", "b"], ["b", "a"])).toEqual({ added: [], removed: [] });
  });
});
