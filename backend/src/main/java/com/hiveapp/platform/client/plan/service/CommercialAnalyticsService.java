package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.CommercialAnalyticsInterval;
import com.hiveapp.platform.client.plan.domain.constant.CommercialAttentionType;
import com.hiveapp.platform.client.plan.dto.CommercialAnalyticsModels;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CommercialAnalyticsService {
    CommercialAnalyticsModels.Overview overview(Query query);

    CommercialAnalyticsModels.FinancialSeries financialSeries(Query query);

    CommercialAnalyticsModels.SubscriptionSeries subscriptionSeries(Query query);

    CommercialAnalyticsModels.OfferSeries offerSeries(Query query);

    List<CommercialAnalyticsModels.ProductHolding> productHoldings();

    Page<CommercialAnalyticsModels.AttentionRow> attention(
            CommercialAttentionType type, Pageable pageable);

    record Query(
            Instant from,
            Instant until,
            String timezone,
            CommercialAnalyticsInterval interval,
            String currencyCode,
            BillingCycle billingCycle
    ) {}
}
