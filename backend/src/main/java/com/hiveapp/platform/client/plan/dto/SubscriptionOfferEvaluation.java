package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.shared.money.ExactDecimal;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SubscriptionOfferEvaluation(
    UUID campaignId,
    UUID offerLineageId,
    UUID offerRevisionId,
    int offerRevisionNumber,
    @ExactDecimal BigDecimal catalogueSubtotal,
    @ExactDecimal BigDecimal policyPrice,
    @ExactDecimal BigDecimal offerPrice,
    @ExactDecimal BigDecimal finalPrice,
    String currencyCode,
    String discountWinner,
    String winnerReason,
    List<CommercialOfferEffectSnapshot.QuotaBonus> quotaBonuses) {
  public SubscriptionOfferEvaluation {
    quotaBonuses = quotaBonuses == null ? List.of() : List.copyOf(quotaBonuses);
  }
}
