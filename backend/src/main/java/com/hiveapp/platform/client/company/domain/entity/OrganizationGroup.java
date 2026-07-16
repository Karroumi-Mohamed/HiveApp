package com.hiveapp.platform.client.company.domain.entity;

import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.company.domain.constant.GroupStatus;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.domain.TenantInvariant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "organization_groups", uniqueConstraints = @UniqueConstraint(
        name = "uk_organization_group_sibling",
        columnNames = {"company_id", "parent_scope_key", "normalized_name"}))
@Getter @Setter
public class OrganizationGroup extends BaseEntity {

    private static final UUID ROOT_SCOPE_KEY = new UUID(0L, 0L);

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private OrganizationGroup parent;

    @Column(name = "parent_scope_key", nullable = false)
    private UUID parentScopeKey;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 160)
    private String normalizedName;

    @Column(length = 1000)
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupStatus status = GroupStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "position_suggestions", columnDefinition = "json")
    private List<String> positionSuggestions = new ArrayList<>();

    @PrePersist
    @PreUpdate
    void validateInvariant() {
        if (parent != null) {
            TenantInvariant.requireSameEntity(
                    company, parent.getCompany(), "Group parent must belong to the same company");
            if (getId() != null && getId().equals(parent.getId())) {
                throw new IllegalStateException("A Group cannot be its own parent");
            }
        }
        parentScopeKey = parent == null ? ROOT_SCOPE_KEY : parent.getId();
    }
}
