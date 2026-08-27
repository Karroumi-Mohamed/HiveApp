import { createBrowserRouter, Navigate, RouterProvider } from "react-router";
import { AppProviders } from "@/app/providers";
import {
  adminOverviewSurfacePermissions,
  adminPermissions,
  adminPriceBookDetailSurfacePermissions,
  clientSubscriptionSurfacePermissions,
} from "@/auth/permissions";
import { AdminReadPermissionGate, ClientReadPermissionGate } from "@/components/patterns/permission-gate";
import { AdminLayout } from "@/features/admin/admin-layout";
import {
  AdminAccountsPlaceholderPage,
  AdminActivitiesPlaceholderPage,
  AdminAnalyticsPlaceholderPage,
  AdminBillingPlaceholderPage,
  AdminCollaborationsPlaceholderPage,
  AdminCommunicationsPlaceholderPage,
  AdminObservabilityPlaceholderPage,
  AdminRoleTemplatesPlaceholderPage,
} from "@/features/admin/admin-placeholder-pages";
import {
  AdminOperationalAddOnsPage,
  AdminOperationalPlansPage,
  AdminOperationalQuotaPackagesPage,
} from "@/features/admin/commercial/admin-commercial-catalog-pages";
import { AdminAddOnsPage, AdminQuotaPackagesPage } from "@/features/admin/commercial/admin-commercial-pages";
import { AdminMePage } from "@/features/admin/me/admin-me-page";
import { AdminOperatorDetailPage } from "@/features/admin/operators/admin-operator-detail-page";
import { AdminOperatorsPage } from "@/features/admin/operators/admin-operators-page";
import { AdminOverviewPage } from "@/features/admin/overview/admin-overview-page";
import { AdminPlanCreatePage } from "@/features/admin/plans/admin-plan-create-page";
import { AdminPlansPage } from "@/features/admin/plans/admin-plans-page";
import { AdminProductPriceCreatePage } from "@/features/admin/price-books/admin-product-price-create-page";
import { AdminProductPriceDetailPage } from "@/features/admin/price-books/admin-product-price-detail-page";
import { AdminProductPricesPage } from "@/features/admin/price-books/admin-product-prices-page";
import { AdminFeaturesPage } from "@/features/admin/registry/admin-features-page";
import { AdminRoleDetailPage } from "@/features/admin/roles/admin-role-detail-page";
import { AdminRolesPage } from "@/features/admin/roles/admin-roles-page";
import { AdminSubscriptionsPage } from "@/features/admin/subscriptions/admin-subscriptions-page";
import {
  AdminActivationPage,
  AdminEmailVerificationPage,
  AdminInitialPasswordPage,
  AdminLoginPage,
  AdminPasswordResetPage,
  AdminPasswordResetRequestPage,
  ClientLoginPage,
  InitialPasswordPage,
  PasswordCompletionPage,
  PasswordResetPage,
} from "@/features/auth/login-page";
import { ClientLayout } from "@/features/client/client-layout";
import { ClientCollaborationsPage } from "@/features/client/collaborations/client-collaborations-page";
import { ClientCompaniesPage } from "@/features/client/companies/client-companies-page";
import { ClientMePage } from "@/features/client/me/client-me-page";
import { ClientMembersPage } from "@/features/client/members/client-members-page";
import { ClientOrganizationPage } from "@/features/client/organization/client-organization-page";
import { ClientOverviewPage } from "@/features/client/overview/client-overview-page";
import { ClientRolesPage } from "@/features/client/roles/client-roles-page";
import { ClientSubscriptionPage } from "@/features/client/subscription/client-subscription-page";
import { DesignSystemPreview } from "@/features/design-system/design-system-preview";

const router = createBrowserRouter([
  {
    path: "/",
    element: <Navigate to="/admin" replace />,
  },
  { path: "/admin/login", element: <AdminLoginPage /> },
  { path: "/admin/activation/complete", element: <AdminActivationPage /> },
  { path: "/admin/email-verification/complete", element: <AdminEmailVerificationPage /> },
  { path: "/admin/initial-password", element: <AdminInitialPasswordPage /> },
  { path: "/admin/password-reset", element: <AdminPasswordResetRequestPage /> },
  { path: "/admin/password-reset/complete", element: <AdminPasswordResetPage /> },
  {
    path: "/admin",
    element: <AdminLayout />,
    children: [
      {
        index: true,
        element: (
          <AdminReadPermissionGate anyOf={adminOverviewSurfacePermissions}>
            <AdminOverviewPage />
          </AdminReadPermissionGate>
        ),
      },
      { path: "operators", element: <AdminOperatorsPage /> },
      { path: "operators/:operatorId", element: <AdminOperatorDetailPage /> },
      { path: "roles", element: <AdminRolesPage /> },
      { path: "roles/:roleId", element: <AdminRoleDetailPage /> },
      {
        path: "plans",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.plansList]}>
            <AdminOperationalPlansPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "plans/new",
        element: (
          <AdminReadPermissionGate anyOf={[adminPermissions.plansCreate, adminPermissions.plansDuplicate]}>
            <AdminPlanCreatePage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "plans/:planId",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.plansReadDetail]}>
            <AdminPlansPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "plans/:planId/features",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.plansReadDetail, adminPermissions.plansListFeatures]}>
            <AdminPlansPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "plans/:planId/schema",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.plansReadDetail, adminPermissions.plansListFeatures]}>
            <AdminPlansPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "plans/:planId/subscribers",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.plansReadDetail, adminPermissions.plansListSubscribers]}>
            <AdminPlansPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "plans/:planId/:tab",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.plansReadDetail]}>
            <AdminPlansPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "subscriptions",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.subscriptionsSearch]}>
            <AdminSubscriptionsPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "subscriptions/:accountId",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.subscriptionsRead]}>
            <AdminSubscriptionsPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "add-ons",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.addOnsList]}>
            <AdminOperationalAddOnsPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "add-ons/:addOnId/:tab?",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.addOnsReadDetail]}>
            <AdminAddOnsPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "quota-packages",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.quotaPackagesList]}>
            <AdminOperationalQuotaPackagesPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "quota-packages/:packageId/:tab?",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.quotaPackagesReadDetail]}>
            <AdminQuotaPackagesPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "price-books",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.priceBooksList]}>
            <AdminProductPricesPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "price-books/new",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.priceBooksCreate]}>
            <AdminProductPriceCreatePage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "price-books/:priceId/:tab?",
        element: (
          <AdminReadPermissionGate anyOf={adminPriceBookDetailSurfacePermissions}>
            <AdminProductPriceDetailPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "features",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.registryRead]}>
            <AdminFeaturesPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "features/:featureId",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.registryRead]}>
            <AdminFeaturesPage />
          </AdminReadPermissionGate>
        ),
      },
      { path: "role-templates", element: <AdminRoleTemplatesPlaceholderPage /> },
      { path: "accounts", element: <AdminAccountsPlaceholderPage /> },
      { path: "collaborations", element: <AdminCollaborationsPlaceholderPage /> },
      { path: "billing", element: <AdminBillingPlaceholderPage /> },
      { path: "activities", element: <AdminActivitiesPlaceholderPage /> },
      { path: "communications", element: <AdminCommunicationsPlaceholderPage /> },
      { path: "observability", element: <AdminObservabilityPlaceholderPage /> },
      { path: "analytics", element: <AdminAnalyticsPlaceholderPage /> },
      { path: "me", element: <AdminMePage /> },
    ],
  },
  { path: "/app/login", element: <ClientLoginPage /> },
  { path: "/app/initial-password", element: <InitialPasswordPage /> },
  { path: "/app/password-reset", element: <PasswordResetPage /> },
  { path: "/app/password-reset/complete", element: <PasswordCompletionPage mode="reset" /> },
  { path: "/app/activation/complete", element: <PasswordCompletionPage mode="activation" /> },
  {
    path: "/app",
    element: <ClientLayout />,
    children: [
      { index: true, element: <ClientOverviewPage /> },
      { path: "companies", element: <ClientCompaniesPage /> },
      { path: "companies/:companyId", element: <ClientCompaniesPage /> },
      { path: "organization", element: <ClientOrganizationPage /> },
      { path: "members", element: <ClientMembersPage /> },
      { path: "members/:memberId", element: <ClientMembersPage /> },
      { path: "roles", element: <ClientRolesPage /> },
      { path: "roles/:roleId", element: <ClientRolesPage /> },
      { path: "collaborations", element: <ClientCollaborationsPage /> },
      { path: "collaborations/:collaborationId", element: <ClientCollaborationsPage /> },
      {
        path: "subscription",
        element: (
          <ClientReadPermissionGate anyOf={clientSubscriptionSurfacePermissions}>
            <ClientSubscriptionPage />
          </ClientReadPermissionGate>
        ),
      },
      { path: "me", element: <ClientMePage /> },
    ],
  },
  {
    path: "/design-system",
    element: <DesignSystemPreview />,
  },
  {
    path: "*",
    element: <Navigate to="/" replace />,
  },
]);

export function App() {
  return (
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>
  );
}

export default App;
