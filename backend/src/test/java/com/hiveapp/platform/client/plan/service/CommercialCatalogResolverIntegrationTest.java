package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.ExtensionAvailabilityReason;
import com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.AddOnFeature;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.repository.AddOnFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.registry.definition.OrganizationFeature;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import com.hiveapp.platform.registry.definition.WorkspaceRolesFeature;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.shared.money.Money;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class CommercialCatalogResolverIntegrationTest {

    @Autowired private CommercialCatalogResolver resolver;
    @Autowired private ProductPriceResolver productPriceResolver;
    @Autowired private PlanRepository planRepository;
    @Autowired private AddOnRepository addOnRepository;
    @Autowired private AddOnFeatureRepository addOnFeatureRepository;
    @Autowired private QuotaPackageRepository quotaPackageRepository;
    @Autowired private ProductPriceRepository productPriceRepository;
    @Autowired private FeatureRepository featureRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void policyMatrixPreservesOpenDefaultsAndBlockedAlwaysWins() {
        var flex = planRepository.findByCode("FLEX").orElseThrow();
        var customRoles = addOnRepository.findByCode("CUSTOM_ROLES").orElseThrow();
        assertThat(flex.getExtensionPolicy()).isEqualTo(PlanExtensionPolicy.OPEN_COMPATIBLE);
        assertThat(flex.getSalesVisibility()).isEqualTo(ProductSalesVisibility.PUBLIC);
        assertThat(addOn("FLEX", "CUSTOM_ROLES").selectable()).isTrue();

        flex.setExtensionPolicy(PlanExtensionPolicy.CLOSED);
        flushAndClear();
        assertThat(reasons(addOn("FLEX", "CUSTOM_ROLES")))
                .contains(ExtensionAvailabilityReason.PLAN_EXTENSIONS_CLOSED);

        flex = planRepository.findByCode("FLEX").orElseThrow();
        customRoles = addOnRepository.findByCode("CUSTOM_ROLES").orElseThrow();
        flex.setExtensionPolicy(PlanExtensionPolicy.ALLOW_LIST);
        customRoles.setAllowedPlanCodes(Set.of());
        flushAndClear();
        assertThat(reasons(addOn("FLEX", "CUSTOM_ROLES")))
                .contains(ExtensionAvailabilityReason.NOT_ALLOW_LISTED);

        flex = planRepository.findByCode("FLEX").orElseThrow();
        customRoles = addOnRepository.findByCode("CUSTOM_ROLES").orElseThrow();
        flex.setExtensionPolicy(PlanExtensionPolicy.OPEN_COMPATIBLE);
        customRoles.setAllowedPlanCodes(Set.of());
        flushAndClear();
        assertThat(addOn("FLEX", "CUSTOM_ROLES").selectable()).isTrue();

        customRoles = addOnRepository.findByCode("CUSTOM_ROLES").orElseThrow();
        customRoles.setAllowedPlanCodes(Set.of());
        customRoles.setBlockedPlanCodes(Set.of("FLEX"));
        flushAndClear();
        assertThat(reasons(addOn("FLEX", "CUSTOM_ROLES")))
                .contains(ExtensionAvailabilityReason.PLAN_EXPLICITLY_BLOCKED);
    }

    @Test
    void directOnlyIsInvisibleToClientsButSelectableByAuthorizedOperators() {
        var flex = planRepository.findByCode("FLEX").orElseThrow();
        var customRoles = addOnRepository.findByCode("CUSTOM_ROLES").orElseThrow();
        flex.setSalesVisibility(ProductSalesVisibility.DIRECT_ONLY);
        customRoles.setSalesVisibility(ProductSalesVisibility.DIRECT_ONLY);
        flushAndClear();

        var clientPlan = plan("FLEX", CommercialCatalogResolver.Audience.CLIENT_CATALOG);
        assertThat(clientPlan.clientVisible()).isFalse();
        assertThat(reasons(clientPlan)).contains(ExtensionAvailabilityReason.DIRECT_ONLY);

        var operatorPlan = plan("FLEX", CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR);
        assertThat(operatorPlan.selectable()).isTrue();
        var operatorAddOn = operatorPlan.addOns().stream()
                .filter(item -> item.code().equals("CUSTOM_ROLES")).findFirst().orElseThrow();
        assertThat(operatorAddOn.selectable()).isTrue();
        assertThat(reasons(operatorAddOn)).doesNotContain(ExtensionAvailabilityReason.DIRECT_ONLY);
    }

    @Test
    void publicVisibilityControlsClientDiscoveryWithoutBlockingAuthorizedOperators() {
        var feature = featureRepository.findByCode(WorkspaceFeature.CODE).orElseThrow();
        feature.setPublicVisible(false);
        flushAndClear();

        var client = plan("FLEX", CommercialCatalogResolver.Audience.CLIENT_CATALOG);
        assertThat(reasons(client)).contains(ExtensionAvailabilityReason.FEATURE_NOT_CLIENT_FACING);

        var operator = plan("FLEX", CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR);
        assertThat(operator.selectable()).isTrue();
        assertThat(reasons(operator)).doesNotContain(
                ExtensionAvailabilityReason.FEATURE_NOT_CLIENT_FACING);

        feature = featureRepository.findByCode(WorkspaceFeature.CODE).orElseThrow();
        feature.setNewSalesEnabled(false);
        flushAndClear();

        assertThat(reasons(plan("FLEX", CommercialCatalogResolver.Audience.CLIENT_CATALOG)))
                .contains(ExtensionAvailabilityReason.FEATURE_NEW_SALES_DISABLED);
        assertThat(reasons(plan("FLEX", CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR)))
                .contains(ExtensionAvailabilityReason.FEATURE_NEW_SALES_DISABLED);
    }

    @Test
    void dependenciesCyclesExclusionsAndDuplicateCapabilitiesFailClosed() {
        var custom = addOnRepository.findByCode("CUSTOM_ROLES").orElseThrow();
        var organization = addOnRepository.findByCode("ORGANIZATION_TOOLS").orElseThrow();
        custom.setDependencyCodes(Set.of("ORGANIZATION_TOOLS"));
        organization.setDependencyCodes(Set.of("CUSTOM_ROLES"));
        flushAndClear();
        assertThat(reasons(addOn("FLEX", "CUSTOM_ROLES")))
                .containsAnyOf(ExtensionAvailabilityReason.DEPENDENCY_CYCLE,
                        ExtensionAvailabilityReason.DEPENDENCY_UNAVAILABLE);

        custom = addOnRepository.findByCode("CUSTOM_ROLES").orElseThrow();
        organization = addOnRepository.findByCode("ORGANIZATION_TOOLS").orElseThrow();
        custom.setDependencyCodes(Set.of());
        organization.setDependencyCodes(Set.of());
        custom.setExclusionCodes(Set.of("ORGANIZATION_TOOLS"));
        flushAndClear();
        var flex = planRepository.findByCode("FLEX").orElseThrow();
        var planPrice = productPriceResolver.resolvePlan(flex, null);
        var excluded = resolver.resolveSelection(
                flex, planPrice, Set.of("CUSTOM_ROLES", "ORGANIZATION_TOOLS"), List.of(),
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR);
        assertThat(reasons(excluded)).contains(ExtensionAvailabilityReason.EXCLUDED_SELECTION);

        custom = addOnRepository.findByCode("CUSTOM_ROLES").orElseThrow();
        custom.setExclusionCodes(Set.of());
        custom.setDependencyCodes(Set.of("ORGANIZATION_TOOLS"));
        var organizationFeature = featureRepository.findByCode(OrganizationFeature.CODE).orElseThrow();
        AddOnFeature duplicate = new AddOnFeature();
        duplicate.setAddOn(custom);
        duplicate.setFeature(organizationFeature);
        duplicate.setQuotaConfigs(List.of());
        addOnFeatureRepository.save(duplicate);
        flushAndClear();
        assertThat(reasons(addOn("FLEX", "CUSTOM_ROLES")))
                .contains(ExtensionAvailabilityReason.DUPLICATE_PAID_CAPABILITY);
        flex = planRepository.findByCode("FLEX").orElseThrow();
        var activation = resolver.resolveAddOnActivation(custom.getId(), List.of(flex))
                .get(flex.getId());
        assertThat(activation.selectable()).isFalse();
        assertThat(activation.issues()).extracting(item -> item.reason())
                .contains(ExtensionAvailabilityReason.DUPLICATE_PAID_CAPABILITY);
        planPrice = productPriceResolver.resolvePlan(flex, null);
        var overlapping = resolver.resolveSelection(
                flex, planPrice, Set.of("CUSTOM_ROLES", "ORGANIZATION_TOOLS"), List.of(),
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR);
        assertThat(reasons(overlapping)).contains(ExtensionAvailabilityReason.DUPLICATE_PAID_CAPABILITY);
    }

    @Test
    void packageReachStatesWhetherItIsDirectOrRequiresAnAddOn() {
        var direct = quota("FLEX", "MEMBERS_5");
        assertThat(direct.selectable()).isTrue();
        assertThat(direct.directlySelectable()).isTrue();
        assertThat(direct.requiredAddOnCodes()).isEmpty();

        var organization = addOnRepository.findByCode("ORGANIZATION_TOOLS").orElseThrow();
        var organizationFeature = featureRepository.findByCode(OrganizationFeature.CODE).orElseThrow();
        var existing = addOnFeatureRepository
                .findByAddOnIdAndFeature_Code(organization.getId(), OrganizationFeature.CODE).orElseThrow();
        existing.setQuotaConfigs(List.of(new QuotaLimitEntry("structures", 2L)));

        QuotaPackage addOnOwned = new QuotaPackage();
        addOnOwned.setCode("STRUCTURES_5_TEST");
        addOnOwned.setName("Five structures");
        addOnOwned.setFeature(organizationFeature);
        addOnOwned.setResource("structures");
        addOnOwned.setCapacityPerUnit(5);
        addOnOwned.setMoney(Money.of(new BigDecimal("2.00"), "MAD"));
        addOnOwned.setBillingCycle(BillingCycle.MONTHLY);
        addOnOwned.setRepeatable(true);
        addOnOwned.setMaximumQuantity(5);
        addOnOwned.setAllowedPlanCodes(Set.of());
        addOnOwned.setAllowedAddOnCodes(Set.of("ORGANIZATION_TOOLS"));
        addOnOwned.setStatus(QuotaPackageStatus.ACTIVE);
        quotaPackageRepository.save(addOnOwned);
        activePrice(addOnOwned, new BigDecimal("2.00"), "MAD", BillingCycle.MONTHLY);
        flushAndClear();

        var reached = quota("FLEX", "STRUCTURES_5_TEST");
        assertThat(reached.selectable()).isTrue();
        assertThat(reached.directlySelectable()).isFalse();
        assertThat(reached.requiredAddOnCodes()).containsExactly("ORGANIZATION_TOOLS");

        var flex = planRepository.findByCode("FLEX").orElseThrow();
        var planPrice = productPriceResolver.resolvePlan(flex, null);
        assertThat(resolver.resolveSelection(
                flex, planPrice, Set.of(), List.of(new QuotaPackageSelection("STRUCTURES_5_TEST", 1)),
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR).selectable()).isFalse();
        assertThat(resolver.resolveSelection(
                flex, planPrice, Set.of("ORGANIZATION_TOOLS"),
                List.of(new QuotaPackageSelection("STRUCTURES_5_TEST", 1)),
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR).selectable()).isTrue();
    }

    @Test
    void exactPriceTupleAndRegistryRuntimeControlsAreMandatory() {
        var flex = planRepository.findByCode("FLEX").orElseThrow();
        var custom = addOnRepository.findByCode("CUSTOM_ROLES").orElseThrow();
        activePrice(flex, BigDecimal.ZERO, "EUR", BillingCycle.YEARLY);
        flushAndClear();

        var yearlyEur = resolver.resolvePlan(
                planRepository.findByCode("FLEX").orElseThrow().getId(),
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR,
                new CommercialCatalogResolver.PriceTuple("EUR", BillingCycle.YEARLY));
        assertThat(reasons(yearlyEur.addOns().stream()
                .filter(item -> item.code().equals("CUSTOM_ROLES")).findFirst().orElseThrow()))
                .contains(ExtensionAvailabilityReason.PRICE_UNAVAILABLE);

        custom = addOnRepository.findByCode("CUSTOM_ROLES").orElseThrow();
        activePrice(custom, BigDecimal.ZERO, "EUR", BillingCycle.YEARLY);
        var feature = featureRepository.findByCode(WorkspaceRolesFeature.CODE).orElseThrow();
        feature.setRuntimeEnabled(false);
        feature.setNewSalesEnabled(false);
        flushAndClear();

        yearlyEur = resolver.resolvePlan(
                planRepository.findByCode("FLEX").orElseThrow().getId(),
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR,
                new CommercialCatalogResolver.PriceTuple("EUR", BillingCycle.YEARLY));
        assertThat(reasons(yearlyEur.addOns().stream()
                .filter(item -> item.code().equals("CUSTOM_ROLES")).findFirst().orElseThrow()))
                .contains(ExtensionAvailabilityReason.FEATURE_RUNTIME_DISABLED,
                        ExtensionAvailabilityReason.FEATURE_NEW_SALES_DISABLED)
                .doesNotContain(ExtensionAvailabilityReason.PRICE_UNAVAILABLE);
    }

    @Test
    void dependencyAndQuotaOwnerMustShareTheExactPlanPriceTuple() {
        var flex = planRepository.findByCode("FLEX").orElseThrow();
        var custom = addOnRepository.findByCode("CUSTOM_ROLES").orElseThrow();
        var organization = addOnRepository.findByCode("ORGANIZATION_TOOLS").orElseThrow();
        activePrice(flex, BigDecimal.ZERO, "EUR", BillingCycle.YEARLY);
        activePrice(custom, BigDecimal.ONE, "EUR", BillingCycle.YEARLY);
        custom.setDependencyCodes(Set.of("ORGANIZATION_TOOLS"));
        flushAndClear();

        var yearly = resolver.resolvePlan(
                planRepository.findByCode("FLEX").orElseThrow().getId(),
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR,
                new CommercialCatalogResolver.PriceTuple("EUR", BillingCycle.YEARLY));
        assertThat(reasons(yearly.addOns().stream()
                .filter(item -> item.code().equals("CUSTOM_ROLES")).findFirst().orElseThrow()))
                .contains(ExtensionAvailabilityReason.DEPENDENCY_UNAVAILABLE,
                        ExtensionAvailabilityReason.PRICE_UNAVAILABLE);

        var organizationFeature = addOnFeatureRepository
                .findByAddOnIdAndFeature_Code(organization.getId(), OrganizationFeature.CODE)
                .orElseThrow();
        organizationFeature.setQuotaConfigs(List.of(new QuotaLimitEntry("structures", 2L)));
        QuotaPackage item = new QuotaPackage();
        item.setCode("DISJOINT_TUPLE_PACKAGE");
        item.setName("Disjoint tuple package");
        item.setFeature(featureRepository.findByCode(OrganizationFeature.CODE).orElseThrow());
        item.setResource("structures");
        item.setCapacityPerUnit(1);
        item.setMoney(Money.of(BigDecimal.ONE, "EUR"));
        item.setBillingCycle(BillingCycle.YEARLY);
        item.setRepeatable(false);
        item.setMaximumQuantity(1);
        item.setAllowedPlanCodes(Set.of());
        item.setAllowedAddOnCodes(Set.of("ORGANIZATION_TOOLS"));
        item.setStatus(QuotaPackageStatus.ACTIVE);
        quotaPackageRepository.save(item);
        activePrice(item, BigDecimal.ONE, "EUR", BillingCycle.YEARLY);
        flushAndClear();

        yearly = resolver.resolvePlan(
                planRepository.findByCode("FLEX").orElseThrow().getId(),
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR,
                new CommercialCatalogResolver.PriceTuple("EUR", BillingCycle.YEARLY));
        assertThat(reasons(yearly.quotaPackages().stream()
                .filter(candidate -> candidate.code().equals("DISJOINT_TUPLE_PACKAGE"))
                .findFirst().orElseThrow()))
                .contains(ExtensionAvailabilityReason.QUOTA_OWNER_MISSING,
                        ExtensionAvailabilityReason.PRICE_UNAVAILABLE);
    }

    @Test
    void retainedDirectOnlyAndPausedItemsSurviveAnUnrelatedSelectionChange() {
        var flex = planRepository.findByCode("FLEX").orElseThrow();
        var custom = addOnRepository.findByCode("CUSTOM_ROLES").orElseThrow();
        var members = quotaPackageRepository.findByCode("MEMBERS_5").orElseThrow();
        custom.setSalesVisibility(ProductSalesVisibility.DIRECT_ONLY);
        custom.setStatus(AddOnStatus.INACTIVE);
        members.setSalesVisibility(ProductSalesVisibility.DIRECT_ONLY);
        members.setStatus(QuotaPackageStatus.INACTIVE);
        flushAndClear();

        flex = planRepository.findByCode("FLEX").orElseThrow();
        var tuple = CommercialCatalogResolver.PriceTuple.from(
                productPriceResolver.resolvePlan(flex, null));
        Set<String> addOns = Set.of("CUSTOM_ROLES");
        List<QuotaPackageSelection> packages = List.of(
                new QuotaPackageSelection("MEMBERS_5", 1),
                new QuotaPackageSelection("COMPANY_1", 1));

        assertThat(resolver.resolveSelection(
                flex, tuple, addOns, packages,
                CommercialCatalogResolver.Audience.CLIENT_CATALOG).selectable()).isFalse();
        assertThat(resolver.resolveSelection(
                flex, tuple, addOns, packages,
                CommercialCatalogResolver.Audience.CLIENT_CATALOG,
                new CommercialCatalogResolver.RetainedSelection(
                        Set.of("CUSTOM_ROLES"), Map.of("MEMBERS_5", 1))).selectable()).isTrue();
        assertThat(resolver.resolveSelection(
                flex, tuple, addOns, List.of(new QuotaPackageSelection("MEMBERS_5", 2)),
                CommercialCatalogResolver.Audience.CLIENT_CATALOG,
                new CommercialCatalogResolver.RetainedSelection(
                        Set.of("CUSTOM_ROLES"), Map.of("MEMBERS_5", 1))).selectable()).isFalse();
    }

    @Test
    void catalogueResolutionQueryCountDoesNotGrowWithProductsOrSharedFeatures() {
        long baseline = statementsFor(() -> resolver.resolveCatalog(
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR));
        var feature = featureRepository.findByCode(WorkspaceRolesFeature.CODE).orElseThrow();
        for (int index = 0; index < 30; index++) {
            AddOn item = new AddOn();
            item.setCode("QUERY_COUNT_ADD_ON_" + index);
            item.setName("Query count " + index);
            item.setMoney(Money.of(BigDecimal.ONE, "MAD"));
            item.setBillingCycle(BillingCycle.MONTHLY);
            item.setStatus(AddOnStatus.ACTIVE);
            item.setAllowedPlanCodes(Set.of("FLEX"));
            addOnRepository.save(item);
            AddOnFeature assignment = new AddOnFeature();
            assignment.setAddOn(item);
            assignment.setFeature(feature);
            assignment.setQuotaConfigs(List.of());
            addOnFeatureRepository.save(assignment);
            activePrice(item, BigDecimal.ONE, "MAD", BillingCycle.MONTHLY);
        }
        flushAndClear();

        long expanded = statementsFor(() -> resolver.resolveCatalog(
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR));
        assertThat(expanded).isEqualTo(baseline);
    }

    private CommercialCatalogResolver.PlanResolution plan(
            String code, CommercialCatalogResolver.Audience audience) {
        return resolver.resolveCatalog(audience).plans().stream()
                .filter(item -> item.plan().getCode().equals(code)).findFirst().orElseThrow();
    }

    private CommercialCatalogResolver.AddOnResolution addOn(String planCode, String addOnCode) {
        return plan(planCode, CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR)
                .addOns().stream().filter(item -> item.code().equals(addOnCode)).findFirst().orElseThrow();
    }

    private CommercialCatalogResolver.QuotaPackageResolution quota(String planCode, String packageCode) {
        return plan(planCode, CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR)
                .quotaPackages().stream().filter(item -> item.code().equals(packageCode)).findFirst().orElseThrow();
    }

    private Set<ExtensionAvailabilityReason> reasons(CommercialCatalogResolver.PlanResolution result) {
        return result.issues().stream().map(issue -> issue.reason())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<ExtensionAvailabilityReason> reasons(CommercialCatalogResolver.AddOnResolution result) {
        return result.issues().stream().map(issue -> issue.reason())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<ExtensionAvailabilityReason> reasons(
            CommercialCatalogResolver.QuotaPackageResolution result) {
        return result.issues().stream().map(issue -> issue.reason())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<ExtensionAvailabilityReason> reasons(CommercialCatalogResolver.SelectionResolution result) {
        List<com.hiveapp.platform.client.plan.dto.ExtensionAvailabilityIssue> issues = new ArrayList<>();
        issues.addAll(result.plan().issues());
        issues.addAll(result.issues());
        return issues.stream().map(issue -> issue.reason())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private void activePrice(Object owner, BigDecimal amount, String currency, BillingCycle cycle) {
        ProductPrice price;
        Money money = Money.of(amount, currency);
        if (owner instanceof com.hiveapp.platform.client.plan.domain.entity.Plan plan) {
            price = ProductPrice.draft(plan, money, cycle, Instant.EPOCH, null);
        } else if (owner instanceof AddOn addOn) {
            price = ProductPrice.draft(addOn, money, cycle, Instant.EPOCH, null);
        } else if (owner instanceof QuotaPackage item) {
            price = ProductPrice.draft(item, money, cycle, Instant.EPOCH, null);
        } else {
            throw new IllegalArgumentException("Unsupported price owner");
        }
        price.activate();
        productPriceRepository.save(price);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private long statementsFor(Runnable operation) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        operation.run();
        return statistics.getPrepareStatementCount();
    }
}
