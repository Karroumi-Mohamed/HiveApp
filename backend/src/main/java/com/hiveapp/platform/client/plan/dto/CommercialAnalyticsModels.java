package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.CommercialAnalyticsInterval;
import com.hiveapp.platform.client.plan.domain.constant.CommercialAttentionType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentProductType;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CommercialAnalyticsModels {
    private CommercialAnalyticsModels() {}

    public record Metadata(
            Instant generatedAt,
            Instant completeThrough,
            Instant from,
            Instant until,
            String timezone,
            CommercialAnalyticsInterval interval,
            boolean currentBucketProvisional
    ) {}

    public record Availability(
            boolean financialSeries,
            boolean subscriptionSeries,
            boolean offerSeries,
            boolean operations
    ) {}

    public record MoneyDimension(
            String currencyCode,
            BillingCycle billingCycle
    ) {}

    public record FinancialTotals(
            MoneyDimension dimension,
            String invoiced,
            String collected,
            String credited,
            String refunded
    ) {}

    public record ConfiguredRecurringValue(
            MoneyDimension dimension,
            String amount,
            long subscriptions
    ) {}

    public record Finality(
            long pendingPayments,
            long pendingRefunds,
            long pendingProviderCommands
    ) {}

    public record Overview(
            Metadata metadata,
            Availability availability,
            List<FinancialTotals> financialTotals,
            List<ConfiguredRecurringValue> configuredRecurringValues,
            Map<SubscriptionStatus, Long> currentSubscriptions,
            long operationsNeedingAttention,
            long graceDeadlinesWithinSevenDays,
            Map<String, Long> offerOutcomes,
            Finality finality
    ) {}

    public record FinancialPoint(
            Instant bucketStart,
            Instant bucketEnd,
            boolean provisional,
            String invoiced,
            String collected,
            String credited,
            String refunded
    ) {}

    public record FinancialDimensionSeries(
            MoneyDimension dimension,
            List<FinancialPoint> points
    ) {}

    public record FinancialSeries(
            Metadata metadata,
            List<FinancialDimensionSeries> dimensions
    ) {}

    public record SubscriptionPoint(
            Instant bucketStart,
            Instant bucketEnd,
            boolean provisional,
            Map<String, Long> lifecycleActions,
            long productsAdded,
            long productsRemoved
    ) {}

    public record ProductMovement(
            CommercialSegmentProductType productType,
            String productCode,
            long additions,
            long removals
    ) {}

    public record SubscriptionSeries(
            Metadata metadata,
            List<SubscriptionPoint> points,
            List<ProductMovement> productMovements
    ) {}

    public record OfferPoint(
            Instant bucketStart,
            Instant bucketEnd,
            boolean provisional,
            long reserved,
            long applied,
            long cancelled,
            long failed
    ) {}

    public record OfferSeries(
            Metadata metadata,
            List<OfferPoint> points
    ) {}

    public record ProductHolding(
            CommercialSegmentProductType productType,
            String productCode,
            long subscriptions
    ) {}

    public record AttentionRow(
            CommercialAttentionType type,
            UUID recordId,
            UUID accountId,
            String accountName,
            String status,
            Instant occurredAt,
            Instant dueAt,
            String amount,
            String currencyCode,
            String reason,
            String destination
    ) {}
}
