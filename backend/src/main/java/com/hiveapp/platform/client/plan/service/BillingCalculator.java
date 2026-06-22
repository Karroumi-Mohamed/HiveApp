package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionFeatureSnapshot;
import com.hiveapp.shared.quota.QuotaOverride;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Calculates the monthly price for a subscription.
 *
 * Formula:
 *   currentPrice = subscriptionSnapshot.basePrice
 *                + sum(snapshotFeature.addOnPrice for each feature in overrides.addedFeatures)
 *                + sum((override.limit - snapshotLimit.limit) × snapshotLimit.pricePerUnit
 *                      for each quota override where bump > 0)
 *
 * Call calculate() whenever overrides change and store the result in Subscription.currentPrice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingCalculator {

    private final PlanFeatureRepository planFeatureRepository;
    private final SubscriptionOverrideReader subscriptionOverrideReader;
    private final SubscriptionSnapshotReader subscriptionSnapshotReader;

    public BigDecimal calculate(Subscription sub) {
        var snapshot = subscriptionSnapshotReader.read(sub.getEntitlementSnapshot()).orElse(null);
        BigDecimal total = basePrice(sub, snapshot);

        if (sub.getCustomOverrides() == null) return total;

        try {
            var overrides = subscriptionOverrideReader.read(sub.getCustomOverrides());

            // --- Feature add-on pricing ---
            if (overrides.addedFeatures() != null) {
                for (String featureCode : overrides.addedFeatures()) {
                    var price = snapshotFeature(snapshot, featureCode)
                            .map(SubscriptionFeatureSnapshot::addOnPrice)
                            .or(() -> planFeatureRepository.findByPlanIdAndFeature_Code(
                                            sub.getPlan().getId(), featureCode)
                                    .map(planFeature -> planFeature.getAddOnPrice()))
                            .orElse(null);
                    if (price != null) {
                        total = total.add(price);
                    }
                }
            }

            // --- Quota bump pricing ---
            if (overrides.quotaOverrides() != null) {
                for (QuotaOverride override : overrides.quotaOverrides()) {
                    var planEntry = snapshotQuota(snapshot, override)
                            .or(() -> planFeatureRepository.findByPlanIdAndFeature_Code(
                                            sub.getPlan().getId(), override.featureCode())
                                    .flatMap(planFeature -> planFeature.getQuotaConfigs().stream()
                                            .filter(e -> e.resource().equals(override.resource()))
                                            .findFirst()));

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
        } catch (Exception e) {
            log.warn("Could not deserialize overrides for subscription {}: {}", sub.getId(), e.getMessage());
            return total;
        }

        return total;
    }

    private BigDecimal basePrice(Subscription sub, SubscriptionEntitlementSnapshot snapshot) {
        if (snapshot != null && snapshot.basePrice() != null) {
            return snapshot.basePrice();
        }
        return sub.getPlan().getPrice() != null ? sub.getPlan().getPrice() : BigDecimal.ZERO;
    }

    private java.util.Optional<SubscriptionFeatureSnapshot> snapshotFeature(
            SubscriptionEntitlementSnapshot snapshot, String featureCode) {
        if (snapshot == null || snapshot.features() == null) {
            return java.util.Optional.empty();
        }
        return snapshot.features().stream()
                .filter(feature -> featureCode.equals(feature.featureCode()))
                .findFirst();
    }

    private java.util.Optional<QuotaLimitEntry> snapshotQuota(
            SubscriptionEntitlementSnapshot snapshot, QuotaOverride override) {
        return snapshotFeature(snapshot, override.featureCode())
                .flatMap(feature -> feature.quotaConfigs() == null
                        ? java.util.Optional.empty()
                        : feature.quotaConfigs().stream()
                                .filter(quota -> override.resource().equals(quota.resource()))
                                .findFirst());
    }
}
