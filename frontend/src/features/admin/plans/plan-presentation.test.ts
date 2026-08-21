import { describe, expect, test } from "bun:test";
import { addOnAvailabilityLabel } from "./plan-presentation";

describe("plan feature add-on availability", () => {
  test("names the single add-on that provides the feature", () => {
    expect(addOnAvailabilityLabel(["Custom Roles"])).toBe("Via Custom Roles");
  });

  test("counts only the additional add-ons after the named one", () => {
    expect(addOnAvailabilityLabel(["Custom Roles", "Agency Tools", "Advanced Access"])).toBe("Via Custom Roles +2");
  });

  test("identifies an unfinished optional-add-on configuration", () => {
    expect(addOnAvailabilityLabel([])).toBe("Aucun add-on associé");
  });
});
