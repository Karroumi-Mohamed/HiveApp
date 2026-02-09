package com.hiveapp.subscription.domain.listener;

import com.hiveapp.account.event.AccountCreatedEvent;
import com.hiveapp.subscription.domain.entity.Subscription;
import com.hiveapp.subscription.domain.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Automatically creates an active subscription when a new account is created.
 * Ensures every account starts with a valid subscription on the assigned plan.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountCreatedSubscriptionListener {

    private final SubscriptionRepository subscriptionRepository;

    @EventListener
    public void onAccountCreated(AccountCreatedEvent event) {
        if (event.getPlanId() == null) {
            log.warn("Account {} created without a plan — skipping subscription creation", event.getAccountId());
            return;
        }

        Instant now = Instant.now();
        Subscription subscription = Subscription.builder()
                .accountId(event.getAccountId())
                .planId(event.getPlanId())
                .currentPeriodStart(now)
                .currentPeriodEnd(now.plus(30, ChronoUnit.DAYS))
                .build();

        subscriptionRepository.save(subscription);
        log.info("Subscription auto-created for account {} on plan {}", event.getAccountId(), event.getPlanId());
    }
}
