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

export function readSession(audience: SessionAudience): StoredSession | null {
  if (audience in cache) return cache[audience] ?? null;
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
  (audience) => writeSession(audience, null),
);
