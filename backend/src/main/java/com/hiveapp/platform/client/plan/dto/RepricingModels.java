package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.shared.money.ExactDecimal;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Closed, price-only commands; target snapshots and amounts are always server-owned. */
public final class RepricingModels {
  private RepricingModels() {}

  public enum Audience {
    SELECTED,
    TARIFF_HOLDERS,
    FILTERED,
    SEGMENT
  }

  public enum State {
    READY,
    PENDING,
    AWAITING_PAYMENT,
    APPLIED,
    CONFLICT,
    CANCELLED
  }

  public enum Delivery {
    NOT_REQUESTED,
    PENDING,
    SENDING,
    SENT,
    SUPPRESSED,
    FAILED,
    CANCELLED
  }

  public record Request(
      @NotNull UUID sourcePriceId,
      @NotNull UUID targetPriceId,
      @NotNull Audience audience,
      @Size(max = 500) List<@NotNull UUID> accountIds,
      @Size(max = 500) List<@NotNull UUID> excludedAccountIds,
      UUID planId,
      SubscriptionStatus subscriptionStatus,
      UUID segmentId,
      Instant notBefore,
      boolean email,
      @NotBlank @Size(max = 2000) String reason) {
    public Request {
      accountIds = accountIds == null ? List.of() : List.copyOf(accountIds);
      excludedAccountIds = excludedAccountIds == null ? List.of() : List.copyOf(excludedAccountIds);
    }
  }

  public record Confirm(@NotBlank @Size(max = 4096) String previewToken) {}

  public record Reason(@NotBlank @Size(max = 2000) String reason) {}

  public record IdentityRequest(@NotEmpty @Size(max = 100) List<@NotNull UUID> itemIds) {}

  public record Identity(UUID itemId, UUID accountId, String accountName) {}

  public record Summary(
      UUID id,
      String status,
      String productName,
      String currencyCode,
      String billingCycle,
      @ExactDecimal BigDecimal sourcePrice,
      @ExactDecimal BigDecimal targetPrice,
      int targetCount,
      long readyCount,
      long pendingCount,
      long appliedCount,
      long conflictCount,
      long cancelledCount,
      long awaitingPaymentCount,
      Instant createdAt) {}

  public record Detail(Summary summary, Request request, Instant confirmedAt) {}

  public record Preview(
      Summary summary, String previewToken, Instant expiresAt, List<Item> sample) {}

  public record Item(
      UUID id,
      State status,
      String blocker,
      int quantity,
      @ExactDecimal BigDecimal oldUnitPrice,
      @ExactDecimal BigDecimal newUnitPrice,
      @ExactDecimal BigDecimal oldTotal,
      @ExactDecimal BigDecimal newTotal,
      String currencyCode,
      String billingCycle,
      Instant effectiveAt,
      UUID operationId,
      Delivery delivery,
      int emailAttempts) {}

  public record Notice(UUID id, String productName, Item change, boolean read, Instant createdAt) {}
}
