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
import java.util.UUID;

@Entity
@Table(name = "subscriptions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_subscriptions_usable_account", columnNames = "usable_account_id")
})
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
    private String customOverrides;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "entitlement_snapshot")
    private String entitlementSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    /**
     * Non-null only while this subscription can provide entitlement. Its uniqueness enforces
     * one ACTIVE or TRIALING subscription per account while allowing historical subscriptions.
     */
    @Column(name = "usable_account_id", columnDefinition = """
            uuid check ((status in ('ACTIVE', 'TRIALING') and usable_account_id is not null and usable_account_id = account_id)
            or (status not in ('ACTIVE', 'TRIALING') and usable_account_id is null))
            """)
    private UUID usableAccountId;

    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;

    /**
     * Snapshot of the calculated monthly price at the time overrides were last saved.
     * = plan.basePrice + sum(addOnPrices) + sum(quotaBumpCosts).
     * Recalculated by BillingCalculator every time overrides change.
     */
    @Column(name = "current_price", precision = 10, scale = 2)
    private BigDecimal currentPrice;

    @PrePersist
    @PreUpdate
    void synchronizeUsableAccountSlot() {
        boolean usable = status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIALING;
        usableAccountId = usable && account != null ? account.getId() : null;
    }
}
