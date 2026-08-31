package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobItemStatus;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeJobModels;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "subscription_change_job_items",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_subscription_change_job_account", columnNames = {"job_id", "account_id"}),
    indexes = {
      @Index(name = "idx_subscription_change_job_item_status", columnList = "job_id,status,id"),
      @Index(name = "idx_subscription_change_job_item_account", columnList = "account_id,created_at,id")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionChangeJobItem extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "job_id", nullable = false, updatable = false)
  private SubscriptionChangeJob job;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id", nullable = false, updatable = false)
  private Account account;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private SubscriptionChangeJobItemStatus status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private SubscriptionChangeJobModels.Assessment assessment;

  @Column(name = "subscription_operation_id")
  private UUID subscriptionOperationId;

  @Column(name = "outcome_code", length = 100)
  private String outcomeCode;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "last_attempt_at")
  private Instant lastAttemptAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Version
  @Column(nullable = false)
  private long version;

  static SubscriptionChangeJobItem create(
      SubscriptionChangeJob job,
      Account account,
      SubscriptionChangeJobModels.Assessment assessment,
      SubscriptionChangeJobItemStatus status,
      String outcomeCode) {
    SubscriptionChangeJobItem item = new SubscriptionChangeJobItem();
    item.job = job;
    item.account = account;
    item.assessment = assessment;
    item.status = status;
    item.outcomeCode = outcomeCode;
    return item;
  }

  public void succeed(
      SubscriptionChangeJobItemStatus outcome, UUID operationId, Instant now) {
    if (outcome != SubscriptionChangeJobItemStatus.APPLIED
        && outcome != SubscriptionChangeJobItemStatus.PENDING_RENEWAL
        && outcome != SubscriptionChangeJobItemStatus.AWAITING_PAYMENT) {
      throw new IllegalArgumentException("Successful subscription job outcome is invalid.");
    }
    status = outcome;
    subscriptionOperationId = operationId;
    outcomeCode = outcome.name();
    attempts++;
    lastAttemptAt = now;
    completedAt = now;
  }

  public void fail(SubscriptionChangeJobItemStatus outcome, String code, Instant now) {
    if (outcome != SubscriptionChangeJobItemStatus.CONFLICT
        && outcome != SubscriptionChangeJobItemStatus.FAILED) {
      throw new IllegalArgumentException("Failed subscription job outcome is invalid.");
    }
    status = outcome;
    outcomeCode = code;
    attempts++;
    lastAttemptAt = now;
    completedAt = now;
  }

  public boolean prepareRetry() {
    if (!status.retryable()) return false;
    status = SubscriptionChangeJobItemStatus.READY;
    outcomeCode = null;
    completedAt = null;
    return true;
  }

  public void cancel() {
    if (status == SubscriptionChangeJobItemStatus.READY) {
      status = SubscriptionChangeJobItemStatus.CANCELLED;
      outcomeCode = SubscriptionChangeJobItemStatus.CANCELLED.name();
    }
  }

  @PrePersist
  @PreUpdate
  void validateItem() {
    if (job == null || account == null || status == null || assessment == null) {
      throw new IllegalStateException("Subscription-change job item is incomplete.");
    }
  }
}
