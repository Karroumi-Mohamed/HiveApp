package com.hiveapp.platform.client.company.domain.entity;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.company.domain.constant.GroupStatus;
import com.hiveapp.platform.client.company.domain.constant.GroupTemplateScope;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.domain.TenantInvariant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "group_structure_templates", uniqueConstraints = @UniqueConstraint(
        name = "uk_group_template_owner_name",
        columnNames = {"scope", "owner_scope_key", "normalized_name"}))
@Getter @Setter
public class GroupStructureTemplate extends BaseEntity {

    private static final UUID PLATFORM_SCOPE_KEY = new UUID(0L, 0L);

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupTemplateScope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "owner_scope_key", nullable = false)
    private UUID ownerScopeKey;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 160)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupStatus status = GroupStatus.ACTIVE;

    @PrePersist
    @PreUpdate
    void validateInvariant() {
        if (scope == null) throw new IllegalStateException("Group template scope is required");
        switch (scope) {
            case PLATFORM -> {
                if (account != null || company != null) {
                    throw new IllegalStateException("Platform Group templates cannot have tenant owners");
                }
                ownerScopeKey = PLATFORM_SCOPE_KEY;
            }
            case ACCOUNT -> {
                if (account == null || company != null) {
                    throw new IllegalStateException("Account Group templates require only an Account owner");
                }
                ownerScopeKey = account.getId();
            }
            case COMPANY -> {
                if (account == null || company == null) {
                    throw new IllegalStateException("Company Group templates require Account and Company owners");
                }
                TenantInvariant.requireSameEntity(
                        account, company.getAccount(), "Group template Company must belong to its Account");
                ownerScopeKey = company.getId();
            }
        }
    }
}
