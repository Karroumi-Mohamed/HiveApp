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
    MESSAGE
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
      boolean replies,
      Instant availableAt,
      Instant expiresAt) {}

  public record Edit(@NotNull Long version, @NotNull @jakarta.validation.Valid Draft draft) {}

  public record Command(@NotNull Long version, @NotBlank @Size(max = 2000) String reason) {}

  public record ReplyRequest(
      @NotNull UUID commandId, @NotBlank @Size(max = 4000) String replyBody) {}

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
      Instant createdAt) {}

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
      boolean canReply,
      boolean closed) {}

  public record Reply(
      UUID id, UUID commandId, boolean fromAdmin, String replyBody, Instant createdAt) {}

  public record Recipient(
      UUID id,
      UUID accountId,
      String accountName,
      boolean visible,
      Delivery emailDelivery,
      int emailAttempts,
      long readers,
      long acknowledgements,
      long replies,
      boolean closed) {}

  public record AccountChoice(UUID id, String name) {}
}
