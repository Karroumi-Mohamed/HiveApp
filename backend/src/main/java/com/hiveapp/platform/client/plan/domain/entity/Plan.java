package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanCreationReason;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.money.Money;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "plans", uniqueConstraints = {
        @UniqueConstraint(name = "uk_plan_lineage_revision", columnNames = {"lineage_id", "revision_number"})
})
@Getter @Setter
public class Plan extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false)
    private BillingCycle billingCycle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanStatus status = PlanStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "extension_policy", nullable = false, length = 24)
    private PlanExtensionPolicy extensionPolicy = PlanExtensionPolicy.OPEN_COMPATIBLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "sales_visibility", nullable = false, length = 20)
    private ProductSalesVisibility salesVisibility = ProductSalesVisibility.PUBLIC;

    @Column(name = "lineage_id", nullable = false, updatable = false)
    private java.util.UUID lineageId = java.util.UUID.randomUUID();

    @Column(name = "revision_number", nullable = false, updatable = false)
    private int revisionNumber = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_plan_id", updatable = false)
    private Plan sourcePlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "creation_reason", nullable = false, updatable = false, length = 20)
    private PlanCreationReason creationReason = PlanCreationReason.CREATED;

    @Version
    @Column(nullable = false)
    private long version;

    public boolean isActive() {
        return status == PlanStatus.ACTIVE;
    }

    public Money money() {
        return Money.of(price, currencyCode);
    }

    public void setMoney(Money money) {
        this.price = money.amount();
        this.currencyCode = money.currencyCode();
    }

    @PrePersist
    @PreUpdate
    void validatePlan() {
        setMoney(Money.of(price, currencyCode));
        if (code == null || code.isBlank()) {
            throw new IllegalStateException("Plan code is required");
        }
        if (lineageId == null || revisionNumber < 1 || creationReason == null) {
            throw new IllegalStateException("Plan lineage identity is required");
        }
        if (extensionPolicy == null || salesVisibility == null) {
            throw new IllegalStateException("Plan commercial availability is required");
        }
    }
}
