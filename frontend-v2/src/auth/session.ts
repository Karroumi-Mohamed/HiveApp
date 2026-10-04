export type AuthResponse = {
  accessToken: string;
  refreshToken: string | null;
  tokenType: string;
  expiresIn: number;
  passwordChangeRequired: boolean;
};
export type Session = AuthResponse & { expiresAt: number };
const key = "hiveapp-react-v2:admin:session";

export function readSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(key);
    if (!raw) return null;
    const value: unknown = JSON.parse(raw);
    if (!value || typeof value !== "object") return null;
    const session = value as Session;
    if (
      typeof session.accessToken !== "string" ||
      !session.accessToken ||
      !(
        session.refreshToken === null ||
        typeof session.refreshToken === "string"
      ) ||
      typeof session.passwordChangeRequired !== "boolean" ||
      !Number.isFinite(session.expiresAt)
    )
      return null;
    if (session.passwordChangeRequired && session.expiresAt <= Date.now()) {
      clearSession();
      return null;
    }
    return session;
  } catch {
    return null;
  }
}
export function writeSession(auth: AuthResponse): Session {
  const session = { ...auth, expiresAt: Date.now() + auth.expiresIn * 1000 };
  sessionStorage.setItem(key, JSON.stringify(session));
  return session;
}
export function clearSession() {
  sessionStorage.removeItem(key);
}
export const authBase = "/api/admin/auth";
export const loginPath = "/admin/login";
export const homePath = "/admin";
