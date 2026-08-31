import {
  ArrowsClockwiseIcon,
  BuildingsIcon,
  ChartLineUpIcon,
  CreditCardIcon,
  CubeIcon,
  CurrencyCircleDollarIcon,
  EnvelopeSimpleIcon,
  FadersHorizontalIcon,
  FunnelIcon,
  GaugeIcon,
  HandshakeIcon,
  HeartbeatIcon,
  HexagonIcon,
  MegaphoneIcon,
  PackageIcon,
  PulseIcon,
  ReceiptIcon,
  ShieldCheckIcon,
  StackIcon,
  TagIcon,
  UserCircleIcon,
  UsersThreeIcon,
} from "@phosphor-icons/react";
import { Navigate, Outlet, useLocation, useNavigate } from "react-router";
import {
  adminAnalyticsSurfacePermissions,
  adminBillingSurfacePermissions,
  adminOverviewSurfacePermissions,
  adminPermissions,
} from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { type ProductNavigationGroup, ProductShell } from "@/components/patterns/product-shell";
import { ErrorState, LoadingState } from "@/components/patterns/remote-state";

export function AdminLayout() {
  const session = useAdminSession();
  const location = useLocation();
  const navigate = useNavigate();
  if (!session.session) return <Navigate replace state={{ from: location.pathname }} to="/admin/login" />;
  // A restricted session can call nothing here; the portal would be a wall of denials.
  if (session.session.passwordChangeRequired) return <Navigate replace to="/admin/initial-password" />;
  if (session.error)
    return (
      <main className="mx-auto max-w-3xl p-8" id="main-content">
        <ErrorState description="La session existe, mais son profil n’a pas pu être chargé." retry={session.retry} />
      </main>
    );
  if (session.loading || !session.me)
    return (
      <main className="mx-auto max-w-3xl p-8" id="main-content">
        <LoadingState />
      </main>
    );
  const plannedSectionsVisible = session.me.isSuperAdmin;
  const groups: ProductNavigationGroup[] = [
    {
      items: [
        {
          label: "Vue d’ensemble",
          to: "/admin",
          icon: GaugeIcon,
          end: true,
          visible: adminOverviewSurfacePermissions.some(session.can),
        },
      ],
    },
    {
      label: "Accès",
      items: [
        {
          label: "Opérateurs",
          to: "/admin/operators",
          icon: UsersThreeIcon,
          visible: session.can(adminPermissions.usersRead),
        },
        { label: "Rôles", to: "/admin/roles", icon: ShieldCheckIcon, visible: session.can(adminPermissions.rolesRead) },
        {
          label: "Modèles de rôles",
          to: "/admin/role-templates",
          icon: StackIcon,
          visible: plannedSectionsVisible,
        },
      ],
    },
    {
      label: "Clients",
      items: [
        { label: "Comptes", to: "/admin/accounts", icon: BuildingsIcon, visible: plannedSectionsVisible },
        {
          label: "Abonnements",
          to: "/admin/subscriptions",
          icon: CreditCardIcon,
          visible: session.can(adminPermissions.subscriptionsSearch),
        },
        {
          label: "Changements en lot",
          to: "/admin/subscription-jobs",
          icon: ArrowsClockwiseIcon,
          visible: session.can(adminPermissions.subscriptionsListChangeJobs),
        },
        {
          label: "Collaborations",
          to: "/admin/collaborations",
          icon: HandshakeIcon,
          visible: plannedSectionsVisible,
        },
      ],
    },
    {
      label: "Commercial",
      items: [
        { label: "Forfaits", to: "/admin/plans", icon: CubeIcon, visible: session.can(adminPermissions.plansList) },
        {
          label: "Add-ons",
          to: "/admin/add-ons",
          icon: PackageIcon,
          visible: session.can(adminPermissions.addOnsList),
        },
        {
          label: "Quotas",
          to: "/admin/quota-packages",
          icon: HexagonIcon,
          visible: session.can(adminPermissions.quotaPackagesList),
        },
        {
          label: "Grille tarifaire",
          to: "/admin/price-books",
          icon: CurrencyCircleDollarIcon,
          visible: session.can(adminPermissions.priceBooksList),
        },
        {
          label: "Segments",
          to: "/admin/segments",
          icon: FunnelIcon,
          visible: session.can(adminPermissions.segmentsList),
        },
        {
          label: "Campagnes",
          to: "/admin/campaigns",
          icon: MegaphoneIcon,
          visible: session.can(adminPermissions.campaignsList),
        },
        {
          label: "Offres",
          to: "/admin/offers",
          icon: TagIcon,
          visible: session.can(adminPermissions.offersList),
        },
        {
          label: "Politiques commerciales",
          to: "/admin/commercial-policies",
          icon: FadersHorizontalIcon,
          visible: session.can(adminPermissions.commercialPoliciesList),
        },
        {
          label: "Facturation",
          to: "/admin/billing",
          icon: ReceiptIcon,
          visible: adminBillingSurfacePermissions.some(session.can),
        },
      ],
    },
    {
      label: "Opérations",
      items: [
        {
          label: "Fonctionnalités",
          to: "/admin/features",
          icon: HexagonIcon,
          visible: session.can(adminPermissions.registryRead),
        },
        { label: "Activités", to: "/admin/activities", icon: PulseIcon, visible: plannedSectionsVisible },
        {
          label: "Communications",
          to: "/admin/communications",
          icon: EnvelopeSimpleIcon,
          visible: plannedSectionsVisible,
        },
      ],
    },
    {
      label: "Observabilité",
      items: [
        {
          label: "Santé & journaux",
          to: "/admin/observability",
          icon: HeartbeatIcon,
          visible: plannedSectionsVisible,
        },
      ],
    },
    {
      label: "Analytique",
      items: [
        {
          label: "Statistiques",
          to: "/admin/analytics",
          icon: ChartLineUpIcon,
          visible: adminAnalyticsSurfacePermissions.some(session.can),
        },
      ],
    },
    {
      label: "Plateforme",
      items: [{ label: "Mon accès", to: "/admin/me", icon: UserCircleIcon }],
    },
  ];
  return (
    <ProductShell
      email={session.me.email}
      groups={groups}
      label="Administration plateforme"
      onLogout={() => void session.logout().then(() => navigate("/admin/login"))}
    >
      <Outlet />
    </ProductShell>
  );
}
