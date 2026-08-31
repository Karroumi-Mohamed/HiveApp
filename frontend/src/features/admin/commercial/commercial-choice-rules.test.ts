import { describe, expect, test } from "bun:test";
import {
  commercialChoiceDescription,
  isCommercialChoiceDisabled,
  mergeCommercialChoices,
} from "./commercial-choice-rules";

const choice = (code: string, choiceState: "SELECTABLE" | "NO_LONGER_ACTIVE" | "MISSING", revisionNumber = 1) => ({
  code,
  choiceState,
  revisionNumber,
});

describe("commercial choice rules", () => {
  test("hydrates selected values outside the current bounded page without duplicating them", () => {
    expect(
      mergeCommercialChoices(
        [choice("VISIBLE", "SELECTABLE"), choice("SAME", "SELECTABLE")],
        [choice("MISSING", "MISSING"), choice("SAME", "NO_LONGER_ACTIVE")],
      ),
    ).toEqual([choice("VISIBLE", "SELECTABLE"), choice("SAME", "NO_LONGER_ACTIVE"), choice("MISSING", "MISSING")]);
  });

  test("retained and missing values are removable but not newly selectable", () => {
    expect(isCommercialChoiceDisabled(choice("OLD", "NO_LONGER_ACTIVE"), true)).toBe(false);
    expect(isCommercialChoiceDisabled(choice("OLD", "NO_LONGER_ACTIVE"), false)).toBe(true);
    expect(isCommercialChoiceDisabled(choice("GONE", "MISSING"), true)).toBe(false);
    expect(isCommercialChoiceDisabled(choice("GONE", "MISSING"), false)).toBe(true);
  });

  test("explains missing and inactive values without exposing a technical code", () => {
    expect(commercialChoiceDescription(choice("OLD", "NO_LONGER_ACTIVE"))).toBe(
      "Sélection conservée — plus disponible",
    );
    expect(commercialChoiceDescription(choice("GONE", "MISSING"))).toBe(
      "Référence absente — peut seulement être retirée",
    );
  });
});
