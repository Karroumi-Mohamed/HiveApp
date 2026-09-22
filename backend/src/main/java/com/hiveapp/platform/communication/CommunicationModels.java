package com.hiveapp.platform.communication;

import com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class CommunicationModels {
  private CommunicationModels() {}

  public enum Kind {
    NOTICE,
    WARNING,
    ACTION,
    OFFER,
    /** Read-only compatibility for previously stored publications; never a conversation. */
    MESSAGE
  }

  public enum Topic {
    GENERAL,
    ACCOUNT,
    BILLING,
    COLLABORATION,
    COMMERCIAL,
    OPERATIONS,
    TASKS
  }

  public enum Audience {
    ACCOUNT,
    MEMBER,
    PLATFORM,
    OPERATOR
  }

  public enum Purpose {
    SERVICE,
    MARKETING
  }

  public enum State {
    DRAFT,
    PUBLISHED,
    CANCELLED
  }

  public enum Interaction {
    READ,
    ACKNOWLEDGE,
    ARCHIVE,
    RESTORE
  }

  public record Draft(
      @NotNull Kind kind,
      @NotNull Purpose purpose,
      @NotBlank @Size(max = 160) String messageTitle,
      @NotBlank @Size(max = 10000) String messageBody,
      @NotEmpty @Size(max = 500) List<@NotNull UUID> accountIds,
      boolean email,
      @AssertFalse(message = "Notifications are one-way; replies are not supported.")
          boolean replies,
      Instant availableAt,
      Instant expiresAt,
      UUID offerId) {}

  public record Edit(@NotNull Long version, @NotNull @jakarta.validation.Valid Draft draft) {}

  public record Command(@NotNull Long version, @NotBlank @Size(max = 2000) String reason) {}

  public record Preference(boolean marketingInApp, boolean marketingEmail) {}

  public record Publication(
      UUID id,
      long version,
      Kind kind,
      Purpose purpose,
      String messageTitle,
      String messageBody,
      List<UUID> accountIds,
      boolean email,
      boolean replies,
      Instant availableAt,
      Instant expiresAt,
      State state,
      Instant createdAt,
      UUID offerId) {}

  public record Item(
      UUID id,
      Kind kind,
      Purpose purpose,
      String messageTitle,
      String messageBody,
      String source,
      String sourceState,
      String actionPath,
      Instant availableAt,
      Instant expiresAt,
      boolean read,
      boolean acknowledged,
      boolean archived,
      boolean canAcknowledge,
      boolean canArchive,
      Topic topic,
      String eventType,
      UUID resourceId,
      Audience audience,
      boolean resolved,
      String senderName) {}

  public record Recipient(
      UUID id,
      UUID accountId,
      String accountName,
      boolean visible,
      Delivery emailDelivery,
      int emailAttempts,
      long readers,
      long acknowledgements) {}

  public record MemberChoice(UUID id, String name) {}

  public record InternalNotice(
      @NotNull UUID commandId,
      @NotBlank @Size(max = 160) String messageTitle,
      @NotBlank @Size(max = 10000) String messageBody,
      @NotEmpty @Size(max = 100) List<@NotNull UUID> memberIds) {}

  public record InboxSummary(long unread) {}

  public record InternalNoticeResult(UUID commandId, int recipients) {}

  public record SentNotice(UUID commandId, String messageTitle, String messageBody,
      Instant createdAt, int recipients, long delivered, long failed, long pending) {}

  public record NotificationSetting(
      @NotNull Topic topic, boolean inAppEnabled, boolean emailEnabled) {}

  public record AccountChoice(UUID id, String name) {}
}
