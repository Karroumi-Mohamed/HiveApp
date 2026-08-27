package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentKind;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentProductType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentSource;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Requests for the closed, operational Account-segment surface. */
public final class CommercialSegmentRequests {

    private CommercialSegmentRequests() {}

    public record Create(
            @NotBlank @Size(max = 180) String name,
            @Size(max = 1000) String description,
            @NotNull CommercialSegmentKind kind,
            @NotNull CommercialSegmentSource source,
            @NotBlank @Size(max = 500) String reason,
            @NotNull @Valid Definition definition
    ) {
        public Create {
            name = trim(name);
            description = trimToNull(description);
            reason = trim(reason);
        }
    }

    public record Update(
            @NotNull @PositiveOrZero Long version,
            @NotBlank @Size(max = 180) String name,
            @Size(max = 1000) String description,
            @NotNull CommercialSegmentKind kind,
            @NotNull CommercialSegmentSource source,
            @NotBlank @Size(max = 500) String reason,
            @NotNull @Valid Definition definition
    ) {
        public Update {
            name = trim(name);
            description = trimToNull(description);
            reason = trim(reason);
        }
    }

    public record Definition(
            @Size(max = 2000) Set<UUID> explicitAccountIds,
            @Valid Criteria criteria
    ) {
        public Definition {
            explicitAccountIds = explicitAccountIds == null ? Set.of() : Set.copyOf(explicitAccountIds);
        }
    }

    /** AND across populated fields, OR within each selected-value set. */
    public record Criteria(
            @Size(max = 100) Set<UUID> currentPlanRevisionIds,
            @Size(max = 20) Set<SubscriptionStatus> subscriptionStatuses,
            @Size(max = 20) Set<@Size(min = 3, max = 3) String> currencyCodes,
            @Size(max = 10) Set<BillingCycle> billingCycles,
            Instant accountCreatedFrom,
            Instant accountCreatedUntil,
            @Size(max = 100) Set<@Valid ProductHolding> productHoldings
    ) {
        public Criteria {
            currentPlanRevisionIds = copy(currentPlanRevisionIds);
            subscriptionStatuses = copy(subscriptionStatuses);
            currencyCodes = currencyCodes == null ? Set.of() : currencyCodes.stream()
                    .map(CommercialSegmentRequests::trim)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            billingCycles = copy(billingCycles);
            productHoldings = copy(productHoldings);
        }
    }

    public record ProductHolding(
            @NotNull CommercialSegmentProductType type,
            @NotBlank @Size(max = 100) String code
    ) {
        public ProductHolding { code = trim(code); }
    }

    public record Duplicate(
            @NotNull @PositiveOrZero Long version,
            @NotBlank @Size(max = 180) String name,
            @NotBlank @Size(max = 500) String reason
    ) {
        public Duplicate { name = trim(name); reason = trim(reason); }
    }

    public record VersionReason(
            @NotNull @PositiveOrZero Long version,
            @NotBlank @Size(max = 500) String reason
    ) {
        public VersionReason { reason = trim(reason); }
    }

    public record Activation(
            @NotNull @PositiveOrZero Long version,
            @NotBlank @Size(max = 500) String reason,
            @NotBlank @Size(max = 2048) String previewToken
    ) {
        public Activation { reason = trim(reason); previewToken = trim(previewToken); }
    }

    public record ReassignOwner(
            @NotNull @PositiveOrZero Long version,
            @NotNull UUID ownerAdminUserId,
            @NotBlank @Size(max = 500) String reason
    ) {
        public ReassignOwner { reason = trim(reason); }
    }

    private static <T> Set<T> copy(Set<T> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }
}
