package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.dto.SubscriptionOfferEvaluation;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "commercial_offer_redemptions",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_offer_redemption_account_key",
            columnNames = {"account_id", "idempotency_key_hash"}),
    indexes = {
      @Index(
          name = "idx_offer_redemption_lineage_status",
          columnList = "offer_lineage_id,status,id"),
      @Index(name = "idx_offer_redemption_account", columnList = "account_id,created_at,id"),
      @Index(name = "idx_offer_redemption_operation", columnList = "subscription_operation_id")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommercialOfferRedemption extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id", nullable = false)
  private Account account;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "offer_id", nullable = false, updatable = false)
  private CommercialOffer offer;

  @Column(name = "offer_lineage_id", nullable = false, updatable = false)
  private UUID offerLineageId;

  @Column(name = "campaign_id", nullable = false, updatable = false)
  private UUID campaignId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private CommercialOfferRedemptionStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private CommercialOfferSurface surface;

  @Column(name = "actor_user_id", nullable = false, updatable = false)
  private UUID actorUserId;

  @Column(name = "idempotency_key_hash", nullable = false, updatable = false, length = 64)
  private String idempotencyKeyHash;

  @Column(name = "request_fingerprint", nullable = false, updatable = false, length = 64)
  private String requestFingerprint;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "commercial_evaluation", nullable = false, updatable = false)
  private SubscriptionOfferEvaluation commercialEvaluation;

  @Column(name = "subscription_operation_id")
  private UUID subscriptionOperationId;

  @Column(name = "reserved_at", nullable = false, updatable = false)
  private Instant reservedAt;

  @Column(name = "applied_at")
  private Instant appliedAt;

  @Column(name = "released_at")
  private Instant releasedAt;

  @Column(name = "terminal_reason", length = 1000)
  private String terminalReason;

  @Version
  @Column(name = "row_version", nullable = false)
  private long version;

  public static CommercialOfferRedemption reserve(
      Account account,
      CommercialOffer offer,
      CommercialOfferSurface surface,
      UUID actor,
      String keyHash,
      String fingerprint,
      SubscriptionOfferEvaluation evaluation,
      Instant now) {
    var r = new CommercialOfferRedemption();
    r.account = account;
    r.offer = offer;
    r.offerLineageId = offer.getLineageId();
    r.campaignId = offer.getCampaign().getId();
    r.status = CommercialOfferRedemptionStatus.RESERVED;
    r.surface = surface;
    r.actorUserId = actor;
    r.idempotencyKeyHash = keyHash;
    r.requestFingerprint = fingerprint;
    r.commercialEvaluation = java.util.Objects.requireNonNull(evaluation);
    r.reservedAt = now;
    return r;
  }

  public void linkOperation(UUID id) {
    if (status != CommercialOfferRedemptionStatus.RESERVED)
      throw new IllegalStateException("Only a reserved redemption can link an operation.");
    if (subscriptionOperationId != null && !subscriptionOperationId.equals(id)) {
      throw new IllegalStateException("Offer redemption is linked to another operation.");
    }
    subscriptionOperationId = id;
  }

  public void apply(Instant now) {
    requireReserved();
    status = CommercialOfferRedemptionStatus.APPLIED;
    appliedAt = now;
  }

  public void cancel(String reason, Instant now) {
    release(CommercialOfferRedemptionStatus.CANCELLED, reason, now);
  }

  public void fail(String reason, Instant now) {
    release(CommercialOfferRedemptionStatus.FAILED, reason, now);
  }

  private void release(CommercialOfferRedemptionStatus target, String reason, Instant now) {
    requireReserved();
    status = target;
    terminalReason = reason;
    releasedAt = now;
  }

  private void requireReserved() {
    if (status != CommercialOfferRedemptionStatus.RESERVED)
      throw new IllegalStateException("Offer redemption is already terminal.");
  }
}
