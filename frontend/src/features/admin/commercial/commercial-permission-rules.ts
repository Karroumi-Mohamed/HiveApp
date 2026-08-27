import { adminPermissions } from "@/auth/permissions";

export type CommercialProductKind = "PLAN" | "ADD_ON" | "QUOTA_PACKAGE";
export type PermissionReader = (permission: string) => boolean;

const permissions = {
  PLAN: {
    choose: adminPermissions.plansChoose,
    resolve: adminPermissions.plansResolveChoices,
    resolveCodes: adminPermissions.plansResolveChoiceCodes,
    read: adminPermissions.plansReadDetail,
    revise: adminPermissions.plansRevise,
  },
  ADD_ON: {
    choose: adminPermissions.addOnsChoose,
    resolve: adminPermissions.addOnsResolveChoices,
    resolveCodes: adminPermissions.addOnsResolveChoiceCodes,
    read: adminPermissions.addOnsReadDetail,
    revise: adminPermissions.addOnsRevise,
  },
  QUOTA_PACKAGE: {
    choose: adminPermissions.quotaPackagesChoose,
    resolve: adminPermissions.quotaPackagesResolveChoices,
    resolveCodes: adminPermissions.quotaPackagesResolveChoiceCodes,
    read: adminPermissions.quotaPackagesReadDetail,
    revise: adminPermissions.quotaPackagesRevise,
  },
} as const;

export function commercialProductPermission(
  kind: CommercialProductKind,
  operation: keyof (typeof permissions)[CommercialProductKind],
) {
  return permissions[kind][operation];
}

export function canOpenCommercialProduct(can: PermissionReader, kind: CommercialProductKind) {
  return can(commercialProductPermission(kind, "read"));
}

export function canReviseCommercialProduct(can: PermissionReader, kind: CommercialProductKind) {
  return canOpenCommercialProduct(can, kind) && can(commercialProductPermission(kind, "revise"));
}
