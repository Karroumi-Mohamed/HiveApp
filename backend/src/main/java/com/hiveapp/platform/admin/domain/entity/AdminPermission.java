package com.hiveapp.platform.admin.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "admin_permissions")
@Getter @Setter
public class AdminPermission extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code; // e.g. "admin.plans.create"

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private String action; // CREATE, READ, UPDATE, DELETE, MANAGE

    @Column(nullable = false)
    private String resource; // plans, accounts, modules, features, users
}
