package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementAttentionStage;
import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementEndInstruction;
import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementPricingMode;
import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementSettlementMode;
import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementStatus;
import com.hiveapp.platform.client.plan.dto.CommercialOfferEffectSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "special_commercial_agreements", uniqueConstraints = {
        @UniqueConstraint(name = "uk_special_agreement_live_account", columnNames = "live_account_id"),
        @UniqueConstraint(name = "uk_special_agreement_operation", columnNames = "change_operation_id")
})
@Getter
@Setter
public class SpecialCommercialAgreement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @Column(name = "live_account_id")
    private UUID liveAccountId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_subscription_id", nullable = false, updatable = false)
    private Subscription sourceSubscription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_plan_id", nullable = false, updatable = false)
    private Plan targetPlan;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "change_operation_id")
    private SubscriptionChangeOperation changeOperation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_subscription_id")
    private Subscription resultSubscription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SpecialAgreementStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_mode", nullable = false, length = 32)
    private SpecialAgreementPricingMode pricingMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_mode", nullable = false, length = 20)
    private SpecialAgreementSettlementMode settlementMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_instruction", nullable = false, length = 40)
    private SpecialAgreementEndInstruction endInstruction;

    @Enumerated(EnumType.STRING)
    @Column(name = "attention_stage", length = 16)
    private SpecialAgreementAttentionStage attentionStage;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false, updatable = false)
    private Instant endsAt;

    @Column(name = "catalogue_cycle_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal catalogueCycleAmount;

    @Column(name = "catalogue_term_amount", precision = 19, scale = 4)
    private BigDecimal catalogueTermAmount;

    @Column(name = "agreed_term_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal agreedTermAmount;

    @Column(name = "follow_on_amount", precision = 19, scale = 4)
    private BigDecimal followOnAmount;

    @Column(name = "previous_recurring_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal previousRecurringAmount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requested_selection", nullable = false, updatable = false)
    private SubscriptionOverrides requestedSelection;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quota_bonuses", nullable = false, updatable = false)
    private List<CommercialOfferEffectSnapshot.QuotaBonus> quotaBonuses;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_snapshot", nullable = false, updatable = false)
    private SubscriptionEntitlementSnapshot beforeSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "term_snapshot", nullable = false, updatable = false)
    private SubscriptionEntitlementSnapshot termSnapshot;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "reason", nullable = false, updatable = false, length = 2000)
    private String reason;

    @Column(name = "attention_reason", length = 2000)
    private String attentionReason;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by_user_id")
    private UUID cancelledByUserId;

    @Column(name = "cancellation_reason", length = 2000)
    private String cancellationReason;

    @Version
    @Column(name = "row_version", nullable = false)
    private long version;

    public Money agreedMoney() {
        return Money.of(agreedTermAmount, currencyCode);
    }

    public Money previousMoney() {
        return Money.of(previousRecurringAmount, currencyCode);
    }

    public Money followOnMoney() {
        return followOnAmount == null ? null : Money.of(followOnAmount, currencyCode);
    }

    @PrePersist
    @PreUpdate
    void validateAgreement() {
        if (account == null || sourceSubscription == null || targetPlan == null || status == null
                || pricingMode == null || settlementMode == null || endInstruction == null
                || startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)
                || requestedSelection == null || quotaBonuses == null
                || beforeSnapshot == null || termSnapshot == null || createdByUserId == null
                || reason == null || reason.isBlank()) {
            throw new IllegalStateException("Special agreement evidence is incomplete.");
        }
        Money cycle = Money.of(catalogueCycleAmount, currencyCode);
        Money agreed = Money.of(agreedTermAmount, currencyCode);
        Money previous = Money.of(previousRecurringAmount, currencyCode);
        if (cycle.isNegative() || agreed.isNegative() || previous.isNegative()
                || (catalogueTermAmount != null && Money.of(catalogueTermAmount, currencyCode).isNegative())
                || (followOnAmount != null && Money.of(followOnAmount, currencyCode).isNegative())) {
            throw new IllegalStateException("Special agreement amounts cannot be negative.");
        }
        if (pricingMode == SpecialAgreementPricingMode.CATALOGUE_TOTAL
                && catalogueTermAmount == null) {
            throw new IllegalStateException("Catalogue pricing requires one complete-cycle term total.");
        }
        if (pricingMode == SpecialAgreementPricingMode.COMPLIMENTARY && agreed.amount().signum() != 0) {
            throw new IllegalStateException("A complimentary agreement must have a zero total.");
        }
        if ((agreed.amount().signum() == 0) != (settlementMode == SpecialAgreementSettlementMode.NONE)) {
            throw new IllegalStateException("Only zero-total agreements use no-payment settlement.");
        }
        if ((endInstruction == SpecialAgreementEndInstruction.CONTINUE_REVIEWED_TERMS)
                != (followOnAmount != null)) {
            throw new IllegalStateException("Only continuation agreements carry a reviewed follow-on amount.");
        }
        boolean live = status == SpecialAgreementStatus.SCHEDULED
                || status == SpecialAgreementStatus.AWAITING_SETTLEMENT
                || status == SpecialAgreementStatus.ACTIVE
                || status == SpecialAgreementStatus.NEEDS_ATTENTION;
        liveAccountId = live ? account.getId() : null;
        if (status == SpecialAgreementStatus.NEEDS_ATTENTION
                && (attentionStage == null || attentionReason == null || attentionReason.isBlank())) {
            throw new IllegalStateException("An agreement needing attention requires its stage and reason.");
        }
        if (status != SpecialAgreementStatus.NEEDS_ATTENTION
                && (attentionStage != null || attentionReason != null)) {
            throw new IllegalStateException("Only an agreement needing attention carries attention evidence.");
        }
        if (status == SpecialAgreementStatus.CANCELLED
                && (cancelledAt == null || cancelledByUserId == null
                || cancellationReason == null || cancellationReason.isBlank())) {
            throw new IllegalStateException("A cancelled agreement requires cancellation evidence.");
        }
    }
}
