package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferRedemptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOfferRedemption;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.repository.CommercialOfferCapacityRepository;
import com.hiveapp.shared.exception.OfferRedemptionBlockedException;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Applies the durable redemption/capacity state machine. Callers retain transaction boundaries and
 * must provide a locked redemption.
 */
@Component
@RequiredArgsConstructor
public class CommercialOfferRedemptionTransitionService {
  private final CommercialOfferCapacityRepository capacities;
  private final Clock clock;

  public void applyOperation(
      CommercialOfferRedemption redemption,
      SubscriptionChangeOperation operation) {
    if (!redemption.getId().equals(operation.getOfferRedemptionId())
        || !redemption.getAccount().getId().equals(operation.getAccount().getId())
        || !redemption.hasApplicationClaim(operation.getOfferApplicationClaimId())) {
      throw new OfferRedemptionBlockedException();
    }
    if (redemption.getStatus() != CommercialOfferRedemptionStatus.RESERVED) return;
    redemption.linkOperation(operation.getId(), operation.getOfferApplicationClaimId());
    switch (operation.getStatus()) {
      case APPLIED -> {
        capacity(redemption).apply();
        redemption.apply(clock.instant());
      }
      case CANCELLED -> {
        capacity(redemption).release();
        redemption.cancel("SUBSCRIPTION_OPERATION_CANCELLED", clock.instant());
      }
      case NEEDS_ATTENTION -> {
        capacity(redemption).release();
        redemption.fail("SUBSCRIPTION_OPERATION_NEEDS_ATTENTION", clock.instant());
      }
      default -> {
        // Pending and scheduled operations retain their reservation for later reconciliation.
      }
    }
  }

  public void failReservation(CommercialOfferRedemption redemption, String reason) {
    if (redemption.getStatus() != CommercialOfferRedemptionStatus.RESERVED) return;
    capacity(redemption).release();
    redemption.fail(reason, clock.instant());
  }

  private com.hiveapp.platform.client.plan.domain.entity.CommercialOfferCapacity capacity(
      CommercialOfferRedemption redemption) {
    return capacities
        .lockByLineage(redemption.getOfferLineageId())
        .orElseThrow(OfferRedemptionBlockedException::new);
  }
}
