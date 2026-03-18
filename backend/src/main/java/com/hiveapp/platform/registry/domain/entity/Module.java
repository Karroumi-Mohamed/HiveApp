package com.hiveapp.platform.registry.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "modules")
@Getter @Setter
public class Module extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code; // e.g. "HR", "CRM"

    @Column(nullable = false)
    private String name;

    private String description;
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
