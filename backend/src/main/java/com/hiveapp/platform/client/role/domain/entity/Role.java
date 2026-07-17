package com.hiveapp.platform.client.role.domain.entity;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.role.domain.constant.RoleStatus;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.domain.TenantInvariant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "roles")
@Getter @Setter
public class Role extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RolePermission> permissions = new ArrayList<>();

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "is_system_role", nullable = false)
    private boolean isSystemRole = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoleStatus status = RoleStatus.INACTIVE;

    @Column(name = "ever_assigned", nullable = false)
    private boolean everAssigned;

    @Column(name = "definition_revision", nullable = false)
    private long definitionRevision;

    @Version
    @Column(nullable = false)
    private long version;

    public boolean isActive() {
        return status == RoleStatus.ACTIVE;
    }

    @PrePersist
    @PreUpdate
    void validateTenantInvariant() {
        if (company != null) {
            TenantInvariant.requireSameEntity(
                    account,
                    company.getAccount(),
                    "Role company must belong to the role account");
        }
    }
}
