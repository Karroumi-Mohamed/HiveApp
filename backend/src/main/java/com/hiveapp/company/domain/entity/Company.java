package com.hiveapp.company.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "companies")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "legal_name")
    private String legalName;

    @Column(name = "tax_id", length = 50)
    private String taxId;

    @Column(length = 100)
    private String industry;

    @Column(length = 100)
    private String country;

    private String address;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CompanyModule> companyModules = new ArrayList<>();

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void addModule(CompanyModule companyModule) {
        companyModules.add(companyModule);
        companyModule.setCompany(this);
    }

    public void removeModule(CompanyModule companyModule) {
        companyModules.remove(companyModule);
        companyModule.setCompany(null);
    }
}
