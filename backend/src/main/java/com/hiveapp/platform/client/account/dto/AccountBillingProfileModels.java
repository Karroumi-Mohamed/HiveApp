package com.hiveapp.platform.client.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AccountBillingProfileModels {
    private AccountBillingProfileModels() {}

    public record Profile(
            UUID accountId,
            String legalName,
            String billingEmail,
            String taxId,
            String address,
            String countryCode,
            boolean explicitlyConfigured
    ) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 240) String legalName,
            @Email @Size(max = 254) String billingEmail,
            @Size(max = 100) String taxId,
            @Size(max = 1000) String address,
            @Pattern(regexp = "(?i)[A-Z]{2}") String countryCode
    ) {}

    public record Snapshot(
            String legalName,
            String billingEmail,
            String taxId,
            String address,
            String countryCode
    ) {}
}
