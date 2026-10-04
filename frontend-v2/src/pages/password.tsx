import { Check, ArrowLeft } from "lucide-react";
import { useState, type FormEvent } from "react";
import {
  Link,
  Navigate,
  useLocation,
  useNavigate,
  useSearchParams,
} from "react-router";
import { authApi, errorMessage } from "../api/auth";
import {
  homePath,
  loginPath,
  readSession,
  writeSession,
} from "../auth/session";
import { AuthLayout } from "../components/auth-layout";
import { Field, FormError, PasswordField } from "../components/fields";

export function PasswordPage({
  kind,
}: {
  kind:
    "password-reset" | "activation" | "initial-password" | "email-verification";
}) {
  const [params] = useSearchParams(),
    navigate = useNavigate();
  const { pathname } = useLocation();
  const [busy, setBusy] = useState(false),
    [done, setDone] = useState(false),
    [error, setError] = useState("");
  const [confirmationError, setConfirmationError] = useState("");
  const token = params.get("token") ?? "";
  const session = readSession();
  const requesting =
    kind === "password-reset" && !token && !pathname.endsWith("/complete");
  const verifying = kind === "email-verification";
  const initial = kind === "initial-password";
  const missing = !requesting && !initial && !token;
  if (initial && !session) return <Navigate replace to={loginPath} />;
  if (initial && session && !session.passwordChangeRequired)
    return <Navigate replace to={homePath} />;
  const title = verifying
    ? "Vérifier mon email"
    : requesting
      ? "Mot de passe oublié ?"
      : initial
        ? "Choisir mon mot de passe"
        : kind === "activation"
          ? "Activer mon accès"
          : "Nouveau mot de passe";
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || missing) return;
    const fields = new FormData(event.currentTarget);
    const password = String(fields.get("password") ?? "");
    if (!requesting && !verifying && password !== fields.get("confirmation")) {
      setConfirmationError("Les mots de passe ne correspondent pas.");
      (
        event.currentTarget.elements.namedItem(
          "confirmation",
        ) as HTMLInputElement
      )?.focus();
      return;
    }
    setError("");
    setConfirmationError("");
    setBusy(true);
    try {
      if (requesting) await authApi.requestReset(String(fields.get("email")));
      else if (verifying) await authApi.verifyEmail(token);
      else if (initial) {
        const auth = await authApi.changeInitial(
          session!.accessToken,
          password,
        );
        writeSession(auth);
        navigate(
          auth.passwordChangeRequired
            ? `${homePath}/initial-password`
            : homePath,
          { replace: true },
        );
        return;
      } else
        await authApi.completePassword(
          kind as "activation" | "password-reset",
          token,
          password,
        );
      setDone(true);
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setBusy(false);
    }
  }
  return (
    <AuthLayout
      title={
        done ? (requesting ? "Consultez votre email" : "C’est fait") : title
      }
      description={
        done || missing
          ? undefined
          : requesting
            ? "Nous vous enverrons un lien de réinitialisation."
            : verifying
              ? "Confirmez que cette adresse vous appartient."
              : "Utilisez un mot de passe personnel d’au moins 8 caractères."
      }
      footer={
        initial ? (
          <button
            className="text-button"
            type="button"
            onClick={() => {
              void authApi.logout().catch(() => undefined);
              navigate(loginPath, { replace: true });
            }}
          >
            Annuler cette session
          </button>
        ) : (
          <Link to={loginPath}>
            <ArrowLeft size={15} strokeWidth={1.7} aria-hidden="true" /> Retour
            à la connexion
          </Link>
        )
      }
    >
      {missing ? (
        <p className="form-error" role="alert">
          Ce lien est incomplet. Demandez un nouveau lien.
        </p>
      ) : done ? (
        <div className="success-message" role="status">
          <Check size={24} strokeWidth={1.7} aria-hidden="true" />
          <p>
            {requesting
              ? "Si cette adresse correspond à un compte éligible, vous recevrez un lien pour choisir un nouveau mot de passe."
              : verifying
                ? "Votre adresse email est vérifiée."
                : "Votre mot de passe a été enregistré. Vous pouvez vous connecter."}
          </p>
          <Link className="button primary" to={loginPath}>
            Se connecter
          </Link>
        </div>
      ) : (
        <form className="auth-form" onSubmit={submit} aria-busy={busy}>
          {requesting ? (
            <Field
              id="email"
              name="email"
              label="Email"
              type="email"
              required
              autoComplete="email"
              autoFocus
              disabled={busy}
            />
          ) : verifying ? null : (
            <>
              <PasswordField
                id="password"
                name="password"
                label="Nouveau mot de passe"
                autoComplete="new-password"
                required
                minLength={8}
                disabled={busy}
              />
              <PasswordField
                id="confirmation"
                name="confirmation"
                label="Confirmer le mot de passe"
                autoComplete="new-password"
                required
                disabled={busy}
                error={confirmationError}
                onChange={() => setConfirmationError("")}
              />
            </>
          )}
          <FormError message={error} />
          <button className="button primary" type="submit" disabled={busy}>
            {busy
              ? "En cours…"
              : requesting
                ? "Envoyer le lien"
                : verifying
                  ? "Vérifier mon email"
                  : "Enregistrer le mot de passe"}
          </button>
        </form>
      )}
    </AuthLayout>
  );
}
