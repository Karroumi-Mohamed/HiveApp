package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentCreationReason;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentKind;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentSource;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A versioned, reviewable Account-audience definition. Active definitions are immutable. */
@Entity
@Table(name = "commercial_segments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_commercial_segment_code", columnNames = "code"),
        @UniqueConstraint(name = "uk_commercial_segment_lineage_revision",
                columnNames = {"lineage_id", "revision_number"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommercialSegment extends BaseEntity {

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
    private CommercialSegmentStatus status = CommercialSegmentStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CommercialSegmentKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CommercialSegmentSource source;

    @Column(nullable = false, length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_admin_user_id", nullable = false)
    private AdminUser owner;

    @Column(name = "lineage_id", nullable = false, updatable = false)
    private UUID lineageId;

    @Column(name = "revision_number", nullable = false, updatable = false)
    private int revisionNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_segment_id", updatable = false)
    private CommercialSegment sourceSegment;

    @Enumerated(EnumType.STRING)
    @Column(name = "creation_reason", nullable = false, updatable = false, length = 20)
    private CommercialSegmentCreationReason creationReason;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "commercial_segment_explicit_accounts",
            joinColumns = @JoinColumn(name = "segment_id"),
            inverseJoinColumns = @JoinColumn(name = "account_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_commercial_segment_explicit_account",
                    columnNames = {"segment_id", "account_id"}))
    private Set<Account> explicitAccounts = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "commercial_segment_plan_criteria",
            joinColumns = @JoinColumn(name = "segment_id"))
    @Column(name = "plan_revision_id", nullable = false)
    private Set<UUID> currentPlanRevisionIds = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "commercial_segment_status_criteria",
            joinColumns = @JoinColumn(name = "segment_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false, length = 24)
    private Set<SubscriptionStatus> subscriptionStatuses = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "commercial_segment_currency_criteria",
            joinColumns = @JoinColumn(name = "segment_id"))
    @Column(name = "currency_code", nullable = false, length = 3)
    private Set<String> currencyCodes = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "commercial_segment_cycle_criteria",
            joinColumns = @JoinColumn(name = "segment_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private Set<BillingCycle> billingCycles = new LinkedHashSet<>();

    @Column(name = "account_created_from")
    private Instant accountCreatedFrom;

    @Column(name = "account_created_until")
    private Instant accountCreatedUntil;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "commercial_segment_product_criteria",
            joinColumns = @JoinColumn(name = "segment_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_commercial_segment_product_criterion",
                    columnNames = {"segment_id", "product_type", "product_code"}))
    private Set<CommercialSegmentProductSelection> productHoldings = new LinkedHashSet<>();

    @Version
    @Column(name = "row_version", nullable = false)
    private long version;

    public static CommercialSegment draft(
            String code,
            String name,
            String description,
            CommercialSegmentKind kind,
            CommercialSegmentSource source,
            String reason,
            AdminUser owner
    ) {
        CommercialSegment segment = new CommercialSegment();
        segment.code = required(code, "Segment code").toUpperCase(Locale.ROOT);
        segment.lineageId = UUID.randomUUID();
        segment.revisionNumber = 1;
        segment.creationReason = CommercialSegmentCreationReason.CREATED;
        segment.applyTerms(name, description, kind, source, reason, owner);
        return segment;
    }

    public void editDraft(
            String name,
            String description,
            CommercialSegmentKind kind,
            CommercialSegmentSource source,
            String reason,
            AdminUser owner
    ) {
        requireDraft("Only a draft Segment can be edited.");
        applyTerms(name, description, kind, source, reason, owner);
    }

    public void configureExplicitAccounts(Collection<Account> accounts) {
        requireDraft("Only a draft Segment definition can be edited.");
        if (kind != CommercialSegmentKind.EXPLICIT_ACCOUNTS) {
            throw new IllegalStateException("Explicit Accounts require EXPLICIT_ACCOUNTS kind.");
        }
        clearDefinition();
        if (accounts != null) {
            accounts.stream().sorted(Comparator.comparing(Account::getId)).forEach(explicitAccounts::add);
        }
        if (explicitAccounts.isEmpty()) {
            throw new IllegalArgumentException("An explicit Segment requires at least one Account.");
        }
    }

    public void configureCriteria(
            Collection<UUID> planIds,
            Collection<SubscriptionStatus> statuses,
            Collection<String> currencies,
            Collection<BillingCycle> cycles,
            Instant createdFrom,
            Instant createdUntil,
            Collection<CommercialSegmentProductSelection> holdings
    ) {
        requireDraft("Only a draft Segment definition can be edited.");
        if (kind != CommercialSegmentKind.TYPED_CRITERIA) {
            throw new IllegalStateException("Typed criteria require TYPED_CRITERIA kind.");
        }
        clearDefinition();
        if (planIds != null) currentPlanRevisionIds.addAll(planIds);
        if (statuses != null) subscriptionStatuses.addAll(statuses);
        if (currencies != null) currencies.stream()
                .map(value -> required(value, "Currency code").toUpperCase(Locale.ROOT))
                .forEach(currencyCodes::add);
        if (cycles != null) billingCycles.addAll(cycles);
        accountCreatedFrom = createdFrom;
        accountCreatedUntil = createdUntil;
        if (holdings != null) productHoldings.addAll(holdings);
        validateCriteria();
    }

    public CommercialSegment duplicate(
            String duplicateCode,
            String duplicateName,
            AdminUser duplicateOwner,
            String duplicateReason
    ) {
        CommercialSegment copy = draft(duplicateCode, duplicateName, description, kind, source,
                duplicateReason, duplicateOwner);
        copy.creationReason = CommercialSegmentCreationReason.DUPLICATED;
        copyDefinitionTo(copy);
        return copy;
    }

    public CommercialSegment revise(
            String successorCode,
            AdminUser successorOwner,
            String revisionReason,
            int successorRevisionNumber
    ) {
        if (status != CommercialSegmentStatus.ACTIVE) {
            throw new IllegalStateException("Only an active Segment can be revised.");
        }
        if (successorRevisionNumber <= revisionNumber) {
            throw new IllegalArgumentException("Successor revision number must advance the lineage.");
        }
        CommercialSegment successor = draft(successorCode, name, description, kind, source,
                revisionReason, successorOwner);
        successor.lineageId = lineageId;
        successor.revisionNumber = successorRevisionNumber;
        successor.sourceSegment = this;
        successor.creationReason = CommercialSegmentCreationReason.REVISED;
        copyDefinitionTo(successor);
        return successor;
    }

    public void activate() {
        requireDraft("Only a draft Segment can be activated.");
        status = CommercialSegmentStatus.ACTIVE;
    }

    public void archive() {
        if (status == CommercialSegmentStatus.ARCHIVED) {
            throw new IllegalStateException("Segment is already archived.");
        }
        status = CommercialSegmentStatus.ARCHIVED;
    }

    public void reassignDraftOwner(AdminUser newOwner) {
        requireDraft("Published Segment ownership changes require a revision.");
        owner = Objects.requireNonNull(newOwner, "Segment owner is required");
    }

    public Set<Account> getExplicitAccounts() {
        return Collections.unmodifiableSet(explicitAccounts);
    }

    public Set<UUID> getCurrentPlanRevisionIds() {
        return Collections.unmodifiableSet(currentPlanRevisionIds);
    }

    public Set<SubscriptionStatus> getSubscriptionStatuses() {
        return Collections.unmodifiableSet(subscriptionStatuses);
    }

    public Set<String> getCurrencyCodes() {
        return Collections.unmodifiableSet(currencyCodes);
    }

    public Set<BillingCycle> getBillingCycles() {
        return Collections.unmodifiableSet(billingCycles);
    }

    public Set<CommercialSegmentProductSelection> getProductHoldings() {
        return Collections.unmodifiableSet(productHoldings);
    }

    private void applyTerms(String name, String description, CommercialSegmentKind kind,
                            CommercialSegmentSource source, String reason, AdminUser owner) {
        this.name = required(name, "Segment name");
        normalizedName = this.name.toLowerCase(Locale.ROOT);
        this.description = optional(description);
        this.kind = Objects.requireNonNull(kind, "Segment kind is required");
        this.source = Objects.requireNonNull(source, "Segment source is required");
        this.reason = required(reason, "Segment reason");
        this.owner = Objects.requireNonNull(owner, "Segment owner is required");
    }

    private void copyDefinitionTo(CommercialSegment target) {
        if (kind == CommercialSegmentKind.EXPLICIT_ACCOUNTS) {
            target.configureExplicitAccounts(explicitAccounts);
        } else {
            target.configureCriteria(currentPlanRevisionIds, subscriptionStatuses, currencyCodes,
                    billingCycles, accountCreatedFrom, accountCreatedUntil, productHoldings);
        }
    }

    private void clearDefinition() {
        explicitAccounts.clear();
        currentPlanRevisionIds.clear();
        subscriptionStatuses.clear();
        currencyCodes.clear();
        billingCycles.clear();
        accountCreatedFrom = null;
        accountCreatedUntil = null;
        productHoldings.clear();
    }

    private void validateCriteria() {
        if (accountCreatedFrom != null && accountCreatedUntil != null
                && !accountCreatedUntil.isAfter(accountCreatedFrom)) {
            throw new IllegalArgumentException("Account creation end must be after the start.");
        }
        boolean empty = currentPlanRevisionIds.isEmpty()
                && subscriptionStatuses.isEmpty()
                && currencyCodes.isEmpty()
                && billingCycles.isEmpty()
                && accountCreatedFrom == null
                && accountCreatedUntil == null
                && productHoldings.isEmpty();
        if (empty) throw new IllegalArgumentException("A typed Segment requires at least one criterion.");
    }

    @PrePersist
    @PreUpdate
    void validateInvariant() {
        required(code, "Segment code");
        required(name, "Segment name");
        required(normalizedName, "Normalized Segment name");
        required(reason, "Segment reason");
        Objects.requireNonNull(owner, "Segment owner is required");
        Objects.requireNonNull(source, "Segment source is required");
        Objects.requireNonNull(status, "Segment status is required");
        Objects.requireNonNull(kind, "Segment kind is required");
        if (lineageId == null || revisionNumber < 1 || creationReason == null) {
            throw new IllegalStateException("Segment lineage identity is required.");
        }
        if (kind == CommercialSegmentKind.EXPLICIT_ACCOUNTS) {
            if (explicitAccounts.isEmpty() || hasCriteria()) {
                throw new IllegalStateException("Explicit Segment definition is inconsistent.");
            }
        } else {
            if (!explicitAccounts.isEmpty()) {
                throw new IllegalStateException("Typed Segment cannot contain explicit Accounts.");
            }
            validateCriteria();
        }
    }

    private boolean hasCriteria() {
        return !currentPlanRevisionIds.isEmpty() || !subscriptionStatuses.isEmpty()
                || !currencyCodes.isEmpty() || !billingCycles.isEmpty()
                || accountCreatedFrom != null || accountCreatedUntil != null
                || !productHoldings.isEmpty();
    }

    private void requireDraft(String message) {
        if (status != CommercialSegmentStatus.DRAFT) throw new IllegalStateException(message);
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
