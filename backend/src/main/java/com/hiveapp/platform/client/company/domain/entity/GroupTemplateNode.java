package com.hiveapp.platform.client.company.domain.entity;

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
@Table(name = "group_template_nodes", uniqueConstraints = @UniqueConstraint(
        name = "uk_group_template_node_sibling",
        columnNames = {"template_id", "parent_scope_key", "normalized_name"}))
@Getter @Setter
public class GroupTemplateNode extends BaseEntity {

    private static final UUID ROOT_SCOPE_KEY = new UUID(0L, 0L);

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private GroupStructureTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private GroupTemplateNode parent;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "position_suggestions", columnDefinition = "json")
    private List<String> positionSuggestions = new ArrayList<>();

    @PrePersist
    @PreUpdate
    void validateInvariant() {
        if (parent != null) {
            TenantInvariant.requireSameEntity(
                    template, parent.getTemplate(), "Group template parent must belong to the same template");
        }
        parentScopeKey = parent == null ? ROOT_SCOPE_KEY : parent.getId();
    }
}
