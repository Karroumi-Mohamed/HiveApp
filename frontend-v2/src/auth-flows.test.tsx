import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { App } from "./app";
import { readSession, writeSession } from "./auth/session";

const auth = {
  accessToken: "test-access",
  refreshToken: "test-refresh",
  expiresIn: 900,
  tokenType: "Bearer",
  passwordChangeRequired: false,
};
const fetchMock = vi.fn<typeof fetch>();
const response = (body: unknown, status = 200) =>
  new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
function open(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}
beforeEach(() => {
  sessionStorage.clear();
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

describe("React authentication flows", () => {
  test.each(["/signup", "/app/login", "/app"])(
    "customer route %s is not part of the admin frontend",
    async (path) => {
      open(path);
      await screen.findByRole("heading", { name: "Page introuvable" });
      expect(fetchMock).not.toHaveBeenCalled();
      expect(
        screen.queryByRole("button", { name: "Créer mon compte" }),
      ).toBeNull();
    },
  );
  test("administrator login has no public signup or customer navigation", () => {
    open("/admin/login");
    expect(screen.queryByRole("link", { name: "Créer un compte" })).toBeNull();
    expect(
      screen.queryByRole("link", { name: "Accès à l’espace client" }),
    ).toBeNull();
  });
  test("administrator login uses its own API and stores only an admin session", async () => {
    fetchMock
      .mockResolvedValueOnce(response(auth))
      .mockResolvedValueOnce(response({ email: "admin@example.test" }));
    open("/admin/login");
    const user = userEvent.setup();
    await user.type(
      screen.getByLabelText("Email ou identifiant"),
      "admin@example.test",
    );
    await user.type(
      screen.getByLabelText("Mot de passe", { exact: true }),
      " password123 ",
    );
    await user.click(screen.getByRole("button", { name: "Se connecter" }));
    await screen.findByRole("heading", { name: "Vous êtes connecté" });
    expect(fetchMock.mock.calls[0][0]).toBe("/api/admin/auth/login");
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toEqual({
      identifier: "admin@example.test",
      password: " password123 ",
    });
    expect(fetchMock.mock.calls[1][0]).toBe("/api/admin/me");
    expect(
      new Headers(fetchMock.mock.calls[1][1]?.headers).get("Authorization"),
    ).toBe("Bearer test-access");
    expect(readSession()?.accessToken).toBe("test-access");
  });
  test("failed login remains on the form and does not persist credentials", async () => {
    fetchMock.mockResolvedValueOnce(
      response({ message: "Identifiants incorrects" }, 401),
    );
    open("/admin/login");
    const user = userEvent.setup();
    await user.type(
      screen.getByLabelText("Email ou identifiant"),
      "wrong@example.test",
    );
    await user.type(
      screen.getByLabelText("Mot de passe", { exact: true }),
      "password123",
    );
    await user.click(screen.getByRole("button", { name: "Se connecter" }));
    expect((await screen.findByRole("alert")).textContent).toBe(
      "Identifiants incorrects",
    );
    expect(readSession()).toBeNull();
    expect(
      screen
        .getByRole("button", { name: "Se connecter" })
        .hasAttribute("disabled"),
    ).toBe(false);
  });
  test("temporary login goes to initial password without reading protected identity", async () => {
    fetchMock.mockResolvedValueOnce(
      response({ ...auth, refreshToken: null, passwordChangeRequired: true }),
    );
    open("/admin/login");
    const user = userEvent.setup();
    await user.type(
      screen.getByLabelText("Email ou identifiant"),
      "invited@example.test",
    );
    await user.type(
      screen.getByLabelText("Mot de passe", { exact: true }),
      "temporary123",
    );
    await user.click(screen.getByRole("button", { name: "Se connecter" }));
    await screen.findByRole("heading", { name: "Choisir mon mot de passe" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
  test("protected destinations redirect unauthenticated visitors without making reads", async () => {
    open("/admin");
    await screen.findByRole("heading", { name: "Se connecter" });
    expect(fetchMock).not.toHaveBeenCalled();
  });
  test("a recovery connection failure is retriable rather than a false sent confirmation", async () => {
    fetchMock
      .mockRejectedValueOnce(new TypeError("network"))
      .mockResolvedValueOnce(response(undefined, 204));
    open("/admin/password-reset");
    const user = userEvent.setup();
    await user.type(
      screen.getByLabelText("Email", { exact: true }),
      "admin@example.test",
    );
    await user.click(screen.getByRole("button", { name: "Envoyer le lien" }));
    await screen.findByRole("alert");
    expect(
      screen.queryByRole("heading", { name: "Consultez votre email" }),
    ).toBeNull();
    await user.click(screen.getByRole("button", { name: "Envoyer le lien" }));
    await screen.findByRole("heading", { name: "Consultez votre email" });
    expect(screen.getByRole("status").textContent).toContain(
      "Si cette adresse correspond",
    );
  });
  test("operator activation completes without inventing an admin session", async () => {
    fetchMock.mockResolvedValueOnce(response(undefined, 204));
    open("/admin/activation?token=test-invitation");
    const user = userEvent.setup();
    await user.type(
      screen.getByLabelText("Nouveau mot de passe"),
      "new-password123",
    );
    await user.type(
      screen.getByLabelText("Confirmer le mot de passe"),
      "new-password123",
    );
    await user.click(
      screen.getByRole("button", { name: "Enregistrer le mot de passe" }),
    );
    await screen.findByRole("heading", { name: "C’est fait" });
    expect(fetchMock.mock.calls[0][0]).toBe(
      "/api/admin/auth/activation/complete",
    );
    expect(readSession()).toBeNull();
  });
  test("logout clears the local session immediately and calls revocation", async () => {
    writeSession(auth);
    fetchMock
      .mockResolvedValueOnce(response({ email: "admin@example.test" }))
      .mockImplementationOnce(() => new Promise(() => {}));
    open("/admin");
    await screen.findByRole("heading", { name: "Vous êtes connecté" });
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Se déconnecter" }));
    await screen.findByRole("heading", { name: "Se connecter" });
    expect(readSession()).toBeNull();
    expect(fetchMock.mock.calls[1][0]).toBe("/api/admin/auth/logout");
  });
  test("missing activation token exposes no password completion action", async () => {
    open("/admin/activation");
    await screen.findByRole("alert");
    expect(
      screen.queryByRole("button", { name: "Enregistrer le mot de passe" }),
    ).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });
  test("a completion URL without a token does not become a recovery-request form", async () => {
    open("/admin/password-reset/complete");
    await screen.findByRole("alert");
    expect(
      screen.queryByRole("button", { name: "Envoyer le lien" }),
    ).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });
  test.each([
    ["/admin/activation/complete?token=invitation", "Activer mon accès"],
    [
      "/admin/email-verification/complete?token=verification",
      "Vérifier mon email",
    ],
    ["/auth/activation?token=legacy-invitation", "Activer mon accès"],
  ])(
    "backend and legacy email link %s opens its completion flow",
    async (path, heading) => {
      open(path);
      await screen.findByRole("heading", { name: heading });
      expect(screen.queryByRole("alert")).toBeNull();
      expect(fetchMock).not.toHaveBeenCalled();
    },
  );
  test("initial password completion sends the restricted bearer token before reading identity", async () => {
    writeSession({ ...auth, refreshToken: null, passwordChangeRequired: true });
    fetchMock
      .mockResolvedValueOnce(response(auth))
      .mockResolvedValueOnce(response({ email: "admin@example.test" }));
    open("/admin/initial-password");
    const user = userEvent.setup();
    await user.type(
      screen.getByLabelText("Nouveau mot de passe"),
      "personal-password123",
    );
    await user.type(
      screen.getByLabelText("Confirmer le mot de passe"),
      "personal-password123",
    );
    await user.click(
      screen.getByRole("button", { name: "Enregistrer le mot de passe" }),
    );
    await screen.findByRole("heading", { name: "Vous êtes connecté" });
    expect(fetchMock.mock.calls[0][0]).toBe(
      "/api/admin/auth/initial-password/change",
    );
    expect(
      new Headers(fetchMock.mock.calls[0][1]?.headers).get("Authorization"),
    ).toBe("Bearer test-access");
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toEqual({
      newPassword: "personal-password123",
    });
    expect(readSession()?.passwordChangeRequired).toBe(false);
  });
  test("password mismatch blocks activation and focuses confirmation", async () => {
    open("/admin/activation/complete?token=invitation");
    const user = userEvent.setup();
    await user.type(
      screen.getByLabelText("Nouveau mot de passe"),
      "personal-password123",
    );
    await user.type(
      screen.getByLabelText("Confirmer le mot de passe"),
      "different-password123",
    );
    await user.click(
      screen.getByRole("button", { name: "Enregistrer le mot de passe" }),
    );
    expect(
      screen.getByText("Les mots de passe ne correspondent pas."),
    ).toBeTruthy();
    expect(document.activeElement).toBe(
      screen.getByLabelText("Confirmer le mot de passe"),
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
