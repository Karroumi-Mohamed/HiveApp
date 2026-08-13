import type {
  AccountDirectoryEntry,
  AddOn,
  AdminAccessOverview,
  AdminMe,
  AdminRole,
  AdminSubscription,
  AdminUser,
  AdminUserCreation,
  BulkOperationResult,
  CommercialOverview,
  FeatureOperationalChange,
  OperatorAccess,
  PageResponse,
  Plan,
  PlanDeletionPreview,
  PlanDetail,
  PlanFeature,
  PlanSubscriber,
  QuotaPackage,
  RegistryFeature,
  RegistryModule,
  RegistrySyncRun,
  RoleHolder,
  Subscription,
  SubscriptionChangeOperation,
  UUID,
} from "@/api/contracts";
import { apiRequest, jsonBody } from "@/api/http";

const admin = <T>(path: string, options: Parameters<typeof apiRequest<T>>[1] = {}) =>
  apiRequest<T>(`/api/admin${path}`, { ...options, audience: "admin" });

export const adminApi = {
  me: () => admin<AdminMe>("/me"),
  accessOverview: () => admin<AdminAccessOverview>("/users/overview"),
  commercialOverview: () => admin<CommercialOverview>("/plans/overview"),
  users: (query: {
    search?: string;
    active?: boolean;
    page?: number;
    size?: number;
    sort?: string;
    direction?: string;
  }) => admin<PageResponse<AdminUser>>("/users", { query }),
  bulkSetOperatorsActive: (ids: UUID[], active: boolean) =>
    admin<BulkOperationResult>("/users/bulk/active", { method: "POST", body: jsonBody({ ids, active }) }),
  bulkAssignOperatorRole: (ids: UUID[], adminRoleId: UUID) =>
    admin<BulkOperationResult>("/users/bulk/roles", { method: "POST", body: jsonBody({ ids, adminRoleId }) }),
  bulkResendOperatorActivation: (ids: UUID[]) =>
    admin<BulkOperationResult>("/users/bulk/access/resend", { method: "POST", body: jsonBody({ ids }) }),
  user: (id: UUID) => admin<AdminUser>(`/users/${id}`),
  createUser: (input: { firstName: string; lastName: string; email: string; isSuperAdmin: boolean }) =>
    admin<AdminUserCreation>("/users", { method: "POST", body: jsonBody(input) }),
  /**
   * Sets the operator's password from an emailed link. Public audience deliberately: the caller
   * holds no session yet, and the endpoint returns none — the operator signs in afterwards.
   */
  completeActivation: (token: string, newPassword: string) =>
    apiRequest<void>("/api/admin/auth/activation/complete", {
      method: "POST",
      body: jsonBody({ token, newPassword }),
    }),
  /** Always resolves, whether or not the address is an operator — the API will not confirm it. */
  requestPasswordReset: (email: string) =>
    apiRequest<void>("/api/admin/auth/password-reset/request", {
      method: "POST",
      body: jsonBody({ email }),
    }),
  completePasswordReset: (token: string, newPassword: string) =>
    apiRequest<void>("/api/admin/auth/password-reset/complete", {
      method: "POST",
      body: jsonBody({ token, newPassword }),
    }),
  /** Single-target by design: a name identifies one person, so there is no bulk form. */
  renameOperator: (id: UUID, input: { firstName: string; lastName: string }) =>
    admin<AdminUser>(`/users/${id}`, { method: "PATCH", body: jsonBody(input) }),
  operatorPermissions: (id: UUID) => admin<string[]>(`/users/${id}/permissions`),
  resendOperatorActivation: (id: UUID) => admin<OperatorAccess>(`/users/${id}/access/resend`, { method: "POST" }),
  generateOperatorTemporaryAccess: (id: UUID) =>
    admin<OperatorAccess>(`/users/${id}/access/temporary`, { method: "POST" }),
  toggleUser: (id: UUID) => admin<void>(`/users/${id}/toggle-active`, { method: "POST" }),
  assignUserRole: (id: UUID, adminRoleId: UUID) =>
    admin<void>(`/users/${id}/roles`, { method: "POST", body: jsonBody({ adminRoleId }) }),
  removeUserRole: (id: UUID, roleId: UUID) => admin<void>(`/users/${id}/roles/${roleId}`, { method: "DELETE" }),
  roles: (query: {
    search?: string;
    active?: boolean;
    page?: number;
    size?: number;
    sort?: string;
    direction?: string;
  }) => admin<PageResponse<AdminRole>>("/roles", { query }),
  roleHolders: (id: UUID) => admin<RoleHolder[]>(`/roles/${id}/operators`),
  bulkSetRolesActive: (ids: UUID[], active: boolean) =>
    admin<BulkOperationResult>("/roles/bulk/active", { method: "POST", body: jsonBody({ ids, active }) }),
  role: (id: UUID) => admin<AdminRole>(`/roles/${id}`),
  createRole: (input: { name: string; description?: string }) =>
    admin<AdminRole>("/roles", { method: "POST", body: jsonBody(input) }),
  updateRole: (id: UUID, input: { name: string; description?: string }) =>
    admin<AdminRole>(`/roles/${id}`, { method: "PUT", body: jsonBody(input) }),
  toggleRole: (id: UUID) => admin<void>(`/roles/${id}/toggle-active`, { method: "POST" }),
  grantRolePermission: (id: UUID, permissionId: UUID) =>
    admin<void>(`/roles/${id}/permissions`, { method: "POST", body: jsonBody({ permissionId }) }),
  revokeRolePermission: (id: UUID, permissionId: UUID) =>
    admin<void>(`/roles/${id}/permissions/${permissionId}`, { method: "DELETE" }),
  plans: () => admin<Plan[]>("/plans"),
  plan: (id: UUID) => admin<PlanDetail>(`/plans/${id}`),
  planFeatures: (id: UUID) => admin<PlanFeature[]>(`/plans/${id}/features`),
  planSubscribers: (id: UUID, query: { search?: string; status?: string; page?: number; size?: number }) =>
    admin<PageResponse<PlanSubscriber>>(`/plans/${id}/subscribers`, { query }),
  createPlan: (input: unknown) => admin<Plan>("/plans", { method: "POST", body: jsonBody(input) }),
  updatePlan: (id: UUID, input: unknown) => admin<Plan>(`/plans/${id}`, { method: "PUT", body: jsonBody(input) }),
  duplicatePlan: (id: UUID, input: unknown) =>
    admin<Plan>(`/plans/${id}/duplicate`, { method: "POST", body: jsonBody(input) }),
  revisePlan: (id: UUID, input: unknown) =>
    admin<Plan>(`/plans/${id}/revisions`, { method: "POST", body: jsonBody(input) }),
  transitionPlan: (id: UUID, status: string) =>
    admin<Plan>(`/plans/${id}/status`, { method: "PATCH", query: { status } }),
  previewPlanDeletion: (id: UUID) => admin<PlanDeletionPreview>(`/plans/${id}/deletion-preview`),
  deletePlan: (id: UUID, input: { confirmationCode: string; expectedVersion: number; previewToken: string }) =>
    admin<void>(`/plans/${id}`, { method: "DELETE", body: jsonBody(input) }),
  assignPlanFeature: (id: UUID, input: unknown) =>
    admin<PlanFeature>(`/plans/${id}/features`, { method: "POST", body: jsonBody(input) }),
  updatePlanFeature: (id: UUID, featureId: UUID, input: unknown) =>
    admin<PlanFeature>(`/plans/${id}/features/${featureId}`, { method: "PUT", body: jsonBody(input) }),
  removePlanFeature: (id: UUID, featureId: UUID) =>
    admin<void>(`/plans/${id}/features/${featureId}`, { method: "DELETE" }),
  addOns: () => admin<AddOn[]>("/add-ons"),
  createAddOn: (input: unknown) => admin<AddOn>("/add-ons", { method: "POST", body: jsonBody(input) }),
  updateAddOn: (id: UUID, input: unknown) => admin<AddOn>(`/add-ons/${id}`, { method: "PUT", body: jsonBody(input) }),
  deleteAddOn: (id: UUID) => admin<void>(`/add-ons/${id}`, { method: "DELETE" }),
  transitionAddOn: (id: UUID, status: string) =>
    admin<AddOn>(`/add-ons/${id}/status`, { method: "PATCH", query: { status } }),
  assignAddOnFeature: (id: UUID, input: unknown) =>
    admin<AddOn["features"][number]>(`/add-ons/${id}/features`, { method: "POST", body: jsonBody(input) }),
  updateAddOnFeature: (id: UUID, featureId: UUID, input: unknown) =>
    admin<AddOn["features"][number]>(`/add-ons/${id}/features/${featureId}`, {
      method: "PUT",
      body: jsonBody(input),
    }),
  removeAddOnFeature: (id: UUID, featureId: UUID) =>
    admin<void>(`/add-ons/${id}/features/${featureId}`, { method: "DELETE" }),
  quotaPackages: () => admin<QuotaPackage[]>("/quota-packages"),
  createQuotaPackage: (input: unknown) =>
    admin<QuotaPackage>("/quota-packages", { method: "POST", body: jsonBody(input) }),
  updateQuotaPackage: (id: UUID, input: unknown) =>
    admin<QuotaPackage>(`/quota-packages/${id}`, { method: "PUT", body: jsonBody(input) }),
  deleteQuotaPackage: (id: UUID) => admin<void>(`/quota-packages/${id}`, { method: "DELETE" }),
  transitionQuotaPackage: (id: UUID, status: string) =>
    admin<QuotaPackage>(`/quota-packages/${id}/status`, { method: "PATCH", query: { status } }),
  subscription: (accountId: UUID) => admin<AdminSubscription>(`/subscriptions/account/${accountId}`),
  accounts: (query: { query?: string; page?: number; size?: number }) =>
    admin<PageResponse<AccountDirectoryEntry>>("/subscriptions/accounts/search", { query }),
  subscriptionChanges: (accountId: UUID) =>
    admin<SubscriptionChangeOperation[]>(`/subscriptions/account/${accountId}/changes`),
  createSubscription: (accountId: UUID, planCode: string) =>
    admin<Subscription>(`/subscriptions/account/${accountId}`, { method: "POST", query: { planCode } }),
  createTrial: (accountId: UUID, planCode: string, trialDays: number) =>
    admin<Subscription>(`/subscriptions/account/${accountId}/trial`, {
      method: "POST",
      query: { planCode, trialDays },
    }),
  updateSubscriptionOverrides: (accountId: UUID, input: unknown) =>
    admin<Subscription>(`/subscriptions/account/${accountId}/overrides`, { method: "PATCH", body: jsonBody(input) }),
  confirmCheckout: (checkoutId: UUID, input: unknown) =>
    admin<unknown>(`/subscriptions/checkouts/${checkoutId}/confirm-manual`, { method: "POST", body: jsonBody(input) }),
  registryInventory: () => admin<RegistryModule[]>("/registry/inventory"),
  permissionCatalog: () =>
    admin<RegistryModule[]>("/registry/permission-catalog", { query: { audience: "PLATFORM_ADMIN_ROLE_GRANTABLE" } }),
  latestRegistrySync: () => admin<RegistrySyncRun>("/registry/synchronization/latest"),
  featureHistory: (id: UUID) => admin<FeatureOperationalChange[]>(`/registry/features/${id}/control-history`),
  updateFeatureControl: (
    id: UUID,
    control: "public-visibility" | "new-sales" | "new-grants",
    enabled: boolean,
    reason: string,
  ) => admin<void>(`/registry/features/${id}/${control}`, { method: "PATCH", body: jsonBody({ enabled, reason }) }),
  updateEmergencyRuntime: (
    id: UUID,
    enabled: boolean,
    reason: string,
    impactConfirmed: boolean,
    communicationConfirmed: boolean,
  ) =>
    admin<void>(`/registry/features/${id}/emergency-runtime`, {
      method: "PATCH",
      body: jsonBody({ enabled, reason, impactConfirmed, communicationConfirmed }),
    }),
  flattenFeatures: (modules: RegistryModule[]): RegistryFeature[] => modules.flatMap((module) => module.features),
};
