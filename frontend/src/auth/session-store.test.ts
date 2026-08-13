import { afterAll, beforeEach, describe, expect, test } from "bun:test";
import { Window } from "happy-dom";
import { configureHttpAuth } from "@/api/http";
import {
  clearSession,
  configureSessionCacheReset,
  readSession,
  replaceSessionIfCurrent,
  subscribeSessions,
  writeSession,
} from "./session-store";

const session = (overrides: Partial<{ expiresAt: number; passwordChangeRequired: boolean }> = {}) => ({
  accessToken: "token",
  refreshToken: "refresh",
  expiresAt: Date.now() + 60_000,
  passwordChangeRequired: false,
  ...overrides,
});

let cleared: string[] = [];

beforeEach(() => {
  const browser = new Window({ url: "http://localhost:3000" });
  Object.defineProperty(globalThis, "window", { configurable: true, value: browser });
  // The store holds module state, so each case starts from a known-empty one.
  writeSession("admin", null);
  writeSession("client", null);
  cleared = [];
  configureSessionCacheReset((audience) => cleared.push(audience));
});

afterAll(() => {
  // Importing this module registers a real token reader on the shared HTTP boundary. Left in
  // place it would leak into other suites.
  configureHttpAuth(
    () => null,
    () => undefined,
  );
});

describe("session expiry", () => {
  test("a live session reads back", () => {
    writeSession("admin", session());
    expect(readSession("admin")?.accessToken).toBe("token");
  });

  test("an expired session reads as absent", () => {
    writeSession("admin", session({ expiresAt: Date.now() - 1 }));
    // Otherwise the shell renders as authenticated and every request inside it answers 401.
    expect(readSession("admin")).toBeNull();
  });
});

describe("clearing a session", () => {
  test("drops that audience's cached queries", () => {
    writeSession("admin", session());
    clearSession("admin");

    expect(readSession("admin")).toBeNull();
    expect(cleared).toEqual(["admin"]);
  });

  test("leaves the other audience's session and cache alone", () => {
    writeSession("admin", session());
    writeSession("client", session());
    clearSession("admin");

    expect(readSession("client")?.accessToken).toBe("token");
    expect(cleared).not.toContain("client");
  });

  test("notifies subscribers so the shell re-renders", () => {
    let notifications = 0;
    const unsubscribe = subscribeSessions(() => {
      notifications += 1;
    });

    writeSession("admin", session());
    clearSession("admin");
    unsubscribe();

    expect(notifications).toBe(2);
  });
});

describe("asynchronous session replacement", () => {
  test("a late refresh cannot revive a session after logout", () => {
    const original = session();
    writeSession("admin", original);
    clearSession("admin");

    expect(replaceSessionIfCurrent("admin", original, session({ expiresAt: Date.now() + 120_000 }))).toBe(false);
    expect(readSession("admin")).toBeNull();
  });

  test("an old refresh failure cannot clear a newer login", () => {
    const original = session();
    const newer = { ...session({ expiresAt: Date.now() + 120_000 }), accessToken: "new-token" };
    writeSession("admin", original);
    writeSession("admin", newer);

    expect(replaceSessionIfCurrent("admin", original, null)).toBe(false);
    expect(readSession("admin")?.accessToken).toBe("new-token");
  });

  test("the current session can be refreshed", () => {
    const original = session();
    const refreshed = { ...session({ expiresAt: Date.now() + 120_000 }), accessToken: "refreshed-token" };
    writeSession("admin", original);

    expect(replaceSessionIfCurrent("admin", original, refreshed)).toBe(true);
    expect(readSession("admin")?.accessToken).toBe("refreshed-token");
  });

  test("expiry can clear the exact session even after its deadline passes", () => {
    const expiring = session({ expiresAt: Date.now() - 1 });
    writeSession("admin", expiring);

    expect(replaceSessionIfCurrent("admin", expiring, null)).toBe(true);
    expect(cleared).toEqual(["admin"]);
  });
});
