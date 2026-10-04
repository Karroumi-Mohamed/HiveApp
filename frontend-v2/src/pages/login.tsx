import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router";
import { authApi, errorMessage } from "../api/auth";
import { homePath, readSession, writeSession } from "../auth/session";
import { AuthLayout } from "../components/auth-layout";
import { Field, FormError, PasswordField } from "../components/fields";

export function LoginPage() {
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const session = readSession();
  const base = homePath;
  if (session)
    return (
      <Navigate
        replace
        to={session.passwordChangeRequired ? `${base}/initial-password` : base}
      />
    );
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const fields = new FormData(event.currentTarget);
    setBusy(true);
    setError("");
    try {
      const auth = await authApi.login(
        String(fields.get("identifier")),
        String(fields.get("password")),
      );
      writeSession(auth);
      navigate(
        auth.passwordChangeRequired ? `${base}/initial-password` : base,
        { replace: true },
      );
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setBusy(false);
    }
  }
  return (
    <AuthLayout title="Se connecter" description="Heureux de vous retrouver.">
      <form className="auth-form" onSubmit={submit} aria-busy={busy}>
        <Field
          id="identifier"
          name="identifier"
          label="Email ou identifiant"
          autoComplete="username"
          autoFocus
          required
          disabled={busy}
        />
        <PasswordField
          id="password"
          name="password"
          label="Mot de passe"
          autoComplete="current-password"
          required
          disabled={busy}
        />
        <Link className="forgot-link" to={`${base}/password-reset`}>
          Mot de passe oublié ?
        </Link>
        <FormError message={error} />
        <button className="button primary" disabled={busy} type="submit">
          {busy ? "Connexion…" : "Se connecter"}
        </button>
      </form>
    </AuthLayout>
  );
}
