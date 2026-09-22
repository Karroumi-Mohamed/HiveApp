package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobItemStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobStatus;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeJobModels;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.PlanVersionRolloutModels;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "subscription_change_jobs",
    indexes = {
      @Index(name = "idx_subscription_change_job_due", columnList = "status,execute_at,id"),
      @Index(name = "idx_subscription_change_job_actor", columnList = "requested_by_user_id,created_at,id"),
      @Index(name = "idx_subscription_change_job_family", columnList = "plan_lineage_id,created_at,id")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionChangeJob extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private SubscriptionChangeJobStatus status = SubscriptionChangeJobStatus.PREVIEWED;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "requested_selection")
  private SubscriptionChangeRequest selection;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "content_definition", updatable = false)
  private PlanVersionRolloutModels.Definition contentDefinition;

  @Column(name = "plan_lineage_id", updatable = false)
  private UUID planLineageId;

  @Column(name = "requested_by_user_id", nullable = false, updatable = false)
  private UUID requestedByUserId;

  @Column(name = "request_reason", nullable = false, length = 2000)
  private String reason;

  @Column(name = "execute_at", nullable = false)
  private Instant executeAt;

  @Column(name = "catalog_revision", nullable = false)
  private long catalogRevision;

  @Column(name = "registry_version", nullable = false, length = 128)
  private String registryVersion;

  @Column(name = "assessment_fingerprint", nullable = false, length = 64)
  private String assessmentFingerprint;

  @Column(name = "evaluated_at", nullable = false)
  private Instant evaluatedAt;

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancelled_by_user_id")
  private UUID cancelledByUserId;

  @Column(name = "cancellation_reason", length = 2000)
  private String cancellationReason;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "last_retried_by_user_id")
  private UUID lastRetriedByUserId;

  @Column(name = "last_retried_at")
  private Instant lastRetriedAt;

  @Column(name = "last_retry_reason", length = 2000)
  private String lastRetryReason;

  @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  @Setter(AccessLevel.NONE)
  private List<SubscriptionChangeJobItem> items = new ArrayList<>();

  @Version
  @Column(nullable = false)
  private long version;

  public static SubscriptionChangeJob previewed(
      SubscriptionChangeRequest selection,
      UUID actorUserId,
      String reason,
      Instant executeAt,
      long catalogRevision,
      String registryVersion,
      Instant evaluatedAt) {
    SubscriptionChangeJob job = new SubscriptionChangeJob();
    job.selection = Objects.requireNonNull(selection, "Selection is required");
    job.requestedByUserId = Objects.requireNonNull(actorUserId, "Actor is required");
    job.reason = required(reason, "Reason");
    job.executeAt = Objects.requireNonNull(executeAt, "Execution time is required");
    job.catalogRevision = catalogRevision;
    job.registryVersion = required(registryVersion, "Registry version");
    job.evaluatedAt = Objects.requireNonNull(evaluatedAt, "Evaluation time is required");
    return job;
  }

  public boolean isContentVersion() { return contentDefinition != null; }

  public static SubscriptionChangeJob assessingContent(
      UUID familyId, PlanVersionRolloutModels.Definition definition, UUID actor,
      long catalogRevision, String registryVersion, Instant now, String fingerprint) {
    SubscriptionChangeJob job = new SubscriptionChangeJob();
    job.contentDefinition = Objects.requireNonNull(definition);
    job.planLineageId = Objects.requireNonNull(familyId);
    job.requestedByUserId = Objects.requireNonNull(actor);
    job.reason = required(definition.request().application().reason(), "Reason");
    job.status = SubscriptionChangeJobStatus.ASSESSING;
    job.executeAt = now;
    job.evaluatedAt = now;
    job.catalogRevision = catalogRevision;
    job.registryVersion = required(registryVersion, "Registry version");
    job.assessmentFingerprint = required(fingerprint, "Frozen audience fingerprint");
    return job;
  }

  public void sealContentAssessment(String fingerprint, Instant now) {
    if (!isContentVersion() || status != SubscriptionChangeJobStatus.ASSESSING)
      throw new IllegalStateException("Only an assessing content job can be reviewed.");
    assessmentFingerprint = required(fingerprint, "Assessment fingerprint");
    evaluatedAt = now;
    status = SubscriptionChangeJobStatus.PREVIEWED;
    Instant requested = contentDefinition.request().application().notBefore();
    executeAt = requested == null || requested.isBefore(now) ? now : requested;
  }

  public void deferContent(Instant until) {
    if (!isContentVersion()) throw new IllegalStateException("Not a content job.");
    executeAt = Objects.requireNonNull(until);
  }

  /** Aggregate counts are queried, never hydrate a large population to finish a job. */
  public boolean completeContent(boolean outstanding, boolean errors, Instant now) {
    if (!isContentVersion() || status != SubscriptionChangeJobStatus.RUNNING || outstanding) return false;
    status = errors ? SubscriptionChangeJobStatus.COMPLETED_WITH_ERRORS : SubscriptionChangeJobStatus.COMPLETED;
    completedAt = now;
    return true;
  }

  public void cancelContent(UUID actor, String reason, Instant now) {
    if (!isContentVersion() || status == SubscriptionChangeJobStatus.COMPLETED
        || status == SubscriptionChangeJobStatus.COMPLETED_WITH_ERRORS || status == SubscriptionChangeJobStatus.CANCELLED)
      throw new IllegalStateException("Only unfinished content jobs can be cancelled.");
    status = SubscriptionChangeJobStatus.CANCELLED;
    cancelledByUserId = Objects.requireNonNull(actor);
    cancellationReason = required(reason, "Cancellation reason");
    cancelledAt = now;
    completedAt = now;
  }

  public void retryContent(UUID actor, String reason, Instant now) {
    if (!isContentVersion() || status != SubscriptionChangeJobStatus.COMPLETED_WITH_ERRORS)
      throw new IllegalStateException("Only completed content jobs with technical failures can retry.");
    lastRetriedByUserId = Objects.requireNonNull(actor);
    lastRetryReason = required(reason, "Retry reason");
    lastRetriedAt = now;
    retryCount++;
    status = SubscriptionChangeJobStatus.QUEUED;
    executeAt = now;
    completedAt = null;
  }

  public void addItem(
      com.hiveapp.platform.client.account.domain.entity.Account account,
      SubscriptionChangeJobModels.Assessment assessment,
      SubscriptionChangeJobItemStatus status,
      String outcomeCode) {
    if (this.status != SubscriptionChangeJobStatus.PREVIEWED) {
      throw new IllegalStateException("Only a previewed job can receive targets.");
    }
    items.add(SubscriptionChangeJobItem.create(this, account, assessment, status, outcomeCode));
  }

  public List<SubscriptionChangeJobItem> getItems() {
    return Collections.unmodifiableList(items);
  }

  public void sealAssessment(String fingerprint) {
    if (items.isEmpty()) throw new IllegalStateException("A subscription-change job requires targets.");
    assessmentFingerprint = required(fingerprint, "Assessment fingerprint");
  }

  public void confirm(Instant now) {
    if (status != SubscriptionChangeJobStatus.PREVIEWED) {
      throw new IllegalStateException("Only a previewed job can be confirmed.");
    }
    confirmedAt = Objects.requireNonNull(now, "Confirmation time is required");
    status = executeAt.isAfter(now)
        ? SubscriptionChangeJobStatus.SCHEDULED
        : SubscriptionChangeJobStatus.QUEUED;
  }

  public boolean start(Instant now) {
    if (status == SubscriptionChangeJobStatus.RUNNING) {
      return false;
    }
    if (status != SubscriptionChangeJobStatus.QUEUED
        && status != SubscriptionChangeJobStatus.SCHEDULED) {
      throw new IllegalStateException("Only a queued or scheduled job can start.");
    }
    status = SubscriptionChangeJobStatus.RUNNING;
    startedAt = Objects.requireNonNull(now, "Start time is required");
    return true;
  }

  public boolean completeIfTerminal(Instant now) {
    if (status != SubscriptionChangeJobStatus.RUNNING) {
      throw new IllegalStateException("Only a running job can complete.");
    }
    if (items.stream().anyMatch(item -> !item.getStatus().terminal())) {
      return false;
    }
    boolean errors = items.stream().anyMatch(item ->
        item.getStatus() == SubscriptionChangeJobItemStatus.CONFLICT
            || item.getStatus() == SubscriptionChangeJobItemStatus.FAILED);
    status = errors
        ? SubscriptionChangeJobStatus.COMPLETED_WITH_ERRORS
        : SubscriptionChangeJobStatus.COMPLETED;
    completedAt = Objects.requireNonNull(now, "Completion time is required");
    return true;
  }

  public void cancel(UUID actorUserId, String reason, Instant now) {
    if (status != SubscriptionChangeJobStatus.PREVIEWED
        && status != SubscriptionChangeJobStatus.QUEUED
        && status != SubscriptionChangeJobStatus.SCHEDULED) {
      throw new IllegalStateException("Only an unstarted job can be cancelled.");
    }
    items.forEach(SubscriptionChangeJobItem::cancel);
    status = SubscriptionChangeJobStatus.CANCELLED;
    cancelledByUserId = Objects.requireNonNull(actorUserId, "Cancellation actor is required");
    cancellationReason = required(reason, "Cancellation reason");
    cancelledAt = Objects.requireNonNull(now, "Cancellation time is required");
    completedAt = now;
  }

  public void prepareRetry(UUID actorUserId, String retryReason, Instant now) {
    if (status != SubscriptionChangeJobStatus.COMPLETED_WITH_ERRORS) {
      throw new IllegalStateException("Only a completed job with errors can be retried.");
    }
    int retried = 0;
    for (SubscriptionChangeJobItem item : items) {
      if (item.prepareRetry()) retried++;
    }
    if (retried == 0) throw new IllegalStateException("The job has no retryable results.");
    lastRetriedByUserId = Objects.requireNonNull(actorUserId, "Retry actor is required");
    lastRetryReason = required(retryReason, "Retry reason");
    lastRetriedAt = Objects.requireNonNull(now, "Retry time is required");
    retryCount++;
    status = SubscriptionChangeJobStatus.QUEUED;
    executeAt = now;
    startedAt = null;
    completedAt = null;
  }

  @PrePersist
  @PreUpdate
  void validateJob() {
    if ((selection == null) == (contentDefinition == null) || requestedByUserId == null || executeAt == null || evaluatedAt == null
        || (contentDefinition != null && planLineageId == null)
        || reason == null || reason.isBlank() || registryVersion == null || registryVersion.isBlank()
        || assessmentFingerprint == null || assessmentFingerprint.isBlank()) {
      throw new IllegalStateException("Subscription-change job provenance is incomplete.");
    }
  }

  private static String required(String value, String label) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
    return value.trim();
  }
}
