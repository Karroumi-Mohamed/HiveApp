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
  details?: unknown;
  requestId?: string | null;
};

export type AuditActorSurface =
  "PLATFORM_ADMIN" | "CLIENT_WORKSPACE" | "SYSTEM";
export type AuditOutcome = "SUCCEEDED" | "FAILED";

export type PlatformActivityActorIdentity = {
  displayName: string;
  email: string;
  active: boolean;
};

export type PlatformActivityAccountIdentity = {
  name: string;
  slug: string;
  active: boolean;
};

export type PlatformActivity = {
  id: UUID;
  occurredAt: Instant;
  actorSurface: AuditActorSurface;
  actorUserId: UUID | null;
  actorIdentity: PlatformActivityActorIdentity | null;
  clientAccountId: UUID | null;
  targetAccountId: UUID | null;
  accountIdentity: PlatformActivityAccountIdentity | null;
  targetCompanyId: UUID | null;
  collaborationId: UUID | null;
  action: string;
  resourceType: string;
  resourceId: string | null;
  outcome: AuditOutcome;
  requestMethod: string | null;
  requestPath: string | null;
  requestId: string | null;
  payloadAvailable: boolean;
};

export type PlatformActivityPayload = {
  id: UUID;
  requestData: string | null;
  resultData: string | null;
  failureType: string | null;
};

export type PlatformActivityActorResolution = {
  activityId: UUID;
  actorUserId: UUID;
  identity: PlatformActivityActorIdentity | null;
};

export type PlatformActivityAccountResolution = {
  activityId: UUID;
  accountId: UUID;
  identity: PlatformActivityAccountIdentity | null;
};

export type CredentialTokenPurpose =
  "ACTIVATION" | "PASSWORD_RESET" | "EMAIL_VERIFICATION";
export type PlatformEmailDeliveryStatus =
  "PENDING" | "SENT" | "FAILED" | "SUPPRESSED";

export type PlatformCommunicationSummary = {
  total: number;
  byStatus: Partial<Record<PlatformEmailDeliveryStatus, number>>;
  byPurpose: Partial<Record<CredentialTokenPurpose, number>>;
};

export type PlatformCommunication = {
  id: UUID;
  accountId: UUID | null;
  recipientUserId: UUID | null;
  recipientEmail: string | null;
  purpose: CredentialTokenPurpose;
  status: PlatformEmailDeliveryStatus;
  createdAt: Instant;
  attemptedAt: Instant | null;
  deliveredAt: Instant | null;
  failureCode: string | null;
  recipientIdentityVisible: boolean;
  failureEvidenceVisible: boolean;
};

export type PlatformCommunicationRecipient = {
  deliveryId: UUID;
  userId: UUID;
  email: string;
};

export type PlatformCommunicationFailureEvidence = {
  deliveryId: UUID;
  attemptedAt: Instant | null;
  deliveredAt: Instant | null;
  failureCode: string | null;
};

export type PlatformComponentState =
  "UP" | "CONFIGURED" | "SUPPRESSED" | "DISABLED" | "DEGRADED" | "UNAVAILABLE";

export type PlatformHealth = {
  generatedAt: Instant;
  components: Array<{
    key: string;
    label: string;
    state: PlatformComponentState;
    guidance: string | null;
  }>;
};

export type PlatformBacklogs = {
  generatedAt: Instant;
  components: Array<{
    key: string;
    label: string;
    counts: Record<string, number>;
    oldestAttentionAt: Instant | null;
    attentionRequired: boolean;
    destination: string | null;
  }>;
};

export type PlatformLogAccess = {
  configured: boolean;
  provider: string | null;
  destination: string | null;
  guidance: string | null;
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

export type CommercialAnalyticsInterval = "DAY" | "WEEK" | "MONTH";
export type CommercialAttentionType =
  "PAST_DUE" | "SUSPENDED" | "OPEN_INVOICE" | "CHANGE_NEEDS_ATTENTION";
export type CommercialAnalyticsProductType =
  "PLAN" | "ADD_ON" | "QUOTA_PACKAGE";

export type CommercialAnalyticsMetadata = {
  generatedAt: Instant;
  completeThrough: Instant;
  from: Instant;
  until: Instant;
  timezone: string;
  interval: CommercialAnalyticsInterval;
  currentBucketProvisional: boolean;
};

export type CommercialAnalyticsAvailability = {
  financialSeries: boolean;
  subscriptionSeries: boolean;
  offerSeries: boolean;
  operations: boolean;
};

export type CommercialMoneyDimension = {
  currencyCode: string;
  billingCycle: BillingCycle;
};

export type CommercialFinancialTotals = {
  dimension: CommercialMoneyDimension;
  invoiced: ExactDecimal;
  collected: ExactDecimal;
  credited: ExactDecimal;
  refunded: ExactDecimal;
};

export type CommercialConfiguredRecurringValue = {
  dimension: CommercialMoneyDimension;
  amount: ExactDecimal;
  subscriptions: number;
};

export type CommercialAnalyticsOverview = {
  metadata: CommercialAnalyticsMetadata;
  availability: CommercialAnalyticsAvailability;
  financialTotals: CommercialFinancialTotals[];
  configuredRecurringValues: CommercialConfiguredRecurringValue[];
  currentSubscriptions: Partial<Record<SubscriptionStatus, number>>;
  operationsNeedingAttention: number;
  graceDeadlinesWithinSevenDays: number;
  offerOutcomes: Record<string, number>;
  finality: {
    pendingPayments: number;
    pendingRefunds: number;
    pendingProviderCommands: number;
  };
};

export type CommercialFinancialPoint = {
  bucketStart: Instant;
  bucketEnd: Instant;
  provisional: boolean;
  invoiced: ExactDecimal;
  collected: ExactDecimal;
  credited: ExactDecimal;
  refunded: ExactDecimal;
};

export type CommercialFinancialSeries = {
  metadata: CommercialAnalyticsMetadata;
  dimensions: {
    dimension: CommercialMoneyDimension;
    points: CommercialFinancialPoint[];
  }[];
};

export type CommercialSubscriptionPoint = {
  bucketStart: Instant;
  bucketEnd: Instant;
  provisional: boolean;
  lifecycleActions: Record<string, number>;
  productsAdded: number;
  productsRemoved: number;
};

export type CommercialProductMovement = {
  productType: CommercialAnalyticsProductType;
  productCode: string;
  additions: number;
  removals: number;
};

export type CommercialSubscriptionSeries = {
  metadata: CommercialAnalyticsMetadata;
  points: CommercialSubscriptionPoint[];
  productMovements: CommercialProductMovement[];
};

export type CommercialOfferPoint = {
  bucketStart: Instant;
  bucketEnd: Instant;
  provisional: boolean;
  reserved: number;
  applied: number;
  cancelled: number;
  failed: number;
};

export type CommercialOfferSeries = {
  metadata: CommercialAnalyticsMetadata;
  points: CommercialOfferPoint[];
};

export type CommercialProductHolding = {
  productType: CommercialAnalyticsProductType;
  productCode: string;
  subscriptions: number;
};

export type CommercialAttentionRow = {
  type: CommercialAttentionType;
  recordId: UUID;
  accountId: UUID;
  accountName: string;
  status: string;
  occurredAt: Instant;
  dueAt: Instant | null;
  amount: ExactDecimal | null;
  currencyCode: string | null;
  reason: string | null;
  destination: string;
};

export type CommercialAnalyticsQuery = {
  from?: Instant;
  until?: Instant;
  timezone?: string;
  interval?: CommercialAnalyticsInterval;
  currencyCode?: string;
  billingCycle?: BillingCycle;
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
export type BillingInvoiceStatus =
  "OPEN" | "SETTLED" | "SETTLED_ZERO" | "CANCELLED";
export type BillingLineType =
  "PLAN" | "ADD_ON" | "QUOTA_PACKAGE" | "COMMERCIAL_ADJUSTMENT";
export type BillingPaymentKind = "PROVIDER" | "MANUAL";
export type BillingPaymentStatus =
  "PENDING" | "SUCCEEDED" | "FAILED" | "CANCELLED";
export type BillingRefundKind = "PROVIDER" | "MANUAL";
export type BillingRefundStatus = "PENDING" | "SUCCEEDED" | "FAILED";
export type BillingOutboxOperation = "CHARGE" | "REFUND";
export type BillingOutboxStatus =
  "PENDING" | "PROCESSING" | "PROCESSED" | "FAILED" | "CANCELLED";
export type BillingProviderEventStatus =
  "RECEIVED" | "APPLIED" | "UNMATCHED" | "MISMATCHED";
export type BillingProviderPaymentStatus = "SUCCESS" | "FAILED" | "PENDING";
export type BillingTimelineEntryType =
  "INVOICE" | "PAYMENT" | "CREDIT" | "REFUND";

export type BillingAccountIdentity = { id: UUID; name: string };

export type BillingInvoiceRow = {
  id: UUID;
  invoiceNumber: string;
  status: BillingInvoiceStatus;
  totalAmount: ExactDecimal;
  currencyCode: string;
  billingCycle: BillingCycle;
  periodStart: Instant;
  periodEnd: Instant;
  issuedAt: Instant;
  settledAt: Instant | null;
  changeOperationId: UUID;
  /** Present only when the caller may read Account identity. */
  account: BillingAccountIdentity | null;
};

export type BillingInvoiceLine = {
  id: UUID;
  position: number;
  type: BillingLineType;
  sourceCode: string;
  sourceName: string;
  sourceVersion: number;
  priceEntryId: UUID;
  quantity: number;
  unitAmount: ExactDecimal;
  lineAmount: ExactDecimal;
  currencyCode: string;
};

export type BillingPayment = {
  id: UUID;
  kind: BillingPaymentKind;
  status: BillingPaymentStatus;
  amount: ExactDecimal;
  currencyCode: string;
  trustedForSettlement: boolean;
  externalReference: string | null;
  failureReason: string | null;
  operatorUserId: UUID | null;
  operatorReason: string | null;
  retryOfPaymentId: UUID | null;
  recoveryReference: string | null;
  completedAt: Instant | null;
  createdAt: Instant;
};

export type BillingSafePayment = Pick<
  BillingPayment,
  "kind" | "status" | "amount" | "currencyCode" | "completedAt"
>;

export type BillingCredit = {
  id: UUID;
  amount: ExactDecimal;
  currencyCode: string;
  reason: string;
  source: string;
  operatorUserId: UUID;
  externalReference: string | null;
  issuedAt: Instant;
};

export type BillingRefund = {
  id: UUID;
  paymentId: UUID;
  kind: BillingRefundKind;
  status: BillingRefundStatus;
  amount: ExactDecimal;
  currencyCode: string;
  reason: string;
  operatorUserId: UUID;
  providerReference: string | null;
  failureReason: string | null;
  completedAt: Instant | null;
  createdAt: Instant;
};

export type BillingInvoiceDetail = {
  invoice: BillingInvoiceRow;
  lines: BillingInvoiceLine[];
  payments: BillingPayment[];
  credits: BillingCredit[];
  refunds: BillingRefund[];
  creditedAmount: ExactDecimal;
  refundedAmount: ExactDecimal;
};

export type ClientBillingInvoiceDetail = {
  invoice: BillingInvoiceRow;
  lines: BillingInvoiceLine[];
  payments: BillingSafePayment[];
  creditedAmount: ExactDecimal;
  refundedAmount: ExactDecimal;
};

export type BillingFinancialTimelineEntry = {
  recordId: UUID;
  type: BillingTimelineEntryType;
  status: string;
  amount: ExactDecimal;
  currencyCode: string;
  invoiceId: UUID;
  invoiceNumber: string;
  occurredAt: Instant;
};

export type AccountBillingProfile = {
  accountId: UUID;
  legalName: string;
  billingEmail: string | null;
  taxId: string | null;
  address: string | null;
  countryCode: string | null;
  explicitlyConfigured: boolean;
};

export type AccountBillingProfileInput = {
  legalName: string;
  billingEmail?: string | null;
  taxId?: string | null;
  address?: string | null;
  countryCode?: string | null;
};

export type BillingDocumentParty = {
  name: string;
  billingEmail: string | null;
  address: string | null;
  countryCode: string | null;
  taxId: string | null;
};

export type BillingInvoiceDocument = {
  invoice: BillingInvoiceRow;
  seller: BillingDocumentParty;
  customer: BillingDocumentParty;
  lines: BillingInvoiceLine[];
  creditedAmount: ExactDecimal;
  refundedAmount: ExactDecimal;
  fiscalReady: boolean;
  missingFiscalFields: string[];
};

export type BillingRefundPreview = {
  paymentId: UUID;
  paymentAmount: ExactDecimal;
  reservedRefundAmount: ExactDecimal;
  remainingRefundableAmount: ExactDecimal;
  currencyCode: string;
  providerRefundAllowed: boolean;
  manualRefundAllowed: boolean;
  providerBlocker: string | null;
  manualBlocker: string | null;
};

export type BillingOutboxRow = {
  id: UUID;
  operation: BillingOutboxOperation;
  aggregateId: UUID;
  status: BillingOutboxStatus;
  attemptCount: number;
  nextAttemptAt: Instant | null;
  claimedAt: Instant | null;
  processedAt: Instant | null;
  lastError: string | null;
  createdAt: Instant;
};

export type BillingProviderEventRow = {
  id: UUID;
  provider: string;
  eventId: string;
  operation: BillingOutboxOperation;
  providerStatus: BillingProviderPaymentStatus;
  amount: ExactDecimal;
  currencyCode: string;
  processingStatus: BillingProviderEventStatus;
  aggregateId: UUID;
  outboxCommandId: UUID | null;
  providerReference: string | null;
  attentionReason: string | null;
  occurredAt: Instant;
  processedAt: Instant | null;
  createdAt: Instant;
};

export type BillingChargeRetryPreview = {
  previousPaymentId: UUID;
  previousPaymentStatus: BillingPaymentStatus;
  previousCommandStatus: BillingOutboxStatus;
  retryAllowed: boolean;
  providerConfirmationRequired: boolean;
  blocker: string | null;
};
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
  | "DELETE_DRAFT"
  | "CHANGE_PRICE"
  | "RESCHEDULE_CHANGE"
  | "CANCEL_CHANGE";
export type ProductPriceBlocker =
  | "OWNER_NOT_ACTIVE"
  | "EFFECTIVE_WINDOW_EXPIRED"
  | "ACTIVE_WINDOW_OVERLAP"
  | "SUCCESSOR_ALREADY_EXISTS"
  | "WRONG_LIFECYCLE_STATE"
  | "ACTIVE_MUST_BE_PAUSED"
  | "ARCHIVED_TERMINAL"
  | "CONTINUOUS_PRICE_REQUIRED";
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

export type ProductPriceChangeRequest = {
  operation: "CHANGE" | "RESCHEDULE" | "CANCEL";
  currentVersion: number;
  scheduledPriceId?: UUID | null;
  scheduledVersion?: number | null;
  timing: "NOW" | "SCHEDULED" | null;
  amount: ExactDecimal | null;
  effectiveFrom: Instant | null;
  reason: string;
};
export type ProductPriceChangePreview = {
  change: ProductPriceChangeRequest;
  currentPrice: ProductPrice;
  scheduledPrice: ProductPrice | null;
  evaluatedAt: Instant;
  cutoff: Instant | null;
  expiresAt: Instant;
  previewToken: string;
  blockingOfferCount: number;
  blockers: string[];
  allowed: boolean;
};
export type ProductPriceChangeResult = {
  previousPrice: ProductPrice;
  successorPrice: ProductPrice | null;
  cutoff: Instant | null;
  existingResult: boolean;
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

export type CommercialPolicyStatus =
  "DRAFT" | "ACTIVE" | "PAUSED" | "ENDED" | "ARCHIVED";
export type CommercialPolicyTargetKind =
  "ACCOUNT" | "ACCOUNT_SET" | "SEGMENT" | "PLAN_REVISION_SUBSCRIBERS";
export type CommercialPolicySource =
  | "CONTRACT"
  | "SALES"
  | "MARKETING"
  | "RETENTION"
  | "SUPPORT"
  | "COMPLIANCE"
  | "OTHER";
export type CommercialPolicyEffectType =
  | "FIXED_SUBSCRIPTION_PRICE"
  | "FIXED_DISCOUNT"
  | "PERCENTAGE_DISCOUNT"
  | "ALLOW_PRODUCT_SELECTION"
  | "BLOCK_PRODUCT_SELECTION"
  | "ADDITIVE_QUOTA_BONUS"
  | "GRANT_ADD_ON"
  | "GRANT_QUOTA_PACKAGE"
  | "BLOCK_FEATURE";
export type CommercialPolicyProductType = "PLAN" | "ADD_ON" | "QUOTA_PACKAGE";
export type CommercialPolicyCreationReason =
  "CREATED" | "DUPLICATED" | "REVISED";
export type CommercialPolicyAction =
  | "EDIT_DRAFT"
  | "DUPLICATE"
  | "REVISE"
  | "COMPARE"
  | "PREVIEW_AUDIENCE"
  | "PREVIEW_ACTIVATION"
  | "ACTIVATE"
  | "PAUSE"
  | "RESUME"
  | "END"
  | "ARCHIVE"
  | "DELETE_DRAFT"
  | "READ_HISTORY"
  | "READ_REVISIONS"
  | "READ_ACTIVATIONS"
  | "READ_ACTIVATION_ACCOUNTS"
  | "READ_OWNER"
  | "REASSIGN_OWNER";
export type CommercialPolicyBlocker =
  | "WRONG_LIFECYCLE_STATE"
  | "EFFECTIVE_WINDOW_EXPIRED"
  | "SEGMENT_RESOLUTION_UNAVAILABLE"
  | "TARGET_NOT_CONFIGURED"
  | "TARGET_ACCOUNT_MISSING"
  | "PLAN_REVISION_MISSING"
  | "EXPLICIT_ACCOUNT_SET_EMPTY"
  | "EXPLICIT_ACCOUNT_MISSING"
  | "AUDIENCE_EXCEEDS_ACTIVATION_LIMIT"
  | "LINEAGE_HAS_MULTIPLE_CURRENT_REVISIONS"
  | "NO_EFFECTS"
  | "INVALID_EFFECT"
  | "PRODUCT_REVISION_MISSING"
  | "PRODUCT_REVISION_NOT_ACTIVE"
  | "FEATURE_MISSING"
  | "FEATURE_NOT_COMMERCIALLY_GRANTABLE"
  | "QUOTA_RESOURCE_NOT_DECLARED"
  | "AUDIENCE_CURRENCY_MISMATCH";
export type CommercialPolicyExecutionBlocker =
  "SCHEDULED_EXECUTION_NOT_AVAILABLE";

export type CommercialPolicySummary = {
  id: UUID;
  code: string;
  name: string;
  status: CommercialPolicyStatus;
  targetKind: CommercialPolicyTargetKind;
  targetLabel: string | null;
  configuredTargetCount: number;
  effectCount: number;
  latestAffectedAccountCount: number | null;
  source: CommercialPolicySource;
  priority: number;
  effectiveFrom: Instant;
  effectiveUntil: Instant | null;
  lineageId: UUID;
  revisionNumber: number;
  creationReason: CommercialPolicyCreationReason;
  version: number;
  createdAt: Instant;
  updatedAt: Instant;
  availableActions: CommercialPolicyAction[];
  blockers: CommercialPolicyBlocker[];
  ownerIdentityRestricted: boolean;
  executionSupported: boolean;
  executionBlockers: CommercialPolicyExecutionBlocker[];
};

export type CommercialPolicyTarget = {
  kind: CommercialPolicyTargetKind;
  accountId: UUID | null;
  accountName: string | null;
  accountIds: UUID[];
  planRevisionId: UUID | null;
  planCode: string | null;
  planName: string | null;
  segmentReference: string | null;
};

export type CommercialPolicyTargetInput = {
  kind: CommercialPolicyTargetKind;
  accountId: UUID | null;
  accountIds: UUID[];
  planRevisionId: UUID | null;
  segmentReference: string | null;
};

export type CommercialPolicySegmentChoice = {
  id: UUID;
  code: string;
  name: string;
  revisionNumber: number;
  kind: CommercialSegmentKind;
  immutableAccountCount: number;
};

export type CommercialPolicyEffectInput = {
  type: CommercialPolicyEffectType;
  productType: CommercialPolicyProductType | null;
  productRevisionId: UUID | null;
  featureCode: string | null;
  quotaResource: string | null;
  quantityDelta: number | null;
  amount: ExactDecimal | null;
  currencyCode: string | null;
  billingCycle: ProductPriceBillingCycle | null;
  percentage: ExactDecimal | null;
  maximumAmount: ExactDecimal | null;
  maximumCurrencyCode: string | null;
};

export type CommercialPolicyEffect = CommercialPolicyEffectInput & {
  id: UUID;
  order: number;
  productCode: string | null;
  precedenceClass: number;
  canOverridePlatformHardLimits: boolean;
};

export type CommercialPolicyDetail = {
  summary: CommercialPolicySummary;
  description: string | null;
  reason: string;
  approvalReference: string | null;
  contractReference: string | null;
  target: CommercialPolicyTarget;
  effects: CommercialPolicyEffect[];
  sourcePolicyId: UUID | null;
};

export type CommercialPolicyWriteInput = {
  name: string;
  description: string | null;
  effectiveFrom: Instant;
  effectiveUntil: Instant | null;
  source: CommercialPolicySource;
  priority: number;
  reason: string;
  approvalReference: string | null;
  contractReference: string | null;
  target: CommercialPolicyTargetInput;
  effects: CommercialPolicyEffectInput[];
};

export type CommercialPolicyAudienceAccount = {
  id: UUID;
  name: string | null;
  slug: string | null;
  active: boolean;
};
export type CommercialPolicyAudiencePreview = {
  policyId: UUID;
  expectedVersion: number;
  targetKind: CommercialPolicyTargetKind;
  totalAccounts: number;
  activationAccountLimit: number;
  withinActivationLimit: boolean;
  accounts: PageResponse<CommercialPolicyAudienceAccount>;
  blockers: CommercialPolicyBlocker[];
};
export type CommercialPolicyActivationPreview = {
  policyId: UUID;
  expectedVersion: number;
  catalogRevision: number;
  registryVersion: string;
  evaluatedAt: Instant;
  expiresAt: Instant;
  previewToken: string;
  activatable: boolean;
  blockers: CommercialPolicyBlocker[];
  affectedAccountCount: number;
  sampleAccounts: CommercialPolicyAudienceAccount[];
  policyRevisionToEnd: UUID | null;
  executionSupported: boolean;
  executionBlockers: CommercialPolicyExecutionBlocker[];
};
export type CommercialPolicyActivation = {
  id: UUID;
  activationNumber: number;
  policyId: UUID;
  actorUserId: UUID;
  evaluatedAt: Instant;
  evidenceExpiresAt: Instant;
  catalogRevision: number;
  registryVersion: string;
  affectedAccountCount: number;
  reason: string;
  recordedAt: Instant;
};
export type CommercialPolicyActivationAudience = {
  policyId: UUID;
  activationId: UUID;
  activationNumber: number;
  immutableAccountCount: number;
  accounts: PageResponse<CommercialPolicyAudienceAccount>;
};
export type CommercialPolicyOwner = {
  policyId: UUID;
  adminUserId: UUID;
  userId: UUID;
  email: string;
  username: string;
  displayName: string | null;
  active: boolean;
};
export type CommercialPolicyComparison = {
  sourcePolicyId: UUID;
  comparedPolicyId: UUID;
  sameLineage: boolean;
  directSuccessor: boolean;
  changedFields: string[];
  source: CommercialPolicyDetail;
  compared: CommercialPolicyDetail;
};
export type CommercialPolicyRevision = {
  id: UUID;
  code: string;
  status: CommercialPolicyStatus;
  revisionNumber: number;
  sourcePolicyId: UUID | null;
  version: number;
  createdAt: Instant;
};
export type CommercialPolicyHistory = {
  id: UUID;
  action: string;
  outcome: "SUCCEEDED" | "FAILED";
  actorUserId: UUID | null;
  actorEmail: string | null;
  reason: string | null;
  occurredAt: Instant;
};

export type CommercialSegmentStatus = "DRAFT" | "ACTIVE" | "ARCHIVED";
export type CommercialSegmentKind = "EXPLICIT_ACCOUNTS" | "TYPED_CRITERIA";
export type CommercialSegmentProductType = "PLAN" | "ADD_ON" | "QUOTA_PACKAGE";
export type CommercialSegmentCreationReason =
  "CREATED" | "DUPLICATED" | "REVISED";
export type CommercialSegmentAction =
  | "EDIT_DRAFT"
  | "DUPLICATE"
  | "COUNT"
  | "PREVIEW"
  | "READ_SAMPLE_IDENTITIES"
  | "ACTIVATE"
  | "REVISE"
  | "COMPARE"
  | "ARCHIVE"
  | "DELETE_DRAFT"
  | "READ_HISTORY"
  | "READ_REVISIONS"
  | "READ_ACTIVATIONS"
  | "READ_ACTIVATION_AUDIENCE"
  | "READ_ACTIVATION_IDENTITIES"
  | "READ_OWNER"
  | "REASSIGN_OWNER";
export type CommercialSegmentBlocker =
  | "EMPTY_AUDIENCE"
  | "AUDIENCE_EXCEEDS_ACTIVATION_LIMIT"
  | "NOT_DRAFT"
  | "NOT_ACTIVE"
  | "ALREADY_ARCHIVED"
  | "NOT_LATEST_REVISION"
  | "HAS_POLICY_REFERENCES"
  | "HAS_ACTIVATION_HISTORY"
  | "ACTIVE_SUCCESSOR_EXISTS";

export type CommercialSegmentProductHolding = {
  type: CommercialSegmentProductType;
  code: string;
};

export type CommercialSegmentCriteria = {
  currentPlanRevisionIds: UUID[];
  subscriptionStatuses: SubscriptionStatus[];
  currencyCodes: string[];
  billingCycles: BillingCycle[];
  accountCreatedFrom: Instant | null;
  accountCreatedUntil: Instant | null;
  productHoldings: CommercialSegmentProductHolding[];
  semantics?: string;
};

export type CommercialSegmentDefinition = {
  explicitAccountIds: UUID[];
  criteria: CommercialSegmentCriteria | null;
};

export type CommercialSegmentSummary = {
  id: UUID;
  code: string;
  name: string;
  status: CommercialSegmentStatus;
  kind: CommercialSegmentKind;
  configuredAccountCount: number;
  latestActivationAccountCount: number | null;
  lineageId: UUID;
  revisionNumber: number;
  creationReason: CommercialSegmentCreationReason;
  version: number;
  createdAt: Instant;
  updatedAt: Instant;
  availableActions: CommercialSegmentAction[];
  blockedActions: Partial<
    Record<CommercialSegmentAction, CommercialSegmentBlocker[]>
  >;
  ownerIdentityRestricted: boolean;
  audienceIdentityRestricted: boolean;
};

export type CommercialSegmentDetail = {
  summary: CommercialSegmentSummary;
  description: string | null;
  reason: string;
  definition: CommercialSegmentDefinition;
  sourceSegmentId: UUID | null;
};

export type CommercialSegmentWriteInput = {
  name: string;
  description: string | null;
  kind: CommercialSegmentKind;
  reason: string;
  definition: CommercialSegmentDefinition;
};

export type CommercialSegmentCount = {
  segmentId: UUID;
  criteriaVersion: number;
  evaluatedAt: Instant;
  totalAccounts: number;
  activationAccountLimit: number;
  withinActivationLimit: boolean;
};

export type CommercialSegmentAudienceReference = { accountId: UUID };
export type CommercialSegmentAudienceIdentity = {
  accountId: UUID;
  accountName: string | null;
  accountSlug: string | null;
  ownerEmail: string | null;
  active: boolean;
};
export type CommercialSegmentPreview = {
  segmentId: UUID;
  criteriaVersion: number;
  evaluatedAt: Instant;
  expiresAt: Instant;
  previewToken: string;
  totalAccounts: number;
  activationAccountLimit: number;
  activatable: boolean;
  blockers: CommercialSegmentBlocker[];
  sample: CommercialSegmentAudienceReference[];
};
export type CommercialSegmentIdentitySample = {
  segmentId: UUID;
  criteriaVersion: number;
  evaluatedAt: Instant;
  totalAccounts: number;
  sample: CommercialSegmentAudienceIdentity[];
};
export type CommercialSegmentActivation = {
  id: UUID;
  activationNumber: number;
  segmentId: UUID;
  actorUserId: UUID;
  evaluatedAt: Instant;
  evidenceExpiresAt: Instant;
  criteriaVersion: number;
  affectedAccountCount: number;
  reason: string;
  recordedAt: Instant;
};
export type CommercialSegmentActivationAudience = {
  segmentId: UUID;
  activationId: UUID;
  activationNumber: number;
  immutableAccountCount: number;
  accounts: PageResponse<CommercialSegmentAudienceReference>;
};
export type CommercialSegmentActivationIdentityAudience = Omit<
  CommercialSegmentActivationAudience,
  "accounts"
> & {
  accounts: PageResponse<CommercialSegmentAudienceIdentity>;
};
export type CommercialSegmentOwner = {
  segmentId: UUID;
  adminUserId: UUID;
  userId: UUID;
  email: string;
  username: string;
  displayName: string | null;
  active: boolean;
};
export type CommercialSegmentRevision = {
  id: UUID;
  code: string;
  status: CommercialSegmentStatus;
  revisionNumber: number;
  sourceSegmentId: UUID | null;
  version: number;
  createdAt: Instant;
};
export type CommercialSegmentComparison = {
  sourceSegmentId: UUID;
  comparedSegmentId: UUID;
  sameLineage: boolean;
  directSuccessor: boolean;
  changedFields: string[];
  source: CommercialSegmentDetail;
  compared: CommercialSegmentDetail;
};
export type CommercialSegmentHistory = {
  id: UUID;
  action: string;
  outcome: "SUCCEEDED" | "FAILED";
  actorUserId: UUID | null;
  actorEmail: string | null;
  reason: string | null;
  occurredAt: Instant;
};

export type CommercialCampaignStatus =
  "DRAFT" | "SCHEDULED" | "ACTIVE" | "PAUSED" | "ENDED" | "ARCHIVED";
export type CommercialCampaignAudienceMode =
  "PUBLIC" | "EXPLICIT_ACCOUNTS" | "SEGMENT";
export type CommercialCampaignSource =
  "MARKETING" | "SALES" | "RETENTION" | "SUPPORT" | "MANUAL";
export type CommercialCampaignCreationReason =
  "CREATED" | "DUPLICATED" | "REVISED";
export type CommercialCampaignSegmentChoiceState =
  | "AVAILABLE"
  | "SEGMENT_INACTIVE"
  | "ACTIVATION_SUPERSEDED"
  | "ACTIVATION_UNAVAILABLE";
export type CommercialCampaignAction =
  | "UPDATE"
  | "DUPLICATE"
  | "REVISE"
  | "COMPARE"
  | "HISTORY"
  | "PREVIEW_SCHEDULE"
  | "SCHEDULE"
  | "PAUSE"
  | "RESUME"
  | "END"
  | "ARCHIVE"
  | "DELETE_DRAFT"
  | "OWNER"
  | "REASSIGN_OWNER"
  | "REVISIONS"
  | "READ_AUDIENCE"
  | "READ_AUDIENCE_IDENTITIES";
export type CommercialCampaignBlocker =
  | "NOT_DRAFT"
  | "NOT_SCHEDULED"
  | "NOT_ACTIVE"
  | "NOT_PAUSED"
  | "NOT_ENDED"
  | "ALREADY_ARCHIVED"
  | "INVALID_WINDOW"
  | "WINDOW_ENDED"
  | "EMPTY_AUDIENCE"
  | "AUDIENCE_TOO_LARGE"
  | "SEGMENT_NOT_ACTIVE"
  | "SEGMENT_ACTIVATION_STALE"
  | "NOT_LATEST_REVISION"
  | "DRAFT_SUCCESSOR_EXISTS"
  | "LIVE_LINEAGE_REVISION_EXISTS"
  | "HAS_SCHEDULE_HISTORY"
  | "HAS_DERIVED_CAMPAIGNS";

export type CommercialCampaignSummary = {
  id: UUID;
  code: string;
  name: string;
  status: CommercialCampaignStatus;
  audienceMode: CommercialCampaignAudienceMode;
  source: CommercialCampaignSource;
  startsAt: Instant;
  endsAt: Instant;
  configuredAccountCount: number | null;
  frozenAccountCount: number | null;
  lineageId: UUID;
  revisionNumber: number;
  creationReason: CommercialCampaignCreationReason;
  version: number;
  createdAt: Instant;
  updatedAt: Instant;
  availableActions: CommercialCampaignAction[];
  blockedActions: Partial<
    Record<CommercialCampaignAction, CommercialCampaignBlocker[]>
  >;
  ownerIdentityRestricted: boolean;
  audienceIdentityRestricted: boolean;
};

export type CommercialCampaignAudienceInput = {
  mode: CommercialCampaignAudienceMode;
  explicitAccountIds: UUID[];
  segmentId: UUID | null;
  segmentActivationId: UUID | null;
};

export type CommercialCampaignDetail = {
  summary: CommercialCampaignSummary;
  description: string | null;
  reason: string;
  audience: CommercialCampaignAudienceInput;
  sourceCampaignId: UUID | null;
  scheduledAt: Instant | null;
  activatedAt: Instant | null;
  pausedAt: Instant | null;
  resumedAt: Instant | null;
  endedAt: Instant | null;
  archivedAt: Instant | null;
};

export type CommercialCampaignOperationState = Pick<
  CommercialCampaignSummary,
  | "id"
  | "code"
  | "name"
  | "status"
  | "revisionNumber"
  | "version"
  | "availableActions"
  | "blockedActions"
>;

export type CommercialCampaignEditableDefinition = {
  campaignId: UUID;
  code: string;
  name: string;
  status: CommercialCampaignStatus;
  description: string | null;
  reason: string;
  audience: CommercialCampaignAudienceInput;
  startsAt: Instant;
  endsAt: Instant;
  source: CommercialCampaignSource;
  lineageId: UUID;
  revisionNumber: number;
  version: number;
};

export type CommercialCampaignMutation = {
  campaignId: UUID;
  status: CommercialCampaignStatus;
  version: number;
};

export type CommercialCampaignWriteInput = {
  name: string;
  description: string | null;
  startsAt: Instant;
  endsAt: Instant;
  source: CommercialCampaignSource;
  reason: string;
  audience: CommercialCampaignAudienceInput;
};

export type CommercialCampaignAudienceReference = { accountId: UUID };
export type CommercialCampaignAudienceIdentity = {
  accountId: UUID;
  accountName: string | null;
  accountSlug: string | null;
  ownerEmail: string | null;
  active: boolean;
};
export type CommercialCampaignSchedulePreview = {
  campaignId: UUID;
  campaignVersion: number;
  mode: CommercialCampaignAudienceMode;
  evaluatedAt: Instant;
  expiresAt: Instant;
  previewToken: string;
  targetedAccountCount: number | null;
  publicAudience: boolean;
  schedulable: boolean;
  blockers: CommercialCampaignBlocker[];
  sample: CommercialCampaignAudienceReference[];
  segmentId: UUID | null;
  segmentActivationId: UUID | null;
  fingerprint: string;
  registryVersion: string;
};
export type CommercialCampaignFrozenAudience = {
  campaignId: UUID;
  snapshotId: UUID;
  mode: CommercialCampaignAudienceMode;
  publicAudience: boolean;
  immutableAccountCount: number;
  segmentId: UUID | null;
  segmentActivationId: UUID | null;
  evaluatedAt: Instant;
  reviewedByActorUserId: UUID;
  evidenceExpiresAt: Instant;
  campaignVersion: number;
  catalogRevision: number;
  registryVersion: string;
  audienceFingerprint: string;
  reason: string;
  startsAt: Instant;
  endsAt: Instant;
  accounts: PageResponse<CommercialCampaignAudienceReference>;
};
export type CommercialCampaignFrozenIdentityAudience = {
  campaignId: UUID;
  snapshotId: UUID;
  immutableAccountCount: number;
  accounts: PageResponse<CommercialCampaignAudienceIdentity>;
};
export type CommercialCampaignOwner = {
  campaignId: UUID;
  adminUserId: UUID;
  userId: UUID;
  email: string;
  username: string;
  displayName: string | null;
  active: boolean;
  status: CommercialCampaignStatus;
  version: number;
};
export type CommercialCampaignOwnerMutation = {
  campaignId: UUID;
  status: CommercialCampaignStatus;
  version: number;
};
export type CommercialCampaignOwnerChoice = {
  adminUserId: UUID;
  email: string;
  username: string;
  displayName: string | null;
};
export type CommercialCampaignRevision = {
  id: UUID;
  code: string;
  status: CommercialCampaignStatus;
  revisionNumber: number;
  sourceCampaignId: UUID | null;
  version: number;
  createdAt: Instant;
};
export type CommercialCampaignComparison = {
  sourceCampaignId: UUID;
  comparedCampaignId: UUID;
  sameLineage: boolean;
  directSuccessor: boolean;
  changedFields: string[];
  source: CommercialCampaignDetail;
  compared: CommercialCampaignDetail;
};
export type CommercialCampaignHistory = {
  id: UUID;
  action: string;
  outcome: "SUCCEEDED" | "FAILED";
  actorUserId: UUID | null;
  actorEmail: string | null;
  reason: string | null;
  occurredAt: Instant;
};
export type CommercialCampaignSegmentChoice = {
  id: UUID;
  code: string;
  name: string;
  revisionNumber: number;
  kind: CommercialSegmentKind;
  activationId: UUID;
  activationNumber: number | null;
  immutableAccountCount: number | null;
  state: CommercialCampaignSegmentChoiceState;
};

export type PlanStatus = "DRAFT" | "ACTIVE" | "INACTIVE" | "ARCHIVED";
export type ProductSalesVisibility = "PUBLIC" | "DIRECT_ONLY";
export type PlanExtensionPolicy = "CLOSED" | "ALLOW_LIST" | "OPEN_COMPATIBLE";
export type CommercialTargetingMode = "OPEN_COMPATIBLE" | "TARGETED";
export type CommercialChoiceState =
  "SELECTABLE" | "NO_LONGER_ACTIVE" | "MISSING";
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
export type CommercialAvailabilityAction =
  "APPLY_PLAN_AVAILABILITY" | "APPLY_SALES_VISIBILITY";
export type RetainedEntitlementState =
  "SELECTABLE" | "RETAINED_ONLY" | "HISTORICAL_ONLY";
export type SubscriptionStatus =
  "TRIALING" | "ACTIVE" | "PAST_DUE" | "SUSPENDED" | "CANCELLED" | "EXPIRED";
export type SubscriptionSuspensionCause =
  "COLLECTION" | "OPERATOR" | "AGREEMENT_REVIEW";
export type SubscriptionLifecycleAction =
  | "CANCEL_AT_PERIOD_END"
  | "KEEP_RENEWING"
  | "CANCEL_IMMEDIATELY"
  | "SUSPEND"
  | "RESTORE"
  | "EXTEND_GRACE";

export type SubscriptionLifecycleActions = {
  subscriptionId: UUID;
  subscriptionVersion: number;
  status: SubscriptionStatus;
  suspensionCause: SubscriptionSuspensionCause | null;
  availableActions: SubscriptionLifecycleAction[];
};
export type PlanFeatureMode =
  "INCLUDED" | "OPTIONAL_ADD_ON" | "BLOCKED_FOR_PLAN";
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

export type UpdatePlanInput = Omit<
  CreatePlanInput,
  "features" | "extensionPolicy" | "salesVisibility"
> & {
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

export type PlanVersion = {
  id: UUID;
  code: string;
  name: string;
  description: string | null;
  lineageId: UUID;
  productVersionNumber: number;
  sourcePlanId: UUID | null;
  status: PlanStatus;
  extensionPolicy: PlanExtensionPolicy;
  salesVisibility: ProductSalesVisibility;
  rowVersion: number;
};
export type PlanVersionPrice = Pick<
  ProductPrice,
  | "id"
  | "amount"
  | "currencyCode"
  | "billingCycle"
  | "effectiveFrom"
  | "effectiveUntil"
>;
export type PlanFamily = {
  lineageId: UUID;
  publicVersion: PlanVersion;
  draft: PlanVersion | null;
  versionCount: number;
  currentSubscriberCount: number | null;
  currentPrices: PlanVersionPrice[];
  pricesVisible: boolean;
};
export type PlanVersions = {
  lineageId: UUID;
  publicPlanId: UUID;
  draftPlanId: UUID | null;
  catalogRevision: number;
  versions: PageResponse<PlanOperationalItem>;
};
export type PlanVersionComparison = {
  lineageId: UUID;
  source: PlanVersion;
  target: PlanVersion;
  features: {
    featureCode: string;
    before: PlanFeature | null;
    after: PlanFeature | null;
    changed: boolean;
  }[];
  extensionPolicyChanged: boolean;
  visibilityChanged: boolean;
  sourcePrices: PlanVersionPrice[];
  targetPrices: PlanVersionPrice[];
  pricesVisible: boolean;
};

export type ComparedPlan = {
  plan: PlanVersion;
  features: PlanFeature[];
  currentPrices: PlanVersionPrice[];
  scheduledPrices: PlanVersionPrice[];
};
export type PlanCatalogComparison = {
  catalogRevision: number;
  asOf: string;
  plans: ComparedPlan[];
  pricesVisible: boolean;
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
export type PlanChooserItem = CommercialChooserItem & {
  extensionPolicy: PlanExtensionPolicy;
};
export type AddOnChooserItem = CommercialChooserItem & {
  targetingMode: CommercialTargetingMode;
};
export type QuotaPackageChooserItem = CommercialChooserItem & {
  featureCode: string;
  resource: string;
  targetingMode: CommercialTargetingMode;
};

export type QuotaLimit = {
  resource: string;
  mode: QuotaLimitMode;
  limit: number | null;
};
export type PlanFeature = {
  id: UUID;
  featureCode: string;
  mode: PlanFeatureMode;
  quotaConfigs: QuotaLimit[];
};

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
  features: Array<{
    id: UUID;
    featureCode: string;
    quotaConfigs: QuotaLimit[];
  }>;
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

export type FeatureCatalogAudience =
  "ALL" | "PLAN_ASSIGNABLE" | "PUBLIC_CATALOG";
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
  control:
    "PUBLIC_VISIBILITY" | "NEW_SALES" | "NEW_GRANTS" | "EMERGENCY_RUNTIME";
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
  changeType:
    | "UPDATE"
    | "ADD_PERMISSION"
    | "REMOVE_PERMISSION"
    | "ACTIVATE"
    | "DEACTIVATE"
    | "ARCHIVE"
    | "DELETE";
  permissionCode: string | null;
  assignmentCount: number;
  affectedMemberCount: number;
  activeMemberCount: number;
  scopes: Array<{
    scope: string;
    companyId: UUID | null;
    assignmentCount: number;
  }>;
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
      permissions: Array<{
        code: string;
        name: string;
        description: string;
        action: string;
        resource: string;
      }>;
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
  status:
    "PENDING" | "ACTIVE" | "SUSPENDED" | "REJECTED" | "CANCELLED" | "REVOKED";
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

export type CommercialPolicyDecisionOutcome =
  | "APPLIED"
  | "AVAILABLE"
  | "REJECTED_LOWER_PRECEDENCE"
  | "REJECTED_INCOMPATIBLE"
  | "BLOCKED_SELECTION";

/** Privacy-safe commercial adjustment returned by Account-member APIs. */
export type ClientCommercialPolicyDecision = {
  effectType: CommercialPolicyEffectType;
  productType: CommercialPolicyProductType | null;
  productCode: string | null;
  featureCode: string | null;
  quotaResource: string | null;
  quantityDelta: number | null;
  outcome: CommercialPolicyDecisionOutcome;
  evaluatedAmount: ExactDecimal | null;
  evaluatedCurrencyCode: string | null;
  explanation: string;
};

/** Immutable operator provenance. This shape must never be used by a client endpoint. */
export type CommercialPolicyDecisionSnapshot =
  ClientCommercialPolicyDecision & {
    policyId: UUID;
    activationId: UUID;
    lineageId: UUID;
    policyRevisionNumber: number;
    policyCode: string;
    policyName: string;
    targetKind: CommercialPolicyTargetKind;
    priority: number;
    effectId: UUID;
    effectOrder: number;
    productId: UUID | null;
    configuredAmount: ExactDecimal | null;
    configuredCurrencyCode: string | null;
    percentage: ExactDecimal | null;
    maximumAmount: ExactDecimal | null;
    maximumCurrencyCode: string | null;
  };

export type CommercialPolicyConflictCode =
  "POLICY_PRODUCT_BLOCKED" | "POLICY_FEATURE_BLOCKED" | "POLICY_QUOTA_OVERFLOW";

export type ClientCommercialPolicyConflict = {
  code: CommercialPolicyConflictCode;
  productCode: string | null;
  featureCode: string | null;
  quotaResource: string | null;
  message: string;
};

export type CommercialPolicyConflict = ClientCommercialPolicyConflict & {
  policyId: UUID;
  effectId: UUID;
};

export type ClientCommercialPolicyEvaluation = {
  evaluatedAt: Instant;
  catalogueRecurringPrice: ExactDecimal;
  fixedRecurringPrice: ExactDecimal;
  discountAmount: ExactDecimal;
  finalRecurringPrice: ExactDecimal;
  currencyCode: string;
  decisions: ClientCommercialPolicyDecision[];
  conflicts: ClientCommercialPolicyConflict[];
};

export type SubscriptionCommercialPolicyEvaluation = Omit<
  ClientCommercialPolicyEvaluation,
  "decisions" | "conflicts"
> & {
  decisions: CommercialPolicyDecisionSnapshot[];
  conflicts: CommercialPolicyConflict[];
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
  commercialPolicyDecisions: ClientCommercialPolicyDecision[];
  plans: Array<{
    code: string;
    name: string;
    description: string | null;
    basePrice: ExactDecimal;
    currencyCode: string;
    billingCycle: BillingCycle;
    current: boolean;
    selectable: boolean;
    commercialPolicyDecisions: ClientCommercialPolicyDecision[];
    features: Array<{
      featureCode: string;
      displayName: string;
      description: string | null;
      mode: string;
      quotas: Array<{
        featureCode: string;
        slot: {
          resource: string;
          type: "COUNT" | "STORAGE" | "RATE";
          unit: string;
        };
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
      features: Array<{
        featureCode: string;
        displayName: string;
        description: string | null;
        quotas: Array<{
          featureCode: string;
          slot: {
            resource: string;
            type: "COUNT" | "STORAGE" | "RATE";
            unit: string;
          };
          mode: string;
          limit: number | null;
          unlimited: boolean;
          currentUsage: number | null;
        }>;
      }>;
      prices: CatalogPrice[];
      selectable: boolean;
      commercialPolicyDecisions: ClientCommercialPolicyDecision[];
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
      selectable: boolean;
      commercialPolicyDecisions: ClientCommercialPolicyDecision[];
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
  commercialPolicyEvaluation: SubscriptionCommercialPolicyEvaluation | null;
};

/** Client review projection; it structurally cannot expose policy/activation/effect identities. */
export type ClientSubscriptionChangePreview = Omit<
  SubscriptionChangePreview,
  "commercialPolicyEvaluation"
> & {
  commercialPolicyEvaluation: ClientCommercialPolicyEvaluation | null;
};

export type QuotaPackageSelection = { packageCode: string; quantity: number };

export type SubscriptionChangeInput = {
  targetPlanCode: string;
  addOnCodes: string[];
  quotaPackages: QuotaPackageSelection[];
  timing: "IMMEDIATE" | "AT_RENEWAL";
  planPriceSelection: ProductPriceSelection;
};

export type SubscriptionChangeJobStatus =
  | "PREVIEWED"
  | "QUEUED"
  | "SCHEDULED"
  | "RUNNING"
  | "COMPLETED"
  | "COMPLETED_WITH_ERRORS"
  | "CANCELLED";

export type SubscriptionChangeJobItemStatus =
  | "READY"
  | "APPLIED"
  | "PENDING_RENEWAL"
  | "AWAITING_PAYMENT"
  | "CONFLICT"
  | "FAILED"
  | "CANCELLED";

export type SubscriptionChangeJobResult = {
  id: UUID;
  status: SubscriptionChangeJobItemStatus;
  assessment: {
    subscriptionId: UUID | null;
    expectedSubscriptionVersion: number;
    currentPlanCode: string | null;
    targetPlanCode: string;
    currentPrice: ExactDecimal | null;
    targetPrice: ExactDecimal | null;
    currencyCode: string | null;
    timing: "IMMEDIATE" | "AT_RENEWAL";
    effectiveAt: Instant | null;
    conflicts: SubscriptionChangePreview["conflicts"];
  };
  subscriptionOperationId: UUID | null;
  outcomeCode: string | null;
  attempts: number;
  lastAttemptAt: Instant | null;
  completedAt: Instant | null;
};

export type SubscriptionChangeJobSummary = {
  id: UUID;
  status: SubscriptionChangeJobStatus;
  targetCount: number;
  readyCount: number;
  appliedCount: number;
  pendingCount: number;
  awaitingPaymentCount: number;
  conflictCount: number;
  failedCount: number;
  cancelledCount: number;
  executeAt: Instant | null;
  startedAt: Instant | null;
  completedAt: Instant | null;
  requestedByUserId: UUID;
  reason: string;
  retryCount: number;
  lastRetriedByUserId: UUID | null;
  lastRetriedAt: Instant | null;
  lastRetryReason: string | null;
  version: number;
  createdAt: Instant;
};

export type SubscriptionChangeJobDetail = {
  summary: SubscriptionChangeJobSummary;
  selection: SubscriptionChangeInput;
};

export type SubscriptionChangeJobPreview = {
  jobId: UUID;
  expectedVersion: number;
  status: SubscriptionChangeJobStatus;
  targetCount: number;
  readyCount: number;
  conflictCount: number;
  executeAt: Instant | null;
  evaluatedAt: Instant;
  expiresAt: Instant;
  previewToken: string;
  sample: SubscriptionChangeJobResult[];
};

export type SubscriptionChangeJobIdentity = {
  itemId: UUID;
  accountId: UUID;
  accountName: string;
};

export type SubscriptionChangeJobPreviewInput = {
  accountIds: UUID[];
  selection: SubscriptionChangeInput;
  reason: string;
  executeAt: Instant | null;
};

export type SubscriptionChangeApplyInput = {
  selection: SubscriptionChangeInput;
  previewToken: string;
};

export type Subscription = {
  id: UUID;
  plan: {
    code: string;
    name: string;
    basePrice: ExactDecimal;
    currencyCode: string;
  };
  status: SubscriptionStatus;
  currentPrice: ExactDecimal;
  currentPriceCurrencyCode: string;
  currentPeriodStart: Instant;
  currentPeriodEnd: Instant;
  cancelAtPeriodEnd: boolean;
  pastDueAt: Instant | null;
  graceEndsAt: Instant | null;
  suspendedAt: Instant | null;
  suspensionCause: SubscriptionSuspensionCause | null;
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
  commercialPolicyEvaluation: SubscriptionCommercialPolicyEvaluation | null;
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
  pastDueAt: Instant | null;
  graceEndsAt: Instant | null;
  suspendedAt: Instant | null;
  suspensionCause: SubscriptionSuspensionCause | null;
  suspensionReason: string | null;
  availableLifecycleActions: SubscriptionLifecycleAction[];
  customOverrides: {
    schemaVersion: number;
    addOnCodes: string[];
    quotaPackages: Array<{ packageCode: string; quantity: number }>;
  };
  entitlementSnapshot: SubscriptionEntitlementSnapshot | null;
};

export type SubscriptionLifecyclePreview = {
  subscriptionId: UUID;
  expectedVersion: number;
  action: SubscriptionLifecycleAction;
  beforeStatus: SubscriptionStatus;
  afterStatus: SubscriptionStatus;
  effectiveAt: Instant;
  previousGraceEndsAt: Instant | null;
  nextGraceEndsAt: Instant | null;
  blockers: string[];
  evaluatedAt: Instant;
  expiresAt: Instant;
  previewToken: string;
};

export type SubscriptionLifecycleMutation = {
  eventId: UUID;
  subscriptionId: UUID;
  subscriptionVersion: number;
  action: SubscriptionLifecycleAction;
  status: SubscriptionStatus;
  cancelAtPeriodEnd: boolean;
  graceEndsAt: Instant | null;
  suspendedAt: Instant | null;
};

export type SubscriptionLifecycleEvent = {
  id: UUID;
  subscriptionId: UUID;
  action: SubscriptionLifecycleAction;
  beforeStatus: SubscriptionStatus;
  afterStatus: SubscriptionStatus;
  effectiveAt: Instant;
  previousGraceEndsAt: Instant | null;
  nextGraceEndsAt: Instant | null;
  actorUserId: UUID;
  actorEmail: string;
  reason: string;
  createdAt: Instant;
};

export type AccountDirectoryEntry = {
  id: UUID;
  name: string;
  slug: string;
  active: boolean;
};

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

/** Client checkout projection without provider references or manual-settlement provenance. */
export type ClientSubscriptionCheckout = {
  id: UUID;
  status: "PENDING_CONFIRMATION" | "CONFIRMED" | "FAILED" | "CANCELLED";
  amount: ExactDecimal;
  currencyCode: string;
  gatewayAttemptStatus: "SUCCESS" | "FAILED" | "PENDING" | null;
  confirmedAt: Instant | null;
};

type SubscriptionChangeOperationCore = {
  id: UUID;
  createdAt: Instant;
  updatedAt: Instant;
  timing: "IMMEDIATE" | "AT_RENEWAL";
  status:
    | "AWAITING_CONFIRMATION"
    | "PENDING"
    | "APPLIED"
    | "NEEDS_ATTENTION"
    | "CANCELLED";
  effectiveAt: Instant | null;
  sourcePlanCode: string;
  targetPlanCode: string;
};

export type SubscriptionChangeOperation = SubscriptionChangeOperationCore & {
  attentionCode: "OPERATOR_ASSISTANCE_REQUIRED" | null;
  checkout: ClientSubscriptionCheckout | null;
  commercialPolicyEvaluation: ClientCommercialPolicyEvaluation | null;
};

export type AdminSubscriptionChangeResult = SubscriptionChangeOperationCore & {
  attentionReason: string | null;
  checkout: SubscriptionCheckout | null;
  commercialPolicyEvaluation: SubscriptionCommercialPolicyEvaluation | null;
};

/** Operator-only subscription history. These fields are intentionally absent from the client DTO. */
export type AdminSubscriptionChangeOperation = AdminSubscriptionChangeResult & {
  requestOrigin: "CLIENT_SELF_SERVICE" | "PLATFORM_ADMIN" | "SYSTEM";
  requestedByUserId: UUID | null;
  requestReason: string | null;
  cancellationOrigin:
    "CLIENT_SELF_SERVICE" | "PLATFORM_ADMIN" | "SYSTEM" | null;
  cancelledByUserId: UUID | null;
  cancellationReason: string | null;
  cancelledAt: Instant | null;
};

export type SubscriptionChangeApplyResponse = {
  subscription: Subscription;
  preview: SubscriptionChangePreview;
  operation: AdminSubscriptionChangeResult;
};

export type ClientSubscriptionChangeApplyResponse = {
  subscription: Subscription;
  preview: ClientSubscriptionChangePreview;
  operation: SubscriptionChangeOperation;
};

export type AdminSubscriptionChangeApplyInput = SubscriptionChangeApplyInput & {
  reason: string;
};

export type SpecialAgreementStatus =
  | "SCHEDULED"
  | "AWAITING_SETTLEMENT"
  | "ACTIVE"
  | "COMPLETED"
  | "CANCELLED"
  | "NEEDS_ATTENTION";
export type SpecialAgreementPricingMode =
  "CATALOGUE_TOTAL" | "CUSTOM_TOTAL" | "COMPLIMENTARY";
export type SpecialAgreementSettlementMode = "PROVIDER" | "MANUAL" | "NONE";
export type SpecialAgreementEndInstruction =
  | "CONTINUE_REVIEWED_TERMS"
  | "RESTORE_PREVIOUS_TERMS"
  | "END_ACCESS"
  | "MANUAL_REVIEW";

export type SpecialAgreementDefinition = {
  selection: SubscriptionChangeInput;
  quotaBonuses: Array<{
    featureCode: string;
    resource: string;
    quantity: number;
  }>;
  startsAt: Instant;
  endsAt: Instant;
  pricingMode: SpecialAgreementPricingMode;
  customTotal: ExactDecimal | null;
  currencyCode: string;
  settlementMode: SpecialAgreementSettlementMode;
  endInstruction: SpecialAgreementEndInstruction;
  followOnPricingMode: SpecialAgreementPricingMode | null;
  followOnCustomAmount: ExactDecimal | null;
};

export type ClientSubscriptionEntitlementState = {
  planCode: string;
  billingCycle: BillingCycle;
  featureCodes: string[];
  effectiveQuotaLimits: SubscriptionChangePreview["effectiveQuotaLimits"];
  addOnCodes: string[];
  quotaPackages: QuotaPackageSelection[];
};

export type SpecialAgreementPreview = {
  subscriptionId: UUID;
  expectedSubscriptionVersion: number;
  catalogRevision: number;
  registryVersion: string;
  evaluatedAt: Instant;
  expiresAt: Instant;
  previewToken: string;
  startsAt: Instant;
  endsAt: Instant;
  completeBillingCycles: number;
  catalogueCycleAmount: ExactDecimal;
  catalogueTermAmount: ExactDecimal | null;
  agreedTermAmount: ExactDecimal;
  varianceAmount: ExactDecimal | null;
  followOnAmount: ExactDecimal | null;
  currencyCode: string;
  pricingMode: SpecialAgreementPricingMode;
  settlementMode: SpecialAgreementSettlementMode;
  endInstruction: SpecialAgreementEndInstruction;
  currentEntitlements: ClientSubscriptionEntitlementState;
  termEntitlements: ClientSubscriptionEntitlementState;
  conflicts: SubscriptionChangePreview["conflicts"];
  confirmable: boolean;
};

export type SpecialAgreementActions = {
  cancel: boolean;
  settleManually: boolean;
  retryStart: boolean;
  retryEnd: boolean;
  resolveManualReview: boolean;
};

export type SpecialAgreementSelectedAddOn = { code: string; name: string };
export type SpecialAgreementSelectedQuotaPackage = {
  code: string;
  name: string;
  resource: string;
  capacityPerUnit: number;
  quantity: number;
};

export type SpecialAgreementSummary = {
  id: UUID;
  version: number;
  accountId: UUID;
  accountName: string;
  planCode: string;
  planName: string;
  status: SpecialAgreementStatus;
  pricingMode: SpecialAgreementPricingMode;
  settlementMode: SpecialAgreementSettlementMode;
  endInstruction: SpecialAgreementEndInstruction;
  startsAt: Instant;
  endsAt: Instant;
  agreedTermAmount: ExactDecimal;
  currencyCode: string;
  attentionStage: "START" | "END" | null;
  availableActions: SpecialAgreementActions;
  createdAt: Instant;
};

export type SpecialAgreementDetail = {
  summary: SpecialAgreementSummary;
  selection: {
    schemaVersion: number;
    addOnCodes: string[];
    quotaPackages: QuotaPackageSelection[];
  };
  addOns: SpecialAgreementSelectedAddOn[];
  quotaPackages: SpecialAgreementSelectedQuotaPackage[];
  quotaBonuses: Array<{
    featureCode: string;
    resource: string;
    quantity: number;
  }>;
  catalogueCycleAmount: ExactDecimal;
  catalogueTermAmount: ExactDecimal | null;
  followOnAmount: ExactDecimal | null;
  previousRecurringAmount: ExactDecimal;
  sourceSubscriptionId: UUID;
  resultSubscriptionId: UUID | null;
  changeOperationId: UUID;
  checkout: SpecialAgreementCheckout | null;
  createdByUserId: UUID;
  reason: string;
  attentionReason: string | null;
  activatedAt: Instant | null;
  completedAt: Instant | null;
  cancelledAt: Instant | null;
  cancelledByUserId: UUID | null;
  cancellationReason: string | null;
};

export type SpecialAgreementCheckout = {
  id: UUID;
  status: "PENDING_CONFIRMATION" | "CONFIRMED" | "FAILED" | "CANCELLED";
  amount: ExactDecimal;
  currencyCode: string;
  confirmationSource:
    "MANUAL_OPERATOR" | "TRUSTED_PROVIDER" | "NO_PAYMENT_REQUIRED" | null;
  confirmedAt: Instant | null;
};

export type SpecialAgreementCreated = {
  agreement: SpecialAgreementDetail;
  checkout: SpecialAgreementCheckout | null;
};

export type ClientSpecialAgreement = {
  id: UUID;
  status: SpecialAgreementStatus;
  planCode: string;
  planName: string;
  selection: {
    schemaVersion: number;
    addOnCodes: string[];
    quotaPackages: QuotaPackageSelection[];
  };
  addOns: SpecialAgreementSelectedAddOn[];
  quotaPackages: SpecialAgreementSelectedQuotaPackage[];
  termEntitlements: ClientSubscriptionEntitlementState;
  startsAt: Instant;
  endsAt: Instant;
  agreedTermAmount: ExactDecimal;
  currencyCode: string;
  pricingMode: SpecialAgreementPricingMode;
  endInstruction: SpecialAgreementEndInstruction;
  attentionStage: "START" | "END" | null;
  activatedAt: Instant | null;
  completedAt: Instant | null;
};

export type SpecialAgreementCurrencyAnalytics = {
  currencyCode: string;
  catalogueValue: ExactDecimal;
  agreedValue: ExactDecimal;
  complimentaryValue: ExactDecimal;
  invoicedValue: ExactDecimal;
  collectedValue: ExactDecimal;
};

export type SpecialAgreementAnalytics = {
  total: number;
  scheduled: number;
  awaitingSettlement: number;
  active: number;
  completed: number;
  cancelled: number;
  needsAttention: number;
  complimentary: number;
  providerSettlement: number;
  manualSettlement: number;
  currencies: SpecialAgreementCurrencyAnalytics[];
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
