import { Link, Navigate, Route, Routes, useLocation } from "react-router";
import { AuthLayout } from "./components/auth-layout";
import { LoginPage } from "./pages/login";
import { PasswordPage } from "./pages/password";
import { SignedInPage } from "./pages/signed-in";

export function App() {
  const { search } = useLocation();
  return (
    <Routes>
      <Route path="/" element={<Navigate replace to="/admin/login" />} />
      <Route path="/login" element={<Navigate replace to="/admin/login" />} />
      <Route path="/admin" element={<SignedInPage />} />
      <Route path="/admin/login" element={<LoginPage />} />
      <Route
        path="/admin/initial-password"
        element={<PasswordPage key="initial" kind="initial-password" />}
      />
      <Route
        path="/admin/password-reset"
        element={<PasswordPage key="reset" kind="password-reset" />}
      />
      <Route
        path="/admin/password-reset/complete"
        element={<PasswordPage key="reset-complete" kind="password-reset" />}
      />
      <Route
        path="/admin/activation"
        element={<PasswordPage key="activation" kind="activation" />}
      />
      <Route
        path="/admin/activation/complete"
        element={<PasswordPage key="activation-complete" kind="activation" />}
      />
      <Route
        path="/admin/email-verification"
        element={<PasswordPage kind="email-verification" />}
      />
      <Route
        path="/admin/email-verification/complete"
        element={<PasswordPage kind="email-verification" />}
      />
      <Route
        path="/auth/activation"
        element={<Navigate replace to={`/admin/activation${search}`} />}
      />
      <Route
        path="/auth/password-reset"
        element={<Navigate replace to={`/admin/password-reset${search}`} />}
      />
      <Route
        path="/auth/email-verification"
        element={<Navigate replace to={`/admin/email-verification${search}`} />}
      />
      <Route
        path="/auth/initial-password"
        element={<Navigate replace to="/admin/initial-password" />}
      />
      <Route
        path="*"
        element={
          <AuthLayout title="Page introuvable">
            <Link className="button secondary" to="/admin/login">
              Retour à la connexion
            </Link>
          </AuthLayout>
        }
      />
    </Routes>
  );
}
