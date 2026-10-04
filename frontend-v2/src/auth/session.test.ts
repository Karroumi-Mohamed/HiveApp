import { describe, expect, test } from "vitest";
import { readSession, writeSession } from "./session";

const auth = {
  accessToken: "test",
  refreshToken: "refresh",
  expiresIn: 900,
  tokenType: "Bearer",
  passwordChangeRequired: false,
};
describe("administrator tab session", () => {
  test("expired restricted tokens are removed without being promoted", () => {
    writeSession({
      ...auth,
      refreshToken: null,
      expiresIn: -1,
      passwordChangeRequired: true,
    });
    expect(readSession()).toBeNull();
    expect(sessionStorage.getItem("hiveapp-react-v2:admin:session")).toBeNull();
  });
  test("corrupt or incomplete persisted sessions are rejected", () => {
    sessionStorage.setItem("hiveapp-react-v2:admin:session", "invalid-json");
    expect(readSession()).toBeNull();
    sessionStorage.setItem(
      "hiveapp-react-v2:admin:session",
      JSON.stringify({ accessToken: "test" }),
    );
    expect(readSession()).toBeNull();
  });
});
