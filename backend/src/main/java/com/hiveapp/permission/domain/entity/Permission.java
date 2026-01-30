package com.hiveapp.permission.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "permissions")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission extends BaseEntity {

    @Column(name = "feature_id", nullable = false)
    private UUID featureId;

    @Column(nullable = false, unique = true, length = 150)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    private String description;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(nullable = false, length = 100)
    private String resource;

    public boolean matches(String action, String resource) {
        return this.action.equals(action) && this.resource.equals(resource);
    }
}
