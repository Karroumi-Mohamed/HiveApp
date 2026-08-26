import {
  BuildingsIcon,
  CreditCardIcon,
  GaugeIcon,
  GitBranchIcon,
  HandshakeIcon,
  ShieldCheckIcon,
  UserCircleIcon,
  UsersThreeIcon,
} from "@phosphor-icons/react";
import { Navigate, Outlet, useLocation, useNavigate } from "react-router";
import { clientPermissions, clientSubscriptionSurfacePermissions } from "@/auth/permissions";
import { useClientSession } from "@/auth/session-provider";
import { type ProductNavigationGroup, ProductShell } from "@/components/patterns/product-shell";
import { ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

export function ClientLayout() {
  const session = useClientSession();
  const location = useLocation();
  const navigate = useNavigate();
  if (!session.session) return <Navigate replace state={{ from: location.pathname }} to="/app/login" />;
  if (session.session.passwordChangeRequired) return <Navigate replace to="/app/initial-password" />;
  if (session.error)
    return (
      <main className="mx-auto max-w-3xl p-8" id="main-content">
        <ErrorState description="Le compte ou ses autorisations n’ont pas pu être chargés." retry={session.retry} />
      </main>
    );
  if (session.loading || !session.account)
    return (
      <main className="mx-auto max-w-3xl p-8" id="main-content">
        <LoadingState />
      </main>
    );
  const groups: ProductNavigationGroup[] = [
    { items: [{ label: "Vue d’ensemble", to: "/app", icon: GaugeIcon, end: true }] },
    {
      label: "Organisation",
      items: [
        {
          label: "Entreprises",
          to: "/app/companies",
          icon: BuildingsIcon,
          visible: session.can(clientPermissions.companiesRead),
        },
        {
          label: "Structure",
          to: "/app/organization",
          icon: GitBranchIcon,
          visible: session.can(clientPermissions.organizationRead),
        },
        {
          label: "Membres",
          to: "/app/members",
          icon: UsersThreeIcon,
          visible: session.can(clientPermissions.membersRead),
        },
        { label: "Rôles", to: "/app/roles", icon: ShieldCheckIcon, visible: session.can(clientPermissions.rolesRead) },
      ],
    },
    {
      label: "Services",
      items: [
        {
          label: "Collaborations",
          to: "/app/collaborations",
          icon: HandshakeIcon,
          visible:
            session.can(clientPermissions.collaborationsRead) ||
            session.can(clientPermissions.incomingCollaborationsRead),
        },
        {
          label: "Abonnement",
          to: "/app/subscription",
          icon: CreditCardIcon,
          visible: clientSubscriptionSurfacePermissions.some(session.can),
        },
        { label: "Mon accès", to: "/app/me", icon: UserCircleIcon },
      ],
    },
  ];
  const activeCompanies = session.companies.filter((company) => company.isActive);
  const context = activeCompanies.length ? (
    <Select
      onValueChange={(value) => session.selectCompany(value === "account" ? null : value)}
      value={session.selectedCompanyId ?? "account"}
    >
      <SelectTrigger
        aria-label="Contexte entreprise"
        className="hidden h-8 w-48 border-0 bg-muted/70 text-xs shadow-none md:flex"
      >
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="account">Tout le compte</SelectItem>
        {activeCompanies.map((company) => (
          <SelectItem key={company.id} value={company.id}>
            {company.name}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  ) : null;
  return (
    <ProductShell
      context={context}
      email={session.account.name}
      groups={groups}
      label={session.account.name}
      onLogout={() => void session.logout().then(() => navigate("/app/login"))}
    >
      <Outlet />
    </ProductShell>
  );
}
