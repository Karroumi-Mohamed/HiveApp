import { createBrowserRouter, Navigate, RouterProvider } from "react-router";
import { AppProviders } from "@/app/providers";
import {
  adminActivitiesSurfacePermissions,
  adminAnalyticsSurfacePermissions,
  adminBillingSurfacePermissions,
  adminCommercialCampaignDetailSurfacePermissions,
  adminCommercialCampaignEditPermissions,
  adminCommercialPolicyDetailSurfacePermissions,
  adminCommercialSegmentDetailSurfacePermissions,
  adminCommunicationsSurfacePermissions,
  adminInvoiceDetailSurfacePermissions,
  adminObservabilitySurfacePermissions,
  adminOfferDetailSurfacePermissions,
  adminOfferEditPermissions,
  adminOverviewSurfacePermissions,
  adminPermissions,
  adminPriceBookDetailSurfacePermissions,
  adminSubscriptionDetailSurfacePermissions,
  adminSubscriptionJobDetailSurfacePermissions,
  clientOfferSurfacePermissions,
  clientPermissions,
  clientSubscriptionSurfacePermissions,
} from "@/auth/permissions";
import { AdminReadPermissionGate, ClientReadPermissionGate } from "@/components/patterns/permission-gate";
import { AdminLayout } from "@/features/admin/admin-layout";
import {
  AdminAccountsPlaceholderPage,
  AdminCollaborationsPlaceholderPage,
  AdminRoleTemplatesPlaceholderPage,
} from "@/features/admin/admin-placeholder-pages";
import { AdminAnalyticsPage } from "@/features/admin/analytics/admin-analytics-page";
import { AdminBillingDocumentPage } from "@/features/admin/billing/admin-billing-document-page";
import { AdminBillingPage } from "@/features/admin/billing/admin-billing-page";
import { AdminInvoiceDetailPage } from "@/features/admin/billing/admin-invoice-detail-page";
import {
  AdminOperationalAddOnsPage,
  AdminOperationalPlansPage,
  AdminOperationalQuotaPackagesPage,
} from "@/features/admin/commercial/admin-commercial-catalog-pages";
import { AdminAddOnsPage, AdminQuotaPackagesPage } from "@/features/admin/commercial/admin-commercial-pages";
import { AdminCommercialCampaignDetailPage } from "@/features/admin/commercial-campaigns/admin-commercial-campaign-detail-page";
import { AdminCommercialCampaignsPage } from "@/features/admin/commercial-campaigns/admin-commercial-campaigns-page";
import {
  AdminCommercialCampaignCreatePage,
  AdminCommercialCampaignEditPage,
} from "@/features/admin/commercial-campaigns/commercial-campaign-editor";
import { AdminOfferDetailPage } from "@/features/admin/commercial-offers/admin-offer-detail-page";
import { AdminOfferCreatePage, AdminOfferEditPage } from "@/features/admin/commercial-offers/admin-offer-editor";
import { AdminOffersPage } from "@/features/admin/commercial-offers/admin-offers-page";
import { AdminCommercialPoliciesPage } from "@/features/admin/commercial-policies/admin-commercial-policies-page";
import { AdminCommercialPolicyDetailPage } from "@/features/admin/commercial-policies/admin-commercial-policy-detail-page";
import {
  AdminCommercialPolicyCreatePage,
  AdminCommercialPolicyEditPage,
} from "@/features/admin/commercial-policies/commercial-policy-editor";
import { AdminCommercialSegmentDetailPage } from "@/features/admin/commercial-segments/admin-commercial-segment-detail-page";
import { AdminCommercialSegmentsPage } from "@/features/admin/commercial-segments/admin-commercial-segments-page";
import {
  AdminCommercialSegmentCreatePage,
  AdminCommercialSegmentEditPage,
} from "@/features/admin/commercial-segments/commercial-segment-editor";
import { AdminMePage } from "@/features/admin/me/admin-me-page";
import { AdminActivitiesPage } from "@/features/admin/operations/admin-activities-page";
import { AdminCommunicationsPage } from "@/features/admin/operations/admin-communications-page";
import { AdminObservabilityPage } from "@/features/admin/operations/admin-observability-page";
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
import { AdminSubscriptionChangeJobCreatePage } from "@/features/admin/subscription-jobs/admin-subscription-change-job-create-page";
import { AdminSubscriptionChangeJobDetailPage } from "@/features/admin/subscription-jobs/admin-subscription-change-job-detail-page";
import { AdminSubscriptionChangeJobsPage } from "@/features/admin/subscription-jobs/admin-subscription-change-jobs-page";
import { AdminSpecialAgreementCreatePage } from "@/features/admin/subscriptions/admin-special-agreement-create-page";
import { AdminSpecialAgreementsPage } from "@/features/admin/subscriptions/admin-special-agreements-page";
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
import { ClientOffersPage } from "@/features/client/offers/client-offers-page";
import { ClientOrganizationPage } from "@/features/client/organization/client-organization-page";
import { ClientOverviewPage } from "@/features/client/overview/client-overview-page";
import { ClientRolesPage } from "@/features/client/roles/client-roles-page";
import { ClientBillingDocumentPage } from "@/features/client/subscription/client-billing-document-page";
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
        path: "subscriptions/agreements",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.subscriptionsSearchSpecialAgreements]}>
            <AdminSpecialAgreementsPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "subscriptions/:accountId",
        element: (
          <AdminReadPermissionGate anyOf={adminSubscriptionDetailSurfacePermissions}>
            <AdminSubscriptionsPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "subscriptions/:accountId/agreements/new",
        element: (
          <AdminReadPermissionGate
            allOf={[
              adminPermissions.subscriptionsChooseChangeOptions,
              adminPermissions.subscriptionsPreviewSpecialAgreement,
              adminPermissions.subscriptionsCreateSpecialAgreement,
            ]}
          >
            <AdminSpecialAgreementCreatePage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "subscription-jobs",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.subscriptionsListChangeJobs]}>
            <AdminSubscriptionChangeJobsPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "subscription-jobs/new",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.subscriptionsPreviewChangeJob]}>
            <AdminSubscriptionChangeJobCreatePage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "subscription-jobs/:jobId",
        element: (
          <AdminReadPermissionGate anyOf={adminSubscriptionJobDetailSurfacePermissions}>
            <AdminSubscriptionChangeJobDetailPage />
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
        path: "segments",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.segmentsList]}>
            <AdminCommercialSegmentsPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "segments/new",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.segmentsCreate]}>
            <AdminCommercialSegmentCreatePage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "segments/:segmentId/edit",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.segmentsRead, adminPermissions.segmentsUpdateDraft]}>
            <AdminCommercialSegmentEditPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "segments/:segmentId/:tab?",
        element: (
          <AdminReadPermissionGate anyOf={adminCommercialSegmentDetailSurfacePermissions}>
            <AdminCommercialSegmentDetailPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "campaigns",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.campaignsList]}>
            <AdminCommercialCampaignsPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "campaigns/new",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.campaignsCreate]}>
            <AdminCommercialCampaignCreatePage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "campaigns/:campaignId/edit",
        element: (
          <AdminReadPermissionGate allOf={adminCommercialCampaignEditPermissions}>
            <AdminCommercialCampaignEditPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "campaigns/:campaignId/:tab?",
        element: (
          <AdminReadPermissionGate anyOf={adminCommercialCampaignDetailSurfacePermissions}>
            <AdminCommercialCampaignDetailPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "offers",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.offersList]}>
            <AdminOffersPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "offers/new",
        element: (
          <AdminReadPermissionGate
            allOf={[adminPermissions.offersCreate, adminPermissions.offersPreviewCreateDefinition]}
          >
            <AdminOfferCreatePage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "offers/:offerId/edit",
        element: (
          <AdminReadPermissionGate allOf={adminOfferEditPermissions}>
            <AdminOfferEditPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "offers/:offerId/redemptions/:redemptionId",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.offersReadRedemptionDetail]}>
            <AdminOfferDetailPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "offers/:offerId/:tab?",
        element: (
          <AdminReadPermissionGate anyOf={adminOfferDetailSurfacePermissions}>
            <AdminOfferDetailPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "commercial-policies",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.commercialPoliciesList]}>
            <AdminCommercialPoliciesPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "commercial-policies/new",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.commercialPoliciesCreate]}>
            <AdminCommercialPolicyCreatePage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "commercial-policies/:policyId/edit",
        element: (
          <AdminReadPermissionGate
            allOf={[adminPermissions.commercialPoliciesRead, adminPermissions.commercialPoliciesUpdateDraft]}
          >
            <AdminCommercialPolicyEditPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "commercial-policies/:policyId/:tab?",
        element: (
          <AdminReadPermissionGate anyOf={adminCommercialPolicyDetailSurfacePermissions}>
            <AdminCommercialPolicyDetailPage />
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
      {
        path: "billing",
        element: (
          <AdminReadPermissionGate anyOf={adminBillingSurfacePermissions}>
            <AdminBillingPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "billing/invoices/:invoiceId",
        element: (
          <AdminReadPermissionGate anyOf={adminInvoiceDetailSurfacePermissions}>
            <AdminInvoiceDetailPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "billing/invoices/:invoiceId/document",
        element: (
          <AdminReadPermissionGate allOf={[adminPermissions.billingReadInvoiceDocument]}>
            <AdminBillingDocumentPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "activities",
        element: (
          <AdminReadPermissionGate anyOf={adminActivitiesSurfacePermissions}>
            <AdminActivitiesPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "communications",
        element: (
          <AdminReadPermissionGate anyOf={adminCommunicationsSurfacePermissions}>
            <AdminCommunicationsPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "observability",
        element: (
          <AdminReadPermissionGate anyOf={adminObservabilitySurfacePermissions}>
            <AdminObservabilityPage />
          </AdminReadPermissionGate>
        ),
      },
      {
        path: "analytics",
        element: (
          <AdminReadPermissionGate anyOf={adminAnalyticsSurfacePermissions}>
            <AdminAnalyticsPage />
          </AdminReadPermissionGate>
        ),
      },
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
      {
        path: "subscription/invoices/:invoiceId/document",
        element: (
          <ClientReadPermissionGate allOf={[clientPermissions.subscriptionReadInvoiceDocument]}>
            <ClientBillingDocumentPage />
          </ClientReadPermissionGate>
        ),
      },
      {
        path: "offers",
        element: (
          <ClientReadPermissionGate anyOf={clientOfferSurfacePermissions}>
            <ClientOffersPage />
          </ClientReadPermissionGate>
        ),
      },
      {
        path: "offers/redemptions/:redemptionId",
        element: (
          <ClientReadPermissionGate allOf={[clientPermissions.subscriptionOfferHistoryDetail]}>
            <ClientOffersPage />
          </ClientReadPermissionGate>
        ),
      },
      {
        path: "offers/:offerId",
        element: (
          <ClientReadPermissionGate allOf={[clientPermissions.subscriptionOfferDetail]}>
            <ClientOffersPage />
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
