package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
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

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

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
