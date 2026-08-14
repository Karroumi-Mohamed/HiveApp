package com.hiveapp.platform.client.plan.infrastructure;

import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.repository.AddOnFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.registry.definition.B2bFeature;
import com.hiveapp.platform.registry.definition.OrganizationFeature;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import com.hiveapp.platform.registry.definition.WorkspaceRolesFeature;
import com.hiveapp.shared.quota.QuotaLimitMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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
        assertThat(flexWorkspace.getQuotaConfigs())
                .extracting(quota -> quota.resource(), quota -> quota.mode(), quota -> quota.limit())
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(WorkspaceFeature.MEMBERS, QuotaLimitMode.FINITE, 5L),
                        org.assertj.core.groups.Tuple.tuple(WorkspaceFeature.COMPANIES, QuotaLimitMode.FINITE, 2L));

        assertThat(planFeatureRepository.findByPlanIdAndFeature_Code(flex.getId(), B2bFeature.CODE))
                .get()
                .extracting(feature -> feature.getMode())
                .isEqualTo(PlanFeatureMode.OPTIONAL_ADD_ON);
    }

    @Test
    void seedsActiveAddOnsOnPlansThatDeclareTheirFeaturesOptional() {
        assertThat(addOnRepository.findAllByOrderByCodeAsc())
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
        }
    }
}
