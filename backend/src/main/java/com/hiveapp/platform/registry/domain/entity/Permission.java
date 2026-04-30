package com.hiveapp.platform.registry.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "permissions")
@Getter @Setter
public class Permission extends BaseEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feature_id", nullable = false)
    private Feature feature;

    @Column(nullable = false, unique = true)
    private String code; // e.g. "hr.employees.read"

    @Column(nullable = false)
    private String name;

    private String description;

    private String action; // READ, CREATE, etc.
    private String resource;
}