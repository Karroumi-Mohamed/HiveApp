package com.hiveapp.platform.security;

import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminPermissionResolutionIntegrationTest extends PlatformShellIntegrationTestSupport {
    @Autowired private FeatureRepository featureRepository;

    @Test
    void repeatedProfileRequestsObserveNewGrantAndRuntimeControlsWithoutRestarting() throws Exception {
        String token = loginAdminAndGetToken();
        var feature = featureRepository.findByCode("platform.plans").orElseThrow();
        boolean originalGrants = feature.isNewGrantsEnabled();
        boolean originalRuntime = feature.isRuntimeEnabled();
        try {
            feature.setNewGrantsEnabled(true);
            feature.setRuntimeEnabled(true);
            featureRepository.saveAndFlush(feature);
            profile(token, true);
            feature.setNewGrantsEnabled(false);
            featureRepository.saveAndFlush(feature);
            profile(token, false);
            feature.setNewGrantsEnabled(true);
            feature.setRuntimeEnabled(false);
            featureRepository.saveAndFlush(feature);
            profile(token, false);
            feature.setRuntimeEnabled(true);
            featureRepository.saveAndFlush(feature);
            profile(token, true);
        } finally {
            feature.setNewGrantsEnabled(originalGrants);
            feature.setRuntimeEnabled(originalRuntime);
            featureRepository.saveAndFlush(feature);
        }
    }

    private void profile(String token, boolean expected) throws Exception {
        mockMvc.perform(get("/api/admin/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", expected ? hasItem("platform.plans.list")
                        : not(hasItem("platform.plans.list"))))
                .andExpect(jsonPath("$.permissions", not(hasItem("platform.company.create"))));
    }
}
