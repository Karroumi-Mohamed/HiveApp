import { Check, LogOut } from "lucide-react";
import { useEffect, useState } from "react";
import { Navigate, useNavigate } from "react-router";
import { ApiError, authApi, errorMessage, sessionIdentity } from "../api/auth";
import { homePath, loginPath, readSession } from "../auth/session";
import { AuthLayout } from "../components/auth-layout";

export function SignedInPage() {
  const navigate = useNavigate();
  const [identity, setIdentity] = useState<{
    email?: string;
    name?: string;
  } | null>(null);
  const [error, setError] = useState(""),
    [attempt, setAttempt] = useState(0);
  const session = readSession();
  useEffect(() => {
    if (!session || session.passwordChangeRequired) return;
    const controller = new AbortController();
    setError("");
    void sessionIdentity(controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setIdentity(value);
      })
      .catch((reason) => {
        if (controller.signal.aborted) return;
        if (reason instanceof ApiError && reason.status === 401)
          navigate(loginPath, { replace: true });
        else setError(errorMessage(reason));
      });
    return () => controller.abort();
  }, [
    session?.accessToken,
    session?.passwordChangeRequired,
    attempt,
    navigate,
  ]);
  if (!session) return <Navigate replace to={loginPath} />;
  if (session.passwordChangeRequired)
    return <Navigate replace to={`${homePath}/initial-password`} />;
  return (
    <AuthLayout
      title={identity ? "Vous êtes connecté" : "Connexion à votre espace"}
      footer={
        <button
          className="text-button"
          type="button"
          onClick={() => {
            void authApi.logout().catch(() => undefined);
            navigate(loginPath, { replace: true });
          }}
        >
          <LogOut size={16} strokeWidth={1.7} aria-hidden="true" /> Se
          déconnecter
        </button>
      }
    >
      {identity ? (
        <div className="success-message" role="status">
          <Check size={24} strokeWidth={1.7} aria-hidden="true" />
          <p className="identity">{identity.email || identity.name}</p>
          <p>
            La connexion fonctionne. Nous construirons la suite de cet espace
            étape par étape.
          </p>
        </div>
      ) : error ? (
        <div className="auth-form">
          <p className="form-error" role="alert">
            {error}
          </p>
          <button
            className="button secondary"
            onClick={() => setAttempt((value) => value + 1)}
          >
            Réessayer
          </button>
        </div>
      ) : (
        <p className="loading-message" role="status">
          Vérification de votre session…
        </p>
      )}
    </AuthLayout>
  );
}
