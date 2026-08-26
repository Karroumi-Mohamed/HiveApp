export type UUID = string;
export type Instant = string;

export type ApiErrorBody = {
  status: number;
  code: string;
  error: string;
  message: string;
  timestamp: string;
  details?: Record<string, unknown> | null;
};

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

export type AuthResponse = {
  accessToken: string;
  refreshToken: string | null;
  tokenType: string;
  expiresIn: number;
  passwordChangeRequired: boolean;
};

export type AdminMe = {
  id: UUID;
  email: string;
  emailVerified: boolean;
  isSuperAdmin: boolean;
  isActive: boolean;
  permissions: string[];
};

export type AdminRoleSummary = {
  id: UUID;
  name: string;
  description: string | null;
  status: AdminRoleStatus;
  isActive: boolean;
};

export type AdminRoleStatus = "INACTIVE" | "ACTIVE" | "ARCHIVED";
export type AdminRoleAction =
  | "READ_DETAIL"
  | "EDIT_METADATA"
  | "DUPLICATE"
  | "EDIT_PERMISSIONS"
  | "TRANSITION_STATUS"
  | "DELETE"
  | "READ_OPERATORS"
  | "READ_HISTORY"
  | "ASSIGN_TO_OPERATOR";

export type AdminPermission = {
  id: UUID;
  code: string;
  name: string;
  description: string | null;
  action: string;
  resource: string;
};

export type AdminUser = {
  id: UUID;
  userId: UUID;
  email: string;
  firstName: string;
  lastName: string;
  emailVerified: boolean;
  isSuperAdmin: boolean;
  isActive: boolean;
  /** ACTIVE once the operator has set their own password; pending states precede that. */
  credentialState: string;
  roles: AdminRoleSummary[];
};

/**
 * Returned once, when an operator is created. `temporaryPassword` is never readable again —
 * the administrator must hand it over before leaving the screen.
 */
export type AdminUserCreation = {
  operator: AdminUser;
  initialAccessMethod: "EMAIL_LINK" | "TEMPORARY_PASSWORD";
  /** Populated only for the explicitly selected temporary-password method. */
  temporaryPassword: string | null;
  linkExpiresAt: string | null;
  credentialState: string;
  emailDeliveryId: UUID | null;
  emailDelivery: EmailDeliverySummary | null;
};

/**
 * Result of reissuing an operator's access. `temporaryPassword` is populated only by the
 * explicit temporary-access fallback, and only on the response that creates it — nothing stores
 * it in clear form, so it can never be shown a second time.
 */
export type OperatorAccess = {
  method: "EMAIL_LINK" | "TEMPORARY_PASSWORD";
  credentialState: string;
  temporaryPassword: string | null;
  linkExpiresAt: string | null;
  emailDeliveryId: UUID | null;
  emailDelivery: EmailDeliverySummary | null;
};

/**
 * Outcome of an operation applied to many rows. Partial failure is normal — an administrator
 * cannot deactivate themselves, and SuperAdmins are protected — so each rejection is reported
 * with its own reason rather than collapsing into one status.
 */
export type BulkOperationResult = {
  requested: number;
  succeeded: number;
  failures: { id: UUID; code: string; message: string }[];
};

export type RoleHolder = {
  adminUserId: UUID;
  email: string;
  firstName: string;
  lastName: string;
  isActive: boolean;
  isSuperAdmin: boolean;
};

export type AdminRole = AdminRoleSummary & {
  version: number;
  createdAt: Instant;
  updatedAt: Instant;
  deletable: boolean;
  /** Operators currently holding this role — what deactivating it would affect. */
  assignedOperatorCount: number;
  permissions: AdminPermission[];
  availableActions: AdminRoleAction[];
};

export type AdminRolePreset = {
  code: string;
  name: string;
  description: string;
  permissions: AdminPermission[];
};

export type AdminRoleImpact = {
  roleId: UUID;
  version: number;
  currentStatus: AdminRoleStatus;
  proposedStatus: AdminRoleStatus;
  assignmentCount: number;
  permissionsAdded: string[];
  permissionsRemoved: string[];
  operatorsLosingLastPermissionSource: number;
  actorMayLoseAccess: boolean;
  confirmationRequired: boolean;
};

export type AdminRoleHistoryEntry = {
  id: UUID;
  occurredAt: Instant;
  actorUserId: UUID | null;
  actorEmail: string | null;
  action: string;
  /** Who the event happened to (assigned/removed operator), when the event has a subject. */
  subject: string | null;
  outcome: "SUCCEEDED" | "FAILED";
  failureType: string | null;
};

export type AdminAccessOverview = {
  totalOperators: number;
  activeOperators: number;
  inactiveOperators: number;
  superAdmins: number;
  totalRoles: number;
  activeRoles: number;
  inactiveRoles: number;
};

export type CommercialOverview = {
  totalPlans: number;
  draftPlans: number;
  activePlans: number;
  inactivePlans: number;
  archivedPlans: number;
  currentSubscriptions: number;
  activeSubscriptions: number;
  trialingSubscriptions: number;
  pastDueSubscriptions: number;
  suspendedSubscriptions: number;
  pendingCheckouts: number;
  scheduledChanges: number;
  changesNeedingAttention: number;
};

export type BillingCycle = "MONTHLY" | "YEARLY" | "FOREVER";
export type PlanStatus = "DRAFT" | "ACTIVE" | "INACTIVE" | "ARCHIVED";
export type SubscriptionStatus = "TRIALING" | "ACTIVE" | "PAST_DUE" | "SUSPENDED" | "CANCELLED" | "EXPIRED";
export type PlanFeatureMode = "INCLUDED" | "OPTIONAL_ADD_ON" | "BLOCKED_FOR_PLAN";
export type QuotaLimitMode = "FINITE" | "UNLIMITED";

export type QuotaLimitInput = {
  resource: string;
  mode: QuotaLimitMode;
  limit: number | null;
};

export type AssignPlanFeatureInput = {
  featureCode: string;
  mode: PlanFeatureMode;
  quotaConfigs: QuotaLimitInput[];
};

export type CreatePlanInput = {
  name: string;
  description: string | null;
  price: number;
  currencyCode: string;
  billingCycle: BillingCycle;
  features: AssignPlanFeatureInput[];
};

export type UpdatePlanInput = Omit<CreatePlanInput, "features">;
export type PlanBranchInput = UpdatePlanInput;

export type Plan = {
  id: UUID;
  code: string;
  name: string;
  description: string | null;
  price: number;
  currencyCode: string;
  billingCycle: BillingCycle;
  status: PlanStatus;
  lineageId: UUID;
  revisionNumber: number;
  sourcePlanId: UUID | null;
  creationReason: "CREATED" | "DUPLICATED" | "REVISED";
};

export type PlanDetail = Plan & {
  featureCount: number;
  quotaConfiguredFeatureCount: number;
  activeSubscriberCount: number;
  trialingSubscriberCount: number;
  currentSubscriberCount: number;
  historicalSubscriberCount: number;
  configuredRecurringPriceTotal: number;
  configuredRecurringPriceCurrencyCode: string;
  warnings: string[];
};

export type QuotaLimit = { resource: string; mode: QuotaLimitMode; limit: number | null };
export type PlanFeature = { id: UUID; featureCode: string; mode: PlanFeatureMode; quotaConfigs: QuotaLimit[] };

export type PlanSubscriber = {
  subscriptionId: UUID;
  accountId: UUID;
  accountName: string;
  planCode: string;
  status: SubscriptionStatus;
  configuredRecurringPrice: number;
  configuredRecurringPriceCurrencyCode: string;
  currentPeriodEnd: Instant | null;
};

export type PlanSubscriberOwnerLookup = {
  ownerEmail: string;
  subscriber: PlanSubscriber;
};

export type PlanDeletionPreview = {
  planId: UUID;
  planName: string;
  expectedVersion: number;
  previewToken: string;
  deletable: boolean;
  ownedFeatureCount: number;
  subscriptionHistoryCount: number;
  changeOperationReferenceCount: number;
  addOnReferenceCount: number;
  quotaPackageReferenceCount: number;
  lineageReferenceCount: number;
  blockers: string[];
};

export type AddOn = {
  id: UUID;
  code: string;
  name: string;
  description: string | null;
  price: number;
  currencyCode: string;
  billingCycle: BillingCycle;
  status: "DRAFT" | "ACTIVE" | "INACTIVE" | "ARCHIVED";
  definitionVersion: number;
  lineageId: UUID;
  revisionNumber: number;
  sourceAddOnId: UUID | null;
  creationReason: "CREATED" | "REVISED";
  allowedPlanCodes: string[];
  blockedPlanCodes: string[];
  dependencyCodes: string[];
  exclusionCodes: string[];
  features: Array<{ id: UUID; featureCode: string; quotaConfigs: QuotaLimit[] }>;
};

export type AddOnInput = {
  name: string;
  description: string | null;
  price: number;
  currencyCode: string;
  billingCycle: BillingCycle;
  allowedPlanCodes: string[];
  blockedPlanCodes: string[];
  dependencyCodes: string[];
  exclusionCodes: string[];
};

export type AssignAddOnFeatureInput = {
  featureCode: string;
  quotaConfigs: QuotaLimitInput[];
};

export type QuotaPackage = {
  id: UUID;
  code: string;
  name: string;
  description: string | null;
  featureCode: string;
  resource: string;
  capacityPerUnit: number;
  price: number;
  currencyCode: string;
  billingCycle: BillingCycle;
  repeatable: boolean;
  maximumQuantity: number;
  status: "DRAFT" | "ACTIVE" | "INACTIVE" | "ARCHIVED";
  definitionVersion: number;
  allowedPlanCodes: string[];
  allowedAddOnCodes: string[];
};

export type QuotaPackageInput = {
  name: string;
  description: string | null;
  featureCode: string;
  resource: string;
  capacityPerUnit: number;
  price: number;
  currencyCode: string;
  billingCycle: BillingCycle;
  repeatable: boolean;
  maximumQuantity: number;
  allowedPlanCodes: string[];
  allowedAddOnCodes: string[];
};

export type FeatureCatalogAudience = "ALL" | "PLAN_ASSIGNABLE" | "PUBLIC_CATALOG";
export type PermissionCatalogAudience =
  | "ALL"
  | "CLIENT_ROLE_GRANTABLE"
  | "PLATFORM_ADMIN_ROLE_GRANTABLE"
  | "B2B_DELEGATABLE";

export type RegistryPermission = AdminPermission;
export type RegistryFeature = {
  id: UUID;
  code: string;
  moduleCode: string;
  featureKey: string;
  displayName: string;
  description: string;
  surface: "PLATFORM_CONTROL" | "CLIENT_WORKSPACE" | "PUBLIC" | "SYSTEM";
  status: "PUBLIC" | "INTERNAL" | "BETA" | "DEPRECATED";
  publicVisible: boolean;
  newSalesEnabled: boolean;
  newGrantsEnabled: boolean;
  runtimeEnabled: boolean;
  registryPresent: boolean;
  planAssignable: boolean;
  clientRoleGrantable: boolean;
  platformAdminRoleGrantable: boolean;
  b2bDelegatable: boolean;
  publicCatalogVisible: boolean;
  publicVisibilityToggleable: boolean;
  newSalesToggleable: boolean;
  newGrantsToggleable: boolean;
  emergencyRuntimeToggleable: boolean;
  sortOrder: number;
  quotaSchema: Array<{ resource: string; type: string; unit: string }>;
  permissions: RegistryPermission[];
};
export type RegistryModule = { code: string; features: RegistryFeature[] };
export type RegistrySyncRun = {
  id: UUID;
  buildVersion: string;
  snapshotHash: string | null;
  status: "SUCCEEDED" | "FAILED";
  startedAt: Instant;
  completedAt: Instant;
  discoveredModules: number;
  discoveredFeatures: number;
  discoveredPermissions: number;
  createdModules: number;
  createdFeatures: number;
  updatedFeatures: number;
  createdPermissions: number;
  updatedPermissions: number;
  orphanedPermissions: number;
  details: string | null;
};

export type FeatureOperationalChange = {
  id: UUID;
  featureCode: string;
  control: "PUBLIC_VISIBILITY" | "NEW_SALES" | "NEW_GRANTS" | "EMERGENCY_RUNTIME";
  actorUserId: UUID;
  previousValue: boolean;
  newValue: boolean;
  reason: string;
  impactConfirmed: boolean;
  communicationConfirmed: boolean;
  effectiveTiming: string;
  createdAt: Instant;
};

export type Account = {
  id: UUID;
  ownerId: UUID;
  name: string;
  slug: string;
  isActive: boolean;
  createdAt: Instant;
  updatedAt: Instant;
};
export type Company = {
  id: UUID;
  accountId: UUID;
  name: string;
  legalName: string | null;
  taxId: string | null;
  industry: string | null;
  country: string | null;
  address: string | null;
  logoUrl: string | null;
  isActive: boolean;
  warnings: string[];
};

export type MemberPermissions = {
  memberId: UUID;
  accountId: UUID;
  companyId: UUID | null;
  isOwner: boolean;
  permissions: string[];
};
export type Member = {
  id: UUID;
  userId: UUID;
  username: string;
  email: string | null;
  displayName: string;
  employeeNumber: string | null;
  isOwner: boolean;
  isActive: boolean;
  credentialState: string;
  emailVerified: boolean;
  initialAccessLocked: boolean;
};

export type MemberCreation = {
  member: Member;
  initialAccessMethod: string;
  credentialState: string;
  temporaryPassword: string | null;
  activationLinkExpiresAt: Instant | null;
  emailDelivery: EmailDeliverySummary | null;
};

export type MemberRoleAssignment = {
  assignmentId: UUID;
  roleId: UUID;
  roleName: string;
  roleStatus: Role["status"];
  scope: "ACCOUNT" | "COMPANY";
  companyId: UUID | null;
  companyName: string | null;
};

export type PermissionOverride = {
  id: UUID;
  memberId: UUID;
  scope: "ACCOUNT" | "COMPANY";
  companyId: UUID | null;
  permissionCode: string;
  decision: "GRANT" | "DENY";
  reason: string;
  createdByMemberId: UUID;
  expiresAt: Instant | null;
  effective: boolean;
  createdAt: Instant;
  updatedAt: Instant;
};

export type MemberAuthorization = {
  member: Member;
  roles: MemberRoleAssignment[];
  overrides: PermissionOverride[];
};

export type EmailDeliverySummary = {
  deliveryId: UUID;
  purpose: string;
  status: "PENDING" | "SENT" | "FAILED" | "SUPPRESSED";
  attemptedAt: Instant | null;
  deliveredAt: Instant | null;
  failureCode: string | null;
  totalAttempts: number;
  failedAttempts: number;
  retryable: boolean;
};

export type MemberAccess = {
  memberId: UUID;
  method: string;
  credentialState: string;
  temporaryPassword: string | null;
  linkExpiresAt: Instant | null;
  emailDelivery: EmailDeliverySummary | null;
};

export type Role = {
  id: UUID;
  accountId: UUID;
  templateBoundary: "ACCOUNT" | "COMPANY";
  boundaryCompanyId: UUID | null;
  name: string;
  description: string | null;
  status: "INACTIVE" | "ACTIVE" | "ARCHIVED";
  isSystemRole: boolean;
  everAssigned: boolean;
  definitionRevision: number;
  version: number;
  permissionCodes: string[];
};

export type RoleImpact = {
  roleId: UUID;
  version: number;
  status: Role["status"];
  changeType: "UPDATE" | "ADD_PERMISSION" | "REMOVE_PERMISSION" | "ACTIVATE" | "DEACTIVATE" | "ARCHIVE" | "DELETE";
  permissionCode: string | null;
  assignmentCount: number;
  affectedMemberCount: number;
  activeMemberCount: number;
  scopes: Array<{ scope: string; companyId: UUID | null; assignmentCount: number }>;
  currentPermissionCodes: string[];
  permissionsGranted: string[];
  permissionsLost: string[];
  confirmationRequired: boolean;
};

export type PermissionPickerCatalog = {
  registryVersion: string;
  audience: string;
  availableChoices: Array<{
    code: string;
    features: Array<{
      code: string;
      displayName: string;
      description: string;
      permissions: Array<{ code: string; name: string; description: string; action: string; resource: string }>;
    }>;
  }>;
  currentSelections: Array<{
    permissionCode: string;
    available: boolean;
    unavailableReason: string | null;
    explanation: string | null;
  }>;
};

export type OrganizationGroup = {
  id: UUID;
  companyId: UUID;
  parentId: UUID | null;
  name: string;
  description: string | null;
  displayOrder: number;
  status: "ACTIVE" | "ARCHIVED";
  positionSuggestions: string[];
  directMemberCount: number;
  createdAt: Instant;
  updatedAt: Instant;
};
export type GroupMembership = {
  id: UUID;
  groupId: UUID;
  memberId: UUID;
  memberDisplayName: string;
  positionTitle: string | null;
};
export type GroupTemplate = {
  id: UUID;
  name: string;
  scope: string;
  status: string;
  accountId: UUID;
  companyId: UUID | null;
  nodeCount: number;
};

export type GroupTemplatePreview = {
  templateId: UUID;
  targetCompanyId: UUID;
  targetParentId: UUID | null;
  nodes: Array<{
    templateNodeId: UUID;
    parentTemplateNodeId: UUID | null;
    name: string;
    description: string | null;
    displayOrder: number;
    positionSuggestions: string[];
  }>;
  conflicts: string[];
  canInstantiate: boolean;
};

export type CollaborationGrant = {
  permissionCode: string;
  description: string;
  configured: boolean;
  currentlyActive: boolean;
  grantedAt: Instant | null;
  revokedAt: Instant | null;
};
export type Collaboration = {
  id: UUID;
  clientAccountId: UUID;
  clientAccountName: string;
  providerAccountId: UUID;
  providerAccountName: string;
  companyId: UUID;
  companyName: string;
  companyCountry: string | null;
  status: "PENDING" | "ACTIVE" | "SUSPENDED" | "REJECTED" | "CANCELLED" | "REVOKED";
  version: number;
  purpose: string;
  requestedPermissionCodes: string[];
  grants: CollaborationGrant[];
  allowedNextActions: string[];
  accessBlockers: string[];
  requestedAt: Instant;
  acceptedAt: Instant | null;
  cancelledAt: Instant | null;
  rejectedAt: Instant | null;
  suspendedAt: Instant | null;
  suspensionReviewAt: Instant | null;
  automaticResumeAt: Instant | null;
  resumedAt: Instant | null;
  revokedAt: Instant | null;
  lifecycleReason: string | null;
};

export type CompanyShareCode = {
  companyId: UUID;
  enabled: boolean;
  shareCode: string | null;
  generatedAt: Instant | null;
  resolutionCount: number;
  requestCount: number;
  lastResolvedAt: Instant | null;
  lastRequestedAt: Instant | null;
};

export type ClientPlanCatalog = {
  currentSubscription: {
    id: UUID;
    planCode: string;
    status: SubscriptionStatus;
    currentPrice: number;
    currentPriceCurrencyCode: string;
    currentPeriodStart: Instant;
    currentPeriodEnd: Instant;
    cancelAtPeriodEnd: boolean;
    addOnCodes: string[];
    quotaPackages: Array<{ packageCode: string; quantity: number }>;
  } | null;
  plans: Array<{
    code: string;
    name: string;
    description: string | null;
    basePrice: number;
    currencyCode: string;
    billingCycle: BillingCycle;
    current: boolean;
    features: Array<{
      featureCode: string;
      displayName: string;
      description: string | null;
      mode: string;
      quotas: Array<{
        featureCode: string;
        slot: string;
        mode: string;
        limit: number | null;
        unlimited: boolean;
        currentUsage: number | null;
      }>;
    }>;
    addOns: Array<{
      code: string;
      name: string;
      description: string | null;
      price: number;
      currencyCode: string;
      billingCycle: BillingCycle;
      definitionVersion: number;
      dependencyCodes: string[];
      exclusionCodes: string[];
      features: unknown[];
    }>;
    quotaPackages: Array<{
      code: string;
      name: string;
      description: string | null;
      definitionVersion: number;
      featureCode: string;
      resource: string;
      capacityPerUnit: number;
      price: number;
      currencyCode: string;
      billingCycle: BillingCycle;
      repeatable: boolean;
      maximumQuantity: number;
      allowedPlanCodes: string[];
      allowedAddOnCodes: string[];
    }>;
  }>;
};

export type SubscriptionChangePreview = {
  currentPlanCode: string;
  targetPlanCode: string;
  currentPrice: number;
  previewPrice: number;
  currencyCode: string;
  immediateAllowed: boolean;
  effectiveFeatureCodes: string[];
  effectiveQuotaLimits: Array<{
    featureCode: string;
    resource: string;
    mode: string;
    includedLimit: number | null;
    purchasedCapacity: number;
    effectiveLimit: number | null;
  }>;
  addOnCodes: string[];
  quotaPackages: Array<{ packageCode: string; quantity: number }>;
  conflicts: Array<{
    code: string;
    featureCode: string | null;
    resource: string | null;
    currentUsage: number | null;
    requestedLimit: number | null;
    message: string;
  }>;
};

export type QuotaPackageSelection = { packageCode: string; quantity: number };

export type SubscriptionChangeInput = {
  targetPlanCode: string;
  addOnCodes: string[];
  quotaPackages: QuotaPackageSelection[];
  timing: "IMMEDIATE" | "AT_RENEWAL";
};

export type Subscription = {
  id: UUID;
  plan: { code: string; name: string; basePrice: number; currencyCode: string };
  status: SubscriptionStatus;
  currentPrice: number;
  currentPriceCurrencyCode: string;
  currentPeriodStart: Instant;
  currentPeriodEnd: Instant;
  cancelAtPeriodEnd: boolean;
};

export type AdminSubscription = {
  id: UUID;
  accountId: UUID;
  accountName: string;
  planCode: string;
  planName: string;
  status: SubscriptionStatus;
  currentPrice: number;
  currentPriceCurrencyCode: string;
  currentPeriodStart: Instant;
  currentPeriodEnd: Instant;
  cancelAtPeriodEnd: boolean;
  customOverrides: {
    schemaVersion: number;
    addOnCodes: string[];
    quotaPackages: Array<{ packageCode: string; quantity: number }>;
  };
  entitlementSnapshot: {
    planCode: string;
    planName: string;
    planDefinitionVersion: number;
    features: unknown[];
    addOns: unknown[];
    quotaPackages: unknown[];
  } | null;
};

export type AccountDirectoryEntry = { id: UUID; name: string; slug: string; ownerEmail: string; active: boolean };

export type SubscriptionChangeOperation = {
  id: UUID;
  timing: "IMMEDIATE" | "AT_RENEWAL";
  status: "AWAITING_CONFIRMATION" | "PENDING" | "APPLIED" | "NEEDS_ATTENTION" | "CANCELLED";
  effectiveAt: Instant | null;
  sourcePlanCode: string;
  targetPlanCode: string;
  attentionReason: string | null;
  checkout: { id: UUID; status: string; amount: number; currencyCode: string } | null;
};

export type SubscriptionChangeApplyResponse = {
  subscription: Subscription;
  preview: SubscriptionChangePreview;
  operation: SubscriptionChangeOperation;
};

export type SubscriptionOverridesInput = {
  addOnCodes: string[];
  quotaPackages: QuotaPackageSelection[];
};

export type ManualCheckoutConfirmationInput = {
  reference: string;
  reason: string;
};

export type SubscriptionCheckout = {
  id: UUID;
  status: "PENDING_CONFIRMATION" | "CONFIRMED" | "FAILED" | "CANCELLED";
  amount: number;
  currencyCode: string;
  gatewayAttemptStatus: "SUCCESS" | "FAILED" | "PENDING" | null;
  gatewayReference: string | null;
  gatewayFailureReason: string | null;
  confirmationSource: "MANUAL_OPERATOR" | "TRUSTED_PROVIDER" | null;
  confirmationReference: string | null;
  confirmedAt: Instant | null;
};
