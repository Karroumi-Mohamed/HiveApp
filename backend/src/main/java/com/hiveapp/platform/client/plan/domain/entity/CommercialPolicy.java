package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyCreationReason;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicySource;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyStatus;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyTargetKind;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A reviewable commercial policy revision. Published terms are never edited in place. */
@Entity
@Table(name = "commercial_policies", uniqueConstraints = {
        @UniqueConstraint(name = "uk_commercial_policy_code", columnNames = "code"),
        @UniqueConstraint(name = "uk_commercial_policy_lineage_revision",
                columnNames = {"lineage_id", "revision_number"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommercialPolicy extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommercialPolicyStatus status = CommercialPolicyStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_kind", nullable = false, length = 40)
    private CommercialPolicyTargetKind targetKind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_account_id")
    private Account targetAccount;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "commercial_policy_target_accounts",
            joinColumns = @JoinColumn(name = "policy_id"),
            inverseJoinColumns = @JoinColumn(name = "account_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_commercial_policy_target_account",
                    columnNames = {"policy_id", "account_id"}))
    private Set<Account> explicitAccounts = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_plan_id")
    private Plan targetPlan;

    @Column(name = "segment_reference", length = 100)
    private String segmentReference;

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CommercialPolicyEffect> effects = new LinkedHashSet<>();

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_until")
    private Instant effectiveUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CommercialPolicySource source;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "approval_reference", length = 180)
    private String approvalReference;

    @Column(name = "contract_reference", length = 180)
    private String contractReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_admin_user_id", nullable = false)
    private AdminUser owner;

    @Column(name = "lineage_id", nullable = false, updatable = false)
    private UUID lineageId;

    @Column(name = "revision_number", nullable = false, updatable = false)
    private int revisionNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_policy_id", updatable = false)
    private CommercialPolicy sourcePolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "creation_reason", nullable = false, updatable = false, length = 20)
    private CommercialPolicyCreationReason creationReason;

    @Version
    @Column(name = "row_version", nullable = false)
    private long version;

    public static CommercialPolicy draft(
            String code,
            String name,
            String description,
            AdminUser owner,
            Instant effectiveFrom,
            Instant effectiveUntil,
            CommercialPolicySource source,
            int priority,
            String reason,
            String approvalReference,
            String contractReference
    ) {
        CommercialPolicy policy = new CommercialPolicy();
        policy.code = required(code, "Policy code");
        policy.lineageId = UUID.randomUUID();
        policy.revisionNumber = 1;
        policy.creationReason = CommercialPolicyCreationReason.CREATED;
        policy.applyTerms(name, description, owner, effectiveFrom, effectiveUntil, source, priority,
                reason, approvalReference, contractReference);
        return policy;
    }

    public CommercialPolicy duplicate(String duplicateCode, String duplicateName, AdminUser duplicateOwner,
                                      String duplicateReason) {
        CommercialPolicy copy = draft(duplicateCode, duplicateName, description, duplicateOwner,
                effectiveFrom, effectiveUntil, source, priority, duplicateReason,
                approvalReference, contractReference);
        copy.creationReason = CommercialPolicyCreationReason.DUPLICATED;
        return copy;
    }

    public CommercialPolicy revise(String successorCode, AdminUser successorOwner, String revisionReason,
                                   int successorRevisionNumber) {
        if (status == CommercialPolicyStatus.DRAFT || status == CommercialPolicyStatus.ARCHIVED) {
            throw new IllegalStateException("Only a published, non-archived policy can be revised.");
        }
        if (successorRevisionNumber <= revisionNumber) {
            throw new IllegalArgumentException("Successor revision number must advance the lineage.");
        }
        CommercialPolicy successor = draft(successorCode, name, description, successorOwner,
                effectiveFrom, effectiveUntil, source, priority, revisionReason,
                approvalReference, contractReference);
        successor.lineageId = lineageId;
        successor.revisionNumber = successorRevisionNumber;
        successor.sourcePolicy = this;
        successor.creationReason = CommercialPolicyCreationReason.REVISED;
        return successor;
    }

    public void editDraft(String name, String description, AdminUser owner,
                          Instant effectiveFrom, Instant effectiveUntil,
                          CommercialPolicySource source, int priority, String reason,
                          String approvalReference, String contractReference) {
        requireStatus(CommercialPolicyStatus.DRAFT, "Only a draft policy can be edited.");
        applyTerms(name, description, owner, effectiveFrom, effectiveUntil, source, priority,
                reason, approvalReference, contractReference);
    }

    public void configureTarget(CommercialPolicyTargetKind kind, Account account,
                                Collection<Account> accounts, Plan plan, String segmentReference) {
        requireStatus(CommercialPolicyStatus.DRAFT, "Only a draft policy target can be edited.");
        targetKind = Objects.requireNonNull(kind, "Policy target kind is required");
        targetAccount = account;
        explicitAccounts.clear();
        if (accounts != null) {
            accounts.stream().sorted(Comparator.comparing(Account::getId)).forEach(explicitAccounts::add);
        }
        targetPlan = plan;
        this.segmentReference = optional(segmentReference);
        validateTarget();
    }

    public void replaceEffects(List<CommercialPolicyEffect> replacement) {
        requireStatus(CommercialPolicyStatus.DRAFT, "Only draft policy effects can be edited.");
        effects.clear();
        effects.addAll(replacement == null ? List.of() : replacement);
    }

    public void activate() {
        if (status != CommercialPolicyStatus.DRAFT && status != CommercialPolicyStatus.PAUSED) {
            throw new IllegalStateException("Only a draft or paused policy can be activated.");
        }
        status = CommercialPolicyStatus.ACTIVE;
    }

    public void pause() {
        requireStatus(CommercialPolicyStatus.ACTIVE, "Only an active policy can be paused.");
        status = CommercialPolicyStatus.PAUSED;
    }

    public void end() {
        if (status != CommercialPolicyStatus.ACTIVE && status != CommercialPolicyStatus.PAUSED) {
            throw new IllegalStateException("Only an active or paused policy can be ended.");
        }
        status = CommercialPolicyStatus.ENDED;
    }

    public void archive() {
        if (status != CommercialPolicyStatus.DRAFT && status != CommercialPolicyStatus.ENDED) {
            throw new IllegalStateException("Only a draft or ended policy can be archived.");
        }
        status = CommercialPolicyStatus.ARCHIVED;
    }

    public void reassignDraftOwner(AdminUser newOwner) {
        requireStatus(CommercialPolicyStatus.DRAFT, "Published policy ownership changes require a revision.");
        owner = Objects.requireNonNull(newOwner, "Policy owner is required");
    }

    private void applyTerms(String name, String description, AdminUser owner,
                            Instant effectiveFrom, Instant effectiveUntil,
                            CommercialPolicySource source, int priority, String reason,
                            String approvalReference, String contractReference) {
        this.name = required(name, "Policy name");
        this.description = optional(description);
        this.owner = Objects.requireNonNull(owner, "Policy owner is required");
        this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "Policy effectiveFrom is required");
        if (effectiveUntil != null && !effectiveUntil.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("Policy effectiveUntil must be after effectiveFrom.");
        }
        this.effectiveUntil = effectiveUntil;
        this.source = Objects.requireNonNull(source, "Policy source is required");
        if (priority < 0 || priority > 1000) {
            throw new IllegalArgumentException("Policy priority must be between 0 and 1000.");
        }
        this.priority = priority;
        this.reason = required(reason, "Policy reason");
        this.approvalReference = optional(approvalReference);
        this.contractReference = optional(contractReference);
    }

    @PrePersist
    @PreUpdate
    void validateInvariant() {
        required(code, "Policy code");
        required(name, "Policy name");
        required(reason, "Policy reason");
        Objects.requireNonNull(owner, "Policy owner is required");
        Objects.requireNonNull(source, "Policy source is required");
        Objects.requireNonNull(status, "Policy status is required");
        Objects.requireNonNull(effectiveFrom, "Policy effectiveFrom is required");
        if (effectiveUntil != null && !effectiveUntil.isAfter(effectiveFrom)) {
            throw new IllegalStateException("Policy effectiveUntil must be after effectiveFrom.");
        }
        if (lineageId == null || revisionNumber < 1 || creationReason == null) {
            throw new IllegalStateException("Policy lineage identity is required.");
        }
        validateTarget();
    }

    private void validateTarget() {
        if (targetKind == null) {
            throw new IllegalStateException("Policy target is required.");
        }
        boolean valid = switch (targetKind) {
            case ACCOUNT -> targetAccount != null && explicitAccounts.isEmpty()
                    && targetPlan == null && segmentReference == null;
            case ACCOUNT_SET -> targetAccount == null && !explicitAccounts.isEmpty()
                    && targetPlan == null && segmentReference == null;
            case PLAN_REVISION_SUBSCRIBERS -> targetAccount == null && explicitAccounts.isEmpty()
                    && targetPlan != null && segmentReference == null;
            case SEGMENT -> targetAccount == null && explicitAccounts.isEmpty()
                    && targetPlan == null && segmentReference != null;
        };
        if (!valid) {
            throw new IllegalStateException("Policy target configuration does not match its target kind.");
        }
    }

    private void requireStatus(CommercialPolicyStatus required, String message) {
        if (status != required) throw new IllegalStateException(message);
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
