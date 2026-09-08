import type { RouteObject } from "react-router";
import { adminPermissions } from "@/auth/permissions";
import { AdminReadPermissionGate } from "@/components/patterns/permission-gate";
import { AdminPlansPage } from "./admin-plans-page";

/** Shared with routing tests so static URLs and their permission gates are exercised together. */
export const adminPlanDetailRoutes: RouteObject[] = [
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
      <AdminReadPermissionGate allOf={[adminPermissions.plansReadDetail, adminPermissions.plansListSubscribers]}>
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
