package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PlanSubscriberViewModels {
  private PlanSubscriberViewModels() {}

  public enum View {
    ALL,
    CURRENT_VERSION,
    OTHER_VERSIONS,
    PENDING,
    NEEDS_REVIEW
  }

  public record Subscriber(
      UUID subscriptionId,
      UUID accountId,
      String accountName,
      UUID planId,
      long productVersionNumber,
      SubscriptionStatus status,
      @com.hiveapp.shared.money.ExactDecimal BigDecimal retainedTotal,
      String currency,
      BillingCycle billingCycle,
      Instant periodEnd) {}
}
