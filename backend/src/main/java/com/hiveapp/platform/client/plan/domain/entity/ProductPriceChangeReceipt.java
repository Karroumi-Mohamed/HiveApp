package com.hiveapp.platform.client.plan.domain.entity;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** An immutable receipt makes response-loss retries safe, even after a later price change. */
@Entity
@Table(name = "product_price_change_receipts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductPriceChangeReceipt {
    @Id private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(nullable = false, updatable = false)
    private UUID currentPriceId;

    @Column(nullable = false, updatable = false, length = 64)
    private String fingerprint;

    @Lob
    @Column(nullable = false, updatable = false)
    private String resultJson;

    public ProductPriceChangeReceipt(
            UUID id, UUID actorUserId, UUID currentPriceId, String fingerprint, String resultJson) {
        this.id = id;
        this.actorUserId = actorUserId;
        this.currentPriceId = currentPriceId;
        this.fingerprint = fingerprint;
        this.resultJson = resultJson;
    }
}
