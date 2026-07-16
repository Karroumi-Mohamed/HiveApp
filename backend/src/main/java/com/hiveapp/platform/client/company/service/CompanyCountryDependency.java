package com.hiveapp.platform.client.company.service;

import java.util.Optional;
import java.util.UUID;

/**
 * Implemented by business domains that create records whose meaning depends on a company's country.
 */
public interface CompanyCountryDependency {
    Optional<String> blockingReason(UUID companyId, String currentCountry, String requestedCountry);
}
