import { reactive, ref } from "vue";
import { apiRequest, configureHttpAuth, jsonBody } from "@/api/http";
import { adminApi } from "@/api/admin-api";
import type { AdminMe, AuthResponse } from "@/api/contracts";
const tokenKey = "hive-v2-admin-token",
  refreshKey = "hive-v2-admin-refresh";
export const session = reactive({
  token: sessionStorage.getItem(tokenKey),
  refreshToken: sessionStorage.getItem(refreshKey),
  initialAccessToken: sessionStorage.getItem("hive-v2-initial-access"),
  me: null as AdminMe | null,
});
export const revision = ref(0);
export const invalidate = () => revision.value++;
function clear() {
  session.token = null;
  session.refreshToken = null;
  session.me = null;
  sessionStorage.removeItem(tokenKey);
  sessionStorage.removeItem(refreshKey);
  invalidate();
}
function store(auth: AuthResponse) {
  session.token = auth.accessToken;
  session.refreshToken = auth.refreshToken;
  sessionStorage.setItem(tokenKey, auth.accessToken);
  if (auth.refreshToken) sessionStorage.setItem(refreshKey, auth.refreshToken);
  else sessionStorage.removeItem(refreshKey);
}
let refreshing: Promise<boolean> | undefined;
async function refreshSession() {
  if (!session.refreshToken) return false;
  if (!refreshing)
    refreshing = (async () => {
      try {
        store(
          await apiRequest<AuthResponse>("/api/admin/auth/refresh", {
            method: "POST",
            body: jsonBody({ refreshToken: session.refreshToken }),
          }),
        );
        return true;
      } catch {
        return false;
      } finally {
        refreshing = undefined;
      }
    })();
  return refreshing;
}
configureHttpAuth(
  () => session.token,
  () => {
    clear();
    window.dispatchEvent(new Event("hive-session-expired"));
  },
  refreshSession,
);
export function can(...permissions: string[]) {
  const requested = permissions.filter(Boolean);
  if (!requested.length) return false;
  return (
    !!session.me?.isSuperAdmin ||
    requested.some((permission) => session.me?.permissions.includes(permission))
  );
}
export async function acceptSession(auth: AuthResponse) {
  store(auth);
  try {
    session.me = await adminApi.me();
  } catch (error) {
    clear();
    throw error;
  }
  session.initialAccessToken = null;
  sessionStorage.removeItem("hive-v2-initial-access");
  invalidate();
}
export async function signIn(identifier: string, password: string) {
  const auth = await apiRequest<AuthResponse>("/api/admin/auth/login", {
    method: "POST",
    body: jsonBody({ identifier, password }),
  });
  if (auth.passwordChangeRequired) {
    clear();
    session.initialAccessToken = auth.accessToken;
    sessionStorage.setItem("hive-v2-initial-access", auth.accessToken);
    return false;
  }
  await acceptSession(auth);
  return true;
}
export async function restoreSession() {
  if (session.token)
    try {
      session.me = await adminApi.me();
    } catch {
      clear();
    }
}
export async function signOut() {
  const token = session.refreshToken,
    initial = session.initialAccessToken;
  clear();
  session.initialAccessToken = null;
  sessionStorage.removeItem("hive-v2-initial-access");
  if (initial)
    await adminApi.logoutInitialAccess(initial).catch(() => undefined);
  if (token)
    await apiRequest<void>("/api/admin/auth/logout", {
      method: "POST",
      body: jsonBody({ refreshToken: token }),
    }).catch(() => undefined);
}
export const toast = reactive({
  message: "",
  tone: "success" as "success" | "error",
});
let toastTimer: ReturnType<typeof setTimeout>;
export function notify(message: string, tone: "success" | "error" = "success") {
  toast.message = message;
  toast.tone = tone;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (toast.message = ""), 6500);
}
