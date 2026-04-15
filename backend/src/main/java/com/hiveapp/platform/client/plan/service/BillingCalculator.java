package com.hiveapp.platform.client.plan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.shared.quota.QuotaOverride;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Calculates the monthly price for a subscription.
 *
 * Formula:
 *   currentPrice = plan.basePrice
 *                + sum(planFeature.addOnPrice  for each feature in overrides.addedFeatures)
 *                + sum((override.limit - planLimit.limit) × planLimit.pricePerUnit
 *                      for each quota override where bump > 0)
 *
 * Call calculate() whenever overrides change and store the result in Subscription.currentPrice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingCalculator {

    private final PlanFeatureRepository planFeatureRepository;
    private final ObjectMapper objectMapper;

    public BigDecimal calculate(Subscription sub) {
        BigDecimal total = sub.getPlan().getPrice() != null
                ? sub.getPlan().getPrice()
                : BigDecimal.ZERO;

        if (sub.getCustomOverrides() == null) return total;

        SubscriptionOverrides overrides;
        try {
            overrides = objectMapper.convertValue(sub.getCustomOverrides(), SubscriptionOverrides.class);
        } catch (Exception e) {
            log.warn("Could not deserialize overrides for subscription {}: {}", sub.getId(), e.getMessage());
            return total;
        }

        // --- Feature add-on pricing ---
        if (overrides.addedFeatures() != null) {
            for (String featureCode : overrides.addedFeatures()) {
                var pf = planFeatureRepository.findByPlanIdAndFeature_Code(
                        sub.getPlan().getId(), featureCode);
                if (pf.isPresent() && pf.get().getAddOnPrice() != null) {
                    total = total.add(pf.get().getAddOnPrice());
                }
            }
        }

        // --- Quota bump pricing ---
        if (overrides.quotaOverrides() != null) {
            for (QuotaOverride override : overrides.quotaOverrides()) {
                var pf = planFeatureRepository.findByPlanIdAndFeature_Code(
                        sub.getPlan().getId(), override.featureCode());
                if (pf.isEmpty()) continue;

                var planEntry = pf.get().getQuotaConfigs().stream()
                        .filter(e -> e.resource().equals(override.resource()))
                        .findFirst();

                if (planEntry.isEmpty()
                        || planEntry.get().pricePerUnit() == null
                        || planEntry.get().limit() == null
                        || override.limit() == null) continue;

                long bump = override.limit() - planEntry.get().limit();
                if (bump > 0) {
                    total = total.add(
                            planEntry.get().pricePerUnit().multiply(BigDecimal.valueOf(bump)));
                }
            }
        }

        return total;
    }
}
