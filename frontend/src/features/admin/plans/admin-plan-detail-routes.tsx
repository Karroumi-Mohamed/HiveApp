import type { RouteObject } from "react-router";
import { adminPermissions } from "@/auth/permissions";
import { AdminReadPermissionGate } from "@/components/patterns/permission-gate";
import { AdminPlansPage } from "./admin-plans-page";
import { PlanApplicationCreatePage } from "./plan-application-create-page";
import { PlanApplicationDetailPage } from "./plan-application-detail-page";
import { PlanVersionComparisonPage, PlanVersionsPage } from "./plan-versions-page";

/** Shared with routing tests so static URLs and their permission gates are exercised together. */
export const adminPlanDetailRoutes: RouteObject[] = [
  {
    path: "plans/:planId/apply",
    element: (
      <AdminReadPermissionGate
        allOf={[
          adminPermissions.plansCreateApplication,
          adminPermissions.plansPreviewApplication,
          adminPermissions.plansReadApplication,
          adminPermissions.plansListVersions,
          adminPermissions.plansReadDetail,
        ]}
      >
        <PlanApplicationCreatePage />
      </AdminReadPermissionGate>
    ),
  },
  {
    path: "plans/:planId/applications/:applicationId",
    element: (
      <AdminReadPermissionGate allOf={[adminPermissions.plansReadApplication]}>
        <PlanApplicationDetailPage />
      </AdminReadPermissionGate>
    ),
  },
  {
    path: "plans/:planId/versions",
    element: (
      <AdminReadPermissionGate allOf={[adminPermissions.plansListVersions]}>
        <PlanVersionsPage />
      </AdminReadPermissionGate>
    ),
  },
  {
    path: "plans/:planId/versions/compare",
    element: (
      <AdminReadPermissionGate allOf={[adminPermissions.plansCompareVersions]}>
        <PlanVersionComparisonPage />
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
        <AdminPlansPage tab="features" />
      </AdminReadPermissionGate>
    ),
  },
  {
    path: "plans/:planId/schema",
    element: (
      <AdminReadPermissionGate allOf={[adminPermissions.plansReadDetail, adminPermissions.plansListFeatures]}>
        <AdminPlansPage tab="schema" />
      </AdminReadPermissionGate>
    ),
  },
  {
    path: "plans/:planId/subscribers",
    element: (
      <AdminReadPermissionGate
        allOf={[adminPermissions.plansReadDetail]}
        anyOf={[adminPermissions.plansListSubscribers, adminPermissions.plansListFamilySubscribers]}
      >
        <AdminPlansPage tab="subscribers" />
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
];
