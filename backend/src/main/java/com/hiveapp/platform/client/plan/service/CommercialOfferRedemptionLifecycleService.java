package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferRedemptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.repository.CommercialOfferCapacityRepository;
import com.hiveapp.platform.client.plan.domain.repository.CommercialOfferRedemptionRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** Reconciles durable Offer capacity with asynchronous checkout and renewal operations. */
@Component
@RequiredArgsConstructor
public class CommercialOfferRedemptionLifecycleService {
    private final CommercialOfferRedemptionRepository redemptions;
    private final CommercialOfferCapacityRepository capacities;
    private final SubscriptionChangeOperationRepository operations;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${hiveapp.offers.redemption-delay-ms:30000}")
    @Transactional
    public void reconcile() {
        var ids = redemptions.findIdsByStatusWithOperation(
                CommercialOfferRedemptionStatus.RESERVED, PageRequest.of(0, 100)).getContent();
        for (var id : ids) {
            var redemption = redemptions.lockById(id).orElse(null);
            if (redemption == null || redemption.getStatus() != CommercialOfferRedemptionStatus.RESERVED) continue;
            var operation = operations.findById(redemption.getSubscriptionOperationId()).orElse(null);
            if (operation == null) continue;
            if (operation.getStatus() == SubscriptionChangeStatus.APPLIED) {
                capacities.lockByLineage(redemption.getOfferLineageId()).orElseThrow().apply();
                redemption.apply(clock.instant());
            } else if (operation.getStatus() == SubscriptionChangeStatus.CANCELLED) {
                capacities.lockByLineage(redemption.getOfferLineageId()).orElseThrow().release();
                redemption.cancel("SUBSCRIPTION_OPERATION_CANCELLED", clock.instant());
            }
        }
    }
}
