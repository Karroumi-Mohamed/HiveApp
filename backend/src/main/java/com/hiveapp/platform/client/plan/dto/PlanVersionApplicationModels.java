package com.hiveapp.platform.client.plan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A content instruction is deliberately not a subscription purchase request. */
public final class PlanVersionApplicationModels {
  private PlanVersionApplicationModels() {}

  public enum Timing {
    NOW,
    AT_RENEWAL,
    AT_DATE
  }

  public enum Outcome {
    READY,
    WAITING,
    APPLIED,
    CONFLICT
  }

  public record Request(
      @NotNull Timing timing, Instant notBefore, @NotBlank @Size(max = 2000) String reason) {
    public Request {
      if (timing == null || (timing == Timing.AT_DATE) != (notBefore != null))
        throw new IllegalArgumentException("Only a fixed-date application requires notBefore.");
    }
  }

  /** Stored only server-side. Each Account keeps its own reviewed terms and purchases. */
  public record Reviewed(
      UUID accountId,
      UUID sourceSubscriptionId,
      UUID sourcePlanId,
      UUID targetPlanId,
      UUID financialTermsId,
      UUID previousEvidenceId,
      SubscriptionEntitlementSnapshot before,
      SubscriptionEntitlementSnapshot target,
      SubscriptionOverrides overrides,
      @com.hiveapp.shared.money.ExactDecimal BigDecimal total,
      String currency,
      Instant periodStart,
      Instant periodEnd,
      Request request) {}

  public record Preview(
      UUID accountId,
      UUID sourcePlanId,
      UUID targetPlanId,
      long sourceVersion,
      long targetVersion,
      Request request,
      Instant plannedAt,
      @com.hiveapp.shared.money.ExactDecimal BigDecimal retainedTotal,
      String currency,
      List<EffectiveQuotaLimit> beforeLimits,
      List<EffectiveQuotaLimit> afterLimits,
      List<String> removedFeatures,
      List<SubscriptionChangeConflict> conflicts,
      String previewToken,
      Instant expiresAt) {
    public boolean ready() {
      return conflicts.isEmpty();
    }
  }

  public record ApplyNow(
      @NotNull UUID commandId, @NotNull @Valid Request request, @NotBlank String previewToken) {}

  public record Result(
      Outcome outcome,
      UUID operationId,
      UUID evidenceId,
      Instant effectiveAt,
      List<SubscriptionChangeConflict> conflicts) {}
}
