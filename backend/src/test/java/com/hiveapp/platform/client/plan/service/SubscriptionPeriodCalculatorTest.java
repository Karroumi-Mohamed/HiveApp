package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.shared.exception.InvalidStateException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionPeriodCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-08-28T10:15:30Z");

    private final SubscriptionPeriodCalculator calculator = new SubscriptionPeriodCalculator(
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void immediateChangeStartsAtClockTime() {
        var period = calculator.change(
                BillingCycle.MONTHLY, SubscriptionChangeTiming.IMMEDIATE, null);

        assertThat(period.startsAt()).isEqualTo(NOW);
        assertThat(period.endsAt()).isEqualTo(Instant.parse("2026-09-28T10:15:30Z"));
    }

    @Test
    void renewalChangeStartsAtCurrentPeriodEndAndUsesTargetCycle() {
        Instant renewalAt = Instant.parse("2026-12-01T00:00:00Z");

        var period = calculator.change(
                BillingCycle.YEARLY, SubscriptionChangeTiming.AT_RENEWAL, renewalAt);

        assertThat(period.startsAt()).isEqualTo(renewalAt);
        assertThat(period.endsAt()).isEqualTo(Instant.parse("2027-12-01T00:00:00Z"));
    }

    @Test
    void renewalChangeRequiresCurrentPeriodEnd() {
        assertThatThrownBy(() -> calculator.change(
                BillingCycle.MONTHLY, SubscriptionChangeTiming.AT_RENEWAL, null))
                .isInstanceOf(InvalidStateException.class)
                .hasMessage("Renewal subscription changes require the current period end.");
    }
}
