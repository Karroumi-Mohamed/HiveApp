package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.shared.exception.InvalidStateException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class SubscriptionPeriodCalculator {

    private final Clock clock;

    public Period recurring(BillingCycle cycle) {
        return recurring(cycle, clock.instant());
    }

    public Period recurring(BillingCycle cycle, Instant startsAt) {
        if (cycle == null || cycle == BillingCycle.FOREVER) {
            throw new InvalidStateException("Recurring subscriptions require a MONTHLY or YEARLY billing cycle.");
        }
        var start = startsAt.atZone(ZoneOffset.UTC);
        Instant endsAt = switch (cycle) {
            case MONTHLY -> start.plusMonths(1).toInstant();
            case YEARLY -> start.plusYears(1).toInstant();
            case FOREVER -> throw new InvalidStateException("FOREVER subscriptions are not supported.");
        };
        return new Period(startsAt, endsAt);
    }

    /**
     * Calculates the target entitlement period for a reviewed subscription change.
     * This method is the shared timing rule for both review and operation creation.
     */
    public Period change(
            BillingCycle cycle,
            SubscriptionChangeTiming timing,
            Instant renewalAt
    ) {
        if (timing == null) {
            throw new InvalidStateException("Subscription change timing is required.");
        }
        if (timing == SubscriptionChangeTiming.AT_RENEWAL && renewalAt == null) {
            throw new InvalidStateException(
                    "Renewal subscription changes require the current period end.");
        }
        return timing == SubscriptionChangeTiming.AT_RENEWAL
                ? recurring(cycle, renewalAt)
                : recurring(cycle);
    }

    public Period trial(int trialDays) {
        if (trialDays < 1 || trialDays > 365) {
            throw new IllegalArgumentException("Trial duration must be between 1 and 365 days.");
        }
        Instant startsAt = clock.instant();
        return new Period(startsAt, startsAt.plusSeconds(Math.multiplyExact(trialDays, 86_400L)));
    }

    public record Period(Instant startsAt, Instant endsAt) {}
}
