package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.dto.*;
import com.hiveapp.platform.client.plan.dto.PlanContentNoticeModels.*;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One private notice per content command, shared by single-Account and population operations. */
@Entity
@Table(
    name = "plan_content_notices",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_plan_content_notice_command", columnNames = "command_id"),
    indexes = {
      @Index(name = "idx_plan_content_notice_account", columnList = "account_id,created_at,id"),
      @Index(name = "idx_plan_content_notice_job", columnList = "job_id,state,id"),
      @Index(
          name = "idx_plan_content_notice_delivery",
          columnList = "notice_delivery,notice_email_claimed_at,id")
    })
@Getter
public class PlanContentNotice extends BaseEntity {
  @Column(name = "command_id", nullable = false, updatable = false)
  private UUID commandId;

  @Column(name = "job_id", updatable = false)
  private UUID jobId;

  @Column(name = "source_plan_id", nullable = false, updatable = false)
  private UUID sourcePlanId;

  @Column(name = "target_plan_id", nullable = false, updatable = false)
  private UUID targetPlanId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id", nullable = false, updatable = false)
  private Account account;

  @Column(name = "plan_name", nullable = false, updatable = false)
  private String planName;

  @Column(name = "source_version", nullable = false, updatable = false)
  private long sourceVersion;

  @Column(name = "target_version", nullable = false, updatable = false)
  private long targetVersion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24, updatable = false)
  private Policy policy;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private State state = State.SCHEDULED;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24, updatable = false)
  private PlanVersionApplicationModels.Timing timing;

  @Column(name = "planned_at", nullable = false, updatable = false)
  private Instant plannedAt;

  @Column(name = "effective_at")
  private Instant effectiveAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, updatable = false)
  private PlanVersionRolloutModels.Impact impact;

  @Embedded private CommercialNoticeDeliveryState delivery = new CommercialNoticeDeliveryState();
  @Version private long version;

  protected PlanContentNotice() {}

  public PlanContentNotice(
      UUID commandId,
      UUID jobId,
      UUID sourcePlanId,
      UUID targetPlanId,
      Account account,
      Policy policy,
      String planName,
      long sourceVersion,
      long targetVersion,
      PlanVersionApplicationModels.Timing timing,
      Instant plannedAt,
      PlanVersionRolloutModels.Impact impact,
      Instant now) {
    this.commandId = java.util.Objects.requireNonNull(commandId);
    this.jobId = jobId;
    this.account = java.util.Objects.requireNonNull(account);
    this.sourcePlanId = java.util.Objects.requireNonNull(sourcePlanId);
    this.targetPlanId = java.util.Objects.requireNonNull(targetPlanId);
    this.policy = java.util.Objects.requireNonNull(policy);
    this.planName = planName;
    this.sourceVersion = sourceVersion;
    this.targetVersion = targetVersion;
    this.timing = timing;
    this.plannedAt = plannedAt;
    this.impact = impact;
    delivery.publish(now, policy != Policy.IN_APP);
  }

  public void applied(Instant at) {
    state = State.APPLIED;
    effectiveAt = at;
  }

  public void conflicted() {
    if (state != State.APPLIED) state = State.CONFLICT;
  }

  public void cancel() {
    if (state != State.APPLIED) {
      state = State.CANCELLED;
      delivery.cancelUndispatched();
    }
  }

  public void resumeAfterNoticeRetry() {
    if (state == State.CONFLICT) state = State.SCHEDULED;
  }
}
