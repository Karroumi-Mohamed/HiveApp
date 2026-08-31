package com.hiveapp.platform.client.plan.domain.constant;

public enum SubscriptionChangeJobItemStatus {
  READY,
  APPLIED,
  PENDING_RENEWAL,
  AWAITING_PAYMENT,
  CONFLICT,
  FAILED,
  CANCELLED;

  public boolean terminal() {
    return this != READY;
  }

  public boolean retryable() {
    return this == CONFLICT || this == FAILED;
  }
}
