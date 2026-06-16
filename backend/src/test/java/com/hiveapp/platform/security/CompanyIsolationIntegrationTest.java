package com.hiveapp.platform.security;

import com.hiveapp.platform.client.company.dto.UpdateCompanyRequest;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompanyIsolationIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Test
    void companyListOnlyReturnsCurrentWorkspaceCompanies() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerCompanyId = UUID.fromString(createCompany(ownerToken, "Owner Company").get("id").asText());
        UUID otherCompanyId = UUID.fromString(createCompany(otherToken, "Other Company").get("id").asText());

        mockMvc.perform(get("/api/v1/companies")
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", everyItem(not(ownerCompanyId.toString()))))
                .andExpect(jsonPath("$[0].id").value(otherCompanyId.toString()));
    }

    @Test
    void clientCannotReadCompanyFromAnotherWorkspace() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerCompanyId = UUID.fromString(createCompany(ownerToken, "Owner Company").get("id").asText());

        mockMvc.perform(get("/api/v1/companies/{id}", ownerCompanyId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientCannotUpdateCompanyFromAnotherWorkspace() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerCompanyId = UUID.fromString(createCompany(ownerToken, "Owner Company").get("id").asText());

        UpdateCompanyRequest request = new UpdateCompanyRequest("Changed", null, null, null);

        mockMvc.perform(patch("/api/v1/companies/{id}", ownerCompanyId)
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientCannotDeleteCompanyFromAnotherWorkspace() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerCompanyId = UUID.fromString(createCompany(ownerToken, "Owner Company").get("id").asText());

        mockMvc.perform(delete("/api/v1/companies/{id}", ownerCompanyId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientCannotBuildCompanyContextForAnotherWorkspace() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerCompanyId = UUID.fromString(createCompany(ownerToken, "Owner Company").get("id").asText());

        mockMvc.perform(get("/api/v1/companies")
                        .header("Authorization", bearer(otherToken))
                        .header("X-Company-ID", ownerCompanyId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void malformedCompanyContextHeaderIsRejectedAsBadRequest() throws Exception {
        String token = registerClientAndGetToken();

        mockMvc.perform(get("/api/v1/companies")
                        .header("Authorization", bearer(token))
                        .header("X-Company-ID", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid UUID format in X-Company-ID header"));
    }

    @Test
    void unknownCompanyContextHeaderIsRejectedAsNotFound() throws Exception {
        String token = registerClientAndGetToken();

        mockMvc.perform(get("/api/v1/companies")
                        .header("Authorization", bearer(token))
                        .header("X-Company-ID", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }
}
