import { expect, test } from "bun:test";
import type { AuthoredNotification } from "@/api/communication-api";
import { changeOriginalLanguage, manualNotificationValid } from "./manual-notification-rules";

const original: AuthoredNotification = { messageTitle: "Bonjour", messageBody: "Original", originalLanguage: "fr" };
test("original-only and complete optional translation are valid; incomplete translation is not", () => {
  expect(manualNotificationValid(original)).toBe(true);
  expect(
    manualNotificationValid({ ...original, translations: { ar: { messageTitle: "مرحبا", messageBody: "النص" } } }),
  ).toBe(true);
  expect(
    manualNotificationValid({ ...original, translations: { ar: { messageTitle: "مرحبا", messageBody: " " } } }),
  ).toBe(false);
  expect(manualNotificationValid({ ...original, translations: { fr: { messageTitle: "x", messageBody: "y" } } })).toBe(
    false,
  );
  expect(
    manualNotificationValid({ ...original, translations: { ar: { messageTitle: "x".repeat(161), messageBody: "y" } } }),
  ).toBe(false);
});
test("switching original language swaps authored content without losing either version", () => {
  const bilingual = { ...original, translations: { ar: { messageTitle: "مرحبا", messageBody: "النص" } } };
  const swapped = changeOriginalLanguage(bilingual, "ar");
  expect(swapped).toEqual({
    originalLanguage: "ar",
    messageTitle: "مرحبا",
    messageBody: "النص",
    translations: { fr: { messageTitle: "Bonjour", messageBody: "Original" } },
  });
  expect(changeOriginalLanguage(swapped, "fr")).toEqual(bilingual);
  expect(changeOriginalLanguage(original, "ar")).toEqual({ ...original, originalLanguage: "ar" });
});
