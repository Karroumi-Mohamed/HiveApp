package com.hiveapp.platform.client.plan.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.shared.audit.domain.AuditOutcome;
import com.hiveapp.shared.money.ExactDecimal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class CommercialOfferViews {
  private CommercialOfferViews() {}

  public record Summary(
      UUID id,
      String businessCode,
      String name,
      CommercialOfferStatus status,
      CommercialOfferDiscovery discovery,
      CommercialOfferAcceptance acceptance,
      Instant startsAt,
      Instant endsAt,
      int revisionNumber,
      long version,
      List<CommercialOfferAction> availableActions,
      Map<CommercialOfferAction, List<CommercialOfferBlocker>> blockedActions) {}

  public record Detail(
      UUID id,
      String businessCode,
      String name,
      String description,
      CommercialOfferStatus status,
      CommercialOfferDiscovery discovery,
      CommercialOfferAcceptance acceptance,
      boolean customerCodeConfigured,
      UUID campaignId,
      String campaignCode,
      Instant startsAt,
      Instant endsAt,
      UUID lineageId,
      int revisionNumber,
      Long globalLimit,
      Long perAccountLimit,
      CommercialOfferSelection selection,
      CommercialOfferEffectSnapshot effects,
      Instant publishedAt,
      Instant retiredAt,
      Instant archivedAt,
      long version,
      List<CommercialOfferAction> availableActions,
      Map<CommercialOfferAction, List<CommercialOfferBlocker>> blockedActions) {}

  /** Definition-free state used to compose independently authorized Offer operations. */
  public record OperationState(
      UUID id,
      String businessCode,
      String name,
      CommercialOfferStatus status,
      int revisionNumber,
      long version,
      List<CommercialOfferAction> availableActions,
      Map<CommercialOfferAction, List<CommercialOfferBlocker>> blockedActions) {}

  /** Draft definition returned only to callers with explicit edit-definition authority. */
  public record EditableDefinition(
      UUID id,
      String name,
      String description,
      Instant startsAt,
      Instant endsAt,
      CommercialOfferDiscovery discovery,
      CommercialOfferAcceptance acceptance,
      boolean customerCodeConfigured,
      Long globalLimit,
      Long perAccountLimit,
      CommercialOfferSelection selection,
      CommercialOfferEffectSnapshot effects,
      long version) {}

  /** Minimal acknowledgement for Offer mutations; definition reads remain separately authorized. */
  public record Mutation(UUID offerId, CommercialOfferStatus status, long version) {
    @JsonIgnore
    public UUID id() {
      return offerId;
    }
  }

  public record Revision(
      UUID id, int revisionNumber, CommercialOfferStatus status, Instant createdAt) {}

  public record Comparison(
      UUID leftId,
      UUID rightId,
      boolean sameLineage,
      Set<String> changedFields,
      ComparisonDefinition left,
      ComparisonDefinition right) {
    public Comparison {
      changedFields = Set.copyOf(changedFields);
    }
  }

  /** Complete, usable revision values for the side-by-side comparison screen. */
  public record ComparisonDefinition(
      UUID id,
      String businessCode,
      String name,
      String description,
      CommercialOfferStatus status,
      UUID campaignId,
      String campaignCode,
      CommercialOfferDiscovery discovery,
      CommercialOfferAcceptance acceptance,
      boolean customerCodeConfigured,
      Instant startsAt,
      Instant endsAt,
      int revisionNumber,
      Long globalLimit,
      Long perAccountLimit,
      CommercialOfferSelection selection,
      CommercialOfferEffectSnapshot effects,
      long version) {}

  public record PublicationPreview(
      UUID offerId,
      long expectedVersion,
      List<String> blockers,
      Instant evaluatedAt,
      Instant expiresAt,
      String previewToken) {}

  public record Owner(
      UUID offerId,
      CommercialOfferStatus offerStatus,
      long offerVersion,
      UUID adminUserId,
      UUID userId,
      String email,
      String username,
      String displayName,
      boolean active) {}

  /** Minimal active-operator identity used only by separately authorized owner choosers. */
  public record OwnerChoice(UUID adminUserId, String email, String username, String displayName) {}

  public record CampaignChoice(
      UUID id,
      String code,
      String name,
      CommercialCampaignStatus status,
      CommercialCampaignAudienceMode audienceMode,
      int revisionNumber,
      Instant startsAt,
      Instant endsAt) {}

  public record ProductCompatibility(
      ProductSalesVisibility salesVisibility,
      String extensionPolicy,
      Set<String> allowedPlanCodes,
      Set<String> blockedPlanCodes,
      Set<String> dependencyCodes,
      Set<String> exclusionCodes,
      Set<String> allowedAddOnCodes,
      String featureCode,
      String quotaResource,
      Long capacityPerUnit,
      Boolean repeatable,
      Integer maximumQuantity) {
    public ProductCompatibility {
      allowedPlanCodes = copy(allowedPlanCodes);
      blockedPlanCodes = copy(blockedPlanCodes);
      dependencyCodes = copy(dependencyCodes);
      exclusionCodes = copy(exclusionCodes);
      allowedAddOnCodes = copy(allowedAddOnCodes);
    }

    private static Set<String> copy(Set<String> values) {
      return values == null ? Set.of() : Set.copyOf(values);
    }
  }

  public record PricedChoice(
      UUID productId,
      String productCode,
      String productName,
      int productRevisionNumber,
      UUID priceId,
      int priceRevisionNumber,
      @ExactDecimal BigDecimal amount,
      String currencyCode,
      BillingCycle billingCycle,
      Instant effectiveFrom,
      Instant effectiveUntil,
      String productState,
      String priceState,
      ProductCompatibility compatibility) {}

  public record QuotaResourceChoice(
      String featureCode, String featureName, String resource, String unit) {}

  /** Client-safe commercial product: readable terms without internal catalogue identifiers. */
  public record ClientProduct(
      String code,
      String name,
      int revisionNumber,
      @ExactDecimal BigDecimal amount,
      String currencyCode,
      BillingCycle billingCycle,
      CommercialOfferSelection.PricingMode pricingMode,
      Integer quantity) {}

  public record ClientSelection(
      ClientProduct plan,
      List<ClientProduct> addOns,
      List<ClientProduct> quotaPackages,
      SubscriptionChangeTiming timing) {
    public ClientSelection {
      addOns = List.copyOf(addOns);
      quotaPackages = List.copyOf(quotaPackages);
    }
  }

  public record ClientOffer(
      UUID id,
      String name,
      String description,
      Instant endsAt,
      String targetPlanCode,
      CommercialOfferDiscountType discountType,
      @ExactDecimal BigDecimal discountAmount,
      BigDecimal percentage,
      @ExactDecimal BigDecimal percentageCap,
      ClientSelection selection,
      CommercialOfferEffectSnapshot effects) {}

  public record CodeResolution(ClientOffer offer, Instant expiresAt, String discoveryToken) {}

  public record EligibilityPreview(
      UUID offerId,
      UUID subscriptionId,
      long expectedSubscriptionVersion,
      @ExactDecimal BigDecimal catalogueSubtotal,
      @ExactDecimal BigDecimal policyPrice,
      @ExactDecimal BigDecimal offerPrice,
      @ExactDecimal BigDecimal finalPrice,
      String currencyCode,
      @ExactDecimal BigDecimal policyFixedBase,
      @ExactDecimal BigDecimal freeProductReduction,
      String discountWinner,
      String winnerReason,
      Instant evaluatedAt,
      Instant expiresAt,
      String previewToken) {}

  public record AcceptedTerms(
      @ExactDecimal BigDecimal catalogueSubtotal,
      @ExactDecimal BigDecimal policyPrice,
      @ExactDecimal BigDecimal offerPrice,
      @ExactDecimal BigDecimal finalPrice,
      String currencyCode,
      String discountWinner,
      String winnerReason,
      List<CommercialOfferEffectSnapshot.QuotaBonus> quotaBonuses) {
    public AcceptedTerms {
      quotaBonuses = quotaBonuses == null ? List.of() : List.copyOf(quotaBonuses);
    }
  }

  public record Acceptance(
      UUID redemptionId,
      UUID subscriptionOperationId,
      CommercialOfferRedemptionStatus status,
      boolean replayed,
      AcceptedTerms acceptedTerms) {}

  public record Redemption(
      UUID id,
      UUID offerId,
      String offerName,
      UUID offerLineageId,
      int offerRevisionNumber,
      UUID campaignId,
      CommercialOfferSelection selection,
      CommercialOfferRedemptionStatus status,
      CommercialOfferSurface surface,
      UUID subscriptionOperationId,
      Instant reservedAt,
      Instant appliedAt,
      Instant releasedAt,
      String terminalReason,
      AcceptedTerms acceptedTerms) {}

  public record ClientRedemption(
      UUID id,
      UUID offerId,
      String offerName,
      int offerRevisionNumber,
      ClientSelection selection,
      CommercialOfferRedemptionStatus status,
      UUID subscriptionOperationId,
      Instant reservedAt,
      Instant appliedAt,
      Instant releasedAt,
      AcceptedTerms acceptedTerms) {}

  public record RedemptionIdentity(
      UUID redemptionId, UUID accountId, String accountName, UUID actorUserId) {}

  public record Stats(
      long reserved,
      long applied,
      long cancelled,
      long failed,
      Long globalLimit,
      Long remainingGlobalCapacity) {}

  public record History(
      UUID id,
      Instant occurredAt,
      String action,
      AuditOutcome outcome,
      UUID actorUserId,
      String actorEmail,
      String reason) {}
}
