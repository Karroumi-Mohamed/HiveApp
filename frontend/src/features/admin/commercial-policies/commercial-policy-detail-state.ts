const MAX_URL_PAGE = 10_000;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function boundedPolicyPage(value: string | null) {
  if (!value || !/^\d+$/.test(value)) return 0;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 0 && parsed <= MAX_URL_PAGE ? parsed : 0;
}

export function boundedPolicyResponsePage(requested: number, totalPages: number) {
  return totalPages <= 0 ? 0 : Math.min(requested, totalPages - 1);
}

export function validPolicyIdentity(value: string | null) {
  const normalized = value?.trim() ?? "";
  return UUID_PATTERN.test(normalized) ? normalized : "";
}

export function withPolicySearchParam(current: URLSearchParams, key: string, value: string | number | null) {
  const next = new URLSearchParams(current);
  if (value === null || value === "" || value === 0) next.delete(key);
  else next.set(key, String(value));
  return next;
}
