package com.hiveapp.subscription.domain.service;

import com.hiveapp.plan.event.PlanChangedEvent;
import com.hiveapp.shared.exception.BusinessException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.subscription.domain.dto.*;
import com.hiveapp.subscription.domain.entity.Subscription;
import com.hiveapp.subscription.domain.mapper.SubscriptionMapper;
import com.hiveapp.subscription.domain.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionMapper subscriptionMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SubscriptionResponse createSubscription(CreateSubscriptionRequest request) {
        Instant now = Instant.now();
        Instant periodEnd = now.plus(30, ChronoUnit.DAYS);

        Subscription subscription = Subscription.builder()
                .accountId(request.getAccountId())
                .planId(request.getPlanId())
                .currentPeriodStart(now)
                .currentPeriodEnd(periodEnd)
                .build();

        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Subscription created for account {}", request.getAccountId());
        return subscriptionMapper.toResponse(saved);
    }

    @Transactional
    public void cancelSubscription(UUID id) {
        Subscription subscription = findSubscriptionOrThrow(id);
        if (subscription.isCancelled()) {
            throw new BusinessException("Subscription is already cancelled");
        }
        subscription.cancel();
        subscriptionRepository.save(subscription);
        log.info("Subscription cancelled: {}", id);
    }

    @Transactional
    public void changePlan(UUID subscriptionId, UUID newPlanId) {
        Subscription subscription = findSubscriptionOrThrow(subscriptionId);
        UUID oldPlanId = subscription.getPlanId();
        subscription.changePlan(newPlanId);
        subscriptionRepository.save(subscription);
        log.info("Subscription {} plan changed from {} to {}", subscriptionId, oldPlanId, newPlanId);

        eventPublisher.publishEvent(
                new PlanChangedEvent(newPlanId, subscription.getAccountId(), oldPlanId, newPlanId));
    }

    public SubscriptionResponse getActiveSubscription(UUID accountId) {
        Subscription subscription = subscriptionRepository.findByAccountIdAndStatus(accountId, "active")
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", "accountId", accountId));
        return subscriptionMapper.toResponse(subscription);
    }

    public List<SubscriptionResponse> getSubscriptionsByAccountId(UUID accountId) {
        return subscriptionMapper.toResponseList(subscriptionRepository.findByAccountId(accountId));
    }

    public Subscription findSubscriptionOrThrow(UUID id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", "id", id));
    }
}
