package com.hiveapp.platform.client.plan.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PlanContentNoticeModels {
  private PlanContentNoticeModels() {}

  public enum Policy {
    IN_APP,
    EMAIL,
    EMAIL_REQUIRED
  }

  public enum State {
    SCHEDULED,
    APPLIED,
    CONFLICT,
    CANCELLED
  }

  public record Retry(
      @jakarta.validation.constraints.NotEmpty @jakarta.validation.constraints.Size(max = 100)
          List<@jakarta.validation.constraints.NotNull UUID> noticeIds,
      @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 2000)
          String reason) {}

  public record Delivery(
      UUID id,
      State state,
      RepricingModels.Delivery emailDelivery,
      int attempts,
      Instant createdAt,
      boolean required) {}

  /**
   * No internal operator reasons, email addresses, marketing evidence or other Account identities.
   */
  public record Notice(
      UUID id,
      String planName,
      long sourceVersion,
      long targetVersion,
      State state,
      PlanVersionApplicationModels.Timing timing,
      Instant plannedAt,
      Instant effectiveAt,
      List<EffectiveQuotaLimit> beforeLimits,
      List<EffectiveQuotaLimit> afterLimits,
      List<String> removedFeatures,
      boolean financialTermsRetained,
      boolean read,
      Instant createdAt) {}
}
