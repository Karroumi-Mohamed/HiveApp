import { type ApiAudience, configureHttpAuth } from "@/api/http";

export type StoredSession = {
  accessToken: string;
  refreshToken: string | null;
  expiresAt: number;
  passwordChangeRequired: boolean;
};
type SessionAudience = Exclude<ApiAudience, "public">;

const keys: Record<SessionAudience, string> = {
  admin: "hiveapp-admin-session",
  client: "hiveapp-client-session",
};

const listeners = new Set<() => void>();
const cache: Partial<Record<SessionAudience, StoredSession | null>> = {};

/**
 * Clears one audience's cached query data. Registered by the provider that owns the query
 * client, so this module can drop stale data without importing it.
 */
type AudienceCacheReset = (audience: SessionAudience) => void;
let resetAudienceCache: AudienceCacheReset = () => undefined;

export function configureSessionCacheReset(reset: AudienceCacheReset) {
  resetAudienceCache = reset;
}

export function isSessionExpired(session: StoredSession): boolean {
  return session.expiresAt <= Date.now();
}

/**
 * An expired session reads as absent. Returning it would render an authenticated shell whose
 * every request then answers 401 — the reader sees a working portal that does nothing.
 */
export function readSession(audience: SessionAudience): StoredSession | null {
  const current = readStoredSession(audience);
  if (current && isSessionExpired(current)) return null;
  return current;
}

function readStoredSession(audience: SessionAudience): StoredSession | null {
  return audience in cache ? (cache[audience] ?? null) : parseStoredSession(audience);
}

function isSameSession(current: StoredSession | null, expected: StoredSession): boolean {
  return (
    current?.accessToken === expected.accessToken &&
    current.refreshToken === expected.refreshToken &&
    current.expiresAt === expected.expiresAt &&
    current.passwordChangeRequired === expected.passwordChangeRequired
  );
}

function parseStoredSession(audience: SessionAudience): StoredSession | null {
  const raw = window.sessionStorage.getItem(keys[audience]);
  if (!raw) {
    cache[audience] = null;
    return null;
  }
  try {
    cache[audience] = JSON.parse(raw) as StoredSession;
    return cache[audience] ?? null;
  } catch {
    window.sessionStorage.removeItem(keys[audience]);
    cache[audience] = null;
    return null;
  }
}

/**
 * The single way a session ends — logout, expiry, a failed refresh, or a 401. Each of those also
 * has to drop the audience's cached data, or the next person to sign in sees the previous one's
 * rows until every query happens to refetch.
 */
export function clearSession(audience: SessionAudience) {
  writeSession(audience, null);
  resetAudienceCache(audience);
}

/**
 * Replaces a session only while the exact session that started an asynchronous operation is
 * still current. This prevents a late refresh from reviving a logged-out session, and prevents
 * an old refresh failure or expiry timer from clearing a newer login.
 */
export function replaceSessionIfCurrent(
  audience: SessionAudience,
  expected: StoredSession,
  replacement: StoredSession | null,
): boolean {
  if (!isSameSession(readStoredSession(audience), expected)) return false;
  if (replacement) writeSession(audience, replacement);
  else clearSession(audience);
  return true;
}

export function writeSession(audience: SessionAudience, session: StoredSession | null) {
  cache[audience] = session;
  if (session) window.sessionStorage.setItem(keys[audience], JSON.stringify(session));
  else window.sessionStorage.removeItem(keys[audience]);
  for (const listener of listeners) listener();
}

export function subscribeSessions(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

configureHttpAuth(
  (audience) => readSession(audience)?.accessToken ?? null,
  (audience) => clearSession(audience),
);
