package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
// Production note: add partial unique index → UNIQUE (account_id) WHERE status = 'ACTIVE'
// JPA @UniqueConstraint can't express partial index — do it in Flyway migration when moving off H2.
@Getter @Setter
public class Subscription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_overrides")
    private Object customOverrides;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;

    /**
     * Snapshot of the calculated monthly price at the time overrides were last saved.
     * = plan.basePrice + sum(addOnPrices) + sum(quotaBumpCosts).
     * Recalculated by BillingCalculator every time overrides change.
     */
    @Column(name = "current_price", precision = 10, scale = 2)
    private BigDecimal currentPrice;
}
