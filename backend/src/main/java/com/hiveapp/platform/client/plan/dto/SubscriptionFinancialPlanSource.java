package com.hiveapp.platform.client.plan.dto;

import java.util.Objects;
import java.util.UUID;

/** Original commercial Plan owner when a later content version retains its financial terms. */
public record SubscriptionFinancialPlanSource(
    UUID planId, String planCode, long productVersionNumber) {
  public SubscriptionFinancialPlanSource {
    Objects.requireNonNull(planId, "Financial Plan identity is required");
    if (planCode == null || planCode.isBlank() || productVersionNumber < 1) {
      throw new IllegalArgumentException("Financial Plan provenance is incomplete");
    }
  }
}
