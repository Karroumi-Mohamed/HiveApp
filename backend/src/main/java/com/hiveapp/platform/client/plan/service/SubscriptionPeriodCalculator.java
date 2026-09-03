package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.shared.exception.InvalidStateException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.OptionalLong;

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

    public Period exact(Instant startsAt, Instant endsAt) {
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new InvalidStateException("Agreement end must be after its start.");
        }
        return new Period(startsAt, endsAt);
    }

    /** Returns a cycle count only when the exact UTC term is made of complete catalogue cycles. */
    public OptionalLong completeCycles(BillingCycle cycle, Instant startsAt, Instant endsAt) {
        exact(startsAt, endsAt);
        if (cycle == null || cycle == BillingCycle.FOREVER) return OptionalLong.empty();
        ZonedDateTime cursor = startsAt.atZone(ZoneOffset.UTC);
        ZonedDateTime target = endsAt.atZone(ZoneOffset.UTC);
        long cycles = 0;
        while (cursor.isBefore(target) && cycles < 1_200) {
            cursor = cycle == BillingCycle.MONTHLY ? cursor.plusMonths(1) : cursor.plusYears(1);
            cycles++;
        }
        return cursor.equals(target) ? OptionalLong.of(cycles) : OptionalLong.empty();
    }

    public record Period(Instant startsAt, Instant endsAt) {}
}
