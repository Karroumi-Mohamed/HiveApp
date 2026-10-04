import { beforeEach, expect, test, vi } from "vitest";
import { authApi, sessionIdentity } from "./auth";
import { readSession, writeSession } from "../auth/session";

const original = {
  accessToken: "old-access",
  refreshToken: "old-refresh",
  expiresIn: 900,
  tokenType: "Bearer",
  passwordChangeRequired: false,
};
const renewed = {
  ...original,
  accessToken: "new-access",
  refreshToken: "new-refresh",
};
const fetchMock = vi.fn<typeof fetch>();
const response = (data: unknown, status = 200) =>
  new Response(data === undefined ? null : JSON.stringify(data), { status });
beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

test("concurrent expired reads share one refresh and retry with the rotated token", async () => {
  writeSession(original);
  let release: (value: Response) => void = () => {};
  const pending = new Promise<Response>((resolve) => {
    release = resolve;
  });
  fetchMock.mockImplementation(async (path, options) => {
    if (String(path).endsWith("/refresh")) return pending;
    const token = new Headers(options?.headers).get("Authorization");
    return token === "Bearer old-access"
      ? response({}, 401)
      : response({ email: "admin@example.test" });
  });
  const first = sessionIdentity(),
    second = sessionIdentity();
  await vi.waitFor(() =>
    expect(
      fetchMock.mock.calls.filter(([path]) =>
        String(path).endsWith("/refresh"),
      ),
    ).toHaveLength(1),
  );
  release(response(renewed));
  expect(await Promise.all([first, second])).toEqual([
    { email: "admin@example.test" },
    { email: "admin@example.test" },
  ]);
  expect(readSession()?.refreshToken).toBe("new-refresh");
});

test("a late refresh cannot restore a signed-out session", async () => {
  writeSession(original);
  let release: (value: Response) => void = () => {};
  fetchMock
    .mockResolvedValueOnce(response({}, 401))
    .mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          release = resolve;
        }),
    )
    .mockResolvedValueOnce(response(undefined, 204));
  const identity = sessionIdentity();
  const failure = expect(identity).rejects.toMatchObject({ status: 401 });
  await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  await authApi.logout();
  release(response(renewed));
  await failure;
  expect(readSession()).toBeNull();
});

test("an old refresh failure cannot sign out a replacement session", async () => {
  writeSession(original);
  let release: (value: Response) => void = () => {};
  fetchMock.mockResolvedValueOnce(response({}, 401)).mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        release = resolve;
      }),
  );
  const identity = sessionIdentity();
  const failure = expect(identity).rejects.toMatchObject({ status: 401 });
  await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  writeSession(renewed);
  release(response({}, 401));
  await failure;
  expect(readSession()?.accessToken).toBe("new-access");
});

test("a rejected rotated access token clears the session instead of redirecting in a loop", async () => {
  writeSession(original);
  fetchMock
    .mockResolvedValueOnce(response({}, 401))
    .mockResolvedValueOnce(response(renewed))
    .mockResolvedValueOnce(response({}, 401));
  await expect(sessionIdentity()).rejects.toMatchObject({ status: 401 });
  expect(readSession()).toBeNull();
});

test("temporary network failures keep a valid session for retry", async () => {
  writeSession(original);
  fetchMock.mockRejectedValueOnce(new TypeError("network"));
  await expect(sessionIdentity()).rejects.toMatchObject({ status: 0 });
  expect(readSession()?.accessToken).toBe("old-access");
});
