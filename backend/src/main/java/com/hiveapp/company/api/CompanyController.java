package com.hiveapp.company.api;

import com.hiveapp.company.domain.dto.CompanyResponse;
import com.hiveapp.company.domain.dto.CreateCompanyRequest;
import com.hiveapp.company.domain.dto.UpdateCompanyRequest;
import com.hiveapp.company.domain.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CompanyResponse> createCompany(@Valid @RequestBody CreateCompanyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(companyService.createCompany(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(#id, 'COMPANY', 'companies.read')")
    public ResponseEntity<CompanyResponse> getCompanyById(@PathVariable UUID id) {
        return ResponseEntity.ok(companyService.getCompanyById(id));
    }

    @GetMapping("/account/{accountId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CompanyResponse>> getCompaniesByAccount(@PathVariable UUID accountId) {
        return ResponseEntity.ok(companyService.getCompaniesByAccountId(accountId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(#id, 'COMPANY', 'companies.update')")
    public ResponseEntity<CompanyResponse> updateCompany(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCompanyRequest request
    ) {
        return ResponseEntity.ok(companyService.updateCompany(id, request));
    }

    @PatchMapping("/{companyId}/modules/{moduleId}/activate")
    @PreAuthorize("hasPermission(#companyId, 'COMPANY', 'companies.modules.manage')")
    public ResponseEntity<Void> activateModule(@PathVariable UUID companyId, @PathVariable UUID moduleId) {
        companyService.activateModule(companyId, moduleId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{companyId}/modules/{moduleId}/deactivate")
    @PreAuthorize("hasPermission(#companyId, 'COMPANY', 'companies.modules.manage')")
    public ResponseEntity<Void> deactivateModule(@PathVariable UUID companyId, @PathVariable UUID moduleId) {
        companyService.deactivateModule(companyId, moduleId);
        return ResponseEntity.noContent().build();
    }
}
