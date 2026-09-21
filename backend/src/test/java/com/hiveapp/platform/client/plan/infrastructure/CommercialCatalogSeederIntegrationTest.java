package com.hiveapp.platform.client.plan.infrastructure;

import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.repository.AddOnFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.registry.definition.B2bFeature;
import com.hiveapp.platform.registry.definition.CompanyFeature;
import com.hiveapp.platform.registry.definition.OrganizationFeature;
import com.hiveapp.platform.registry.definition.StaffFeature;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import com.hiveapp.platform.registry.definition.WorkspaceRolesFeature;
import com.hiveapp.shared.quota.QuotaLimitMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CommercialCatalogSeederIntegrationTest {

    @Autowired private PlanRepository planRepository;
    @Autowired private PlanFeatureRepository planFeatureRepository;
    @Autowired private AddOnRepository addOnRepository;
    @Autowired private AddOnFeatureRepository addOnFeatureRepository;
    @Autowired private QuotaPackageRepository quotaPackageRepository;
    @Autowired private ProductPriceRepository productPriceRepository;

    @Test
    void seedsAllProductPricesInMadWithoutChangingDemoAmounts() {
        var planAmounts = Map.of(
                "FREE", "0", "FLEX", "9.99", "PRO", "29.99",
                "BUSINESS", "59.99", "SCALE", "79.99", "ENTERPRISE", "99.99");
        planAmounts.forEach((code, amount) -> {
            var plan = planRepository.findByCode(code).orElseThrow();
            assertThat(plan.getCurrencyCode()).isEqualTo("MAD");
            assertThat(plan.getPrice()).isEqualByComparingTo(amount);
            assertSeededMadPrice(productPriceRepository.findAllByPlanId(plan.getId()), new BigDecimal(amount));
        });
        for (var specification : CommercialCatalogSeeder.BOOTSTRAP_ADD_ONS) {
            var addOn = addOnRepository.findByCode(specification.code()).orElseThrow();
            assertThat(addOn.getCurrencyCode()).isEqualTo("MAD");
            assertThat(addOn.getPrice()).isEqualByComparingTo(specification.price());
            assertSeededMadPrice(productPriceRepository.findAllByAddOnId(addOn.getId()), specification.price());
        }
        for (var specification : CommercialCatalogSeeder.BOOTSTRAP_QUOTA_PACKAGES) {
            var item = quotaPackageRepository.findByCode(specification.code()).orElseThrow();
            assertThat(item.getCurrencyCode()).isEqualTo("MAD");
            assertThat(item.getPrice()).isEqualByComparingTo(specification.price());
            assertSeededMadPrice(productPriceRepository.findAllByQuotaPackageId(item.getId()), specification.price());
        }
    }

    private void assertSeededMadPrice(List<ProductPrice> prices, BigDecimal amount) {
        assertThat(prices).singleElement().satisfies(price -> {
            assertThat(price.getCurrencyCode()).isEqualTo("MAD");
            assertThat(price.getAmount()).isEqualByComparingTo(amount);
            assertThat(price.getBillingCycle()).isEqualTo(BillingCycle.MONTHLY);
            assertThat(price.getStatus()).isEqualTo(ProductPriceStatus.ACTIVE);
            assertThat(price.isCompatibilityDefault()).isTrue();
        });
    }

    @Test
    void seedsSixPlansWithExplicitQuotaAndOptionalFeatureComposition() {
        assertThat(planRepository.findAll())
                .extracting(plan -> plan.getCode())
                .contains("FREE", "FLEX", "PRO", "BUSINESS", "SCALE", "ENTERPRISE");

        var flex = planRepository.findByCode("FLEX").orElseThrow();
        assertThat(planFeatureRepository.findByPlanIdAndFeature_Code(flex.getId(), OrganizationFeature.CODE))
                .get()
                .extracting(feature -> feature.getMode())
                .isEqualTo(PlanFeatureMode.OPTIONAL_ADD_ON);
        assertThat(planFeatureRepository.findByPlanIdAndFeature_Code(flex.getId(), WorkspaceRolesFeature.CODE))
                .get()
                .extracting(feature -> feature.getMode())
                .isEqualTo(PlanFeatureMode.OPTIONAL_ADD_ON);

        var flexWorkspace = planFeatureRepository
                .findByPlanIdAndFeature_Code(flex.getId(), WorkspaceFeature.CODE)
                .orElseThrow();
        assertThat(flexWorkspace.getQuotaConfigs()).isEmpty();

        var flexStaff = planFeatureRepository
                .findByPlanIdAndFeature_Code(flex.getId(), StaffFeature.CODE)
                .orElseThrow();
        assertThat(flexStaff.getQuotaConfigs())
                .extracting(quota -> quota.resource(), quota -> quota.mode(), quota -> quota.limit())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(StaffFeature.MEMBERS, QuotaLimitMode.FINITE, 5L));

        var flexCompanies = planFeatureRepository
                .findByPlanIdAndFeature_Code(flex.getId(), CompanyFeature.CODE)
                .orElseThrow();
        assertThat(flexCompanies.getQuotaConfigs())
                .extracting(quota -> quota.resource(), quota -> quota.mode(), quota -> quota.limit())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(CompanyFeature.COMPANIES, QuotaLimitMode.FINITE, 2L));

        assertThat(planFeatureRepository.findByPlanIdAndFeature_Code(flex.getId(), B2bFeature.CODE))
                .get()
                .extracting(feature -> feature.getMode())
                .isEqualTo(PlanFeatureMode.OPTIONAL_ADD_ON);
    }

    @Test
    void seedsActiveAddOnsOnPlansThatDeclareTheirFeaturesOptional() {
        assertThat(addOnRepository.findAllByOrderByNameAscRevisionNumberDesc())
                .extracting(addOn -> addOn.getCode())
                .contains("ORGANIZATION_TOOLS", "CUSTOM_ROLES", "B2B_COLLABORATION");

        for (var specification : CommercialCatalogSeeder.BOOTSTRAP_ADD_ONS) {
            var addOn = addOnRepository.findByCode(specification.code()).orElseThrow();
            assertThat(addOn.getStatus()).isEqualTo(AddOnStatus.ACTIVE);
            assertThat(addOn.getAllowedPlanCodes()).isEqualTo(specification.allowedPlanCodes());
            assertThat(addOnFeatureRepository.findAllByAddOnId(addOn.getId()))
                    .singleElement()
                    .satisfies(feature -> assertThat(feature.getFeature().getCode())
                            .isEqualTo(specification.featureCode()));
        }
    }

    @Test
    void seedsActiveQuotaPackagesOnlyOnFiniteCompatiblePlans() {
        assertThat(quotaPackageRepository.findAllByOrderByCodeAsc())
                .extracting(item -> item.getCode())
                .contains("MEMBERS_5", "MEMBERS_25", "MEMBERS_100", "COMPANY_1", "COMPANIES_5", "COMPANIES_20");

        for (var specification : CommercialCatalogSeeder.BOOTSTRAP_QUOTA_PACKAGES) {
            var item = quotaPackageRepository.findByCode(specification.code()).orElseThrow();
            assertThat(item.getStatus()).isEqualTo(QuotaPackageStatus.ACTIVE);
            assertThat(item.getAllowedPlanCodes()).isEqualTo(specification.allowedPlanCodes());
            assertThat(item.getAllowedAddOnCodes()).isEqualTo(Set.of());
            assertThat(item.getCapacityPerUnit()).isEqualTo(specification.capacityPerUnit());
            assertThat(item.getFeature().getCode()).isEqualTo(specification.featureCode());
            assertThat(item.getResource()).isEqualTo(specification.resource());
        }
    }
}
