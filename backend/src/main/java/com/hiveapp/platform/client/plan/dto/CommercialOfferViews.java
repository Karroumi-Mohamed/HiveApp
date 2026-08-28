package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.shared.money.ExactDecimal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class CommercialOfferViews {
 private CommercialOfferViews(){}
 public record Summary(UUID id,String businessCode,String name,CommercialOfferStatus status,
  CommercialOfferDiscovery discovery,CommercialOfferAcceptance acceptance,Instant startsAt,Instant endsAt,
  int revisionNumber,long version,Set<String> availableActions,Map<String,String> blockedActions){}
 public record Detail(UUID id,String businessCode,String name,String description,CommercialOfferStatus status,
  CommercialOfferDiscovery discovery,CommercialOfferAcceptance acceptance,boolean customerCodeConfigured,
  UUID campaignId,String campaignCode,Instant startsAt,Instant endsAt,
  UUID lineageId,int revisionNumber,Long globalLimit,Long perAccountLimit,
  CommercialOfferSelection selection,CommercialOfferEffectSnapshot effects,Instant publishedAt,
  Instant retiredAt,Instant archivedAt,long version,Set<String> availableActions,Map<String,String> blockedActions){}
 public record Revision(UUID id,int revisionNumber,CommercialOfferStatus status,Instant createdAt){}
 public record Comparison(UUID leftId,UUID rightId,boolean sameLineage,Set<String> changedFields){}
 public record PublicationPreview(UUID offerId,long expectedVersion,List<String> blockers,
  Instant evaluatedAt,Instant expiresAt,String previewToken){}
 public record Owner(UUID id,String email,boolean active){}
 public record Choice(UUID id,String code,String name,String state){}
 public record PricedChoice(UUID productId,String productCode,String productName,UUID priceId,
  @ExactDecimal BigDecimal amount,String currencyCode,BillingCycle billingCycle,String productState,
  String priceState){}
 public record QuotaResourceChoice(String featureCode,String featureName,String resource,String unit){}
 public record ClientOffer(UUID id,String name,String description,Instant endsAt,String targetPlanCode,
  CommercialOfferDiscountType discountType,@ExactDecimal BigDecimal discountAmount,BigDecimal percentage,
  @ExactDecimal BigDecimal percentageCap,CommercialOfferSelection selection,
  CommercialOfferEffectSnapshot effects){}
 public record CodeResolution(ClientOffer offer,Instant expiresAt,String discoveryToken){}
 public record EligibilityPreview(UUID offerId,UUID subscriptionId,long expectedSubscriptionVersion,
  @ExactDecimal BigDecimal catalogueSubtotal,@ExactDecimal BigDecimal policyPrice,
  @ExactDecimal BigDecimal offerPrice,@ExactDecimal BigDecimal finalPrice,String currencyCode,
  @ExactDecimal BigDecimal policyFixedBase,@ExactDecimal BigDecimal freeProductReduction,
  String discountWinner,String winnerReason,Instant evaluatedAt,Instant expiresAt,String previewToken){}
 public record AcceptedTerms(@ExactDecimal BigDecimal catalogueSubtotal,
  @ExactDecimal BigDecimal policyPrice,@ExactDecimal BigDecimal offerPrice,
  @ExactDecimal BigDecimal finalPrice,String currencyCode,String discountWinner,String winnerReason){}
 public record Acceptance(UUID redemptionId,UUID subscriptionOperationId,
  CommercialOfferRedemptionStatus status,boolean replayed,AcceptedTerms acceptedTerms){}
 public record Redemption(UUID id,UUID offerId,String offerName,CommercialOfferRedemptionStatus status,
  CommercialOfferSurface surface,UUID subscriptionOperationId,Instant reservedAt,Instant appliedAt,
  Instant releasedAt,String terminalReason){}
 public record ClientRedemption(UUID id,UUID offerId,String offerName,
  CommercialOfferRedemptionStatus status,UUID subscriptionOperationId,Instant reservedAt,
  Instant appliedAt,Instant releasedAt){}
 public record RedemptionIdentity(UUID redemptionId,UUID accountId,String accountName,UUID actorUserId){}
 public record Stats(long reserved,long applied,long cancelled,long failed){}
 public record History(Instant occurredAt,String action,String outcome,UUID actorUserId,String reason){}
}
