import type { StoredSession } from "@/auth/session-store";

/** The server's minimum. Kept here so the rule and its test cannot drift from the form. */
export const MINIMUM_PASSWORD_LENGTH = 8;

/**
 * Whether a new-password form may be submitted.
 *
 * <p>The confirmation must match exactly, not merely be non-empty: showing a mismatch while
 * still allowing submission silently sets whatever was typed in the first box.
 */
export function canSubmitNewPassword(password: string, confirmation: string): boolean {
  return password.length >= MINIMUM_PASSWORD_LENGTH && password === confirmation;
}

/**
 * Whether the admin profile should be requested for this session.
 *
 * <p>A session pending an initial password change holds a restricted token that cannot reach
 * `/api/admin/me`. Requesting it anyway guarantees a failure, which the shell then renders as
 * "the session exists but its profile could not be loaded" over the password-change screen.
 */
export function shouldLoadAdminProfile(session: StoredSession | null): boolean {
  return session !== null && !session.passwordChangeRequired;
}
