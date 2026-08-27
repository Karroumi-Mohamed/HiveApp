package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignAudienceMode;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable schedule evidence and frozen non-public audience for one Campaign revision. */
@Entity
@Immutable
@Table(name = "commercial_campaign_audience_snapshots",
        uniqueConstraints = @UniqueConstraint(name = "uk_campaign_audience_snapshot",
                columnNames = "campaign_id"),
        indexes = @Index(name = "idx_campaign_snapshot_actor", columnList = "actor_user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommercialCampaignAudienceSnapshot extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false, updatable = false)
    private CommercialCampaign campaign;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_mode", nullable = false, updatable = false, length = 24)
    private CommercialCampaignAudienceMode audienceMode;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "evaluated_at", nullable = false, updatable = false)
    private Instant evaluatedAt;

    @Column(name = "evidence_expires_at", nullable = false, updatable = false)
    private Instant evidenceExpiresAt;

    @Column(name = "campaign_version", nullable = false, updatable = false)
    private long campaignVersion;

    @Column(name = "catalog_revision", nullable = false, updatable = false)
    private long catalogRevision;

    @Column(name = "audience_fingerprint", nullable = false, updatable = false, length = 64)
    private String audienceFingerprint;

    @Column(name = "segment_id", updatable = false)
    private UUID segmentId;

    @Column(name = "segment_activation_id", updatable = false)
    private UUID segmentActivationId;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false, updatable = false)
    private Instant endsAt;

    @Column(name = "affected_account_count", nullable = false, updatable = false)
    private int affectedAccountCount;

    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    private String reason;

    @ElementCollection(fetch = FetchType.LAZY)
    @Immutable
    @CollectionTable(name = "commercial_campaign_audience_accounts",
            joinColumns = @JoinColumn(name = "snapshot_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_campaign_snapshot_account",
                    columnNames = {"snapshot_id", "account_id"}))
    @Column(name = "account_id", nullable = false)
    private Set<UUID> accountIds = new LinkedHashSet<>();

    public static CommercialCampaignAudienceSnapshot record(
            CommercialCampaign campaign, UUID actorUserId, Instant evaluatedAt,
            Instant evidenceExpiresAt, long campaignVersion, long catalogRevision,
            String fingerprint, UUID segmentId, UUID segmentActivationId,
            String reason, Collection<UUID> accountIds) {
        CommercialCampaignAudienceSnapshot snapshot = new CommercialCampaignAudienceSnapshot();
        snapshot.campaign = Objects.requireNonNull(campaign, "Campaign is required");
        snapshot.audienceMode = campaign.getAudienceMode();
        snapshot.actorUserId = Objects.requireNonNull(actorUserId, "Actor is required");
        snapshot.evaluatedAt = Objects.requireNonNull(evaluatedAt, "Evaluation time is required");
        snapshot.evidenceExpiresAt = Objects.requireNonNull(evidenceExpiresAt, "Evidence expiry is required");
        snapshot.campaignVersion = campaignVersion;
        snapshot.catalogRevision = catalogRevision;
        snapshot.audienceFingerprint = required(fingerprint, "Audience fingerprint");
        snapshot.segmentId = segmentId;
        snapshot.segmentActivationId = segmentActivationId;
        snapshot.startsAt = campaign.getStartsAt();
        snapshot.endsAt = campaign.getEndsAt();
        snapshot.reason = required(reason, "Schedule reason");
        snapshot.accountIds = new LinkedHashSet<>(accountIds == null ? Set.of() : accountIds);
        snapshot.affectedAccountCount = snapshot.accountIds.size();
        if (snapshot.audienceMode == CommercialCampaignAudienceMode.PUBLIC) {
            snapshot.affectedAccountCount = 0;
            snapshot.accountIds.clear();
        }
        return snapshot;
    }

    public Set<UUID> getAccountIds() { return Set.copyOf(accountIds); }

    @PreUpdate
    @PreRemove
    void rejectMutation() {
        throw new IllegalStateException("Campaign schedule evidence is immutable.");
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }
}
