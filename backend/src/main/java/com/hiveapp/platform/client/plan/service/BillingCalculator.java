package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.shared.money.Money;
import lombok.RequiredArgsConstructor;
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
@Service
@RequiredArgsConstructor
public class BillingCalculator {

    private final SubscriptionSnapshotReader subscriptionSnapshotReader;

    public Money calculateMoney(Subscription sub) {
        var snapshot = subscriptionSnapshotReader.read(sub.getEntitlementSnapshot())
                .orElseThrow(() -> new IllegalStateException("Subscription entitlement snapshot is required"));
        if (snapshot.commercialPolicyEvaluation() != null
                && snapshot.commercialPolicyEvaluation().finalRecurringPrice() != null) {
            return Money.of(
                    snapshot.commercialPolicyEvaluation().finalRecurringPrice(),
                    snapshot.commercialPolicyEvaluation().currencyCode());
        }
        return catalogueMoney(snapshot);
    }

    /** Exact recurring catalogue price before commercial-policy price adjustments. */
    public Money catalogueMoney(SubscriptionEntitlementSnapshot snapshot) {
        Money total = Money.of(snapshot.basePrice(), snapshot.currencyCode());

        for (var addOn : snapshot.addOns()) {
            total = total.add(Money.of(addOn.price(), addOn.currencyCode()));
        }
        for (var quotaPackage : snapshot.quotaPackages()) {
            total = total.add(Money.of(quotaPackage.unitPrice(), quotaPackage.currencyCode())
                    .multiply(quotaPackage.quantity()));
        }
        return total;
    }

    /** Compatibility accessor for callers that only display an amount. New persistence uses calculateMoney(). */
    public BigDecimal calculate(Subscription sub) {
        return calculateMoney(sub).amount();
    }

}
