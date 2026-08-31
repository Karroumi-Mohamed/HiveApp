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
import java.util.UUID;

/** Immutable evidence and exact Account audience accepted by one activation/resume. */
@Entity
@Immutable
@Table(name = "commercial_policy_activations", uniqueConstraints =
        @UniqueConstraint(name = "uk_commercial_policy_activation_number",
                columnNames = {"policy_id", "activation_number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommercialPolicyActivation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false, updatable = false)
    private CommercialPolicy policy;

    @Column(name = "activation_number", nullable = false, updatable = false)
    private int activationNumber;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "evaluated_at", nullable = false, updatable = false)
    private Instant evaluatedAt;

    @Column(name = "evidence_expires_at", nullable = false, updatable = false)
    private Instant evidenceExpiresAt;

    @Column(name = "catalog_revision", nullable = false, updatable = false)
    private long catalogRevision;

    @Column(name = "registry_version", nullable = false, updatable = false, length = 180)
    private String registryVersion;

    @Column(name = "assessment_fingerprint", nullable = false, updatable = false, length = 64)
    private String assessmentFingerprint;

    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "affected_account_count", nullable = false, updatable = false)
    private int affectedAccountCount;

    @ElementCollection(fetch = FetchType.LAZY)
    @Immutable
    @CollectionTable(name = "commercial_policy_activation_accounts",
            joinColumns = @JoinColumn(name = "activation_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_commercial_policy_activation_account",
                    columnNames = {"activation_id", "account_id"}))
    @Column(name = "account_id", nullable = false)
    private java.util.Set<UUID> accountIds = new LinkedHashSet<>();

    public java.util.Set<UUID> getAccountIds() {
        return java.util.Set.copyOf(accountIds);
    }

    public static CommercialPolicyActivation record(
            CommercialPolicy policy,
            int activationNumber,
            UUID actorUserId,
            Instant evaluatedAt,
            Instant evidenceExpiresAt,
            long catalogRevision,
            String registryVersion,
            String assessmentFingerprint,
            String reason,
            Collection<UUID> accountIds
    ) {
        CommercialPolicyActivation activation = new CommercialPolicyActivation();
        activation.policy = Objects.requireNonNull(policy, "Policy is required");
        activation.activationNumber = activationNumber;
        activation.actorUserId = Objects.requireNonNull(actorUserId, "Actor is required");
        activation.evaluatedAt = Objects.requireNonNull(evaluatedAt, "Evaluation time is required");
        activation.evidenceExpiresAt = Objects.requireNonNull(evidenceExpiresAt, "Expiry is required");
        activation.catalogRevision = catalogRevision;
        activation.registryVersion = required(registryVersion, "Registry version");
        activation.assessmentFingerprint = required(assessmentFingerprint, "Assessment fingerprint");
        activation.reason = required(reason, "Activation reason");
        activation.accountIds = new LinkedHashSet<>(accountIds == null ? java.util.List.of() : accountIds);
        activation.affectedAccountCount = activation.accountIds.size();
        if (activationNumber < 1 || catalogRevision < 0) {
            throw new IllegalArgumentException("Activation identity is invalid.");
        }
        return activation;
    }

    @PreUpdate
    @PreRemove
    void rejectMutation() {
        throw new IllegalStateException("Commercial policy activation evidence is immutable.");
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }
}
