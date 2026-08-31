package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.constant.CheckoutConfirmationSource;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionCheckoutStatus;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.money.Money;
import com.hiveapp.shared.payment.PaymentStatus;
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
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_checkouts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_subscription_checkout_operation", columnNames = "change_operation_id"),
        @UniqueConstraint(name = "uk_subscription_checkout_confirmation_reference",
                columnNames = "confirmation_reference")
})
@Getter
@Setter
public class SubscriptionCheckout extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "change_operation_id", nullable = false)
    private SubscriptionChangeOperation changeOperation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SubscriptionCheckoutStatus status = SubscriptionCheckoutStatus.PENDING_CONFIRMATION;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "requested_by_user_id")
    private UUID requestedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway_attempt_status", length = 24)
    private PaymentStatus gatewayAttemptStatus;

    @Column(name = "gateway_reference", length = 255)
    private String gatewayReference;

    @Column(name = "gateway_failure_reason", length = 2000)
    private String gatewayFailureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_source", length = 32)
    private CheckoutConfirmationSource confirmationSource;

    @Column(name = "confirmation_reference", length = 255)
    private String confirmationReference;

    @Column(name = "confirmation_reason", length = 2000)
    private String confirmationReason;

    @Column(name = "confirmed_by_user_id")
    private UUID confirmedByUserId;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public Money money() {
        return Money.of(amount, currencyCode);
    }

    public void setMoney(Money money) {
        amount = money.amount();
        currencyCode = money.currencyCode();
    }

    @PrePersist
    @PreUpdate
    void validateCheckout() {
        setMoney(Money.of(amount, currencyCode));
        if (amount.signum() <= 0) {
            throw new IllegalStateException("A checkout requires a positive amount");
        }
        if (status == SubscriptionCheckoutStatus.CONFIRMED) {
            if (confirmationSource == null || confirmationReference == null
                    || confirmationReference.isBlank() || confirmationReason == null
                    || confirmationReason.isBlank() || confirmedAt == null
                    || (confirmationSource == CheckoutConfirmationSource.MANUAL_OPERATOR
                    && confirmedByUserId == null)
                    || (confirmationSource == CheckoutConfirmationSource.TRUSTED_PROVIDER
                    && confirmedByUserId != null)) {
                throw new IllegalStateException("A confirmed checkout requires confirmation evidence");
            }
        } else if (confirmationSource != null || confirmationReference != null
                || confirmationReason != null || confirmedByUserId != null || confirmedAt != null) {
            throw new IllegalStateException("Only confirmed checkouts may contain confirmation evidence");
        }
        if (requestedByUserId == null
                && (changeOperation == null
                || changeOperation.getRequestOrigin()
                != com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeOrigin.SYSTEM)) {
            throw new IllegalStateException("A non-system checkout requires a requesting user");
        }
    }
}
