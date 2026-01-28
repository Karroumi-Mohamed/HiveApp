package com.hiveapp.plan.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "plans")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "billing_cycle", nullable = false, length = 20)
    @Builder.Default
    private String billingCycle = "monthly";

    @Column(name = "max_companies", nullable = false)
    @Builder.Default
    private int maxCompanies = 1;

    @Column(name = "max_members", nullable = false)
    @Builder.Default
    private int maxMembers = 5;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PlanFeature> planFeatures = new ArrayList<>();

    public void addFeature(PlanFeature planFeature) {
        planFeatures.add(planFeature);
        planFeature.setPlan(this);
    }

    public void removeFeature(PlanFeature planFeature) {
        planFeatures.remove(planFeature);
        planFeature.setPlan(null);
    }

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
