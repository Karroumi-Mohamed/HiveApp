package com.hiveapp.platform.security;

import com.hiveapp.platform.client.company.dto.UpdateCompanyRequest;
import com.hiveapp.platform.client.company.dto.CreateCompanyRequest;
import com.hiveapp.platform.client.company.service.impl.CompanyServiceImpl;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .andExpect(status().isNotFound());
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
                .andExpect(status().isNotFound());
    }

    @Test
    void clientCannotDeleteCompanyFromAnotherWorkspace() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerCompanyId = UUID.fromString(createCompany(ownerToken, "Owner Company").get("id").asText());

        mockMvc.perform(delete("/api/v1/companies/{id}", ownerCompanyId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
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

    @Test
    void inactiveCompanyBlocksOperationalContextUntilExplicitReactivation() throws Exception {
        String token = registerClientAndGetToken();
        UUID companyId = UUID.fromString(createCompany(token, "Lifecycle Company").get("id").asText());

        mockMvc.perform(delete("/api/v1/companies/{id}", companyId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/companies")
                        .header("Authorization", bearer(token))
                        .header("X-Company-ID", companyId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access Denied: Company is inactive"));

        mockMvc.perform(post("/api/v1/companies/{id}/reactivate", companyId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true));

        mockMvc.perform(get("/api/v1/companies")
                        .header("Authorization", bearer(token))
                        .header("X-Company-ID", companyId.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void companyMetadataIsNormalizedAndDuplicateTaxIdIsOnlyWarnedInsideWorkspace() throws Exception {
        String token = registerClientAndGetToken();
        CreateCompanyRequest first = new CreateCompanyRequest(
                " First Company ", " First Legal ", " tax-42 ", " Software ",
                "us", " Main Street ", " https://example.com/first.png ");
        CreateCompanyRequest second = new CreateCompanyRequest(
                "Second Company", null, "TAX-42", null, "US", null, null);

        String firstResponse = mockMvc.perform(post("/api/v1/companies")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("First Company"))
                .andExpect(jsonPath("$.taxId").value("TAX-42"))
                .andExpect(jsonPath("$.country").value("US"))
                .andExpect(jsonPath("$.address").value("Main Street"))
                .andExpect(jsonPath("$.logoUrl").value("https://example.com/first.png"))
                .andExpect(jsonPath("$.warnings").isEmpty())
                .andReturn().getResponse().getContentAsString();

        UUID firstCompanyId = UUID.fromString(objectMapper.readTree(firstResponse).get("id").asText());
        mockMvc.perform(delete("/api/v1/companies/{id}", firstCompanyId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/companies")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warnings[0]")
                        .value(CompanyServiceImpl.DUPLICATE_TAX_ID_WARNING));
    }

    @Test
    void sameTaxIdInAnotherWorkspaceIsNotDisclosedAsAWarning() throws Exception {
        String firstToken = registerClientAndGetToken();
        String secondToken = registerClientAndGetToken();
        CreateCompanyRequest request = new CreateCompanyRequest(
                "Company", null, "TAX-PRIVATE", null, "US", null, null);

        mockMvc.perform(post("/api/v1/companies")
                        .header("Authorization", bearer(firstToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warnings").isEmpty());

        mockMvc.perform(post("/api/v1/companies")
                        .header("Authorization", bearer(secondToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warnings").isEmpty());
    }

    @Test
    void companyCreationRequiresTwoLetterCountryCode() throws Exception {
        String token = registerClientAndGetToken();
        CreateCompanyRequest request = new CreateCompanyRequest(
                "Company", null, null, null, null, null, null);

        mockMvc.perform(post("/api/v1/companies")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("country")));
    }
}
