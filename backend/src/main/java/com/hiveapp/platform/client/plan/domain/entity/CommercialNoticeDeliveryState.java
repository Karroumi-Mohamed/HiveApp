package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Reusable in-app publication and durable email lease; a read receipt is not consent. */
@Embeddable
@Getter
public class CommercialNoticeDeliveryState {
  @Column(name = "notice_created_at")
  private Instant createdAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "notice_delivery", length = 24)
  private Delivery delivery = Delivery.NOT_REQUESTED;

  @Column(name = "notice_email_attempts")
  private int attempts;

  @Column(name = "notice_email_claim_id")
  private UUID claimId;

  @Column(name = "notice_email_claimed_at")
  private Instant claimedAt;

  @Column(name = "notice_email_recipient_id")
  private UUID recipientId;

  public void publish(Instant now, boolean email) {
    if (createdAt != null) throw new IllegalStateException("This notice was already published.");
    createdAt = now;
    delivery = email ? Delivery.PENDING : Delivery.NOT_REQUESTED;
  }

  public boolean claimable(Instant now) {
    return delivery == Delivery.PENDING
        || (delivery == Delivery.SENDING
            && claimedAt != null
            && claimedAt.isBefore(now.minusSeconds(300)));
  }

  public boolean automaticAttemptsExhausted() {
    return delivery == Delivery.SENDING && attempts >= 3;
  }

  public UUID claim(UUID recipient, Instant now) {
    if (!claimable(now)) throw new IllegalStateException("Notice is not ready for dispatch.");
    recipientId = recipient;
    claimId = UUID.randomUUID();
    claimedAt = now;
    attempts++;
    delivery = Delivery.SENDING;
    return claimId;
  }

  public void suppress() {
    delivery = Delivery.SUPPRESSED;
  }

  public void fail() {
    delivery = Delivery.FAILED;
  }

  public void cancelUndispatched() {
    if (delivery == Delivery.PENDING) delivery = Delivery.CANCELLED;
  }

  public void cancelAbandonedClaim() {
    delivery = Delivery.CANCELLED;
  }

  public void complete(UUID claim, Delivery outcome) {
    if (delivery != Delivery.SENDING || !java.util.Objects.equals(claimId, claim)) return;
    if (outcome != Delivery.SENT && outcome != Delivery.SUPPRESSED && outcome != Delivery.FAILED)
      throw new IllegalArgumentException("Invalid transport result.");
    delivery = outcome;
  }

  public boolean retryForNewRecipient(UUID currentRecipient) {
    if (delivery != Delivery.SENT || java.util.Objects.equals(recipientId, currentRecipient))
      return false;
    delivery = Delivery.PENDING;
    return true;
  }

  public boolean retry() {
    if (createdAt == null || (delivery != Delivery.FAILED && delivery != Delivery.SUPPRESSED))
      return false;
    delivery = Delivery.PENDING;
    return true;
  }
}
