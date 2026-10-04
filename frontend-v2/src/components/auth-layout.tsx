import { useEffect, type ReactNode } from "react";
import { Link } from "react-router";

export function AuthLayout({
  title,
  description,
  children,
  footer,
}: {
  title: string;
  description?: string;
  children: ReactNode;
  footer?: ReactNode;
}) {
  useEffect(() => {
    document.title = `${title} · HiveApp`;
  }, [title]);
  return (
    <main className="auth-page" id="main-content">
      <div className="auth-shell">
        <Link className="brand" to="/admin/login" aria-label="HiveApp, accueil">
          <img src="/hive.svg" width="36" height="36" alt="" />
          <span>HiveApp</span>
        </Link>
        <section className="auth-card" aria-labelledby="page-title">
          <header className="auth-heading">
            <p className="eyebrow">Administration</p>
            <h1 id="page-title">{title}</h1>
            {description && <p>{description}</p>}
          </header>
          {children}
        </section>
        {footer && <div className="auth-footer">{footer}</div>}
      </div>
    </main>
  );
}
