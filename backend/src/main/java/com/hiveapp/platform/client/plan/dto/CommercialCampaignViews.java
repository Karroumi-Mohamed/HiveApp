package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignAction;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignAudienceMode;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignBlocker;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignCreationReason;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignSource;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignStatus;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignSegmentChoiceState;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentKind;
import com.hiveapp.shared.api.PageResponse;
import com.hiveapp.shared.audit.domain.AuditOutcome;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CommercialCampaignViews {

    private CommercialCampaignViews() {}

    public record Summary(
            UUID id, String code, String name, CommercialCampaignStatus status,
            CommercialCampaignAudienceMode audienceMode, CommercialCampaignSource source,
            Instant startsAt, Instant endsAt, Integer configuredAccountCount,
            Integer frozenAccountCount, UUID lineageId, int revisionNumber,
            CommercialCampaignCreationReason creationReason, long version,
            Instant createdAt, Instant updatedAt,
            List<CommercialCampaignAction> availableActions,
            Map<CommercialCampaignAction, List<CommercialCampaignBlocker>> blockedActions,
            boolean ownerIdentityRestricted, boolean audienceIdentityRestricted) {
        public Summary {
            availableActions = List.copyOf(availableActions);
            blockedActions = blockedActions.entrySet().stream().collect(
                    java.util.stream.Collectors.toUnmodifiableMap(
                            Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        }
    }

    public record Detail(Summary summary, String description, String reason,
                         Audience audience, UUID sourceCampaignId,
                         Instant scheduledAt, Instant activatedAt, Instant pausedAt, Instant resumedAt,
                         Instant endedAt, Instant archivedAt) {
        public UUID id() { return summary.id(); }
    }

    public record Audience(CommercialCampaignAudienceMode mode, Set<UUID> explicitAccountIds,
                           UUID segmentId, UUID segmentActivationId) {
        public Audience { explicitAccountIds = Set.copyOf(explicitAccountIds); }
    }

    public record AudienceReference(UUID accountId) {}

    public record AudienceIdentity(UUID accountId, String accountName, String accountSlug,
                                   String ownerEmail, boolean active) {}

    public record AudiencePreview(UUID campaignId, long campaignVersion,
                                  CommercialCampaignAudienceMode mode,
                                  String registryVersion,
                                  Instant evaluatedAt, Instant expiresAt, String previewToken,
                                  Long targetedAccountCount, boolean publicAudience,
                                  boolean schedulable, List<CommercialCampaignBlocker> blockers,
                                  List<AudienceReference> sample, UUID segmentId,
                                  UUID segmentActivationId, String fingerprint) {
        public AudiencePreview { blockers = List.copyOf(blockers); sample = List.copyOf(sample); }
    }

    public record FrozenAudience(UUID campaignId, UUID snapshotId,
                                 CommercialCampaignAudienceMode mode,
                                 boolean publicAudience, int immutableAccountCount,
                                 UUID segmentId, UUID segmentActivationId,
                                 UUID reviewedByActorUserId,
                                 Instant evaluatedAt, Instant evidenceExpiresAt,
                                 long campaignVersion, long catalogRevision,
                                 String registryVersion, String audienceFingerprint, String reason,
                                 Instant startsAt, Instant endsAt,
                                 PageResponse<AudienceReference> accounts) {}

    public record FrozenIdentityAudience(UUID campaignId, UUID snapshotId,
                                         int immutableAccountCount,
                                         PageResponse<AudienceIdentity> accounts) {}

    public record Owner(UUID campaignId, UUID adminUserId, UUID userId, String email,
                        String username, String displayName, boolean active) {}

    /** Minimal identity projection exposed only through the dedicated owner-choice permissions. */
    public record OwnerChoice(UUID adminUserId, String email, String username,
                              String displayName) {}

    public record Revision(UUID id, String code, CommercialCampaignStatus status,
                           int revisionNumber, UUID sourceCampaignId, long version,
                           Instant createdAt) {}

    public record Comparison(UUID sourceCampaignId, UUID comparedCampaignId,
                             boolean sameLineage, boolean directSuccessor,
                             Set<String> changedFields, Detail source, Detail compared) {
        public Comparison { changedFields = Set.copyOf(changedFields); }
    }

    public record History(UUID id, String action, AuditOutcome outcome, UUID actorUserId,
                          String actorEmail, String reason, Instant occurredAt) {}

    public record SegmentChoice(UUID id, String code, String name, int revisionNumber,
                                CommercialSegmentKind kind, UUID activationId,
                                Integer activationNumber, Integer immutableAccountCount,
                                CommercialCampaignSegmentChoiceState state) {}
}
