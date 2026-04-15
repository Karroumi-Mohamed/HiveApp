package com.hiveapp.platform.client.company.api;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import com.hiveapp.platform.generated.PlatformPermissions;
import com.hiveapp.platform.client.company.dto.CompanyDto;
import com.hiveapp.platform.client.company.dto.CreateCompanyRequest;
import com.hiveapp.platform.client.company.dto.UpdateCompanyRequest;
import com.hiveapp.platform.client.company.mapper.CompanyMapper;
import com.hiveapp.platform.client.company.service.CompanyService;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import com.hiveapp.shared.quota.QuotaEnforcer;
import com.hiveapp.platform.client.feature.PlatformFeature;
import com.hiveapp.shared.exception.UnauthorizedException;

import dev.karroumi.permissionizer.PermissionGuard;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@PermissionNode(key = "company", description = "Company Management")
public class CompanyController {
    
    private final CompanyService companyService;
    private final CompanyMapper companyMapper;
    private final QuotaEnforcer quotaEnforcer;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PermissionNode(key = "create", description = "Create Company")
    public CompanyDto createCompany(@Valid @RequestBody CreateCompanyRequest req) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        
        quotaEnforcer.check(
            PlatformFeature.WORKSPACE, 
            PlatformFeature.COMPANIES, 
            accountId, 
            () -> (long) companyService.getAccountCompanies(accountId).size()
        );
        
        var company = companyService.createCompany(
            accountId, req.name(), req.legalName(), req.taxId(), req.industry(), req.country(), req.address()
        );
        return companyMapper.toDto(company);
    }

    @GetMapping
    @PermissionNode(key = "read_all", description = "List Account Companies")
    public List<CompanyDto> getCompanies() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return companyService.getAccountCompanies(accountId).stream()
                .map(companyMapper::toDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @PermissionNode(key = "read_single", description = "Get Company Details")
    public CompanyDto getCompany(@PathVariable UUID id) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        var company = companyService.getCompany(id);
        
        if (!company.getAccount().getId().equals(accountId)) {
            throw new UnauthorizedException("Company does not belong to your account");
        }
        
        return companyMapper.toDto(company);
    }

    @PatchMapping("/{id}")
    @PermissionNode(key = "update", description = "Update Company")
    public CompanyDto updateCompany(@PathVariable UUID id, @Valid @RequestBody UpdateCompanyRequest req) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        var companyCheck = companyService.getCompany(id);
        
        if (!companyCheck.getAccount().getId().equals(accountId)) {
            throw new UnauthorizedException("Company does not belong to your account");
        }
        
        var updated = companyService.updateCompany(id, req.name(), req.legalName(), req.industry(), req.country());
        return companyMapper.toDto(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermissionNode(key = "delete", description = "Deactivate Company")
    public void deactivateCompany(@PathVariable UUID id) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        var companyCheck = companyService.getCompany(id);
        
        if (!companyCheck.getAccount().getId().equals(accountId)) {
            throw new UnauthorizedException("Company does not belong to your account");
        }
        
        companyService.deactivateCompany(id);
    }
}
