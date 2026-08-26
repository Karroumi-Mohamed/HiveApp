package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.AddOnCreationReason;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "add_ons", uniqueConstraints = {
        @UniqueConstraint(name = "uk_add_ons_code", columnNames = "code"),
        @UniqueConstraint(name = "uk_add_on_lineage_revision", columnNames = {"lineage_id", "revision_number"})
})
@Getter
@Setter
public class AddOn extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private BillingCycle billingCycle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AddOnStatus status = AddOnStatus.DRAFT;

    @Column(name = "definition_version", nullable = false)
    private long definitionVersion = 1;

    @Column(name = "lineage_id", nullable = false, updatable = false)
    private java.util.UUID lineageId = java.util.UUID.randomUUID();

    @Column(name = "revision_number", nullable = false, updatable = false)
    private int revisionNumber = 1;

    @jakarta.persistence.ManyToOne(fetch = FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "source_add_on_id", updatable = false)
    private AddOn sourceAddOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "creation_reason", nullable = false, updatable = false, length = 20)
    private AddOnCreationReason creationReason = AddOnCreationReason.CREATED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_plan_codes")
    private Set<String> allowedPlanCodes = new LinkedHashSet<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "blocked_plan_codes")
    private Set<String> blockedPlanCodes = new LinkedHashSet<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dependency_codes")
    private Set<String> dependencyCodes = new LinkedHashSet<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exclusion_codes")
    private Set<String> exclusionCodes = new LinkedHashSet<>();

    @OneToMany(mappedBy = "addOn", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AddOnFeature> features = new ArrayList<>();

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    public Money money() {
        return Money.of(price, currencyCode);
    }

    public void setMoney(Money money) {
        price = money.amount();
        currencyCode = money.currencyCode();
    }

    public void touchDefinition() {
        definitionVersion++;
    }

    public boolean isActive() {
        return status == AddOnStatus.ACTIVE;
    }

    @PrePersist
    @PreUpdate
    void validateCommercialConfiguration() {
        Money normalized = Money.of(price, currencyCode);
        if (normalized.isNegative()) {
            throw new IllegalStateException("AddOn price cannot be negative");
        }
        setMoney(normalized);
        allowedPlanCodes = copy(allowedPlanCodes);
        blockedPlanCodes = copy(blockedPlanCodes);
        dependencyCodes = copy(dependencyCodes);
        exclusionCodes = copy(exclusionCodes);
        if (!java.util.Collections.disjoint(allowedPlanCodes, blockedPlanCodes)) {
            throw new IllegalStateException("An AddOn cannot both allow and block the same Plan");
        }
        if (!java.util.Collections.disjoint(dependencyCodes, exclusionCodes)) {
            throw new IllegalStateException("An AddOn cannot both require and exclude the same AddOn");
        }
        if (dependencyCodes.contains(code) || exclusionCodes.contains(code)) {
            throw new IllegalStateException("An AddOn cannot depend on or exclude itself");
        }
        if (lineageId == null || revisionNumber < 1 || creationReason == null) {
            throw new IllegalStateException("AddOn lineage identity is required");
        }
    }

    private Set<String> copy(Set<String> values) {
        return new LinkedHashSet<>(values == null ? Set.of() : values);
    }
}
