package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.money.Money;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "plans")
@Getter @Setter
public class Plan extends BaseEntity {

    @Column(nullable = false, unique = true)
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
    void normalizeMoney() {
        setMoney(Money.of(price, currencyCode));
    }
}
