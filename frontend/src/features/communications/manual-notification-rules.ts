import type { AuthoredNotification, NotificationContent, NotificationLanguage } from "@/api/communication-api";

const contentValid = (value: NotificationContent) =>
  !!value.messageTitle.trim() &&
  value.messageTitle.length <= 160 &&
  !!value.messageBody.trim() &&
  value.messageBody.length <= 10000;
export function manualNotificationValid(value: AuthoredNotification) {
  const original = value.originalLanguage ?? "fr",
    entries = Object.entries(value.translations ?? {});
  return (
    contentValid(value) &&
    ["fr", "ar"].includes(original) &&
    entries.length <= 1 &&
    entries.every(
      ([language, content]) =>
        language !== original && ["fr", "ar"].includes(language) && !!content && contentValid(content),
    )
  );
}
/** Switching the original language preserves both authored versions. */
export function changeOriginalLanguage(value: AuthoredNotification, next: NotificationLanguage): AuthoredNotification {
  const previous = value.originalLanguage ?? "fr",
    alternative = value.translations?.[next];
  if (next === previous) return value;
  if (!alternative) return { ...value, originalLanguage: next };
  return {
    ...alternative,
    originalLanguage: next,
    translations: { [previous]: { messageTitle: value.messageTitle, messageBody: value.messageBody } },
  };
}
