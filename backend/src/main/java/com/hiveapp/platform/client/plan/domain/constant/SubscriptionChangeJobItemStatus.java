package com.hiveapp.platform.client.plan.domain.constant;

public enum SubscriptionChangeJobItemStatus {
  ASSESSING,
  READY,
  WAITING,
  APPLIED,
  UNCHANGED,
  EXCLUDED,
  PENDING_RENEWAL,
  AWAITING_PAYMENT,
  CONFLICT,
  FAILED,
  CANCELLED;

  public boolean terminal() {
    return this != READY && this != ASSESSING && this != WAITING;
  }

  public boolean retryable() {
    return this == CONFLICT || this == FAILED;
  }
}
