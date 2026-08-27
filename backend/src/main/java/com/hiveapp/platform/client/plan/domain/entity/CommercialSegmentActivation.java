package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

/** Immutable audience accepted by one Segment activation. */
@Entity
@Immutable
@Table(name = "commercial_segment_activations", uniqueConstraints =
        @UniqueConstraint(name = "uk_commercial_segment_activation_number",
                columnNames = {"segment_id", "activation_number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommercialSegmentActivation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "segment_id", nullable = false, updatable = false)
    private CommercialSegment segment;

    @Column(name = "activation_number", nullable = false, updatable = false)
    private int activationNumber;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "evaluated_at", nullable = false, updatable = false)
    private Instant evaluatedAt;

    @Column(name = "evidence_expires_at", nullable = false, updatable = false)
    private Instant evidenceExpiresAt;

    @Column(name = "criteria_version", nullable = false, updatable = false)
    private long criteriaVersion;

    @Column(name = "assessment_fingerprint", nullable = false, updatable = false, length = 64)
    private String assessmentFingerprint;

    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "affected_account_count", nullable = false, updatable = false)
    private int affectedAccountCount;

    @ElementCollection(fetch = FetchType.LAZY)
    @Immutable
    @CollectionTable(name = "commercial_segment_activation_accounts",
            joinColumns = @JoinColumn(name = "activation_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_commercial_segment_activation_account",
                    columnNames = {"activation_id", "account_id"}))
    @Column(name = "account_id", nullable = false)
    private Set<UUID> accountIds = new LinkedHashSet<>();

    public static CommercialSegmentActivation record(
            CommercialSegment segment,
            int activationNumber,
            UUID actorUserId,
            Instant evaluatedAt,
            Instant evidenceExpiresAt,
            long criteriaVersion,
            String fingerprint,
            String reason,
            Collection<UUID> accountIds
    ) {
        CommercialSegmentActivation activation = new CommercialSegmentActivation();
        activation.segment = Objects.requireNonNull(segment, "Segment is required");
        activation.activationNumber = activationNumber;
        activation.actorUserId = Objects.requireNonNull(actorUserId, "Actor is required");
        activation.evaluatedAt = Objects.requireNonNull(evaluatedAt, "Evaluation time is required");
        activation.evidenceExpiresAt = Objects.requireNonNull(evidenceExpiresAt, "Evidence expiry is required");
        activation.criteriaVersion = criteriaVersion;
        activation.assessmentFingerprint = required(fingerprint, "Assessment fingerprint");
        activation.reason = required(reason, "Activation reason");
        activation.accountIds = new LinkedHashSet<>(accountIds == null ? Set.of() : accountIds);
        activation.affectedAccountCount = activation.accountIds.size();
        if (activationNumber < 1 || criteriaVersion < 0) {
            throw new IllegalArgumentException("Segment activation identity is invalid.");
        }
        return activation;
    }

    public Set<UUID> getAccountIds() {
        return Set.copyOf(accountIds);
    }

    @PreUpdate
    @PreRemove
    void rejectMutation() {
        throw new IllegalStateException("Segment activation evidence is immutable.");
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }
}
