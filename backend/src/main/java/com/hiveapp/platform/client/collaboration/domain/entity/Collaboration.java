package com.hiveapp.platform.client.collaboration.domain.entity;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.domain.TenantInvariant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "collaborations")
@Getter @Setter
public class Collaboration extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_account_id", nullable = false)
    private Account clientAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_account_id", nullable = false)
    private Account providerAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CollaborationStatus status;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @PrePersist
    @PreUpdate
    void validateTenantInvariant() {
        TenantInvariant.requireSameEntity(
                providerAccount,
                company.getAccount(),
                "Collaboration company must belong to the provider account");
        TenantInvariant.requireDifferentEntities(
                clientAccount,
                providerAccount,
                "Collaboration client and provider accounts must be different");
    }
}
