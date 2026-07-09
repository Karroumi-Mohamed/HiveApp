package com.hiveapp.platform.client.company.domain.entity;

import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.domain.TenantInvariant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "departments")
@Getter @Setter
public class Department extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Department parent;

    @Column(nullable = false)
    private String name;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Member manager; // Corrected: Must be a Member of the Account

    @PrePersist
    @PreUpdate
    void validateTenantInvariant() {
        if (parent != null) {
            TenantInvariant.requireSameEntity(
                    company,
                    parent.getCompany(),
                    "Department parent must belong to the same company");
        }
        if (manager != null) {
            TenantInvariant.requireSameEntity(
                    company.getAccount(),
                    manager.getAccount(),
                    "Department manager must belong to the company account");
        }
    }
}
