import type {
  AccountDirectoryEntry,
  AdminSubscriptionChangeOperation,
  BillingCycle,
  ClientCommercialPolicyEvaluation,
  ClientSubscriptionCheckout,
  ExactDecimal,
  PageResponse,
  SubscriptionChangeOperation,
  SubscriptionCommercialPolicyEvaluation,
  UUID,
} from "@/api/contracts";

export type OfferStatus = "DRAFT" | "PUBLISHED" | "RETIRED" | "ARCHIVED";
export type OfferDiscovery = "CATALOG" | "CODE_ONLY";
export type OfferAcceptance = "CLIENT_OR_OPERATOR" | "OPERATOR_ONLY";
export type OfferDiscountType = "NONE" | "FIXED" | "PERCENTAGE_WITH_CAP";
export type OfferRedemptionStatus =
  "RESERVED" | "APPLIED" | "CANCELLED" | "FAILED";
export type OfferSurface = "CLIENT" | "OPERATOR";
export type OfferPricingMode = "PAID" | "FREE";
export type OfferProductOwnerType = "PLAN" | "ADD_ON" | "QUOTA_PACKAGE";
export type OfferProductState = "DRAFT" | "ACTIVE" | "INACTIVE" | "ARCHIVED";
export type OfferPriceState = "DRAFT" | "ACTIVE" | "INACTIVE" | "ARCHIVED";
export type OfferPublicationMode = "PUBLISH" | "RESTORE";
export type OfferAcceptanceProgress =
  "IN_PROGRESS" | "OPERATION_AVAILABLE" | "TERMINAL_WITHOUT_OPERATION";
export type OfferAcceptanceNextAction =
  "RETRY_LATER" | "TRACK_OPERATION" | "NONE";

export type OfferAction =
  | "READ_DEFINITION"
  | "UPDATE"
  | "DUPLICATE"
  | "REVISE"
  | "REVISIONS"
  | "COMPARE"
  | "HISTORY"
  | "PREVIEW_PUBLICATION"
  | "PUBLISH"
  | "RETIRE"
  | "RESTORE"
  | "ARCHIVE"
  | "DELETE_DRAFT"
  | "OWNER"
  | "REASSIGN_OWNER"
  | "READ_STATS"
  | "READ_REDEMPTIONS"
  | "READ_REDEMPTION_DETAIL"
  | "READ_REDEMPTION_IDENTITIES"
  | "PREVIEW_FOR_ACCOUNT"
  | "APPLY_FOR_ACCOUNT";

export type OfferBlocker =
  | "NOT_DRAFT"
  | "NOT_PUBLISHED"
  | "NOT_RETIRED"
  | "NOT_LATEST_REVISION"
  | "DRAFT_SUCCESSOR_EXISTS"
  | "PUBLISHED_SUCCESSOR_EXISTS"
  | "HAS_DERIVED_OFFERS"
  | "CAMPAIGN_NOT_LIVE"
  | "WINDOW_ENDED"
  | "WINDOW_OUTSIDE_CAMPAIGN"
  | "CODE_REQUIRED"
  | "INVALID_SELECTION"
  | "INVALID_LIFECYCLE_STATE"
  | "CAMPAIGN_NOT_ACTIVE"
  | "WINDOW_NOT_STARTED";

export type OfferDefinitionIssueCode =
  | "CAMPAIGN_UNAVAILABLE"
  | "INVALID_WINDOW"
  | "WINDOW_OUTSIDE_CAMPAIGN"
  | "WINDOW_ENDED"
  | "CUSTOMER_CODE_REQUIRED"
  | "CUSTOMER_CODE_INVALID"
  | "CUSTOMER_CODE_UNAVAILABLE"
  | "PRICE_OWNER_MISMATCH"
  | "PRICE_INACTIVE_OR_WINDOW_UNCOVERED"
  | "CURRENCY_MISMATCH"
  | "BILLING_CYCLE_MISMATCH"
  | "DUPLICATE_PRODUCT_REVISION"
  | "DIRECT_ONLY_REQUIRES_TARGETED_CAMPAIGN"
  | "SELECTION_INCOMPATIBLE"
  | "QUOTA_BONUS_NOT_IN_SELECTION"
  | "QUOTA_BONUS_OVERFLOW"
  | "INVALID_DISCOUNT"
  | "LINEAGE_TERMS_IMMUTABLE";

export type OfferEligibilityBlocker =
  | "ACCOUNT_INACTIVE"
  | "OFFER_NOT_PUBLISHED"
  | "OFFER_WINDOW_NOT_STARTED"
  | "OFFER_WINDOW_ENDED"
  | "CAMPAIGN_NOT_ACTIVE"
  | "ACCOUNT_OUTSIDE_AUDIENCE"
  | "NO_ACTIVE_SUBSCRIPTION"
  | "GLOBAL_CAPACITY_EXHAUSTED"
  | "ACCOUNT_CAPACITY_EXHAUSTED"
  | "OUTSTANDING_SUBSCRIPTION_OPERATION"
  | "SELECTION_UNAVAILABLE"
  | "POLICY_CONFLICT"
  | "IMMEDIATE_CHANGE_CONFLICT"
  | "PAID_CHECKOUT_UNAVAILABLE"
  | "NO_CHANGE";

export type OfferCampaignChoice = {
  id: UUID;
  code: string;
  name: string;
  status: "DRAFT" | "SCHEDULED" | "ACTIVE" | "PAUSED" | "ENDED" | "ARCHIVED";
  audienceMode: "PUBLIC" | "EXPLICIT_ACCOUNTS" | "SEGMENT";
  revisionNumber: number;
  startsAt: string;
  endsAt: string;
};

export type OfferSelection = {
  planId: UUID;
  planPriceId: UUID;
  addOns: Array<{
    addOnId: UUID;
    priceId: UUID;
    pricingMode: OfferPricingMode;
  }>;
  quotaPackages: Array<{
    quotaPackageId: UUID;
    priceId: UUID;
    quantity: number;
    pricingMode: OfferPricingMode;
  }>;
  timing: "IMMEDIATE" | "AT_RENEWAL";
};

export type OfferEffects = {
  discountType: OfferDiscountType;
  discountAmount: ExactDecimal | null;
  percentage: ExactDecimal | null;
  percentageCap: ExactDecimal | null;
  finiteQuotaBonuses: Array<{
    featureCode: string;
    resource: string;
    quantity: number;
  }>;
};

export type OfferClientProduct = {
  code: string;
  name: string;
  revisionNumber: number;
  amount: ExactDecimal;
  currencyCode: string;
  billingCycle: BillingCycle;
  pricingMode: OfferPricingMode;
  quantity: number | null;
};

export type OfferClientSelection = {
  plan: OfferClientProduct;
  addOns: OfferClientProduct[];
  quotaPackages: OfferClientProduct[];
  timing: "IMMEDIATE" | "AT_RENEWAL";
};

export type OfferSummary = {
  id: UUID;
  businessCode: string;
  name: string;
  status: OfferStatus;
  discovery: OfferDiscovery;
  acceptance: OfferAcceptance;
  campaign: OfferCampaignChoice;
  startsAt: string;
  endsAt: string;
  revisionNumber: number;
  version: number;
  availableActions: OfferAction[];
  blockedActions: Partial<Record<OfferAction, OfferBlocker[]>>;
};

export type OfferDetail = OfferSummary & {
  description: string | null;
  customerCodeConfigured: boolean;
  campaignId: UUID;
  campaignCode: string;
  lineageId: UUID;
  globalLimit: number | null;
  perAccountLimit: number | null;
  selection: OfferSelection;
  resolvedSelection: OfferClientSelection;
  effects: OfferEffects;
  publishedAt: string | null;
  retiredAt: string | null;
  archivedAt: string | null;
};

export type OfferOperationState = Pick<
  OfferSummary,
  | "id"
  | "businessCode"
  | "name"
  | "status"
  | "revisionNumber"
  | "version"
  | "availableActions"
  | "blockedActions"
>;

export type OfferEditableDefinition = {
  id: UUID;
  businessCode: string;
  status: OfferStatus;
  name: string;
  description: string | null;
  startsAt: string;
  endsAt: string;
  discovery: OfferDiscovery;
  acceptance: OfferAcceptance;
  customerCodeConfigured: boolean;
  campaign: OfferCampaignChoice;
  lineageId: UUID;
  revisionNumber: number;
  lineageTermsEditable: boolean;
  globalLimit: number | null;
  perAccountLimit: number | null;
  selection: OfferSelection;
  resolvedSelection: OfferClientSelection;
  effects: OfferEffects;
  lineageVersion: number;
  version: number;
};

export type OfferMutation = {
  offerId: UUID;
  status: OfferStatus;
  version: number;
};
export type OfferDefinitionIssue = {
  code: OfferDefinitionIssueCode;
  fieldPath: string;
  message: string;
};
export type OfferDefinitionPreview = {
  offerId: UUID | null;
  expectedVersion: number | null;
  valid: boolean;
  lineageTermsEditable: boolean;
  campaign: OfferCampaignChoice | null;
  resolvedSelection: OfferClientSelection | null;
  issues: OfferDefinitionIssue[];
};
export type OfferPublicationPreview = {
  offerId: UUID;
  expectedVersion: number;
  mode: OfferPublicationMode;
  ready: boolean;
  blockers: OfferBlocker[];
  definitionIssues: OfferDefinitionIssue[];
  evaluatedAt: string;
  expiresAt: string;
  previewToken: string;
};
export type OfferRevision = {
  id: UUID;
  revisionNumber: number;
  status: OfferStatus;
  sourceOfferId: UUID | null;
  version: number;
  createdAt: string;
};
export type OfferComparisonDefinition = Omit<
  OfferDetail,
  | "availableActions"
  | "blockedActions"
  | "publishedAt"
  | "retiredAt"
  | "archivedAt"
  | "lineageId"
  | "customerCodeConfigured"
>;
export type OfferComparison = {
  leftId: UUID;
  rightId: UUID;
  sameLineage: boolean;
  directSuccessor: boolean;
  changedFields: string[];
  left: OfferComparisonDefinition;
  right: OfferComparisonDefinition;
};
export type OfferOwner = {
  offerId: UUID;
  offerStatus: OfferStatus;
  offerVersion: number;
  lineageVersion: number;
  adminUserId: UUID;
  userId: UUID;
  email: string;
  username: string;
  displayName: string | null;
  active: boolean;
};
export type OfferOwnerMutation = {
  offerId: UUID;
  adminUserId: UUID;
  lineageVersion: number;
};
export type OfferOwnerChoice = {
  adminUserId: UUID;
  email: string;
  username: string;
  displayName: string | null;
};
export type OfferStats = {
  reserved: number;
  applied: number;
  cancelled: number;
  failed: number;
  globalLimit: number | null;
  remainingGlobalCapacity: number | null;
};
export type OfferHistoryEntry = {
  id: UUID;
  occurredAt: string;
  action: string;
  outcome: "SUCCEEDED" | "FAILED";
  actorUserId: UUID | null;
  actorEmail: string | null;
  reason: string | null;
};

export type OfferProductCompatibility = {
  salesVisibility: "PUBLIC" | "DIRECT_ONLY";
  extensionPolicy: "CLOSED" | "ALLOW_LIST" | "OPEN_COMPATIBLE";
  allowedPlanCodes: string[];
  blockedPlanCodes: string[];
  dependencyCodes: string[];
  exclusionCodes: string[];
  allowedAddOnCodes: string[];
  featureCode: string | null;
  quotaResource: string | null;
  capacityPerUnit: number | null;
  repeatable: boolean | null;
  maximumQuantity: number | null;
};
export type OfferPricedChoice = {
  ownerType: OfferProductOwnerType;
  productId: UUID;
  productCode: string;
  productName: string;
  productRevisionNumber: number;
  priceId: UUID;
  priceRevisionNumber: number;
  amount: ExactDecimal;
  currencyCode: string;
  billingCycle: BillingCycle;
  effectiveFrom: string;
  effectiveUntil: string | null;
  productState: OfferProductState;
  priceState: OfferPriceState;
  compatibility: OfferProductCompatibility;
};
export type OfferQuotaResourceChoice = {
  featureCode: string;
  featureName: string;
  resource: string;
  unit: string;
};

export type OfferClient = {
  id: UUID;
  name: string;
  description: string | null;
  endsAt: string;
  targetPlanCode: string;
  discountType: OfferDiscountType;
  discountAmount: ExactDecimal | null;
  percentage: ExactDecimal | null;
  percentageCap: ExactDecimal | null;
  selection: OfferClientSelection;
  effects: OfferEffects;
};
export type OfferCodeResolution = {
  offer: OfferClient;
  expiresAt: string;
  discoveryToken: string;
};
export type OfferEntitlementState = {
  planCode: string;
  featureCodes: string[];
  addOnCodes: string[];
  quotaLimits: Array<{
    featureCode: string;
    resource: string;
    mode: string;
    includedLimit: number | null;
    purchasedCapacity: number;
    effectiveLimit: number | null;
  }>;
};
export type OfferChangeReview = {
  currentPrice: ExactDecimal;
  timing: "IMMEDIATE" | "AT_RENEWAL";
  estimatedEffectiveAt: string;
  estimatedEffectiveUntil: string | null;
  immediateAllowed: boolean;
  currentEntitlements: OfferEntitlementState;
  targetEntitlements: OfferEntitlementState;
  conflicts: Array<{
    code: string;
    featureCode: string | null;
    resource: string | null;
    currentUsage: number | null;
    requestedLimit: number | null;
    message: string;
  }>;
  policyEvaluation: ClientCommercialPolicyEvaluation | null;
  checkoutRequired: boolean;
  checkoutAmount: ExactDecimal;
  expectedOperationStatus:
    | "AWAITING_CONFIRMATION"
    | "PENDING"
    | "APPLIED"
    | "NEEDS_ATTENTION"
    | "CANCELLED";
};
export type OfferEligibilityPreview = {
  offerId: UUID;
  subscriptionId: UUID;
  expectedSubscriptionVersion: number;
  selection: OfferClientSelection;
  change: OfferChangeReview;
  catalogueSubtotal: ExactDecimal;
  policyPrice: ExactDecimal;
  offerPrice: ExactDecimal;
  finalPrice: ExactDecimal;
  currencyCode: string;
  policyFixedBase: ExactDecimal;
  freeProductReduction: ExactDecimal;
  discountWinner: "POLICY" | "OFFER" | "TIE" | "NONE";
  discountDecisionCode: string;
  winnerReason: string;
  evaluatedAt: string;
  expiresAt: string;
  previewToken: string;
};
export type OfferAccountAssessment = {
  offerId: UUID;
  accountId: UUID;
  eligible: boolean;
  blockers: OfferEligibilityBlocker[];
  evaluatedAt: string;
  preview: OfferEligibilityPreview | null;
};
export type OfferAcceptedTerms = {
  catalogueSubtotal: ExactDecimal;
  policyPrice: ExactDecimal;
  offerPrice: ExactDecimal;
  finalPrice: ExactDecimal;
  currencyCode: string;
  discountWinner: "POLICY" | "OFFER" | "TIE" | "NONE";
  discountDecisionCode: string;
  winnerReason: string;
  quotaBonuses: OfferEffects["finiteQuotaBonuses"];
};
export type OfferClientAcceptance = {
  redemptionId: UUID;
  subscriptionOperationId: UUID | null;
  status: OfferRedemptionStatus;
  replayed: boolean;
  progress: OfferAcceptanceProgress;
  nextAction: OfferAcceptanceNextAction;
  retryAfter: string | null;
  operation: SubscriptionChangeOperation | null;
  acceptedTerms: OfferAcceptedTerms;
};
export type OfferAdminAcceptance = Omit<OfferClientAcceptance, "operation"> & {
  operation: AdminSubscriptionChangeOperation | null;
};
export type OfferRedemption = {
  id: UUID;
  offerId: UUID;
  offerName: string;
  offerLineageId: UUID;
  offerRevisionNumber: number;
  campaignId: UUID;
  selection: OfferSelection;
  resolvedSelection: OfferClientSelection;
  status: OfferRedemptionStatus;
  surface: OfferSurface;
  subscriptionOperationId: UUID | null;
  operation: SubscriptionChangeOperation | null;
  reservedAt: string;
  appliedAt: string | null;
  releasedAt: string | null;
  terminalReason: string | null;
  acceptedTerms: OfferAcceptedTerms;
};
export type OfferClientRedemption = Omit<
  OfferRedemption,
  | "offerLineageId"
  | "campaignId"
  | "selection"
  | "resolvedSelection"
  | "surface"
  | "terminalReason"
> & {
  selection: OfferClientSelection;
};
export type OfferRedemptionIdentity = {
  redemptionId: UUID;
  accountId: UUID;
  accountName: string;
  actorUserId: UUID;
};

export type OfferCreateInput = {
  name: string;
  description: string | null;
  campaignId: UUID;
  startsAt: string;
  endsAt: string;
  discovery: OfferDiscovery;
  acceptance: OfferAcceptance;
  customerCode: string | null;
  globalLimit: number | null;
  perAccountLimit: number | null;
  selection: OfferSelection;
  effects: OfferEffects;
};
export type OfferUpdateInput = {
  version: number;
  name: string;
  description: string | null;
  startsAt: string;
  endsAt: string;
  selection: OfferSelection;
  effects: OfferEffects;
  lineageTerms: {
    expectedLineageVersion: number;
    discovery: OfferDiscovery;
    acceptance: OfferAcceptance;
    globalLimit: number | null;
    perAccountLimit: number | null;
    customerCodeChange: {
      mode: "KEEP" | "REPLACE" | "REMOVE";
      value: string | null;
    };
  } | null;
};

export type OfferPage<T> = PageResponse<T>;
export type OfferAccountChoice = AccountDirectoryEntry;

// Kept structural so the Offer module does not expose operator-only policy provenance to clients.
export type OfferOperatorPolicyEvaluation =
  SubscriptionCommercialPolicyEvaluation;
export type OfferClientCheckout = ClientSubscriptionCheckout;
