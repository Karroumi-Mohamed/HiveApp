package com.hiveapp.platform.client.account.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "companies")
@Getter @Setter
public class Company extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "legal_name", length = 240)
    private String legalName;

    @Column(name = "tax_id", length = 100)
    private String taxId;

    @Column(length = 120)
    private String industry;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(length = 1000)
    private String address;

    @Column(name = "logo_url", length = 2048)
    private String logoUrl;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
