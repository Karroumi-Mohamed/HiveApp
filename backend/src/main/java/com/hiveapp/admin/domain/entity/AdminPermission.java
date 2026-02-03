package com.hiveapp.admin.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "admin_permissions")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminPermission extends BaseEntity {

    @Column(nullable = false, unique = true, length = 150)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    private String description;

    @Column(name = "module_id")
    private UUID moduleId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(nullable = false, length = 100)
    private String resource;

    public boolean matches(String action, String resource) {
        return this.action.equals(action) && this.resource.equals(resource);
    }
}
