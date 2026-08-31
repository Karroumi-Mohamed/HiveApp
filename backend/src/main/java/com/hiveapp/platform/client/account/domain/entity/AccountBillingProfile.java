package com.hiveapp.platform.client.account.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account_billing_profiles", uniqueConstraints =
        @UniqueConstraint(name = "uk_account_billing_profile_account", columnNames = "account_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountBillingProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @Column(name = "legal_name", nullable = false, length = 240)
    private String legalName;

    @Column(name = "billing_email", length = 254)
    private String billingEmail;

    @Column(name = "tax_id", length = 100)
    private String taxId;

    @Column(length = 1000)
    private String address;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    public static AccountBillingProfile create(Account account, String legalName) {
        AccountBillingProfile profile = new AccountBillingProfile();
        profile.account = account;
        profile.legalName = required(legalName);
        return profile;
    }

    public void update(
            String legalName,
            String billingEmail,
            String taxId,
            String address,
            String countryCode
    ) {
        this.legalName = required(legalName);
        this.billingEmail = optional(billingEmail);
        this.taxId = optional(taxId);
        this.address = optional(address);
        this.countryCode = optional(countryCode);
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Billing legal name is required");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
