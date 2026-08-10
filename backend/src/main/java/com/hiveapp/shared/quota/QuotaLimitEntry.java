package com.hiveapp.shared.quota;

import com.hiveapp.shared.money.Money;

import java.math.BigDecimal;

/**
 * One limit value for a quota slot, stored per plan in PlanFeature.quota_configs JSONB.
 *
 * resource      — matches a resource name declared in the Feature's QuotaSlot list.
 * limit         — null = explicitly unlimited for this plan tier.
 * pricePerUnit  — cost per unit above this plan's limit when a client bumps the quota.
 *                 null = this slot cannot be bumped (fixed per tier).
 */
public record QuotaLimitEntry(
        String resource,
        Long limit,
        BigDecimal pricePerUnit,
        String priceCurrencyCode
) {
    public QuotaLimitEntry {
        if (pricePerUnit == null && priceCurrencyCode != null) {
            throw new IllegalArgumentException("Quota price currency requires a price");
        }
        if (pricePerUnit != null) {
            Money price = Money.of(pricePerUnit, priceCurrencyCode);
            pricePerUnit = price.amount();
            priceCurrencyCode = price.currencyCode();
        }
    }

    /** Convenience constructor — no bump pricing (boolean-access or fixed-tier slots). */
    public QuotaLimitEntry(String resource, Long limit) {
        this(resource, limit, null, null);
    }

    public Money priceMoney() {
        return pricePerUnit == null ? null : Money.of(pricePerUnit, priceCurrencyCode);
    }
}
