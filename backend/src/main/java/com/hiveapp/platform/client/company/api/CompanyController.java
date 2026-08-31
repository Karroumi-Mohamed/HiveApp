package com.hiveapp.platform.client.company.api;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

import com.hiveapp.platform.client.company.dto.CompanyDto;
import com.hiveapp.platform.client.company.dto.CreateCompanyRequest;
import com.hiveapp.platform.client.company.dto.UpdateCompanyRequest;
import com.hiveapp.platform.client.company.service.CompanyService;
import com.hiveapp.shared.security.context.HiveAppContextHolder;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyDto createCompany(@Valid @RequestBody CreateCompanyRequest req) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return companyService.createCompany(
            accountId, req.name(), req.legalName(), req.taxId(), req.industry(), req.country(), req.address(), req.logoUrl()
        );
    }

    @GetMapping
    public List<CompanyDto> getCompanies() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return companyService.getAccountCompanies(accountId);
    }

    @GetMapping("/{id}")
    public CompanyDto getCompany(@PathVariable UUID id) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return companyService.getCompany(accountId, id);
    }

    @PatchMapping("/{id}")
    public CompanyDto updateCompany(@PathVariable UUID id, @Valid @RequestBody UpdateCompanyRequest req) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return companyService.updateCompany(
                accountId, id, req.name(), req.legalName(), req.taxId(), req.industry(),
                req.country(), req.address(), req.logoUrl());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateCompany(@PathVariable UUID id) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        companyService.deactivateCompany(accountId, id);
    }

    @PostMapping("/{id}/reactivate")
    public CompanyDto reactivateCompany(@PathVariable UUID id) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return companyService.reactivateCompany(accountId, id);
    }
}
