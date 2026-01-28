package com.hiveapp.account.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account extends BaseEntity {

    @Column(name = "owner_id", nullable = false, unique = true)
    private UUID ownerId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    public void suspend() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }

    public void changePlan(UUID newPlanId) {
        this.planId = newPlanId;
    }
}
