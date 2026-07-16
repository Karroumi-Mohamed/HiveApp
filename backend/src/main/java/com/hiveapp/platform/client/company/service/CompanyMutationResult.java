package com.hiveapp.platform.client.company.service;

import com.hiveapp.platform.client.account.domain.entity.Company;

import java.util.List;

public record CompanyMutationResult(Company company, List<String> warnings) {
    public CompanyMutationResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
