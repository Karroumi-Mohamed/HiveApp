package com.hiveapp.platform.security;

import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicFeatureCatalogIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired
    private FeatureRepository featureRepository;

    @Test
    void publicCatalogCanBeReadWithoutAuthenticationAndOnlyReturnsSafeMetadata() throws Exception {
        String response = mockMvc.perform(get("/api/v1/features/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..code", hasItem("platform.company")))
                .andExpect(jsonPath("$..code", not(hasItem("platform.registry"))))
                .andExpect(jsonPath("$..code", not(hasItem("platform.plans"))))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response)
                .doesNotContain("\"permissions\"")
                .doesNotContain("\"surface\"")
                .doesNotContain("\"planAssignable\"")
                .doesNotContain("\"clientRoleGrantable\"")
                .doesNotContain("\"platformAdminRoleGrantable\"");
    }

    @Test
    void publicCatalogDoesNotExposeControlPlaneFeatureEvenIfRegistryStatusIsPublic() throws Exception {
        Feature plans = featureRepository.findByCode("platform.plans").orElseThrow();
        FeatureStatus originalStatus = plans.getStatus();
        plans.setStatus(FeatureStatus.PUBLIC);
        featureRepository.saveAndFlush(plans);

        try {
            mockMvc.perform(get("/api/v1/features/catalog"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$..code", not(hasItem("platform.plans"))))
                    .andExpect(jsonPath("$..code", not(hasItem("platform.registry"))));
        } finally {
            plans.setStatus(originalStatus);
            featureRepository.saveAndFlush(plans);
        }
    }

    @Test
    void publicCatalogFiltersByLifecycleStatusAfterCodeVisibility() throws Exception {
        Feature company = featureRepository.findByCode("platform.company").orElseThrow();
        FeatureStatus originalStatus = company.getStatus();

        company.setStatus(FeatureStatus.BETA);
        featureRepository.saveAndFlush(company);
        mockMvc.perform(get("/api/v1/features/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..code", hasItem("platform.company")));

        company.setStatus(FeatureStatus.INTERNAL);
        featureRepository.saveAndFlush(company);
        try {
            mockMvc.perform(get("/api/v1/features/catalog"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$..code", not(hasItem("platform.company"))));
        } finally {
            company.setStatus(originalStatus);
            featureRepository.saveAndFlush(company);
        }
    }
}
