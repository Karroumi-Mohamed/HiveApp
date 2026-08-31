package com.hiveapp.platform.client.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.CommercialAnalyticsInterval;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.infrastructure.CommercialAnalyticsReadRepository;
import com.hiveapp.platform.client.plan.service.impl.CommercialAnalyticsServiceImpl;
import com.hiveapp.shared.exception.InvalidRequestException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommercialAnalyticsServiceImplTest {

    @Test
    void financialSeriesKeepsMoneyDimensionsSeparateAndUsesDstAwareBuckets() {
        CommercialAnalyticsReadRepository reads = mock(CommercialAnalyticsReadRepository.class);
        SubscriptionChangeOperationRepository operations =
                mock(SubscriptionChangeOperationRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-03-30T12:00:00Z"), ZoneOffset.UTC);
        var service = new CommercialAnalyticsServiceImpl(reads, operations, clock);
        Instant from = Instant.parse("2026-03-28T23:00:00Z");
        Instant until = Instant.parse("2026-03-30T22:00:00Z");
        when(reads.financialFacts(from, until, null, null)).thenReturn(List.of(
                fact("INVOICED", "2026-03-29T10:00:00Z", "10.25", "USD", BillingCycle.MONTHLY),
                fact("COLLECTED", "2026-03-29T11:00:00Z", "7.00", "USD", BillingCycle.MONTHLY),
                fact("INVOICED", "2026-03-30T11:00:00Z", "5", "EUR", BillingCycle.YEARLY)));

        var result = service.financialSeries(new CommercialAnalyticsService.Query(
                from, until, "Europe/Paris", CommercialAnalyticsInterval.DAY, null, null));

        assertThat(result.dimensions()).hasSize(2);
        var euro = result.dimensions().get(0);
        var usd = result.dimensions().get(1);
        assertThat(euro.dimension().currencyCode()).isEqualTo("EUR");
        assertThat(euro.dimension().billingCycle()).isEqualTo(BillingCycle.YEARLY);
        assertThat(usd.dimension().currencyCode()).isEqualTo("USD");
        assertThat(usd.points()).hasSize(2);
        assertThat(usd.points().get(0).bucketStart()).isEqualTo(from);
        assertThat(usd.points().get(0).bucketEnd())
                .isEqualTo(Instant.parse("2026-03-29T22:00:00Z"));
        assertThat(usd.points().get(0).invoiced()).isEqualTo("10.2500");
        assertThat(usd.points().get(0).collected()).isEqualTo("7.0000");
        assertThat(usd.points().get(0).provisional()).isFalse();
        assertThat(usd.points().get(1).provisional()).isTrue();
        assertThat(result.metadata().currentBucketProvisional()).isTrue();
    }

    @Test
    void rangeAndCurrencyValidationFailBeforePersistence() {
        var service = new CommercialAnalyticsServiceImpl(
                mock(CommercialAnalyticsReadRepository.class),
                mock(SubscriptionChangeOperationRepository.class),
                Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.financialSeries(new CommercialAnalyticsService.Query(
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2026-08-31T00:00:00Z"),
                "UTC", CommercialAnalyticsInterval.MONTH, null, null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("366 days");
        assertThatThrownBy(() -> service.financialSeries(new CommercialAnalyticsService.Query(
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-31T00:00:00Z"),
                "UTC", CommercialAnalyticsInterval.DAY, "US", null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("3 letters");
    }

    private CommercialAnalyticsReadRepository.FinancialFact fact(
            String metric,
            String at,
            String amount,
            String currency,
            BillingCycle cycle
    ) {
        return new CommercialAnalyticsReadRepository.FinancialFact(
                metric, Instant.parse(at), new BigDecimal(amount), currency, cycle);
    }
}
