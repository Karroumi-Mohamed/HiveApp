package com.hiveapp.platform.client.role.domain.entity;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.shared.domain.BaseEntity;
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

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
