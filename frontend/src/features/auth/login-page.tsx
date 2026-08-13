import { EyeIcon, EyeSlashIcon, HexagonIcon } from "@phosphor-icons/react";
import { type FormEvent, useState } from "react";
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from "react-router";
import { adminApi } from "@/api/admin-api";
import { authApi } from "@/api/client-api";
import type { AuthResponse } from "@/api/contracts";
import { ApiError } from "@/api/http";
import { useAdminSession, useClientSession } from "@/auth/session-provider";
import { writeSession } from "@/auth/session-store";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";

function LoginFrame({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <main className="grid min-h-dvh bg-background lg:grid-cols-[minmax(0,1fr)_minmax(28rem,0.72fr)]" id="main-content">
      <section className="hidden overflow-hidden bg-sidebar p-12 text-sidebar-accent-foreground lg:flex lg:flex-col lg:justify-between">
        <div className="flex items-center gap-3">
          <span className="relative grid size-10 place-items-center text-sidebar-primary">
            <HexagonIcon className="absolute size-10" weight="fill" />
            <span className="relative text-sm font-black text-sidebar-primary-foreground">H</span>
          </span>
          <span className="text-lg font-bold">HiveApp</span>
        </div>
        <div className="max-w-xl">
          <p className="text-3xl font-semibold leading-tight tracking-[-0.04em]">
            Un espace opérationnel unique pour votre organisation.
          </p>
          <p className="mt-4 max-w-lg text-sm leading-6 text-sidebar-foreground/65">
            Accédez uniquement aux données, entreprises et actions qui vous sont confiées.
          </p>
        </div>
        <p className="text-xs text-sidebar-foreground/45">HiveApp</p>
      </section>
      <section className="flex items-center justify-center px-5 py-12 sm:px-10">
        <div className="w-full max-w-md">
          <div className="mb-9 flex items-center gap-3 lg:hidden">
            <HexagonIcon className="size-8 text-primary" weight="fill" />
            <span className="font-bold">HiveApp</span>
          </div>
          <h1 className="text-2xl font-semibold tracking-[-0.035em]">{title}</h1>
          {children}
        </div>
      </section>
    </main>
  );
}

function persistClientSession(response: AuthResponse) {
  writeSession("client", {
    accessToken: response.accessToken,
    refreshToken: response.refreshToken,
    expiresAt: Date.now() + response.expiresIn * 1000,
    passwordChangeRequired: response.passwordChangeRequired,
  });
}

function PasswordField({ password, setPassword }: { password: string; setPassword: (value: string) => void }) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="space-y-2">
      <Label htmlFor="password">Mot de passe</Label>
      <div className="relative">
        <Input
          autoComplete="current-password"
          className="pe-11"
          id="password"
          onChange={(event) => setPassword(event.target.value)}
          required
          type={visible ? "text" : "password"}
          value={password}
        />
        <Button
          aria-label={visible ? "Masquer le mot de passe" : "Afficher le mot de passe"}
          className="absolute end-1 top-1"
          onClick={() => setVisible((value) => !value)}
          size="icon-sm"
          type="button"
          variant="ghost"
        >
          {visible ? <EyeSlashIcon /> : <EyeIcon />}
        </Button>
      </div>
    </div>
  );
}

export function AdminLoginPage() {
  const session = useAdminSession();
  const navigate = useNavigate();
  const location = useLocation();
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  if (session.session) return <Navigate replace to="/admin" />;
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await session.login({ identifier, password });
      navigate((location.state as { from?: string } | null)?.from ?? "/admin", { replace: true });
    } catch (reason) {
      setError(reason instanceof ApiError ? reason.message : "Connexion impossible.");
    } finally {
      setSubmitting(false);
    }
  };
  return (
    <LoginFrame title="Administration plateforme">
      <form className="mt-8 space-y-5" onSubmit={submit}>
        <div className="space-y-2">
          <Label htmlFor="identifier">Email ou identifiant</Label>
          <Input
            autoComplete="username"
            autoFocus
            id="identifier"
            onChange={(event) => setIdentifier(event.target.value)}
            required
            value={identifier}
          />
        </div>
        <PasswordField password={password} setPassword={setPassword} />
        {error ? (
          <p className="text-sm text-destructive" role="alert">
            {error}
          </p>
        ) : null}
        <Button className="w-full" disabled={submitting} type="submit">
          {submitting ? "Connexion…" : "Se connecter"}
        </Button>
        <Link className="block text-center text-sm underline" to="/admin/password-reset">
          Mot de passe oublié ?
        </Link>
      </form>
    </LoginFrame>
  );
}

export function ClientLoginPage() {
  const session = useClientSession();
  const navigate = useNavigate();
  const [password, setPassword] = useState("");
  const [identifier, setIdentifier] = useState("");
  const [accountCode, setAccountCode] = useState("");
  const [employeeNumber, setEmployeeNumber] = useState("");
  const [mode, setMode] = useState("identifier");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  if (session.session)
    return <Navigate replace to={session.session.passwordChangeRequired ? "/app/initial-password" : "/app"} />;
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const response = await session.login(
        mode === "identifier" ? { identifier, password } : { accountCode, employeeNumber, password },
      );
      navigate(response.passwordChangeRequired ? "/app/initial-password" : "/app", { replace: true });
    } catch (reason) {
      setError(reason instanceof ApiError ? reason.message : "Connexion impossible.");
    } finally {
      setSubmitting(false);
    }
  };
  return (
    <LoginFrame title="Espace de travail">
      <form className="mt-8 space-y-5" onSubmit={submit}>
        <Tabs onValueChange={setMode} value={mode}>
          <TabsList className="grid w-full grid-cols-2">
            <TabsTrigger value="identifier">Email ou identifiant</TabsTrigger>
            <TabsTrigger value="employee">Numéro employé</TabsTrigger>
          </TabsList>
          <TabsContent className="mt-5" value="identifier">
            <div className="space-y-2">
              <Label htmlFor="member-identifier">Email ou identifiant</Label>
              <Input
                autoComplete="username"
                autoFocus
                id="member-identifier"
                onChange={(event) => setIdentifier(event.target.value)}
                required={mode === "identifier"}
                value={identifier}
              />
            </div>
          </TabsContent>
          <TabsContent className="mt-5 space-y-4" value="employee">
            <div className="space-y-2">
              <Label htmlFor="account-code">Code du compte</Label>
              <Input
                id="account-code"
                onChange={(event) => setAccountCode(event.target.value)}
                required={mode === "employee"}
                value={accountCode}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="employee-number">Numéro employé</Label>
              <Input
                id="employee-number"
                onChange={(event) => setEmployeeNumber(event.target.value)}
                required={mode === "employee"}
                value={employeeNumber}
              />
            </div>
          </TabsContent>
        </Tabs>
        <PasswordField password={password} setPassword={setPassword} />
        {error ? (
          <p className="text-sm text-destructive" role="alert">
            {error}
          </p>
        ) : null}
        <Button className="w-full" disabled={submitting} type="submit">
          {submitting ? "Connexion…" : "Se connecter"}
        </Button>
        <Button asChild className="w-full" variant="ghost">
          <Link to="/app/password-reset">Mot de passe oublié</Link>
        </Button>
      </form>
    </LoginFrame>
  );
}

function NewPasswordFields({ value, setValue }: { value: string; setValue: (value: string) => void }) {
  const [confirmation, setConfirmation] = useState("");
  return (
    <>
      <div className="space-y-2">
        <Label htmlFor="new-password">Nouveau mot de passe</Label>
        <Input
          autoComplete="new-password"
          id="new-password"
          minLength={8}
          onChange={(event) => setValue(event.target.value)}
          required
          type="password"
          value={value}
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor="password-confirmation">Confirmer le mot de passe</Label>
        <Input
          aria-invalid={Boolean(confirmation && confirmation !== value)}
          autoComplete="new-password"
          id="password-confirmation"
          minLength={8}
          onChange={(event) => setConfirmation(event.target.value)}
          required
          type="password"
          value={confirmation}
        />
        {confirmation && confirmation !== value ? (
          <p className="text-xs text-destructive">Les mots de passe diffèrent.</p>
        ) : null}
      </div>
      <input name="passwords-match" type="hidden" value={confirmation === value ? "yes" : ""} />
    </>
  );
}

export function InitialPasswordPage() {
  const session = useClientSession();
  const navigate = useNavigate();
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  if (!session.session) return <Navigate replace to="/app/login" />;
  if (!session.session.passwordChangeRequired) return <Navigate replace to="/app" />;
  const currentSession = session.session;
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const response = await authApi.changeInitialPassword(currentSession.accessToken, password);
      persistClientSession(response);
      navigate("/app", { replace: true });
    } catch (reason) {
      setError(reason instanceof ApiError ? reason.message : "Modification impossible.");
    } finally {
      setSubmitting(false);
    }
  };
  return (
    <LoginFrame title="Choisir votre mot de passe">
      <form className="mt-8 space-y-5" onSubmit={submit}>
        <NewPasswordFields setValue={setPassword} value={password} />
        {error ? <p className="text-sm text-destructive">{error}</p> : null}
        <Button className="w-full" disabled={password.length < 8 || submitting} type="submit">
          {submitting ? "Enregistrement…" : "Continuer"}
        </Button>
        <Button
          className="w-full"
          onClick={() =>
            void authApi
              .logoutInitialAccess(session.session?.accessToken ?? "")
              .finally(() => writeSession("client", null))
          }
          type="button"
          variant="ghost"
        >
          Annuler cette session
        </Button>
      </form>
    </LoginFrame>
  );
}

export function PasswordResetPage() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    await authApi.requestPasswordReset(email).catch(() => undefined);
    setSubmitting(false);
    setSent(true);
  };
  return (
    <LoginFrame title="Réinitialiser l’accès">
      {sent ? (
        <div className="mt-8 space-y-5">
          <p className="text-sm leading-6 text-muted-foreground">
            Si cette adresse correspond à un compte, un lien de réinitialisation a été envoyé.
          </p>
          <Button asChild className="w-full" variant="outline">
            <Link to="/app/login">Retour à la connexion</Link>
          </Button>
        </div>
      ) : (
        <form className="mt-8 space-y-5" onSubmit={submit}>
          <div className="space-y-2">
            <Label htmlFor="reset-email">Email</Label>
            <Input
              autoComplete="email"
              id="reset-email"
              onChange={(event) => setEmail(event.target.value)}
              required
              type="email"
              value={email}
            />
          </div>
          <Button className="w-full" disabled={submitting} type="submit">
            {submitting ? "Envoi…" : "Envoyer le lien"}
          </Button>
          <Button asChild className="w-full" variant="ghost">
            <Link to="/app/login">Retour</Link>
          </Button>
        </form>
      )}
    </LoginFrame>
  );
}

/**
 * Operator activation. Deliberately does not sign the operator in: the endpoint returns no
 * session, so an emailed link can never by itself produce an authenticated admin session. The
 * operator sets a password here and then signs in through the normal admin login.
 */
export function AdminActivationPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const token = params.get("token") ?? "";
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await adminApi.completeActivation(token, password);
      navigate("/admin/login", { replace: true });
    } catch (reason) {
      setError(reason instanceof ApiError ? reason.message : "Lien invalide ou expiré.");
    } finally {
      setSubmitting(false);
    }
  };
  return (
    <LoginFrame title="Activer votre accès opérateur">
      {!token ? (
        <p className="mt-8 text-sm text-destructive">Le lien ne contient aucun jeton valide.</p>
      ) : (
        <form className="mt-8 space-y-5" onSubmit={submit}>
          <NewPasswordFields setValue={setPassword} value={password} />
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
          <p className="text-xs text-muted-foreground">
            Vous serez ensuite redirigé vers la connexion pour vous identifier.
          </p>
          <Button className="w-full" disabled={password.length < 8 || submitting} type="submit">
            {submitting ? "Enregistrement…" : "Définir le mot de passe"}
          </Button>
        </form>
      )}
    </LoginFrame>
  );
}

/**
 * Operator password reset. Like activation, it issues no session — the operator signs in after
 * setting the new password.
 */
export function AdminPasswordResetPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const token = params.get("token") ?? "";
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await adminApi.completePasswordReset(token, password);
      navigate("/admin/login", { replace: true });
    } catch (reason) {
      setError(reason instanceof ApiError ? reason.message : "Lien invalide ou expiré.");
    } finally {
      setSubmitting(false);
    }
  };
  return (
    <LoginFrame title="Nouveau mot de passe">
      {!token ? (
        <p className="mt-8 text-sm text-destructive">Le lien ne contient aucun jeton valide.</p>
      ) : (
        <form className="mt-8 space-y-5" onSubmit={submit}>
          <NewPasswordFields setValue={setPassword} value={password} />
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
          <Button className="w-full" disabled={password.length < 8 || submitting} type="submit">
            {submitting ? "Enregistrement…" : "Définir le mot de passe"}
          </Button>
        </form>
      )}
    </LoginFrame>
  );
}

/**
 * Always reports success. The API deliberately does not confirm whether an address belongs to an
 * operator, and this screen must not leak what the API withholds.
 */
export function AdminPasswordResetRequestPage() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    try {
      await adminApi.requestPasswordReset(email.trim());
    } catch {
      // Deliberately ignored — see above.
    } finally {
      setSubmitting(false);
      setSent(true);
    }
  };
  return (
    <LoginFrame title="Mot de passe oublié">
      {sent ? (
        <div className="mt-8 space-y-4">
          <p className="text-sm text-muted-foreground">
            Si cette adresse correspond à un accès opérateur vérifié, un lien de réinitialisation vient d’être envoyé.
            Le lien expire après 24 heures.
          </p>
          <p className="text-xs text-muted-foreground">
            Si votre adresse ne reçoit pas d’email, demandez à un administrateur de vous générer un accès temporaire.
          </p>
          <Link className="block text-sm underline" to="/admin/login">
            Retour à la connexion
          </Link>
        </div>
      ) : (
        <form className="mt-8 space-y-5" onSubmit={submit}>
          <div className="space-y-2">
            <Label htmlFor="admin-reset-email">Email professionnel</Label>
            <Input
              autoFocus
              id="admin-reset-email"
              onChange={(event) => setEmail(event.target.value)}
              type="email"
              value={email}
            />
          </div>
          <Button className="w-full" disabled={!email.trim() || submitting} type="submit">
            {submitting ? "Envoi…" : "Envoyer le lien"}
          </Button>
          <Link className="block text-center text-sm underline" to="/admin/login">
            Retour à la connexion
          </Link>
        </form>
      )}
    </LoginFrame>
  );
}

export function PasswordCompletionPage({ mode }: { mode: "activation" | "reset" }) {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const token = params.get("token") ?? "";
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const response =
        mode === "activation"
          ? await authApi.completeActivation(token, password)
          : await authApi.completePasswordReset(token, password);
      persistClientSession(response);
      navigate("/app", { replace: true });
    } catch (reason) {
      setError(reason instanceof ApiError ? reason.message : "Lien invalide ou expiré.");
    } finally {
      setSubmitting(false);
    }
  };
  return (
    <LoginFrame title={mode === "activation" ? "Activer votre accès" : "Nouveau mot de passe"}>
      {!token ? (
        <p className="mt-8 text-sm text-destructive">Le lien ne contient aucun jeton valide.</p>
      ) : (
        <form className="mt-8 space-y-5" onSubmit={submit}>
          <NewPasswordFields setValue={setPassword} value={password} />
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
          <Button className="w-full" disabled={password.length < 8 || submitting} type="submit">
            {submitting ? "Enregistrement…" : "Continuer"}
          </Button>
        </form>
      )}
    </LoginFrame>
  );
}
