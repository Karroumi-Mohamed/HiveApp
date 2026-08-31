package com.hiveapp.platform.client.plan.infrastructure;

import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageCreationReason;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.AddOnFeature;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.repository.AddOnFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.service.CommercialCatalogMutation;
import com.hiveapp.platform.client.plan.service.BillingConfigurationValidator;
import com.hiveapp.platform.registry.definition.B2bFeature;
import com.hiveapp.platform.registry.definition.CompanyFeature;
import com.hiveapp.platform.registry.definition.OrganizationFeature;
import com.hiveapp.platform.registry.definition.StaffFeature;
import com.hiveapp.platform.registry.definition.WorkspaceRolesFeature;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.shared.money.Money;
import com.hiveapp.shared.quota.QuotaLimitMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Seeds a useful commercial demo catalogue after the Plan templates exist.
 *
 * <p>Like {@link PlanSeeder}, this creates only missing bootstrap rows and never rewrites an
 * operator-managed item. Every attachment is validated against the real Plan composition and
 * code-owned quota definitions before an ACTIVE item is inserted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommercialCatalogSeeder {

    static final List<SeedAddOn> BOOTSTRAP_ADD_ONS = List.of(
            new SeedAddOn(
                    "ORGANIZATION_TOOLS",
                    "Organization Tools",
                    "Organization groups, positions, and reusable structure templates.",
                    new BigDecimal("9.99"),
                    OrganizationFeature.CODE,
                    Set.of("FLEX")),
            new SeedAddOn(
                    "CUSTOM_ROLES",
                    "Custom Roles",
                    "Reusable Account and Company roles with controlled permission delegation.",
                    new BigDecimal("7.99"),
                    WorkspaceRolesFeature.CODE,
                    Set.of("FLEX")),
            new SeedAddOn(
                    "B2B_COLLABORATION",
                    "B2B Collaboration",
                    "Controlled collaboration and delegated work between customer Accounts.",
                    new BigDecimal("19.99"),
                    B2bFeature.CODE,
                    Set.of("FLEX"))
    );

    static final List<SeedQuotaPackage> BOOTSTRAP_QUOTA_PACKAGES = List.of(
            new SeedQuotaPackage(
                    "MEMBERS_5", "5 extra members", StaffFeature.CODE, StaffFeature.MEMBERS,
                    5L, new BigDecimal("4.99"), true, 10,
                    Set.of("FLEX", "PRO", "BUSINESS", "SCALE")),
            new SeedQuotaPackage(
                    "MEMBERS_25", "25 extra members", StaffFeature.CODE, StaffFeature.MEMBERS,
                    25L, new BigDecimal("14.99"), true, 10,
                    Set.of("PRO", "BUSINESS", "SCALE")),
            new SeedQuotaPackage(
                    "MEMBERS_100", "100 extra members", StaffFeature.CODE, StaffFeature.MEMBERS,
                    100L, new BigDecimal("39.99"), true, 10,
                    Set.of("BUSINESS", "SCALE")),
            new SeedQuotaPackage(
                    "COMPANY_1", "1 extra company", CompanyFeature.CODE, CompanyFeature.COMPANIES,
                    1L, new BigDecimal("9.99"), true, 10,
                    Set.of("FLEX", "PRO")),
            new SeedQuotaPackage(
                    "COMPANIES_5", "5 extra companies", CompanyFeature.CODE, CompanyFeature.COMPANIES,
                    5L, new BigDecimal("29.99"), true, 10,
                    Set.of("PRO", "BUSINESS", "SCALE")),
            new SeedQuotaPackage(
                    "COMPANIES_20", "20 extra companies", CompanyFeature.CODE, CompanyFeature.COMPANIES,
                    20L, new BigDecimal("79.99"), true, 5,
                    Set.of("BUSINESS", "SCALE"))
    );

    private final PlanRepository planRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final AddOnRepository addOnRepository;
    private final AddOnFeatureRepository addOnFeatureRepository;
    private final QuotaPackageRepository quotaPackageRepository;
    private final BillingConfigurationValidator billingConfigurationValidator;

    @EventListener(ApplicationReadyEvent.class)
    @Order(4)
    @Transactional
    @CommercialCatalogMutation
    public void seed() {
        int addOnsCreated = seedAddOns();
        int packagesCreated = seedQuotaPackages();
        log.info(
                "Commercial catalogue bootstrap complete — AddOns created: {}, quota packages created: {}.",
                addOnsCreated,
                packagesCreated);
    }

    private int seedAddOns() {
        int created = 0;
        for (SeedAddOn specification : BOOTSTRAP_ADD_ONS) {
            if (addOnRepository.findByCode(specification.code()).isPresent()) {
                continue;
            }
            Feature feature = billingConfigurationValidator.validateAddOnFeature(
                    specification.featureCode(), List.of(), PlanSeeder.DEFAULT_CURRENCY);
            for (String planCode : specification.allowedPlanCodes()) {
                Plan plan = requireActiveMonthlyUsdPlan(planCode);
                var planFeature = planFeatureRepository
                        .findByPlanIdAndFeature_Code(plan.getId(), specification.featureCode())
                        .orElseThrow(() -> new IllegalStateException(
                                "Bootstrap AddOn feature is missing from Plan " + planCode + ": "
                                        + specification.featureCode()));
                if (planFeature.getMode() != PlanFeatureMode.OPTIONAL_ADD_ON) {
                    throw new IllegalStateException(
                            "Bootstrap AddOn " + specification.code() + " requires "
                                    + specification.featureCode() + " to be OPTIONAL_ADD_ON in Plan " + planCode + ".");
                }
            }

            AddOn addOn = new AddOn();
            addOn.setCode(specification.code());
            addOn.setName(specification.name());
            addOn.setDescription(specification.description());
            addOn.setMoney(Money.of(specification.price(), PlanSeeder.DEFAULT_CURRENCY));
            addOn.setBillingCycle(BillingCycle.MONTHLY);
            addOn.setAllowedPlanCodes(new LinkedHashSet<>(specification.allowedPlanCodes()));
            addOn.setStatus(AddOnStatus.ACTIVE);
            AddOn saved = addOnRepository.save(addOn);

            AddOnFeature addOnFeature = new AddOnFeature();
            addOnFeature.setAddOn(saved);
            addOnFeature.setFeature(feature);
            addOnFeature.setQuotaConfigs(List.of());
            addOnFeatureRepository.save(addOnFeature);
            created++;
        }
        return created;
    }

    private int seedQuotaPackages() {
        int created = 0;
        for (SeedQuotaPackage specification : BOOTSTRAP_QUOTA_PACKAGES) {
            if (quotaPackageRepository.findByCode(specification.code()).isPresent()) {
                continue;
            }
            Feature feature = billingConfigurationValidator.validateQuotaPackageDefinition(
                    specification.featureCode(), specification.resource());
            for (String planCode : specification.allowedPlanCodes()) {
                Plan plan = requireActiveMonthlyUsdPlan(planCode);
                var planFeature = planFeatureRepository
                        .findByPlanIdAndFeature_Code(plan.getId(), specification.featureCode())
                        .orElseThrow(() -> new IllegalStateException(
                                "Quota-package feature is missing from bootstrap Plan " + planCode + ": "
                                        + specification.featureCode() + "."));
                boolean finiteBase = planFeature.getMode() == PlanFeatureMode.INCLUDED
                        && planFeature.getQuotaConfigs().stream().anyMatch(quota ->
                        quota.resource().equals(specification.resource())
                                && quota.mode() == QuotaLimitMode.FINITE);
                if (!finiteBase) {
                    throw new IllegalStateException(
                            "Bootstrap quota package " + specification.code() + " requires a finite base quota for "
                                    + specification.resource() + " in Plan " + planCode + ".");
                }
            }

            QuotaPackage quotaPackage = new QuotaPackage();
            quotaPackage.setCode(specification.code());
            quotaPackage.setName(specification.name());
            quotaPackage.setDescription("Adds capacity to the Plan's included feature quota.");
            quotaPackage.setFeature(feature);
            quotaPackage.setResource(specification.resource());
            quotaPackage.setCapacityPerUnit(specification.capacityPerUnit());
            quotaPackage.setMoney(Money.of(specification.price(), PlanSeeder.DEFAULT_CURRENCY));
            quotaPackage.setBillingCycle(BillingCycle.MONTHLY);
            quotaPackage.setRepeatable(specification.repeatable());
            quotaPackage.setMaximumQuantity(specification.maximumQuantity());
            quotaPackage.setAllowedPlanCodes(new LinkedHashSet<>(specification.allowedPlanCodes()));
            quotaPackage.setAllowedAddOnCodes(new LinkedHashSet<>());
            quotaPackage.setStatus(QuotaPackageStatus.ACTIVE);
            quotaPackage.setCreationReason(QuotaPackageCreationReason.SEEDED);
            quotaPackageRepository.save(quotaPackage);
            created++;
        }
        return created;
    }

    private Plan requireActiveMonthlyUsdPlan(String code) {
        Plan plan = planRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Required bootstrap Plan is missing: " + code));
        if (!plan.isActive()
                || plan.getBillingCycle() != BillingCycle.MONTHLY
                || !PlanSeeder.DEFAULT_CURRENCY.equals(plan.getCurrencyCode())) {
            throw new IllegalStateException("Bootstrap Plan is not active monthly USD: " + code);
        }
        return plan;
    }

    record SeedAddOn(
            String code,
            String name,
            String description,
            BigDecimal price,
            String featureCode,
            Set<String> allowedPlanCodes
    ) {
    }

    record SeedQuotaPackage(
            String code,
            String name,
            String featureCode,
            String resource,
            long capacityPerUnit,
            BigDecimal price,
            boolean repeatable,
            int maximumQuantity,
            Set<String> allowedPlanCodes
    ) {
    }
}
