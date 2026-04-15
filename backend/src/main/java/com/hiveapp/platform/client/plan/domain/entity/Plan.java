package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.shared.domain.BaseEntity;
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
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle")
    private BillingCycle billingCycle;

    @Column(name = "is_active")
    private boolean isActive = true;
}
