package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyEffectType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyProductType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicySource;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyTargetKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Request contracts grouped under one namespace to keep the commercial policy API cohesive. */
public final class CommercialPolicyRequests {

    private CommercialPolicyRequests() {}

    public record Create(
            @NotBlank @Size(max = 180) String name,
            @Size(max = 1000) String description,
            @NotNull Instant effectiveFrom,
            Instant effectiveUntil,
            @NotNull CommercialPolicySource source,
            @PositiveOrZero int priority,
            @NotBlank @Size(max = 500) String reason,
            @Size(max = 180) String approvalReference,
            @Size(max = 180) String contractReference,
            @NotNull @Valid Target target,
            @NotEmpty @Size(max = 50) List<@Valid Effect> effects
    ) {
        public Create {
            name = trim(name);
            description = trimToNull(description);
            reason = trim(reason);
            approvalReference = trimToNull(approvalReference);
            contractReference = trimToNull(contractReference);
            effects = effects == null ? null : List.copyOf(effects);
        }
    }

    public record Update(
            @NotNull @PositiveOrZero Long version,
            @NotBlank @Size(max = 180) String name,
            @Size(max = 1000) String description,
            @NotNull Instant effectiveFrom,
            Instant effectiveUntil,
            @NotNull CommercialPolicySource source,
            @PositiveOrZero int priority,
            @NotBlank @Size(max = 500) String reason,
            @Size(max = 180) String approvalReference,
            @Size(max = 180) String contractReference,
            @NotNull @Valid Target target,
            @NotEmpty @Size(max = 50) List<@Valid Effect> effects
    ) {
        public Update {
            name = trim(name);
            description = trimToNull(description);
            reason = trim(reason);
            approvalReference = trimToNull(approvalReference);
            contractReference = trimToNull(contractReference);
            effects = effects == null ? null : List.copyOf(effects);
        }
    }

    public record Target(
            @NotNull CommercialPolicyTargetKind kind,
            UUID accountId,
            @Size(max = 1000) Set<UUID> accountIds,
            UUID planRevisionId,
            @Size(max = 100) String segmentReference
    ) {
        public Target {
            accountIds = accountIds == null ? Set.of() : Set.copyOf(accountIds);
            segmentReference = trimToNull(segmentReference);
        }
    }

    public record Effect(
            @NotNull CommercialPolicyEffectType type,
            CommercialPolicyProductType productType,
            UUID productRevisionId,
            @Size(max = 150) String featureCode,
            @Size(max = 100) String quotaResource,
            Long quantityDelta,
            @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @Size(max = 3) String currencyCode,
            BillingCycle billingCycle,
            @Digits(integer = 3, fraction = 4) BigDecimal percentage,
            @Digits(integer = 15, fraction = 4) BigDecimal maximumAmount,
            @Size(max = 3) String maximumCurrencyCode
    ) {
        public Effect {
            featureCode = trimToNull(featureCode);
            quotaResource = trimToNull(quotaResource);
            currencyCode = trimToNull(currencyCode);
            maximumCurrencyCode = trimToNull(maximumCurrencyCode);
        }
    }

    public record VersionReason(
            @NotNull @PositiveOrZero Long version,
            @NotBlank @Size(max = 500) String reason
    ) {
        public VersionReason { reason = trim(reason); }
    }

    public record Duplicate(
            @NotNull @PositiveOrZero Long version,
            @NotBlank @Size(max = 180) String name,
            @NotBlank @Size(max = 500) String reason
    ) {
        public Duplicate {
            name = trim(name);
            reason = trim(reason);
        }
    }

    public record Activation(
            @NotNull @PositiveOrZero Long version,
            @NotBlank @Size(max = 500) String reason,
            @NotBlank @Size(max = 2048) String activationPreviewToken
    ) {
        public Activation {
            reason = trim(reason);
            activationPreviewToken = trim(activationPreviewToken);
        }
    }

    public record ReassignOwner(
            @NotNull @PositiveOrZero Long version,
            @NotNull UUID ownerAdminUserId,
            @NotBlank @Size(max = 500) String reason
    ) {
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
