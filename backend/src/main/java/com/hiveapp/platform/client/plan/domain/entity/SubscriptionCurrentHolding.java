package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentProductType;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Portable, indexed projection of the products in the authoritative entitlement JSON snapshot.
 * It exists only to support bounded database-side commercial audience selection.
 */
@Entity
@Table(name = "subscription_current_holdings", indexes = {
        @Index(name = "idx_subscription_holding_product", columnList = "product_type,product_code"),
        @Index(name = "idx_subscription_holding_subscription", columnList = "subscription_id")
}, uniqueConstraints = @UniqueConstraint(name = "uk_subscription_holding_product",
        columnNames = {"subscription_id", "product_type", "product_code"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionCurrentHolding extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false, updatable = false)
    private Subscription subscription;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, updatable = false, length = 24)
    private CommercialSegmentProductType productType;

    @Column(name = "product_code", nullable = false, updatable = false, length = 100)
    private String productCode;

    static SubscriptionCurrentHolding of(
            Subscription subscription,
            CommercialSegmentProductType productType,
            String productCode
    ) {
        if (subscription == null || productType == null || productCode == null || productCode.isBlank()) {
            throw new IllegalArgumentException("A current subscription holding requires product identity.");
        }
        SubscriptionCurrentHolding holding = new SubscriptionCurrentHolding();
        holding.subscription = subscription;
        holding.productType = productType;
        holding.productCode = productCode.trim().toUpperCase(java.util.Locale.ROOT);
        return holding;
    }
}
