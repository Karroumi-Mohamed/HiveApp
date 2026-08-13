import { describe, expect, test } from "bun:test";
import { canSubmitNewPassword, shouldLoadAdminProfile } from "./session-rules";

const session = (passwordChangeRequired: boolean) => ({
  accessToken: "token",
  refreshToken: null,
  expiresAt: Date.now() + 60_000,
  passwordChangeRequired,
});

describe("new password submission", () => {
  test("a mismatched confirmation cannot be submitted", () => {
    expect(canSubmitNewPassword("correct-horse", "correct-horse-typo")).toBe(false);
  });

  test("an empty confirmation cannot be submitted", () => {
    // The regression this guards: the form showed the mismatch and submitted anyway, setting
    // whatever was in the first box.
    expect(canSubmitNewPassword("correct-horse", "")).toBe(false);
  });

  test("a matching password shorter than the minimum cannot be submitted", () => {
    expect(canSubmitNewPassword("short", "short")).toBe(false);
  });

  test("a matching password of sufficient length can be submitted", () => {
    expect(canSubmitNewPassword("correct-horse", "correct-horse")).toBe(true);
  });

  test("matching is exact, not case- or whitespace-insensitive", () => {
    expect(canSubmitNewPassword("correct-horse", "Correct-Horse")).toBe(false);
    expect(canSubmitNewPassword("correct-horse", "correct-horse ")).toBe(false);
  });
});

describe("restricted admin session", () => {
  test("no profile is requested while a password change is pending", () => {
    expect(shouldLoadAdminProfile(session(true))).toBe(false);
  });

  test("a normal session loads its profile", () => {
    expect(shouldLoadAdminProfile(session(false))).toBe(true);
  });

  test("no session requests nothing", () => {
    expect(shouldLoadAdminProfile(null)).toBe(false);
  });
});
