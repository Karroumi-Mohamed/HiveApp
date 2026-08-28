package com.hiveapp.platform.client.plan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hiveapp.platform.client.plan.domain.constant.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class CommercialOfferRequests {
  private CommercialOfferRequests() {}

  public record Create(
      @NotBlank @Size(max = 180) String name,
      @Size(max = 1000) String description,
      @NotNull UUID campaignId,
      @NotNull Instant startsAt,
      @NotNull Instant endsAt,
      @NotNull CommercialOfferDiscovery discovery,
      @NotNull CommercialOfferAcceptance acceptance,
      @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Size(max = 64) String customerCode,
      @Positive Long globalLimit,
      @Positive Long perAccountLimit,
      @NotNull @Valid CommercialOfferSelection selection,
      @NotNull @Valid CommercialOfferEffectSnapshot effects) {}

  public record Update(
      @NotNull @PositiveOrZero Long version,
      @NotBlank @Size(max = 180) String name,
      @Size(max = 1000) String description,
      @NotNull Instant startsAt,
      @NotNull Instant endsAt,
      @NotNull @Valid CommercialOfferSelection selection,
      @NotNull @Valid CommercialOfferEffectSnapshot effects,
      @Valid LineageTerms lineageTerms) {}

  public enum CustomerCodeChangeMode {
    KEEP,
    REPLACE,
    REMOVE
  }

  public record CustomerCodeChange(
      @NotNull CustomerCodeChangeMode mode,
      @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Size(max = 64) String value) {}

  public record LineageTerms(
      @NotNull CommercialOfferDiscovery discovery,
      @NotNull CommercialOfferAcceptance acceptance,
      @Positive Long globalLimit,
      @Positive Long perAccountLimit,
      @NotNull @Valid CustomerCodeChange customerCodeChange) {}

  public record VersionReason(
      @NotNull @PositiveOrZero Long version, @NotBlank @Size(max = 500) String reason) {}

  public record Duplicate(
      @NotNull @PositiveOrZero Long version,
      @NotBlank @Size(max = 180) String name,
      @NotBlank @Size(max = 500) String reason) {}

  public record Publish(
      @NotNull @PositiveOrZero Long version,
      @NotBlank @Size(max = 500) String reason,
      @NotBlank @Size(max = 2048) String previewToken) {}

  public record ReassignOwner(
      @NotNull @PositiveOrZero Long version,
      @NotNull UUID ownerAdminUserId,
      @NotBlank @Size(max = 500) String reason) {}

  public record ResolveCode(
      @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(max = 64)
          String code) {}

  public record Preview(@Size(max = 2048) String discoveryToken) {}

  public record ClientAccept(@NotBlank @Size(max = 2048) String previewToken) {}

  public record OperatorAccept(
      @NotBlank @Size(max = 2048) String previewToken,
      @NotBlank @Size(max = 500) String reason) {}

  public record RedemptionIdentityResolution(
      @NotNull @Size(min = 1, max = 100) Set<UUID> redemptionIds) {
    public RedemptionIdentityResolution {
      redemptionIds = redemptionIds == null ? null : Set.copyOf(redemptionIds);
    }
  }
}
