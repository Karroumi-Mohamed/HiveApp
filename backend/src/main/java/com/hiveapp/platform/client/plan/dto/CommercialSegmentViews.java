package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentAction;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentBlocker;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentCreationReason;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentKind;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentProductType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.shared.api.PageResponse;
import com.hiveapp.shared.audit.domain.AuditOutcome;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CommercialSegmentViews {

    private CommercialSegmentViews() {}

    public record Summary(
            UUID id,
            String code,
            String name,
            CommercialSegmentStatus status,
            CommercialSegmentKind kind,
            int configuredAccountCount,
            Integer latestActivationAccountCount,
            UUID lineageId,
            int revisionNumber,
            CommercialSegmentCreationReason creationReason,
            long version,
            Instant createdAt,
            Instant updatedAt,
            List<CommercialSegmentAction> availableActions,
            Map<CommercialSegmentAction, List<CommercialSegmentBlocker>> blockedActions,
            boolean ownerIdentityRestricted,
            boolean audienceIdentityRestricted
    ) {
        public Summary {
            availableActions = List.copyOf(availableActions);
            blockedActions = blockedActions.entrySet().stream().collect(
                    java.util.stream.Collectors.toUnmodifiableMap(
                            Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        }
    }

    public record Detail(
            Summary summary,
            String description,
            String reason,
            Definition definition,
            UUID sourceSegmentId
    ) {
        public UUID id() { return summary.id(); }
    }

    public record Definition(
            Set<UUID> explicitAccountIds,
            Criteria criteria
    ) {
        public Definition {
            explicitAccountIds = Set.copyOf(explicitAccountIds == null ? Set.of() : explicitAccountIds);
        }
    }

    public record Criteria(
            Set<UUID> currentPlanRevisionIds,
            Set<SubscriptionStatus> subscriptionStatuses,
            Set<String> currencyCodes,
            Set<BillingCycle> billingCycles,
            Instant accountCreatedFrom,
            Instant accountCreatedUntil,
            Set<ProductHolding> productHoldings,
            String semantics
    ) {
        public Criteria {
            currentPlanRevisionIds = Set.copyOf(currentPlanRevisionIds);
            subscriptionStatuses = Set.copyOf(subscriptionStatuses);
            currencyCodes = Set.copyOf(currencyCodes);
            billingCycles = Set.copyOf(billingCycles);
            productHoldings = Set.copyOf(productHoldings);
        }
    }

    public record ProductHolding(CommercialSegmentProductType type, String code) {}

    /** Cheap count-only evaluation: no identity sample and no activation evidence are produced. */
    public record Count(
            UUID segmentId,
            long criteriaVersion,
            Instant evaluatedAt,
            long totalAccounts,
            int activationAccountLimit,
            boolean withinActivationLimit
    ) {}

    /** Ordinary preview deliberately carries no Account name, slug, or owner identity. */
    public record AudienceReference(UUID accountId) {}

    /** Narrow identity surface; callers need read_sample_identities/read_activation_identities. */
    public record AudienceIdentity(
            UUID accountId,
            String accountName,
            String accountSlug,
            String ownerEmail,
            boolean active
    ) {}

    public record Preview(
            UUID segmentId,
            long criteriaVersion,
            Instant evaluatedAt,
            Instant expiresAt,
            String previewToken,
            long totalAccounts,
            int activationAccountLimit,
            boolean activatable,
            List<CommercialSegmentBlocker> blockers,
            List<AudienceReference> sample
    ) {
        public Preview { blockers = List.copyOf(blockers); sample = List.copyOf(sample); }
    }

    public record IdentitySample(
            UUID segmentId,
            long criteriaVersion,
            Instant evaluatedAt,
            long totalAccounts,
            List<AudienceIdentity> sample
    ) {
        public IdentitySample { sample = List.copyOf(sample); }
    }

    public record Activation(
            UUID id,
            int activationNumber,
            UUID segmentId,
            UUID actorUserId,
            Instant evaluatedAt,
            Instant evidenceExpiresAt,
            long criteriaVersion,
            int affectedAccountCount,
            String reason,
            Instant recordedAt
    ) {}

    public record ActivationAudience(
            UUID segmentId,
            UUID activationId,
            int activationNumber,
            int immutableAccountCount,
            PageResponse<AudienceReference> accounts
    ) {}

    public record ActivationIdentityAudience(
            UUID segmentId,
            UUID activationId,
            int activationNumber,
            int immutableAccountCount,
            PageResponse<AudienceIdentity> accounts
    ) {}

    public record Owner(
            UUID segmentId,
            UUID adminUserId,
            UUID userId,
            String email,
            String username,
            String displayName,
            boolean active
    ) {}

    public record Revision(
            UUID id,
            String code,
            CommercialSegmentStatus status,
            int revisionNumber,
            UUID sourceSegmentId,
            long version,
            Instant createdAt
    ) {}

    public record Comparison(
            UUID sourceSegmentId,
            UUID comparedSegmentId,
            boolean sameLineage,
            boolean directSuccessor,
            Set<String> changedFields,
            Detail source,
            Detail compared
    ) {
        public Comparison { changedFields = Set.copyOf(changedFields); }
    }

    public record History(
            UUID id,
            String action,
            AuditOutcome outcome,
            UUID actorUserId,
            String actorEmail,
            String reason,
            Instant occurredAt
    ) {}
}
