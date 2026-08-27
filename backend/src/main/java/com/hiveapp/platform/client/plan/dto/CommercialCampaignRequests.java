package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignAudienceMode;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class CommercialCampaignRequests {

    private CommercialCampaignRequests() {}

    public record Create(
            @NotBlank @Size(max = 180) String name,
            @Size(max = 1000) String description,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @NotNull CommercialCampaignSource source,
            @NotBlank @Size(max = 500) String reason,
            @NotNull @Valid Audience audience) {
        public Create {
            name = trim(name); description = trimToNull(description); reason = trim(reason);
        }
    }

    public record Update(
            @NotNull @PositiveOrZero Long version,
            @NotBlank @Size(max = 180) String name,
            @Size(max = 1000) String description,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @NotNull CommercialCampaignSource source,
            @NotBlank @Size(max = 500) String reason,
            @NotNull @Valid Audience audience) {
        public Update {
            name = trim(name); description = trimToNull(description); reason = trim(reason);
        }
    }

    public record Audience(
            @NotNull CommercialCampaignAudienceMode mode,
            @Size(max = 10000) Set<@NotNull UUID> explicitAccountIds,
            UUID segmentId,
            UUID segmentActivationId) {
        public Audience {
            explicitAccountIds = explicitAccountIds == null
                    ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(explicitAccountIds));
        }
    }

    public record Duplicate(
            @NotNull @PositiveOrZero Long version,
            @NotBlank @Size(max = 180) String name,
            @NotBlank @Size(max = 500) String reason) {
        public Duplicate { name = trim(name); reason = trim(reason); }
    }

    public record VersionReason(
            @NotNull @PositiveOrZero Long version,
            @NotBlank @Size(max = 500) String reason) {
        public VersionReason { reason = trim(reason); }
    }

    public record Schedule(
            @NotNull @PositiveOrZero Long version,
            @NotBlank @Size(max = 500) String reason,
            @NotBlank @Size(max = 2048) String previewToken) {
        public Schedule { reason = trim(reason); previewToken = trim(previewToken); }
    }

    public record ReassignOwner(
            @NotNull @PositiveOrZero Long version,
            @NotNull UUID ownerAdminUserId,
            @NotBlank @Size(max = 500) String reason) {
        public ReassignOwner { reason = trim(reason); }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }
}
