package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOffer;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.shared.exception.OfferNotAvailableException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
    if (!blockers(offer, accountId, operator, discovered).isEmpty()) {
      throw new OfferNotAvailableException();
    }
    return offer;
  }

  void requireActiveAccount(UUID accountId) {
    if (!accounts.existsActiveById(accountId)) throw new OfferNotAvailableException();
  }

  boolean eligible(CommercialOffer offer, UUID accountId, boolean operator, boolean discovered) {
    return staticBlockers(offer, accountId, operator, discovered).isEmpty();
  }

  List<CommercialOfferEligibilityBlocker> blockers(
      CommercialOffer offer, UUID accountId, boolean operator, boolean discovered) {
    List<CommercialOfferEligibilityBlocker> result =
        new ArrayList<>(staticBlockers(offer, accountId, operator, discovered));
    if (result.isEmpty()) result.addAll(capacityBlockers(offer, accountId));
    return List.copyOf(new LinkedHashSet<>(result));
  }

  private List<CommercialOfferEligibilityBlocker> staticBlockers(
      CommercialOffer offer, UUID accountId, boolean operator, boolean discovered) {
    List<CommercialOfferEligibilityBlocker> result = new ArrayList<>();
    Instant now = clock.instant();
    if (!accounts.existsActiveById(accountId)) {
      result.add(CommercialOfferEligibilityBlocker.ACCOUNT_INACTIVE);
    }
    if (offer.getStatus() != CommercialOfferStatus.PUBLISHED) {
      result.add(CommercialOfferEligibilityBlocker.OFFER_NOT_PUBLISHED);
    }
    if (now.isBefore(offer.getStartsAt())) {
      result.add(CommercialOfferEligibilityBlocker.OFFER_WINDOW_NOT_STARTED);
    }
    if (!now.isBefore(offer.getEndsAt())) {
      result.add(CommercialOfferEligibilityBlocker.OFFER_WINDOW_ENDED);
    }
    if (offer.getCampaign().getStatus() != CommercialCampaignStatus.ACTIVE) {
      result.add(CommercialOfferEligibilityBlocker.CAMPAIGN_NOT_ACTIVE);
    }
    if (!operator && offer.getAcceptance() == CommercialOfferAcceptance.OPERATOR_ONLY) {
      result.add(CommercialOfferEligibilityBlocker.SELECTION_UNAVAILABLE);
    }
    if (!discovered && offer.getDiscovery() != CommercialOfferDiscovery.CATALOG) {
      result.add(CommercialOfferEligibilityBlocker.SELECTION_UNAVAILABLE);
    }
    if (audiences.countEligibleAccount(offer.getCampaign().getId(), accountId) == 0) {
      result.add(CommercialOfferEligibilityBlocker.ACCOUNT_OUTSIDE_AUDIENCE);
    }
    if (subscriptions
        .findTopByAccountIdAndStatusInOrderByCreatedAtDesc(
            accountId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING))
        .isEmpty()) {
      result.add(CommercialOfferEligibilityBlocker.NO_ACTIVE_SUBSCRIPTION);
    }
    return List.copyOf(new LinkedHashSet<>(result));
  }

  boolean hasAvailableCapacity(CommercialOffer offer, UUID accountId) {
    return capacityBlockers(offer, accountId).isEmpty();
  }

  private List<CommercialOfferEligibilityBlocker> capacityBlockers(
      CommercialOffer offer, UUID accountId) {
    List<CommercialOfferEligibilityBlocker> result = new ArrayList<>();
    var capacity = capacities.findByLineageId(offer.getLineageId()).orElse(null);
    if (capacity == null) {
      return List.of(CommercialOfferEligibilityBlocker.GLOBAL_CAPACITY_EXHAUSTED);
    }
    if (offer.getGlobalLimit() != null
        && capacity.getReservedCount() + capacity.getAppliedCount() >= offer.getGlobalLimit()) {
      result.add(CommercialOfferEligibilityBlocker.GLOBAL_CAPACITY_EXHAUSTED);
    }
    if (offer.getPerAccountLimit() != null
        && redemptions.countByOfferLineageIdAndAccount_IdAndStatusIn(
                offer.getLineageId(), accountId, USED)
            >= offer.getPerAccountLimit()) {
      result.add(CommercialOfferEligibilityBlocker.ACCOUNT_CAPACITY_EXHAUSTED);
    }
    return List.copyOf(result);
  }
}
