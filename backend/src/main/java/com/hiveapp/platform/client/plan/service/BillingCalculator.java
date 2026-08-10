package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionFeatureSnapshot;
import com.hiveapp.shared.quota.QuotaOverride;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.money.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Calculates the recurring price for a subscription.
 *
 * Formula:
 *   currentPrice = subscriptionSnapshot.basePrice
 *                + sum(snapshotAddOn.price for each AddOn in overrides.addOnCodes)
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
    private final AddOnRepository addOnRepository;
    private final SubscriptionOverrideReader subscriptionOverrideReader;
    private final SubscriptionSnapshotReader subscriptionSnapshotReader;

    public Money calculateMoney(Subscription sub) {
        var snapshot = subscriptionSnapshotReader.read(sub.getEntitlementSnapshot()).orElse(null);
        Money total = basePrice(sub, snapshot);

        if (sub.getCustomOverrides() == null) return total;

        com.hiveapp.platform.client.plan.dto.SubscriptionOverrides overrides;
        try {
            overrides = subscriptionOverrideReader.read(sub.getCustomOverrides());
        } catch (Exception e) {
            log.warn("Could not deserialize overrides for subscription {}: {}", sub.getId(), e.getMessage());
            return total;
        }

        // --- First-class AddOn pricing ---
        if (overrides.addOnCodes() != null) {
            for (String addOnCode : overrides.addOnCodes()) {
                var snapshotPrice = snapshotAddOn(snapshot, addOnCode)
                        .map(addOn -> Money.of(addOn.price(), addOn.currencyCode()));
                var price = snapshotPrice.or(() -> addOnRepository.findByCode(addOnCode)
                                .map(com.hiveapp.platform.client.plan.domain.entity.AddOn::money))
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
                    total = total.add(planEntry.get().priceMoney().multiply(bump));
                }
            }
        }

        return total;
    }

    /** Compatibility accessor for callers that only display an amount. New persistence uses calculateMoney(). */
    public BigDecimal calculate(Subscription sub) {
        return calculateMoney(sub).amount();
    }

    private Money basePrice(Subscription sub, SubscriptionEntitlementSnapshot snapshot) {
        if (snapshot != null && snapshot.basePrice() != null) {
            return Money.of(snapshot.basePrice(), snapshot.currencyCode());
        }
        return sub.getPlan().getPrice() != null
                ? sub.getPlan().money()
                : Money.zero(sub.getPlan().getCurrencyCode());
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

    private java.util.Optional<SubscriptionAddOnSnapshot> snapshotAddOn(
            SubscriptionEntitlementSnapshot snapshot, String addOnCode) {
        if (snapshot == null || snapshot.addOns() == null) {
            return java.util.Optional.empty();
        }
        return snapshot.addOns().stream()
                .filter(addOn -> addOnCode.equals(addOn.code()))
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
