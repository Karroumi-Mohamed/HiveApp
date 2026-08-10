package com.hiveapp.platform.client.plan.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@RequiredArgsConstructor
public class SubscriptionLifecycleScheduler {

    private final SubscriptionRenewalChangeProcessor renewalChangeProcessor;
    private final SubscriptionLifecycleManager subscriptionLifecycleManager;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${hiveapp.subscriptions.lifecycle-delay-ms:60000}")
    public void processDueSubscriptions() {
        renewalChangeProcessor.processDue(clock.instant());
        subscriptionLifecycleManager.processDueSubscriptions();
    }
}
