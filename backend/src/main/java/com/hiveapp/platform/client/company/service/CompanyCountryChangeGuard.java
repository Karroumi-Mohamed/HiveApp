package com.hiveapp.platform.client.company.service;

import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.shared.exception.InvalidStateException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CompanyCountryChangeGuard {

    private final List<CompanyCountryDependency> dependencies;

    public void requireChangeAllowed(Company company, String requestedCountry) {
        if (Objects.equals(company.getCountry(), requestedCountry)) {
            return;
        }
        List<String> blockers = dependencies.stream()
                .map(dependency -> dependency.blockingReason(
                        company.getId(), company.getCountry(), requestedCountry))
                .flatMap(java.util.Optional::stream)
                .distinct()
                .toList();
        if (!blockers.isEmpty()) {
            throw new InvalidStateException(
                    "Company country cannot be changed while country-dependent records exist: "
                            + String.join("; ", blockers));
        }
    }
}
