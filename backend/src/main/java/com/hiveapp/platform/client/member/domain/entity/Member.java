package com.hiveapp.platform.client.member.domain.entity;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.domain.TenantInvariant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "members", uniqueConstraints = {
        @UniqueConstraint(name = "uk_members_account_user", columnNames = {"account_id", "user_id"}),
        @UniqueConstraint(name = "uk_members_account_employee", columnNames = {"account_id", "employee_number"})
})
@Getter @Setter
public class Member extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "employee_number", length = 80)
    private String employeeNumber;

    @Column(name = "is_owner", nullable = false)
    private boolean isOwner = false;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @PrePersist
    @PreUpdate
    void validateTenantInvariant() {
        if (isOwner) {
            TenantInvariant.requireSameEntity(
                    account.getOwner(),
                    user,
                    "An owner member must reference the account owner");
        }
    }
}
