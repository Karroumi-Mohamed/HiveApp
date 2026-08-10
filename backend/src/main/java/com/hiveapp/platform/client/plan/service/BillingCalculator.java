package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
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
 *                + sum(snapshot AddOn prices)
 *                + sum(snapshot quota package unit price × selected quantity)
 *
 * Call calculate() whenever overrides change and store the result in Subscription.currentPrice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingCalculator {

    private final AddOnRepository addOnRepository;
    private final QuotaPackageRepository quotaPackageRepository;
    private final SubscriptionOverrideReader subscriptionOverrideReader;
    private final SubscriptionSnapshotReader subscriptionSnapshotReader;

    public Money calculateMoney(Subscription sub) {
        var snapshot = subscriptionSnapshotReader.read(sub.getEntitlementSnapshot()).orElse(null);
        Money total = basePrice(sub, snapshot);

        if (snapshot != null) {
            if (snapshot.addOns() != null) {
                for (var addOn : snapshot.addOns()) {
                    total = total.add(Money.of(addOn.price(), addOn.currencyCode()));
                }
            }
            if (snapshot.quotaPackages() != null) {
                for (var quotaPackage : snapshot.quotaPackages()) {
                    total = total.add(Money.of(quotaPackage.unitPrice(), quotaPackage.currencyCode())
                            .multiply(quotaPackage.quantity()));
                }
            }
            return total;
        }

        if (sub.getCustomOverrides() == null) return total;

        com.hiveapp.platform.client.plan.dto.SubscriptionOverrides overrides;
        try {
            overrides = subscriptionOverrideReader.read(sub.getCustomOverrides());
        } catch (Exception e) {
            log.warn("Could not deserialize overrides for subscription {}: {}", sub.getId(), e.getMessage());
            return total;
        }

        if (overrides.addOnCodes() != null) {
            for (String addOnCode : overrides.addOnCodes()) {
                var price = addOnRepository.findByCode(addOnCode)
                        .map(com.hiveapp.platform.client.plan.domain.entity.AddOn::money)
                        .orElse(null);
                if (price != null) {
                    total = total.add(price);
                }
            }
        }

        if (overrides.quotaPackages() != null) {
            for (var selection : overrides.quotaPackages()) {
                var item = quotaPackageRepository.findByCode(selection.packageCode()).orElse(null);
                if (item != null) {
                    total = total.add(item.money().multiply(selection.quantity()));
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

}
