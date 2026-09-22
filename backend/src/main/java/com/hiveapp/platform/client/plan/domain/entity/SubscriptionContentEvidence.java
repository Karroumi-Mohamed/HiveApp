package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.plan.dto.PlanVersionApplicationModels.Reviewed;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only effective content history, separate from immutable invoices and paid periods. */
@Entity
@Immutable
@Table(
    name = "subscription_content_evidence",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_content_evidence_command", columnNames = "command_id"),
      @UniqueConstraint(name = "uk_content_evidence_operation", columnNames = "operation_id")
    },
    indexes =
        @Index(
            name = "idx_content_evidence_account_effective",
            columnList = "account_id,effective_at"))
@Getter
public class SubscriptionContentEvidence extends BaseEntity {
  @Column(name = "command_id", nullable = false, updatable = false)
  private UUID commandId;

  @Column(name = "account_id", nullable = false, updatable = false)
  private UUID accountId;

  @Column(name = "subscription_id", nullable = false, updatable = false)
  private UUID subscriptionId;

  @Column(name = "operation_id", nullable = false, updatable = false)
  private UUID operationId;

  @Column(name = "billing_period_id", nullable = false, updatable = false)
  private UUID billingPeriodId;

  @Column(name = "previous_evidence_id", updatable = false)
  private UUID previousEvidenceId;

  @Column(name = "actor_user_id", nullable = false, updatable = false)
  private UUID actorUserId;

  @Column(name = "planned_at", nullable = false, updatable = false)
  private Instant plannedAt;

  @Column(name = "effective_at", nullable = false, updatable = false)
  private Instant effectiveAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "reviewed_terms", nullable = false, updatable = false)
  private Reviewed reviewedTerms;

  protected SubscriptionContentEvidence() {}

  public SubscriptionContentEvidence(
      UUID commandId,
      UUID subscriptionId,
      UUID operationId,
      UUID billingPeriodId,
      UUID actorUserId,
      Instant plannedAt,
      Instant effectiveAt,
      Reviewed reviewed) {
    this.commandId = commandId;
    this.accountId = reviewed.accountId();
    this.subscriptionId = subscriptionId;
    this.operationId = operationId;
    this.billingPeriodId = billingPeriodId;
    this.previousEvidenceId = reviewed.previousEvidenceId();
    this.actorUserId = actorUserId;
    this.plannedAt = plannedAt;
    this.effectiveAt = effectiveAt;
    this.reviewedTerms = reviewed;
  }
}
