package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.CommercialAnalyticsInterval;
import com.hiveapp.platform.client.plan.domain.constant.CommercialAttentionType;
import com.hiveapp.platform.client.plan.dto.CommercialAnalyticsModels;
import com.hiveapp.platform.client.plan.service.CommercialAnalyticsService;
import com.hiveapp.shared.api.PageResponse;
import com.hiveapp.shared.exception.InvalidRequestException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
public class CommercialAnalyticsAdminController {
    private final CommercialAnalyticsService analytics;

    @GetMapping("/overview")
    public CommercialAnalyticsModels.Overview overview(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant until,
            @RequestParam(required = false) String timezone,
            @RequestParam(required = false) CommercialAnalyticsInterval interval,
            @RequestParam(required = false) String currencyCode,
            @RequestParam(required = false) BillingCycle billingCycle
    ) {
        return analytics.overview(query(
                from, until, timezone, interval, currencyCode, billingCycle));
    }

    @GetMapping("/financial-series")
    public CommercialAnalyticsModels.FinancialSeries financialSeries(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant until,
            @RequestParam(required = false) String timezone,
            @RequestParam(required = false) CommercialAnalyticsInterval interval,
            @RequestParam(required = false) String currencyCode,
            @RequestParam(required = false) BillingCycle billingCycle
    ) {
        return analytics.financialSeries(query(
                from, until, timezone, interval, currencyCode, billingCycle));
    }

    @GetMapping("/subscription-series")
    public CommercialAnalyticsModels.SubscriptionSeries subscriptionSeries(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant until,
            @RequestParam(required = false) String timezone,
            @RequestParam(required = false) CommercialAnalyticsInterval interval
    ) {
        return analytics.subscriptionSeries(query(from, until, timezone, interval, null, null));
    }

    @GetMapping("/offer-series")
    public CommercialAnalyticsModels.OfferSeries offerSeries(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant until,
            @RequestParam(required = false) String timezone,
            @RequestParam(required = false) CommercialAnalyticsInterval interval
    ) {
        return analytics.offerSeries(query(from, until, timezone, interval, null, null));
    }

    @GetMapping("/product-holdings")
    public List<CommercialAnalyticsModels.ProductHolding> productHoldings() {
        return analytics.productHoldings();
    }

    @GetMapping("/attention")
    public PageResponse<CommercialAnalyticsModels.AttentionRow> attention(
            @RequestParam(required = false) CommercialAttentionType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidRequestException("Analytics attention pages support 1 to 100 rows.");
        }
        return PageResponse.from(analytics.attention(type, PageRequest.of(page, size)));
    }

    private CommercialAnalyticsService.Query query(
            Instant from,
            Instant until,
            String timezone,
            CommercialAnalyticsInterval interval,
            String currencyCode,
            BillingCycle billingCycle
    ) {
        return new CommercialAnalyticsService.Query(
                from, until, timezone, interval, currencyCode, billingCycle);
    }
}
