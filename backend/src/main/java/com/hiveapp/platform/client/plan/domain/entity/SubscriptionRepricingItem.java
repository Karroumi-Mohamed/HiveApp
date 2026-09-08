package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.dto.RepricingModels;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "subscription_repricing_items",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_repricing_account",
          columnNames = {"job_id", "account_id"}),
      @UniqueConstraint(name = "uk_repricing_pending_account", columnNames = "pending_account_id")
    },
    indexes = {
      @Index(name = "idx_repricing_due", columnList = "status,effective_at"),
      @Index(name = "idx_repricing_job_results", columnList = "job_id,created_at"),
      @Index(name = "idx_repricing_account_notices", columnList = "account_id,notice_created_at"),
      @Index(name = "idx_repricing_email_queue", columnList = "delivery,created_at")
    })
@Getter
@Setter
public class SubscriptionRepricingItem extends BaseEntity {
  @Version private long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private SubscriptionRepricing job;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Account account;

  private UUID termsIdentity;

  @Column(length = 64)
  private String termsFingerprint;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private RepricingModels.State status;

  @Column(name = "pending_account_id")
  private UUID pendingAccountId;

  @Column(length = 100)
  private String blocker;

  @JdbcTypeCode(SqlTypes.JSON)
  private SubscriptionEntitlementSnapshot beforeSnapshot;

  @JdbcTypeCode(SqlTypes.JSON)
  private SubscriptionEntitlementSnapshot targetSnapshot;

  @Column(precision = 19, scale = 4)
  private BigDecimal oldTotal;

  @Column(precision = 19, scale = 4)
  private BigDecimal newTotal;

  private int quantity;

  @Column(name = "effective_at")
  private Instant effectiveAt;

  @ManyToOne(fetch = FetchType.LAZY)
  private SubscriptionChangeOperation operation;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private RepricingModels.Delivery delivery = RepricingModels.Delivery.NOT_REQUESTED;

  private int emailAttempts;
  private Instant emailClaimedAt;
  private UUID emailClaimId;
  private UUID emailRecipientId;
  private Instant noticeCreatedAt;

  @Column(length = 2000)
  private String cancellationReason;

  private UUID cancelledBy;

  @PrePersist
  @PreUpdate
  void synchronizePendingSlot() {
    pendingAccountId = status == RepricingModels.State.PENDING ? account.getId() : null;
  }
}
