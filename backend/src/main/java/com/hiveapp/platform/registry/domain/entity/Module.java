package com.hiveapp.platform.registry.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
import java.util.ArrayList;

/**
 * Namespace and catalog container for Features.
 * Participates in ZERO business logic — no permissions, no quotas, no sieve.
 * Display name and icon are managed by frontend code, not stored in DB.
 * Seeded automatically by FeatureSeeder at startup from AppFeature enums.
 */
@Entity
@Table(name = "modules")
@Getter @Setter
public class Module extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code; // e.g. "hr", "crm" — first segment of feature codes

    @OneToMany(mappedBy = "module", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Feature> features = new ArrayList<>();

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
