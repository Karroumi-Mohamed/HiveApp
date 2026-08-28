package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOffer;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.shared.exception.OfferNotAvailableException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Centralizes eligibility while leaving the definitive capacity mutation behind a database lock.
 */
@Component
@RequiredArgsConstructor
class CommercialOfferEligibilityService {
  private static final List<CommercialOfferRedemptionStatus> USED =
      List.of(CommercialOfferRedemptionStatus.RESERVED, CommercialOfferRedemptionStatus.APPLIED);

  private final CommercialOfferRepository offers;
  private final AccountRepository accounts;
  private final CommercialCampaignAudienceSnapshotRepository audiences;
  private final SubscriptionRepository subscriptions;
  private final CommercialOfferCapacityRepository capacities;
  private final CommercialOfferRedemptionRepository redemptions;
  private final Clock clock;

  CommercialOffer requireAvailable(UUID id, UUID accountId, boolean operator, boolean discovered) {
    CommercialOffer offer = offers.findDetailById(id).orElseThrow(OfferNotAvailableException::new);
    if (!eligible(offer, accountId, operator, discovered)
        || !hasAvailableCapacity(offer, accountId)) {
      throw new OfferNotAvailableException();
    }
    return offer;
  }

  void requireActiveAccount(UUID accountId) {
    if (!accounts.existsActiveById(accountId)) throw new OfferNotAvailableException();
  }

  boolean eligible(CommercialOffer offer, UUID accountId, boolean operator, boolean discovered) {
    Instant now = clock.instant();
    if (!accounts.existsActiveById(accountId)
        || offer.getStatus() != CommercialOfferStatus.PUBLISHED
        || now.isBefore(offer.getStartsAt())
        || !now.isBefore(offer.getEndsAt())
        || offer.getCampaign().getStatus() != CommercialCampaignStatus.ACTIVE) {
      return false;
    }
    if (!operator && offer.getAcceptance() == CommercialOfferAcceptance.OPERATOR_ONLY) return false;
    if (!discovered && offer.getDiscovery() != CommercialOfferDiscovery.CATALOG) return false;
    return audiences.countEligibleAccount(offer.getCampaign().getId(), accountId) > 0
        && subscriptions
            .findTopByAccountIdAndStatusInOrderByCreatedAtDesc(
                accountId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING))
            .isPresent();
  }

  boolean hasAvailableCapacity(CommercialOffer offer, UUID accountId) {
    var capacity = capacities.findByLineageId(offer.getLineageId()).orElse(null);
    if (capacity == null) return false;
    if (offer.getGlobalLimit() != null
        && capacity.getReservedCount() + capacity.getAppliedCount() >= offer.getGlobalLimit()) {
      return false;
    }
    return offer.getPerAccountLimit() == null
        || redemptions.countByOfferLineageIdAndAccount_IdAndStatusIn(
                offer.getLineageId(), accountId, USED)
            < offer.getPerAccountLimit();
  }
}
