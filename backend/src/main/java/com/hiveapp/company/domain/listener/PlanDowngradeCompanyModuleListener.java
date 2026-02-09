package com.hiveapp.company.domain.listener;

import com.hiveapp.company.domain.entity.Company;
import com.hiveapp.company.domain.entity.CompanyModule;
import com.hiveapp.company.domain.repository.CompanyRepository;
import com.hiveapp.module.domain.repository.FeatureRepository;
import com.hiveapp.plan.domain.entity.Plan;
import com.hiveapp.plan.domain.entity.PlanFeature;
import com.hiveapp.plan.domain.service.PlanService;
import com.hiveapp.plan.event.PlanChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * On plan downgrade, deactivates CompanyModules whose features are no longer
 * covered by the new plan.
 *
 * Spec rule: "Lors d'un downgrade, les CompanyModules hors-plan sont désactivés"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanDowngradeCompanyModuleListener {

    private final CompanyRepository companyRepository;
    private final PlanService planService;
    private final FeatureRepository featureRepository;

    @EventListener
    @Transactional
    public void onPlanChanged(PlanChangedEvent event) {
        UUID accountId = event.getAccountId();
        UUID newPlanId = event.getNewPlanId();

        Plan newPlan = planService.findPlanOrThrow(newPlanId);
        Set<UUID> newPlanFeatureIds = newPlan.getPlanFeatures().stream()
                .map(PlanFeature::getFeatureId)
                .collect(Collectors.toSet());

        List<Company> companies = companyRepository.findByAccountIdWithModules(accountId);

        for (Company company : companies) {
            List<CompanyModule> activeModules = company.getCompanyModules().stream()
                    .filter(CompanyModule::isActive)
                    .toList();

            for (CompanyModule cm : activeModules) {
                // Check if any feature of this module is still in the new plan
                Set<UUID> moduleFeatureIds = featureRepository.findFeatureIdsByModuleId(cm.getModuleId());
                boolean hasOverlap = moduleFeatureIds.stream().anyMatch(newPlanFeatureIds::contains);

                if (!hasOverlap) {
                    cm.deactivate();
                    log.info("CompanyModule (module={}) deactivated for company '{}' due to plan downgrade",
                            cm.getModuleId(), company.getName());
                }
            }

            companyRepository.save(company);
        }
    }
}
