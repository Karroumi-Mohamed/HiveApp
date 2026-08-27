export type UUID = string;
export type Instant = string;
/** Exact JSON decimal serialized as text; never coerce commercial money through IEEE-754. */
export type ExactDecimal = string;

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
export type ProductPriceBillingCycle = Exclude<BillingCycle, "FOREVER">;
export type ProductPriceOwnerType = "PLAN" | "ADD_ON" | "QUOTA_PACKAGE";
export type ProductPriceStatus = "DRAFT" | "ACTIVE" | "INACTIVE" | "ARCHIVED";
export type ProductPriceAction =
  | "EDIT_DRAFT"
  | "PREVIEW_ACTIVATION"
  | "ACTIVATE"
  | "PAUSE"
  | "REACTIVATE"
  | "REVISE"
  | "ARCHIVE"
  | "DELETE_DRAFT";
export type ProductPriceBlocker =
  | "OWNER_NOT_ACTIVE"
  | "EFFECTIVE_WINDOW_EXPIRED"
  | "ACTIVE_WINDOW_OVERLAP"
  | "SUCCESSOR_ALREADY_EXISTS"
  | "WRONG_LIFECYCLE_STATE"
  | "ACTIVE_MUST_BE_PAUSED"
  | "ARCHIVED_TERMINAL";
export type ProductPriceReplacementBlocker =
  | "CURRENT_NOT_ACTIVE"
  | "SUCCESSOR_NOT_DRAFT"
  | "SUCCESSOR_NOT_DIRECT_REVISION"
  | "COMMERCIAL_TUPLE_MISMATCH"
  | "CUTOFF_NOT_FUTURE"
  | "CURRENT_DOES_NOT_COVER_CUTOFF"
  | "OWNER_NOT_ACTIVE"
  | "OTHER_ACTIVE_WINDOW_OVERLAP";

export type ProductPrice = {
  id: UUID;
  productType: ProductPriceOwnerType;
  productId: UUID;
  productCode: string;
  productName: string;
  amount: ExactDecimal;
  currencyCode: string;
  billingCycle: ProductPriceBillingCycle;
  status: ProductPriceStatus;
  effectiveFrom: Instant;
  effectiveUntil: Instant | null;
  lineageId: UUID;
  revisionNumber: number;
  sourcePriceId: UUID | null;
  compatibilityDefault: boolean;
  version: number;
  createdAt: Instant;
  updatedAt: Instant;
  availableActions: ProductPriceAction[];
  blockers: ProductPriceBlocker[];
};

export type ProductPriceInput = {
  amount: ExactDecimal;
  currencyCode: string;
  billingCycle: ProductPriceBillingCycle;
  effectiveFrom: Instant;
  effectiveUntil: Instant | null;
};

export type ProductPriceActivationPreview = {
  priceEntryId: UUID;
  expectedVersion: number;
  catalogRevision: number;
  registryVersion: string;
  evaluatedAt: Instant;
  expiresAt: Instant;
  previewToken: string;
  activatable: boolean;
  blockers: ProductPriceBlocker[];
};

export type ProductPriceActivationRequest = {
  version: number;
  reason: string;
  activationPreviewToken: string;
};

export type ProductPriceReplacementPreview = {
  currentPriceId: UUID;
  currentVersion: number;
  successorPriceId: UUID;
  successorVersion: number;
  cutoff: Instant;
  schedulable: boolean;
  blockers: ProductPriceReplacementBlocker[];
};

export type ProductPriceReplacementResult = {
  previousPrice: ProductPrice;
  successorPrice: ProductPrice;
  cutoff: Instant;
  existingResult: boolean;
};

export type ProductPriceHistoryEntry = {
  id: UUID;
  occurredAt: Instant;
  actorUserId: UUID | null;
  actorEmail: string | null;
  action: string;
  outcome: "SUCCEEDED" | "FAILED";
  failureType: string | null;
  reason: string | null;
};

export type CatalogPrice = {
  priceEntryId: UUID;
  amount: ExactDecimal;
  currencyCode: string;
  billingCycle: ProductPriceBillingCycle;
  effectiveFrom: Instant;
  effectiveUntil: Instant | null;
};

export type ProductPriceSelection = {
  priceEntryId: UUID;
  currencyCode: string;
  billingCycle: ProductPriceBillingCycle;
};

export type AssignablePlanPrice = {
  planId: UUID;
  planCode: string;
  planName: string;
  planRevisionNumber: number;
  priceEntryId: UUID;
  amount: ExactDecimal;
  currencyCode: string;
  billingCycle: ProductPriceBillingCycle;
  effectiveFrom: Instant;
  effectiveUntil: Instant | null;
};
export type PlanStatus = "DRAFT" | "ACTIVE" | "INACTIVE" | "ARCHIVED";
export type ProductSalesVisibility = "PUBLIC" | "DIRECT_ONLY";
export type PlanExtensionPolicy = "CLOSED" | "ALLOW_LIST" | "OPEN_COMPATIBLE";
export type CommercialTargetingMode = "OPEN_COMPATIBLE" | "TARGETED";
export type CommercialChoiceState = "SELECTABLE" | "NO_LONGER_ACTIVE" | "MISSING";
export type CommercialProductType = "ADD_ON" | "QUOTA_PACKAGE";
export type CommercialProductAction =
  | "EDIT_DRAFT"
  | "MANAGE_COMPOSITION"
  | "MANAGE_PRICES"
  | "PREVIEW_ACTIVATION"
  | "ACTIVATE"
  | "DEACTIVATE"
  | "ARCHIVE"
  | "DELETE_DRAFT"
  | "REVISE"
  | "COMPARE"
  | "READ_HISTORY";
export type CommercialProductBlocker =
  | "NOT_PUBLISHED"
  | "PAUSED"
  | "ARCHIVED_TERMINAL"
  | "NO_ACTIVE_PRICE"
  | "NO_PRICE_STARTING_POINT"
  | "PUBLISHED_PRICE_HISTORY"
  | "REFERENCED_BY_ADD_ON"
  | "REFERENCED_BY_QUOTA_PACKAGE"
  | "NO_FEATURES"
  | "NO_INCLUDED_FEATURES"
  | "DRAFT_SUCCESSOR_EXISTS"
  | "NOT_LATEST_REVISION"
  | "DEFAULT_PLAN_LOCKED";
export type CommercialAvailabilityBlocker = "ARCHIVED_PRODUCT" | "NO_CHANGE";
export type CommercialAvailabilityAction = "APPLY_PLAN_AVAILABILITY" | "APPLY_SALES_VISIBILITY";
export type RetainedEntitlementState = "SELECTABLE" | "RETAINED_ONLY" | "HISTORICAL_ONLY";
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
  price: ExactDecimal;
  currencyCode: string;
  billingCycle: BillingCycle;
  features: AssignPlanFeatureInput[];
  extensionPolicy?: PlanExtensionPolicy;
  salesVisibility?: ProductSalesVisibility;
};

export type UpdatePlanInput = Omit<CreatePlanInput, "features" | "extensionPolicy" | "salesVisibility"> & {
  expectedVersion?: number;
};
export type PlanBranchInput = UpdatePlanInput;

export type Plan = {
  id: UUID;
  code: string;
  name: string;
  description: string | null;
  price: ExactDecimal;
  currencyCode: string;
  billingCycle: BillingCycle;
  status: PlanStatus;
  lineageId: UUID;
  revisionNumber: number;
  sourcePlanId: UUID | null;
  creationReason: "CREATED" | "DUPLICATED" | "REVISED";
  extensionPolicy: PlanExtensionPolicy;
  salesVisibility: ProductSalesVisibility;
  version: number;
};

export type PlanDetail = Plan & {
  featureCount: number;
  quotaConfiguredFeatureCount: number;
  activeSubscriberCount: number;
  trialingSubscriberCount: number;
  currentSubscriberCount: number;
  historicalSubscriberCount: number;
  affectedSubscriptionCount: number;
  configuredRecurringPriceTotal: ExactDecimal;
  configuredRecurringPriceCurrencyCode: string;
  warnings: string[];
};

export type CommercialOperationalItem = {
  id: UUID;
  code: string;
  name: string;
  status: PlanStatus;
  lineageId: UUID;
  revisionNumber: number;
  salesVisibility: ProductSalesVisibility;
  version: number;
  createdAt: Instant;
  updatedAt: Instant;
  applicablePriceCount: number;
  draftPriceCount: number;
  publishedPriceCount: number;
  availableActions: CommercialProductAction[];
  blockers: CommercialProductBlocker[];
};

export type PlanOperationalItem = CommercialOperationalItem & {
  sourcePlanId: UUID | null;
  creationReason: "CREATED" | "DUPLICATED" | "REVISED";
  extensionPolicy: PlanExtensionPolicy;
  featureCount: number;
  includedFeatureCount: number;
  currentSubscriberCount: number;
  affectedSubscriptionCount: number;
};

export type AddOnOperationalItem = CommercialOperationalItem & {
  sourceAddOnId: UUID | null;
  creationReason: "CREATED" | "REVISED";
  featureCount: number;
  targetingMode: CommercialTargetingMode;
  targetPlanCount: number;
  blockedPlanCount: number;
  dependencyCount: number;
  exclusionCount: number;
  referencedByAddOnCount: number;
  referencedByQuotaPackageCount: number;
};

export type QuotaPackageOperationalItem = CommercialOperationalItem & {
  sourceQuotaPackageId: UUID | null;
  creationReason: "CREATED" | "REVISED";
  featureCode: string;
  resource: string;
  targetingMode: CommercialTargetingMode;
  targetPlanCount: number;
  targetAddOnCount: number;
};

export type CommercialChooserItem = {
  id: UUID;
  code: string;
  name: string;
  status: PlanStatus;
  lineageId: UUID;
  revisionNumber: number;
  salesVisibility: ProductSalesVisibility;
  choiceState: CommercialChoiceState;
};
export type PlanChooserItem = CommercialChooserItem & { extensionPolicy: PlanExtensionPolicy };
export type AddOnChooserItem = CommercialChooserItem & { targetingMode: CommercialTargetingMode };
export type QuotaPackageChooserItem = CommercialChooserItem & {
  featureCode: string;
  resource: string;
  targetingMode: CommercialTargetingMode;
};

export type QuotaLimit = { resource: string; mode: QuotaLimitMode; limit: number | null };
export type PlanFeature = { id: UUID; featureCode: string; mode: PlanFeatureMode; quotaConfigs: QuotaLimit[] };

export type PlanSubscriber = {
  subscriptionId: UUID;
  accountId: UUID;
  accountName: string;
  planCode: string;
  status: SubscriptionStatus;
  configuredRecurringPrice: ExactDecimal;
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
  catalogRevision: number;
  registryVersion: string;
  evaluatedAt: Instant;
  expiresAt: Instant;
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
  price: ExactDecimal;
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
  salesVisibility: ProductSalesVisibility;
  version: number;
};

export type AddOnInput = {
  name: string;
  description: string | null;
  price: ExactDecimal;
  currencyCode: string;
  billingCycle: BillingCycle;
  allowedPlanCodes: string[];
  blockedPlanCodes: string[];
  dependencyCodes: string[];
  exclusionCodes: string[];
  salesVisibility?: ProductSalesVisibility;
  expectedVersion?: number;
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
  price: ExactDecimal;
  currencyCode: string;
  billingCycle: BillingCycle;
  repeatable: boolean;
  maximumQuantity: number;
  status: "DRAFT" | "ACTIVE" | "INACTIVE" | "ARCHIVED";
  definitionVersion: number;
  allowedPlanCodes: string[];
  allowedAddOnCodes: string[];
  salesVisibility: ProductSalesVisibility;
  version: number;
  lineageId: UUID;
  revisionNumber: number;
  sourceQuotaPackageId: UUID | null;
  creationReason: "CREATED" | "REVISED";
};

export type QuotaPackageInput = {
  name: string;
  description: string | null;
  featureCode: string;
  resource: string;
  capacityPerUnit: number;
  price: ExactDecimal;
  currencyCode: string;
  billingCycle: BillingCycle;
  repeatable: boolean;
  maximumQuantity: number;
  allowedPlanCodes: string[];
  allowedAddOnCodes: string[];
  salesVisibility?: ProductSalesVisibility;
  expectedVersion?: number;
};

export type ExtensionAvailabilityIssue = {
  reason: string;
  source: string;
  sourceCode: string | null;
};
export type ExtensionCompatibility = {
  productType: CommercialProductType;
  productId: UUID;
  code: string;
  name: string;
  salesVisibility: ProductSalesVisibility;
  operatorSelectable: boolean;
  clientCatalogVisible: boolean;
  issues: ExtensionAvailabilityIssue[];
  applicablePriceCount: number;
  directlySelectable: boolean;
  requiredAddOnCodes: string[];
  featureCodes: string[];
  quotaFeatureCode: string | null;
  quotaResource: string | null;
  capacityPerUnit: number | null;
  repeatable: boolean | null;
  maximumQuantity: number | null;
};
export type PlanAvailabilityPreview = {
  planId: UUID;
  planCode: string;
  expectedVersion: number;
  catalogRevision: number;
  registryVersion: string;
  evaluatedAt: Instant;
  expiresAt: Instant;
  currentExtensionPolicy: PlanExtensionPolicy;
  targetExtensionPolicy: PlanExtensionPolicy;
  currentSalesVisibility: ProductSalesVisibility;
  targetSalesVisibility: ProductSalesVisibility;
  affectedSubscriptionCount: number;
  totalExtensions: number;
  operatorSelectableBefore: number;
  operatorSelectableAfter: number;
  clientVisibleBefore: number;
  clientVisibleAfter: number;
  changedCount: number;
  changesTruncated: boolean;
  changedExtensions: ExtensionCompatibility[];
  applicable: boolean;
  blockers: CommercialAvailabilityBlocker[];
  availableActions: CommercialAvailabilityAction[];
  previewToken: string;
};
export type ProductVisibilityPreview = {
  productType: CommercialProductType;
  productId: UUID;
  productCode: string;
  expectedVersion: number;
  catalogRevision: number;
  registryVersion: string;
  evaluatedAt: Instant;
  expiresAt: Instant;
  currentSalesVisibility: ProductSalesVisibility;
  targetSalesVisibility: ProductSalesVisibility;
  compatiblePlanCount: number;
  clientVisiblePlanCountBefore: number;
  clientVisiblePlanCountAfter: number;
  applicable: boolean;
  blockers: CommercialAvailabilityBlocker[];
  availableActions: CommercialAvailabilityAction[];
  previewToken: string;
};
export type CommercialAvailabilityHistoryEntry = {
  id: UUID;
  occurredAt: Instant;
  actorUserId: UUID | null;
  actorEmail: string | null;
  action: string;
  outcome: "SUCCEEDED" | "FAILED";
  failureType: string | null;
  reason: string | null;
  productType: CommercialProductType;
  productCode: string;
  previousExtensionPolicy: PlanExtensionPolicy | null;
  resultingExtensionPolicy: PlanExtensionPolicy | null;
  previousSalesVisibility: ProductSalesVisibility | null;
  resultingSalesVisibility: ProductSalesVisibility | null;
};
export type QuotaPackagePriceDraft = {
  id: UUID;
  amount: ExactDecimal;
  currencyCode: string;
  billingCycle: BillingCycle;
  status: ProductPriceStatus;
  effectiveFrom: Instant;
  effectiveUntil: Instant | null;
  lineageId: UUID;
  revisionNumber: number;
  version: number;
  compatibilityDefault: boolean;
};
export type QuotaPackageActivationPreview = {
  quotaPackageId: UUID;
  expectedVersion: number;
  catalogRevision: number;
  registryVersion: string;
  evaluatedAt: Instant;
  expiresAt: Instant;
  previewToken: string;
  activatable: boolean;
  blockers: string[];
  reviewedPrices: QuotaPackagePriceDraft[];
  packagesToDeactivate: UUID[];
};

export type CommercialLifecycleAction = "ACTIVATE" | "DEACTIVATE" | "ARCHIVE";
export type CommercialLifecycleInput = {
  action: CommercialLifecycleAction;
  expectedVersion: number;
  reason: string;
  activationPreviewToken?: string | null;
};
export type ProductActivationPrice = {
  id: UUID;
  amount: ExactDecimal;
  currencyCode: string;
  billingCycle: BillingCycle;
  status: string;
  effectiveFrom: Instant | null;
  effectiveUntil: Instant | null;
  lineageId: UUID;
  revisionNumber: number;
  version: number;
  compatibilityDefault: boolean;
};
export type PlanActivationPreview = {
  planId: UUID;
  expectedVersion: number;
  catalogRevision: number;
  registryVersion: string;
  evaluatedAt: Instant;
  expiresAt: Instant;
  previewToken: string;
  activatable: boolean;
  blockers: string[];
  includedFeatureCount: number;
  optionalAddOnFeatureCount: number;
  blockedFeatureCount: number;
  reviewedPrices: ProductActivationPrice[];
};
export type AddOnActivationPreview = {
  addOnId: UUID;
  expectedVersion: number;
  catalogRevision: number;
  registryVersion: string;
  evaluatedAt: Instant;
  expiresAt: Instant;
  previewToken: string;
  activatable: boolean;
  blockers: string[];
  featureCount: number;
  evaluatedPlanCount: number;
  compatiblePlanCount: number;
  explicitTargetPlanCount: number;
  reviewedPrices: ProductActivationPrice[];
  addOnsToDeactivate: UUID[];
};
export type QuotaPackageComparison = {
  base: QuotaPackage;
  candidate: QuotaPackage;
  directSuccessor: boolean;
  changedFields: string[];
  basePrices: QuotaPackagePriceDraft[];
  candidatePrices: QuotaPackagePriceDraft[];
};
export type QuotaPackageRevisionResult = {
  successor: QuotaPackage;
  copiedPriceDrafts: QuotaPackagePriceDraft[];
  warnings: string[];
};
export type QuotaPackageHistoryEntry = {
  id: UUID;
  occurredAt: Instant;
  actorUserId: UUID | null;
  actorEmail: string | null;
  action: string;
  outcome: "SUCCEEDED" | "FAILED";
  failureType: string | null;
  reason: string | null;
  resourceId: UUID | null;
  revisionNumber: number | null;
  lifecycleAction: "ACTIVATE" | "DEACTIVATE" | "ARCHIVE" | null;
  resultingStatus: PlanStatus | null;
  successorId: UUID | null;
  successorRevisionNumber: number | null;
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
    currentPrice: ExactDecimal;
    currentPriceCurrencyCode: string;
    planPriceEntryId: UUID | null;
    billingCycle: ProductPriceBillingCycle | null;
    currentPeriodStart: Instant;
    currentPeriodEnd: Instant;
    cancelAtPeriodEnd: boolean;
    addOnCodes: string[];
    quotaPackages: Array<{ packageCode: string; quantity: number }>;
    retainedAddOns: Array<{
      code: string;
      name: string;
      definitionVersion: number;
      unitPrice: ExactDecimal;
      currencyCode: string;
      billingCycle: BillingCycle;
      featureCodes: string[];
      priceEntryId: UUID;
      state: RetainedEntitlementState;
      removable: boolean;
      selectableForNewSale: boolean;
    }>;
    retainedQuotaPackages: Array<{
      code: string;
      name: string;
      definitionVersion: number;
      featureCode: string;
      resource: string;
      capacityPerUnit: number;
      quantity: number;
      unitPrice: ExactDecimal;
      currencyCode: string;
      billingCycle: BillingCycle;
      priceEntryId: UUID;
      state: RetainedEntitlementState;
      removable: boolean;
      quantityEditable: boolean;
      maximumSelectableQuantity: number | null;
    }>;
  } | null;
  plans: Array<{
    code: string;
    name: string;
    description: string | null;
    basePrice: ExactDecimal;
    currencyCode: string;
    billingCycle: BillingCycle;
    current: boolean;
    selectable: boolean;
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
      price: ExactDecimal;
      currencyCode: string;
      billingCycle: BillingCycle;
      definitionVersion: number;
      dependencyCodes: string[];
      exclusionCodes: string[];
      features: unknown[];
      prices: CatalogPrice[];
    }>;
    quotaPackages: Array<{
      code: string;
      name: string;
      description: string | null;
      definitionVersion: number;
      featureCode: string;
      resource: string;
      capacityPerUnit: number;
      price: ExactDecimal;
      currencyCode: string;
      billingCycle: BillingCycle;
      repeatable: boolean;
      maximumQuantity: number;
      allowedPlanCodes: string[];
      allowedAddOnCodes: string[];
      prices: CatalogPrice[];
      directlyAvailable: boolean;
      requiresAddOnCodes: string[];
    }>;
    prices: CatalogPrice[];
  }>;
};

export type SubscriptionChangePreview = {
  subscriptionId: UUID;
  expectedSubscriptionVersion: number;
  catalogRevision: number;
  registryVersion: string;
  evaluatedAt: Instant;
  expiresAt: Instant;
  previewToken: string;
  currentPlanCode: string;
  targetPlanCode: string;
  currentPrice: ExactDecimal;
  previewPrice: ExactDecimal;
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
  planPriceSelection: ProductPriceSelection;
};

export type SubscriptionChangeApplyInput = {
  selection: SubscriptionChangeInput;
  previewToken: string;
};

export type Subscription = {
  id: UUID;
  plan: { code: string; name: string; basePrice: ExactDecimal; currencyCode: string };
  status: SubscriptionStatus;
  currentPrice: ExactDecimal;
  currentPriceCurrencyCode: string;
  currentPeriodStart: Instant;
  currentPeriodEnd: Instant;
  cancelAtPeriodEnd: boolean;
};

export type SubscriptionEntitlementSnapshot = {
  schemaVersion: number;
  planCode: string;
  planName: string | null;
  planDefinitionVersion: number;
  basePrice: ExactDecimal;
  currencyCode: string;
  billingCycle: BillingCycle;
  effectiveFrom: Instant | null;
  effectiveUntil: Instant | null;
  features: unknown[];
  addOns: Array<{
    code: string;
    name: string;
    definitionVersion: number;
    price: ExactDecimal;
    currencyCode: string;
    billingCycle: BillingCycle;
    featureCodes: string[];
    priceEntryId: UUID | null;
  }>;
  quotaPackages: Array<{
    code: string;
    name: string;
    definitionVersion: number;
    featureCode: string;
    resource: string;
    capacityPerUnit: number;
    quantity: number;
    unitPrice: ExactDecimal;
    currencyCode: string;
    billingCycle: BillingCycle;
    priceEntryId: UUID | null;
  }>;
  planPriceEntryId: UUID | null;
};

export type AdminSubscription = {
  id: UUID;
  accountId: UUID;
  accountName: string;
  planCode: string;
  planName: string;
  status: SubscriptionStatus;
  currentPrice: ExactDecimal;
  currentPriceCurrencyCode: string;
  currentPeriodStart: Instant;
  currentPeriodEnd: Instant;
  cancelAtPeriodEnd: boolean;
  customOverrides: {
    schemaVersion: number;
    addOnCodes: string[];
    quotaPackages: Array<{ packageCode: string; quantity: number }>;
  };
  entitlementSnapshot: SubscriptionEntitlementSnapshot | null;
};

export type AccountDirectoryEntry = { id: UUID; name: string; slug: string; active: boolean };

export type SubscriptionAccountListItem = AccountDirectoryEntry & {
  createdAt: Instant;
  latestSubscription: {
    id: UUID;
    status: SubscriptionStatus;
    planId: UUID;
    planCode: string;
    planName: string;
    planRevisionNumber: number;
    billingCycle: ProductPriceBillingCycle;
    currentPeriodEnd: Instant | null;
    cancelAtPeriodEnd: boolean;
    currentPrice: ExactDecimal;
    currencyCode: string;
  } | null;
};

export type SubscriptionAccountOwnerLookup = {
  ownerEmail: string;
  account: SubscriptionAccountListItem;
};

export type SubscriptionChangeOperation = {
  id: UUID;
  createdAt: Instant;
  updatedAt: Instant;
  timing: "IMMEDIATE" | "AT_RENEWAL";
  status: "AWAITING_CONFIRMATION" | "PENDING" | "APPLIED" | "NEEDS_ATTENTION" | "CANCELLED";
  effectiveAt: Instant | null;
  sourcePlanCode: string;
  targetPlanCode: string;
  attentionReason: string | null;
  checkout: SubscriptionCheckout | null;
};

/** Operator-only subscription history. These fields are intentionally absent from the client DTO. */
export type AdminSubscriptionChangeOperation = SubscriptionChangeOperation & {
  requestOrigin: "CLIENT" | "PLATFORM_ADMIN" | "SYSTEM";
  requestedByUserId: UUID | null;
  requestReason: string | null;
  cancellationOrigin: "CLIENT" | "PLATFORM_ADMIN" | "SYSTEM" | null;
  cancelledByUserId: UUID | null;
  cancellationReason: string | null;
  cancelledAt: Instant | null;
};

export type SubscriptionChangeApplyResponse = {
  subscription: Subscription;
  preview: SubscriptionChangePreview;
  operation: SubscriptionChangeOperation;
};

export type AdminSubscriptionChangeApplyInput = SubscriptionChangeApplyInput & {
  reason: string;
};

export type SubscriptionOverridesInput = {
  addOnCodes: string[];
  quotaPackages: QuotaPackageSelection[];
};

export type SubscriptionOverrideChoicePage<T> = {
  content: T[];
  retainedSelections: T[];
  page: number;
  size: number;
  hasMoreCandidates: boolean;
};
export type SubscriptionAddOnOverrideChoice = {
  productId: UUID;
  code: string;
  name: string;
  featureCodes: string[];
  requiredAddOnCodes: string[];
  priceEntryId: UUID;
  unitPrice: ExactDecimal;
  currencyCode: string;
  billingCycle: BillingCycle;
  state: RetainedEntitlementState;
  retained: boolean;
  removable: boolean;
  unavailabilityReasons: ExtensionAvailabilityIssue[];
};
export type SubscriptionQuotaPackageOverrideChoice = {
  productId: UUID;
  code: string;
  name: string;
  featureCode: string;
  resource: string;
  capacityPerUnit: number;
  repeatable: boolean;
  maximumQuantity: number;
  retainedQuantity: number | null;
  requiredAddOnCodes: string[];
  priceEntryId: UUID;
  unitPrice: ExactDecimal;
  currencyCode: string;
  billingCycle: BillingCycle;
  state: RetainedEntitlementState;
  retained: boolean;
  removable: boolean;
  quantityEditable: boolean;
  unavailabilityReasons: ExtensionAvailabilityIssue[];
};

export type ManualCheckoutConfirmationInput = {
  reference: string;
  reason: string;
};

export type SubscriptionCheckout = {
  id: UUID;
  status: "PENDING_CONFIRMATION" | "CONFIRMED" | "FAILED" | "CANCELLED";
  amount: ExactDecimal;
  currencyCode: string;
  gatewayAttemptStatus: "SUCCESS" | "FAILED" | "PENDING" | null;
  gatewayReference: string | null;
  gatewayFailureReason: string | null;
  confirmationSource: "MANUAL_OPERATOR" | "TRUSTED_PROVIDER" | null;
  confirmationReference: string | null;
  confirmedAt: Instant | null;
};
