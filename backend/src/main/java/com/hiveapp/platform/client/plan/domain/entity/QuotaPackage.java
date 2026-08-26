package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "quota_packages", uniqueConstraints = {
        @UniqueConstraint(name = "uk_quota_packages_code", columnNames = "code")
})
@Getter
@Setter
public class QuotaPackage extends BaseEntity {

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feature_id", nullable = false)
    private Feature feature;

    @Column(nullable = false)
    private String resource;

    @Column(name = "capacity_per_unit", nullable = false)
    private long capacityPerUnit;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private BillingCycle billingCycle;

    @Column(nullable = false)
    private boolean repeatable;

    @Column(name = "maximum_quantity", nullable = false)
    private int maximumQuantity = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuotaPackageStatus status = QuotaPackageStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "sales_visibility", nullable = false, length = 20)
    private ProductSalesVisibility salesVisibility = ProductSalesVisibility.PUBLIC;

    @Column(name = "definition_version", nullable = false)
    private long definitionVersion = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_plan_codes")
    private Set<String> allowedPlanCodes = new LinkedHashSet<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_add_on_codes")
    private Set<String> allowedAddOnCodes = new LinkedHashSet<>();

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
        return status == QuotaPackageStatus.ACTIVE;
    }

    @PrePersist
    @PreUpdate
    void validateCommercialConfiguration() {
        if (capacityPerUnit <= 0) {
            throw new IllegalStateException("Quota package capacity must be positive");
        }
        if (maximumQuantity <= 0 || (!repeatable && maximumQuantity != 1)) {
            throw new IllegalStateException("Quota package maximum quantity is invalid");
        }
        Money normalized = Money.of(price, currencyCode);
        if (normalized.isNegative()) {
            throw new IllegalStateException("Quota package price cannot be negative");
        }
        setMoney(normalized);
        allowedPlanCodes = new LinkedHashSet<>(allowedPlanCodes == null ? Set.of() : allowedPlanCodes);
        allowedAddOnCodes = new LinkedHashSet<>(allowedAddOnCodes == null ? Set.of() : allowedAddOnCodes);
        if (salesVisibility == null) {
            throw new IllegalStateException("Quota package sales visibility is required");
        }
    }
}
