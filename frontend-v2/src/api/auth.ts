import {
  authBase,
  clearSession,
  readSession,
  writeSession,
  type AuthResponse,
} from "../auth/session";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}
const origin = import.meta.env.VITE_API_URL?.trim().replace(/\/$/, "") || "";

async function request<T>(
  path: string,
  body?: unknown,
  token?: string,
  signal?: AbortSignal,
): Promise<T> {
  const headers = new Headers({
    Accept: "application/json",
    "Accept-Language": "fr",
  });
  if (body !== undefined) headers.set("Content-Type", "application/json");
  if (token) headers.set("Authorization", `Bearer ${token}`);
  let response: Response;
  try {
    response = await fetch(`${origin}${path}`, {
      method: body === undefined ? "GET" : "POST",
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: signal
        ? AbortSignal.any([signal, AbortSignal.timeout(20_000)])
        : AbortSignal.timeout(20_000),
    });
  } catch (error) {
    if (signal?.aborted) throw error;
    throw new ApiError(
      0,
      "Connexion au serveur impossible. Réessayez dans un instant.",
    );
  }
  if (!response.ok) {
    const data = (await response.json().catch(() => null)) as {
      message?: string;
    } | null;
    throw new ApiError(
      response.status,
      data?.message || "Cette action n’a pas pu être effectuée.",
    );
  }
  return response.status === 204
    ? (undefined as T)
    : ((await response.json()) as T);
}

let refreshInFlight:
  { token: string; promise: Promise<AuthResponse> } | undefined;
async function refresh(): Promise<AuthResponse> {
  const session = readSession();
  if (!session?.refreshToken || session.passwordChangeRequired)
    throw new ApiError(401, "Votre session a expiré.");
  let pending = refreshInFlight;
  if (!pending || pending.token !== session.refreshToken) {
    const promise = request<AuthResponse>(`${authBase}/refresh`, {
      refreshToken: session.refreshToken,
    })
      .then((auth) => {
        // A late refresh must not restore a session that was signed out or replaced.
        if (readSession()?.refreshToken === session.refreshToken)
          writeSession(auth);
        return auth;
      })
      .finally(() => {
        if (refreshInFlight?.promise === promise) refreshInFlight = undefined;
      });
    const entry = { token: session.refreshToken, promise };
    refreshInFlight = entry;
    pending = entry;
  }
  return pending.promise;
}

export async function sessionIdentity(signal?: AbortSignal) {
  const current = readSession();
  if (!current || current.passwordChangeRequired)
    throw new ApiError(401, "Connectez-vous pour continuer.");
  const path = "/api/admin/me";
  try {
    return await request<{ email?: string; name?: string }>(
      path,
      undefined,
      current.accessToken,
      signal,
    );
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) throw error;
    let renewedToken: string | undefined;
    try {
      const auth = await refresh();
      renewedToken = auth.accessToken;
      if (readSession()?.accessToken !== auth.accessToken)
        throw new ApiError(401, "Votre session a expiré.");
      return await request<{ email?: string; name?: string }>(
        path,
        undefined,
        auth.accessToken,
        signal,
      );
    } catch (reason) {
      if (
        reason instanceof ApiError &&
        reason.status === 401 &&
        [current.accessToken, renewedToken].includes(readSession()?.accessToken)
      )
        clearSession();
      throw reason;
    }
  }
}

export const authApi = {
  login: (identifier: string, password: string) =>
    request<AuthResponse>(`${authBase}/login`, {
      identifier: identifier.trim(),
      password,
    }),
  requestReset: (email: string) =>
    request<void>(`${authBase}/password-reset/request`, {
      email: email.trim(),
    }),
  completePassword: (
    kind: "activation" | "password-reset",
    token: string,
    newPassword: string,
  ) => request<void>(`${authBase}/${kind}/complete`, { token, newPassword }),
  changeInitial: (token: string, newPassword: string) =>
    request<AuthResponse>(
      `${authBase}/initial-password/change`,
      { newPassword },
      token,
    ),
  verifyEmail: (token: string) =>
    request<void>("/api/admin/auth/email-verification/complete", { token }),
  async logout() {
    const session = readSession();
    clearSession();
    if (!session) return;
    if (session.passwordChangeRequired)
      await request<void>(
        `${authBase}/initial-password/logout`,
        {},
        session.accessToken,
      );
    else if (session.refreshToken)
      await request<void>(`${authBase}/logout`, {
        refreshToken: session.refreshToken,
      });
  },
};

export const errorMessage = (error: unknown) =>
  error instanceof ApiError
    ? error.message
    : "Une erreur est survenue. Réessayez.";
