package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobItemStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.shared.money.ExactDecimal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SubscriptionChangeJobModels {
  private SubscriptionChangeJobModels() {}

  public record PreviewRequest(
      @NotEmpty @Size(max = 500) List<@NotNull UUID> accountIds,
      @NotNull @Valid SubscriptionChangeRequest selection,
      @NotBlank @Size(max = 2000) String reason,
      Instant executeAt) {
    public PreviewRequest {
      accountIds = accountIds == null ? List.of() : List.copyOf(accountIds);
    }
  }

  public record ConfirmRequest(@NotBlank @Size(max = 4096) String previewToken) {}

  public record CancelRequest(@NotBlank @Size(max = 2000) String reason) {}

  public record RetryRequest(@NotBlank @Size(max = 2000) String reason) {}

  public record IdentityRequest(
      @NotEmpty @Size(max = 100) List<@NotNull UUID> resultIds) {
    public IdentityRequest {
      resultIds = resultIds == null ? List.of() : List.copyOf(resultIds);
    }
  }

  public record Assessment(
      UUID subscriptionId,
      long expectedSubscriptionVersion,
      String currentPlanCode,
      String targetPlanCode,
      @ExactDecimal BigDecimal currentPrice,
      @ExactDecimal BigDecimal targetPrice,
      String currencyCode,
      SubscriptionChangeTiming timing,
      Instant effectiveAt,
      List<SubscriptionChangeConflict> conflicts) {
    public Assessment {
      conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }
  }

  public record Preview(
      UUID jobId,
      long expectedVersion,
      SubscriptionChangeJobStatus status,
      int targetCount,
      int readyCount,
      int conflictCount,
      Instant executeAt,
      Instant evaluatedAt,
      Instant expiresAt,
      String previewToken,
      List<Item> sample) {
    public Preview {
      sample = sample == null ? List.of() : List.copyOf(sample);
    }
  }

  public record Summary(
      UUID id,
      SubscriptionChangeJobStatus status,
      int targetCount,
      int readyCount,
      int appliedCount,
      int pendingCount,
      int awaitingPaymentCount,
      int conflictCount,
      int failedCount,
      int cancelledCount,
      Instant executeAt,
      Instant startedAt,
      Instant completedAt,
      UUID requestedByUserId,
      String reason,
      int retryCount,
      UUID lastRetriedByUserId,
      Instant lastRetriedAt,
      String lastRetryReason,
      long version,
      Instant createdAt) {}

  public record Detail(Summary summary, SubscriptionChangeRequest selection) {}

  public record Item(
      UUID id,
      SubscriptionChangeJobItemStatus status,
      Assessment assessment,
      UUID subscriptionOperationId,
      String outcomeCode,
      int attempts,
      Instant lastAttemptAt,
      Instant completedAt) {}

  public record Identity(UUID itemId, UUID accountId, String accountName) {}
}
