import type {
  AccountDirectoryEntry,
  AddOn,
  AddOnActivationPreview,
  AddOnChooserItem,
  AddOnInput,
  AddOnOperationalItem,
  AdminAccessOverview,
  AdminMe,
  AdminPermission,
  AdminRole,
  AdminRoleHistoryEntry,
  AdminRoleImpact,
  AdminRolePreset,
  AdminRoleStatus,
  AdminSubscription,
  AdminSubscriptionChangeApplyInput,
  AdminSubscriptionChangeOperation,
  AdminUser,
  AdminUserCreation,
  AssignAddOnFeatureInput,
  AssignablePlanPrice,
  AssignPlanFeatureInput,
  AuthResponse,
  BillingCycle,
  BulkOperationResult,
  ClientPlanCatalog,
  CommercialAvailabilityHistoryEntry,
  CommercialLifecycleInput,
  CommercialOverview,
  CommercialPolicyActivation,
  CommercialPolicyActivationAudience,
  CommercialPolicyActivationPreview,
  CommercialPolicyAudiencePreview,
  CommercialPolicyComparison,
  CommercialPolicyDetail,
  CommercialPolicyHistory,
  CommercialPolicyOwner,
  CommercialPolicyRevision,
  CommercialPolicySource,
  CommercialPolicyStatus,
  CommercialPolicySummary,
  CommercialPolicyTargetKind,
  CommercialPolicyWriteInput,
  CommercialProductType,
  CreatePlanInput,
  ExtensionCompatibility,
  FeatureCatalogAudience,
  FeatureOperationalChange,
  ManualCheckoutConfirmationInput,
  OperatorAccess,
  PageResponse,
  PermissionCatalogAudience,
  Plan,
  PlanActivationPreview,
  PlanAvailabilityPreview,
  PlanBranchInput,
  PlanChooserItem,
  PlanDeletionPreview,
  PlanDetail,
  PlanExtensionPolicy,
  PlanFeature,
  PlanOperationalItem,
  PlanSubscriber,
  PlanSubscriberOwnerLookup,
  ProductPrice,
  ProductPriceActivationPreview,
  ProductPriceActivationRequest,
  ProductPriceBillingCycle,
  ProductPriceHistoryEntry,
  ProductPriceInput,
  ProductPriceOwnerType,
  ProductPriceReplacementPreview,
  ProductPriceReplacementResult,
  ProductPriceSelection,
  ProductPriceStatus,
  ProductSalesVisibility,
  ProductVisibilityPreview,
  QuotaPackage,
  QuotaPackageActivationPreview,
  QuotaPackageChooserItem,
  QuotaPackageComparison,
  QuotaPackageHistoryEntry,
  QuotaPackageInput,
  QuotaPackageOperationalItem,
  QuotaPackageRevisionResult,
  RegistryFeature,
  RegistryModule,
  RegistrySyncRun,
  RoleHolder,
  Subscription,
  SubscriptionAccountListItem,
  SubscriptionAccountOwnerLookup,
  SubscriptionAddOnOverrideChoice,
  SubscriptionChangeApplyResponse,
  SubscriptionChangeInput,
  SubscriptionChangeOperation,
  SubscriptionChangePreview,
  SubscriptionCheckout,
  SubscriptionOverrideChoicePage,
  SubscriptionOverridesInput,
  SubscriptionQuotaPackageOverrideChoice,
  SubscriptionStatus,
  UpdatePlanInput,
  UUID,
} from "@/api/contracts";
import { apiRequest, jsonBody } from "@/api/http";

const admin = <T>(path: string, options: Parameters<typeof apiRequest<T>>[1] = {}) =>
  apiRequest<T>(`/api/admin${path}`, { ...options, audience: "admin" });

export const adminApi = {
  me: () => admin<AdminMe>("/me"),
  sendMyEmailVerification: () => admin<OperatorAccess>("/me/email-verification", { method: "POST" }),
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
  createUser: (input: {
    firstName: string;
    lastName: string;
    email: string;
    initialAccessMethod: "EMAIL_LINK" | "TEMPORARY_PASSWORD";
    isSuperAdmin: boolean;
  }) => admin<AdminUserCreation>("/users", { method: "POST", body: jsonBody(input) }),
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
  completeEmailVerification: (token: string) =>
    apiRequest<void>("/api/admin/auth/email-verification/complete", {
      method: "POST",
      body: jsonBody({ token }),
    }),
  /** Single-target by design: a name identifies one person, so there is no bulk form. */
  renameOperator: (id: UUID, input: { firstName: string; lastName: string }) =>
    admin<AdminUser>(`/users/${id}`, { method: "PATCH", body: jsonBody(input) }),
  changeOperatorEmail: (id: UUID, email: string) =>
    admin<AdminUser>(`/users/${id}/email`, { method: "PATCH", body: jsonBody({ email }) }),
  /** Completes the change forced after a temporary password, returning a real admin session. */
  changeInitialPassword: (initialAccessToken: string, newPassword: string) =>
    apiRequest<AuthResponse>("/api/admin/auth/initial-password/change", {
      method: "POST",
      headers: { Authorization: `Bearer ${initialAccessToken}` },
      body: jsonBody({ newPassword }),
    }),
  /** Abandons a pending initial-password change, freeing the operator from that screen. */
  logoutInitialAccess: (initialAccessToken: string) =>
    apiRequest<void>("/api/admin/auth/initial-password/logout", {
      method: "POST",
      headers: { Authorization: `Bearer ${initialAccessToken}` },
    }),
  operatorPermissions: (id: UUID) => admin<AdminPermission[]>(`/users/${id}/permissions`),
  resendOperatorActivation: (id: UUID) => admin<OperatorAccess>(`/users/${id}/access/resend`, { method: "POST" }),
  sendOperatorEmailVerification: (id: UUID) =>
    admin<OperatorAccess>(`/users/${id}/email-verification`, { method: "POST" }),
  generateOperatorTemporaryAccess: (id: UUID) =>
    admin<OperatorAccess>(`/users/${id}/access/temporary`, { method: "POST" }),
  toggleUser: (id: UUID) => admin<void>(`/users/${id}/toggle-active`, { method: "POST" }),
  assignUserRole: (id: UUID, adminRoleId: UUID) => admin<void>(`/users/${id}/roles/${adminRoleId}`, { method: "POST" }),
  removeUserRole: (id: UUID, roleId: UUID) => admin<void>(`/users/${id}/roles/${roleId}`, { method: "DELETE" }),
  replaceUserRoles: (id: UUID, roleIds: UUID[]) =>
    admin<void>(`/users/${id}/roles`, { method: "PUT", body: jsonBody({ roleIds }) }),
  roles: (query: {
    search?: string;
    active?: boolean;
    status?: AdminRoleStatus;
    page?: number;
    size?: number;
    sort?: string;
    direction?: string;
  }) => admin<PageResponse<AdminRole>>("/roles", { query }),
  roleHolders: (id: UUID) => admin<RoleHolder[]>(`/roles/${id}/operators`),
  roleHistory: (id: UUID) => admin<AdminRoleHistoryEntry[]>(`/roles/${id}/history`),
  rolePresets: () => admin<AdminRolePreset[]>("/role-presets"),
  grantableRolePermissions: () => admin<AdminPermission[]>("/roles/grantable-permissions"),
  bulkSetRolesActive: (ids: UUID[], active: boolean) =>
    admin<BulkOperationResult>("/roles/bulk/active", { method: "POST", body: jsonBody({ ids, active }) }),
  role: (id: UUID) => admin<AdminRole>(`/roles/${id}`),
  createRole: (input: { name: string; description?: string; permissionIds?: UUID[] }) =>
    admin<AdminRole>("/roles", { method: "POST", body: jsonBody(input) }),
  createRoleFromPreset: (input: { presetCode: string; name: string; description?: string; permissionIds: UUID[] }) =>
    admin<AdminRole>("/roles/from-preset", { method: "POST", body: jsonBody(input) }),
  duplicateRole: (id: UUID, input: { name: string; description?: string }) =>
    admin<AdminRole>(`/roles/${id}/duplicate`, { method: "POST", body: jsonBody(input) }),
  updateRole: (id: UUID, input: { name: string; description?: string; expectedVersion: number }) =>
    admin<AdminRole>(`/roles/${id}/metadata`, { method: "PATCH", body: jsonBody(input) }),
  previewRoleImpact: (id: UUID, input: { permissionIds?: UUID[]; status?: AdminRoleStatus }) =>
    admin<AdminRoleImpact>(`/roles/${id}/impact-preview`, { method: "POST", body: jsonBody(input) }),
  replaceRolePermissions: (
    id: UUID,
    input: { permissionIds: UUID[]; expectedVersion: number; confirmedAssignmentCount: number },
  ) => admin<AdminRole>(`/roles/${id}/permissions`, { method: "PUT", body: jsonBody(input) }),
  transitionRoleStatus: (
    id: UUID,
    input: { status: AdminRoleStatus; expectedVersion: number; confirmedAssignmentCount: number },
  ) => admin<AdminRole>(`/roles/${id}/status`, { method: "POST", body: jsonBody(input) }),
  deleteRole: (id: UUID) => admin<void>(`/roles/${id}`, { method: "DELETE" }),
  toggleRole: (id: UUID) => admin<void>(`/roles/${id}/toggle-active`, { method: "POST" }),
  grantRolePermission: (id: UUID, permissionId: UUID) =>
    admin<void>(`/roles/${id}/permissions`, { method: "POST", body: jsonBody({ permissionId }) }),
  revokeRolePermission: (id: UUID, permissionId: UUID) =>
    admin<void>(`/roles/${id}/permissions/${permissionId}`, { method: "DELETE" }),
  operationalPlans: (
    query: {
      search?: string;
      status?: string;
      salesVisibility?: ProductSalesVisibility;
      extensionPolicy?: PlanExtensionPolicy;
      lineageId?: UUID;
      page?: number;
      size?: number;
      sort?: string;
      direction?: string;
    } = {},
  ) => admin<PageResponse<PlanOperationalItem>>("/plans", { query }),
  planChoices: (
    query: { search?: string; salesVisibility?: ProductSalesVisibility; page?: number; size?: number } = {},
  ) => admin<PageResponse<PlanChooserItem>>("/plans/chooser", { query }),
  selectedPlanChoices: (ids: UUID[]) => admin<PlanChooserItem[]>("/plans/chooser/selected", { query: { ids } }),
  selectedPlanCodeChoices: (codes: string[]) =>
    admin<PlanChooserItem[]>("/plans/chooser/selected-codes", { query: { codes } }),
  plan: (id: UUID) => admin<PlanDetail>(`/plans/${id}`),
  planOperations: (id: UUID) => admin<PlanOperationalItem>(`/plans/${id}/operations`),
  planFeatures: (id: UUID) => admin<PlanFeature[]>(`/plans/${id}/features`),
  planSubscribers: (id: UUID, query: { search?: string; status?: string; page?: number; size?: number }) =>
    admin<PageResponse<PlanSubscriber>>(`/plans/${id}/subscribers`, { query }),
  planSubscribersByOwnerEmail: (id: UUID, input: { ownerEmail: string; page?: number; size?: number }) => {
    const { ownerEmail, ...query } = input;
    return admin<PageResponse<PlanSubscriberOwnerLookup>>(`/plans/${id}/subscribers/by-owner-email`, {
      method: "POST",
      query,
      body: jsonBody({ ownerEmail }),
    });
  },
  createPlan: (input: CreatePlanInput) => admin<Plan>("/plans", { method: "POST", body: jsonBody(input) }),
  updatePlan: (id: UUID, input: UpdatePlanInput) =>
    admin<Plan>(`/plans/${id}`, { method: "PUT", body: jsonBody(input) }),
  duplicatePlan: (id: UUID, expectedVersionOrInput: number | PlanBranchInput, maybeInput?: PlanBranchInput) =>
    admin<Plan>(`/plans/${id}/duplicate`, {
      method: "POST",
      query: { expectedVersion: typeof expectedVersionOrInput === "number" ? expectedVersionOrInput : undefined },
      body: jsonBody(maybeInput ?? (expectedVersionOrInput as PlanBranchInput)),
    }),
  revisePlan: (id: UUID, expectedVersionOrInput: number | PlanBranchInput, maybeInput?: PlanBranchInput) =>
    admin<Plan>(`/plans/${id}/revisions`, {
      method: "POST",
      query: { expectedVersion: typeof expectedVersionOrInput === "number" ? expectedVersionOrInput : undefined },
      body: jsonBody(maybeInput ?? (expectedVersionOrInput as PlanBranchInput)),
    }),
  previewPlanActivation: (id: UUID) => admin<PlanActivationPreview>(`/plans/${id}/activation-preview`),
  changePlanLifecycle: (id: UUID, input: CommercialLifecycleInput) =>
    admin<Plan>(`/plans/${id}/lifecycle`, { method: "POST", body: jsonBody(input) }),
  previewPlanDeletion: (id: UUID) => admin<PlanDeletionPreview>(`/plans/${id}/deletion-preview`),
  deletePlan: (id: UUID, input: { confirmationName: string; expectedVersion: number; previewToken: string }) =>
    admin<void>(`/plans/${id}`, { method: "DELETE", body: jsonBody(input) }),
  assignPlanFeature: (
    id: UUID,
    expectedVersionOrInput: number | AssignPlanFeatureInput,
    maybeInput?: AssignPlanFeatureInput,
  ) =>
    admin<PlanFeature>(`/plans/${id}/features`, {
      method: "POST",
      query: { expectedVersion: typeof expectedVersionOrInput === "number" ? expectedVersionOrInput : undefined },
      body: jsonBody(maybeInput ?? (expectedVersionOrInput as AssignPlanFeatureInput)),
    }),
  updatePlanFeature: (
    id: UUID,
    featureId: UUID,
    expectedVersionOrInput: number | AssignPlanFeatureInput,
    maybeInput?: AssignPlanFeatureInput,
  ) =>
    admin<PlanFeature>(`/plans/${id}/features/${featureId}`, {
      method: "PUT",
      query: { expectedVersion: typeof expectedVersionOrInput === "number" ? expectedVersionOrInput : undefined },
      body: jsonBody(maybeInput ?? (expectedVersionOrInput as AssignPlanFeatureInput)),
    }),
  removePlanFeature: (id: UUID, featureId: UUID, expectedVersion?: number) =>
    admin<void>(`/plans/${id}/features/${featureId}`, { method: "DELETE", query: { expectedVersion } }),
  operationalAddOns: (
    query: {
      search?: string;
      status?: string;
      salesVisibility?: ProductSalesVisibility;
      lineageId?: UUID;
      featureCode?: string;
      targetPlanCode?: string;
      page?: number;
      size?: number;
      sort?: string;
      direction?: string;
    } = {},
  ) => admin<PageResponse<AddOnOperationalItem>>("/add-ons", { query }),
  addOnChoices: (
    query: {
      search?: string;
      salesVisibility?: ProductSalesVisibility;
      featureCode?: string;
      page?: number;
      size?: number;
    } = {},
  ) => admin<PageResponse<AddOnChooserItem>>("/add-ons/chooser", { query }),
  selectedAddOnChoices: (ids: UUID[]) => admin<AddOnChooserItem[]>("/add-ons/chooser/selected", { query: { ids } }),
  selectedAddOnCodeChoices: (codes: string[]) =>
    admin<AddOnChooserItem[]>("/add-ons/chooser/selected-codes", { query: { codes } }),
  addOn: (id: UUID) => admin<AddOn>(`/add-ons/${id}`),
  addOnOperations: (id: UUID) => admin<AddOnOperationalItem>(`/add-ons/${id}/operations`),
  createAddOn: (input: AddOnInput) => admin<AddOn>("/add-ons", { method: "POST", body: jsonBody(input) }),
  reviseAddOn: (id: UUID, expectedVersion?: number) =>
    admin<AddOn>(`/add-ons/${id}/revisions`, { method: "POST", query: { expectedVersion } }),
  updateAddOn: (id: UUID, input: AddOnInput) =>
    admin<AddOn>(`/add-ons/${id}`, { method: "PUT", body: jsonBody(input) }),
  deleteAddOn: (id: UUID, expectedVersion: number) =>
    admin<void>(`/add-ons/${id}`, { method: "DELETE", query: { expectedVersion } }),
  previewAddOnActivation: (id: UUID) => admin<AddOnActivationPreview>(`/add-ons/${id}/activation-preview`),
  changeAddOnLifecycle: (id: UUID, input: CommercialLifecycleInput) =>
    admin<AddOn>(`/add-ons/${id}/lifecycle`, { method: "POST", body: jsonBody(input) }),
  assignAddOnFeature: (id: UUID, expectedVersion: number, input: AssignAddOnFeatureInput) =>
    admin<AddOn["features"][number]>(`/add-ons/${id}/features`, {
      method: "POST",
      query: { expectedVersion },
      body: jsonBody(input),
    }),
  updateAddOnFeature: (id: UUID, featureId: UUID, expectedVersion: number, input: AssignAddOnFeatureInput) =>
    admin<AddOn["features"][number]>(`/add-ons/${id}/features/${featureId}`, {
      method: "PUT",
      query: { expectedVersion },
      body: jsonBody(input),
    }),
  removeAddOnFeature: (id: UUID, featureId: UUID, expectedVersion: number) =>
    admin<void>(`/add-ons/${id}/features/${featureId}`, { method: "DELETE", query: { expectedVersion } }),
  operationalQuotaPackages: (
    query: {
      search?: string;
      status?: string;
      salesVisibility?: ProductSalesVisibility;
      lineageId?: UUID;
      featureCode?: string;
      resource?: string;
      targetPlanCode?: string;
      targetAddOnCode?: string;
      page?: number;
      size?: number;
      sort?: string;
      direction?: string;
    } = {},
  ) => admin<PageResponse<QuotaPackageOperationalItem>>("/quota-packages", { query }),
  quotaPackageChoices: (
    query: {
      search?: string;
      salesVisibility?: ProductSalesVisibility;
      featureCode?: string;
      resource?: string;
      page?: number;
      size?: number;
    } = {},
  ) => admin<PageResponse<QuotaPackageChooserItem>>("/quota-packages/chooser", { query }),
  selectedQuotaPackageChoices: (ids: UUID[]) =>
    admin<QuotaPackageChooserItem[]>("/quota-packages/chooser/selected", { query: { ids } }),
  selectedQuotaPackageCodeChoices: (codes: string[]) =>
    admin<QuotaPackageChooserItem[]>("/quota-packages/chooser/selected-codes", { query: { codes } }),
  quotaPackage: (id: UUID) => admin<QuotaPackage>(`/quota-packages/${id}`),
  quotaPackageOperations: (id: UUID) => admin<QuotaPackageOperationalItem>(`/quota-packages/${id}/operations`),
  createQuotaPackage: (input: QuotaPackageInput) =>
    admin<QuotaPackage>("/quota-packages", { method: "POST", body: jsonBody(input) }),
  updateQuotaPackage: (id: UUID, input: QuotaPackageInput) =>
    admin<QuotaPackage>(`/quota-packages/${id}`, { method: "PUT", body: jsonBody(input) }),
  deleteQuotaPackage: (id: UUID, expectedVersion: number) =>
    admin<void>(`/quota-packages/${id}`, { method: "DELETE", query: { expectedVersion } }),
  reviseQuotaPackage: (id: UUID, expectedVersion: number, reason: string) =>
    admin<QuotaPackageRevisionResult>(`/quota-packages/${id}/revisions`, {
      method: "POST",
      body: jsonBody({ expectedVersion, reason }),
    }),
  compareQuotaPackage: (id: UUID, candidateId: UUID) =>
    admin<QuotaPackageComparison>(`/quota-packages/${id}/comparison`, {
      query: { againstQuotaPackageId: candidateId },
    }),
  previewQuotaPackageActivation: (id: UUID) =>
    admin<QuotaPackageActivationPreview>(`/quota-packages/${id}/activation-preview`),
  changeQuotaPackageLifecycle: (
    id: UUID,
    input: {
      action: "ACTIVATE" | "DEACTIVATE" | "ARCHIVE";
      expectedVersion: number;
      reason: string;
      activationPreviewToken?: string | null;
    },
  ) => admin<QuotaPackage>(`/quota-packages/${id}/lifecycle`, { method: "POST", body: jsonBody(input) }),
  quotaPackageHistory: (id: UUID, page = 0, size = 20) =>
    admin<PageResponse<QuotaPackageHistoryEntry>>(`/quota-packages/${id}/history`, { query: { page, size } }),
  inspectPlanCompatibility: (
    id: UUID,
    query: {
      search?: string;
      type?: CommercialProductType;
      available?: boolean;
      currencyCode?: string;
      billingCycle?: BillingCycle;
      page?: number;
      size?: number;
    } = {},
  ) => admin<PageResponse<ExtensionCompatibility>>(`/plans/${id}/extensions/compatibility`, { query }),
  previewPlanAvailability: (id: UUID, extensionPolicy: PlanExtensionPolicy, salesVisibility: ProductSalesVisibility) =>
    admin<PlanAvailabilityPreview>(`/plans/${id}/commercial-availability/preview`, {
      method: "POST",
      body: jsonBody({ extensionPolicy, salesVisibility }),
    }),
  updatePlanAvailability: (
    id: UUID,
    input: {
      expectedVersion: number;
      extensionPolicy: PlanExtensionPolicy;
      salesVisibility: ProductSalesVisibility;
      reason: string;
      previewToken: string;
    },
  ) => admin<Plan>(`/plans/${id}/commercial-availability`, { method: "PATCH", body: jsonBody(input) }),
  previewAddOnVisibility: (id: UUID, salesVisibility: ProductSalesVisibility) =>
    admin<ProductVisibilityPreview>(`/add-ons/${id}/sales-visibility/preview`, {
      method: "POST",
      body: jsonBody({ salesVisibility }),
    }),
  updateAddOnVisibility: (
    id: UUID,
    input: { expectedVersion: number; salesVisibility: ProductSalesVisibility; reason: string; previewToken: string },
  ) => admin<AddOn>(`/add-ons/${id}/sales-visibility`, { method: "PATCH", body: jsonBody(input) }),
  previewQuotaPackageVisibility: (id: UUID, salesVisibility: ProductSalesVisibility) =>
    admin<ProductVisibilityPreview>(`/quota-packages/${id}/sales-visibility/preview`, {
      method: "POST",
      body: jsonBody({ salesVisibility }),
    }),
  updateQuotaPackageVisibility: (
    id: UUID,
    input: { expectedVersion: number; salesVisibility: ProductSalesVisibility; reason: string; previewToken: string },
  ) => admin<QuotaPackage>(`/quota-packages/${id}/sales-visibility`, { method: "PATCH", body: jsonBody(input) }),
  commercialAvailabilityHistory: (id: UUID, page = 0, size = 20) =>
    admin<PageResponse<CommercialAvailabilityHistoryEntry>>(`/commercial-products/${id}/availability-history`, {
      query: { page, size },
    }),
  productPrices: (query: {
    search?: string;
    ownerType?: ProductPriceOwnerType;
    ownerId?: UUID;
    status?: ProductPriceStatus;
    currencyCode?: string;
    billingCycle?: ProductPriceBillingCycle;
    page?: number;
    size?: number;
    sort?: string;
    direction?: string;
  }) => admin<PageResponse<ProductPrice>>("/product-prices", { query }),
  productPrice: (id: UUID) => admin<ProductPrice>(`/product-prices/${id}`),
  productPriceHistory: (id: UUID, page = 0, size = 20) =>
    admin<PageResponse<ProductPriceHistoryEntry>>(`/product-prices/${id}/history`, { query: { page, size } }),
  createProductPrice: (ownerType: ProductPriceOwnerType, ownerId: UUID, input: ProductPriceInput) =>
    admin<ProductPrice>("/product-prices", {
      method: "POST",
      query: { ownerType, ownerId },
      body: jsonBody(input),
    }),
  updateProductPrice: (id: UUID, input: ProductPriceInput & { version: number }) =>
    admin<ProductPrice>(`/product-prices/${id}`, { method: "PUT", body: jsonBody(input) }),
  previewProductPriceActivation: (id: UUID) =>
    admin<ProductPriceActivationPreview>(`/product-prices/${id}/activation-preview`),
  activateProductPrice: (id: UUID, request: ProductPriceActivationRequest) =>
    admin<ProductPrice>(`/product-prices/${id}/activate`, {
      method: "POST",
      body: jsonBody(request),
    }),
  pauseProductPrice: (id: UUID, version: number, reason: string) =>
    admin<ProductPrice>(`/product-prices/${id}/pause`, { method: "POST", body: jsonBody({ version, reason }) }),
  reactivateProductPrice: (id: UUID, request: ProductPriceActivationRequest) =>
    admin<ProductPrice>(`/product-prices/${id}/reactivate`, {
      method: "POST",
      body: jsonBody(request),
    }),
  reviseProductPrice: (id: UUID, version: number, reason: string) =>
    admin<ProductPrice>(`/product-prices/${id}/revisions`, {
      method: "POST",
      body: jsonBody({ version, reason }),
    }),
  previewProductPriceReplacement: (
    successorId: UUID,
    input: { currentPriceId: UUID; currentVersion: number; successorVersion: number },
  ) =>
    admin<ProductPriceReplacementPreview>(`/product-prices/${successorId}/replacement-preview`, {
      method: "POST",
      body: jsonBody(input),
    }),
  scheduleProductPriceReplacement: (
    successorId: UUID,
    input: { currentPriceId: UUID; currentVersion: number; successorVersion: number; reason: string },
  ) =>
    admin<ProductPriceReplacementResult>(`/product-prices/${successorId}/schedule-replacement`, {
      method: "POST",
      body: jsonBody(input),
    }),
  archiveProductPrice: (id: UUID, version: number, reason: string) =>
    admin<ProductPrice>(`/product-prices/${id}/archive`, {
      method: "POST",
      body: jsonBody({ version, reason }),
    }),
  deleteProductPrice: (id: UUID, version: number) =>
    admin<void>(`/product-prices/${id}`, { method: "DELETE", query: { version } }),
  commercialPolicies: (query: {
    search?: string;
    status?: CommercialPolicyStatus;
    targetKind?: CommercialPolicyTargetKind;
    source?: CommercialPolicySource;
    effectiveAt?: string;
    includeArchived?: boolean;
    page?: number;
    size?: number;
    sort?: string;
    direction?: "asc" | "desc";
  }) => admin<PageResponse<CommercialPolicySummary>>("/commercial-policies", { query }),
  commercialPolicy: (id: UUID) => admin<CommercialPolicyDetail>(`/commercial-policies/${id}`),
  createCommercialPolicy: (input: CommercialPolicyWriteInput) =>
    admin<CommercialPolicyDetail>("/commercial-policies", { method: "POST", body: jsonBody(input) }),
  updateCommercialPolicy: (id: UUID, input: CommercialPolicyWriteInput & { version: number }) =>
    admin<CommercialPolicyDetail>(`/commercial-policies/${id}`, { method: "PUT", body: jsonBody(input) }),
  duplicateCommercialPolicy: (id: UUID, input: { version: number; name: string; reason: string }) =>
    admin<CommercialPolicyDetail>(`/commercial-policies/${id}/duplicate`, {
      method: "POST",
      body: jsonBody(input),
    }),
  reviseCommercialPolicy: (id: UUID, input: { version: number; reason: string }) =>
    admin<CommercialPolicyDetail>(`/commercial-policies/${id}/revisions`, {
      method: "POST",
      body: jsonBody(input),
    }),
  commercialPolicyRevisions: (id: UUID, page = 0, size = 20) =>
    admin<PageResponse<CommercialPolicyRevision>>(`/commercial-policies/${id}/revisions`, {
      query: { page, size },
    }),
  compareCommercialPolicies: (id: UUID, comparedId: UUID) =>
    admin<CommercialPolicyComparison>(`/commercial-policies/${id}/compare/${comparedId}`),
  commercialPolicyHistory: (id: UUID, page = 0, size = 20) =>
    admin<PageResponse<CommercialPolicyHistory>>(`/commercial-policies/${id}/history`, { query: { page, size } }),
  commercialPolicyActivations: (id: UUID, page = 0, size = 20) =>
    admin<PageResponse<CommercialPolicyActivation>>(`/commercial-policies/${id}/activations`, {
      query: { page, size },
    }),
  commercialPolicyActivationAudience: (id: UUID, activationId: UUID, page = 0, size = 20) =>
    admin<CommercialPolicyActivationAudience>(`/commercial-policies/${id}/activations/${activationId}/accounts`, {
      query: { page, size },
    }),
  commercialPolicyAudience: (id: UUID, page = 0, size = 20) =>
    admin<CommercialPolicyAudiencePreview>(`/commercial-policies/${id}/audience`, { query: { page, size } }),
  previewCommercialPolicyActivation: (id: UUID) =>
    admin<CommercialPolicyActivationPreview>(`/commercial-policies/${id}/activation-preview`),
  activateCommercialPolicy: (id: UUID, input: { version: number; reason: string; activationPreviewToken: string }) =>
    admin<CommercialPolicyDetail>(`/commercial-policies/${id}/activate`, {
      method: "POST",
      body: jsonBody(input),
    }),
  resumeCommercialPolicy: (id: UUID, input: { version: number; reason: string; activationPreviewToken: string }) =>
    admin<CommercialPolicyDetail>(`/commercial-policies/${id}/resume`, {
      method: "POST",
      body: jsonBody(input),
    }),
  pauseCommercialPolicy: (id: UUID, input: { version: number; reason: string }) =>
    admin<CommercialPolicyDetail>(`/commercial-policies/${id}/pause`, { method: "POST", body: jsonBody(input) }),
  endCommercialPolicy: (id: UUID, input: { version: number; reason: string }) =>
    admin<CommercialPolicyDetail>(`/commercial-policies/${id}/end`, { method: "POST", body: jsonBody(input) }),
  archiveCommercialPolicy: (id: UUID, input: { version: number; reason: string }) =>
    admin<CommercialPolicyDetail>(`/commercial-policies/${id}/archive`, { method: "POST", body: jsonBody(input) }),
  deleteCommercialPolicy: (id: UUID, input: { version: number; reason: string }) =>
    admin<void>(`/commercial-policies/${id}`, { method: "DELETE", body: jsonBody(input) }),
  commercialPolicyOwner: (id: UUID) => admin<CommercialPolicyOwner>(`/commercial-policies/${id}/owner`),
  reassignCommercialPolicyOwner: (id: UUID, input: { version: number; ownerAdminUserId: UUID; reason: string }) =>
    admin<CommercialPolicyDetail>(`/commercial-policies/${id}/owner`, { method: "PUT", body: jsonBody(input) }),
  commercialPolicyAccountChoices: (query: { query?: string; active?: boolean; page?: number; size?: number }) =>
    admin<PageResponse<AccountDirectoryEntry>>("/commercial-policies/account-choices", { query }),
  resolveCommercialPolicyAccountChoices: (ids: UUID[]) =>
    admin<AccountDirectoryEntry[]>("/commercial-policies/account-choices/selected", { query: { ids } }),
  subscription: (accountId: UUID) => admin<AdminSubscription>(`/subscriptions/account/${accountId}`),
  accounts: (query: {
    query?: string;
    accountActive?: boolean;
    subscriptionStatus?: SubscriptionStatus;
    hasSubscription?: boolean;
    page?: number;
    size?: number;
    sort?: "name" | "slug" | "active" | "createdAt";
    direction?: "asc" | "desc";
  }) => admin<PageResponse<SubscriptionAccountListItem>>("/subscriptions/accounts/search", { query }),
  subscriptionAccountsByOwnerEmail: (input: {
    ownerEmail: string;
    accountActive?: boolean;
    subscriptionStatus?: SubscriptionStatus;
    hasSubscription?: boolean;
    planCode?: string;
    page?: number;
    size?: number;
    sort?: "name" | "slug" | "active" | "createdAt";
    direction?: "asc" | "desc";
  }) => {
    const { ownerEmail, ...query } = input;
    return admin<PageResponse<SubscriptionAccountOwnerLookup>>("/subscriptions/accounts/by-owner-email", {
      method: "POST",
      query,
      body: jsonBody({ ownerEmail }),
    });
  },
  chooseSubscriptionAccounts: (query: {
    query?: string;
    active?: boolean;
    page?: number;
    size?: number;
    sort?: "name" | "slug";
    direction?: "asc" | "desc";
  }) => admin<PageResponse<AccountDirectoryEntry>>("/subscriptions/accounts/chooser", { query }),
  resolveSubscriptionAccounts: (ids: UUID[]) =>
    admin<AccountDirectoryEntry[]>("/subscriptions/accounts/chooser/selected", { query: { ids } }),
  subscriptionChangeCatalog: (accountId: UUID) =>
    admin<ClientPlanCatalog>(`/subscriptions/account/${accountId}/change-catalog`),
  subscriptionChanges: (
    accountId: UUID,
    query: {
      page?: number;
      size?: number;
      sort?: "createdAt" | "effectiveAt" | "status" | "timing";
      direction?: "asc" | "desc";
    } = {},
  ) => admin<PageResponse<AdminSubscriptionChangeOperation>>(`/subscriptions/account/${accountId}/changes`, { query }),
  previewSubscriptionChange: (accountId: UUID, input: SubscriptionChangeInput) =>
    admin<SubscriptionChangePreview>(`/subscriptions/account/${accountId}/changes/preview`, {
      method: "POST",
      body: jsonBody(input),
    }),
  applySubscriptionChange: (accountId: UUID, input: AdminSubscriptionChangeApplyInput) =>
    admin<SubscriptionChangeApplyResponse>(`/subscriptions/account/${accountId}/changes/apply`, {
      method: "POST",
      body: jsonBody(input),
    }),
  cancelSubscriptionChange: (accountId: UUID, operationId: UUID, reason: string) =>
    admin<SubscriptionChangeOperation>(`/subscriptions/account/${accountId}/changes/${operationId}/cancel`, {
      method: "POST",
      body: jsonBody({ reason }),
    }),
  assignablePlanPrices: (query: {
    search?: string;
    currencyCode?: string;
    billingCycle?: ProductPriceBillingCycle;
    page?: number;
    size?: number;
    sort?: "planCode" | "planName" | "amount" | "currencyCode" | "billingCycle" | "effectiveFrom";
    direction?: "asc" | "desc";
  }) => admin<PageResponse<AssignablePlanPrice>>("/subscriptions/assignable-plan-prices", { query }),
  createSubscription: (accountId: UUID, planCode: string, priceSelection: ProductPriceSelection) =>
    admin<Subscription>(`/subscriptions/account/${accountId}`, {
      method: "POST",
      query: { planCode },
      body: jsonBody(priceSelection),
    }),
  createTrial: (accountId: UUID, planCode: string, trialDays: number, priceSelection: ProductPriceSelection) =>
    admin<Subscription>(`/subscriptions/account/${accountId}/trial`, {
      method: "POST",
      query: { planCode, trialDays },
      body: jsonBody(priceSelection),
    }),
  updateSubscriptionOverrides: (accountId: UUID, input: SubscriptionOverridesInput) =>
    admin<Subscription>(`/subscriptions/account/${accountId}/overrides`, { method: "PATCH", body: jsonBody(input) }),
  subscriptionAddOnOverrideChoices: (
    accountId: UUID,
    query: {
      search?: string;
      selectedAddOnCodes?: string[];
      useCurrentAddOnSelections?: boolean;
      page?: number;
      size?: number;
    } = {},
  ) =>
    admin<SubscriptionOverrideChoicePage<SubscriptionAddOnOverrideChoice>>(
      `/subscriptions/account/${accountId}/override-choices/add-ons`,
      { query },
    ),
  subscriptionQuotaOverrideChoices: (
    accountId: UUID,
    query: {
      search?: string;
      featureCode?: string;
      resource?: string;
      selectedAddOnCodes?: string[];
      useCurrentAddOnSelections?: boolean;
      page?: number;
      size?: number;
    } = {},
  ) =>
    admin<SubscriptionOverrideChoicePage<SubscriptionQuotaPackageOverrideChoice>>(
      `/subscriptions/account/${accountId}/override-choices/quota-packages`,
      { query },
    ),
  confirmCheckout: (checkoutId: UUID, input: ManualCheckoutConfirmationInput) =>
    admin<SubscriptionCheckout>(`/subscriptions/checkouts/${checkoutId}/confirm-manual`, {
      method: "POST",
      body: jsonBody(input),
    }),
  registryInventory: () => admin<RegistryModule[]>("/registry/inventory"),
  featureCatalog: (audience: FeatureCatalogAudience = "ALL") =>
    admin<RegistryModule[]>("/registry/feature-catalog", { query: { audience } }),
  permissionCatalog: (audience: PermissionCatalogAudience = "PLATFORM_ADMIN_ROLE_GRANTABLE") =>
    admin<RegistryModule[]>("/registry/permission-catalog", { query: { audience } }),
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
