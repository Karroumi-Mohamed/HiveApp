package com.hiveapp.platform.client.plan.infrastructure;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanCodes;
import com.hiveapp.platform.client.plan.domain.constant.PlanCreationReason;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.service.CommercialCatalogMutation;
import com.hiveapp.platform.registry.definition.B2bFeature;
import com.hiveapp.platform.registry.definition.ClientSubscriptionFeature;
import com.hiveapp.platform.registry.definition.CompanyFeature;
import com.hiveapp.platform.registry.definition.OrganizationFeature;
import com.hiveapp.platform.registry.definition.StaffFeature;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import com.hiveapp.platform.registry.definition.WorkspaceRolesFeature;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.money.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seeds plan templates and their feature compositions.
 *
 * Order 3 — after FeatureSeeder (1) and PermissionSeeder (2).
 * Feature rows are guaranteed to exist by the time this runs.
 *
 * This is bootstrap data, not a product-catalog migration mechanism. The explicit feature list
 * prevents newly discovered client features from silently becoming included in every plan.
 * Existing templates are never overwritten; missing bootstrap templates are created atomically.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanSeeder {

    public static final String DEFAULT_CURRENCY = "USD";
    static final List<String> BASELINE_FEATURE_CODES = List.of(
            WorkspaceFeature.CODE,
            CompanyFeature.CODE,
            StaffFeature.CODE,
            WorkspaceRolesFeature.CODE,
            OrganizationFeature.CODE,
            B2bFeature.CODE,
            ClientSubscriptionFeature.CODE
    );
    private static final List<SeedPlan> BOOTSTRAP_PLANS = List.of(
            new SeedPlan(PlanCodes.DEFAULT, "Free", "Evaluate the complete platform shell with a small team.",
                    BigDecimal.ZERO, BillingCycle.MONTHLY, 3L, 1L, Set.of()),
            new SeedPlan("FLEX", "Flex", "Build a larger workspace from the core shell and optional capabilities.",
                    new BigDecimal("9.99"), BillingCycle.MONTHLY, 5L, 2L,
                    Set.of(OrganizationFeature.CODE, WorkspaceRolesFeature.CODE, B2bFeature.CODE)),
            new SeedPlan("PRO", "Pro", "More capacity for growing teams and multiple companies.",
                    new BigDecimal("29.99"), BillingCycle.MONTHLY, 10L, 5L, Set.of()),
            new SeedPlan("BUSINESS", "Business", "Complete collaboration and access control for established operations.",
                    new BigDecimal("59.99"), BillingCycle.MONTHLY, 30L, 10L, Set.of()),
            new SeedPlan("SCALE", "Scale", "Higher finite capacity with the complete platform shell.",
                    new BigDecimal("79.99"), BillingCycle.MONTHLY, 75L, 25L, Set.of()),
            new SeedPlan("ENTERPRISE", "Enterprise", "Unlimited workspace capacity for complex organizations.",
                    new BigDecimal("99.99"), BillingCycle.MONTHLY, null, null, Set.of())
    );

    private final PlanRepository planRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final FeatureRepository featureRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Order(3)
    @Transactional
    @CommercialCatalogMutation
    public void seed() {
        Map<String, Feature> baselineFeatures = requireBaselineFeatures();
        int created = 0;

        for (SeedPlan specification : BOOTSTRAP_PLANS) {
            var existing = planRepository.findByCode(specification.code());
            if (existing.isPresent()) {
                if (PlanCodes.DEFAULT.equals(specification.code()) && !existing.get().isActive()) {
                    throw new IllegalStateException("The required FREE provisioning plan exists but is inactive.");
                }
                continue;
            }

            Plan plan = createPlan(specification);
            seedComposition(plan, specification, baselineFeatures);
            created++;
        }

        log.info("Plan bootstrap complete — created {} missing template(s); existing templates were preserved.", created);
    }

    private Map<String, Feature> requireBaselineFeatures() {
        Map<String, Feature> features = new LinkedHashMap<>();
        for (String featureCode : BASELINE_FEATURE_CODES) {
            Feature feature = featureRepository.findByCode(featureCode)
                    .orElseThrow(() -> new IllegalStateException(
                            "Required bootstrap feature is missing from the registry: " + featureCode));
            features.put(featureCode, feature);
        }
        return features;
    }

    private void seedComposition(Plan plan, SeedPlan specification, Map<String, Feature> features) {
        for (String featureCode : BASELINE_FEATURE_CODES) {
            Feature feature = features.get(featureCode);
            if (StaffFeature.CODE.equals(featureCode)) {
                seedStaff(plan, feature, specification);
            } else if (CompanyFeature.CODE.equals(featureCode)) {
                seedCompanies(plan, feature, specification);
            } else {
                PlanFeatureMode mode = modeFor(specification, featureCode);
                assign(plan, feature, mode, List.of());
            }
        }
    }

    private PlanFeatureMode modeFor(SeedPlan specification, String featureCode) {
        if (specification.optionalFeatureCodes().contains(featureCode)) {
            return PlanFeatureMode.OPTIONAL_ADD_ON;
        }
        return PlanFeatureMode.INCLUDED;
    }

    private void seedStaff(Plan plan, Feature feature, SeedPlan specification) {
        var memberEntry = new QuotaLimitEntry(StaffFeature.MEMBERS, specification.members());
        assign(plan, feature, PlanFeatureMode.INCLUDED, List.of(memberEntry));
    }

    private void seedCompanies(Plan plan, Feature feature, SeedPlan specification) {
        var companyEntry = new QuotaLimitEntry(CompanyFeature.COMPANIES, specification.companies());
        assign(plan, feature, PlanFeatureMode.INCLUDED, List.of(companyEntry));
    }

    private void assign(
            Plan plan,
            Feature feature,
            PlanFeatureMode mode,
            List<QuotaLimitEntry> quotaConfigs
    ) {
        var pf = new PlanFeature();
        pf.setPlan(plan);
        pf.setFeature(feature);
        pf.setMode(mode);
        pf.setQuotaConfigs(quotaConfigs);
        planFeatureRepository.save(pf);
    }

    private Plan createPlan(SeedPlan specification) {
        var p = new Plan();
        p.setCode(specification.code());
        p.setName(specification.name());
        p.setDescription(specification.description());
        p.setMoney(Money.of(specification.price(), DEFAULT_CURRENCY));
        p.setBillingCycle(specification.billingCycle());
        p.setStatus(PlanStatus.ACTIVE);
        p.setCreationReason(PlanCreationReason.SEEDED);
        return planRepository.save(p);
    }

    private record SeedPlan(
            String code,
            String name,
            String description,
            BigDecimal price,
            BillingCycle billingCycle,
            Long members,
            Long companies,
            Set<String> optionalFeatureCodes
    ) {
    }
}
