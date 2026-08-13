import { describe, expect, test } from "bun:test";
import { getLanguageDirection, normalizeLanguage } from "./i18n";

describe("locale boundaries", () => {
  test("French is the safe default", () => {
    expect(normalizeLanguage()).toBe("fr");
    expect(normalizeLanguage("en-US")).toBe("fr");
    expect(getLanguageDirection("fr-MA")).toBe("ltr");
  });

  test("Arabic activates RTL", () => {
    expect(normalizeLanguage("ar-MA")).toBe("ar");
    expect(getLanguageDirection("ar")).toBe("rtl");
  });
});
