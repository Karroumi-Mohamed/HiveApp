export const number = (value: number) =>
  new Intl.NumberFormat("en-US").format(value);
export function date(value?: string | null, time = false, timezone?: string) {
  if (!value || !Number.isFinite(new Date(value).getTime())) return "—";
  const parts = new Intl.DateTimeFormat("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
    ...(timezone ? { timeZone: timezone } : {}),
    ...(time ? { hour: "2-digit", minute: "2-digit" } : {}),
  }).formatToParts(new Date(value));
  const part = (key: string) => parts.find((p) => p.type === key)?.value;
  return `${part("day")} ${part("month")} ${part("year")}${time ? `, ${part("hour")}:${part("minute")}` : ""}`;
}
export const label = (value?: string | null) =>
  value
    ? value
        .toLowerCase()
        .replaceAll("_", " ")
        .replace(/^./, (c) => c.toUpperCase())
    : "—";
export const initials = (name: string) =>
  name
    .split(/[\s.@_-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((s) => s[0])
    .join("")
    .toUpperCase();
export function money(amount: string, currency = "MAD") {
  const formatter = new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    maximumFractionDigits: 4,
  });
  return (formatter.format as unknown as (n: string) => string)(amount);
}
export function errorMessage(error: unknown) {
  if (error instanceof Error && error.name === "TimeoutError")
    return "The server took too long to respond. Try again.";
  if (error instanceof TypeError)
    return "Cannot reach the API. Check the backend connection, then try again.";
  return error instanceof Error
    ? error.message
    : "Something went wrong. Try again.";
}
