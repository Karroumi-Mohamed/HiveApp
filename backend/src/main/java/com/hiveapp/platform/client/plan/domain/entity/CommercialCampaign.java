package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignAudienceMode;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignCreationReason;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignSource;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignStatus;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** One immutable-after-scheduling Campaign revision. */
@Entity
@Table(name = "commercial_campaigns",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_commercial_campaign_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_commercial_campaign_lineage_revision",
                        columnNames = {"lineage_id", "revision_number"}),
                @UniqueConstraint(name = "uk_commercial_campaign_draft_lineage",
                        columnNames = "draft_lineage_key")
        },
        indexes = {
                @Index(name = "idx_campaign_normalized_name", columnList = "normalized_name"),
                @Index(name = "idx_campaign_status_start", columnList = "status,starts_at,id"),
                @Index(name = "idx_campaign_status_end", columnList = "status,ends_at,id"),
                @Index(name = "idx_campaign_owner", columnList = "owner_admin_user_id"),
                @Index(name = "idx_campaign_source", columnList = "source_campaign_id"),
                @Index(name = "idx_campaign_segment_status", columnList = "segment_id,status,id"),
                @Index(name = "idx_campaign_segment_activation", columnList = "segment_activation_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommercialCampaign extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 180)
    private String normalizedName;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommercialCampaignStatus status = CommercialCampaignStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_mode", nullable = false, length = 24)
    private CommercialCampaignAudienceMode audienceMode;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "commercial_campaign_explicit_accounts",
            joinColumns = @JoinColumn(name = "campaign_id"),
            inverseJoinColumns = @JoinColumn(name = "account_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_campaign_explicit_account",
                    columnNames = {"campaign_id", "account_id"}))
    private Set<Account> explicitAccounts = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segment_id")
    private CommercialSegment segment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segment_activation_id")
    private CommercialSegmentActivation segmentActivation;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CommercialCampaignSource source;

    @Column(nullable = false, length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_admin_user_id", nullable = false)
    private AdminUser owner;

    @Column(name = "lineage_id", nullable = false, updatable = false)
    private UUID lineageId;

    /** Non-null only while DRAFT, making one draft per lineage a database invariant. */
    @Column(name = "draft_lineage_key")
    private UUID draftLineageKey;

    @Column(name = "revision_number", nullable = false, updatable = false)
    private int revisionNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_campaign_id", updatable = false)
    private CommercialCampaign sourceCampaign;

    @Enumerated(EnumType.STRING)
    @Column(name = "creation_reason", nullable = false, updatable = false, length = 20)
    private CommercialCampaignCreationReason creationReason;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "resumed_at")
    private Instant resumedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long version;

    public static CommercialCampaign draft(
            String code, String name, String description, Instant startsAt, Instant endsAt,
            CommercialCampaignSource source, String reason, AdminUser owner) {
        CommercialCampaign campaign = new CommercialCampaign();
        campaign.code = required(code, "Campaign code").toUpperCase(Locale.ROOT);
        campaign.lineageId = UUID.randomUUID();
        campaign.draftLineageKey = campaign.lineageId;
        campaign.revisionNumber = 1;
        campaign.creationReason = CommercialCampaignCreationReason.CREATED;
        campaign.applyTerms(name, description, startsAt, endsAt, source, reason, owner);
        return campaign;
    }

    public void editDraft(String name, String description, Instant startsAt, Instant endsAt,
                          CommercialCampaignSource source, String reason) {
        requireDraft("Only a draft Campaign can be edited.");
        applyTerms(name, description, startsAt, endsAt, source, reason, owner);
    }

    public void configurePublicAudience() {
        requireDraft("Only a draft Campaign audience can be edited.");
        clearAudience();
        audienceMode = CommercialCampaignAudienceMode.PUBLIC;
    }

    public void configureExplicitAudience(Collection<Account> accounts) {
        requireDraft("Only a draft Campaign audience can be edited.");
        clearAudience();
        audienceMode = CommercialCampaignAudienceMode.EXPLICIT_ACCOUNTS;
        if (accounts != null) {
            accounts.stream().sorted(Comparator.comparing(Account::getId)).forEach(explicitAccounts::add);
        }
        if (explicitAccounts.isEmpty()) {
            throw new IllegalArgumentException("An explicit Campaign audience requires Accounts.");
        }
    }

    public void configureSegmentAudience(CommercialSegment segment,
                                         CommercialSegmentActivation activation) {
        requireDraft("Only a draft Campaign audience can be edited.");
        clearAudience();
        this.audienceMode = CommercialCampaignAudienceMode.SEGMENT;
        this.segment = Objects.requireNonNull(segment, "Segment is required");
        this.segmentActivation = Objects.requireNonNull(activation, "Segment activation is required");
        if (!activation.getSegment().getId().equals(segment.getId())) {
            throw new IllegalArgumentException("Segment activation does not belong to the Segment.");
        }
    }

    public CommercialCampaign duplicate(String duplicateCode, String duplicateName,
                                         AdminUser duplicateOwner, String duplicateReason) {
        CommercialCampaign copy = draft(duplicateCode, duplicateName, description, startsAt, endsAt,
                source, duplicateReason, duplicateOwner);
        copy.creationReason = CommercialCampaignCreationReason.DUPLICATED;
        copy.sourceCampaign = this;
        copyAudienceTo(copy);
        return copy;
    }

    public CommercialCampaign revise(String successorCode, AdminUser successorOwner,
                                     String revisionReason, int successorRevision) {
        if (status == CommercialCampaignStatus.DRAFT || status == CommercialCampaignStatus.ARCHIVED) {
            throw new IllegalStateException("Only a published, non-archived Campaign can be revised.");
        }
        CommercialCampaign successor = draft(successorCode, name, description, startsAt, endsAt,
                source, revisionReason, successorOwner);
        successor.lineageId = lineageId;
        successor.draftLineageKey = lineageId;
        successor.revisionNumber = successorRevision;
        successor.sourceCampaign = this;
        successor.creationReason = CommercialCampaignCreationReason.REVISED;
        copyAudienceTo(successor);
        return successor;
    }

    public void schedule(Instant now) {
        requireDraft("Only a draft Campaign can be scheduled.");
        status = CommercialCampaignStatus.SCHEDULED;
        draftLineageKey = null;
        scheduledAt = Objects.requireNonNull(now, "Schedule time is required");
    }

    public void start(Instant now) {
        if (status != CommercialCampaignStatus.SCHEDULED) {
            throw new IllegalStateException("Only a scheduled Campaign can start.");
        }
        status = CommercialCampaignStatus.ACTIVE;
        activatedAt = Objects.requireNonNull(now, "Activation time is required");
    }

    public void pause(Instant now) {
        if (status != CommercialCampaignStatus.ACTIVE) {
            throw new IllegalStateException("Only an active Campaign can be paused.");
        }
        status = CommercialCampaignStatus.PAUSED;
        pausedAt = Objects.requireNonNull(now, "Pause time is required");
    }

    public void resume(Instant now) {
        if (status != CommercialCampaignStatus.PAUSED) {
            throw new IllegalStateException("Only a paused Campaign can resume.");
        }
        status = CommercialCampaignStatus.ACTIVE;
        resumedAt = Objects.requireNonNull(now, "Resume time is required");
    }

    public void end(Instant now) {
        if (status != CommercialCampaignStatus.SCHEDULED
                && status != CommercialCampaignStatus.ACTIVE
                && status != CommercialCampaignStatus.PAUSED) {
            throw new IllegalStateException("Only a scheduled, active, or paused Campaign can end.");
        }
        status = CommercialCampaignStatus.ENDED;
        endedAt = Objects.requireNonNull(now, "End time is required");
    }

    public void archive(Instant now) {
        if (status != CommercialCampaignStatus.ENDED) {
            throw new IllegalStateException("Only an ended Campaign can be archived.");
        }
        status = CommercialCampaignStatus.ARCHIVED;
        archivedAt = Objects.requireNonNull(now, "Archive time is required");
    }

    public void reassignDraftOwner(AdminUser newOwner) {
        requireDraft("Published Campaign ownership changes require a revision.");
        owner = Objects.requireNonNull(newOwner, "Campaign owner is required");
    }

    public Set<Account> getExplicitAccounts() {
        return Set.copyOf(explicitAccounts);
    }

    private void applyTerms(String name, String description, Instant startsAt, Instant endsAt,
                            CommercialCampaignSource source, String reason, AdminUser owner) {
        this.name = required(name, "Campaign name");
        this.normalizedName = this.name.toLowerCase(Locale.ROOT);
        this.description = optional(description);
        this.startsAt = Objects.requireNonNull(startsAt, "Campaign start is required");
        this.endsAt = Objects.requireNonNull(endsAt, "Campaign end is required");
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("Campaign end must be after its start.");
        }
        this.source = Objects.requireNonNull(source, "Campaign source is required");
        this.reason = required(reason, "Campaign reason");
        this.owner = Objects.requireNonNull(owner, "Campaign owner is required");
    }

    private void copyAudienceTo(CommercialCampaign target) {
        switch (audienceMode) {
            case PUBLIC -> target.configurePublicAudience();
            case EXPLICIT_ACCOUNTS -> target.configureExplicitAudience(explicitAccounts);
            case SEGMENT -> target.configureSegmentAudience(segment, segmentActivation);
        }
    }

    private void clearAudience() {
        explicitAccounts.clear();
        segment = null;
        segmentActivation = null;
    }

    @PrePersist
    @PreUpdate
    void validateInvariant() {
        required(code, "Campaign code");
        required(name, "Campaign name");
        required(normalizedName, "Normalized Campaign name");
        required(reason, "Campaign reason");
        Objects.requireNonNull(status, "Campaign status is required");
        Objects.requireNonNull(audienceMode, "Campaign audience mode is required");
        Objects.requireNonNull(source, "Campaign source is required");
        Objects.requireNonNull(owner, "Campaign owner is required");
        if (lineageId == null || revisionNumber < 1 || creationReason == null) {
            throw new IllegalStateException("Campaign lineage identity is required.");
        }
        if ((status == CommercialCampaignStatus.DRAFT) != (draftLineageKey != null)) {
            throw new IllegalStateException("Campaign draft lineage key is inconsistent.");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalStateException("Campaign end must be after its start.");
        }
        boolean audienceValid = switch (audienceMode) {
            case PUBLIC -> explicitAccounts.isEmpty() && segment == null && segmentActivation == null;
            case EXPLICIT_ACCOUNTS -> !explicitAccounts.isEmpty()
                    && segment == null && segmentActivation == null;
            case SEGMENT -> explicitAccounts.isEmpty()
                    && segment != null && segmentActivation != null
                    && segmentActivation.getSegment().getId().equals(segment.getId());
        };
        if (!audienceValid) throw new IllegalStateException("Campaign audience is inconsistent.");
    }

    private void requireDraft(String message) {
        if (status != CommercialCampaignStatus.DRAFT) throw new IllegalStateException(message);
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
