package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

/** A frozen content audience. It never supplies prices or an ordinary purchase selection. */
public final class PlanVersionRolloutModels {
  private PlanVersionRolloutModels() {}

  public enum Scope {
    VERSION,
    FAMILY
  }

  public enum Audience {
    SELECTED,
    FILTERED,
    ALL
  }

  public record Request(
      @NotNull UUID sourcePlanId,
      @NotNull Scope scope,
      @NotNull Audience audience,
      @Size(max = 10000) List<@NotNull UUID> accountIds,
      @Size(max = 10000) List<@NotNull UUID> excludedAccountIds,
      Set<SubscriptionStatus> statuses,
      @Size(max = 200) String search,
      @Pattern(regexp = "[A-Z]{3}") String currency,
      BillingCycle billingCycle,
      @NotNull @Valid PlanVersionApplicationModels.Request application) {
    public Request {
      accountIds = accountIds == null ? List.of() : List.copyOf(accountIds);
      excludedAccountIds = excludedAccountIds == null ? List.of() : List.copyOf(excludedAccountIds);
      statuses =
          statuses == null || statuses.isEmpty()
              ? Set.of(SubscriptionStatus.ACTIVE)
              : Set.copyOf(statuses);
    }
  }

  public record Definition(UUID targetPlanId, Request request) {}

  public record Assessment(
      PlanVersionApplicationModels.Reviewed reviewed,
      List<SubscriptionChangeConflict> conflicts,
      List<EffectiveQuotaLimit> beforeLimits,
      List<EffectiveQuotaLimit> afterLimits,
      List<String> removedFeatures) {
    public Assessment {
      conflicts = List.copyOf(conflicts);
      beforeLimits = List.copyOf(beforeLimits);
      afterLimits = List.copyOf(afterLimits);
      removedFeatures = List.copyOf(removedFeatures);
    }
  }

  public record Summary(
      UUID id,
      UUID familyId,
      UUID targetPlanId,
      SubscriptionChangeJobStatus status,
      Map<SubscriptionChangeJobItemStatus, Long> counts,
      Instant createdAt,
      Instant evaluatedAt,
      Instant executeAt,
      Instant completedAt,
      UUID requestedByUserId,
      String reason,
      long version) {}

  public record ConflictGroup(String primaryReason, long accounts) {}

  public record Detail(
      Summary summary,
      Definition definition,
      List<ConflictGroup> conflicts,
      boolean reviewInvalidated,
      String previewToken,
      Instant expiresAt) {}

  public record Confirm(@NotBlank String previewToken, boolean applyReadyOnly) {}

  public record Impact(
      long sourceVersion,
      long targetVersion,
      @com.hiveapp.shared.money.ExactDecimal java.math.BigDecimal retainedTotal,
      String currency,
      List<SubscriptionChangeConflict> conflicts,
      List<EffectiveQuotaLimit> beforeLimits,
      List<EffectiveQuotaLimit> afterLimits,
      List<String> removedFeatures) {}

  /**
   * Identity remains separately permission-gated; this result has no owner email or account name.
   */
  public record Item(
      UUID id,
      SubscriptionChangeJobItemStatus status,
      UUID frozenSubscriptionId,
      Impact impact,
      List<SubscriptionChangeConflict> executionConflicts,
      UUID operationId,
      String outcomeCode,
      int attempts,
      Instant nextAttemptAt,
      Instant completedAt) {}
}
