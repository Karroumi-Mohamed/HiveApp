package com.hiveapp.platform.client.plan.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionLifecycleAction;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionSuspensionCause;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SubscriptionLifecycleRulesTest {
    private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");

    @Test
    void collectionSuspensionCannotBeRestoredWithoutSettlement() {
        Subscription subscription = subscription(SubscriptionStatus.SUSPENDED);
        subscription.setSuspensionCause(SubscriptionSuspensionCause.COLLECTION);
        subscription.setSuspendedFromStatus(SubscriptionStatus.PAST_DUE);

        assertThat(SubscriptionLifecycleRules.availableActions(subscription, NOW))
                .contains(SubscriptionLifecycleAction.EXTEND_GRACE)
                .doesNotContain(SubscriptionLifecycleAction.RESTORE);
    }

    @Test
    void operatorSuspensionCanBeRestoredOnlyBeforeItsPeriodEnds() {
        Subscription subscription = subscription(SubscriptionStatus.SUSPENDED);
        subscription.setSuspensionCause(SubscriptionSuspensionCause.OPERATOR);
        subscription.setSuspendedFromStatus(SubscriptionStatus.ACTIVE);

        assertThat(SubscriptionLifecycleRules.availableActions(subscription, NOW))
                .contains(SubscriptionLifecycleAction.RESTORE);
        assertThat(SubscriptionLifecycleRules.availableActions(
                subscription, subscription.getCurrentPeriodEnd()))
                .doesNotContain(SubscriptionLifecycleAction.RESTORE);
    }

    @Test
    void graceMustActuallyExtendThePersistedDeadline() {
        Subscription subscription = subscription(SubscriptionStatus.PAST_DUE);
        subscription.setPastDueAt(NOW.minusSeconds(3_600));
        subscription.setGraceEndsAt(NOW.plusSeconds(3_600));

        assertThat(SubscriptionLifecycleRules.blockers(
                subscription,
                SubscriptionLifecycleAction.EXTEND_GRACE,
                NOW.plusSeconds(1_800),
                NOW)).containsExactly("The new grace deadline must extend the current deadline.");
        assertThat(SubscriptionLifecycleRules.blockers(
                subscription,
                SubscriptionLifecycleAction.EXTEND_GRACE,
                NOW.plusSeconds(7_200),
                NOW)).isEmpty();
    }

    private Subscription subscription(SubscriptionStatus status) {
        Subscription subscription = new Subscription();
        subscription.setStatus(status);
        subscription.setCurrentPeriodStart(NOW.minusSeconds(86_400));
        subscription.setCurrentPeriodEnd(NOW.plusSeconds(86_400));
        return subscription;
    }
}
