package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyAction;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyBlocker;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyCreationReason;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyEffectType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyExecutionBlocker;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyProductType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicySource;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyStatus;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyTargetKind;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentKind;
import com.hiveapp.shared.api.PageResponse;
import com.hiveapp.shared.audit.domain.AuditOutcome;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Read contracts for ordinary rows, detail, evidence, and separately authorized identity. */
public final class CommercialPolicyViews {

    private CommercialPolicyViews() {}

    public record Summary(
            UUID id,
            String code,
            String name,
            CommercialPolicyStatus status,
            CommercialPolicyTargetKind targetKind,
            String targetLabel,
            int configuredTargetCount,
            int effectCount,
            Integer latestAffectedAccountCount,
            CommercialPolicySource source,
            int priority,
            Instant effectiveFrom,
            Instant effectiveUntil,
            UUID lineageId,
            int revisionNumber,
            CommercialPolicyCreationReason creationReason,
            long version,
            Instant createdAt,
            Instant updatedAt,
            List<CommercialPolicyAction> availableActions,
            List<CommercialPolicyBlocker> blockers,
            boolean ownerIdentityRestricted,
            boolean executionSupported,
            List<CommercialPolicyExecutionBlocker> executionBlockers
    ) {
        public Summary {
            availableActions = List.copyOf(availableActions);
            blockers = List.copyOf(blockers);
            executionBlockers = List.copyOf(executionBlockers);
        }
    }

    public record Detail(
            Summary summary,
            String description,
            String reason,
            String approvalReference,
            String contractReference,
            Target target,
            List<Effect> effects,
            UUID sourcePolicyId
    ) {
        public Detail { effects = List.copyOf(effects); }
        public UUID id() { return summary.id(); }
    }

    public record Target(
            CommercialPolicyTargetKind kind,
            UUID accountId,
            String accountName,
            Set<UUID> accountIds,
            UUID planRevisionId,
            String planCode,
            String planName,
            String segmentReference
    ) {
        public Target { accountIds = Set.copyOf(accountIds == null ? Set.of() : accountIds); }
    }

    /** Policy-scoped chooser row: executable Segment metadata without Account identities. */
    public record SegmentChoice(
            UUID id,
            String code,
            String name,
            int revisionNumber,
            CommercialSegmentKind kind,
            int immutableAccountCount
    ) {}

    public record Effect(
            UUID id,
            int order,
            CommercialPolicyEffectType type,
            CommercialPolicyProductType productType,
            UUID productRevisionId,
            String productCode,
            String featureCode,
            String quotaResource,
            Long quantityDelta,
            BigDecimal amount,
            String currencyCode,
            BillingCycle billingCycle,
            BigDecimal percentage,
            BigDecimal maximumAmount,
            String maximumCurrencyCode,
            int precedenceClass,
            boolean canOverridePlatformHardLimits
    ) {}

    public record AudienceAccount(
            UUID id,
            String name,
            String slug,
            boolean active
    ) {}

    public record AudiencePreview(
            UUID policyId,
            long expectedVersion,
            CommercialPolicyTargetKind targetKind,
            long totalAccounts,
            int activationAccountLimit,
            boolean withinActivationLimit,
            PageResponse<AudienceAccount> accounts,
            List<CommercialPolicyBlocker> blockers
    ) {
        public AudiencePreview { blockers = List.copyOf(blockers); }
    }

    public record ActivationPreview(
            UUID policyId,
            long expectedVersion,
            long catalogRevision,
            String registryVersion,
            Instant evaluatedAt,
            Instant expiresAt,
            String previewToken,
            boolean activatable,
            List<CommercialPolicyBlocker> blockers,
            long affectedAccountCount,
            List<AudienceAccount> sampleAccounts,
            UUID policyRevisionToEnd,
            boolean executionSupported,
            List<CommercialPolicyExecutionBlocker> executionBlockers
    ) {
        public ActivationPreview {
            blockers = List.copyOf(blockers);
            sampleAccounts = List.copyOf(sampleAccounts);
            executionBlockers = List.copyOf(executionBlockers);
        }
    }

    public record Activation(
            UUID id,
            int activationNumber,
            UUID policyId,
            UUID actorUserId,
            Instant evaluatedAt,
            Instant evidenceExpiresAt,
            long catalogRevision,
            String registryVersion,
            int affectedAccountCount,
            String reason,
            Instant recordedAt
    ) {}

    public record ActivationAudience(
            UUID policyId,
            UUID activationId,
            int activationNumber,
            int immutableAccountCount,
            PageResponse<AudienceAccount> accounts
    ) {}

    public record Owner(
            UUID policyId,
            UUID adminUserId,
            UUID userId,
            String email,
            String username,
            String displayName,
            boolean active
    ) {}

    public record Comparison(
            UUID sourcePolicyId,
            UUID comparedPolicyId,
            boolean sameLineage,
            boolean directSuccessor,
            Set<String> changedFields,
            Detail source,
            Detail compared
    ) {
        public Comparison { changedFields = Set.copyOf(changedFields); }
    }

    public record Revision(
            UUID id,
            String code,
            CommercialPolicyStatus status,
            int revisionNumber,
            UUID sourcePolicyId,
            long version,
            Instant createdAt
    ) {}

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
