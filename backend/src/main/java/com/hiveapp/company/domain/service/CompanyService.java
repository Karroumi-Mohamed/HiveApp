package com.hiveapp.company.domain.service;

import com.hiveapp.company.domain.dto.*;
import com.hiveapp.company.domain.entity.Company;
import com.hiveapp.company.domain.entity.CompanyModule;
import com.hiveapp.company.domain.mapper.CompanyMapper;
import com.hiveapp.company.domain.repository.CompanyModuleRepository;
import com.hiveapp.company.domain.repository.CompanyRepository;
import com.hiveapp.module.domain.service.ModuleService;
import com.hiveapp.plan.domain.entity.Plan;
import com.hiveapp.plan.domain.entity.PlanFeature;
import com.hiveapp.plan.domain.service.PlanService;
import com.hiveapp.shared.exception.BusinessException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.subscription.domain.entity.Subscription;
import com.hiveapp.subscription.domain.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanyModuleRepository companyModuleRepository;
    private final CompanyMapper companyMapper;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanService planService;
    private final ModuleService moduleService;

    @Transactional
    public CompanyResponse createCompany(CreateCompanyRequest request) {
        Subscription subscription = subscriptionRepository
                .findByAccountIdAndStatus(request.getAccountId(), "active")
                .orElseThrow(() -> new BusinessException("No active subscription found for account"));

        Plan plan = planService.findPlanOrThrow(subscription.getPlanId());
        long currentCount = companyRepository.countByAccountIdAndIsActiveTrue(request.getAccountId());

        if (currentCount >= plan.getMaxCompanies()) {
            throw new BusinessException(
                    "Maximum number of companies (" + plan.getMaxCompanies() + ") reached for current plan");
        }

        Company company = companyMapper.toEntity(request);
        Company saved = companyRepository.save(company);
        log.info("Company created: {} for account {}", saved.getId(), request.getAccountId());

        return companyMapper.toResponse(saved);
    }

    @Transactional
    public CompanyResponse updateCompany(UUID id, UpdateCompanyRequest request) {
        Company company = findCompanyOrThrow(id);

        if (request.getName() != null) company.setName(request.getName());
        if (request.getLegalName() != null) company.setLegalName(request.getLegalName());
        if (request.getTaxId() != null) company.setTaxId(request.getTaxId());
        if (request.getIndustry() != null) company.setIndustry(request.getIndustry());
        if (request.getCountry() != null) company.setCountry(request.getCountry());
        if (request.getAddress() != null) company.setAddress(request.getAddress());
        if (request.getLogoUrl() != null) company.setLogoUrl(request.getLogoUrl());

        Company saved = companyRepository.save(company);
        log.info("Company updated: {}", saved.getId());

        return companyMapper.toResponse(saved);
    }

    @Transactional
    public CompanyResponse activateModule(UUID companyId, UUID moduleId) {
        Company company = findCompanyOrThrow(companyId);

        // Validate that the module is included in the Account's Plan
        validateModuleInPlan(company.getAccountId(), moduleId);

        CompanyModule companyModule = companyModuleRepository
                .findByCompanyIdAndModuleId(companyId, moduleId)
                .orElseGet(() -> {
                    CompanyModule cm = CompanyModule.builder()
                            .moduleId(moduleId)
                            .activatedAt(Instant.now())
                            .build();
                    company.addModule(cm);
                    return cm;
                });

        companyModule.activate();
        companyRepository.save(company);
        log.info("Module {} activated for company {}", moduleId, companyId);

        return companyMapper.toResponse(company);
    }

    @Transactional
    public CompanyResponse deactivateModule(UUID companyId, UUID moduleId) {
        CompanyModule companyModule = companyModuleRepository
                .findByCompanyIdAndModuleId(companyId, moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyModule", "moduleId", moduleId));

        companyModule.deactivate();
        companyModuleRepository.save(companyModule);
        log.info("Module {} deactivated for company {}", moduleId, companyId);

        Company company = findCompanyOrThrow(companyId);
        return companyMapper.toResponse(company);
    }

    public CompanyResponse getCompanyById(UUID id) {
        Company company = companyRepository.findByIdWithModules(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", id));
        return companyMapper.toResponse(company);
    }

    public List<CompanyResponse> getCompaniesByAccountId(UUID accountId) {
        return companyMapper.toResponseList(companyRepository.findByAccountIdAndIsActiveTrue(accountId));
    }

    public Set<UUID> getActiveModuleIds(UUID companyId) {
        return companyModuleRepository.findActiveModuleIdsByCompanyId(companyId);
    }

    public Company findCompanyOrThrow(UUID id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", id));
    }

    /**
     * Validates that the given module is included in the account's active Plan.
     * A module is "in the plan" if the plan includes at least one feature from that module.
     */
    private void validateModuleInPlan(UUID accountId, UUID moduleId) {
        Subscription subscription = subscriptionRepository
                .findByAccountIdAndStatus(accountId, "active")
                .orElseThrow(() -> new BusinessException("No active subscription found for account"));

        Plan plan = planService.findPlanOrThrow(subscription.getPlanId());

        Set<UUID> planFeatureIds = plan.getPlanFeatures().stream()
                .map(PlanFeature::getFeatureId)
                .collect(Collectors.toSet());

        // Get the module's feature IDs and check if any overlap with plan features
        Set<UUID> moduleFeatureIds = moduleService.getFeatureIdsByModuleId(moduleId);

        boolean moduleInPlan = moduleFeatureIds.stream().anyMatch(planFeatureIds::contains);
        if (!moduleInPlan) {
            throw new BusinessException("Module is not included in the account's current plan");
        }
    }
}
