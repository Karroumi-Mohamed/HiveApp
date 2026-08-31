package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionLifecycleAction;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionSuspensionCause;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** One business rule source for backend actions, review, apply, and the admin read model. */
public final class SubscriptionLifecycleRules {
    private SubscriptionLifecycleRules() {}

    public static Set<SubscriptionLifecycleAction> availableActions(
            Subscription subscription, Instant now) {
        EnumSet<SubscriptionLifecycleAction> actions = EnumSet.noneOf(SubscriptionLifecycleAction.class);
        switch (subscription.getStatus()) {
            case ACTIVE, TRIALING -> {
                actions.add(subscription.isCancelAtPeriodEnd()
                        ? SubscriptionLifecycleAction.KEEP_RENEWING
                        : SubscriptionLifecycleAction.CANCEL_AT_PERIOD_END);
                actions.add(SubscriptionLifecycleAction.CANCEL_IMMEDIATELY);
                actions.add(SubscriptionLifecycleAction.SUSPEND);
            }
            case PAST_DUE -> {
                actions.add(SubscriptionLifecycleAction.CANCEL_IMMEDIATELY);
                actions.add(SubscriptionLifecycleAction.EXTEND_GRACE);
            }
            case SUSPENDED -> {
                actions.add(SubscriptionLifecycleAction.CANCEL_IMMEDIATELY);
                if (subscription.getSuspensionCause() == SubscriptionSuspensionCause.OPERATOR
                        && subscription.getCurrentPeriodEnd().isAfter(now)) {
                    actions.add(SubscriptionLifecycleAction.RESTORE);
                }
                if (subscription.getSuspensionCause() == SubscriptionSuspensionCause.COLLECTION) {
                    actions.add(SubscriptionLifecycleAction.EXTEND_GRACE);
                }
            }
            case CANCELLED, EXPIRED -> {
                // Terminal restoration requires a new reviewed commercial selection, not a
                // status flip. It will be delivered with reviewed trial/new-entitlement creation.
            }
        }
        return Set.copyOf(actions);
    }

    public static List<String> blockers(
            Subscription subscription,
            SubscriptionLifecycleAction action,
            Instant requestedGraceEndsAt,
            Instant now) {
        if (!availableActions(subscription, now).contains(action)) {
            return List.of("Action unavailable for the current subscription state.");
        }
        if (action == SubscriptionLifecycleAction.EXTEND_GRACE) {
            if (requestedGraceEndsAt == null) {
                return List.of("A new grace deadline is required.");
            }
            if (!requestedGraceEndsAt.isAfter(now)) {
                return List.of("The new grace deadline must be in the future.");
            }
            if (subscription.getGraceEndsAt() != null
                    && !requestedGraceEndsAt.isAfter(subscription.getGraceEndsAt())) {
                return List.of("The new grace deadline must extend the current deadline.");
            }
        }
        return List.of();
    }
}
