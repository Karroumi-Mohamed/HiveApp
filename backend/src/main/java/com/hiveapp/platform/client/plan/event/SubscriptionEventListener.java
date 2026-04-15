package com.hiveapp.platform.client.plan.event;

import com.hiveapp.platform.client.account.event.AccountCreatedEvent;
import com.hiveapp.platform.client.plan.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionEventListener {

    private final SubscriptionService subscriptionService;

    @EventListener
    public void handleAccountCreatedEvent(AccountCreatedEvent event) {
        log.info("Provisioning FREE subscription for newly created account: {}", event.getAccountId());
        try {
            subscriptionService.createSubscription(event.getAccountId(), "FREE");
        } catch (Exception e) {
            log.error("Failed to provision FREE subscription for account {}: {}", event.getAccountId(), e.getMessage());
        }
    }
}
