package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SubscriptionRenewalChangeProcessor {

    private final SubscriptionChangeOperationRepository operationRepository;
    private final SubscriptionChangeActivationService activationService;

    @Transactional
    public void processDue(Instant cutoff) {
        for (SubscriptionChangeOperation operation : operationRepository.findDueForUpdate(
                SubscriptionChangeStatus.PENDING, cutoff)) {
            activationService.activate(operation, operation.getEffectiveAt());
        }
    }
}
